"""Видеопереходы между клипами на основе FFmpeg ``xfade``.

Каждый переход — это:

* ``xfade``-имя (или составной фильтр для «стилевых» переходов);
* метаданные для UI (подпись, иконка, направление);
* генератор превью-кадров для анимированной иконки (без FFmpeg, на numpy —
  чтобы список переходов рисовался мгновенно).

Для переходов, которых нет в ``xfade`` (glitch, blur), собирается цепочка
фильтров (``gblur`` / ``noise`` + ``xfade`` базового типа).
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np

LOG = logging.getLogger(__name__)

#: Все переходы: ключ → описание.
TRANSITIONS: Dict[str, Dict[str, object]] = {
    "fade": {"label": "Fade", "icon": "◐", "xfade": "fade"},
    "fadeblack": {"label": "Fade to Black", "icon": "◑", "xfade": "fadeblack"},
    "fadewhite": {"label": "Fade to White", "icon": "◒", "xfade": "fadewhite"},
    "dissolve": {"label": "Dissolve", "icon": "▤", "xfade": "dissolve"},
    "wipeleft": {"label": "Wipe ←", "icon": "◀", "xfade": "wipeleft"},
    "wiperight": {"label": "Wipe →", "icon": "▶", "xfade": "wiperight"},
    "wipeup": {"label": "Wipe ↑", "icon": "▲", "xfade": "wipeup"},
    "wipedown": {"label": "Wipe ↓", "icon": "▼", "xfade": "wipedown"},
    "slideleft": {"label": "Slide ←", "icon": "⇤", "xfade": "slideleft"},
    "slideright": {"label": "Slide →", "icon": "⇥", "xfade": "slideright"},
    "slideup": {"label": "Slide ↑", "icon": "⇧", "xfade": "slideup"},
    "slidedown": {"label": "Slide ↓", "icon": "⇩", "xfade": "slidedown"},
    "circleopen": {"label": "Circle Open", "icon": "◯", "xfade": "circleopen"},
    "circleclose": {"label": "Circle Close", "icon": "◍", "xfade": "circleclose"},
    "circlecrop": {"label": "Circle Crop", "icon": "◉", "xfade": "circlecrop"},
    "radial": {"label": "Radial", "icon": "◎", "xfade": "radial"},
    "rectcrop": {"label": "Rect Crop", "icon": "▣", "xfade": "rectcrop"},
    "distance": {"label": "Distance", "icon": "⤢", "xfade": "distance"},
    "pixelize": {"label": "Pixelize", "icon": "▦", "xfade": "pixelize"},
    "squeezeh": {"label": "Squeeze H", "icon": "⬌", "xfade": "squeezeh"},
    "squeezev": {"label": "Squeeze V", "icon": "⬍", "xfade": "squeezev"},
    "zoomin": {"label": "Zoom In", "icon": "🔍", "xfade": "zoomin"},
    "hblur": {"label": "Horizontal Blur", "icon": "▤", "xfade": "hblur"},
    "smoothleft": {"label": "Smooth ←", "icon": "◁", "xfade": "smoothleft"},
    "smoothright": {"label": "Smooth →", "icon": "▷", "xfade": "smoothright"},
    # стилевые (собираются из базовых + дополнительных фильтров)
    "glitch": {"label": "Glitch", "icon": "⚡", "xfade": "pixelize",
               "pre_filter": "noise=alls=28:allf=t,chromashift=cbh=6:crh=-6"},
    "blur": {"label": "Blur", "icon": "🌫", "xfade": "fade",
             "pre_filter": "gblur=sigma=12"},
    "spin": {"label": "Spin", "icon": "🌀", "xfade": "circleopen",
             "pre_filter": "rotate=PI*0.25:c=black@0"},
    "vhs": {"label": "VHS", "icon": "📼", "xfade": "dissolve",
            "pre_filter": "eq=saturation=1.5:contrast=1.1,noise=alls=16:allf=t"},
}


@dataclass
class TransitionSpec:
    """Настройки перехода между двумя клипами.

    Attributes:
        kind: Ключ из :data:`TRANSITIONS`.
        duration: Длительность, сек (0.1–2.0).
        easing: Зарезервировано под будущий рендер с кривыми.
    """

    kind: str = "fade"
    duration: float = 0.5
    easing: str = "linear"

    @property
    def label(self) -> str:
        """Подпись для UI."""
        return str(TRANSITIONS.get(self.kind, {}).get("label", self.kind))

    @property
    def icon(self) -> str:
        """Иконка для UI."""
        return str(TRANSITIONS.get(self.kind, {}).get("icon", "◐"))

    def validate(self) -> "TransitionSpec":
        """Привести значения к допустимым (длительность 0.1–2.0)."""
        self.duration = float(max(0.1, min(2.0, self.duration)))
        if self.kind not in TRANSITIONS:
            LOG.warning("Неизвестный переход '%s', использую fade", self.kind)
            self.kind = "fade"
        return self


def xfade_filter(kind: str, offset: float, duration: float,
                 left: str = "v0", right: str = "v1", out: str = "vout") -> str:
    """Собрать строку ``xfade``-фильтра.

    Args:
        kind: Ключ перехода.
        offset: Момент начала перехода на таймлайне, сек.
        duration: Длительность, сек.
        left: Метка левого входа.
        right: Метка правого входа.
        out: Метка выхода.

    Returns:
        Строка фильтра вида ``[a][b]xfade=transition=fade:duration=0.5:offset=3[vout]``.
    """
    spec = TRANSITIONS.get(kind, TRANSITIONS["fade"])
    xfade_name = str(spec.get("xfade", "fade"))
    pre = spec.get("pre_filter")
    parts: List[str] = []
    if pre:
        # применяем стилевой фильтр к правому входу перед склейкой
        styled = f"{out}_styled"
        parts.append(f"[{right}]{pre}[{styled}]")
        right = styled
    parts.append(
        f"[{left}][{right}]xfade=transition={xfade_name}:"
        f"duration={max(0.05, duration):.3f}:offset={max(0.0, offset):.3f}[{out}]"
    )
    return ";".join(parts)


def build_transition_chain(clips: List[Dict[str, object]], *,
                           left_prefix: str = "c") -> Tuple[str, str]:
    """Построить полную цепочку переходов между последовательными клипами.

    Args:
        clips: Список словарей ``{"label", "start", "duration", "transition",
            "transition_duration"}`` — в порядке таймлайна.
        left_prefix: Префикс меток (для читаемости).

    Returns:
        Пара ``(filter_string, last_label)``; если переходов нет — пустая строка
        и метка последнего клипа.
    """
    if not clips:
        return "", ""
    parts: List[str] = []
    current = str(clips[0].get("label", f"{left_prefix}0"))
    timeline_end = float(clips[0].get("start", 0.0)) + float(clips[0].get("duration", 0.0))

    for index in range(1, len(clips)):
        item = clips[index]
        label = str(item.get("label", f"{left_prefix}{index}"))
        kind = str(item.get("transition") or "")
        if kind:
            spec = TransitionSpec(kind, float(item.get("transition_duration", 0.5))).validate()
            out = f"{label}_xf"
            parts.append(xfade_filter(spec.kind, timeline_end - spec.duration,
                                     spec.duration, current, label, out))
            current = out
            timeline_end = timeline_end + float(item.get("duration", 0.0)) - spec.duration
        else:
            out = f"{label}_cat"
            delay = float(item.get("start", 0.0))
            parts.append(f"[{current}][{label}]overlay=eof_action=pass:"
                         f"enable='gte(t,{delay:.3f})'[{out}]")
            current = out
            timeline_end = max(timeline_end, float(item.get("start", 0.0)) +
                               float(item.get("duration", 0.0)))
    return ";".join(parts), current


def list_transitions() -> List[Tuple[str, str, str]]:
    """Список переходов для UI: ``(ключ, подпись, иконка)``."""
    return [(key, str(spec["label"]), str(spec["icon"])) for key, spec in TRANSITIONS.items()]


def preview_frames(kind: str, size: int = 64, steps: int = 6) -> List[np.ndarray]:
    """Сгенерировать превью перехода без FFmpeg (для анимированной иконки).

    Рисует абстрактные «два кадра» (оранжевый и голубой прямоугольники) и
    применяет упрощённое приближение перехода.

    Args:
        kind: Ключ перехода.
        size: Сторона кадра в пикселях.
        steps: Сколько кадров сгенерировать.

    Returns:
        Список RGB-массивов ``(size, size, 3)`` uint8.
    """
    left_color = np.array([255, 102, 0], dtype=np.float32)
    right_color = np.array([0, 191, 255], dtype=np.float32)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / max(1, size - 1)
    frames: List[np.ndarray] = []
    spec = TRANSITIONS.get(kind, TRANSITIONS["fade"])
    xfade_name = str(spec.get("xfade", "fade"))

    for i in range(steps):
        alpha = i / max(1, steps - 1)
        if xfade_name in ("fade", "dissolve", "smoothleft", "smoothright"):
            mask = np.full((size, size), alpha, dtype=np.float32)
        elif xfade_name.startswith("wipe"):
            direction = xfade_name.replace("wipe", "")
            if direction == "left":
                mask = np.clip((alpha * 1.4 - (1.0 - xx)), 0, 1)
            elif direction == "right":
                mask = np.clip((alpha * 1.4 - xx), 0, 1)
            elif direction == "up":
                mask = np.clip((alpha * 1.4 - (1.0 - yy)), 0, 1)
            else:
                mask = np.clip((alpha * 1.4 - yy), 0, 1)
        elif xfade_name.startswith("slide"):
            direction = xfade_name.replace("slide", "")
            shift = alpha
            if direction in ("left", "right"):
                mask = np.abs(xx - shift) * 0.0 + (xx < shift).astype(np.float32)
            else:
                mask = (yy < shift).astype(np.float32)
            if direction in ("left", "up"):
                mask = 1.0 - mask
        elif xfade_name.startswith("circle"):
            dist = np.sqrt((xx - 0.5) ** 2 + (yy - 0.5) ** 2)
            mask = (dist < alpha * 0.75).astype(np.float32)
        elif xfade_name.startswith("pixelize"):
            step = max(1, int(size / 10))
            mask = (((xx * size // step + yy * size // step) % 2).astype(np.float32) * alpha
                    + alpha) / 2.0
        else:
            mask = np.clip((alpha * 1.5 - np.abs(xx - 0.5) * 1.2), 0, 1)
        frame = left_color[None, None, :] * (1 - mask[..., None]) + \
            right_color[None, None, :] * mask[..., None]
        frames.append(np.clip(frame, 0, 255).astype(np.uint8))
    return frames
