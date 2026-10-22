/**
 * bedroom.mjs — «MenuBedroom»: кинематографичная спальня для главного меню.
 * Маркеры: BedPoint (лечь), SleepCameraPoint (камера во время сна),
 *          MenuCameraStart / MenuCameraEnd (медленный пан), MenuFocus (точка взгляда).
 */
import { el, v3, cf, rgb, udim2 } from '../rbxkit.mjs';
import {
  M as MAT, box as b, deco as d, sphere as sph, cyl as c, windowFrame as win,
  pointLight as pl, bed, wardrobe, pcSetup, bookStack, toy, plant, rug, poster,
  table, chair, shelf, marker, markerSet, model, flat, mist, sign,
} from './kit.mjs';

export function bedroom() {
  const parts = [];
  const W = 34, D = 40, H = 16;

  /* --- оболочка комнаты --- */
  parts.push(b([0, -1, 0], [W + 6, 2, D + 6], rgb(66, 52, 40), MAT.WoodPlanks, { canCollide: true })); // пол
  parts.push(b([0, H + 1, 0], [W + 2, 2, D + 2], rgb(52, 44, 40), MAT.Plaster, { canCollide: true })); // потолок
  parts.push(b([0, H / 2, -D / 2 - 1], [W + 2, H, 2], rgb(58, 58, 74), MAT.Plaster, { canCollide: true })); // задняя
  parts.push(b([0, H / 2, D / 2 + 1], [W + 2, H, 2], rgb(58, 58, 74), MAT.Plaster, { canCollide: true })); // передняя
  parts.push(b([-W / 2 - 1, H / 2, 0], [2, H, D + 2], rgb(52, 52, 66), MAT.Plaster, { canCollide: true })); // левая
  parts.push(b([W / 2 + 1, H / 2, 0], [2, H, D + 2], rgb(52, 52, 66), MAT.Plaster, { canCollide: true })); // правая

  // Плинтусы и карниз
  for (const [x, z, sw, sd] of [[0, -D / 2, W, 0.4], [0, D / 2, W, 0.4], [-W / 2, 0, 0.4, D], [W / 2, 0, 0.4, D]]) {
    parts.push(d([x, 0.6, z], [sw || 0.5, 1.2, sd || 0.5], rgb(40, 30, 24), MAT.WoodPlanks));
    parts.push(d([x, H - 0.8, z], [sw || 0.5, 0.5, sd || 0.5], rgb(40, 30, 24), MAT.WoodPlanks));
  }

  // Деревянные панели на стенах
  for (let i = -8; i <= 8; i++) {
    parts.push(d([i * 2, 5.5, -D / 2 + 0.4], [0.18, 9, 0.2], rgb(46, 40, 54), MAT.WoodPlanks));
  }

  // Обои-акцент за кроватью
  parts.push(d([0, 8, -D / 2 + 0.35], [W - 4, 14, 0.2], rgb(38, 34, 58), MAT.Fabric));

  /* --- окно с луной и звёздами --- */
  parts.push(...win([W / 2 - 0.2, 9, -6], [10, 8], rgb(40, 32, 26), { yaw: 90 }));
  parts.push(b([W / 2 + 0.25, 9, -6], [0.3, 8, 10], rgb(120, 150, 210), MAT.Glass, { transparency: 0.62 }));
  // Луна за окном
  parts.push(sph([W / 2 + 34, 30, -10], 9, rgb(240, 240, 225), MAT.Neon, { transparency: 0.05 }));
  parts.push(sph([W / 2 + 36, 31, -9], 9, rgb(245, 245, 235), MAT.SmoothPlastic, { transparency: 0 }));
  for (let i = 0; i < 26; i++) {
    const sx = W / 2 + 60 + Math.random() * 80;
    const sy = 20 + Math.random() * 60;
    const sz = -80 + Math.random() * 160;
    parts.push(sph([sx, sy, sz], 0.8 + Math.random() * 1.2, rgb(230, 235, 255), MAT.Neon, { transparency: 0.15 }));
  }
  // блик луны на полу
  parts.push(d([12, 0.1, -4], [10, 0.12, 14], rgb(150, 170, 220), MAT.Glass, { transparency: 0.82 }));
  parts.push(d([W / 2 - 0.4, 9, -6], [0.2, 8, 10], rgb(90, 120, 200), MAT.Neon, { transparency: 0.86 }));

  /* --- кровать --- */
  parts.push(...bed([-8, 0, -8], {}, { frame: rgb(64, 46, 34), sheet: rgb(70, 76, 120), pillow: rgb(212, 216, 236) }));
  // Плед
  parts.push(d([-8, 4.1, -4], [9.6, 0.4, 8], rgb(58, 52, 96), MAT.Fabric));
  // Прикроватная тумба
  parts.push(b([1.5, 3, -12], [6, 6, 6], rgb(56, 42, 32), MAT.WoodPlanks, { canCollide: true }));
  parts.push(b([1.5, 3.1, -8.8], [5.2, 4.4, 0.4], rgb(44, 32, 24), MAT.WoodPlanks));
  parts.push(b([1.5, 3.1, -15.2], [5.2, 4.4, 0.4], rgb(44, 32, 24), MAT.WoodPlanks));
  parts.push(c([0.6, 4.4, -14], [0.5, 1.4, 0.5], rgb(200, 190, 160), MAT.Metal));
  // Лампа на тумбе
  parts.push(c([1.5, 7.2, -13], [2.6, 0.6, 2.6], rgb(60, 50, 40), MAT.Wood));
  parts.push(c([1.5, 7.9, -13], [0.5, 1.6, 0.5], rgb(160, 150, 120), MAT.Metal));
  parts.push(el('Part', {
    Shape: 'Ball', Size: v3(3.4, 3.6, 3.4), CFrame: cf([1.5, 9.6, -13]),
    Color: rgb(255, 196, 130), Material: MAT.Neon, Transparency: 0.25, Anchored: true, CanCollide: false,
  }, [
    el('PointLight', { Color: rgb(255, 190, 120), Brightness: 2.4, Range: 22, Shadows: true }),
  ]));
  // Будильник
  parts.push(b([4.3, 6.6, -11.4], [1.6, 1.2, 0.9], rgb(30, 30, 36), MAT.SmoothPlastic, { canCollide: false }));
  parts.push(b([4.3, 6.6, -10.92], [1.3, 0.9, 0.12], rgb(220, 60, 70), MAT.Neon, { canCollide: false }));
  // Книги на тумбе
  parts.push(...bookStack([-0.4, 6, -11.5], [rgb(140, 60, 70), rgb(70, 90, 150)]));

  /* --- ковёр и мебель --- */
  parts.push(rug([0, 0, 2], [26, 22], rgb(48, 40, 70), MAT.Fabric));
  parts.push(d([0, 0.08, 2], [22, 18, 0.06], rgb(70, 58, 100), MAT.Fabric));

  // Шкаф
  parts.push(...wardrobe([-13, 0, 12], {}, rgb(62, 46, 34)));
  // Комод
  parts.push(b([-11, 4, -16], [12, 8, 5], rgb(58, 44, 32), MAT.WoodPlanks, { canCollide: true }));
  for (let i = 0; i < 3; i++) {
    parts.push(d([-11, 2 + i * 2.4, -13.45], [11, 2, 0.2], rgb(44, 33, 24), MAT.WoodPlanks));
    parts.push(d([-11, 2.4 + i * 2.4, -13.30], [2.6, 0.35, 0.3], rgb(200, 180, 130), MAT.Metal));
  }
  // Письменный стол и компьютер
  parts.push(...pcSetup([9, 0, -15]));
  parts.push(...chair([9, 0, -10], rgb(52, 40, 30), { yaw: 180 }));
  // Полка с книгами
  parts.push(...shelf([14, 6, 4], [10, 12, 2.4], rgb(58, 44, 32), { yaw: 270 }));
  for (let i = 0; i < 12; i++) {
    const hue = [rgb(150, 60, 60), rgb(60, 90, 150), rgb(140, 130, 60), rgb(90, 60, 140)][i % 4];
    parts.push(b([13.6, 3 + (i % 4) * 2.6, 0.4 + Math.floor(i / 4) * 2.6], [1.1, 2.3, 1.9], hue, MAT.SmoothPlastic, { canCollide: false }));
  }
  // Постеры
  parts.push(poster([-16.9, 10, 2], [7, 9], rgb(30, 24, 46), { yaw: 90 }, 'СНЫ'));
  parts.push(poster([-16.9, 10, 10], [7, 7], rgb(40, 26, 40), { yaw: 90 }, '🌙'));
  parts.push(poster([0, 12, 20.7], [10, 7], rgb(34, 28, 50), { yaw: 180 }, 'NIGHTMARE'));
  // Растение
  parts.push(...plant([-15, 0, -14], 1.2));
  parts.push(...plant([15, 0, 17], 1.1));
  // Игрушки (детская нота)
  parts.push(...toy([-14.5, 0, 18], rgb(220, 120, 140)));
  parts.push(...toy([-12.5, 0, 19], rgb(120, 150, 220)));
  // Коробка
  parts.push(b([15, 1.6, 18], [5, 3.2, 4], rgb(110, 84, 58), MAT.Cardboard, { canCollide: true }));
  // Гирлянда над кроватью
  for (let i = 0; i < 15; i++) {
    const x = -16 + i * 2.2;
    const y = 13.4 - Math.sin((i / 14) * Math.PI) * 2;
    parts.push(el('Part', {
      Shape: 'Ball', Size: v3(0.55, 0.55, 0.55), CFrame: cf([x, y, -19.6]),
      Color: i % 2 === 0 ? rgb(255, 190, 120) : rgb(190, 150, 255), Material: MAT.Neon,
      Anchored: true, CanCollide: false,
    }));
  }
  parts.push(el('Part', {
    Name: 'StringLights', Size: v3(1, 1, 1), CFrame: cf([0, 12, -19]),
    Transparency: 1, Anchored: true, CanCollide: false, Color: rgb(255, 200, 150),
  }, [pl(rgb(255, 200, 150), 2, 26)]));

  // Вентилятор на потолке
  parts.push(c([0, H - 0.4, 0], [1, 1.2, 1], rgb(40, 40, 46), MAT.Metal));
  for (let i = 0; i < 3; i++) {
    parts.push(b([0, H - 1.4, 0], [11, 0.18, 1.6], rgb(50, 50, 58), MAT.Metal, { rot: { yaw: i * 60 }, canCollide: false }));
  }

  // Пыль в лунном свете
  parts.push(mist([8, 7, 0], rgb(150, 170, 220), { rate: 6, spread: 40 }));

  /* --- вывеска лобби на задней стене --- */
  parts.push(sign([0, 12.6, -D / 2 + 0.55], [24, 4.2], '🌙 DREAM SIMULATOR', rgb(240, 234, 255), { boardColor: rgb(9, 8, 18) }));
  parts.push(sign([0, 9.3, -D / 2 + 0.55], [19, 1.9], 'Каждый сон — новая история...', rgb(196, 186, 255), { boardColor: rgb(9, 8, 18) }));
  parts.push(sign([0, 6.6, -D / 2 + 0.55], [10, 1.5], 'ЛОББИ · СПАЛЬНЯ', rgb(140, 128, 220), { boardColor: rgb(9, 8, 18) }));
  // неоновые полосы вокруг вывески
  parts.push(d([0, 15.1, -D / 2 + 0.6], [25, 0.28, 0.18], rgb(150, 120, 255), MAT.Neon));
  parts.push(d([0, 5.6, -D / 2 + 0.6], [25, 0.28, 0.18], rgb(150, 120, 255), MAT.Neon));
  parts.push(d([-12.5, 10.35, -D / 2 + 0.6], [0.28, 9.6, 0.18], rgb(110, 90, 220), MAT.Neon));
  parts.push(d([12.5, 10.35, -D / 2 + 0.6], [0.28, 9.6, 0.18], rgb(110, 90, 220), MAT.Neon));
  parts.push(pl(rgb(150, 120, 255), 2.2, 26));

  /* --- маркеры и камера --- */
  parts.push(marker('BedPoint', [-8, 4.8, -4]));
  parts.push(marker('SleepCameraPoint', [-2, 12, 8]));
  parts.push(marker('MenuSpawn', [0, 4, 10]));
  parts.push(marker('DoorPoint', [0, 3, 18]));
  parts.push(marker('CameraTarget', [-4, 7, -8], { Size: v3(1, 1, 1) }));

  // Точка появления игрока
  parts.push(el('SpawnLocation', {
    Name: 'MenuSpawn',
    Size: v3(6, 1, 6),
    CFrame: cf([0, 1, 10]),
    Anchored: true,
    Neutral: true,
    Enabled: true,
    Transparency: 1,
    CanCollide: true,
    Color: rgb(40, 34, 60),
    Material: MAT.SmoothPlastic,
    Duration: 0,
  }));

  // Путь камеры меню (кинематографичный облёт)
  const cameraPath = el('Folder', { Name: 'CameraPath' }, [
    el('Part', { Name: 'Node1', Size: v3(1, 1, 1), CFrame: cf([14, 11, 16]), Transparency: 1, Anchored: true, CanCollide: false, CanQuery: false }),
    el('Part', { Name: 'Node2', Size: v3(1, 1, 1), CFrame: cf([6, 9, 15]), Transparency: 1, Anchored: true, CanCollide: false, CanQuery: false }),
    el('Part', { Name: 'Node3', Size: v3(1, 1, 1), CFrame: cf([-3, 8.4, 12]), Transparency: 1, Anchored: true, CanCollide: false, CanQuery: false }),
    el('Part', { Name: 'Node4', Size: v3(1, 1, 1), CFrame: cf([-6, 9.6, 4]), Transparency: 1, Anchored: true, CanCollide: false, CanQuery: false }),
    el('Part', { Name: 'Node5', Size: v3(1, 1, 1), CFrame: cf([8, 12, 6]), Transparency: 1, Anchored: true, CanCollide: false, CanQuery: false }),
  ]);

  const scene = model('MenuBedroom', flat(parts), [0, 0, 0]);
  scene.children.push(cameraPath);
  // Дополнительный свет сцены
  scene.children.push(el('Part', {
    Name: 'MoonRim', Size: v3(1, 1, 1), CFrame: cf([12, 13, -2]), Transparency: 1,
    Anchored: true, CanCollide: false, Color: rgb(140, 170, 255),
  }, [el('SpotLight', { Color: rgb(140, 170, 255), Brightness: 1.6, Range: 46, Angle: 92, Shadows: true })]));
  return scene;
}
