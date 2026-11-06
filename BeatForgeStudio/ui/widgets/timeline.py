"""Универсальный таймлайн: дорожки, клипы, линейка, зум, drag & drop.

``TimelineWidget`` — модель + отрисовка (QPainter) без внешних зависимостей:

* дорожки V1–V4 / A1–A4 (или аудиодорожки редактора) с заголовками слева;
* клипы: перемещение мышью, тянущиеся края (trim), выделение, разрез;
* линейка времени и тактов, snap к сетке BPM, зум колёсиком (Ctrl) и
  горизонтальная прокрутка (Shift/тачпад);
* playhead, следующий за воспроизведением;
* drag & drop файлов из проводника/браузера медиа.

Сигналы отдают «намерения» пользователя, а изменения модели делает
владелец (вкладка), что упрощает undo/redo.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from PyQt6.QtCore import (QPoint, QPointF, QRect, QRectF, Qt, QTimer,
                          pyqtSignal)
from PyQt6.QtGui import (QBrush, QColor, QCursor, QFont, QMouseEvent, QPainter,
                         QPen, QPixmap, QPolygonF, QWheelEvent)
from PyQt6.QtWidgets import QMenu, QSizePolicy, QToolTip, QWidget

from core.audio_engine import format_time
from ui.theme import color

# Высота дорожки по умолчанию.
TRACK_HEIGHT = 40
# Ширина колонки с заголовками дорожек.
HEADER_WIDTH = 92
# Высота линейки времени.
RULER_HEIGHT = 26

# Цвета дорожек (по кругу).
TRACK_COLORS = ("FF6600", "00BFFF", "8A7CFF", "3FBF6F",
                "E8A33D", "E85A5A", "00C2A8", "B07CFF")


@dataclass
class TimelineClip:
    """Клип на таймлайне.

    Attributes:
        id: Уникальный идентификатор (для поиска в модели).
        start: Начало на таймлайне, сек.
        duration: Длительность, сек.
        source_start: Смещение внутри исходника, сек.
        name: Подпись.
        path: Путь к медиафайлу.
        track: Индекс дорожки.
        color: Цвет клипа (hex).
        is_audio: Аудиоклип (волна) или видеоклип (превью-полоса).
        thumbnail: Путь к миниатюре (для видеоклипов).
        muted: Клип выключен.
        filters: Включённые фильтры (имена, для индикации).
        transition: Переход на стыке с предыдущим клипом.
        transition_duration: Длительность перехода, сек.
        extra: Прочие данные (текст, opacity, speed…).
    """

    id: str
    start: float
    duration: float
    source_start: float = 0.0
    name: str = ""
    path: str = ""
    track: int = 0
    color: str = "FF6600"
    is_audio: bool = False
    thumbnail: str = ""
    muted: bool = False
    filters: List[str] = field(default_factory=list)
    transition: str = ""
    transition_duration: float = 0.5
    extra: Dict[str, object] = field(default_factory=dict)

    @property
    def end(self) -> float:
        """Конец клипа, сек."""
        return self.start + self.duration


@dataclass
class TimelineTrack:
    """Дорожка таймлайна.

    Attributes:
        index: Индекс (0 — верхняя).
        name: Имя (``V1``, ``A1``, ``Бит``).
        kind: ``video`` | ``audio`` | ``text`` | ``image``.
        color: Акцентный цвет.
        mute: Выключена.
        solo: Соло.
        volume: Громкость 0..1.
        height: Высота в px.
        locked: Заблокирована от изменений.
    """

    index: int
    name: str = ""
    kind: str = "video"
    color: str = "FF6600"
    mute: bool = False
    solo: bool = False
    volume: float = 0.8
    height: int = TRACK_HEIGHT
    locked: bool = False


class TimelineWidget(QWidget):
    """Интерактивный таймлайн с клипами.

    Сигналы:
        clipSelected(str): id выбранного клипа (пустая строка — снято выделение).
        clipMoved(str, float, int): id, новое начало (сек), новая дорожка.
        clipResized(str, float, float): id, новое начало, новая длительность.
        clipDoubleClicked(str): двойной клик (открыть свойства).
        clipDeleteRequested(str): запрос удаления.
        clipSplitRequested(str, float): id клипа и время разреза.
        seekRequested(float): клик по линейке — перемотка.
        emptyDoubleClicked(int, float): двойной клик по пустому месту (дорожка, время).
        filesDropped(object, int, float): список путей, дорожка, время.
        trackSelected(int): выбрана дорожка (клик по заголовку).
    """

    clipSelected = pyqtSignal(str)
    clipMoved = pyqtSignal(str, float, int)
    clipResized = pyqtSignal(str, float, float)
    clipDoubleClicked = pyqtSignal(str)
    clipDeleteRequested = pyqtSignal(str)
    clipSplitRequested = pyqtSignal(str, float)
    seekRequested = pyqtSignal(float)
    emptyDoubleClicked = pyqtSignal(int, float)
    filesDropped = pyqtSignal(object, int, float)
    trackSelected = pyqtSignal(int)

    MIN_PPS = 2.0  # пикселей в секунду (минимум)
    MAX_PPS = 900.0  # пикселей в секунду (максимум)

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Timeline")
        self.setAcceptDrops(True)
        self.setMouseTracking(True)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.setMinimumHeight(200)

        self.tracks: List[TimelineTrack] = []
        self.clips: List[TimelineClip] = []
        self.pixels_per_second: float = 40.0
        self.scroll_x: float = 0.0  # левый край в секундах
        self.vscroll: int = 0  # вертикальная прокрутка, px
        self.playhead: float = 0.0
        self.bpm: Optional[float] = None
        self.snap_enabled = True
        self.duration_hint: float = 60.0
        self.selected_clip: Optional[str] = None
        self.selected_track: int = 0
        self._drag_mode: Optional[str] = None  # move | resize-left | resize-right | seek
        self._drag_start = QPoint()
        self._drag_clip_start: Tuple[float, float] = (0.0, 0.0)
        self._drag_origin_track = 0
        self._hover_clip: Optional[str] = None
        self._drop_track: Optional[int] = None
        self._drop_time: Optional[float] = None
        self._thumb_cache: Dict[str, QPixmap] = {}

        self._click_timer = QTimer(self)
        self._click_timer.setSingleShot(True)
        self._click_timer.setInterval(220)

     # ====== модель
    def set_tracks(self, tracks: List[TimelineTrack]) -> None:
        """Заменить список дорожек."""
        self.tracks = tracks
        self.update()

    def add_track(self, track: TimelineTrack) -> None:
        """Добавить дорожку."""
        self.tracks.append(track)
        self.update()

    def set_clips(self, clips: List[TimelineClip]) -> None:
        """Заменить все клипы (например, после undo)."""
        self.clips = clips
        self.update()

    def add_clip(self, clip: TimelineClip) -> None:
        """Добавить клип."""
        self.clips.append(clip)
        self._ensure_duration(clip.end)
        self.update()

    def remove_clip(self, clip_id: str) -> None:
        """Удалить клип по id."""
        self.clips = [c for c in self.clips if c.id != clip_id]
        if self.selected_clip == clip_id:
            self.selected_clip = None
            self.clipSelected.emit("")
        self.update()

    def get_clip(self, clip_id: str) -> Optional[TimelineClip]:
        """Найти клип по id."""
        return next((c for c in self.clips if c.id == clip_id), None)

    def clips_at(self, track: int) -> List[TimelineClip]:
        """Клипы конкретной дорожки, отсортированные по времени."""
        return sorted((c for c in self.clips if c.track == track), key=lambda c: c.start)

    def _ensure_duration(self, value: float) -> None:
        if value > self.duration_hint:
            self.duration_hint = value + 10.0

    def set_bpm(self, bpm: Optional[float]) -> None:
        """Задать BPM сетки."""
        self.bpm = bpm
        self.update()

    def set_snap(self, enabled: bool) -> None:
        """Включить/выключить привязку к сетке."""
        self.snap_enabled = enabled
        self.update()

    def set_playhead(self, seconds: float, follow: bool = True) -> None:
        """Передвинуть playhead; при ``follow`` — прокрутить, если он ушёл за экран."""
        self.playhead = max(0.0, seconds)
        if follow:
            visible = self.visible_seconds
            if self.playhead > self.scroll_x + visible * 0.92:
                self.scroll_x = max(0.0, self.playhead - visible * 0.25)
        self.update()

     # ====== геометрия
    @property
    def visible_seconds(self) -> float:
        """Сколько секунд помещается в видимой области."""
        width = max(1, self.width() - HEADER_WIDTH)
        return width / max(self.pixels_per_second, 1e-6)

    def _x_to_time(self, x: float) -> float:
        """Пиксель (в области дорожек) → секунды."""
        return self.scroll_x + max(0.0, x - HEADER_WIDTH) / self.pixels_per_second

    def _time_to_x(self, seconds: float) -> float:
        """Секунды → пиксель."""
        return HEADER_WIDTH + (seconds - self.scroll_x) * self.pixels_per_second

    def _track_at_y(self, y: float) -> Optional[int]:
        """Индекс дорожки по Y (или None)."""
        top = RULER_HEIGHT - self.vscroll
        for track in self.tracks:
            if top <= y < top + track.height:
                return track.index
            top += track.height
        return None

    def _track_top(self, index: int) -> int:
        """Y-координата верхней границы дорожки."""
        top = RULER_HEIGHT - self.vscroll
        for track in self.tracks:
            if track.index == index:
                return top
            top += track.height
        return top

    def _clip_rect(self, clip: TimelineClip) -> QRectF:
        """Прямоугольник клипа в координатах виджета."""
        x = self._time_to_x(clip.start)
        w = max(6.0, clip.duration * self.pixels_per_second)
        y = self._track_top(clip.track)
        height = self.tracks[clip.track].height - 8 if clip.track < len(self.tracks) else TRACK_HEIGHT - 8
        return QRectF(x, y + 4, w, height)

    def _clip_at(self, pos: QPoint) -> Optional[TimelineClip]:
        """Клип под курсором (сверху вниз по дорожкам)."""
        for clip in reversed(self.clips):
            if self._clip_rect(clip).contains(QPointF(pos)):
                return clip
        return None

    def _snap(self, seconds: float, enabled: Optional[bool] = None) -> float:
        """Привязать время к сетке (BPM) и к краям соседних клипов."""
        use = self.snap_enabled if enabled is None else enabled
        if not use or seconds < 0:
            return seconds
        grid = (60.0 / self.bpm) if (self.bpm and self.bpm > 0) else 1.0
        snapped = round(seconds / grid) * grid
        for clip in self.clips:
            for edge in (clip.start, clip.end):
                if abs(edge - seconds) < grid * 0.35:
                    return edge
        return snapped

     # ====== отрисовка
    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = self.rect()
        painter.fillRect(rect, QColor(color("PANEL")))

        self._draw_tracks_bg(painter)
        self._draw_clips(painter)
        self._draw_ruler(painter)
        self._draw_playhead(painter)
        self._draw_drop_indicator(painter)
        painter.end()

    def _draw_ruler(self, painter: QPainter) -> None:
        """Линейка времени/тактов сверху."""
        height = RULER_HEIGHT
        painter.fillRect(QRect(0, 0, self.width(), height), QColor(color("PANEL_HI")))
        painter.setPen(QPen(QColor(color("BORDER")), 1))
        painter.drawLine(0, height - 1, self.width(), height - 1)

        pen = QPen(QColor(color("BORDER")), 1)
        painter.setPen(pen)
         # шаг сетки подбираем так, чтобы подписи не наезжали
        target_px = 90
        raw_step = target_px / self.pixels_per_second
        nice_steps = (0.25, 0.5, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
        step = next((s for s in nice_steps if s >= raw_step), 600)
        start = math.floor(self.scroll_x / step) * step
        t = start
        while t <= self.scroll_x + self.visible_seconds + step:
            x = self._time_to_x(t)
            if x >= HEADER_WIDTH - 1:
                painter.drawLine(QPointF(x, height - 7), QPointF(x, height))
                painter.setPen(QColor(color("TEXT_MUTED")))
                painter.drawText(QPointF(x + 4, height - 10), format_time(t, with_ms=step < 1))
                painter.setPen(QPen(QColor(color("BORDER")), 1))
            t += step

         # такты при известном BPM
        if self.bpm and self.bpm > 0:
            bar = 60.0 / self.bpm * 4
            if bar * self.pixels_per_second > 24:
                painter.setPen(QPen(QColor(color("ACCENT_DIM")), 1, Qt.PenStyle.DotLine))
                b = math.floor(self.scroll_x / bar) * bar
                while b <= self.scroll_x + self.visible_seconds:
                    x = self._time_to_x(b)
                    if x >= HEADER_WIDTH:
                        painter.drawLine(QPointF(x, 0), QPointF(x, RULER_HEIGHT))
                    b += bar

         # заголовочная колонка линейки
        painter.fillRect(QRect(0, 0, HEADER_WIDTH, height), QColor(color("PANEL_HI")))
        painter.setPen(QColor(color("TEXT_MUTED")))
        small = painter.font()
        small.setPointSize(8)
        painter.setFont(small)
        painter.drawText(QRect(8, 0, HEADER_WIDTH - 16, height),
                         Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft,
                         "Дорожки")

    def _draw_tracks_bg(self, painter: QPainter) -> None:
        """Фон дорожек, заголовки, M/S кнопки."""
        top = RULER_HEIGHT - self.vscroll
        for track in self.tracks:
            row = QRect(0, int(top), self.width(), track.height)
            base = color("PANEL_ALT") if track.index % 2 else color("PANEL")
            painter.fillRect(row, QColor(base))
            if track.index == self.selected_track:
                painter.fillRect(row, QColor(255, 255, 255, 8))

             # границы по вертикали
            painter.setPen(QPen(QColor(color("BORDER")), 1))
            painter.drawLine(row.left(), row.bottom(), row.right(), row.bottom())

             # заголовок
            header = QRect(0, int(top), HEADER_WIDTH, track.height)
            painter.fillRect(header, QColor(color("PANEL_HI")))
            painter.fillRect(QRect(0, int(top), 4, track.height),
                             QColor(track.color if not track.mute else color("TEXT_DISABLED")))
            painter.setPen(QColor(color("TEXT")))
            font = painter.font()
            font.setPointSize(9)
            font.setBold(True)
            painter.setFont(font)
            kind_names = {"video": "видео", "audio": "аудио", "text": "текст", "image": "фото"}
            painter.drawText(QRect(12, int(top) + 4, HEADER_WIDTH - 60, 16),
                             Qt.AlignmentFlag.AlignLeft,
                             track.name or f"T{track.index + 1}")
            font.setBold(False)
            font.setPointSize(7)
            painter.setFont(font)
            painter.setPen(QColor(color("TEXT_MUTED")))
            painter.drawText(QRect(12, int(top) + 21, HEADER_WIDTH - 60, 14),
                             Qt.AlignmentFlag.AlignLeft, kind_names.get(track.kind, track.kind))
            # кнопки M / S
            m_rect = QRect(HEADER_WIDTH - 44, int(top) + 10, 16, 14)
            s_rect = QRect(HEADER_WIDTH - 24, int(top) + 10, 16, 14)
            for box, letter, active, active_color in (
                (m_rect, "M", track.mute, "E85A5A"),
                (s_rect, "S", track.solo, color("ACCENT")),
            ):
                painter.setPen(Qt.PenStyle.NoPen)
                painter.setBrush(QColor(active_color if active else color("INPUT")))
                painter.drawRoundedRect(box, 3, 3)
                painter.setPen(QColor("111111" if active else color("TEXT_MUTED")))
                painter.drawText(box, Qt.AlignmentFlag.AlignCenter, letter)
            painter.setPen(QPen(QColor(color("BORDER")), 1))
            painter.drawLine(HEADER_WIDTH, int(top), HEADER_WIDTH, int(top) + track.height)
            top += track.height

         # вертикальная сетка по времени (затемняем поверх клипов не будем)
        if not self.tracks:
            painter.setPen(QColor(color("TEXT_DISABLED")))
            painter.drawText(QRect(HEADER_WIDTH, RULER_HEIGHT, self.width() - HEADER_WIDTH, 120),
                             Qt.AlignmentFlag.AlignCenter,
                             "Перетащите файлы сюда, чтобы добавить дорожку")

    def _draw_clips(self, painter: QPainter) -> None:
        """Клипы: фон, волна/миниатюра, подписи, ручки и переходы."""
        for clip in self.clips:
            rect = self._clip_rect(clip)
            if rect.right() < HEADER_WIDTH or rect.left() > self.width():
                continue
            visible_rect = rect.intersected(QRectF(HEADER_WIDTH, 0, self.width() - HEADER_WIDTH,
                                                   self.height()))
            if visible_rect.isEmpty():
                continue
            clip_color = QColor(clip.color)
            if clip.muted:
                clip_color.setAlpha(90)
            selected = clip.id == self.selected_clip
            hovered = clip.id == self._hover_clip

            path_rect = QRectF(visible_rect.left(), rect.top(), visible_rect.width(), rect.height())
            painter.setPen(QPen(QColor(color("ACCENT") if selected else clip_color.darker(140)),
                                2 if selected else 1))
            painter.setBrush(clip_color.darker(190 if not hovered else 165))
            painter.drawRoundedRect(path_rect, 6, 6)

             # содержимое: волна или превью
            painter.save()
            painter.setClipRect(path_rect.adjusted(1, 1, -1, -1))
            if clip.is_audio:
                self._draw_wave_placeholder(painter, clip, path_rect)
            else:
                self._draw_thumb_strip(painter, clip, path_rect)
            painter.restore()

             # подпись
            painter.setPen(QColor(255, 255, 255, 225))
            font = painter.font()
            font.setPointSize(8)
            font.setBold(True)
            painter.setFont(font)
            text_rect = QRectF(path_rect.left() + 8, path_rect.top() + 4,
                               max(0.0, path_rect.width() - 16), 14)
            metrics = painter.fontMetrics()
            label = metrics.elidedText(clip.name or Path(clip.path).stem or "Клип",
                                       Qt.TextElideMode.ElideRight, int(text_rect.width()))
            painter.drawText(text_rect, Qt.AlignmentFlag.AlignLeft, label)

            font.setPointSize(7)
            font.setBold(False)
            painter.setFont(font)
            painter.setPen(QColor(255, 255, 255, 160))
            painter.drawText(QRectF(text_rect.left(), path_rect.bottom() - 15,
                                    text_rect.width(), 13),
                             Qt.AlignmentFlag.AlignLeft,
                             f"{format_time(clip.duration, True)}"
                             + (f"  ·  {clip.transition}" if clip.transition else "")
                             + ("  ·  ⨯" if clip.muted else ""))

             # индикатор фильтров
            if clip.filters:
                painter.setBrush(QColor(color("ACCENT2")))
                painter.setPen(Qt.PenStyle.NoPen)
                painter.drawEllipse(QPointF(path_rect.right() - 10, path_rect.top() + 10), 3.5, 3.5)

             # ручки по краям
            if selected or hovered:
                painter.setBrush(QColor(color("ACCENT")))
                painter.drawRoundedRect(QRectF(path_rect.left() + 1, path_rect.top() + 8,
                                               3, path_rect.height() - 16), 1.5, 1.5)
                painter.drawRoundedRect(QRectF(path_rect.right() - 4, path_rect.top() + 8,
                                               3, path_rect.height() - 16), 1.5, 1.5)

    def _draw_wave_placeholder(self, painter: QPainter, clip: TimelineClip,
                               rect: QRectF) -> None:
        """Псевдо-волна (реальные пики рисует audio-редактор поверх)."""
        peaks = clip.extra.get("peaks")
        mid = rect.center().y()
        amp = rect.height() * 0.34
        painter.setPen(Qt.PenStyle.NoPen)
        base = QColor(clip.color).lighter(150)
        base.setAlpha(170)
        painter.setBrush(base)
        if isinstance(peaks, (list, tuple)) and peaks:
            n = min(len(peaks), int(rect.width() / 2))
            step = max(1, len(peaks) // max(1, n))
            for i in range(0, len(peaks), step):
                value = float(max(peaks[i:i + step]))
                x = rect.left() + i / len(peaks) * rect.width()
                h = max(0.5, value * amp)
                painter.drawRect(QRectF(x, mid - h, 1.6, h * 2))
        else:
            painter.drawRect(QRectF(rect.left(), mid - 1, rect.width(), 2))

    def _draw_thumb_strip(self, painter: QPainter, clip: TimelineClip, rect: QRectF) -> None:
        """Полоса миниатюр для видеоклипа (если есть картинка)."""
        pix = self._thumb_cache.get(clip.path)
        if pix is None and clip.thumbnail:
            pix = QPixmap(clip.thumbnail)
            if not pix.isNull():
                self._thumb_cache[clip.path] = pix
        if pix is None or pix.isNull():
             # диагональная штриховка как заглушка
            painter.setPen(QPen(QColor(255, 255, 255, 18), 6))
            step = 18
            x = rect.left() - rect.height()
            while x < rect.right():
                painter.drawLine(QPointF(x, rect.bottom()), QPointF(x + rect.height(), rect.top()))
                x += step
            return
        target_h = rect.height()
        scaled = pix.scaledToHeight(int(target_h), Qt.TransformationMode.SmoothTransformation)
        x = rect.left()
        while x < rect.right():
            painter.drawPixmap(QPointF(x, rect.top()), scaled)
            x += scaled.width()

    def _draw_playhead(self, painter: QPainter) -> None:
        """Красная линия playhead с треугольником на линейке."""
        x = self._time_to_x(self.playhead)
        if x < HEADER_WIDTH or x > self.width():
            return
        painter.setPen(QPen(QColor(color("ACCENT")), 1.5))
        painter.drawLine(QPointF(x, RULER_HEIGHT), QPointF(x, self.height()))
        painter.setBrush(QColor(color("ACCENT")))
        painter.setPen(Qt.PenStyle.NoPen)
        triangle = QPolygonF([QPointF(x - 5, RULER_HEIGHT - 8), QPointF(x + 5, RULER_HEIGHT - 8),
                              QPointF(x, RULER_HEIGHT + 2)])
        painter.drawPolygon(triangle)

    def _draw_drop_indicator(self, painter: QPainter) -> None:
        """Подсветка дорожки/времени при drag & drop."""
        if self._drop_track is None or self._drop_time is None:
            return
        top = self._track_top(self._drop_track)
        height = self.tracks[self._drop_track].height if self._drop_track < len(self.tracks) else TRACK_HEIGHT
        x = self._time_to_x(self._drop_time)
        painter.setPen(QPen(QColor(color("ACCENT2")), 2, Qt.PenStyle.DashLine))
        painter.setBrush(QColor(0, 191, 255, 26))
        painter.drawRoundedRect(QRectF(HEADER_WIDTH, top + 2, self.width() - HEADER_WIDTH,
                                       height - 4), 6, 6)
        painter.setPen(QPen(QColor(color("ACCENT2")), 2))
        painter.drawLine(QPointF(x, top), QPointF(x, top + height))

     # ====== события
    def wheelEvent(self, event: QWheelEvent) -> None:  # noqa: N802
        delta = event.angleDelta()
        if event.modifiers() & Qt.KeyboardModifier.ControlModifier:
            anchor_time = self._x_to_time(event.position().x())
            factor = 1.18 if delta.y() > 0 else 1 / 1.18
            new_pps = max(self.MIN_PPS, min(self.MAX_PPS, self.pixels_per_second * factor))
            ratio = (event.position().x() - HEADER_WIDTH) / max(1, self.width() - HEADER_WIDTH)
            self.pixels_per_second = new_pps
            self.scroll_x = max(0.0, anchor_time - self.visible_seconds * ratio)
        elif event.modifiers() & Qt.KeyboardModifier.ShiftModifier or delta.x() != 0:
            self.scroll_x = max(0.0, self.scroll_x - (delta.x() or delta.y()) / self.pixels_per_second * 0.5)
        else:
            self.vscroll = max(0, self.vscroll - delta.y() // 2)
        self.update()
        event.accept()

    def mousePressEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        pos = event.position().toPoint()
        if event.button() == Qt.MouseButton.LeftButton:
            if pos.y() < RULER_HEIGHT:
                self._drag_mode = "seek"
                self.seekRequested.emit(max(0.0, self._x_to_time(pos.x())))
                return
            if pos.x() < HEADER_WIDTH:
                track = self._track_at_y(pos.y())
                if track is not None:
                     # клики по M/S
                    top = self._track_top(track)
                    if QRect(HEADER_WIDTH - 44, top + 8, 16, 14).contains(pos):
                        self.tracks[track].mute = not self.tracks[track].mute
                        self.trackSelected.emit(track)
                        self.update()
                        return
                    if QRect(HEADER_WIDTH - 24, top + 8, 16, 14).contains(pos):
                        self.tracks[track].solo = not self.tracks[track].solo
                        self.update()
                        return
                    self.selected_track = track
                    self.trackSelected.emit(track)
                    self.update()
                return

            clip = self._clip_at(pos)
            if clip is not None:
                self.selected_clip = clip.id
                self.selected_track = clip.track
                self.clipSelected.emit(clip.id)
                rect = self._clip_rect(clip)
                self._drag_start = pos
                self._drag_clip_start = (clip.start, clip.duration)
                self._drag_origin_track = clip.track
                if pos.x() - rect.left() <= 6:
                    self._drag_mode = "resize-left"
                elif rect.right() - pos.x() <= 6:
                    self._drag_mode = "resize-right"
                else:
                    self._drag_mode = "move"
                self.setCursor(QCursor(Qt.CursorShape.ClosedHandCursor))
            else:
                self.selected_clip = None
                self.clipSelected.emit("")
                track = self._track_at_y(pos.y())
                if track is not None:
                    self.selected_track = track
                    self.seekRequested.emit(max(0.0, self._snap(self._x_to_time(pos.x()))))
                self._drag_mode = "seek"
            self.update()
        elif event.button() == Qt.MouseButton.MiddleButton:
            self._drag_mode = "pan"
            self._drag_start = pos
            self._drag_clip_start = (self.scroll_x, 0.0)
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        pos = event.position().toPoint()
        clip = self._clip_at(pos)
        new_hover = clip.id if clip else None
        if new_hover != self._hover_clip:
            self._hover_clip = new_hover
            self.update()

        if self._drag_mode == "move" and self.selected_clip:
            clip = self.get_clip(self.selected_clip)
            if clip:
                dt = (pos.x() - self._drag_start.x()) / self.pixels_per_second
                new_start = max(0.0, self._snap(self._drag_clip_start[0] + dt))
                clip.start = new_start
                track = self._track_at_y(pos.y())
                if track is not None:
                    clip.track = track
                self.update()
        elif self._drag_mode in ("resize-left", "resize-right") and self.selected_clip:
            clip = self.get_clip(self.selected_clip)
            if clip:
                dt = (pos.x() - self._drag_start.x()) / self.pixels_per_second
                if self._drag_mode == "resize-right":
                    clip.duration = max(0.1, self._snap(self._drag_clip_start[1] + dt))
                else:
                    start, duration = self._drag_clip_start
                    new_start = max(0.0, self._snap(start + dt))
                    delta = new_start - start
                    clip.start = new_start
                    clip.duration = max(0.1, duration - delta)
                    clip.source_start = max(0.0, clip.source_start + delta)
                self.update()
        elif self._drag_mode == "seek":
            if pos.y() < RULER_HEIGHT or pos.y() > RULER_HEIGHT:
                self.seekRequested.emit(max(0.0, self._snap(self._x_to_time(pos.x()))))
        elif self._drag_mode == "pan":
            dx = (self._drag_start.x() - pos.x()) / self.pixels_per_second
            self.scroll_x = max(0.0, self._drag_clip_start[0] + dx)
            self.update()
        else:
             # курсор-подсказка на краях клипа
            if clip is not None:
                rect = self._clip_rect(clip)
                if pos.x() - rect.left() <= 6 or rect.right() - pos.x() <= 6:
                    self.setCursor(QCursor(Qt.CursorShape.SizeHorCursor))
                else:
                    self.setCursor(QCursor(Qt.CursorShape.OpenHandCursor))
            elif pos.x() > HEADER_WIDTH:
                self.setCursor(QCursor(Qt.CursorShape.ArrowCursor))
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if self.selected_clip and self._drag_mode in ("move", "resize-left", "resize-right"):
            clip = self.get_clip(self.selected_clip)
            if clip is not None:
                if self._drag_mode == "move":
                    self.clipMoved.emit(clip.id, clip.start, clip.track)
                else:
                    self.clipResized.emit(clip.id, clip.start, clip.duration)
        self._drag_mode = None
        self.setCursor(QCursor(Qt.CursorShape.ArrowCursor))
        self.update()
        super().mouseReleaseEvent(event)

    def mouseDoubleClickEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        pos = event.position().toPoint()
        clip = self._clip_at(pos)
        if clip is not None:
            self.clipDoubleClicked.emit(clip.id)
        else:
            track = self._track_at_y(pos.y())
            if track is not None:
                self.emptyDoubleClicked.emit(track, max(0.0, self._snap(self._x_to_time(pos.x()))))
        super().mouseDoubleClickEvent(event)

    def contextMenuEvent(self, event) -> None:  # noqa: N802
        pos = event.pos()
        clip = self._clip_at(pos)
        menu = QMenu(self)
        if clip is not None:
            menu.addAction("✂ Разрезать здесь", lambda: self.clipSplitRequested.emit(
                clip.id, max(clip.start, min(clip.end, self._x_to_time(pos.x())))))
            menu.addAction("◉ Свойства", lambda: self.clipDoubleClicked.emit(clip.id))
            menu.addSeparator()
            mute_action = menu.addAction("⨯ " + ("Включить" if clip.muted else "Выключить"))
            mute_action.triggered.connect(lambda: (setattr(clip, "muted", not clip.muted), self.update()))
            menu.addAction("✕ Удалить", lambda: self.clipDeleteRequested.emit(clip.id))
        else:
            track = self._track_at_y(pos.y())
            if track is not None:
                menu.addAction("+ Импортировать файлы…",
                               lambda: self.filesDropped.emit([], track,
                                                              self._x_to_time(pos.x())))
            menu.addAction("⌕ Вся сцена", self.fit_view)
        menu.exec(event.globalPos())

    def keyPressEvent(self, event) -> None:  # noqa: N802
        if self.selected_clip:
            if event.key() == Qt.Key.Key_Delete:
                self.clipDeleteRequested.emit(self.selected_clip)
                return
            if event.key() == Qt.Key.Key_S:
                clip = self.get_clip(self.selected_clip)
                if clip:
                    self.clipSplitRequested.emit(clip.id, self.playhead)
                    return
        if event.key() in (Qt.Key.Key_Plus, Qt.Key.Key_Equal):
            self.zoom_by(1.2)
        elif event.key() == Qt.Key.Key_Minus:
            self.zoom_by(1 / 1.2)
        super().keyPressEvent(event)

     # ====== drag & drop
    def dragEnterEvent(self, event) -> None:  # noqa: N802
        if event.mimeData().hasUrls() or event.mimeData().hasFormat("application/x-beatforge-track"):
            event.acceptProposedAction()
            self.setStyleSheet(f"QWidgetTimeline {{ border: 1px dashed {color('ACCENT2')}; }}")

    def dragLeaveEvent(self, event) -> None:  # noqa: N802
        self._drop_track = None
        self.setStyleSheet("")
        self.update()

    def dragMoveEvent(self, event) -> None:  # noqa: N802
        pos = event.position().toPoint()
        track = self._track_at_y(pos.y())
        self._drop_track = track if track is not None else (len(self.tracks) - 1 if self.tracks else None)
        self._drop_time = max(0.0, self._snap(self._x_to_time(pos.x())))
        self.update()
        event.acceptProposedAction()

    def dropEvent(self, event) -> None:  # noqa: N802
        pos = event.position().toPoint()
        track = self._track_at_y(pos.y())
        if track is None:
            track = len(self.tracks) - 1 if self.tracks else 0
        time_at = max(0.0, self._snap(self._x_to_time(pos.x())))
        paths: List[str] = []
        if event.mimeData().hasUrls():
            paths = [url.toLocalFile() for url in event.mimeData().urls() if url.isLocalFile()]
        elif event.mimeData().hasFormat("application/x-beatforge-track"):
            paths = [bytes(event.mimeData().data("application/x-beatforge-track")).decode("utf-8")]
        self._drop_track = None
        self._drop_time = None
        self.setStyleSheet("")
        if paths:
            self.filesDropped.emit(paths, track, time_at)
        event.acceptProposedAction()
        self.update()

     # ====== навигация
    def zoom_by(self, factor: float) -> None:
        """Изменить масштаб вокруг центра."""
        center = self.scroll_x + self.visible_seconds / 2
        self.pixels_per_second = max(self.MIN_PPS, min(self.MAX_PPS,
                                                       self.pixels_per_second * factor))
        self.scroll_x = max(0.0, center - self.visible_seconds / 2)
        self.update()

    def fit_view(self) -> None:
        """Вписать всю сцену в видимую область."""
        total = max(self.duration_hint, self.playhead + 5, 10.0)
        width = max(1, self.width() - HEADER_WIDTH)
        self.pixels_per_second = max(self.MIN_PPS, width / total)
        self.scroll_x = 0.0
        self.update()

    def center_on(self, seconds: float) -> None:
        """Прокрутить так, чтобы время было в центре."""
        self.scroll_x = max(0.0, seconds - self.visible_seconds / 2)
        self.update()
