"""Панель свойств клипа: позиция, размер, скорость, прозрачность, звук."""

from __future__ import annotations

from typing import Any, Dict, Optional

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (QSizePolicy, QComboBox, QDoubleSpinBox, QFormLayout, QFrame,
                             QHBoxLayout, QLabel, QPushButton, QScrollArea,
                             QSlider, QSpinBox, QVBoxLayout, QWidget)

from ui.theme import color
from ui.widgets.common import Separator


class PropertiesPanel(QFrame):
    """Редактор свойств выбранного клипа.

    Панель не хранит модель: она показывает значения и эмитит
    ``propertyChanged(имя, значение)``; сохранение и undo делает вкладка.

    Сигналы:
        propertyChanged(str, object): имя свойства и новое значение.
        actionRequested(str): запрос действия (``split``, ``freeze``, ``reset``…).
    """

    propertyChanged = pyqtSignal(str, object)
    actionRequested = pyqtSignal(str)

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Panel")
        self.setMinimumWidth(260)
        self.setMaximumWidth(340)
        self._loading = False

        outer = QVBoxLayout(self)
        outer.setContentsMargins(12, 12, 12, 12)
        outer.setSpacing(10)

        title = QLabel("Свойства клипа")
        title.setObjectName("H3")
        outer.addWidget(title)
        self.name_label = QLabel("Клип не выбран")
        self.name_label.setObjectName("Muted")
        outer.addWidget(self.name_label)
        outer.addWidget(Separator())

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        body = QWidget()
        form = QFormLayout(body)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignLeft)
        form.setFormAlignment(Qt.AlignmentFlag.AlignTop)
        form.setContentsMargins(0, 0, 6, 0)
        form.setSpacing(8)

         # тайминг
        self.start_spin = self._double_spin(0, 99999, 0.0, "сек", 2)
        self.duration_spin = self._double_spin(0.05, 99999, 1.0, "сек", 2)
        self.source_spin = self._double_spin(0, 99999, 0.0, "сек", 2)
        form.addRow("Начало", self.start_spin)
        form.addRow("Длительность", self.duration_spin)
        form.addRow("Смещение в файле", self.source_spin)

         # скорость
        speed_row = QWidget()
        speed_layout = QHBoxLayout(speed_row)
        speed_layout.setContentsMargins(0, 0, 0, 0)
        speed_layout.setSpacing(6)
        self.speed_slider = QSlider(Qt.Orientation.Horizontal)
        self.speed_slider.setRange(25, 400)
        self.speed_slider.setValue(100)
        self.speed_label = QLabel("1.00x")
        self.speed_label.setObjectName("Tiny")
        self.speed_label.setFixedWidth(44)
        speed_layout.addWidget(self.speed_slider, 1)
        speed_layout.addWidget(self.speed_label)
        form.addRow("Скорость", speed_row)

        self.pitch_combo = QComboBox()
        self.pitch_combo.addItems(["Сохранять тон", "Менять тон"])
        form.addRow("Аудио", self.pitch_combo)

         # геометрия
        self.scale_spin = QSpinBox()
        self.scale_spin.setRange(10, 400)
        self.scale_spin.setValue(100)
        self.scale_spin.setSuffix(" %")
        form.addRow("Масштаб", self.scale_spin)

        self.x_spin = QSpinBox()
        self.x_spin.setRange(-4000, 4000)
        self.x_spin.setMinimumWidth(58)
        self.x_spin.setMaximumWidth(72)
        self.x_spin.setSizePolicy(QSizePolicy.Policy.Ignored,
                                       QSizePolicy.Policy.Fixed)
        self.x_spin.setSuffix(" px")
        self.y_spin = QSpinBox()
        self.y_spin.setRange(-4000, 4000)
        self.y_spin.setMinimumWidth(58)
        self.y_spin.setMaximumWidth(72)
        self.y_spin.setSizePolicy(QSizePolicy.Policy.Ignored,
                                       QSizePolicy.Policy.Fixed)
        self.y_spin.setSuffix(" px")
        pos_row = QWidget()
        pos_layout = QHBoxLayout(pos_row)
        pos_layout.setContentsMargins(0, 0, 0, 0)
        pos_layout.setSpacing(6)
        pos_layout.addWidget(QLabel("X"))
        pos_layout.addWidget(self.x_spin)
        pos_layout.addWidget(QLabel("Y"))
        pos_layout.addWidget(self.y_spin)
        form.addRow("Позиция", pos_row)

        self.rotate_spin = QSpinBox()
        self.rotate_spin.setRange(-180, 180)
        self.rotate_spin.setSuffix("°")
        form.addRow("Поворот", self.rotate_spin)

         # прозрачность
        opacity_row = QWidget()
        opacity_layout = QHBoxLayout(opacity_row)
        opacity_layout.setContentsMargins(0, 0, 0, 0)
        self.opacity_slider = QSlider(Qt.Orientation.Horizontal)
        self.opacity_slider.setRange(0, 100)
        self.opacity_slider.setValue(100)
        self.opacity_label = QLabel("100%")
        self.opacity_label.setObjectName("Tiny")
        self.opacity_label.setFixedWidth(44)
        opacity_layout.addWidget(self.opacity_slider, 1)
        opacity_layout.addWidget(self.opacity_label)
        form.addRow("Прозрачность", opacity_row)

         # звук
        volume_row = QWidget()
        volume_layout = QHBoxLayout(volume_row)
        volume_layout.setContentsMargins(0, 0, 0, 0)
        self.volume_slider = QSlider(Qt.Orientation.Horizontal)
        self.volume_slider.setRange(0, 200)
        self.volume_slider.setValue(100)
        self.volume_label = QLabel("100%")
        self.volume_label.setObjectName("Tiny")
        self.volume_label.setFixedWidth(44)
        volume_layout.addWidget(self.volume_slider, 1)
        volume_layout.addWidget(self.volume_label)
        form.addRow("Громкость", volume_row)

        self.fade_spin = self._double_spin(0, 10, 0.0, "сек", 2)
        form.addRow("Fade", self.fade_spin)

         # переход
        self.transition_combo = QComboBox()
        self.transition_combo.addItem("без перехода", "")
        for key, label in (("fade", "Fade"), ("dissolve", "Dissolve"), ("wipeleft", "Wipe ←"),
                           ("wiperight", "Wipe →"), ("wipeup", "Wipe ↑"), ("wipedown", "Wipe ↓"),
                           ("zoomin", "Zoom In"), ("zoomout", "Zoom Out"), ("slideleft", "Slide ←"),
                           ("slideright", "Slide →"), ("circleopen", "Circle"), ("pixelize", "Pixelize"),
                           ("glitch", "Glitch (стиль)"), ("blur", "Blur (стиль)")):
            self.transition_combo.addItem(label, key)
        form.addRow("Переход", self.transition_combo)

        self.transition_duration = self._double_spin(0.1, 2.0, 0.5, "сек", 2)
        form.addRow("Длит. перехода", self.transition_duration)

         # фильтры (текстовое поле)
        self.filters_label = QLabel("—")
        self.filters_label.setObjectName("Muted")
        self.filters_label.setWordWrap(True)
        form.addRow("Фильтры", self.filters_label)

        scroll.setWidget(body)
        outer.addWidget(scroll, 1)

         # кнопки действий
        buttons = QHBoxLayout()
        buttons.setSpacing(6)
        self.split_button = QPushButton("✂ Разрезать")
        self.split_button.setObjectName("Ghost")
        self.split_button.clicked.connect(lambda: self.actionRequested.emit("split"))
        buttons.addWidget(self.split_button)

        self.freeze_button = QPushButton("■ Freeze")
        self.freeze_button.setObjectName("Ghost")
        self.freeze_button.setToolTip("Заморозить кадр на месте playhead")
        self.freeze_button.clicked.connect(lambda: self.actionRequested.emit("freeze"))
        buttons.addWidget(self.freeze_button)
        outer.addLayout(buttons)

        reset_button = QPushButton("↺ Сбросить изменения")
        reset_button.setObjectName("Ghost")
        reset_button.clicked.connect(lambda: self.actionRequested.emit("reset"))
        outer.addWidget(reset_button)

        self.setEnabled(False)
        self._connect_signals()

     # хелперы
    def _double_spin(self, minimum: float, maximum: float, value: float,
                     suffix: str = "", decimals: int = 2) -> QDoubleSpinBox:
        spin = QDoubleSpinBox()
        spin.setRange(minimum, maximum)
        spin.setDecimals(decimals)
        spin.setValue(value)
        spin.setSuffix(f" {suffix}" if suffix else "")
        spin.setSingleStep(0.1)
        # иначе minimumSizeHint считается по «99999.00 сек» и панель
        # получает горизонтальный скролл
        spin.setMinimumWidth(72)
        return spin

    def _connect_signals(self) -> None:
        self.start_spin.valueChanged.connect(lambda v: self._emit("start", float(v)))
        self.duration_spin.valueChanged.connect(lambda v: self._emit("duration", float(v)))
        self.source_spin.valueChanged.connect(lambda v: self._emit("source_start", float(v)))
        self.speed_slider.valueChanged.connect(self._on_speed)
        self.pitch_combo.currentIndexChanged.connect(
            lambda i: self._emit("keep_pitch", i == 0))
        self.scale_spin.valueChanged.connect(lambda v: self._emit("scale", v / 100.0))
        self.x_spin.valueChanged.connect(lambda v: self._emit("x", v))
        self.y_spin.valueChanged.connect(lambda v: self._emit("y", v))
        self.rotate_spin.valueChanged.connect(lambda v: self._emit("rotate", v))
        self.opacity_slider.valueChanged.connect(self._on_opacity)
        self.volume_slider.valueChanged.connect(self._on_volume)
        self.fade_spin.valueChanged.connect(lambda v: self._emit("fade", float(v)))
        self.transition_combo.currentIndexChanged.connect(
            lambda i: self._emit("transition", self.transition_combo.itemData(i) or ""))
        self.transition_duration.valueChanged.connect(
            lambda v: self._emit("transition_duration", float(v)))

    def _emit(self, name: str, value: Any) -> None:
        if not self._loading:
            self.propertyChanged.emit(name, value)

    def _on_speed(self, value: int) -> None:
        self.speed_label.setText(f"{value / 100:.2f}x")
        self._emit("speed", value / 100.0)

    def _on_opacity(self, value: int) -> None:
        self.opacity_label.setText(f"{value}%")
        self._emit("opacity", value / 100.0)

    def _on_volume(self, value: int) -> None:
        self.volume_label.setText(f"{value}%")
        self._emit("volume", value / 100.0)

     # API
    def show_clip(self, data: Optional[Dict[str, Any]]) -> None:
        """Показать свойства клипа.

        Args:
            data: Словарь свойств (``name``, ``start``, ``duration``, …) или None,
                чтобы очистить панель.
        """
        self._loading = True
        try:
            if not data:
                self.setEnabled(False)
                self.name_label.setText("Клип не выбран")
                return
            self.setEnabled(True)
            self.name_label.setText(str(data.get("name") or "Клип"))
            self.start_spin.setValue(float(data.get("start", 0.0)))
            self.duration_spin.setValue(float(data.get("duration", 1.0)))
            self.source_spin.setValue(float(data.get("source_start", 0.0)))
            speed = float(data.get("speed", 1.0))
            self.speed_slider.setValue(int(speed * 100))
            self.speed_label.setText(f"{speed:.2f}x")
            self.pitch_combo.setCurrentIndex(0 if data.get("keep_pitch", True) else 1)
            self.scale_spin.setValue(int(float(data.get("scale", 1.0)) * 100))
            self.x_spin.setValue(int(float(data.get("x", 0))))
            self.y_spin.setValue(int(float(data.get("y", 0))))
            self.rotate_spin.setValue(int(float(data.get("rotate", 0))))
            opacity = float(data.get("opacity", 1.0))
            self.opacity_slider.setValue(int(opacity * 100))
            self.opacity_label.setText(f"{int(opacity * 100)}%")
            volume = float(data.get("volume", 1.0))
            self.volume_slider.setValue(int(volume * 100))
            self.volume_label.setText(f"{int(volume * 100)}%")
            self.fade_spin.setValue(float(data.get("fade", 0.0)))
            transition = str(data.get("transition", "") or "")
            index = self.transition_combo.findData(transition)
            self.transition_combo.setCurrentIndex(max(0, index))
            self.transition_duration.setValue(float(data.get("transition_duration", 0.5)))
            filters = data.get("filters") or []
            self.filters_label.setText(", ".join(filters) if filters else "—")
            self.freeze_button.setEnabled(not bool(data.get("is_image")))
            self.volume_slider.setEnabled(not bool(data.get("is_image")))
        finally:
            self._loading = False
