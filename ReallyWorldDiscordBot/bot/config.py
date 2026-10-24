"""Application configuration and Discord OAuth2 Invite URL builder (supports source & PyInstaller .exe)."""
from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlencode

from dotenv import load_dotenv

IS_FROZEN = getattr(sys, "frozen", False)
BUNDLE_DIR = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
APP_DIR = Path(sys.executable).resolve().parent if IS_FROZEN else Path(__file__).resolve().parent.parent

ENV_FILE = APP_DIR / ".env"
load_dotenv(ENV_FILE, override=True)

DEFAULT_CLIENT_ID = "1563651952506576918"
DEFAULT_OWNER_ID = 1381255368650526861
DEFAULT_OWNER_USERNAME = "robik0277"


def build_invite_url(client_id: int | str, permissions: int = 8) -> str:
    """Build a direct Discord OAuth2 bot + slash-commands invite link."""
    cid = str(client_id).strip()
    if not cid.isdigit():
        raise ValueError("Application ID (CLIENT_ID) должен состоять только из цифр.")
    query = urlencode(
        {
            "client_id": cid,
            "permissions": str(permissions),
            "scope": "bot applications.commands",
        }
    )
    return f"https://discord.com/oauth2/authorize?{query}"


def save_env_settings(
    token: str,
    client_id: str = DEFAULT_CLIENT_ID,
    dev_guild_id: str = "",
    minecraft_ip: str = "mc.reallyworld.me",
    auto_avatar: bool = True,
) -> Path:
    """Persist settings into .env next to the executable / project root."""
    lines = [
        f"DISCORD_TOKEN={token.strip()}",
        f"CLIENT_ID={client_id.strip() or DEFAULT_CLIENT_ID}",
        f"OWNER_ID={DEFAULT_OWNER_ID}",
        f"OWNER_USERNAME={DEFAULT_OWNER_USERNAME}",
        f"DEV_GUILD_ID={dev_guild_id.strip()}",
        "SERVER_NAME=ReallyWorld КП",
        f"MINECRAFT_SERVER_IP={minecraft_ip.strip() or 'mc.reallyworld.me'}",
        f"AUTO_SET_KP_AVATAR={'true' if auto_avatar else 'false'}",
        "",
    ]
    ENV_FILE.write_text("\n".join(lines), encoding="utf-8")
    load_dotenv(ENV_FILE, override=True)
    return ENV_FILE


@dataclass(slots=True)
class BotConfig:
    token: str
    client_id: str = DEFAULT_CLIENT_ID
    owner_id: int = DEFAULT_OWNER_ID
    owner_username: str = DEFAULT_OWNER_USERNAME
    dev_guild_id: int | None = None
    project_name: str = "ReallyWorld | Команда Проекта"
    minecraft_ip: str = "mc.reallyworld.me"
    minecraft_versions: str = "1.21.4 — 1.21.11"
    auto_set_kp_avatar: bool = True
    db_path: Path = field(default_factory=lambda: APP_DIR / "data" / "reallyworld_bot.db")
    avatar_path: Path = field(default_factory=lambda: BUNDLE_DIR / "assets" / "avatar_kp_beach.png")
    raw_json: dict = field(default_factory=dict)

    @classmethod
    def load(cls) -> "BotConfig":
        load_dotenv(ENV_FILE, override=True)
        cfg_file = APP_DIR / "config.json"
        if not cfg_file.exists():
            cfg_file = BUNDLE_DIR / "config.json"
        raw = json.loads(cfg_file.read_text(encoding="utf-8")) if cfg_file.exists() else {}

        owner_raw = os.getenv("OWNER_ID", str(raw.get("owner_id", DEFAULT_OWNER_ID))).strip()
        owner_id = int(owner_raw) if owner_raw.isdigit() else DEFAULT_OWNER_ID
        owner_username = os.getenv("OWNER_USERNAME", raw.get("owner_username", DEFAULT_OWNER_USERNAME)).strip()

        dev_guild_raw = os.getenv("DEV_GUILD_ID", "").strip()
        dev_guild_id = int(dev_guild_raw) if dev_guild_raw.isdigit() else None

        auto_avatar = os.getenv("AUTO_SET_KP_AVATAR", "true").strip().lower() in {"1", "true", "yes", "on"}

        avatar_candidate = APP_DIR / "assets" / "avatar_kp_beach.png"
        if not avatar_candidate.exists():
            avatar_candidate = BUNDLE_DIR / "assets" / "avatar_kp_beach.png"

        return cls(
            token=os.getenv("DISCORD_TOKEN", "").strip(),
            client_id=os.getenv("CLIENT_ID", str(raw.get("client_id", DEFAULT_CLIENT_ID))).strip() or DEFAULT_CLIENT_ID,
            owner_id=owner_id,
            owner_username=owner_username or DEFAULT_OWNER_USERNAME,
            dev_guild_id=dev_guild_id,
            project_name=os.getenv("SERVER_NAME", raw.get("project_name", "ReallyWorld | Команда Проекта")),
            minecraft_ip=os.getenv("MINECRAFT_SERVER_IP", raw.get("minecraft_ip", "mc.reallyworld.me")),
            minecraft_versions=raw.get("minecraft_versions", "1.21.4 — 1.21.11"),
            auto_set_kp_avatar=auto_avatar,
            avatar_path=avatar_candidate,
            raw_json=raw,
        )
