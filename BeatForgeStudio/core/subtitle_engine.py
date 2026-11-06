"""Субтитры: SRT/ASS/VTT, ручной редактор и авто-генерация через Whisper.

Возможности:

* :class:`SubtitleCue` — реплика (начало, конец, текст, стиль);
* :class:`SubtitleTrack` — набор реплик: сортировка, сдвиг, экспорт SRT/ASS/VTT,
  парсинг всех трёх форматов, стилизация под FFmpeg (``subtitles``/``ass`` или
  встроенный ``drawtext`` для сжжёного текста);
* :func:`transcribe` — локальная расшифровка через openai-whisper (если
  установлен): ``tiny``/``base``/``small``/``medium``/``large``;
* :class:`SubtitleGenerator` — Qt-обёртка с прогрессом и отменой.

Формат SRT пишется в UTF-8 с BOM-опцией (Windows-плееры любят BOM).
"""

from __future__ import annotations

import logging
import re
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Tuple

from PyQt6.QtCore import QObject, QThread, pyqtSignal

from utils.config import get_config

LOG = logging.getLogger(__name__)

_TIMECODE_RE = re.compile(
    r"(?P<h>\d{1,2}):(?P<m>\d{2}):(?P<s>\d{2})[,.](?P<ms>\d{1,3})")
_ASS_TIME_RE = re.compile(r"(?P<h>\d):(?P<m>\d{2}):(?P<s>\d{2})\.(?P<cs>\d{2})")


def _to_seconds(h: int, m: int, s: int, ms: int = 0) -> float:
    """Собрать секунды из компонентов времени."""
    return int(h) * 3600 + int(m) * 60 + int(s) + ms / 1000.0


def format_srt_time(seconds: float) -> str:
    """Время в формате SRT: ``00:00:01,500``."""
    seconds = max(0.0, float(seconds))
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    whole = int(secs)
    ms = int(round((secs - whole) * 1000))
    if ms == 1000:
        whole += 1
        ms = 0
    return f"{int(hours):02d}:{int(minutes):02d}:{whole:02d},{ms:03d}"


def format_ass_time(seconds: float) -> str:
    """Время в формате ASS: ``0:00:01.50``."""
    seconds = max(0.0, float(seconds))
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    cs = int(round((secs - int(secs)) * 100))
    if cs == 100:
        cs = 99
    return f"{int(hours)}:{int(minutes):02d}:{int(secs):02d}.{cs:02d}"


def format_vtt_time(seconds: float) -> str:
    """Время в формате WebVTT: ``00:00:01.500``."""
    return format_srt_time(seconds).replace(",", ".")


def parse_timecode(value: str) -> float:
    """Разобрать таймкод любого из трёх форматов в секунды."""
    value = value.strip()
    match = _TIMECODE_RE.search(value)
    if match:
        ms_text = match.group("ms")
        ms = int(ms_text.ljust(3, "0")) if len(ms_text) < 3 else int(ms_text)
        return _to_seconds(int(match.group("h")), int(match.group("m")),
                           int(match.group("s")), ms)
    match = _ASS_TIME_RE.search(value)
    if match:
        return _to_seconds(int(match.group("h")), int(match.group("m")),
                           int(match.group("s")), int(match.group("cs")) * 10)
    return 0.0


@dataclass
class SubtitleCue:
    """Одна реплика субтитров.

    Attributes:
        start: Начало, сек.
        end: Конец, сек.
        text: Текст (может содержать переносы строк).
        style: Имя стиля ASS (``Default``, ``TikTok``…).
        speaker: Говорящий (для ASS-диалогов).
    """

    start: float
    end: float
    text: str
    style: str = "Default"
    speaker: str = ""

    @property
    def duration(self) -> float:
        """Длительность реплики, сек."""
        return max(0.0, self.end - self.start)

    def shifted(self, delta: float) -> "SubtitleCue":
        """Копия, сдвинутая по времени на ``delta`` секунд."""
        return SubtitleCue(max(0.0, self.start + delta), max(0.0, self.end + delta),
                           self.text, self.style, self.speaker)

    def to_dict(self) -> dict:
        """Сериализация реплики."""
        return {"start": self.start, "end": self.end, "text": self.text,
                "style": self.style, "speaker": self.speaker}


@dataclass
class SubtitleStyle:
    """Стиль субтитров (для ASS и «сжжёного» текста).

    Attributes:
        font: Шрифт.
        size: Размер.
        primary: Основной цвет ``#RRGGBB``.
        outline: Цвет обводки.
        outline_width: Толщина обводки.
        shadow: Тень 0..4.
        position: ``bottom`` | ``middle`` | ``top``.
        margin_v: Отступ от края, px.
        bold: Жирный.
        background: Полупрозрачная плашка.
    """

    font: str = "Inter"
    size: int = 54
    primary: str = "#FFFFFF"
    outline: str = "#000000"
    outline_width: int = 3
    shadow: int = 1
    position: str = "bottom"
    margin_v: int = 80
    bold: bool = True
    background: bool = False

    @classmethod
    def from_config(cls) -> "SubtitleStyle":
        """Собрать стиль из настроек приложения."""
        cfg = get_config().subtitles
        return cls(font=cfg.font_family, size=cfg.font_size, primary=cfg.primary_color,
                   outline=cfg.outline_color, outline_width=cfg.outline_width,
                   position=cfg.position, margin_v=cfg.margin_v)

    def to_ass_style(self, name: str = "Default") -> str:
        """Строка стиля для секции ``[V4+ Styles]``."""
        alignment = {"bottom": 2, "middle": 5, "top": 8}.get(self.position, 2)
        ass_color = _hex_to_ass(self.primary)
        ass_outline = _hex_to_ass(self.outline)
        return (f"Style: {name},{self.font},{self.size},{ass_color},{ass_color},"
                f"{ass_outline},#00000000,{-1 if self.bold else 0},0,0,0,100,100,0,0,"
                f"1,{self.outline_width},{self.shadow},{alignment},40,40,{self.margin_v},1")


def _hex_to_ass(hex_color: str) -> str:
    """``#RRGGBB`` → ``&H00BBGGRR`` (формат ASS)."""
    value = hex_color.lstrip("#")
    if len(value) != 6:
        return "&H00FFFFFF"
    r, g, b = value[0:2], value[2:4], value[4:6]
    return f"&H00{b}{g}{r}".upper()


class SubtitleTrack:
    """Набор реплик субтитров с экспортом и разбором форматов."""

    def __init__(self, cues: Optional[List[SubtitleCue]] = None,
                 style: Optional[SubtitleStyle] = None) -> None:
        """Args:
            cues: Реплики.
            style: Стиль оформления.
        """
        self.cues: List[SubtitleCue] = sorted(cues or [], key=lambda c: c.start)
        self.style = style or SubtitleStyle.from_config()

    # ------------------------------------------------------------ операции
    def add(self, cue: SubtitleCue) -> SubtitleCue:
        """Добавить реплику и отсортировать."""
        self.cues.append(cue)
        self.cues.sort(key=lambda c: c.start)
        return cue

    def remove(self, index: int) -> None:
        """Удалить реплику по индексу."""
        if 0 <= index < len(self.cues):
            self.cues.pop(index)

    def shift(self, delta: float) -> None:
        """Сдвинуть все реплики по времени."""
        self.cues = [cue.shifted(delta) for cue in self.cues]

    def clear(self) -> None:
        """Удалить все реплики."""
        self.cues.clear()

    @property
    def duration(self) -> float:
        """Конец последней реплики, сек."""
        return max((c.end for c in self.cues), default=0.0)

    def cue_at(self, time_sec: float) -> Optional[SubtitleCue]:
        """Реплика, активная в момент времени."""
        for cue in self.cues:
            if cue.start <= time_sec <= cue.end:
                return cue
        return None

    def text_at(self, time_sec: float) -> str:
        """Текст активной реплики (или пустая строка)."""
        cue = self.cue_at(time_sec)
        return cue.text if cue else ""

    # ------------------------------------------------------------ импорт
    @classmethod
    def from_srt(cls, text: str, style: Optional[SubtitleStyle] = None) -> "SubtitleTrack":
        """Разобрать SRT."""
        cues: List[SubtitleCue] = []
        blocks = re.split(r"\n\s*\n", text.replace("\r\n", "\n").strip())
        for block in blocks:
            lines = [line for line in block.split("\n") if line.strip()]
            if not lines:
                continue
            time_line_index = 0
            if lines[0].strip().isdigit() and len(lines) > 1:
                time_line_index = 1
            if time_line_index >= len(lines) or "-->" not in lines[time_line_index]:
                continue
            start_text, end_text = lines[time_line_index].split("-->", 1)
            body = "\n".join(lines[time_line_index + 1:])
            cues.append(SubtitleCue(parse_timecode(start_text), parse_timecode(end_text),
                                    body.strip()))
        return cls(cues, style)

    @classmethod
    def from_vtt(cls, text: str, style: Optional[SubtitleStyle] = None) -> "SubtitleTrack":
        """Разобрать WebVTT."""
        cleaned = text.replace("\r\n", "\n")
        cleaned = re.sub(r"^WEBVTT.*?\n\n", "", cleaned, flags=re.S)
        return cls.from_srt(cleaned, style)

    @classmethod
    def from_ass(cls, text: str, style: Optional[SubtitleStyle] = None) -> "SubtitleTrack":
        """Разобрать ASS/SSA (секции Events/Dialogue)."""
        cues: List[SubtitleCue] = []
        in_events = False
        for raw_line in text.replace("\r\n", "\n").split("\n"):
            line = raw_line.strip()
            if line.lower().startswith("[events]"):
                in_events = True
                continue
            if line.startswith("[") and in_events:
                in_events = False
            if not in_events or not line.lower().startswith("dialogue:"):
                continue
            payload = line.split(":", 1)[1]
            fields = payload.split(",", 9)
            if len(fields) < 10:
                continue
            start = parse_timecode(fields[1])
            end = parse_timecode(fields[2])
            cue_style = fields[3].strip() or "Default"
            body = fields[9].replace(r"\N", "\n").replace(r"\n", "\n").strip()
            cues.append(SubtitleCue(start, end, body, cue_style))
        return cls(cues, style)

    def load(self, path: str | Path) -> "SubtitleTrack":
        """Загрузить файл субтитров (по расширению)."""
        src = Path(path)
        text = src.read_text(encoding="utf-8-sig", errors="replace")
        suffix = src.suffix.lower()
        if suffix == ".ass" or suffix == ".ssa":
            parsed = SubtitleTrack.from_ass(text, self.style)
        elif suffix == ".vtt":
            parsed = SubtitleTrack.from_vtt(text, self.style)
        else:
            parsed = SubtitleTrack.from_srt(text, self.style)
        self.cues = parsed.cues
        return self

    # ------------------------------------------------------------ экспорт
    def to_srt(self, with_bom: bool = False) -> str:
        """Собрать SRT-текст."""
        blocks: List[str] = []
        for i, cue in enumerate(sorted(self.cues, key=lambda c: c.start), start=1):
            blocks.append(f"{i}\n{format_srt_time(cue.start)} --> {format_srt_time(cue.end)}\n"
                          f"{cue.text}\n")
        text = "\n".join(blocks)
        return ("\ufeff" + text) if with_bom else text

    def to_vtt(self) -> str:
        """Собрать WebVTT-текст."""
        blocks = ["WEBVTT", ""]
        for cue in sorted(self.cues, key=lambda c: c.start):
            blocks.append(f"{format_vtt_time(cue.start)} --> {format_vtt_time(cue.end)}")
            blocks.append(cue.text)
            blocks.append("")
        return "\n".join(blocks)

    def to_ass(self, title: str = "BeatForge Studio") -> str:
        """Собрать ASS-файл со стилем."""
        header = [
            "[Script Info]",
            f"Title: {title}",
            "ScriptType: v4.00+",
            "PlayResX: 1920",
            "PlayResY: 1080",
            "WrapStyle: 0",
            "ScaledBorderAndShadow: yes",
            "",
            "[V4+ Styles]",
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
            "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, "
            "ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, "
            "MarginL, MarginR, MarginV, Encoding",
            self.style.to_ass_style("Default"),
            "",
            "[Events]",
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, "
            "Effect, Text",
        ]
        lines = list(header)
        for cue in sorted(self.cues, key=lambda c: c.start):
            body = cue.text.replace("\n", r"\N")
            lines.append(
                f"Dialogue: 0,{format_ass_time(cue.start)},{format_ass_time(cue.end)},"
                f"{cue.style or 'Default'},{cue.speaker},0,0,0,,{body}")
        return "\n".join(lines) + "\n"

    def save(self, path: str | Path, fmt: Optional[str] = None) -> Path:
        """Сохранить субтитры в файл.

        Args:
            path: Путь назначения.
            fmt: ``srt`` | ``vtt`` | ``ass`` (по умолчанию — по расширению).

        Returns:
            Путь к файлу.
        """
        out = Path(path)
        kind = (fmt or out.suffix.lstrip(".") or "srt").lower()
        if kind == "vtt":
            content = self.to_vtt()
        elif kind in ("ass", "ssa"):
            content = self.to_ass()
        else:
            content = self.to_srt()
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(content, encoding="utf-8")
        return out

    # ------------------------------------------------------------ FFmpeg
    def to_ffmpeg_filter(self, burned: bool = True) -> str:
        """Фильтр для вжигания субтитров (нужен файл — сохраните его первым).

        Для встраивания «сжжёного» текста без внешнего файла используйте
        :meth:`to_drawtext_filters`.

        Args:
            burned: True — вжечь (``subtitles``), False — только стиль.

        Returns:
            Строка фильтра или пустая строка, если реплик нет.
        """
        return "subtitles='{path}'" if self.cues else ""

    def to_drawtext_filters(self) -> str:
        """Фильтры drawtext для каждой реплики (без внешних файлов)."""
        from core.video_engine import build_drawtext

        filters: List[str] = []
        for cue in self.cues:
            y_expr = {"bottom": f"h-text_h-{self.style.margin_v}",
                      "middle": "(h-text_h)/2",
                      "top": str(self.style.margin_v)}.get(self.style.position,
                                                           f"h-text_h-{self.style.margin_v}")
            for line_index, line in enumerate(cue.text.split("\n")):
                filters.append(build_drawtext(
                    text=line, font_size=self.style.size, color=self.style.primary,
                    border=self.style.outline_width, border_color=self.style.outline,
                    y=f"{y_expr}+{line_index * int(self.style.size * 1.25)}",
                    font_family=self.style.font, start=cue.start, end=cue.end,
                    box=self.style.background,
                ))
        return ",".join(filters)

    # ------------------------------------------------------------ сериализация
    def to_dict(self) -> dict:
        """Сериализация в проект."""
        return {"style": self.style.__dict__, "cues": [c.to_dict() for c in self.cues]}

    @classmethod
    def from_dict(cls, data: dict) -> "SubtitleTrack":
        """Восстановить из проекта."""
        style_data = data.get("style") or {}
        style = SubtitleStyle(**{k: v for k, v in style_data.items()
                                 if k in SubtitleStyle.__dataclass_fields__})
        cues = [SubtitleCue(**c) for c in (data.get("cues") or [])]
        return cls(cues, style)


# ============================================================
#  Whisper
# ============================================================
def whisper_available() -> bool:
    """Установлен ли openai-whisper."""
    try:
        import whisper  # noqa: F401

        return True
    except Exception:
        return False


def transcribe(audio_path: str | Path, model_size: str = "base",
               language: Optional[str] = "ru",
               progress_cb=None,
               cancel_event: Optional[threading.Event] = None) -> SubtitleTrack:
    """Расшифровать аудио/видео через Whisper (локально, без интернета).

    Args:
        audio_path: Медиафайл (FFmpeg внутри whisper сам извлечёт звук).
        model_size: ``tiny`` | ``base`` | ``small`` | ``medium`` | ``large``.
        language: Код языка (``ru``/``en``) или None для автоопределения.
        progress_cb: Колбэк ``(процент, статус)``.
        cancel_event: Флаг отмены.

    Returns:
        :class:`SubtitleTrack` с репликами.

    Raises:
        RuntimeError: если whisper не установлен или произошла ошибка.
    """
    try:
        import whisper
    except Exception as exc:
        raise RuntimeError(
            "openai-whisper не установлен. Установите: pip install openai-whisper"
        ) from exc

    if cancel_event is not None and cancel_event.is_set():
        raise RuntimeError("Расшифровка отменена")

    if progress_cb:
        progress_cb(5, f"загрузка модели {model_size}…")
    model = whisper.load_model(model_size)
    if progress_cb:
        progress_cb(25, "распознавание речи…")

    result = model.transcribe(str(audio_path), language=language, verbose=False,
                              word_timestamps=False)
    if cancel_event is not None and cancel_event.is_set():
        raise RuntimeError("Расшифровка отменена")

    track = SubtitleTrack(style=SubtitleStyle.from_config())
    for segment in result.get("segments", []):
        text = str(segment.get("text", "")).strip()
        if not text:
            continue
        track.add(SubtitleCue(float(segment.get("start", 0.0)),
                              float(segment.get("end", 0.0)), text))
    if progress_cb:
        progress_cb(100, f"готово: {len(track.cues)} реплик")
    LOG.info("Whisper: %s реплик из %s", len(track.cues), audio_path)
    return track


class SubtitleWorker(QThread):
    """Поток автогенерации субтитров.

    Сигналы:
        progress(int, str): Прогресс.
        finished_ok(object): Готовый :class:`SubtitleTrack`.
        failed(str): Ошибка.
    """

    progress = pyqtSignal(int, str)
    finished_ok = pyqtSignal(object)
    failed = pyqtSignal(str)

    def __init__(self, media_path: str | Path, model_size: str = "base",
                 language: Optional[str] = "ru", parent: Optional[QObject] = None) -> None:
        """Args:
            media_path: Медиафайл.
            model_size: Размер модели Whisper.
            language: Язык.
            parent: Родитель.
        """
        super().__init__(parent)
        self._path = str(media_path)
        self._model = model_size
        self._language = language
        self._cancel = threading.Event()

    def cancel(self) -> None:
        """Отменить расшифровку."""
        self._cancel.set()

    def run(self) -> None:  # noqa: D401
        """Выполнить расшифровку в фоне."""
        try:
            track = transcribe(self._path, self._model, self._language,
                               progress_cb=lambda p, s: self.progress.emit(p, s),
                               cancel_event=self._cancel)
            self.finished_ok.emit(track)
        except Exception as exc:
            LOG.exception("Whisper упал")
            self.failed.emit(str(exc))
