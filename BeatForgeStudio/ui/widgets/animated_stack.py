"""Плавное переключение вкладок: fade + slide.

``AnimatedStack`` — замена ``QStackedWidget``: при смене индекса старый виджет
уезжает влево и растворяется, новый въезжает справа (QPropertyAnimation,
``OutCubic``, 300 мс по умолчанию). Анимации можно выключить в настройках.
"""

from __future__ import annotations

from typing import Dict, Optional

from PyQt6.QtCore import (QEasingCurve, QParallelAnimationGroup, QPoint,
                          QPropertyAnimation, Qt, pyqtSignal)
from PyQt6.QtWidgets import (QFrame, QGraphicsOpacityEffect, QStackedWidget,
                             QWidget)

from utils.config import get_config


class AnimatedStack(QStackedWidget):
    """QStackedWidget с анимацией перехода между страницами.

    Сигналы:
        pageChanged(int): Индекс новой страницы после начала перехода.
    """

    pageChanged = pyqtSignal(int)

    def __init__(self, parent: Optional[QWidget] = None, duration: Optional[int] = None,
                 shift: int = 40) -> None:
        """Args:
            parent: Родитель.
            duration: Длительность анимации, мс (по умолчанию из конфига).
            shift: Насколько пикселей сдвигаются страницы.
        """
        super().__init__(parent)
        cfg = get_config().ui
        self._duration = int(duration if duration is not None else cfg.animation_ms)
        self._enabled = bool(cfg.animations)
        self._shift = shift
        self._effects: Dict[int, QGraphicsOpacityEffect] = {}
        self._anim: Optional[QParallelAnimationGroup] = None
        self._busy = False

     # настройки
    def set_animated(self, enabled: bool, duration: Optional[int] = None) -> None:
        """Включить/выключить анимацию (и при желании поменять длительность)."""
        self._enabled = bool(enabled)
        if duration is not None:
            self._duration = int(duration)

     # переход
    def slide_to(self, index: int) -> None:
        """Перейти на страницу с анимацией (fade + slide).

        Args:
            index: Индекс страницы.
        """
        if index < 0 or index >= self.count():
            return
        if index == self.currentIndex():
            self.pageChanged.emit(index)
            return
        if not self._enabled or self._busy or self.width() <= 0:
            self.setCurrentIndex(index)
            self.pageChanged.emit(index)
            return

        old_index = self.currentIndex()
        old_page = self.widget(old_index)
        new_page = self.widget(index)
        forward = index > old_index
        direction = 1 if forward else -1
        distance = min(self._shift, max(12, self.width() // 8)) * direction

        start_new = QPoint(distance, 0)
        start_old = QPoint(-distance, 0)
        new_page.setGeometry(0, 0, self.width(), self.height())
        new_page.move(start_new)
        new_page.show()
        new_page.raise_()

        group = QParallelAnimationGroup(self)
        for page, start_pos, end_pos, start_op, end_op in (
            (new_page, start_new, QPoint(0, 0), 0.0, 1.0),
            (old_page, QPoint(0, 0), start_old, 1.0, 0.0),
        ):
            effect = QGraphicsOpacityEffect(page)
            effect.setOpacity(start_op)
            page.setGraphicsEffect(effect)
            fade = QPropertyAnimation(effect, b"opacity", self)
            fade.setDuration(self._duration)
            fade.setStartValue(start_op)
            fade.setEndValue(end_op)
            fade.setEasingCurve(QEasingCurve.Type.OutCubic)
            slide = QPropertyAnimation(page, b"pos", self)
            slide.setDuration(self._duration)
            slide.setStartValue(start_pos)
            slide.setEndValue(end_pos)
            slide.setEasingCurve(QEasingCurve.Type.OutCubic)
            group.addAnimation(fade)
            group.addAnimation(slide)

        def finish() -> None:
            self._busy = False
            for page in (old_page, new_page):
                page.setGraphicsEffect(None)
            old_page.hide()
            new_page.move(0, 0)
            self.setCurrentIndex(index)
            self.widget(index).setGeometry(0, 0, self.width(), self.height())
            self.pageChanged.emit(index)

        group.finished.connect(finish)
        self._busy = True
        self._anim = group
        group.start()

    def setCurrentIndex(self, index: int) -> None:  # noqa: N802 (Qt API)
        """Переопределено: обычная смена без анимации (для внутренних нужд)."""
        super().setCurrentIndex(index)
        self.widget(index).setGeometry(0, 0, self.width(), self.height())

    def resizeEvent(self, event) -> None:  # noqa: N802
        super().resizeEvent(event)
        widget = self.currentWidget()
        if widget is not None:
            widget.setGeometry(0, 0, self.width(), self.height())
