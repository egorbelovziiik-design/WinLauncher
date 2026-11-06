"""Виджет аудиоволны: отрисовка QPainter, зум, выделение, drag клипов.

``WaveformWidget`` показывает волну одного буфера (или клипа), умеет:

* рисовать пики с кэшированием (пересчёт только при изменении зума/данных);
* зум колёсиком (с сохранением позиции под курсором) и прокрутку;
* выделение области мышью (клик + тянуть) и перемещение playhead;
* показывать сетку по BPM (такты) при включённом snap;
* подсвечивать выделение и отображать время под курсором.

Виджет сигналит о действиях пользователя, а решение (резать/копировать)
принимает вкладка-редактор.
"""

from __future__ import annotations

import math
from typing import List, Optional, Sequence, Tuple

import numpy as np
from PyQt6.QtCore import QPoint, QRect, QRectF, Qt, pyqtSignal
from PyQt6.QtGui import (QBrush, QColor, QCursor, QMouseEvent, QPainter, QPen,
                         QPixmap, QWheelEvent)
from PyQt6.QtWidgets import QSizePolicy, QToolTip, QWidget

from core.audio_engine import AudioBuffer, format_time
from ui.theme import color


class WaveformWidget(QWidget):
    """Волна аудио с зумом, выделением и перетаскиванием.

    Сигналы:
        seekRequested(float): Пользователь кликнул по волне (время, сек).
        selectionChanged(float, float): Начало и конец выделения (сек).
        zoomChanged(float): Новый коэффициент зума.
    """

    seekRequested = pyqtSignal(float)
    selectionChanged = pyqtSignal(float, float)
    zoomChanged = pyqtSignal(float)

    MIN_ZOOM = 0.05
    MAX_ZOOM = 400.0

    def __init__(self, parent: Optional[QWidget] = None, *, show_time: bool = True,
                 interactive: bool = True) -> None:
        """Args:
            parent: Родитель.
            show_time: Показывать подписи времени/сетку.
            interactive: Разрешить выделение/перемотку мышью.
        """
        super().__init__(parent)
        self._buffer: Optional[AudioBuffer] = None
        self._peaks: Optional[np.ndarray] = None
        self._peaks_key: Tuple[int, int] = (0, 0)
        self._peaks_cache: Optional[QPixmap] = None
        self._zoom = 1.0
        self._offset = 0.0  # левый край окна в секундах
        self._playhead = 0.0
        self._selection: Optional[Tuple[float, float]] = None
        self._dragging: Optional[str] = None
        self._drag_start_x = 0
        self._drag_start_offset = 0.0
        self._hover_x: Optional[int] = None
        self._show_time = show_time
        self._interactive = interactive
        self._bpm: Optional[float] = None
        self._gain_color = color("ACCENT")
        self._title = ""
        self._selected_color = color("ACCENT2")
        self.setMinimumHeight(96)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.setMouseTracking(True)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setCursor(QCursor(Qt.CursorShape.IBeamCursor))

     # данные
    def set_buffer(self, buffer: Optional[AudioBuffer], peaks: Optional[Sequence[float]] = None,
                   title: str = "") -> None:
        """Установить аудиоданные для отрисовки.

        Args:
            buffer: Аудиобуфер (или None, чтобы очистить).
            peaks: Готовые пики 0..1 (если уже посчитаны — экономим CPU).
            title: Подпись клипа (имя файла).
        """
        self._buffer = buffer
        self._title = title
        self._peaks = None
        self._peaks_cache = None
        self._selection = None
        self._playhead = 0.0
        self._zoom = 1.0
        self._offset = 0.0
        if peaks is not None and len(peaks):
            self._peaks = np.asarray(peaks, dtype=np.float32)
        self.update()

    def set_peaks(self, peaks: Sequence[float]) -> None:
        """Задать пики напрямую (для дорожек mixer без буфера)."""
        self._peaks = np.asarray(peaks, dtype=np.float32)
        self._peaks_cache = None
        self.update()

    @property
    def duration(self) -> float:
        """Длительность буфера, сек."""
        return self._buffer.duration if self._buffer else 0.0

    @property
    def zoom(self) -> float:
        """Текущий зум."""
        return self._zoom

    @property
    def selection(self) -> Optional[Tuple[float, float]]:
        """Выделенный диапазон (сек) или None."""
        if self._selection is None:
            return None
        a, b = self._selection
        return (min(a, b), max(a, b))

    @property
    def playhead(self) -> float:
        """Позиция курсора-плейхеда, сек."""
        return self._playhead

     # настройки
    def set_playhead(self, seconds: float) -> None:
        """Переместить плейхед (с перерисовкой)."""
        self._playhead = max(0.0, float(seconds))
        self.update()

    def set_zoom(self, zoom: float, anchor_seconds: Optional[float] = None) -> None:
        """Установить зум (1.0 — вся волна в окне).

        Args:
            zoom: Коэффициент (0.05..400).
            anchor_seconds: Точка, остающаяся на месте (по умолчанию центр).
        """
        zoom = max(self.MIN_ZOOM, min(self.MAX_ZOOM, float(zoom)))
        if abs(zoom - self._zoom) < 1e-6:
            return
        duration = self.duration or 1.0
        anchor = anchor_seconds if anchor_seconds is not None else self._offset + self._visible_duration / 2
        self._zoom = zoom
        self._offset = anchor - self._visible_duration / 2
        self._clamp_offset()
        self._clamp_to_duration()
        self.zoomChanged.emit(zoom)
        self.update()

    def zoom_in(self) -> None:
        """Увеличить зум в 1.5 раза."""
        self.set_zoom(self._zoom * 1.5)

    def zoom_out(self) -> None:
        """Уменшить зум в 1.5 раза."""
        self.set_zoom(self._zoom / 1.5)

    def fit(self) -> None:
        """Показать всю волну (сброс зума и прокрутки)."""
        self._zoom = 1.0
        self._offset = 0.0
        self._peaks_cache = None
        self.zoomChanged.emit(self._zoom)
        self.update()

    def set_bpm(self, bpm: Optional[float]) -> None:
        """Задать BPM для сетки тактов."""
        self._bpm = bpm
        self.update()

    def set_selection(self, start: Optional[float], end: Optional[float] = None) -> None:
        """Задать выделение программно."""
        if start is None:
            self._selection = None
        else:
            self._selection = (float(start), float(end if end is not None else start))
        self.selectionChanged.emit(*self.selection) if self._selection else None
        self.update()

    def clear_selection(self) -> None:
        """Снять выделение."""
        self._selection = None
        self.selectionChanged.emit(0.0, 0.0)
        self.update()

    def set_accent(self, color_hex: str) -> None:
        """Сменить цвет волны (например, для отдельных дорожек)."""
        self._gain_color = color_hex
        self._peaks_cache = None
        self.update()

     # геометрия
    @property
    def _visible_duration(self) -> float:
        """Сколько секунд видно в окне."""
        total = self.duration or 1.0
        return total / max(self._zoom, 1e-6)

    def _clamp_offset(self) -> None:
        total = self.duration or 1.0
        max_offset = max(0.0, total - self._visible_duration)
        self._offset = max(0.0, min(self._offset, max_offset))

    def _clamp_to_duration(self) -> None:
        pass

    def _x_to_time(self, x: float) -> float:
        """Пиксель → секунды."""
        if self.width() <= 0:
            return 0.0
        return self._offset + (x / self.width()) * self._visible_duration

    def _time_to_x(self, seconds: float) -> float:
        """Секунды → пиксель."""
        vis = self._visible_duration or 1.0
        return ((seconds - self._offset) / vis) * self.width()

     # отрисовка
    def _ensure_peaks(self, columns: int) -> np.ndarray:
        """Получить пики нужного разрешения (кэш по колонкам/зуму/сдвигу)."""
        key = (columns, round(self._zoom, 4), round(self._offset, 4))
        if self._peaks is not None and self._peaks.size >= columns and self._peaks_key == key:
            return self._peaks
        if self._buffer is None:
            return np.zeros(columns, dtype=np.float32)
        mono = np.abs(self._buffer.samples).mean(axis=1)
        total = mono.size or 1
        start = int(self._offset / max(self.duration, 1e-9) * total)
        end = int((self._offset + self._visible_duration) / max(self.duration, 1e-9) * total)
        start = max(0, min(start, total - 1))
        end = max(start + 1, min(end, total))
        segment = mono[start:end]
        if segment.size >= columns * 2:
            edges = np.linspace(0, segment.size, columns + 1).astype(np.int64)
            peaks = np.array([segment[edges[i]:max(edges[i] + 1, edges[i + 1])].max()
                              for i in range(columns)], dtype=np.float32)
        else:
            peaks = np.interp(np.linspace(0, segment.size - 1, columns),
                              np.arange(segment.size), segment).astype(np.float32)
        peak = float(peaks.max()) if peaks.size else 1.0
        if peak > 0:
            peaks = peaks / peak
        self._peaks = peaks
        self._peaks_key = key
        return peaks

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = self.rect()

        painter.fillRect(rect, QColor(color("INPUT")))

        if self._buffer is None and self._peaks is None:
            painter.setPen(QColor(color("TEXT_DISABLED")))
            painter.drawText(rect, Qt.AlignmentFlag.AlignCenter, "Перетащите аудиофайл или выберите трек")
            painter.end()
            return

         # сетка по BPM
        if self._show_time and self._bpm:
            self._draw_grid(painter)

         # центральная линия
        mid = rect.center().y()
        painter.setPen(QPen(QColor(color("BORDER")), 1))
        painter.drawLine(0, mid, rect.width(), mid)

         # волна
        columns = max(8, self.width())
        peaks = self._ensure_peaks(columns)
        bar_color = QColor(self._gain_color)
        sel = self.selection
        height = rect.height()
        amp = height * 0.42
        painter.setPen(Qt.PenStyle.NoPen)
        samples_per_px = (self._buffer.frames / max(self.duration, 1e-9)) * (
            self._visible_duration / max(1, self.width())) if self._buffer else 99
        if samples_per_px < 3 and self._buffer is not None:
             # сильно приблизились — рисуем настоящую волну линией
            self._draw_sample_line(painter, mid, amp, bar_color)
        else:
            n = peaks.size
            bar_w = max(1.0, self.width() / n - 1.0)
            for i in range(n):
                t_sec = self._offset + (i / n) * self._visible_duration
                if sel and sel[0] <= t_sec <= sel[1]:
                    painter.setBrush(QColor(self._selected_color))
                elif not sel:
                    painter.setBrush(bar_color)
                else:
                    muted = QColor(color("TEXT_DISABLED"))
                    painter.setBrush(muted)
                h = max(0.6, float(peaks[i]) * amp)
                x = i / n * rect.width()
                painter.drawRoundedRect(QRectF(x, mid - h, bar_w, h * 2), bar_w / 2, bar_w / 2)

         # выделение (прозрачная заливка + границы)
        if sel:
            x1 = self._time_to_x(sel[0])
            x2 = self._time_to_x(sel[1])
            painter.setBrush(QColor(self._selected_color).lighter(120))
            painter.setOpacity(0.14)
            painter.drawRect(QRectF(x1, 0, x2 - x1, height))
            painter.setOpacity(1.0)
            painter.setPen(QPen(QColor(self._selected_color), 1, Qt.PenStyle.DashLine))
            painter.drawLine(int(x1), 0, int(x1), height)
            painter.drawLine(int(x2), 0, int(x2), height)

         # плейхед
        px = self._time_to_x(self._playhead)
        if 0 <= px <= rect.width():
            painter.setPen(QPen(QColor(color("ACCENT")), 2))
            painter.drawLine(int(px), 0, int(px), height)
            painter.setBrush(QColor(color("ACCENT")))
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawEllipse(QPoint(int(px), 4), 4, 4)

         # подписи времени
        if self._show_time:
            self._draw_time_ruler(painter)

         # курсор времени под мышью
        if self._hover_x is not None and self._interactive:
            hx = self._hover_x
            painter.setPen(QPen(QColor(color("TEXT_MUTED")), 1, Qt.PenStyle.DotLine))
            painter.drawLine(hx, 0, hx, height)
            text = format_time(self._x_to_time(hx), with_ms=True)
            painter.setPen(QColor(color("TEXT_MUTED")))
            painter.drawText(min(hx + 6, self.width() - 70), 14, text)

         # название клипа
        if self._title:
            painter.setPen(QColor(color("TEXT_MUTED")))
            painter.drawText(8, 16, self._title)
        painter.end()

    def _draw_sample_line(self, painter: QPainter, mid: int, amp: float,
                          brush: QColor) -> None:
        """Нарисовать реальную форму сигнала (когда сэмплов на пиксель мало)."""
        if self._buffer is None:
            return
        mono = self._buffer.samples.mean(axis=1)
        total = mono.size
        start = int(self._offset / max(self.duration, 1e-9) * total)
        end = int((self._offset + self._visible_duration) / max(self.duration, 1e-9) * total)
        segment = mono[max(0, start):min(total, end)]
        if segment.size < 2:
            return
        step = max(1, segment.size // max(1, self.width()))
        pen = QPen(brush, 1.4)
        pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
        painter.setPen(pen)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        path_points = []
        for i in range(0, segment.size, step):
            x = i / segment.size * self.width()
            y = mid - float(segment[i]) * amp
            path_points.append((x, y))
        from PyQt6.QtGui import QPolygonF
        from PyQt6.QtCore import QPointF
        if len(path_points) > 1:
            painter.drawPolyline(QPolygonF([QPointF(x, y) for x, y in path_points]))

    def _draw_grid(self, painter: QPainter) -> None:
        """Нарисовать сетку тактов по BPM."""
        bpm = self._bpm or 0
        if bpm <= 0:
            return
        beat = 60.0 / bpm
        bar = beat * 4
        pen = QPen(QColor(color("BORDER")), 1)
        pen.setStyle(Qt.PenStyle.DotLine)
        painter.setPen(pen)
        duration = self.duration or 1.0
        step = bar
        while step < self._visible_duration / 40:
            step *= 4
        t = math.floor(self._offset / step) * step
        while t < self._offset + self._visible_duration and t <= duration:
            if t >= 0:
                x = int(self._time_to_x(t))
                painter.drawLine(x, 0, x, self.height())
            t += step

    def _draw_time_ruler(self, painter: QPainter) -> None:
        """Подписи времени по низу волны."""
        painter.setPen(QColor(color("TEXT_DISABLED")))
        width = self.width()
        step = self._visible_duration / 6
        for i in range(7):
            t = self._offset + i * step
            x = int(i / 6 * width)
            painter.drawText(x + 2, self.height() - 4, format_time(t, with_ms=step < 1))

     # события
    def wheelEvent(self, event: QWheelEvent) -> None:  # noqa: N802
        if not self._interactive or self.duration <= 0:
            super().wheelEvent(event)
            return
        delta = event.angleDelta().y()
        anchor = self._x_to_time(event.position().x())
        factor = 1.2 if delta > 0 else 1 / 1.2
        self.set_zoom(self._zoom * factor, anchor)
        event.accept()

    def mousePressEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if not self._interactive or self.duration <= 0:
            return
        pos = event.position()
        if event.button() == Qt.MouseButton.MiddleButton:
            self._dragging = "pan"
            self._drag_start_x = int(pos.x())
            self._drag_start_offset = self._offset
        elif event.button() == Qt.MouseButton.LeftButton:
            if event.modifiers() & Qt.KeyboardModifier.ShiftModifier:
                self._dragging = "select"
                t = self._x_to_time(pos.x())
                self._selection = (t, t)
            else:
                self._dragging = "seek"
                self.seekRequested.emit(max(0.0, min(self.duration, self._x_to_time(pos.x()))))
        elif event.button() == Qt.MouseButton.RightButton:
            self._dragging = "select"
            t = self._x_to_time(pos.x())
            self._selection = (t, t)
        self.update()

    def mouseMoveEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        pos = event.position()
        self._hover_x = int(pos.x())
        if self._dragging == "select" and self._selection is not None:
            start = self._selection[0]
            self._selection = (start, self._x_to_time(pos.x()))
            self.selectionChanged.emit(*self.selection) if self.selection else None
        elif self._dragging == "pan":
            dx = self._drag_start_x - int(pos.x())
            seconds_per_px = self._visible_duration / max(1, self.width())
            self._offset = self._drag_start_offset + dx * seconds_per_px
            self._clamp_offset()
        self.update()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if self._dragging == "select" and self._selection:
            a, b = self.selection or (0.0, 0.0)
            if abs(b - a) < 1e-3:
                self._selection = None
        self._dragging = None
        self.update()

    def leaveEvent(self, event) -> None:  # noqa: N802
        self._hover_x = None
        self.update()
        super().leaveEvent(event)

    def keyPressEvent(self, event) -> None:  # noqa: N802
        if event.key() == Qt.Key.Key_Escape:
            self.clear_selection()
        super().keyPressEvent(event)


class WaveformThumb(QWidget):
    """Мини-волна (для карточек поиска и списков треков)."""

    def __init__(self, parent: Optional[QWidget] = None, height: int = 34) -> None:
        super().__init__(parent)
        self._peaks: np.ndarray = np.zeros(64, dtype=np.float32)
        self._accent = color("ACCENT2")
        self.setFixedHeight(height)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def set_peaks(self, peaks: Sequence[float], accent: Optional[str] = None) -> None:
        """Задать пики и (опционально) цвет."""
        self._peaks = np.asarray(peaks, dtype=np.float32) if len(peaks) else np.zeros(32, np.float32)
        if accent:
            self._accent = accent
        self.update()

    def set_buffer(self, buffer: AudioBuffer) -> None:
        """Посчитать пики из буфера."""
        self.set_peaks(buffer.to_mono_peaks(96))

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        mid = self.height() / 2
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(self._accent))
        peaks = self._peaks
        if peaks.size == 0:
            painter.end()
            return
        step = max(1, peaks.size // max(1, self.width() // 3))
        bar_w = max(1.5, self.width() / (peaks.size / step) * 0.6)
        for i in range(0, peaks.size, step):
            value = float(peaks[i:i + step].max()) if i + step <= peaks.size else float(peaks[i])
            x = i / peaks.size * self.width()
            h = max(1.0, value * mid * 0.9)
            painter.drawRoundedRect(QRectF(x, mid - h, bar_w, h * 2), bar_w / 2, bar_w / 2)
        painter.end()
