"""DEAD WORLD: APOCALYPSE - entry point.

  python main.py            run the game
  python main.py --selftest headless logic/asset self-check (used by CI and the Windows build smoke test)
  python main.py --smoke    build the whole game world headless, simulate a few
                            seconds of gameplay, then exit (integration smoke)
  python main.py --guitest  REAL run loop with the software renderer: menu ->
                            new game -> fire/kill/save/load -> screenshots must
                            not be blank. Runs against the built EXE in CI.
"""
import os, sys


class _Tee:
    """Write to several streams, never raise (windowed EXE has no console)."""

    def __init__(self, *streams):
        self._streams = [s for s in streams if s is not None]

    def write(self, data):
        for s in self._streams:
            try:
                s.write(data)
            except Exception:
                pass
        return len(data)

    def flush(self):
        for s in self._streams:
            try:
                s.flush()
            except Exception:
                pass

    def isatty(self):
        return False

    @property
    def encoding(self):
        return "utf-8"


def _bootstrap():
    """Diagnostics from the very first instruction: mirror stdout/stderr
    into <user data>/startup.log and arm faulthandler. A windowed frozen
    EXE gives NO visible error output otherwise, and any early hang (even
    inside `import ursina`) now leaves a stack dump in the log."""
    fh = None
    try:
        if sys.platform.startswith("win"):
            base = os.environ.get("APPDATA") or os.path.expanduser("~")
        else:
            base = os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share")
        d = os.path.join(base, "DeadWorldApocalypse")
        os.makedirs(d, exist_ok=True)
        fh = open(os.path.join(d, "startup.log"), "w", encoding="utf-8", buffering=1)
    except Exception:
        fh = None
    sys.stdout = _Tee(getattr(sys, "stdout", None), fh)
    sys.stderr = _Tee(getattr(sys, "stderr", None), fh)
    if fh is not None:
        try:
            import faulthandler
            faulthandler.enable(file=fh, all_threads=True)
            # watchdog: if anything hangs, dump all thread stacks every 90s
            faulthandler.dump_traceback_later(90, repeat=True, file=fh)
        except Exception:
            pass
    print(f"[boot] main entered argv={sys.argv} frozen={getattr(sys, 'frozen', False)} "
          f"exe={sys.executable} cwd={os.getcwd()}", flush=True)


def selftest():
    import json, os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from game.config import resource_root, Settings
    from game.survival import SurvivalStats
    from game.inventory import Inventory
    from game.ai_brain import ZombieBrain
    from game.data import ZOMBIE_TYPES, WEAPONS, ITEMS
    from game.anim import AnimationLibrary
    from game.combat import hitscan, HitZone
    from game import save_system
    import random

    root = resource_root()
    manifest = json.load(open(os.path.join(root, "assets", "manifest.json"), encoding="utf-8"))
    assert len(manifest["zombies"]) >= 10, "zombie kits missing"
    assert len(manifest["weapons"]) >= 10, "weapons missing"
    for name in manifest["zombies"]:
        skel = os.path.join(root, "assets", "models", "zombies", name, "skeleton.json")
        assert os.path.exists(skel), skel
    lib = AnimationLibrary(os.path.join(root, "assets", "animations", "zombie_animations.json"))
    for needed in ("idle", "walk", "run", "attack", "death", "headshot_death", "hit", "crawl", "scream"):
        assert needed in lib, f"anim {needed} missing"
    st = SurvivalStats()
    st.update(120)
    assert st.hunger < 82 and not st.dead
    inv = Inventory()
    assert inv.add("pistol") == 1 and inv.add("ammo_9mm", 2) == 2
    assert inv.reload("pistol") == 12
    brain = ZombieBrain(ZOMBIE_TYPES["zombie_walker"], rng=random.Random(1))
    assert brain.can_see((0, 0), 0.0, (0, 10)) is True
    zone = HitZone(object(), "head", (0, 1.6, 10), 0.2, 3.0)
    hit, pt, t = hitscan((0, 1.6, 0), (0, 0, 1), 100, [zone], [])
    assert hit is zone, "hitscan broken"
    Settings()
    print("SELFTEST_OK", flush=True)
    # windowed EXE has no stdout: also drop a marker file next to the executable / cwd
    try:
        import os as _os, sys as _sys
        base = _os.path.dirname(_sys.executable) if getattr(_sys, "frozen", False) else _os.getcwd()
        with open(_os.path.join(base, "selftest_result.txt"), "w", encoding="utf-8") as f:
            f.write("SELFTEST_OK\n")
    except Exception:
        pass
    return 0


def smoke():
    from game.app import run_game
    app, game = run_game(headless=True)
    game.new_game()
    # simulate some frames of the real game logic
    from ursina import mouse
    for i in range(120):
        game.update()
        app.taskMgr.step()
        if i == 30:
            game.player.try_fire()
        if i == 60 and game.zombies:
            z = game.zombies[0]
            z.damage(120, "head", game.player.pos_xz, (0, 1))
    state = game.collect_state()
    assert game.state == "playing"
    assert len(game.zombies) > 5, "no zombies spawned"
    assert game.kills >= 1, "kill was not registered"
    game.save_to("slot3")
    game.load_from("slot3")
    for _ in range(10):
        game.update()
        app.taskMgr.step()
    print(f"SMOKE_OK zombies={len(game.zombies)} kills={game.kills} "
          f"pickups={len(game.pickups)} vehicles={len(game.vehicles)} "
          f"day={game.director.day}", flush=True)
    return 0


if __name__ == "__main__":
    _bootstrap()
    if "--selftest" in sys.argv:
        sys.exit(selftest())
    if "--smoke" in sys.argv:
        sys.exit(smoke())
    print("[boot] importing game.app (engine import)...", flush=True)
    from game.app import run_game
    print("[boot] engine imported", flush=True)
    if "--guitest" in sys.argv:
        run_game(guitest=True)   # exits via os._exit from inside the test driver
        sys.exit(0)
    run_game()
