"""Аудиоэффекты BeatForge Studio (numpy/scipy, с пресетами и bypass).

Архитектура:

* каждая обработка — самостоятельная функция ``process(samples, sr, **params)``,
  работающая с массивом формы ``(frames, channels)`` float32;
* :class:`AudioEffect` — обёртка «эффект + параметры + bypass», умеет отдавать
  копию для параллельной обработки;
* :class:`EffectChain` — последовательная цепочка эффектов с быстрым превью
  (пересчёт при изменении параметров) и лёгким сглаживанием громкости;
* :data:`FX_PRESETS` — готовые пресеты («Vocal Warm», «Bass Boost», …).

Все эффекты работают офлайн (целиком на буфере), что даёт лучшее качество и
позволяет использовать точные scipy-фильтры; для real-time превью цепочка
пересчитывается в фоновом потоке и подменяет буфер движка.

Замечание про производительность: тяжёлые эффекты (reverb-свёртка, pitch shift)
на длинных файлах занимают сотни миллисекунд — их стоит применять к выделению
или на финальном экспорте, а не на каждый сдвиг слайдера. В UI это решается
задержкой (debounce) в 120 мс перед пересчётом.
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence

import numpy as np
from scipy import signal

from core.audio_engine import AudioBuffer, normalize_buffer
from utils.config import get_config

LOG = logging.getLogger(__name__)


# ============================================================
#  Базовые утилиты
# ============================================================
def _as_2d(samples: np.ndarray) -> np.ndarray:
    """Привести вход к форме (frames, channels) float32."""
    data = np.asarray(samples, dtype=np.float32)
    if data.ndim == 1:
        data = data[:, None]
    return data


def _match_channels(data: np.ndarray, channels: int) -> np.ndarray:
    """Подогнать число каналов."""
    if data.shape[1] == channels:
        return data
    if channels == 1:
        return data.mean(axis=1, keepdims=True)
    if data.shape[1] == 1:
        return np.repeat(data, channels, axis=1)
    if data.shape[1] > channels:
        return data[:, :channels]
    pad = np.repeat(data[:, -1:], channels - data.shape[1], axis=1)
    return np.hstack([data, pad])


def db_to_gain(db: float) -> float:
    """Перевести dB в линейный коэффициент."""
    return float(10 ** (db / 20.0))


def gain_to_db(gain: float) -> float:
    """Перевести линейный коэффициент в dB (с защитой от нуля)."""
    return 20.0 * math.log10(max(gain, 1e-9))


# ============================================================
#  Эффекты
# ============================================================
def apply_gain(samples: np.ndarray, gain_db: float) -> np.ndarray:
    """Изменить громкость на ``gain_db`` дБ."""
    return (_as_2d(samples) * db_to_gain(gain_db)).astype(np.float32)


def normalize(samples: np.ndarray, target_db: float = -1.0) -> np.ndarray:
    """Нормализовать пик до ``target_db`` dBFS."""
    data = _as_2d(samples)
    peak = float(np.max(np.abs(data))) if data.size else 0.0
    if peak <= 1e-9:
        return data
    target = 10 ** (target_db / 20.0)
    return (data * (target / peak)).astype(np.float32)


def reverse(samples: np.ndarray) -> np.ndarray:
    """Проиграть буфер задом наперёд."""
    return np.ascontiguousarray(_as_2d(samples)[::-1])


def fade(samples: np.ndarray, sample_rate: int, fade_in: float = 0.0,
         fade_out: float = 0.0, curve: str = "linear") -> np.ndarray:
    """Применить fade-in/fade-out.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        fade_in: Длительность нарастания, сек.
        fade_out: Длительность затухания, сек.
        curve: ``linear`` | ``exp`` | ``log``.

    Returns:
        Новый массив с плавными краями.
    """
    data = _as_2d(samples).copy()
    frames = data.shape[0]
    if frames == 0:
        return data

    def ramp(n: int, rising: bool) -> np.ndarray:
        if n <= 0:
            return np.zeros(0, dtype=np.float32)
        x = np.linspace(0.0, 1.0, n, dtype=np.float32)
        if curve == "exp":
            x = x ** 2
        elif curve == "log":
            x = np.sqrt(x)
        return x if rising else x[::-1]

    nin = min(frames, max(0, int(fade_in * sample_rate)))
    nout = min(frames, max(0, int(fade_out * sample_rate)))
    if nin:
        data[:nin] *= ramp(nin, True)[:, None]
    if nout:
        data[-nout:] *= ramp(nout, False)[:, None]
    return data


def equalizer(samples: np.ndarray, sample_rate: int, low_db: float = 0.0,
              mid_db: float = 0.0, high_db: float = 0.0,
              low_freq: float = 200.0, high_freq: float = 4000.0) -> np.ndarray:
    """3-полосный эквалайзер (low/high-shelf + band-pass через butter).

    Реализация «split + gain + sum»: сигнал раскладывается на три полосы
    (lowpass / bandpass / highpass второго порядка), каждая усиливается на
    свою величину в dB, затем полосы складываются. Сумма полос без изменений
    даёт исходный сигнал, поэтому усиливать можно в любую сторону.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        low_db: Усиление низов, dB.
        mid_db: Усиление середины, dB.
        high_db: Усиление верхов, dB.
        low_freq: Граница низов, Гц.
        high_freq: Граница верхов, Гц.

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    if not any((low_db, mid_db, high_db)) or data.size == 0:
        return data
    nyq = sample_rate / 2.0
    low = max(20.0, min(float(low_freq), nyq * 0.8))
    high = max(low * 1.5, min(float(high_freq), nyq * 0.92))

    b_low, a_low = signal.butter(2, low / nyq, btype="low", output="ba")
    b_mid, a_mid = signal.butter(2, [low / nyq, high / nyq], btype="band", output="ba")
    b_high, a_high = signal.butter(2, high / nyq, btype="high", output="ba")

    low_band = signal.lfilter(b_low, a_low, data, axis=0)
    high_band = signal.lfilter(b_high, a_high, data, axis=0)
    mid_band = signal.lfilter(b_mid, a_mid, data, axis=0)

    out = (low_band * db_to_gain(low_db)
           + mid_band * db_to_gain(mid_db)
           + high_band * db_to_gain(high_db))
    return out.astype(np.float32)


def _make_impulse_response(sample_rate: int, decay: float = 0.5,
                           room: float = 0.5, damping: float = 0.4) -> np.ndarray:
    """Сгенерировать синтетический импульсный отклик для реверба.

    Args:
        sample_rate: Частота дискретизации.
        decay: Время затухания, сек.
        room: «Размер помещения» 0..1 (влияет на плотность отражений).
        damping: Демпфирование высоких частот 0..1.

    Returns:
        Моно-IR нормализованный по энергии.
    """
    length = max(64, int(decay * sample_rate))
    rng = np.random.default_rng(1234)
    t = np.linspace(0.0, decay, length, dtype=np.float32)
    noise = rng.standard_normal(length).astype(np.float32)
    envelope = np.exp(-t * (6.0 / max(decay, 0.05)))
    ir = noise * envelope
    # ранние отражения для «комнаты»
    early = int(sample_rate * (0.01 + 0.05 * room))
    if 0 < early < length:
        ir[early:early + max(1, length // 60)] *= 2.2
    # демпфирование: лёгкий low-pass
    cutoff = max(0.05, 1.0 - 0.8 * damping)
    b, a = signal.butter(1, cutoff)
    ir = signal.lfilter(b, a, ir).astype(np.float32)
    energy = math.sqrt(float(np.sum(ir ** 2))) or 1.0
    return ir / energy


def reverb(samples: np.ndarray, sample_rate: int, mix: float = 0.25,
           decay: float = 0.6, room: float = 0.5, damping: float = 0.4,
           predelay_ms: float = 12.0) -> np.ndarray:
    """Реверберация свёрткой с синтетическим IR.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        mix: Доля эффекта 0..1.
        decay: Время затухания, сек.
        room: Размер помещения 0..1.
        damping: Демпфирование 0..1.
        predelay_ms: Задержка до реверба, мс.

    Returns:
        Обработанный массив (wet/dry микс).
    """
    data = _as_2d(samples)
    if mix <= 0.0:
        return data
    ir = _make_impulse_response(sample_rate, decay, room, damping)
    pre = np.zeros(int(sample_rate * predelay_ms / 1000.0), dtype=np.float32)
    ir = np.concatenate([pre, ir])
    wet = np.empty_like(data)
    for ch in range(data.shape[1]):
        wet[:, ch] = signal.fftconvolve(data[:, ch], ir, mode="full")[: data.shape[0]]
    wet_peak = float(np.max(np.abs(wet))) or 1.0
    dry_peak = float(np.max(np.abs(data))) or 1.0
    wet *= dry_peak / wet_peak  # выравниваем уровни
    out = (1.0 - mix) * data + mix * wet
    return out.astype(np.float32)


def delay(samples: np.ndarray, sample_rate: int, time_ms: float = 250.0,
          feedback: float = 0.35, mix: float = 0.25, ping_pong: bool = False,
          high_cut: float = 6000.0) -> np.ndarray:
    """Дилей с обратной связью (опционально ping-pong).

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        time_ms: Задержка, мс.
        feedback: Обратная связь 0..0.95.
        mix: Доля wet 0..1.
        ping_pong: Отражения прыгают между каналами.
        high_cut: Срез верхов в линии задержки (аналоговое затухание), Гц.

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    if mix <= 0.0 or data.size == 0:
        return data
    delay_samples = max(1, int(sample_rate * max(1.0, time_ms) / 1000.0))
    feedback = float(max(0.0, min(0.95, feedback)))
    channels = data.shape[1]
    frames = data.shape[0]
    wet = np.zeros_like(data)

    for ch in range(channels):
        line = np.zeros(frames + delay_samples, dtype=np.float32)
        line[delay_samples:] = data[:, ch]
        target = (1 - ch) if (ping_pong and channels > 1) else ch
        echo_number = 1
        while echo_number <= 8:
            offset = echo_number * delay_samples
            if offset >= line.size:
                break
            gain = feedback ** (echo_number - 1)
            segment = line[: line.size - offset] * gain
            usable = min(segment.size, wet.shape[0] - offset)
            if usable > 0:
                wet[offset:offset + usable, target] += segment[:usable]
            echo_number += 1

    nyq = sample_rate / 2.0
    cutoff = min(0.95, max(0.05, high_cut / nyq))
    b, a = signal.butter(2, cutoff, btype="low", output="ba")
    wet = signal.lfilter(b, a, wet, axis=0).astype(np.float32)
    return ((1.0 - mix) * data + mix * wet).astype(np.float32)


def compressor(samples: np.ndarray, sample_rate: int, threshold_db: float = -18.0,
               ratio: float = 3.0, attack_ms: float = 12.0, release_ms: float = 180.0,
               makeup_db: float = 0.0, knee_db: float = 6.0) -> np.ndarray:
    """Динамический компрессор с soft-knee.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        threshold_db: Порог срабатывания, dBFS.
        ratio: Степень сжатия (4.0 = 4:1).
        attack_ms: Атака, мс.
        release_ms: Восстановление, мс.
        makeup_db: Компенсирующее усиление, dB.
        knee_db: Ширина мягкого колена, dB.

    Returns:
        Сжатый массив.
    """
    data = _as_2d(samples)
    if data.size == 0:
        return data
    abs_signal = np.max(np.abs(data), axis=1)
    level_db = 20.0 * np.log10(np.maximum(abs_signal, 1e-9))
    over = level_db - threshold_db
    knee = max(0.1, knee_db)
    gain_reduction = np.zeros_like(level_db)
    above = over > knee / 2
    below = (over > -knee / 2) & ~above
    gain_reduction[above] = over[above] * (1.0 - 1.0 / max(ratio, 1.0))
    # мягкое колено (квадратичная интерполяция)
    gain_reduction[below] = ((over[below] + knee / 2) ** 2) / (2 * knee) * (1 - 1 / max(ratio, 1.0))
    # сглаживание огибающей (attack/release)
    attack = math.exp(-1.0 / max(1.0, sample_rate * attack_ms / 1000.0))
    release = math.exp(-1.0 / max(1.0, sample_rate * release_ms / 1000.0))
    smoothed = np.empty_like(gain_reduction)
    state = 0.0
    for i, value in enumerate(gain_reduction):
        coeff = attack if value > state else release
        state = coeff * state + (1.0 - coeff) * value
        smoothed[i] = state
    gain = (10.0 ** ((-smoothed + makeup_db) / 20.0)).astype(np.float32)
    return (data * gain[:, None]).astype(np.float32)


def distortion(samples: np.ndarray, drive: float = 2.0, mix: float = 0.5,
               tone: float = 0.6, output_db: float = -1.0) -> np.ndarray:
    """Сатирация/дисторшн с тональным фильтром.

    Args:
        samples: Аудиоданные.
        drive: Величина перегруза (1..12).
        mix: Доля wet.
        tone: 0 — темно, 1 — ярко (пост-фильтр).
        output_db: Выходной уровень.

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    drive = max(1.0, min(20.0, drive))
    wet = np.tanh(data * drive) / math.tanh(drive)
    # tone: смесь между "мягким" и "жёстким" звучанием
    tone = max(0.0, min(1.0, tone))
    wet = wet * (0.6 + 0.4 * tone) + data * (1 - tone) * 0.4
    out = (1.0 - mix) * data + mix * wet
    return normalize(out, output_db)


def chorus(samples: np.ndarray, sample_rate: int, depth_ms: float = 12.0,
           rate_hz: float = 0.6, mix: float = 0.35, voices: int = 2) -> np.ndarray:
    """Хор/удвоение через модулированные линии задержки.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        depth_ms: Глубина модуляции, мс.
        rate_hz: Частота LFO, Гц.
        mix: Доля wet.
        voices: Число голосов (1..4).

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    n = data.shape[0]
    t = np.arange(n, dtype=np.float32) / sample_rate
    base_delay = 0.012 * sample_rate
    depth = (depth_ms / 1000.0) * sample_rate
    wet = np.zeros_like(data)
    voices = max(1, min(4, voices))
    for v in range(voices):
        lfo = np.sin(2 * np.pi * rate_hz * t + v * np.pi / 2)
        delay_samples = base_delay + depth * (lfo + 1) / 2
        for ch in range(data.shape[1]):
            idx = np.arange(n) - delay_samples
            wet[:, ch] += np.interp(idx, np.arange(n), data[:, ch], left=0.0)
    wet /= voices
    return ((1.0 - mix) * data + mix * wet).astype(np.float32)


def pitch_shift(samples: np.ndarray, sample_rate: int, semitones: float,
                preserve_formants: bool = True) -> np.ndarray:
    """Сдвиг высоты тона через librosa (офлайн).

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        semitones: Сдвиг в полутонах (-24..24).
        preserve_formants: Оставить форманты (для вокала).

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    if abs(semitones) < 1e-3:
        return data
    try:
        import librosa
    except Exception as exc:  # pragma: no cover
        LOG.warning("librosa недоступна, pitch shift пропущен: %s", exc)
        return data
    out = np.stack([
        librosa.effects.pitch_shift(data[:, ch].astype(np.float32), sr=sample_rate,
                                    n_steps=float(semitones),
                                    res_type="soxr_hq" if preserve_formants else "kaiser_fast")
        for ch in range(data.shape[1])
    ], axis=1)
    return out.astype(np.float32)


def time_stretch(samples: np.ndarray, sample_rate: int, rate: float,
                 preserve_pitch: bool = True) -> np.ndarray:
    """Растянуть/сжать по времени.

    Args:
        samples: Аудиоданные.
        sample_rate: Частота дискретизации.
        rate: >1 — быстрее, <1 — медленнее.
        preserve_pitch: Сохранять тон (иначе — ресемплинг как на плёнке).

    Returns:
        Обработанный массив.
    """
    data = _as_2d(samples)
    rate = max(0.25, min(4.0, float(rate)))
    if abs(rate - 1.0) < 1e-3:
        return data
    if not preserve_pitch:
        new_len = max(1, int(data.shape[0] / rate))
        x_old = np.arange(data.shape[0], dtype=np.float64)
        x_new = np.linspace(0, data.shape[0] - 1, new_len)
        return np.stack([np.interp(x_new, x_old, data[:, ch]) for ch in range(data.shape[1])],
                        axis=1).astype(np.float32)
    try:
        import librosa

        return np.stack([
            librosa.effects.time_stretch(data[:, ch].astype(np.float32), rate=rate)
            for ch in range(data.shape[1])
        ], axis=1).astype(np.float32)
    except Exception as exc:  # pragma: no cover
        LOG.warning("time_stretch недоступен: %s", exc)
        return data


def deesser(samples: np.ndarray, sample_rate: int, intensity: float = 0.5) -> np.ndarray:
    """Мягкое подавление «шипящих» (упрощённый де-эссер 5–9 кГц)."""
    data = _as_2d(samples)
    nyq = sample_rate / 2.0
    if nyq < 10000:
        return data
    b, a = signal.butter(2, [5000 / nyq, min(0.98, 9000 / nyq)], btype="band", output="ba")
    high = signal.lfilter(b, a, data, axis=0)
    envelope = np.max(np.abs(high), axis=1, keepdims=True)
    threshold = 0.08
    gain = np.where(envelope > threshold,
                    1.0 - intensity * np.clip((envelope - threshold) / (threshold * 4), 0, 0.8),
                    1.0)
    return (data - high * (1 - gain)).astype(np.float32)


def limiter(samples: np.ndarray, ceiling_db: float = -0.3, lookahead_ms: float = 3.0,
            sample_rate: int = 44100) -> np.ndarray:
    """Лимитер: плавно снижает пики до потолка, не «срезая» волну.

    Args:
        samples: Аудиоданные.
        ceiling_db: Потолок, dBFS (обычно -0.3…-1.0).
        lookahead_ms: Окно упреждения для сглаживания огибающей, мс.
        sample_rate: Частота дискретизации.

    Returns:
        Ограниченный массив.
    """
    data = _as_2d(samples)
    if data.size == 0:
        return data
    ceiling = db_to_gain(ceiling_db)
    peak = float(np.max(np.abs(data)))
    if peak <= ceiling:
        return data
    # усиление, необходимое чтобы уложиться в потолок, сглаживаем по времени
    need = ceiling / peak
    window = max(1, int(sample_rate * lookahead_ms / 1000.0))
    if window > 1 and data.shape[0] > window:
        kernel = np.ones(window, dtype=np.float32) / window
        envelope = np.max(np.abs(data), axis=1)
        smoothed = np.convolve(envelope, kernel, mode="same")
        target = np.where(smoothed > ceiling, ceiling / np.maximum(smoothed, 1e-9), 1.0)
        target = np.minimum(target, need if need < 1.0 else 1.0)
        data = data * target[:, None]
    else:
        data = data * need
    return np.clip(data, -ceiling, ceiling).astype(np.float32)


def silence(samples: np.ndarray, start: float, end: float, sample_rate: int) -> np.ndarray:
    """Вырезать (заглушить) диапазон времени."""
    data = _as_2d(samples).copy()
    a = max(0, int(start * sample_rate))
    b = min(data.shape[0], int(end * sample_rate))
    data[a:b] = 0.0
    return data


# ============================================================
#  Обёртки и цепочки
# ============================================================
@dataclass
class AudioEffect:
    """Эффект с параметрами и bypass.

    Attributes:
        kind: Тип эффекта (``reverb``, ``delay``, ``eq``, …).
        params: Параметры для соответствующей функции.
        bypass: Выключен (звук проходит без обработки).
        mix: Общая доля эффекта (где поддерживается).
        name: Подпись для UI.
    """

    kind: str
    params: Dict[str, float] = field(default_factory=dict)
    bypass: bool = False
    name: str = ""

    def apply(self, buffer: AudioBuffer) -> AudioBuffer:
        """Применить эффект к буферу.

        Args:
            buffer: Исходный буфер.

        Returns:
            Новый буфер (или исходный, если bypass).
        """
        if self.bypass:
            return buffer
        data = _process(self.kind, buffer.samples, buffer.sample_rate, self.params)
        return AudioBuffer(data, buffer.sample_rate, buffer.path)

    def to_dict(self) -> Dict[str, object]:
        """Сериализация для проекта."""
        return {"kind": self.kind, "params": dict(self.params),
                "bypass": self.bypass, "name": self.name}

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "AudioEffect":
        """Десериализация из проекта."""
        return cls(kind=str(data.get("kind", "eq")),
                   params={k: float(v) for k, v in (data.get("params") or {}).items()},
                   bypass=bool(data.get("bypass", False)),
                   name=str(data.get("name", "")))


def _process(kind: str, samples: np.ndarray, sample_rate: int,
             params: Dict[str, float]) -> np.ndarray:
    """Диспетчер эффектов: применяет нужную функцию по имени."""
    p = {k: float(v) for k, v in params.items()}
    data = _as_2d(samples)
    if kind == "gain":
        return apply_gain(data, p.get("db", 0.0))
    if kind == "normalize":
        return normalize(data, p.get("target_db", -1.0))
    if kind == "reverse":
        return reverse(data)
    if kind == "fade":
        return fade(data, sample_rate, p.get("fade_in", 0.0), p.get("fade_out", 0.0),
                    str(params.get("curve", "linear")))
    if kind == "eq":
        return equalizer(data, sample_rate, p.get("low_db", 0.0), p.get("mid_db", 0.0),
                         p.get("high_db", 0.0), p.get("low_freq", 200.0),
                         p.get("high_freq", 4000.0))
    if kind == "reverb":
        return reverb(data, sample_rate, p.get("mix", 0.25), p.get("decay", 0.6),
                      p.get("room", 0.5), p.get("damping", 0.4), p.get("predelay_ms", 12.0))
    if kind == "delay":
        return delay(data, sample_rate, p.get("time_ms", 250.0), p.get("feedback", 0.35),
                     p.get("mix", 0.25), bool(p.get("ping_pong", 0.0)),
                     p.get("high_cut", 6000.0))
    if kind == "compressor":
        return compressor(data, sample_rate, p.get("threshold_db", -18.0), p.get("ratio", 3.0),
                          p.get("attack_ms", 12.0), p.get("release_ms", 180.0),
                          p.get("makeup_db", 0.0), p.get("knee_db", 6.0))
    if kind == "distortion":
        return distortion(data, p.get("drive", 2.0), p.get("mix", 0.5),
                          p.get("tone", 0.6), p.get("output_db", -1.0))
    if kind == "chorus":
        return chorus(data, sample_rate, p.get("depth_ms", 12.0), p.get("rate_hz", 0.6),
                      p.get("mix", 0.35), int(p.get("voices", 2)))
    if kind == "pitch":
        return pitch_shift(data, sample_rate, p.get("semitones", 0.0))
    if kind == "stretch":
        return time_stretch(data, sample_rate, p.get("rate", 1.0),
                            bool(p.get("preserve_pitch", 1.0)))
    if kind == "deesser":
        return deesser(data, sample_rate, p.get("intensity", 0.5))
    if kind == "limiter":
        return limiter(data, p.get("ceiling_db", -0.3), sample_rate=sample_rate)
    LOG.warning("Неизвестный аудиоэффект: %s", kind)
    return data


#: Метаданные эффектов: подписи, параметры и их диапазоны (для UI).
EFFECT_SPECS: Dict[str, Dict[str, object]] = {
    "eq": {"label": "Эквалайзер 3 полосы", "params": {
        "low_db": ("Низы", -18.0, 18.0, 0.0),
        "mid_db": ("Середина", -18.0, 18.0, 0.0),
        "high_db": ("Верха", -18.0, 18.0, 0.0),
        "low_freq": ("Граница низов", 60.0, 600.0, 200.0),
        "high_freq": ("Граница верхов", 2000.0, 12000.0, 4000.0)}},
    "reverb": {"label": "Реверберация", "params": {
        "mix": ("Микс", 0.0, 1.0, 0.25),
        "decay": ("Затухание, с", 0.1, 4.0, 0.6),
        "room": ("Помещение", 0.0, 1.0, 0.5),
        "damping": ("Демпфирование", 0.0, 1.0, 0.4),
        "predelay_ms": ("Predelay, мс", 0.0, 120.0, 12.0)}},
    "delay": {"label": "Дилей", "params": {
        "time_ms": ("Время, мс", 20.0, 1200.0, 250.0),
        "feedback": ("Обратная связь", 0.0, 0.95, 0.35),
        "mix": ("Микс", 0.0, 1.0, 0.25),
        "high_cut": ("Срез верхов, Гц", 1000.0, 16000.0, 6000.0),
        "ping_pong": ("Ping-pong", 0.0, 1.0, 0.0)}},
    "compressor": {"label": "Компрессор", "params": {
        "threshold_db": ("Порог, dB", -60.0, 0.0, -18.0),
        "ratio": ("Ratio", 1.0, 20.0, 3.0),
        "attack_ms": ("Атака, мс", 0.5, 200.0, 12.0),
        "release_ms": ("Release, мс", 20.0, 1000.0, 180.0),
        "makeup_db": ("Makeup, dB", 0.0, 24.0, 0.0),
        "knee_db": ("Knee, dB", 0.0, 24.0, 6.0)}},
    "pitch": {"label": "Pitch Shift", "params": {
        "semitones": ("Полутона", -24.0, 24.0, 0.0)}},
    "stretch": {"label": "Time Stretch", "params": {
        "rate": ("Скорость", 0.25, 4.0, 1.0),
        "preserve_pitch": ("Сохранять тон", 0.0, 1.0, 1.0)}},
    "distortion": {"label": "Дисторшн", "params": {
        "drive": ("Драйв", 1.0, 20.0, 2.0),
        "mix": ("Микс", 0.0, 1.0, 0.5),
        "tone": ("Тон", 0.0, 1.0, 0.6),
        "output_db": ("Выход, dB", -24.0, 0.0, -1.0)}},
    "chorus": {"label": "Хор", "params": {
        "depth_ms": ("Глубина, мс", 1.0, 40.0, 12.0),
        "rate_hz": ("Частота, Гц", 0.05, 6.0, 0.6),
        "mix": ("Микс", 0.0, 1.0, 0.35),
        "voices": ("Голоса", 1.0, 4.0, 2.0)}},
    "deesser": {"label": "Де-эссер", "params": {
        "intensity": ("Интенсивность", 0.0, 1.0, 0.5)}},
    "limiter": {"label": "Лимитер", "params": {
        "ceiling_db": ("Потолок, dB", -12.0, 0.0, -0.3)}},
    "normalize": {"label": "Нормализация", "params": {
        "target_db": ("Целевой пик, dB", -12.0, 0.0, -1.0)}},
    "reverse": {"label": "Реверс", "params": {}},
    "fade": {"label": "Fade In/Out", "params": {
        "fade_in": ("Fade in, с", 0.0, 10.0, 0.5),
        "fade_out": ("Fade out, с", 0.0, 10.0, 0.5)}},
}

#: Готовые пресеты: имя → список эффектов.
FX_PRESETS: Dict[str, List[AudioEffect]] = {
    "Vocal Warm": [
        AudioEffect("eq", {"low_db": 2.5, "mid_db": -1.5, "high_db": 1.5,
                           "low_freq": 180.0, "high_freq": 6000.0}, name="EQ"),
        AudioEffect("compressor", {"threshold_db": -20.0, "ratio": 3.5,
                                   "attack_ms": 15.0, "release_ms": 160.0,
                                   "makeup_db": 3.0}, name="Comp"),
        AudioEffect("reverb", {"mix": 0.18, "decay": 0.5, "room": 0.35}, name="Reverb"),
        AudioEffect("deesser", {"intensity": 0.4}, name="De-esser"),
    ],
    "Bass Boost": [
        AudioEffect("eq", {"low_db": 7.0, "mid_db": -1.0, "high_db": 0.0,
                           "low_freq": 140.0}, name="EQ"),
        AudioEffect("compressor", {"threshold_db": -16.0, "ratio": 4.0,
                                   "attack_ms": 25.0, "release_ms": 240.0}, name="Comp"),
        AudioEffect("limiter", {"ceiling_db": -0.5}, name="Limiter"),
    ],
    "Lo-Fi": [
        AudioEffect("eq", {"low_db": 2.0, "mid_db": 1.0, "high_db": -9.0,
                           "high_freq": 3500.0}, name="EQ"),
        AudioEffect("distortion", {"drive": 1.6, "mix": 0.25, "tone": 0.3}, name="Saturation"),
        AudioEffect("chorus", {"depth_ms": 18.0, "rate_hz": 0.35, "mix": 0.28}, name="Chorus"),
        AudioEffect("reverb", {"mix": 0.12, "decay": 0.35, "damping": 0.7}, name="Reverb"),
    ],
    "Radio": [
        AudioEffect("eq", {"low_db": -12.0, "mid_db": 6.0, "high_db": -6.0,
                           "low_freq": 300.0, "high_freq": 3200.0}, name="EQ"),
        AudioEffect("compressor", {"threshold_db": -24.0, "ratio": 8.0,
                                   "attack_ms": 5.0, "release_ms": 90.0,
                                   "makeup_db": 6.0}, name="Comp"),
        AudioEffect("limiter", {"ceiling_db": -0.5}, name="Limiter"),
    ],
    "Stadium": [
        AudioEffect("eq", {"low_db": 1.5, "mid_db": -2.0, "high_db": 3.0}, name="EQ"),
        AudioEffect("delay", {"time_ms": 320.0, "feedback": 0.45, "mix": 0.22,
                              "ping_pong": 1.0}, name="Delay"),
        AudioEffect("reverb", {"mix": 0.38, "decay": 2.2, "room": 0.9,
                               "predelay_ms": 30.0}, name="Reverb"),
    ],
    "Night Drive": [
        AudioEffect("chorus", {"depth_ms": 9.0, "rate_hz": 0.8, "mix": 0.3}, name="Chorus"),
        AudioEffect("delay", {"time_ms": 420.0, "feedback": 0.3, "mix": 0.2}, name="Delay"),
        AudioEffect("reverb", {"mix": 0.28, "decay": 1.4, "damping": 0.5}, name="Reverb"),
    ],
}


class EffectChain:
    """Последовательная цепочка аудиоэффектов с кэшированием результата."""

    def __init__(self, effects: Optional[Sequence[AudioEffect]] = None) -> None:
        """Args:
            effects: Начальный список эффектов.
        """
        self.effects: List[AudioEffect] = list(effects or [])
        self._cache_key: Optional[str] = None
        self._cache: Optional[np.ndarray] = None

    # ------------------------------------------------------------ управление
    def add(self, effect: AudioEffect) -> None:
        """Добавить эффект в конец цепочки."""
        self.effects.append(effect)
        self.invalidate()

    def remove(self, index: int) -> None:
        """Удалить эффект по индексу."""
        if 0 <= index < len(self.effects):
            self.effects.pop(index)
            self.invalidate()

    def move(self, index: int, delta: int) -> None:
        """Переместить эффект в цепочке (для drag в UI)."""
        target = index + delta
        if 0 <= index < len(self.effects) and 0 <= target < len(self.effects):
            self.effects[index], self.effects[target] = self.effects[target], self.effects[index]
            self.invalidate()

    def clear(self) -> None:
        """Очистить цепочку."""
        self.effects.clear()
        self.invalidate()

    def set_bypass(self, index: int, bypass: bool) -> None:
        """Включить/выключить эффект."""
        if 0 <= index < len(self.effects):
            self.effects[index].bypass = bypass
            self.invalidate()

    def set_param(self, index: int, key: str, value: float) -> None:
        """Изменить параметр эффекта."""
        if 0 <= index < len(self.effects):
            self.effects[index].params[key] = float(value)
            self.invalidate()

    def invalidate(self) -> None:
        """Сбросить кэш (следующий process пересчитает)."""
        self._cache_key = None
        self._cache = None

    def load_preset(self, name: str) -> bool:
        """Загрузить пресет по имени.

        Args:
            name: Имя пресета из :data:`FX_PRESETS`.

        Returns:
            True, если пресет найден.
        """
        preset = FX_PRESETS.get(name)
        if preset is None:
            return False
        self.effects = [AudioEffect(e.kind, dict(e.params), e.bypass, e.name) for e in preset]
        self.invalidate()
        return True

    @property
    def bypassed_all(self) -> bool:
        """Все эффекты выключены (или цепочка пуста)."""
        return not self.effects or all(e.bypass for e in self.effects)

    # ------------------------------------------------------------ обработка
    def process(self, buffer: AudioBuffer) -> AudioBuffer:
        """Применить всю цепочку к буферу (с кэшем по параметрам)."""
        if self.bypassed_all:
            return buffer
        key = f"{id(buffer)}:{buffer.frames}:{self.signature()}"
        if self._cache_key == key and self._cache is not None:
            return AudioBuffer(self._cache, buffer.sample_rate, buffer.path)
        data = buffer.samples
        for effect in self.effects:
            if effect.bypass:
                continue
            data = _process(effect.kind, data, buffer.sample_rate, effect.params)
        self._cache_key = key
        self._cache = data
        return AudioBuffer(data, buffer.sample_rate, buffer.path)

    def signature(self) -> str:
        """Строка-отпечаток параметров (для кэша/undo)."""
        return "|".join(f"{e.kind}:{e.bypass}:{sorted(e.params.items())}" for e in self.effects)

    def to_list(self) -> List[Dict[str, object]]:
        """Сериализация цепочки."""
        return [e.to_dict() for e in self.effects]

    @classmethod
    def from_list(cls, items: Sequence[Dict[str, object]]) -> "EffectChain":
        """Восстановить цепочку из проекта."""
        return cls([AudioEffect.from_dict(item) for item in items])


def process_file(path: str, effects: Sequence[AudioEffect], out_path: str,
                 target_db: Optional[float] = None) -> str:
    """Применить эффекты к файлу и сохранить результат (WAV).

    Args:
        path: Исходный аудиофайл.
        effects: Список эффектов.
        out_path: Куда сохранить (WAV/FLAC через soundfile).
        target_db: Нормализовать результат до указанного пика.

    Returns:
        Путь к результату.
    """
    import soundfile as sf

    from core.audio_engine import load_audio

    cfg = get_config().audio
    buffer = load_audio(path, cfg.sample_rate, cfg.channels)
    data = buffer.samples
    for effect in effects:
        if effect.bypass:
            continue
        data = _process(effect.kind, data, cfg.sample_rate, effect.params)
    if target_db is not None:
        data = normalize(data, target_db)
    sf.write(out_path, data, cfg.sample_rate)
    return out_path
