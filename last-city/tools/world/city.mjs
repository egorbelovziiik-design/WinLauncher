import { part, folder, sign, lamp, car, crate, loot, palette as P } from './primitives.mjs';
import { roadNetwork, CITY_ROADS } from './roads.mjs';
import { apartment } from './architecture.mjs';
export function city(world) {
  roadNetwork(world.Roads,'MeridianStreets',CITY_ROADS,5);
  let id=0;
  for (const [x,z,w,d,h] of [[62,99,56,65,49],[-64,348,60,65,56],[63,348,62,63,92],[-185,92,55,60,40],[-185,220,58,65,71],[185,220,58,70,57],[-180,351,60,65,83],[185,351,59,65,98]]) {
    apartment(world,x,z,w,d,h,['#777a6a','#737d77','#8a8070','#657574'][id++%4],id);
  }
  // MERIDIAN market: open front doorway and broken facade; three unique quest crates.
  const market = folder(world.Cities,'02_MeridianMarket',true);
  part(market,'Floor',[65,0.5,65],[-62,0.3,100],P.concrete);
  part(market,'Back',[65,15,1],[-62,7.5,132],'#8a7b67','Brick');
  for(const x of [-94,-30]) part(market,'Side',[1,15,65],[x,7.5,100],'#8a7b67','Brick');
  for(const x of [-83,-41]) part(market,'Storefront',[23,15,1],[x,7.5,68],'#8a7b67','Brick');
  part(market,'Lintel',[20,4,1],[-62,13,68],P.concrete);
  part(market,'PartialRoof',[65,0.7,35],[-62,15.3,116],P.dark,'Metal');
  sign(market,'MERIDIAN\nFOOD  /  SUPPLIES',[61,5,0.7],[-62,18.7,67],P.amber,P.dark);
  for(const x of [-82,-42]) for(const z of [91,117]) {
    part(market,'Shelf',[10,0.5,3],[x,4,z],P.steel,'Metal');
    part(market,'Shelf',[10,0.5,3],[x,1.8,z],P.steel,'Metal');
    for(const dx of [-4,4]) part(market,'Support',[0.4,6,3],[x+dx,3,z],P.dark,'Metal');
  }
  for(const [i,x,z] of [[1,-78,105],[2,-43,120],[3,-65,126]]) part(world.LootSpawns,`Supply${i}`,[4,3,4],[x,1.8,z],P.orange,'Metal');
  loot(world.LootSpawns,'food',3,-42,84,'market',110);
  loot(world.LootSpawns,'medkit',1,-83,122,'market',180);
  // Industrial salvage depot; open gates and physical machinery.
  const depot = folder(world.Cities,'03_SalvageDepot',true);
  part(depot,'Yard',[72,0.3,70],[187,0.1,98],'#555b56','Concrete');
  sign(depot,'K-12  /  SALVAGE\nПРОМЫШЛЕННЫЙ УЗЕЛ',[54,5,1],[187,14,63],P.orange,P.dark);
  for(const x of [157,217]) part(depot,'Post',[1,16,1],[x,8,63],P.dark,'Metal');
  for(const z of [91,115]) {
    part(depot,'Container',[22,10,10],[197,5,z],z===91?'#4e716e':'#8c694e','Metal');
    for(let x=187;x<207;x+=3) part(depot,'Rib',[0.4,10,10.3],[x,5,z],P.steel,'Metal');
  }
  part(depot,'CraneMast',[3,40,3],[160,20,125],P.orange,'Metal');
  part(depot,'CraneArm',[47,2,3],[182,39,125],P.orange,'Metal');
  part(depot,'Cable',[0.25,22,0.25],[201,27,125],P.dark,'Metal');
  part(depot,'Hook',[2,2,2],[201,15.5,125],P.steel,'Metal');
  for(const [x,z] of [[159,78],[170,93],[160,112],[176,126]]) loot(world.LootSpawns,'metal',10,x,z,`depot_${x}_${z}`);
  for(const [x,z] of [[172,76],[182,105]]) loot(world.LootSpawns,'wire',5,x,z,`depot_${x}`);
  loot(world.LootSpawns,'electronics',5,210,80,'depot');
  loot(world.LootSpawns,'fuel',3,211,130,'depot',120);
  // Close-to-gate salvage makes the tutorial possible without crossing the entire district.
  for(const [id,n,x,z] of [['metal',10,25,18],['metal',10,-25,8],['wood',10,-27,-6],['wire',5,26,55],['wire',5,103,63],['electronics',5,27,154],['scrap',8,99,182],['fuel',2,-106,145],['ammo',12,-8,218]]) loot(world.LootSpawns,id,n,x,z,`${x}_${z}`);
  for(const [x,z,yaw,col] of [[7,63,0.15,P.rust],[-8,176,-0.2,P.teal],[126,200,0.3,'#8b895f'],[-124,100,-0.4,P.rust],[88,281,1.6,'#6b7780'],[-85,282,1.4,P.rust],[8,325,0.4,P.teal]]) car(world.Roads,x,z,yaw,col);
  for(const x of [-19,103,-141]) for(const z of [62,187,311]) lamp(world.Roads,x,z,x===-19&&z===62);
  // Checkpoint and northern map boundary (not a false inaccessible quest destination).
  const cp = folder(world.MilitaryBases,'NorthernCheckpoint',true);
  sign(cp,'QUARANTINE\nКАРАНТИН — ПРОХОД ЗАКРЫТ',[36,8,1],[0,17,416],P.amber,P.dark);
  for(const x of [-20,20]) part(cp,'Post',[2,22,2],[x,11,416],P.steel,'Metal');
  for(const x of [-9,9]) part(cp,'Barrier',[15,5,4],[x,2.5,414],P.concrete);
  for(const [i,x,z] of [[1,-12,23],[2,34,47],[3,-108,48],[4,17,103],[5,-112,193],[6,111,145],[7,140,55],[8,10,251],[9,-11,298],[10,120,282],[11,-121,311],[12,205,156]]) {
    part(world.ZombieSpawns,`Spawn${i}`,[1,1,1],[x,0,z],P.orange,'Plastic',0,{Transparency:1,CanCollide:false,CanQuery:false});
  }
}
