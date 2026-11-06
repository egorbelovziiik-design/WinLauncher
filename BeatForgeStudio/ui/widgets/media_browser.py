"""Браузер медиа: недавние файлы, загрузки, проекты, кэш треков.

Виджет используется во вкладке видеоредактора (панель «Медиа») и в
аудиоредакторе. Умеет показывать файлы с кэшем миниатюр, принимать
drag & drop и эмитить выбор.

Файлы можно перетаскивать прямо на таймлайн: MIME-тип
``application/x-beatforge-track`` содержит путь.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Iterable, List, Optional

from PyQt6.QtCore import QMimeData, QPoint, QSize, Qt, QUrl, pyqtSignal
from PyQt6.QtGui import QDrag, QIcon, QPixmap
from PyQt6.QtWidgets import (QAbstractItemView, QFileDialog, QHBoxLayout, QLabel,
                             QLineEdit, QListWidget, QListWidgetItem, QPushButton,
                             QTabWidget, QVBoxLayout, QWidget)

from core.video_engine import IMAGE_EXTENSIONS, MEDIA_EXTENSIONS, VIDEO_EXTENSIONS
from ui.theme import color
from ui.widgets.cards import placeholder_cover
from utils.cache_db import get_db

LOG = logging.getLogger(__name__)

MIME_TRACK = "application/x-beatforge-track"


class MediaList(QListWidget):
    """Список медиафайлов с drag-исходником для таймлайна."""

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self.setViewMode(QListWidget.ViewMode.IconMode)
        self.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.setMovement(QListWidget.Movement.Static)
        self.setSpacing(6)
        self.setIconSize(QSize(112, 64))
        self.setGridSize(QSize(128, 100))
        self.setWordWrap(True)
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.setDragEnabled(True)

    def startDrag(self, supported_actions) -> None:  # noqa: N802
        item = self.currentItem()
        if item is None:
            return
        path = item.data(Qt.ItemDataRole.UserRole)
        if not path:
            return
        mime = QMimeData()
        mime.setData(MIME_TRACK, str(path).encode("utf-8"))
        if Path(path).exists():
            mime.setUrls([QUrl.fromLocalFile(str(path))])
        drag = QDrag(self)
        drag.setMimeData(mime)
        icon = item.icon()
        if not icon.isNull():
            drag.setPixmap(icon.pixmap(96, 54))
        drag.exec(Qt.DropAction.CopyAction)


class MediaBrowser(QWidget):
    """Панель выбора медиа: файлы на диске, загрузки, кэш треков.

    Сигналы:
        fileDoubleClicked(str): двойной клик по файлу (добавить на таймлайн).
        addRequested(str): кнопка «＋» на файле.
        importRequested(): запрос импорта файлов с диска.
    """

    fileDoubleClicked = pyqtSignal(str)
    addRequested = pyqtSignal(str)
    importRequested = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None,
                 media_kind: str = "video") -> None:
        """Args:
            parent: Родитель.
            media_kind: ``video`` (видео+картинки+аудио) или ``audio``.
        """
        super().__init__(parent)
        self._kind = media_kind
        self._paths: List[Path] = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        top = QHBoxLayout()
        self.import_button = QPushButton("⊞ Импорт")
        self.import_button.setObjectName("Ghost")
        self.import_button.clicked.connect(self._pick_files)
        top.addWidget(self.import_button)
        self.refresh_button = QPushButton("⟳")
        self.refresh_button.setObjectName("Ghost")
        self.refresh_button.setFixedWidth(34)
        self.refresh_button.setToolTip("Обновить список")
        self.refresh_button.clicked.connect(self.refresh)
        top.addWidget(self.refresh_button)
        top.addStretch(1)
        layout.addLayout(top)

        self.search = QLineEdit()
        self.search.setPlaceholderText("Фильтр по имени…")
        self.search.textChanged.connect(self._apply_filter)
        layout.addWidget(self.search)

        self.tabs = QTabWidget()
        self.tabs.setDocumentMode(True)
        # в узкой панели вкладки не должны обрезаться: elide + стрелки прокрутки
        self.tabs.setElideMode(Qt.TextElideMode.ElideRight)
        self.tabs.setUsesScrollButtons(True)
        self.tabs.setObjectName("MediaTabs")
        self.files_list = MediaList()
        self.files_list.itemDoubleClicked.connect(self._on_double_click)
        self.downloads_list = MediaList()
        self.downloads_list.itemDoubleClicked.connect(self._on_double_click)
        self.cache_list = MediaList()
        self.cache_list.itemDoubleClicked.connect(self._on_double_click)
        self.tabs.addTab(self.files_list, "Файлы")
        self.tabs.addTab(self.downloads_list, "Загрузки")
        self.tabs.addTab(self.cache_list, "Из поиска")
        layout.addWidget(self.tabs, 1)

        self.hint = QLabel("Перетащите файл в окно или дважды кликните по элементу")
        self.hint.setObjectName("Tiny")
        self.hint.setWordWrap(True)
        layout.addWidget(self.hint)

        self.refresh()

     # наполнение
    def set_paths(self, paths: Iterable[str | Path]) -> None:
        """Показать конкретный список файлов (например, из проекта)."""
        self._paths = [Path(p) for p in paths if Path(p).exists()]
        self._fill(self.files_list, self._paths)

    def refresh(self) -> None:
        """Перечитать загрузки и кэш треков из базы."""
        self._load_downloads()
        self._load_cache()
        if not self._paths:
            downloads = Path.home() / "BeatForgeStudio" / "downloads"
            if downloads.exists():
                exts = MEDIA_EXTENSIONS if self._kind == "video" else (
                    ".mp3", ".wav", ".flac", ".ogg", ".m4a", ".opus")
                self._paths = sorted(
                    (p for p in downloads.rglob("*") if p.suffix.lower() in exts),
                    key=lambda p: p.stat().st_mtime, reverse=True)[:120]
                self._fill(self.files_list, self._paths)

    def _load_downloads(self) -> None:
        try:
            rows = get_db().downloads(60)
            paths = [Path(r["target_path"]) for r in rows if r.get("target_path")
                     and Path(r["target_path"]).exists()]
            self._fill(self.downloads_list, paths)
        except Exception:
            LOG.debug("Не удалось загрузить историю загрузок", exc_info=True)

    def _load_cache(self) -> None:
        try:
            rows = get_db().recent_tracks(80, downloaded_only=True)
            paths = [Path(r.file_path) for r in rows if r.file_path and Path(r.file_path).exists()]
            self._fill(self.cache_list, paths)
        except Exception:
            LOG.debug("Не удалось загрузить кэш треков", exc_info=True)

    def _fill(self, target: MediaList, paths: Iterable[Path]) -> None:
        """Заполнить список элементами с иконками."""
        target.clear()
        for path in paths:
            item = QListWidgetItem(self._icon_for(path), path.name)
            item.setData(Qt.ItemDataRole.UserRole, str(path))
            item.setToolTip(f"{path}\n{path.stat().st_size / 1048576:.1f} МБ")
            target.addItem(item)
        if target.count() == 0:
            placeholder = QListWidgetItem("Пусто")
            placeholder.setFlags(Qt.ItemFlag.NoItemFlags)
            target.addItem(placeholder)
        self._apply_filter(self.search.text())

    def _icon_for(self, path: Path) -> QIcon:
        """Иконка файла: миниатюра видео/картинки или плейсхолдер."""
        suffix = path.suffix.lower()
        if suffix in IMAGE_EXTENSIONS:
            pix = QPixmap(str(path))
            if not pix.isNull():
                return QIcon(pix.scaled(112, 64, Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                        Qt.TransformationMode.SmoothTransformation))
        if suffix in VIDEO_EXTENSIONS:
            from core.video_engine import VideoEngine

            thumb = VideoEngine().thumbnail(path, 0.5, 160)
            if thumb:
                pix = QPixmap(str(thumb))
                if not pix.isNull():
                    return QIcon(pix)
        return QIcon(placeholder_cover(path.stem, 64))

    def _apply_filter(self, text: str) -> None:
        needle = (text or "").lower()
        for list_widget in (self.files_list, self.downloads_list, self.cache_list):
            for i in range(list_widget.count()):
                item = list_widget.item(i)
                if not (item.flags() & Qt.ItemFlag.ItemIsEnabled):
                    continue
                item.setHidden(bool(needle) and needle not in item.text().lower())

     # события
    def _on_double_click(self, item: QListWidgetItem) -> None:
        path = item.data(Qt.ItemDataRole.UserRole)
        if path:
            self.fileDoubleClicked.emit(path)

    def _pick_files(self) -> None:
        if self._kind == "audio":
            filters = "Аудио (*.wav *.mp3 *.flac *.ogg *.m4a *.opus *.aac);;Все файлы (*)"
        else:
            filters = ("Медиа (*.mp4 *.mov *.mkv *.webm *.avi *.gif *.png *.jpg *.jpeg "
                       "*.wav *.mp3 *.flac *.m4a);;Все файлы (*)")
        files, _ = QFileDialog.getOpenFileNames(self, "Импорт медиа", str(Path.home()), filters)
        if files:
            self._paths = [Path(f) for f in files] + self._paths
            self._fill(self.files_list, self._paths[:200])
            for f in files:
                self.addRequested.emit(f)
