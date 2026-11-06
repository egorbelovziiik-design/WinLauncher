"""Видеодвижок BeatForge Studio — обёртка над FFmpeg.

Модуль даёт всё, что нужно редактору:

* :class:`VideoEngine` — превью-кадры (:meth:`frame_at`), миниатюры
  (:meth:`thumbnail`), плеер-последовательность кадров (:class:`PreviewPlayer`),
  прокси-превью (:meth:`make_preview_proxy`) и финальный рендер (:meth:`render`);
* :func:`build_render_command` — сборка комплексного ``filter_complex`` из
  :class:`Scene`: клипы, дорожки, оверлеи (текст/PiP), переходы ``xfade``,
  аудиомикс, громкости, ``loudnorm``;
* :func:`aspect_filter` — пресеты соцсетей 16:9 / 9:16 / 1:1 / 4:5 с blur-фоном.

Вся тяжёлая работа выполняется в фоновых потоках; для UI есть
:class:`RenderWorker` с прогрессом и отменой (Qt-сигналы).

Ограничения: рендер идёт одним проходом FFmpeg, поэтому сложные сцены требуют
промежуточной нормализации клипов (разные fps/разрешения приводятся фильтрами
``scale``/``fps``/``setsar`` автоматически).
"""

from __future__ import annotations

import json
import logging
import math
import os
import re
import shutil
import signal
import subprocess
import tempfile
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, List, Optional, Sequence, Tuple

from PyQt6.QtCore import QObject, QThread, pyqtSignal

from utils.config import get_config
from utils.ffmpeg_helper import ensure_ffmpeg, ffmpeg_path, probe, run

LOG = logging.getLogger(__name__)

VIDEO_EXTENSIONS: Tuple[str, ...] = (
    ".mp4", ".mov", ".avi", ".mkv", ".webm", ".gif", ".m4v", ".wmv", ".flv", ".mts",
)
IMAGE_EXTENSIONS: Tuple[str, ...] = (".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tiff")
MEDIA_EXTENSIONS: Tuple[str, ...] = VIDEO_EXTENSIONS + IMAGE_EXTENSIONS

#: Пресеты соотношений сторон для соцсетей.
ASPECT_PRESETS: Dict[str, Tuple[int, int]] = {
    "16:9": (1920, 1080),
    "9:16": (1080, 1920),
    "1:1": (1080, 1080),
    "4:5": (1080, 1350),
    "4:3": (1440, 1080),
    "21:9": (2560, 1080),
}

#: Готовые пресеты экспорта.
RENDER_PRESETS: Dict[str, Dict[str, Any]] = {
    "youtube_1080": {"label": "YouTube 1080p", "width": 1920, "height": 1080,
                     "fps": 30, "codec": "libx264", "crf": 20, "audio_bitrate": 192},
    "youtube_4k": {"label": "YouTube 4K", "width": 3840, "height": 2160,
                   "fps": 30, "codec": "libx264", "crf": 22, "audio_bitrate": 256},
    "tiktok": {"label": "TikTok / Reels 1080×1920", "width": 1080, "height": 1920,
               "fps": 30, "codec": "libx264", "crf": 20, "audio_bitrate": 192},
    "instagram_post": {"label": "Instagram 1:1", "width": 1080, "height": 1080,
                       "fps": 30, "codec": "libx264", "crf": 20, "audio_bitrate": 192},
    "max_quality": {"label": "Максимальное качество (H.265)", "width": 1920, "height": 1080,
                    "fps": 60, "codec": "libx265", "crf": 16, "audio_bitrate": 320},
    "small_size": {"label": "Минимальный размер", "width": 1280, "height": 720,
                   "fps": 30, "codec": "libx264", "crf": 28, "audio_bitrate": 128},
}

#: Кодовые имена фильтров цвета → FFmpeg-выражения.
COLOR_FILTERS: Dict[str, str] = {
    "cinematic": "eq=contrast=1.12:saturation=0.88:brightness=-0.02,colorbalance=rs=-0.04:bs=0.06",
    "vintage": "curves=preset=vintage,eq=saturation=0.8:contrast=1.05",
    "bw": "hue=s=0,eq=contrast=1.15",
    "vhs": "eq=saturation=1.4:contrast=1.1,noise=alls=12:allf=t+u",
    "neon": "eq=saturation=1.6:contrast=1.15,colorbalance=bs=0.15:rs=0.08",
    "cold": "colorbalance=bs=0.18:gs=0.04:rs=-0.08,eq=saturation=0.9",
    "warm": "colorbalance=rs=0.16:gs=0.03:bs=-0.10,eq=saturation=1.05",
    "grain": "noise=alls=9:allf=t,eq=contrast=1.04",
    "vignette": "vignette=PI/5",
    "sharpen": "unsharp=5:5:0.9:3:3:0.0",
    "soft": "gblur=sigma=1.2,eq=contrast=0.98",
}


# ============================================================
#  Модель сцены для рендера
# ============================================================
@dataclass
class ClipSpec:
    """Один клип на таймлайне.

    Attributes:
        path: Исходный медиафайл.
        start: Начало на таймлайне, сек.
        duration: Длительность клипа на таймлайне, сек.
        source_start: С какого момента брать исходник, сек.
        speed: Скорость (0.25–4.0).
        volume: Громкость встроенного звука 0..1.
        mute: Отключить звук клипа.
        filters: Список FFmpeg-фильтров (уже в виде строк).
        text_overlays: Список словарей ``{text, ...}`` для drawtext.
        opacity: Прозрачность 0..1 (для оверлеев).
        transform: ``{"scale":1.0,"x":0,"y":0,"rotate":0}``.
        is_image: Клип — картинка (без потока времени).
        freeze_at: Момент кадра для freeze-frame (сек в исходнике).
    """

    path: str
    start: float = 0.0
    duration: float = 5.0
    source_start: float = 0.0
    speed: float = 1.0
    volume: float = 1.0
    mute: bool = False
    filters: List[str] = field(default_factory=list)
    text_overlays: List[Dict[str, Any]] = field(default_factory=list)
    opacity: float = 1.0
    transform: Dict[str, float] = field(default_factory=dict)
    is_image: bool = False
    freeze_at: Optional[float] = None
    transition: Optional[str] = None
    transition_duration: float = 0.5

    @property
    def end(self) -> float:
        """Конец клипа на таймлайне, сек."""
        return self.start + self.duration


@dataclass
class TrackSpec:
    """Дорожка таймлайна.

    Attributes:
        kind: ``"video"`` | ``"audio"`` | ``"text"`` | ``"image"``.
        clips: Клипы дорожки.
        volume: Громкость дорожки (для аудио), 0..1.
        mute: Дорожка выключена.
        name: Подпись для логов.
    """

    kind: str
    clips: List[ClipSpec] = field(default_factory=list)
    volume: float = 1.0
    mute: bool = False
    name: str = ""

    @property
    def end(self) -> float:
        """Конец последнего клипа, сек."""
        return max((c.end for c in self.clips), default=0.0)


@dataclass
class Scene:
    """Полное описание проекта для рендера.

    Attributes:
        tracks: Дорожки (видео/аудио/текст).
        duration: Общая длительность, сек.
        width: Ширина кадра.
        height: Высота кадра.
        fps: Частота кадров.
        background: Цвет фона (``#121212``).
        aspect_mode: ``"crop"`` (кадрировать) | ``"fit"`` (вписать) | ``"blur"``.
        master_volume: Громкость мастера.
        normalize_audio: Включить loudnorm.
        audio_codec: Кодек аудио.
        audio_bitrate: Битрейт аудио, кбит/с.
        extra_filters: Глобальные фильтры после сборки кадра (текстовые слои,
            субтитры drawtext, водяные знаки). Применяются к ``[vout]``.
    """

    tracks: List[TrackSpec] = field(default_factory=list)
    duration: float = 0.0
    width: int = 1920
    height: int = 1080
    fps: int = 30
    background: str = "#121212"
    aspect_mode: str = "blur"
    master_volume: float = 1.0
    normalize_audio: bool = True
    audio_codec: str = "aac"
    audio_bitrate: int = 192
    extra_filters: List[str] = field(default_factory=list)

    @property
    def duration_or_max(self) -> float:
        """Длительность: явная или максимальная по клипам."""
        if self.duration > 0:
            return self.duration
        return max((t.end for t in self.tracks), default=0.0)


# ============================================================
#  Вспомогательные функции
# ============================================================
def hex_to_ffcolor(value: str, alpha: Optional[float] = None) -> str:
    """Преобразовать ``#RRGGBB`` в формат FFmpeg (``0xRRGGBB``).

    Args:
        value: Цвет в hex.
        alpha: Прозрачность 0..1 (для ``drawtext``/``drawbox``).

    Returns:
        Строка цвета для FFmpeg (``0xRRGGBB`` или ``0xRRGGBB@0.50``).
    """
    color = value.strip()
    if color.startswith("#"):
        color = "0x" + color[1:]
    elif not color.startswith("0x"):
        color = "0x" + color
    if alpha is None:
        return color
    return f"{color}@{max(0.0, min(1.0, alpha)):.2f}"


def escape_path(path: str | Path) -> str:
    """Экранировать путь для использования внутри ``-filter_complex``."""
    text = str(path).replace("\\", "/")
    text = text.replace(":", r"\:")
    text = text.replace("'", r"\'")
    return text


def _quote_concat(items: Iterable[str]) -> str:
    """Склеить элементы ``concat``-фильтра через ``|``."""
    return "|".join(items)


def aspect_filter(mode: str, width: int, height: int, bg: str = "#000000") -> str:
    """Построить фильтр приведения кадра к целевому соотношению.

    Args:
        mode: ``crop`` | ``fit`` | ``blur`` | ``stretch``.
        width: Ширина результата.
        height: Высота результата.
        bg: Цвет фона для ``fit``.

    Returns:
        Строка фильтра (начинается с ``scale``/``split``).
    """
    color = hex_to_ffcolor(bg)
    if mode == "crop":
        return (f"scale={width}:{height}:force_original_aspect_ratio=increase,"
                f"crop={width}:{height}")
    if mode == "stretch":
        return f"scale={width}:{height}"
    if mode == "fit":
        return (f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color={color}")
    # blur: размытый фон + вписанный кадр (как в CapCut)
    return (f"split=2[__bg][__fg];"
            f"[__bg]scale={width}:{height}:force_original_aspect_ratio=increase,"
            f"crop={width}:{height},gblur=sigma=28:steps=2,eq=brightness=-0.08[__bg2];"
            f"[__fg]scale={width}:{height}:force_original_aspect_ratio=decrease[__fg2];"
            f"[__bg2][__fg2]overlay=(W-w)/2:(H-h)/2")


def build_drawtext(text: str, font_file: Optional[str] = None, font_size: int = 54,
                   color: str = "#FFFFFF", border: int = 3, border_color: str = "#000000",
                   x: str = "(w-text_w)/2", y: str = "h-text_h-80",
                   font_family: str = "Inter", start: Optional[float] = None,
                   end: Optional[float] = None, box: bool = False) -> str:
    """Собрать фильтр ``drawtext`` для одного текстового слоя.

    Args:
        text: Текст (экранируется автоматически).
        font_file: Путь к TTF-файлу (``fontfile=``). Если задан — используется он.
        font_size: Размер шрифта в px.
        color: Цвет текста ``#RRGGBB``.
        border: Толщина обводки px.
        border_color: Цвет обводки.
        x, y: Позиция (выражения FFmpeg).
        font_family: Имя семейства шрифта (когда нет файла).
        start: Время появления, сек.
        end: Время исчезновения, сек.
        box: Рисовать полупрозрачную подложку.

    Returns:
        Строка фильтра.
    """
    safe = (text.replace("\\", r"\\").replace(":", r"\:").replace("'", r"\'")
            .replace("%", r"\%").replace(",", r"\,").replace("[", r"\[")
            .replace("]", r"\]").replace(";", r"\;"))
    parts = [f"text='{safe}'", f"fontsize={int(font_size)}",
             f"fontcolor={hex_to_ffcolor(color)}",
             f"borderw={int(border)}", f"bordercolor={hex_to_ffcolor(border_color)}",
             f"x={x}", f"y={y}"]
    if font_file and Path(font_file).exists():
        parts.append(f"fontfile='{escape_path(font_file)}'")
    else:
        parts.append(f"font='{font_family}'")
    if start is not None or end is not None:
        a = start if start is not None else 0.0
        b = end if end is not None else 1e6
        parts.append(f"enable='between(t,{a:.3f},{b:.3f})'")
    if box:
        parts.append("box=1")
        parts.append(f"boxcolor={hex_to_ffcolor('#000000', 0.45)}")
        parts.append("boxborderw=18")
    return "drawtext=" + ":".join(parts)


def speed_filters(speed: float, keep_pitch: bool = True) -> Tuple[str, str]:
    """Фильтры скорости для видео и аудио.

    Args:
        speed: Коэффициент скорости 0.25–4.0.
        keep_pitch: Сохранять тон (``atempo``) или менять (``asetrate``).

    Returns:
        Пара ``(video_filter, audio_filter)``.
    """
    speed = max(0.25, min(4.0, float(speed)))
    video = f"setpts={1.0 / speed:.6f}*PTS"
    if abs(speed - 1.0) < 1e-3:
        return video, "anull"
    if keep_pitch:
        remaining = speed
        chain = []
        while remaining > 2.0:
            chain.append("atempo=2.0")
            remaining /= 2.0
        while remaining < 0.5:
            chain.append("atempo=0.5")
            remaining /= 0.5
        chain.append(f"atempo={remaining:.6f}")
        return video, ",".join(chain)
    return video, f"asetrate={int(44100 * speed)},aresample=44100"


# ============================================================
#  Сборка команды рендера
# ============================================================
def build_render_command(scene: Scene, out_path: str | Path, *,
                         codec: str = "libx264", crf: int = 20,
                         preset: str = "medium", bitrate: Optional[str] = None,
                         pixel_format: str = "yuv420p",
                         extra_args: Optional[List[str]] = None) -> List[str]:
    """Собрать полную команду FFmpeg для сцены.

    Каждый вход нормализуется (fps, разрешение, SAR), видео-клипы раскладываются
    по дорожкам, между соседними клипами дорожки вставляется ``xfade`` (если задан
    переход), дорожки накладываются ``overlay``, аудио микшируется через ``amix``
    с задержками по таймлайну и громкостями; в конце — ``loudnorm``/``alimiter``.

    Args:
        scene: Описание сцены.
        out_path: Куда рендерить.
        codec: Видеокодек (``libx264``/``libx265``/``h264_nvenc``).
        crf: Качество (меньше — лучше), используется если не задан ``bitrate``.
        preset: Пресет кодека (``medium``/``fast``/``slow``).
        bitrate: Битрейт видео, например ``"12M"`` (перебивает CRF).
        pixel_format: Формат пикселей (``yuv420p`` для совместимости).
        extra_args: Дополнительные аргументы FFmpeg.

    Returns:
        Список аргументов, первый элемент — путь к ffmpeg.
    """
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден — рендер невозможен")

    width, height, fps = scene.width, scene.height, scene.fps
    duration = max(0.1, scene.duration_or_max)
    sr = get_config().audio.sample_rate

    inputs: List[List[str]] = []
    filter_parts: List[str] = []
    video_inputs: List[Tuple[str, ClipSpec, TrackSpec]] = []
    audio_inputs: List[Tuple[str, ClipSpec, TrackSpec]] = []

    for track in scene.tracks:
        if track.mute:
            continue
        for clip in track.clips:
            if not Path(clip.path).exists():
                LOG.warning("Клип пропущен (нет файла): %s", clip.path)
                continue
            is_image = clip.is_image or Path(clip.path).suffix.lower() in IMAGE_EXTENSIONS
            idx = len(inputs)
            args: List[str] = []
            if is_image:
                args += ["-loop", "1", "-t", f"{max(0.1, clip.duration):.3f}", "-i", str(clip.path)]
            else:
                src_at = clip.freeze_at if clip.freeze_at is not None else clip.source_start
                args += ["-ss", f"{max(0.0, src_at):.3f}",
                         "-t", f"{max(0.1, clip.duration * clip.speed):.3f}",
                         "-i", str(clip.path)]
            inputs.append(args)
            label = f"in{idx}"
            has_video = is_image or _has_video(clip.path)
            has_audio = (not is_image) and (not clip.mute) and _has_audio(clip.path)
            if track.kind in ("video", "text", "image") and has_video:
                video_inputs.append((label, clip, track))
            if has_audio:
                audio_inputs.append((label, clip, track))

    # ---------------- видео: нормализация клипов
    clip_labels: List[Tuple[str, ClipSpec, TrackSpec]] = []
    for i, (label, clip, track) in enumerate(video_inputs):
        steps = [
            f"fps={fps}",
            f"scale={width}:{height}:force_original_aspect_ratio=decrease",
            f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color={hex_to_ffcolor(scene.background)}",
            "setsar=1",
        ]
        if abs(clip.speed - 1.0) > 1e-3:
            steps.insert(0, f"setpts={1.0 / clip.speed:.6f}*PTS")
        steps.append(f"trim=duration={max(0.05, clip.duration):.3f}")
        steps.append("format=rgba")
        steps += [f for f in clip.filters if f]
        for txt_layer in clip.text_overlays:
            steps.append(build_drawtext(
                text=str(txt_layer.get("text", "")),
                font_size=int(txt_layer.get("size", 54)),
                color=str(txt_layer.get("color", "#FFFFFF")),
                border=int(txt_layer.get("border", 3)),
                border_color=str(txt_layer.get("border_color", "#000000")),
                x=str(txt_layer.get("x", "(w-text_w)/2")),
                y=str(txt_layer.get("y", "h-text_h-80")),
                font_file=txt_layer.get("font_file"),
                font_family=str(txt_layer.get("font", "Inter")),
                start=txt_layer.get("start"),
                end=txt_layer.get("end"),
                box=bool(txt_layer.get("box", False)),
            ))
        if clip.opacity < 0.999:
            steps.append(f"colorchannelmixer=aa={clip.opacity:.3f}")
        out_label = f"cv{i}"
        filter_parts.append(f"[{label}:v]" + ",".join(steps) + f"[{out_label}]")
        clip_labels.append((out_label, clip, track))

    # ---------------- видео: склейка по дорожкам + переходы
    grouped: List[List[Tuple[str, ClipSpec, TrackSpec]]] = []
    for item in sorted(clip_labels, key=lambda it: (it[2].name, it[1].start)):
        if grouped and grouped[-1][0][2].name == item[2].name:
            grouped[-1].append(item)
        else:
            grouped.append([item])

    track_outputs: List[str] = []
    xfade_counter = 0
    for g_index, items in enumerate(grouped):
        items = sorted(items, key=lambda it: it[1].start)
        current, current_clip, _ = items[0]
        timeline_end = current_clip.start + current_clip.duration
        for next_label, next_clip, _ in items[1:]:
            out_label = f"tr{g_index}_{xfade_counter}"
            xfade_counter += 1
            if next_clip.transition:
                dur = max(0.1, min(2.0, next_clip.transition_duration))
                offset = max(0.0, timeline_end - dur)
                filter_parts.append(
                    f"[{current}][{next_label}]xfade=transition={next_clip.transition}:"
                    f"duration={dur:.3f}:offset={offset:.3f}[{out_label}]"
                )
                timeline_end = offset + dur + next_clip.duration - dur
            else:
                delay = max(0.0, next_clip.start)
                filter_parts.append(
                    f"[{current}][{next_label}]overlay=eof_action=pass"
                    f":x=0:y=0:enable='gte(t,{delay:.3f})'[{out_label}]"
                )
                timeline_end = max(timeline_end, next_clip.start + next_clip.duration)
            current = out_label
        track_outputs.append(current)

    if track_outputs:
        current = track_outputs[0]
        for i, other in enumerate(track_outputs[1:], start=1):
            out_label = f"ov{i}"
            filter_parts.append(f"[{current}][{other}]overlay=format=auto:eof_action=pass[{out_label}]")
            current = out_label
        extra = ",".join(f for f in scene.extra_filters if f)
        if extra:
            filter_parts.append(
                f"[{current}]trim=duration={duration:.3f},format={pixel_format},"
                f"tpad=stop_mode=add:stop_duration={duration:.3f},{extra}[vout]"
            )
        else:
            filter_parts.append(
                f"[{current}]trim=duration={duration:.3f},format={pixel_format},"
                f"tpad=stop_mode=add:stop_duration={duration:.3f}[vout]"
            )
    else:
        inputs.append(["-f", "lavfi", "-i",
                       f"color=c={hex_to_ffcolor(scene.background)}:s={width}x{height}"
                       f":r={fps}:d={duration:.3f}"])
        filter_parts.append(f"[{len(inputs) - 1}:v]format={pixel_format}[vout]")

    # ---------------- аудио
    audio_labels: List[str] = []
    for i, (label, clip, track) in enumerate(audio_inputs):
        steps = [f"aresample={sr}", "aformat=channel_layouts=stereo"]
        if abs(clip.speed - 1.0) > 1e-3:
            _, af = speed_filters(clip.speed, keep_pitch=True)
            steps.append(af)
        gain = max(0.0, min(2.0, clip.volume * track.volume * scene.master_volume))
        steps.append(f"volume={gain:.4f}")
        delay_ms = int(max(0.0, clip.start) * 1000)
        steps.append(f"adelay={delay_ms}|{delay_ms}")
        steps.append(f"apad=whole_dur={duration:.3f}")
        steps.append(f"atrim=0:{duration:.3f}")
        out_label = f"ca{i}"
        filter_parts.append(f"[{label}:a]" + ",".join(steps) + f"[{out_label}]")
        audio_labels.append(out_label)

    if audio_labels:
        if len(audio_labels) == 1:
            mix_label = audio_labels[0]
        else:
            mix_label = "amix_all"
            filter_parts.append(
                "".join(f"[{a}]" for a in audio_labels)
                + f"amix=inputs={len(audio_labels)}:duration=longest:dropout_transition=0"
                  f":normalize=0[{mix_label}]"
            )
        post = []
        if scene.normalize_audio:
            post.append("loudnorm=I=-14:TP=-1.0:LRA=11")
        post.append("alimiter=limit=0.98")
        filter_parts.append(f"[{mix_label}]" + ",".join(post) + "[aout]")
    else:
        inputs.append(["-f", "lavfi", "-t", f"{duration:.3f}", "-i",
                       f"anullsrc=r={sr}:cl=stereo"])
        filter_parts.append(f"[{len(inputs) - 1}:a]atrim=0:{duration:.3f}[aout]")

    args: List[str] = [ff, "-hide_banner", "-nostdin", "-y",
                       "-progress", "pipe:1", "-loglevel", "error"]
    for chunk in inputs:
        args += chunk
    args += ["-filter_complex", ";".join(filter_parts),
             "-map", "[vout]", "-map", "[aout]",
             "-c:v", codec, "-pix_fmt", pixel_format, "-preset", preset,
             "-r", str(fps), "-s", f"{width}x{height}", "-t", f"{duration:.3f}",
             "-c:a", scene.audio_codec, "-b:a", f"{scene.audio_bitrate}k",
             "-movflags", "+faststart"]
    if bitrate:
        args += ["-b:v", bitrate]
    else:
        args += ["-crf", str(crf)]
    if extra_args:
        args += extra_args
    args.append(str(out_path))
    return args


def _has_video(path: str) -> bool:
    """Есть ли в файле видеопоток (картинки считаются видео)."""
    suffix = Path(path).suffix.lower()
    if suffix in IMAGE_EXTENSIONS:
        return True
    if suffix in VIDEO_EXTENSIONS:
        try:
            streams = probe(path).get("streams", [])
            if not streams:
                return True
            return any(s.get("codec_type") == "video" for s in streams)
        except Exception:
            return True
    return False


def _has_audio(path: str) -> bool:
    """Есть ли в файле аудиопоток."""
    if Path(path).suffix.lower() in IMAGE_EXTENSIONS:
        return False
    try:
        streams = probe(path).get("streams", [])
        if not streams:
            return True
        return any(s.get("codec_type") == "audio" for s in streams)
    except Exception:
        return False


# ============================================================
#  Движок
# ============================================================
class VideoEngine(QObject):
    """Утилиты работы с видео: кадры, миниатюры, прокси, рендер.

    Сигналы:
        errorOccurred(str): Ошибка (для QMessageBox/toast).
        renderProgress(int, str): Прогресс рендера (0..100, «время/всего»).
        renderFinished(str): Путь готового файла.
    """

    errorOccurred = pyqtSignal(str)
    renderProgress = pyqtSignal(int, str)
    renderFinished = pyqtSignal(str)

    def __init__(self, parent: Optional[QObject] = None) -> None:
        """Args:
            parent: Родительский объект.
        """
        super().__init__(parent)
        self._cfg = get_config().video
        status = ensure_ffmpeg()
        self._available = status.available
        if not status.available:
            LOG.warning("FFmpeg недоступен: %s", status.message)

    @property
    def available(self) -> bool:
        """Доступен ли FFmpeg."""
        return self._available

    # ------------------------------------------------------------ кадры
    def frame_at(self, path: str | Path, at: float = 0.0,
                 width: Optional[int] = None) -> Optional[bytes]:
        """Получить PNG-кадр видео в момент ``at``.

        Args:
            path: Видеофайл.
            at: Момент времени, сек.
            width: Ширина кадра (по умолчанию из конфига).

        Returns:
            Байты PNG или None при ошибке.
        """
        ff = ffmpeg_path()
        if not ff:
            return None
        w = width or self._cfg.preview_width
        cmd = [ff, "-hide_banner", "-loglevel", "error", "-ss", f"{max(0.0, at):.3f}",
               "-i", str(path), "-frames:v", "1", "-vf", f"scale={w}:-2",
               "-f", "image2pipe", "-vcodec", "png", "-"]
        try:
            proc = subprocess.run(cmd, capture_output=True, timeout=60)
            return proc.stdout or None
        except Exception as exc:
            LOG.debug("frame_at(%s, %.2f) failed: %s", path, at, exc)
            return None

    def thumbnail(self, path: str | Path, at: float = 0.5, width: int = 480) -> Optional[Path]:
        """Сохранить миниатюру видео в кэш и вернуть путь.

        Args:
            path: Видеофайл.
            at: Момент времени.
            width: Ширина миниатюры.

        Returns:
            Путь к PNG или None.
        """
        data = self.frame_at(path, at, width)
        if not data:
            return None
        cfg = get_config()
        out_dir = Path(cfg.paths.thumbnails_dir)
        out_dir.mkdir(parents=True, exist_ok=True)
        stem = f"{Path(path).stem}_{int(at * 1000)}_{width}"
        out = out_dir / (re.sub(r"[^\w\-]+", "_", stem) + ".png")
        out.write_bytes(data)
        return out

    def make_preview_proxy(self, path: str | Path, out_dir: Optional[Path] = None,
                           max_seconds: float = 0.0) -> Optional[Path]:
        """Создать низкокачественный прокси-файл для плавного превью.

        Args:
            path: Исходное видео.
            out_dir: Куда положить (по умолчанию кэш/прокси).
            max_seconds: Ограничить длительность (0 — полностью).

        Returns:
            Путь к прокси-файлу или None.
        """
        ff = ffmpeg_path()
        if not ff:
            return None
        cfg = get_config()
        target_dir = out_dir or (Path(cfg.paths.cache_dir) / "proxy")
        target_dir.mkdir(parents=True, exist_ok=True)
        out = target_dir / f"{Path(path).stem}_proxy.mp4"
        if out.exists() and out.stat().st_mtime >= Path(path).stat().st_mtime:
            return out
        cmd = [ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(path)]
        if max_seconds > 0:
            cmd += ["-t", f"{max_seconds:.3f}"]
        cmd += ["-vf", f"scale={self._cfg.preview_width}:-2,fps={self._cfg.preview_fps}",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "30",
                "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", str(out)]
        try:
            run(cmd, timeout=3600)
            return out
        except Exception as exc:
            LOG.warning("Не удалось создать прокси для %s: %s", path, exc)
            return None

    # ------------------------------------------------------------ рендер
    def render(self, scene: Scene, out_path: str | Path, *,
               on_progress: Optional[Callable[[int, str], None]] = None,
               cancel_event: Optional[threading.Event] = None,
               preset_data: Optional[Dict[str, Any]] = None,
               extra_args: Optional[List[str]] = None) -> Path:
        """Отрендерить сцену в файл (синхронно — вызывать из фонового потока!).

        Args:
            scene: Описание сцены.
            out_path: Файл результата.
            on_progress: Callback прогресса (процент, строка статуса).
            cancel_event: Флаг отмены (потокобезопасный ``threading.Event``).
            preset_data: Переопределение кодека/crf/битрейта.
            extra_args: Доп. аргументы FFmpeg.

        Returns:
            Путь к готовому файлу.

        Raises:
            RuntimeError: FFmpeg отсутствует или рендер упал/отменён.
        """
        if not self._available:
            raise RuntimeError("FFmpeg не найден — рендер невозможен")
        out = Path(out_path)
        out.parent.mkdir(parents=True, exist_ok=True)

        params = {"codec": self._cfg.codec, "crf": self._cfg.crf,
                  "bitrate": None, "preset": "medium", "pixel_format": "yuv420p"}
        if preset_data:
            params.update({k: v for k, v in preset_data.items() if k in params})
        cmd = build_render_command(scene, out, extra_args=extra_args, **params)
        LOG.info("render: %s", " ".join(cmd)[:3000])

        full = scene.duration_or_max
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, encoding="utf-8", errors="replace",
                                bufsize=1)
        stderr_tail: List[str] = []
        try:
            assert proc.stdout is not None
            for line in proc.stdout:
                if cancel_event is not None and cancel_event.is_set():
                    proc.terminate()
                    raise RuntimeError("Рендер отменён пользователем")
                line = line.strip()
                if line.startswith("out_time_ms=") and full > 0:
                    try:
                        done = int(line.split("=")[1]) / 1_000_000.0
                        pct = int(max(0.0, min(99.0, done / full * 100.0)))
                        if on_progress:
                            on_progress(pct, f"{done:0.1f} / {full:0.1f} с")
                    except ValueError:
                        pass
                elif line.startswith("progress=") and line.endswith("end"):
                    if on_progress:
                        on_progress(100, "готово")
            proc.wait(timeout=60)
            if proc.returncode != 0:
                err = (proc.stderr.read() if proc.stderr else "") or ""
                stderr_tail = err.strip().splitlines()[-12:]
                raise RuntimeError("FFmpeg вернул ошибку:\n" + "\n".join(stderr_tail))
        except Exception:
            if proc.poll() is None:
                proc.kill()
            raise
        if not out.exists() or out.stat().st_size == 0:
            raise RuntimeError("FFmpeg не создал файл результата")
        if on_progress:
            on_progress(100, "готово")
        return out

    # ------------------------------------------------------------ утилиты
    def concat_demuxer(self, files: Sequence[str | Path], out_path: str | Path) -> Path:
        """Склеить файлы одного формата через concat-demuxer (без перекодирования)."""
        ff = ffmpeg_path()
        if not ff:
            raise RuntimeError("FFmpeg не найден")
        list_file = Path(tempfile.mkstemp(suffix=".txt")[1])
        try:
            list_file.write_text(
                "\n".join(f"file '{Path(f).as_posix()}'" for f in files), encoding="utf-8"
            )
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat",
                 "-safe", "0", "-i", str(list_file), "-c", "copy", str(out_path)], timeout=3600)
        finally:
            list_file.unlink(missing_ok=True)
        return Path(out_path)

    def extract_audio(self, video: str | Path, out_path: str | Path,
                      bitrate: int = 192) -> Path:
        """Извлечь аудио из видео в M4A/MP3 (по расширению)."""
        ff = ffmpeg_path()
        if not ff:
            raise RuntimeError("FFmpeg не найден")
        out = Path(out_path)
        codec = "libmp3lame" if out.suffix.lower() == ".mp3" else "aac"
        run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(video),
             "-vn", "-c:a", codec, "-b:a", f"{bitrate}k", str(out)], timeout=3600)
        return out

    def make_gif(self, video: str | Path, out_path: str | Path,
                 start: float = 0.0, duration: float = 3.0, width: int = 480,
                 fps: int = 15) -> Path:
        """Сделать GIF из фрагмента видео (двухпроходная палитра)."""
        ff = ffmpeg_path()
        if not ff:
            raise RuntimeError("FFmpeg не найден")
        palette = Path(tempfile.mkstemp(suffix=".png")[1])
        try:
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{start:.3f}",
                 "-t", f"{duration:.3f}", "-i", str(video), "-vf",
                 f"fps={fps},scale={width}:-1:flags=lanczos,palettegen", str(palette)],
                timeout=600)
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{start:.3f}",
                 "-t", f"{duration:.3f}", "-i", str(video), "-i", str(palette),
                 "-lavfi", f"fps={fps},scale={width}:-1:flags=lanczos[x];[x][1:v]paletteuse",
                 str(out_path)], timeout=900)
        finally:
            palette.unlink(missing_ok=True)
        return Path(out_path)


class RenderWorker(QThread):
    """Поток рендера сцены с прогрессом и отменой.

    Сигналы:
        progress(int, str): Процент и статус.
        finished_ok(str): Путь готового файла.
        failed(str): Текст ошибки.
    """

    progress = pyqtSignal(int, str)
    finished_ok = pyqtSignal(str)
    failed = pyqtSignal(str)

    def __init__(self, engine: VideoEngine, scene: Scene, out_path: str | Path,
                 preset_data: Optional[Dict[str, Any]] = None,
                 extra_args: Optional[List[str]] = None,
                 parent: Optional[QObject] = None) -> None:
        """Args:
            engine: Видеодвижок.
            scene: Сцена.
            out_path: Куда рендерить.
            preset_data: Параметры кодека.
            extra_args: Доп. аргументы FFmpeg.
            parent: Родитель.
        """
        super().__init__(parent)
        self._engine = engine
        self._scene = scene
        self._out = out_path
        self._preset = preset_data
        self._extra = extra_args
        self._cancel = threading.Event()

    def cancel(self) -> None:
        """Запросить отмену рендера."""
        self._cancel.set()

    def run(self) -> None:
        """Точка входа потока."""
        try:
            path = self._engine.render(
                self._scene, self._out,
                on_progress=lambda p, s: self.progress.emit(p, s),
                cancel_event=self._cancel,
                preset_data=self._preset,
                extra_args=self._extra,
            )
            self.finished_ok.emit(str(path))
        except Exception as exc:
            LOG.exception("Рендер не удался")
            self.failed.emit(str(exc))


class PreviewPlayer(QObject):
    """Покадровый предпросмотр: отдаёт PNG-кадры по таймеру/запросу.

    Не использует QMediaPlayer (нет в PyQt6 без WebEngine), поэтому превью
    строится из кадров FFmpeg и прокси-файла — работает одинаково везде.

    Сигналы:
        frameReady(bytes): PNG-кадр.
        positionChanged(float): Текущая позиция в клипе, сек.
        finished(): Конец клипа.
    """

    frameReady = pyqtSignal(bytes)
    positionChanged = pyqtSignal(float)
    finished = pyqtSignal()

    def __init__(self, engine: VideoEngine, parent: Optional[QObject] = None) -> None:
        """Args:
            engine: Видеодвижок.
            parent: Родитель.
        """
        super().__init__(parent)
        self._engine = engine
        self._path: Optional[str] = None
        self._duration: float = 0.0
        self._position: float = 0.0
        self._playing = False
        self._last_emit = 0.0
        self._lock = threading.Lock()

    @property
    def position(self) -> float:
        """Текущая позиция, сек."""
        return self._position

    @property
    def duration(self) -> float:
        """Длительность клипа, сек."""
        return self._duration

    def set_media(self, path: str, duration: Optional[float] = None) -> None:
        """Загрузить файл для превью.

        Args:
            path: Видеофайл.
            duration: Длительность (если известно).
        """
        self._path = str(path)
        self._duration = duration if duration is not None else probe(path).get("duration", 0.0)
        self._position = 0.0
        self.refresh()

    def seek(self, seconds: float) -> None:
        """Перейти к моменту времени и обновить кадр."""
        self._position = max(0.0, min(seconds, self._duration or seconds))
        self.refresh()

    def play(self) -> None:
        """Запустить воспроизведение превью (кадры дёргает UI-таймер)."""
        self._playing = True

    def pause(self) -> None:
        """Пауза превью."""
        self._playing = False

    def stop(self) -> None:
        """Остановить и вернуться в начало."""
        self._playing = False
        self._position = 0.0
        self.refresh()

    def tick(self, dt: float) -> None:
        """Продвинуть позицию на ``dt`` секунд (вызывается из GUI-таймера)."""
        if not self._playing or not self._path:
            return
        self._position += dt
        if self._duration and self._position >= self._duration:
            self._position = self._duration
            self._playing = False
            self.finished.emit()
        now = time.monotonic()
        if now - self._last_emit >= 1.0 / max(5, get_config().video.preview_fps):
            self._last_emit = now
            self.refresh()
        self.positionChanged.emit(self._position)

    def refresh(self) -> None:
        """Запросить актуальный кадр (в отдельном потоке, чтобы не замораживать UI)."""
        if not self._path:
            return
        path, pos = self._path, self._position

        def work() -> None:
            data = self._engine.frame_at(path, pos, get_config().video.preview_width)
            if not data:
                return
            try:
                self.frameReady.emit(data)
            except RuntimeError:
                # объект уже удалён (окно закрыто) — молча выходим
                LOG.debug("PreviewPlayer удалён до прихода кадра")

        threading.Thread(target=work, name="bfs-preview-frame", daemon=True).start()
