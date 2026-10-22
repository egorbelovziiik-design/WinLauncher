/**
 * checkmaps.mjs — геометрическая проверка карт снов (без Roblox Studio).
 * Для каждого маркера проверяет: есть ли под ним пол, не стоит ли он внутри
 * сплошной геометрии, и не оказался ли он в «пустоте» без опоры.
 *
 * node tools/checkmaps.mjs            — краткий отчёт по всем картам
 * node tools/checkmaps.mjs forest     — подробно по одной карте
 */
import { MAP_BUILDERS } from './maps/maps.mjs';
import { sanitizeMap } from './maps/sanitize.mjs';

const only = process.argv[2];

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

const collectParts = (node, out = []) => {
  if (node && node.className === 'Part' && node.properties && node.properties.CFrame) {
    const cf = raw(node.properties.CFrame, 'CFrame');
    const size = raw(node.properties.Size, 'Vector3') || [1, 1, 1];
    out.push({
      name: node.name || '',
      pos: cf.position,
      size,
      collides: node.properties.CanCollide !== false,
      transparency: typeof node.properties.Transparency === 'number' ? node.properties.Transparency : 0,
      material: typeof node.properties.Material === 'string' ? node.properties.Material : '',
    });
  }
  for (const child of node.children || []) collectParts(child, out);
  return out;
};

// Грубая AABB-проверка (без учёта поворотов — достаточно для поиска грубых ошибок)
const aabb = (p) => ({
  minX: p.pos[0] - p.size[0] / 2, maxX: p.pos[0] + p.size[0] / 2,
  minY: p.pos[1] - p.size[1] / 2, maxY: p.pos[1] + p.size[1] / 2,
  minZ: p.pos[2] - p.size[2] / 2, maxZ: p.pos[2] + p.size[2] / 2,
});

const overlaps = (a, b, shrink = 0) =>
  a.minX < b.maxX - shrink && a.maxX > b.minX + shrink &&
  a.minY < b.maxY - shrink && a.maxY > b.minY + shrink &&
  a.minZ < b.maxZ - shrink && a.maxZ > b.minZ + shrink;

let failures = 0;

for (const [id, builder] of Object.entries(MAP_BUILDERS)) {
  if (only && only !== id) continue;
  const model = builder();
  if (!process.env.NO_SANITIZE) sanitizeMap(model);
  const parts = collectParts(model);
  const solids = parts.filter((p) => p.collides && p.transparency < 0.9);
  const floors = solids.filter((p) => p.size[1] <= 6);
  const markers = parts.filter((p) => p.name.startsWith('MK_'));

  const issues = [];
  for (const marker of markers) {
    const m = aabb({
      pos: marker.pos,
      size: marker.size,
    });
    // пол под маркером: верх пола в пределах [-14, +2] по Y и маркер внутри XZ пола
    const ground = floors.find((floor) => {
      const f = aabb(floor);
      return f.maxY <= m.minY + 2 &&
        f.maxY >= m.minY - 14 &&
        f.minX <= m.minX + 1 && f.maxX >= m.maxX - 1 &&
        f.minZ <= m.minZ + 1 && f.maxZ >= m.maxZ - 1;
    });
    if (!ground) issues.push(`${marker.name} нет пола под маркером @ ${marker.pos.map((v) => v.toFixed(1)).join(',')}`);

    // маркер внутри сплошного объекта (стена, мебель)
    const blocker = solids.find((solid) => {
      const s = aabb(solid);
      const markerBox = {
        minX: marker.pos[0] - 1.2, maxX: marker.pos[0] + 1.2,
        minY: marker.pos[1] + 0.4, maxY: marker.pos[1] + 5.2,
        minZ: marker.pos[2] - 1.2, maxZ: marker.pos[2] + 1.2,
      };
      return overlaps(markerBox, s, 0.4);
    });
    if (blocker) issues.push(`${marker.name} внутри "${blocker.name || blocker.material}" @ ${marker.pos.map((v) => v.toFixed(1)).join(',')}`);
  }

  const label = `${id.padEnd(9)} деталей=${String(parts.length).padStart(5)} маркеров=${String(markers.length).padStart(3)}`;
  if (issues.length === 0) {
    console.log(`✓ ${label} — проблем нет`);
  } else {
    failures += issues.length;
    console.log(`✗ ${label} — ${issues.length} проблем`);
    for (const issue of issues.slice(0, only ? 99 : 6)) console.log('    ' + issue);
  }
}

console.log(failures === 0 ? '\nВсе маркеры стоят на полу.' : `\nВсего проблем: ${failures}`);
process.exitCode = failures === 0 ? 0 : 1;
