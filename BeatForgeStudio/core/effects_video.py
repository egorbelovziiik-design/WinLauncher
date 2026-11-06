"""Видеоэффекты и фильтры: цветокоррекция, пресеты, размытие, хромакей, кадрирование.

Модуль даёт:

* :data:`COLOR_SPECS` — параметры цветокоррекции для UI (яркость, контраст,
  насыщенность, температура, оттенок);
* :data:`FILTER_PRESETS` — готовые стили (Cinematic, Vintage, B&W, VHS, Neon…);
* :func:`build_filters` — преобразование «настроек клипа» в строку FFmpeg;
* :func:`chroma_key` — фильтр зелёного экрана с заменой фона;
* :func:`crop_filter` — кадрирование/масштаб под формат;
* :func:`stabilize` — двухпроходная стабилизация vidstab;
* :func:`preview_frame` — быстрый кадр с эффектами (через FFmpeg) для превью
  без полного рендера;
* :class:`VideoEffectChain` — сериализуемая цепочка эффектов клипа.
"""

from __future__ import annotations

import logging
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from utils.ffmpeg_helper import ffmpeg_path, run

LOG = logging.getLogger(__name__)

#: Описание параметров цветокоррекции: (подпись, мин, макс, дефолт).
COLOR_SPECS: Dict[str, Tuple[str, float, float, float]] = {
    "brightness": ("Яркость", -1.0, 1.0, 0.0),
    "contrast": ("Контраст", 0.0, 3.0, 1.0),
    "saturation": ("Насыщенность", 0.0, 3.0, 1.0),
    "gamma": ("Гамма", 0.2, 3.0, 1.0),
    "temperature": ("Температура", -1.0, 1.0, 0.0),
    "hue": ("Оттенок", -180.0, 180.0, 0.0),
    "sharpen": ("Резкость", 0.0, 2.0, 0.0),
    "vignette": ("Виньетка", 0.0, 1.0, 0.0),
}

#: Пресеты фильтров: имя → строка фильтров FFmpeg.
FILTER_PRESETS: Dict[str, str] = {
    "none": "",
    "Cinematic": "eq=contrast=1.12:saturation=0.9:gamma=0.98,"
                 "colorbalance=rs=-0.04:bs=0.06,vignette=PI/5",
    "Vintage": "curves=preset=vintage,eq=saturation=0.82:contrast=1.05,noise=alls=6:allf=t",
    "B&W": "hue=s=0,eq=contrast=1.18",
    "VHS": "eq=saturation=1.45:contrast=1.12,noise=alls=14:allf=t+u,"
           "chromashift=cbh=3:crh=-3",
    "Neon": "eq=saturation=1.65:contrast=1.15,colorbalance=bs=0.18:rs=0.10",
    "Cold": "colorbalance=bs=0.20:gs=0.05:rs=-0.10,eq=saturation=0.92",
    "Warm": "colorbalance=rs=0.18:gs=0.04:bs=-0.12,eq=saturation=1.06",
    "Film Grain": "noise=alls=11:allf=t,eq=contrast=1.05",
    "Vignette": "vignette=PI/4",
    "Dreamy": "gblur=sigma=2.4,eq=brightness=0.04:saturation=1.1",
    "Sharp": "unsharp=5:5:1.1:5:5:0.0",
}

#: Доступные типы размытия.
BLUR_TYPES: Dict[str, str] = {
    "gaussian": "gblur=sigma={value}",
    "box": "boxblur={value}:2",
    "motion": "motionblur=alpha={value}:frames=8",
    "radial": "gblur=sigma={value},vignette=PI/6",
}

#: Пресеты кадрирования под соцсети: имя → (ширина, высота, описание).
CROP_PRESETS: Dict[str, Tuple[int, int, str]] = {
    "16:9": (1920, 1080, "YouTube / Twitter"),
    "9:16": (1080, 1920, "TikTok / Reels / Shorts"),
    "1:1": (1080, 1080, "Instagram пост"),
    "4:5": (1080, 1350, "Instagram лента"),
    "free": (0, 0, "Свободное"),
}


@dataclass
class VideoEffectChain:
    """Набор эффектов клипа (сериализуется в проект).

    Attributes:
        preset: Название пресета фильтра.
        color: Значения цветокоррекции (по :data:`COLOR_SPECS`).
        blur_type: Тип размытия (``gaussian``/…), пустая строка — без размытия.
        blur_value: Сила размытия.
        chroma_enabled: Включён хромакей.
        chroma_color: Цвет ключа (``#00FF00``).
        chroma_similarity: Допуск по цвету 0..1.
        chroma_blend: Смешивание краёв 0..1.
        chroma_background: Путь к картинке-фону (или цвет ``#RRGGBB``).
        mirror_h: Зеркало по горизонтали.
        mirror_v: Зеркало по вертикали.
        rotate: Поворот, градусы.
        crop_mode: ``crop`` | ``fit`` | ``blur``.
        stabilize: Применить стабилизацию при рендере.
        extra_filters: Дополнительные строки фильтров (ручной ввод).
    """

    preset: str = "none"
    color: Dict[str, float] = field(default_factory=lambda: {k: v[3] for k, v in COLOR_SPECS.items()})
    blur_type: str = ""
    blur_value: float = 4.0
    chroma_enabled: bool = False
    chroma_color: str = "#00FF00"
    chroma_similarity: float = 0.25
    chroma_blend: float = 0.1
    chroma_background: str = ""
    mirror_h: bool = False
    mirror_v: bool = False
    rotate: float = 0.0
    crop_mode: str = "fit"
    stabilize: bool = False
    extra_filters: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, object]:
        """Сериализация в словарь."""
        return {
            "preset": self.preset, "color": dict(self.color), "blur_type": self.blur_type,
            "blur_value": self.blur_value, "chroma_enabled": self.chroma_enabled,
            "chroma_color": self.chroma_color, "chroma_similarity": self.chroma_similarity,
            "chroma_blend": self.chroma_blend, "chroma_background": self.chroma_background,
            "mirror_h": self.mirror_h, "mirror_v": self.mirror_v, "rotate": self.rotate,
            "crop_mode": self.crop_mode, "stabilize": self.stabilize,
            "extra_filters": list(self.extra_filters),
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "VideoEffectChain":
        """Восстановить цепочку из проекта."""
        color = {k: float(v) for k, v in (data.get("color") or {}).items()}
        return cls(
            preset=str(data.get("preset", "none")),
            color=color, blur_type=str(data.get("blur_type", "")),
            blur_value=float(data.get("blur_value", 4.0)),
            chroma_enabled=bool(data.get("chroma_enabled", False)),
            chroma_color=str(data.get("chroma_color", "#00FF00")),
            chroma_similarity=float(data.get("chroma_similarity", 0.25)),
            chroma_blend=float(data.get("chroma_blend", 0.1)),
            chroma_background=str(data.get("chroma_background", "")),
            mirror_h=bool(data.get("mirror_h", False)),
            mirror_v=bool(data.get("mirror_v", False)),
            rotate=float(data.get("rotate", 0.0)),
            crop_mode=str(data.get("crop_mode", "fit")),
            stabilize=bool(data.get("stabilize", False)),
            extra_filters=list(data.get("extra_filters") or []),
        )

    def is_empty(self) -> bool:
        """Нет ли активных эффектов (быстрая проверка для рендера)."""
        defaults = {k: v[3] for k, v in COLOR_SPECS.items()}
        return (self.preset in ("", "none") and not self.blur_type
                and not self.chroma_enabled and not self.mirror_h and not self.mirror_v
                and abs(self.rotate) < 0.01 and all(
                    abs(self.color.get(k, v) - v) < 1e-6 for k, v in defaults.items())
                and not self.extra_filters)


def build_filters(chain: VideoEffectChain, width: int = 1920, height: int = 1080,
                  bg_color: str = "#000000") -> str:
    """Собрать строку видеофильтров для FFmpeg.

    Args:
        chain: Настройки эффектов клипа.
        width: Целевая ширина.
        height: Целевая высота.
        bg_color: Цвет фона для режима ``fit``.

    Returns:
        Строка фильтров через запятую (может быть пустой).
    """
    from core.video_engine import aspect_filter, hex_to_ffcolor

    parts: List[str] = []

    # 1) кадрирование/масштаб под формат
    if chain.crop_mode in ("crop", "fit", "blur", "stretch"):
        parts.append(aspect_filter(chain.crop_mode, width, height, bg_color))

    # 2) поворот и зеркало
    if abs(chain.rotate) > 0.01:
        parts.append(f"rotate={chain.rotate}*PI/180:c=black@0:ow=iw:oh=ih")
    if chain.mirror_h:
        parts.append("hflip")
    if chain.mirror_v:
        parts.append("vflip")

    # 3) пресет стиля
    preset = FILTER_PRESETS.get(chain.preset, "")
    if preset:
        parts.append(preset)

    # 4) ручная цветокоррекция
    color = chain.color or {}
    eq_parts: List[str] = []
    brightness = float(color.get("brightness", 0.0))
    contrast = float(color.get("contrast", 1.0))
    saturation = float(color.get("saturation", 1.0))
    gamma = float(color.get("gamma", 1.0))
    if abs(brightness) > 1e-3:
        eq_parts.append(f"brightness={brightness:.3f}")
    if abs(contrast - 1.0) > 1e-3:
        eq_parts.append(f"contrast={contrast:.3f}")
    if abs(saturation - 1.0) > 1e-3:
        eq_parts.append(f"saturation={saturation:.3f}")
    if abs(gamma - 1.0) > 1e-3:
        eq_parts.append(f"gamma={gamma:.3f}")
    if eq_parts:
        parts.append("eq=" + ":".join(eq_parts))

    temperature = float(color.get("temperature", 0.0))
    if abs(temperature) > 1e-3:
        warm = max(0.0, temperature)
        cool = max(0.0, -temperature)
        parts.append(f"colorbalance=rs={warm * 0.2:.3f}:bs={cool * 0.2:.3f}"
                     f":gs={min(warm, cool) * 0.05:.3f}")

    hue = float(color.get("hue", 0.0))
    if abs(hue) > 1e-3:
        parts.append(f"hue=h={hue:.1f}")

    sharpen = float(color.get("sharpen", 0.0))
    if sharpen > 1e-3:
        parts.append(f"unsharp=5:5:{sharpen:.2f}:5:5:0")

    vignette = float(color.get("vignette", 0.0))
    if vignette > 1e-3:
        parts.append(f"vignette=angle={vignette * 1.8:.3f}")

    # 5) размытие
    if chain.blur_type in BLUR_TYPES and chain.blur_value > 0:
        formula = BLUR_TYPES[chain.blur_type]
        parts.append(formula.format(value=f"{chain.blur_value:.2f}"))

    # 6) ручные фильтры
    parts.extend(f for f in chain.extra_filters if f)

    return ",".join(p for p in parts if p)


def chroma_key(key_color: str = "#00FF00", similarity: float = 0.25,
               blend: float = 0.1, background: str = "",
               width: int = 1920, height: int = 1080) -> str:
    """Собрать фильтр хромакея с заменой фона.

    Args:
        key_color: Цвет ключа (зелёный/синий экран).
        similarity: Допуск 0..1.
        blend: Смешивание краёв 0..1.
        background: Путь к картинке-фону или цвет ``#RRGGBB`` (пусто — прозрачность).
        width: Ширина кадра.
        height: Высота кадра.

    Returns:
        Строка фильтров (рассчитана на вход ``[in]`` и фоновый вход ``[bg]``
        при наличии картинки; иначе работает как обычный фильтр).
    """
    from core.video_engine import hex_to_ffcolor

    color = hex_to_ffcolor(key_color)
    similarity = max(0.01, min(1.0, similarity))
    blend = max(0.0, min(1.0, blend))
    parts = [f"chromakey={color}:{similarity:.3f}:{blend:.3f}"]
    if background and not background.startswith("#"):
        # фон-картинка подаётся отдельным входом, здесь только масштабируем
        parts.append(f"scale={width}:{height}:force_original_aspect_ratio=increase")
        parts.append(f"crop={width}:{height}")
    elif background.startswith("#"):
        parts.append(f"format=rgba")
        parts.append(f"colorchannelmixer=aa=1.0")
    return ",".join(parts)


def crop_filter(mode: str, width: int, height: int) -> str:
    """Кадрирование под формат (свободное/16:9/9:16/1:1/4:5).

    Args:
        mode: ``crop`` | ``fit`` | ``blur`` | ``stretch``.
        width: Целевая ширина.
        height: Целевая высота.

    Returns:
        Строка фильтров.
    """
    from core.video_engine import aspect_filter

    return aspect_filter(mode, width, height)


def stabilize(video_path: str | Path, out_path: Optional[str | Path] = None,
              shakiness: int = 5, accuracy: int = 9) -> Path:
    """Двухпроходная стабилизация через vidstab.

    Args:
        video_path: Исходное видео.
        out_path: Куда сохранить (по умолчанию ``<name>_stable.mp4``).
        shakiness: Степень «тряскости» (1–10).
        accuracy: Точность (1–15).

    Returns:
        Путь к стабилизированному файлу.

    Raises:
        RuntimeError: если FFmpeg недоступен или не поддерживает vidstab.
    """
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден")
    src = Path(video_path)
    out = Path(out_path) if out_path else src.with_name(f"{src.stem}_stable.mp4")
    with tempfile.TemporaryDirectory(prefix="bfs_stab_") as tmp:
        transforms = Path(tmp) / "transforms.trf"
        try:
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src),
                 "-vf", f"vidstabdetect=shakiness={shakiness}:accuracy={accuracy}:result={transforms}",
                 "-f", "null", "-"], timeout=3600)
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src),
                 "-vf", f"vidstabtransform=input={transforms}:smoothing=15:zoom=2,unsharp=5:5:0.8",
                 "-c:a", "copy", str(out)], timeout=3600)
        except RuntimeError as exc:
            raise RuntimeError(f"Стабилизация недоступна: {exc}") from exc
    return out


def preview_frame(video_path: str | Path, at: float, chain: VideoEffectChain,
                  width: int = 960, height: int = 540) -> Optional[bytes]:
    """Отрендерить один кадр с эффектами (PNG-байты) для превью.

    Args:
        video_path: Видеофайл.
        at: Момент времени, сек.
        chain: Эффекты клипа.
        width: Ширина кадра (пропорционально уменьшает рендер).
        height: Высота кадра.

    Returns:
        PNG-байты или None при ошибке.
    """
    ff = ffmpeg_path()
    if not ff:
        return None
    filters = build_filters(chain, width, height)
    cmd = [ff, "-hide_banner", "-loglevel", "error", "-ss", f"{max(0.0, at):.3f}",
           "-i", str(video_path), "-frames:v", "1"]
    if filters:
        cmd += ["-vf", filters]
    cmd += ["-f", "image2pipe", "-vcodec", "png", "-"]
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=90)
        return proc.stdout or None
    except Exception as exc:
        LOG.debug("preview_frame failed: %s", exc)
        return None


def apply_filters_to_file(src: str | Path, out: str | Path, chain: VideoEffectChain,
                          width: int = 1920, height: int = 1080,
                          crf: int = 20) -> Path:
    """Применить эффекты к файлу целиком (перекодирование)."""
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден")
    filters = build_filters(chain, width, height)
    cmd = [ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src)]
    if filters:
        cmd += ["-vf", filters]
    cmd += ["-c:v", "libx264", "-crf", str(crf), "-preset", "medium",
            "-c:a", "copy", str(out)]
    run(cmd, timeout=7200)
    return Path(out)


def list_presets() -> List[Dict[str, str]]:
    """Список пресетов для UI: ``[{"key": ..., "filters": ...}]``."""
    return [{"key": name, "filters": filters} for name, filters in FILTER_PRESETS.items()]
