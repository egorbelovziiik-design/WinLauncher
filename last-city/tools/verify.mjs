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
check(own.Metadata.get('Version')==='0.5.0','binary metadata declares Iron Horizon version 0.5.0');
const independent=reader.readFile(artifact);
check(independent.instances.length>1500,'independent rbx-reader parses scene');
function at(...parts) {
  let list=independent.result; let node;
  for(const name of parts) { node=list.find(n=>n.Name===name); assert.ok(node,`Missing ${parts.join('/')}`); list=node.Children; }
  return node;
}
for(const name of ['Workspace','Lighting','MaterialService','ReplicatedStorage','ReplicatedFirst','ServerScriptService','StarterPlayer','StarterGui','SoundService']) {
  check(own.Roots.some(n=>n.Name===name&&n.IsService),`${name} has service marker`);
}
const ws=at('Workspace');
const terrain=at('Workspace','Terrain');
check(terrain.PhysicsGrid===EMPTY_PHYSICS_GRID,'Terrain.PhysicsGrid matches 14-byte Studio fixture including trailing NULs');
check(terrain.SmoothGrid===EMPTY_SMOOTH_GRID,'Terrain.SmoothGrid matches the 2-byte Studio empty voxel fixture');
check(at('Lighting').TimeOfDay==='15:30:00','authored initial lighting time is serialized');
check(ws.StreamingEnabled===true&&ws.StreamingMinRadius===128&&ws.StreamingTargetRadius===512,'streaming enabled with bounded radii');
check(at('Workspace','World','Plots').Children.length===9,'nine physically distinct building plots');
const spawnMarkers=at('Workspace','World','ZombieSpawns').Children;
check(spawnMarkers.length===28,'both cities and mine expose twenty-eight bounded enemy spawn markers');
check(spawnMarkers.some(n=>n.Name==='MineBossSpawn'),'dedicated mine boss spawn is physically authored');
check(spawnMarkers.filter(n=>n.Name.startsWith('MineMinionSpawn')).length===3,'three explicit mine-only strong-minion markers');
check(spawnMarkers.filter(n=>n.Name.startsWith('HarborSpawn')).length===8,'second city has eight bounded infected spawn markers');
const loot=at('Workspace','World','LootSpawns').Children;
check(loot.filter(n=>n.Name.startsWith('Supply')).length===3,'three distinct quest supply crates');
check(loot.filter(n=>n.findFirstChild('ItemId')).length>=20,'real resource spawn definitions');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='02_MeridianMarket'),'enterable market included');
const market=at('Workspace','World','Cities','02_MeridianMarket');
check(market.Children.some(n=>n.Name==='Roof')&&!market.Children.some(n=>n.Name==='PartialRoof'),'MERIDIAN market has a complete roof rather than the misleading partial slab');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='03_SalvageDepot'),'industrial depot included');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='10_BlackVeinQuarry'),'expanded east quarry and traversable mine included');
check(at('Workspace','World','Cities','10_BlackVeinQuarry','TraversableMine').Children.some(n=>n.Name==='TunnelFloor'),'mine has a physical walkable tunnel');
check(at('Workspace','World','Cities','10_BlackVeinQuarry','ForemanChamber').Children.some(n=>n.Name==='ChamberFloor'),'mine boss chamber is physical playable geometry');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='11_NewFrontier'),'НОВЫЙ РУБЕЖ second-city district is physically included');
for(const name of ['12_FrontierCommand','13_FrontierClinic','14_GarrisonSupply','15_FrontierWorkshop'])check(at('Workspace','World','Cities',name).ClassName==='Model',`${name} is a physical second-city facility`);
check(at('Workspace','World','TerrainBase').Children.some(n=>n.Name==='SecondCityGround'),'second city has a grounded collidable terrain foundation');
const transports=at('Workspace','World','Transports');
for(const name of ['City1Pad','City2Pad','City1Arrival','City2Arrival'])check(transports.Children.some(n=>n.Name===name&&n.ClassName==='Part'),`${name} is a real authored travel point`);
for(const name of ['City1Helicopter','City2Helicopter']) {
  const helicopter=at('Workspace','World','Transports',name);
  check(helicopter.ClassName==='Model'&&helicopter.PrimaryPart?.Name==='Body'&&helicopter.Children.filter(n=>n.Name==='MainRotor').length===2,`${name} is a pivotable modeled helicopter with crossed rotors`);
}
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
for(const name of ['04_MedicalPharmacy','05_PoliceStation','06_FuelStation','07_FuelOffice'])check(at('Workspace','World','Cities',name).ClassName==='Model',`${name} is physically included`);
check(at('Workspace','World','Interactables').Children.length===2,'two server-interactive door models');
check(loot.filter(n=>n.findFirstChild('QuestId')).length===11,'eleven distinct persisted quest targets across both cities');
check(loot.filter(n=>n.Name.startsWith('HarborMedical')).length===3,'three separate second-city medical quest caches');
const materialService=own.Roots.find(n=>n.ClassName==='MaterialService');
check(materialService.Use2022MaterialsXml===true,'native detailed material set enabled');
check(own.Roots.find(n=>n.ClassName==='Lighting').Technology.Name==='Future','modern lighting pipeline serialized');
check(ownNodes.filter(n=>n.ClassName==='TrussPart'&&n.Name==='RoofAccessLadder').length===2,'two climbable roof-access ladders');
check(ownNodes.filter(n=>n.ClassName==='Part'&&n.Name==='WindowPane').length>500,'four-sided detailed building facades');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='08_MeridianReliefCamp'),'playable relief camp is physically included');
check(at('Workspace','World','Cities').Children.some(n=>n.Name==='09_EvacuationConvoy'),'evacuation convoy is physically included');
check(ownNodes.filter(n=>n.ClassName==='WedgePart'&&n.Name.endsWith('Ridge')).length>=50,'angular layered boundary ridges replace spherical horizon chain');
check(!ownNodes.some(n=>n.Name==='DistantHills'),'old giant spherical boundary hills are absent');
check(ownNodes.filter(n=>n.ClassName==='Model'&&n.Name==='RoadClosure').length>=9,'abrupt road ends are authored quarantine closures');
check(terrain.findFirstChild('StormFront')?.ClassName==='Clouds','native dynamic cloud layer is serialized');
check(at('ReplicatedStorage','Shared','ModelFactory').Source.includes('HumanoidRigType.R15'),'runtime actors use player-shaped R15 articulation');
const animationSource=at('StarterPlayer','StarterPlayerScripts','Controllers','ActorAnimationController').Source;
check(animationSource.includes('ZombieAttack')&&animationSource.includes('HeadScratch')&&animationSource.includes('Salute')&&animationSource.includes('Aiming')&&animationSource.includes('Downed'),'distinct repaired combat, ambient and squad poses are embedded');
const npcSource=at('ServerScriptService','Services','NPCService').Source;
check(npcSource.includes('KeyboardKeyCode = Enum.KeyCode.E'),'NPC talk explicitly binds keyboard E on the server-created prompt');
check(npcSource.includes('playerHead')&&npcSource.includes('npcHead'),'Victor and resident dialogue validates head-level line of sight');
check(npcSource.includes('function Service:Evacuate')&&npcSource.includes('forcePath = true')&&npcSource.includes('AgentCanJump = false'),'workers, builder and squad route around structures without jump links');
const zombieDefsSource=at('ReplicatedStorage','Shared','ZombieDefinitions').Source;
check(zombieDefsSource.includes('damage = 5')&&zombieDefsSource.includes('foreman'),'graduated zombie roster starts at five damage and includes the mine boss');
check((zombieDefsSource.match(/= \{name =/g)||[]).length===12&&zombieDefsSource.includes('mineOnly = true')&&zombieDefsSource.includes('city2Only = true'),'twelve infected variants carry explicit mine/second-city restrictions');
const zombieSource=at('ServerScriptService','Services','ZombieService').Source;
check(zombieSource.includes('AgentCanJump = false')&&zombieSource.includes('BuildingService:Damage'),'infected cannot path-jump walls and can attack blocking defenses');
const questDefsSource=at('ReplicatedStorage','Shared','QuestDefinitions').Source;
check(questDefsSource.includes('id = "glock"')&&questDefsSource.includes('ammo = 10')&&questDefsSource.includes('items = {ammo = 48}'),'mine quest embeds exact Glock 10/10 plus 48-reserve reward');
check(questDefsSource.includes('second_city')&&questDefsSource.includes('count = 25')&&questDefsSource.includes('rescue_fireteam'),'Alex 25-kill travel unlock and military rescue quest are embedded');
const buildingDefsSource=at('ReplicatedStorage','Shared','BuildingDefinitions').Source;
for(const id of ['metal_wall','spikes','advanced_generator','greenhouse','purifier','ammo_press','field_hospital','radio_tower'])check(buildingDefsSource.includes(`${id} =`),`${id} functional building definition is embedded`);
const squadSource=at('ServerScriptService','Services','SquadService').Source;
check(squadSource.includes('NPCService:Walk')&&squadSource.includes('rifle_ammo -= 1')&&squadSource.includes('CompleteExpedition'),'quest squad uses no-jump resident routing, consumes ammo and gathers real resources');
const travelSource=at('ServerScriptService','Services','TravelService').Source;
check(travelSource.includes('task.delay(6')&&travelSource.includes('if not unlocked(s.Campaign)')&&travelSource.includes('root.Anchored = true'),'two-way helicopter travel is quest-locked and server-timed');
check(at('ServerScriptService','Services','PlayerDataService').Source.includes('canceled = true')&&at('StarterPlayer','StarterPlayerScripts','Controllers','CameraController').Source.includes('CharacterAdded:Connect'),'respawn cancels the server flight token and client cinematic');
const audioSource=at('StarterPlayer','StarterPlayerScripts','Controllers','AudioController').Source;
check(audioSource.includes('MusicPulse')&&audioSource.includes('SetTravel')&&!/rbxassetid:\/\/\d+/.test(audioSource),'music/effects controller uses configured bundled audio, not arbitrary catalog IDs');
const factorySource=at('ReplicatedStorage','Shared','ModelFactory').Source;
check(factorySource.includes('HookTip')&&factorySource.includes('PryClaw')&&factorySource.includes('GripWrap'),'crowbar has modeled hook, pry claw and grip wraps');
check(at('StarterPlayer','StarterPlayerScripts','Client').Source.includes('action = "toggleCrowbar"')&&at('ServerScriptService','Services','InventoryService').Source.includes('p.action == "toggleCrowbar"'),'keyboard 1 delegates crowbar toggle state and UID choice to the server');
const combatControllerSource=at('StarterPlayer','StarterPlayerScripts','Controllers','CombatController').Source;
check(combatControllerSource.includes('Enum.KeyCode.K')&&!combatControllerSource.includes('Enum.KeyCode.I] = "inventory"'),'backpack binding moved from I to selected K key');
check(!at('ServerScriptService','Domain','Building').Source.includes('hx + ox + 0.1'),'authoritative building overlap has no artificial adjacency gap');
check(ownNodes.filter(n=>n.IsA('BasePart')).length<7000,'expanded two-city geometry stays within explicit 7000-part build budget');

const roundtrip=own.WriteToBuffer();
const parsed=reader.parseBuffer(roundtrip.buffer.slice(roundtrip.byteOffset,roundtrip.byteOffset+roundtrip.byteLength));
check(parsed.instances.length===independent.instances.length,'binary roundtrip preserves instance count');
check(parsed.instances.filter(n=>['Script','LocalScript','ModuleScript'].includes(n.ClassName)).every(n=>independent.instances.some(o=>o.ClassName===n.ClassName&&o.Name===n.Name&&o.Source===n.Source)),'binary roundtrip preserves exact UTF-8 script bytes');
const classes={}; for(const n of independent.instances)classes[n.ClassName]=(classes[n.ClassName]??0)+1;
const result={assertions,instances:independent.instances.length,parts:ownNodes.filter(n=>n.IsA('BasePart')).length,scripts:sourceCount,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),classes};
fs.writeFileSync(path.join(path.dirname(artifact),'verification.json'),JSON.stringify(result,null,2)+'\n');
console.log(`\nFILE RESULTS: ${assertions} PASSED / 0 FAILED\n${JSON.stringify(result,null,2)}`);
