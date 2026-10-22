import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { RobloxFile } from 'rbxm-parser';
import reader from 'rbx-reader';
import { decodeBinaryText } from './encoding.mjs';
import { EMPTY_PHYSICS_GRID, EMPTY_SMOOTH_GRID } from './instances.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const artifact=process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(root,'../release-artifacts/last-city/LAST_CITY_3D.rbxl');
const bytes=fs.readFileSync(artifact);
let assertions=0;
function check(condition,message) { assert.ok(condition,message); assertions++; console.log(`PASSED | FILE ${message}`); }
check(bytes.subarray(0,8).toString('ascii')==='<roblox!','true binary Roblox header (not renamed XML)');
check(bytes.length>50000,'nonempty embedded sources and place geometry');
const own=RobloxFile.ReadFromBuffer(bytes);
check(!!own,'binary deserializes with rbxm-parser');
const independent=reader.readFile(artifact);
check(independent.instances.length>1500,'independent rbx-reader parses scene');
function at(...parts) {
  let list=independent.result; let node;
  for(const name of parts) { node=list.find(n=>n.Name===name); assert.ok(node,`Missing ${parts.join('/')}`); list=node.Children; }
  return node;
}
for(const name of ['Workspace','Lighting','ReplicatedStorage','ReplicatedFirst','ServerScriptService','StarterPlayer','StarterGui','SoundService']) {
  check(own.Roots.some(n=>n.Name===name&&n.IsService),`${name} has service marker`);
}
const ws=at('Workspace');
const terrain=at('Workspace','Terrain');
check(terrain.PhysicsGrid===EMPTY_PHYSICS_GRID,'Terrain.PhysicsGrid matches 14-byte Studio fixture including trailing NULs');
check(terrain.SmoothGrid===EMPTY_SMOOTH_GRID,'Terrain.SmoothGrid matches the 2-byte Studio empty voxel fixture');
check(at('Lighting').TimeOfDay==='15:30:00','authored initial lighting time is serialized');
check(ws.StreamingEnabled===true&&ws.StreamingMinRadius===128&&ws.StreamingTargetRadius===512,'streaming enabled with bounded radii');
check(at('Workspace','World','Plots').Children.length===6,'six physically distinct building plots');
check(at('Workspace','World','ZombieSpawns').Children.length===12,'twelve enemy spawn markers');
const loot=at('Workspace','World','LootSpawns').Children;
check(loot.filter(n=>n.Name.startsWith('Supply')).length===3,'three distinct quest supply crates');
check(loot.filter(n=>n.findFirstChild('ItemId')).length>=20,'real resource spawn definitions');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='02_MeridianMarket'),'enterable market included');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='03_SalvageDepot'),'industrial depot included');
const ground=at('Workspace','World','TerrainBase','Ground');
check(ground.Anchored===true&&ground.CanCollide===true,'collidable anchored world floor');
const ownNodes=[]; function collect(node){ownNodes.push(node);node.Children.forEach(collect);}own.Roots.forEach(collect);
const floor=ownNodes.find(n=>n.ClassName==='Part'&&n.Name==='Ground');
check(floor.Color3uint8.R===60/255&&floor.Color3uint8.G===69/255&&floor.Color3uint8.B===64/255,'authored ground color survives serialization');
check(new Set(ownNodes.filter(n=>n.ClassName==='Part').map(n=>JSON.stringify(n.Color3uint8))).size>12,'static world has authored color palette, not default grey');
check(ownNodes.filter(n=>n.ClassName==='TextLabel').every(n=>n.FontFace.Family==='rbxasset://fonts/families/SourceSansPro.json'),'signage uses actual serialized FontFace');
const spawn=at('Workspace','World','Cities','01_MeridianShelter','ShelterSpawn');
check(spawn.ClassName==='SpawnLocation'&&spawn.Neutral===true,'neutral playable spawn included');
let sourceCount=0;
function verifySources(dir,tree) {
  for(const entry of fs.readdirSync(dir,{withFileTypes:true})) {
    const source=path.join(dir,entry.name);
    if(entry.isDirectory()) { verifySources(source,[...tree,entry.name]); continue; }
    if(!entry.name.endsWith('.luau')) continue;
    const name=entry.name.replace(/\.(server|client)\.luau$|\.luau$/,'');
    const node=at(...tree,name);
    const type=entry.name.endsWith('.server.luau')?'Script':entry.name.endsWith('.client.luau')?'LocalScript':'ModuleScript';
    assert.equal(node.ClassName,type,source); assert.equal(decodeBinaryText(node.Source),fs.readFileSync(source,'utf8'),source);
    assert.notEqual(node.Disabled,true,source); sourceCount++;
  }
}
verifySources(path.join(root,'src'),[]);
check(independent.instances.some(n=>n.ClassName==='TextLabel'&&decodeBinaryText(n.Text??'').includes('СВОБОДНЫЙ УЧАСТОК')),'UTF-8 Russian signage survives binary encoding');
check(sourceCount===independent.instances.filter(n=>['Script','LocalScript','ModuleScript'].includes(n.ClassName)).length,`all ${sourceCount} embedded Luau sources exactly match repository`);
for(const name of ['SessionRemote','QuestRemote','InventoryRemote','CombatRemote','BuildRemote','ShopRemote','NPCRemote','MovementRemote','CraftingRemote','StateRemote','EffectsRemote']) {
  check(at('ReplicatedStorage','Remotes',name).ClassName==='RemoteEvent',`${name} is a real RemoteEvent`);
}
check(at('ServerScriptService','Services','SaveService').Source.includes(':UpdateAsync('),'UpdateAsync persistence logic lives on server');
check(at('ServerScriptService','Services','ZombieService').Source.includes('PathfindingService'),'pathfinding AI included on server');
check(at('StarterPlayer','StarterPlayerScripts','Client').ClassName==='LocalScript','actual client entry point included');
const roundtrip=own.WriteToBuffer();
const parsed=reader.parseBuffer(roundtrip.buffer.slice(roundtrip.byteOffset,roundtrip.byteOffset+roundtrip.byteLength));
check(parsed.instances.length===independent.instances.length,'binary roundtrip preserves instance count');
check(parsed.instances.filter(n=>['Script','LocalScript','ModuleScript'].includes(n.ClassName)).every(n=>independent.instances.some(o=>o.ClassName===n.ClassName&&o.Name===n.Name&&o.Source===n.Source)),'binary roundtrip preserves exact UTF-8 script bytes');
const classes={}; for(const n of independent.instances)classes[n.ClassName]=(classes[n.ClassName]??0)+1;
const result={assertions,instances:independent.instances.length,parts:(classes.Part??0)+(classes.SpawnLocation??0),scripts:sourceCount,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),classes};
fs.writeFileSync(path.join(path.dirname(artifact),'verification.json'),JSON.stringify(result,null,2)+'\n');
console.log(`\nFILE RESULTS: ${assertions} PASSED / 0 FAILED\n${JSON.stringify(result,null,2)}`);
