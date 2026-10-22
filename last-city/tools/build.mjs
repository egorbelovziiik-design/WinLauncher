import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { R, folder, color, lookAt } from './world/primitives.mjs';
import { shelter } from './world/shelter.mjs';
import { city } from './world/city.mjs';
import { landscape } from './world/landscape.mjs';
import { encodeAuthoredText } from './encoding.mjs';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const output = path.resolve(root, '../release-artifacts/last-city');
fs.mkdirSync(output, {recursive:true});
const file = new R.RobloxFile();
file.Metadata.set('Creator','Last City • source-built vertical slice');
file.Metadata.set('Version','0.1.0');
function service(name) { const o = new R[name](); o.Name = name; file.AddRoot(o); return o; }
const Workspace = service('Workspace'); Workspace.StreamingEnabled = true; Workspace.StreamingMinRadius = 128; Workspace.StreamingTargetRadius = 512; Workspace.Gravity = 196.2; Workspace.FallenPartsDestroyHeight = -100;
const camera = new R.Camera(); camera.Name='Camera'; camera.CFrame=lookAt([65,45,-20],[-30,15,120]); camera.FieldOfView=70; camera.Parent=Workspace; Workspace.CurrentCamera=camera;
const terrain = new R.Terrain(); terrain.Name='Terrain'; terrain.Parent=Workspace;
const worldFolder=folder(Workspace,'World');
const world={};
for(const name of ['TerrainBase','Cities','Forests','MilitaryBases','Roads','Buildings','ZombieSpawns','NPCSpawns','LootSpawns','Zombies','Plots']) world[name]=folder(worldFolder,name);
landscape(world); shelter(world); city(world);
const Lighting=service('Lighting'); Lighting.ClockTime=15.5; Lighting.Brightness=2.3; Lighting.GlobalShadows=true;
Lighting.Ambient=color('#727e83'); Lighting.OutdoorAmbient=color('#87958f'); Lighting.EnvironmentDiffuseScale=0.7; Lighting.EnvironmentSpecularScale=0.5;
const atmosphere=new R.Atmosphere(); atmosphere.Name='AshAtmosphere'; atmosphere.Density=0.29; atmosphere.Offset=0.08; atmosphere.Color=color('#b6b4a0'); atmosphere.Decay=color('#5d6d74'); atmosphere.Glare=0.2; atmosphere.Haze=2; atmosphere.Parent=Lighting;
const correction=new R.ColorCorrectionEffect(); correction.Name='DeadWorldGrade'; correction.TintColor=color('#ffebd3'); correction.Saturation=-0.13; correction.Contrast=0.12; correction.Parent=Lighting;
const bloom=new R.BloomEffect(); bloom.Name='PracticalLights'; bloom.Intensity=0.18; bloom.Size=20; bloom.Threshold=1.4; bloom.Parent=Lighting;
const rays=new R.SunRaysEffect(); rays.Intensity=0.06; rays.Spread=0.8; rays.Parent=Lighting;
const sky=new R.Sky(); sky.Name='Sky'; sky.StarCount=1800; sky.Parent=Lighting;
const ReplicatedStorage=service('ReplicatedStorage');
const remotes=folder(ReplicatedStorage,'Remotes');
for(const name of ['SessionRemote','QuestRemote','InventoryRemote','CombatRemote','BuildRemote','ShopRemote','NPCRemote','MovementRemote','CraftingRemote','StateRemote','EffectsRemote']) { const r=new R.RemoteEvent(); r.Name=name; r.Parent=remotes; }
const ServerScriptService=service('ServerScriptService');
const StarterPlayer=service('StarterPlayer'); StarterPlayer.CameraMinZoomDistance=7; StarterPlayer.CameraMaxZoomDistance=22; StarterPlayer.EnableMouseLockOption=true;
const StarterPlayerScripts=new R.StarterPlayerScripts(); StarterPlayerScripts.Name='StarterPlayerScripts'; StarterPlayerScripts.Parent=StarterPlayer;
service('StarterGui');
const ReplicatedFirst=service('ReplicatedFirst');
service('SoundService');
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
    const script=new R[type](); script.Name=entry.name.replace(/\.(server|client)\.luau$|\.luau$/,''); script.Source=fs.readFileSync(source,'utf8'); script.Parent=parent; scripts++;
  }
}
for(const [dir,parent] of [['ReplicatedStorage',ReplicatedStorage],['ServerScriptService',ServerScriptService],['StarterPlayer/StarterPlayerScripts',StarterPlayerScripts],['ReplicatedFirst',ReplicatedFirst]]) load(path.join(root,'src',dir),parent);
encodeAuthoredText(file);
const bytes=file.WriteToBuffer();
const main=path.join(output,'LAST_CITY_3D.rbxl'); fs.writeFileSync(main,bytes);
fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify({version:'0.1.0',name:'LAST CITY 3D — First Light',format:'Roblox binary place',scripts,bytes:bytes.length},null,2)+'\n');
console.log(`Built ${main}\n${scripts} Luau sources; ${bytes.length.toLocaleString()} bytes`);
