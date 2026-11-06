"""Управление проектами (.bfs): сохранение, загрузка, автосохранение, импорт/экспорт.

Формат ``.bfs`` — JSON с расширением, содержащий:

* ``meta`` — версия, имя, тип (audio/video/mixed), дата, длительность;
* ``audio`` — данные :class:`core.mixer.MixerProject`;
* ``video`` — сцена видеоредактора (дорожки, клипы, эффекты, текст, субтитры);
* ``settings`` — соотношение сторон, fps, громкость мастера и т. п.;
* ``media`` — список внешних файлов (для переноса проекта и поиска пропавших).

Автосохранение: :class:`ProjectManager` держит таймер и пишет ``*.autosave.bfs``
рядом с проектом (или в ``~/.beatforge_studio/autosave``), не мешая основной
работе пользователя.
"""

from __future__ import annotations

import json
import logging
import shutil
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from PyQt6.QtCore import QObject, QTimer, pyqtSignal

from core.mixer import MixerProject
from utils.cache_db import get_db
from utils.config import CONFIG_VERSION, get_config

LOG = logging.getLogger(__name__)

#: Текущая версия формата проекта.
PROJECT_VERSION = 1
#: Расширение файлов проекта.
PROJECT_EXT = ".bfs"
#: Имя файла автосохранения.
AUTOSAVE_SUFFIX = ".autosave" + PROJECT_EXT


class ProjectError(RuntimeError):
    """Ошибка работы с проектом (битый файл, несовместимая версия)."""


@dataclass
class ProjectMeta:
    """Метаданные проекта.

    Attributes:
        name: Имя проекта.
        kind: ``audio`` | ``video`` | ``mixed``.
        path: Путь к файлу (если сохранён).
        duration: Длительность, сек.
        created: Время создания (unix).
        modified: Время последнего сохранения (unix).
        thumbnail: Путь к превью проекта.
        version: Версия формата.
        app_version: Версия конфигурации приложения.
    """

    name: str = "Без названия"
    kind: str = "audio"
    path: Optional[Path] = None
    duration: float = 0.0
    created: float = field(default_factory=time.time)
    modified: float = field(default_factory=time.time)
    thumbnail: str = ""
    version: int = PROJECT_VERSION
    app_version: int = CONFIG_VERSION

    @property
    def display_name(self) -> str:
        """Имя для заголовка окна."""
        return self.name + (" *" if self.path is None else "")

    def to_dict(self) -> Dict[str, Any]:
        """Сериализация метаданных."""
        return {"name": self.name, "kind": self.kind,
                "path": str(self.path) if self.path else "",
                "duration": self.duration, "created": self.created,
                "modified": self.modified, "thumbnail": self.thumbnail,
                "version": self.version, "app_version": self.app_version}


class ProjectData:
    """Полное содержимое проекта (модель для сохранения)."""

    def __init__(self, meta: Optional[ProjectMeta] = None) -> None:
        """Args:
            meta: Метаданные (по умолчанию пустые).
        """
        self.meta = meta or ProjectMeta()
        self.audio: Optional[MixerProject] = None
        self.video: Dict[str, Any] = {}
        self.subtitles: Dict[str, Any] = {}
        self.text_layers: List[Dict[str, Any]] = []
        self.settings: Dict[str, Any] = {
            "width": 1920, "height": 1080, "fps": 30,
            "aspect": "16:9", "master_volume": 0.8, "bpm": None,
            "snap": True, "theme": "dark",
        }
        self.media: List[str] = []
        self.extra: Dict[str, Any] = {}

    # ------------------------------------------------------------ сериализация
    def to_dict(self) -> Dict[str, Any]:
        """Сериализовать проект в словарь JSON."""
        return {
            "meta": self.meta.to_dict(),
            "settings": self.settings,
            "audio": self.audio.to_dict() if self.audio else None,
            "video": self.video,
            "subtitles": self.subtitles,
            "text_layers": self.text_layers,
            "media": self.media,
            "extra": self.extra,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProjectData":
        """Восстановить проект из словаря.

        Raises:
            ProjectError: если формат не поддерживается.
        """
        if not isinstance(data, dict):
            raise ProjectError("Файл проекта повреждён: ожидался JSON-объект")
        meta_raw = data.get("meta") or {}
        version = int(meta_raw.get("version", 0) or 0)
        if version > PROJECT_VERSION:
            raise ProjectError(
                f"Проект создан в более новой версии BeatForge Studio (v{version}). "
                f"Обновите приложение."
            )
        meta = ProjectMeta(
            name=str(meta_raw.get("name", "Без названия")),
            kind=str(meta_raw.get("kind", "audio")),
            path=Path(meta_raw["path"]) if meta_raw.get("path") else None,
            duration=float(meta_raw.get("duration", 0.0)),
            created=float(meta_raw.get("created", time.time())),
            modified=float(meta_raw.get("modified", time.time())),
            thumbnail=str(meta_raw.get("thumbnail", "")),
            version=version or PROJECT_VERSION,
            app_version=int(meta_raw.get("app_version", CONFIG_VERSION)),
        )
        project = cls(meta)
        audio_data = data.get("audio")
        if audio_data:
            project.audio = MixerProject.from_dict(audio_data)
        project.video = data.get("video") or {}
        project.subtitles = data.get("subtitles") or {}
        project.text_layers = data.get("text_layers") or []
        project.settings.update(data.get("settings") or {})
        project.media = list(data.get("media") or [])
        project.extra = data.get("extra") or {}
        return project

    # ------------------------------------------------------------ сервис
    def collect_media(self) -> List[str]:
        """Собрать все используемые медиафайлы (для проверки/переноса)."""
        paths: List[str] = []
        if self.audio:
            for track in self.audio.tracks:
                for clip in track.clips:
                    if clip.path:
                        paths.append(clip.path)
        for item in self.video.get("clips", []) if isinstance(self.video, dict) else []:
            path = item.get("path")
            if path:
                paths.append(str(path))
        paths.extend(self.media)
        unique: List[str] = []
        for path in paths:
            if path not in unique:
                unique.append(path)
        return unique

    def missing_media(self) -> List[str]:
        """Список отсутствующих на диске файлов."""
        return [p for p in self.collect_media() if not Path(p).exists()]


def _autosave_target(project_path: str | Path) -> Path:
    """Путь файла автосохранения для проекта.

    ``Путь/project.bfs`` → ``Путь/project.autosave.bfs`` — суффикс добавляется
    один раз (``Path.with_suffix`` здесь не годится: он затирает расширение).

    Args:
        project_path: Путь проекта.

    Returns:
        Путь автосохранения.
    """
    src = Path(project_path)
    if src.name.endswith(AUTOSAVE_SUFFIX):
        return src
    return src.with_name(f"{src.stem}{AUTOSAVE_SUFFIX}")


class ProjectManager(QObject):
    """Менеджер проектов с автосохранением и списком недавних.

    Сигналы:
        saved(str): Проект сохранён (путь).
        loaded(object): Проект загружен (:class:`ProjectData`).
        autoSaved(str): Автосохранение выполнено.
        dirtyChanged(bool): Изменилось состояние «есть несохранённые правки».
        errorOccurred(str): Ошибка (для QMessageBox).
    """

    saved = pyqtSignal(str)
    loaded = pyqtSignal(object)
    autoSaved = pyqtSignal(str)
    dirtyChanged = pyqtSignal(bool)
    errorOccurred = pyqtSignal(str)

    def __init__(self, parent: Optional[QObject] = None, autosave_interval_ms: int = 120_000) -> None:
        """Args:
            parent: Родитель.
            autosave_interval_ms: Интервал автосохранения (по умолчанию 2 минуты).
        """
        super().__init__(parent)
        self.current: Optional[ProjectData] = None
        self._dirty = False
        self._timer = QTimer(self)
        self._timer.setInterval(max(15_000, int(autosave_interval_ms)))
        self._timer.timeout.connect(self.autosave)
        self._timer.start()
        self._last_autosave: Optional[Path] = None

    # ------------------------------------------------------------ состояние
    @property
    def dirty(self) -> bool:
        """Есть ли несохранённые изменения."""
        return self._dirty

    def mark_dirty(self, dirty: bool = True) -> None:
        """Отметить наличие/отсутствие изменений."""
        if dirty != self._dirty:
            self._dirty = dirty
            self.dirtyChanged.emit(dirty)

    def set_project(self, project: ProjectData) -> None:
        """Назначить текущий проект (без загрузки с диска)."""
        self.current = project
        self.mark_dirty(True)

    def new_project(self, name: str = "Новый проект", kind: str = "audio") -> ProjectData:
        """Создать новый пустой проект.

        Args:
            name: Имя.
            kind: ``audio`` | ``video`` | ``mixed``.

        Returns:
            Новый :class:`ProjectData`.
        """
        project = ProjectData(ProjectMeta(name=name, kind=kind))
        if kind in ("audio", "mixed"):
            project.audio = MixerProject(name)
        self.current = project
        self._dirty = False
        return project

    # ------------------------------------------------------------ сохранение
    def save(self, path: Optional[str | Path] = None, *, silent: bool = False) -> Optional[Path]:
        """Сохранить проект на диск.

        Args:
            path: Путь (по умолчанию файл текущего проекта или ``projects_dir``).
            silent: Не эмитить сигнал ``saved`` (для автосохранения).

        Returns:
            Путь сохранённого файла или None при ошибке.
        """
        if self.current is None:
            return None
        cfg = get_config()
        if path is not None:
            target = Path(path)
        elif self.current.meta.path:
            target = Path(self.current.meta.path)
        else:
            folder = Path(cfg.paths.projects_dir)
            folder.mkdir(parents=True, exist_ok=True)
            target = folder / f"{self.current.meta.name}{PROJECT_EXT}"
        if target.suffix.lower() != PROJECT_EXT:
            target = target.with_suffix(PROJECT_EXT)
        target.parent.mkdir(parents=True, exist_ok=True)

        meta = self.current.meta
        meta.path = target
        meta.name = target.stem
        meta.modified = time.time()
        if self.current.audio is not None:
            meta.duration = self.current.audio.duration
        self.current.media = self.current.collect_media()

        payload = self.current.to_dict()
        tmp = target.with_suffix(target.suffix + ".tmp")
        try:
            tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            tmp.replace(target)
        except OSError as exc:
            LOG.exception("Не удалось сохранить проект %s", target)
            self.errorOccurred.emit(f"Не удалось сохранить проект: {exc}")
            return None
        finally:
            tmp.unlink(missing_ok=True)

        self._dirty = False
        try:
            get_db().remember_project(target, meta.name, meta.kind, meta.duration,
                                      meta.thumbnail)
        except Exception:
            LOG.debug("Не удалось записать проект в базу", exc_info=True)
        if not silent:
            self.saved.emit(str(target))
        LOG.info("Проект сохранён: %s", target)
        return target

    def save_as(self, path: str | Path) -> Optional[Path]:
        """Сохранить проект под новым именем."""
        return self.save(path)

    # ------------------------------------------------------------ загрузка
    def load(self, path: str | Path) -> Optional[ProjectData]:
        """Загрузить проект из файла.

        Args:
            path: Путь к ``.bfs``.

        Returns:
            :class:`ProjectData` или None при ошибке (сигнал ``errorOccurred``).
        """
        src = Path(path)
        if not src.exists():
            self.errorOccurred.emit(f"Файл проекта не найден: {src}")
            return None
        try:
            data = json.loads(src.read_text(encoding="utf-8"))
            project = ProjectData.from_dict(data)
        except ProjectError as exc:
            LOG.error("Проект несовместим: %s", exc)
            self.errorOccurred.emit(str(exc))
            return None
        except (json.JSONDecodeError, OSError) as exc:
            LOG.exception("Не удалось прочитать проект")
            self.errorOccurred.emit(f"Файл проекта повреждён: {exc}")
            return None

        project.meta.path = src
        missing = project.missing_media()
        if missing:
            LOG.warning("В проекте отсутствуют файлы: %s", ", ".join(missing[:5]))
        self.current = project
        self._dirty = False
        try:
            get_db().remember_project(src, project.meta.name, project.meta.kind,
                                      project.meta.duration, project.meta.thumbnail)
        except Exception:
            LOG.debug("Не удалось обновить список недавних", exc_info=True)
        self.loaded.emit(project)
        LOG.info("Проект загружен: %s (медиа: %s, пропущено: %s)",
                 src, len(project.collect_media()), len(missing))
        return project

    # ------------------------------------------------------------ автосохранение
    def autosave(self) -> Optional[Path]:
        """Автосохранение раз в N минут (только если есть изменения).

        Returns:
            Путь к файлу автосохранения или None.
        """
        if self.current is None or not self._dirty:
            return None
        base = self.current.meta.path
        if base:
            target = _autosave_target(base)
        else:
            folder = Path(get_config().paths.projects_dir) / "autosave"
            folder.mkdir(parents=True, exist_ok=True)
            target = folder / f"{self.current.meta.name}_{uuid.uuid4().hex[:6]}{AUTOSAVE_SUFFIX}"
        saved = self.save(target, silent=True)
        if saved:
            self._last_autosave = saved
            self.autoSaved.emit(str(saved))
            LOG.info("Автосохранение: %s", saved)
        return saved

    def autosave_path(self) -> Optional[Path]:
        """Путь последнего автосохранения (если было)."""
        return self._last_autosave

    def has_autosave(self) -> bool:
        """Есть ли файл автосохранения рядом с текущим проектом."""
        if self.current is None or not self.current.meta.path:
            return False
        candidate = _autosave_target(self.current.meta.path)
        return candidate.exists()

    def recover_autosave(self) -> Optional[ProjectData]:
        """Загрузить автосохранение (предложение восстановления при старте)."""
        if self.current is None or not self.current.meta.path:
            return None
        candidate = _autosave_target(self.current.meta.path)
        if not candidate.exists():
            return None
        return self.load(candidate)

    # ------------------------------------------------------------ перенос
    def export_bundle(self, target_dir: str | Path, copy_media: bool = True) -> Path:
        """Экспортировать проект вместе с медиа (для переноса на другой ПК).

        Создаёт папку ``<name>_bundle`` с ``project.bfs`` и подпапкой ``media``;
        пути к медиа переписываются на относительные.

        Args:
            target_dir: Куда создать набор.
            copy_media: Копировать ли файлы (иначе только список путей).

        Returns:
            Путь к папке набора.
        """
        if self.current is None:
            raise ProjectError("Нет открытого проекта")
        name = self.current.meta.name or "project"
        bundle = Path(target_dir) / f"{name}_bundle"
        bundle.mkdir(parents=True, exist_ok=True)
        media_dir = bundle / "media"
        mapping: Dict[str, str] = {}
        if copy_media:
            media_dir.mkdir(exist_ok=True)
            for path in self.current.collect_media():
                src = Path(path)
                if not src.exists():
                    continue
                dst = media_dir / src.name
                counter = 1
                while dst.exists() and dst.stat().st_size != src.stat().st_size:
                    dst = media_dir / f"{src.stem}_{counter}{src.suffix}"
                    counter += 1
                shutil.copy2(src, dst)
                mapping[str(src)] = f"media/{dst.name}"

        payload = self.current.to_dict()
        if copy_media and mapping:
            if self.current.audio:
                for track in self.current.audio.tracks:
                    for clip in track.clips:
                        clip.path = mapping.get(clip.path, clip.path)
            json_text = json.dumps(payload, ensure_ascii=False, indent=2)
            for original, relative in mapping.items():
                json_text = json_text.replace(json.dumps(original)[1:-1],
                                              json.dumps(relative)[1:-1])
        else:
            json_text = json.dumps(payload, ensure_ascii=False, indent=2)
        out = bundle / f"{name}{PROJECT_EXT}"
        out.write_text(json_text, encoding="utf-8")
        LOG.info("Набор проекта создан: %s (медиа: %s)", bundle, len(mapping))
        return bundle

    def import_bundle(self, bundle_dir: str | Path) -> Optional[ProjectData]:
        """Импортировать проект-набор (с восстановлением путей к медиа).

        Args:
            bundle_dir: Папка набора.

        Returns:
            Загруженный проект.
        """
        folder = Path(bundle_dir)
        candidates = list(folder.glob(f"*{PROJECT_EXT}"))
        if not candidates:
            self.errorOccurred.emit("В папке нет файла проекта (.bfs)")
            return None
        project = self.load(candidates[0])
        if project is None:
            return None
        # делаем пути абсолютными относительно папки набора
        for track in (project.audio.tracks if project.audio else []):
            for clip in track.clips:
                candidate = Path(clip.path)
                if not candidate.is_absolute():
                    absolute = folder / candidate
                    if absolute.exists():
                        clip.path = str(absolute)
                        clip.invalidate()
        return project

    # ------------------------------------------------------------ недавние
    def recent(self, limit: int = 12) -> List[Dict[str, Any]]:
        """Недавние проекты (из SQLite)."""
        try:
            return get_db().recent_projects(limit)
        except Exception:
            LOG.debug("Не удалось получить недавние проекты", exc_info=True)
            return []
