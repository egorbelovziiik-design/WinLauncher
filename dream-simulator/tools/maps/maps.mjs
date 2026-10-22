/**
 * maps.mjs — 10 подробных карт снов + маркеры интерактивных точек.
 * Каждая карта: земля, постройки, реквизит, свет, туман и маркеры MK_*.
 */
import { el, v3, v2, cf, rgb, c3, nr, ns, cs, font, udim2, udim } from '../rbxkit.mjs';
import {
  M, box, deco, sphere, cyl, wedge, pointLight, mist, particles, sign, billboardText,
  ground, wall, windowFrame, building, tree, pine, streetLamp, fence, crate, barrel,
  table, chair, shelf, bookStack, pcSetup, rug, poster, plant, bed, toy, wardrobe,
  marker, model, flat, fogEmitter,
} from './kit.mjs';

const DARK = rgb(10, 12, 20);

/* ------------------------------------------------------------------ */
/* 1. ТЁМНЫЙ ЛЕС                                                       */
/* ------------------------------------------------------------------ */
function darkForest() {
  const p = [];
  p.push(ground(420, 420, rgb(18, 30, 20), M.Grass));
  // Тропа
  for (let i = -20; i <= 20; i++) {
    p.push(deco([Math.sin(i * 0.5) * 22, 0.1, i * 12], [16, 0.2, 12], rgb(48, 40, 30), M.Pebble));
  }
  // Деревья по сетке с шумом
  for (let x = -180; x <= 180; x += 26) {
    for (let z = -180; z <= 180; z += 26) {
      if (Math.abs(x) < 26 && Math.abs(z) < 120) continue;
      const jx = x + (Math.random() * 10 - 5);
      const jz = z + (Math.random() * 10 - 5);
      const s = 0.8 + Math.random() * 0.7;
      p.push(...tree([jx, 0, jz], s, rgb(48, 34, 24), rgb(18 + Math.random() * 12, 48, 30)));
    }
  }
  // Хижина смотрителя
  p.push(box([46, 0.6, -34], [26, 1.2, 22], rgb(40, 30, 22), M.WoodPlanks, { canCollide: true }));
  p.push(...[0, 1, 2, 3].map((i) => box([46, 0.6 + i * 5, -34], [26, 0.4, 22], rgb(52, 40, 28), M.WoodPlanks)));
  for (const [dx, dz, w, d] of [[0, -11, 26, 0.8], [0, 11, 26, 0.8], [-13, 0, 0.8, 22], [13, 0, 0.8, 22]]) {
    p.push(box([46 + dx, 6, -34 + dz], [w, 12, d], rgb(52, 38, 26), M.WoodPlanks, { canCollide: true }));
  }
  // Крыша
  for (let i = 0; i < 6; i++) {
    p.push(wedge([46, 11 + i * 1.4, -34 - 11 + i * 3.4], [28, 1.6, 7], rgb(34, 26, 22), M.RoofShingles, { pitch: 0 }));
    p.push(wedge([46, 11 + i * 1.4, -34 + 11 - i * 3.4], [28, 1.6, 7], rgb(34, 26, 22), M.RoofShingles, { pitch: 0, yaw: 180 }));
  }
  // Окно хижины с тёплым светом
  p.push(box([32.6, 6, -34], [0.5, 4, 5], rgb(255, 190, 110), M.Neon, { transparency: 0.2 }));
  p.push(box([59.4, 6, -34], [0.5, 4, 5], rgb(255, 190, 110), M.Neon, { transparency: 0.2 }));
  p.push(box([46, 15, -34], [1, 1, 1], rgb(255, 200, 140), M.SmoothPlastic, { transparency: 1, canCollide: false }));
  // Дверь
  p.push(box([46, 4, -23.2], [6, 7, 0.6], rgb(30, 22, 16), M.Wood, { canCollide: true }));
  // Колодец
  p.push(cyl([-40, 2, 30], [10, 4, 10], rgb(70, 70, 76), M.Cobblestone));
  p.push(cyl([-40, 4.2, 30], [8, 0.4, 8], rgb(10, 20, 30), M.Water, { transparency: 0.2 }));
  for (const [dx, dz] of [[-4, -4], [4, -4], [-4, 4], [4, 4]]) {
    p.push(box([-40 + dx, 7, 30 + dz], [0.6, 10, 0.6], rgb(46, 34, 24), M.Wood));
  }
  p.push(box([-40, 12.4, 30], [10, 1, 10], rgb(34, 26, 20), M.WoodPlanks));
  // Костёр + брёвна
  p.push(...[0, 1, 2, 3, 4, 5].map((i) => box([0, 0.8, 40], [6, 0.8, 0.9], rgb(48, 34, 24), M.Wood, { rot: { yaw: i * 30 } })));
  p.push(box([0, 0.6, 40], [4, 0.6, 4], rgb(255, 140, 40), M.Neon, { transparency: 0.2 }));
  p.push(el('Part', { Name: 'FireLight', Size: v3(1, 1, 1), CFrame: cf([0, 3, 40]), Transparency: 1, Anchored: true, CanCollide: false, Color: rgb(255, 160, 70) }, [
    pointLight(rgb(255, 150, 60), 5, 40),
    particles([0, 2, 40], { name: 'Sparks', texture: 'rbxasset://textures/particles/sparkles_main.dds', rate: 40, color: c3(1, 0.6, 0.2), life: 0.7, speed: 8, accel: [0, 6, 0], startSize: 0.2, endSize: 0.05, glow: true, transparency: 0 }),
  ]));
  for (const [x, z, yaw] of [[10, 40, 90], [-10, 40, 90], [0, 50, 0]]) {
    p.push(...[box([x, 1.4, z], [9, 1.6, 2.2], rgb(44, 32, 22), M.Wood, { rot: { yaw }, canCollide: true })]);
  }
  // Фонари вдоль тропы
  for (let i = -12; i <= 12; i += 6) {
    if (i === 0) continue;
    p.push(...streetLamp([Math.sin(i * 0.5) * 22 + 12, 0, i * 12], rgb(255, 200, 130), { height: 14, range: 26 }));
  }
  // Забор на границе
  for (let i = -3; i <= 3; i++) {
    p.push(...fence([i * 60 - 30, 0, 190], [i * 60 + 30, 0, 190], rgb(40, 34, 28), 8));
    p.push(...fence([i * 60 - 30, 0, -190], [i * 60 + 30, 0, -190], rgb(40, 34, 28), 8));
  }
  p.push(mist([0, 10, 0], rgb(120, 140, 130), { rate: 30, spread: 220 }));
  p.push(mist([60, 8, 60], rgb(110, 130, 120), { rate: 16, spread: 140 }));
  p.push(...markers({ spawn: [0, 2, 150], portal: [0, 3, 182], npcs: [[20, 1, 20], [-30, 1, 60], [52, 1, -30]],
    items: [[-14, 2, 10], [30, 2, 60], [-50, 2, -20], [12, 2, 96], [-24, 2, 120], [70, 2, 90]],
    interacts: [[46, 1, -23], [-40, 1, 30], [0, 1, 40], [46, 8, -34]],
    reaches: [[-80, 1, -120], [120, 1, 40]], patrols: [[0, 1, 0], [60, 1, -60], [-60, 1, 60], [30, 1, 130], [-120, 1, 20]],
    monsters: [[0, 1, -60], [90, 1, 90]], lamps: [[12, 1, 60], [-12, 1, 84], [12, 1, 12]] }));
  return model('DarkForest', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 2. ЗАБРОШЕННАЯ ШКОЛА                                                */
/* ------------------------------------------------------------------ */
function school() {
  const p = [];
  p.push(ground(240, 240, rgb(30, 32, 38), M.Asphalt));
  p.push(...[0, 1].map((i) => box([0, 0.4 + i * 14, -60], [200, 0.6, 120], rgb(70, 66, 58), M.CeramicTiles, { canCollide: true })));

  // Двухэтажное здание школы
  const bx = -20, bz = -70, bw = 120, bd = 70;
  p.push(box([bx, 14, bz], [bw, 28, bd], rgb(58, 56, 60), M.Concrete, { canCollide: true }));
  p.push(box([bx, 29, bz], [bw + 4, 2, bd + 4], rgb(36, 36, 42), M.Concrete, { canCollide: true }));
  // Окна фасада в 2 ряда
  for (let f = 0; f < 2; f++) {
    for (let i = 0; i < 12; i++) {
      const x = bx - bw / 2 + 8 + i * 9.5;
      const y = 8 + f * 12;
      const lit = Math.random() < 0.22;
      p.push(box([x, y, bz + bd / 2 + 0.4], [5, 6, 0.5], lit ? rgb(210, 220, 180) : rgb(12, 14, 18), lit ? M.Neon : M.Glass, { transparency: 0.15 }));
    }
  }
  // Вход с колоннами
  p.push(box([bx, 5, bz + bd / 2 + 4], [40, 10, 10], rgb(74, 70, 66), M.Concrete, { canCollide: true }));
  p.push(box([bx, 11, bz + bd / 2 + 4], [44, 2, 12], rgb(60, 58, 64), M.Concrete));
  for (const dx of [-16, -6, 6, 16]) {
    p.push(cyl([bx + dx, 6, bz + bd / 2 + 8], [2, 12, 2], rgb(88, 86, 92), M.Concrete));
  }
  // Двери
  for (const dx of [-8, 0, 8]) {
    p.push(box([bx + dx, 5, bz + bd / 2 + 0.5], [6, 10, 0.6], rgb(36, 28, 22), M.Wood, { canCollide: true }));
    p.push(box([bx + dx, 10.4, bz + bd / 2 + 0.9], [2, 1.4, 0.3], rgb(120, 140, 160), M.Metal));
  }
  // Табличка школы
  p.push(sign([bx, 21, bz + bd / 2 + 0.8], [46, 10], 'ШКОЛА № 13', rgb(210, 210, 190), { boardColor: rgb(26, 26, 32), boardMaterial: M.Concrete }));
  // Внутренний коридор (открытая часть здания)
  p.push(box([bx, 0.2, bz], [bw - 6, 0.4, bd - 6], rgb(64, 60, 52), M.CeramicTiles));
  for (let i = 0; i < 8; i++) {
    p.push(box([bx - bw / 2 + 6 + i * 15, 6, bz - bd / 2 + 4], [1, 12, bd - 8], rgb(72, 68, 62), M.Concrete, { canCollide: true }));
  }
  // Парты в классах
  for (let room = 0; room < 6; room++) {
    const rx = bx - bw / 2 + 6 + room * 15 + 7;
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 2; c++) {
        p.push(...table([rx + c * 5 - 2.5, 0.4, bz - 6 + r * 9], [3.4, 0.4, 2.2], rgb(96, 74, 48)));
        p.push(...chair([rx + c * 5 - 2.5, 0.4, bz - 3 + r * 9], rgb(60, 50, 40), { yaw: 180 }));
      }
    }
    // Доска
    p.push(box([rx, 7, bz - 33], [10, 6, 0.4], rgb(22, 34, 26), M.SmoothPlastic, { canCollide: false }));
  }
  // Шкафчики в коридоре
  for (let i = 0; i < 16; i++) {
    p.push(box([-78 + i * 4.2, 5, -102], [4, 9, 2], rgb(40, 70, 90), M.Metal, { canCollide: true }));
    p.push(box([-78 + i * 4.2, 5, -100.9], [3.6, 8, 0.2], rgb(30, 54, 70), M.Metal));
  }
  // Спортзал — отдельный корпус
  p.push(box([90, 10, 30], [70, 20, 50], rgb(52, 50, 54), M.Concrete, { canCollide: true }));
  p.push(box([90, 20.5, 30], [72, 1.5, 52], rgb(34, 34, 40), M.Concrete));
  p.push(box([90, 1, 30], [66, 2, 46], rgb(120, 90, 60), M.WoodPlanks, { canCollide: true }));
  for (const [dx, dz] of [[-32, -24], [32, -24], [-32, 24], [32, 24]]) {
    p.push(cyl([90 + dx, 4, 30 + dz], [1.2, 8, 1.2], rgb(140, 130, 110), M.Metal));
  }
  // Футбольные ворота
  p.push(box([0, 6, 80], [26, 0.9, 0.9], rgb(200, 200, 200), M.Metal));
  p.push(box([-13, 3, 80], [0.9, 6, 0.9], rgb(200, 200, 200), M.Metal));
  p.push(box([13, 3, 80], [0.9, 6, 0.9], rgb(200, 200, 200), M.Metal));
  // Автобус на площадке
  p.push(box([-90, 5, 50], [8, 8, 26], rgb(160, 150, 60), M.CorrodedMetal, { canCollide: true }));
  p.push(box([-90, 8, 50], [8.2, 3, 22], rgb(20, 26, 34), M.Glass, { transparency: 0.4 }));
  for (const [dx, dz] of [[-4, -8], [4, -8], [-4, 8], [4, 8]]) {
    p.push(cyl([-90 + dx, 2, 50 + dz], [3, 1.4, 3], rgb(20, 20, 24), M.Rubber, { y: 90 }));
  }
  // Деревья и мусор
  for (let i = 0; i < 18; i++) {
    const x = -110 + Math.random() * 220, z = 60 + Math.random() * 60;
    if (Math.abs(x) < 20 && Math.abs(z - 80) < 20) continue;
    p.push(...tree([x, 0, z], 0.7 + Math.random() * 0.5, rgb(44, 32, 22), rgb(26, 46, 30)));
  }
  for (let i = 0; i < 26; i++) {
    p.push(deco([-110 + Math.random() * 220, 0.1, -120 + Math.random() * 200], [1.4, 0.2, 1], rgb(120, 116, 100), M.SmoothPlastic, { rot: { yaw: Math.random() * 360 } }));
  }
  p.push(...streetLamp([-30, 0, 10], rgb(180, 200, 220), { height: 18, range: 30 }));
  p.push(...streetLamp([40, 0, 20], rgb(180, 200, 220), { height: 18, range: 30 }));
  p.push(mist([0, 12, 0], rgb(90, 100, 120), { rate: 26, spread: 200 }));
  p.push(...markers({ spawn: [0, 2, 90], portal: [bx, 3, bz + bd / 2 + 14], npcs: [[bx - 30, 1, bz + 10], [90, 2, 30], [-40, 1, 40]],
    items: [[-78, 2, -102], [-40, 2, -60], [20, 2, -70], [90, 2, 30], [-90, 2, 50], [bx, 2, bz - 20]],
    interacts: [[bx, 7, bz - 33], [90, 2, 30], [-90, 1, 50], [bx, 2, bz + bd / 2 + 4]],
    reaches: [[90, 1, 30], [bx, 1, bz - bd / 2 + 6], [-90, 1, 50]], patrols: [[0, 1, 20], [bx, 1, bz], [90, 1, 30], [-60, 1, 40], [40, 1, -20]],
    monsters: [[bx, 1, bz], [90, 1, 30]], lamps: [[-30, 1, 10], [40, 1, 20], [bx, 1, bz + 20]] }));
  return model('AbandonedSchool', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 3. ПУСТОЙ ГОРОД                                                     */
/* ------------------------------------------------------------------ */
function emptyCity() {
  const p = [];
  p.push(ground(480, 480, rgb(26, 26, 30), M.Asphalt));
  // Дороги
  for (let i = -2; i <= 2; i++) {
    p.push(deco([i * 90, 0.05, 0], [22, 0.2, 480], rgb(34, 34, 38), M.Asphalt));
    p.push(deco([0, 0.05, i * 90], [480, 0.2, 22], rgb(34, 34, 38), M.Asphalt));
    for (let t = -230; t < 230; t += 20) {
      p.push(deco([i * 90, 0.12, t], [0.6, 0.1, 8], rgb(180, 170, 90), M.Neon, { transparency: 0.3 }));
      p.push(deco([t, 0.12, i * 90], [8, 0.1, 0.6], rgb(180, 170, 90), M.Neon, { transparency: 0.3 }));
    }
  }
  // Тротуары
  for (let i = -2; i <= 2; i++) {
    p.push(deco([i * 90, 0.3, 0], [6, 0.4, 480], rgb(60, 60, 66), M.Pavement));
    p.push(deco([i * 90 + 18, 0.3, 0], [6, 0.4, 480], rgb(60, 60, 66), M.Pavement));
  }
  // Кварталы зданий
  const spots = [];
  for (let bx = -200; bx <= 200; bx += 92) {
    for (let bz = -200; bz <= 200; bz += 92) {
      if (Math.abs(bx) < 40 || Math.abs(bz) < 40) continue;
      if (Math.random() < 0.08) continue;
      const w = 40 + Math.random() * 20, d = 40 + Math.random() * 20;
      const h = 50 + Math.random() * 110;
      p.push(...building({ pos: [bx, 0.4, bz], w, d, h, floors: Math.max(4, Math.floor(h / 12)), color: rgb(34 + Math.random() * 16, 34, 44), windowColor: rgb(255, 200, 130), litChance: 0.16 }));
      spots.push([bx, bz]);
    }
  }
  // Разбитые машины
  for (let i = 0; i < 24; i++) {
    const x = -200 + Math.random() * 400, z = Math.random() < 0.5 ? (Math.floor(Math.random() * 5) - 2) * 90 + 8 : 0;
    const zz = Math.random() < 0.5 ? z : -200 + Math.random() * 400;
    p.push(box([x, 2.2, zz], [7, 2.6, 15], i % 3 === 0 ? rgb(120, 40, 44) : rgb(40, 44, 52), M.CorrodedMetal, { canCollide: true, rot: { yaw: Math.random() * 30 } }));
    p.push(box([x, 3.6, zz], [6, 1.8, 8], rgb(20, 26, 34), M.Glass, { transparency: 0.45, rot: { yaw: Math.random() * 30 } }));
  }
  // Светофоры и знаки
  for (let i = -2; i <= 2; i++) {
    for (let j = -2; j <= 2; j++) {
      p.push(cyl([i * 90 + 14, 8, j * 90 + 14], [0.8, 16, 0.8], rgb(40, 42, 48), M.Metal));
      p.push(box([i * 90 + 14, 14, j * 90 + 14], [2.4, 5, 2], rgb(26, 28, 32), M.Metal));
      p.push(box([i * 90 + 14, 15.6, j * 90 + 12.8], [1.4, 1.4, 0.4], rgb(255, 40, 40), M.Neon));
    }
  }
  // Фары уличного света
  for (let i = -2; i <= 2; i++) {
    for (let t = -180; t <= 180; t += 60) {
      p.push(...streetLamp([i * 90 + 10, 0.4, t], rgb(220, 220, 200), { height: 20, range: 40, brightness: 4 }));
    }
  }
  // Билборд
  p.push(box([-6, 24, 20], [30, 16, 1], rgb(20, 20, 26), M.Metal, { rot: { yaw: 90 } }));
  p.push(sign([11, 24, 20], [28, 14], 'НЕ СПИ', rgb(230, 80, 90), { rot: { yaw: 270 }, boardColor: rgb(16, 16, 22) }));
  p.push(mist([0, 14, 0], rgb(70, 76, 96), { rate: 40, spread: 320 }));
  p.push(...markers({ spawn: [0, 2, 40], portal: [0, 3, 0], npcs: [[24, 1, -20], [-40, 1, 60], [100, 1, 100]],
    items: [[24, 2, -30], [-60, 2, 30], [90, 2, -60], [-100, 2, -40], [140, 2, 60], [-20, 2, 120], [60, 2, 160]],
    interacts: [[14, 1, 14], [-6, 1, 24], [0, 1, 0], [120, 1, 120]],
    reaches: [[-190, 1, -190], [190, 1, 190]], patrols: [[0, 1, 0], [90, 1, 90], [-90, 1, -90], [90, 1, -90], [-90, 1, 90]],
    monsters: [[0, 1, -40], [100, 1, 0]], lamps: [[0, 1, 0], [14, 1, 14], [0, 1, 90]] }));
  return model('EmptyCity', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 4. БОЛЬНИЦА                                                         */
/* ------------------------------------------------------------------ */
function hospital() {
  const p = [];
  p.push(ground(160, 200, rgb(24, 26, 30), M.Limestone));
  // Корпус
  const W = 110, D = 150;
  p.push(box([0, -0.6, 0], [W, 1.2, D], rgb(150, 156, 150), M.CeramicTiles, { canCollide: true }));
  p.push(box([0, 0.2, 0], [W - 4, 0.2, D - 4], rgb(120, 140, 138), M.CeramicTiles));
  // Внешние стены
  for (const [x, z, w, d] of [[0, -D / 2, W, 2], [0, D / 2, W, 2], [-W / 2, 0, 2, D], [W / 2, 0, 2, D]]) {
    p.push(box([x, 14, z], [w, 28, d], rgb(160, 166, 160), M.Concrete, { canCollide: true }));
  }
  // Крыша
  p.push(box([0, 28.5, 0], [W + 2, 2, D + 2], rgb(60, 62, 64), M.Concrete));
  // Палаты: перегородки и койки
  for (let row = -2; row <= 2; row++) {
    const z = row * 28;
    p.push(box([0, 8, z - 14], [W - 6, 16, 1.2], rgb(140, 146, 142), M.Plaster, { canCollide: true }));
    for (let i = -2; i <= 2; i++) {
      p.push(...bed([i * 18, 0.4, z], { yaw: 90 }, { frame: rgb(150, 152, 148), sheet: rgb(220, 224, 218), pillow: rgb(240, 240, 236) }));
      p.push(box([i * 18 + 6, 4, z], [0.6, 6, 0.6], rgb(180, 184, 176), M.Metal));
      p.push(eq('Капельница', [i * 18 + 6, 6.4, z], rgb(210, 214, 208)));
    }
  }
  // Коридор: лампы
  for (let i = -3; i <= 3; i++) {
    p.push(box([0, 16, i * 20], [10, 0.4, 2], rgb(240, 245, 240), M.Neon, { transparency: 0.1 }));
    p.push(box([0, 15.2, i * 20], [1, 1, 1], rgb(240, 245, 240), M.Neon, { transparency: 1, canCollide: false }));
  }
  // Стойка регистрации
  p.push(box([0, 3, 66], [30, 6, 4], rgb(120, 116, 104), M.WoodPlanks, { canCollide: true }));
  p.push(box([0, 6.4, 66], [31, 0.6, 5], rgb(160, 156, 140), M.Marble));
  // Кресла ожидания
  for (let i = 0; i < 8; i++) {
    p.push(box([-20 + i * 6, 2.4, 58], [4, 1, 4], rgb(60, 90, 110), M.Fabric, { canCollide: true }));
    p.push(box([-20 + i * 6, 3.6, 60], [4, 4, 0.5], rgb(50, 78, 96), M.Fabric));
  }
  // Таблички
  p.push(sign([0, 22, -D / 2 - 1.2], [40, 8], 'БОЛЬНИЦА', rgb(220, 240, 235), { boardColor: rgb(70, 84, 80) }));
  p.push(sign([W / 2 + 1.2, 12, 0], [30, 6], 'ХИРУРГИЯ', rgb(220, 120, 120), { rot: { yaw: 90 }, boardColor: rgb(50, 50, 54) }));
  // Красный крест
  p.push(box([0, 24, -D / 2 - 1.4], [10, 3, 0.6], rgb(220, 60, 60), M.Neon));
  p.push(box([0, 24, -D / 2 - 1.4], [3, 10, 0.6], rgb(220, 60, 60), M.Neon));
  // Каталки
  for (let i = 0; i < 4; i++) {
    p.push(box([-6 + i * 4, 3, 40 + i * 6], [6, 0.6, 12], rgb(180, 184, 190), M.Metal, { rot: { yaw: i * 12 }, canCollide: true }));
  }
  // Операционная: стол и лампа
  p.push(box([-30, 4, -50], [16, 1, 10], rgb(140, 150, 156), M.Metal, { canCollide: true }));
  p.push(cyl([-30, 16, -50], [10, 1.5, 10], rgb(220, 230, 240), M.Neon, { transparency: 0.2 }));
  p.push(eq('Лампа', [-30, 10, -50], rgb(240, 250, 255)));
  p.push(mist([0, 14, 0], rgb(140, 160, 170), { rate: 24, spread: 140 }));
  p.push(...markers({ spawn: [0, 2, 74], portal: [0, 3, 88], npcs: [[10, 1, 66], [-40, 1, 20], [30, 1, -60]],
    items: [[-36, 2, 20], [18, 2, -20], [40, 2, 40], [-20, 2, -60], [0, 2, -70], [44, 2, -40]],
    interacts: [[-30, 5, -50], [0, 3, 66], [-30, 2, 0], [30, 2, 0]],
    reaches: [[-50, 1, -70], [50, 1, 70]], patrols: [[0, 1, 0], [0, 1, -50], [0, 1, 50], [-40, 1, 0], [40, 1, 0]],
    monsters: [[0, 1, -40], [40, 1, 30]], lamps: [[0, 1, 20], [0, 1, -20], [-30, 1, -50]] }));
  return model('Hospital', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 5. ПРОКЛЯТЫЙ ЦИРК                                                   */
/* ------------------------------------------------------------------ */
function circus() {
  const p = [];
  p.push(ground(320, 320, rgb(26, 22, 20), M.Mud));
  // Шатёр
  p.push(cyl([0, 14, 0], [90, 28, 90], rgb(140, 40, 52), M.Fabric, { transparency: 0.02 }));
  p.push(cyl([0, 28.4, 0], [94, 2, 94], rgb(220, 210, 190), M.Fabric));
  p.push(cyl([0, 34, 0], [74, 10, 74], rgb(120, 32, 44), M.Fabric));
  p.push(cyl([0, 43, 0], [36, 10, 36], rgb(210, 200, 180), M.Fabric));
  p.push(cyl([0, 50, 0], [1, 6, 1], rgb(90, 30, 36), M.Metal));
  // Полосатый купол
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    p.push(box([Math.cos(a) * 30, 36, Math.sin(a) * 30], [6, 20, 46], i % 2 ? rgb(220, 210, 190) : rgb(140, 40, 52), M.Fabric, { rot: { yaw: (a * 180) / Math.PI }, canCollide: false }));
  }
  // Вход
  p.push(box([0, 8, 45], [22, 16, 3], rgb(30, 22, 20), M.Wood, { canCollide: true }));
  p.push(box([0, 16, 45], [26, 3, 4], rgb(200, 180, 120), M.Neon, { transparency: 0.3 }));
  // Арена: барьер
  p.push(cyl([0, 2, 0], [60, 4, 60], rgb(90, 70, 50), M.Wood, { transparency: 1 }));
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * Math.PI * 2;
    p.push(box([Math.cos(a) * 30, 3, Math.sin(a) * 30], [2, 6, 2], rgb(180, 40, 50), M.Wood, { rot: { yaw: (a * 180) / Math.PI } }));
    p.push(box([Math.cos(a) * 26, 1.4, Math.sin(a) * 26], [4, 2.4, 4], rgb(70, 60, 50), M.Cobblestone));
  }
  // Трибуны
  for (let ring = 0; ring < 4; ring++) {
    p.push(cyl([0, 2 + ring * 2.4, 0], [66 + ring * 10, 2.4, 66 + ring * 10], rgb(60 + ring * 8, 46, 40), M.Wood, { transparency: 1 }));
    for (let i = 0; i < 28; i++) {
      const a = (i / 28) * Math.PI * 2;
      const r = 33 + ring * 5;
      p.push(box([Math.cos(a) * r, 2 + ring * 2.4, Math.sin(a) * r], [5, 1, 4], rgb(70 + ring * 6, 56, 48), M.WoodPlanks, { rot: { yaw: (a * 180) / Math.PI }, canCollide: true }));
    }
  }
  // Клетка с уродом
  p.push(box([-70, 10, 30], [26, 20, 26], rgb(40, 40, 46), M.DiamondPlate, { canCollide: true, transparency: 0.15 }));
  for (const [dx, dz] of [[-12, -12], [12, -12], [-12, 12], [12, 12]]) {
    p.push(cyl([-70 + dx, 10, 30 + dz], [1.4, 22, 1.4], rgb(50, 50, 56), M.Metal));
  }
  p.push(eq('Клетка', [-70, 2, 30], rgb(220, 120, 60)));
  // Колесо обозрения (старое)
  p.push(cyl([80, 22, -30], [2, 44, 2], rgb(70, 60, 50), M.CorrodedMetal));
  p.push(cyl([80, 22, -30], [44, 1.5, 44], rgb(60, 50, 42), M.CorrodedMetal, { y: 90 }));
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2;
    p.push(box([80 + Math.cos(a) * 21, 22 + Math.sin(a) * 21, -30], [4, 5, 4], i % 2 ? rgb(150, 50, 60) : rgb(60, 100, 120), M.CorrodedMetal, { canCollide: false }));
  }
  // Вагончики-фургоны
  for (const [x, z, c] of [[-60, -60, rgb(120, 60, 60)], [60, 60, rgb(70, 90, 120)], [-30, 70, rgb(120, 100, 60)]]) {
    p.push(box([x, 5, z], [16, 8, 10], c, M.WoodPlanks, { canCollide: true }));
    p.push(box([x, 10, z], [17, 1, 11], rgb(50, 40, 36), M.WoodPlanks));
    p.push(box([x, 8, z + 5.2], [3, 6, 0.4], rgb(26, 22, 20), M.Wood));
    for (const dx of [-5, 5]) p.push(cyl([x + dx, 2, z], [6, 1.4, 6], rgb(24, 24, 26), M.Rubber, { y: 90 }));
  }
  // Флажки по кругу
  for (let i = 0; i < 40; i++) {
    const a = (i / 40) * Math.PI * 2;
    p.push(sphere([Math.cos(a) * 52, 30 + Math.sin(i) * 2, Math.sin(a) * 52], 1.6, i % 3 === 0 ? rgb(240, 190, 70) : rgb(220, 90, 110), M.Neon, { transparency: 0.1 }));
  }
  p.push(...streetLamp([0, 0, 60], rgb(255, 120, 150), { height: 22, range: 46 }));
  p.push(...streetLamp([-50, 0, 0], rgb(255, 120, 150), { height: 20, range: 40 }));
  p.push(...streetLamp([50, 0, 0], rgb(255, 120, 150), { height: 20, range: 40 }));
  p.push(mist([0, 12, 0], rgb(120, 70, 80), { rate: 30, spread: 200 }));
  p.push(...markers({ spawn: [0, 2, 60], portal: [0, 3, 76], npcs: [[10, 1, 40], [-20, 2, 20], [-70, 1, 45]],
    items: [[-70, 2, 30], [20, 2, -20], [-40, 2, 40], [70, 2, 20], [40, 2, -40], [-20, 2, -50]],
    interacts: [[-70, 2, 30], [0, 3, 0], [80, 40, -30], [0, 17, 45]],
    reaches: [[70, 1, 60], [-80, 1, -60]], patrols: [[0, 1, 0], [40, 1, 40], [-40, 1, 40], [40, 1, -40], [-40, 1, -40]],
    monsters: [[0, 1, -20], [50, 1, 20]], lamps: [[0, 1, 30], [-20, 1, 0], [20, 1, 0]] }));
  return model('CursedCircus', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 6. ЗАТОПЛЕННЫЙ ГОРОД                                                */
/* ------------------------------------------------------------------ */
function floodedCity() {
  const p = [];
  // Вода
  p.push(el('Part', {
    Name: 'Water', Size: v3(520, 2, 520), CFrame: cf([0, -1.4, 0]),
    Color: rgb(20, 52, 66), Material: M.Water, Transparency: 0.35, Anchored: true, Reflectance: 0.2,
  }));
  p.push(ground(520, 520, rgb(22, 26, 28), M.Mud, -4));
  // Дома, торчащие из воды
  const blocks = [];
  for (let i = 0; i < 22; i++) {
    const x = -180 + Math.random() * 360, z = -180 + Math.random() * 360;
    if (blocks.some(([bx, bz]) => Math.hypot(bx - x, bz - z) < 55)) continue;
    blocks.push([x, z]);
    const w = 22 + Math.random() * 16, d = 22 + Math.random() * 16, h = 20 + Math.random() * 40;
    p.push(...building({ pos: [x, -1, z], w, d, h, floors: Math.max(2, Math.floor(h / 12)), color: rgb(50, 56, 58), windowColor: rgb(140, 210, 210), litChance: 0.1, roofColor: rgb(30, 34, 36), door: false }));
    // балкон/вывеска
    p.push(box([x, h * 0.4, z + d / 2 + 1], [w * 0.6, 4, 2], rgb(38, 42, 44), M.CorrodedMetal, { canCollide: false }));
  }
  // Мостки/плоты
  for (let i = 0; i < 14; i++) {
    const x = -160 + Math.random() * 320, z = -160 + Math.random() * 320;
    p.push(box([x, 0.6, z], [12, 0.8, 8], rgb(60, 48, 34), M.WoodPlanks, { canCollide: true, rot: { yaw: Math.random() * 30 } }));
  }
  // Затонувший автобус
  p.push(box([30, 2, 40], [8, 8, 24], rgb(150, 140, 60), M.CorrodedMetal, { canCollide: true, rot: { yaw: 20, roll: 12 } }));
  // Фонарные столбы из воды
  for (let i = 0; i < 16; i++) {
    const x = -140 + Math.random() * 280, z = -140 + Math.random() * 280;
    p.push(cyl([x, 6, z], [0.7, 16, 0.7], rgb(36, 40, 42), M.CorrodedMetal, { roll: 8 }));
    p.push(box([x, 12, z], [1.6, 1.6, 1.6], rgb(200, 230, 230), M.Neon, { transparency: 0.2 }));
  }
  // Маяк
  p.push(cyl([-120, 30, -120], [10, 60, 10], rgb(200, 200, 190), M.Concrete));
  p.push(cyl([-120, 46, -120], [10.4, 6, 10.4], rgb(170, 50, 50), M.Concrete));
  p.push(box([-120, 62, -120], [6, 6, 6], rgb(255, 230, 160), M.Neon, { transparency: 0.1 }));
  p.push(el('Part', { Name: 'LighthouseBeam', Size: v3(1, 1, 1), CFrame: cf([-120, 62, -120]), Transparency: 1, Anchored: true, CanCollide: false, Color: rgb(255, 230, 170) }, [
    pointLight(rgb(255, 230, 170), 6, 90),
  ]));
  // Волны (декор)
  for (let i = 0; i < 60; i++) {
    const x = -220 + Math.random() * 440, z = -220 + Math.random() * 440;
    p.push(deco([x, -0.9, z], [8 + Math.random() * 10, 0.2, 3], rgb(70, 130, 150), M.Glass, { transparency: 0.75 }));
  }
  p.push(particles([0, 20, 0], { name: 'Rain', texture: 'rbxasset://textures/particles/water_main.dds', rate: 300, color: c3(0.7, 0.85, 0.95), life: 3, speed: 8, accel: [0, -40, 0], startSize: 0.35, endSize: 0.2, size: 300, transparency: 0.4 }));
  p.push(mist([0, 8, 0], rgb(120, 160, 170), { rate: 34, spread: 300 }));
  p.push(...markers({ spawn: [0, 2, 30], portal: [0, 3, 0], npcs: [[20, 2, 20], [-40, 2, 60], [60, 2, -40]],
    items: [[20, 2, 20], [-60, 2, -20], [50, 2, 60], [-100, 2, 40], [80, 2, -80], [-20, 2, -60], [110, 2, 110]],
    interacts: [[30, 3, 40], [-120, 2, -120], [0, 2, 0], [60, 2, -40]],
    reaches: [[-190, 1, -190], [190, 1, 190]], patrols: [[0, 1, 0], [80, 1, 80], [-80, 1, -80], [80, 1, -80], [-80, 1, 80]],
    monsters: [[0, 1, -50], [60, 1, 60]], lamps: [[0, 1, 0], [20, 1, 20], [-40, 1, 60]] }));
  return model('FloodedCity', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 7. ЛЕДЯНОЙ МИР                                                      */
/* ------------------------------------------------------------------ */
function iceWorld() {
  const p = [];
  p.push(ground(460, 460, rgb(210, 230, 245), M.Snow));
  p.push(deco([0, 0.1, 0], [120, 0.2, 460], rgb(180, 215, 235), M.Ice, { transparency: 0.25 }));
  // Ледяные скалы
  for (let i = 0; i < 34; i++) {
    const x = -200 + Math.random() * 400, z = -200 + Math.random() * 400;
    if (Math.hypot(x, z) < 40) continue;
    const h = 20 + Math.random() * 60;
    p.push(el('Part', {
      Name: 'IceSpike', Size: v3(8 + Math.random() * 10, h, 8 + Math.random() * 10),
      CFrame: cf([x, h / 2, z], { roll: (Math.random() * 16 - 8), pitch: (Math.random() * 16 - 8) }),
      Color: rgb(170 + Math.random() * 40, 205, 230), Material: M.Ice,
      Transparency: 0.15, Reflectance: 0.25, Anchored: true, CanCollide: true,
    }));
  }
  // Ледяной дворец
  const px = 0, pz = -120;
  p.push(box([px, 20, pz], [60, 40, 40], rgb(190, 220, 240), M.Ice, { transparency: 0.12, canCollide: true }));
  p.push(box([px, 41, pz], [66, 2, 46], rgb(180, 210, 235), M.Ice, { transparency: 0.08 }));
  for (const dx of [-30, -15, 0, 15, 30]) {
    p.push(cyl([px + dx, 24, pz + 22], [3, 48, 3], rgb(200, 230, 250), M.Ice, { transparency: 0.1 }));
    p.push(cyl([px + dx, 50, pz + 22], [4, 4, 4], rgb(220, 240, 255), M.Ice, { transparency: 0.05 }));
  }
  p.push(box([px, 6, pz + 21], [14, 12, 2], rgb(240, 250, 255), M.Glass, { transparency: 0.55 }));
  // Замёрзший лес
  for (let i = 0; i < 40; i++) {
    const x = -190 + Math.random() * 380, z = 20 + Math.random() * 180;
    p.push(...pine([x, 0, z], 0.7 + Math.random() * 0.6, rgb(28, 62, 62)));
  }
  // Замёрзшее озеро
  p.push(cyl([90, 0.3, 60], [100, 0.4, 100], rgb(150, 200, 225), M.Ice, { transparency: 0.2 }));
  // Ледяные арки
  for (let i = 0; i < 5; i++) {
    const z = 20 + i * 30;
    p.push(box([-40, 12, z], [3, 24, 3], rgb(200, 230, 248), M.Ice, { transparency: 0.12 }));
    p.push(box([-24, 12, z], [3, 24, 3], rgb(200, 230, 248), M.Ice, { transparency: 0.12 }));
    p.push(box([-32, 24, z], [19, 3, 3], rgb(210, 235, 250), M.Ice, { transparency: 0.1 }));
  }
  // Костёр путника
  p.push(box([20, 0.4, 40], [8, 0.4, 8], rgb(80, 70, 60), M.Cobblestone));
  p.push(...[0, 1, 2, 3].map((i) => box([20, 1, 40], [5, 0.7, 0.8], rgb(60, 44, 32), M.Wood, { rot: { yaw: i * 45 } })));
  p.push(el('Part', { Name: 'CampFire', Size: v3(1, 1, 1), CFrame: cf([20, 3, 40]), Transparency: 1, Anchored: true, CanCollide: false, Color: rgb(255, 150, 70) }, [
    pointLight(rgb(255, 160, 80), 4, 30),
  ]));
  p.push(particles([0, 40, 0], { name: 'Snow', texture: 'rbxasset://textures/particles/sparkles_main.dds', rate: 200, color: c3(0.95, 0.98, 1), life: 8, speed: 1, accel: [0.5, -3, 0], startSize: 0.5, endSize: 0.4, size: 400, transparency: 0.2, glow: false }));
  p.push(mist([0, 10, 0], rgb(200, 220, 235), { rate: 30, spread: 280 }));
  p.push(...markers({ spawn: [0, 2, 60], portal: [0, 3, 30], npcs: [[20, 1, 40], [-40, 1, 60], [60, 1, 80]],
    items: [[20, 2, 40], [-40, 2, 60], [90, 2, 60], [-70, 2, 100], [50, 2, 120], [-20, 2, 130]],
    interacts: [[20, 2, 40], [0, 2, -100], [-32, 2, 50], [90, 2, 60]],
    reaches: [[90, 1, 60], [0, 1, -100]], patrols: [[0, 1, 20], [60, 1, 60], [-60, 1, 60], [0, 1, 120], [90, 1, 60]],
    monsters: [[0, 1, -40], [70, 1, 40]], lamps: [[20, 1, 40], [0, 1, 20], [0, 1, -60]] }));
  return model('IceWorld', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 8. ФАБРИКА                                                          */
/* ------------------------------------------------------------------ */
function factory() {
  const p = [];
  p.push(ground(300, 300, rgb(34, 32, 30), M.DiamondPlate));
  // Цех
  const W = 200, D = 160, H = 40;
  p.push(box([0, H, 0], [W + 4, 2, D + 4], rgb(40, 40, 44), M.DiamondPlate));
  for (const [x, z, w, d] of [[0, -D / 2, W, 3], [0, D / 2, W, 3], [-W / 2, 0, 3, D], [W / 2, 0, 3, D]]) {
    p.push(box([x, H / 2, z], [w, H, d], rgb(52, 50, 48), M.CorrodedMetal, { canCollide: true }));
  }
  // Окна верхнего света
  for (let i = -4; i <= 4; i++) {
    p.push(box([i * 20, H - 4, -D / 2 + 1.6], [12, 6, 0.4], rgb(180, 190, 170), M.Glass, { transparency: 0.5 }));
  }
  // Колонны
  for (let x = -80; x <= 80; x += 40) {
    for (let z = -60; z <= 60; z += 40) {
      p.push(box([x, H / 2, z], [6, H, 6], rgb(64, 60, 56), M.Concrete, { canCollide: true }));
      p.push(box([x, 1, z], [10, 2, 10], rgb(50, 48, 46), M.Concrete));
    }
  }
  // Конвейеры
  for (let i = 0; i < 3; i++) {
    const z = -40 + i * 40;
    p.push(box([0, 5, z], [150, 1.4, 10], rgb(46, 46, 50), M.DiamondPlate, { canCollide: true }));
    for (let x = -70; x <= 70; x += 10) {
      p.push(cyl([x, 5.9, z], [2, 10, 2], rgb(70, 66, 60), M.CorrodedMetal, { roll: 90 }));
    }
    // Ящики на конвейере
    for (let x = -60; x <= 60; x += 30) {
      p.push(...crate([x, 6, z], 0.8, rgb(96, 74, 48)));
    }
  }
  // Станки
  for (let i = 0; i < 6; i++) {
    const x = -70 + i * 28;
    p.push(box([x, 4, 60], [12, 8, 12], rgb(56, 52, 48), M.CorrodedMetal, { canCollide: true }));
    p.push(cyl([x, 9.4, 60], [3, 4, 3], rgb(90, 70, 50), M.CorrodedMetal));
    p.push(box([x, 8.4, 66.4], [8, 1.2, 1.2], rgb(180, 170, 90), M.Neon, { transparency: 0.2 }));
  }
  // Резервуары
  for (const [x, z] of [[-120, -100], [-120, -70], [120, -100], [120, -70]]) {
    p.push(cyl([x, 16, z], [22, 32, 22], rgb(60, 64, 60), M.CorrodedMetal, { transparency: 0.05 }));
    p.push(cyl([x, 32.5, z], [22, 1, 22], rgb(48, 52, 50), M.Metal));
    p.push(cyl([x, 8, z], [22.2, 1, 22.2], rgb(150, 120, 50), M.Metal));
  }
  // Трубы под потолком и по стенам
  for (let z = -70; z <= 70; z += 35) {
    p.push(cyl([0, H - 6, z], [6, 8, 6], rgb(84, 62, 44), M.CorrodedMetal, { roll: 90 }));
  }
  for (const x of [-90, -50, 50, 90]) {
    p.push(cyl([x, H - 9, 0], [3, D, 3], rgb(90, 66, 46), M.CorrodedMetal, { roll: 0 }));
  }
  // Пульт управления
  p.push(box([0, 6, -70], [30, 12, 6], rgb(44, 46, 50), M.Metal, { canCollide: true }));
  p.push(box([0, 12.2, -66.9], [28, 8, 0.3], rgb(30, 60, 50), M.Neon, { transparency: 0.15 }));
  for (let i = 0; i < 12; i++) {
    p.push(box([-12 + i * 2.2, 8 + (i % 3) * 1.6, -66.8], [0.8, 0.8, 0.3], i % 3 === 0 ? rgb(220, 80, 60) : rgb(90, 220, 140), M.Neon));
  }
  // Козловой кран
  p.push(box([0, H - 3, 0], [W - 10, 2, 4], rgb(150, 120, 40), M.CorrodedMetal));
  p.push(box([0, 20, 0], [4, 4, 4], rgb(120, 100, 40), M.CorrodedMetal));
  p.push(cyl([0, 26, 0], [0.4, 16, 0.4], rgb(80, 76, 70), M.Metal));
  p.push(box([0, 16, 0], [6, 3, 6], rgb(60, 56, 50), M.CorrodedMetal, { canCollide: false }));
  // Искры и пар
  p.push(particles([0, 16, 0], { name: 'Sparks', texture: 'rbxasset://textures/particles/sparkles_main.dds', rate: 60, color: c3(1, 0.7, 0.2), life: 0.8, speed: 6, accel: [0, -20, 0], startSize: 0.15, endSize: 0.05, glow: true, size: 30 }));
  p.push(mist([0, 20, 0], rgb(120, 110, 100), { rate: 30, spread: 180 }));
  p.push(...streetLamp([-80, 0, 40], rgb(255, 220, 150), { height: 24, range: 50 }));
  p.push(...streetLamp([80, 0, 40], rgb(255, 220, 150), { height: 24, range: 50 }));
  p.push(...markers({ spawn: [0, 2, 66], portal: [0, 3, 78], npcs: [[0, 2, -70], [-60, 2, 0], [60, 2, 40]],
    items: [[-60, 2, 0], [40, 2, -40], [60, 2, 40], [0, 2, 40], [-90, 2, 50], [80, 2, -20]],
    interacts: [[0, 2, -70], [0, 2, 0], [-70, 2, 60], [120, 2, -70]],
    reaches: [[-90, 1, -70], [90, 1, 70]], patrols: [[0, 1, 0], [60, 1, -50], [-60, 1, 50], [0, 1, 60], [-90, 1, 0]],
    monsters: [[0, 1, -30], [80, 1, 20]], lamps: [[0, 1, 20], [-20, 1, -20], [40, 1, 20]] }));
  return model('Factory', flat(p), [0, 0, 0]);
}

/* ------------------------------------------------------------------ */
/* 9. КОСМИЧЕСКАЯ СТАНЦИЯ                                              */
/* ------------------------------------------------------------------ */
function spaceStation() {
  const p = [];
  // Звёздный фон
  p.push(el('Part', { Name: 'Void', Size: v3(1200, 1200, 1200), CFrame: cf([0, 0, 0]), Color: rgb(2, 3, 8), Material: M.SmoothPlastic, Transparency: 1, Anchored: true, CanCollide: false }));
  for (let i = 0; i < 260; i++) {
    p.push(sphere([-500 + Math.random() * 1000, -300 + Math.random() * 600, -500 + Math.random() * 1000], 1 + Math.random() * 2, rgb(230, 235, 255), M.Neon, { transparency: Math.random() * 0.4 }));
  }
  p.push(sphere([420, 200, -360], 150, rgb(120, 60, 200), M.Neon, { transparency: 0.1 }));
  p.push(sphere([-460, -180, 300], 110, rgb(240, 140, 60), M.Neon, { transparency: 0.15 }));
  // Центральный модуль
  p.push(cyl([0, 0, 0], [70, 30, 70], rgb(120, 126, 134), M.DiamondPlate, { transparency: 0 }));
  p.push(box([0, 0.4, 0], [70, 0.8, 70], rgb(96, 104, 112), M.DiamondPlate, { canCollide: true }));
  p.push(cyl([0, 15.4, 0], [70, 1, 70], rgb(70, 76, 84), M.Metal));
  // Коридоры-модули (4)
  for (let i = 0; i < 4; i++) {
    const a = (i / 4) * Math.PI * 2;
    const cx = Math.cos(a) * 60, cz = Math.sin(a) * 60;
    p.push(box([cx, 8, cz], [46, 4, 20], rgb(110, 116, 124), M.DiamondPlate, { rot: { yaw: (a * 180) / Math.PI }, canCollide: true }));
    p.push(box([cx, 1, cz], [46, 1, 20], rgb(86, 92, 100), M.Metal, { rot: { yaw: (a * 180) / Math.PI }, canCollide: true }));
    for (const s of [-9.5, 9.5]) {
      p.push(box([cx + Math.sin(a) * s, 7, cz - Math.cos(a) * s], [46, 12, 1], rgb(120, 126, 134), M.DiamondPlate, { rot: { yaw: (a * 180) / Math.PI }, canCollide: true }));
    }
    // Конечный отсек
    const ex = Math.cos(a) * 120, ez = Math.sin(a) * 120;
    p.push(...roomModule(ex, ez, 34, 34, 18, rgb(104, 112, 120)));
  }
  // Мостик со стеклом
  p.push(box([0, 24, 0], [40, 4, 40], rgb(110, 118, 128), M.DiamondPlate, { canCollide: true }));
  p.push(cyl([0, 24, 0], [18, 6, 18], rgb(140, 200, 230), M.Glass, { transparency: 0.55 }));
  p.push(box([0, 27.4, 0], [40, 0.6, 40], rgb(80, 88, 96), M.Metal));
  // Пульты
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    p.push(box([Math.cos(a) * 15, 25.6, Math.sin(a) * 15], [8, 2.4, 4], rgb(50, 58, 66), M.Metal, { rot: { yaw: (a * 180) / Math.PI + 90 } }));
    p.push(box([Math.cos(a) * 15, 26.8, Math.sin(a) * 15], [6, 1.4, 0.3], rgb(80, 200, 240), M.Neon, { transparency: 0.2, rot: { yaw: (a * 180) / Math.PI + 90 } }));
  }
  // Скафандры в капсулах
  for (const [x, z] of [[-40, 40], [-40, 50], [40, 40], [40, 50]]) {
    p.push(cyl([x, 4, z], [8, 8, 8], rgb(180, 200, 210), M.Glass, { transparency: 0.5 }));
    p.push(box([x, 2, z], [3, 5, 3], rgb(230, 230, 235), M.Fabric));
    p.push(sphere([x, 5.4, z], 2.4, rgb(220, 225, 230), M.Glass, { transparency: 0.2 }));
  }
  // Солнечные панели
  for (const [x, y, z, yaw] of [[-110, 10, 0, 0], [110, 10, 0, 0], [0, 10, -110, 90], [0, 10, 110, 90]]) {
    p.push(box([x, y, z], [50, 0.6, 16], rgb(30, 40, 80), M.Metal, { rot: { yaw }, reflectance: 0.4 }));
    for (let i = -2; i <= 2; i++) {
      p.push(box([x + (yaw ? 0 : i * 10), y + 0.5, z + (yaw ? i * 10 : 0)], [8, 0.2, 14], rgb(60, 90, 160), M.Glass, { transparency: 0.4 }));
    }
  }
  // Аварийные лампы
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2;
    p.push(box([Math.cos(a) * 33, 4, Math.sin(a) * 33], [1.4, 1.4, 1.4], rgb(230, 70, 60), M.Neon));
  }
  p.push(particles([0, 20, 0], { name: 'Dust', texture: 'rbxasset://textures/particles/sparkles_main.dds', rate: 10, color: c3(0.6, 0.8, 1), life: 10, speed: 0.3, accel: [0, 0, 0], startSize: 0.25, endSize: 0.2, transparency: 0.5, glow: true }));
  p.push(...markers({ spawn: [0, 2, 0], portal: [0, 3, 20], npcs: [[0, 2, 0], [120, 2, 0], [-120, 2, 0]],
    items: [[0, 2, 0], [120, 2, 0], [-120, 2, 0], [0, 2, 120], [0, 2, -120], [40, 2, 40]],
    interacts: [[0, 2, 0], [120, 2, 0], [-40, 2, 40], [0, 26, 0]],
    reaches: [[120, 1, 0], [0, 1, 120]], patrols: [[0, 1, 0], [60, 1, 0], [-60, 1, 0], [0, 1, 60], [0, 1, -60]],
    monsters: [[60, 1, 0], [0, 1, -60]], lamps: [[0, 1, 20], [40, 1, 0], [0, 1, 40]] }));
  return model('SpaceStation', flat(p), [0, 0, 0]);
}

function roomModule(x, z, w, d, h, color) {
  const parts = [];
  parts.push(box([x, 0.4, z], [w, 0.8, d], color, M.DiamondPlate, { canCollide: true }));
  parts.push(box([x, h, z], [w, 1, d], rgb(70, 76, 84), M.Metal));
  for (const [dx, dz, ww, dd] of [[0, -d / 2, w, 1], [0, d / 2, w, 1], [-w / 2, 0, 1, d], [w / 2, 0, 1, d]]) {
    parts.push(box([x + dx, h / 2, z + dz], [ww, h, dd], color, M.DiamondPlate, { canCollide: true }));
  }
  parts.push(box([x, h - 1.6, z], [w * 0.5, 0.4, d * 0.5], rgb(200, 230, 245), M.Neon, { transparency: 0.2 }));
  return parts;
}

/* ------------------------------------------------------------------ */
/* 10. ПРОКЛЯТЫЙ ЗАМОК                                                 */
/* ------------------------------------------------------------------ */
function cursedCastle() {
  const p = [];
  p.push(ground(380, 380, rgb(26, 22, 30), M.Cobblestone));
  // Стены замка
  const W = 160, D = 160, H = 34;
  for (const [x, z, w, d] of [[0, -D / 2, W, 6], [0, D / 2, W, 6], [-W / 2, 0, 6, D], [W / 2, 0, 6, D]]) {
    p.push(box([x, H / 2, z], [w, H, d], rgb(58, 54, 62), M.Slate, { canCollide: true }));
    for (let i = 0; i < Math.floor(w / 10); i++) {
      const t = (i - Math.floor(w / 20)) * 10;
      const px = w > d ? x + t : x, pz = w > d ? z : z + t;
      p.push(box([px, H + 2, pz], [4, 4, 4], rgb(52, 48, 56), M.Slate, { canCollide: true }));
    }
  }
  // Ворота
  p.push(box([0, 10, D / 2 + 3], [24, 20, 2], rgb(34, 28, 24), M.WoodPlanks, { canCollide: true }));
  p.push(box([0, 10, D / 2 + 2], [26, 22, 1], rgb(70, 64, 60), M.Metal));
  for (let i = 0; i < 8; i++) {
    p.push(cyl([-11 + i * 3.2, 10, D / 2 + 1.2], [0.6, 20, 0.6], rgb(40, 36, 32), M.Metal));
  }
  // Донжон
  p.push(cyl([0, 30, -30], [60, 60, 60], rgb(66, 62, 70), M.Slate, { canCollide: true }));
  p.push(cyl([0, 61, -30], [64, 3, 64], rgb(50, 46, 54), M.Slate));
  p.push(cyl([0, 72, -30], [26, 20, 26], rgb(58, 54, 62), M.Slate));
  // Конусная крыша
  for (let i = 0; i < 8; i++) {
    p.push(cyl([0, 84 + i * 3, -30], [26 - i * 3, 3, 26 - i * 3], i % 2 ? rgb(90, 40, 46) : rgb(60, 40, 46), M.Slate));
  }
  // Окна донжона
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2;
    p.push(box([Math.cos(a) * 30.2, 30 + (i % 3) * 8, -30 + Math.sin(a) * 30.2], [3, 5, 1], rgb(255, 180, 90), M.Neon, { transparency: 0.25, rot: { yaw: (a * 180) / Math.PI } }));
  }
  // Двор: булыжник и дорожки
  p.push(deco([0, 0.3, 0], [W - 12, 0.3, D - 12], rgb(44, 42, 50), M.Cobblestone));
  p.push(deco([0, 0.4, 20], [10, 0.3, 100], rgb(54, 50, 58), M.Cobblestone));
  // Четыре башни по углам
  for (const [x, z] of [[-W / 2, -D / 2], [W / 2, -D / 2], [-W / 2, D / 2], [W / 2, D / 2]]) {
    p.push(cyl([x, 24, z], [24, 48, 24], rgb(62, 58, 66), M.Slate, { canCollide: true }));
    p.push(cyl([x, 49, z], [26, 2, 26], rgb(50, 46, 54), M.Slate));
    for (let i = 0; i < 6; i++) {
      const a = (i / 6) * Math.PI * 2;
      p.push(box([x + Math.cos(a) * 11, 52, z + Math.sin(a) * 11], [3, 6, 3], rgb(56, 52, 60), M.Slate));
    }
  }
  // Кладбище
  for (let i = 0; i < 16; i++) {
    const x = -60 + Math.random() * 120, z = 50 + Math.random() * 24;
    p.push(box([x, 2.4, z], [2, 5, 0.6], rgb(80, 82, 80), M.Slate, { rot: { yaw: (Math.random() * 20 - 10) } }));
    p.push(box([x, 0.4, z - 1], [3, 0.6, 2.4], rgb(40, 42, 42), M.Mud));
  }
  // Виселица
  p.push(box([-50, 12, 70], [1.4, 24, 1.4], rgb(50, 40, 30), M.Wood));
  p.push(box([-45, 23, 70], [11, 1.2, 1.2], rgb(50, 40, 30), M.Wood));
  p.push(cyl([-42, 18, 70], [0.2, 10, 0.2], rgb(120, 110, 90), M.Fabric));
  // Факелы
  for (const [x, z] of [[-14, 60], [14, 60], [-50, 20], [50, 20], [0, -30]]) {
    p.push(cyl([x, 6, z], [1, 4, 1], rgb(50, 40, 30), M.Wood));
    p.push(el('Part', { Name: 'Torch', Size: v3(2, 2, 2), CFrame: cf([x, 8.4, z]), Color: rgb(255, 140, 50), Material: M.Neon, Transparency: 0.2, Anchored: true, CanCollide: false }, [
      pointLight(rgb(255, 150, 60), 4, 34),
      particles([x, 9, z], { name: 'TorchFire', texture: 'rbxasset://textures/particles/sparkles_main.dds', rate: 26, color: c3(1, 0.55, 0.15), life: 0.6, speed: 4, accel: [0, 5, 0], startSize: 0.3, endSize: 0.05, transparency: 0, glow: true }),
    ]));
  }
  // Флаги
  for (const [x, z] of [[-W / 2, -D / 2], [W / 2, -D / 2]]) {
    p.push(cyl([x, 60, z], [0.4, 20, 0.4], rgb(60, 56, 50), M.Metal));
    p.push(box([x + 5, 66, z], [10, 6, 0.3], rgb(110, 30, 40), M.Fabric));
  }
  p.push(mist([0, 14, 0], rgb(80, 70, 110), { rate: 34, spread: 220 }));
  p.push(...markers({ spawn: [0, 2, 66], portal: [0, 3, 78], npcs: [[-30, 2, 20], [30, 2, 20], [0, 2, -10]],
    items: [[-30, 2, 20], [30, 2, 30], [-60, 2, 60], [60, 2, 60], [-W / 2, 2, -D / 2], [W / 2, 2, D / 2], [0, 2, -60]],
    interacts: [[0, 2, -30], [-50, 2, 70], [0, 12, 0], [0, 30, -30]],
    reaches: [[-W / 2, 1, -D / 2], [W / 2, 1, D / 2]], patrols: [[0, 1, 0], [60, 1, 40], [-60, 1, 40], [0, 1, 60], [0, 1, -50]],
    monsters: [[0, 1, -20], [50, 1, 50]], lamps: [[0, 1, 40], [-14, 1, 58], [14, 1, 58]] }));
  return model('CursedCastle', flat(p), [0, 0, 0]);
}

/* helpers ------------------------------------------------------------ */

function eq(name, pos, color) {
  return el('Part', {
    Name: 'Glow_' + name.replace(/\s/g, ''),
    Size: v3(1, 1, 1),
    CFrame: cf(pos),
    Transparency: 1,
    Anchored: true,
    CanCollide: false,
    Color: color,
  }, [pointLight(color, 1.6, 22)]);
}

/** Собирает маркеры из карты описаний. */
function markers(m) {
  const out = [];
  const put = (name, pos, y) => out.push(marker(name, [pos[0], pos[1] ?? y ?? 1, pos[2]]));
  if (m.spawn) put('MK_Spawn', m.spawn, 2);
  if (m.portal) put('MK_Portal', m.portal, 3);
  (m.npcs || []).forEach((pos, i) => put(`MK_NPC_${i + 1}`, pos, 1));
  (m.items || []).forEach((pos, i) => put(`MK_Item_${i + 1}`, pos, 2));
  (m.interacts || []).forEach((pos, i) => put(`MK_Interact_${i + 1}`, pos, 1));
  (m.reaches || []).forEach((pos, i) => put(`MK_Reach_${i + 1}`, pos, 1));
  (m.patrols || []).forEach((pos, i) => put(`MK_Patrol_${i + 1}`, pos, 1));
  (m.monsters || []).forEach((pos, i) => put(`MK_Monster_${i + 1}`, pos, 1));
  (m.lamps || []).forEach((pos, i) => put(`MK_Lamp_${i + 1}`, pos, 1));
  return out;
}

export const MAP_BUILDERS = {
  forest: darkForest,
  school: school,
  city: emptyCity,
  hospital: hospital,
  circus: circus,
  flood: floodedCity,
  ice: iceWorld,
  factory: factory,
  space: spaceStation,
  castle: cursedCastle,
};

export function allDreamMaps() {
  return Object.entries(MAP_BUILDERS).map(([id, fn]) => {
    const m = fn();
    m.children.push(el('StringValue', { Name: 'DreamId', Value: id }));
    return m;
  });
}
