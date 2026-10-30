"""Audio manager: loops (wind/rain/music/engine), per-clip voice pools,
distance-attenuated 3D-ish playback. All clips are our own synthesized WAVs."""
import math, random
from ursina import Audio

try:
    Audio.volume_multiplier = 1.0
except Exception:
    pass

CLIPS = ["shoot_pistol", "shoot_rifle", "shoot_sniper", "shoot_shotgun", "dry_fire",
         "reload", "melee_swing", "melee_hit", "hit_flesh", "zombie_groan1",
         "zombie_groan2", "zombie_scream", "zombie_attack", "zombie_death",
         "player_hurt", "step_grass", "step_hard", "pickup", "eat", "drink",
         "door", "ui_click", "heartbeat", "thunder", "car_hit", "quest_done",
         "death_sting"]
VOICES_PER_CLIP = 3


class AudioManager:
    def __init__(self, settings, assets_path=None):
        self.settings = settings
        self.assets_path = assets_path
        self.rng = random.Random()
        self.voices = {}          # name -> [Audio, ...]
        self.voice_idx = {}
        self.loops = {}
        for name in ("wind", "rain", "music_ambient", "engine"):
            try:
                self.loops[name] = self._make(name, loop=True, autoplay=False, volume=0)
            except Exception:
                pass
        self._started = set()

    def _vol(self, base):
        return max(0.0, min(1.0, base * self.settings.sfx_volume * self.settings.master_volume))

    def _make(self, name, **kw):
        """Create an Audio by searching only the (small) assets tree.
        Ursina's clip loader globs '**/name' under asset_folder; with the
        asset folder pointed at the whole PyInstaller bundle that walk is
        very slow on Windows, so we temporarily point it at assets/."""
        if not self.assets_path:
            return Audio(f"assets/audio/{name}.wav", **kw)
        from pathlib import Path
        from ursina import application
        old = application.asset_folder
        try:
            application.asset_folder = Path(self.assets_path)
            return Audio(f"audio/{name}.wav", **kw)
        finally:
            application.asset_folder = old

    def _voice(self, name):
        lst = self.voices.setdefault(name, [])
        if len(lst) < VOICES_PER_CLIP:
            lst.append(self._make(name, autoplay=False, volume=0))
            return lst[-1]
        i = self.voice_idx.get(name, 0)
        self.voice_idx[name] = (i + 1) % len(lst)
        return lst[i]

    def play(self, name, volume=0.8, pitch_var=0.06):
        if name not in CLIPS:
            return
        try:
            a = self._voice(name)
            a.volume = self._vol(volume)
            a.pitch = 1.0 + self.rng.uniform(-pitch_var, pitch_var)
            a.stop()
            a.play()
        except Exception:
            pass

    def play_at(self, name, pos, listener_pos, volume=0.9, max_dist=45.0):
        d = math.dist(pos, listener_pos) if listener_pos else 999
        if d > max_dist:
            return
        att = max(0.0, 1.0 - d / max_dist) ** 1.4
        self.play(name, volume * att)

    def set_loop(self, name, volume, pitch=1.0):
        a = self.loops.get(name)
        if not a:
            return
        base = self.settings.music_volume if name == "music_ambient" else self.settings.sfx_volume
        try:
            a.volume = max(0.0, min(1.0, volume * base * self.settings.master_volume))
            a.pitch = pitch
            if name not in self._started:
                a.play()
                self._started.add(name)
        except Exception:
            pass

    def stop_all_loops(self):
        for a in self.loops.values():
            try:
                a.stop()
            except Exception:
                pass
        self._started.clear()
