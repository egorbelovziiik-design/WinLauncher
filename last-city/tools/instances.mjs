import assert from 'node:assert/strict';
import * as R from 'rbxm-parser';

// Generated serializer classes expose serialized property names, NOT every
// Roblox scripting alias. A plain assignment to Part.Color / Lighting.ClockTime
// / TextLabel.Font would silently create a JS field that never reaches the file.
export function instance(className) {
  assert.equal(typeof R[className], 'function', `Unknown Roblox class: ${className}`);
  return Object.preventExtensions(new R[className]());
}

// rbxm-parser 1.1.4 truncates Terrain.PhysicsGrid's default at the first NUL:
// 02 03 instead of the Studio-saved 02 03 + 12 zero bytes. The general binary
// parsers treat this field as opaque, but Studio's internal terrain decoder
// attempts to read at offset 2 and fails. This world uses Parts, not voxel data.
// Reference: pinned Studio fixture in tests/fixtures/empty-terrain.json.
export const EMPTY_PHYSICS_GRID = Buffer.from('AgMAAAAAAAAAAAAAAAA=', 'base64').toString('latin1');
export const EMPTY_SMOOTH_GRID = Buffer.from('AQU=', 'base64').toString('latin1');
export function emptyTerrain() {
  const terrain = instance('Terrain');
  terrain.PhysicsGrid = EMPTY_PHYSICS_GRID;
  terrain.SmoothGrid = EMPTY_SMOOTH_GRID;
  return terrain;
}
export function validateEmptyTerrain(terrain) {
  assert.equal(terrain.ClassName, 'Terrain');
  assert.equal(terrain.PhysicsGrid, EMPTY_PHYSICS_GRID,
    'Terrain.PhysicsGrid is not a complete empty grid (expected 14 bytes, including twelve trailing NULs)');
  assert.equal(terrain.SmoothGrid, EMPTY_SMOOTH_GRID,
    'Unexpected voxel grid: this builder currently supports only the Studio empty-terrain fixture');
}

export function validateAuthoredInstances(file) {
  function visit(node) {
    // No accidental, unserialized scripting aliases or typos may escape.
    assert.deepEqual(Object.keys(node).filter(key => !key.startsWith('_')), [],
      `${node.ClassName}:${node.Name} has a JS property that will not serialize`);
    if (node.ClassName === 'Terrain') validateEmptyTerrain(node);
    node.Children.forEach(visit);
  }
  file.Roots.forEach(visit);
}
export function boldFont() {
  return new R.RBXMFont('rbxasset://fonts/families/SourceSansPro.json', R.FontWeight.Bold, R.FontStyle.Normal);
}
