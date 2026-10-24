"""Multi-Bot Profile Manager & Token Client-ID Decoder."""
from __future__ import annotations

import base64
import json
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path

from bot.config import (
    APP_DIR,
    BUNDLE_DIR,
    DEFAULT_CLIENT_ID,
    DEFAULT_OWNER_ID,
    DEFAULT_OWNER_USERNAME,
    BotConfig,
)

ALL_COG_KEYS = [
    "bot.cogs.owner",
    "bot.cogs.kp_staff",
    "bot.cogs.tickets",
    "bot.cogs.moderation",
    "bot.cogs.community",
]

PERMISSION_PRESETS: dict[str, int] = {
    "Администратор (все права)": 8,
    "Модерация + Тикеты + Роли": 1099780066422,
    "Базовый бот + Слэш-команды": 277025770560,
}


def extract_client_id_from_token(token: str) -> str | None:
    """Decode the Discord Application/Bot ID from the first segment of a Bot Token."""
    raw = (token or "").strip()
    if "." not in raw:
        return None
    first_part = raw.split(".", 1)[0].strip()
    if not first_part:
        return None
    padding = "=" * ((4 - len(first_part) % 4) % 4)
    try:
        decoded = base64.urlsafe_b64decode(first_part + padding).decode("utf-8", errors="strict").strip()
        if decoded.isdigit() and 15 <= len(decoded) <= 22:
            return decoded
    except Exception:
        return None
    return None


@dataclass
class BotProfile:
    profile_id: str
    name: str
    token: str = ""
    client_id: str = ""
    owner_id: int = DEFAULT_OWNER_ID
    owner_username: str = DEFAULT_OWNER_USERNAME
    dev_guild_id: str = ""
    project_name: str = "ReallyWorld | Команда Проекта"
    minecraft_ip: str = "mc.reallyworld.me"
    status_type: str = "playing"  # playing | watching | listening
    status_text: str = "mc.reallyworld.me | /help"
    invite_perm_label: str = "Администратор (все права)"
    auto_set_avatar: bool = False
    avatar_file: str = ""
    enabled_cogs: list[str] = field(default_factory=lambda: list(ALL_COG_KEYS))

    @property
    def resolved_client_id(self) -> str:
        cid = "".join(c for c in (self.client_id or "") if c.isdigit())
        if cid:
            return cid
        from_tok = extract_client_id_from_token(self.token)
        return from_tok or ""

    @property
    def invite_permissions(self) -> int:
        return PERMISSION_PRESETS.get(self.invite_perm_label, 8)

    def to_bot_config(self, base_data_dir: Path | None = None) -> BotConfig:
        data_root = base_data_dir or (APP_DIR / "data")
        data_root.mkdir(parents=True, exist_ok=True)

        cfg_file = APP_DIR / "config.json"
        if not cfg_file.exists():
            cfg_file = BUNDLE_DIR / "config.json"
        raw = json.loads(cfg_file.read_text(encoding="utf-8")) if cfg_file.exists() else {}

        dev_gid = int(self.dev_guild_id.strip()) if self.dev_guild_id.strip().isdigit() else None

        if self.avatar_file and Path(self.avatar_file).exists():
            av_path = Path(self.avatar_file)
        else:
            cand = APP_DIR / "assets" / "avatar_kp_beach.png"
            av_path = cand if cand.exists() else (BUNDLE_DIR / "assets" / "avatar_kp_beach.png")

        return BotConfig(
            token=self.token.strip(),
            client_id=self.resolved_client_id,
            owner_id=int(self.owner_id) if str(self.owner_id).isdigit() else DEFAULT_OWNER_ID,
            owner_username=self.owner_username.strip() or DEFAULT_OWNER_USERNAME,
            dev_guild_id=dev_gid,
            project_name=self.project_name.strip() or self.name,
            minecraft_ip=self.minecraft_ip.strip() or "mc.reallyworld.me",
            auto_set_kp_avatar=self.auto_set_avatar,
            db_path=data_root / f"bot_{self.profile_id}.db",
            avatar_path=av_path,
            raw_json=raw,
        )


class ProfileStore:
    def __init__(self, store_path: Path | None = None) -> None:
        self.store_path = store_path or (APP_DIR / "data" / "bots_profiles.json")
        self.profiles: list[BotProfile] = []

    def load(self) -> list[BotProfile]:
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        if self.store_path.exists():
            try:
                raw_list = json.loads(self.store_path.read_text(encoding="utf-8"))
                self.profiles = [BotProfile(**item) for item in raw_list if isinstance(item, dict)]
            except Exception:
                self.profiles = []

        if not self.profiles:
            default_bot = BotProfile(
                profile_id="rw_kp_main",
                name="ReallyWorld КП (Основной)",
                token="",
                client_id=DEFAULT_CLIENT_ID,
                owner_id=DEFAULT_OWNER_ID,
                owner_username=DEFAULT_OWNER_USERNAME,
                project_name="ReallyWorld | Команда Проекта",
                minecraft_ip="mc.reallyworld.me",
                status_type="playing",
                status_text="mc.reallyworld.me | /help",
                auto_set_avatar=True,
                enabled_cogs=list(ALL_COG_KEYS),
            )
            self.profiles = [default_bot]
            self.save()
        return self.profiles

    def save(self) -> None:
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.store_path.with_suffix(".tmp")
        tmp.write_text(
            json.dumps([asdict(p) for p in self.profiles], ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        tmp.replace(self.store_path)

    def create_new(self, name: str = "Новый Discord-Бот") -> BotProfile:
        short_id = uuid.uuid4().hex[:8]
        prof = BotProfile(
            profile_id=f"bot_{short_id}",
            name=name,
            token="",
            client_id="",
            owner_id=DEFAULT_OWNER_ID,
            owner_username=DEFAULT_OWNER_USERNAME,
            project_name=name,
            minecraft_ip="mc.reallyworld.me",
            status_type="playing",
            status_text="/help",
            auto_set_avatar=False,
            enabled_cogs=list(ALL_COG_KEYS),
        )
        self.profiles.append(prof)
        self.save()
        return prof

    def duplicate(self, source: BotProfile) -> BotProfile:
        short_id = uuid.uuid4().hex[:8]
        copy_prof = BotProfile(
            profile_id=f"bot_{short_id}",
            name=f"{source.name} (Копия)",
            token="",
            client_id="",
            owner_id=source.owner_id,
            owner_username=source.owner_username,
            dev_guild_id=source.dev_guild_id,
            project_name=source.project_name,
            minecraft_ip=source.minecraft_ip,
            status_type=source.status_type,
            status_text=source.status_text,
            invite_perm_label=source.invite_perm_label,
            auto_set_avatar=source.auto_set_avatar,
            avatar_file=source.avatar_file,
            enabled_cogs=list(source.enabled_cogs),
        )
        self.profiles.append(copy_prof)
        self.save()
        return copy_prof

    def delete(self, profile_id: str) -> bool:
        before = len(self.profiles)
        self.profiles = [p for p in self.profiles if p.profile_id != profile_id]
        if not self.profiles:
            self.create_new("Мой Бот #1")
        else:
            self.save()
        return len(self.profiles) < before
