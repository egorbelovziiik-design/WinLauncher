"""Toast-уведомления: slide-in справа сверху, авто-скрытие.

Пример::

    toasts = ToastManager(parent_window)
    toasts.show("Экспорт готов", "Файл: beat.mp3", kind="success")
"""

from __future__ import annotations

from typing import List, Optional

from PyQt6.QtCore import (QEvent, QEasingCurve, QObject, QPoint,
                          QPropertyAnimation, QTimer, Qt,
                          pyqtSignal)
from PyQt6.QtGui import QColor, QPainter
from PyQt6.QtWidgets import (QFrame, QGraphicsOpacityEffect, QHBoxLayout, QLabel,
                             QPushButton, QVBoxLayout, QWidget)

from ui.theme import color

KIND_ICONS = {
    "info": "ℹ️",
    "success": "✓",
    "warning": "!",
    "error": "✕",
    "upload": "☁",
    "music": "♬",
}
KIND_COLORS = {
    "info": "00BFFF",
    "success": "3FBF6F",
    "warning": "E8A33D",
    "error": "E85A5A",
    "upload": "8A7CFF",
    "music": "FF6600",
}


class Toast(QFrame):
    """Одно всплывающее уведомление."""

    closed = pyqtSignal(object)

    #: Менеджер стека (проставляется при добавлении) — нужен для пересчёта.
    _toast_manager: "ToastManager | None" = None

    def __init__(self, title: str, text: str = "", kind: str = "info",
                 timeout: int = 3000, parent: Optional[QWidget] = None,
                 action_text: str = "") -> None:
        """Args:
            title: Заголовок.
            text: Текст (может быть пустым).
            kind: ``info``/``success``/``warning``/``error``/…
            timeout: Через сколько мс скрыть (0 — не скрывать).
            parent: Родитель.
            action_text: Подпись кнопки-действия (если нужна).
        """
        super().__init__(parent)
        self.setObjectName("Toast")
        self.setFixedWidth(340)
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)

        self._timeout = timeout
        accent = KIND_COLORS.get(kind, color("ACCENT"))
        self.setStyleSheet(
            f"QFrame#Toast {{ background-color: {color('PANEL_HI')};"
            f" border: 1px solid {color('BORDER')}; border-left: 3px solid {accent};"
            f" border-radius: 10px; }}"
        )

        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 10, 8, 10)
        layout.setSpacing(10)

        icon = QLabel(KIND_ICONS.get(kind, "ℹ️"))
        font = icon.font()
        font.setPointSize(13)
        icon.setFont(font)
        icon.setAlignment(Qt.AlignmentFlag.AlignTop)
        layout.addWidget(icon)

        texts = QVBoxLayout()
        texts.setSpacing(2)
        self.title_label = QLabel(title)
        self.title_label.setObjectName("ToastTitle")
        self.title_label.setWordWrap(True)
        self.title_label.setStyleSheet(f"color: {color('TEXT')}; font-weight: 600;")
        texts.addWidget(self.title_label)
        if text:
            self.text_label = QLabel(text)
            self.text_label.setObjectName("ToastText")
            self.text_label.setWordWrap(True)
            self.text_label.setStyleSheet(f"color: {color('TEXT_MUTED')}; font-size: 9pt;")
            texts.addWidget(self.text_label)
        if action_text:
            # Кнопка-действие не должна вылезать за правую границу тоста:
            # подпись сокращаем многоточием по реальной доступной ширине.
            self.action_button = QPushButton(action_text)
            self.action_button.setObjectName("Ghost")
            self.action_button.setToolTip(action_text)
            self.action_button.setCursor(Qt.CursorShape.PointingHandCursor)
            self.action_button.clicked.connect(self._on_action)
            self.action_button.ensurePolished()
            texts.addWidget(self.action_button, 0, Qt.AlignmentFlag.AlignLeft)
            # 12 + 8 отступы + 24 иконка + 10 зазор + 24 крестик + 10 зазор
            available = self.width() - 88
            shown = self.action_button.fontMetrics().elidedText(
                action_text, Qt.TextElideMode.ElideRight, max(60, available - 28))
            if shown != action_text:
                self.action_button.setText(shown)
        layout.addLayout(texts, 1)

        self.close_button = QPushButton("✕")
        self.close_button.setObjectName("IconButton")
        self.close_button.setFixedSize(22, 22)
        self.close_button.setCursor(Qt.CursorShape.PointingHandCursor)
        self.close_button.clicked.connect(self.dismiss)
        layout.addWidget(self.close_button, 0, Qt.AlignmentFlag.AlignTop)

        self._fit_height()

        self._effect = QGraphicsOpacityEffect(self)
        self._effect.setOpacity(0.0)
        self.setGraphicsEffect(self._effect)
        self.action_clicked = False

     # показ/скрытие
    def _fit_height(self) -> None:
        """Подобрать высоту под перенос строк (QLabel + wordWrap).

        У ``QLabel`` с ``setWordWrap(True)`` высота зависит от ширины, а
        ``sizeHint`` этого не учитывает — поэтому высоту каждой подписи
        считаем через ``fontMetrics().boundingRect`` с реальной шириной.
        """
        for label in self.findChildren(QLabel):
            if not label.wordWrap():
                continue
            width = label.width() if label.width() > 40 else self.width() - 90
            needed = label.fontMetrics().boundingRect(
                0, 0, width, 4000, int(Qt.TextFlag.TextWordWrap), label.text()).height()
            if needed > label.minimumHeight():
                label.setMinimumHeight(needed + 2)
        box = self.layout()
        if box is None:
            return
        box.activate()
        self.setFixedHeight(max(box.heightForWidth(self.width()), box.sizeHint().height()))

    def reveal(self, duration: int = 220) -> None:
        """Показать тост с плавным появлением (fade-in).

        Позицию задаёт контейнер стека (``QVBoxLayout``), поэтому координаты
        здесь не нужны.

        Args:
            duration: Длительность анимации, мс.
        """
        self._effect.setOpacity(0.0)
        self._fit_height()
        self.show()
        self.raise_()
        fade = QPropertyAnimation(self._effect, b"opacity", self)
        fade.setDuration(duration)
        fade.setStartValue(0.0)
        fade.setEndValue(1.0)
        fade.setEasingCurve(QEasingCurve.Type.OutCubic)
        fade.start(QPropertyAnimation.DeletionPolicy.DeleteWhenStopped)
        self._fade_anim = fade
        QTimer.singleShot(0, self._refit)
        if self._timeout > 0:
            QTimer.singleShot(self._timeout, self.dismiss)

    def _refit(self) -> None:
        """Уточнить высоту после того, как стали известны реальные ширины."""
        self._fit_height()
        if self._toast_manager is not None:
            self._toast_manager.refresh_layout()

    def reveal_instantly(self) -> None:
        """Показать без анимации (для тестов и «мгновенных» сценариев)."""
        self._effect.setOpacity(1.0)
        self.show()

    def dismiss(self) -> None:
        """Скрыть с анимацией и удалить."""
        if not self.isVisible():
            return
        fade = QPropertyAnimation(self._effect, b"opacity", self)
        fade.setDuration(160)
        fade.setStartValue(self._effect.opacity())
        fade.setEndValue(0.0)
        fade.finished.connect(self._after_hide)
        fade.start(QPropertyAnimation.DeletionPolicy.DeleteWhenStopped)
        self._hide_anim = fade

    def _after_hide(self) -> None:
        self.hide()
        self.closed.emit(self)
        self.deleteLater()

    def _on_action(self) -> None:
        self.action_clicked = True
        self.dismiss()


class ToastManager(QObject):
    """Менеджер стека уведомлений в правом верхнем углу окна.

    Наследуется от ``QObject`` ради фильтра событий: при изменении размера
    окна стек пересчитывается автоматически.
    """

    def __init__(self, host: QWidget, margin: int = 18, spacing: int = 8,
                 max_visible: int = 3) -> None:
        """Args:
            host: Окно/виджет, поверх которого показываются тосты.
            margin: Отступ от краёв.
            spacing: Зазор между тостами.
            max_visible: Сколько тостов держать одновременно.
        """
        super().__init__(host)
        self._host = host
        host.installEventFilter(self)
        self._margin = margin
        self._spacing = spacing
        self._max = max_visible
        self._toasts: List[Toast] = []
        self._container = QWidget(host)
        self._container.setObjectName("ToastHost")
        self._container.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self._container.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, False)
        self._stack_layout = QVBoxLayout(self._container)
        self._stack_layout.setContentsMargins(0, 0, 0, 0)
        self._stack_layout.setSpacing(self._spacing)
        self._stack_layout.setAlignment(Qt.AlignmentFlag.AlignTop |
                                        Qt.AlignmentFlag.AlignRight)

    def show(self, title: str, text: str = "", kind: str = "info",
             timeout: int = 3000, action_text: str = "") -> Toast:
        """Показать уведомление.

        Args:
            title: Заголовок.
            text: Текст.
            kind: Тип (info/success/warning/error/upload/music).
            timeout: Автоскрытие, мс (0 — не скрывать).
            action_text: Кнопка действия (пустая — без кнопки).

        Returns:
            Созданный :class:`Toast`.
        """
        toast = Toast(title, text, kind, timeout, self._container, action_text)
        toast._toast_manager = self  # noqa: SLF001 - внутренний контракт стека
        toast.closed.connect(self._on_closed)
        self._toasts.append(toast)
        # контейнер должен быть поверх окна, иначе первые тосты перекрываются
        self._container.raise_()
        self._container.adjustSize()
        self._stack_layout.addWidget(toast)
        toast.reveal()
        while len(self._toasts) > self._max:
            oldest = self._toasts.pop(0)
            self._stack_layout.removeWidget(oldest)
            oldest.deleteLater()
        # первый проход раскладки — сразу, второй — когда высоты уже известны
        self._layout()
        QTimer.singleShot(0, self._layout)
        return toast

    def success(self, title: str, text: str = "", timeout: int = 3000) -> Toast:
        """Короткий хелпер: успешное уведомление."""
        return self.show(title, text, "success", timeout)

    def error(self, title: str, text: str = "", timeout: int = 5000) -> Toast:
        """Короткий хелпер: ошибка."""
        return self.show(title, text, "error", timeout)

    def info(self, title: str, text: str = "", timeout: int = 3000) -> Toast:
        """Короткий хелпер: информация."""
        return self.show(title, text, "info", timeout)

    def eventFilter(self, obj, event) -> bool:  # noqa: N802
        """Пересчитать стек при изменении размера окна-хозяина."""
        if obj is self._host and event.type() == QEvent.Type.Resize:
            self._layout()
        return False

    def _on_closed(self, toast: Toast) -> None:
        if toast in self._toasts:
            self._toasts.remove(toast)
            self._stack_layout.removeWidget(toast)
        self._layout()

    def refresh_layout(self) -> None:
        """Пересобрать раскладку стека (после уточнения высот тостов)."""
        self._stack_layout.invalidate()
        self._container.adjustSize()
        self._layout()

    def _layout(self) -> None:
        """Пересчитать размеры и позицию контейнера со стеком уведомлений.

        Сами тосты выстраивает ``QVBoxLayout`` внутри контейнера — так высота
        считается по содержимому, и уведомления не наезжают друг на друга.
        """
        self._stack_layout.invalidate()
        self._container.adjustSize()
        x = max(self._margin, self._host.width() - self._container.width() - self._margin)
        self._container.move(x, self._margin)
        self._container.raise_()
