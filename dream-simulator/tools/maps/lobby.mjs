/**
 * lobby.mjs — ЛОББИ «Спальня сновидца» с нуля.
 *
 * Простая и надёжная геометрия: одна замкнутая комната, ровный пол,
 * мебель расставлена по стенам, центр свободен для спавна игрока.
 * Все детали Anchored; ничего не пересекает точку появления.
 *
 * Маркеры (контракт с сервером/клиентом):
 *   MenuSpawn       — SpawnLocation на полу (точка появления)
 *   BedPoint        — верх кровати (камера засыпания)
 *   SleepCameraPoint— точка камеры во время засыпания
 *   CameraTarget    — точка взгляда (вывеска над кроватью)
 *   CameraPath/Node1..6 — декоративный облёт (запасной вариант)
 */
import { el, v3, cf, rgb, udim2, font, ns, cs } from '../rbxkit.mjs';
import {
  M as MAT, box as b, deco as d, sphere as sph, cyl as c,
  pointLight as pl, rug, plant, marker, model, flat,
} from './kit.mjs';

const ROOM = { w: 44, d: 48, h: 18 };

/* --------------------------- свет --------------------------- */
const lamp = (pos, color = rgb(255, 226, 186), brightness = 2.4, range = 42) =>
  el('Part', {
    Name: 'LampGlow',
    Size: v3(0.6, 0.6, 0.6),
    CFrame: cf(pos),
    Transparency: 1,
    Anchored: true,
    CanCollide: false,
    CanQuery: false,
    Color: color,
  }, [pl(color, brightness, range)]);

const ceilingLight = (x, z) => [
  c([x, ROOM.h - 0.9, z], [5.2, 0.6, 5.2], rgb(70, 62, 92), MAT.Metal),
  el('Part', {
    Name: 'CeilingBulb',
    Shape: 'Cylinder',
    Size: v3(4.6, 1.1, 4.6),
    CFrame: cf([x, ROOM.h - 1.5, z]),
    Color: rgb(255, 240, 208),
    Material: MAT.Neon,
    Transparency: 0.08,
    Anchored: true,
    CanCollide: false,
  }),
  lamp([x, ROOM.h - 2.2, z], rgb(255, 230, 195), 2.6, 48),
];

const sconce = (pos, rotYaw) => [
  b([pos[0], pos[1], pos[2]], [1.6, 2.4, 0.6], rgb(60, 54, 80), MAT.Metal, { rot: { yaw: rotYaw || 0 } }),
  el('Part', {
    Name: 'SconceBulb',
    Size: v3(1.2, 1.4, 0.5),
    CFrame: cf([pos[0], pos[1] + 1.2, pos[2]], { yaw: rotYaw || 0 }),
    Color: rgb(255, 214, 170),
    Material: MAT.Neon,
    Transparency: 0.1,
    Anchored: true,
    CanCollide: false,
  }),
  lamp([pos[0], pos[1] + 1.2, pos[2] + (pos[2] < 0 ? 1.2 : -1.2)], rgb(255, 210, 165), 1.6, 26),
];

/* --------------------------- мебель --------------------------- */
const bookshelf = (x, z, yaw) => {
  const parts = [];
  parts.push(b([x, 6, z], [10, 12, 2.4], rgb(74, 52, 34), MAT.WoodPlanks, { rot: { yaw }, canCollide: true }));
  for (let i = 0; i < 4; i++) {
    parts.push(d([x, 1.6 + i * 3, z], [9.2, 0.3, 2], rgb(56, 38, 24), MAT.WoodPlanks, { rot: { yaw } }));
    for (let j = 0; j < 7; j++) {
      const hue = [rgb(150, 70, 70), rgb(70, 110, 160), rgb(150, 130, 70), rgb(90, 140, 100)][(i + j) % 4];
      parts.push(d([x - 3.6 + j * 1.1, 2.4 + i * 3, z], [0.8, 1.7, 1.6], hue, MAT.Fabric, { rot: { yaw } }));
    }
  }
  return parts;
};

const desk = (x, z) => {
  const parts = [];
  parts.push(b([x, 3.1, z], [12, 0.6, 6], rgb(78, 56, 38), MAT.WoodPlanks, { canCollide: true }));
  for (const [dx, dz] of [[-5, -2.4], [5, -2.4], [-5, 2.4], [5, 2.4]]) {
    parts.push(b([x + dx, 1.4, z + dz], [0.7, 2.8, 0.7], rgb(58, 42, 30), MAT.Wood, { canCollide: true }));
  }
  // монитор, клавиатура, кружка, лампа
  parts.push(b([x, 5.4, z - 1.4], [4.6, 2.8, 0.35], rgb(20, 22, 34), MAT.SmoothPlastic, { rot: { yaw: 0 } }));
  parts.push(b([x, 5.4, z - 1.2], [4.1, 2.3, 0.2], rgb(70, 120, 190), MAT.Neon, { transparency: 0.25 }));
  parts.push(b([x, 3.8, z - 1.6], [1.2, 1, 1.2], rgb(40, 42, 54), MAT.Metal));
  parts.push(b([x, 3.6, z + 0.9], [4, 0.35, 1.6], rgb(44, 44, 56), MAT.SmoothPlastic));
  parts.push(c([x + 4.2, 4.2, z + 1.4], [1.1, 1.6, 1.1], rgb(180, 150, 120), MAT.CeramicTiles));
  parts.push(b([x - 4.4, 4.9, z + 1.6], [0.5, 3, 0.5], rgb(60, 60, 70), MAT.Metal));
  parts.push(b([x - 4.4, 6.6, z + 1.6], [2.4, 1.6, 2.4], rgb(255, 224, 170), MAT.Neon, { transparency: 0.15 }));
  parts.push(lamp([x - 4.4, 6.2, z + 1.6], rgb(255, 220, 165), 1.5, 22));
  return parts;
};

const sofa = (x, z, yaw) => [
  b([x, 1.8, z], [14, 2.2, 6], rgb(74, 66, 110), MAT.Fabric, { rot: { yaw }, canCollide: true }),
  b([x, 4.2, z - 2.6], [14, 3.4, 1.4], rgb(64, 56, 98), MAT.Fabric, { rot: { yaw }, canCollide: true }),
  b([x - 6.6, 3.2, z], [1.2, 3.2, 6], rgb(64, 56, 98), MAT.Fabric, { rot: { yaw }, canCollide: true }),
  b([x + 6.6, 3.2, z], [1.2, 3.2, 6], rgb(64, 56, 98), MAT.Fabric, { rot: { yaw }, canCollide: true }),
  b([x - 3.4, 3.3, z + 0.4], [1.6, 1.2, 4.6], rgb(120, 100, 170), MAT.Fabric, { rot: { yaw } }),
  b([x + 3.4, 3.3, z + 0.4], [1.6, 1.2, 4.6], rgb(120, 100, 170), MAT.Fabric, { rot: { yaw } }),
];

const bedSet = (x, z) => {
  const parts = [];
  // основание и матрас
  parts.push(b([x, 1.2, z], [12, 2.4, 18], rgb(72, 52, 36), MAT.Wood, { canCollide: true }));
  parts.push(b([x, 2.8, z], [11.2, 1.6, 17], rgb(196, 202, 232), MAT.Fabric, { canCollide: true }));
  // подушки
  parts.push(b([x - 3, 4.2, z - 6.4], [4.6, 1.4, 3], rgb(240, 240, 250), MAT.Fabric));
  parts.push(b([x + 3, 4.2, z - 6.4], [4.6, 1.4, 3], rgb(240, 240, 250), MAT.Fabric));
  // одеяло
  parts.push(b([x, 3.9, z + 3.2], [11.4, 1.2, 9.4], rgb(122, 112, 190), MAT.Fabric, { canCollide: true }));
  // спинка
  parts.push(b([x, 5.6, z - 9], [12.6, 8, 1], rgb(62, 44, 32), MAT.WoodPlanks, { canCollide: true }));
  // тумбочка + лампа
  parts.push(b([x + 8.4, 2.2, z - 7], [4.4, 4.4, 4.4], rgb(66, 48, 34), MAT.Wood, { canCollide: true }));
  parts.push(b([x + 8.4, 5.2, z - 7], [1, 1.8, 1], rgb(70, 70, 82), MAT.Metal));
  parts.push(b([x + 8.4, 6.6, z - 7], [2.8, 1.8, 2.8], rgb(255, 226, 180), MAT.Neon, { transparency: 0.1 }));
  parts.push(lamp([x + 8.4, 6.2, z - 7], rgb(255, 220, 175), 1.8, 26));
  return parts;
};

const windowWall = (x, z, yaw) => {
  // Окно: рама + ночное небо + луна (стена остаётся целой — без дыр в геометрии)
  const parts = [];
  parts.push(d([x, 11, z], [18, 12, 0.4], rgb(12, 14, 28), MAT.SmoothPlastic, { rot: { yaw: yaw || 0 } }));
  parts.push(d([x, 11, z], [17.2, 11.2, 0.2], rgb(26, 32, 62), MAT.Glass, { rot: { yaw: yaw || 0 }, transparency: 0.35 }));
  parts.push(sph([x + 4, 14, z], 3.6, rgb(240, 240, 224), MAT.Neon, { transparency: 0.05 }));
  for (let i = 0; i < 14; i++) {
    const sx = x - 7 + (i * 1.1) % 14;
    const sy = 7 + (i * 2.3) % 9;
    parts.push(sph([sx, sy, z], 0.4, rgb(220, 226, 255), MAT.Neon, { transparency: 0.2 }));
  }
  // рама
  parts.push(d([x, 17, z], [18.6, 0.8, 0.7], rgb(40, 32, 26), MAT.WoodPlanks, { rot: { yaw: yaw || 0 } }));
  parts.push(d([x, 5, z], [18.6, 0.8, 0.7], rgb(40, 32, 26), MAT.WoodPlanks, { rot: { yaw: yaw || 0 } }));
  parts.push(d([x - 8.8, 11, z], [0.8, 12.6, 0.7], rgb(40, 32, 26), MAT.WoodPlanks, { rot: { yaw: yaw || 0 } }));
  parts.push(d([x + 8.8, 11, z], [0.8, 12.6, 0.7], rgb(40, 32, 26), MAT.WoodPlanks, { rot: { yaw: yaw || 0 } }));
  parts.push(d([x, 11, z], [0.5, 12, 0.5], rgb(40, 32, 26), MAT.WoodPlanks, { rot: { yaw: yaw || 0 } }));
  return parts;
};

const neonSign = (z) => [
  b([0, 12.8, z], [26, 5.2, 0.5], rgb(12, 10, 24), MAT.SmoothPlastic),
  el('Part', {
    Name: 'LobbyTitleBoard',
    Size: v3(25, 4.4, 0.2),
    CFrame: cf([0, 13.4, z + 0.35], { yaw: 0 }),
    Color: rgb(9, 8, 18),
    Material: MAT.SmoothPlastic,
    Anchored: true,
    CanCollide: false,
  }, [
    el('SurfaceGui', { Face: 'Front', PixelsPerStud: 64, LightInfluence: 0.1 }, [
      el('TextLabel', {
        Size: udim2(1, 1),
        BackgroundTransparency: 1,
        Text: '🌙 DREAM SIMULATOR',
        TextColor3: rgb(240, 234, 255),
        TextScaled: true,
        FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold'),
        TextStrokeTransparency: 0.4,
      }),
    ]),
  ]),
  el('Part', {
    Name: 'LobbySubtitleBoard',
    Size: v3(20, 1.8, 0.2),
    CFrame: cf([0, 10.6, z + 0.35]),
    Color: rgb(9, 8, 18),
    Material: MAT.SmoothPlastic,
    Anchored: true,
    CanCollide: false,
  }, [
    el('SurfaceGui', { Face: 'Front', PixelsPerStud: 64, LightInfluence: 0.1 }, [
      el('TextLabel', {
        Size: udim2(1, 1),
        BackgroundTransparency: 1,
        Text: 'Каждый сон — новая история...',
        TextColor3: rgb(198, 186, 255),
        TextScaled: true,
        FontFace: font('rbxasset://fonts/families/GothamSSm.json'),
      }),
    ]),
  ]),
  el('Part', {
    Name: 'LobbyTagBoard',
    Size: v3(9, 1.2, 0.2),
    CFrame: cf([0, 9.1, z + 0.35]),
    Color: rgb(9, 8, 18),
    Material: MAT.SmoothPlastic,
    Anchored: true,
    CanCollide: false,
  }, [
    el('SurfaceGui', { Face: 'Front', PixelsPerStud: 64, LightInfluence: 0.1 }, [
      el('TextLabel', {
        Size: udim2(1, 1),
        BackgroundTransparency: 1,
        Text: 'ЛОББИ · СПАЛЬНЯ СНОВИДЦА',
        TextColor3: rgb(150, 136, 235),
        TextScaled: true,
        FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold'),
      }),
    ]),
  ]),
  // неоновые полосы
  d([0, 15.6, z + 0.3], [26.5, 0.3, 0.25], rgb(150, 120, 255), MAT.Neon),
  d([0, 10.0, z + 0.3], [26.5, 0.3, 0.25], rgb(150, 120, 255), MAT.Neon),
  d([-13.2, 12.8, z + 0.3], [0.3, 5.6, 0.25], rgb(120, 96, 230), MAT.Neon),
  d([13.2, 12.8, z + 0.3], [0.3, 5.6, 0.25], rgb(120, 96, 230), MAT.Neon),
  lamp([0, 12.8, z + 2.4], rgb(160, 130, 255), 2.2, 30),
];

/* --------------------------- сборка --------------------------- */
export function lobby() {
  const parts = [];
  const { w, d: depth, h } = ROOM;

  /* пол и потолок */
  parts.push(b([0, -0.5, 0], [w, 1, depth], rgb(74, 58, 44), MAT.WoodPlanks, { canCollide: true, name: 'Floor' }));
  parts.push(b([0, h + 0.5, 0], [w, 1, depth], rgb(48, 44, 56), MAT.Plaster, { canCollide: true, name: 'Ceiling' }));

  /* стены (сплошные, без щелей) */
  parts.push(b([0, h / 2, -depth / 2 - 0.5], [w + 2, h, 1], rgb(56, 54, 72), MAT.Plaster, { canCollide: true, name: 'WallBack' }));
  parts.push(b([0, h / 2, depth / 2 + 0.5], [w + 2, h, 1], rgb(56, 54, 72), MAT.Plaster, { canCollide: true, name: 'WallFront' }));
  parts.push(b([-w / 2 - 0.5, h / 2, 0], [1, h, depth + 2], rgb(52, 50, 68), MAT.Plaster, { canCollide: true, name: 'WallLeft' }));
  parts.push(b([w / 2 + 0.5, h / 2, 0], [1, h, depth + 2], rgb(52, 50, 68), MAT.Plaster, { canCollide: true, name: 'WallRight' }));

  /* плинтусы и карниз */
  for (const [x, z, sx, sz] of [[0, -depth / 2 + 0.3, w, 0.4], [0, depth / 2 - 0.3, w, 0.4], [-w / 2 + 0.3, 0, 0.4, depth], [w / 2 - 0.3, 0, 0.4, depth]]) {
    parts.push(d([x, 0.55, z], [sx, 1.1, sz], rgb(46, 34, 26), MAT.WoodPlanks));
    parts.push(d([x, h - 0.6, z], [sx, 0.5, sz], rgb(46, 34, 26), MAT.WoodPlanks));
  }

  /* панели на стенах */
  for (let i = -9; i <= 9; i++) {
    parts.push(d([i * 2.2, 5.5, -depth / 2 + 0.25], [0.16, 9, 0.2], rgb(44, 40, 58), MAT.WoodPlanks));
  }

  /* ковёр, мебель, растения */
  parts.push(rug([0, 0.06, 4], [26, 22], rgb(48, 40, 70), MAT.Fabric));
  parts.push(...bedSet(-14, -11));
  parts.push(...windowWall(0, -depth / 2 + 0.45));
  parts.push(...sofa(13, 15, 0));
  parts.push(...desk(14, -16));
  parts.push(...bookshelf(-19.5, 6, 90));
  parts.push(...bookshelf(-19.5, 16, 90));
  parts.push(...plant([-18, 2.2, -19], 1.1));
  parts.push(...plant([18, 2.2, 4], 1.0));
  parts.push(...plant([18, 2.2, -6], 0.9));
  parts.push(...plant([-18, 2.2, 21], 0.8));
  // хлам для живости
  parts.push(b([-4, 1.4, 20], [4, 2.8, 4], rgb(112, 86, 60), MAT.Cardboard, { canCollide: true }));
  parts.push(b([-4, 2.9, 20], [3.4, 0.2, 3.4], rgb(96, 74, 52), MAT.Cardboard));
  parts.push(sph([1.2, 1.2, 18.4], 2, rgb(220, 120, 140), MAT.Fabric));
  parts.push(sph([0, 1.4, 18.6], 1.2, rgb(120, 150, 220), MAT.Fabric));

  /* свет */
  parts.push(...ceilingLight(0, 0));
  parts.push(...ceilingLight(-12, 12));
  parts.push(...ceilingLight(12, -8));
  parts.push(...sconce([-w / 2 + 1.2, 9, -8], 90));
  parts.push(...sconce([-w / 2 + 1.2, 9, 10], 90));
  parts.push(...sconce([w / 2 - 1.2, 9, 6], 270));
  parts.push(...sconce([w / 2 - 1.2, 9, -14], 270));
  parts.push(...neonSign(-depth / 2 + 0.4));

  /* гирлянда над кроватью */
  for (let i = 0; i < 13; i++) {
    const x = -21 + i * 1.35;
    const y = 16.2 - Math.sin((i / 12) * Math.PI) * 1.6;
    parts.push(el('Part', {
      Name: 'StringLight',
      Shape: 'Ball',
      Size: v3(0.5, 0.5, 0.5),
      CFrame: cf([x, y, -depth / 2 + 1.2]),
      Color: i % 2 === 0 ? rgb(255, 196, 130) : rgb(190, 156, 255),
      Material: MAT.Neon,
      Anchored: true,
      CanCollide: false,
    }));
  }

  /* маркеры */
  // Точка кровати + интерактивный prompt: можно лечь спать прямо из лобби.
  parts.push(el('Part', {
    Name: 'BedPoint',
    Size: v3(1, 1, 1),
    CFrame: cf([-14, 3.6, -11]),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
    Color: rgb(255, 0, 255),
    Material: 'Neon',
  }, [
    el('ProximityPrompt', {
      Name: 'BedPrompt',
      ActionText: 'Лечь спать',
      ObjectText: 'Кровать сновидца',
      HoldDuration: 0.6,
      MaxActivationDistance: 11,
      RequiresLineOfSight: false,
      KeyboardKeyCode: 'E',
      ClickablePrompt: true,
      Style: 'Default',
    }),
  ]));
  parts.push(marker('SleepCameraPoint', [0, 11, 8]));
  parts.push(marker('CameraTarget', [0, 12, -depth / 2 + 1], { Size: v3(1, 1, 1) }));

  /* спавн: ровно над полом, в свободном центре комнаты */
  parts.push(el('SpawnLocation', {
    Name: 'MenuSpawn',
    Size: v3(8, 1, 8),
    CFrame: cf([0, 0.5, 14]),
    Anchored: true,
    Neutral: true,
    Enabled: true,
    Transparency: 1,
    CanCollide: false,
    Color: rgb(40, 34, 60),
    Material: MAT.SmoothPlastic,
    Duration: 0,
  }));

  /* декоративный путь камеры (запасной вариант, если понадобится облёт) */
  const cameraPath = el('Folder', { Name: 'CameraPath' }, [
    ['Node1', [16, 9, 18]], ['Node2', [8, 10, 20]], ['Node3', [-4, 10.5, 18]],
    ['Node4', [-16, 10, 8]], ['Node5', [-12, 11, -6]], ['Node6', [8, 11.5, 0]],
  ].map(([name, pos]) => el('Part', {
    Name: name, Size: v3(1, 1, 1), CFrame: cf(pos), Transparency: 1,
    Anchored: true, CanCollide: false, CanQuery: false,
  })));

  const scene = model('MenuBedroom', flat(parts), [0, 0, 0]);
  scene.children.push(cameraPath);
  scene.children.push(el('Folder', { Name: 'LobbyMeta' }, [
    el('StringValue', { Name: 'Version', Value: 'lobby-2' }),
  ]));
  return scene;
}
