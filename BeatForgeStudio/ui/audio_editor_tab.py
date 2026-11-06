"""Вкладка аудиоредактора (✂️): волна, выделение, монтаж, эффекты, undo/redo.

Композиция:

* сверху — дорожки микшера с волнами (одна активная), панель инструментов;
* справа — панель эффектов (цепочка с bypass и пресетами) и свойств клипа;
* снизу — транспорт (в главном окне), здесь — мини-транспорт для выделения.

Все операции над данными идут через :class:`Command` (паттерн «Команда»),
что даёт честный undo/redo на 50 шагов и предсказуемое поведение.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QKeySequence, QShortcut
from PyQt6.QtWidgets import (QComboBox, QDoubleSpinBox, QFileDialog, QFrame,
                             QHBoxLayout, QLabel, QPushButton, QScrollArea,
                             QSlider, QSplitter, QVBoxLayout, QWidget)

from core.audio_engine import AudioBuffer, format_time
from core.effects_audio import EFFECT_SPECS, FX_PRESETS, AudioEffect, EffectChain
from core.mixer import MixerClip, MixerProject
from ui.theme import color
from ui.widgets.common import FlowLayout, IconButton, make_heading
from ui.widgets.waveform import WaveformWidget
from utils.workers import submit

LOG = logging.getLogger(__name__)


 # ============================================================
  # Команды (undo/redo)
 # ============================================================
class Command:
    """Базовая команда: ``do`` + ``undo``."""

    title: str = "Действие"

    def do(self) -> None:  # noqa: D401
        """Выполнить команду."""

    def undo(self) -> None:  # noqa: D401
        """Откатить команду."""


class CommandStack:
    """Стек команд до 50 шагов (undo/redo)."""

    def __init__(self, limit: int = 50) -> None:
        """Args:
            limit: Максимальная глубина истории.
        """
        self._limit = limit
        self._undo: List[Command] = []
        self._redo: List[Command] = []
        self.on_change: Optional[Callable[[], None]] = None

    def push(self, command: Command, execute: bool = True) -> None:
        """Добавить команду (и выполнить её)."""
        if execute:
            command.do()
        self._undo.append(command)
        if len(self._undo) > self._limit:
            self._undo.pop(0)
        self._redo.clear()
        if self.on_change:
            self.on_change()

    def undo(self) -> Optional[Command]:
        """Откатить последнюю команду."""
        if not self._undo:
            return None
        command = self._undo.pop()
        command.undo()
        self._redo.append(command)
        if self.on_change:
            self.on_change()
        return command

    def redo(self) -> Optional[Command]:
        """Повторить откаченную команду."""
        if not self._redo:
            return None
        command = self._redo.pop()
        command.do()
        self._undo.append(command)
        if self.on_change:
            self.on_change()
        return command

    @property
    def can_undo(self) -> bool:
        """Есть ли что откатывать."""
        return bool(self._undo)

    @property
    def can_redo(self) -> bool:
        """Есть ли что повторять."""
        return bool(self._redo)

    @property
    def undo_title(self) -> str:
        """Название следующего отката (для меню)."""
        return self._undo[-1].title if self._undo else ""

    def clear(self) -> None:
        """Очистить историю (например, при открытии проекта)."""
        self._undo.clear()
        self._redo.clear()
        if self.on_change:
            self.on_change()


class TrimCommand(Command):
    """Изменение границ клипа (trim / перемещение)."""

    title = "Обрезка клипа"

    def __init__(self, clip: MixerClip, start: float, duration: float,
                 label: str = "Обрезка клипа") -> None:
        self._clip = clip
        self._new = (start, duration)
        self._old = (clip.start, clip.duration)
        self.title = label

    def do(self) -> None:
        self._clip.start, self._clip.duration = self._new
        self._clip.invalidate()

    def undo(self) -> None:
        self._clip.start, self._clip.duration = self._old
        self._clip.invalidate()


class SplitCommand(Command):
    """Разрез клипа в точке."""

    title = "Разрез"

    def __init__(self, project: MixerProject, clip_id: str, at: float,
                 on_split: Optional[Callable[[Optional[MixerClip]], None]] = None) -> None:
        self._project = project
        self._clip_id = clip_id
        self._at = at
        self._created: Optional[MixerClip] = None
        self._old_duration: Optional[float] = None
        self._on_split = on_split

    def do(self) -> None:
        clip = self._project.find_clip(self._clip_id)
        if clip is None:
            return
        self._old_duration = clip.duration
        self._created = self._project.split_clip(self._clip_id, self._at)
        if self._on_split:
            self._on_split(self._created)

    def undo(self) -> None:
        clip = self._project.find_clip(self._clip_id)
        if self._created is not None:
            self._project.remove_clip(self._created.id)
            self._created = None
        if clip is not None and self._old_duration is not None:
            clip.duration = self._old_duration
            clip.invalidate()
        if self._on_split:
            self._on_split(None)


class DeleteClipCommand(Command):
    """Удаление клипа."""

    title = "Удаление клипа"

    def __init__(self, project: MixerProject, clip_id: str) -> None:
        self._project = project
        self._clip_id = clip_id
        self._clip: Optional[MixerClip] = None
        self._track_index: Optional[int] = None

    def do(self) -> None:
        self._clip = self._project.find_clip(self._clip_id)
        if self._clip is None:
            return
        for track in self._project.tracks:
            if self._clip in track.clips:
                self._track_index = track.index
                track.clips.remove(self._clip)
                break

    def undo(self) -> None:
        if self._clip is not None and self._track_index is not None:
            track = self._project.track(self._track_index)
            if track is not None:
                track.clips.append(self._clip)
                track.clips.sort(key=lambda c: c.start)


class AddClipCommand(Command):
    """Добавление клипа на дорожку."""

    title = "Добавление клипа"

    def __init__(self, project: MixerProject, track_index: int, path: str,
                 start: float = 0.0, on_added: Optional[Callable[[Optional[MixerClip]], None]] = None
                 ) -> None:
        self._project = project
        self._track_index = track_index
        self._path = path
        self._start = start
        self._clip: Optional[MixerClip] = None
        self._on_added = on_added

    def do(self) -> None:
        if self._clip is None:
            self._clip = self._project.add_clip(self._track_index, self._path, self._start)
        else:
            track = self._project.track(self._track_index)
            if track is not None:
                track.clips.append(self._clip)
        if self._on_added and self._clip:
            self._on_added(self._clip)

    def undo(self) -> None:
        if self._clip is not None:
            self._project.remove_clip(self._clip.id)


class EffectCommand(Command):
    """Добавление/удаление эффекта в цепочке."""

    def __init__(self, chain: EffectChain, effect: AudioEffect,
                 label: str = "Эффект") -> None:
        self._chain = chain
        self._effect = effect
        self.title = f"Эффект: {label}"

    def do(self) -> None:
        self._chain.add(self._effect)

    def undo(self) -> None:
        self._chain.effects.remove(self._effect)
        self._chain.invalidate()


class FadeCommand(Command):
    """Fade in/out клипа."""

    title = "Fade"

    def __init__(self, clip: MixerClip, fade_in: float, fade_out: float) -> None:
        self._clip = clip
        self._new = (fade_in, fade_out)
        self._old = (clip.fade.fade_in, clip.fade.fade_out)

    def do(self) -> None:
        self._clip.fade.fade_in, self._clip.fade.fade_out = self._new

    def undo(self) -> None:
        self._clip.fade.fade_in, self._clip.fade.fade_out = self._old


 # ============================================================
  # Панель эффектов
 # ============================================================
class EffectsPanel(QFrame):
    """Панель цепочки эффектов выделенного трека/клипа.

    Сигналы:
        chainChanged(): Цепочка изменилась (нужен пересчёт превью).
        previewRequested(): Пользователь просит прослушать результат.
    """

    chainChanged = pyqtSignal()
    previewRequested = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        """Args:
            parent: Родитель.
        """
        super().__init__(parent)
        self.setObjectName("Panel")
        self._chain = EffectChain()
        self._build_ui()

    def _build_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(10)

        title = QLabel("Эффекты")
        title.setObjectName("H3")
        layout.addWidget(title)

        preset_row = QHBoxLayout()
        self.preset_combo = QComboBox()
        self.preset_combo.addItem("Пресет…", "")
        for name in FX_PRESETS:
            self.preset_combo.addItem(name, name)
        self.preset_combo.currentIndexChanged.connect(self._on_preset)
        preset_row.addWidget(self.preset_combo, 1)
        clear_button = IconButton("✕", "Очистить цепочку", 30)
        clear_button.clicked.connect(self._clear)
        preset_row.addWidget(clear_button)
        layout.addLayout(preset_row)

        add_row = QHBoxLayout()
        self.effect_combo = QComboBox()
        for key, spec in EFFECT_SPECS.items():
            self.effect_combo.addItem(str(spec["label"]), key)
        add_row.addWidget(self.effect_combo, 1)
        add_button = QPushButton("+")
        add_button.setObjectName("Ghost")
        add_button.setFixedWidth(34)
        add_button.clicked.connect(self._add_effect)
        add_row.addWidget(add_button)
        layout.addLayout(add_row)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.host = QWidget()
        self.host_layout = QVBoxLayout(self.host)
        self.host_layout.setContentsMargins(0, 0, 4, 0)
        self.host_layout.setSpacing(8)
        self.host_layout.addStretch(1)
        self.scroll.setWidget(self.host)
        layout.addWidget(self.scroll, 1)

        self.apply_button = QPushButton("Применить к треку")
        self.apply_button.setObjectName("Secondary")
        self.apply_button.clicked.connect(self.previewRequested)
        layout.addWidget(self.apply_button)

     # API
    def set_chain(self, chain: EffectChain) -> None:
        """Показать другую цепочку."""
        self._chain = chain
        self._rebuild()

    @property
    def chain(self) -> EffectChain:
        """Текущая цепочка."""
        return self._chain

    def _rebuild(self) -> None:
        """Пересобрать список эффектов с контролами."""
        while self.host_layout.count() > 1:
            item = self.host_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        for index, effect in enumerate(self._chain.effects):
            self.host_layout.insertWidget(self.host_layout.count() - 1,
                                          self._effect_widget(index, effect))
        if not self._chain.effects:
            hint = QLabel("Цепочка пуста — добавьте эффект или выберите пресет")
            hint.setObjectName("Muted")
            hint.setWordWrap(True)
            self.host_layout.insertWidget(0, hint)

    def _effect_widget(self, index: int, effect: AudioEffect) -> QWidget:
        """Виджет одного эффекта: заголовок, bypass, параметры-слайдеры."""
        spec = EFFECT_SPECS.get(effect.kind, {"label": effect.kind, "params": {}})
        card = QFrame()
        card.setObjectName("Card")
        box = QVBoxLayout(card)
        box.setContentsMargins(10, 8, 10, 8)
        box.setSpacing(6)

        head = QHBoxLayout()
        label = QLabel(str(spec.get("label", effect.kind)))
        label.setStyleSheet(f"color: {color('TEXT')}; font-weight: 600;")
        head.addWidget(label)
        head.addStretch(1)
        bypass = QPushButton("bypass")
        bypass.setObjectName("Ghost")
        bypass.setCheckable(True)
        bypass.setChecked(effect.bypass)
        bypass.setFixedHeight(24)
        bypass.toggled.connect(lambda checked, i=index: self._toggle_bypass(i, checked))
        head.addWidget(bypass)
        remove = IconButton("✕", "Удалить эффект", 24)
        remove.clicked.connect(lambda _=False, i=index: self._remove_effect(i))
        head.addWidget(remove)
        box.addLayout(head)

        params: Dict[str, Tuple[str, float, float, float]] = spec.get("params") or {}
        for key, (caption, minimum, maximum, default) in params.items():
            row = QWidget()
            row_layout = QHBoxLayout(row)
            row_layout.setContentsMargins(0, 0, 0, 0)
            row_layout.setSpacing(6)
            caption_label = QLabel(caption)
            caption_label.setObjectName("Tiny")
            caption_label.setFixedWidth(112)
            row_layout.addWidget(caption_label)
            slider = QSlider(Qt.Orientation.Horizontal)
            steps = 100
            slider.setRange(0, steps)
            value = float(effect.params.get(key, default))
            span = (maximum - minimum) or 1.0
            slider.setValue(int((value - minimum) / span * steps))
            value_label = QLabel(f"{value:.2f}")
            value_label.setObjectName("Tiny")
            value_label.setFixedWidth(46)

            def on_change(raw: int, k=key, mn=minimum, mx=maximum, lbl=value_label,
                          idx=index) -> None:
                real = mn + (raw / 100.0) * (mx - mn)
                lbl.setText(f"{real:.2f}")
                self._chain.set_param(idx, k, real)
                self.chainChanged.emit()

            slider.valueChanged.connect(on_change)
            row_layout.addWidget(slider, 1)
            row_layout.addWidget(value_label)
            box.addWidget(row)
        return card

     # действия
    def _add_effect(self) -> None:
        kind = self.effect_combo.currentData()
        if not kind:
            return
        spec = EFFECT_SPECS.get(kind, {"params": {}})
        defaults = {k: v[3] for k, v in (spec.get("params") or {}).items()}
        self._chain.add(AudioEffect(kind, defaults))
        self._rebuild()
        self.chainChanged.emit()

    def _remove_effect(self, index: int) -> None:
        self._chain.remove(index)
        self._rebuild()
        self.chainChanged.emit()

    def _toggle_bypass(self, index: int, bypass: bool) -> None:
        self._chain.set_bypass(index, bypass)
        self.chainChanged.emit()

    def _on_preset(self, _index: int) -> None:
        name = self.preset_combo.currentData()
        if name and self._chain.load_preset(str(name)):
            self._rebuild()
            self.chainChanged.emit()

    def _clear(self) -> None:
        self._chain.clear()
        self._rebuild()
        self.chainChanged.emit()


 # ============================================================
  # Вкладка
 # ============================================================
class AudioEditorTab(QWidget):
    """Редактор аудио: волна, монтаж, эффекты.

    Сигналы:
        projectChanged(object): Проект изменился (для автосохранения).
        playRequested(object): Запрос воспроизведения буфера (:class:`AudioBuffer`).
        stopRequested(): Остановить воспроизведение.
        positionChanged(float): Позиция в секундах (для транспорта).
        statusMessage(str): Статус для главного окна.
        durationChanged(float): Длительность проекта.
    """

    projectChanged = pyqtSignal(object)
    playRequested = pyqtSignal(object)
    stopRequested = pyqtSignal()
    positionChanged = pyqtSignal(float)
    statusMessage = pyqtSignal(str)
    durationChanged = pyqtSignal(float)
    selectionChanged = pyqtSignal(float, float)

    def __init__(self, project: Optional[MixerProject] = None,
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            project: Модель микшера (создаётся своя, если None).
            parent: Родитель.
        """
        super().__init__(parent)
        self.project = project or MixerProject("Аудиопроект")
        self.history = CommandStack(50)
        self.history.on_change = self._on_history_change
        self._active_track = 0
        self._active_clip: Optional[MixerClip] = None
        self._selection: Optional[Tuple[float, float]] = None
        self._preview_buffer: Optional[AudioBuffer] = None
        self._build_ui()
        self._bind_shortcuts()
        self.refresh()

     # ====== UI
    def _build_ui(self) -> None:
        """Собрать интерфейс вкладки."""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 14, 18, 10)
        layout.setSpacing(10)

        header = QHBoxLayout()
        title = make_heading("Аудиоредактор")
        header.addWidget(title)
        header.addStretch(1)

        self.undo_button = IconButton("↺", "Отменить (Ctrl+Z)", 32)
        self.undo_button.clicked.connect(self.undo)
        self.redo_button = IconButton("⟳", "Повторить (Ctrl+Shift+Z)", 32)
        self.redo_button.clicked.connect(self.redo)
        header.addWidget(self.undo_button)
        header.addWidget(self.redo_button)
        header.addSpacing(10)

        self.import_button = QPushButton("⊞ Импорт")
        self.import_button.setObjectName("Ghost")
        self.import_button.clicked.connect(self.import_files)
        header.addWidget(self.import_button)

        self.add_track_button = QPushButton("+ Дорожка")
        self.add_track_button.setObjectName("Ghost")
        self.add_track_button.clicked.connect(self.add_track)
        header.addWidget(self.add_track_button)

        self.preview_button = QPushButton("▶ Прослушать микс")
        self.preview_button.setObjectName("Primary")
        self.preview_button.clicked.connect(self.preview_mix)
        header.addWidget(self.preview_button)
        layout.addLayout(header)

         # информация о выделении / инструментах
        tools = FlowLayout(h_spacing=6, v_spacing=6)
        self.selection_label = QLabel("Выделение: —")
        self.selection_label.setObjectName("Muted")
        tools.addWidget(self.selection_label)
        for text, tooltip, slot in (
            ("✂ Разрезать", "Разрезать выделение (Ctrl+B)", self.split_selection),
            ("✕ Удалить", "Удалить выделение (Delete)", self.delete_selection),
            ("≡ Trim", "Оставить только выделение", self.trim_to_selection),
            ("↔ Fade in", "Fade in на выделении", lambda: self.fade_selection(True)),
            ("↔ Fade out", "Fade out на выделении", lambda: self.fade_selection(False)),
            ("⇄ Реверс", "Реверс выделения", self.reverse_selection),
            ("⤢ Нормализация", "Нормализовать выделение/клип", self.normalize_selection),
            ("−", "Уменьшить", lambda: self.waveform.zoom_out()),
            ("+", "Увеличить", lambda: self.waveform.zoom_in()),
            ("⤢ Вся волна", "Вписать волну", lambda: self.waveform.fit()),
        ):
            button = QPushButton(text)
            button.setObjectName("ToolButton")
            button.setMinimumHeight(28)
            button.setToolTip(tooltip)
            button.clicked.connect(slot)
            tools.addWidget(button)
        layout.addLayout(tools)

         # основная область: волна + панель эффектов
        splitter = QSplitter(Qt.Orientation.Horizontal)
        wave_host = QWidget()
        wave_layout = QVBoxLayout(wave_host)
        wave_layout.setContentsMargins(0, 0, 0, 0)
        wave_layout.setSpacing(8)

        self.track_bar = QWidget()
        self.track_bar_layout = QHBoxLayout(self.track_bar)
        self.track_bar_layout.setContentsMargins(0, 0, 0, 0)
        self.track_bar_layout.setSpacing(6)
        self.track_bar_layout.addStretch(1)
        wave_layout.addWidget(self.track_bar)

        self.waveform = WaveformWidget()
        self.waveform.seekRequested.connect(self._on_seek)
        self.waveform.selectionChanged.connect(self._on_selection)
        wave_layout.addWidget(self.waveform, 1)
        splitter.addWidget(wave_host)

        self.effects_panel = EffectsPanel()
        self.effects_panel.setMaximumWidth(360)
        self.effects_panel.chainChanged.connect(self._on_chain_changed)
        self.effects_panel.previewRequested.connect(self.apply_effects)
        splitter.addWidget(self.effects_panel)
        splitter.setStretchFactor(0, 3)
        splitter.setStretchFactor(1, 1)
        layout.addWidget(splitter, 1)

         # мини-транспорт по выделению
        bottom = QHBoxLayout()
        self.clip_info = QLabel("Клипов нет")
        self.clip_info.setObjectName("Muted")
        bottom.addWidget(self.clip_info)
        bottom.addStretch(1)
        self.bpm_spin = QDoubleSpinBox()
        self.bpm_spin.setRange(40, 300)
        self.bpm_spin.setValue(120)
        self.bpm_spin.setDecimals(1)
        self.bpm_spin.setSuffix(" BPM")
        self.bpm_spin.setToolTip("BPM сетки (для привязки)")
        self.bpm_spin.valueChanged.connect(self._on_bpm)
        bottom.addWidget(QLabel("Сетка:"))
        bottom.addWidget(self.bpm_spin)
        layout.addLayout(bottom)

    def _bind_shortcuts(self) -> None:
        """Горячие клавиши вкладки."""
        for keys, slot in (
            ("Ctrl+Z", self.undo),
            ("Ctrl+Shift+Z", self.redo),
            ("Ctrl+B", self.split_selection),
            ("Delete", self.delete_selection),
            ("Ctrl+D", lambda: self.duplicate_clip()),
        ):
            shortcut = QShortcut(QKeySequence(keys), self)
            shortcut.activated.connect(slot)

     # ====== модель
    def set_project(self, project: MixerProject) -> None:
        """Загрузить другой проект."""
        self.project = project
        self.history.clear()
        self._active_track = 0
        self._active_clip = None
        self.refresh()

    def refresh(self) -> None:
        """Перерисовать дорожки, волну и панель эффектов."""
        self._rebuild_track_bar()
        self._show_active_track()
        self.durationChanged.emit(self.project.duration)
        self._on_history_change()

    def _rebuild_track_bar(self) -> None:
        """Кнопки выбора дорожек над волной."""
        while self.track_bar_layout.count() > 1:
            item = self.track_bar_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        for track in self.project.tracks:
            button = QPushButton(track.name or f"Дорожка {track.index + 1}")
            button.setObjectName("Chip")
            button.setCheckable(True)
            button.setChecked(track.index == self._active_track)
            button.setStyleSheet(
                f"QPushButtonChip:checked {{ background: {track.color}22;"
                f" border: 1px solid {track.color}; color: {color('TEXT')}; }}"
            )
            button.clicked.connect(lambda _=False, i=track.index: self.select_track(i))
            self.track_bar_layout.insertWidget(self.track_bar_layout.count() - 1, button)

    def select_track(self, index: int) -> None:
        """Выбрать активную дорожку."""
        self._active_track = index
        track = self.project.track(index)
        self._active_clip = track.clips[0] if track and track.clips else None
        self.refresh()

    def _show_active_track(self) -> None:
        """Показать волну активного клипа активной дорожки."""
        track = self.project.track(self._active_track)
        if track is None:
            self.waveform.set_buffer(None)
            self.clip_info.setText("Дорожек нет")
            return
        self.effects_panel.set_chain(EffectChain(track.effects))
        if not track.clips:
            self.waveform.set_buffer(None, title=f"{track.name}: пусто")
            self.clip_info.setText(f"{track.name}: клипов нет — перетащите файл")
            return
        clip = self._active_clip if self._active_clip in track.clips else track.clips[0]
        self._active_clip = clip
        try:
            buffer = clip.load(self.project.sample_rate, self.project.channels)
        except Exception as exc:
            LOG.error("Клип не загрузился: %s", exc)
            self.waveform.set_buffer(None, title=f"Ошибка: {Path(clip.path).name}")
            return
        if buffer.frames:
            self.waveform.set_buffer(buffer, title=Path(clip.path).name,
                                     peaks=buffer.to_mono_peaks(2000))
        else:
            self.waveform.set_buffer(None)
        self.waveform.set_bpm(self.project.bpm or self.bpm_spin.value())
        self.clip_info.setText(
            f"{track.name} · {len(track.clips)} клип(ов) · "
            f"{format_time(clip.start, True)}–{format_time(clip.end, True)} · "
            f"всего {format_time(self.project.duration)}")

     # ====== файлы
    def import_files(self) -> None:
        """Диалог импорта аудиофайлов на активную дорожку."""
        files, _ = QFileDialog.getOpenFileNames(
            self, "Импорт аудио", str(Path.home()),
            "Аудио (*.wav *.mp3 *.flac *.ogg *.m4a *.aac *.opus);;Все файлы (*)")
        self.add_files(files)

    def add_files(self, paths: List[str], track_index: Optional[int] = None) -> None:
        """Добавить файлы на дорожку (с undo-командой для каждого).

        Args:
            paths: Пути к файлам.
            track_index: Дорожка (по умолчанию активная).
        """
        index = self._active_track if track_index is None else track_index
        track = self.project.track(index)
        if track is None:
            self.add_track()
            track = self.project.track(self._active_track)
        if track is None:
            return
        start = track.end if track.clips else 0.0
        added = 0
        for path in paths:
            if not Path(path).exists():
                continue
            command = AddClipCommand(self.project, track.index, path, start)
            self.history.push(command)
            if command._clip is not None:  # noqa: SLF001
                self._active_clip = command._clip
                start = command._clip.end
                added += 1
        if added:
            self.statusMessage.emit(f"Добавлено файлов: {added}")
            self.projectChanged.emit(self.project)
            self.refresh()

    def add_track(self) -> None:
        """Добавить дорожку (если не превышен лимит)."""
        track = self.project.add_track()
        if track is None:
            self.statusMessage.emit(
                f"Достигнут лимит дорожек ({self.project.max_tracks})")
            return
        self._active_track = track.index
        self.refresh()
        self.projectChanged.emit(self.project)

     # ====== операции
    def _current_clip(self) -> Optional[MixerClip]:
        track = self.project.track(self._active_track)
        if track is None or not track.clips:
            return None
        if self._active_clip in track.clips:
            return self._active_clip
        return track.clips[0]

    def _selection_absolute(self) -> Optional[Tuple[float, float]]:
        """Выделение в координатах таймлайна (сек)."""
        if not self._selection:
            return None
        clip = self._current_clip()
        if clip is None:
            return None
        a, b = self._selection
        return (clip.start + a, clip.start + b)

    def split_selection(self) -> None:
        """Разрезать клип в позиции плейхеда (или середине выделения)."""
        clip = self._current_clip()
        if clip is None:
            return
        if self._selection:
            at = clip.start + (self._selection[0] + self._selection[1]) / 2
        else:
            at = clip.start + self.waveform.playhead
        if not (clip.start < at < clip.end):
            self.statusMessage.emit("Курсор вне клипа — разрез невозможен")
            return
        command = SplitCommand(self.project, clip.id, at, self._after_split)
        self.history.push(command)
        self.statusMessage.emit("Клип разрезан")
        self.projectChanged.emit(self.project)
        self.refresh()

    def _after_split(self, created: Optional[MixerClip]) -> None:
        if created is not None:
            self._active_clip = created

    def delete_selection(self) -> None:
        """Удалить выделение (или весь клип, если выделения нет)."""
        clip = self._current_clip()
        if clip is None:
            return
        if not self._selection:
            self.history.push(DeleteClipCommand(self.project, clip.id))
            self._active_clip = None
            self.statusMessage.emit("Клип удалён")
        else:
            a, b = min(self._selection), max(self._selection)
             # удаление диапазона = два разреза + удаление середины
            self._delete_range(clip, a, b)
        self.projectChanged.emit(self.project)
        self.refresh()

    def _delete_range(self, clip: MixerClip, a: float, b: float) -> None:
        """Удалить диапазон внутри клипа (через разрезы)."""
        abs_a, abs_b = clip.start + a, clip.start + b
        if abs_a > clip.start + 0.001:
            self.history.push(SplitCommand(self.project, clip.id, abs_a), execute=True)
            middle = self.project.track(self._active_track)
            clip = next((c for c in middle.clips if abs(c.start - abs_a) < 0.01), clip)
        if abs_b < clip.end - 0.001:
            self.history.push(SplitCommand(self.project, clip.id, abs_b), execute=True)
        track = self.project.track(self._active_track)
        for candidate in list(track.clips):
            if abs(candidate.start - abs_a) < 0.02:
                self.history.push(DeleteClipCommand(self.project, candidate.id), execute=False)
        self.statusMessage.emit("Диапазон удалён")

    def trim_to_selection(self) -> None:
        """Оставить только выделенный фрагмент клипа."""
        clip = self._current_clip()
        if clip is None or not self._selection:
            self.statusMessage.emit("Выделите фрагмент на волне")
            return
        a, b = min(self._selection), max(self._selection)
        command = TrimCommand(clip, clip.start + a, max(0.05, b - a), "Trim")
        self.history.push(command)
        self.statusMessage.emit("Клип обрезан по выделению")
        self.projectChanged.emit(self.project)
        self.refresh()

    def fade_selection(self, fade_in: bool = True) -> None:
        """Применить fade in/out к клипу (по длине выделения)."""
        clip = self._current_clip()
        if clip is None:
            return
        length = (max(self._selection) - min(self._selection)) if self._selection else 0.5
        new_in = length if fade_in else clip.fade.fade_in
        new_out = 0.0 if fade_in else length
        self.history.push(FadeCommand(clip, new_in, new_out))
        self.statusMessage.emit("Fade применён")
        self.projectChanged.emit(self.project)
        self.refresh()

    def reverse_selection(self) -> None:
        """Реверс клипа (или выделенного фрагмента — как отдельный клип)."""
        clip = self._current_clip()
        if clip is None:
            return
        clip.reversed = not clip.reversed
        clip.invalidate()
        self.statusMessage.emit("Реверс применён к клипу")
        self.projectChanged.emit(self.project)
        self.refresh()

    def normalize_selection(self) -> None:
        """Нормализовать клип до -1 dBFS."""
        track = self.project.track(self._active_track)
        if track is None:
            return
        if not any(e.kind == "normalize" for e in track.effects):
            track.effects.append(AudioEffect("normalize", {"target_db": -1.0},
                                             name="Нормализация"))
        clip = self._current_clip()
        if clip is not None:
            clip.invalidate()
        self.effects_panel.set_chain(EffectChain(track.effects))
        self.statusMessage.emit("Нормализация включена")
        self.projectChanged.emit(self.project)
        self.refresh()

    def duplicate_clip(self) -> None:
        """Дублировать текущий клип."""
        clip = self._current_clip()
        if clip is None:
            return
        copy = self.project.duplicate_clip(clip.id)
        if copy is not None:
            self._active_clip = copy
            self.statusMessage.emit("Клип продублирован")
            self.projectChanged.emit(self.project)
            self.refresh()

    def undo(self) -> None:
        """Откатить последнее действие."""
        command = self.history.undo()
        if command:
            self.statusMessage.emit(f"Отменено: {command.title}")
            self.projectChanged.emit(self.project)
            self.refresh()

    def redo(self) -> None:
        """Повторить откаченное действие."""
        command = self.history.redo()
        if command:
            self.statusMessage.emit(f"Повторено: {command.title}")
            self.projectChanged.emit(self.project)
            self.refresh()

     # ====== звук
    def preview_mix(self) -> None:
        """Смикшировать проект и отдать на воспроизведение."""
        if self.project.duration <= 0:
            self.statusMessage.emit("Проект пуст")
            return
        self.statusMessage.emit("Рендер микса…")
        submit(lambda: self.project.render(), on_done=self._on_mix_ready,
               on_error=lambda msg, tb: self.statusMessage.emit(f"Ошибка микса: {msg}"),
               name="mix-preview")

    def _on_mix_ready(self, buffer: AudioBuffer) -> None:
        self._preview_buffer = buffer
        self.playRequested.emit(buffer)
        self.statusMessage.emit("Воспроизведение микса")

    def apply_effects(self) -> None:
        """Пересчитать цепочку эффектов активной дорожки и проиграть результат."""
        track = self.project.track(self._active_track)
        if track is None:
            return
        track.effects = [AudioEffect.from_dict(e.to_dict())
                         for e in self.effects_panel.chain.effects]
        for clip in track.clips:
            clip.invalidate()
        self.statusMessage.emit("Эффекты применены")
        self.preview_mix()

    def _on_chain_changed(self) -> None:
        """Сохранение цепочки из панели в модель (без рендера)."""
        track = self.project.track(self._active_track)
        if track is None:
            return
        track.effects = [AudioEffect.from_dict(e.to_dict())
                         for e in self.effects_panel.chain.effects]
        self.projectChanged.emit(self.project)

     # ====== события
    def _on_seek(self, seconds: float) -> None:
        self.waveform.set_playhead(seconds)
        clip = self._current_clip()
        absolute = (clip.start + seconds) if clip else seconds
        self.positionChanged.emit(absolute)

    def _on_selection(self, start: float, end: float) -> None:
        if abs(end - start) < 1e-3:
            self._selection = None
            self.selection_label.setText("Выделение: —")
        else:
            self._selection = (min(start, end), max(start, end))
            length = self._selection[1] - self._selection[0]
            self.selection_label.setText(
                f"Выделение: {format_time(self._selection[0], True)} – "
                f"{format_time(self._selection[1], True)} ({length:.2f} с)")
            self.selectionChanged.emit(*self._selection)

    def _on_bpm(self, value: float) -> None:
        self.project.bpm = float(value)
        self.waveform.set_bpm(value)
        self.projectChanged.emit(self.project)

    def _on_history_change(self) -> None:
        self.undo_button.setEnabled(self.history.can_undo)
        self.redo_button.setEnabled(self.history.can_redo)
        self.undo_button.setToolTip(
            f"Отменить: {self.history.undo_title}" if self.history.can_undo else "Нечего отменять")

     # ====== drag&drop
    def dragEnterEvent(self, event) -> None:  # noqa: N802
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
            self.setStyleSheet(f"QWidget {{ border: 1px dashed {color('ACCENT2')}; }}")

    def dragLeaveEvent(self, event) -> None:  # noqa: N802
        self.setStyleSheet("")

    def dropEvent(self, event) -> None:  # noqa: N802
        self.setStyleSheet("")
        paths = [url.toLocalFile() for url in event.mimeData().urls() if url.isLocalFile()]
        if paths:
            self.add_files(paths)
        event.acceptProposedAction()

     # ====== drag-enabled
    def enable_drop(self) -> None:
        """Включить приём файлов (вызывается главным окном)."""
        self.setAcceptDrops(True)
