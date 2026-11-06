"""Диалог текстового слоя: пресеты, стиль, анимация и живое превью.

Превью рисуется тем же кодом, что и финальный рендер —
:func:`core.text_overlay.render_text_png`, поэтому результат в диалоге
совпадает с кадром в фильме (с точностью до масштаба).
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QImage, QPixmap
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QDialog, QDialogButtonBox,
                             QDoubleSpinBox, QFormLayout,
                             QFrame, QHBoxLayout, QLabel, QLineEdit,
                             QPlainTextEdit, QPushButton, QSpinBox, QVBoxLayout,
                             QWidget)

from core.text_overlay import ANIMATIONS, TEXT_PRESETS, TextLayer, render_text_png
from ui.theme import color

LOG = logging.getLogger(__name__)


class TextDialog(QDialog):
    """Создать или отредактировать текстовый слой.

    Attributes:
        result_layer: Слой после ``accept()``.
    """

    def __init__(self, has_clip: bool = True, parent: Optional[QWidget] = None,
                 layer: Optional[TextLayer] = None) -> None:
        """Args:
            has_clip: Есть ли выбранный клип (влияет на подсказку).
            parent: Родитель.
            layer: Существующий слой для редактирования.
        """
        super().__init__(parent)
        self.setWindowTitle("Текстовый слой")
        self.setMinimumSize(680, 480)
        self.result_layer = layer or TextLayer(text="Ваш текст")
        self._preview_timer = QTimer(self)
        self._preview_timer.setSingleShot(True)
        self._preview_timer.setInterval(180)
        self._preview_timer.timeout.connect(self._render_preview)
        self._y_expr = str(self.result_layer.y)
        self._build_ui(has_clip)

    # ================================================================ UI
    def _build_ui(self, has_clip: bool) -> None:
        """Собрать интерфейс диалога."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 18, 20, 16)
        layout.setSpacing(12)

        title = QLabel("Текст и субтитры")
        title.setObjectName("H2")
        layout.addWidget(title)

        body = QHBoxLayout()
        body.setSpacing(14)

        left = QWidget()
        form = QFormLayout(left)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignRight)
        form.setHorizontalSpacing(12)
        form.setVerticalSpacing(9)

        self.preset_combo = QComboBox()
        self.preset_combo.addItem("Свой стиль", "")
        for name in TEXT_PRESETS:
            self.preset_combo.addItem(name, name)
        self.preset_combo.currentIndexChanged.connect(self._on_preset)
        form.addRow("Пресет:", self.preset_combo)

        self.text_edit = QPlainTextEdit(self.result_layer.text)
        self.text_edit.setFixedHeight(74)
        self.text_edit.textChanged.connect(self._schedule_preview)
        form.addRow("Текст:", self.text_edit)

        self.font_combo = QComboBox()
        for family in ("Inter", "Segoe UI", "Arial", "Montserrat", "Roboto Condensed",
                       "Georgia", "Impact", "Courier New", "Verdana"):
            self.font_combo.addItem(family)
        self.font_combo.setEditable(True)
        self.font_combo.setCurrentText(self.result_layer.font_family)
        self.font_combo.currentTextChanged.connect(self._schedule_preview)
        form.addRow("Шрифт:", self.font_combo)

        size_row = QWidget()
        size_layout = QHBoxLayout(size_row)
        size_layout.setContentsMargins(0, 0, 0, 0)
        self.size_spin = QSpinBox()
        self.size_spin.setRange(10, 400)
        self.size_spin.setValue(int(self.result_layer.size))
        self.size_spin.valueChanged.connect(self._schedule_preview)
        size_layout.addWidget(self.size_spin)
        self.shadow_check = QCheckBox("тень")
        self.shadow_check.setChecked(self.result_layer.shadow)
        self.shadow_check.toggled.connect(self._schedule_preview)
        size_layout.addWidget(self.shadow_check)
        form.addRow("Размер:", size_row)

        color_row = QWidget()
        color_layout = QHBoxLayout(color_row)
        color_layout.setContentsMargins(0, 0, 0, 0)
        self.color_edit = QLineEdit(self.result_layer.color)
        self.color_edit.setFixedWidth(90)
        self.color_edit.textChanged.connect(self._schedule_preview)
        color_layout.addWidget(self.color_edit)
        self.outline_edit = QLineEdit(self.result_layer.border_color)
        self.outline_edit.setFixedWidth(90)
        self.outline_edit.textChanged.connect(self._schedule_preview)
        color_layout.addWidget(QLabel("обводка"))
        color_layout.addWidget(self.outline_edit)
        color_layout.addStretch(1)
        form.addRow("Цвет:", color_row)

        self.pos_combo = QComboBox()
        for key, label, y_expr in (
            ("top", "Сверху", "80"),
            ("center", "По центру", "(h-text_h)/2"),
            ("bottom", "Снизу", "h-text_h-80"),
            ("lower_third", "Lower third", "h*0.72"),
        ):
            self.pos_combo.addItem(label, (key, y_expr))
        self.pos_combo.currentIndexChanged.connect(self._on_position)
        form.addRow("Позиция:", self.pos_combo)

        self.anim_combo = QComboBox()
        for name in ANIMATIONS:
            self.anim_combo.addItem(name, name)
        index = self.anim_combo.findData(self.result_layer.animation)
        self.anim_combo.setCurrentIndex(max(0, index))
        self.anim_combo.currentIndexChanged.connect(self._schedule_preview)
        form.addRow("Анимация:", self.anim_combo)

        time_row = QWidget()
        time_layout = QHBoxLayout(time_row)
        time_layout.setContentsMargins(0, 0, 0, 0)
        self.start_spin = QDoubleSpinBox()
        self.start_spin.setRange(0, 9999)
        self.start_spin.setDecimals(2)
        self.start_spin.setSuffix(" с")
        self.start_spin.setValue(float(self.result_layer.start))
        time_layout.addWidget(self.start_spin)
        self.duration_spin = QDoubleSpinBox()
        self.duration_spin.setRange(0.2, 9999)
        self.duration_spin.setDecimals(2)
        self.duration_spin.setSuffix(" с")
        self.duration_spin.setValue(float(self.result_layer.duration or 3.0))
        time_layout.addWidget(self.duration_spin)
        form.addRow("Время:", time_row)

        self.box_check = QCheckBox("Подложка под текстом")
        self.box_check.setChecked(self.result_layer.box)
        self.box_check.toggled.connect(self._schedule_preview)
        form.addRow("", self.box_check)

        body.addWidget(left, 1)

        right = QFrame()
        right.setObjectName("Panel")
        right.setMinimumWidth(240)
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(10, 10, 10, 10)
        right_layout.addWidget(QLabel("Превью"))
        self.preview_label = QLabel()
        self.preview_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.preview_label.setMinimumSize(220, 220)
        self.preview_label.setStyleSheet(
            f"background: {color('BG')}; border: 1px solid {color('BORDER')};"
            f" border-radius: 8px;")
        right_layout.addWidget(self.preview_label, 1)
        hint = QLabel("Слой появится на плейхеде таймлайна." if has_clip else
                      "Клип не выбран — слой будет добавлен поверх всей сцены.")
        hint.setObjectName("Tiny")
        hint.setWordWrap(True)
        right_layout.addWidget(hint)
        body.addWidget(right, 0)
        layout.addLayout(body, 1)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok |
                                   QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Добавить")
        buttons.button(QDialogButtonBox.StandardButton.Ok).setObjectName("Primary")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self._on_accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)
        self._schedule_preview()

    # ================================================================ логика
    def _read_layer(self) -> TextLayer:
        """Прочитать значения полей в слой."""
        layer = TextLayer(
            text=self.text_edit.toPlainText().strip() or "Ваш текст",
            font_family=self.font_combo.currentText(),
            size=int(self.size_spin.value()),
            shadow=self.shadow_check.isChecked(),
            color=self.color_edit.text().strip() or "#FFFFFF",
            border_color=self.outline_edit.text().strip() or "#000000",
            animation=self.anim_combo.currentData() or "none",
            start=float(self.start_spin.value()),
            duration=float(self.duration_spin.value()),
            box=self.box_check.isChecked(),
            y=getattr(self, "_y_expr", "h-text_h-80"),
        )
        return layer

    def layer(self) -> TextLayer:
        """Итоговый слой (после accept)."""
        return self._read_layer()

    def _schedule_preview(self) -> None:
        """Запустить отложенный рендер превью."""
        self._preview_timer.start()

    def _render_preview(self) -> None:
        """Отрисовать слой и показать в QLabel."""
        try:
            target = render_text_png(self._read_layer(), 480, 270)
            data = Path(target).read_bytes()
            Path(target).unlink(missing_ok=True)
        except Exception as exc:
            LOG.warning("Превью текста не отрисовалось: %s", exc)
            self.preview_label.setText("Ошибка превью")
            return
        image = QImage.fromData(data)
        if image.isNull():
            self.preview_label.setText("—")
            return
        self.preview_label.setPixmap(QPixmap.fromImage(image).scaled(
            240, 240, Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation))

    def _on_preset(self, _index: int) -> None:
        """Применить готовый пресет к полям."""
        name = self.preset_combo.currentData()
        if not name:
            return
        self.result_layer.apply_preset(name)
        self._sync_fields(self.result_layer)
        self._schedule_preview()

    def _on_position(self, _index: int = 0) -> None:
        """Позиция текста: меняем вертикальное выражение FFmpeg."""
        data = self.pos_combo.currentData()
        if data:
            self._y_expr = data[1]
        self._schedule_preview()

    def _sync_fields(self, layer: TextLayer) -> None:
        """Перенести значения слоя в поля формы."""
        self.font_combo.setCurrentText(layer.font_family)
        self.size_spin.setValue(int(layer.size))
        self.color_edit.setText(layer.color)
        self.outline_edit.setText(layer.border_color)
        index = self.anim_combo.findData(layer.animation)
        if index >= 0:
            self.anim_combo.setCurrentIndex(index)
        self.box_check.setChecked(layer.box)

    def _on_accept(self) -> None:
        """Собрать слой и закрыть диалог."""
        self.result_layer = self._read_layer()
        self.accept()
