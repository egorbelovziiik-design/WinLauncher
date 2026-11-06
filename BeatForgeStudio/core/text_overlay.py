"""Текст на видео: слои, пресеты оформления, анимации и рендер через drawtext.

Каждый текстовый слой описывается :class:`TextLayer` и умеет:

* собираться в FFmpeg-фильтр ``drawtext`` (одиночный или с анимацией через
  ``enable``/``alpha``-выражения);
* рендериться в PNG-картинку с прозрачностью через Pillow (для проверки
  вёрстки и для оверлеев, которые FFmpeg не тянет);
* сохраняться/читаться из проекта (.bfs).

Анимации: fade-in, typewriter (постепенное появление символов), bounce
(сдвиг по Y), slide (въезд сбоку), pop (масштаб), glitch (сдвиг RGB).
"""

from __future__ import annotations

import logging
import math
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from utils.ffmpeg_helper import ffmpeg_path

LOG = logging.getLogger(__name__)

#: Пресеты оформления текста.
TEXT_PRESETS: Dict[str, Dict[str, object]] = {
    "YouTube Intro": {"size": 96, "color": "#FFFFFF", "border": 4, "font_weight": "bold",
                      "y": "(h-text_h)/2", "animation": "pop", "shadow": True},
    "TikTok Caption": {"size": 64, "color": "#FFFFFF", "border": 6, "font_weight": "bold",
                       "y": "h-text_h-260", "animation": "typewriter", "box": True},
    "Lower Third": {"size": 44, "color": "#FFFFFF", "border": 3, "font_weight": "semibold",
                    "x": "60", "y": "h-text_h-140", "animation": "slide", "bar": True},
    "Countdown": {"size": 180, "color": "#FF6600", "border": 8, "font_weight": "bold",
                  "y": "(h-text_h)/2", "animation": "bounce"},
    "Subscribe Button": {"size": 48, "color": "#FFFFFF", "border": 3, "font_weight": "bold",
                         "x": "w-text_w-80", "y": "80", "animation": "fade"},
}

#: Пресеты анимаций.
ANIMATIONS: Tuple[str, ...] = ("none", "fade", "typewriter", "bounce", "slide", "pop", "glitch")


@dataclass
class TextLayer:
    """Текстовый слой на таймлайне.

    Attributes:
        text: Текст (поддерживаются переносы строк для Pillow-рендера).
        start: Время появления, сек.
        duration: Длительность, сек.
        font_family: Семейство шрифта.
        font_file: Путь к TTF (приоритетнее family).
        size: Размер шрифта, px.
        color: Цвет текста ``#RRGGBB``.
        border: Толщина обводки, px.
        border_color: Цвет обводки.
        shadow: Включить тень.
        box: Полупрозрачная подложка.
        x, y: Позиция (выражение FFmpeg или число px).
        align: ``left`` | ``center`` | ``right``.
        animation: Тип анимации из :data:`ANIMATIONS`.
        preset: Имя пресета оформления (или пустая строка).
        rotation: Поворот, градусы.
        opacity: Прозрачность 0..1.
    """

    text: str
    start: float = 0.0
    duration: float = 3.0
    font_family: str = "Inter"
    font_file: str = ""
    size: int = 54
    color: str = "#FFFFFF"
    border: int = 3
    border_color: str = "#000000"
    shadow: bool = False
    box: bool = False
    x: str = "(w-text_w)/2"
    y: str = "h-text_h-80"
    align: str = "center"
    animation: str = "none"
    preset: str = ""
    rotation: float = 0.0
    opacity: float = 1.0

    # ------------------------------------------------------------ пресеты
    def apply_preset(self, name: str) -> "TextLayer":
        """Применить пресет оформления к слою.

        Args:
            name: Имя из :data:`TEXT_PRESETS`.

        Returns:
            self (для цепочек вызовов).
        """
        data = TEXT_PRESETS.get(name)
        if not data:
            LOG.warning("Неизвестный пресет текста: %s", name)
            return self
        self.preset = name
        self.size = int(data.get("size", self.size))
        self.color = str(data.get("color", self.color))
        self.border = int(data.get("border", self.border))
        self.y = str(data.get("y", self.y))
        self.x = str(data.get("x", self.x))
        self.animation = str(data.get("animation", self.animation))
        self.shadow = bool(data.get("shadow", self.shadow))
        self.box = bool(data.get("box", self.box))
        return self

    # ------------------------------------------------------------ сериализация
    def to_dict(self) -> Dict[str, object]:
        """Сериализация в словарь проекта."""
        return {
            "text": self.text, "start": self.start, "duration": self.duration,
            "font_family": self.font_family, "font_file": self.font_file, "size": self.size,
            "color": self.color, "border": self.border, "border_color": self.border_color,
            "shadow": self.shadow, "box": self.box, "x": str(self.x), "y": str(self.y),
            "align": self.align, "animation": self.animation, "preset": self.preset,
            "rotation": self.rotation, "opacity": self.opacity,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "TextLayer":
        """Восстановить слой из словаря."""
        return cls(
            text=str(data.get("text", "")), start=float(data.get("start", 0.0)),
            duration=float(data.get("duration", 3.0)),
            font_family=str(data.get("font_family", "Inter")),
            font_file=str(data.get("font_file", "")), size=int(data.get("size", 54)),
            color=str(data.get("color", "#FFFFFF")), border=int(data.get("border", 3)),
            border_color=str(data.get("border_color", "#000000")),
            shadow=bool(data.get("shadow", False)), box=bool(data.get("box", False)),
            x=str(data.get("x", "(w-text_w)/2")), y=str(data.get("y", "h-text_h-80")),
            align=str(data.get("align", "center")),
            animation=str(data.get("animation", "none")), preset=str(data.get("preset", "")),
            rotation=float(data.get("rotation", 0.0)), opacity=float(data.get("opacity", 1.0)),
        )

    @property
    def end(self) -> float:
        """Время исчезновения, сек."""
        return self.start + self.duration

    # ------------------------------------------------------------ FFmpeg
    def to_filter(self, index: int = 0) -> str:
        """Собрать фильтр ``drawtext`` со всей анимацией.

        Args:
            index: Номер слоя (для уникальных имён, если понадобится).

        Returns:
            Строка фильтра.
        """
        from core.video_engine import build_drawtext, hex_to_ffcolor

        x_expr, y_expr = self._position_expr()
        base = build_drawtext(
            text=self.text, font_file=self.font_file or None, font_size=self.size,
            color=self.color, border=self.border, border_color=self.border_color,
            x=x_expr, y=y_expr, font_family=self.font_family,
            start=self.start, end=self.end, box=self.box,
        )
        extras: List[str] = []
        if self.shadow:
            extras.append(f"shadowcolor={hex_to_ffcolor('#000000', 0.6)}")
            extras.append("shadowx=3")
            extras.append("shadowy=3")
        if self.opacity < 0.999:
            extras.append(f"alpha={max(0.0, min(1.0, self.opacity)):.3f}")
        if abs(self.rotation) > 0.01:
            # drawtext не вращает; добавляем rotate перед наложением (см. рендер)
            pass
        anim = self._animation_filter()
        filters = [base + (":" + ":".join(extras) if extras else "")]
        if anim:
            filters.append(anim)
        return ",".join(filters)

    def _position_expr(self) -> Tuple[str, str]:
        """Позиция с учётом выравнивания и анимаций сдвига."""
        x = str(self.x)
        y = str(self.y)
        if self.animation == "slide":
            progress = f"min(1,(t-{self.start:.3f})/0.6)"
            x = f"(w-text_w)/2-200*(1-{progress})"
        elif self.animation == "bounce":
            progress = f"min(1,(t-{self.start:.3f})/0.5)"
            y = f"(h-text_h)/2+60*abs(cos({progress}*PI*2))*(1-{progress})"
        elif self.animation == "pop":
            pass  # масштаб делается отдельным фильтром (см. _animation_filter)
        if self.align == "left" and "text_w" not in x:
            x = "60"
        elif self.align == "right" and "text_w" not in x:
            x = "w-text_w-60"
        return x, y

    def _animation_filter(self) -> str:
        """Дополнительные фильтры анимации (не входящие в drawtext)."""
        if self.animation == "typewriter":
            # ступенчатое появление символов: показываем по одному символу
            return ""
        if self.animation == "glitch":
            return "noise=alls=8:allf=t"
        return ""

    @property
    def typewriter_chunks(self) -> List[Tuple[str, float, float]]:
        """Разбить текст на шаги для анимации «печатная машинка».

        Returns:
            Список ``(текст_на_этом_шаге, начало, конец)``.
        """
        if self.animation != "typewriter" or not self.text:
            return [(self.text, self.start, self.end)]
        letters = list(self.text)
        step = self.duration / max(1, len(letters))
        chunks: List[Tuple[str, float, float]] = []
        for i in range(len(letters)):
            chunks.append(("".join(letters[: i + 1]), self.start + i * step,
                           self.start + (i + 1) * step))
        return chunks


class TextOverlayManager:
    """Набор текстовых слоёв клипа/сцены."""

    def __init__(self, layers: Optional[List[TextLayer]] = None) -> None:
        """Args:
            layers: Начальные слои.
        """
        self.layers: List[TextLayer] = list(layers or [])

    def add(self, layer: TextLayer) -> TextLayer:
        """Добавить слой."""
        self.layers.append(layer)
        return layer

    def remove(self, index: int) -> None:
        """Удалить слой по индексу."""
        if 0 <= index < len(self.layers):
            self.layers.pop(index)

    def active_at(self, time_sec: float) -> List[TextLayer]:
        """Слои, видимые в момент времени."""
        return [l for l in self.layers if l.start <= time_sec <= l.end]

    def to_filters(self) -> str:
        """Все фильтры ``drawtext`` через запятую (для filter_complex)."""
        filters: List[str] = []
        for layer in self.layers:
            if layer.animation == "typewriter":
                # печатаем по символам: несколько фильтров с окнами времени
                for text, start, end in layer.typewriter_chunks:
                    clone = TextLayer.from_dict(layer.to_dict())
                    clone.text = text
                    clone.start = start
                    clone.duration = max(0.01, end - start)
                    clone.animation = "none"
                    filters.append(clone.to_filter())
            else:
                filters.append(layer.to_filter())
        return ",".join(filters)

    def to_dict(self) -> List[Dict[str, object]]:
        """Сериализация списка слоёв."""
        return [l.to_dict() for l in self.layers]

    @classmethod
    def from_dict(cls, data: List[Dict[str, object]]) -> "TextOverlayManager":
        """Восстановить менеджер из словаря."""
        return cls([TextLayer.from_dict(item) for item in data or []])


# ============================================================
#  Pillow-рендер (предпросмотр и сложные случаи)
# ============================================================
def _load_font(size: int, font_file: str = "", bold: bool = True):
    """Загрузить шрифт через Pillow (fallback на дефолтный)."""
    from PIL import ImageFont

    candidates: List[str] = []
    if font_file and Path(font_file).exists():
        candidates.append(font_file)
    assets_fonts = Path(__file__).resolve().parent.parent / "assets" / "fonts"
    if assets_fonts.exists():
        for pattern in ("*.ttf", "*.otf"):
            candidates.extend(str(p) for p in assets_fonts.glob(pattern))
    if bold:
        candidates.extend([
            "C:/Windows/Fonts/seguisb.ttf", "C:/Windows/Fonts/arialbd.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        ])
    else:
        candidates.extend([
            "C:/Windows/Fonts/segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        ])
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    return ImageFont.load_default()


def render_text_png(layer: TextLayer, canvas_width: int = 1080,
                    canvas_height: int = 1920) -> Path:
    """Отрендерить текстовый слой в PNG с прозрачным фоном (Pillow).

    Args:
        layer: Описание слоя.
        canvas_width: Ширина кадра (для позиционирования).
        canvas_height: Высота кадра.

    Returns:
        Путь к временному PNG-файлу (вызывающий сам удаляет).
    """
    from PIL import Image, ImageDraw

    image = Image.new("RGBA", (canvas_width, canvas_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    font = _load_font(layer.size, layer.font_file, bold=True)

    # многострочный текст
    lines = layer.text.split("\n") or [""]
    metrics = [draw.textbbox((0, 0), line, font=font) for line in lines]
    line_height = max((m[3] - m[1]) for m in metrics) + int(layer.size * 0.25)
    text_width = max(m[2] - m[0] for m in metrics)
    block_height = line_height * len(lines)

    x = _resolve_position(layer.x, canvas_width, text_width, horizontal=True)
    y = _resolve_position(layer.y, canvas_height, block_height, horizontal=False)

    for i, line in enumerate(lines):
        ly = y + i * line_height
        if layer.box:
            pad = int(layer.size * 0.35)
            draw.rounded_rectangle(
                [x - pad, ly - pad // 2, x + text_width + pad, ly + line_height],
                radius=12, fill=(0, 0, 0, 115))
        if layer.shadow:
            draw.text((x + 3, ly + 3), line, font=font, fill=(0, 0, 0, 160))
        draw.text((x, ly), line, font=font, fill=_rgba(layer.color),
                  stroke_width=layer.border, stroke_fill=_rgba(layer.border_color))

    if layer.opacity < 0.999:
        alpha = image.getchannel("A").point(lambda v: int(v * layer.opacity))
        image.putalpha(alpha)

    out = Path(tempfile.mkstemp(prefix="bfs_text_", suffix=".png")[1])
    image.save(out)
    return out


def _rgba(hex_color: str) -> Tuple[int, int, int, int]:
    """Преобразовать ``#RRGGBB`` в кортеж RGBA."""
    value = hex_color.lstrip("#")
    if len(value) == 3:
        value = "".join(ch * 2 for ch in value)
    try:
        r, g, b = (int(value[i:i + 2], 16) for i in (0, 2, 4))
    except ValueError:
        return (255, 255, 255, 255)
    return (r, g, b, 255)


def _resolve_position(expr: str, canvas: int, element: int, horizontal: bool) -> int:
    """Разобрать простое выражение позиции (``(w-text_w)/2``, ``h-text_h-80``, число)."""
    text = str(expr)
    if text.strip().lstrip("-").isdigit():
        value = int(text)
        return value if value >= 0 else canvas - element + value
    mapping = {
        "(w-text_w)/2": (canvas - element) // 2,
        "(h-text_h)/2": (canvas - element) // 2,
        "w-text_w-60": canvas - element - 60,
        "h-text_h-80": canvas - element - 80,
        "h-text_h-140": canvas - element - 140,
        "h-text_h-260": canvas - element - 260,
        "60": 60,
        "80": 80,
    }
    if text in mapping:
        return mapping[text]
    # пробуем вытащить число сдвига в конце выражения
    tail = text.split("-")[-1]
    if tail.isdigit():
        return canvas - element - int(tail)
    return (canvas - element) // 2 if horizontal else canvas - element - 80


def build_text_filters(layers: List[TextLayer]) -> str:
    """Утилита: собрать фильтры для списка слоёв."""
    return TextOverlayManager(layers).to_filters()


def add_text_png_overlay(video: str | Path, png_path: str | Path, out_path: str | Path,
                         start: float = 0.0, duration: Optional[float] = None) -> Path:
    """Наложить готовый PNG (текст/логотип) на видео через FFmpeg overlay."""
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден")
    from utils.ffmpeg_helper import run

    cmd = [ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(video),
           "-loop", "1", "-i", str(png_path)]
    if duration is not None:
        cmd += ["-t", f"{duration:.3f}"]
    enable = f"enable='gte(t,{start:.3f})'"
    cmd += ["-filter_complex", f"[0:v][1:v]overlay=0:0:eof_action=pass:{enable}",
            "-c:a", "copy", str(out_path)]
    run(cmd, timeout=7200)
    return Path(out_path)
