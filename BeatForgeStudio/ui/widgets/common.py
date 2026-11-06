"""Мелкие переиспользуемые виджеты и лэйауты BeatForge Studio."""

from __future__ import annotations

from typing import Callable, List, Optional, Sequence

from PyQt6.QtCore import (QEasingCurve, QPropertyAnimation, QRect, QSize, Qt,
                          QTimer, pyqtProperty, pyqtSignal)
from PyQt6.QtGui import QColor, QFont, QIcon, QPainter, QPixmap
from PyQt6.QtWidgets import (QFrame, QGraphicsOpacityEffect, QHBoxLayout, QLabel,
                             QLayout, QPushButton, QSizePolicy, QVBoxLayout,
                             QWidget)

from ui.theme import color


class FlowLayout(QLayout):
    """Потоковый лэйаут: карточки переносятся на новую строку при нехватке места."""

    def __init__(self, parent: Optional[QWidget] = None, margin: int = 0,
                 h_spacing: int = 12, v_spacing: int = 12) -> None:
        """Args:
            parent: Родитель.
            margin: Отступы по краям.
            h_spacing: Горизонтальный зазор.
            v_spacing: Вертикальный зазор.
        """
        super().__init__(parent)
        self._items: List[QWidget] = []
        self._h_space = h_spacing
        self._v_space = v_spacing
        self.setContentsMargins(margin, margin, margin, margin)

    def addItem(self, item) -> None:  # noqa: N802 (Qt API)
        self._items.append(item)

    def count(self) -> int:
        return len(self._items)

    def itemAt(self, index: int):  # noqa: N802
        return self._items[index] if 0 <= index < len(self._items) else None

    def takeAt(self, index: int):  # noqa: N802
        return self._items.pop(index) if 0 <= index < len(self._items) else None

    def expandingDirections(self):  # noqa: N802
        return Qt.Orientation(0)

    def hasHeightForWidth(self) -> bool:  # noqa: N802
        return True

    def heightForWidth(self, width: int) -> int:  # noqa: N802
        return self._do_layout(QRect(0, 0, width, 0), test_only=True)

    def setGeometry(self, rect: QRect) -> None:  # noqa: N802
        super().setGeometry(rect)
        self._do_layout(rect, test_only=False)

    def sizeHint(self) -> QSize:  # noqa: N802
        if not self._items:
            return QSize(0, 0)
        width = self.geometry().width() or 900
        return QSize(width, self.heightForWidth(width))

    def minimumSize(self) -> QSize:  # noqa: N802
        size = QSize()
        for item in self._items:
            size = size.expandedTo(item.minimumSize())
        return size

    def _do_layout(self, rect: QRect, test_only: bool) -> int:
        """Разложить элементы; вернуть итоговую высоту."""
        x, y = rect.x(), rect.y()
        line_height = 0
        for item in self._items:
            widget = item.widget() if hasattr(item, "widget") else item
            if widget is None or (hasattr(widget, "isHidden") and widget.isHidden()):
                continue
            space = self._h_space
            next_x = x + item.sizeHint().width() + space
            if next_x - space > rect.right() and line_height > 0:
                x = rect.x()
                y += line_height + self._v_space
                next_x = x + item.sizeHint().width() + space
                line_height = 0
            if not test_only:
                item.setGeometry(QRect(x, y, item.sizeHint().width(), item.sizeHint().height()))
            x = next_x
            line_height = max(line_height, item.sizeHint().height())
        return y + line_height - rect.y()


class IconButton(QPushButton):
    """Круглая/квадратная кнопка-иконка с тултипом."""

    def __init__(self, text: str, tooltip: str = "", size: int = 34,
                 parent: Optional[QWidget] = None, object_name: str = "IconButton") -> None:
        super().__init__(text, parent)
        self.setObjectName(object_name)
        self.setToolTip(tooltip)
        self.setFixedSize(size, size)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        font = self.font()
        font.setPointSize(max(9, int(size * 0.36)))
        self.setFont(font)


class SectionTitle(QWidget):
    """Заголовок секции с подписью и правым виджетом действий."""

    def __init__(self, title: str, subtitle: str = "", parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        box = QHBoxLayout(self)
        box.setContentsMargins(0, 0, 0, 0)
        texts = QVBoxLayout()
        texts.setSpacing(2)
        self.title_label = QLabel(title)
        self.title_label.setObjectName("H2")
        texts.addWidget(self.title_label)
        self.subtitle_label = QLabel(subtitle)
        self.subtitle_label.setObjectName("Muted")
        self.subtitle_label.setVisible(bool(subtitle))
        texts.addWidget(self.subtitle_label)
        box.addLayout(texts)
        box.addStretch(1)
        self.actions = QHBoxLayout()
        self.actions.setSpacing(8)
        box.addLayout(self.actions)

    def add_action(self, widget: QWidget) -> None:
        """Добавить виджет справа от заголовка."""
        self.actions.addWidget(widget)

    def set_subtitle(self, text: str) -> None:
        """Обновить подпись."""
        self.subtitle_label.setText(text)
        self.subtitle_label.setVisible(bool(text))


class Separator(QFrame):
    """Горизонтальная линия-разделитель."""

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self.setObjectName("HLine")
        self.setFixedHeight(1)


class Badge(QLabel):
    """Небольшой бейдж-пилюля (BPM, жанр, статус)."""

    def __init__(self, text: str = "", parent: Optional[QWidget] = None,
                 background: str = "", foreground: str = "") -> None:
        super().__init__(text, parent)
        self.setObjectName("Badge")
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._bg = background
        self._fg = foreground
        if background or foreground:
            self.set_colors(background, foreground)

    def set_colors(self, background: str = "", foreground: str = "") -> None:
        """Задать цвета фона/текста (hex)."""
        bg = background or color("PANEL_HI")
        fg = foreground or color("TEXT_MUTED")
        self.setStyleSheet(
            f"background-color: {bg}; color: {fg}; border-radius: 6px;"
            f"padding: 2px 8px; font-size: 9pt;"
        )


class FadeInWidget(QWidget):
    """Виджет с анимацией появления (opacity + сдвиг вверх)."""

    def __init__(self, parent: Optional[QWidget] = None, duration: int = 260,
                 offset: int = 12) -> None:
        super().__init__(parent)
        self._effect = QGraphicsOpacityEffect(self)
        self._effect.setOpacity(1.0)
        self.setGraphicsEffect(self._effect)
        self._anim: Optional[QPropertyAnimation] = None
        self._duration = duration
        self._offset = offset

    def fade_in(self, delay_ms: int = 0) -> None:
        """Запустить появление с задержкой (staggered)."""
        self._effect.setOpacity(0.0)
        anim = QPropertyAnimation(self._effect, b"opacity", self)
        anim.setDuration(self._duration)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(QEasingCurve.Type.OutCubic)

        def start() -> None:
            self._anim = anim
            anim.start(QPropertyAnimation.DeletionPolicy.DeleteWhenStopped)

        if delay_ms > 0:
            QTimer.singleShot(delay_ms, start)
        else:
            start()


class Spinner(QWidget):
    """Мини-спиннер (вращающаяся дуга), рисуется QPainter."""

    def __init__(self, parent: Optional[QWidget] = None, size: int = 22) -> None:
        super().__init__(parent)
        self._angle = 0
        self._size = size
        self.setFixedSize(size, size)
        self._timer = QTimer(self)
        self._timer.setInterval(16)
        self._timer.timeout.connect(self._rotate)
        self.hide()

    def start(self) -> None:
        """Показать и запустить анимацию."""
        self.show()
        self._timer.start()

    def stop(self) -> None:
        """Остановить и спрятать."""
        self._timer.stop()
        self.hide()

    def _rotate(self) -> None:
        self._angle = (self._angle + 8) % 360
        self.update()

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.translate(self.width() / 2, self.height() / 2)
        painter.rotate(self._angle)
        pen = painter.pen()
        pen.setWidth(3)
        pen.setColor(QColor(color("ACCENT")))
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen)
        rect = self.rect().adjusted(4, 4, -4, -4)
        painter.drawArc(rect.translated(-self.width() / 2, -self.height() / 2), 0, 270 * 16)
        painter.end()


class ClickableFrame(QFrame):
    """QFrame, который сообщает о клике (для карточек)."""

    clicked = pyqtSignal()
    doubleClicked = pyqtSignal()

    def mousePressEvent(self, event) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit()
        super().mousePressEvent(event)

    def mouseDoubleClickEvent(self, event) -> None:  # noqa: N802
        self.doubleClicked.emit()
        super().mouseDoubleClickEvent(event)


def make_label(text: str, object_name: str = "", word_wrap: bool = False,
               parent: Optional[QWidget] = None) -> QLabel:
    """Создать QLabel с нужным objectName (для QSS)."""
    label = QLabel(text, parent)
    if object_name:
        label.setObjectName(object_name)
    label.setWordWrap(word_wrap)
    return label


def make_heading(text: str, object_name: str = "H1", pad: int = 12,
                 parent: Optional[QWidget] = None) -> QLabel:
    """QLabel-заголовок с шириной по реальному шрифту.

    Размер из QSS (``QLabel#H1``) применяется только после polish, поэтому
    ``sizeHint`` сразу после создания занижен и заголовок обрезается точками.
    """
    label = QLabel(text, parent)
    label.setObjectName(object_name)
    label.ensurePolished()
    label.setMinimumWidth(label.fontMetrics().horizontalAdvance(text) + pad)
    return label


def hline() -> Separator:
    """Вернуть готовый разделитель."""
    return Separator()


def spacer(width: int = 0, height: int = 0) -> QWidget:
    """Пустой виджет-распорка фиксированного размера."""
    widget = QWidget()
    widget.setFixedSize(max(0, width), max(0, height))
    if width == 0 and height == 0:
        widget.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
    return widget


def icon_pixmap(path: str, size: int = 24) -> QPixmap:
    """Загрузить PNG как pixmap заданного размера."""
    pix = QPixmap(path)
    if pix.isNull():
        return pix
    return pix.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatio,
                      Qt.TransformationMode.SmoothTransformation)


class SegmentedControlSimple(QFrame):
    """Простой переключатель сегментов (2–4 варианта) для шапок и диалогов.

    Сигналы:
        changed(int): Индекс выбранного сегмента.
    """

    changed = pyqtSignal(int)

    def __init__(self, labels: Sequence[str], parent: Optional[QWidget] = None) -> None:
        """Args:
            labels: Подписи сегментов.
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Card")
        self._buttons: List[QPushButton] = []
        layout = QHBoxLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.setSpacing(4)
        for index, text in enumerate(labels):
            button = QPushButton(text)
            button.setObjectName("Chip")
            button.setCheckable(True)
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.clicked.connect(lambda _checked=False, i=index: self.set_index(i))
            layout.addWidget(button)
            self._buttons.append(button)
        self._index = 0
        self.set_index(0, emit=False)

    def set_index(self, index: int, emit: bool = True) -> None:
        """Выбрать сегмент по индексу."""
        index = max(0, min(index, len(self._buttons) - 1))
        self._index = index
        for i, button in enumerate(self._buttons):
            button.setChecked(i == index)
        if emit:
            self.changed.emit(index)

    @property
    def index(self) -> int:
        """Индекс активного сегмента."""
        return self._index
