"""Конфигурация BeatForge Studio.

Настройки хранятся в ``~/.beatforge_studio/config.json``. Модуль не зависит от
Qt, поэтому его можно использовать в фоновых потоках и тестах.

Пример::

    from utils.config import get_config
    cfg = get_config()
    cfg.video.fps = 60
    cfg.save()
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
from dataclasses import asdict, dataclass, field, fields, is_dataclass
from pathlib import Path
from typing import Any, Dict, Optional, Type, TypeVar

LOG = logging.getLogger(__name__)

APP_NAME = "BeatForge Studio"
CONFIG_VERSION = 1

APP_DIR: Path = Path.home() / ".beatforge_studio"
CONFIG_FILE: Path = APP_DIR / "config.json"
SESSION_FILE: Path = APP_DIR / "session.json"

T = TypeVar("T")


# --------------------------------------------------------------------------
#  Секции конфигурации
# --------------------------------------------------------------------------
@dataclass
class PathsSettings:
    """Каталоги приложения."""

    cache_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "cache")
    downloads_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "downloads")
    projects_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "projects")
    exports_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "exports")
    thumbnails_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "thumbnails")
    db_path: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "studio.db")
    models_dir: Path = field(default_factory=lambda: Path.home() / "BeatForgeStudio" / "models")


@dataclass
class AudioSettings:
    """Параметры аудиодвижка."""

    sample_rate: int = 44100
    block_size: int = 1024
    channels: int = 2
    master_volume: float = 0.8
    speed: float = 1.0
    preserve_pitch: bool = True
    seek_crossfade_ms: int = 10
    max_audio_tracks: int = 8
    preview_seconds: int = 30
    device: Optional[str] = None
    latency: str = "high"


@dataclass
class VideoSettings:
    """Параметры видеодвижка и рендера."""

    width: int = 1920
    height: int = 1080
    fps: int = 30
    preview_width: int = 854
    preview_height: int = 480
    preview_fps: int = 15
    bitrate_mbps: float = 12.0
    codec: str = "libx264"
    audio_codec: str = "aac"
    audio_bitrate_kbps: int = 192
    crf: int = 20
    hwaccel: bool = False
    ffmpeg_path: str = ""
    ffprobe_path: str = ""
    max_video_tracks: int = 4
    max_audio_tracks: int = 4


@dataclass
class UiSettings:
    """Внешний вид и поведение интерфейса."""

    theme: str = "dark"  # dark | light | amoled
    language: str = "ru"  # ru | en
    font_family: str = "Inter"
    font_size: int = 11
    accent: str = "#FF6600"
    accent2: str = "#00BFFF"
    animations: bool = True
    animation_ms: int = 300
    show_toasts: bool = True
    restore_session: bool = True
    window_width: int = 1440
    window_height: int = 900
    sidebar_collapsed: bool = False


@dataclass
class SearchSettings:
    """Поиск музыки и битов."""

    results_limit: int = 15
    default_genre: str = "Trap"
    default_source: str = "youtube"  # youtube | soundcloud
    max_duration_sec: int = 0
    cache_results: bool = True
    proxy: Optional[str] = None
    cookies_browser: str = ""  # chrome | firefox | edge
    preview_seconds: int = 30


@dataclass
class ExportSettings:
    """Экспорт аудио/видео и загрузка ссылкой."""

    audio_format: str = "mp3"
    audio_bitrate: int = 320
    wav_bit_depth: int = 16
    video_format: str = "mp4"
    video_preset: str = "youtube_1080"
    resolution: str = "1080p"
    fps: int = 30
    normalize_audio: bool = True
    normalize_target_db: float = -1.0
    upload_service: str = "fileio"  # fileio | transfer
    upload_ttl: str = "1d"
    auto_copy_link: bool = True
    filename_template: str = "BeatForge_{kind}_{date}_{time}"
    open_folder_after_export: bool = True


@dataclass
class PublishSettings:
    """Публикация в соцсетях."""

    default_platform: str = "youtube"
    default_privacy: str = "public"  # public | unlisted | private
    default_tags: str = "beat, music, BeatForgeStudio"
    auto_adapt_tiktok: bool = True
    tiktok_music_slider: int = 50  # 0..100 (громкость оригинального звука)
    upload_chunk_size: int = 8 * 1024 * 1024
    youtube_client_secrets: str = ""
    tiktok_username: str = ""
    tiktok_headless: bool = False


@dataclass
class SubtitleSettings:
    """Субтитры и Whisper."""

    whisper_model: str = "base"
    whisper_device: str = "auto"
    whisper_language: str = "ru"
    font_family: str = "Inter"
    font_size: int = 54
    primary_color: str = "#FFFFFF"
    outline_color: str = "#000000"
    outline_width: int = 3
    position: str = "bottom"  # top | middle | bottom
    margin_v: int = 80


@dataclass
class HotkeySettings:
    """Горячие клавиши (строки в формате Qt: ``Ctrl+Shift+E``)."""

    play_pause: str = "Space"
    stop: str = "Ctrl+."
    export: str = "Ctrl+E"
    save: str = "Ctrl+S"
    open: str = "Ctrl+O"
    new: str = "Ctrl+N"
    undo: str = "Ctrl+Z"
    redo: str = "Ctrl+Shift+Z"
    split: str = "Ctrl+B"
    delete: str = "Delete"
    search_focus: str = "Ctrl+F"
    publish: str = "Ctrl+Shift+P"
    fit_timeline: str = "F"


@dataclass
class AppConfig:
    """Корневой объект конфигурации приложения."""

    version: int = CONFIG_VERSION
    paths: PathsSettings = field(default_factory=PathsSettings)
    audio: AudioSettings = field(default_factory=AudioSettings)
    video: VideoSettings = field(default_factory=VideoSettings)
    ui: UiSettings = field(default_factory=UiSettings)
    search: SearchSettings = field(default_factory=SearchSettings)
    export: ExportSettings = field(default_factory=ExportSettings)
    publish: PublishSettings = field(default_factory=PublishSettings)
    subtitles: SubtitleSettings = field(default_factory=SubtitleSettings)
    hotkeys: HotkeySettings = field(default_factory=HotkeySettings)

    # ------------------------------------------------------------ сериализация
    def to_dict(self) -> Dict[str, Any]:
        """Сериализовать конфиг в JSON-совместимый словарь."""

        def enc(value: Any) -> Any:
            if isinstance(value, Path):
                return str(value)
            if is_dataclass(value) and not isinstance(value, type):
                return {f.name: enc(getattr(value, f.name)) for f in fields(value)}
            if isinstance(value, dict):
                return {k: enc(v) for k, v in value.items()}
            if isinstance(value, (list, tuple)):
                return [enc(v) for v in value]
            return value

        return enc(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "AppConfig":
        """Собрать конфиг из словаря, игнорируя неизвестные/битые поля."""
        cfg = cls()
        if not isinstance(data, dict):
            return cfg
        for f in fields(cls):
            if f.name not in data:
                continue
            value = data[f.name]
            current = getattr(cfg, f.name)
            if is_dataclass(current) and isinstance(value, dict):
                setattr(cfg, f.name, _build(type(current), value))
            elif f.name == "version":
                try:
                    cfg.version = int(value)
                except (TypeError, ValueError):
                    LOG.warning("Некорректная версия конфига: %r", value)
        return cfg

    # ------------------------------------------------------------ файл
    def save(self, path: Optional[Path] = None) -> None:
        """Атомарно сохранить конфиг (через временный файл)."""
        target = Path(path) if path else CONFIG_FILE
        target.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(self.to_dict(), ensure_ascii=False, indent=2)
        fd, tmp = tempfile.mkstemp(dir=str(target.parent), suffix=".tmp")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as fh:
                fh.write(payload)
            os.replace(tmp, target)
        except OSError:
            LOG.exception("Не удалось сохранить конфиг в %s", target)
            try:
                os.unlink(tmp)
            except OSError:
                pass
            raise

    @classmethod
    def load(cls, path: Optional[Path] = None) -> "AppConfig":
        """Загрузить конфиг; при ошибке вернуть дефолтный и сделать ``.bak``."""
        src = Path(path) if path else CONFIG_FILE
        if not src.exists():
            return cls()
        try:
            with src.open("r", encoding="utf-8") as fh:
                return cls.from_dict(json.load(fh))
        except (json.JSONDecodeError, OSError) as exc:
            LOG.error("Конфиг повреждён (%s), беру значения по умолчанию", exc)
            try:
                src.replace(src.with_suffix(src.suffix + ".bak"))
            except OSError:
                pass
            return cls()

    def ensure_dirs(self) -> None:
        """Создать все каталоги из конфига."""
        APP_DIR.mkdir(parents=True, exist_ok=True)
        for p in (self.paths.cache_dir, self.paths.downloads_dir, self.paths.projects_dir,
                  self.paths.exports_dir, self.paths.thumbnails_dir,
                  self.paths.models_dir, self.paths.db_path.parent):
            Path(p).mkdir(parents=True, exist_ok=True)

    def reset(self) -> None:
        """Сбросить значения всех секций к дефолтным (in-place)."""
        fresh = AppConfig()
        for f in fields(AppConfig):
            setattr(self, f.name, getattr(fresh, f.name))

    # ------------------------------------------------------------ утилиты
    def as_json(self) -> str:
        """Конфиг как pretty-JSON (для экрана настроек «показать JSON»)."""
        return json.dumps(self.to_dict(), ensure_ascii=False, indent=2)


def _build(cls: Type[T], data: Dict[str, Any]) -> T:
    """Собрать даталкассу из словаря с приведением типов к дефолтным."""
    defaults = cls()  # type: ignore[call-arg]
    kwargs: Dict[str, Any] = {}
    valid = {f.name for f in fields(cls)}  # type: ignore[arg-type]
    for key, value in data.items():
        if key not in valid:
            LOG.debug("Пропускаю неизвестный ключ: %s.%s", cls.__name__, key)
            continue
        default = getattr(defaults, key)
        try:
            if isinstance(default, Path):
                kwargs[key] = Path(str(value)).expanduser()
            elif isinstance(default, bool):
                kwargs[key] = bool(value)
            elif isinstance(default, int):
                kwargs[key] = int(value)
            elif isinstance(default, float):
                kwargs[key] = float(value)
            elif default is None:
                kwargs[key] = value
            elif isinstance(default, str):
                kwargs[key] = str(value)
            else:
                kwargs[key] = value
        except (TypeError, ValueError):
            LOG.warning("Плохое значение %s.%s=%r — оставляю дефолт", cls.__name__, key, value)
    return cls(**kwargs)  # type: ignore[return-value]


_config: Optional[AppConfig] = None


def get_config(reload: bool = False) -> AppConfig:
    """Вернуть глобальный конфиг (singleton), при необходимости перечитав файл."""
    global _config
    if _config is None or reload:
        _config = AppConfig.load()
        _config.ensure_dirs()
    return _config


def save_config() -> None:
    """Сохранить глобальный конфиг на диск."""
    get_config().save()


# ---------------------------------------------------------------- QSettings
def qsettings():
    """Вернуть QSettings с общими ключами приложения (ленивый импорт Qt).

    Используется для хранения состояния окна/таблиц, которое логичнее держать
    в системном реестре (Windows) / plist (macOS), а не в JSON.
    """
    from PyQt6.QtCore import QSettings

    return QSettings("BeatForge", "BeatForgeStudio")
