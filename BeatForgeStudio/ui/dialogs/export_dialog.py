"""Диалог экспорта: аудио (MP3/WAV/FLAC/OGG/M4A) и видео (MP4/WebM/MOV/GIF).

Диалог только собирает параметры — сам экспорт выполняет
:class:`ui.dialogs.progress_dialog.ProgressDialog` через ``core.exporter``.
Это позволяет тестировать диалог без FFmpeg и не блокировать UI.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, Optional

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QDialog, QDialogButtonBox,
                             QFileDialog, QGridLayout, QHBoxLayout, QLabel,
                             QLineEdit, QPushButton, QSpinBox, QStackedWidget,
                             QVBoxLayout, QWidget)

from core.exporter import AUDIO_FORMATS, UPLOAD_SERVICES, VIDEO_PRESETS, build_filename
from ui.widgets.common import SegmentedControlSimple, Separator
from utils.config import get_config

LOG = logging.getLogger(__name__)

#: Видеоконтейнеры и их кодеки.
VIDEO_FORMATS: Dict[str, Dict[str, str]] = {
    "mp4": {"label": "MP4 (H.264/H.265)", "codec": "libx264"},
    "webm": {"label": "WebM (VP9)", "codec": "libvpx-vp9"},
    "mov": {"label": "MOV (ProRes)", "codec": "prores_ks"},
    "gif": {"label": "GIF (анимация)", "codec": "gif"},
}


class ExportDialog(QDialog):
    """Собрать настройки экспорта.

    Attributes:
        result_settings: Заполняется после ``accept()`` — словарь параметров
            для :class:`core.exporter.ExportTask`.
    """

    def __init__(self, kind: str = "audio", duration: float = 0.0,
                 suggested_stem: str = "", parent: Optional[QWidget] = None) -> None:
        """Args:
            kind: ``audio`` | ``video``.
            duration: Длительность проекта (для оценки размера).
            suggested_stem: Имя по умолчанию (без расширения).
            parent: Родитель.
        """
        super().__init__(parent)
        self.setWindowTitle("Экспорт")
        self.setMinimumWidth(620)
        self.result_settings: Dict[str, Any] = {}
        self._kind = kind if kind in ("audio", "video") else "audio"
        self._duration = max(0.0, float(duration))
        self._suggested_stem = suggested_stem or "BeatForge"
        self._cfg = get_config().export
        self._build_ui()
        self._switch_kind(self._kind)

    # ================================================================ UI
    def _build_ui(self) -> None:
        """Собрать интерфейс диалога."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 18, 20, 16)
        layout.setSpacing(12)

        title = QLabel("Экспорт проекта")
        title.setObjectName("H2")
        layout.addWidget(title)

        self.kind_switch = SegmentedControlSimple(["♪ Аудио", "▶ Видео"])
        self.kind_switch.set_index(0 if self._kind == "audio" else 1, emit=False)
        self.kind_switch.changed.connect(
            lambda i: self._switch_kind("audio" if i == 0 else "video"))
        layout.addWidget(self.kind_switch)

        self.stack = QStackedWidget()
        self.stack.addWidget(self._build_audio_page())
        self.stack.addWidget(self._build_video_page())
        layout.addWidget(self.stack)

        layout.addWidget(Separator())

        # --- путь
        path_row = QHBoxLayout()
        path_row.addWidget(QLabel("Папка:"))
        self.dir_edit = QLineEdit(str(get_config().paths.exports_dir))
        self.dir_edit.setReadOnly(True)
        path_row.addWidget(self.dir_edit, 1)
        browse = QPushButton("Выбрать…")
        browse.setObjectName("Ghost")
        browse.clicked.connect(self._pick_dir)
        path_row.addWidget(browse)
        layout.addLayout(path_row)

        name_row = QHBoxLayout()
        name_row.addWidget(QLabel("Имя файла:"))
        self.name_edit = QLineEdit(self._suggested_stem)
        self.name_edit.textChanged.connect(self._update_preview)
        name_row.addWidget(self.name_edit, 1)
        layout.addLayout(name_row)

        self.preview_label = QLabel("")
        self.preview_label.setObjectName("Muted")
        self.preview_label.setWordWrap(True)
        layout.addWidget(self.preview_label)

        # --- ссылка
        self.upload_check = QCheckBox("Загрузить ссылкой после экспорта")
        self.upload_check.toggled.connect(lambda c: self.upload_combo.setEnabled(c))
        layout.addWidget(self.upload_check)
        upload_row = QHBoxLayout()
        self.upload_combo = QComboBox()
        for key, label in UPLOAD_SERVICES.items():
            self.upload_combo.addItem(label, key)
        self.upload_combo.setEnabled(False)
        upload_row.addWidget(self.upload_combo, 1)
        layout.addLayout(upload_row)

        self.open_check = QCheckBox("Открыть папку после экспорта")
        self.open_check.setChecked(self._cfg.open_folder_after_export)
        layout.addWidget(self.open_check)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok |
                                   QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Экспортировать")
        buttons.button(QDialogButtonBox.StandardButton.Ok).setObjectName("Primary")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self._on_accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)
        self._update_preview()

    def _build_audio_page(self) -> QWidget:
        """Страница аудио-настроек."""
        page = QWidget()
        grid = QGridLayout(page)
        grid.setContentsMargins(0, 4, 0, 4)
        grid.setHorizontalSpacing(12)
        grid.setVerticalSpacing(8)

        grid.addWidget(QLabel("Формат:"), 0, 0)
        self.audio_format = QComboBox()
        for key in AUDIO_FORMATS:
            self.audio_format.addItem(key.upper(), key)
        index = max(0, self.audio_format.findData(self._cfg.audio_format))
        self.audio_format.setCurrentIndex(index)
        self.audio_format.currentIndexChanged.connect(self._on_audio_format)
        grid.addWidget(self.audio_format, 0, 1)

        grid.addWidget(QLabel("Битрейт:"), 1, 0)
        self.bitrate_combo = QComboBox()
        for value in ("128k", "192k", "256k", "320k"):
            self.bitrate_combo.addItem(value, int(value[:-1]))
        self.bitrate_combo.setCurrentIndex(
            max(0, self.bitrate_combo.findData(self._cfg.audio_bitrate)))
        self.bitrate_combo.currentIndexChanged.connect(self._update_preview)
        grid.addWidget(self.bitrate_combo, 1, 1)

        grid.addWidget(QLabel("Разрядность WAV:"), 2, 0)
        self.depth_combo = QComboBox()
        for depth in (16, 24, 32):
            self.depth_combo.addItem(f"{depth} бит", depth)
        self.depth_combo.setCurrentIndex(
            max(0, self.depth_combo.findData(self._cfg.wav_bit_depth)))
        grid.addWidget(self.depth_combo, 2, 1)

        self.normalize_check = QCheckBox("Нормализовать до −1 dBFS")
        self.normalize_check.setChecked(self._cfg.normalize_audio)
        self.normalize_check.toggled.connect(self._update_preview)
        grid.addWidget(self.normalize_check, 3, 0, 1, 2)
        grid.setColumnStretch(2, 1)
        return page

    def _build_video_page(self) -> QWidget:
        """Страница видео-настроек."""
        page = QWidget()
        grid = QGridLayout(page)
        grid.setContentsMargins(0, 4, 0, 4)
        grid.setHorizontalSpacing(12)
        grid.setVerticalSpacing(8)

        grid.addWidget(QLabel("Пресет:"), 0, 0)
        self.preset_combo = QComboBox()
        for name in VIDEO_PRESETS:
            self.preset_combo.addItem(name, name)
        self.preset_combo.currentIndexChanged.connect(self._update_preview)
        grid.addWidget(self.preset_combo, 0, 1, 1, 2)

        grid.addWidget(QLabel("Контейнер:"), 1, 0)
        self.video_format = QComboBox()
        for key, spec in VIDEO_FORMATS.items():
            self.video_format.addItem(str(spec["label"]), key)
        index = max(0, self.video_format.findData(self._cfg.video_format))
        self.video_format.setCurrentIndex(index)
        self.video_format.currentIndexChanged.connect(self._on_video_format)
        grid.addWidget(self.video_format, 1, 1, 1, 2)

        grid.addWidget(QLabel("FPS:"), 2, 0)
        self.fps_spin = QSpinBox()
        self.fps_spin.setRange(10, 120)
        self.fps_spin.setValue(int(self._cfg.fps))
        grid.addWidget(self.fps_spin, 2, 1)

        self.overlay_check = QCheckBox("Наложить текстовые слои")
        self.overlay_check.setChecked(True)
        grid.addWidget(self.overlay_check, 3, 0, 1, 3)

        self.subtitle_check = QCheckBox("Добавить субтитры (если есть)")
        self.subtitle_check.setChecked(True)
        grid.addWidget(self.subtitle_check, 4, 0, 1, 3)
        grid.setColumnStretch(2, 1)
        return page

    # ================================================================ логика
    def _switch_kind(self, kind: str) -> None:
        """Переключить аудио/видео-страницу."""
        self._kind = kind
        self.stack.setCurrentIndex(0 if kind == "audio" else 1)
        self.kind_switch.set_index(0 if kind == "audio" else 1, emit=False)
        self._on_audio_format()
        self._update_preview()

    def _on_audio_format(self, _index: int = 0) -> None:
        """Показать только релевантные аудио-параметры."""
        fmt = self.audio_format.currentData() or "mp3"
        self.bitrate_combo.setEnabled(fmt in ("mp3", "m4a", "ogg"))
        self.depth_combo.setEnabled(fmt in ("wav", "flac"))
        self._update_preview()

    def _on_video_format(self, _index: int = 0) -> None:
        """GIF ограничивает пресеты."""
        is_gif = self.video_format.currentData() == "gif"
        self.fps_spin.setEnabled(not is_gif)
        if is_gif:
            self.fps_spin.setValue(min(24, self.fps_spin.value()))
        self._update_preview()

    def _pick_dir(self) -> None:
        """Выбрать папку назначения."""
        chosen = QFileDialog.getExistingDirectory(self, "Папка экспорта", self.dir_edit.text())
        if chosen:
            self.dir_edit.setText(chosen)
            self._update_preview()

    def current_extension(self) -> str:
        """Расширение итогового файла без точки."""
        if self._kind == "audio":
            return str(self.audio_format.currentData() or "mp3")
        return str(self.video_format.currentData() or "mp4")

    def target_path(self) -> Path:
        """Итоговый путь файла."""
        stem = self.name_edit.text().strip() or self._suggested_stem
        ext = self.current_extension()
        return Path(self.dir_edit.text()) / f"{stem}.{ext}"

    def _estimate_size(self) -> str:
        """Грубая оценка размера файла."""
        if self._duration <= 0:
            return "—"
        if self._kind == "audio":
            fmt = self.audio_format.currentData()
            if fmt == "wav":
                depth = int(self.depth_combo.currentData() or 16) / 8
                mb = self._duration * 44100 * 2 * depth / 1048576
            elif fmt == "flac":
                mb = self._duration * 44100 * 2 * 2 / 1048576 * 0.6
            else:
                kbps = int(self.bitrate_combo.currentData() or 320)
                mb = self._duration * kbps / 8 / 1024
        else:
            preset = VIDEO_PRESETS.get(self.preset_combo.currentText(), {})
            crf = int(preset.get("crf", 20))
            kbps = max(600, 6000 - (crf - 16) * 350)
            if self.video_format.currentData() == "gif":
                kbps *= 0.5
            mb = self._duration * kbps / 8 / 1024
        return f"≈ {mb:.1f} МБ"

    def _update_preview(self) -> None:
        """Обновить строку описания."""
        ext = self.current_extension()
        name = build_filename(self._kind, self.name_edit.text().strip(), ext)
        size = self._estimate_size()
        duration = f"{self._duration:.1f} с" if self._duration else "неизвестно"
        self.preview_label.setText(
            f"Файл: {name} · длительность: {duration} · размер: {size}")

    def _on_accept(self) -> None:
        """Собрать настройки и закрыть диалог."""
        name = self.name_edit.text().strip()
        if not name:
            self.name_edit.setFocus()
            return
        settings: Dict[str, Any] = {
            "kind": self._kind,
            "out_path": str(self.target_path()),
            "open_folder": self.open_check.isChecked(),
            "upload": self.upload_check.isChecked(),
            "upload_service": self.upload_combo.currentData(),
        }
        if self._kind == "audio":
            settings.update({
                "fmt": self.audio_format.currentData(),
                "bitrate": int(self.bitrate_combo.currentData() or 320),
                "bit_depth": int(self.depth_combo.currentData() or 16),
                "normalize": self.normalize_check.isChecked(),
            })
        else:
            settings.update({
                "fmt": self.video_format.currentData(),
                "preset": self.preset_combo.currentText(),
                "fps": int(self.fps_spin.value()),
                "overlay_text": self.overlay_check.isChecked(),
                "subtitles": self.subtitle_check.isChecked(),
            })
        self.result_settings = settings
        LOG.info("Параметры экспорта: %s", settings)
        self.accept()
