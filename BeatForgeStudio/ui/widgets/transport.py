"""Транспорт-панель: play/pause/stop, перемотка, громкость, скорость, таймкод.

Панель не знает, что именно играет (аудио или видео) — она лишь отправляет
сигналы и обновляет отображение. Логику связывает главное окно или вкладка.
"""

from __future__ import annotations

from typing import Optional

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (QHBoxLayout, QLabel, QSlider, QVBoxLayout, QWidget)

from core.audio_engine import format_time
from ui.theme import color
from ui.widgets.common import IconButton


class LevelMeter(QWidget):
    """Индикатор уровня мастера (две полоски: L/R)."""

    def __init__(self, parent: Optional[QWidget] = None, width: int = 54) -> None:
        super().__init__(parent)
        self._level = 0.0
        self._peak = 0.0
        self.setFixedSize(width, 20)

    def set_level(self, level: float) -> None:
        """Обновить уровень 0..1 (с затуханием пика)."""
        self._level = max(0.0, min(1.0, level))
        self._peak = max(self._peak * 0.92, self._level)
        self.update()

    def paintEvent(self, event) -> None:  # noqa: N802
        from PyQt6.QtCore import QRectF
        from PyQt6.QtGui import QColor, QPainter

        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)
        segments = 14
        seg_w = self.width() / segments
        for i in range(segments):
            value = (i + 1) / segments
            active = value <= self._level
            if value > 0.9:
                seg_color = "E85A5A"
            elif value > 0.7:
                seg_color = color("ACCENT")
            else:
                seg_color = "3FBF6F"
            painter.setBrush(QColor(seg_color if active else color("INPUT")))
            painter.drawRoundedRect(QRectF(i * seg_w + 1, 4, seg_w - 2, self.height() - 8), 1.5, 1.5)
        painter.end()


class TransportBar(QWidget):
    """Нижняя панель транспорта.

    Сигналы:
        playPauseClicked(): Нажата кнопка play/pause.
        stopClicked(): Нажата кнопка stop.
        skipRequested(int): ``-1`` — назад, ``1`` — вперёд (на 5 с).
        seekRequested(float): Перемотка в секундах (слайдер позиции).
        volumeChanged(float): Громкость 0..1.
        speedChanged(float): Скорость 0.25..2.0.
    """

    playPauseClicked = pyqtSignal()
    stopClicked = pyqtSignal()
    skipRequested = pyqtSignal(int)
    seekRequested = pyqtSignal(float)
    volumeChanged = pyqtSignal(float)
    speedChanged = pyqtSignal(float)

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Transport")
        self.setFixedHeight(58)
        self._duration = 0.0
        self._scrubbing = False

        layout = QHBoxLayout(self)
        layout.setContentsMargins(14, 8, 14, 8)
        layout.setSpacing(10)

         # кнопки
        self.btn_start = IconButton("⇤", "В начало (Home)", 36)
        self.btn_back = IconButton("«", "Назад 5 с (←)", 36)
        self.btn_play = IconButton("▶", "Play/Pause (Space)", 46, object_name="TransportPlay")
        self.btn_stop = IconButton("■", "Стоп", 36)
        self.btn_fwd = IconButton("»", "Вперёд 5 с (→)", 36)

        for button in (self.btn_start, self.btn_back, self.btn_play, self.btn_stop,
                       self.btn_fwd):
            layout.addWidget(button)

        self.btn_start.clicked.connect(lambda: self.skipRequested.emit(0))
        self.btn_back.clicked.connect(lambda: self.skipRequested.emit(-1))
        self.btn_play.clicked.connect(self.playPauseClicked)
        self.btn_stop.clicked.connect(self.stopClicked)
        self.btn_fwd.clicked.connect(lambda: self.skipRequested.emit(1))

         # таймкод + слайдер позиции
        time_box = QVBoxLayout()
        time_box.setSpacing(2)
        self.time_label = QLabel("00:00 / 00:00")
        self.time_label.setStyleSheet(f"color: {color('TEXT')}; font-weight: 600;")
        time_box.addWidget(self.time_label)
        self.position_slider = QSlider(Qt.Orientation.Horizontal)
        self.position_slider.setRange(0, 1000)
        self.position_slider.setValue(0)
        self.position_slider.setToolTip("Позиция воспроизведения")
        self.position_slider.sliderPressed.connect(self._on_slider_pressed)
        self.position_slider.sliderReleased.connect(self._on_slider_released)
        self.position_slider.valueChanged.connect(self._on_slider_moved)
        time_box.addWidget(self.position_slider)
        layout.addLayout(time_box, 1)

         # громкость
        volume_box = QHBoxLayout()
        volume_box.setSpacing(6)
        self.volume_icon = QLabel("♬")
        volume_box.addWidget(self.volume_icon)
        self.volume_slider = QSlider(Qt.Orientation.Horizontal)
        self.volume_slider.setRange(0, 100)
        self.volume_slider.setValue(80)
        self.volume_slider.setFixedWidth(96)
        self.volume_slider.setToolTip("Громкость мастера")
        self.volume_slider.valueChanged.connect(lambda v: self.volumeChanged.emit(v / 100.0))
        volume_box.addWidget(self.volume_slider)
        self.level_meter = LevelMeter()
        volume_box.addWidget(self.level_meter)
        layout.addLayout(volume_box)

         # скорость
        speed_box = QHBoxLayout()
        speed_box.setSpacing(6)
        speed_label = QLabel("Скорость")
        speed_label.setObjectName("Tiny")
        speed_box.addWidget(speed_label)
        self.speed_slider = QSlider(Qt.Orientation.Horizontal)
        self.speed_slider.setRange(25, 200)
        self.speed_slider.setValue(100)
        self.speed_slider.setFixedWidth(80)
        self.speed_slider.setToolTip("Скорость воспроизведения (0.25x – 2.0x)")
        self.speed_slider.valueChanged.connect(
            lambda v: (self.speed_label.setText(f"{v / 100:.2f}x"),
                       self.speedChanged.emit(v / 100.0))
        )
        speed_box.addWidget(self.speed_slider)
        self.speed_label = QLabel("1.00x")
        self.speed_label.setObjectName("Tiny")
        self.speed_label.setFixedWidth(42)
        speed_box.addWidget(self.speed_label)
        layout.addLayout(speed_box)

     # состояние
    def set_playing(self, playing: bool) -> None:
        """Сменить иконку play/pause."""
        self.btn_play.setText("❚❚" if playing else "▶")

    def set_duration(self, seconds: float) -> None:
        """Задать длительность (для подписи и слайдера)."""
        self._duration = max(0.0, seconds)
        self._update_time_label(self.position_from_slider())

    def set_position(self, seconds: float) -> None:
        """Обновить позицию (если пользователь не тянет слайдер)."""
        if self._scrubbing:
            return
        self._update_time_label(seconds)
        if self._duration > 0:
            self.position_slider.blockSignals(True)
            self.position_slider.setValue(int(max(0.0, min(1.0, seconds / self._duration)) * 1000))
            self.position_slider.blockSignals(False)

    def set_volume(self, value: float) -> None:
        """Установить положение слайдера громкости 0..1 (без сигнала)."""
        self.volume_slider.blockSignals(True)
        self.volume_slider.setValue(int(max(0.0, min(1.0, value)) * 100))
        self.volume_slider.blockSignals(False)

    def set_speed(self, value: float) -> None:
        """Установить слайдер скорости без сигнала."""
        self.speed_slider.blockSignals(True)
        self.speed_slider.setValue(int(max(0.25, min(2.0, value)) * 100))
        self.speed_slider.blockSignals(False)
        self.speed_label.setText(f"{value:.2f}x")

    def set_level(self, level: float) -> None:
        """Обновить индикатор уровня."""
        self.level_meter.set_level(level)

    def position_from_slider(self) -> float:
        """Позиция, соответствующая слайдеру (сек)."""
        return self.position_slider.value() / 1000.0 * (self._duration or 0.0)

     # внутреннее
    def _update_time_label(self, seconds: float) -> None:
        self.time_label.setText(f"{format_time(seconds)} / {format_time(self._duration)}")

    def _on_slider_pressed(self) -> None:
        self._scrubbing = True

    def _on_slider_released(self) -> None:
        self._scrubbing = False
        self.seekRequested.emit(self.position_from_slider())

    def _on_slider_moved(self, value: int) -> None:
        seconds = value / 1000.0 * (self._duration or 0.0)
        self._update_time_label(seconds)
        if self._scrubbing:
            self.seekRequested.emit(seconds)
