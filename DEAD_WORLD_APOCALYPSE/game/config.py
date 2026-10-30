"""Paths, settings and tuning constants. Pure stdlib - safe to import anywhere."""
import json, os, sys
from dataclasses import dataclass, asdict, field


def resource_root():
    """Root of game files both in dev and in a PyInstaller-frozen build."""
    if getattr(sys, "frozen", False):
        return getattr(sys, "_MEIPASS", os.path.dirname(sys.executable))
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def user_data_dir():
    if sys.platform.startswith("win"):
        base = os.environ.get("APPDATA") or os.path.expanduser("~")
    else:
        base = os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share")
    d = os.path.join(base, "DeadWorldApocalypse")
    os.makedirs(d, exist_ok=True)
    return d


ASSETS = os.path.join(resource_root(), "assets")


@dataclass
class Settings:
    resolution: str = "1280x720"
    fullscreen: bool = False
    quality: str = "medium"        # low / medium / high
    shadows: bool = True
    fog: bool = True
    particles: bool = True
    view_distance: float = 220.0    # meters, fog tuned from this
    mouse_sensitivity: float = 40.0
    master_volume: float = 0.8
    music_volume: float = 0.5
    sfx_volume: float = 0.9
    invert_y: bool = False
    language: str = "ru"

    def max_zombies(self):
        return {"low": 18, "medium": 28, "high": 40}.get(self.quality, 28)

    def particle_count(self):
        if not self.particles:
            return 0
        return {"low": 60, "medium": 130, "high": 220}.get(self.quality, 130)

    def res_tuple(self):
        try:
            w, h = self.resolution.lower().split("x")
            return int(w), int(h)
        except Exception:
            return (1280, 720)


SETTINGS_FILE = os.path.join(user_data_dir(), "settings.json")


def load_settings(path=None):
    path = path or SETTINGS_FILE
    s = Settings()
    if os.path.exists(path):
        try:
            data = json.load(open(path, encoding="utf-8"))
            for k, v in data.items():
                if hasattr(s, k):
                    setattr(s, k, v)
        except Exception:
            pass
    return s


def save_settings(s, path=None):
    path = path or SETTINGS_FILE
    with open(path, "w", encoding="utf-8") as f:
        json.dump(asdict(s), f, ensure_ascii=False, indent=1)
    return path


# --- world / balance tuning -------------------------------------------------
WORLD_SIZE = 360.0          # playable district, meters (x,z in [-180,180])
DAY_LENGTH_SEC = 720.0      # full 24h cycle in real seconds (12 min)
START_HOUR = 8.0
GRAVITY = 22.0
PLAYER_HEIGHT = 1.75
PLAYER_EYE = 1.62
AUTOSAVE_INTERVAL = 60.0
