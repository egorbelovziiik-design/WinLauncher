/**
 * rbxkit.mjs — a small declarative layer on top of rbx-dom (rojo-rbx/rbx-dom N-API bindings)
 * for building real Roblox place files with properties, GUI trees, scripts and models.
 */
import { createDom, readBinary, readXml, types, reflection } from 'rbx-dom';
import fs from 'node:fs';
import path from 'node:path';

/* ------------------------------------------------------------------ */
/* value markers                                                       */
/* ------------------------------------------------------------------ */

/** Wrap a raw value with an explicit Roblox datatype tag. */
export const mk = (type, value) => ({ __t: type, v: value });

export const v3 = (x, y, z) => mk('Vector3', [x, y, z]);
export const v2 = (x, y) => mk('Vector2', [x, y]);
export const c3 = (r, g, b) => mk('Color3', [r, g, b]);
/** 0-255 colour helper */
export const rgb = (r, g, b) => mk('Color3', [r / 255, g / 255, b / 255]);
export const hex = (h) => {
  const s = h.replace('#', '');
  return mk('Color3', [
    parseInt(s.slice(0, 2), 16) / 255,
    parseInt(s.slice(2, 4), 16) / 255,
    parseInt(s.slice(4, 6), 16) / 255,
  ]);
};
export const udim = (scale, offset = 0) => mk('UDim', [scale, offset]);
export const udim2 = (x, y) => mk('UDim2', [
  Array.isArray(x) ? x : [x, 0],
  Array.isArray(y) ? y : [y, 0],
]);
export const nr = (a, b = a) => mk('NumberRange', [a, b]);
export const ns = (kps) => mk('NumberSequence', { keypoints: kps });
export const cs = (kps) => mk('ColorSequence', {
  keypoints: kps.map((k) => ({ time: k[0], color: k[1] && k[1].v ? k[1].v : k[1] })),
});
export const content = (uri) => mk('Content', { Uri: uri });
export const font = (family, weight = 'Regular', style = 'Normal') => mk('Font', { family, weight, style, cachedFaceId: null });
export const enumOf = (name, item) => mk('EnumRef', { name, item });
export const rect = (minX, minY, maxX, maxY) => mk('Rect', [minX, minY, maxX, maxY]);

/**
 * CFrame helper. Rotation accepts degrees about axes:
 *   cf([x,y,z], {y: 45})  /  cf([x,y,z], {yaw:45,pitch:0,roll:0})
 * or a look-at target:  cfLookAt(position, target)
 */
export function cf(position, rot = {}) {
  const yaw = (rot.yaw ?? rot.y ?? 0) * Math.PI / 180;
  const pitch = (rot.pitch ?? rot.x ?? 0) * Math.PI / 180;
  const roll = (rot.roll ?? rot.z ?? 0) * Math.PI / 180;
  const cy = Math.cos(yaw), sy = Math.sin(yaw);
  const cp = Math.cos(pitch), sp = Math.sin(pitch);
  const cr = Math.cos(roll), sr = Math.sin(roll);
  // Yaw (Y) * Pitch (X) * Roll (Z)
  const ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]];
  const rx = [[1, 0, 0], [0, cp, -sp], [0, sp, cp]];
  const rz = [[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]];
  const mul = (a, b) => a.map((row, i) => [0, 1, 2].map((j) =>
    row[0] * b[0][j] + row[1] * b[1][j] + row[2] * b[2][j]));
  const m = mul(mul(ry, rx), rz);
  return mk('CFrame', { position, orientation: m });
}

export function cfLookAt(position, target, up = [0, 1, 0]) {
  const dx = target[0] - position[0], dy = target[1] - position[1], dz = target[2] - position[2];
  const len = Math.hypot(dx, dy, dz) || 1;
  const f = [dx / len, dy / len, dz / len]; // look vector (-Z of the CFrame)
  const zAxis = [-f[0], -f[1], -f[2]];
  // right = up x (-look)
  let rx = up[1] * zAxis[2] - up[2] * zAxis[1];
  let ry = up[2] * zAxis[0] - up[0] * zAxis[2];
  let rz = up[0] * zAxis[1] - up[1] * zAxis[0];
  const rl = Math.hypot(rx, ry, rz) || 1;
  rx /= rl; ry /= rl; rz /= rl;
  const uy = [zAxis[1] * rz - zAxis[2] * ry, zAxis[2] * rx - zAxis[0] * rz, zAxis[0] * ry - zAxis[1] * rx];
  return mk('CFrame', { position, orientation: [[rx, uy[0], zAxis[0]], [ry, uy[1], zAxis[1]], [rz, uy[2], zAxis[2]]] });
}

/* ------------------------------------------------------------------ */
/* reflection helpers                                                  */
/* ------------------------------------------------------------------ */

const propCache = new Map();
const enumCache = new Map();

export function enumItems(name) {
  if (!enumCache.has(name)) enumCache.set(name, reflection.enumItems(name) || {});
  return enumCache.get(name);
}
export function enumItem(name, item) {
  const items = enumItems(name);
  if (typeof item === 'number') return item;
  const value = items[item];
  if (value === undefined) throw new Error(`Unknown enum item ${name}.${item}`);
  return value;
}

export function propInfo(className, propName) {
  const key = className + '.' + propName;
  if (propCache.has(key)) return propCache.get(key);
  let info = null;
  const seen = new Set();
  const queue = [className];
  while (queue.length && !info) {
    const cls = queue.shift();
    if (!cls || seen.has(cls)) continue;
    seen.add(cls);
    const direct = reflection.property(cls, propName);
    if (direct) { info = direct; break; }
    const supers = reflection.superclasses(cls) || [];
    for (const s of supers) queue.push(s);
  }
  propCache.set(key, info);
  return info;
}

function convertValue(className, propName, value) {
  const info = propInfo(className, propName);
  if (!info) throw new Error(`Unknown property ${className}.${propName}`);
  const dt = info.DataType;
  if (dt.Enum) {
    let v = value;
    if (v && v.__t === 'EnumRef') v = v.v.item;
    return types.taggedVariant('Enum', enumItem(dt.Enum, v));
  }
  const kind = dt.Value;
  const raw = value && value.__t ? value.v : value;
  switch (kind) {
    case 'Bool': return !!raw;
    case 'String': return String(raw);
    case 'Float32': return types.taggedVariant('Float32', Number(raw));
    case 'Float64': return types.taggedVariant('Float64', Number(raw));
    case 'Int32': return types.taggedVariant('Int32', Math.round(Number(raw)));
    case 'Int64': return types.taggedVariant('Int64', String(Math.round(Number(raw))));
    case 'Vector2': return types.taggedVariant('Vector2', raw);
    case 'Vector3': return types.taggedVariant('Vector3', raw);
    case 'Color3': return types.taggedVariant('Color3', raw);
    case 'Color3uint8': {
      const c = value && value.__t === 'Color3uint8' ? raw : raw.map((x) => Math.round(x * 255));
      return types.taggedVariant('Color3uint8', c);
    }
    case 'UDim': return types.taggedVariant('UDim', raw);
    case 'UDim2': return types.taggedVariant('UDim2', raw);
    case 'NumberRange': return types.taggedVariant('NumberRange', raw);
    case 'NumberSequence': {
      const kps = (raw.keypoints || []).map((k) =>
        types.numberSequenceKeypoint(k.time ?? k[0], k.value ?? k[1], 0));
      return types.taggedVariant('NumberSequence', types.numberSequence(kps));
    }
    case 'ColorSequence': {
      const kps = (raw.keypoints || []).map((k) =>
        types.colorSequenceKeypoint(k.time ?? k[0], (k.color && k.color.v) ? k.color.v : k.color));
      return types.taggedVariant('ColorSequence', types.colorSequence(kps));
    }
    case 'CFrame': return types.taggedVariant('CFrame', raw);
    case 'Rect': return types.taggedVariant('Rect', raw);
    case 'Content':
    case 'ContentId': {
      const uri = value && value.__t ? value.v.Uri : String(value);
      return types.taggedVariant('Content', types.contentUri(uri));
    }
    case 'Font': return types.taggedVariant('Font', raw);
    case 'BrickColor': return types.taggedVariant('BrickColor', typeof raw === 'string' ? types.brickColorByName(raw) : types.brickColorByNumber(raw));
    case 'Tags': return types.taggedVariant('Tags', types.tagsEncode(raw || []));
    case 'BinaryString': return types.taggedVariant('BinaryString', raw);
    case 'SharedString': return types.taggedVariant('SharedString', raw);
    default:
      throw new Error(`Unsupported datatype ${kind} for ${className}.${propName}`);
  }
}

/* ------------------------------------------------------------------ */
/* instance specs                                                      */
/* ------------------------------------------------------------------ */

/**
 * el('Part', { Name: 'Wall', Size: v3(10,10,1), CFrame: cf([0,5,0]), Color: rgb(20,20,20) }, [children])
 * Properties are auto-typed from the reflection database.
 */
export function el(className, props = {}, children = []) {
  const out = {};
  let name;
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined) continue;
    if (key === 'Name') { name = String(value); continue; }
    out[key] = convertValue(className, key, value);
  }
  const spec = { className, properties: out, children: children.filter(Boolean) };
  if (name) spec.name = name;
  return spec;
}

/** Insert children into an existing spec (mutable, handy for generators). */
export function addChild(spec, child) {
  if (child) spec.children.push(child);
  return spec;
}

/* ------------------------------------------------------------------ */
/* geometry kit                                                        */
/* ------------------------------------------------------------------ */

export const MATERIALS = {
  Plastic: 'Plastic', SmoothPlastic: 'SmoothPlastic', Neon: 'Neon', Wood: 'Wood',
  WoodPlanks: 'WoodPlanks', Marble: 'Marble', Slate: 'Slate', Concrete: 'Concrete',
  Granite: 'Granite', Brick: 'Brick', Pebble: 'Pebble', Cobblestone: 'Cobblestone',
  CorrodedMetal: 'CorrodedMetal', DiamondPlate: 'DiamondPlate', Foil: 'Foil',
  Metal: 'Metal', Grass: 'Grass', Sand: 'Sand', Fabric: 'Fabric', Ice: 'Ice',
  Glass: 'Glass', ForceField: 'ForceField', Carpet: 'Carpet', CeramicTiles: 'CeramicTiles',
  ClayRoofTiles: 'ClayRoofTiles', Basalt: 'Basalt', CrackedLava: 'CrackedLava',
  Asphalt: 'Asphalt', LeafyGrass: 'LeafyGrass', Mud: 'Mud', Rock: 'Rock',
  Salt: 'Salt', Snow: 'Snow', Limestone: 'Limestone', Pavement: 'Pavement',
  Plaster: 'Plaster', Rubber: 'Rubber', Cardboard: 'Cardboard',
  MetalMesh: 'MetalMesh', RoofShingles: 'RoofShingles', Water: 'Water',
};

/** A block part. pos = centre, size = [x,y,z]. */
export function part(props = {}) {
  const { pos = [0, 0, 0], size = [4, 1, 2], color = [0.5, 0.5, 0.5], material = 'Plastic', rot = {}, cframe, ...rest } = props;
  const base = {
    Anchored: true,
    Size: v3(size[0], size[1], size[2]),
    CFrame: cframe || cf(pos, rot),
    Color: Array.isArray(color) ? c3(color[0], color[1], color[2]) : color,
    Material: material,
    TopSurface: 'Smooth',
    BottomSurface: 'Smooth',
    LeftSurface: 'Smooth',
    RightSurface: 'Smooth',
    FrontSurface: 'Smooth',
    BackSurface: 'Smooth',
  };
  return el('Part', { ...base, ...rest });
}

export const wedge = (props = {}) => {
  const { pos = [0, 0, 0], size = [4, 1, 2], color = [0.5, 0.5, 0.5], material = 'Plastic', rot = {}, ...rest } = props;
  return el('WedgePart', {
    Anchored: true, Size: v3(...size), CFrame: cf(pos, rot),
    Color: c3(...color), Material: material, ...rest,
  });
};

export const ball = (props = {}) => {
  const { pos = [0, 0, 0], size = 2, color = [0.5, 0.5, 0.5], material = 'Plastic', ...rest } = props;
  const s = Array.isArray(size) ? size : [size, size, size];
  return el('Part', {
    Anchored: true, Shape: 'Ball', Size: v3(...s), CFrame: cf(pos),
    Color: c3(...color), Material: material, ...rest,
  });
};

export const cylinder = (props = {}) => {
  const { pos = [0, 0, 0], size = [1, 4, 1], color = [0.5, 0.5, 0.5], material = 'Plastic', rot = {}, ...rest } = props;
  const s = Array.isArray(size) ? size : [size, size, size];
  return el('Part', {
    Anchored: true, Shape: 'Cylinder', Size: v3(s[0], s[1] === undefined ? s[0] : s[1], s[2] === undefined ? s[0] : s[2]),
    CFrame: cf(pos, rot), Color: c3(...color), Material: material, ...rest,
  });
};

export const light = (props = {}) => el('PointLight', {
  Brightness: 2, Range: 16, Shadows: true,
  Color: c3(1, 0.85, 0.65),
  ...props,
});

export const spot = (props = {}) => el('SpotLight', {
  Brightness: 2, Range: 28, Angle: 70, Shadows: true,
  Color: c3(1, 0.9, 0.75),
  ...props,
});

export const surfaceGui = (props = {}, children = []) => el('SurfaceGui', {
  Face: 'Front', AlwaysOnTop: false, LightInfluence: 0.4,
  PixelsPerStud: 50, ResetOnSpawn: false,
  ...props,
}, children);

export const billboard = (props = {}, children = []) => el('BillboardGui', {
  Size: udim2(udim(0, 200), udim(0, 60)), StudsOffset: v3(0, 3, 0),
  AlwaysOnTop: true, ResetOnSpawn: false, MaxDistance: 80,
  ...props,
}, children);

export const decal = (props = {}) => el('Decal', { Face: 'Front', ...props });

export const sparkles = (props = {}) => el('ParticleEmitter', {
  Texture: 'rbxassetid://241876428', Rate: 12, Lifetime: nr(1, 2), Speed: nr(0.5, 1.5),
  Size: ns([[0, 0.2], [1, 0]]), Transparency: ns([[0, 0.4], [1, 1]]),
  LightEmission: 1, LightInfluence: 0,
  Color: cs([[0, c3(1, 0.9, 0.6)], [1, c3(0.6, 0.4, 1)]]),
  ...props,
});

/* ------------------------------------------------------------------ */
/* GUI kit                                                             */
/* ------------------------------------------------------------------ */

export const UI_FONT = 'rbxasset://fonts/families/GothamSSm.json';
export const UI_FONT_BOLD = 'rbxasset://fonts/families/GothamSSm.json';

export const ui = {
  frame: (props = {}, children = []) => el('Frame', {
    BackgroundColor3: rgb(10, 12, 24), BorderSizePixel: 0, ...props,
  }, children),
  label: (text, props = {}, children = []) => el('TextLabel', {
    BackgroundTransparency: 1, BorderSizePixel: 0,
    Text: text, TextColor3: rgb(235, 238, 255), TextScaled: false, TextSize: 18,
    FontFace: font(UI_FONT), TextXAlignment: 'Center', TextYAlignment: 'Center',
    RichText: true, ...props,
  }, children),
  button: (text, props = {}, children = []) => el('TextButton', {
    BackgroundColor3: rgb(40, 30, 80), BorderSizePixel: 0,
    Text: text, TextColor3: rgb(240, 240, 255), TextSize: 20,
    FontFace: font(UI_FONT), AutoButtonColor: false, RichText: true,
    TextXAlignment: 'Center', TextYAlignment: 'Center', ...props,
  }, children),
  image: (image, props = {}, children = []) => el('ImageLabel', {
    BackgroundTransparency: 1, BorderSizePixel: 0, Image: image,
    ScaleType: 'Fit', ...props,
  }, children),
  corner: (radius = 12) => el('UICorner', { CornerRadius: udim(0, radius) }),
  stroke: (props = {}) => el('UIStroke', {
    Thickness: 1.5, Color: rgb(150, 120, 255), Transparency: 0.2,
    ApplyStrokeMode: 'Contextual', ...props,
  }),
  gradient: (stops, props = {}) => el('UIGradient', {
    Color: cs(stops.map((s, i) => [i / Math.max(1, stops.length - 1), s])),
    Rotation: 90, ...props,
  }),
  list: (props = {}) => el('UIListLayout', {
    Padding: udim(0, 8), FillDirection: 'Vertical', SortOrder: 'LayoutOrder',
    HorizontalAlignment: 'Center', VerticalAlignment: 'Top', ...props,
  }),
  grid: (props = {}) => el('UIGridLayout', {
    CellSize: udim2(0, 120, 0, 120), CellPadding: udim2(0, 10, 0, 10),
    SortOrder: 'LayoutOrder', ...props,
  }),
  pad: (props = {}) => el('UIPadding', {
    PaddingTop: udim(0, 10), PaddingBottom: udim(0, 10),
    PaddingLeft: udim(0, 10), PaddingRight: udim(0, 10), ...props,
  }),
  aspect: (ratio = 1) => el('UIAspectRatioConstraint', { AspectRatio: ratio, DominantAxis: 'Width' }),
  scroll: (props = {}, children = []) => el('ScrollingFrame', {
    BackgroundTransparency: 1, BorderSizePixel: 0, ScrollBarThickness: 5,
    ScrollBarImageColor3: rgb(150, 120, 255), CanvasSize: udim2(0, 0, 0, 0),
    AutomaticCanvasSize: 'Y', ScrollingDirection: 'Y', ...props,
  }, children),
  padding: (px) => ui.pad({ PaddingTop: udim(0, px), PaddingBottom: udim(0, px), PaddingLeft: udim(0, px), PaddingRight: udim(0, px) }),
};

export const screenGui = (name, props = {}, children = []) => el('ScreenGui', {
  Name: name, ResetOnSpawn: false, IgnoreGuiInset: true, ZIndexBehavior: 'Sibling',
  DisplayOrder: 1, Enabled: true, ...props,
}, children);

/* ------------------------------------------------------------------ */
/* scripts                                                             */
/* ------------------------------------------------------------------ */

export function readScript(relPath) {
  const abs = path.resolve(relPath);
  return fs.readFileSync(abs, 'utf8');
}

/** script('Script', 'ServerScriptService/Main.server.luau') — loads source from src/ */
export function scriptFile(className, name, relPath, props = {}) {
  const source = readScript(path.join(ROOT, 'src', relPath));
  return el(className, { Name: name, Source: source, ...props });
}

export const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');

export function moduleScript(name, relPath, props = {}) {
  return scriptFile('ModuleScript', name, relPath, props);
}
export function serverScript(name, relPath, props = {}) {
  return scriptFile('Script', name, relPath, props);
}
export function clientScript(name, relPath, props = {}) {
  return scriptFile('LocalScript', name, relPath, props);
}

/* ------------------------------------------------------------------ */
/* place assembly                                                      */
/* ------------------------------------------------------------------ */

export function buildPlace(tree, outBase) {
  const dom = createDom(tree);
  const binary = dom.toBinary({ compression: 'lz4' });
  const xml = dom.toXml();
  fs.mkdirSync(path.dirname(outBase), { recursive: true });
  fs.writeFileSync(outBase + '.rbxl', binary);
  fs.writeFileSync(outBase + '.rbxlx', xml);
  return { binary, xml, instances: dom.instanceCount };
}

export { types, reflection, readBinary, readXml };
