"""Превью видео: кадры из FFmpeg, соотношение сторон, сетка безопасных зон.

Виджет не использует QMediaPlayer (его нет в PyQt6 без WebEngine) — кадры
приходят из :class:`core.video_engine.PreviewPlayer` в виде PNG-байтов и
отрисовываются с сохранением пропорций (letterbox) и, при желании, с
наложением сетки третей и safe-area для TikTok/Reels.
"""

from __future__ import annotations

from typing import Optional

from PyQt6.QtCore import QRect, QRectF, Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QColor, QFont, QImage, QPainter, QPen, QPixmap
from PyQt6.QtWidgets import QSizePolicy, QWidget

from core.video_engine import PreviewPlayer
from ui.theme import color


class VideoPreview(QWidget):
    """Окно превью видео с управлением и оверлеями.

    Сигналы:
        clicked(): Клик по превью.
        doubleClicked(): Двойной клик (например, на весь экран).
    """

    clicked = pyqtSignal()
    doubleClicked = pyqtSignal()

    def __init__(self, player: Optional[PreviewPlayer] = None,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            player: Плеер кадров (если None — превью пустое).
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("VideoPreview")
        self.setMinimumSize(320, 180)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self._pixmap: Optional[QPixmap] = None
        self._aspect = 16 / 9
        self._show_grid = False
        self._show_safe_area = False
        self._message = "Нет видео"
        self._hint = "Перетащите файл или добавьте клип на таймлайн"
        self._player: Optional[PreviewPlayer] = None
        self._timer = QTimer(self)
        self._timer.setInterval(66)
        self._timer.timeout.connect(self._tick)
        self._last_tick = 0.0
        if player is not None:
            self.set_player(player)

     # данные
    def set_player(self, player: PreviewPlayer) -> None:
        """Подключиться к плееру кадров."""
        self._player = player
        player.frameReady.connect(self._on_frame)
        player.positionChanged.connect(lambda _p: None)

    def set_aspect(self, width: int, height: int) -> None:
        """Задать соотношение сторон (16:9, 9:16, 1:1…)."""
        self._aspect = max(0.1, width / max(1, height))
        self.update()

    def set_grid(self, enabled: bool) -> None:
        """Показать/скрыть сетку третей."""
        self._show_grid = enabled
        self.update()

    def set_safe_area(self, enabled: bool) -> None:
        """Показать/скрыть safe-area (для TikTok)."""
        self._show_safe_area = enabled
        self.update()

    def set_message(self, message: str, hint: str = "") -> None:
        """Показать сообщение вместо кадра."""
        self._message = message
        self._hint = hint
        self._pixmap = None
        self.update()

    def set_pixmap(self, pixmap: QPixmap) -> None:
        """Показать готовый кадр (например, из QPainter-оверлея)."""
        self._pixmap = pixmap
        self.update()

    def clear(self) -> None:
        """Очистить превью."""
        self._pixmap = None
        self.set_message("Нет видео")
        self.update()

     # воспроизведение
    def play(self) -> None:
        """Запустить показ кадров."""
        if self._player is None:
            return
        self._player.play()
        import time

        self._last_tick = time.monotonic()
        self._timer.start()

    def pause(self) -> None:
        """Пауза."""
        self._timer.stop()
        if self._player:
            self._player.pause()

    def stop(self) -> None:
        """Стоп."""
        self._timer.stop()
        if self._player:
            self._player.stop()

    def _tick(self) -> None:
        import time

        now = time.monotonic()
        dt = max(0.0, now - self._last_tick)
        self._last_tick = now
        if self._player:
            self._player.tick(dt)

     # события
    def _on_frame(self, data: bytes) -> None:
        image = QImage.fromData(data)
        if image.isNull():
            return
        self._pixmap = QPixmap.fromImage(image)
        self.update()

    def resizeEvent(self, event) -> None:  # noqa: N802
        super().resizeEvent(event)

    def mousePressEvent(self, event) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit()
        super().mousePressEvent(event)

    def mouseDoubleClickEvent(self, event) -> None:  # noqa: N802
        self.doubleClicked.emit()
        super().mouseDoubleClickEvent(event)

     # отрисовка
    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)
        painter.fillRect(self.rect(), QColor("0A0A0A"))

        content = self._content_rect()
        if self._pixmap is not None and not self._pixmap.isNull():
            scaled = self._pixmap.scaled(content.size(),
                                         Qt.AspectRatioMode.KeepAspectRatio,
                                         Qt.TransformationMode.SmoothTransformation)
            x = content.x() + (content.width() - scaled.width()) // 2
            y = content.y() + (content.height() - scaled.height()) // 2
            painter.drawPixmap(x, y, scaled)
            frame_rect = QRect(x, y, scaled.width(), scaled.height())
        else:
            painter.setPen(QColor(color("TEXT_MUTED")))
            font = painter.font()
            font.setPointSize(12)
            painter.setFont(font)
            painter.drawText(content, Qt.AlignmentFlag.AlignCenter, self._message)
            if self._hint:
                font.setPointSize(9)
                painter.setFont(font)
                painter.setPen(QColor(color("TEXT_DISABLED")))
                painter.drawText(content.adjusted(0, 28, 0, 0),
                                 Qt.AlignmentFlag.AlignCenter, self._hint)
            frame_rect = content

        if self._show_grid:
            painter.setPen(QPen(QColor(255, 255, 255, 60), 1, Qt.PenStyle.DashLine))
            for i in (1, 2):
                x = frame_rect.left() + frame_rect.width() * i / 3
                y = frame_rect.top() + frame_rect.height() * i / 3
                painter.drawLine(int(x), frame_rect.top(), int(x), frame_rect.bottom())
                painter.drawLine(frame_rect.left(), int(y), frame_rect.right(), int(y))

        if self._show_safe_area:
            painter.setPen(QPen(QColor(0, 191, 255, 120), 1, Qt.PenStyle.DotLine))
            margin_x = frame_rect.width() * 0.06
            margin_y = frame_rect.height() * 0.12
            safe = QRectF(frame_rect).adjusted(margin_x, margin_y, -margin_x, -margin_y)
            painter.drawRect(safe)
            painter.setPen(QColor(0, 191, 255, 140))
            small = painter.font()
            small.setPointSize(7)
            painter.setFont(small)
            painter.drawText(safe.adjusted(4, 4, 0, 0), Qt.AlignmentFlag.AlignLeft, "safe area")

        painter.setPen(QPen(QColor(color("BORDER")), 1))
        painter.drawRect(frame_rect.adjusted(0, 0, -1, -1))
        painter.end()

    def _content_rect(self) -> QRect:
        """Вписать кадр с нужным соотношением сторон в доступную область."""
        area = self.rect().adjusted(8, 8, -8, -8)
        if area.width() <= 0 or area.height() <= 0:
            return self.rect()
        target_ratio = self._aspect
        area_ratio = area.width() / max(1, area.height())
        if area_ratio > target_ratio:
            height = area.height()
            width = int(height * target_ratio)
        else:
            width = area.width()
            height = int(width / target_ratio)
        x = area.x() + (area.width() - width) // 2
        y = area.y() + (area.height() - height) // 2
        return QRect(x, y, width, height)
