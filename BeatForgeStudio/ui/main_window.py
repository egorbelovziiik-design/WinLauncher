"""Главное окно BeatForge Studio.

Собирает всё вместе:

* сайдбар с иконками и анимированный стек вкладок (fade + slide, 300 мс);
* четыре вкладки: Поиск, Аудио, Видео, Публикация;
* транспорт, привязанный к активной вкладке (аудио — движок, видео — плейхед);
* строку состояния с прогрессом и индикатором устройств;
* меню «Файл/Правка/Вид/Помощь», горячие клавиши, drag & drop файлов;
* автосохранение проекта через :class:`core.project_manager.ProjectManager`;
* тосты для всех важных событий и QMessageBox для ошибок (без крашей).

Все тяжёлые операции запускаются в QThread/пуле потоков — главный поток
занимается только интерфейсом.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, Optional

from PyQt6.QtCore import Qt, QTimer, pyqtSignal
from PyQt6.QtGui import QAction, QCloseEvent, QKeySequence
from PyQt6.QtWidgets import (QApplication, QFileDialog, QFrame,
                             QHBoxLayout, QLabel, QMainWindow, QMessageBox,
                             QProgressBar, QPushButton, QVBoxLayout, QWidget)

from core.audio_engine import AudioBuffer, AudioEngine, PreviewManager
from core.exporter import ExportTask, open_in_explorer
from core.mixer import MixerProject
from core.project_manager import PROJECT_EXT, ProjectData, ProjectManager
from core.video_engine import VideoEngine
from social.auth_manager import AuthManager
from ui.audio_editor_tab import AudioEditorTab
from ui.dialogs.export_dialog import ExportDialog
from ui.dialogs.progress_dialog import ExportProgressDialog, ProgressDialog
from ui.dialogs.publish_dialog import PublishDialog
from ui.dialogs.settings_dialog import SettingsDialog
from ui.search_tab import SearchTab
from ui.theme import apply_theme, color
from ui.video_editor_tab import VideoEditorTab
from ui.widgets.animated_stack import AnimatedStack
from ui.widgets.common import make_heading
from ui.widgets.sidebar import NavItem, Sidebar
from ui.widgets.toast import ToastManager
from ui.widgets.transport import TransportBar
from utils.config import SESSION_FILE, get_config
from utils.logger import log_exception
from utils.workers import submit

LOG = logging.getLogger(__name__)

#: Пункты навигации.
NAV_ITEMS = [
    NavItem("search", "⌕", "Поиск", "Поиск музыки и битов (Ctrl+1)"),
    NavItem("audio", "♬", "Аудио", "Аудиоредактор (Ctrl+2)"),
    NavItem("video", "▶", "Видео", "Видеоредактор (Ctrl+3)"),
    NavItem("publish", "☁", "Публикация", "Экспорт и публикация (Ctrl+4)"),
]

#: Порядок вкладок соответствует NAV_ITEMS.
PAGE_SEARCH, PAGE_AUDIO, PAGE_VIDEO, PAGE_PUBLISH = range(4)


class MainWindow(QMainWindow):
    """Главное окно приложения.

    Сигналы:
        closing(): Окно закрывается (для отключения устройств).
    """

    closing = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель (обычно None).
        """
        super().__init__(parent)
        self._cfg = get_config()
        self.setWindowTitle("BeatForge Studio")
        self.setMinimumSize(1200, 700)
        self.resize(self._cfg.ui.window_width, self._cfg.ui.window_height)

        # --- движки и сервисы
        self.audio_engine = AudioEngine(self)
        self.preview_manager = PreviewManager(self.audio_engine, self)
        self.video_engine = VideoEngine(self)
        self.auth = AuthManager()
        self.projects = ProjectManager(self)
        self._mixer_project = MixerProject("Новый проект")

        self._build_ui()
        self._build_menus()
        self._connect_signals()
        self._restore_session()
        self._startup_checks()

    # ================================================================ UI
    def _build_ui(self) -> None:
        """Собрать интерфейс главного окна."""
        central = QWidget()
        layout = QVBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        body = QHBoxLayout()
        body.setContentsMargins(0, 0, 0, 0)
        body.setSpacing(0)

        self.sidebar = Sidebar(NAV_ITEMS)
        self.sidebar.set_collapsed(self._cfg.ui.sidebar_collapsed)
        body.addWidget(self.sidebar)

        right = QWidget()
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(0)

        self.stack = AnimatedStack(duration=self._cfg.ui.animation_ms)
        self.stack.set_animated(self._cfg.ui.animations, self._cfg.ui.animation_ms)

        self.search_tab = SearchTab(self.preview_manager)
        self.audio_tab = AudioEditorTab(self._mixer_project)
        self.audio_tab.enable_drop()
        self.video_tab = VideoEditorTab(self.video_engine)
        self.publish_page = self._build_publish_page()

        self.stack.addWidget(self.search_tab)
        self.stack.addWidget(self.audio_tab)
        self.stack.addWidget(self.video_tab)
        self.stack.addWidget(self.publish_page)
        right_layout.addWidget(self.stack, 1)

        self.transport = TransportBar()
        self.transport.set_volume(self._cfg.audio.master_volume)
        right_layout.addWidget(self.transport)

        body.addWidget(right, 1)
        layout.addLayout(body, 1)

        # --- статусбар
        status_bar = self.statusBar()
        status_bar.setSizeGripEnabled(False)
        self.status_label = QLabel("Готово")
        self.status_label.setObjectName("Muted")
        status_bar.addWidget(self.status_label, 1)

        self.busy_bar = QProgressBar()
        self.busy_bar.setFixedWidth(160)
        self.busy_bar.setFixedHeight(8)
        self.busy_bar.setTextVisible(False)
        self.busy_bar.setVisible(False)
        status_bar.addPermanentWidget(self.busy_bar)

        self.device_label = QLabel("")
        self.device_label.setObjectName("Tiny")
        status_bar.addPermanentWidget(self.device_label)

        self.project_label = QLabel("Проект не сохранён")
        self.project_label.setObjectName("Tiny")
        status_bar.addPermanentWidget(self.project_label)

        self.setCentralWidget(central)
        self.toasts = ToastManager(self)
        self.sidebar.set_selected("search")
        self.sidebar.itemSelected.connect(self._on_nav)

    def _build_publish_page(self) -> QWidget:
        """Страница «Публикация»: экспорт и загрузка в соцсети."""
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(14)

        title = make_heading("Экспорт и публикация")
        layout.addWidget(title)
        subtitle = QLabel("Соберите микс или фильм, затем выгрузите файл на диск, "
                          "ссылкой или сразу в соцсети.")
        subtitle.setObjectName("Muted")
        subtitle.setWordWrap(True)
        layout.addWidget(subtitle)

        cards = QHBoxLayout()
        cards.setSpacing(12)

        def card(icon: str, heading: str, text: str, button_text: str,
                 slot) -> QFrame:
            frame = QFrame()
            frame.setObjectName("Card")
            box = QVBoxLayout(frame)
            box.setContentsMargins(16, 14, 16, 14)
            box.setSpacing(8)
            icon_label = QLabel(icon)
            icon_font = icon_label.font()
            icon_font.setPointSize(22)
            icon_label.setFont(icon_font)
            icon_label.setStyleSheet(f"color: {color('ACCENT')};")
            box.addWidget(icon_label)
            heading_label = QLabel(heading)
            heading_label.setObjectName("H3")
            box.addWidget(heading_label)
            text_label = QLabel(text)
            text_label.setObjectName("Muted")
            text_label.setWordWrap(True)
            text_label.setMinimumHeight(44)
            text_label.setAlignment(Qt.AlignmentFlag.AlignLeft |
                                    Qt.AlignmentFlag.AlignTop)
            box.addWidget(text_label)
            box.addStretch(1)
            action = QPushButton(button_text)
            action.setObjectName("Secondary")
            action.clicked.connect(slot)
            box.addWidget(action)
            return frame

        self.export_audio_button = None
        audio_card = card("♪", "Экспорт аудио",
                          "MP3 · WAV · FLAC · OGG · M4A из аудиоредактора.", "Экспорт аудио",
                          lambda: self.open_export("audio"))
        video_card = card("▶", "Экспорт видео",
                          "MP4 · WebM · MOV · GIF с пресетами соцсетей.",
                          "Экспорт видео", lambda: self.open_export("video"))
        link_card = card("☁", "Ссылка на файл",
                         "file.io или transfer.sh — одной кнопкой.",
                         "Экспорт ссылкой", lambda: self.open_export("video", upload=True))
        publish_card = card("✈", "В соцсети",
                            "YouTube (OAuth2) и TikTok (Playwright).",
                            "Открыть публикацию", self.open_publish)
        for widget in (audio_card, video_card, link_card, publish_card):
            cards.addWidget(widget, 1)
        layout.addLayout(cards)

        self.publish_log = QLabel("Последних публикаций и экспортов пока нет")
        self.publish_log.setObjectName("Muted")
        self.publish_log.setWordWrap(True)
        layout.addWidget(self.publish_log)
        layout.addStretch(1)
        return page

    def _build_menus(self) -> None:
        """Создать меню и горячие клавиши."""
        menu = self.menuBar()

        file_menu = menu.addMenu("Файл")
        self._action(file_menu, "Новый проект", "Ctrl+N", self.new_project)
        self._action(file_menu, "Открыть проект…", "Ctrl+O", self.open_project)
        self._action(file_menu, "Сохранить", "Ctrl+S", self.save_project)
        self._action(file_menu, "Сохранить как…", "Ctrl+Shift+S", self.save_project_as)
        self._action(file_menu, "Экспортировать бандл…", "", self.export_bundle)
        self._action(file_menu, "Импортировать бандл…", "", self.import_bundle)
        file_menu.addSeparator()
        self._action(file_menu, "Экспорт аудио…", "Ctrl+E", lambda: self.open_export("audio"))
        self._action(file_menu, "Экспорт видео…", "", lambda: self.open_export("video"))
        file_menu.addSeparator()
        self._action(file_menu, "Настройки…", "Ctrl+,", self.open_settings)
        self._action(file_menu, "Выход", "Ctrl+Q", self.close)

        edit_menu = menu.addMenu("Правка")
        self._action(edit_menu, "Отменить", "Ctrl+Z", lambda: self.audio_tab.undo())
        self._action(edit_menu, "Повторить", "Ctrl+Shift+Z", lambda: self.audio_tab.redo())
        edit_menu.addSeparator()
        self._action(edit_menu, "Добавить файлы в аудио…", "Ctrl+I", self.import_audio_files)

        view_menu = menu.addMenu("Вид")
        for index, item in enumerate(NAV_ITEMS):
            self._action(view_menu, item.title, f"Ctrl+{index + 1}",
                         lambda _checked=False, i=index: self.go_to_page(i))
        view_menu.addSeparator()
        self._action(view_menu, "Свернуть сайдбар", "Ctrl+B",
                     lambda: self.sidebar.set_collapsed(not self.sidebar.collapsed()))

        help_menu = menu.addMenu("Помощь")
        self._action(help_menu, "О программе", "", self.show_about)
        self._action(help_menu, "Открыть папку логов", "", self.open_logs)

    def _action(self, menu, text: str, shortcut: str, slot) -> QAction:
        """Добавить QAction в меню (с горячей клавишей)."""
        action = QAction(text, self)
        if shortcut:
            action.setShortcut(QKeySequence(shortcut))
        action.triggered.connect(lambda _checked=False: slot())
        menu.addAction(action)
        self.addAction(action)
        return action

    # ================================================================ сигналы
    def _connect_signals(self) -> None:
        """Подключить сигналы вкладок и движков."""
        self.search_tab.addToProject.connect(self._add_search_result)
        self.search_tab.downloadFinished.connect(self._on_download_finished)
        self.search_tab.statusMessage.connect(self.show_status)
        self.audio_tab.playRequested.connect(self._play_buffer)
        self.audio_tab.stopRequested.connect(self.audio_engine.stop)
        self.audio_tab.statusMessage.connect(self.show_status)
        self.audio_tab.positionChanged.connect(self._on_audio_position)
        self.audio_tab.durationChanged.connect(self.transport.set_duration)
        self.audio_tab.projectChanged.connect(self._on_audio_project_changed)

        self.video_tab.statusMessage.connect(self.show_status)
        self.video_tab.exportRequested.connect(lambda: self.open_export("video"))
        self.video_tab.durationChanged.connect(self.transport.set_duration)
        self.video_tab.projectChanged.connect(self._mark_dirty)
        self.video_tab.playRequested.connect(self._toggle_video_play)

        self.transport.playPauseClicked.connect(self._toggle_play)
        self.transport.stopClicked.connect(self._stop)
        self.transport.skipRequested.connect(self._skip)
        self.transport.seekRequested.connect(self._seek)
        self.transport.volumeChanged.connect(self._on_volume)
        self.transport.speedChanged.connect(lambda v: self.audio_engine.set_speed(v))

        self.audio_engine.positionChanged.connect(self._on_engine_position)
        self.audio_engine.stateChanged.connect(self._on_engine_state)
        self.audio_engine.errorOccurred.connect(
            lambda message: self.show_error("Аудиодвижок", message))
        self.audio_engine.levelChanged.connect(self.transport.set_level)

        self.preview_manager.previewFailed.connect(
            lambda url, err: self.toast_error("Превью недоступно", err[:160]))
        self.preview_manager.previewStarted.connect(
            lambda url: self.show_status(f"Загрузка превью: {url[:60]}…"))

        self.projects.saved.connect(self._on_project_saved)
        self.projects.loaded.connect(self._on_project_loaded)
        self.projects.autoSaved.connect(self._on_autosaved)
        self.projects.errorOccurred.connect(
            lambda message: self.show_error("Проект", message))
        self.projects.dirtyChanged.connect(self._on_dirty_changed)

        self.sidebar.collapsedChanged.connect(self._on_sidebar_collapsed)

    # ================================================================ страницы
    def go_to_page(self, index: int) -> None:
        """Переключить вкладку с анимацией."""
        index = max(0, min(index, self.stack.count() - 1))
        self.stack.slide_to(index)
        key = NAV_ITEMS[index].key
        if self.sidebar.current_key() != key:
            self.sidebar.set_selected(key)
        self._sync_transport_mode(index)

    def _on_nav(self, key: str) -> None:
        """Выбор пункта сайдбара."""
        for index, item in enumerate(NAV_ITEMS):
            if item.key == key:
                self.go_to_page(index)
                return

    def _sync_transport_mode(self, index: int) -> None:
        """Транспорт показывает то время, которое актуально для вкладки."""
        if index == PAGE_AUDIO:
            self.transport.set_duration(self.audio_tab.project.duration)
        elif index == PAGE_VIDEO:
            self.transport.set_duration(self.video_tab.project.duration)
        else:
            self.transport.set_duration(0.0)

    # ================================================================ транспорт
    def _toggle_play(self) -> None:
        """Play/pause для текущей вкладки."""
        index = self.stack.currentIndex()
        if index == PAGE_VIDEO:
            self._toggle_video_play()
            return
        if index == PAGE_AUDIO and self.audio_tab.project.duration > 0:
            if self.audio_engine.state() == "playing":
                self.audio_engine.pause()
                return
            if self.audio_engine.duration() <= 0:
                self.audio_tab.preview_mix()
                return
            self.audio_engine.play()
            return
        if self.audio_engine.duration() > 0:
            self.audio_engine.toggle()

    def _toggle_video_play(self) -> None:
        """Воспроизведение превью видео."""
        if self.video_tab.preview.isVisible() and self.video_tab.player._playing:  # noqa: SLF001
            self.video_tab.preview.pause()
            self.transport.set_playing(False)
            return
        clip = self.video_tab._clip_at_time(self.video_tab.timeline.playhead)  # noqa: SLF001
        if clip is None:
            self.toast_info("Нечего играть", "На плейхеде нет видеоклипа")
            return
        self.video_tab.preview.play()
        self.transport.set_playing(True)

    def _stop(self) -> None:
        """Остановить текущее воспроизведение."""
        self.audio_engine.stop()
        self.video_tab.preview.stop()
        self.preview_manager.stop()
        self.transport.set_playing(False)
        self.transport.set_position(0.0)

    def _skip(self, direction: int) -> None:
        """Перемотка на ±5 секунд."""
        if self.stack.currentIndex() == PAGE_VIDEO:
            self.video_tab.seek(self.video_tab.playhead() + direction * 5)
        else:
            self.audio_engine.skip(direction)

    def _seek(self, seconds: float) -> None:
        """Перемотка по слайдеру транспорта."""
        if self.stack.currentIndex() == PAGE_VIDEO:
            self.video_tab.seek(seconds)
            self.transport.set_position(seconds)
        else:
            self.audio_engine.seek(seconds)

    def _on_volume(self, value: float) -> None:
        """Изменить громкость мастера."""
        self.audio_engine.set_volume(value)
        self._cfg.audio.master_volume = value

    def _play_buffer(self, buffer: AudioBuffer) -> None:
        """Проиграть буфер (микс или скачанный трек)."""
        self.audio_engine.set_buffer(buffer)
        self.audio_engine.play()
        self.transport.set_duration(buffer.duration)

    def _on_engine_position(self, seconds: float) -> None:
        """Позиция из аудиодвижка."""
        if self.stack.currentIndex() != PAGE_VIDEO:
            self.transport.set_position(seconds)

    def _on_audio_position(self, seconds: float) -> None:
        """Позиция из аудиоредактора (клик по волне)."""
        self.transport.set_position(seconds)

    def _on_engine_state(self, state: str) -> None:
        """Синхронизировать кнопку play/pause."""
        self.transport.set_playing(state == "playing")

    # ================================================================ проект
    def _on_audio_project_changed(self, project: MixerProject) -> None:
        """Аудиопроект изменился."""
        self._mixer_project = project
        self._mark_dirty()
        self.transport.set_duration(project.duration)

    def _mark_dirty(self) -> None:
        """Пометить проект как изменённый."""
        self.projects.mark_dirty()

    def _current_project_data(self) -> ProjectData:
        """Собрать снимок состояния для сохранения."""
        data = self.projects.current or ProjectData()
        data.audio = self.audio_tab.project
        data.video = self.video_tab.project_data()
        data.text_layers = [layer.to_dict() for layer in self.video_tab.project.text_layers]
        data.settings.update({
            "width": self.video_tab.project.width,
            "height": self.video_tab.project.height,
            "fps": self.video_tab.project.fps,
            "master_volume": self.audio_engine.volume,
        })
        return data

    def new_project(self) -> None:
        """Создать новый проект."""
        if self.projects.dirty and not self._confirm_discard():
            return
        self._mixer_project = MixerProject("Новый проект")
        self.audio_tab.set_project(self._mixer_project)
        self.video_tab.load_project_data({})
        self.projects.new_project()
        self.setWindowTitle("BeatForge Studio")
        self.project_label.setText("Проект не сохранён")
        self.show_status("Создан новый проект")
        self.toast_info("Новый проект", "Чистый лист — можно творить")

    def open_project(self) -> None:
        """Открыть проект ``.bfs``."""
        if self.projects.dirty and not self._confirm_discard():
            return
        path, _ = QFileDialog.getOpenFileName(
            self, "Открыть проект", str(self._cfg.paths.projects_dir),
            f"Проект BeatForge (*{PROJECT_EXT});;Все файлы (*)")
        if not path:
            return
        self.projects.load(path)

    def save_project(self) -> bool:
        """Сохранить проект (если путь не задан — «Сохранить как»)."""
        if self.projects.current is None or not self.projects.current.meta.path:
            return self.save_project_as()
        try:
            self.projects.save(self._current_project_data())
            return True
        except Exception as exc:
            self.show_error("Сохранение проекта", str(exc))
            return False

    def save_project_as(self) -> bool:
        """Сохранить проект под новым именем."""
        suggested = str(self._cfg.paths.projects_dir / f"project{PROJECT_EXT}")
        path, _ = QFileDialog.getSaveFileName(
            self, "Сохранить проект как", suggested,
            f"Проект BeatForge (*{PROJECT_EXT})")
        if not path:
            return False
        if not path.endswith(PROJECT_EXT):
            path += PROJECT_EXT
        try:
            if self.projects.current is None:
                self.projects.new_project("Новый проект", "mixed")
            self.projects.save_as(path, silent=True)
            self.projects.set_project(self._current_project_data())
            self.projects.save(self._current_project_data())
            return True
        except Exception as exc:
            self.show_error("Сохранение проекта", str(exc))
            return False

    def export_bundle(self) -> None:
        """Экспортировать проект вместе с медиа в папку-набор."""
        folder = QFileDialog.getExistingDirectory(
            self, "Куда сохранить бандл", str(self._cfg.paths.projects_dir))
        if not folder:
            return
        self.show_status("Собираю бандл…")
        data = self._current_project_data()
        if self.projects.current is None:
            self.projects.new_project("Новый проект", "mixed")
        self.projects.set_project(data)

        def work() -> Path:
            return self.projects.export_bundle(folder, copy_media=True)

        submit(work, on_done=lambda p: self.toast_success("Бандл готов", str(p)),
               on_error=lambda m, tb: self.show_error("Бандл", m), name="bundle-export")

    def import_bundle(self) -> None:
        """Импортировать бандл проекта (папка с project.bfs)."""
        path = QFileDialog.getExistingDirectory(
            self, "Папка бандла проекта", str(self._cfg.paths.projects_dir))
        if not path:
            return

        def work() -> ProjectData:
            return self.projects.import_bundle(path)

        submit(work, on_done=self._apply_project_data,
               on_error=lambda m, tb: self.show_error("Импорт бандла", m), name="bundle-import")

    def _on_project_saved(self, path: str) -> None:
        """Проект сохранён."""
        self.setWindowTitle(f"BeatForge Studio — {Path(path).name}")
        self.project_label.setText(Path(path).name)
        self.show_status(f"Сохранено: {Path(path).name}")

    def _on_autosaved(self, path: str) -> None:
        """Автосохранение сработало."""
        self.show_status(f"Автосохранение: {Path(path).name}")

    def _on_project_loaded(self, data: ProjectData) -> None:
        """Проект загружен — применить к вкладкам."""
        self._apply_project_data(data)

    def _apply_project_data(self, data: ProjectData) -> None:
        """Разложить данные проекта по вкладкам."""
        if data.audio is not None:
            self._mixer_project = data.audio
            self.audio_tab.set_project(data.audio)
        if data.video:
            self.video_tab.load_project_data(data.video)
        if data.meta and data.meta.name:
            self.setWindowTitle(f"BeatForge Studio — {data.meta.name}")
            self.project_label.setText(data.meta.name)
        elif data.meta and data.meta.path:
            self.setWindowTitle(f"BeatForge Studio — {Path(str(data.meta.path)).name}")
            self.project_label.setText(Path(str(data.meta.path)).name)
        missing = data.missing_media() if hasattr(data, "missing_media") else []
        if missing:
            self.show_status(f"Проект загружен, но {len(missing)} медиафайл(ов) не найдено")
        else:
            self.show_status("Проект загружен")

    def _on_dirty_changed(self, dirty: bool) -> None:
        """Индикатор несохранённых изменений."""
        marker = " •" if dirty else ""
        base = self.windowTitle().rstrip(" •")
        self.setWindowTitle(base + marker)

    def _confirm_discard(self) -> bool:
        """Спросить, сохранять ли изменения."""
        answer = QMessageBox.question(
            self, "Несохранённые изменения",
            "В проекте есть несохранённые изменения. Сохранить перед продолжением?",
            QMessageBox.StandardButton.Save | QMessageBox.StandardButton.Discard |
            QMessageBox.StandardButton.Cancel)
        if answer == QMessageBox.StandardButton.Save:
            return self.save_project()
        return answer == QMessageBox.StandardButton.Discard

    # ================================================================ экспорт
    def open_export(self, kind: str = "audio", upload: bool = False) -> None:
        """Открыть диалог экспорта и запустить задачу."""
        duration = (self.audio_tab.project.duration if kind == "audio"
                    else self.video_tab.project.duration)
        if duration <= 0:
            self.toast_warning("Нечего экспортировать",
                               "Сначала добавьте материал на таймлайн")
            return
        dialog = ExportDialog(kind, duration, "beatforge", self)
        if upload:
            dialog.upload_check.setChecked(True)
        if not dialog.exec():
            return
        settings = dialog.result_settings
        self.start_export(settings)

    def start_export(self, settings: Dict[str, Any]) -> None:
        """Запустить экспорт по настройкам из диалога.

        Args:
            settings: Словарь из :class:`ui.dialogs.export_dialog.ExportDialog`.
        """
        kind = settings.get("kind", "audio")
        out_path = settings.get("out_path")
        if not out_path:
            return
        export_fmt = str(settings.get("fmt") or ("mp3" if kind == "audio" else "mp4"))
        sub_format = "gif" if export_fmt == "gif" else None

        if kind == "audio":
            self.show_status("Готовлю микс к экспорту…")

            def prepare() -> AudioBuffer:
                buffer = self.audio_tab.project.render()
                if settings.get("normalize"):
                    from core.audio_engine import normalize_buffer as normalize

                    buffer = normalize(buffer, target_db=-1.0)
                return buffer

            submit(prepare, on_done=lambda buffer: self._start_export_task(
                buffer, settings, kind, export_fmt, sub_format),
                on_error=lambda m, tb: self.show_error("Микс", m), name="mix-render")
            return

        scene = self.video_tab.build_scene(for_export=True)
        if not scene.tracks:
            self.toast_warning("Видео пустое", "Добавьте клипы на таймлайн")
            return
        self._start_export_task(scene, settings, kind, export_fmt, sub_format)

    def _start_export_task(self, payload, settings: Dict[str, Any], kind: str,
                           export_fmt: str, sub_format: Optional[str]) -> None:
        """Создать ``ExportTask`` и показать диалог прогресса."""
        out_path = settings.get("out_path")
        try:
            task = ExportTask(
                payload, out_path, kind=kind,
                fmt=(sub_format or export_fmt),
                preset=settings.get("preset"),
                bitrate=settings.get("bitrate"),
                normalize=settings.get("normalize"),
                upload=bool(settings.get("upload")),
                upload_service=settings.get("upload_service"),
                parent=self,
            )
        except Exception as exc:
            self.show_error("Экспорт", str(exc))
            return
        dialog = ExportProgressDialog(task, settings, self)
        dialog.finished.connect(lambda: self._after_export(out_path, settings))
        self.busy_bar.setVisible(True)
        task.progress.connect(lambda p, _s: self.busy_bar.setValue(p))
        dialog.exec()
        self.busy_bar.setVisible(False)

    def _after_export(self, out_path: str, settings: Dict[str, Any]) -> None:
        """Сообщить об успешном экспорте."""
        self.publish_log.setText(f"Последний экспорт: {Path(str(out_path)).name}")
        self.toast_success("Экспорт завершён", Path(str(out_path)).name,
                          action_text="Открыть")

    def open_publish(self) -> None:
        """Открыть диалог публикации."""
        latest = self._cfg.paths.exports_dir
        candidates = sorted(Path(latest).glob("*.mp4"), key=lambda p: p.stat().st_mtime,
                            reverse=True) if Path(latest).exists() else []
        dialog = PublishDialog(str(candidates[0]) if candidates else "",
                               "BeatForge Studio", self._cfg.publish.default_tags, self)
        dialog.published.connect(self._on_published)
        dialog.exec()

    def _on_published(self, platform: str, url: str) -> None:
        """Публикация завершена."""
        self.toast_success("Опубликовано", url or platform,
                          action_text="Открыть ссылку" if url else "")
        self.publish_log.setText(
            f"Публикация: {platform} · {url or 'без ссылки'}")

    def open_settings(self) -> None:
        """Открыть диалог настроек."""
        dialog = SettingsDialog(self)
        dialog.applied.connect(self._apply_settings)
        dialog.exec()

    def _apply_settings(self) -> None:
        """Применить настройки после сохранения (тема, анимации, звук)."""
        self._cfg = get_config()
        app = QApplication.instance()
        if app is not None:
            apply_theme(app, self._cfg.ui.theme, self._cfg.ui.font_family,
                        self._cfg.ui.font_size)
        self.stack.set_animated(self._cfg.ui.animations, self._cfg.ui.animation_ms)
        self.audio_engine.set_volume(self._cfg.audio.master_volume)
        self.transport.set_volume(self._cfg.audio.master_volume)
        self.video_engine = VideoEngine(self)
        self.toast_info("Настройки применены")

    # ================================================================ поиск
    def _add_search_result(self, result) -> None:
        """Добавить трек из поиска в аудиопроект."""
        if result.file_path and Path(result.file_path).exists():
            self.audio_tab.add_files([str(result.file_path)])
            self.go_to_page(PAGE_AUDIO)
            self.toast_success("Добавлено в проект", result.title[:60])
            return
        self.show_status("Трек ещё не скачан — начинаю загрузку")
        self.search_tab._download(result)  # noqa: SLF001

    def _on_download_finished(self, download_result) -> None:
        """Скачивание из поиска завершено — подсказка и автодобавление."""
        path = str(getattr(download_result, "path", ""))
        title = str(getattr(download_result, "title", Path(path).name))
        self.toast_success("Трек скачан", title[:60])
        if self.audio_tab.project.duration <= 0:
            self.audio_tab.add_files([path])

    # ================================================================ прочее
    def import_audio_files(self) -> None:
        """Быстрый импорт аудиофайлов в проект."""
        files, _ = QFileDialog.getOpenFileNames(
            self, "Импорт аудио", str(self._cfg.paths.downloads_dir),
            "Аудио (*.wav *.mp3 *.flac *.ogg *.m4a)")
        if files:
            self.audio_tab.add_files(files)
            self.go_to_page(PAGE_AUDIO)

    def show_about(self) -> None:
        """Показать окно «О программе»."""
        QMessageBox.about(
            self, "О BeatForge Studio",
            "<h2>BeatForge Studio</h2>"
            "<p>Мультимедийная студия: поиск музыки, аудио- и видеоредактор, "
            "экспорт и публикация.</p>"
            "<p>Python 3.11 · PyQt6 · FFmpeg</p>")

    def open_logs(self) -> None:
        """Открыть папку с логами."""
        from utils.config import APP_DIR

        if APP_DIR.exists():
            open_in_explorer(str(APP_DIR))
        else:
            self.show_status(f"Папка логов: {APP_DIR}")

    def show_status(self, message: str) -> None:
        """Показать сообщение в строке состояния (на 6 с)."""
        self.status_label.setText(message)
        LOG.debug("Статус: %s", message)
        QTimer.singleShot(6000, lambda: self.status_label.setText("Готово")
                          if self.status_label.text() == message else None)

    def set_silent_errors(self, silent: bool = True) -> None:
        """Не показывать модальные окна ошибок (пакетный режим, --export-audio)."""
        self._silent_errors = bool(silent)

    def show_error(self, title: str, message: str) -> None:
        """Показать ошибку в QMessageBox и логе (без падения)."""
        LOG.error("%s: %s", title, message)
        if getattr(self, "_silent_errors", False):
            return
        QMessageBox.warning(self, title, message[:1500] or "Неизвестная ошибка")

    def toast_success(self, title: str, text: str = "", action_text: str = "") -> None:
        """Тост «успех»."""
        if self._cfg.ui.show_toasts:
            self.toasts.show(title, text, kind="success", action_text=action_text)

    def toast_error(self, title: str, text: str = "") -> None:
        """Тост «ошибка»."""
        if self._cfg.ui.show_toasts:
            self.toasts.error(title, text)

    def toast_info(self, title: str, text: str = "") -> None:
        """Тост «информация»."""
        if self._cfg.ui.show_toasts:
            self.toasts.info(title, text)

    def toast_warning(self, title: str, text: str = "") -> None:
        """Тост «предупреждение»."""
        if self._cfg.ui.show_toasts:
            self.toasts.show(title, text, kind="warning")

    def _on_sidebar_collapsed(self, collapsed: bool) -> None:
        """Запомнить состояние сайдбара."""
        self._cfg.ui.sidebar_collapsed = collapsed

    def _startup_checks(self) -> None:
        """Проверить окружение при запуске и показать статус."""
        status_bits = []
        status_bits.append("♪ звук" if self.audio_engine.device_available else "♪ звук недоступен")
        status_bits.append("▶ FFmpeg" if self.video_engine.available else "▶ FFmpeg нет")
        self.device_label.setText(" · ".join(status_bits))

        if not self.video_engine.available:
            self.toast_warning("FFmpeg не найден",
                               "Видеомонтаж и экспорт видео недоступны. Установите FFmpeg.")
        recovered = self.projects.recover_autosave() if self._cfg.ui.restore_session else None
        if recovered is not None:
            self.show_status("Восстановлено автосохранение последней сессии")

    def _restore_session(self) -> None:
        """Восстановить последний проект, если включено в настройках."""
        if not self._cfg.ui.restore_session:
            return
        try:
            recent = self.projects.recent(5)
        except Exception:
            recent = []
        for item in recent:
            raw = item.get("path") if isinstance(item, dict) else str(item)
            if not raw:
                continue
            candidate = Path(raw)
            if candidate.exists():
                self.projects.load(str(candidate))
                return

    # ================================================================ события
    def dragEnterEvent(self, event) -> None:  # noqa: N802
        """Принять перетаскивание файлов в окно."""
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event) -> None:  # noqa: N802
        """Разложить перетащенные файлы по подходящим вкладкам."""
        paths = [url.toLocalFile() for url in event.mimeData().urls() if url.isLocalFile()]
        if not paths:
            return
        audio_ext = {".wav", ".mp3", ".flac", ".ogg", ".m4a", ".aac"}
        audio = [p for p in paths if Path(p).suffix.lower() in audio_ext]
        video = [p for p in paths if p not in audio]
        if audio:
            self.audio_tab.add_files(audio)
        if video:
            self.video_tab.add_files_at(video, 0, self.video_tab.timeline.playhead)
        self.show_status(f"Импортировано файлов: {len(paths)}")
        event.acceptProposedAction()

    def closeEvent(self, event: QCloseEvent) -> None:  # noqa: N802
        """Корректно завершить: сохранить сессию, остановить движки."""
        try:
            if self.projects.dirty and not self._confirm_discard():
                event.ignore()
                return
            self._save_session()
            self.audio_engine.shutdown()
            self._cfg.ui.window_width = self.width()
            self._cfg.ui.window_height = self.height()
            self._cfg.save()
            self.closing.emit()
        except Exception:
            log_exception("Ошибка при закрытии окна")
        super().closeEvent(event)

    def _save_session(self) -> None:
        """Сохранить состояние сессии (размер окна и вкладка)."""
        try:
            import json

            SESSION_FILE.parent.mkdir(parents=True, exist_ok=True)
            SESSION_FILE.write_text(json.dumps({
                "page": self.stack.currentIndex(),
                "width": self.width(),
                "height": self.height(),
                "sidebar_collapsed": self.sidebar.collapsed(),
            }, ensure_ascii=False, indent=2), encoding="utf-8")
        except OSError as exc:
            LOG.warning("Не удалось сохранить сессию: %s", exc)
