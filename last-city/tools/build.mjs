import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { R, folder, color, lookAt } from './world/primitives.mjs';
import { shelter } from './world/shelter.mjs';
import { city } from './world/city.mjs';
import { landscape } from './world/landscape.mjs';
import { locations } from './world/locations.mjs';
import { district } from './world/district.mjs';
import { mine } from './world/mine.mjs';
import { secondCity } from './world/secondCity.mjs';
import { encodeAuthoredText } from './encoding.mjs';
import { instance, emptyTerrain, validateAuthoredInstances } from './instances.mjs';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const output = path.resolve(root, '../release-artifacts/last-city');
fs.mkdirSync(output, {recursive:true});
const file = new R.RobloxFile();
file.Metadata.set('Creator','Last City source-built vertical slice');
file.Metadata.set('Version','0.5.0');
function service(name) { const o = instance(name); o.Name = name; file.AddRoot(o); return o; }
const Workspace = service('Workspace'); Workspace.StreamingEnabled = true; Workspace.StreamingMinRadius = 128; Workspace.StreamingTargetRadius = 512; Workspace.Gravity = 196.2; Workspace.FallenPartsDestroyHeight = -100;
const camera = instance('Camera'); camera.Name='Camera'; camera.CFrame=lookAt([65,45,-20],[-30,15,120]); camera.FieldOfView=70; camera.Parent=Workspace; Workspace.CurrentCamera=camera;
const terrain = emptyTerrain(); terrain.Name='Terrain'; terrain.Parent=Workspace;
const clouds=instance('Clouds');clouds.Name='StormFront';clouds.Cover=0.32;clouds.Density=0.42;clouds.Color=color('#c7cec6');clouds.Parent=terrain;
const worldFolder=folder(Workspace,'World');
const world={};
for(const name of ['TerrainBase','Cities','Forests','MilitaryBases','Roads','Buildings','ZombieSpawns','NPCSpawns','LootSpawns','Zombies','Plots','Interactables','Transports']) world[name]=folder(worldFolder,name);
landscape(world); shelter(world); city(world); locations(world); district(world); mine(world); secondCity(world);
const Lighting=service('Lighting'); Lighting.TimeOfDay='15:30:00'; Lighting.Brightness=2.6; Lighting.GlobalShadows=true; Lighting.GeographicLatitude=47.5;
Lighting.Technology=R.Technology.Future; Lighting.LightingStyle=R.LightingStyle.Realistic; Lighting.PrioritizeLightingQuality=true; Lighting.ExposureCompensation=0.08; Lighting.ShadowSoftness=0.3;
Lighting.Ambient=color('#34434c'); Lighting.OutdoorAmbient=color('#697b83'); Lighting.EnvironmentDiffuseScale=0.65; Lighting.EnvironmentSpecularScale=0.85;
const atmosphere=instance('Atmosphere'); atmosphere.Name='AshAtmosphere'; atmosphere.Density=0.17; atmosphere.Offset=0.08; atmosphere.Color=color('#d1d6cd'); atmosphere.Decay=color('#899b9f'); atmosphere.Glare=0.12; atmosphere.Haze=0.9; atmosphere.Parent=Lighting;
const correction=instance('ColorCorrectionEffect'); correction.Name='DeadWorldGrade'; correction.TintColor=color('#faf3e8'); correction.Saturation=-0.06; correction.Contrast=0.1; correction.Parent=Lighting;
const bloom=instance('BloomEffect'); bloom.Name='PracticalLights'; bloom.Intensity=0.12; bloom.Size=20; bloom.Threshold=1.7; bloom.Parent=Lighting;
const rays=instance('SunRaysEffect'); rays.Intensity=0.06; rays.Spread=0.8; rays.Parent=Lighting;
const sky=instance('Sky'); sky.Name='Sky'; sky.StarCount=1800; sky.Parent=Lighting;
const ReplicatedStorage=service('ReplicatedStorage');
const remotes=folder(ReplicatedStorage,'Remotes');
for(const name of ['SessionRemote','QuestRemote','InventoryRemote','CombatRemote','BuildRemote','ShopRemote','NPCRemote','MovementRemote','CraftingRemote','StateRemote','EffectsRemote']) { const r=instance('RemoteEvent'); r.Name=name; r.Parent=remotes; }
const ServerScriptService=service('ServerScriptService');
const StarterPlayer=service('StarterPlayer'); StarterPlayer.CameraMinZoomDistance=7; StarterPlayer.CameraMaxZoomDistance=22; StarterPlayer.EnableMouseLockOption=true;
const StarterPlayerScripts=instance('StarterPlayerScripts'); StarterPlayerScripts.Name='StarterPlayerScripts'; StarterPlayerScripts.Parent=StarterPlayer;
service('StarterGui');
const ReplicatedFirst=service('ReplicatedFirst');
service('SoundService');
const materials=service('MaterialService'); materials.Use2022MaterialsXml=true;
let scripts=0;
function containsSource(directory) {
  return fs.readdirSync(directory,{withFileTypes:true}).some(entry => entry.isDirectory()
    ? containsSource(path.join(directory,entry.name)) : entry.name.endsWith('.luau'));
}
function load(directory,parent) {
  if(!fs.existsSync(directory))return;
  for(const entry of fs.readdirSync(directory,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))) {
    const source=path.join(directory,entry.name);
    if(entry.isDirectory()) { if(containsSource(source)) load(source,folder(parent,entry.name)); continue; }
    if(!entry.name.endsWith('.luau'))continue;
    const type=entry.name.endsWith('.server.luau')?'Script':entry.name.endsWith('.client.luau')?'LocalScript':'ModuleScript';
    const script=instance(type); script.Name=entry.name.replace(/\.(server|client)\.luau$|\.luau$/,''); script.Source=fs.readFileSync(source,'utf8'); script.Parent=parent; scripts++;
  }
}
for(const [dir,parent] of [['ReplicatedStorage',ReplicatedStorage],['ServerScriptService',ServerScriptService],['StarterPlayer/StarterPlayerScripts',StarterPlayerScripts],['ReplicatedFirst',ReplicatedFirst]]) load(path.join(root,'src',dir),parent);
validateAuthoredInstances(file);
encodeAuthoredText(file);
const bytes=file.WriteToBuffer();
const main=path.join(output,'LAST_CITY_3D.rbxl'); fs.writeFileSync(main,bytes);
fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify({version:'0.5.0',name:'LAST CITY 3D — Iron Horizon',format:'Roblox binary place',scripts,bytes:bytes.length},null,2)+'\n');
console.log(`Built ${main}\n${scripts} Luau sources; ${bytes.length.toLocaleString()} bytes`);
