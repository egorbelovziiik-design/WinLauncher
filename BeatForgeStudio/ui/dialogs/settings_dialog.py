"""Диалог настроек: внешний вид, аудио, видео, экспорт, публикация, прочее.

Изменения применяются к :class:`utils.config.AppConfig` при нажатии
«Сохранить» (кнопка «Применить» сохраняет без закрытия). Диалог сам пишет
конфиг на диск; главное окно перечитывает конфиг и обновляет тему/анимации,
если оно подключено к сигналу :attr:`SettingsDialog.applied`.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, Optional

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QDesktopServices
from PyQt6.QtCore import QUrl
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QDialog, QDialogButtonBox,
                             QFileDialog, QFontComboBox, QFormLayout, QFrame,
                             QHBoxLayout, QLabel, QLineEdit, QListWidget,
                             QPushButton, QSpinBox, QStackedWidget,
                             QVBoxLayout, QWidget)

from ui.theme import THEMES, color
from utils.config import APP_DIR, CONFIG_FILE, get_config
from utils.ffmpeg_helper import ensure_ffmpeg

LOG = logging.getLogger(__name__)


class SettingsDialog(QDialog):
    """Окно настроек приложения.

    Сигналы:
        applied(): Настройки сохранены (нужно перерисовать тему/анимации).
    """

    applied = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None,
                 page: int = 0) -> None:
        """Args:
            parent: Родитель.
            page: Стартовая страница (0 — внешний вид).
        """
        super().__init__(parent)
        self.setWindowTitle("Настройки BeatForge Studio")
        self.setMinimumSize(760, 560)
        self._cfg = get_config()
        self._build_ui()
        self._list.setCurrentRow(page)

    # ================================================================ UI
    def _build_ui(self) -> None:
        """Собрать интерфейс."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 14)
        layout.setSpacing(12)

        title = QLabel("Настройки")
        title.setObjectName("H1")
        layout.addWidget(title)

        body = QHBoxLayout()
        body.setSpacing(14)
        self._list = QListWidget()
        self._list.setFixedWidth(190)
        self._list.addItems(["Внешний вид", "Аудио", "Видео", "Экспорт",
                             "Публикация", "Файлы и кэш", "О программе"])
        self._list.currentRowChanged.connect(self._on_page)
        body.addWidget(self._list)

        self._stack = QStackedWidget()
        self._stack.addWidget(self._page_appearance())
        self._stack.addWidget(self._page_audio())
        self._stack.addWidget(self._page_video())
        self._stack.addWidget(self._page_export())
        self._stack.addWidget(self._page_publish())
        self._stack.addWidget(self._page_files())
        self._stack.addWidget(self._page_about())
        body.addWidget(self._stack, 1)
        layout.addLayout(body, 1)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save |
                                   QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Save).setText("Сохранить")
        buttons.button(QDialogButtonBox.StandardButton.Save).setObjectName("Primary")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self._save)
        buttons.rejected.connect(self.reject)
        self._apply_button = QPushButton("Применить")
        self._apply_button.setObjectName("Secondary")
        self._apply_button.clicked.connect(lambda: self._save(close=False))
        buttons.addButton(self._apply_button, QDialogButtonBox.ButtonRole.ApplyRole)
        layout.addWidget(buttons)

    def _form_page(self) -> tuple:
        """Хелпер: страница с QFormLayout внутри скролла-подобного контейнера."""
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(4, 0, 4, 0)
        form = QFormLayout()
        form.setLabelAlignment(Qt.AlignmentFlag.AlignRight)
        form.setHorizontalSpacing(14)
        form.setVerticalSpacing(10)
        outer.addLayout(form)
        outer.addStretch(1)
        return page, form

    def _page_appearance(self) -> QWidget:
        """Внешний вид: тема, шрифт, анимации."""
        page, form = self._form_page()
        self.theme_combo = QComboBox()
        for key in THEMES:
            self.theme_combo.addItem({"dark": "Тёмная (по умолчанию)",
                                      "light": "Светлая", "amoled": "AMOLED"}.get(key, key), key)
        index = self.theme_combo.findData(self._cfg.ui.theme)
        self.theme_combo.setCurrentIndex(max(0, index))
        form.addRow("Тема:", self.theme_combo)

        self.font_combo = QFontComboBox()
        self.font_combo.setCurrentText(self._cfg.ui.font_family)
        form.addRow("Шрифт:", self.font_combo)

        self.font_size = QSpinBox()
        self.font_size.setRange(8, 18)
        self.font_size.setValue(int(self._cfg.ui.font_size))
        self.font_size.setSuffix(" pt")
        form.addRow("Размер шрифта:", self.font_size)

        self.anim_check = QCheckBox("Анимации интерфейса")
        self.anim_check.setChecked(self._cfg.ui.animations)
        form.addRow("", self.anim_check)

        self.anim_speed = QSpinBox()
        self.anim_speed.setRange(80, 800)
        self.anim_speed.setSingleStep(20)
        self.anim_speed.setValue(int(self._cfg.ui.animation_ms))
        self.anim_speed.setSuffix(" мс")
        form.addRow("Длительность перехода:", self.anim_speed)

        self.toast_check = QCheckBox("Показывать всплывающие уведомления")
        self.toast_check.setChecked(self._cfg.ui.show_toasts)
        form.addRow("", self.toast_check)

        self.restore_check = QCheckBox("Восстанавливать сессию при запуске")
        self.restore_check.setChecked(self._cfg.ui.restore_session)
        form.addRow("", self.restore_check)

        self.collapse_check = QCheckBox("Свёрнутый сайдбар")
        self.collapse_check.setChecked(self._cfg.ui.sidebar_collapsed)
        form.addRow("", self.collapse_check)
        return page

    def _page_audio(self) -> QWidget:
        """Аудио: частота, каналы, громкость."""
        page, form = self._form_page()
        self.sr_combo = QComboBox()
        for sr in (44100, 48000, 88200, 96000):
            self.sr_combo.addItem(f"{sr} Гц", sr)
        self.sr_combo.setCurrentIndex(max(0, self.sr_combo.findData(self._cfg.audio.sample_rate)))
        form.addRow("Частота дискретизации:", self.sr_combo)

        self.channels_combo = QComboBox()
        self.channels_combo.addItem("Моно", 1)
        self.channels_combo.addItem("Стерео", 2)
        self.channels_combo.setCurrentIndex(
            max(0, self.channels_combo.findData(self._cfg.audio.channels)))
        form.addRow("Каналы:", self.channels_combo)

        self.volume_spin = QSpinBox()
        self.volume_spin.setRange(0, 100)
        self.volume_spin.setValue(int(self._cfg.audio.master_volume * 100))
        self.volume_spin.setSuffix(" %")
        form.addRow("Громкость мастера:", self.volume_spin)

        self.crossfade_spin = QSpinBox()
        self.crossfade_spin.setRange(0, 100)
        self.crossfade_spin.setValue(int(self._cfg.audio.seek_crossfade_ms))
        self.crossfade_spin.setSuffix(" мс")
        form.addRow("Кроссфейд при перемотке:", self.crossfade_spin)

        self.tracks_spin = QSpinBox()
        self.tracks_spin.setRange(1, 8)
        self.tracks_spin.setValue(int(self._cfg.audio.max_audio_tracks))
        form.addRow("Максимум аудиодорожек:", self.tracks_spin)

        self.preview_spin = QSpinBox()
        self.preview_spin.setRange(5, 120)
        self.preview_spin.setValue(int(self._cfg.search.preview_seconds))
        self.preview_spin.setSuffix(" с")
        form.addRow("Длина превью трека:", self.preview_spin)
        return page

    def _page_video(self) -> QWidget:
        """Видео: разрешение, fps, качество превью, FFmpeg."""
        page, form = self._form_page()
        self.video_width = QSpinBox()
        self.video_width.setRange(320, 7680)
        self.video_width.setSingleStep(2)
        self.video_width.setValue(int(self._cfg.video.width))
        form.addRow("Ширина по умолчанию:", self.video_width)

        self.video_height = QSpinBox()
        self.video_height.setRange(240, 4320)
        self.video_height.setSingleStep(2)
        self.video_height.setValue(int(self._cfg.video.height))
        form.addRow("Высота по умолчанию:", self.video_height)

        self.video_fps = QSpinBox()
        self.video_fps.setRange(10, 120)
        self.video_fps.setValue(int(self._cfg.video.fps))
        form.addRow("FPS:", self.video_fps)

        self.preview_w = QSpinBox()
        self.preview_w.setRange(320, 1920)
        self.preview_w.setValue(int(self._cfg.video.preview_width))
        form.addRow("Ширина превью:", self.preview_w)

        self.preview_fps = QSpinBox()
        self.preview_fps.setRange(5, 60)
        self.preview_fps.setValue(int(self._cfg.video.preview_fps))
        form.addRow("FPS превью:", self.preview_fps)

        self.crf_spin = QSpinBox()
        self.crf_spin.setRange(12, 40)
        self.crf_spin.setValue(int(self._cfg.video.crf))
        self.crf_spin.setToolTip("Меньше значение — выше качество и размер")
        form.addRow("CRF (качество):", self.crf_spin)

        self.hwaccel_check = QCheckBox("Аппаратное ускорение (если доступно)")
        self.hwaccel_check.setChecked(self._cfg.video.hwaccel)
        form.addRow("", self.hwaccel_check)

        ffmpeg_row = QWidget()
        row = QHBoxLayout(ffmpeg_row)
        row.setContentsMargins(0, 0, 0, 0)
        self.ffmpeg_edit = QLineEdit(self._cfg.video.ffmpeg_path)
        self.ffmpeg_edit.setPlaceholderText("Автоопределение (PATH / imageio-ffmpeg)")
        row.addWidget(self.ffmpeg_edit, 1)
        browse = QPushButton("…")
        browse.setObjectName("Ghost")
        browse.setFixedWidth(36)
        browse.clicked.connect(self._pick_ffmpeg)
        row.addWidget(browse)
        form.addRow("Путь к FFmpeg:", ffmpeg_row)

        self.ffmpeg_status = QLabel("Проверить окружение…")
        self.ffmpeg_status.setObjectName("Muted")
        self.ffmpeg_status.setWordWrap(True)
        form.addRow("", self.ffmpeg_status)
        check = QPushButton("Проверить FFmpeg")
        check.setObjectName("Ghost")
        check.clicked.connect(self._check_ffmpeg)
        form.addRow("", check)
        return page

    def _page_export(self) -> QWidget:
        """Экспорт: шаблон имени, нормализация, папка."""
        page, form = self._form_page()
        self.template_edit = QLineEdit(self._cfg.export.filename_template)
        self.template_edit.setToolTip("Доступны {kind}, {date}, {time}, {stem}")
        form.addRow("Шаблон имени файла:", self.template_edit)

        self.norm_check = QCheckBox("Нормализация аудио при экспорте")
        self.norm_check.setChecked(self._cfg.export.normalize_audio)
        form.addRow("", self.norm_check)

        self.target_db = QSpinBox()
        self.target_db.setRange(-24, 0)
        self.target_db.setValue(int(self._cfg.export.normalize_target_db))
        self.target_db.setSuffix(" dBFS")
        form.addRow("Целевой уровень:", self.target_db)

        self.export_dir_edit = QLineEdit(str(self._cfg.paths.exports_dir))
        self.export_dir_edit.setReadOnly(True)
        dir_row = QWidget()
        row = QHBoxLayout(dir_row)
        row.setContentsMargins(0, 0, 0, 0)
        row.addWidget(self.export_dir_edit, 1)
        browse = QPushButton("…")
        browse.setObjectName("Ghost")
        browse.setFixedWidth(36)
        browse.clicked.connect(self._pick_export_dir)
        row.addWidget(browse)
        form.addRow("Папка экспорта:", dir_row)

        self.open_folder_check = QCheckBox("Открывать папку после экспорта")
        self.open_folder_check.setChecked(self._cfg.export.open_folder_after_export)
        form.addRow("", self.open_folder_check)

        self.results_spin = QSpinBox()
        self.results_spin.setRange(5, 50)
        self.results_spin.setValue(int(self._cfg.search.results_limit))
        form.addRow("Результатов на страницу поиска:", self.results_spin)
        return page

    def _page_publish(self) -> QWidget:
        """Публикация: платформа по умолчанию, приватность, теги."""
        page, form = self._form_page()
        self.platform_combo = QComboBox()
        self.platform_combo.addItem("YouTube", "youtube")
        self.platform_combo.addItem("TikTok", "tiktok")
        self.platform_combo.setCurrentIndex(
            max(0, self.platform_combo.findData(self._cfg.publish.default_platform)))
        form.addRow("Платформа:", self.platform_combo)

        self.privacy_combo = QComboBox()
        for key, label in (("public", "Публичное"), ("unlisted", "По ссылке"),
                           ("private", "Приватное")):
            self.privacy_combo.addItem(label, key)
        self.privacy_combo.setCurrentIndex(
            max(0, self.privacy_combo.findData(self._cfg.publish.default_privacy)))
        form.addRow("Приватность:", self.privacy_combo)

        self.tags_edit = QLineEdit(self._cfg.publish.default_tags)
        form.addRow("Теги по умолчанию:", self.tags_edit)

        self.tiktok_headless = QCheckBox("TikTok: публиковать в фоне (без окна браузера)")
        self.tiktok_headless.setChecked(bool(getattr(self._cfg.publish, "tiktok_headless", False)))
        form.addRow("", self.tiktok_headless)

        hint = QLabel("OAuth-ключи YouTube задаются файлом client_secret.json в папке "
                      f"<code>{APP_DIR}</code>. Привязка аккаунтов — на вкладке «Публикация».")
        hint.setObjectName("Muted")
        hint.setWordWrap(True)
        form.addRow("", hint)
        return page

    def _page_files(self) -> QWidget:
        """Файлы: папки, кэш, база, логи."""
        page, form = self._form_page()
        for label, value in (("Кэш:", self._cfg.paths.cache_dir),
                             ("Загрузки:", self._cfg.paths.downloads_dir),
                             ("Проекты:", self._cfg.paths.projects_dir),
                             ("База:", self._cfg.paths.db_path),
                             ("Конфиг:", CONFIG_FILE)):
            edit = QLineEdit(str(value))
            edit.setReadOnly(True)
            form.addRow(label, edit)

        buttons = QWidget()
        row = QHBoxLayout(buttons)
        row.setContentsMargins(0, 0, 0, 0)
        open_cfg = QPushButton("Открыть папку конфига")
        open_cfg.setObjectName("Ghost")
        open_cfg.clicked.connect(lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(APP_DIR))))
        row.addWidget(open_cfg)
        open_projects = QPushButton("Открыть проекты")
        open_projects.setObjectName("Ghost")
        open_projects.clicked.connect(
            lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(self._cfg.paths.projects_dir))))
        row.addWidget(open_projects)
        clear_cache = QPushButton("Очистить кэш превью")
        clear_cache.setObjectName("Danger")
        clear_cache.clicked.connect(self._clear_cache)
        row.addWidget(clear_cache)
        row.addStretch(1)
        form.addRow("", buttons)
        return page

    def _page_about(self) -> QWidget:
        """О программе."""
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(4, 0, 4, 0)
        layout.setSpacing(10)
        header = QLabel("BeatForge Studio")
        header.setObjectName("H1")
        layout.addWidget(header)
        subtitle = QLabel("Мультимедийная студия: поиск, аудиоредактор, видеоредактор, "
                          "экспорт и публикация")
        subtitle.setObjectName("Muted")
        subtitle.setWordWrap(True)
        layout.addWidget(subtitle)
        details = QLabel(
            "<ul>"
            "<li>Python 3.11 · PyQt6 · FFmpeg</li>"
            "<li>Аудио: 8 дорожек, эффекты, undo/redo на 50 шагов</li>"
            "<li>Видео: V1–V4 / A1–A4, переходы xfade, хромакей, субтитры Whisper</li>"
            "<li>Экспорт: MP3/WAV/FLAC/OGG/M4A, MP4/WebM/MOV/GIF</li>"
            "<li>Публикация: YouTube (OAuth2), TikTok (Playwright)</li>"
            "</ul>")
        details.setTextFormat(Qt.TextFormat.RichText)
        layout.addWidget(details)
        license_label = QLabel("Учебный проект. Сторонние библиотеки — под их лицензиями.")
        license_label.setObjectName("Tiny")
        layout.addWidget(license_label)
        layout.addStretch(1)
        return page

    # ================================================================ логика
    def _on_page(self, index: int) -> None:
        """Переключить страницу настроек."""
        if 0 <= index < self._stack.count():
            self._stack.setCurrentIndex(index)

    def _pick_ffmpeg(self) -> None:
        """Выбрать бинарник FFmpeg вручную."""
        path, _ = QFileDialog.getOpenFileName(self, "Выберите FFmpeg", str(Path.home()))
        if path:
            self.ffmpeg_edit.setText(path)

    def _pick_export_dir(self) -> None:
        """Выбрать папку экспорта."""
        path = QFileDialog.getExistingDirectory(self, "Папка экспорта",
                                                self.export_dir_edit.text())
        if path:
            self.export_dir_edit.setText(path)

    def _check_ffmpeg(self) -> None:
        """Проверить наличие FFmpeg и показать результат."""
        status = ensure_ffmpeg()
        self.ffmpeg_status.setText(status.message)
        self.ffmpeg_status.setStyleSheet(
            f"color: {color('SUCCESS') if status.available else color('DANGER')};")

    def _clear_cache(self) -> None:
        """Удалить файлы кэша (превью, миниатюры, прокси)."""
        removed = 0
        for folder in (self._cfg.paths.cache_dir, self._cfg.paths.thumbnails_dir):
            root = Path(folder)
            if not root.exists():
                continue
            for item in root.rglob("*"):
                if item.is_file():
                    try:
                        item.unlink()
                        removed += 1
                    except OSError:
                        pass
        self.ffmpeg_status.setText(f"Удалено файлов: {removed}")
        LOG.info("Кэш очищен: %d файлов", removed)

    def _collect(self) -> Dict[str, Any]:
        """Собрать значения полей в структуру для сохранения."""
        return {
            "ui": {
                "theme": self.theme_combo.currentData(),
                "font_family": self.font_combo.currentText(),
                "font_size": int(self.font_size.value()),
                "animations": self.anim_check.isChecked(),
                "animation_ms": int(self.anim_speed.value()),
                "show_toasts": self.toast_check.isChecked(),
                "restore_session": self.restore_check.isChecked(),
                "sidebar_collapsed": self.collapse_check.isChecked(),
            },
            "audio": {
                "sample_rate": int(self.sr_combo.currentData()),
                "channels": int(self.channels_combo.currentData()),
                "master_volume": self.volume_spin.value() / 100.0,
                "seek_crossfade_ms": int(self.crossfade_spin.value()),
                "max_audio_tracks": int(self.tracks_spin.value()),
            },
            "video": {
                "width": int(self.video_width.value()),
                "height": int(self.video_height.value()),
                "fps": int(self.video_fps.value()),
                "preview_width": int(self.preview_w.value()),
                "preview_fps": int(self.preview_fps.value()),
                "crf": int(self.crf_spin.value()),
                "hwaccel": self.hwaccel_check.isChecked(),
                "ffmpeg_path": self.ffmpeg_edit.text().strip(),
            },
            "export": {
                "filename_template": self.template_edit.text().strip() or
                                     "BeatForge_{kind}_{date}_{time}",
                "normalize_audio": self.norm_check.isChecked(),
                "normalize_target_db": float(self.target_db.value()),
                "open_folder_after_export": self.open_folder_check.isChecked(),
            },
            "publish": {
                "default_platform": self.platform_combo.currentData(),
                "default_privacy": self.privacy_combo.currentData(),
                "default_tags": self.tags_edit.text().strip(),
                "tiktok_headless": self.tiktok_headless.isChecked(),
            },
            "search": {"results_limit": int(self.results_spin.value()),
                       "preview_seconds": int(self.preview_spin.value())},
            "exports_dir": self.export_dir_edit.text(),
        }

    def _save(self, close: bool = True) -> None:
        """Применить и сохранить настройки."""
        values = self._collect()
        ui = values["ui"]
        self._cfg.ui.theme = ui["theme"]
        self._cfg.ui.font_family = ui["font_family"]
        self._cfg.ui.font_size = ui["font_size"]
        self._cfg.ui.animations = ui["animations"]
        self._cfg.ui.animation_ms = ui["animation_ms"]
        self._cfg.ui.show_toasts = ui["show_toasts"]
        self._cfg.ui.restore_session = ui["restore_session"]
        self._cfg.ui.sidebar_collapsed = ui["sidebar_collapsed"]

        audio = values["audio"]
        for key, value in audio.items():
            setattr(self._cfg.audio, key, value)
        for key, value in values["video"].items():
            setattr(self._cfg.video, key, value)
        for key, value in values["export"].items():
            setattr(self._cfg.export, key, value)
        for key, value in values["publish"].items():
            setattr(self._cfg.publish, key, value)
        for key, value in values["search"].items():
            setattr(self._cfg.search, key, value)
        exports_dir = values["exports_dir"]
        if exports_dir:
            self._cfg.paths.exports_dir = Path(exports_dir)
        try:
            self._cfg.save()
            LOG.info("Настройки сохранены в %s", CONFIG_FILE)
        except OSError as exc:
            LOG.error("Не удалось сохранить настройки: %s", exc)
        self.applied.emit()
        if close:
            self.accept()
