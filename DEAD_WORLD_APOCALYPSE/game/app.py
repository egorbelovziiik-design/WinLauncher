"""DEAD WORLD: APOCALYPSE - application root: screens, wiring, game loop,
interactions, objectives, save/load."""
import math, os, random, sys
from pathlib import Path

from ursina import (Entity, Text, Button, camera, color, mouse, window, scene,
                    application, held_keys, DirectionalLight, AmbientLight,
                    load_texture, destroy)
from ursina.prefabs.sky import Sky

from game.config import (resource_root, load_settings, save_settings,
                         AUTOSAVE_INTERVAL, START_HOUR)
from game.data import (ITEMS, WEAPONS, ZOMBIE_TYPES, ZOMBIE_DROPS, OBJECTIVES)
from game.survival import SurvivalStats
from game.inventory import Inventory
from game.anim import AnimationLibrary
from game.audio_mgr import AudioManager
from game.world import World, Pickup
from game.director import Director
from game.player import Player
from game.character import SurvivorNPC
from game.vehicle import Vehicle, spawn_wrecks
from game.hud import HUD
from game import save_system

LOOT_WEIGHTED = {}


def weighted_choice(rng, table):
    total = sum(w for _, w in table)
    r = rng.uniform(0, total)
    for item, w in table:
        r -= w
        if r <= 0:
            return item
    return table[0][0]


# viewmodel placement per weapon (grip at model origin, barrel +Z)
WEAPON_VIEW_META = {
    "pistol":   dict(scale=1.0, pos=(0, -0.02, 0.1), rot=(0, 0, 0), muzzle=(0.26, -0.18, 0.85)),
    "revolver": dict(scale=1.0, pos=(0, -0.02, 0.1), rot=(0, 0, 0), muzzle=(0.26, -0.18, 0.9)),
    "rifle":    dict(scale=0.85, pos=(0.02, -0.03, 0.05), rot=(0, 0, 0), muzzle=(0.27, -0.2, 1.25)),
    "shotgun":  dict(scale=0.85, pos=(0.02, -0.03, 0.05), rot=(0, 0, 0), muzzle=(0.27, -0.2, 1.2)),
    "sniper":   dict(scale=0.8, pos=(0.02, -0.02, 0.0), rot=(0, 0, 0), muzzle=(0.27, -0.2, 1.4)),
    "crossbow": dict(scale=0.85, pos=(0.0, -0.04, 0.1), rot=(0, 0, 0), muzzle=(0.26, -0.2, 1.0)),
    "knife":    dict(scale=1.1, pos=(0.02, -0.05, 0.12), rot=(0, 0, 0)),
    "bat":      dict(scale=0.8, pos=(0.03, -0.06, 0.05), rot=(18, 0, 0)),
    "machete":  dict(scale=0.95, pos=(0.02, -0.05, 0.1), rot=(10, 0, 0)),
    "axe":      dict(scale=0.85, pos=(0.03, -0.06, 0.05), rot=(14, 0, 0)),
}


class Game(Entity):
    def __init__(self, settings):
        """Light init only - heavy work lives in build_steps() so the
        engine's own run loop can present loading-screen frames between
        chunks (v0.1.x built everything before the first frame and could
        sit on an unpainted white window for minutes on Windows)."""
        super().__init__()
        self.settings = settings
        self.assets_path = str(Path(resource_root()) / "assets")
        self.rng = random.Random(4242)
        self.state = "menu"          # menu | playing | inventory | paused | dead | settings
        self.state_before_settings = "menu"
        self.time_played = 0.0
        self.kills = 0
        self.headshots = 0
        self.flags = set()
        self.selected_slot = 0
        self.autosave_t = AUTOSAVE_INTERVAL
        self.boss_ref = None
        self._fps_acc, self._fps_frames, self._fps_val = 0.0, 0, 0.0
        self.zombies = []
        self.pickups = []
        self.vehicles = []
        self.npcs = []
        self._update_err_t = 0.0

    def build_steps(self):
        """Generator yielding (label, frac) between heavy build chunks."""
        settings = self.settings
        yield ("Анимации...", 0.36)
        self.anim_library = AnimationLibrary(
            os.path.join(self.assets_path, "animations", "zombie_animations.json"))
        yield ("Звук...", 0.38)
        self.audio = AudioManager(settings, self.assets_path)
        self.stats = SurvivalStats()
        self.inventory = Inventory()

        # world & environment
        yield ("Строим мир...", 0.42)
        self.world = World(self)
        yield from self.world.build_steps(0.42, 0.72)

        yield ("Небо и свет...", 0.75)
        self.sky = Sky()
        if getattr(self, "_no_glsl", False):
            try:
                self.sky.shader = None   # fixed-function sky on GLSL-less renderers
            except Exception:
                pass
        camera.clip_plane_far = max(400.0, settings.view_distance * 2.0)
        self.sun = DirectionalLight(
            shadows=bool(settings.shadows) and not getattr(self, "_no_glsl", False))
        self.sun.shadow_map_resolution = (2048, 2048) if settings.quality == "high" else (1024, 1024)
        self.sun.look_at(Entity(position=(0, -1, 0.35)))
        self.ambient = AmbientLight()
        self.ambient.color = color.rgba32(140, 150, 170, 255)
        try:
            self.sun.update_bounds()
        except Exception:
            pass
        self.cam_rig = Entity()
        self.menu_rig = Entity(position=(0, 16, 46))
        self.menu_rig.look_at((0, 2, 0))

        yield ("Игрок и интерфейс...", 0.78)
        self.player = Player(self)
        self.weapon_view_meta = WEAPON_VIEW_META
        self.hud = HUD(self)
        self.director = Director(self)
        self.director.hour = START_HOUR

        yield ("Транспорт и выжившие...", 0.86)
        self._spawn_vehicles()
        self.wrecks = spawn_wrecks(self)
        self._spawn_npcs()

        yield ("Разбрасываем припасы...", 0.94)
        self.spawn_loot(initial=True)
        self._initial_vehicle_state = [v.to_dict() for v in self.vehicles]

        yield ("Меню...", 0.98)
        self._build_menus()
        self._ready = True
        self.show_menu()

    # ------------------------------------------------------------------ assets helpers
    def tex(self, name):
        return load_texture(name, path=str(Path(self.assets_path) / "textures"))

    # ------------------------------------------------------------------ spawning
    def _spawn_vehicles(self):
        spawns = [
            ("sedan", (5, 0, 8), 14, 32),
            ("pickup", (50, 0, 48), -24, 8),
            ("suv", (24, 0, -24), 96, 19),
            ("military", (-90, 0, -88), 40, 46),
            ("pickup", (-98, 0, 74), 0, 22),
        ]
        for name, pos, yaw, fuel in spawns:
            self.vehicles.append(Vehicle(name, pos, yaw, self, fuel=fuel, drivable=True))

    def _spawn_npcs(self):
        w = self.world.zones
        vx, vz = w["village"]
        self.npcs.append(SurvivorNPC("trader", (vx, 0, vz + 2), self))
        self.npcs.append(SurvivorNPC("survivor_f", (vx - 5, 0, vz - 3), self))
        self.npcs.append(SurvivorNPC("survivor_m", (vx + 6, 0, vz - 4), self))
        mx, mz = w["military"]
        self.npcs.append(SurvivorNPC("soldier_npc", (mx + 2, 0, mz + 4), self))

    def spawn_loot(self, initial=False):
        from game.data import LOOT_TABLES
        for (x, z, table) in self.world.loot_points:
            if table is None:
                continue
            item = weighted_choice(self.rng, LOOT_TABLES[table])
            self.pickups.append(Pickup(item, (x, z), self))
        if initial:
            # guaranteed story items so objectives are always completable
            guaranteed = [
                ("pistol", (-64, -62)), ("ammo_9mm", (-62, -62)), ("shotgun", (-58, -68)),
                ("antibiotics", (57, -67)), ("antibiotics", (68, -88)), ("medkit", (62, -66)),
                ("rifle", (-95, -92)), ("ammo_rifle", (-93, -92)),
                ("fuel_canister", (59, 52)), ("fuel_canister", (89, 81)),
                ("fuel_canister", (-97, 79)), ("axe", (88, 84)),
                ("crossbow", (-88, 12)), ("ammo_bolts", (-87, 13)),
                ("bat", (3, 63)), ("medkit", (84, -47)),
                ("backpack", (18, -28)), ("machete", (-23, 24)),
                ("sniper", (99, 39)), ("ammo_rifle", (100, 40)),
            ]
            for item, (x, z) in guaranteed:
                self.pickups.append(Pickup(item, (x, z), self))

    # ------------------------------------------------------------------ menus / screens
    def _btn(self, parent, text, y, cb, x=0.0, w=0.34, h=0.052, col=None, scale=0.95):
        b = Button(parent=parent, text=text, position=(x, y), scale=(w, h),
                   color=col or color.rgb32(46, 28, 26))
        b.text_entity.scale = scale
        b.text_entity.color = color.rgb32(245, 238, 226)
        b.on_click = lambda: (self.audio.play("ui_click", 0.7), cb())
        return b

    def _build_menus(self):
        ui = camera.ui
        # ---- main menu
        self.menu_root = Entity(parent=ui)
        Entity(parent=self.menu_root, model="quad", scale=(2.4, 1.4), position=(0, 0, 0.2),
               color=color.rgba32(6, 4, 5, 168))
        Text("DEAD WORLD", parent=self.menu_root, position=(0, 0.34), origin=(0, 0), scale=3.2,
             color=color.rgb32(214, 40, 34))
        Text("A P O C A L Y P S E", parent=self.menu_root, position=(0, 0.255), origin=(0, 0), scale=1.5,
             color=color.rgb32(232, 224, 206))
        Text("зомби-выживание в открытом мире", parent=self.menu_root, position=(0, 0.20),
             origin=(0, 0), scale=0.9, color=color.rgb32(170, 168, 160))
        self._btn(self.menu_root, "НОВАЯ ИГРА", 0.10, self.new_game)
        self.menu_continue = self._btn(self.menu_root, "ПРОДОЛЖИТЬ", 0.035, self.continue_game)
        self._btn(self.menu_root, "ЗАГРУЗИТЬ ИГРУ", -0.03, lambda: self.show_load("menu"))
        self._btn(self.menu_root, "НАСТРОЙКИ", -0.095, lambda: self.show_settings("menu"))
        self._btn(self.menu_root, "КАК ИГРАТЬ", -0.16, self.show_help_from_menu)
        self._btn(self.menu_root, "ВЫХОД", -0.225, self.quit)
        from game import __version__
        Text(f"v{__version__} • Ursina/Panda3D • модели: Blender 4.2 (процедурно)",
             parent=self.menu_root, position=(0, -0.44), origin=(0, 0), scale=0.68,
             color=color.rgb32(140, 140, 135))

        # ---- pause
        self.pause_root = Entity(parent=ui, enabled=False)
        Entity(parent=self.pause_root, model="quad", scale=(2.4, 1.4), position=(0, 0, 0.2),
               color=color.rgba32(5, 5, 8, 175))
        Text("ПАУЗА", parent=self.pause_root, position=(0, 0.30), origin=(0, 0), scale=2.0,
             color=color.rgb32(240, 236, 226))
        self._btn(self.pause_root, "ПРОДОЛЖИТЬ", 0.19, self.resume)
        self._btn(self.pause_root, "СОХРАНИТЬ (Слот 1)", 0.125, lambda: self.save_to("slot1", notify=True))
        self._btn(self.pause_root, "ЗАГРУЗИТЬ", 0.06, lambda: self.show_load("paused"))
        self._btn(self.pause_root, "НАСТРОЙКИ", -0.005, lambda: self.show_settings("paused"))
        self._btn(self.pause_root, "В ГЛАВНОЕ МЕНЮ", -0.07, self.to_main_menu)
        self._btn(self.pause_root, "ВЫХОД ИЗ ИГРЫ", -0.135, self.quit)

        # ---- load screen
        self.load_root = Entity(parent=ui, enabled=False)
        Entity(parent=self.load_root, model="quad", scale=(0.95, 0.8), position=(0, 0, 0.15),
               color=color.rgba32(12, 12, 16, 245))
        Text("ЗАГРУЗКА ИГРЫ", parent=self.load_root, position=(0, 0.32), origin=(0, 0), scale=1.5,
             color=color.rgb32(255, 214, 130))
        self.load_buttons = []
        for i in range(4):
            b = self._btn(self.load_root, "—", 0.22 - i * 0.085, lambda: None, w=0.8, h=0.068, scale=0.8)
            self.load_buttons.append(b)
        self._btn(self.load_root, "НАЗАД", -0.17, self.load_back, w=0.3)
        self.load_return = "menu"

        # ---- death screen
        self.death_root = Entity(parent=ui, enabled=False)
        Entity(parent=self.death_root, model="quad", scale=(2.4, 1.4), position=(0, 0, 0.2),
               color=color.rgba32(24, 2, 2, 200))
        Text("ВЫ МЕРТВЫ", parent=self.death_root, position=(0, 0.26), origin=(0, 0), scale=3.0,
             color=color.rgb32(214, 40, 34))
        self.death_stats = Text("", parent=self.death_root, position=(0, 0.12), origin=(0, 0),
                                scale=1.0, color=color.rgb32(235, 230, 220))
        self._btn(self.death_root, "НОВАЯ ИГРА", -0.02, self.new_game)
        self._btn(self.death_root, "ЗАГРУЗИТЬ", -0.085, lambda: self.show_load("dead"))
        self._btn(self.death_root, "В ГЛАВНОЕ МЕНЮ", -0.15, self.to_main_menu)

        # ---- settings screen
        self.settings_root = Entity(parent=ui, enabled=False)
        Entity(parent=self.settings_root, model="quad", scale=(1.05, 0.9), position=(0, 0, 0.15),
               color=color.rgba32(12, 13, 17, 248))
        Text("НАСТРОЙКИ", parent=self.settings_root, position=(0, 0.385), origin=(0, 0), scale=1.6,
             color=color.rgb32(255, 214, 130))
        self.set_buttons = {}
        rows = [
            ("resolution", "Разрешение (перезапуск)"), ("fullscreen", "Полноэкранный режим"),
            ("quality", "Качество графики"), ("shadows", "Тени"),
            ("fog", "Туман"), ("particles", "Частицы (дождь)"),
            ("view_distance", "Дальность прорисовки"), ("mouse_sensitivity", "Чувствительность мыши"),
            ("master_volume", "Общая громкость"), ("music_volume", "Музыка"),
            ("sfx_volume", "Звуковые эффекты"), ("invert_y", "Инверсия мыши Y"),
        ]
        for i, (key, label) in enumerate(rows):
            y = 0.31 - i * 0.052
            Text(label, parent=self.settings_root, position=(-0.47, y), scale=0.75,
                 color=color.rgb32(220, 224, 230))
            minus = self._btn(self.settings_root, "−", y, (lambda k=key: self.setting_step(k, -1)),
                              x=0.13, w=0.045, h=0.04, scale=1.0)
            val = Text("", parent=self.settings_root, position=(0.30, y), origin=(0, 0), scale=0.8,
                       color=color.rgb32(255, 230, 170))
            plus = self._btn(self.settings_root, "+", y, (lambda k=key: self.setting_step(k, 1)),
                             x=0.47, w=0.045, h=0.04, scale=1.0)
            self.set_buttons[key] = val
        self._btn(self.settings_root, "СОХРАНИТЬ И НАЗАД", -0.36, self.settings_back, w=0.36)
        Text("Управление: WASD движение • мышь обзор • ЛКМ огонь • ПКМ прицел • E действие\n"
             "R перезарядка • Tab инвентарь • Shift спринт • Ctrl пригнуться • L фонарь • F1 справка",
             parent=self.settings_root, position=(0, -0.435), origin=(0, 0), scale=0.62,
             color=color.rgb32(150, 155, 165))

    # ------------------------------------------------------------------ screen flow
    @staticmethod
    def _set_cursor(locked):
        """Lock/hide or free/show the cursor; harmless in headless mode."""
        try:
            mouse.locked = bool(locked)
        except Exception:
            pass
        try:
            mouse.visible = not locked
        except Exception:
            pass

    def show_menu(self):
        self.state = "menu"
        self._set_cursor(False)
        for r in (self.pause_root, self.load_root, self.death_root, self.settings_root):
            r.enabled = False
        self.hud.root.enabled = False
        self.hud.inv_root.enabled = False
        self.hud.help_root.enabled = False
        self.menu_root.enabled = True
        has = save_system.has_continue()
        self.menu_continue.color = color.rgb32(46, 28, 26) if has else color.rgb32(30, 30, 34)
        camera.parent = self.menu_rig
        camera.position = (0, 0, 0)
        camera.rotation = (0, 0, 0)
        camera.fov = 58
        self.audio.set_loop("music_ambient", 0.5)
        self.audio.set_loop("wind", 0.12)

    def to_main_menu(self):
        if self.player.vehicle:
            self.player.exit_vehicle()
        self.show_menu()

    def start_playing(self):
        self.state = "playing"
        for r in (self.menu_root, self.pause_root, self.load_root, self.death_root, self.settings_root):
            r.enabled = False
        self.hud.root.enabled = True
        camera.parent = self.player.head
        camera.position = (0, 0, 0)
        camera.rotation = (0, 0, 0)
        camera.fov = 76
        self._set_cursor(True)
        self.refresh_hotbar_ui()
        self.hud.refresh_inventory(self.inventory)

    def resume(self):
        if self.state in ("paused", "inventory"):
            self.pause_root.enabled = False
            self.hud.inv_root.enabled = False
            self.hud.help_root.enabled = False
            self.state = "playing"
            self._set_cursor(True)

    def pause(self):
        if self.state != "playing":
            return
        self.state = "paused"
        self.pause_root.enabled = True
        self._set_cursor(False)
        self.save_to("autosave")

    def open_inventory(self):
        if self.state != "playing":
            return
        self.state = "inventory"
        self.hud.inv_root.enabled = True
        self.hud.refresh_inventory(self.inventory)
        self._set_cursor(False)

    def close_inventory(self):
        if self.state == "inventory":
            self.hud.inv_root.enabled = False
            self.state = "playing"
            self._set_cursor(True)

    def show_help_from_menu(self):
        self.hud.help_root.enabled = not self.hud.help_root.enabled

    def show_load(self, ret):
        self.load_return = ret
        self.menu_root.enabled = False
        self.pause_root.enabled = False
        self.death_root.enabled = False
        self.settings_root.enabled = False
        self.load_root.enabled = True
        infos = {i["slot"]: i for i in save_system.list_saves()}
        names = {"autosave": "Автосохранение", "slot1": "Слот 1", "slot2": "Слот 2", "slot3": "Слот 3"}
        for b, slot in zip(self.load_buttons, save_system.SLOTS):
            info = infos.get(slot)
            if info:
                b.text = (f"{names[slot]} — день {info['day']}, убийств: {info['kills']}, "
                          f"HP {info['hp']} ({info['saved_at']})")
                b.color = color.rgb32(46, 28, 26)
                b.on_click = (lambda s=slot: (self.audio.play("ui_click", 0.7), self.load_from(s)))
            else:
                b.text = f"{names[slot]} — пусто"
                b.color = color.rgb32(30, 30, 34)
                b.on_click = lambda: None

    def load_back(self):
        self.load_root.enabled = False
        if self.load_return == "paused":
            self.pause_root.enabled = True
        elif self.load_return == "dead":
            self.death_root.enabled = True
        else:
            self.menu_root.enabled = True

    def show_settings(self, ret):
        self.state_before_settings = ret
        self.menu_root.enabled = False
        self.pause_root.enabled = False
        self.settings_root.enabled = True
        self.refresh_settings_labels()

    def settings_back(self):
        save_settings(self.settings)
        self.settings_root.enabled = False
        camera.clip_plane_far = max(400.0, self.settings.view_distance * 2.0)
        try:
            window.fullscreen = self.settings.fullscreen
        except Exception:
            pass
        if self.state_before_settings == "paused":
            self.pause_root.enabled = True
        else:
            self.menu_root.enabled = True

    def refresh_settings_labels(self):
        s = self.settings
        onoff = lambda v: "ВКЛ" if v else "ВЫКЛ"
        vals = {
            "resolution": s.resolution, "fullscreen": onoff(s.fullscreen),
            "quality": {"low": "Низкое", "medium": "Среднее", "high": "Высокое"}[s.quality],
            "shadows": onoff(s.shadows), "fog": onoff(s.fog), "particles": onoff(s.particles),
            "view_distance": f"{int(s.view_distance)} м",
            "mouse_sensitivity": f"{s.mouse_sensitivity:.0f}",
            "master_volume": f"{int(s.master_volume * 100)}%",
            "music_volume": f"{int(s.music_volume * 100)}%",
            "sfx_volume": f"{int(s.sfx_volume * 100)}%",
            "invert_y": onoff(s.invert_y),
        }
        for k, t in self.set_buttons.items():
            t.text = vals.get(k, "—")

    def setting_step(self, key, d):
        s = self.settings
        if key == "resolution":
            opts = ["1024x600", "1280x720", "1366x768", "1600x900", "1920x1080"]
            i = opts.index(s.resolution) if s.resolution in opts else 1
            s.resolution = opts[(i + d) % len(opts)]
        elif key == "quality":
            opts = ["low", "medium", "high"]
            s.quality = opts[(opts.index(s.quality) + d) % len(opts)]
        elif key in ("fullscreen", "shadows", "fog", "particles", "invert_y"):
            setattr(s, key, not getattr(s, key))
        elif key == "view_distance":
            s.view_distance = max(110, min(340, s.view_distance + d * 20))
        elif key == "mouse_sensitivity":
            s.mouse_sensitivity = max(10, min(90, s.mouse_sensitivity + d * 5))
        elif key in ("master_volume", "music_volume", "sfx_volume"):
            setattr(s, key, max(0.0, min(1.0, round(getattr(s, key) + d * 0.1, 2))))
        self.refresh_settings_labels()

    def quit(self):
        if self.state in ("playing", "paused", "inventory"):
            self.save_to("autosave")
        application.quit()

    # ------------------------------------------------------------------ game lifecycle
    def _reset_world_state(self):
        for z in list(self.zombies):
            z.despawn()
        self.zombies.clear()
        for p in self.pickups:
            if not p.taken:
                p.take()
        self.pickups.clear()
        if self.player.vehicle:
            self.player.exit_vehicle()
        for v, st in zip(self.vehicles, self._initial_vehicle_state):
            v.apply_dict(st)
            v.speed = 0.0
            v.occupied = False
        for door in self.world.doors:
            door.hp = 120.0
            door.broken = False
            if door.open:
                door.toggle()
            door.panel.rotation_x = 0
        self.boss_ref = None
        self.director.boss_spawned = False
        self.director.day = 1
        self.director.hour = START_HOUR
        self.director.weather = "clear"
        self.kills = 0
        self.headshots = 0
        self.flags = set()
        self.time_played = 0.0
        self.autosave_t = AUTOSAVE_INTERVAL

    def new_game(self):
        self._reset_world_state()
        self.stats = SurvivalStats()
        self.inventory = Inventory()
        self.inventory.add("knife")
        self.inventory.add("bandage")
        self.inventory.add("food_can")
        self.inventory.add("water_bottle")
        self.player.set_position((*self.world.zones["spawn"][0:1], 0, self.world.zones["spawn"][1]), yaw=180)
        spawn = self.world.zones["spawn"]
        self.player.root.position = (spawn[0], 0, spawn[1])
        self.player.yaw_deg = 180
        self.player.pitch_deg = 0
        self.selected_slot = 0
        self.spawn_loot(initial=True)
        self.director.initial_population()
        owned = self.inventory.weapons_owned()
        if owned:
            self.player.equip(owned[0])
        self.start_playing()
        self.notify("День 1. Найдите оружие и припасы. Удачи.", big=True)
        self.save_to("autosave")

    def continue_game(self):
        slot = save_system.newest_slot()
        if slot:
            self.load_from(slot)
        else:
            self.new_game()

    def die(self, cause=""):
        if self.state == "dead":
            return
        self.state = "dead"
        self.audio.play("death_sting", 0.9)
        self.audio.set_loop("engine", 0.0)
        self.death_stats.text = (f"{cause}\nВы продержались {self.director.day} дн., "
                                 f"убито зомби: {self.kills} (в голову: {self.headshots})\n"
                                 f"Время выживания: {int(self.time_played // 60)} мин")
        self.death_root.enabled = True
        self.hud.root.enabled = False
        self.hud.inv_root.enabled = False
        self.hud.help_root.enabled = False
        self._set_cursor(False)

    # ------------------------------------------------------------------ save / load
    def collect_state(self):
        return {
            "player_pos": [self.player.root.x, 0, self.player.root.z],
            "player_yaw": self.player.yaw_deg,
            "stats": self.stats.to_dict(),
            "inventory": self.inventory.to_dict(),
            "selected_slot": self.selected_slot,
            "current_weapon": self.player.current_weapon,
            "day": self.director.day,
            "hour": self.director.hour,
            "weather": self.director.weather,
            "kills": self.kills,
            "headshots": self.headshots,
            "flags": sorted(self.flags),
            "boss_spawned": self.director.boss_spawned,
            "vehicles": [v.to_dict() for v in self.vehicles],
            "time_played": self.time_played,
        }

    def apply_state(self, st):
        self._reset_world_state()
        self.stats = SurvivalStats.from_dict(st.get("stats"))
        self.inventory = Inventory.from_dict(st.get("inventory"))
        pos = st.get("player_pos", [0, 0, 20])
        self.player.root.position = (pos[0], 0, pos[2])
        self.player.yaw_deg = st.get("player_yaw", 180)
        self.director.day = st.get("day", 1)
        self.director.hour = st.get("hour", START_HOUR)
        self.director.weather = st.get("weather", "clear")
        self.director.boss_spawned = bool(st.get("boss_spawned", False))
        self.kills = st.get("kills", 0)
        self.headshots = st.get("headshots", 0)
        self.flags = set(st.get("flags", []))
        self.time_played = st.get("time_played", 0.0)
        for v, vd in zip(self.vehicles, st.get("vehicles", [])):
            v.apply_dict(vd)
        self.selected_slot = st.get("selected_slot", 0)
        self.spawn_loot(initial=True)
        self.director.initial_population()
        wid = st.get("current_weapon")
        if wid and self.inventory.has(wid):
            self.player.equip(wid)
        if self.director.boss_spawned and "boss_dead" not in self.flags:
            self.director.boss_spawned = False   # allow the boss to re-emerge on load

    def save_to(self, slot, notify=False):
        try:
            save_system.save_game(self.collect_state(), slot)
            if notify:
                self.notify("Игра сохранена")
            return True
        except Exception as e:
            if notify:
                self.notify(f"Ошибка сохранения: {e}", bad=True)
            return False

    def load_from(self, slot):
        try:
            st = save_system.load_game(slot)
        except ValueError as e:
            self.notify(str(e), bad=True)
            return
        if st is None:
            return
        self.apply_state(st)
        self.start_playing()
        self.notify("Игра загружена")

    # ------------------------------------------------------------------ gameplay callbacks
    def player_pos(self):
        if self.player.vehicle:
            v = self.player.vehicle
            return (v.body.x, 1.0, v.body.z)
        return self.player.pos

    def player_alive(self):
        return self.state in ("playing", "inventory") and not self.stats.dead

    def player_crouched(self):
        return self.player.crouched and not self.player.vehicle

    def notify(self, msg, bad=False, big=False):
        self.hud.notify(msg, bad=bad, big=big)

    def on_zombie_killed(self, zombie, headshot):
        self.kills += 1
        if zombie.cfg.get("boss"):
            self.flags.add("boss_dead")
            self.notify("ГИГАНТ мёртв! Лаборатория зачищена.", big=True)
            self.audio.play("quest_done", 0.9)
        drops = ZOMBIE_DROPS.get(zombie.type_id, ZOMBIE_DROPS["_default"])
        for item, chance_pct in drops:
            if self.rng.random() * 100 < chance_pct:
                self.pickups.append(Pickup(item, zombie.pos_xz, self))

    def on_zombie_attack(self, zombie):
        if not self.player_alive():
            return
        dmg = zombie.cfg["damage"]
        if self.player.vehicle:
            v = self.player.vehicle
            v.hp -= dmg * 1.2
            self.player.cam_shake = min(0.6, self.player.cam_shake + 0.18)
            self.audio.play("car_hit", 0.55)
            if v.hp <= 0 and v.alive is False:
                pass
            if v.hp <= 0:
                self.notify("Машина разбита!", bad=True)
                self.player.exit_vehicle()
            return
        infection = 4.0 + zombie.cfg.get("infection_bonus", 0)
        if zombie.cfg.get("crawler"):
            infection += 2.0
        died = self.stats.damage(dmg, cause_bleeding_chance=0.16,
                                  infection_gain=infection, rng=self.rng)
        self.hud.hurt_flash(0.4)
        self.player.cam_shake = min(0.7, self.player.cam_shake + 0.25)
        self.audio.play("player_hurt", 0.85)
        if self.stats.dead:
            cause = "Инфекция вас доконала." if self.stats.infection >= 100 else "Вас разорвали заражённые."
            self.die(cause)

    def on_vehicle_crash(self, dmg):
        self.stats.damage(dmg * 0.3)
        self.hud.hurt_flash(0.25)
        if self.stats.dead:
            self.die("Авария оказалась смертельной.")

    # ------------------------------------------------------------------ objectives
    def objective_done(self, obj):
        c = obj["check"]
        if c == "has_gun":
            return any(WEAPONS[w]["kind"] == "gun" for w in self.inventory.weapons_owned()) or "has_gun" in self.flags
        if c == "has_antibiotics":
            return self.inventory.has("antibiotics") or "got_antibiotics" in self.flags
        if c == "car_fueled":
            return "car_fueled" in self.flags
        if c == "boss_dead":
            return "boss_dead" in self.flags
        return False

    def current_objective(self):
        for obj in OBJECTIVES:
            if not self.objective_done(obj):
                return obj
        return OBJECTIVES[-1]

    def objective_marker(self):
        obj = self.current_objective()
        if not obj:
            return None
        return {"has_gun": self.world.zones["police"], "has_antibiotics": self.world.zones["hospital"],
                "car_fueled": self.world.zones["gas"], "boss_dead": self.world.zones["lab"],
                "never": None}.get(obj["check"])

    def check_objective_progress(self, before_id):
        now = self.current_objective()
        if now and now["id"] != before_id:
            self.audio.play("quest_done", 0.8)
            self.notify("Цель выполнена! Новая: " + now["text"], big=False)

    # ------------------------------------------------------------------ inventory interactions
    def refresh_hotbar_ui(self):
        self.hud.refresh_hotbar(self.inventory, self.selected_slot)

    def on_inventory_sort(self):
        self.inventory.sort()
        self.hud.refresh_inventory(self.inventory)

    def on_inventory_click(self, idx):
        inv = self.inventory
        if idx >= len(inv.slots):
            return
        slot = inv.slots[idx]
        item_id = slot["id"]
        if held_keys["shift"]:
            inv.remove(item_id, 1)
            p = self.player
            fwd = p.forward()
            drop_pos = (p.root.x + fwd[0] * 1.2, p.root.z + fwd[2] * 1.2)
            self.pickups.append(Pickup(item_id, drop_pos, self))
            if item_id == self.player.current_weapon and not inv.has(item_id):
                self.player.equip(None)
            self.hud.refresh_inventory(inv)
            self.refresh_hotbar_ui()
            return
        it = ITEMS[item_id]
        if it["kind"] == "weapon":
            slot_i = inv.assign_hotbar(item_id)
            if slot_i >= 0:
                self.selected_slot = slot_i
            self.player.equip(item_id)
            self.refresh_hotbar_ui()
        elif it["kind"] in ("food", "drink", "med"):
            if inv.use_consumable(item_id, self.stats):
                clip = {"food": "eat", "drink": "drink"}.get(it["kind"], "pickup")
                self.audio.play(clip, 0.8)
                self.notify(f"Использовано: {it['name']}")
                self.hud.refresh_inventory(inv)
        elif item_id == "backpack":
            if "backpack_on" not in self.flags:
                self.flags.add("backpack_on")
                inv.remove("backpack", 1)
                inv.max_weight_bonus = 10.0
                self.notify("Рюкзак надет: +10 кг к лимиту")
                self.hud.refresh_inventory(inv)
        elif it["kind"] == "fuel":
            self.notify("Канистру применяйте клавишей F рядом с машиной")

    def use_item_from_hotbar(self, item_id):
        if self.inventory.use_consumable(item_id, self.stats):
            self.audio.play("pickup", 0.8)

    def try_refuel_nearby(self):
        if not self.inventory.has("fuel_canister"):
            self.notify("Нужна канистра с топливом", bad=True)
            return
        p = self.player.pos_xz
        for v in self.vehicles:
            if v.drivable and math.dist(p, v.pos_xz) < 3.6:
                if v.fuel >= 99:
                    self.notify("Бак полон")
                    return
                self.inventory.remove("fuel_canister", 1)
                v.fuel = min(100.0, v.fuel + ITEMS["fuel_canister"]["fuel"])
                self.audio.play("drink", 0.8)
                self.notify(f"{v.stats['name']}: топливо {v.fuel:.0f}%")
                if v.fuel >= 60:
                    self.flags.add("car_fueled")
                return
        self.notify("Рядом нет машины", bad=True)

    # ------------------------------------------------------------------ interaction scan
    def find_interactable(self):
        p = self.player
        pxz = p.pos_xz
        best = None
        best_d = 3.4
        for pk in self.pickups:
            if pk.taken:
                continue
            d = math.dist(pxz, pk.pos_xz)
            if d < best_d:
                best, best_d = ("pickup", pk), d
        door = self.world.nearest_door(pxz, 2.6)
        if door is not None:
            d = math.dist(pxz, door.pos_xz)
            if d <= best_d:
                best, best_d = ("door", door), d
        for d in self.world.doors:
            if d.open and not d.broken and math.dist(pxz, d.pos_xz) < 2.6:
                bd = math.dist(pxz, d.pos_xz)
                if bd <= best_d:
                    best, best_d = ("door", d), bd
        for v in self.vehicles:
            if v.drivable and v.alive:
                d = math.dist(pxz, v.pos_xz)
                if d < max(3.4, v.meta["length"] / 2 + 1.0) and d <= best_d + 0.6:
                    best, best_d = ("vehicle", v), d
        for npc in self.npcs:
            d = math.dist(pxz, npc.pos_xz)
            if d < 3.0 and d <= best_d + 1.0:
                best, best_d = ("npc", npc), d
        return best

    def interact_prompt(self, found):
        if self.player.vehicle:
            return "E — выйти из машины"
        if not found:
            return ""
        kind, obj = found
        if kind == "pickup":
            return f"E — подобрать: {ITEMS[obj.item_id]['name']}"
        if kind == "door":
            return "E — закрыть дверь" if obj.open else "E — открыть дверь"
        if kind == "vehicle":
            extra = " • F — заправить" if self.inventory.has("fuel_canister") else ""
            fuel_note = " (нет топлива!)" if obj.fuel <= 0 else f" (топливо {obj.fuel:.0f}%)"
            return f"E — сесть в {obj.stats['name']}{fuel_note}{extra}"
        if kind == "npc":
            return "E — поговорить"
        return ""

    def interact(self):
        if self.player.vehicle:
            self.player.exit_vehicle()
            return
        found = self.find_interactable()
        if not found:
            return
        kind, obj = found
        before_obj = self.current_objective()["id"]
        if kind == "pickup":
            added = self.inventory.add(obj.item_id)
            if added:
                obj.take()
                self.audio.play("pickup", 0.85)
                name = ITEMS[obj.item_id]["name"]
                if ITEMS[obj.item_id]["kind"] == "ammo":
                    name += f" (+{ITEMS[obj.item_id]['amount']})"
                self.notify(f"Подобрано: {name}")
                if obj.item_id == "antibiotics":
                    self.flags.add("got_antibiotics")
                if ITEMS[obj.item_id]["kind"] == "weapon":
                    if WEAPONS[obj.item_id]["kind"] == "gun":
                        self.flags.add("has_gun")
                    if not self.player.current_weapon or WEAPONS.get(self.player.current_weapon, {}).get("kind") == "melee":
                        idx = self.inventory.hotbar.index(obj.item_id) if obj.item_id in self.inventory.hotbar else -1
                        if idx >= 0:
                            self.selected_slot = idx
                        self.player.equip(obj.item_id)
                    # found guns come with a partial magazine
                    if WEAPONS[obj.item_id]["kind"] == "gun" and self.inventory.mags.get(obj.item_id, 0) == 0:
                        self.inventory.mags[obj.item_id] = max(1, WEAPONS[obj.item_id]["mag"] // 2)
                self.refresh_hotbar_ui()
            else:
                self.notify("Слишком тяжело! Освободите инвентарь (Tab)", bad=True)
        elif kind == "door":
            obj.toggle()
        elif kind == "vehicle":
            if obj.fuel <= 0:
                self.notify("Бак пуст. Нужна канистра (F рядом с машиной)", bad=True)
            self.player.enter_vehicle(obj)
        elif kind == "npc":
            self.notify(obj.line())
            self.audio.play("pickup", 0.4)
        self.check_objective_progress(before_obj)

    # ------------------------------------------------------------------ environment
    def apply_environment(self, sky_rgb, fog_rgb, sun_rgb, sun_i, amb_i, fog_density, hour):
        self.sky.color = color.rgb32(int(sky_rgb[0]), int(sky_rgb[1]), int(sky_rgb[2]))
        scene.fog_color = color.rgb32(int(fog_rgb[0]), int(fog_rgb[1]), int(fog_rgb[2]))
        scene.fog_density = float(fog_density)
        # sun path: east (morning) -> west (evening); dim blue "moon" at night
        ang = (hour - 6.0) / 12.0 * math.pi
        elevation = math.sin(ang)
        az = math.cos(ang)
        pp = self.player_pos()
        target = (pp[0] + az * 10, pp[1] - max(0.18, elevation) * 10, pp[2] + 3.5)
        self.sun.position = (pp[0] - az * 30, 40, pp[2] - 10)
        try:
            self.sun.look_at(target)
        except Exception:
            pass
        si = max(0.06, sun_i / 2.2)
        try:
            self.sun._light.setColor((sun_rgb[0] / 255 * si, sun_rgb[1] / 255 * si, sun_rgb[2] / 255 * si, 1))
            self.ambient._light.setColor((0.62 * amb_i, 0.66 * amb_i, 0.74 * amb_i, 1))
        except Exception:
            pass

    # ------------------------------------------------------------------ input
    def input(self, key):
        if not getattr(self, "_ready", False):
            return
        if self.state == "playing":
            if key == "escape":
                self.pause()
            elif key in ("tab", "i"):
                self.open_inventory()
            elif key == "left mouse down":
                self.player.try_fire()
            elif key == "r":
                self.player.start_reload()
            elif key == "e":
                self.interact()
            elif key == "f":
                if not self.player.vehicle:
                    self.try_refuel_nearby()
            elif key == "l":
                self.player.flashlight_on = not self.player.flashlight_on
                self.player.flashlight.enabled = self.player.flashlight_on
                self.audio.play("ui_click", 0.5)
            elif key in ("1", "2", "3", "4", "5", "6"):
                self.player.select_slot(int(key) - 1)
            elif key == "scroll up":
                self.player.cycle_weapon(1)
            elif key == "scroll down":
                self.player.cycle_weapon(-1)
            elif key == "f5":
                self.save_to("slot1", notify=True)
            elif key == "f9":
                self.load_from("slot1") if save_system.slot_info("slot1") else self.load_from("autosave")
            elif key == "f1":
                self.hud.help_root.enabled = not self.hud.help_root.enabled
            elif key == "f3":
                self.hud.show_fps = not self.hud.show_fps
        elif self.state == "inventory":
            if key in ("tab", "i", "escape"):
                self.close_inventory()
        elif self.state == "paused":
            if key == "escape":
                self.resume()
        elif self.state == "menu":
            if key == "escape":
                self.hud.help_root.enabled = False

    # ------------------------------------------------------------------ main loop
    def update(self):
        if not getattr(self, "_ready", False):
            return
        try:
            self._update_impl()
        except Exception:
            import time as _t, traceback as _tb
            now = _t.time()
            if now - getattr(self, "_update_err_t", 0.0) > 5.0:
                self._update_err_t = now
                print("ERROR in Game.update:\n" + _tb.format_exc(), flush=True)

    def _update_impl(self):
        from ursina import time as utime
        dt = min(0.05, utime.dt)
        if self.state == "menu":
            # slow cinematic orbit behind the menu
            t = utime.time() if hasattr(utime, "time") else 0
            self.director.hour = 17.8   # moody dusk backdrop
            sky, fog_c, sun_c, sun_i, amb_i, fog_d = self.director.sky_at(self.director.hour)
            px, pz = 34 * math.sin(t * 0.05), 34 * math.cos(t * 0.05)
            self.menu_rig.position = (px, 13 + math.sin(t * 0.11) * 2.5, pz)
            self.menu_rig.look_at((0, 3, 0))
            self.apply_environment(sky, fog_c, sun_c, sun_i, amb_i, fog_d * 1.4, self.director.hour)
            return
        if self.state in ("paused", "dead"):
            return
        # fps counter
        self._fps_acc += dt
        self._fps_frames += 1
        if self._fps_acc >= 0.5:
            self._fps_val = self._fps_frames / self._fps_acc
            self._fps_acc, self._fps_frames = 0.0, 0
            alive = sum(1 for z in self.zombies if not z.dead)
            self.hud.fps_text.text = (f"{self._fps_val:.0f} FPS • зомби: {alive}" if self.hud.show_fps else "")

        self.time_played += dt
        if self.state == "inventory":
            # The world keeps living while you rummage (zombies can still
            # get you), but the character takes no movement/weapon input.
            self.director.update(dt)
            self.world.update(dt)
            for z in list(self.zombies):
                z.update(dt)
            for npc in self.npcs:
                npc.update(dt)
            self.stats.update(dt, ambient_temp_c=self.director.ambient_temp,
                              wet=self.director.weather in ("rain", "storm"))
            if self.stats.dead:
                self.die("Вы не пережили этот мир.")
                return
            found = self.find_interactable()
            self.hud.prompt.text = self.interact_prompt(found)
            self.hud.update(dt, self)
            return
        self.player.update(dt)
        if self.stats.dead and self.state == "playing":
            cause = ("Инфекция превратила вас в одного из них." if self.stats.infection >= 100
                     else "Вы не пережили этот мир.")
            self.die(cause)
            return
        self.director.update(dt)
        self.world.update(dt)
        for z in list(self.zombies):
            z.update(dt)
        for npc in self.npcs:
            npc.update(dt)
        for pk in self.pickups:
            pk.update(dt)
        for v in self.vehicles:
            if v is not self.player.vehicle:
                v.update(dt)
        # heartbeat at low hp
        if self.stats.health < 26 and int(self.time_played * 1.15) != int((self.time_played - dt) * 1.15):
            self.audio.play("heartbeat", 0.5)

        found = self.find_interactable()
        self.hud.prompt.text = self.interact_prompt(found)
        self.hud.update(dt, self)

        self.autosave_t -= dt
        if self.autosave_t <= 0:
            self.autosave_t = AUTOSAVE_INTERVAL
            self.save_to("autosave")


# ======================================================================
# Startup: the engine run loop starts FIRST (splash visible and the
# window painting from the very first frame), the game then builds in
# time-sliced steps inside _Loader.update(). v0.1.0 built everything
# before app.run(); v0.1.1 pumped taskMgr.step() by hand before
# app.run(). Both could leave a white, unpainted window on Windows.
# ======================================================================

def _exe_dir():
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.getcwd()


def _flag_file(name):
    """Escape-hatch flag files placed next to the EXE (see README):
    software_render.txt -> CPU renderer if the OpenGL driver is broken;
    no_audio.txt -> null audio device if audio init hangs."""
    for base in (_exe_dir(), os.getcwd(), resource_root()):
        try:
            if os.path.exists(os.path.join(base, name)):
                return True
        except Exception:
            pass
    return False


def _preload_model_steps(lo=0.04, hi=0.34):
    """Pre-convert every .obj into Ursina's mesh cache by DIRECT file path.

    Entity(model='assets/.../x.obj') otherwise triggers a recursive
    glob('**/...') over the whole application folder for every model -
    inside a PyInstaller bundle that is thousands of files per lookup."""
    try:
        from ursina.mesh_importer import imported_meshes, obj_to_ursinamesh
    except Exception:
        return
    root = Path(resource_root())
    files = sorted((root / "assets" / "models").rglob("*.obj"))
    total = len(files)
    for i, f in enumerate(files):
        key = f.relative_to(root).with_suffix("").as_posix()
        if key not in imported_meshes:
            try:
                m = obj_to_ursinamesh(path=f.parent, name=f.stem, return_mesh=True)
            except Exception:
                m = None
            if m is not None:
                try:
                    m.path = f
                    m.name = key
                except Exception:
                    pass
                imported_meshes[key] = m
        if i % 6 == 0 or i == total - 1:
            yield (f"Загрузка моделей: {i + 1}/{total}", lo + (hi - lo) * (i + 1) / max(1, total))


def _preload_models(progress):
    n = 0
    for label, frac in _preload_model_steps():
        n += 1
        if progress:
            progress(label, frac)
    return n


class _Loader(Entity):
    """Advances the build generator inside the normal run loop, so real
    frames (with the loading splash) are presented while building."""

    BUDGET = 0.04    # seconds of build work per frame

    def __init__(self, steps_factory, on_progress, on_done, on_error, log):
        super().__init__()
        self._factory = steps_factory
        self._on_progress = on_progress
        self._on_done = on_done
        self._on_error = on_error
        self._log = log
        self._gen = None
        self.done = False
        self.error = None
        self._first = True

    def update(self):
        if self.done:
            return
        if self._first:
            self._first = False
            self._log("first frame presented - run loop is live")
        import time as _time
        if self._gen is None:
            self._gen = self._factory()
        deadline = _time.time() + self.BUDGET
        while True:
            try:
                label, frac = next(self._gen)
            except StopIteration:
                self.done = True
                self.enabled = False
                self._on_done()
                return
            except Exception as exc:
                self.done = True
                self.error = exc
                self.enabled = False
                self._on_error(exc)
                return
            self._on_progress(label, frac)
            if _time.time() >= deadline:
                return


class _GuiTest(Entity):
    """Automated graphical test run by Windows CI against the BUILT exe
    (--guitest): real run loop, menu -> new game -> fire -> kill ->
    save/load -> screenshot must not be a uniform white frame."""

    def __init__(self, app, game, log):
        super().__init__()
        import time as _time
        self.app = app
        self.game = game
        self._log = log
        self.frame = 0
        self.t0 = _time.time()
        self._kills_ok = False
        self.menu_stats = None

    def _marker(self, text):
        try:
            with open(os.path.join(_exe_dir(), "guitest_result.txt"), "w", encoding="utf-8") as f:
                f.write(text + "\n")
        except Exception:
            pass

    def _fail(self, msg):
        self._log("GUITEST_FAIL: " + msg)
        self._marker("GUITEST_FAIL " + msg)
        os._exit(3)

    def _renderer_tag(self):
        try:
            gsg = self.app.win.gsg
            return (f"pipe={self.app.pipe.getType()} vendor={gsg.getDriverVendor()!r} "
                    f"version={gsg.getDriverVersion()!r} glsl={bool(gsg.getSupportsGlsl())}")
        except Exception as e:
            return f"unknown({e})"

    def _shot(self, name):
        from panda3d.core import PNMImage, Filename
        img = PNMImage()
        ok = self.app.win.getScreenshot(img)
        path = os.path.join(_exe_dir(), name)
        try:
            img.write(Filename.fromOsSpecific(path))
        except Exception:
            pass
        w, h = img.getXSize(), img.getYSize()
        samples = []
        for yy in range(0, max(1, h), 17):
            for xx in range(0, max(1, w), 17):
                try:
                    samples.append(img.getXel(xx, yy))
                except Exception:
                    pass
        if not samples:
            return dict(ok=False, mean=255.0, distinct=0)
        mean = sum((c.getX() + c.getY() + c.getZ()) / 3.0 for c in samples) / len(samples)
        distinct = len({(round(c.getX(), 2), round(c.getY(), 2), round(c.getZ(), 2)) for c in samples})
        self._log(f"guitest shot {name}: ok={ok} {w}x{h} mean={mean:.3f} distinct={distinct}")
        return dict(ok=bool(ok), mean=mean, distinct=distinct)

    def update(self):
        import time as _time
        try:
            self.frame += 1
            g = self.game
            if _time.time() - self.t0 > 240:
                self._fail("timeout")
            if self.frame == 5:
                assert g.state == "menu", f"expected menu, got {g.state}"
                # pixel stats recorded into the marker; not asserted here -
                # the software renderer has no GLSL, so untextured UI quads
                # render white in this harness even in a healthy build
                self.menu_stats = self._shot("guitest_menu.png")
            elif self.frame == 8:
                g.new_game()
            elif self.frame == 40:
                g.player.try_fire()
            elif self.frame == 70 and g.zombies:
                z = g.zombies[0]
                z.damage(120, "head", g.player.pos_xz, (0, 1))
            elif self.frame == 100:
                self._kills_ok = g.kills >= 1
                g.save_to("slot3")
                g.load_from("slot3")
            elif self.frame >= 130:
                stats = self._shot("guitest_game.png")
                m = self.menu_stats or {}
                assert m.get("mean", 1.0) < 0.95 and m.get("distinct", 0) > 10, \
                    f"MENU FRAME IS BLANK/WHITE: menu stats={m} (color-scale regression?)"
                assert g.state == "playing", f"state={g.state}"
                assert len(g.zombies) > 5, f"zombies={len(g.zombies)}"
                assert self._kills_ok, "kill was not registered"
                assert stats["ok"], "screenshot failed"
                assert stats["mean"] < 0.97, f"frame is (near) white: mean={stats['mean']:.3f}"
                assert stats["distinct"] > 25, f"frame looks uniform: distinct={stats['distinct']}"
                m = self.menu_stats or {}
                summary = (f"GUITEST_OK renderer={self._renderer_tag()} menu_mean={m.get('mean', -1):.3f} menu_distinct={m.get('distinct', -1)} "
                           f"game_mean={stats['mean']:.3f} game_distinct={stats['distinct']} "
                           f"zombies={len(g.zombies)} kills={g.kills} pickups={len(g.pickups)} "
                           f"vehicles={len(g.vehicles)} day={g.director.day}")
                self._log(summary)
                self._marker(summary)
                os._exit(0)
        except Exception as exc:
            import traceback as _tb
            print(_tb.format_exc(), flush=True)
            self._fail(str(exc))


def run_game(headless=False, guitest=False):
    import time as _time
    import faulthandler
    import traceback as _tb
    boot_t0 = _time.time()

    def log(msg):
        try:
            print(f"[{_time.time() - boot_t0:7.2f}s] {msg}", flush=True)
        except Exception:
            pass

    log("DEAD WORLD: APOCALYPSE starting (run_game entered)")

    def _excepthook(exc_type, exc, tb):
        log("FATAL EXCEPTION:\n" + "".join(_tb.format_exception(exc_type, exc, tb)))
        sys.__excepthook__(exc_type, exc, tb)

    sys.excepthook = _excepthook

    settings = load_settings()
    log(f"settings: res={settings.resolution} fullscreen={settings.fullscreen} quality={settings.quality}")

    gl_test = guitest and os.environ.get("DW_GUITEST_GL") == "1"
    software = (guitest and not gl_test) or _flag_file("software_render.txt")
    no_audio = guitest or _flag_file("no_audio.txt")
    try:
        from panda3d.core import loadPrcFileData
        if software:
            loadPrcFileData("", "load-display p3tinydisplay\n")
        if no_audio:
            loadPrcFileData("", "audio-library-name null\n")
        if guitest:
            loadPrcFileData("", "win-size 854 480\n")
    except Exception:
        log("PRC setup failed:\n" + _tb.format_exc())
    log(f"renderer: {'software (tinydisplay)' if software else 'OpenGL'}, no_audio={no_audio}, "
        f"headless={headless}, guitest={guitest}")

    from ursina import Ursina
    kwargs = dict(title="DEAD WORLD: APOCALYPSE", borderless=False,
                  fullscreen=bool(settings.fullscreen) and not headless and not guitest and not software,
                  size=(854, 480) if (guitest or software) else settings.res_tuple(),
                  vsync=not guitest, development_mode=False,
                  show_ursina_splash=False, editor_ui_enabled=False)
    if headless:
        kwargs["window_type"] = "none"
    # CI can force a real on-screen window (software renderer still) via
    # DW_GUITEST_ONSCREEN=1 - closest to a user launch; offscreen is the
    # fallback when the runner session cannot create windows.
    onscreen_test = gl_test or (guitest and os.environ.get("DW_GUITEST_ONSCREEN") == "1")
    if guitest and not onscreen_test:
        kwargs["window_type"] = "offscreen"
    log(f"creating window (onscreen_test={onscreen_test})...")
    app = Ursina(**kwargs)
    no_glsl = False
    try:
        _gsg = app.win.gsg
        _glsl = bool(_gsg.getSupportsGlsl())
        no_glsl = not _glsl
        log(f"window created pipe={app.pipe.getType()} vendor={_gsg.getDriverVendor()!r} "
            f"version={_gsg.getDriverVersion()!r} glsl={_glsl}")
        if no_glsl:
            log("GLSL unsupported on this renderer - compat mode: shadows off, sky fixed-function")
    except Exception:
        log("window created (renderer caps unavailable):\n" + _tb.format_exc())
    if headless and not hasattr(camera, "lens"):
        try:
            from panda3d.core import PerspectiveLens
            camera.lens = PerspectiveLens()
        except Exception:
            pass
    # asset folder must be the game root (dev or frozen _MEIPASS / exe dir)
    application.asset_folder = Path(resource_root())
    try:
        import ursina.texture_importer as _ti
        root = Path(resource_root())
        _ti.folders = [root, root / "assets" / "textures"] + list(_ti.folders)
    except Exception:
        pass
    # Never write .bam caches at runtime: slow on Windows and pointless
    # with the in-memory preload; read-only install dirs would fail too.
    try:
        import ursina.mesh as _meshmod
        _meshmod.Mesh.save = lambda self, *a, **k: None
    except Exception:
        pass
    # UI font: Ursina's bundled OpenSans already ships Cyrillic glyphs (verified).
    application.development_mode = False
    from game import __version__ as _ver
    window.title = f"DEAD WORLD: APOCALYPSE v{_ver}"
    window.exit_button_visible = False
    window.fps_counter_enabled = False
    try:
        window.clear_color = color.rgb32(8, 8, 11)
    except Exception:
        pass

    # ---- loading splash (drawn by the normal run loop from frame #1)
    splash = Entity(parent=camera.ui)
    Entity(parent=splash, model="quad", scale=(3, 2), color=color.rgb32(8, 8, 11), z=0.1)
    Text("DEAD WORLD", parent=splash, position=(0, 0.16), origin=(0, 0), scale=2.6,
         color=color.rgb32(214, 40, 34))
    Text("APOCALYPSE", parent=splash, position=(0, 0.085), origin=(0, 0), scale=1.3,
         color=color.rgb32(232, 224, 206))
    splash_status = Text("Загрузка...", parent=splash, position=(0, -0.06), origin=(0, 0),
                         scale=0.95, color=color.rgb32(205, 210, 215))
    Entity(parent=splash, model="quad", position=(0, -0.13), scale=(0.52, 0.02),
           color=color.rgb32(34, 34, 40))
    splash_bar = Entity(parent=splash, model="quad", position=(-0.26, -0.13, -0.01),
                        scale=(0.001, 0.014), color=color.rgb32(214, 60, 44), origin=(-0.5, 0))
    Text("первый запуск может занять до минуты", parent=splash, position=(0, -0.2),
         origin=(0, 0), scale=0.65, color=color.rgb32(140, 145, 155))
    Text(f"v{_ver}", parent=splash, position=(0.42, -0.44), origin=(0, 0), scale=0.7,
         color=color.rgb32(150, 155, 165))
    splash_error = Text("", parent=splash, position=(0, -0.28), origin=(0, 0), scale=0.8,
                        color=color.rgb32(255, 120, 100))
    splash_error.enabled = False

    holder = {}

    def _all_steps():
        yield ("Подготовка...", 0.02)
        yield from _preload_model_steps()
        log("models preloaded into cache")
        game = Game(settings)
        game._no_glsl = no_glsl
        holder["game"] = game
        yield from game.build_steps()
        yield ("Готово", 1.0)

    def on_progress(label, frac):
        log(f"load {int(frac * 100):3d}%  {label}")
        try:
            splash_status.text = label
            f = min(1.0, max(0.0, frac))
            splash_bar.scale_x = max(0.001, 0.52 * f)
            splash_bar.x = -0.26 + 0.52 * f / 2
        except Exception:
            pass

    def on_done():
        try:
            faulthandler.cancel_dump_traceback_later()
        except Exception:
            pass
        destroy(splash)
        log(f"startup complete in {_time.time() - boot_t0:.1f}s - entering menu")
        if guitest:
            _GuiTest(app, holder["game"], log)

    def on_error(exc):
        log("LOADING FAILED:\n" + "".join(_tb.format_exception(type(exc), exc, exc.__traceback__)))
        try:
            from game.config import user_data_dir
            log_path = os.path.join(user_data_dir(), "startup.log")
        except Exception:
            log_path = "startup.log"
        try:
            splash_status.text = "Ошибка загрузки игры"
            splash_error.text = f"{type(exc).__name__}: {exc}\nПодробности: {log_path}"
            splash_error.enabled = True
        except Exception:
            pass

    loader = _Loader(_all_steps, on_progress, on_done, on_error, log)
    log("entering run loop (loading continues inside it)")
    if headless:
        guard = 0
        while not loader.done and guard < 200000:
            app.taskMgr.step()
            guard += 1
        if loader.error is not None:
            raise RuntimeError(f"headless loading failed: {loader.error}")
        return app, holder["game"]
    app.run()
    return None
