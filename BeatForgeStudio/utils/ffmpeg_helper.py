"""Проверка и управление FFmpeg/FFprobe.

Модуль ищет бинарники в порядке: конфиг → ``PATH`` → пакет ``imageio-ffmpeg``
(на Windows туда же можно положить ``ffmpeg.exe`` рядом с приложением).

Основные функции:

* :func:`ffmpeg_path`, :func:`ffprobe_path` — пути к бинарникам;
* :func:`ensure_ffmpeg` — проверка с человекочитаемым результатом;
* :func:`probe` — метаданные медиафайла (длительность, потоки);
* :func:`duration` — длительность файла в секундах;
* :func:`run` — обёртка над ``subprocess`` с логами и таймаутом;
* :func:`ffmpeg_supports` — проверка наличия фильтра/кодека.
"""

from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
import sys
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

from utils.config import get_config

LOG = logging.getLogger(__name__)

_cached_ffmpeg: Optional[str] = None
_cached_ffprobe: Optional[str] = None
_cache_lock = threading.RLock()  # reentrant: ffprobe_path() вызывает ffmpeg_path()


@dataclass
class FFmpegStatus:
    """Результат проверки окружения FFmpeg.

    Attributes:
        available: Найден ли ffmpeg.
        ffmpeg: Путь к ffmpeg.
        ffprobe: Путь к ffprobe (может отсутствовать).
        version: Первая строка ``ffmpeg -version``.
        encoders: Множество доступных кодировщиков (если запрашивалось).
        message: Человекочитаемое описание для UI.
    """

    available: bool
    ffmpeg: Optional[str] = None
    ffprobe: Optional[str] = None
    version: str = ""
    encoders: Optional[set] = None
    message: str = ""


# ---------------------------------------------------------------- поиск путей
def _candidate_name(base: str) -> str:
    """Имя бинарника с учётом платформы."""
    return f"{base}.exe" if os.name == "nt" else base


def _search_sidecar(base: str) -> Optional[str]:
    """Поискать бинарник рядом с приложением/скриптом (portable-режим)."""
    roots = [Path.cwd(), Path(__file__).resolve().parent.parent]
    if getattr(sys, "frozen", False):  # PyInstaller
        roots.insert(0, Path(sys.executable).resolve().parent)
        roots.insert(0, Path(getattr(sys, "_MEIPASS", ".")))
    for root in roots:
        for sub in ("", "bin", "ffmpeg", "assets/ffmpeg"):
            candidate = root / sub / _candidate_name(base)
            if candidate.exists():
                return str(candidate)
    return None


def ffmpeg_path() -> Optional[str]:
    """Вернуть путь к ffmpeg (или None)."""
    global _cached_ffmpeg
    with _cache_lock:
        if _cached_ffmpeg and Path(_cached_ffmpeg).exists():
            return _cached_ffmpeg
        cfg = get_config().video
        if cfg.ffmpeg_path and Path(cfg.ffmpeg_path).exists():
            _cached_ffmpeg = cfg.ffmpeg_path
            return _cached_ffmpeg
        found = _search_sidecar("ffmpeg") or shutil.which("ffmpeg")
        if not found:
            try:
                import imageio_ffmpeg

                found = imageio_ffmpeg.get_ffmpeg_exe()
            except Exception:  # pragma: no cover
                found = None
        _cached_ffmpeg = found
        return found


def ffprobe_path() -> Optional[str]:
    """Вернуть путь к ffprobe (или None, если его нет рядом с ffmpeg)."""
    global _cached_ffprobe
    with _cache_lock:
        if _cached_ffprobe and Path(_cached_ffprobe).exists():
            return _cached_ffprobe
        cfg = get_config().video
        if cfg.ffprobe_path and Path(cfg.ffprobe_path).exists():
            _cached_ffprobe = cfg.ffprobe_path
            return _cached_ffprobe
        found = _search_sidecar("ffprobe") or shutil.which("ffprobe")
        if not found:
            ff = ffmpeg_path()
            if ff:
                sibling = Path(ff).with_name(_candidate_name("ffprobe"))
                if sibling.exists():
                    found = str(sibling)
        _cached_ffprobe = found
        return found


def reset_cache() -> None:
    """Сбросить кэш найденных путей (после смены пути в настройках)."""
    global _cached_ffmpeg, _cached_ffprobe
    with _cache_lock:
        _cached_ffmpeg = None
        _cached_ffprobe = None


# ---------------------------------------------------------------- запуск
def run(args: Sequence[str], timeout: float = 120.0,
        capture: bool = True) -> subprocess.CompletedProcess:
    """Запустить ffmpeg/ffprobe с логами.

    Args:
        args: Полная командная строка, начиная с бинарника.
        timeout: Таймаут, сек.
        capture: Захватывать ли stdout/stderr.

    Returns:
        ``CompletedProcess``.

    Raises:
        RuntimeError: Если процесс завершился с ошибкой.
    """
    LOG.debug("exec: %s", " ".join(str(a) for a in args)[:2000])
    kwargs: Dict[str, Any] = {"timeout": timeout}
    if capture:
        kwargs.update(stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                      encoding="utf-8", errors="replace")
    if os.name == "nt":
        kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    try:
        proc = subprocess.run([str(a) for a in args], **kwargs)
    except subprocess.TimeoutExpired:
        raise RuntimeError(f"Процесс не завершился за {timeout:.0f} с") from None
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().splitlines()
        tail = "\n".join(err[-8:]) if err else "нет вывода"
        raise RuntimeError(f"ffmpeg завершился с кодом {proc.returncode}:\n{tail}")
    return proc


def ensure_ffmpeg(with_encoders: bool = False) -> FFmpegStatus:
    """Проверить окружение FFmpeg и вернуть статус для UI.

    Args:
        with_encoders: Дополнительно запросить список кодировщиков (медленнее).

    Returns:
        :class:`FFmpegStatus` — и для показа в настройках, и для логики.
    """
    ff = ffmpeg_path()
    if not ff:
        return FFmpegStatus(
            available=False,
            message=("FFmpeg не найден. Установите его (winget install Gyan.FFmpeg) "
                     "или укажите путь в Настройках → FFmpeg."),
        )
    try:
        proc = run([ff, "-hide_banner", "-version"], timeout=15)
        version = (proc.stdout or "").splitlines()[0] if proc.stdout else "ffmpeg"
    except Exception as exc:
        return FFmpegStatus(available=False, ffmpeg=ff,
                            message=f"FFmpeg не запускается: {exc}")

    encoders: Optional[set] = None
    if with_encoders:
        try:
            out = run([ff, "-hide_banner", "-encoders"], timeout=30).stdout or ""
            encoders = {line.split()[1] for line in out.splitlines()[2:] if len(line.split()) > 1}
        except Exception:
            LOG.debug("Не удалось получить список кодировщиков", exc_info=True)

    probe = ffprobe_path()
    note = "" if probe else " (ffprobe не найден — длительность будет считаться через ffmpeg)"
    return FFmpegStatus(
        available=True,
        ffmpeg=ff,
        ffprobe=probe,
        version=version,
        encoders=encoders,
        message=f"FFmpeg готов: {version}{note}",
    )


def ffmpeg_supports(name: str, kind: str = "filter") -> bool:
    """Проверить, поддерживает ли сборка фильтр/кодек.

    Args:
        name: Имя фильтра или кодировщика (``xfade``, ``libx264``).
        kind: ``filter`` | ``encoder`` | ``decoder``.
    """
    ff = ffmpeg_path()
    if not ff:
        return False
    flag = {"filter": "-filters", "encoder": "-encoders", "decoder": "-decoders"}[kind]
    try:
        out = run([ff, "-hide_banner", flag], timeout=30).stdout or ""
    except Exception:
        return False
    return any(name in line for line in out.splitlines())


# ---------------------------------------------------------------- метаданные
def probe(path: str | Path) -> Dict[str, Any]:
    """Получить метаданные медиафайла через ``ffprobe``.

    Если ffprobe нет — возвращается словарь с длительностью из ``ffmpeg``.

    Args:
        path: Путь к файлу.

    Returns:
        Словарь с ключами ``duration``, ``format``, ``streams``, ``size``.
    """
    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")
    probe_bin = ffprobe_path()
    if probe_bin:
        proc = run([probe_bin, "-v", "error", "-print_format", "json",
                    "-show_format", "-show_streams", str(src)], timeout=60)
        data = json.loads(proc.stdout or "{}")
        fmt = data.get("format", {})
        return {
            "duration": float(fmt.get("duration") or 0.0),
            "format": fmt.get("format_long_name", ""),
            "size": int(fmt.get("size") or src.stat().st_size),
            "streams": data.get("streams", []),
            "path": str(src),
        }

    # fallback: ffmpeg -i (пишет метаданные в stderr) + парсинг Duration
    ff = ffmpeg_path()
    if not ff:
        return {"duration": 0.0, "format": src.suffix.lstrip("."), "size": src.stat().st_size,
                "streams": [], "path": str(src)}
    proc = subprocess.run([ff, "-hide_banner", "-i", str(src)],
                          capture_output=True, text=True, timeout=60,
                          encoding="utf-8", errors="replace")
    dur = 0.0
    streams: List[Dict[str, Any]] = []
    for line in (proc.stderr or "").splitlines():
        if "Duration:" in line:
            try:
                stamp = line.split("Duration:")[1].split(",")[0].strip()
                h, m, s = stamp.split(":")
                dur = int(h) * 3600 + int(m) * 60 + float(s)
            except Exception:
                pass
        elif "Stream #" in line:
            if "Video:" in line:
                streams.append({"codec_type": "video"})
            elif "Audio:" in line:
                streams.append({"codec_type": "audio"})
    return {"duration": dur, "format": src.suffix.lstrip("."),
            "size": src.stat().st_size, "streams": streams, "path": str(src)}


def duration(path: str | Path) -> float:
    """Длительность медиафайла в секундах (0.0 при ошибке)."""
    try:
        return float(probe(path).get("duration", 0.0))
    except Exception as exc:
        LOG.warning("Не удалось определить длительность %s: %s", path, exc)
        return 0.0


def has_audio_stream(path: str | Path) -> bool:
    """Есть ли в файле аудиодорожка."""
    try:
        streams = probe(path).get("streams", [])
        return any(s.get("codec_type") == "audio" for s in streams) if streams else True
    except Exception:
        return True


def has_video_stream(path: str | Path) -> bool:
    """Есть ли в файле видеодорожка."""
    try:
        streams = probe(path).get("streams", [])
        return any(s.get("codec_type") == "video" for s in streams)
    except Exception:
        return False


def extract_frame(path: str | Path, at: float, out_path: str | Path,
                  width: int = 640) -> Path:
    """Извлечь кадр из видео в PNG (для превью/обложек).

    Args:
        path: Видеофайл.
        at: Момент времени, сек.
        out_path: Куда сохранить PNG.
        width: Ширина кадра.

    Returns:
        Путь к сохранённому изображению.
    """
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден")
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    run([ff, "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{max(0.0, at):.3f}",
         "-i", str(path), "-frames:v", "1", "-vf", f"scale={width}:-2", str(out)], timeout=120)
    return out


def make_silent_audio(path: str | Path, seconds: float, sample_rate: int = 44100,
                      channels: int = 2) -> Path:
    """Сгенерировать WAV с тишиной (нужен для рендера без аудио)."""
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("FFmpeg не найден")
    out = Path(path)
    run([ff, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi",
         "-i", f"anullsrc=r={sample_rate}:cl={'stereo' if channels == 2 else 'mono'}",
         "-t", f"{seconds:.3f}", str(out)], timeout=120)
    return out


def list_video_encoders() -> List[str]:
    """Список доступных видеокодировщиков (для выпадающего списка в экспорте)."""
    ff = ffmpeg_path()
    if not ff:
        return []
    try:
        out = run([ff, "-hide_banner", "-encoders"], timeout=30).stdout or ""
    except Exception:
        return []
    wanted = {"libx264", "libx265", "h264_nvenc", "hevc_nvenc", "h264_qsv",
              "libvpx-vp9", "libvpx", "prores_ks", "gif", "libaom-av1"}
    found = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[1] in wanted:
            found.append(parts[1])
    return found
