"""Генерация всех звуков приложения через numpy (без внешних файлов).

Каждая функция возвращает ``pygame.mixer.Sound``, собранный из
numpy-массива через ``pygame.sndarray.make_sound``.
"""

from __future__ import annotations

import numpy as np
import pygame

import config

_SR: int = int(config.SAMPLE_RATE)
_RNG = np.random.default_rng(20240210)  # детерминированная генерация


# ---------------------------------------------------------------------------
# Вспомогательные функции
# ---------------------------------------------------------------------------
def _to_sound(mono: np.ndarray, gain: float = 1.0) -> pygame.mixer.Sound:
    """Преобразовать mono float-массив [-1..1] в стерео pygame.Sound."""
    mono = np.asarray(mono, dtype=np.float64)
    mono = np.clip(mono, -1.0, 1.0)
    stereo = np.empty((mono.shape[0], 2), dtype=np.int16)
    pcm = (mono * 32767.0 * gain).astype(np.int16)
    stereo[:, 0] = pcm
    stereo[:, 1] = pcm
    return pygame.sndarray.make_sound(np.ascontiguousarray(stereo))


def _t(duration: float) -> np.ndarray:
    """Вектор времени длиной duration секунд."""
    return np.arange(int(duration * _SR)) / _SR


def _exp_decay(duration: float, rate: float) -> np.ndarray:
    """Показательное затухание."""
    return np.exp(-_t(duration) * rate)


def _lowpass(data: np.ndarray, cutoff: float) -> np.ndarray:
    """Простой однополюсный ФНЧ (cutoff — коэффициент 0..1)."""
    out = np.empty_like(data)
    acc = 0.0
    for i in range(data.shape[0]):
        acc += cutoff * (data[i] - acc)
        out[i] = acc
    return out


def _lowpass_fft(data: np.ndarray, cutoff_hz: float) -> np.ndarray:
    """Быстрый ФНЧ через FFT (для длинных сигналов)."""
    spectrum = np.fft.rfft(data)
    freqs = np.fft.rfftfreq(data.shape[0], d=1.0 / _SR)
    spectrum[freqs > cutoff_hz] *= 0.0
    return np.fft.irfft(spectrum, n=data.shape[0])


def _highpass_fft(data: np.ndarray, cutoff_hz: float) -> np.ndarray:
    """Быстрый ФВЧ через FFT."""
    spectrum = np.fft.rfft(data)
    freqs = np.fft.rfftfreq(data.shape[0], d=1.0 / _SR)
    spectrum[freqs < cutoff_hz] *= 0.0
    return np.fft.irfft(spectrum, n=data.shape[0])


def _saw(freq: float, t: np.ndarray) -> np.ndarray:
    """Пилообразная волна (гармонический синтез)."""
    out = np.zeros_like(t)
    k = 1
    while freq * k < _SR / 2 and k <= 12:
        out += np.sin(2.0 * np.pi * freq * k * t) / k
        k += 1
    return out


# ---------------------------------------------------------------------------
# Одиночные звуки
# ---------------------------------------------------------------------------
def generate_bass(freq: float = 55.0, duration: float = 0.5) -> pygame.mixer.Sound:
    """Низкий бас: синусоида + лёгкая гармоника, затухание."""
    t = _t(duration)
    signal = np.sin(2.0 * np.pi * freq * t)
    signal += 0.3 * np.sin(2.0 * np.pi * freq * 2 * t)
    envelope = np.exp(-t * 4.0)
    return _to_sound(signal * envelope)


def generate_kick() -> pygame.mixer.Sound:
    """Удар бочки: синус со скользящей частотой + затухание."""
    duration = 0.25
    t = _t(duration)
    freq_sweep = 150.0 * np.exp(-t * 30.0) + 45.0
    phase = 2.0 * np.pi * np.cumsum(freq_sweep) / _SR
    signal = np.sin(phase) * np.exp(-t * 12.0)
    click = _RNG.standard_normal(t.shape[0]) * np.exp(-t * 300.0) * 0.4
    return _to_sound(np.tanh((signal + click) * 1.5))


def generate_snare() -> pygame.mixer.Sound:
    """Снэйр: белый шум через ФВЧ + тональная составляющая."""
    duration = 0.2
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _highpass_fft(noise, 900.0)
    tone = np.sin(2.0 * np.pi * 190.0 * t) * 0.5
    envelope = np.exp(-t * 18.0)
    return _to_sound((noise * 0.8 + tone) * envelope)


def generate_hat() -> pygame.mixer.Sound:
    """Хай-хат: короткий шум через ФВЧ."""
    duration = 0.08
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _highpass_fft(noise, 5000.0)
    envelope = np.exp(-t * 60.0)
    return _to_sound(noise * envelope * 0.5)


def generate_explosion(duration: float = 1.2) -> pygame.mixer.Sound:
    """Взрыв: белый шум + ударный бас + затухание."""
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _lowpass_fft(noise, 2500.0)
    sweep_freq = 60.0 * np.exp(-t * 3.0) + 25.0
    phase = 2.0 * np.pi * np.cumsum(sweep_freq) / _SR
    sub = np.sin(phase) * np.exp(-t * 2.5)
    crackle = _RNG.standard_normal(t.shape[0]) * np.exp(-t * 25.0)
    crackle = _highpass_fft(crackle, 1200.0)
    signal = noise * np.exp(-t * 4.0) * 0.8 + sub * 1.0 + crackle * 0.5
    return _to_sound(np.tanh(signal * 1.4))


def generate_hit() -> pygame.mixer.Sound:
    """Короткий удар: шумовой всплеск + бас."""
    duration = 0.15
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _highpass_fft(noise, 500.0)
    body = np.sin(2.0 * np.pi * 90.0 * t)
    envelope = np.exp(-t * 40.0)
    return _to_sound((noise * 0.7 + body * 0.6) * envelope)


def generate_siren(duration: float = 2.0) -> pygame.mixer.Sound:
    """Сирена: синус с частотной модуляцией (лай-ла)."""
    t = _t(duration)
    base = 700.0
    lfo = np.sin(2.0 * np.pi * 2.5 * t) * 250.0
    freq = base + lfo
    phase = 2.0 * np.pi * np.cumsum(freq) / _SR
    signal = np.sin(phase) * 0.8
    signal += 0.2 * np.sin(phase * 2.0)
    return _to_sound(signal)


def generate_crowd(duration: float = 2.5) -> pygame.mixer.Sound:
    """Рёв толпы: шум через ФНЧ + амплитудная модуляция."""
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _lowpass_fft(noise, 1200.0)
    noise = _highpass_fft(noise, 250.0)
    amplitude = 0.6 + 0.4 * np.sin(2.0 * np.pi * 1.7 * t)
    envelope = np.minimum(t * 8.0, 1.0) * np.minimum((duration - t) * 3.0, 1.0)
    return _to_sound(noise * amplitude * envelope * 0.7)


def generate_fanfare() -> pygame.mixer.Sound:
    """Фанфары: мажорный аккорд из пилообразных волн."""
    duration = 1.6
    t = _t(duration)
    notes = [261.63, 329.63, 392.00, 523.25]  # C E G C
    signal = np.zeros_like(t)
    for freq in notes:
        signal += _saw(freq, t) * 0.25
    envelope = np.minimum(t * 10.0, 1.0) * np.exp(-t * 1.5)
    tremolo = 1.0 + 0.15 * np.sin(2.0 * np.pi * 6.0 * t)
    return _to_sound(signal * envelope * tremolo)


def generate_fire(duration: float = 1.0) -> pygame.mixer.Sound:
    """Огонь: розовый шум + низкочастотная модуляция + треск."""
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _lowpass_fft(noise, 3000.0)
    rumble = np.sin(2.0 * np.pi * 40.0 * t) * 0.4
    lfo = 0.7 + 0.3 * np.sin(2.0 * np.pi * 7.0 * t)
    crackle = _RNG.standard_normal(t.shape[0])
    crackle = np.where(
        np.abs(crackle) > 2.8, crackle * 2.0, 0.0
    ) * np.exp(-t * 10.0)
    signal = noise * lfo * 0.5 + rumble * lfo + crackle * 0.3
    return _to_sound(signal)


def generate_ice(duration: float = 1.0) -> pygame.mixer.Sound:
    """Лёд: высокочастотный звон + кристаллические гармоники."""
    t = _t(duration)
    signal = np.zeros_like(t)
    for freq, amp in ((2400.0, 0.5), (3600.0, 0.3), (5200.0, 0.2), (1800.0, 0.25)):
        signal += np.sin(2.0 * np.pi * freq * t) * amp
    shimmer = _RNG.standard_normal(t.shape[0])
    shimmer = _highpass_fft(shimmer, 6000.0) * 0.3
    envelope = np.exp(-t * 5.0)
    return _to_sound((signal + shimmer) * envelope)


def generate_gong() -> pygame.mixer.Sound:
    """Эпичный гонг: многосоставной металлический удар."""
    duration = 2.5
    t = _t(duration)
    signal = np.zeros_like(t)
    partials = (110.0, 164.8, 220.0, 329.6, 440.0)
    for i, freq in enumerate(partials):
        decay = 1.2 + i * 0.5
        signal += (
            np.sin(2.0 * np.pi * freq * t)
            * np.exp(-t * decay)
            * (1.0 / (i + 1))
        )
    strike = _RNG.standard_normal(t.shape[0]) * np.exp(-t * 60.0) * 0.5
    return _to_sound(signal * 0.5 + strike)


def generate_mega_explosion() -> pygame.mixer.Sound:
    """Финальный мега-взрыв: бас + белый шум + имитация реверба."""
    duration = 2.0
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _lowpass_fft(noise, 1800.0)
    sweep = 90.0 * np.exp(-t * 1.8) + 22.0
    phase = 2.0 * np.pi * np.cumsum(sweep) / _SR
    sub = np.sin(phase) * np.exp(-t * 1.4)
    signal = noise * np.exp(-t * 3.0) * 0.9 + sub * 1.2
    # Имитация реверба: несколько копий с задержкой и затуханием.
    reverb = np.zeros_like(signal)
    for delay_ms, gain in ((80, 0.5), (160, 0.3), (260, 0.2), (400, 0.12)):
        shift = int(_SR * delay_ms / 1000)
        if shift < signal.shape[0]:
            reverb[shift:] += signal[:-shift] * gain
    return _to_sound(np.tanh((signal + reverb) * 1.3))


def generate_sword() -> pygame.mixer.Sound:
    """Удар мечей: металлический всплеск."""
    duration = 0.3
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    noise = _highpass_fft(noise, 3000.0)
    ring = sum(
        np.sin(2.0 * np.pi * f * t) * a
        for f, a in ((3200.0, 0.3), (4700.0, 0.2))
    )
    envelope = np.exp(-t * 20.0)
    return _to_sound((noise * 0.6 + ring) * envelope)


def generate_death() -> pygame.mixer.Sound:
    """Крик/всхлип падающего солдата: шум с глиссандо вниз."""
    duration = 0.4
    t = _t(duration)
    noise = _RNG.standard_normal(t.shape[0])
    cutoff = 2000.0 * np.exp(-t * 4.0) + 200.0
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(noise.shape[0], d=1.0 / _SR)
    # приблизительное затухание высоких частот со временем
    mask = np.ones_like(freqs)
    spectrum *= mask
    noise = np.fft.irfft(spectrum, n=noise.shape[0])
    tone_sweep = 400.0 * np.exp(-t * 5.0) + 80.0
    phase = 2.0 * np.pi * np.cumsum(tone_sweep) / _SR
    tone = np.sin(phase) * 0.4
    envelope = np.exp(-t * 7.0)
    _ = cutoff
    return _to_sound((noise * 0.3 + tone) * envelope)


# ---------------------------------------------------------------------------
# Эпичная музыка
# ---------------------------------------------------------------------------
def generate_epic_music(
    bpm: int = 160, duration: float = 20.0, bars: int = 0
) -> pygame.mixer.Sound:
    """Сгенерировать эпичный трек: kick + snare + hi-hat + бас + мелодия.

    Тональность — ля минор, темп ``bpm``. Паттерн повторяется каждые 4 такта.
    """
    beat = 60.0 / float(bpm)
    bar = beat * 4.0
    total_samples = int(duration * _SR)
    track = np.zeros(total_samples)

    def _add(sound: pygame.mixer.Sound, at: float, gain: float = 1.0) -> None:
        """Наложить pygame.Sound на трек в момент ``at`` секунд."""
        arr = pygame.sndarray.array(sound)[:, 0].astype(np.float64) / 32767.0
        start = int(at * _SR)
        if start >= total_samples:
            return
        end = min(start + arr.shape[0], total_samples)
        track[start:end] += arr[: end - start] * gain

    # Быстрые одноразовые семплы
    kick = generate_kick()
    snare = generate_snare()
    hat = generate_hat()

    # Ля минор: A2 F2 C3 G3 (i - VI - III - VII)
    bass_line = [110.00, 87.31, 130.81, 98.00]
    # Мелодия в ля миноре (ноты октавы выше)
    melody_notes = [
        440.00, 440.00, 523.25, 587.33,
        659.25, 587.33, 523.25, 440.00,
        392.00, 440.00, 523.25, 440.00,
        349.23, 392.00, 440.00, 440.00,
    ]

    n_beats = int(duration / beat) + 1
    melody_idx = 0

    for b in range(n_beats):
        at = b * beat
        if at >= duration:
            break

        # --- Ударные ---
        _add(kick, at, 1.0)
        if b % 4 in (1, 3):
            _add(snare, at, 0.8)
        # Восьмые хай-хаты
        _add(hat, at + beat * 0.5, 0.5)

        # --- Бас: каждые 2 удара новая нота ---
        if b % 2 == 0:
            bar_idx = int(at / bar) % 4
            bass_freq = bass_line[bar_idx]
            bass_dur = beat * 1.9
            t_bass = _t(bass_dur)
            bass_sig = (
                np.sin(2.0 * np.pi * bass_freq * t_bass)
                + 0.4 * np.sin(2.0 * np.pi * bass_freq * 2 * t_bass)
            ) * np.exp(-t_bass * 2.0)
            bass_sound = _to_sound(bass_sig)
            _add(bass_sound, at, 0.9)

        # --- Мелодия: каждую половину удара ---
        note_freq = melody_notes[melody_idx % len(melody_notes)]
        melody_idx += 1
        lead_dur = beat * 0.55
        t_lead = _t(lead_dur)
        lead_sig = (
            _saw(note_freq, t_lead) * 0.5
            + np.sin(2.0 * np.pi * note_freq * t_lead) * 0.5
        ) * np.exp(-t_lead * 5.0)
        lead_sound = _to_sound(lead_sig)
        _add(lead_sound, at + beat * 0.5, 0.45)

    # Нормализация и мягкий лимитер
    track = np.tanh(track * 0.9)
    peak = np.max(np.abs(track))
    if peak > 1e-9:
        track = track / peak * 0.95
    _ = bars
    return _to_sound(track)
