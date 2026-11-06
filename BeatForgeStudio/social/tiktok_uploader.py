"""Загрузка видео в TikTok: Playwright (основной путь) и tiktok-uploader (fallback).

Playwright-путь:

1. берём сохранённые cookies (``storage_state``) после ручного входа;
2. открываем ``https://www.tiktok.com/tiktokstudio/upload``;
3. загружаем файл через ``set_input_files``;
4. заполняем описание и теги, ждём обработки;
5. нажимаем «Post» (или сохраняем в черновики, если ``draft=True``).

Ограничения платформы (проверяются заранее): длительность до 10 минут,
вертикальное видео 9:16 рекомендуется, MP4/H.264 + AAC.

Модуль не может гарантировать работу при изменении вёрстки TikTok — все
ошибки логируются и возвращаются с понятным сообщением.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from PyQt6.QtCore import QObject, QThread, pyqtSignal

from social.auth_manager import Account, AuthError, AuthManager
from utils.config import get_config
from utils.ffmpeg_helper import probe

LOG = logging.getLogger(__name__)

#: Ограничения TikTok.
TIKTOK_MAX_DURATION = 10 * 60  # 10 минут
TIKTOK_MAX_SIZE = 4 * 1024 * 1024 * 1024  # 4 ГБ (веб-загрузка)
TIKTOK_UPLOAD_URL = "https://www.tiktok.com/tiktokstudio/upload"


@dataclass
class TikTokMetadata:
    """Данные для публикации в TikTok.

    Attributes:
        caption: Подпись (включая теги).
        hashtags: Список тегов без ``#``.
        privacy: ``public`` | ``friends`` | ``private`` (через переключатель в UI).
        allow_comments: Разрешить комментарии.
        allow_duet: Разрешить дуэты.
        allow_stitch: Разрешить ститчи.
        cover_time: Секунда для обложки.
        music_volume: Громкость оригинального звука 0..100.
        draft: Сохранить как черновик вместо публикации.
    """

    caption: str = "Сделано в BeatForge Studio"
    hashtags: List[str] = field(default_factory=list)
    privacy: str = "public"
    allow_comments: bool = True
    allow_duet: bool = True
    allow_stitch: bool = True
    cover_time: float = 0.0
    music_volume: int = 50
    draft: bool = False

    @property
    def full_caption(self) -> str:
        """Подпись с тегами (``текст #tag1 #tag2``)."""
        tags = " ".join(f"#{t.lstrip('#')}" for t in self.hashtags if t.strip())
        caption = (self.caption or "").strip()
        text = f"{caption} {tags}".strip()
        return text[:2200]


@dataclass
class TikTokResult:
    """Результат публикации.

    Attributes:
        url: Ссылка на видео (если удалось получить).
        status: ``posted`` | ``draft``.
        message: Дополнительный текст для UI.
    """

    url: str = ""
    status: str = "posted"
    message: str = ""


def validate_video(path: str | Path) -> Dict[str, Any]:
    """Проверить видео на соответствие требованиям TikTok.

    Args:
        path: Файл видео.

    Returns:
        Словарь ``{"ok": bool, "warnings": [...], "duration": float}``.
    """
    src = Path(path)
    warnings: List[str] = []
    info: Dict[str, Any] = {"ok": True, "warnings": warnings, "duration": 0.0}
    if not src.exists():
        return {"ok": False, "warnings": ["Файл не найден"], "duration": 0.0}
    if src.stat().st_size > TIKTOK_MAX_SIZE:
        warnings.append("Файл больше 4 ГБ — TikTok может отклонить загрузку")
        info["ok"] = False
    try:
        meta = probe(src)
        duration = float(meta.get("duration", 0.0))
        info["duration"] = duration
        if duration > TIKTOK_MAX_DURATION:
            warnings.append(
                f"Длительность {duration / 60:.1f} мин превышает лимит TikTok (10 мин)")
            info["ok"] = False
        streams = meta.get("streams", [])
        video_stream = next((s for s in streams if s.get("codec_type") == "video"), None)
        if video_stream:
            width = int(video_stream.get("width", 0) or 0)
            height = int(video_stream.get("height", 0) or 0)
            if width and height and width > height:
                warnings.append("Горизонтальное видео: TikTok лучше принимает 9:16 "
                                "(приложение умеет адаптировать при экспорте)")
            if video_stream.get("codec_name") not in (None, "h264"):
                warnings.append("Рекомендуется H.264 — конвертируйте при экспорте")
    except Exception as exc:
        warnings.append(f"Не удалось проверить видео: {exc}")
    return info


def _cookies_path(account: Optional[Account], auth: AuthManager) -> Path:
    """Путь к cookies аккаунта TikTok.

    Raises:
        AuthError: если аккаунт не привязан.
    """
    acc = account or auth.default_account("tiktok")
    if acc is None:
        raise AuthError("TikTok не привязан — добавьте аккаунт во вкладке «Публикация»")
    path = acc.meta.get("cookies_path") or acc.tokens.get("cookies_path") or ""
    candidate = Path(path) if path else None
    if candidate is None or not candidate.exists():
        candidate = auth.tiktok_cookies_path(acc)
    if candidate is None or not candidate.exists():
        raise AuthError("Файл cookies TikTok не найден — привяжите аккаунт заново")
    return candidate


def upload_video_playwright(path: str | Path, metadata: TikTokMetadata, *,
                            account: Optional[Account] = None,
                            auth: Optional[AuthManager] = None,
                            progress_cb: Optional[Callable[[int, str], None]] = None,
                            cancel_event: Optional[threading.Event] = None,
                            headless: Optional[bool] = None) -> TikTokResult:
    """Загрузить видео в TikTok через Playwright.

    Args:
        path: Видеофайл.
        metadata: Подпись, теги, настройки.
        account: Аккаунт TikTok.
        auth: Менеджер авторизации.
        progress_cb: Колбэк ``(процент, статус)``.
        cancel_event: Флаг отмены.
        headless: Запуск без окна (по умолчанию из настроек).

    Returns:
        :class:`TikTokResult`.

    Raises:
        AuthError: если Playwright/аккаунт недоступны.
        RuntimeError: если публикация не удалась.
    """
    try:
        from playwright.sync_api import sync_playwright
    except Exception as exc:  # pragma: no cover
        raise AuthError(
            "Playwright не установлен: pip install playwright && playwright install chromium"
        ) from exc

    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")
    check = validate_video(src)
    if not check["ok"]:
        raise RuntimeError("; ".join(check["warnings"]))

    manager = auth or AuthManager()
    cookies = _cookies_path(account, manager)
    cfg = get_config().publish
    run_headless = cfg.tiktok_headless if headless is None else headless

    def report(percent: int, status: str) -> None:
        if progress_cb:
            progress_cb(max(0, min(100, percent)), status)

    report(5, "запуск браузера…")
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=run_headless)
        context = browser.new_context(storage_state=str(cookies),
                                      viewport={"width": 1440, "height": 900})
        page = context.new_page()
        try:
            report(12, "открываю TikTok Studio…")
            page.goto(TIKTOK_UPLOAD_URL, timeout=90_000, wait_until="domcontentloaded")
            page.wait_for_timeout(2500)
            if "login" in page.url:
                raise AuthError("Сессия TikTok истекла — привяжите аккаунт заново")

            report(25, "загружаю файл…")
            file_input = page.locator('input[type="file"]').first
            file_input.set_input_files(str(src))
            page.wait_for_timeout(3000)

            # ждём окончания обработки (кнопка Post становится активной)
            report(45, "обработка видео…")
            deadline = time.time() + 600
            post_button = None
            while time.time() < deadline:
                if cancel_event is not None and cancel_event.is_set():
                    raise RuntimeError("Публикация отменена")
                for selector in ('button:has-text("Post")', 'button:has-text("Опубликовать")',
                                 'div[role="button"]:has-text("Post")'):
                    locator = page.locator(selector)
                    if locator.count() and locator.first.is_enabled():
                        post_button = locator.first
                        break
                if post_button is not None:
                    break
                page.wait_for_timeout(1500)
            if post_button is None:
                raise RuntimeError("Не дождались готовности TikTok Studio (кнопка Post не активна)")

            # описание
            report(60, "заполняю описание…")
            caption = metadata.full_caption
            for selector in ('.public-DraftEditor-content', 'div[contenteditable="true"]',
                             'textarea[placeholder*="историю"]'):
                locator = page.locator(selector)
                if locator.count():
                    try:
                        locator.first.click()
                        locator.first.type(caption, delay=8)
                        break
                    except Exception:
                        continue

            if metadata.draft:
                report(75, "сохраняю черновик…")
                for selector in ('button:has-text("Drafts")', 'button:has-text("Черновики")'):
                    locator = page.locator(selector)
                    if locator.count():
                        locator.first.click()
                        page.wait_for_timeout(3000)
                        return TikTokResult(status="draft", message="Сохранено в черновики TikTok")
                raise RuntimeError("Кнопка «Черновики» не найдена")

            report(80, "публикую…")
            post_button.click()
            report(90, "ожидание подтверждения…")
            page.wait_for_timeout(8000)
            url = ""
            try:
                page.goto("https://www.tiktok.com/@me", timeout=45_000)
                page.wait_for_timeout(2500)
                links = page.locator('a[href*="/video/"]')
                if links.count():
                    href = links.first.get_attribute("href") or ""
                    if href.startswith("http"):
                        url = href
                    elif href:
                        url = f"https://www.tiktok.com{href}"
            except Exception:
                LOG.debug("Не удалось получить ссылку на видео", exc_info=True)

            context.storage_state(path=str(cookies))  # обновляем cookies
            report(100, "готово")
            return TikTokResult(url=url, status="posted",
                                message="Видео опубликовано в TikTok" if not url else "")
        except Exception as exc:
            LOG.exception("Публикация в TikTok не удалась")
            if isinstance(exc, (AuthError, RuntimeError)):
                raise
            raise RuntimeError(f"Ошибка TikTok: {exc}") from exc
        finally:
            try:
                browser.close()
            except Exception:
                pass


def upload_video_legacy(path: str | Path, metadata: TikTokMetadata, *,
                        cookies_path: str | Path,
                        progress_cb: Optional[Callable[[int, str], None]] = None
                        ) -> TikTokResult:
    """Fallback через пакет ``tiktok-uploader`` (selenium).

    Args:
        path: Видео.
        metadata: Подпись/теги.
        cookies_path: Файл cookies (формат для tiktok-uploader: Netscape или JSON).
        progress_cb: Прогресс.

    Returns:
        :class:`TikTokResult`.

    Raises:
        RuntimeError: если пакет не установлен или публикация не удалась.
    """
    try:
        from tiktok_uploader.upload import upload_video as legacy_upload
    except Exception as exc:  # pragma: no cover
        raise RuntimeError("Пакет tiktok-uploader не установлен") from exc

    if progress_cb:
        progress_cb(30, "загрузка через selenium…")
    try:
        result = legacy_upload(
            filename=str(path),
            description=metadata.full_caption,
            cookies=str(cookies_path),
            headless=False,
        )
        if progress_cb:
            progress_cb(100, "готово")
        return TikTokResult(status="posted", message=str(result or "Опубликовано"))
    except Exception as exc:
        raise RuntimeError(f"tiktok-uploader: {exc}") from exc


class TikTokUploadTask(QThread):
    """QThread-обёртка публикации в TikTok.

    Сигналы:
        progress(int, str): Процент и статус.
        finished_ok(object): :class:`TikTokResult`.
        failed(str): Ошибка.
        canceled(): Отменено.
    """

    progress = pyqtSignal(int, str)
    finished_ok = pyqtSignal(object)
    failed = pyqtSignal(str)
    canceled = pyqtSignal()

    def __init__(self, path: str | Path, metadata: TikTokMetadata,
                 account: Optional[Account] = None,
                 parent: Optional[QObject] = None) -> None:
        """Args:
            path: Видеофайл.
            metadata: Метаданные.
            account: Аккаунт.
            parent: Родитель.
        """
        super().__init__(parent)
        self._path = Path(path)
        self._metadata = metadata
        self._account = account
        self._cancel = threading.Event()

    def cancel(self) -> None:
        """Отменить публикацию."""
        self._cancel.set()

    def run(self) -> None:  # noqa: D401
        """Выполнить публикацию в фоне."""
        try:
            result = upload_video_playwright(
                self._path, self._metadata, account=self._account,
                progress_cb=lambda p, s: self.progress.emit(p, s),
                cancel_event=self._cancel,
            )
            self.finished_ok.emit(result)
        except Exception as exc:
            if self._cancel.is_set():
                self.canceled.emit()
            else:
                LOG.exception("TikTok-задача упала")
                self.failed.emit(str(exc))
