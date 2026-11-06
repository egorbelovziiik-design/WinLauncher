"""Панель одной дорожки: имя, mute/solo, громкость, индикатор активности.

Используется и в аудиомикшере, и в видеоредакторе (для A-дорожек).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (QFrame, QHBoxLayout, QLabel, QPushButton, QSlider,
                             QVBoxLayout, QWidget)

from ui.theme import color


@dataclass
class TrackState:
    """Состояние дорожки.

    Attributes:
        name: Название дорожки.
        index: Порядковый номер (0-based).
        volume: Громкость 0..1.
        pan: Панорама -1..1.
        mute: Выключена.
        solo: Соло.
        color: Цвет дорожки (hex) для волны/полосы.
    """

    name: str
    index: int = 0
    volume: float = 0.8
    pan: float = 0.0
    mute: bool = False
    solo: bool = False
    color: str = "FF6600"


class TrackPanel(QFrame):
    """Компактная панель управления дорожкой.

    Сигналы:
        muteToggled(int, bool): индекс и новое состояние mute.
        soloToggled(int, bool): индекс и новое состояние solo.
        volumeChanged(int, float): индекс и громкость 0..1.
        nameChanged(int, str): новое имя дорожки.
        removeRequested(int): запрос на удаление дорожки.
    """

    muteToggled = pyqtSignal(int, bool)
    soloToggled = pyqtSignal(int, bool)
    volumeChanged = pyqtSignal(int, float)
    nameChanged = pyqtSignal(int, str)
    removeRequested = pyqtSignal(int)

    def __init__(self, state: TrackState, parent: Optional[QWidget] = None,
                 compact: bool = False) -> None:
        """Args:
            state: Начальное состояние дорожки.
            parent: Родитель.
            compact: Компактный режим (для видеоредактора).
        """
        super().__init__(parent)
        self.state = state
        self.setObjectName("TrackHeader")
        self._compact = compact

        layout = QHBoxLayout(self)
        layout.setContentsMargins(8, 6, 8, 6)
        layout.setSpacing(6 if compact else 8)

        self.color_bar = QFrame()
        self.color_bar.setFixedWidth(4)
        self.color_bar.setStyleSheet(f"background: {state.color}; border-radius: 2px;")
        layout.addWidget(self.color_bar)

        labels = QVBoxLayout()
        labels.setSpacing(0)
        self.name_label = QLabel(state.name)
        self.name_label.setStyleSheet(f"color: {color('TEXT')}; font-weight: 600;")
        labels.addWidget(self.name_label)
        if not compact:
            self.info_label = QLabel("—")
            self.info_label.setObjectName("Tiny")
            labels.addWidget(self.info_label)
        layout.addLayout(labels, 1)

        self.mute_button = QPushButton("M")
        self.mute_button.setObjectName("Ghost")
        self.mute_button.setCheckable(True)
        self.mute_button.setFixedSize(28, 26)
        self.mute_button.setToolTip("Mute")
        self.mute_button.toggled.connect(self._on_mute)
        layout.addWidget(self.mute_button)

        self.solo_button = QPushButton("S")
        self.solo_button.setObjectName("Ghost")
        self.solo_button.setCheckable(True)
        self.solo_button.setFixedSize(28, 26)
        self.solo_button.setToolTip("Solo")
        self.solo_button.toggled.connect(self._on_solo)
        layout.addWidget(self.solo_button)

        if not compact:
            self.volume_slider = QSlider(Qt.Orientation.Horizontal)
            self.volume_slider.setRange(0, 100)
            self.volume_slider.setValue(int(state.volume * 100))
            self.volume_slider.setFixedWidth(90)
            self.volume_slider.setToolTip("Громкость дорожки")
            self.volume_slider.valueChanged.connect(
                lambda v: self._on_volume(v / 100.0))
            layout.addWidget(self.volume_slider)

            self.level = QFrame()
            self.level.setFixedSize(6, 26)
            self.level.setStyleSheet(f"background: {color('INPUT')}; border-radius: 3px;")
            layout.addWidget(self.level)

     # API
    def set_name(self, name: str) -> None:
        """Обновить имя и состояние."""
        self.state.name = name
        self.name_label.setText(name)

    def set_info(self, text: str) -> None:
        """Обновить строку информации (длительность, BPM)."""
        if hasattr(self, "info_label"):
            self.info_label.setText(text)

    def set_level(self, value: float) -> None:
        """Показать уровень сигнала (0..1) цветом индикатора."""
        if not hasattr(self, "level"):
            return
        pct = max(0.02, min(1.0, value))
        height = int(26 * pct)
        shade = "3FBF6F" if pct < 0.75 else (color("ACCENT") if pct < 0.95 else "E85A5A")
        self.level.setStyleSheet(
            f"background: {color('INPUT')}; border-radius: 3px;"
            f" border-bottom: {height}px solid {shade};"
        )

    def set_mute(self, muted: bool) -> None:
        """Программно переключить mute (без сигнала)."""
        self.mute_button.blockSignals(True)
        self.mute_button.setChecked(muted)
        self.mute_button.blockSignals(False)
        self.state.mute = muted

    def set_solo(self, solo: bool) -> None:
        """Программно переключить solo (без сигнала)."""
        self.solo_button.blockSignals(True)
        self.solo_button.setChecked(solo)
        self.solo_button.blockSignals(False)
        self.state.solo = solo

     # события
    def _on_mute(self, checked: bool) -> None:
        self.state.mute = checked
        self.setStyleSheet(
            f"QFrameTrackHeader {{ background-color: {color('PANEL_HI')};"
            f" border-radius: 8px; {'' if not checked else 'opacity: 0.6;'} }}"
        )
        self.muteToggled.emit(self.state.index, checked)

    def _on_solo(self, checked: bool) -> None:
        self.state.solo = checked
        self.soloToggled.emit(self.state.index, checked)

    def _on_volume(self, value: float) -> None:
        self.state.volume = value
        self.volumeChanged.emit(self.state.index, value)
