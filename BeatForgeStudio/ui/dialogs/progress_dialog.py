"""Диалог прогресса длительных операций: экспорт, загрузка, публикация.

Диалог умеет:

* показывать процент, этап, скорость и оставшееся время;
* переключаться в режим «неопределённый прогресс» (когда точный процент
  неизвестен, например FFmpeg не сообщает прогресс);
* отменять операцию (через колбэк ``cancel_callable``);
* по завершении показать результат: путь к файлу, кнопку «Открыть папку»,
  ссылку для «экспорта ссылкой» и кнопку «Копировать».

Экспорт запускается в :class:`core.exporter.ExportTask` (QThread), поэтому
интерфейс не замерзает.
"""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Callable, Dict, Optional

from PyQt6.QtCore import Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QGuiApplication
from PyQt6.QtWidgets import (QDialog, QFrame, QHBoxLayout, QLabel,
                             QProgressBar, QPushButton, QVBoxLayout, QWidget)

from ui.theme import color

LOG = logging.getLogger(__name__)


class ProgressDialog(QDialog):
    """Модальный диалог прогресса с отменой.

    Сигналы:
        finishedWithPath(str): Операция успешно завершена.
        failedWith(str): Ошибка.
        cancelled(): Пользователь отменил операцию.
    """

    finishedWithPath = pyqtSignal(str)
    failedWith = pyqtSignal(str)
    cancelled = pyqtSignal()

    def __init__(self, title: str = "Экспорт", parent: Optional[QWidget] = None,
                 cancel_callable: Optional[Callable[[], None]] = None,
                 allow_close_on_done: bool = True) -> None:
        """Args:
            title: Заголовок окна и подпись.
            parent: Родитель.
            cancel_callable: Что вызвать при «Отменить».
            allow_close_on_done: Показывать «Закрыть» после завершения.
        """
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setMinimumWidth(520)
        self.setModal(True)
        self._cancel_callable = cancel_callable
        self._allow_close = allow_close_on_done
        self._started = time.monotonic()
        self._done = False
        self._upload_url = ""
        self._result_path = ""
        self._build_ui(title)
        self._ticker = QTimer(self)
        self._ticker.setInterval(1000)
        self._ticker.timeout.connect(self._update_eta)
        self._ticker.start()

    # ================================================================ UI
    def _build_ui(self, title: str) -> None:
        """Собрать интерфейс."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(22, 20, 22, 18)
        layout.setSpacing(12)

        self.title_label = QLabel(title)
        self.title_label.setObjectName("H2")
        layout.addWidget(self.title_label)

        self.stage_label = QLabel("Подготовка…")
        self.stage_label.setObjectName("Muted")
        layout.addWidget(self.stage_label)

        self.bar = QProgressBar()
        self.bar.setRange(0, 100)
        self.bar.setValue(0)
        self.bar.setTextVisible(True)
        self.bar.setFixedHeight(12)
        layout.addWidget(self.bar)

        info_row = QHBoxLayout()
        self.percent_label = QLabel("0%")
        self.percent_label.setObjectName("Accent")
        info_row.addWidget(self.percent_label)
        info_row.addStretch(1)
        self.eta_label = QLabel("")
        self.eta_label.setObjectName("Muted")
        info_row.addWidget(self.eta_label)
        layout.addLayout(info_row)

        self.result_frame = QFrame()
        self.result_frame.setObjectName("Card")
        result_layout = QVBoxLayout(self.result_frame)
        result_layout.setContentsMargins(12, 10, 12, 10)
        result_layout.setSpacing(8)
        self.result_label = QLabel("")
        self.result_label.setWordWrap(True)
        self.result_label.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse)
        result_layout.addWidget(self.result_label)

        buttons_row = QHBoxLayout()
        self.open_button = QPushButton("Открыть папку")
        self.open_button.setObjectName("Ghost")
        self.open_button.clicked.connect(self._open_folder)
        buttons_row.addWidget(self.open_button)
        self.copy_button = QPushButton("Копировать ссылку")
        self.copy_button.setObjectName("Ghost")
        self.copy_button.clicked.connect(self._copy_link)
        buttons_row.addWidget(self.copy_button)
        buttons_row.addStretch(1)
        result_layout.addLayout(buttons_row)
        self.result_frame.setVisible(False)
        layout.addWidget(self.result_frame)

        bottom = QHBoxLayout()
        bottom.addStretch(1)
        self.cancel_button = QPushButton("Отменить")
        self.cancel_button.setObjectName("Danger")
        self.cancel_button.clicked.connect(self._on_cancel)
        bottom.addWidget(self.cancel_button)
        self.close_button = QPushButton("Закрыть")
        self.close_button.setObjectName("Primary")
        self.close_button.clicked.connect(self.accept)
        self.close_button.setVisible(False)
        bottom.addWidget(self.close_button)
        layout.addLayout(bottom)

    # ================================================================ API
    def set_progress(self, percent: int, stage: str = "") -> None:
        """Обновить прогресс.

        Args:
            percent: 0..100 (отрицательное — неопределённый прогресс).
            stage: Текстовый этап.
        """
        if percent < 0:
            self.bar.setRange(0, 0)
            self.percent_label.setText("…")
        else:
            if self.bar.maximum() == 0:
                self.bar.setRange(0, 100)
            self.bar.setValue(max(0, min(100, int(percent))))
            self.percent_label.setText(f"{int(percent)}%")
        if stage:
            self.stage_label.setText(stage)

    def set_upload_url(self, url: str) -> None:
        """Показать ссылку на файл (экспорт ссылкой)."""
        self._upload_url = url
        self.copy_button.setVisible(bool(url))

    def finish(self, path: str, message: str = "") -> None:
        """Отметить успешное завершение.

        Args:
            path: Путь к готовому файлу.
            message: Дополнительный текст.
        """
        self._done = True
        self._result_path = path
        self._ticker.stop()
        self.bar.setRange(0, 100)
        self.bar.setValue(100)
        self.percent_label.setText("100%")
        self.stage_label.setText(message or "Готово")
        self.title_label.setText("Готово ✓")
        self.result_label.setText(
            f"Файл: <b>{Path(path).name}</b><br>"
            f"<span style='color:{color('TEXT_MUTED')}'>{Path(path).parent}</span>"
            + (f"<br><br>Ссылка: {self._upload_url}" if self._upload_url else ""))
        self.result_frame.setVisible(True)
        self.cancel_button.setVisible(False)
        self.close_button.setVisible(True)
        self.finishedWithPath.emit(path)

    def fail(self, message: str) -> None:
        """Показать ошибку."""
        self._done = True
        self._ticker.stop()
        self.bar.setRange(0, 100)
        self.bar.setValue(0)
        self.stage_label.setText("Ошибка")
        self.stage_label.setStyleSheet(f"color: {color('DANGER')};")
        self.title_label.setText("Не удалось выполнить операцию")
        self.result_label.setText(message[:600])
        self.result_frame.setVisible(True)
        self.open_button.setVisible(bool(self._result_path))
        self.copy_button.setVisible(False)
        self.cancel_button.setVisible(False)
        self.close_button.setVisible(True)
        self.failedWith.emit(message)

    def cancel_available(self) -> None:
        """Показать кнопку отмены (когда операция уже стартовала)."""
        self.cancel_button.setVisible(True)

    # ================================================================ события
    def _on_cancel(self) -> None:
        """Пользователь нажал «Отменить»."""
        self.cancel_button.setEnabled(False)
        self.stage_label.setText("Отмена…")
        if self._cancel_callable:
            try:
                self._cancel_callable()
            except Exception as exc:  # pragma: no cover - защитный код
                LOG.warning("Ошибка отмены: %s", exc)
        self.cancelled.emit()
        self._done = True
        QTimer.singleShot(1200, self.reject)

    def _update_eta(self) -> None:
        """Оценить оставшееся время по прошедшему времени и проценту."""
        if self._done:
            return
        value = self.bar.value()
        elapsed = time.monotonic() - self._started
        if value > 3:
            remaining = elapsed / (value / 100.0) - elapsed
            self.eta_label.setText(f"осталось ≈ {int(remaining)} с")
        else:
            self.eta_label.setText(f"прошло {int(elapsed)} с")

    def _open_folder(self) -> None:
        """Открыть папку с результатом в системном файловом менеджере."""
        if not self._result_path:
            return
        from core.exporter import open_in_explorer

        open_in_explorer(self._result_path)

    def _copy_link(self) -> None:
        """Скопировать ссылку в буфер обмена."""
        if self._upload_url:
            QGuiApplication.clipboard().setText(self._upload_url)
            self.copy_button.setText("Скопировано ✓")
            QTimer.singleShot(1500, lambda: self.copy_button.setText("Копировать ссылку"))

    def closeEvent(self, event) -> None:  # noqa: N802
        """Не дать закрыть окно во время работы (кроме отмены)."""
        if not self._done and self.cancel_button.isVisible():
            self._on_cancel()
            event.ignore()
            return
        super().closeEvent(event)


class ExportProgressDialog(ProgressDialog):
    """Готовый диалог для :class:`core.exporter.ExportTask`."""

    def __init__(self, task, settings: Dict[str, object],
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            task: Экземпляр ``ExportTask`` (уже созданный, но не запущенный).
            settings: Словарь из :class:`ui.dialogs.export_dialog.ExportDialog`.
            parent: Родитель.
        """
        kind = "видео" if settings.get("kind") == "video" else "аудио"
        super().__init__(f"Экспорт {kind}", parent, cancel_callable=task.cancel)
        self._task = task
        self._settings = dict(settings)
        self._task.progress.connect(self.set_progress)
        self._task.finished_ok.connect(self._on_finished)
        self._task.failed.connect(self.fail)
        self._task.uploaded.connect(self._on_uploaded)
        self._task.start()
        self.cancel_available()

    def _on_uploaded(self, result) -> None:
        """Пришла ссылка после загрузки файла."""
        self.set_upload_url(getattr(result, "url", ""))
        if getattr(result, "url", ""):
            self.result_label.setText(
                f"{self.result_label.text()}<br><br>Ссылка: {result.url}")

    def _on_finished(self, path: str) -> None:
        """Экспорт завершён."""
        upload = bool(self._settings.get("upload"))
        if upload:
            self.stage_label.setText("Публикую ссылку…")
            self.bar.setRange(0, 0)
            return
        self.finish(path)
        if self._settings.get("open_folder"):
            QTimer.singleShot(300, self._open_folder)

    def reject(self) -> None:  # noqa: D401
        """Закрытие окна: отменяем задачу, если она ещё работает."""
        if not self._done:
            self._task.cancel()
        super().reject()
