"""Central configuration for **MADorigg**.

Every magic number of the show lives here: timings, army tuning, colours and
audio levels.  Nothing in this module touches pygame, so it is safe to import
from anywhere (including tests and build scripts).
"""

from __future__ import annotations

from typing import Final, Tuple

# --------------------------------------------------------------------------- #
# Identity
# --------------------------------------------------------------------------- #
APP_NAME: Final[str] = "MADorigg"
APP_VERSION: Final[str] = "1.0.0"
WINDOW_TITLE: Final[str] = "MADorigg"
WATERMARK_TEXT: Final[str] = "MADorigg was here"

# --------------------------------------------------------------------------- #
# Windowing / main loop
# --------------------------------------------------------------------------- #
WINDOWED_SIZE: Final[Tuple[int, int]] = (1280, 720)
FPS: Final[int] = 60
MAX_DT: Final[float] = 0.05  # never simulate more than 50 ms in one frame

# --------------------------------------------------------------------------- #
# Phase durations (seconds, scaled by --time-scale at runtime)
# --------------------------------------------------------------------------- #
PHASE_INTRO_DURATION: Final[float] = 5.0
PHASE_BOSSES_DURATION: Final[float] = 3.0
PHASE_BATTLE_DURATION: Final[Tuple[float, float]] = (15.0, 22.0)
PHASE_FINALE_DURATION: Final[float] = 5.0
PHASE_DEATH_DURATION: Final[float] = 10.0
PHASE_CLEANUP_DURATION: Final[float] = 4.0

# --------------------------------------------------------------------------- #
# Armies
# --------------------------------------------------------------------------- #
SOLDIER_HEIGHT: Final[int] = 20
SOLDIER_SPEED: Final[Tuple[float, float]] = (110.0, 210.0)
SOLDIER_SPAWN_INTERVAL: Final[float] = 0.5
SOLDIER_SPAWN_BATCH: Final[Tuple[int, int]] = (6, 10)
MAX_SOLDIERS: Final[int] = 120  # per side
SOLDIER_ENGAGE_DISTANCE: Final[float] = 15.0
GROUND_RATIO: Final[float] = 0.86

# --------------------------------------------------------------------------- #
# Bosses
# --------------------------------------------------------------------------- #
BOSS_SIZE: Final[int] = 150
BOSS_FINAL_SIZE: Final[int] = 300
BOSS_ATTACK_COOLDOWN: Final[float] = 2.0
BOSS_Y_RATIO: Final[float] = 0.42
BOSS_X_RATIO: Final[float] = 0.17

# --------------------------------------------------------------------------- #
# Projectiles
# --------------------------------------------------------------------------- #
FIREBALL_SPEED: Final[float] = 620.0
FIREBALL_SIZE: Final[int] = 34
ICE_BEAM_SPEED: Final[float] = 720.0
ICE_BEAM_LENGTH: Final[int] = 260
PROJECTILE_LIFETIME: Final[float] = 4.0

# --------------------------------------------------------------------------- #
# Particles / effects
# --------------------------------------------------------------------------- #
MAX_PARTICLES: Final[int] = 1000
FRONT_LINE_LIMIT: Final[float] = 0.85
ESCAPE_HOLD_SECONDS: Final[float] = 2.0
HARD_LIMIT_SECONDS: Final[float] = 180.0
HITSTOP_SECONDS: Final[float] = 0.07
SLOWMO_FACTOR: Final[float] = 0.35
SLOWMO_SECONDS: Final[float] = 0.6
GLITCH_INTERVAL: Final[Tuple[float, float]] = (5.0, 8.0)
VIGNETTE_STRENGTH: Final[float] = 0.75

# --------------------------------------------------------------------------- #
# Audio
# --------------------------------------------------------------------------- #
SAMPLE_RATE: Final[int] = 44100
AUDIO_BUFFER: Final[int] = 1024
AUDIO_CHANNELS: Final[int] = 24
MASTER_VOLUME: Final[float] = 0.85
MUSIC_BPM: Final[int] = 160
BASS_BOOST_DB: Final[float] = 12.0
TREBLE_BOOST_DB: Final[float] = 6.0
SYSTEM_VOLUME_TARGET: Final[float] = 1.0
VOLUME_FADE_SECONDS: Final[float] = 1.0

# --------------------------------------------------------------------------- #
# Colours
# --------------------------------------------------------------------------- #
Color = Tuple[int, int, int]

BLACK: Final[Color] = (0, 0, 0)
WHITE: Final[Color] = (255, 255, 255)
GOLD: Final[Color] = (255, 215, 0)
ORANGE: Final[Color] = (255, 120, 20)
YELLOW: Final[Color] = (255, 236, 80)
CYAN: Final[Color] = (120, 220, 255)

RED_PRIMARY: Final[Color] = (255, 0, 0)
RED_BRIGHT: Final[Color] = (255, 70, 60)
RED_DARK: Final[Color] = (139, 0, 0)
RED_DEEP: Final[Color] = (48, 0, 0)

BLUE_PRIMARY: Final[Color] = (0, 102, 255)
BLUE_BRIGHT: Final[Color] = (80, 170, 255)
BLUE_DARK: Final[Color] = (0, 0, 139)
BLUE_DEEP: Final[Color] = (2, 10, 48)

FIRE_COLORS: Final[Tuple[Color, ...]] = (
    (255, 245, 200),
    (255, 190, 60),
    (255, 120, 25),
    (225, 45, 15),
    (120, 20, 8),
)
ICE_COLORS: Final[Tuple[Color, ...]] = (
    (255, 255, 255),
    (200, 245, 255),
    (120, 210, 255),
    (55, 145, 255),
    (25, 70, 190),
)
CONFETTI_COLORS: Final[Tuple[Color, ...]] = (
    GOLD,
    RED_BRIGHT,
    BLUE_BRIGHT,
    (0, 255, 140),
    (255, 0, 255),
    (255, 255, 255),
    (255, 140, 0),
)
SMOKE_COLORS: Final[Tuple[Color, ...]] = (
    (52, 52, 62),
    (38, 38, 46),
    (68, 68, 78),
)

# --------------------------------------------------------------------------- #
# Fonts
# --------------------------------------------------------------------------- #
FONT_CANDIDATES: Final[Tuple[str, ...]] = (
    "impact",
    "arialblack",
    "arialbd",
    "arial",
    "dejavusans",
    "liberationsans",
    "freesansbold",
)
