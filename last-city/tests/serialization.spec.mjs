import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { R, part, color } from '../tools/world/primitives.mjs';
import { instance, emptyTerrain, validateEmptyTerrain, validateAuthoredInstances, boldFont } from '../tools/instances.mjs';
import { encodeAuthoredText } from '../tools/encoding.mjs';
const fixture=JSON.parse(fs.readFileSync(new URL('./fixtures/empty-terrain.json',import.meta.url),'utf8'));
const binary=base64=>Buffer.from(base64,'base64').toString('latin1');
function roundtrip(root) {
  const file=new R.RobloxFile(); file.AddRoot(root);
  return R.RobloxFile.ReadFromBuffer(file.WriteToBuffer());
}

test('Studio fixture retains the twelve trailing NUL bytes in PhysicsGrid',()=>{
  const data=Buffer.from(fixture.PhysicsGrid,'base64');
  assert.equal(data.length,14); assert.equal(data.subarray(0,2).toString('hex'),'0203');
  assert.ok(data.subarray(2).every(v=>v===0));
  assert.equal(Buffer.from(fixture.SmoothGrid,'base64').length,2);
});
test('reject the exact truncated rbxm-parser Terrain default',()=>{
  const bad=new R.Terrain();
  assert.equal(bad.PhysicsGrid,binary(fixture.brokenPhysicsGrid));
  assert.throws(()=>validateEmptyTerrain(bad),/PhysicsGrid.*14 bytes/);
});
test('generic binary roundtrip is NOT enough to validate opaque terrain',()=>{
  const root=instance('Workspace'); const bad=new R.Terrain(); bad.Parent=root;
  const decoded=roundtrip(root); assert.ok(decoded);
  assert.throws(()=>validateEmptyTerrain(decoded.Roots[0].Children[0]),/PhysicsGrid/);
});
test('fixed terrain matches the Studio fixture before and after serialization',()=>{
  const root=instance('Workspace'); const terrain=emptyTerrain(); terrain.Parent=root;
  assert.equal(terrain.PhysicsGrid,binary(fixture.PhysicsGrid));
  assert.equal(terrain.SmoothGrid,binary(fixture.SmoothGrid));
  validateEmptyTerrain(terrain);
  const parsed=roundtrip(root); validateEmptyTerrain(parsed.Roots[0].Children[0]);
});
test('UTF-8 text conversion never changes binary terrain payloads',()=>{
  const file=new R.RobloxFile(); const root=instance('Workspace'); file.AddRoot(root);
  const terrain=emptyTerrain(); terrain.Parent=root; encodeAuthoredText(file);
  assert.equal(terrain.PhysicsGrid,binary(fixture.PhysicsGrid));
  validateAuthoredInstances(file);
});
test('unsupported scripting aliases cannot silently create unserialized JS fields',()=>{
  assert.throws(()=>{instance('Part').Color=color('#f97316');},TypeError);
  assert.throws(()=>{instance('Lighting').ClockTime=15.5;},TypeError);
  assert.throws(()=>{instance('TextLabel').Font=R.Font.GothamBold;},TypeError);
});
test('static part color is actually serialized as Color3uint8',()=>{
  const root=instance('Workspace'); part(root,'Orange',[1,1,1],[0,0,0],'#f97316');
  const p=roundtrip(root).Roots[0].Children[0];
  assert.equal(p.Color3uint8.R,249/255); assert.equal(p.Color3uint8.G,115/255); assert.equal(p.Color3uint8.B,22/255);
});
test('static signage FontFace survives binary encoding',()=>{
  const label=instance('TextLabel'); label.FontFace=boldFont();
  const parsed=roundtrip(label).Roots[0];
  assert.equal(parsed.FontFace.Family,'rbxasset://fonts/families/SourceSansPro.json');
  assert.equal(parsed.FontFace.Weight,R.FontWeight.Bold);
});
test('initial lighting time is serialized rather than only assigned in JS',()=>{
  const lighting=instance('Lighting'); lighting.TimeOfDay='15:30:00';
  assert.equal(roundtrip(lighting).Roots[0].TimeOfDay,'15:30:00');
});
test('pre-write validation rejects expando properties even on unguarded instances',()=>{
  const file=new R.RobloxFile(); const p=new R.Part(); p.Color=color('#f97316'); file.AddRoot(p);
  assert.throws(()=>validateAuthoredInstances(file),/will not serialize/);
});
