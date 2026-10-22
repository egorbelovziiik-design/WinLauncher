/**
 * kit.mjs — «конструктор» геометрии снов: комнаты, дома, деревья, фонари, маркеры.
 * Все функции возвращают один инстанс или массив инстансов.
 */
import { el, v3, v2, c3, cf, udim2, udim, rgb, font, nr, ns, cs } from '../rbxkit.mjs';

export const M = {
  Concrete: 'Concrete', Brick: 'Brick', Wood: 'Wood', WoodPlanks: 'WoodPlanks',
  Metal: 'Metal', CorrodedMetal: 'CorrodedMetal', Neon: 'Neon', Glass: 'Glass',
  Fabric: 'Fabric', Carpet: 'Carpet', Grass: 'Grass', LeafyGrass: 'LeafyGrass',
  Rock: 'Rock', Slate: 'Slate', Ice: 'Ice', Snow: 'Snow', Sand: 'Sand',
  Marble: 'Marble', Granite: 'Granite', Pebble: 'Pebble', Cobblestone: 'Cobblestone',
  Asphalt: 'Asphalt', Pavement: 'Pavement', DiamondPlate: 'DiamondPlate',
  CeramicTiles: 'CeramicTiles', Plaster: 'Plaster', Rubber: 'Rubber',
  DiamondPlate: 'DiamondPlate', Water: 'Water', SmoothPlastic: 'SmoothPlastic',
  ForceField: 'ForceField', Basalt: 'Basalt', CrackedLava: 'CrackedLava',
  Mud: 'Mud', Limestone: 'Limestone', Salt: 'Salt', RoofShingles: 'RoofShingles',
  ClayRoofTiles: 'ClayRoofTiles', Cardboard: 'Cardboard', Foil: 'Foil',
};

const flat = (arr) => arr.flat(4).filter(Boolean);

/** Блок. */
export const box = (pos, size, color, material = M.Concrete, extra = {}) =>
  el('Part', {
    Size: v3(size[0], size[1], size[2]),
    CFrame: cf(pos, extra.rot || {}),
    Color: color,
    Material: material,
    Anchored: true,
    TopSurface: 'Smooth', BottomSurface: 'Smooth', LeftSurface: 'Smooth',
    RightSurface: 'Smooth', FrontSurface: 'Smooth', BackSurface: 'Smooth',
    CastShadow: extra.castShadow !== false,
    ...(extra.name ? { Name: extra.name } : {}),
    ...(extra.transparency !== undefined ? { Transparency: extra.transparency } : {}),
    ...(extra.canCollide !== undefined ? { CanCollide: extra.canCollide } : { CanCollide: extra.canCollide || false }),
    ...(extra.reflectance !== undefined ? { Reflectance: extra.reflectance } : {}),
  });

/** Декоративный блок (не мешает движению). */
export const deco = (pos, size, color, material = M.SmoothPlastic, extra = {}) =>
  box(pos, size, color, material, { canCollide: false, name: 'Deco_' + (extra.tag || 'piece'), ...extra });

/** Шар / сфера. */
export const sphere = (pos, size, color, material = M.SmoothPlastic, extra = {}) =>
  el('Part', {
    Shape: 'Ball',
    Size: v3(size, size, size),
    CFrame: cf(pos, extra.rot || {}),
    Color: color,
    Material: material,
    Anchored: true,
    CanCollide: false,
    ...(extra.transparency !== undefined ? { Transparency: extra.transparency } : {}),
  });

/** Цилиндр (ось Y по умолчанию). */
export const cyl = (pos, size, color, material = M.Metal, rot = {}, extra = {}) =>
  el('Part', {
    Shape: 'Cylinder',
    Size: v3(size[0], size[1], size[2]),
    CFrame: cf(pos, rot),
    Color: color,
    Material: material,
    Anchored: true,
    CanCollide: false,
    ...(extra.transparency !== undefined ? { Transparency: extra.transparency } : {}),
  });

/** Клин (крыша, скат). */
export const wedge = (pos, size, color, material = M.Concrete, rot = {}) =>
  el('WedgePart', {
    Size: v3(...size),
    CFrame: cf(pos, rot),
    Color: color,
    Material: material,
    Anchored: true,
    CanCollide: false,
  });

export const pointLight = (color, brightness = 2, range = 18, shadows = false) =>
  el('PointLight', { Color: color, Brightness: brightness, Range: range, Shadows: shadows });

export const spotLight = (color, brightness = 3, range = 40, angle = 70, shadows = true) =>
  el('SpotLight', { Color: color, Brightness: brightness, Range: range, Angle: angle, Shadows: shadows });

export const fogEmitter = (pos, color, rate = 18, size = 40) =>
  el('Part', {
    Name: 'FogVolume',
    Size: v3(size, 24, size),
    CFrame: cf(pos),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
    Color: color,
    Material: M.SmoothPlastic,
  }, [
    el('ParticleEmitter', {
      Name: 'Fog',
      Texture: 'rbxasset://textures/particles/smoke_main.dds',
      Color: cs([[0, c3(color.v[0], color.v[1], color.v[2])], [1, c3(color.v[0], color.v[1], color.v[2])]]),
      LightInfluence: 0.5,
      Rate: rate,
      Lifetime: nr(6, 12),
      Speed: nr(0.2, 1.2),
      Size: ns([[0, 8], [0.35, 20], [1, 26]]),
      Transparency: ns([[0, 0.75], [0.5, 0.62], [1, 0.9]]),
      Rotation: nr(0, 360),
      RotSpeed: nr(-4, 4),
      Acceleration: v3(0, 0.6, 0),
    }),
  ]);

/** Туман: «дымовая» сфера с частицами. */
export const mist = (pos, color, opts = {}) =>
  el('Part', {
    Name: 'Mist',
    Size: v3(opts.spread || 60, 24, opts.spread || 60),
    CFrame: cf(pos),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
    Color: color,
    Material: M.SmoothPlastic,
  }, [
    el('ParticleEmitter', {
      Name: 'MistEmitter',
      Texture: 'rbxasset://textures/particles/smoke_main.dds',
      Color: cs([[0, c3(color.v[0], color.v[1], color.v[2])], [1, c3(color.v[0], color.v[1], color.v[2])]]),
      LightInfluence: 0.7,
      Rate: opts.rate ?? 22,
      Lifetime: nr(8, 16),
      Speed: nr(0.1, 0.6),
      Size: ns([[0, 20], [1, 38]]),
      Transparency: ns([[0, 0.82], [1, 0.96]]),
      Rotation: nr(0, 360),
      RotSpeed: nr(-3, 3),
      Acceleration: v3(0.2, 0.4, 0.2),
    }),
  ]);

/** Снег / пыль / искры — универсальный эмиттер. */
export const particles = (pos, opts = {}) =>
  el('Part', {
    Name: opts.name || 'Particles',
    Size: opts.size ? v3(opts.size, opts.size, opts.size) : v3(40, 20, 40),
    CFrame: cf(pos),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
    Color: opts.color || c3(1, 1, 1),
    Material: M.SmoothPlastic,
  }, [
    el('ParticleEmitter', {
      Name: 'Emit',
      Texture: opts.texture || 'rbxasset://textures/particles/sparkles_main.dds',
      Rate: opts.rate ?? 12,
      Lifetime: nr(opts.life ?? 3, (opts.life ?? 3) * 2),
      Speed: nr(0.5, opts.speed ?? 2),
      Size: ns([[0, opts.startSize ?? 0.4], [1, opts.endSize ?? 0.9]]),
      Transparency: ns([[0, opts.transparency ?? 0.4], [1, 1]]),
      SpreadAngle: v2(180, 180),
      Rotation: nr(0, 360),
      RotSpeed: nr(-30, 30),
      Acceleration: opts.accel ? v3(opts.accel[0], opts.accel[1], opts.accel[2]) : v3(0, -1.5, 0),
      LightEmission: opts.glow ? 0.6 : 0,
      LightInfluence: opts.lightInfluence ?? 0.8,
    }),
  ]);

/** Светящаяся надпись на поверхности (неоновая вывеска). */
export const sign = (pos, size, text, color, opts = {}) =>
  el('Part', {
    Name: opts.name || 'SignBoard',
    Size: v3(size[0], size[1], 0.4),
    CFrame: cf(pos, opts.rot || {}),
    Color: opts.boardColor || rgb(12, 12, 20),
    Material: opts.boardMaterial || M.SmoothPlastic,
    Anchored: true,
    CanCollide: false,
  }, [
    el('SurfaceGui', {
      Face: 'Front',
      PixelsPerStud: 60,
      AlwaysOnTop: false,
      LightInfluence: 0.2,
    }, [
      el('TextLabel', {
        Size: udim2(1, 1),
        BackgroundTransparency: 1,
        Text: text,
        TextColor3: color,
        TextScaled: true,
        FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold'),
        TextStrokeTransparency: 0.5,
      }),
    ]),
  ]);

export const billboardText = (pos, text, color, size = [10, 3], opts = {}) =>
  el('Part', {
    Name: 'Billboard',
    Size: v3(1, 1, 1),
    CFrame: cf(pos),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
  }, [
    el('BillboardGui', {
      Size: udim2(udim(0, size[0] * 30), udim(0, size[1] * 30)),
      AlwaysOnTop: true,
      MaxDistance: opts.maxDistance || 200,
    }, [
      el('TextLabel', {
        Size: udim2(1, 1),
        BackgroundTransparency: 1,
        Text: text,
        TextColor3: color,
        TextScaled: true,
        FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold'),
        TextStrokeTransparency: 0.4,
      }),
    ]),
  ]);

/* ------------------------- архитектура ------------------------- */

/** Пол заданного размера с поверхностью на y = 0. */
export const ground = (w, d, color, material = M.Grass, y = 0, extra = {}) =>
  el('Part', {
    Name: 'Ground',
    Size: v3(w, 4, d),
    CFrame: cf([0, y - 2, 0]),
    Color: color,
    Material: material,
    Anchored: true,
    TopSurface: 'Smooth',
    ...extra,
  });

export const wall = (pos, length, height = 12, thickness = 1, color = rgb(90, 90, 100), material = M.Concrete, rot = {}) =>
  box(pos, [length, height, thickness], color, material, { rot, canCollide: true, castShadow: true });

/** Простая комната: пол, 4 стены, потолок (по желанию). */
export function room(opts) {
  const {
    pos = [0, 0, 0], w = 40, d = 40, h = 14,
    floorColor = rgb(50, 50, 58), wallColor = rgb(80, 80, 92),
    floorMaterial = M.Concrete, wallMaterial = M.Concrete,
    ceiling = true, doorSide = null, windows = 0,
  } = opts;
  const [x, y, z] = pos;
  const parts = [];
  parts.push(box([x, y - 0.5, z], [w, 1, d], floorColor, floorMaterial, { canCollide: true }));
  if (ceiling) parts.push(box([x, y + h, z], [w, 2, d], wallColor, wallMaterial, { canCollide: true }));
  const halfD = d / 2 - 0.5;
  const halfW = w / 2 - 0.5;
  parts.push(wall([x, y + h / 2, z - halfD], w, h, 1, wallColor, wallMaterial));
  parts.push(wall([x, y + h / 2, z + halfD], w, h, 1, wallColor, wallMaterial));
  parts.push(wall([x - halfW, y + h / 2, z], d, h, 1, wallColor, wallMaterial, { yaw: 90 }));
  parts.push(wall([x + halfW, y + h / 2, z], d, h, 1, wallColor, wallMaterial, { yaw: 90 }));
  for (let i = 0; i < windows; i++) {
    const wz = z - d / 3 + (i * d) / Math.max(1, windows * 1.5);
    parts.push(box([x - halfW, y + h / 2, wz], [0.6, h * 0.45, w * 0.12], rgb(20, 30, 45), M.Glass, { transparency: 0.45 }));
  }
  return parts;
}

/** Оконная рама со стеклом. */
export const windowFrame = (pos, size, color = rgb(40, 50, 70), rot = {}) => {
  const [w, h] = size;
  return [
    box(pos, [w, h, 0.3], rgb(18, 24, 36), M.Glass, { rot, transparency: 0.55 }),
    box([pos[0], pos[1] + h / 2, pos[2]], [w + 0.4, 0.3, 0.5], color, M.WoodPlanks, { rot, canCollide: false }),
    box([pos[0], pos[1] - h / 2, pos[2]], [w + 0.4, 0.3, 0.5], color, M.WoodPlanks, { rot, canCollide: false }),
    box([pos[0], pos[1], pos[2] + 0.1], [0.22, h, 0.42], color, M.WoodPlanks, { rot, canCollide: false }),
  ];
};

/** Детализированное здание с окнами, крышей и дверью. */
export function building(opts) {
  const {
    pos = [0, 0, 0], w = 24, d = 24, h = 40, floors = 5,
    color = rgb(46, 46, 56), windowColor = rgb(255, 210, 140),
    litChance = 0.35, roofColor = rgb(28, 28, 36), door = true,
  } = opts;
  const [x, y, z] = pos;
  const parts = [];
  parts.push(box([x, y + h / 2, z], [w, h, d], color, M.Concrete, { canCollide: true }));
  parts.push(box([x, y + h + 0.6, z], [w + 1.2, 1.2, d + 1.2], roofColor, M.Concrete, { canCollide: true }));
  // Пояс этажей
  for (let f = 1; f <= floors; f++) {
    const fy = y + (h / (floors + 1)) * f;
    parts.push(box([x, fy, z], [w + 0.4, 0.3, d + 0.4], roofColor, M.Concrete));
  }
  // Окна: фасады и бока (экономно по количеству)
  const cols = Math.min(4, Math.max(2, Math.floor(w / 12)));
  for (let f = 0; f < floors; f++) {
    const fy = y + (h / (floors + 1)) * (f + 0.6) + 2;
    const wh = (h / (floors + 1)) * 0.42;
    const ww = (w / (cols + 1)) * 0.62;
    for (let c = 0; c < cols; c++) {
      const offset = (c - (cols - 1) / 2) * (w / (cols + 1));
      const lit = Math.random() < litChance;
      const winColor = lit ? windowColor : rgb(12, 16, 26);
      const mat = lit ? M.Neon : M.Glass;
      const tr = lit ? 0.1 : 0.5;
      parts.push(box([x + offset, fy, z - d / 2 - 0.2], [ww, wh, 0.3], winColor, mat, { transparency: tr }));
      parts.push(box([x + offset, fy, z + d / 2 + 0.2], [ww, wh, 0.3], winColor, mat, { transparency: tr }));
    }
    const sideCols = Math.min(3, cols);
    for (let c = 0; c < sideCols; c++) {
      const offset = (c - (sideCols - 1) / 2) * (d / (sideCols + 1));
      const lit = Math.random() < litChance;
      const winColor = lit ? windowColor : rgb(12, 16, 26);
      const mat = lit ? M.Neon : M.Glass;
      const tr = lit ? 0.1 : 0.5;
      parts.push(box([x - w / 2 - 0.2, fy, z + offset], [0.3, wh, ww], winColor, mat, { transparency: tr }));
      parts.push(box([x + w / 2 + 0.2, fy, z + offset], [0.3, wh, ww], winColor, mat, { transparency: tr }));
    }
  }
  if (door) {
    parts.push(box([x, y + 3, z - d / 2 - 0.4], [5, 6, 0.6], rgb(18, 14, 12), M.Wood, { canCollide: true }));
    parts.push(box([x, y + 6.4, z - d / 2 - 0.5], [5.6, 0.4, 0.9], rgb(60, 50, 40), M.Wood));
  }
  return parts;
}

/** Дерево: ствол + несколько сфер кроны. */
export function tree(pos, scale = 1, trunkColor = rgb(58, 42, 30), leafColor = rgb(30, 70, 45)) {
  const [x, y, z] = pos;
  const h = 16 * scale;
  const parts = [
    cyl([x, y + h / 2, z], [2.6 * scale, h, 2.6 * scale], trunkColor, M.Wood),
  ];
  const blobs = 4;
  for (let i = 0; i < blobs; i++) {
    const angle = (i / blobs) * Math.PI * 2;
    parts.push(sphere(
      [x + Math.cos(angle) * 3 * scale, y + h * 0.85 + Math.sin(i * 2) * 2 * scale, z + Math.sin(angle) * 3 * scale],
      (11 + Math.random() * 5) * scale, leafColor, M.LeafyGrass
    ));
  }
  parts.push(sphere([x, y + h + 3 * scale, z], 13 * scale, leafColor, M.LeafyGrass));
  return parts;
}

/** Ель для ледяного мира. */
export function pine(pos, scale = 1, color = rgb(30, 60, 50)) {
  const [x, y, z] = pos;
  const parts = [cyl([x, y + 4 * scale, z], [2 * scale, 8 * scale, 2 * scale], rgb(50, 40, 32), M.Wood)];
  for (let i = 0; i < 3; i++) {
    parts.push(el('Part', {
      Shape: 'Ball',
      Size: v3((14 - i * 3) * scale, (10 - i * 2) * scale, (14 - i * 3) * scale),
      CFrame: cf([x, y + (9 + i * 5) * scale, z]),
      Color: color,
      Material: M.LeafyGrass,
      Anchored: true,
      CanCollide: false,
      Transparency: 0.05,
    }));
  }
  return parts;
}

/** Уличный фонарь с тёплым светом. */
export function streetLamp(pos, color = rgb(255, 200, 130), opts = {}) {
  const [x, y, z] = pos;
  const h = opts.height || 16;
  return [
    cyl([x, y + h / 2, z], [0.7, h, 0.7], rgb(30, 32, 38), M.Metal),
    box([x, y + h, z], [3, 0.8, 3], rgb(26, 28, 34), M.Metal),
    box([x, y + h - 0.7, z], [2, 0.5, 2], color, M.Neon, { transparency: 0.15 }),
    el('Part', {
      Name: 'LampGlow',
      Size: v3(2, 0.5, 2),
      CFrame: cf([x, y + h - 0.7, z]),
      Transparency: 1,
      Anchored: true,
      CanCollide: false,
      Color: color,
    }, [pointLight(color, opts.brightness || 3, opts.range || 28)]),
  ];
}

export const fence = (from, to, color = rgb(40, 38, 36), height = 6) => {
  const [x1, y1, z1] = from;
  const [x2, , z2] = to;
  const dx = x2 - x1, dz = z2 - z1;
  const length = Math.hypot(dx, dz);
  const yaw = (Math.atan2(-dx, -dz) * 180) / Math.PI;
  const cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
  const parts = [
    box([cx, y1 + height / 2, cz], [length, 0.3, 0.3], color, M.WoodPlanks, { rot: { yaw }, canCollide: false }),
    box([cx, y1 + height * 0.7, cz], [length, 0.3, 0.3], color, M.WoodPlanks, { rot: { yaw }, canCollide: false }),
  ];
  const posts = Math.max(2, Math.floor(length / 8));
  for (let i = 0; i <= posts; i++) {
    const t = i / posts;
    parts.push(box([x1 + dx * t, y1 + height / 2, z1 + dz * t], [0.35, height, 0.35], color, M.WoodPlanks, { canCollide: false }));
  }
  return parts;
};

/* ------------------------- мебель/декор ------------------------- */

export const crate = (pos, scale = 1, color = rgb(96, 72, 44)) => {
  const s = 4 * scale;
  const [x, y, z] = pos;
  return [
    box([x, y + s / 2, z], [s, s, s], color, M.WoodPlanks, { canCollide: true }),
    box([x, y + s / 2, z], [s + 0.2, 0.3, s + 0.2], rgb(70, 52, 32), M.WoodPlanks),
    box([x, y + s / 2, z + s / 2 + 0.1], [s * 0.8, s * 0.12, 0.1], rgb(50, 38, 24), M.WoodPlanks),
  ];
};

export const barrel = (pos, color = rgb(80, 60, 40)) => [
  cyl([pos[0], pos[1] + 2, pos[2]], [4, 4, 4], color, M.Wood),
  cyl([pos[0], pos[1] + 4.1, pos[2]], [4.2, 0.3, 4.2], rgb(40, 40, 44), M.Metal),
];

export const table = (pos, size = [8, 0.5, 4], color = rgb(70, 50, 32)) => {
  const [x, y, z] = pos;
  const [w, , d] = size;
  const legs = [];
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    legs.push(box([x + sx * (w / 2 - 0.6), y + 1.4, z + sz * (d / 2 - 0.6)], [0.5, 3, 0.5], color, M.Wood));
  }
  return [box([x, y + 3, z], size, color, M.Wood, { canCollide: true }), ...legs];
};

export const chair = (pos, color = rgb(60, 44, 30), rot = {}) => {
  const [x, y, z] = pos;
  return [
    box([x, y + 2, z], [2.2, 0.4, 2.2], color, M.Wood, { rot }),
    box([x, y + 1, z], [0.4, 2, 0.4], color, M.Wood, { rot }),
    box([x, y + 3.6, z - 0.9], [2.2, 3, 0.4], color, M.Wood, { rot }),
  ];
};

export const shelf = (pos, size = [8, 12, 2], color = rgb(64, 48, 34), rot = {}) => {
  const [x, y, z] = pos;
  const [w, h, d] = size;
  const parts = [
    box([x, y + h / 2, z], [w, h, d], color, M.WoodPlanks, { rot, canCollide: true }),
    box([x, y + h / 2, z + d / 2 + 0.1], [w * 0.9, h * 0.9, 0.1], rgb(40, 30, 22), M.WoodPlanks, { rot }),
  ];
  for (let i = 1; i <= 3; i++) {
    parts.push(box([x, y + (h / 4) * i, z + d / 2 + 0.15], [w * 0.94, 0.25, 0.6], rgb(40, 30, 22), M.WoodPlanks, { rot }));
  }
  return parts;
};

export const bookStack = (pos, colors = [rgb(150, 60, 60), rgb(60, 90, 150), rgb(150, 130, 60)]) =>
  colors.map((color, i) => box([pos[0] + (i % 2) * 0.4, pos[1] + 0.3 + i * 0.55, pos[2]], [2.2, 0.5, 1.5], color, M.SmoothPlastic, { rot: { yaw: (i * 7) % 30 } }));

export const pcSetup = (pos, rot = {}) => {
  const [x, y, z] = pos;
  return [
    box([x, y + 3, z], [12, 0.5, 5], rgb(70, 52, 36), M.Wood, { rot, canCollide: true }),
    box([x - 3, y + 3.6, z], [3, 0.4, 2.4], rgb(30, 32, 40), M.SmoothPlastic, { rot }),
    box([x - 3, y + 5.4, z - 0.4], [4.4, 2.8, 0.3], rgb(20, 26, 40), M.SmoothPlastic, { rot }),
    box([x - 3, y + 5.4, z - 0.55], [4, 2.4, 0.1], rgb(90, 180, 255), M.Neon, { rot, transparency: 0.2 }),
    box([x + 3.4, y + 4.2, z], [0.5, 2, 0.5], rgb(30, 32, 40), M.SmoothPlastic, { rot }),
    box([x + 4.6, y + 3.6, z], [1.4, 0.3, 3.2], rgb(26, 28, 36), M.SmoothPlastic, { rot }),
    el('Part', {
      Name: 'MonitorGlow', Size: v3(1, 1, 1), CFrame: cf([x - 3, y + 5.4, z + 0.4], rot),
      Transparency: 1, Anchored: true, CanCollide: false, Color: rgb(90, 180, 255),
    }, [pointLight(rgb(90, 170, 255), 2, 14)]),
  ];
};

export const rug = (pos, size, color, material = M.Fabric) =>
  box([pos[0], pos[1] + 0.06, pos[2]], [size[0], 0.12, size[1]], color, material, { canCollide: false });

export const poster = (pos, size, color, rot = {}, text = null) =>
  el('Part', {
    Name: 'Poster',
    Size: v3(size[0], size[1], 0.12),
    CFrame: cf(pos, rot),
    Color: color,
    Material: M.SmoothPlastic,
    Anchored: true,
    CanCollide: false,
  }, text ? [
    el('SurfaceGui', { Face: 'Front', PixelsPerStud: 40, LightInfluence: 0.3 }, [
      el('TextLabel', {
        Size: udim2(1, 1), BackgroundTransparency: 1, Text: text, TextScaled: true,
        TextColor3: rgb(240, 235, 255), FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold'),
      }),
    ]),
  ] : []);

export const plant = (pos, scale = 1) => {
  const [x, y, z] = pos;
  return [
    cyl([x, y + 1.6 * scale, z], [3.4 * scale, 3.2 * scale, 3.4 * scale], rgb(120, 70, 50), M.Marble),
    sphere([x, y + 4.4 * scale, z], 5 * scale, rgb(40, 100, 55), M.LeafyGrass),
    sphere([x + 1.2 * scale, y + 5.6 * scale, z + 0.4 * scale], 3.4 * scale, rgb(50, 120, 62), M.LeafyGrass),
    sphere([x - 1.1 * scale, y + 5.2 * scale, z - 0.6 * scale], 3.1 * scale, rgb(36, 92, 50), M.LeafyGrass),
  ];
};

export const bed = (pos, rot = {}, colors = { frame: rgb(90, 66, 44), sheet: rgb(190, 200, 235), pillow: rgb(240, 240, 250) }) => {
  const [x, y, z] = pos;
  return [
    box([x, y + 1.6, z], [10, 1.2, 16], colors.frame, M.Wood, { rot, canCollide: true }),
    box([x, y + 2.4, z], [9.4, 1.4, 15], colors.sheet, M.Fabric, { rot, canCollide: true }),
    box([x, y + 3.3, z - 6], [8.6, 0.9, 3.2], colors.pillow, M.Fabric, { rot }),
    box([x, y + 4.4, z - 8.4], [10.4, 6, 0.8], colors.frame, M.Wood, { rot, canCollide: true }),
    box([x, y + 2.4, z + 5.5], [9.4, 1.5, 3.4], rgb(140, 130, 190), M.Fabric, { rot }),
  ];
};

export const wardrobe = (pos, rot = {}, color = rgb(84, 64, 46)) => {
  const [x, y, z] = pos;
  return [
    box([x, y + 7, z], [12, 14, 5], color, M.WoodPlanks, { rot, canCollide: true }),
    box([x, y + 7, z + 2.55], [5.6, 13, 0.2], rgb(70, 52, 36), M.WoodPlanks, { rot }),
    box([x + 2.6, y + 7, z + 2.7], [0.3, 3, 0.3], rgb(220, 200, 150), M.Metal),
    box([x - 2.6, y + 7, z + 2.7], [0.3, 3, 0.3], rgb(220, 200, 150), M.Metal),
    box([x, y + 14.1, z], [12.6, 0.4, 5.6], rgb(60, 44, 30), M.WoodPlanks, { rot }),
  ];
};

export const toy = (pos, color = rgb(230, 120, 140)) => [
  sphere([pos[0], pos[1] + 1, pos[2]], 2, color, M.Fabric),
  sphere([pos[0] - 0.9, pos[1] + 2.1, pos[2]], 0.9, color, M.Fabric),
  sphere([pos[0] + 0.9, pos[1] + 2.1, pos[2]], 0.9, color, M.Fabric),
];

/* ------------------------- маркеры ------------------------- */

export const marker = (name, pos, extra = {}) =>
  el('Part', {
    Name: name,
    Size: v3(1, 1, 1),
    CFrame: cf(pos),
    Transparency: 1,
    CanCollide: false,
    CanQuery: false,
    CanTouch: false,
    Anchored: true,
    Color: rgb(255, 0, 255),
    Material: M.Neon,
    ...extra,
  });

/**
 * Набор маркеров.
 * markers = { spawn:[x,y,z], npcs:[[..]], items:[[..]], interacts:[[..]], patrols:[[..]],
 *             portal:[..], secret:[..], reach:[..], door:[..], boss:[..], monsters:[[..]] }
 */
export function markerSet(markers) {
  const out = [];
  const push = (prefix, list) => {
    (list || []).forEach((pos, index) => {
      out.push(marker(list.length > 1 ? `${prefix}_${index + 1}` : prefix, [pos[0], pos[1] ?? 1, pos[2]]));
    });
  };
  if (markers.spawn) out.push(marker('MK_Spawn', [markers.spawn[0], markers.spawn[1] ?? 2, markers.spawn[2]]));
  push('MK_NPC', markers.npcs);
  push('MK_Item', markers.items);
  push('MK_Interact', markers.interacts);
  push('MK_Patrol', markers.patrols);
  push('MK_Monster', markers.monsters);
  push('MK_Portal', markers.portal);
  push('MK_Secret', markers.secret);
  push('MK_Reach', markers.reach);
  push('MK_Door', markers.door);
  push('MK_Boss', markers.boss);
  return out;
}

export const model = (name, children, pivot = [0, 0, 0]) =>
  el('Model', { Name: name, WorldPivot: cf(pivot) }, flat(children));

export { flat };
