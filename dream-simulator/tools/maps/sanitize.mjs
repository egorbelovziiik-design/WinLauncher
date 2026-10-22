/**
 * sanitize.mjs — автоматическая расстановка маркеров на реальный пол карты.
 *
 * Проблема: маркеры карт генерируются вручную и иногда попадают внутрь стен,
 * деревьев или в пустоту — из-за этого монстры/NPC/квестовые объекты появлялись
 * в геометрии и «багались».
 *
 * sanitizeMap(model) проходит по всем MK_* маркерам и ставит каждый на
 * ближайшую свободную поверхность (пол, крыша, платформа).
 */

const raw = (value, tag) => {
  if (value == null) return undefined;
  if (Array.isArray(value)) return value;
  if (typeof value === 'object') {
    if (tag && value[tag] !== undefined) return value[tag];
    if (value.v !== undefined) return value.v;
    if (value.CFrame !== undefined) return value.CFrame;
  }
  return value;
};

const CELL = 20;

export function collectSolidParts(node, out = []) {
  if (node && node.className === 'Part' && node.properties && node.properties.CFrame) {
    const cf = raw(node.properties.CFrame, 'CFrame');
    const size = raw(node.properties.Size, 'Vector3') || [1, 1, 1];
    const transparency = typeof node.properties.Transparency === 'number' ? node.properties.Transparency : 0;
    const collides = node.properties.CanCollide !== false;
    if (cf && cf.position && collides && transparency < 0.9) {
      const [x, y, z] = cf.position;
      const [sx, sy, sz] = size;
      out.push({
        node,
        x, y, z, sx, sy, sz,
        minX: x - sx / 2, maxX: x + sx / 2,
        minY: y - sy / 2, maxY: y + sy / 2,
        minZ: z - sz / 2, maxZ: z + sz / 2,
        area: sx * sz,
      });
    }
  }
  for (const child of node.children || []) collectSolidParts(child, out);
  return out;
}

const cellKey = (x, z) => `${Math.floor(x / CELL)}:${Math.floor(z / CELL)}`;

function indexByCell(parts) {
  const grid = new Map();
  for (const part of parts) {
    const x0 = Math.floor(part.minX / CELL), x1 = Math.floor(part.maxX / CELL);
    const z0 = Math.floor(part.minZ / CELL), z1 = Math.floor(part.maxZ / CELL);
    for (let x = x0; x <= x1; x++) {
      for (let z = z0; z <= z1; z++) {
        const key = `${x}:${z}`;
        if (!grid.has(key)) grid.set(key, []);
        grid.get(key).push(part);
      }
    }
  }
  return grid;
}

const near = (grid, x, z) => {
  const parts = [];
  const cx = Math.floor(x / CELL), cz = Math.floor(z / CELL);
  for (let dx = -1; dx <= 1; dx++) {
    for (let dz = -1; dz <= 1; dz++) {
      const bucket = grid.get(`${cx + dx}:${cz + dz}`);
      if (bucket) parts.push(...bucket);
    }
  }
  return parts;
};

/** Пол под точкой (x,z): самый высокий верх в пределах от yMin до yMax. */
function groundAt(grid, x, z, yMin = -80, yMax = 80, inset = 0.6) {
  let best = null;
  for (const part of near(grid, x, z)) {
    if (part.maxY < yMin || part.maxY > yMax) continue;
    if (x < part.minX + inset || x > part.maxX - inset) continue;
    if (z < part.minZ + inset || z > part.maxZ - inset) continue;
    if (part.sy > 8 && part.sy < part.sx * 0.9) continue; // столбы/колонны — не пол
    if (!best || part.maxY > best) best = part.maxY;
  }
  return best;
}

/** Свободен ли объём над точкой. */
function isClear(grid, x, y, z, radius = 1.6, height = 5.4) {
  for (const part of near(grid, x, z)) {
    const overlapsX = x + radius > part.minX + 0.15 && x - radius < part.maxX - 0.15;
    const overlapsZ = z + radius > part.minZ + 0.15 && z - radius < part.maxZ - 0.15;
    const overlapsY = y + height > part.minY + 0.15 && y + 0.4 < part.maxY - 0.15;
    if (overlapsX && overlapsZ && overlapsY) return false;
  }
  return true;
}

const setPosition = (markerNode, x, y, z) => {
  const cframe = raw(markerNode.properties.CFrame, 'CFrame');
  markerNode.properties.CFrame = {
    CFrame: { orientation: cframe.orientation, position: [x, y, z] },
  };
};

const markerPosition = (markerNode) => {
  const cframe = raw(markerNode.properties.CFrame, 'CFrame');
  return cframe.position;
};

/** Ищет место для маркера рядом с исходным. Возвращает [x,y,z] или nil. */
function findSpot(grid, x0, y0, z0, radius = 2, height = 5.5, reach = 48) {
  const trySpot = (x, z) => {
    const floorTop = groundAt(grid, x, z, -90, y0 + 24);
    if (floorTop == null) return null;
    if (!isClear(grid, x, floorTop, z, radius, height)) return null;
    return [x, floorTop + 0.2, z];
  };

  const direct = trySpot(x0, z0);
  if (direct) return direct;

  for (let step = 1; step <= 90; step++) {
    const angle = step * 2.399963;
    const distance = 2 + (reach * step) / 90;
    const x = x0 + Math.cos(angle) * distance;
    const z = z0 + Math.sin(angle) * distance;
    const spot = trySpot(x, z);
    if (spot) return spot;
  }
  return null;
}

/**
 * Расставляет все маркеры карты на свободные поверхности.
 * Возвращает { moved, dropped, markers }.
 */
export function sanitizeMap(model, options = {}) {
  const solids = collectSolidParts(model);
  const grid = indexByCell(solids);
  const stats = { moved: 0, kept: 0, dropped: 0, markers: 0 };

  const markers = [];
  const collect = (node) => {
    if (node && node.className === 'Part' && typeof node.name === 'string' && node.name.startsWith('MK_')) {
      markers.push(node);
    }
    for (const child of node.children || []) collect(child);
  };
  collect(model);
  stats.markers = markers.length;

  for (const marker of markers) {
    const [x0, y0, z0] = markerPosition(marker);
    const isPortal = marker.name === 'MK_Portal';
    const isSpawn = marker.name === 'MK_Spawn';
    const radius = isPortal ? 4.5 : (isSpawn ? 3 : 1.8);
    const height = isPortal ? 7 : (isSpawn ? 7 : 5.5);
    const spot = findSpot(grid, x0, y0, z0, radius, height, options.reach || 48);
    if (!spot) {
      stats.dropped += 1;
      continue;
    }
    if (Math.abs(spot[0] - x0) < 0.1 && Math.abs(spot[2] - z0) < 0.1) {
      stats.kept += 1;
    } else {
      stats.moved += 1;
    }
    setPosition(marker, spot[0], spot[1], spot[2]);
  }
  return stats;
}

export const __internals = { groundAt, isClear, indexByCell };
