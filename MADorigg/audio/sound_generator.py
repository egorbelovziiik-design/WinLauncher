"""Procedural sound design for MADorigg.

Everything you hear is synthesised at runtime with numpy - the project ships
zero ``.wav``/``.mp3`` files.  All generators return **mono float64** arrays in
``[-1, 1]``; :func:`build_sfx_library` assembles the whole sound bank and the
music player converts the buffers to 16-bit stereo pygame sounds.
"""

from __future__ import annotations

import math
from typing import Callable, Dict, Iterable, Optional, Tuple

import numpy as np

from config import BASS_BOOST_DB, MUSIC_BPM, SAMPLE_RATE, TREBLE_BOOST_DB

SR: int = SAMPLE_RATE
RNG: np.random.Generator = np.random.default_rng(20241107)

# --------------------------------------------------------------------------- #
# DSP helpers
# --------------------------------------------------------------------------- #


def _t(duration: float, sr: int = SR) -> np.ndarray:
    """Return a time axis for ``duration`` seconds."""
    return np.arange(int(sr * duration), dtype=np.float64) / sr


def _noise(n: int, rng: np.random.Generator = RNG) -> np.ndarray:
    """White noise in ``[-1, 1]``."""
    return rng.standard_normal(n) * 0.5


def _decay(t: np.ndarray, rate: float, attack: float = 0.004) -> np.ndarray:
    """Fast attack + exponential decay envelope."""
    return np.minimum(1.0, t / max(attack, 1e-5)) * np.exp(-t * rate)


def _sweep(f0: float, f1: float, t: np.ndarray) -> np.ndarray:
    """Sinusoid whose frequency glides exponentially from ``f0`` to ``f1``."""
    if len(t) == 0:
        return np.zeros(0)
    duration = max(t[-1], 1e-6)
    freq = f0 * (f1 / f0) ** (t / duration)
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase)


def _fft_filter(
    signal: np.ndarray,
    low: Optional[float] = None,
    high: Optional[float] = None,
    sr: int = SR,
    order: float = 2.0,
) -> np.ndarray:
    """Cheap zero-phase low/high-pass implemented in the frequency domain."""
    if signal.size == 0:
        return signal
    spectrum = np.fft.rfft(signal)
    freqs = np.fft.rfftfreq(signal.size, 1.0 / sr)
    freqs = np.maximum(freqs, 1e-6)
    gain = np.ones_like(freqs)
    if low is not None:
        gain *= 1.0 / np.sqrt(1.0 + (low / freqs) ** (2.0 * order))
    if high is not None:
        gain *= 1.0 / np.sqrt(1.0 + (freqs / high) ** (2.0 * order))
    return np.fft.irfft(spectrum * gain, signal.size)


def _normalize(signal: np.ndarray, peak: float = 0.95) -> np.ndarray:
    """Scale a buffer so its loudest sample equals ``peak``."""
    if signal.size == 0:
        return signal
    maximum = float(np.max(np.abs(signal)))
    if maximum < 1e-9:
        return signal
    return signal * (peak / maximum)


def _soft_clip(signal: np.ndarray) -> np.ndarray:
    """Gentle saturation that keeps peaks inside ``[-1, 1]``."""
    return np.tanh(signal * 1.15) * 0.95


def apply_eq(
    signal: np.ndarray,
    bass_db: float = BASS_BOOST_DB,
    treble_db: float = TREBLE_BOOST_DB,
    sr: int = SR,
) -> np.ndarray:
    """Apply the MADorigg loudness curve to a mono or stereo buffer.

    ``bass_db`` is a low shelf around 200 Hz, ``treble_db`` a high shelf around
    4 kHz.  The result is soft-clipped and normalised so it never distorts.
    """
    if signal.size == 0:
        return signal
    mono = signal.ndim == 1
    data = signal[:, None] if mono else signal
    n = data.shape[0]
    spectrum = np.fft.rfft(data, axis=0)
    freqs = np.fft.rfftfreq(n, 1.0 / sr)[:, None]
    low = 1.0 / (1.0 + (freqs / 200.0) ** 2)
    high = (freqs / 4000.0) ** 2 / (1.0 + (freqs / 4000.0) ** 2)
    gain = 1.0 + (10.0 ** (bass_db / 20.0) - 1.0) * low
    gain += (10.0 ** (treble_db / 20.0) - 1.0) * high
    shaped = np.fft.irfft(spectrum * gain, n, axis=0)
    if float(np.max(np.abs(shaped))) > 1.0:
        shaped = _soft_clip(shaped)
    shaped = _normalize(shaped, 0.95)
    return shaped[:, 0] if mono else shaped


def to_stereo(mono: np.ndarray, pan: float = 0.0) -> np.ndarray:
    """Convert a mono buffer to a 2-channel float array (``pan``: -1..1)."""
    left = math.sqrt(max(0.0, 0.5 * (1.0 - pan)))
    right = math.sqrt(max(0.0, 0.5 * (1.0 + pan)))
    return np.stack([mono * left, mono * right], axis=1)


def to_int16(stereo: np.ndarray) -> np.ndarray:
    """Convert float stereo samples to the int16 layout pygame expects."""
    data = np.clip(stereo, -1.0, 1.0)
    return np.ascontiguousarray((data * 32767.0).astype(np.int16))


# --------------------------------------------------------------------------- #
# Percussion
# --------------------------------------------------------------------------- #


def generate_kick(duration: float = 0.38) -> np.ndarray:
    """Punchy 808-style kick."""
    t = _t(duration)
    freq = 150.0 * np.exp(-t * 22.0) + 42.0
    body = np.sin(2.0 * np.pi * np.cumsum(freq) / SR)
    click = _noise(len(t)) * np.exp(-t * 220.0) * 0.5
    return _normalize(body * _decay(t, 11.0) + click)


def generate_snare(duration: float = 0.3) -> np.ndarray:
    """Bright snare = filtered noise + 190 Hz body."""
    t = _t(duration)
    noise = _fft_filter(_noise(len(t)), low=220.0, high=7500.0)
    body = np.sin(2.0 * np.pi * 190.0 * t) * 0.35
    return _normalize((noise * 0.9 + body) * _decay(t, 16.0))


def generate_hihat(duration: float = 0.12, open_hat: bool = False) -> np.ndarray:
    """Metallic hi-hat (closed by default)."""
    t = _t(duration)
    noise = _fft_filter(_noise(len(t)), low=6000.0)
    return _normalize(noise * _decay(t, 60.0 if not open_hat else 16.0), 0.55)


# --------------------------------------------------------------------------- #
# Weapons / impact
# --------------------------------------------------------------------------- #


def generate_explosion(duration: float = 1.3) -> np.ndarray:
    """Big cinematic explosion: noise burst + falling sub-bass."""
    t = _t(duration)
    rumble = _sweep(120.0, 28.0, t) * np.exp(-t * 3.2)
    noise = _fft_filter(_noise(len(t)), high=1800.0) * np.exp(-t * 4.0)
    crack = _noise(len(t)) * np.exp(-t * 45.0) * 0.7
    return _normalize(rumble * 0.9 + noise * 0.8 + crack, 0.98)


def generate_hit(duration: float = 0.16) -> np.ndarray:
    """Short crunchy impact for soldier-vs-soldier collisions."""
    t = _t(duration)
    body = _sweep(320.0, 90.0, t) * np.exp(-t * 40.0)
    noise = _fft_filter(_noise(len(t)), high=5200.0) * np.exp(-t * 55.0)
    return _normalize(body + noise * 0.8, 0.85)


def generate_sword(duration: float = 0.35) -> np.ndarray:
    """Metallic clang of colliding swords."""
    t = _t(duration)
    partials = np.zeros(len(t))
    for freq, decay in ((1180.0, 26.0), (1830.0, 34.0), (2670.0, 45.0), (3900.0, 60.0)):
        partials += np.sin(2.0 * np.pi * freq * t) * np.exp(-t * decay)
    sparkle = _fft_filter(_noise(len(t)), low=3500.0) * np.exp(-t * 60.0)
    return _normalize(partials * 0.5 + sparkle * 0.6, 0.9)


def generate_gong(duration: float = 2.8) -> np.ndarray:
    """Epic inharmonic gong used for the boss reveal."""
    t = _t(duration)
    out = np.zeros(len(t))
    for freq, gain, rate in (
        (72.0, 1.0, 1.1),
        (108.0, 0.7, 1.4),
        (163.0, 0.5, 1.8),
        (241.0, 0.35, 2.2),
        (362.0, 0.22, 2.8),
        (544.0, 0.15, 3.4),
    ):
        out += np.sin(2.0 * np.pi * freq * t + freq * 0.01) * gain * np.exp(-t * rate)
    out *= np.minimum(1.0, t / 0.02)
    return _normalize(out * 0.9, 0.95)


def generate_mega_boom(duration: float = 2.6) -> np.ndarray:
    """Final hit: sub drop + noise + fake reverb tail."""
    t = _t(duration)
    sub = _sweep(90.0, 22.0, t) * np.exp(-t * 1.4)
    blast = _fft_filter(_noise(len(t)), high=2600.0) * np.exp(-t * 2.6)
    tail = _fft_filter(_noise(len(t)), high=900.0) * np.exp(-t * 1.1) * 0.5
    return _normalize(sub + blast * 0.9 + tail, 0.99)


def generate_thunder(duration: float = 1.8) -> np.ndarray:
    """Crack + rolling thunder for the lightning divider."""
    t = _t(duration)
    crack = _fft_filter(_noise(len(t)), low=1500.0) * np.exp(-t * 18.0)
    roll = _fft_filter(_noise(len(t)), high=420.0) * np.exp(-t * 2.0)
    return _normalize(crack + roll * 0.8, 0.9)


def generate_whoosh(duration: float = 0.55) -> np.ndarray:
    """Air sweep for beams and projectiles."""
    t = _t(duration)
    sweep = _fft_filter(_noise(len(t)), low=400.0, high=6000.0)
    env = np.sin(np.pi * np.clip(t / max(duration, 1e-6), 0.0, 1.0)) ** 2
    return _normalize(sweep * env, 0.75)


# --------------------------------------------------------------------------- #
# Elements / ambience
# --------------------------------------------------------------------------- #


def generate_fire(duration: float = 1.0) -> np.ndarray:
    """Roaring flame: low-passed noise with a slow wobble."""
    t = _t(duration)
    noise = _fft_filter(_noise(len(t)), high=1300.0)
    wobble = 0.65 + 0.35 * np.sin(2.0 * np.pi * 4.5 * t + 1.3)
    crackle = (_noise(len(t)) > 2.4).astype(np.float64)
    crackle = _fft_filter(crackle, high=5000.0) * 0.5
    return _normalize((noise * wobble + crackle) * _decay(t, 2.4), 0.85)


def generate_ice(duration: float = 0.8) -> np.ndarray:
    """Crystalline freeze: high shimmer + cracking ice."""
    t = _t(duration)
    shimmer = np.zeros(len(t))
    for freq, gain in ((2400.0, 1.0), (3620.0, 0.6), (5100.0, 0.4), (7200.0, 0.25)):
        shimmer += np.sin(2.0 * np.pi * freq * t) * gain
    shimmer *= np.exp(-t * 5.0)
    cracks = _fft_filter((_noise(len(t)) > 1.9).astype(np.float64), low=1800.0) * np.exp(-t * 10.0)
    air = _fft_filter(_noise(len(t)), low=4000.0) * 0.3
    return _normalize(shimmer * 0.5 + cracks + air, 0.85)


def generate_siren(duration: float = 1.4) -> np.ndarray:
    """Air-raid siren used by the intro build-up."""
    t = _t(duration)
    mod = 0.5 + 0.5 * np.sin(2.0 * np.pi * 1.6 * t)
    freq = 520.0 + 640.0 * mod
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    tone = np.sin(phase) + 0.35 * np.sin(phase * 2.0)
    return _normalize(tone * np.minimum(1.0, t / 0.08) * np.exp(-t * 1.2), 0.8)


def generate_crowd(duration: float = 2.4) -> np.ndarray:
    """Stadium roar: band-limited noise with random swells."""
    t = _t(duration)
    base = _fft_filter(_noise(len(t)), low=280.0, high=3200.0)
    swell = 0.55 + 0.45 * np.sin(2.0 * np.pi * 0.8 * t - 0.6)
    chatter = _fft_filter(_noise(len(t)), low=700.0, high=2600.0) * 0.25
    env = np.minimum(1.0, t / 0.35) * np.minimum(1.0, (duration - t) / 0.6)
    return _normalize((base * swell + chatter) * np.clip(env, 0.0, 1.0), 0.85)


def generate_scream(duration: float = 0.6) -> np.ndarray:
    """Battle cry: gliding saw with vibrato."""
    t = _t(duration)
    freq = 780.0 * np.exp(-t * 2.2) + 180.0
    freq *= 1.0 + 0.03 * np.sin(2.0 * np.pi * 7.0 * t)
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    saw = 2.0 * ((phase / (2.0 * np.pi)) % 1.0) - 1.0
    return _normalize(saw * _decay(t, 3.5) * 0.6, 0.7)


# --------------------------------------------------------------------------- #
# Musical pieces
# --------------------------------------------------------------------------- #


def generate_bass(freq: float = 55.0, duration: float = 0.7) -> np.ndarray:
    """Deep saw-ish bass note."""
    t = _t(duration)
    wave = (
        np.sin(2.0 * np.pi * freq * t)
        + 0.45 * np.sin(2.0 * np.pi * freq * 2.0 * t)
        + 0.22 * np.sin(2.0 * np.pi * freq * 3.0 * t)
    )
    return _normalize(wave * _decay(t, 3.0), 0.9)


def _lead_note(freq: float, duration: float) -> np.ndarray:
    """Detuned saw lead with a brassy envelope."""
    t = _t(duration)
    out = np.zeros(len(t))
    for detune in (1.0, 1.005, 0.995):
        phase = 2.0 * np.pi * freq * detune * t
        out += 2.0 * ((phase / (2.0 * np.pi)) % 1.0) - 1.0
    out = _fft_filter(out / 3.0, high=4800.0)
    env = np.minimum(1.0, t / 0.012) * np.exp(-t * 3.2)
    return _normalize(out * env, 0.7)


def _pad_chord(freqs: Iterable[float], duration: float) -> np.ndarray:
    """Soft sustained chord bed."""
    t = _t(duration)
    out = np.zeros(len(t))
    for freq in freqs:
        for detune in (1.0, 1.003):
            phase = 2.0 * np.pi * freq * detune * t
            out += 2.0 * ((phase / (2.0 * np.pi)) % 1.0) - 1.0
    out = _fft_filter(out / max(1, len(tuple(freqs)) * 2), high=1800.0)
    env = np.minimum(1.0, t / 0.35) * np.minimum(1.0, (duration - t) / 0.4)
    return out * np.clip(env, 0.0, 1.0)


def _semitones(base: float, steps: float) -> float:
    """Transpose ``base`` by ``steps`` semitones."""
    return base * (2.0 ** (steps / 12.0))


def generate_battle_theme(
    bpm: int = MUSIC_BPM, bars: int = 8, seed: int = 4242
) -> np.ndarray:
    """Loopable epic battle theme in A minor (drums + bass + lead + pads)."""
    rng = np.random.default_rng(seed)
    beat = 60.0 / bpm
    step = beat / 4.0
    total = bars * 4.0 * beat + 0.05
    buffer = np.zeros(int(total * SR) + 1)

    def add(signal: np.ndarray, at: float, gain: float = 1.0) -> None:
        index = int(at * SR)
        if index >= len(buffer):
            return
        end = min(len(buffer), index + len(signal))
        buffer[index:end] += signal[: end - index] * gain

    kick = generate_kick()
    snare = generate_snare()
    hat = generate_hihat()

    # A minor: Am - F - C - G, roots in the low register.
    roots = (55.0, 43.65, 65.41, 49.0)
    chords = (
        (220.0, 261.63, 329.63),  # A3 C4 E4
        (174.61, 220.0, 261.63),  # F3 A3 C4
        (196.0, 261.63, 329.63),  # G3 C4 E4
        (196.0, 246.94, 293.66),  # G3 B3 D4
    )
    riff = (12, 15, 17, 19, 22, 19, 17, 15)
    riff_alt = (12, 15, 19, 22, 24, 22, 19, 15)

    for bar in range(bars):
        bar_start = bar * 4.0 * beat
        root = roots[bar % 4]

        # --- drums ------------------------------------------------------- #
        add(kick, bar_start, 1.0)
        add(kick, bar_start + 2.0 * beat, 0.95)
        add(kick, bar_start + 2.5 * beat, 0.6)
        add(snare, bar_start + 1.0 * beat, 0.62)
        add(snare, bar_start + 3.0 * beat, 0.62)
        if bar % 2 == 1:
            add(snare, bar_start + 3.75 * beat, 0.3)
        for step_index in range(16):
            gain = 0.16 if step_index % 2 else 0.09
            if step_index % 4 == 2:
                gain *= 1.4
            add(hat, bar_start + step_index * step, gain)

        # --- bass -------------------------------------------------------- #
        for eighth in range(8):
            at = bar_start + eighth * beat / 2.0
            freq = root * (2.0 if eighth in (3, 7) else 1.0)
            add(generate_bass(freq, beat * 0.46), at, 0.55)

        # --- lead -------------------------------------------------------- #
        melody = riff if bar % 2 == 0 else riff_alt
        for eighth, semis in enumerate(melody):
            at = bar_start + eighth * beat / 2.0
            add(_lead_note(_semitones(440.0, semis), beat * 0.42), at, 0.35)

        # --- pad --------------------------------------------------------- #
        add(_pad_chord(chords[bar % 4], 4.0 * beat), bar_start, 0.16)

    # A couple of crowd screams for flavour.
    add(generate_scream(0.5), 2.0 * beat, 0.18)
    add(generate_scream(0.45), (4.0 * 6 + 2.0) * beat, 0.16)
    buffer += rng.standard_normal(len(buffer)) * 0.0015  # tape hiss

    # Loop-safe fades (10 ms) and master shaping.
    fade = int(0.01 * SR)
    buffer[:fade] *= np.linspace(0.0, 1.0, fade)
    buffer[-fade:] *= np.linspace(1.0, 0.0, fade)
    return _normalize(_soft_clip(buffer), 0.92)


def generate_fanfare(duration: float = 2.6) -> np.ndarray:
    """Victory fanfare: rising major arpeggio with brass stack."""
    t = _t(duration)
    out = np.zeros(len(t))
    notes = ((0, 523.25), (0.18, 659.25), (0.36, 783.99), (0.55, 1046.5))
    for offset, freq in notes:
        start = int(offset * SR)
        seg_t = _t(duration - offset)
        tone = (
            np.sin(2.0 * np.pi * freq * seg_t)
            + 0.5 * np.sin(2.0 * np.pi * freq * 2.0 * seg_t)
            + 0.25 * np.sin(2.0 * np.pi * freq * 3.0 * seg_t)
        )
        env = np.minimum(1.0, seg_t / 0.01) * np.exp(-seg_t * 1.4)
        end = min(len(t), start + len(tone))
        out[start:end] += (tone * env)[: end - start]

    # Sustained C-major chord under the melody.
    for freq, gain in ((261.63, 0.5), (329.63, 0.45), (392.0, 0.4), (523.25, 0.3)):
        out += np.sin(2.0 * np.pi * freq * t) * gain * np.exp(-t * 1.1) * np.minimum(1.0, t / 0.4)

    return _normalize(_soft_clip(out), 0.92)


def generate_victory_theme(seed: int = 7) -> np.ndarray:
    """Short victory loop: fanfare, crowd and marching drums."""
    fanfare = generate_fanfare(3.0)
    crowd = generate_crowd(3.2)
    out = np.zeros(max(len(fanfare), len(crowd)) + SR)
    out[: len(fanfare)] += fanfare
    out[: len(crowd)] += crowd * 0.35
    beat = 60.0 / 132.0
    for i in range(6):
        start = int(i * beat * SR)
        kick = generate_kick() * 0.7
        end = min(len(out), start + len(kick))
        out[start:end] += kick[: end - start]
    rng = np.random.default_rng(seed)
    out += rng.standard_normal(len(out)) * 0.001
    return _normalize(_soft_clip(out), 0.9)


# --------------------------------------------------------------------------- #
# Library assembly
# --------------------------------------------------------------------------- #

SFX_BUILDERS: Dict[str, Callable[[], np.ndarray]] = {
    "kick": generate_kick,
    "snare": generate_snare,
    "hat": generate_hihat,
    "explosion": generate_explosion,
    "hit": generate_hit,
    "sword": generate_sword,
    "gong": generate_gong,
    "mega_boom": generate_mega_boom,
    "thunder": generate_thunder,
    "whoosh": generate_whoosh,
    "fire": generate_fire,
    "ice": generate_ice,
    "siren": generate_siren,
    "crowd": generate_crowd,
    "scream": generate_scream,
    "bass": generate_bass,
}


def build_sfx_library() -> Dict[str, np.ndarray]:
    """Render every one-shot effect.  Returns ``name -> mono float array``."""
    library: Dict[str, np.ndarray] = {}
    for name, builder in SFX_BUILDERS.items():
        try:
            library[name] = builder()
        except Exception:  # pragma: no cover - synthesis is deterministic
            library[name] = np.zeros(int(0.2 * SR))
    return library


def generate_all_music() -> Dict[str, np.ndarray]:
    """Render the two music loops used by the show."""
    return {
        "battle_theme": generate_battle_theme(),
        "victory_theme": generate_victory_theme(),
    }


def stereo_int16(mono: np.ndarray, pan: float = 0.0) -> np.ndarray:
    """One-shot helper: mono float -> int16 stereo buffer."""
    return to_int16(to_stereo(mono, pan))


__all__ = [
    "SR",
    "apply_eq",
    "build_sfx_library",
    "generate_all_music",
    "generate_bass",
    "generate_battle_theme",
    "generate_crowd",
    "generate_explosion",
    "generate_fanfare",
    "generate_fire",
    "generate_gong",
    "generate_hit",
    "generate_ice",
    "generate_kick",
    "generate_mega_boom",
    "generate_scream",
    "generate_siren",
    "generate_snare",
    "generate_thunder",
    "generate_victory_theme",
    "stereo_int16",
    "to_int16",
    "to_stereo",
]
