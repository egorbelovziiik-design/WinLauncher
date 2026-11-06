"""Сквозная проверка работоспособности приложения (без GUI).

Запускается в собранном exe (``--self-check``) и из исходников в CI.
Проверяет именно то, что ломается при неполной сборке PyInstaller:
импорты тяжёлых зависимостей, чтение/запись аудио, эффекты на scipy,
FFmpeg, кадры видео и сериализацию проекта.

Код возврата: 0 — всё в порядке, 1 — есть провалы (список в выводе).
"""

from __future__ import annotations

import sys
import tempfile
import traceback
from pathlib import Path
from typing import Callable, List, Tuple

OK = "OK"
FAIL = "FAIL"


def _sample_audio(seconds: float = 1.0, sample_rate: int = 22050):
    """Сгенерировать стерео-синус (без внешних файлов)."""
    import numpy as np

    from core.audio_engine import AudioBuffer

    frames = int(seconds * sample_rate)
    t = np.arange(frames, dtype=np.float32) / sample_rate
    left = 0.3 * np.sin(2 * np.pi * 220.0 * t)
    right = 0.3 * np.sin(2 * np.pi * 330.0 * t)
    return AudioBuffer(np.stack([left, right], axis=1).astype(np.float32), sample_rate)


def _make_test_video(path: Path) -> None:
    """Сгенерировать тестовый клип через FFmpeg (без внешних файлов)."""
    from utils.ffmpeg_helper import ffmpeg_path, run

    ff = ffmpeg_path()
    assert ff, "FFmpeg не найден"
    run([
        ff, "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc=size=320x180:rate=15:duration=1",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", str(path),
    ], timeout=180)


def check_imports(tmp: Path) -> str:  # noqa: ARG001 - единая сигнатура
    """Импорты, которые раньше падали в собранном exe."""
    import numpy
    import PIL.Image  # noqa: F401
    import requests  # noqa: F401
    import scipy
    import scipy.signal  # noqa: F401  (тянет pydoc через _docscrape)
    import soundfile  # noqa: F401

    import core.effects_audio  # noqa: F401
    import core.exporter  # noqa: F401
    import core.mixer  # noqa: F401
    import core.project_manager  # noqa: F401
    import core.subtitle_engine  # noqa: F401
    import core.text_overlay  # noqa: F401
    import core.video_engine  # noqa: F401
    import search.beat_search  # noqa: F401
    import utils.ffmpeg_helper  # noqa: F401

    return f"numpy {numpy.__version__}, scipy {scipy.__version__}"


def check_audio_io(tmp: Path) -> str:
    """Запись/чтение WAV и рендер микса."""
    import soundfile as sf

    from core.audio_engine import load_audio
    from core.mixer import MixerProject

    buffer = _sample_audio(1.0)
    wav = tmp / "tone.wav"
    sf.write(str(wav), buffer.samples, buffer.sample_rate)

    loaded = load_audio(str(wav), buffer.sample_rate, 2)
    assert loaded.frames > 0, "load_audio вернул пустой буфер"

    project = MixerProject(name="self-check")
    project.add_track("A")
    clip = project.add_clip(0, str(wav), 0.0)
    assert clip is not None, "add_clip не добавил клип"
    mix = project.render()
    assert mix.frames > 0, "микс пустой"
    return f"кадров в миксе: {mix.frames}, длительность {project.duration:.2f} с"


def check_effects(tmp: Path) -> str:  # noqa: ARG001 - единая сигнатура
    """Эффекты на scipy (EQ/компрессор) — раньше давали ModuleNotFoundError."""
    import numpy as np

    from core.effects_audio import AudioEffect, EffectChain

    buffer = _sample_audio(0.5)
    chain = EffectChain()
    chain.add(AudioEffect(kind="eq", params={"low": 2.0, "mid": -1.0, "high": 1.5}))
    out = chain.process(buffer)
    assert out is not None and out.frames == buffer.frames, "EQ испортил длину"
    assert np.isfinite(out.samples).all(), "в результате NaN/inf"
    return f"эффектов в цепочке: {len(chain.effects)}"


def check_ffmpeg(tmp: Path) -> str:  # noqa: ARG001 - единая сигнатура
    """FFmpeg доступен (системный или из imageio-ffmpeg)."""
    from utils.ffmpeg_helper import ffmpeg_path

    path = ffmpeg_path()
    assert path and Path(path).exists(), "FFmpeg не найден"
    return str(path)


def check_subtitles(tmp: Path) -> str:
    """Субтитры: roundtrip SRT + drawtext-фильтры."""
    from core.subtitle_engine import SubtitleCue, SubtitleTrack

    track = SubtitleTrack(cues=[SubtitleCue(0.0, 1.0, "Проверка субтитров")])
    srt = tmp / "selfcheck.srt"
    track.save(str(srt))
    loaded = SubtitleTrack().load(str(srt))
    assert len(loaded.cues) == 1, "SRT roundtrip не сохранил реплику"
    filters = track.to_drawtext_filters()
    assert "drawtext" in filters, "нет drawtext-фильтра"
    return f"реплик: {len(loaded.cues)}"


def check_project(tmp: Path) -> str:
    """Сериализация проекта .bfs."""
    import json

    from core.mixer import MixerProject
    from core.project_manager import ProjectData

    project = MixerProject(name="self-check")
    project.add_track("A")
    data = ProjectData()
    data.audio = project
    data.meta.name = "self-check"
    data.meta.kind = "audio"

    path = tmp / "selfcheck.bfs"
    path.write_text(json.dumps(data.to_dict(), ensure_ascii=False), encoding="utf-8")
    restored = ProjectData.from_dict(json.loads(path.read_text(encoding="utf-8")))
    assert restored.meta.name == "self-check", "метаданные не восстановились"
    return f"{path.stat().st_size} байт JSON"


def check_video_clip(tmp: Path) -> str:
    """Кадр из видео (конвейер FFmpeg)."""
    from core.video_engine import VideoEngine

    source = tmp / "source.mp4"
    _make_test_video(source)

    engine = VideoEngine()
    assert engine.available, "движок видео считает FFmpeg недоступным"
    data = engine.frame_at(str(source), 0.2, width=160)
    assert data and data[:4] == b"\x89PNG", "кадр не извлёкся"
    return f"PNG-кадр {len(data)} байт"


def check_video_export(tmp: Path) -> str:
    """Полный экспорт сцены с drawtext-оверлеем (как при «Экспорт видео»)."""
    from core.exporter import export_video
    from core.video_engine import ClipSpec, Scene, TrackSpec

    source = tmp / "source.mp4"
    _make_test_video(source)

    scene = Scene(
        width=320, height=180, fps=15, duration=1.0,
        tracks=[TrackSpec(kind="video", clips=[
            ClipSpec(path=str(source), start=0.0, duration=1.0)])],
        normalize_audio=False,
        extra_filters=["drawtext=text='self-check':fontsize=16:x=10:y=10:fontcolor=white"],
    )
    out = tmp / "selfcheck.mp4"
    export_video(scene, out, preset="Минимальный размер")
    size = out.stat().st_size if out.exists() else 0
    assert size > 2000, f"видео не записалось ({size} байт)"
    return f"{size} байт"


CHECKS: List[Tuple[str, Callable]] = [
    ("imports", check_imports),
    ("audio_io", check_audio_io),
    ("effects", check_effects),
    ("ffmpeg", check_ffmpeg),
    ("subtitles", check_subtitles),
    ("project", check_project),
    ("video_clip", check_video_clip),
    ("video_export", check_video_export),
]


def run_self_check(verbose: bool = True) -> int:
    """Прогнать все проверки и вернуть код возврата."""
    failures: List[str] = []

    def emit(text: str) -> None:
        if not verbose:
            return
        # В оконной сборке sys.stdout может быть None
        for stream in (sys.stdout, sys.stderr):
            if stream is not None:
                try:
                    stream.write(text + "\n")
                    stream.flush()
                except (OSError, ValueError):
                    pass
                return

    with tempfile.TemporaryDirectory(prefix="beatforge_check_") as tmpdir:
        tmp = Path(tmpdir)
        for name, fn in CHECKS:
            try:
                detail = fn(tmp)
                emit(f"[{OK}]   {name}: {detail}")
            except Exception as exc:  # noqa: BLE001 - важен сам факт провала
                failures.append(f"{name}: {type(exc).__name__}: {exc}")
                emit(f"[{FAIL}] {name}: {type(exc).__name__}: {exc}")
                emit(traceback.format_exc())

    if failures:
        emit(f"\nПровалено проверок: {len(failures)} из {len(CHECKS)}")
        return 1
    emit(f"\nВсе проверки пройдены ({len(CHECKS)}).")
    return 0
