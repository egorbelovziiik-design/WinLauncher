"""Вкладка поиска музыки и битов.

Две «страницы» в одной: обычный поиск треков (YouTube/SoundCloud) и поиск
битов с жанровыми чипами, BPM-слайдером, тональностью и настроением.

Результаты — карточки :class:`ui.widgets.cards.ResultCard` с превью, скачиванием
и кнопкой «В проект». Всё тяжёлое (поиск, скачивание) выполняется в пуле
потоков, интерфейс не блокируется.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import List, Optional

from PyQt6.QtCore import Qt, QTimer, pyqtSignal
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QCompleter, QFrame,
                             QHBoxLayout, QLabel, QLineEdit, QPushButton,
                             QScrollArea, QSlider, QVBoxLayout, QWidget)

from core.audio_engine import PreviewManager
from search.beat_search import (GENRE_PRESETS, KEY_PRESETS, MOOD_PRESETS,
                                BeatFilter, BeatSearch)
from search.models import SearchResult
from search.soundcloud_search import SoundCloudSearch
from search.youtube_search import SearchError, YouTubeSearch
from ui.theme import color
from ui.widgets.cards import EmptyState, GenreChip, ResultCard
from ui.widgets.common import Spinner, make_heading
from utils.cache_db import get_db
from utils.config import get_config
from utils.downloader import DownloadManager
from utils.workers import submit

LOG = logging.getLogger(__name__)


class SegmentedControl(QFrame):
    """Переключатель «Музыка / Биты» в стиле сегментов iOS."""

    changed = pyqtSignal(int)

    def __init__(self, labels: List[str], parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self.setObjectName("Card")
        layout = QHBoxLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.setSpacing(4)
        self._buttons: List[QPushButton] = []
        for i, text in enumerate(labels):
            button = QPushButton(text)
            button.setObjectName("Chip")
            button.setCheckable(True)
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.clicked.connect(lambda _=False, index=i: self.set_index(index))
            layout.addWidget(button)
            self._buttons.append(button)
        self._index = 0
        self.set_index(0, emit=False)

    def set_index(self, index: int, emit: bool = True) -> None:
        """Выбрать сегмент."""
        self._index = index
        for i, button in enumerate(self._buttons):
            button.setChecked(i == index)
        if emit:
            self.changed.emit(index)

    @property
    def index(self) -> int:
        """Индекс активного сегмента."""
        return self._index


class SearchTab(QWidget):
    """Вкладка «🔍 Поиск».

    Сигналы:
        addToProject(object): Пользователь просит добавить трек в проект
            (:class:`SearchResult` с локальным файлом или без).
        downloadFinished(object): Загрузка завершена (``DownloadResult``).
        statusMessage(str): Строка статуса для главного окна.
    """

    addToProject = pyqtSignal(object)
    downloadFinished = pyqtSignal(object)
    statusMessage = pyqtSignal(str)

    def __init__(self, preview: Optional[PreviewManager] = None,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            preview: Менеджер превью (создаётся главным окном).
            parent: Родитель.
        """
        super().__init__(parent)
        self._preview = preview
        self._yt = YouTubeSearch()
        self._sc = SoundCloudSearch()
        self._beats = BeatSearch()
        self._downloads = DownloadManager(self)
        self._cards: List[ResultCard] = []
        self._results: List[SearchResult] = []
        self._mode = 0  # 0 — музыка, 1 — биты
        self._busy = False
        self._active_genre = GENRE_PRESETS[0]

        self._debounce = QTimer(self)
        self._debounce.setSingleShot(True)
        self._debounce.setInterval(550)
        self._debounce.timeout.connect(self.perform_search)

        self._build_ui()
        self._wire_downloads()
        self._load_history()

     # ====== UI
    def _build_ui(self) -> None:
        """Собрать интерфейс вкладки."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 12)
        layout.setSpacing(12)

        header = QHBoxLayout()
        title = make_heading("Поиск музыки и битов")
        header.addWidget(title)
        header.addStretch(1)
        self.mode_switch = SegmentedControl(["♬ Музыка", "◆ Биты"])
        self.mode_switch.changed.connect(self._on_mode_changed)
        header.addWidget(self.mode_switch)
        layout.addLayout(header)

         # строка поиска
        search_row = QHBoxLayout()
        search_row.setSpacing(8)
        self.search_input = QLineEdit()
        self.search_input.setObjectName("Search")
        self.search_input.setPlaceholderText("Исполнитель, трек или запрос: «Trap type beat sad 140 bpm»")
        self.search_input.returnPressed.connect(self.perform_search)
        self.search_input.textChanged.connect(lambda _t: self._debounce.start())
        self._completer = QCompleter(self)
        self._completer.setCaseSensitivity(Qt.CaseSensitivity.CaseInsensitive)
        self._completer.setFilterMode(Qt.MatchFlag.MatchContains)
        self.search_input.setCompleter(self._completer)
        search_row.addWidget(self.search_input, 1)

        self.source_combo = QComboBox()
        self.source_combo.addItem("YouTube", "youtube")
        self.source_combo.addItem("SoundCloud", "soundcloud")
        self.source_combo.addItem("Все источники", "all")
        self.source_combo.setFixedWidth(150)
        search_row.addWidget(self.source_combo)

        self.search_button = QPushButton("Найти")
        self.search_button.setObjectName("Primary")
        self.search_button.setFixedWidth(110)
        self.search_button.clicked.connect(self.perform_search)
        search_row.addWidget(self.search_button)
        layout.addLayout(search_row)

         # панель фильтров битов
        self.beat_panel = QFrame()
        self.beat_panel.setObjectName("Card")
        panel_layout = QVBoxLayout(self.beat_panel)
        panel_layout.setContentsMargins(14, 12, 14, 12)
        panel_layout.setSpacing(10)

        chips_row = QHBoxLayout()
        chips_row.setSpacing(6)
        chips_label = QLabel("Жанр")
        chips_label.setObjectName("Muted")
        chips_row.addWidget(chips_label)
        self._chips: List[GenreChip] = []
        for genre in GENRE_PRESETS:
            chip = GenreChip(genre)
            chip.clicked.connect(lambda _checked=False, g=genre: self._on_genre_chip(g))
            chips_row.addWidget(chip)
            self._chips.append(chip)
        chips_row.addStretch(1)
        panel_layout.addLayout(chips_row)
        self._chips[0].setChecked(True)

        filters_row = QHBoxLayout()
        filters_row.setSpacing(12)

        bpm_box = QHBoxLayout()
        bpm_label = QLabel("BPM")
        bpm_label.setObjectName("Muted")
        bpm_box.addWidget(bpm_label)
        self.bpm_slider = QSlider(Qt.Orientation.Horizontal)
        self.bpm_slider.setRange(60, 200)
        self.bpm_slider.setValue(140)
        self.bpm_slider.setFixedWidth(160)
        self.bpm_slider.valueChanged.connect(self._on_bpm_changed)
        bpm_box.addWidget(self.bpm_slider)
        self.bpm_value = QLabel("140")
        self.bpm_value.setObjectName("Accent")
        self.bpm_value.setFixedWidth(32)
        bpm_box.addWidget(self.bpm_value)
        self.bpm_enabled = QCheckBox("учитывать")
        self.bpm_enabled.setChecked(False)
        bpm_box.addWidget(self.bpm_enabled)
        filters_row.addLayout(bpm_box)

        self.key_combo = QComboBox()
        self.key_combo.addItems(list(KEY_PRESETS))
        self.key_combo.setFixedWidth(100)
        self.key_combo.setToolTip("Тональность")
        filters_row.addWidget(QLabel("Тональность"))
        filters_row.addWidget(self.key_combo)

        self.mood_combo = QComboBox()
        self.mood_combo.addItem("любое", "")
        for mood in MOOD_PRESETS:
            self.mood_combo.addItem(mood, mood)
        self.mood_combo.setFixedWidth(120)
        filters_row.addWidget(QLabel("Настроение"))
        filters_row.addWidget(self.mood_combo)

        self.free_check = QCheckBox("Только бесплатные")
        filters_row.addWidget(self.free_check)
        filters_row.addStretch(1)

        self.beat_search_button = QPushButton("Искать биты")
        self.beat_search_button.setObjectName("Secondary")
        self.beat_search_button.clicked.connect(self.perform_search)
        filters_row.addWidget(self.beat_search_button)
        panel_layout.addLayout(filters_row)
        self.beat_panel.setVisible(False)
        layout.addWidget(self.beat_panel)

         # статус/подсказки
        status_row = QHBoxLayout()
        self.spinner = Spinner(size=18)
        status_row.addWidget(self.spinner)
        self.status_label = QLabel("Введите запрос и нажмите «Найти»")
        self.status_label.setObjectName("Muted")
        status_row.addWidget(self.status_label)
        status_row.addStretch(1)
        self.cancel_button = QPushButton("Отменить все загрузки")
        self.cancel_button.setObjectName("Ghost")
        self.cancel_button.setVisible(False)
        self.cancel_button.clicked.connect(self._cancel_downloads)
        status_row.addWidget(self.cancel_button)
        layout.addLayout(status_row)

         # результаты
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.results_host = QWidget()
        self.results_layout = QVBoxLayout(self.results_host)
        self.results_layout.setContentsMargins(0, 0, 6, 0)
        self.results_layout.setSpacing(10)
        self.results_layout.addStretch(1)
        self.scroll.setWidget(self.results_host)
        layout.addWidget(self.scroll, 1)

        self.empty_state = EmptyState(
            "⌕", "Начните поиск",
            "Введите имя исполнителя, название трека или запрос вида "
            "«drill type beat dark 140 bpm»",
            "Показать примеры",
        )
        self.empty_state.actionClicked.connect(self._show_examples)
        self.results_layout.insertWidget(0, self.empty_state)

     # ====== данные
    def _load_history(self) -> None:
        """Подсказки из истории поиска."""
        try:
            history = get_db().search_history(40)
        except Exception:
            history = []
        from PyQt6.QtCore import QStringListModel

        self._completer.setModel(QStringListModel(history, self._completer))

    def _on_mode_changed(self, index: int) -> None:
        self._mode = index
        self.beat_panel.setVisible(index == 1)
        self.search_input.setPlaceholderText(
            "Запрос для битов: жанр, настроение, BPM…" if index == 1
            else "Исполнитель, трек или запрос: «Trap type beat»")
        self.empty_state.title_label.setText(
            "Ищем биты" if index == 1 else "Начните поиск")

    def _on_genre_chip(self, genre: str) -> None:
        self._active_genre = genre
        for chip in self._chips:
            chip.setChecked(chip.genre == genre)
        if self._mode == 1:
            self.perform_search()

    def _on_bpm_changed(self, value: int) -> None:
        self.bpm_value.setText(str(value))

    def _show_examples(self) -> None:
        examples = ["Drake type beat", "Phonk 2026", "Medkid", "Lo-Fi chill beat",
                    "Jersey Club remix", "sad piano beat"]
        self._clear_results()
        for example in examples:
            button = QPushButton(f"«{example}»")
            button.setObjectName("Ghost")
            button.setCursor(Qt.CursorShape.PointingHandCursor)
            button.clicked.connect(lambda _=False, text=example: self._use_example(text))
            wrapper = QWidget()
            row = QHBoxLayout(wrapper)
            row.setContentsMargins(0, 0, 0, 0)
            row.addWidget(button)
            row.addStretch(1)
            self.results_layout.insertWidget(self.results_layout.count() - 1, wrapper)

    def _use_example(self, text: str) -> None:
        self.search_input.setText(text)
        self.perform_search()

     # ====== поиск
    def perform_search(self) -> None:
        """Запустить поиск в фоне (музыка или биты — по активному режиму)."""
        if self._busy:
            return
        query = self.search_input.text().strip()
        source = self.source_combo.currentData()
        if self._mode == 1:
            self._search_beats()
            return
        if not query:
            if source == "all":
                self.status_label.setText("Введите запрос для поиска")
                return
            self.search_input.setFocus()
            return
        self._set_busy(True, "Ищу на YouTube и SoundCloud…")
        self._clear_results()
        self.statusMessage.emit(f"Поиск: {query}")

        def work() -> List[SearchResult]:
            results: List[SearchResult] = []
            errors: List[str] = []
            if source in ("youtube", "all"):
                try:
                    results += self._yt.search(query, limit=get_config().search.results_limit)
                except SearchError as exc:
                    errors.append(str(exc))
            if source in ("soundcloud", "all"):
                try:
                    results += self._sc.search(query, limit=get_config().search.results_limit)
                except SearchError as exc:
                    errors.append(str(exc))
            if not results and errors:
                raise SearchError("; ".join(errors))
            return results

        submit(work, on_done=self._on_results, on_error=self._on_search_error,
               name="search-music")

    def _search_beats(self) -> None:
        """Поиск битов по фильтрам."""
        flt = BeatFilter(
            genre=self._active_genre,
            mood=self.mood_combo.currentData() or "",
            bpm=self.bpm_slider.value() if self.bpm_enabled.isChecked() else None,
            key=self.key_combo.currentText().strip(),
            year=None,
            free=self.free_check.isChecked(),
            source=self.source_combo.currentData() or "youtube",
            limit=get_config().search.results_limit,
        )
        query = flt.query()
        if self.search_input.text().strip():
            query = self.search_input.text().strip()
        self.search_input.setText(query)
        self._set_busy(True, f"Ищу биты: {flt.describe()}")
        self._clear_results()
        self.statusMessage.emit(f"Поиск битов: {flt.describe()}")
        submit(lambda: self._beats.search(flt), on_done=self._on_results,
               on_error=self._on_search_error, name="search-beats")

    def _on_results(self, results: List[SearchResult]) -> None:
        self._set_busy(False)
        self._results = list(results)
        self._clear_results()
        if not results:
            self.empty_state = EmptyState("○", "Ничего не найдено",
                                         "Попробуйте другой запрос или смените фильтры")
            self.results_layout.insertWidget(0, self.empty_state)
            self.status_label.setText("Результатов нет")
            return

        cached = sum(1 for r in results if r.file_path)
        self.status_label.setText(
            f"Найдено: {len(results)}" + (f" · в кэше: {cached}" if cached else ""))
        for index, result in enumerate(results):
            card = ResultCard(result, show_genre=self._mode == 1)
            card.playRequested.connect(self._play_preview)
            card.downloadRequested.connect(self._download)
            card.addRequested.connect(self._add_to_project)
            card.doubleClicked.connect(self._add_to_project)
            self.results_layout.insertWidget(self.results_layout.count() - 1, card)
            self._cards.append(card)
            if result.file_path:
                card.set_downloaded()

    def _on_search_error(self, message: str, _traceback: str) -> None:
        self._set_busy(False)
        self.status_label.setText(f"Ошибка поиска: {message[:160]}")
        self.statusMessage.emit("Ошибка поиска")

     # ====== действия
    def _play_preview(self, result: SearchResult) -> None:
        if self._preview is None:
            self.status_label.setText("Превью недоступно (движок не инициализирован)")
            return
        if result.file_path and Path(result.file_path).exists():
             # уже скачан — играем локально
            from core.audio_engine import load_audio

            submit(lambda: load_audio(result.file_path, 44100, 2),
                   on_done=lambda buf: (self._preview_engine().set_buffer(buf),
                                        self._preview_engine().play()), name="preview-local")
            return
        self.status_label.setText(f"Превью: {result.title[:60]}…")
        self._preview.play_url(result.url)

    def _preview_engine(self):
        """Доступ к движку превью (у PreviewManager есть ссылка на AudioEngine)."""
        return self._preview._engine  # noqa: SLF001 - приватный доступ по делу

    def _download(self, result: SearchResult) -> None:
        card = self._card_for(result)
        if card is not None:
            card.set_downloading(0, "в очереди")
        self.cancel_button.setVisible(True)
        self.status_label.setText(f"Скачивание: {result.title[:60]}…")
        self._downloads.enqueue(
            result.url,
            audio_only=True,
            filename=None,
            meta={"title": result.title, "artist": result.artist,
                  "genre": result.genre, "mood": result.mood,
                  "bpm": result.bpm or "", "is_beat": result.is_beat},
        )

    def _card_for(self, result: SearchResult) -> Optional[ResultCard]:
        for card in self._cards:
            if card.result is result or (card.result.source_id == result.source_id
                                         and card.result.source == result.source):
                return card
        return None

    def _add_to_project(self, result: SearchResult) -> None:
        self.addToProject.emit(result)
        self.status_label.setText(f"Добавлено в проект: {result.title[:60]}")

    def _cancel_downloads(self) -> None:
        self._downloads.cancel_all()
        self.status_label.setText("Все загрузки отменены")

     # ====== загрузки
    def _wire_downloads(self) -> None:
        self._downloads.taskProgress.connect(self._on_download_progress)
        self._downloads.taskFinished.connect(self._on_download_done)
        self._downloads.taskFailed.connect(self._on_download_failed)
        self._downloads.queueChanged.connect(self._on_queue_changed)

    def _find_result_by_url(self, url: str) -> Optional[SearchResult]:
        return next((r for r in self._results if r.url == url), None)

    def _on_download_progress(self, url: str, percent: int, status: str) -> None:
        result = self._find_result_by_url(url)
        card = self._card_for(result) if result else None
        if card:
            card.set_downloading(percent, status)
        self.status_label.setText(f"Скачивание {percent}% · {status}")

    def _on_download_done(self, url: str, download_result) -> None:
        result = self._find_result_by_url(url)
        if result:
            result.file_path = str(download_result.path)
            card = self._card_for(result)
            if card:
                card.set_downloaded()
        self.status_label.setText(f"Готово: {Path(download_result.path).name}")
        self.statusMessage.emit("Трек скачан")
        self.downloadFinished.emit(download_result)

    def _on_download_failed(self, url: str, error: str) -> None:
        result = self._find_result_by_url(url)
        card = self._card_for(result) if result else None
        if card:
            card.set_download_failed(error)
        self.status_label.setText(f"Ошибка загрузки: {error[:160]}")

    def _on_queue_changed(self, active: int) -> None:
        self.cancel_button.setVisible(active > 0)

     # ====== сервис
    def _set_busy(self, busy: bool, status: str = "") -> None:
        self._busy = busy
        self.search_button.setEnabled(not busy)
        self.beat_search_button.setEnabled(not busy)
        if busy:
            self.spinner.start()
            if status:
                self.status_label.setText(status)
        else:
            self.spinner.stop()

    def _clear_results(self) -> None:
        for card in self._cards:
            card.setParent(None)
            card.deleteLater()
        self._cards.clear()
        while self.results_layout.count() > 1:
            item = self.results_layout.takeAt(0)
            widget = item.widget()
            if widget is not None and widget not in (self.empty_state,):
                widget.deleteLater()
        self.empty_state.setParent(None)

    def focus_search(self) -> None:
        """Фокус в строку поиска (горячая клавиша Ctrl+F)."""
        self.search_input.setFocus()
        self.search_input.selectAll()
