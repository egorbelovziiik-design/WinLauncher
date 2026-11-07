"""Playback engine: pre-rendered numpy sounds on pygame mixer channels.

``MusicPlayer`` owns the mixer.  It renders the whole sound bank once (a few
hundred milliseconds), applies the +12 dB / +6 dB EQ curve, and then dispatches
one-shots with per-effect throttling so a hundred simultaneous collisions do
not turn into white noise.
"""

from __future__ import annotations

import logging
import time
from typing import Dict, Optional

import numpy as np
import pygame

from config import (
    AUDIO_BUFFER,
    AUDIO_CHANNELS,
    BASS_BOOST_DB,
    MASTER_VOLUME,
    SAMPLE_RATE,
    TREBLE_BOOST_DB,
)
from . import sound_generator as sg

LOG = logging.getLogger(__name__)


class MusicPlayer:
    """Music + SFX bus with a smoothly automatable master volume.

    Parameters
    ----------
    enabled:
        ``False`` disables all audio without failing (used by ``--no-audio``).
    master_volume:
        Initial gain of our own bus, independent of the OS volume.
    """

    MUSIC_CHANNEL = 0

    def __init__(
        self,
        enabled: bool = True,
        master_volume: float = MASTER_VOLUME,
        bass_db: float = BASS_BOOST_DB,
        treble_db: float = TREBLE_BOOST_DB,
    ) -> None:
        self.enabled = bool(enabled)
        self.master_volume = float(master_volume)
        self._master = float(master_volume)
        self._target = float(master_volume)
        self._fade_rate = 0.0
        self.bass_db = bass_db
        self.treble_db = treble_db

        self.sounds: Dict[str, pygame.mixer.Sound] = {}
        self._channels = []
        self._channel_base: Dict[int, float] = {}
        self._last_played: Dict[str, float] = {}
        self._music_name: Optional[str] = None
        self._music_gain = 0.9
        self._ready = False

        if not self.enabled:
            LOG.info("Audio disabled by configuration")
            return
        if not self._init_mixer():
            self.enabled = False
            return
        self._grab_channels()

    # ------------------------------------------------------------------ #
    # Setup
    # ------------------------------------------------------------------ #
    def _init_mixer(self) -> bool:
        """Initialise ``pygame.mixer`` (idempotent, never raises)."""
        try:
            if pygame.mixer.get_init() is None:
                pygame.mixer.init(
                    frequency=SAMPLE_RATE,
                    size=-16,
                    channels=2,
                    buffer=AUDIO_BUFFER,
                )
            pygame.mixer.set_num_channels(max(AUDIO_CHANNELS, 8))
            LOG.info("Mixer ready: %s", pygame.mixer.get_init())
            return True
        except Exception as exc:  # pragma: no cover - depends on the host
            LOG.warning("Mixer unavailable (%s) - running silent", exc)
            return False

    def _grab_channels(self) -> None:
        """Reserve all mixer channels for our own scheduling."""
        count = max(AUDIO_CHANNELS, 8)
        self._channels = [pygame.mixer.Channel(i) for i in range(count)]

    def _make_sound(self, mono: np.ndarray, gain: float = 1.0) -> Optional[pygame.mixer.Sound]:
        """Apply EQ, normalise and convert a mono buffer into a pygame Sound."""
        if mono is None or len(mono) == 0:
            return None
        try:
            shaped = sg.apply_eq(mono, self.bass_db, self.treble_db)
            stereo = sg.to_int16(sg.to_stereo(shaped * gain))
            return pygame.sndarray.make_sound(stereo)
        except Exception as exc:  # pragma: no cover - defensive
            LOG.warning("Could not create sound: %s", exc)
            return None

    def preload(self) -> None:
        """Render all effects and music loops.  Safe to call twice."""
        if not self.enabled or self._ready:
            return
        started = time.perf_counter()
        try:
            for name, mono in sg.build_sfx_library().items():
                sound = self._make_sound(mono)
                if sound is not None:
                    self.sounds[name] = sound
            for name, mono in sg.generate_all_music().items():
                sound = self._make_sound(mono, gain=0.9)
                if sound is not None:
                    self.sounds[name] = sound
            self._ready = True
            LOG.info(
                "Sound bank rendered: %d sounds in %.0f ms",
                len(self.sounds),
                (time.perf_counter() - started) * 1000.0,
            )
        except Exception as exc:  # pragma: no cover - defensive
            LOG.exception("Sound bank generation failed: %s", exc)
            self.enabled = False

    # ------------------------------------------------------------------ #
    # Playback
    # ------------------------------------------------------------------ #
    def play(self, name: str, volume: float = 1.0, cooldown: float = 0.035) -> None:
        """Play a one-shot effect with a small anti-machine-gun cooldown."""
        if not self.enabled or not self._ready:
            return
        sound = self.sounds.get(name)
        if sound is None:
            return
        now = time.perf_counter()
        if now - self._last_played.get(name, 0.0) < cooldown:
            return
        self._last_played[name] = now
        found = self._find_free_channel()
        if found is None:
            return
        index, channel = found
        base = max(0.0, min(1.0, volume))
        channel.set_volume(base * self._master)
        channel.play(sound)
        self._channel_base[index] = base

    def _find_free_channel(self):
        """Prefer an idle channel, otherwise steal the oldest SFX channel."""
        for index, channel in enumerate(self._channels):
            if index == self.MUSIC_CHANNEL:
                continue
            if not channel.get_busy():
                return index, channel
        if len(self._channels) > 1:
            return 1, self._channels[1]
        return None

    def play_music(self, name: str = "battle_theme", loops: int = -1, volume: float = 0.9) -> None:
        """Start a looping music track on the dedicated music channel."""
        if not self.enabled or not self._ready:
            return
        sound = self.sounds.get(name)
        if sound is None:
            return
        channel = self._channels[self.MUSIC_CHANNEL] if self._channels else None
        if channel is None:
            return
        self._music_name = name
        self._music_gain = max(0.0, min(1.0, volume))
        channel.stop()
        try:
            channel.play(sound, loops=loops)
            channel.set_volume(self._music_gain * self._master)
        except Exception as exc:  # pragma: no cover - defensive
            LOG.warning("play_music failed: %s", exc)

    def stop_music(self, fade_ms: int = 600) -> None:
        """Fade the music channel out over ``fade_ms`` milliseconds."""
        if not self._channels:
            return
        try:
            self._channels[self.MUSIC_CHANNEL].fadeout(max(0, fade_ms))
        except Exception:  # pragma: no cover - defensive
            pass
        self._music_name = None

    def music_finished(self) -> bool:
        """True when the music channel is idle (used for track changes)."""
        if not self._channels:
            return True
        return not self._channels[self.MUSIC_CHANNEL].get_busy()

    # ------------------------------------------------------------------ #
    # Volume automation
    # ------------------------------------------------------------------ #
    def set_master_volume(self, volume: float) -> None:
        """Jump the bus volume to ``volume`` immediately."""
        self._master = max(0.0, min(1.0, volume))
        self._target = self._master
        self._fade_rate = 0.0
        self._apply_master()

    def fade_master(self, target: float, duration: float) -> None:
        """Linearly automate the bus volume over ``duration`` seconds."""
        target = max(0.0, min(1.0, target))
        if duration <= 0.0:
            self.set_master_volume(target)
            return
        self._target = target
        self._fade_rate = abs(target - self._master) / duration

    def update(self, dt: float) -> None:
        """Advance the volume automation (call once per frame)."""
        if self._fade_rate <= 0.0:
            return
        step = self._fade_rate * dt
        if abs(self._target - self._master) <= step:
            self._master = self._target
            self._fade_rate = 0.0
        else:
            self._master += step if self._target > self._master else -step
        self._apply_master()

    def _apply_master(self) -> None:
        """Push the current master volume into every live channel."""
        if not self._channels:
            return
        try:
            if self._music_name:
                self._channels[self.MUSIC_CHANNEL].set_volume(self._music_gain * self._master)
            for index, channel in enumerate(self._channels):
                if index == self.MUSIC_CHANNEL or not channel.get_busy():
                    continue
                base = self._channel_base.get(index, 1.0)
                channel.set_volume(base * self._master)
        except Exception:  # pragma: no cover - defensive
            pass

    # ------------------------------------------------------------------ #
    # Teardown
    # ------------------------------------------------------------------ #
    def shutdown(self) -> None:
        """Stop everything and release the mixer."""
        if not self.enabled:
            return
        try:
            for channel in self._channels:
                channel.stop()
            pygame.mixer.stop()
            pygame.mixer.quit()
        except Exception:  # pragma: no cover - defensive
            pass
        self._ready = False
