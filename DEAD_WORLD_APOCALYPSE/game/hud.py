"""HUD & in-game UI (Russian): vitals, hotbar, ammo, crosshair, minimap,
objective tracker, notifications, interaction prompt, damage vignette,
inventory screen, vehicle dashboard, help overlay."""
import math
from ursina import Entity, Text, Button, camera, color, held_keys

from game.data import ITEMS, WEAPONS

BAR_BG = color.rgba32(10, 10, 12, 170)


def _bar(parent, x, y, w, h, col, label):
    bg = Entity(parent=parent, model="quad", position=(x, y), scale=(w, h), color=BAR_BG)
    fill = Entity(parent=parent, model="quad", position=(x, y, -0.01), scale=(w, h * 0.72),
                  color=col, origin=(-0.5, 0))
    fill.x = x - w / 2
    txt = Text(label, parent=parent, position=(x - w / 2 + 0.004, y + h * 0.62), scale=0.72,
               color=color.rgb32(230, 230, 225))
    return {"bg": bg, "fill": fill, "w": w, "txt": txt, "x": x}


def _set_bar(bar, frac):
    frac = max(0.0, min(1.0, frac))
    bar["fill"].scale_x = bar["w"] * frac
    bar["fill"].x = bar["x"] - bar["w"] / 2 + bar["w"] * frac / 2


class HUD:
    def __init__(self, game):
        self.game = game
        self.root = Entity(parent=camera.ui)
        ui = self.root
        self.bars = {
            "health":  _bar(ui, -0.72, -0.40, 0.30, 0.030, color.rgb32(196, 44, 40), "ЗДОРОВЬЕ"),
            "stamina": _bar(ui, -0.72, -0.445, 0.30, 0.022, color.rgb32(80, 180, 90), "ВЫНОСЛИВОСТЬ"),
            "hunger":  _bar(ui, -0.72, -0.482, 0.145, 0.018, color.rgb32(220, 150, 60), "ГОЛОД"),
            "thirst":  _bar(ui, -0.565, -0.482, 0.145, 0.018, color.rgb32(70, 150, 220), "ЖАЖДА"),
        }
        self.warn_text = Text("", parent=ui, position=(-0.87, -0.315), scale=0.85,
                              color=color.rgb32(255, 90, 70))
        self.temp_text = Text("", parent=ui, position=(-0.87, -0.345), scale=0.75,
                              color=color.rgb32(200, 205, 215))
        # crosshair
        self.cross = []
        for dx, dy, sx, sy in [(0, 0.018, 0.0022, 0.014), (0, -0.018, 0.0022, 0.014),
                               (0.016, 0, 0.013, 0.0022), (-0.016, 0, 0.013, 0.0022)]:
            self.cross.append(Entity(parent=ui, model="quad", position=(dx, dy),
                                     scale=(sx, sy), color=color.rgba32(240, 240, 235, 200)))
        self.hitmarker = Text("✕", parent=ui, position=(0, 0), origin=(0, 0), scale=1.1,
                              color=color.rgb32(255, 80, 60))
        self.hitmarker.enabled = False
        self.hit_t = 0.0
        # weapon / ammo
        self.weapon_text = Text("", parent=ui, position=(0.86, -0.36), origin=(1, 0), scale=1.0,
                                color=color.rgb32(240, 238, 230))
        self.ammo_text = Text("", parent=ui, position=(0.86, -0.405), origin=(1, 0), scale=1.5,
                              color=color.rgb32(250, 246, 235))
        # hotbar
        self.hotbar_cells = []
        self.hotbar_labels = []
        for i in range(6):
            x = -0.165 + i * 0.066
            cell = Entity(parent=ui, model="quad", position=(x, -0.452), scale=(0.06, 0.06),
                          color=color.rgba32(16, 16, 20, 190))
            num = Text(str(i + 1), parent=ui, position=(x - 0.026, -0.425), scale=0.62,
                       color=color.rgb32(180, 180, 175))
            lab = Text("", parent=ui, position=(x, -0.462), origin=(0, 0), scale=0.55,
                       color=color.rgb32(235, 232, 220))
            self.hotbar_cells.append(cell)
            self.hotbar_labels.append(lab)
        self.hotbar_sel = Entity(parent=ui, model="quad", position=(-0.165, -0.452, 0.01),
                                 scale=(0.066, 0.066), color=color.rgba32(255, 200, 80, 90))
        # objective + clock
        self.objective_text = Text("", parent=ui, position=(-0.87, 0.462), scale=0.9,
                                   color=color.rgb32(255, 214, 130))
        self.clock_text = Text("", parent=ui, position=(-0.87, 0.425), scale=0.85,
                               color=color.rgb32(205, 212, 225))
        self.kill_text = Text("", parent=ui, position=(-0.87, 0.395), scale=0.8,
                              color=color.rgb32(190, 195, 205))
        # prompt + notifications
        self.prompt = Text("", parent=ui, position=(0, -0.16), origin=(0, 0), scale=1.0,
                           color=color.rgb32(255, 240, 200))
        self.notify_big = Text("", parent=ui, position=(0, 0.24), origin=(0, 0), scale=1.7,
                               color=color.rgb32(255, 120, 90))
        self.notify_text = Text("", parent=ui, position=(0, 0.155), origin=(0, 0), scale=1.0,
                                color=color.rgb32(240, 240, 230))
        self.notify_t = 0.0
        self.notify_big_t = 0.0
        # damage vignette + low-hp pulse
        self.vignette = Entity(parent=ui, model="quad", scale=(2.2, 1.3), position=(0, 0, 0.05),
                               color=color.rgba32(160, 0, 0, 0))
        self.vignette_alpha = 0.0
        # minimap
        self.map_bg = Entity(parent=ui, model="quad", position=(0.755, 0.355), scale=(0.21, 0.21),
                             color=color.rgba32(12, 16, 12, 205))
        self.map_player = Entity(parent=ui, model="quad", position=(0.755, 0.355, -0.02),
                                 scale=(0.012, 0.012), color=color.rgb32(255, 255, 255), rotation_z=45)
        self.map_dots = []
        for _ in range(46):
            d = Entity(parent=ui, model="quad", scale=(0.008, 0.008), position=(0.755, 0.355, -0.02),
                       color=color.rgb32(255, 60, 50), enabled=False)
            self.map_dots.append(d)
        self.map_label = Text("КАРТА", parent=ui, position=(0.755, 0.475), origin=(0, 0), scale=0.65,
                              color=color.rgb32(190, 200, 190))
        # vehicle dash
        self.vehicle_text = Text("", parent=ui, position=(0.86, -0.30), origin=(1, 0), scale=0.95,
                                 color=color.rgb32(170, 220, 255))
        # boss bar
        self.boss_bar = _bar(ui, 0.0, 0.40, 0.5, 0.026, color.rgb32(160, 30, 160), "ГИГАНТ")
        for part in self.boss_bar.values():
            if hasattr(part, "enabled"):
                part.enabled = False
        self.fps_text = Text("", parent=ui, position=(0.87, 0.47), origin=(1, 0), scale=0.65,
                             color=color.rgb32(160, 170, 160))
        self.show_fps = False
        # inventory screen
        self.inv_root = Entity(parent=camera.ui, enabled=False)
        self._build_inventory()
        # help overlay
        self.help_root = Entity(parent=camera.ui, enabled=False)
        self._build_help()
        self.root.enabled = False

    # ---------------------------------------------------------------- inventory
    def _build_inventory(self):
        r = self.inv_root
        Entity(parent=r, model="quad", scale=(0.86, 0.78), color=color.rgba32(14, 15, 18, 240))
        Text("ИНВЕНТАРЬ", parent=r, position=(0, 0.345), origin=(0, 0), scale=1.4,
             color=color.rgb32(255, 214, 130))
        self.inv_info = Text("", parent=r, position=(-0.4, 0.30), scale=0.8,
                             color=color.rgb32(205, 210, 215))
        self.inv_cells = []
        self.inv_labels = []
        for row in range(4):
            for coln in range(6):
                x = -0.375 + coln * 0.15
                y = 0.22 - row * 0.13
                b = Button(parent=r, text=" ", model="quad", position=(x, y), scale=(0.14, 0.11),
                           color=color.rgba32(34, 36, 42, 255))
                if b.text_entity:
                    b.text_entity.scale = 0.62
                    b.text_entity.color = color.rgb32(235, 232, 220)
                idx = row * 6 + coln
                b.on_click = (lambda i=idx: self.game.on_inventory_click(i))
                self.inv_cells.append(b)
        self.inv_ammo = Text("", parent=r, position=(-0.4, -0.30), scale=0.8,
                             color=color.rgb32(205, 210, 215))
        hint = ("ЛКМ — использовать / экипировать    SHIFT+ЛКМ — выбросить\n"
                "Оружие автоматически попадает на панель быстрого доступа (1–6)")
        Text(hint, parent=r, position=(0, -0.355), origin=(0, 0), scale=0.68,
             color=color.rgb32(160, 165, 175))
        sort_b = Button(parent=r, text="Сортировать", position=(0.30, 0.30), scale=(0.17, 0.045),
                        color=color.rgb32(52, 56, 66))
        sort_b.text_entity.scale = 0.8
        sort_b.on_click = self.game.on_inventory_sort
        close_b = Button(parent=r, text="Закрыть (Tab)", position=(0.30, -0.30), scale=(0.17, 0.045),
                         color=color.rgb32(52, 56, 66))
        close_b.text_entity.scale = 0.8
        close_b.on_click = self.game.close_inventory

    def _build_help(self):
        r = self.help_root
        Entity(parent=r, model="quad", scale=(0.8, 0.85), color=color.rgba32(12, 13, 16, 242))
        Text("УПРАВЛЕНИЕ", parent=r, position=(0, 0.37), origin=(0, 0), scale=1.3,
             color=color.rgb32(255, 214, 130))
        lines = [
            "WASD — движение, мышь — обзор",
            "SHIFT — спринт (шум привлекает зомби!)",
            "CTRL — пригнуться (тише и незаметнее)",
            "SPACE — прыжок",
            "ЛКМ — атака / огонь, ПКМ — прицеливание",
            "R — перезарядка, 1–6 / колесо — оружие",
            "E — действие: подобрать, дверь, машина, разговор",
            "F — заправить машину (нужна канистра)",
            "Tab / I — инвентарь, L — фонарь",
            "F5 — быстрое сохранение, F9 — загрузка",
            "F1 — справка, F3 — FPS, Esc — пауза",
            "",
            "Выстрелы и спринт слышат все в округе.",
            "Кровотечение — бинт. Инфекция — антибиотики.",
            "Крикун призывает орду — убивайте его первым.",
            "Ночью холодно и темно. Держитесь света фонарей.",
        ]
        Text("\n".join(lines), parent=r, position=(-0.36, 0.31), scale=0.78,
             color=color.rgb32(225, 228, 232))

    # ---------------------------------------------------------------- runtime
    def notify(self, msg, bad=False, big=False):
        if big:
            self.notify_big.text = msg
            self.notify_big.color = color.rgb32(255, 110, 80) if bad else color.rgb32(255, 214, 130)
            self.notify_big_t = 3.2
        else:
            self.notify_text.text = msg
            self.notify_text.color = color.rgb32(255, 130, 100) if bad else color.rgb32(240, 240, 230)
            self.notify_t = 3.4

    def show_hitmarker(self, kill=False):
        self.hitmarker.enabled = True
        self.hitmarker.color = color.rgb32(255, 60, 40) if kill else color.rgb32(255, 220, 120)
        self.hit_t = 0.16

    def hurt_flash(self, amount=0.45):
        self.vignette_alpha = min(0.75, self.vignette_alpha + amount)

    def refresh_inventory(self, inv):
        for i, cell in enumerate(self.inv_cells):
            if i < len(inv.slots):
                s = inv.slots[i]
                name = ITEMS[s["id"]]["name"]
                cnt = f"\nx{s['count']}" if s["count"] > 1 else ""
                cell.text = name + cnt
                cell.color = color.rgba32(44, 48, 58, 255)
            else:
                cell.text = ""
                cell.color = color.rgba32(26, 27, 32, 255)
        self.inv_info.text = f"Вес: {inv.total_weight():.1f} / {inv.max_weight:.0f} кг"
        am = inv.ammo
        self.inv_ammo.text = (f"9мм: {am['ammo_9mm']}    5.56: {am['ammo_rifle']}    "
                              f"12к: {am['ammo_shells']}    Болты: {am['ammo_bolts']}")

    def refresh_hotbar(self, inv, selected):
        for i, (cell, lab) in enumerate(zip(self.hotbar_cells, self.hotbar_labels)):
            item = inv.hotbar[i]
            if item:
                lab.text = WEAPONS[item]["name"].split()[0][:7] if item in WEAPONS else ITEMS[item]["name"][:7]
            else:
                lab.text = ""
        if 0 <= selected < 6:
            self.hotbar_sel.x = -0.165 + selected * 0.066
            self.hotbar_sel.enabled = True
        else:
            self.hotbar_sel.enabled = False

    def update(self, dt, game):
        st = game.stats
        _set_bar(self.bars["health"], st.health / 100)
        _set_bar(self.bars["stamina"], st.stamina / 100)
        _set_bar(self.bars["hunger"], st.hunger / 100)
        _set_bar(self.bars["thirst"], st.thirst / 100)
        warns = []
        wm = {"health": "КРИТИЧЕСКОЕ ЗДОРОВЬЕ", "hunger": "ГОЛОД", "thirst": "ЖАЖДА",
              "bleeding": "КРОВОТЕЧЕНИЕ!", "infection": f"ИНФЕКЦИЯ {int(st.infection)}%",
              "cold": "ПЕРЕОХЛАЖДЕНИЕ", "fever": "ЛИХОРАДКА"}
        for w in st.warnings():
            warns.append(wm.get(w, w))
        self.warn_text.text = "  ".join(warns)
        self.temp_text.text = f"t° тела: {st.temperature:.1f}°C   на улице: {game.director.ambient_temp:.0f}°C"

        wid = game.player.current_weapon
        if wid and wid in WEAPONS:
            w = WEAPONS[wid]
            self.weapon_text.text = w["name"]
            if w["kind"] == "gun":
                mag = game.inventory.mags.get(wid, 0)
                reserve = game.inventory.ammo.get(w["ammo"], 0)
                self.ammo_text.text = f"{mag} / {reserve}"
                self.ammo_text.color = color.rgb32(255, 90, 70) if mag == 0 else color.rgb32(250, 246, 235)
            else:
                self.ammo_text.text = "—"
        else:
            self.weapon_text.text = "Руки пусты"
            self.ammo_text.text = ""

        d = game.director
        hh = int(d.hour)
        mm = int((d.hour - hh) * 60)
        weather_ru = {"clear": "ясно", "cloudy": "облачно", "rain": "дождь", "fog": "туман", "storm": "гроза"}[d.weather]
        self.clock_text.text = f"День {d.day} • {hh:02d}:{mm:02d} • {weather_ru}"
        self.kill_text.text = f"Убито зомби: {game.kills} (в голову: {game.headshots})"
        obj = game.current_objective()
        self.objective_text.text = "Цель: " + (obj["text"] if obj else "—")

        # timers
        if self.notify_t > 0:
            self.notify_t -= dt
            if self.notify_t <= 0:
                self.notify_text.text = ""
        if self.notify_big_t > 0:
            self.notify_big_t -= dt
            if self.notify_big_t <= 0:
                self.notify_big.text = ""
        if self.hit_t > 0:
            self.hit_t -= dt
            if self.hit_t <= 0:
                self.hitmarker.enabled = False

        # vignette: damage flash decay + low hp pulse
        pulse = 0.22 + 0.1 * math.sin(game.time_played * 4.2) if st.health < 32 else 0.0
        self.vignette_alpha = max(0.0, self.vignette_alpha - dt * 1.4)
        a = int(255 * min(0.8, max(self.vignette_alpha, pulse)))
        self.vignette.color = color.rgba32(160, 0, 0, a)

        # minimap
        p = game.player_pos()
        yaw = math.radians(game.player.yaw_deg)
        rng_m = 70.0
        ms = 0.21 / 2
        dots = self.map_dots
        di = 0

        def place(world_x, world_z, col, scale=0.008):
            nonlocal di
            if di >= len(dots):
                return
            dx = world_x - p[0]
            dz = world_z - p[2]
            # rotate into player-facing frame (up = forward)
            rx = dx * math.cos(yaw) - dz * math.sin(yaw)
            rz = dx * math.sin(yaw) + dz * math.cos(yaw)
            dist = math.hypot(rx, rz)
            if dist > rng_m:
                s = rng_m / dist
                rx *= s
                rz *= s
            dot = dots[di]
            di += 1
            dot.enabled = True
            dot.color = col
            dot.scale = (scale, scale)
            dot.position = (0.755 + rx / rng_m * ms, 0.355 + rz / rng_m * ms, -0.02)

        for z in game.zombies:
            if not z.dead:
                place(z.root.x, z.root.z, color.rgb32(255, 60, 50),
                      0.013 if z.cfg.get("boss") else 0.008)
        for v in game.vehicles:
            if v.drivable:
                place(v.body.x, v.body.z, color.rgb32(90, 170, 255), 0.01)
        for npc in game.npcs:
            place(npc.root.x, npc.root.z, color.rgb32(110, 255, 140), 0.009)
        mark = game.objective_marker()
        if mark:
            place(mark[0], mark[1], color.rgb32(255, 214, 90), 0.012)
        for k in range(di, len(dots)):
            dots[k].enabled = False

        # vehicle dash
        if game.player.vehicle:
            v = game.player.vehicle
            self.vehicle_text.text = (f"{v.stats['name']} • {abs(v.speed) * 3.6:.0f} км/ч\n"
                                      f"Топливо: {v.fuel:.0f}% • Прочность: {max(0, v.hp):.0f}")
        else:
            self.vehicle_text.text = ""

        # boss bar
        boss = getattr(game, "boss_ref", None)
        show_boss = boss is not None and not boss.dead
        for part in self.boss_bar.values():
            if hasattr(part, "enabled"):
                part.enabled = show_boss
        if show_boss:
            _set_bar(self.boss_bar, boss.hp / boss.max_hp)
