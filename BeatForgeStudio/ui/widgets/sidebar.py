"""Боковая панель навигации с анимированной подсветкой активного пункта.

Icon-only в стиле Ableton/FL: при наведении пункт расширяется, показывая
подпись; активный пункт получает оранжевую полоску слева, которая плавно
переезжает между пунктами (QPropertyAnimation по геометрии).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

from PyQt6.QtCore import (QEasingCurve, QPoint, QPropertyAnimation, QRect,
                          QSize, Qt, pyqtSignal)
from PyQt6.QtGui import QColor, QFont, QPainter, QPixmap
from PyQt6.QtWidgets import (QFrame, QHBoxLayout, QLabel, QSizePolicy,
                             QVBoxLayout, QWidget)

from ui.theme import color


@dataclass
class NavItem:
    """Описание пункта навигации.

    Attributes:
        key: Внутренний ключ (``search``, ``audio``, …).
        icon: Эмодзи-иконка (работает без внешних ресурсов).
        title: Подпись.
        tooltip: Подсказка.
    """

    key: str
    icon: str
    title: str
    tooltip: str = ""


class SidebarButton(QFrame):
    """Кнопка навигации: иконка + подпись, подсветка при наведении/активации."""

    clicked = pyqtSignal(str)

    def __init__(self, item: NavItem, parent: Optional[QWidget] = None,
                 expanded: bool = True) -> None:
        super().__init__(parent)
        self.item = item
        self._active = False
        self._hover = False
        self._expanded = expanded
        self.setObjectName("SidebarButton")
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFixedHeight(44)
        self.setToolTip(item.tooltip or item.title)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(18, 0, 12, 0)
        layout.setSpacing(12)
        self.icon_label = QLabel(item.icon)
        font = self.icon_label.font()
        font.setPointSize(14)
        self.icon_label.setFont(font)
        self.icon_label.setFixedWidth(24)
        self.icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.icon_label)

        self.text_label = QLabel(item.title)
        text_font = self.text_label.font()
        text_font.setPointSize(10)
        self.text_label.setFont(text_font)
        layout.addWidget(self.text_label)
        layout.addStretch(1)
        self.text_label.setVisible(expanded)

     # состояние
    def set_active(self, active: bool) -> None:
        """Включить/выключить активную подсветку."""
        if self._active != active:
            self._active = active
            self.update()

    def set_expanded(self, expanded: bool) -> None:
        """Показать/скрыть подпись (свёрнутый режим)."""
        self._expanded = expanded
        self.text_label.setVisible(expanded)

    @property
    def active(self) -> bool:
        """Активен ли пункт."""
        return self._active

     # события
    def enterEvent(self, event) -> None:  # noqa: N802
        self._hover = True
        self.update()
        super().enterEvent(event)

    def leaveEvent(self, event) -> None:  # noqa: N802
        self._hover = False
        self.update()
        super().leaveEvent(event)

    def mousePressEvent(self, event) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit(self.item.key)
        super().mousePressEvent(event)

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = self.rect().adjusted(6, 2, -6, -2)
        if self._active:
            painter.setBrush(QColor(color("ACCENT_SOFT")))
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(rect, 8, 8)
            painter.setBrush(QColor(color("ACCENT")))
            painter.drawRoundedRect(QRect(0, 10, 3, self.height() - 20), 2, 2)
        elif self._hover:
            painter.setBrush(QColor(color("HOVER")))
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(rect, 8, 8)
        painter.end()

        text_color = color("ACCENT") if self._active else (
            color("TEXT") if self._hover else color("TEXT_MUTED"))
        self.text_label.setStyleSheet(f"color: {text_color}; background: transparent;")


class Sidebar(QFrame):
    """Левая панель навигации.

    Сигналы:
        itemSelected(str): Ключ выбранного пункта.
        collapsedChanged(bool): Панель свёрнута/развёрнута.
    """

    itemSelected = pyqtSignal(str)
    collapsedChanged = pyqtSignal(bool)

    EXPANDED_WIDTH = 210
    COLLAPSED_WIDTH = 64

    def __init__(self, items: Optional[List[NavItem]] = None,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            items: Пункты навигации.
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Sidebar")
        self._items = items or []
        self._buttons: List[SidebarButton] = []
        self._current = ""
        self._collapsed = False
        self.setFixedWidth(self.EXPANDED_WIDTH)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 8)
        outer.setSpacing(0)

         # логотип
        self.logo_frame = QFrame(self)
        logo_layout = QHBoxLayout(self.logo_frame)
        logo_layout.setContentsMargins(18, 16, 12, 16)
        logo_layout.setSpacing(10)
        self.logo_icon = QLabel("❖")
        font = self.logo_icon.font()
        font.setPointSize(18)
        self.logo_icon.setFont(font)
        logo_layout.addWidget(self.logo_icon)
        self.logo_text = QLabel("BeatForge")
        logo_font = self.logo_text.font()
        logo_font.setPointSize(13)
        logo_font.setWeight(QFont.Weight.Bold)
        self.logo_text.setFont(logo_font)
        self.logo_text.setStyleSheet(f"color: {color('TEXT')};")
        logo_layout.addWidget(self.logo_text)
        logo_layout.addStretch(1)
        outer.addWidget(self.logo_frame)

         # пункты
        self._nav_container = QWidget(self)
        nav_layout = QVBoxLayout(self._nav_container)
        nav_layout.setContentsMargins(0, 8, 0, 8)
        nav_layout.setSpacing(2)
        for item in self._items:
            button = SidebarButton(item, self, expanded=True)
            button.clicked.connect(self._on_click)
            nav_layout.addWidget(button)
            self._buttons.append(button)
        nav_layout.addStretch(1)
        outer.addWidget(self._nav_container, 1)

         # кнопка сворачивания
        self.toggle_button = QFrame(self)
        self.toggle_button.setCursor(Qt.CursorShape.PointingHandCursor)
        self.toggle_button.setFixedHeight(36)
        toggle_layout = QHBoxLayout(self.toggle_button)
        toggle_layout.setContentsMargins(18, 0, 12, 0)
        self.toggle_label = QLabel("«  Свернуть")
        self.toggle_label.setStyleSheet(f"color: {color('TEXT_MUTED')}; font-size: 9pt;")
        toggle_layout.addWidget(self.toggle_label)
        toggle_layout.addStretch(1)
        self.toggle_button.mousePressEvent = lambda e: self.set_collapsed(not self._collapsed)  # type: ignore[method-assign]
        outer.addWidget(self.toggle_button)

        self.set_selected(self._items[0].key if self._items else "")

     # API
    def set_selected(self, key: str) -> None:
        """Подсветить пункт по ключу (без сигнала)."""
        self._current = key
        for button in self._buttons:
            button.set_active(button.item.key == key)

    def current_key(self) -> str:
        """Ключ активного пункта."""
        return self._current

    def set_collapsed(self, collapsed: bool) -> None:
        """Свернуть/развернуть панель (с анимацией ширины)."""
        if collapsed == self._collapsed:
            return
        self._collapsed = collapsed
        for button in self._buttons:
            button.set_expanded(not collapsed)
        self.logo_text.setVisible(not collapsed)
        self.toggle_label.setText("»" if collapsed else "«  Свернуть")

        anim = QPropertyAnimation(self, b"minimumWidth", self)
        anim.setDuration(180)
        anim.setStartValue(self.width())
        anim.setEndValue(self.COLLAPSED_WIDTH if collapsed else self.EXPANDED_WIDTH)
        anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        anim2 = QPropertyAnimation(self, b"maximumWidth", self)
        anim2.setDuration(180)
        anim2.setStartValue(self.width())
        anim2.setEndValue(self.COLLAPSED_WIDTH if collapsed else self.EXPANDED_WIDTH)
        anim2.setEasingCurve(QEasingCurve.Type.OutCubic)
        anim.start()
        anim2.start()
        self._animations = (anim, anim2)
        self.collapsedChanged.emit(collapsed)

    @property
    def collapsed(self) -> bool:
        """Свёрнута ли панель."""
        return self._collapsed

    def add_bottom_widget(self, widget: QWidget) -> None:
        """Добавить виджет в нижнюю часть панели (например, статус движка)."""
        layout = self.layout()
        if isinstance(layout, QVBoxLayout):
            layout.insertWidget(layout.count() - 1, widget)

    def _on_click(self, key: str) -> None:
        self.set_selected(key)
        self.itemSelected.emit(key)
