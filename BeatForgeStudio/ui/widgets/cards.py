"""Карточки результатов поиска, плейсхолдеры обложек и drag-хелперы."""

from __future__ import annotations

import logging
from typing import Optional

from PyQt6.QtCore import (QPoint, QRectF, QSize, Qt, QTimer, QUrl, pyqtSignal)
from PyQt6.QtGui import (QColor, QFont, QLinearGradient, QPainter, QPainterPath,
                         QPen, QPixmap)
from PyQt6.QtNetwork import QNetworkAccessManager, QNetworkReply, QNetworkRequest
from PyQt6.QtWidgets import (QFrame, QHBoxLayout, QLabel, QPushButton, QSizePolicy,
                             QVBoxLayout, QWidget)

from search.models import SearchResult
from ui.theme import color

LOG = logging.getLogger(__name__)

# Общий сетевой менеджер для загрузки обложек.
_NAM: Optional[QNetworkAccessManager] = None


def network_manager() -> QNetworkAccessManager:
    """Вернуть общий QNetworkAccessManager для картинок."""
    global _NAM
    if _NAM is None:
        _NAM = QNetworkAccessManager()
    return _NAM


def placeholder_cover(seed: str, size: int = 160) -> QPixmap:
    """Сгенерировать плейсхолдер-обложку (градиент + инициалы).

    Args:
        seed: Строка, из которой берутся инициалы и цвет.
        size: Размер стороны.

    Returns:
        Готовый QPixmap.
    """
    pix = QPixmap(size, size)
    pix.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pix)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)

    hue = (abs(hash(seed)) % 360)
    grad = QLinearGradient(0, 0, size, size)
    grad.setColorAt(0.0, QColor.fromHsv(hue, 150, 90))
    grad.setColorAt(1.0, QColor.fromHsv((hue + 50) % 360, 190, 45))
    path = QPainterPath()
    path.addRoundedRect(QRectF(0, 0, size, size), 10, 10)
    painter.fillPath(path, grad)

    initials = "".join(part[0] for part in str(seed).split()[:2]).upper() or "♪"
    font = QFont("Inter")
    font.setPointSize(int(size * 0.3))
    font.setWeight(QFont.Weight.Bold)
    painter.setFont(font)
    painter.setPen(QColor(255, 255, 255, 215))
    painter.drawText(pix.rect(), Qt.AlignmentFlag.AlignCenter, initials)
    painter.end()
    return pix


class CoverLabel(QLabel):
    """Обложка с асинхронной загрузкой по URL и плейсхолдером."""

    def __init__(self, size: int = 110, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self._size = size
        self._seed = "beat"
        self._reply: Optional[QNetworkReply] = None
        self.setFixedSize(size, size)
        self.setScaledContents(False)
        self._set_placeholder()

    def _set_placeholder(self) -> None:
        self.setPixmap(placeholder_cover(self._seed, self._size))

    def load(self, url: str, seed: str = "") -> None:
        """Загрузить обложку по URL (или показать плейсхолдер).

        Args:
            url: Ссылка на изображение.
            seed: Текст для плейсхолдера (название трека).
        """
        self._seed = seed or url or "beat"
        self._set_placeholder()
        if not url or not url.startswith("http"):
            if url and not url.startswith("http"):
                pix = QPixmap(url)
                if not pix.isNull():
                    self.setPixmap(pix.scaled(self._size, self._size,
                                              Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                              Qt.TransformationMode.SmoothTransformation))
            return
        if self._reply is not None:
            self._reply.abort()
        request = QNetworkRequest(QUrl(url))
        request.setAttribute(QNetworkRequest.Attribute.CacheLoadControlAttribute,
                             QNetworkRequest.CacheLoadControl.PreferCache)
        self._reply = network_manager().get(request)
        self._reply.finished.connect(self._on_loaded)

    def _on_loaded(self) -> None:
        reply = self._reply
        self._reply = None
        if reply is None:
            return
        try:
            if reply.error() == QNetworkReply.NetworkError.NoError:
                data = reply.readAll()
                pix = QPixmap()
                if pix.loadFromData(data):
                    self.setPixmap(pix.scaled(
                        self._size, self._size,
                        Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                        Qt.TransformationMode.SmoothTransformation))
        except Exception:
            LOG.debug("Не удалось загрузить обложку", exc_info=True)
        finally:
            reply.deleteLater()


class ResultCard(QFrame):
    """Карточка результата поиска: обложка, название, метаданные, действия.

    Сигналы:
        playRequested(object): Запрос превью (:class:`SearchResult`).
        downloadRequested(object): Запрос скачивания.
        addRequested(object): Добавить в проект.
        doubleClicked(object): Двойной клик по карточке.
    """

    playRequested = pyqtSignal(object)
    downloadRequested = pyqtSignal(object)
    addRequested = pyqtSignal(object)
    doubleClicked = pyqtSignal(object)

    def __init__(self, result: SearchResult, parent: Optional[QWidget] = None,
                 show_genre: bool = False) -> None:
        """Args:
            result: Данные результата.
            parent: Родитель.
            show_genre: Показывать бейдж жанра/BPM (для битов).
        """
        super().__init__(parent)
        self.result = result
        self.setObjectName("Card")
        self.setFixedHeight(140)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(10, 10, 12, 10)
        layout.setSpacing(12)

        self.cover = CoverLabel(118)
        self.cover.load(result.thumbnail, result.title)
        layout.addWidget(self.cover)

        info = QVBoxLayout()
        info.setSpacing(4)
        title = QLabel(result.title)
        title.setWordWrap(True)
        font = title.font()
        font.setWeight(QFont.Weight.DemiBold)
        font.setPointSize(11)
        title.setFont(font)
        title.setStyleSheet(f"color: {color('TEXT')};")
        info.addWidget(title)

        meta_parts = [result.artist or "неизвестный автор", result.duration_text]
        if show_genre and result.bpm:
            meta_parts.append(f"{int(result.bpm)} BPM")
        if show_genre and result.musical_key:
            meta_parts.append(result.musical_key)
        if result.file_path:
            meta_parts.append("в кэше")
        meta = QLabel(" • ".join(p for p in meta_parts if p))
        meta.setObjectName("Muted")
        meta.setStyleSheet(f"color: {color('TEXT_MUTED')}; font-size: 9pt;")
        info.addWidget(meta)

        badges = QHBoxLayout()
        badges.setSpacing(6)
        source_names = {"youtube": "YouTube", "soundcloud": "SoundCloud",
                        "beatstars": "BeatStars", "local": "Локально"}
        for text, fg in ((source_names.get(result.source, result.source), ""),
                         (result.genre, color("ACCENT2")) if show_genre and result.genre else ("", ""),
                         (result.mood, "") if show_genre and result.mood else ("", "")):
            if not text:
                continue
            badge = QLabel(text)
            badge.setStyleSheet(
                f"background: {color('PANEL_HI')}; color: {fg or color('TEXT_MUTED')};"
                f" border-radius: 6px; padding: 2px 8px; font-size: 8pt;"
            )
            badges.addWidget(badge)
        badges.addStretch(1)
        info.addLayout(badges)
        info.addStretch(1)

        actions = QHBoxLayout()
        actions.setSpacing(6)
        self.play_button = QPushButton("▶ Превью")
        self.play_button.setObjectName("Ghost")
        self.play_button.setToolTip("Прослушать первые 30 секунд")
        self.play_button.clicked.connect(lambda: self.playRequested.emit(self.result))
        actions.addWidget(self.play_button)

        self.add_button = QPushButton("+ В проект")
        self.add_button.setObjectName("Ghost")
        self.add_button.clicked.connect(lambda: self.addRequested.emit(self.result))
        actions.addWidget(self.add_button)

        self.download_button = QPushButton("⬇ Скачать")
        self.download_button.setObjectName("Primary")
        self.download_button.setFixedHeight(30)
        self.download_button.clicked.connect(lambda: self.downloadRequested.emit(self.result))
        actions.addWidget(self.download_button)
        info.addLayout(actions)

        layout.addLayout(info, 1)
        if result.is_beat:
            beat_badge = QLabel("BEAT")
            beat_badge.setStyleSheet(
                f"background: {color('ACCENT')}; color: 14100C; border-radius: 6px;"
                f" padding: 3px 8px; font-size: 8pt; font-weight: 700;")
            layout.addWidget(beat_badge, 0, Qt.AlignmentFlag.AlignTop)

     # сервис
    def set_downloading(self, percent: int = 0, status: str = "") -> None:
        """Перевести кнопку скачивания в режим прогресса."""
        self.download_button.setEnabled(False)
        self.download_button.setText(f"⬇ {percent}%" if percent < 100 else "✓ Готово")
        if status:
            self.download_button.setToolTip(status)

    def set_downloaded(self) -> None:
        """Отметить трек как скачанный."""
        self.download_button.setEnabled(True)
        self.download_button.setText("✓ В кэше")
        self.download_button.setObjectName("Ghost")

    def set_download_failed(self, error: str = "") -> None:
        """Вернуть кнопку в исходное состояние после ошибки."""
        self.download_button.setEnabled(True)
        self.download_button.setText("⬇ Повторить")
        self.download_button.setToolTip(error)

    def enterEvent(self, event) -> None:  # noqa: N802
        self.setStyleSheet(
            f"QFrameCard {{ background-color: {color('PANEL_HI')};"
            f" border: 1px solid {color('ACCENT_DIM')}; border-radius: 10px; }}"
        )
        super().enterEvent(event)

    def leaveEvent(self, event) -> None:  # noqa: N802
        self.setStyleSheet("")
        super().leaveEvent(event)

    def mouseDoubleClickEvent(self, event) -> None:  # noqa: N802
        self.doubleClicked.emit(self.result)
        super().mouseDoubleClickEvent(event)


class EmptyState(QWidget):
    """Заглушка «ничего нет»: иконка, заголовок, подсказка и опциональная кнопка."""

    actionClicked = pyqtSignal()

    def __init__(self, icon: str = "⌕", title: str = "Пусто",
                 hint: str = "", action_text: str = "",
                 parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setSpacing(8)

        icon_label = QLabel(icon)
        font = icon_label.font()
        font.setPointSize(36)
        icon_label.setFont(font)
        icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(icon_label)
        self.icon_label = icon_label

        self.title_label = QLabel(title)
        self.title_label.setObjectName("H3")
        self.title_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.title_label)

        self.hint_label = QLabel(hint)
        self.hint_label.setObjectName("Muted")
        self.hint_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.hint_label.setWordWrap(True)
        self.hint_label.setVisible(bool(hint))
        layout.addWidget(self.hint_label)
        layout.addSpacing(4)
        self.hint_label.setFixedWidth(440)
        self.hint_label.setAlignment(Qt.AlignmentFlag.AlignHCenter |
                                     Qt.AlignmentFlag.AlignTop)
        self.hint_label.setMinimumHeight(
            self.hint_label.fontMetrics().boundingRect(
                0, 0, 440, 400, int(Qt.TextFlag.TextWordWrap), hint).height() + 6
            if hint else 0)

        if action_text:
            self.action_button = QPushButton(action_text)
            self.action_button.setObjectName("Primary")
            self.action_button.setFixedWidth(200)
            self.action_button.clicked.connect(self.actionClicked)
            layout.addWidget(self.action_button, 0, Qt.AlignmentFlag.AlignCenter)


class GenreChip(QPushButton):
    """Чип-переключатель жанра."""

    def __init__(self, genre: str, parent: Optional[QWidget] = None) -> None:
        super().__init__(genre, parent)
        self.genre = genre
        self.setObjectName("Chip")
        self.setCheckable(True)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setToolTip(f"Искать биты в жанре {genre}")
