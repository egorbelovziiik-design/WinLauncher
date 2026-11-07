"""Audio subsystem: Windows volume control, numpy synthesis and playback."""

from __future__ import annotations

from .music_player import MusicPlayer
from .volume_control import EqualizerState, VolumeControl

__all__ = ["MusicPlayer", "VolumeControl", "EqualizerState"]
