"""Управление привязанными аккаунтами: OAuth2 (YouTube) и токены (TikTok).

Безопасность:

* токены шифруются AES-GCM (``cryptography``) ключом, производным от
  machine-id + случайной соли в ``~/.beatforge_studio/.salt``;
* в SQLite (``accounts``) лежит только шифротекст;
* ключ никогда не пишется в логи; утечка базы без файла соли бесполезна
  (это не аппаратное хранилище — для максимальной защиты используйте keyring).

Функции:

* :class:`AuthManager` — CRUD аккаунтов, шифрование, автообновление токенов;
* :func:`youtube_device_flow` — OAuth2 device flow (без браузера, удобно для ПК);
* :func:`youtube_installed_flow` — классический installed-app flow с локальным
  сервером и открытием браузера;
* :func:`tiktok_playwright_login` — сохранение cookies TikTok через Playwright.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import platform
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from utils.cache_db import get_db
from utils.config import APP_DIR, get_config

LOG = logging.getLogger(__name__)

SALT_FILE = APP_DIR / ".salt"
#: Имя OAuth-клиента для хранения в метаданных.
YOUTUBE_SCOPES = [
    "https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube.readonly",
]


class AuthError(RuntimeError):
    """Ошибка авторизации/токенов."""


# ============================================================
#  Шифрование
# ============================================================
def _machine_fingerprint() -> bytes:
    """Собрать «отпечаток» машины (не секрет, только для привязки ключа)."""
    parts = [platform.node(), platform.machine(), platform.system(),
             str(uuid.getnode()), os.environ.get("USERNAME") or os.environ.get("USER") or ""]
    return "|".join(parts).encode("utf-8", errors="replace")


def _derive_key() -> bytes:
    """Вывести 32-байтный ключ из отпечатка машины и соли.

    Raises:
        AuthError: если ``cryptography`` не установлена.
    """
    try:
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    except Exception as exc:  # pragma: no cover
        raise AuthError("Для шифрования токенов установите cryptography") from exc

    APP_DIR.mkdir(parents=True, exist_ok=True)
    if SALT_FILE.exists():
        salt = SALT_FILE.read_bytes()
    else:
        salt = os.urandom(16)
        try:
            SALT_FILE.write_bytes(salt)
            if os.name != "nt":
                os.chmod(SALT_FILE, 0o600)
        except OSError as exc:
            LOG.warning("Не удалось сохранить соль: %s", exc)
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=200_000)
    return kdf.derive(_machine_fingerprint())


def encrypt_token(payload: Dict[str, Any]) -> bytes:
    """Зашифровать словарь токенов AES-GCM.

    Args:
        payload: Данные (access_token, refresh_token, expiry…).

    Returns:
        Шифротекст ``nonce || ciphertext``.

    Raises:
        AuthError: при проблемах с cryptography.
    """
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except Exception as exc:  # pragma: no cover
        raise AuthError("cryptography недоступна — токены хранить небезопасно") from exc
    key = _derive_key()
    nonce = os.urandom(12)
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    return nonce + AESGCM(key).encrypt(nonce, data, b"beatforge-studio")


def decrypt_token(blob: bytes) -> Dict[str, Any]:
    """Расшифровать токены.

    Args:
        blob: Шифротекст из базы.

    Returns:
        Словарь токенов (пустой, если расшифровать не удалось).
    """
    if not blob or len(blob) < 13:
        return {}
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM

        nonce, ciphertext = blob[:12], blob[12:]
        data = AESGCM(_derive_key()).decrypt(nonce, ciphertext, b"beatforge-studio")
        return json.loads(data.decode("utf-8"))
    except Exception as exc:
        LOG.warning("Не удалось расшифровать токены (другая машина?): %s", exc)
        return {}


# ============================================================
#  Модель аккаунта
# ============================================================
@dataclass
class Account:
    """Привязанный аккаунт соцсети.

    Attributes:
        platform: ``youtube`` | ``tiktok``.
        account_id: Стабильный id (channel id / username).
        display_name: Отображаемое имя.
        avatar_url: Аватар.
        tokens: Расшифрованные токены.
        meta: Доп. данные (scopes, expiry, cookies_path…).
        db_id: id записи в SQLite.
    """

    platform: str
    account_id: str
    display_name: str = ""
    avatar_url: str = ""
    tokens: Dict[str, Any] = field(default_factory=dict)
    meta: Dict[str, Any] = field(default_factory=dict)
    db_id: Optional[int] = None

    @property
    def label(self) -> str:
        """Подпись для UI."""
        name = {"youtube": "YouTube", "tiktok": "TikTok"}.get(self.platform, self.platform)
        return f"{name} · @{self.display_name or self.account_id}"

    @property
    def expires_at(self) -> float:
        """Время истечения access-токена (unix) или 0."""
        return float(self.tokens.get("expiry", 0) or 0)

    @property
    def expired(self) -> bool:
        """Истёк ли access-токен."""
        expiry = self.expires_at
        return bool(expiry) and expiry - 60 < time.time()

    def to_dict(self) -> Dict[str, Any]:
        """Публичное представление (без токенов)."""
        return {"platform": self.platform, "account_id": self.account_id,
                "display_name": self.display_name, "avatar_url": self.avatar_url,
                "meta": self.meta, "db_id": self.db_id, "expired": self.expired}


# ============================================================
#  Менеджер
# ============================================================
class AuthManager:
    """Хранилище аккаунтов и работа с токенами."""

    def __init__(self, db=None) -> None:
        """Args:
            db: Кэш-база (по умолчанию глобальная).
        """
        self._db = db or get_db()
        self._lock = threading.RLock()

    # ------------------------------------------------------------ CRUD
    def accounts(self, platform: Optional[str] = None) -> List[Account]:
        """Список привязанных аккаунтов с расшифрованными токенами."""
        out: List[Account] = []
        with self._lock:
            for row in self._db.accounts(platform):
                blob = self._db.account_token(row["platform"], row["account_id"]) or b""
                out.append(Account(
                    platform=row["platform"], account_id=row["account_id"],
                    display_name=row.get("display_name", ""),
                    avatar_url=row.get("avatar_url", ""),
                    tokens=decrypt_token(blob),
                    meta=row.get("meta", {}), db_id=row.get("id"),
                ))
        return out

    def account(self, platform: str, account_id: str) -> Optional[Account]:
        """Найти конкретный аккаунт."""
        for item in self.accounts(platform):
            if item.account_id == account_id:
                return item
        return None

    def default_account(self, platform: str) -> Optional[Account]:
        """Первый (последний добавленный) аккаунт платформы."""
        items = self.accounts(platform)
        return items[0] if items else None

    def save_account(self, account: Account) -> int:
        """Сохранить аккаунт (токены шифруются).

        Args:
            account: Данные аккаунта.

        Returns:
            id записи в базе.
        """
        with self._lock:
            blob = encrypt_token(account.tokens) if account.tokens else None
            account.db_id = self._db.upsert_account(
                account.platform, account.account_id, account.display_name,
                account.avatar_url, blob, account.meta,
            )
        LOG.info("Аккаунт сохранён: %s", account.label)
        return account.db_id

    def remove_account(self, platform: str, account_id: str) -> None:
        """Отвязать аккаунт (и удалить cookies TikTok, если есть)."""
        with self._lock:
            account = self.account(platform, account_id)
            if account and account.db_id:
                self._db.delete_account(account.db_id)
            cookies = (account.meta.get("cookies_path") if account else "") or ""
            if cookies and Path(cookies).exists():
                try:
                    Path(cookies).unlink()
                except OSError:
                    LOG.debug("Не удалось удалить cookies %s", cookies)
        LOG.info("Аккаунт отвязан: %s/%s", platform, account_id)

    # ------------------------------------------------------------ токены
    def update_tokens(self, account: Account, tokens: Dict[str, Any]) -> None:
        """Обновить токены аккаунта."""
        account.tokens = tokens
        self.save_account(account)

    def ensure_fresh_youtube(self, account: Account) -> Account:
        """Обновить access-токен YouTube, если он истёк.

        Args:
            account: Аккаунт YouTube.

        Returns:
            Тот же аккаунт с валидным токеном.

        Raises:
            AuthError: если обновить не удалось (нужна повторная привязка).
        """
        if account.platform != "youtube" or not account.expired:
            return account
        refresh_token = account.tokens.get("refresh_token")
        if not refresh_token:
            raise AuthError("Токен YouTube истёк, привяжите аккаунт заново")

        try:
            from google.auth.transport.requests import Request
            from google.oauth2.credentials import Credentials

            creds = Credentials(
                token=account.tokens.get("token", ""),
                refresh_token=refresh_token,
                token_uri=account.tokens.get("token_uri", "https://oauth2.googleapis.com/token"),
                client_id=account.meta.get("client_id", ""),
                client_secret=account.meta.get("client_secret", ""),
                scopes=account.tokens.get("scopes", YOUTUBE_SCOPES),
            )
            creds.refresh(Request())
            account.tokens.update({
                "token": creds.token,
                "expiry": creds.expiry.timestamp() if creds.expiry else time.time() + 3300,
            })
            self.update_tokens(account, account.tokens)
            LOG.info("Токен YouTube обновлён для %s", account.label)
            return account
        except Exception as exc:
            raise AuthError(f"Не удалось обновить токен YouTube: {exc}") from exc

    def youtube_credentials(self, account: Optional[Account] = None):
        """Вернуть ``google.oauth2.credentials.Credentials`` для API.

        Args:
            account: Аккаунт (по умолчанию — первый привязанный).

        Raises:
            AuthError: если аккаунт не привязан или токен мёртв.
        """
        acc = account or self.default_account("youtube")
        if acc is None:
            raise AuthError("YouTube не привязан")
        acc = self.ensure_fresh_youtube(acc)
        try:
            from google.oauth2.credentials import Credentials
        except Exception as exc:  # pragma: no cover
            raise AuthError("Установите google-auth: pip install google-auth") from exc
        return Credentials(
            token=acc.tokens.get("token", ""),
            refresh_token=acc.tokens.get("refresh_token", ""),
            token_uri=acc.tokens.get("token_uri", "https://oauth2.googleapis.com/token"),
            client_id=acc.meta.get("client_id", ""),
            client_secret=acc.meta.get("client_secret", ""),
            scopes=acc.tokens.get("scopes", YOUTUBE_SCOPES),
        )

    def tiktok_cookies_path(self, account: Optional[Account] = None) -> Optional[Path]:
        """Путь к файлу cookies TikTok (если аккаунт привязан)."""
        acc = account or self.default_account("tiktok")
        if acc is None:
            return None
        path = acc.meta.get("cookies_path") or acc.tokens.get("cookies_path")
        if not path:
            return None
        candidate = Path(path)
        return candidate if candidate.exists() else None


# ============================================================
#  YouTube OAuth2
# ============================================================
def _client_config() -> Tuple[str, str]:
    """Взять client_id/secret из настроек или переменных окружения."""
    cfg = get_config().publish
    raw = cfg.youtube_client_secrets.strip()
    client_id, client_secret = "", ""
    if raw:
        # поддерживаем и JSON-файл, и строку "id:secret"
        candidate = Path(raw)
        if candidate.exists():
            try:
                data = json.loads(candidate.read_text(encoding="utf-8"))
                block = data.get("installed") or data.get("web") or {}
                client_id = block.get("client_id", "")
                client_secret = block.get("client_secret", "")
            except Exception:
                LOG.warning("Не удалось прочитать client secrets из %s", candidate)
        elif ":" in raw:
            client_id, client_secret = raw.split(":", 1)
    client_id = client_id or os.environ.get("BEATFORGE_YOUTUBE_CLIENT_ID", "")
    client_secret = client_secret or os.environ.get("BEATFORGE_YOUTUBE_CLIENT_SECRET", "")
    return client_id.strip(), client_secret.strip()


def youtube_device_flow(on_code=None, timeout: float = 600.0) -> Dict[str, Any]:
    """OAuth2 Device Flow: показывает код, пользователь вводит его в браузере.

    Args:
        on_code: Колбэк ``(verification_url, user_code, expires_in)`` для UI.
        timeout: Сколько ждать подтверждения, сек.

    Returns:
        Токены для :meth:`AuthManager.save_account`.

    Raises:
        AuthError: если client_id не настроен или пользователь не подтвердил.
    """
    client_id, client_secret = _client_config()
    if not client_id:
        raise AuthError(
            "Не задан OAuth client_id для YouTube.\n"
            "Создайте OAuth client (Desktop) в Google Cloud Console, скачайте JSON "
            "и укажите путь в Настройках → Публикация, либо задайте переменные "
            "BEATFORGE_YOUTUBE_CLIENT_ID / BEATFORGE_YOUTUBE_CLIENT_SECRET."
        )
    try:
        import requests
    except ImportError as exc:  # pragma: no cover
        raise AuthError("requests не установлен") from exc

    endpoint = "https://oauth2.googleapis.com/device/code"
    scope = " ".join(YOUTUBE_SCOPES)
    try:
        resp = requests.post(endpoint, data={"client_id": client_id, "scope": scope}, timeout=30)
        resp.raise_for_status()
        payload = resp.json()
    except Exception as exc:
        raise AuthError(f"Не удалось начать авторизацию: {exc}") from exc

    device_code = payload.get("device_code", "")
    user_code = payload.get("user_code", "")
    verify_url = payload.get("verification_url") or payload.get("verification_uri", "")
    interval = int(payload.get("interval", 5))
    expires_in = int(payload.get("expires_in", 1800))
    if on_code:
        on_code(verify_url, user_code, expires_in)

    token_endpoint = "https://oauth2.googleapis.com/token"
    deadline = time.time() + min(timeout, expires_in)
    while time.time() < deadline:
        time.sleep(interval)
        try:
            resp = requests.post(token_endpoint, data={
                "client_id": client_id, "client_secret": client_secret,
                "device_code": device_code,
                "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            }, timeout=30)
            data = resp.json()
        except Exception as exc:
            LOG.debug("Ожидание подтверждения: %s", exc)
            continue
        if "access_token" in data:
            return {
                "token": data["access_token"],
                "refresh_token": data.get("refresh_token", ""),
                "token_uri": token_endpoint,
                "scopes": YOUTUBE_SCOPES,
                "expiry": time.time() + float(data.get("expires_in", 3600)),
            }
        error = data.get("error", "")
        if error == "authorization_pending":
            continue
        if error == "slow_down":
            interval += 2
            continue
        if error in ("access_denied", "expired_token"):
            raise AuthError("Авторизация отклонена или истекла — попробуйте снова")
        raise AuthError(f"Ошибка авторизации: {error or data}")
    raise AuthError("Время ожидания авторизации истекло")


def youtube_installed_flow(client_secret_file: Optional[str] = None,
                           open_browser: bool = True) -> Dict[str, Any]:
    """Классический flow installed-app (открывает браузер, слушает localhost).

    Args:
        client_secret_file: Путь к JSON от Google Cloud (или из настроек).
        open_browser: Открыть браузер автоматически.

    Returns:
        Токены + ``client_id``/``client_secret`` для сохранения.

    Raises:
        AuthError: если не установлен google-auth-oauthlib или файл не найден.
    """
    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except Exception as exc:  # pragma: no cover
        raise AuthError("Установите google-auth-oauthlib") from exc

    client_id, client_secret = _client_config()
    path = client_secret_file or get_config().publish.youtube_client_secrets
    flow = None
    if path and Path(path).exists():
        flow = InstalledAppFlow.from_client_secrets_file(path, YOUTUBE_SCOPES)
    elif client_id and client_secret:
        flow = InstalledAppFlow.from_client_config(
            {"installed": {"client_id": client_id, "client_secret": client_secret,
                           "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                           "token_uri": "https://oauth2.googleapis.com/token",
                           "redirect_uris": ["http://localhost"]}},
            YOUTUBE_SCOPES,
        )
    if flow is None:
        raise AuthError("Не найден client_secret.json для YouTube OAuth")

    creds = flow.run_local_server(port=0, open_browser=open_browser, timeout_seconds=300)
    return {
        "token": creds.token,
        "refresh_token": creds.refresh_token or "",
        "token_uri": creds.token_uri or "https://oauth2.googleapis.com/token",
        "scopes": list(creds.scopes or YOUTUBE_SCOPES),
        "expiry": creds.expiry.timestamp() if creds.expiry else time.time() + 3300,
        "client_id": client_id or flow.client_config.get("installed", {}).get("client_id", ""),
        "client_secret": client_secret or flow.client_config.get("installed", {}).get(
            "client_secret", ""),
    }


# ============================================================
#  TikTok (Playwright)
# ============================================================
TIKTOK_LOGIN_URL = "https://www.tiktok.com/login"
TIKTOK_COOKIES_DIR = APP_DIR / "cookies"


def tiktok_playwright_login(username: str = "", headless: bool = False
                            ) -> Optional[Path]:
    """Открыть браузер Playwright, дать пользователю войти, сохранить cookies.

    Args:
        username: Имя (используется в имени файла cookies и как account_id).
        headless: Запустить без окна (для повторного входа с сохранённой сессией).

    Returns:
        Путь к файлу cookies (storage_state) или None при неудаче.

    Raises:
        AuthError: если Playwright не установлен/не установлены браузеры.
    """
    try:
        from playwright.sync_api import sync_playwright
    except Exception as exc:  # pragma: no cover
        raise AuthError(
            "Playwright не установлен. Выполните:\n"
            "  pip install playwright\n  playwright install chromium"
        ) from exc

    TIKTOK_COOKIES_DIR.mkdir(parents=True, exist_ok=True)
    safe_name = "".join(ch for ch in (username or "account") if ch.isalnum() or ch in "-_") or "account"
    out = TIKTOK_COOKIES_DIR / f"tiktok_{safe_name}.json"

    with sync_playwright() as pw:
        try:
            browser = pw.chromium.launch(headless=headless)
        except Exception as exc:
            raise AuthError(f"Не удалось запустить Chromium: {exc}") from exc
        context = browser.new_context()
        page = context.new_page()
        page.goto(TIKTOK_LOGIN_URL, timeout=90_000)
        if not headless:
            LOG.info("Ожидание входа в TikTok (окно браузера открыто)…")
            try:
                page.wait_for_url("**/tiktok.com/foryou**", timeout=300_000)
            except Exception:
                LOG.warning("Не удалось дождаться перехода на /foryou — сохраняю как есть")
        try:
            context.storage_state(path=str(out))
        except Exception as exc:
            raise AuthError(f"Не удалось сохранить cookies: {exc}") from exc
        try:
            cookies = context.cookies()
            has_session = any(c.get("name") in ("sessionid", "sid_tt") for c in cookies)
        except Exception:
            has_session = True
        browser.close()
    if not has_session:
        LOG.warning("В cookies не найден sessionid — возможно, вход не выполнен")
    LOG.info("TikTok cookies сохранены: %s", out)
    return out


def tiktok_session_info(cookies_path: str | Path) -> Dict[str, Any]:
    """Прочитать базовую информацию из cookies (без сетевых запросов).

    Args:
        cookies_path: Путь к storage_state JSON.

    Returns:
        Словарь ``{"username": str, "has_session": bool, "count": int}``.
    """
    path = Path(cookies_path)
    if not path.exists():
        return {"username": "", "has_session": False, "count": 0}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {"username": "", "has_session": False, "count": 0}
    cookies = data.get("cookies") or []
    has_session = any(c.get("name") in ("sessionid", "sid_tt") for c in cookies)
    username = ""
    for cookie in cookies:
        if cookie.get("name") == "tt_chain_token":
            continue
        if cookie.get("name") == "uid_tt" and not username:
            username = cookie.get("value", "")[:12]
    return {"username": username, "has_session": has_session, "count": len(cookies)}
