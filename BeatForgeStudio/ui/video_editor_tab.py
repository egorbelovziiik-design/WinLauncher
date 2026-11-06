"""Вкладка видеоредактора (🎬): V1–V4 + A1–A4, таймлайн, превью, эффекты.

Композиция окна:

* слева — медиабраузер (загрузки, кэш, файлы);
* в центре — превью кадра (кадры из FFmpeg, safe-area для соцсетей) и таймлайн
  с четырьмя видеодорожками и четырьмя аудиодорожками;
* справа — свойства клипа (тайминг, скорость, геометрия, прозрачность, звук,
  переход, фильтры) и панель видеоэффектов (цветокоррекция, размытие, хромакей,
  зеркало, PiP, пресеты соцсетей).

Модель: плоский список :class:`ui.widgets.timeline.TimelineClip`. В сцену
FFmpeg они превращаются в :class:`core.video_engine.Scene` при превью/экспорте.
"""

from __future__ import annotations

import logging
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QImage, QKeySequence, QPixmap, QShortcut
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QDoubleSpinBox, QFileDialog,
                             QFrame, QHBoxLayout, QLabel, QPushButton,
                             QScrollArea, QSizePolicy, QSlider, QSplitter,
                             QVBoxLayout, QWidget)

from core.effects_video import COLOR_SPECS, FILTER_PRESETS, VideoEffectChain
from core.subtitle_engine import SubtitleTrack
from core.text_overlay import TextLayer
from core.video_engine import (ASPECT_PRESETS, MEDIA_EXTENSIONS, ClipSpec,
                               PreviewPlayer, Scene, TrackSpec, VideoEngine)
from ui.theme import color
from ui.widgets.common import FlowLayout, Separator, make_heading
from ui.widgets.media_browser import MediaBrowser
from ui.widgets.properties import PropertiesPanel
from ui.widgets.timeline import TimelineClip, TimelineTrack, TimelineWidget
from ui.widgets.video_preview import VideoPreview
from utils.config import get_config
from utils.ffmpeg_helper import probe
from utils.workers import submit

LOG = logging.getLogger(__name__)

#: Список дорожек по умолчанию: 4 видео + 4 аудио.
VIDEO_TRACK_COUNT = 4
AUDIO_TRACK_COUNT = 4
TRACK_COLORS = ["FF6600", "00BFFF", "8E7CFF", "44D492", "FFD166", "FF5C8A",
                "4FC3F7", "B2FF59"]


@dataclass
class VideoProject:
    """Состояние видеопроекта.

    Attributes:
        scenes: Клипы таймлайна.
        width: Ширина кадра.
        height: Высота кадра.
        fps: Кадров в секунду.
        background: Цвет фона.
        aspect_mode: Как вписывать кадр (``blur`` | ``pad`` | ``crop``).
        master_volume: Громкость мастера 0..1.
        text_layers: Текстовые слои.
    """

    clips: List[TimelineClip]
    width: int = 1920
    height: int = 1080
    fps: int = 30
    background: str = "#121212"
    aspect_mode: str = "blur"
    master_volume: float = 1.0
    text_layers: List[TextLayer] = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        """Инициализировать изменяемые значения по умолчанию."""
        if self.text_layers is None:
            self.text_layers = []

    @property
    def duration(self) -> float:
        """Длительность проекта, сек."""
        return max((c.end for c in self.clips), default=0.0)

    def sync(self, clips: List[TimelineClip]) -> None:
        """Связать модель с живым списком клипов таймлайна."""
        self.clips = clips


class VideoEditorTab(QWidget):
    """Редактор видео с таймлайном и превью.

    Сигналы:
        projectChanged(): Проект изменился (автосохранение, заголовок).
        playRequested(): Нажато «play» на транспорте.
        exportRequested(): Пользователь просит открыть диалог экспорта.
        statusMessage(str): Статус для строки состояния.
        durationChanged(float): Длительность проекта.
        aspectChanged(int, int): Смена соотношения сторон.
    """

    projectChanged = pyqtSignal()
    playRequested = pyqtSignal()
    exportRequested = pyqtSignal()
    statusMessage = pyqtSignal(str)
    durationChanged = pyqtSignal(float)
    aspectChanged = pyqtSignal(int, int)

    def __init__(self, engine: Optional[VideoEngine] = None,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            engine: Движок FFmpeg (создаётся свой, если None).
            parent: Родитель.
        """
        super().__init__(parent)
        self.engine = engine or VideoEngine(self)
        self.project = VideoProject(clips=[])
        self.player = PreviewPlayer(self.engine, self)
        self._selected_id: Optional[str] = None
        self._thumb_jobs: set[str] = set()
        self._build_ui()
        self._bind_shortcuts()
        self._init_tracks()
        self.project.sync(self.timeline.clips)
        self.subtitle_track = SubtitleTrack()
        self.player.frameReady.connect(self._on_preview_frame)
        self.preview.set_player(self.player)

    # ================================================================ UI
    def _build_ui(self) -> None:
        """Собрать интерфейс вкладки."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 12, 16, 10)
        layout.setSpacing(10)

        # --- шапка: заголовок + основное действие, под ним — инструменты
        header_top = QHBoxLayout()
        header_top.setSpacing(10)
        title = make_heading("Видеоредактор", pad=20)
        header_top.addWidget(title)
        header_top.addStretch(1)

        self.export_button = QPushButton("⬇ Экспорт")
        self.export_button.setObjectName("Primary")
        self.export_button.setToolTip("Экспорт видео (Ctrl+E)")
        self.export_button.clicked.connect(self.exportRequested)
        header_top.addWidget(self.export_button)
        layout.addLayout(header_top)

        # инструменты — потоком, чтобы не обрезались в узком окне
        header = FlowLayout(h_spacing=8, v_spacing=6)
        self.aspect_combo = QComboBox()
        for name, value in ASPECT_PRESETS.items():
            self.aspect_combo.addItem(f"{name}", value)
        self.aspect_combo.setFixedWidth(124)
        self.aspect_combo.currentIndexChanged.connect(self._on_aspect_changed)
        header.addWidget(QLabel("Формат:"))
        header.addWidget(self.aspect_combo)

        self.fps_combo = QComboBox()
        for fps in (24, 25, 30, 50, 60):
            self.fps_combo.addItem(f"{fps} fps", fps)
        self.fps_combo.setCurrentIndex(2)
        self.fps_combo.setFixedWidth(88)
        self.fps_combo.currentIndexChanged.connect(self._on_fps_changed)
        header.addWidget(self.fps_combo)

        self.grid_check = QCheckBox("Сетка")
        self.grid_check.toggled.connect(self._on_grid)
        header.addWidget(self.grid_check)
        self.safe_check = QCheckBox("Safe-area")
        self.safe_check.toggled.connect(self._on_safe)
        header.addWidget(self.safe_check)

        self.subtitle_button = QPushButton("🔤 Субтитры")
        self.subtitle_button.setObjectName("Ghost")
        self.subtitle_button.setToolTip("Субтитры: Whisper, SRT/ASS/VTT")
        self.subtitle_button.clicked.connect(self.open_subtitle_dialog)
        header.addWidget(self.subtitle_button)

        self.add_text_button = QPushButton("T Текст")
        self.add_text_button.setObjectName("Ghost")
        self.add_text_button.setToolTip("Добавить текстовый слой (Ctrl+T)")
        self.add_text_button.clicked.connect(self.open_text_dialog)
        header.addWidget(self.add_text_button)

        header_box = QWidget()
        header_box.setLayout(header)
        policy = header_box.sizePolicy()
        policy.setHeightForWidth(True)
        header_box.setSizePolicy(policy)
        layout.addWidget(header_box)

        # --- основная область
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.setChildrenCollapsible(False)

        self.media = MediaBrowser()
        # медиабраузер не должен растягивать высоту сплиттера
        self.media.setSizePolicy(self.media.sizePolicy().horizontalPolicy(),
                                 QSizePolicy.Policy.Expanding)
        self.media.fileDoubleClicked.connect(self.add_file)
        self.media.addRequested.connect(self.add_file)
        self.media.importRequested.connect(self.import_files)
        self.media.setMinimumWidth(216)
        self.media.setMaximumWidth(300)
        splitter.addWidget(self.media)

        center = QWidget()
        center_layout = QVBoxLayout(center)
        center_layout.setContentsMargins(0, 0, 0, 0)
        center_layout.setSpacing(8)

        self.preview = VideoPreview(self.player)
        self.preview.setMinimumHeight(200)
        self.preview.set_message("Перетащите видео на таймлайн",
                                 "или дважды кликните файл в медиабраузере")
        center_layout.addWidget(self.preview, 3)

        # таймлайн-тулбар
        tl_bar = QHBoxLayout()
        tl_bar.setSpacing(6)
        for text, tooltip, slot in (
            ("✂", "Разрезать на плейхеде (S)", self.split_at_playhead),
            ("✕", "Удалить клип (Del)", self.delete_selected),
            ("⧉", "Дублировать (Ctrl+D)", self.duplicate_selected),
            ("⟲", "Вписать таймлайн (F)", lambda: self.timeline.fit_view()),
            ("−", "Уменьшить масштаб", lambda: self.timeline.zoom_by(1 / 1.25)),
            ("+", "Увеличить масштаб", lambda: self.timeline.zoom_by(1.25)),
        ):
            button = QPushButton(text)
            button.setObjectName("ToolButton")
            button.setToolTip(tooltip)
            button.setFixedSize(30, 26)
            button.clicked.connect(slot)
            tl_bar.addWidget(button)

        self.snap_check = QCheckBox("Snap")
        self.snap_check.setToolTip("Привязка к краям клипов и тактам")
        self.snap_check.setChecked(True)
        self.snap_check.toggled.connect(lambda v: self.timeline.set_snap(v))
        tl_bar.addWidget(self.snap_check)

        self.bpm_spin = QDoubleSpinBox()
        self.bpm_spin.setFixedWidth(74)
        self.bpm_spin.setRange(0, 300)
        self.bpm_spin.setValue(0)
        self.bpm_spin.setDecimals(1)
        self.bpm_spin.setToolTip("BPM для привязки к тактам (0 — без сетки)")
        self.bpm_spin.valueChanged.connect(
            lambda v: self.timeline.set_bpm(v if v > 0 else None))
        bpm_label = QLabel("BPM")
        bpm_label.setObjectName("Tiny")
        tl_bar.addWidget(bpm_label)
        tl_bar.addWidget(self.bpm_spin)
        tl_bar.addStretch(1)
        self.time_label = QLabel("00:00/00:00")
        self.time_label.setObjectName("Tiny")
        self.time_label.setMinimumWidth(140)
        self.time_label.setAlignment(Qt.AlignmentFlag.AlignRight |
                                     Qt.AlignmentFlag.AlignVCenter)
        tl_bar.addWidget(self.time_label)
        center_layout.addLayout(tl_bar, 0)

        self.timeline = TimelineWidget()
        self.timeline.setMinimumHeight(230)
        self.timeline.clipSelected.connect(self._on_clip_selected)
        self.timeline.clipMoved.connect(self._on_clip_moved)
        self.timeline.clipResized.connect(self._on_clip_resized)
        self.timeline.clipDeleteRequested.connect(self.delete_clip)
        self.timeline.clipSplitRequested.connect(self.split_clip)
        self.timeline.clipDoubleClicked.connect(self._on_clip_selected)
        self.timeline.seekRequested.connect(self.seek)
        self.timeline.filesDropped.connect(self.add_files_at)
        self.timeline.emptyDoubleClicked.connect(self._on_empty_double_click)
        center_layout.addWidget(self.timeline, 2)
        splitter.addWidget(center)

        right = QWidget()
        right.setMinimumWidth(310)
        right.setMaximumWidth(400)
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(8)
        self.properties = PropertiesPanel()
        self.properties.propertyChanged.connect(self._on_property_changed)
        self.properties.actionRequested.connect(self._on_property_action)
        right_layout.addWidget(self.properties, 1)
        self.effects = VideoEffectsPanel()
        self.effects.changed.connect(self._on_effects_changed)
        right_layout.addWidget(self.effects, 1)
        splitter.addWidget(right)

        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setStretchFactor(2, 0)
        splitter.setSizes([240, 720, 340])
        layout.addWidget(splitter, 1)

    def _bind_shortcuts(self) -> None:
        """Горячие клавиши вкладки."""
        for keys, slot in (("S", self.split_at_playhead), ("F", lambda: self.timeline.fit_view()),
                           ("Del", self.delete_selected), ("Ctrl+D", self.duplicate_selected),
                           ("Ctrl+T", self.open_text_dialog),
                           ("Home", lambda: self.seek(0.0))):
            QShortcut(QKeySequence(keys), self).activated.connect(slot)

    def _init_tracks(self) -> None:
        """Создать 4 видеодорожки и 4 аудиодорожки."""
        tracks: List[TimelineTrack] = []
        for i in range(VIDEO_TRACK_COUNT):
            tracks.append(TimelineTrack(index=i, name=f"V{i + 1}", kind="video",
                                        color=TRACK_COLORS[i]))
        for i in range(AUDIO_TRACK_COUNT):
            tracks.append(TimelineTrack(index=VIDEO_TRACK_COUNT + i,
                                        name=f"A{i + 1}", kind="audio",
                                        color=TRACK_COLORS[VIDEO_TRACK_COUNT + i]))
        self.timeline.set_tracks(tracks)

    # ================================================================ сценa
    def build_scene(self, *, for_export: bool = False) -> Scene:
        """Собрать :class:`Scene` из таймлайна для превью или экспорта.

        Args:
            for_export: Дополнительно подклеить текстовые слои и переходы.

        Returns:
            Готовая сцена для ``VideoEngine.render``.
        """
        tracks: List[TrackSpec] = []
        for track in self.timeline.tracks:
            clips: List[ClipSpec] = []
            for clip in sorted(self.timeline.clips_at(track.index),
                               key=lambda c: c.start):
                if not clip.path or not Path(clip.path).exists():
                    continue
                extra = clip.extra or {}
                clips.append(ClipSpec(
                    path=clip.path,
                    start=clip.start,
                    duration=clip.duration,
                    source_start=clip.source_start,
                    speed=float(extra.get("speed", 1.0)),
                    volume=float(extra.get("volume", 1.0)),
                    mute=bool(clip.muted or track.mute),
                    filters=list(clip.filters),
                    opacity=float(extra.get("opacity", 1.0)),
                    is_image=bool(extra.get("is_image", False)),
                    freeze_at=extra.get("freeze_at"),
                    transition=clip.transition or None,
                    transition_duration=clip.transition_duration,
                    transform={"x": float(extra.get("x", 0)), "y": float(extra.get("y", 0)),
                               "scale": float(extra.get("scale", 1.0)),
                               "rotate": float(extra.get("rotate", 0))},
                ))
            if clips:
                tracks.append(TrackSpec(kind=track.kind, name=track.name,
                                        clips=clips, volume=track.volume,
                                        mute=track.mute))
        scene = Scene(tracks=tracks, width=self.project.width, height=self.project.height,
                      fps=self.project.fps, background=self.project.background,
                      aspect_mode=self.project.aspect_mode,
                      master_volume=self.project.master_volume)
        scene.duration = self.project.duration
        if for_export:
            scene.extra_filters = self._overlay_filters()

        # явная длительность, чтобы рендер не обрывался на последнем кадре
        if scene.duration <= 0:
            scene.duration = max((track.end for track in tracks), default=0.0)
        return scene

    def _overlay_filters(self) -> List[str]:
        """Собрать общий слой оверлеев: текстовые слои + субтитры.

        Returns:
            Список фильтров FFmpeg (drawtext), применяемых к финальному кадру.
        """
        filters: List[str] = []
        if self.project.text_layers:
            from core.text_overlay import TextOverlayManager

            manager = TextOverlayManager(list(self.project.text_layers))
            joined = manager.to_filters()
            filters.extend(part for part in joined.split(",") if part)
        if self.subtitle_track and self.subtitle_track.cues:
            joined = self.subtitle_track.to_drawtext_filters()
            filters.extend(part for part in joined.split(",") if part)
        return filters

    def project_data(self) -> Dict[str, Any]:
        """Сериализовать проект (для ``.bfs``)."""
        return {
            "width": self.project.width, "height": self.project.height,
            "fps": self.project.fps, "background": self.project.background,
            "aspect_mode": self.project.aspect_mode,
            "master_volume": self.project.master_volume,
            "clips": [self._clip_to_dict(c) for c in self.timeline.clips],
            "tracks": [{"index": t.index, "name": t.name, "kind": t.kind,
                        "mute": t.mute, "solo": t.solo, "volume": t.volume,
                        "locked": t.locked} for t in self.timeline.tracks],
            "text_layers": [layer.to_dict() for layer in self.project.text_layers],
            "subtitles": self.subtitle_track.to_dict() if self.subtitle_track else {},
        }

    def load_project_data(self, data: Dict[str, Any]) -> None:
        """Восстановить проект из словаря.

        Args:
            data: Словарь из :meth:`project_data`.
        """
        self.project.width = int(data.get("width", 1920))
        self.project.height = int(data.get("height", 1080))
        self.project.fps = int(data.get("fps", 30))
        self.project.background = data.get("background", "#121212")
        self.project.aspect_mode = data.get("aspect_mode", "blur")
        self.project.master_volume = float(data.get("master_volume", 1.0))
        clips = [self._clip_from_dict(item) for item in data.get("clips", [])]
        self.timeline.set_clips(clips)
        for track_data in data.get("tracks", []):
            track = next((t for t in self.timeline.tracks
                          if t.index == int(track_data.get("index", -1))), None)
            if track:
                track.mute = bool(track_data.get("mute", False))
                track.solo = bool(track_data.get("solo", False))
                track.volume = float(track_data.get("volume", 0.8))
                track.locked = bool(track_data.get("locked", False))
        self.project.text_layers = [TextLayer.from_dict(item)
                                    for item in data.get("text_layers", [])]
        self.subtitle_track = SubtitleTrack.from_dict(data.get("subtitles") or {})
        index = self.aspect_combo.findData([self.project.width, self.project.height])
        if index >= 0:
            self.aspect_combo.setCurrentIndex(index)
        self.preview.set_aspect(self.project.width, self.project.height)
        self._after_change(emit=True)

    @staticmethod
    def _clip_to_dict(clip: TimelineClip) -> Dict[str, Any]:
        """Сериализовать клип."""
        return {
            "id": clip.id, "start": clip.start, "duration": clip.duration,
            "source_start": clip.source_start, "name": clip.name, "path": clip.path,
            "track": clip.track, "color": clip.color, "is_audio": clip.is_audio,
            "thumbnail": clip.thumbnail, "muted": clip.muted,
            "filters": list(clip.filters), "transition": clip.transition,
            "transition_duration": clip.transition_duration,
            "extra": dict(clip.extra),
        }

    @staticmethod
    def _clip_from_dict(data: Dict[str, Any]) -> TimelineClip:
        """Восстановить клип."""
        return TimelineClip(
            id=str(data.get("id") or uuid.uuid4().hex[:8]),
            start=float(data.get("start", 0.0)),
            duration=float(data.get("duration", 1.0)),
            source_start=float(data.get("source_start", 0.0)),
            name=str(data.get("name", "")), path=str(data.get("path", "")),
            track=int(data.get("track", 0)), color=str(data.get("color", "FF6600")),
            is_audio=bool(data.get("is_audio", False)),
            thumbnail=str(data.get("thumbnail", "")), muted=bool(data.get("muted", False)),
            filters=list(data.get("filters", [])),
            transition=str(data.get("transition", "")),
            transition_duration=float(data.get("transition_duration", 0.5)),
            extra=dict(data.get("extra", {})),
        )

    # ================================================================ файлы
    def import_files(self) -> None:
        """Диалог выбора файлов для добавления на таймлайн."""
        patterns = " ".join(f"*{ext}" for ext in sorted(MEDIA_EXTENSIONS))
        paths, _ = QFileDialog.getOpenFileNames(
            self, "Импорт медиа", str(Path.home()),
            f"Медиа ({patterns});;Все файлы (*)")
        if paths:
            self.add_files_at(paths, self._preferred_track(paths[0]), self.timeline.playhead)

    def add_file(self, path: str) -> None:
        """Добавить один файл на таймлайн."""
        self.add_files_at([path], self._preferred_track(path), self.timeline.playhead)

    def _preferred_track(self, path: str) -> int:
        """Выбрать дорожку под тип файла (видео → V1, аудио → A1)."""
        is_audio = Path(path).suffix.lower() in {".mp3", ".wav", ".flac", ".ogg",
                                                 ".m4a", ".aac", ".opus"}
        return VIDEO_TRACK_COUNT if is_audio else 0

    def add_files_at(self, paths: List[str], track_index: int, start: float) -> None:
        """Добавить файлы на конкретную дорожку.

        Args:
            paths: Пути.
            track_index: Индекс дорожки таймлайна.
            start: Позиция начала первого клипа, сек.
        """
        added = 0
        cursor = max(0.0, float(start))
        for path in paths:
            if not Path(path).exists():
                continue
            clip = self._make_clip(path, track_index, cursor)
            if clip is None:
                continue
            self.timeline.add_clip(clip)
            cursor = clip.end
            added += 1
            if added == 1:
                self.timeline.set_playhead(clip.start)
                self._on_clip_selected(clip.id)
        if added:
            self.statusMessage.emit(f"Добавлено клипов: {added}")
            self.preview.set_message("Кликните по таймлайну", "чтобы увидеть кадр")
            self._after_change(emit=True)

    def _make_clip(self, path: str, track_index: int, start: float) -> Optional[TimelineClip]:
        """Создать клип из файла: длительность, миниатюра, флаги."""
        suffix = Path(path).suffix.lower()
        name = Path(path).name
        try:
            info = probe(path)
            duration = float(info.get("duration", 0.0))
        except Exception as exc:
            LOG.warning("Не удалось прочитать %s: %s", path, exc)
            duration = 0.0
        is_image = suffix in {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif"}
        is_audio = suffix in {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".opus"}
        if is_image:
            duration = max(duration, 3.0)
        if duration <= 0:
            duration = 5.0
        clip = TimelineClip(
            id=uuid.uuid4().hex[:8], start=max(0.0, start), duration=duration,
            name=name, path=path, track=track_index,
            color=TRACK_COLORS[track_index % len(TRACK_COLORS)],
            is_audio=is_audio, extra={"is_image": is_image},
        )
        if not is_audio:
            self._request_thumbnail(clip)
        return clip

    def _request_thumbnail(self, clip: TimelineClip) -> None:
        """Асинхронно сгенерировать миниатюру для полосы клипа."""
        if clip.id in self._thumb_jobs:
            return
        self._thumb_jobs.add(clip.id)
        path = clip.path

        def work() -> str:
            result = self.engine.thumbnail(path, at=0.5, width=240)
            return str(result) if result else ""

        def done(thumb: str) -> None:
            self._thumb_jobs.discard(clip.id)
            if thumb:
                clip.thumbnail = thumb
                self.timeline.update()

        submit(work, on_done=done, name="thumbnail")

    # ================================================================ операции
    def _selected(self) -> Optional[TimelineClip]:
        """Выбранный клип или None."""
        return self.timeline.get_clip(self._selected_id) if self._selected_id else None

    def seek(self, seconds: float) -> None:
        """Переместить плейхед и показать кадр."""
        self.timeline.set_playhead(max(0.0, seconds))
        self._after_change()
        self._refresh_frame()

    def _refresh_frame(self) -> None:
        """Обновить кадр превью по текущей позиции плейхеда."""
        clip = self._clip_at_time(self.timeline.playhead)
        if clip is None or not clip.path:
            self.preview.set_message("На этой позиции нет видео")
            return
        local = self.timeline.playhead - clip.start + clip.source_start
        if self.player._path != clip.path:  # noqa: SLF001 - дешёвая проверка смены клипа
            self.player.set_media(clip.path, clip.source_start + clip.duration)
        self.player.seek(local)

    def _on_preview_frame(self, data: bytes) -> None:
        """Показать PNG-кадр из FFmpeg в превью."""
        image = QImage.fromData(data)
        if image.isNull():
            return
        self.preview.set_pixmap(QPixmap.fromImage(image))

    def _clip_at_time(self, seconds: float) -> Optional[TimelineClip]:
        """Первый видеоклип на дорожке с максимальным приоритетом в точке."""
        for track in self.timeline.tracks:
            if track.kind != "video" or track.mute:
                continue
            for clip in self.timeline.clips_at(track.index):
                if clip.start <= seconds <= clip.end and not clip.extra.get("is_image"):
                    return clip
        return None

    def split_at_playhead(self) -> None:
        """Разрезать выбранный клип (или все клипы под плейхедом) на позиции."""
        at = self.timeline.playhead
        targets = ([self._selected()] if self._selected() else
                   [c for c in self.timeline.clips if c.start < at < c.end])
        split_any = False
        for clip in [c for c in targets if c and c.start < at < c.end]:
            self.split_clip(clip.id, at)
            split_any = True
        if not split_any:
            self.statusMessage.emit("На плейхеде нечего разрезать")

    def split_clip(self, clip_id: str, at: float) -> None:
        """Разрезать клип в позиции ``at`` (сек таймлайна)."""
        clip = self.timeline.get_clip(clip_id)
        if clip is None or not (clip.start < at < clip.end):
            return
        left_duration = at - clip.start
        right = TimelineClip(
            id=uuid.uuid4().hex[:8], start=at, duration=clip.duration - left_duration,
            source_start=clip.source_start + left_duration, name=clip.name,
            path=clip.path, track=clip.track, color=clip.color,
            is_audio=clip.is_audio, thumbnail=clip.thumbnail, muted=clip.muted,
            filters=list(clip.filters), extra=dict(clip.extra),
        )
        clip.duration = left_duration
        self.timeline.add_clip(right)
        self.timeline.update()
        self.statusMessage.emit("Клип разрезан")
        self._after_change(emit=True)

    def delete_clip(self, clip_id: str) -> None:
        """Удалить клип по id."""
        if self.timeline.get_clip(clip_id) is None:
            return
        self.timeline.remove_clip(clip_id)
        if self._selected_id == clip_id:
            self._selected_id = None
            self.properties.show_clip(None)
        self.statusMessage.emit("Клип удалён")
        self._after_change(emit=True)

    def delete_selected(self) -> None:
        """Удалить выбранный клип."""
        if self._selected_id:
            self.delete_clip(self._selected_id)

    def duplicate_selected(self) -> None:
        """Продублировать выбранный клип сразу после него."""
        clip = self._selected()
        if clip is None:
            return
        copy = TimelineClip(
            id=uuid.uuid4().hex[:8], start=clip.end, duration=clip.duration,
            source_start=clip.source_start, name=clip.name, path=clip.path,
            track=clip.track, color=clip.color, is_audio=clip.is_audio,
            thumbnail=clip.thumbnail, muted=clip.muted, filters=list(clip.filters),
            transition=clip.transition, transition_duration=clip.transition_duration,
            extra=dict(clip.extra),
        )
        self.timeline.add_clip(copy)
        self._on_clip_selected(copy.id)
        self.statusMessage.emit("Клип продублирован")
        self._after_change(emit=True)

    # ================================================================ события
    def _on_clip_selected(self, clip_id: str) -> None:
        """Показать свойства выбранного клипа."""
        self._selected_id = clip_id
        clip = self.timeline.get_clip(clip_id)
        if clip is None:
            self.properties.show_clip(None)
            return
        data = {
            "name": clip.name, "start": clip.start, "duration": clip.duration,
            "source_start": clip.source_start, "speed": clip.extra.get("speed", 1.0),
            "keep_pitch": clip.extra.get("keep_pitch", True),
            "scale": clip.extra.get("scale", 1.0), "x": clip.extra.get("x", 0),
            "y": clip.extra.get("y", 0), "rotate": clip.extra.get("rotate", 0),
            "opacity": clip.extra.get("opacity", 1.0),
            "volume": clip.extra.get("volume", 1.0),
            "fade": clip.extra.get("fade", 0.0),
            "transition": clip.transition,
            "transition_duration": clip.transition_duration,
            "filters": clip.filters, "is_image": clip.extra.get("is_image", False),
        }
        self.properties.show_clip(data)
        self.effects.apply_from_clip(clip.filters)
        self.seek(clip.start + min(0.1, clip.duration / 2))

    def _on_clip_moved(self, clip_id: str, new_start: float, track: int) -> None:
        """Клип перетащили на другую позицию/дорожку."""
        clip = self.timeline.get_clip(clip_id)
        if clip is None:
            return
        clip.start = max(0.0, new_start)
        clip.track = track
        self.timeline.update()
        self.projectChanged.emit()
        self._after_change()

    def _on_clip_resized(self, clip_id: str, new_start: float, new_duration: float) -> None:
        """Изменили границы клипа."""
        clip = self.timeline.get_clip(clip_id)
        if clip is None:
            return
        delta = new_start - clip.start
        clip.start = max(0.0, new_start)
        clip.duration = max(0.1, new_duration)
        if clip.source_start + delta >= 0:
            clip.source_start += delta
        self.timeline.update()
        self._after_change()

    def _on_empty_double_click(self, track: int, at: float) -> None:
        """Двойной клик по пустому месту — выбор файла."""
        self.import_files()

    def _on_property_changed(self, name: str, value: Any) -> None:
        """Изменилось свойство клипа в панели справа."""
        clip = self._selected()
        if clip is None:
            return
        if name in ("start", "duration"):
            setattr(clip, name, max(0.05 if name == "duration" else 0.0, float(value)))
        elif name == "filters":
            clip.filters = list(value)
        elif name == "transition":
            clip.transition = str(value)
        elif name == "transition_duration":
            clip.transition_duration = float(value)
        else:
            clip.extra[name] = value
        self.timeline.update()
        self._after_change()

    def _on_property_action(self, action: str) -> None:
        """Кнопки панели свойств (разрезать, freeze, сброс)."""
        clip = self._selected()
        if clip is None:
            return
        if action == "split":
            self.split_at_playhead()
        elif action == "freeze":
            clip.extra["freeze_at"] = self.timeline.playhead - clip.start
            clip.duration = max(clip.duration, 1.0)
            self.statusMessage.emit("Кадр заморожен")
            self._after_change(emit=True)
        elif action == "reset":
            for key in ("speed", "scale", "x", "y", "rotate", "opacity", "volume", "fade"):
                clip.extra.pop(key, None)
            clip.filters = []
            clip.transition = ""
            self.timeline.update()
            self._on_clip_selected(clip.id)
            self._after_change(emit=True)

    def _on_effects_changed(self) -> None:
        """Применить панель эффектов к выбранному клипу."""
        clip = self._selected()
        if clip is None:
            self.statusMessage.emit("Выберите клип, чтобы применить эффекты")
            return
        clip.filters = self.effects.build_filters()
        self.timeline.update()
        self._refresh_frame()
        self._after_change()

    def _on_aspect_changed(self, _index: int) -> None:
        """Сменить пресет соотношения сторон."""
        value = self.aspect_combo.currentData()
        if not value:
            return
        width, height = int(value[0]), int(value[1])
        self.project.width, self.project.height = width, height
        self.preview.set_aspect(width, height)
        self.preview.set_safe_area(height > width)
        self.safe_check.setChecked(height > width)
        self.aspectChanged.emit(width, height)
        self._after_change()

    def _on_fps_changed(self, _index: int) -> None:
        self.project.fps = int(self.fps_combo.currentData() or 30)
        self.projectChanged.emit()

    def _on_grid(self, checked: bool) -> None:
        self.preview.set_grid(checked)

    def _on_safe(self, checked: bool) -> None:
        self.preview.set_safe_area(checked)

    def _after_change(self, emit: bool = False) -> None:
        """Обновить индикаторы и сообщить об изменениях."""
        self.project.sync(self.timeline.clips)
        self.subtitle_track = SubtitleTrack()
        duration = self.project.duration
        self.time_label.setText(
            f"{self._fmt(self.timeline.playhead)}/{self._fmt(duration)}")
        self.durationChanged.emit(duration)
        if emit:
            self.projectChanged.emit()

    @staticmethod
    def _fmt(seconds: float) -> str:
        """``MM:SS`` (с десятыми, если длительность небольшая)."""
        seconds = max(0.0, float(seconds))
        minutes, rest = divmod(seconds, 60)
        return f"{int(minutes):02d}:{rest:04.1f}"

    # ================================================================ текст
    def open_text_dialog(self) -> None:
        """Открыть диалог создания текстового слоя."""
        from ui.dialogs.text_dialog import TextDialog

        dialog = TextDialog(self._selected() is not None, self)
        if dialog.exec():
            layer = dialog.layer()
            layer.start = self.timeline.playhead
            self.project.text_layers.append(layer)
            self.statusMessage.emit(f"Текстовый слой: {layer.text[:40]}")
            self._after_change(emit=True)

    def open_subtitle_dialog(self) -> None:
        """Открыть диалог субтитров (распознавание/импорт/правка)."""
        from ui.dialogs.subtitle_dialog import SubtitleDialog

        clip = self._clip_at_time(self.timeline.playhead) or (
            self.timeline.clips[0] if self.timeline.clips else None)
        media = clip.path if clip and not clip.extra.get("is_image") else ""
        dialog = SubtitleDialog(media, self, self.subtitle_track)
        if dialog.exec():
            self.subtitle_track = dialog.result_track
            count = len(self.subtitle_track.cues)
            if count:
                self.statusMessage.emit(f"Субтитров: {count} — будут наложены при экспорте")
            else:
                self.statusMessage.emit("Субтитры очищены")
            self.projectChanged.emit()

    # ================================================================ сервис
    def set_playhead_text(self, seconds: float) -> None:
        """Обновить подпись времени (вызывается из главного окна)."""
        self.time_label.setText(f"{self._fmt(seconds)}/{self._fmt(self.project.duration)}")

    def playhead(self) -> float:
        """Текущая позиция плейхеда."""
        return self.timeline.playhead


# ============================================================
#  Панель видеоэффектов
# ============================================================
class VideoEffectsPanel(QFrame):
    """Видеоэффекты: пресеты, цветокоррекция, размытие, хромакей, зеркало.

    Сигналы:
        changed(): Пользователь изменил набор эффектов.
    """

    changed = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Panel")
        self.chain = VideoEffectChain()
        self._loading = False
        self._build_ui()

    def _build_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(8)

        title = QLabel("Видеоэффекты")
        title.setObjectName("H3")
        layout.addWidget(title)

        self.preset_combo = QComboBox()
        self.preset_combo.addItem("Пресет…", "")
        for name in FILTER_PRESETS:
            self.preset_combo.addItem(name, name)
        self.preset_combo.currentIndexChanged.connect(self._on_preset)
        layout.addWidget(self.preset_combo)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        host = QWidget()
        form = QVBoxLayout(host)
        form.setContentsMargins(0, 0, 4, 0)
        form.setSpacing(6)

        self.sliders: Dict[str, QSlider] = {}
        self.value_labels: Dict[str, QLabel] = {}
        for key, (label, minimum, maximum, default) in COLOR_SPECS.items():
            form.addLayout(self._slider_row(key, label, minimum, maximum, default))

        form.addWidget(Separator())
        self.blur_combo = QComboBox()
        self.blur_combo.addItem("Без размытия", "")
        for name in ("gblur", "boxblur", "pixelize"):
            self.blur_combo.addItem(name, name)
        self.blur_combo.currentIndexChanged.connect(lambda _i: self._emit())
        form.addWidget(self.blur_combo)

        blur_row = QHBoxLayout()
        blur_row.addWidget(QLabel("Сила"))
        self.blur_slider = QSlider(Qt.Orientation.Horizontal)
        self.blur_slider.setRange(1, 40)
        self.blur_slider.setValue(6)
        self.blur_slider.valueChanged.connect(lambda _v: self._emit())
        blur_row.addWidget(self.blur_slider, 1)
        form.addLayout(blur_row)

        form.addWidget(Separator())
        self.mirror_check = QCheckBox("Отразить по горизонтали")
        self.mirror_check.toggled.connect(lambda _c: self._emit())
        form.addWidget(self.mirror_check)

        chroma_row = QHBoxLayout()
        self.chroma_check = QCheckBox("Хромакей")
        self.chroma_check.toggled.connect(lambda _c: self._emit())
        chroma_row.addWidget(self.chroma_check)
        self.chroma_color = QComboBox()
        for name, value in (("зелёный", "green"), ("синий", "blue"), ("любой", "0x00FF00")):
            self.chroma_color.addItem(name, value)
        self.chroma_color.currentIndexChanged.connect(lambda _i: self._emit())
        chroma_row.addWidget(self.chroma_color, 1)
        form.addLayout(chroma_row)

        chroma_sim = QHBoxLayout()
        chroma_sim.addWidget(QLabel("Порог"))
        self.chroma_slider = QSlider(Qt.Orientation.Horizontal)
        self.chroma_slider.setRange(1, 100)
        self.chroma_slider.setValue(20)
        self.chroma_slider.valueChanged.connect(lambda _v: self._emit())
        chroma_sim.addWidget(self.chroma_slider, 1)
        form.addLayout(chroma_sim)

        form.addWidget(Separator())
        pip_row = QHBoxLayout()
        self.pip_check = QCheckBox("PiP (картинка в картинке)")
        self.pip_check.toggled.connect(lambda _c: self._emit())
        pip_row.addWidget(self.pip_check)
        form.addLayout(pip_row)

        self.pip_scale = QSlider(Qt.Orientation.Horizontal)
        self.pip_scale.setRange(10, 60)
        self.pip_scale.setValue(28)
        self.pip_scale.valueChanged.connect(lambda _v: self._emit())
        form.addWidget(self.pip_scale)

        self.pip_corner = QComboBox()
        for label, value in (("слева сверху", "tl"), ("справа сверху", "tr"),
                             ("слева снизу", "bl"), ("справа снизу", "br")):
            self.pip_corner.addItem(label, value)
        self.pip_corner.currentIndexChanged.connect(lambda _i: self._emit())
        form.addWidget(self.pip_corner)

        form.addStretch(1)
        self.scroll.setWidget(host)
        layout.addWidget(self.scroll, 1)

        reset = QPushButton("Сбросить эффекты")
        reset.setObjectName("Ghost")
        reset.clicked.connect(self.reset)
        layout.addWidget(reset)

    def _slider_row(self, key: str, label: str, minimum: float, maximum: float,
                    default: float) -> QHBoxLayout:
        """Строка «подпись + слайдер + значение» для параметра цвета."""
        row = QHBoxLayout()
        caption = QLabel(label)
        caption.setObjectName("Tiny")
        caption.setFixedWidth(104)
        row.addWidget(caption)
        slider = QSlider(Qt.Orientation.Horizontal)
        slider.setRange(0, 100)
        span = (maximum - minimum) or 1.0
        slider.setValue(int((default - minimum) / span * 100))
        value_label = QLabel(f"{default:g}")
        value_label.setObjectName("Tiny")
        value_label.setFixedWidth(42)
        slider.valueChanged.connect(
            lambda raw, k=key, mn=minimum, mx=maximum, lbl=value_label:
            (lbl.setText(f"{mn + raw / 100 * (mx - mn):g}"), self._emit()))
        self.sliders[key] = slider
        self.value_labels[key] = value_label
        row.addWidget(slider, 1)
        row.addWidget(value_label)
        return row

    # ------------------------------------------------------------ данные
    def _values(self) -> Dict[str, float]:
        """Текущие значения цветовых слайдеров в реальных единицах."""
        result: Dict[str, float] = {}
        for key, slider in self.sliders.items():
            minimum, maximum = COLOR_SPECS[key][1], COLOR_SPECS[key][2]
            result[key] = minimum + slider.value() / 100.0 * (maximum - minimum)
        return result

    def build_filters(self) -> List[str]:
        """Собрать FFmpeg-фильтры для выбранного клипа."""
        from core.effects_video import build_filters as build_fx
        from core.effects_video import chroma_key

        chain = VideoEffectChain(preset=self.chain.preset, color=self._values())
        chain.rotate = 0.0
        chain.blur_type = self.blur_combo.currentData() or ""
        chain.blur_value = float(self.blur_slider.value())
        chain.mirror_h = self.mirror_check.isChecked()
        joined = build_fx(chain, 1920, 1080)
        filters = [part for part in joined.split(",") if part]
        preset_filter = self.preset_filter()
        if preset_filter:
            filters.extend(part for part in preset_filter.split(",") if part)
        if self.chroma_check.isChecked():
            key = self.chroma_color.currentData() or "#00FF00"
            similarity = self.chroma_slider.value() / 100.0
            filters.append(chroma_key(key, similarity, min(0.5, similarity / 2)))
        if self.pip_check.isChecked():
            # PiP: уменьшаем кадр и сдвигаем в угол на чёрной подложке
            scale = self.pip_scale.value() / 100.0
            corner = self.pip_corner.currentData() or "br"
            x = "16" if corner in ("tl", "bl") else f"iw*{1 - scale:.3f}-16"
            y = "16" if corner in ("tl", "tr") else f"ih*{1 - scale:.3f}-16"
            filters.append(
                f"scale=iw*{scale:.3f}:ih*{scale:.3f},"
                f"pad=iw/{scale:.3f}:ih/{scale:.3f}:{x}:{y}:color=black")
        return [f for f in filters if f]

    def apply_from_clip(self, filters: List[str]) -> None:
        """Загрузить состояние из набора фильтров клипа (по возможности).

        Полностью восстановить параметры из строки FFmpeg нельзя, поэтому
        распознаются только однозначные признаки (зеркало, хромакей, размытие).

        Args:
            filters: Список фильтров клипа.
        """
        self._loading = True
        try:
            joined = ",".join(filters)
            self.mirror_check.setChecked("hflip" in joined)
            self.chroma_check.setChecked("chromakey" in joined or "colorkey" in joined)
            self.blur_combo.setCurrentIndex(1 if "blur" in joined else 0)
            self.pip_check.setChecked(False)
        finally:
            self._loading = False

    def reset(self) -> None:
        """Сбросить все эффекты."""
        for key, slider in self.sliders.items():
            default = COLOR_SPECS[key][3]
            minimum, maximum = COLOR_SPECS[key][1], COLOR_SPECS[key][2]
            span = (maximum - minimum) or 1.0
            slider.setValue(int((default - minimum) / span * 100))
        self.blur_combo.setCurrentIndex(0)
        self.mirror_check.setChecked(False)
        self.chroma_check.setChecked(False)
        self.pip_check.setChecked(False)
        self.preset_combo.blockSignals(True)
        self.preset_combo.setCurrentIndex(0)
        self.preset_combo.blockSignals(False)
        self.chain.preset = ""
        self._preset_filter = ""
        self.changed.emit()

    def _on_preset(self, _index: int) -> None:
        """Выбрать готовый стиль (пресет — строка фильтров FFmpeg).

        Так как пресет задан строкой, слайдеры сбрасываются к нейтральным
        значениям, а сам стиль уходит в цепочку фильтров клипа.
        """
        name = self.preset_combo.currentData()
        if not name:
            return
        self.chain.preset = "" if name == "none" else name
        self._apply_preset_filter(FILTER_PRESETS.get(name, ""))
        self.changed.emit()

    def _apply_preset_filter(self, filter_string: str) -> None:
        """Запомнить фильтр пресета (добавляется в конец цепочки)."""
        self._preset_filter = filter_string

    def preset_filter(self) -> str:
        """Строка фильтра выбранного пресета (пустая, если не выбран)."""
        return getattr(self, "_preset_filter", "")

    def _emit(self) -> None:
        if not self._loading:
            self.changed.emit()
