"""Диалог субтитров: ручное редактирование, импорт/экспорт SRT/ASS/VTT и Whisper.

Сценарии:

1. **Автогенерация** — кнопка «Распознать речь» запускает Whisper
   (:func:`core.subtitle_engine.transcribe`) в фоне с прогрессом. Если пакет
   не установлен, кнопка неактивна и подсказка объясняет, что установить.
2. **Импорт** — ``.srt`` / ``.vtt`` / ``.ass`` из файла.
3. **Правка** — таблица реплик (начало, конец, текст), двойной клик по
   ячейке редактирует значение.
4. **Экспорт** — сохранение в любой из трёх форматов, а также «наложить в
   проект» (субтитры уходят в фильтры drawtext видеоредактора).
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import List, Optional

from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtWidgets import (QAbstractItemView, QComboBox, QDialog,
                             QDialogButtonBox, QFileDialog, QFormLayout,
                             QHBoxLayout, QHeaderView, QLabel, QPushButton,
                             QSpinBox, QTableWidget, QTableWidgetItem,
                             QVBoxLayout, QWidget)

from core.subtitle_engine import (SubtitleCue, SubtitleStyle, SubtitleTrack,
                                  format_srt_time, parse_timecode,
                                  transcribe, whisper_available)
from ui.dialogs.progress_dialog import ProgressDialog
from ui.dialogs.text_dialog import TextDialog
from ui.theme import color
from utils.config import get_config
from utils.workers import submit

LOG = logging.getLogger(__name__)


class _WhisperTask(QThread):
    """Фоновое распознавание речи (Whisper) с прогрессом.

    Сигналы:
        progress(int, str): Процент и статус.
        finished_ok(object): Готовая :class:`SubtitleTrack`.
        failed(str): Текст ошибки.
    """

    progress = pyqtSignal(int, str)
    finished_ok = pyqtSignal(object)
    failed = pyqtSignal(str)

    def __init__(self, media_path: str, model: str, language: str,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            media_path: Видео/аудиофайл.
            model: Размер модели (tiny/base/small/medium/large).
            language: Язык (``ru``/``auto``).
            parent: Родитель.
        """
        super().__init__(parent)
        self._path = media_path
        self._model = model
        self._language = language

    def run(self) -> None:  # noqa: D401
        """Выполнить распознавание."""
        try:
            track = transcribe(
                self._path, model_size=self._model, language=self._language,
                progress_cb=lambda p, s: self.progress.emit(int(p), s),
            )
            self.finished_ok.emit(track)
        except Exception as exc:
            LOG.exception("Whisper не смог распознать речь")
            self.failed.emit(str(exc))


class SubtitleDialog(QDialog):
    """Окно работы с субтитрами.

    Attributes:
        result_track: Итоговая дорожка субтитров после ``accept()``.
    """

    def __init__(self, media_path: str = "", parent: Optional[QWidget] = None,
                 track: Optional[SubtitleTrack] = None) -> None:
        """Args:
            media_path: Медиафайл для распознавания речи.
            parent: Родитель.
            track: Существующая дорожка для правки.
        """
        super().__init__(parent)
        self.setWindowTitle("Субтитры")
        self.setMinimumSize(760, 560)
        self._media_path = media_path
        self._task: Optional[_WhisperTask] = None
        self.result_track = track or SubtitleTrack()
        self._cfg = get_config().subtitles
        self._build_ui()
        self._reload_table()

    # ================================================================ UI
    def _build_ui(self) -> None:
        """Собрать интерфейс."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 14)
        layout.setSpacing(10)

        title = QLabel("Субтитры")
        title.setObjectName("H1")
        layout.addWidget(title)

        # --- верхняя панель
        top = QHBoxLayout()
        top.setSpacing(8)
        self.whisper_button = QPushButton("🎙 Распознать речь (Whisper)")
        self.whisper_button.setObjectName("Secondary")
        self.whisper_button.setEnabled(whisper_available() and bool(self._media_path))
        self.whisper_button.setToolTip(
            "Требуется пакет openai-whisper: pip install openai-whisper"
            if not whisper_available() else "Распознать речь в медиафайле")
        self.whisper_button.clicked.connect(self._run_whisper)
        top.addWidget(self.whisper_button)

        top.addWidget(QLabel("Модель:"))
        self.model_combo = QComboBox()
        for name in ("tiny", "base", "small", "medium", "large"):
            self.model_combo.addItem(name)
        index = self.model_combo.findText(getattr(self._cfg, "whisper_model", "base"))
        self.model_combo.setCurrentIndex(max(0, index))
        self.model_combo.setFixedWidth(100)
        top.addWidget(self.model_combo)

        top.addWidget(QLabel("Язык:"))
        self.language_combo = QComboBox()
        for code, label in (("ru", "Русский"), ("en", "English"), ("auto", "Авто")):
            self.language_combo.addItem(label, code)
        self.language_combo.setFixedWidth(110)
        top.addWidget(self.language_combo)
        top.addStretch(1)

        import_button = QPushButton("Импорт…")
        import_button.setObjectName("Ghost")
        import_button.clicked.connect(self._import_file)
        top.addWidget(import_button)
        layout.addLayout(top)

        # --- таблица реплик
        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["Начало", "Конец", "Текст"])
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setAlternatingRowColors(True)
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.itemChanged.connect(self._on_cell_changed)
        layout.addWidget(self.table, 1)

        # --- кнопки строк
        row_actions = QHBoxLayout()
        for text, slot in (("+ Реплика", self._add_cue),
                           ("− Удалить", self._remove_cue),
                           ("↑", lambda: self._move_cue(-1)),
                           ("↓", lambda: self._move_cue(1)),
                           ("Стиль…", self._edit_style)):
            button = QPushButton(text)
            button.setObjectName("Ghost")
            button.clicked.connect(slot)
            row_actions.addWidget(button)
        row_actions.addStretch(1)
        self.info_label = QLabel("")
        self.info_label.setObjectName("Muted")
        row_actions.addWidget(self.info_label)
        layout.addLayout(row_actions)

        # --- экспорт
        export_row = QHBoxLayout()
        export_row.addWidget(QLabel("Экспорт:"))
        self.format_combo = QComboBox()
        for fmt in ("srt", "vtt", "ass"):
            self.format_combo.addItem(fmt.upper(), fmt)
        self.format_combo.setFixedWidth(90)
        export_row.addWidget(self.format_combo)
        export_button = QPushButton("Сохранить файл…")
        export_button.setObjectName("Ghost")
        export_button.clicked.connect(self._export_file)
        export_row.addWidget(export_button)
        export_row.addStretch(1)
        layout.addLayout(export_row)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok |
                                   QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Применить")
        buttons.button(QDialogButtonBox.StandardButton.Ok).setObjectName("Primary")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

        if not whisper_available():
            hint = QLabel("Whisper не установлен — доступны импорт, ручная правка "
                          "и экспорт субтитров. Для автогенерации выполните: "
                          "<code>pip install openai-whisper</code>")
            hint.setObjectName("Tiny")
            hint.setWordWrap(True)
            layout.addWidget(hint)

    # ================================================================ таблица
    def _reload_table(self) -> None:
        """Перерисовать таблицу из модели."""
        self.table.blockSignals(True)
        self.table.setRowCount(0)
        for cue in self.result_track.cues:
            self._append_row(cue)
        self.table.blockSignals(False)
        self.info_label.setText(
            f"реплик: {len(self.result_track.cues)} · "
            f"длительность: {self.result_track.duration:.1f} с")

    def _append_row(self, cue: SubtitleCue) -> None:
        """Добавить строку таблицы."""
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setItem(row, 0, QTableWidgetItem(format_srt_time(cue.start)))
        self.table.setItem(row, 1, QTableWidgetItem(format_srt_time(cue.end)))
        self.table.setItem(row, 2, QTableWidgetItem(cue.text))

    def _on_cell_changed(self, item: QTableWidgetItem) -> None:
        """Обновить модель после правки ячейки."""
        row, column = item.row(), item.column()
        if row < 0 or row >= len(self.result_track.cues):
            return
        cue = self.result_track.cues[row]
        if column == 2:
            cue.text = item.text()
        else:
            try:
                value = parse_timecode(item.text())
            except ValueError:
                self.table.blockSignals(True)
                item.setText(format_srt_time(cue.start if column == 0 else cue.end))
                self.table.blockSignals(False)
                return
            if column == 0:
                cue.start = value
            else:
                cue.end = max(cue.start + 0.1, value)
        self.result_track.cues.sort(key=lambda c: c.start)

    def _add_cue(self) -> None:
        """Добавить новую реплику."""
        start = self.result_track.cues[-1].end if self.result_track.cues else 0.0
        cue = SubtitleCue(start=start, end=start + 2.0, text="Новая реплика")
        self.result_track.cues.append(cue)
        self._reload_table()
        self.table.selectRow(self.table.rowCount() - 1)

    def _remove_cue(self) -> None:
        """Удалить выбранную реплику."""
        row = self.table.currentRow()
        if 0 <= row < len(self.result_track.cues):
            del self.result_track.cues[row]
            self._reload_table()

    def _move_cue(self, delta: int) -> None:
        """Сдвинуть выбранную реплику в списке."""
        row = self.table.currentRow()
        target = row + delta
        cues = self.result_track.cues
        if 0 <= row < len(cues) and 0 <= target < len(cues):
            cues[row], cues[target] = cues[target], cues[row]
            self._reload_table()
            self.table.selectRow(target)

    def _edit_style(self) -> None:
        """Настроить стиль отображения (через диалог текста)."""
        dialog = TextDialog(True, self)
        if not dialog.exec():
            return
        layer = dialog.layer()
        style = self.result_track.style
        style.font = layer.font_family
        style.size = int(layer.size)
        style.primary = layer.color
        style.outline = layer.border_color
        style.outline_width = int(layer.border)
        self.info_label.setText(
            f"Стиль: {layer.font_family} {layer.size}px, {layer.color}")

    # ================================================================ файлы
    def _import_file(self) -> None:
        """Импортировать субтитры из файла."""
        path, _ = QFileDialog.getOpenFileName(
            self, "Импорт субтитров", str(Path.home()),
            "Субтитры (*.srt *.vtt *.ass);;Все файлы (*)")
        if not path:
            return
        try:
            self.result_track.load(path)
        except Exception as exc:
            self.info_label.setText(f"Не удалось прочитать: {exc}")
            return
        self._reload_table()

    def _export_file(self) -> None:
        """Экспортировать субтитры в выбранном формате."""
        fmt = str(self.format_combo.currentData() or "srt")
        suggested = str(Path(get_config().paths.exports_dir) /
                        f"subtitles.{fmt}")
        path, _ = QFileDialog.getSaveFileName(
            self, "Сохранить субтитры", suggested,
            f"{fmt.upper()} (*.{fmt});;Все файлы (*)")
        if not path:
            return
        try:
            self.result_track.save(path)
            self.info_label.setText(f"Сохранено: {Path(path).name}")
        except Exception as exc:
            self.info_label.setText(f"Ошибка сохранения: {exc}")

    # ================================================================ whisper
    def _run_whisper(self) -> None:
        """Запустить распознавание речи в фоне."""
        if not self._media_path or not whisper_available():
            return
        dialog = ProgressDialog("Распознавание речи", self,
                                allow_close_on_done=True)
        task = _WhisperTask(self._media_path, self.model_combo.currentText(),
                            str(self.language_combo.currentData() or "ru"), self)
        self._task = task
        dialog._cancel_callable = task.terminate  # noqa: SLF001 - аварийная отмена
        task.progress.connect(dialog.set_progress)
        task.finished_ok.connect(lambda track: (self._apply_track(track),
                                                dialog.finish(self._media_path,
                                                              "Распознано")))
        task.failed.connect(dialog.fail)
        dialog.cancel_available()
        task.start()
        dialog.exec()

    def _apply_track(self, track: SubtitleTrack) -> None:
        """Применить распознанную дорожку к диалогу."""
        track.style = self.result_track.style
        self.result_track = track
        self._reload_table()

    def accept(self) -> None:  # noqa: D401
        """Сохранить результат и закрыть окно."""
        self.result_track.cues.sort(key=lambda cue: cue.start)
        super().accept()
