import {R,part,folder,sign,loot,crate,car,value,instance,color,palette as P} from './primitives.mjs';
import {enterable} from './architecture.mjs';
function cache(world,name,quest,event,target,at,label) {
  const p=part(world.LootSpawns,name,[2.8,1.5,2.2],at,'#59796c','Metal');
  value(p,'QuestId',quest);value(p,'QuestEvent',event);value(p,'QuestTarget',target);value(p,'QuestLabel',label);
  part(p,'Latch',[0.4,0.25,0.15],[at[0],at[1],at[2]-1.17],'#b4bca3','Metal');
  return p;
}
function door(world,x,z,title) {
  const m=folder(world.Interactables,title,true);
  const hinge=part(m,'Hinge',[0.2,0.2,0.2],[x-4.8,5,z],P.dark,'Metal',0,{Transparency:1,CanCollide:false,CanQuery:false});
  const leaf=part(m,'Leaf',[9.6,9.4,0.38],[x,5,z],'#495e5a','Metal');
  m.PrimaryPart=hinge; value(m,'DoorWidth',9.6); value(m,'DoorLabel',title);
  return leaf;
}
export function locations(world) {
  const pharmacy=enterable(world,'04_MedicalPharmacy',-62,220,58,60,'#7c8172');
  sign(pharmacy,'MERIDIAN  /  MEDICAL\nАПТЕКА  •  ЛЕКАРСТВА',[52,4,0.5],[-62,15.8,189.4],'#d5ded0','#36564e');
  part(pharmacy,'EntryPath',[11,0.28,13],[-62,0.14,183],'#777b73','Concrete');
  door(world,-62,189.5,'Дверь аптеки');
  for(const x of [-82,-42])for(const z of [208,234]) {
    for(const y of [1.5,3.4,5.3])part(pharmacy,'MedicalShelf',[10,0.25,2.2],[x,y,z],'#8a9488','Metal');
    for(const dx of [-4.7,4.7])part(pharmacy,'ShelfLeg',[0.22,6,2.2],[x+dx,3,z],P.steel,'Metal');
    for(let i=0;i<4;i++)part(pharmacy,'EmptyMedicineCarton',[1,0.6,0.7],[x-3+i*2,3.85,z],'#a7b4a0','SmoothPlastic',0,{CanCollide:false});
  }
  part(pharmacy,'Counter',[15,3.5,3],[-62,1.75,239],'#556b62','WoodPlanks');
  part(pharmacy,'Countertop',[15.6,0.3,3.6],[-62,3.65,239],'#b2b6a5','Marble');
  for(const [i,x,z]of [[1,-81,209],[2,-42,234],[3,-63,244]])cache(world,`MedicalCache${i}`,'medical_run','medical','pharmacy',[x,1.2,z],'Медицинский контейнер');
  loot(world.LootSpawns,'bandage',3,-43,208,'pharmacy',150);
  loot(world.LootSpawns,'cloth',6,-82,238,'pharmacy',100);
  loot(world.LootSpawns,'medkit',1,-64,230,'pharmacy',180);

  const police=enterable(world,'05_PoliceStation',62,220,58,62,'#6f7c7a');
  sign(police,'MERIDIAN POLICE\nУЧАСТОК № 07',[52,4,0.5],[62,16,188.5],'#d4daca','#384d5a');
  part(police,'EntryPath',[11,0.28,13],[62,0.14,182],'#777b73','Concrete');
  door(world,62,188.5,'Дверь участка');
  for(const x of [47,75])for(const z of [209,234]) {
    part(police,'Desk',[8,0.55,4],[x,3,z],'#66746e','WoodPlanks');
    for(const side of [-1,1])part(police,'DeskLeg',[0.3,3,3],[x+side*3.5,1.5,z],P.dark,'Metal');
    part(police,'Monitor',[2.7,1.8,0.25],[x,4.15,z+1],P.dark,'SmoothPlastic');
    part(police,'Chair',[2.2,2.8,2.2],[x,1.4,z-3],'#3a494c','Fabric');
  }
  for(let i=0;i<7;i++)part(police,'ArchiveLocker',[2.3,7,2.8],[41+i*3,3.5,248],'#4b615e','Metal');
  cache(world,'PoliceDispatch','radio_recovery','intel','radio',[74,3.75,234],'Журнал диспетчера');
  loot(world.LootSpawns,'ammo',16,43,242,'police',150);
  loot(world.LootSpawns,'parts',4,79,242,'police',160);
  car(world.Roads,78,182,Math.PI/2,'#344c5b');

  const gas=folder(world.Cities,'06_FuelStation',true);
  part(gas,'Forecourt',[72,0.25,63],[274,0.125,100],'#656b61','Concrete');
  part(gas,'Canopy',[67,0.7,26],[272,13.5,83],'#7c8a7a','Metal');
  for(const x of [247,295])part(gas,'CanopyPillar',[0.7,13,0.7],[x,6.5,83],P.steel,'Metal');
  for(const x of [257,285]) {
    part(gas,'PumpIsland',[12,0.65,7],[x,0.325,83],'#8b8d7c','Concrete');
    part(gas,'FuelPump',[3.5,6,2.6],[x,3.6,83],'#986b45','Metal');
    part(gas,'PumpDisplay',[2.3,1.2,0.12],[x,4.8,81.64],'#223333','Glass');
    part(gas,'Hose',[0.18,4,0.18],[x+2.05,2.8,83],'#20292a','Rubber');
  }
  sign(gas,'NORTHLINE  /  FUEL',[54,3,0.4],[272,14.7,69.7],'#d7d6bc','#475e56');
  const shop=enterable(world,'07_FuelOffice',274,121,36,21,'#817c65');
  sign(shop,'SERVICE / CASH',[28,2,0.35],[274,11.6,110.2],'#c3cbb2','#384d45');
  cache(world,'RadioMaintenance','radio_recovery','intel','radio',[284,1.2,124],'Схема ретранслятора');
  loot(world.LootSpawns,'fuel',5,265,125,'fuel_station',160);
  loot(world.LootSpawns,'water',3,285,115,'fuel_station',120);
  part(gas,'AccessLane',[13,0.24,42],[254,0,151],'#343938','Asphalt');
  part(gas,'AccessLink',[15.5,0.24,13],[239.75,0,166],'#343938','Asphalt');
  sign(gas,'АПТЕКА ←     ПОЛИЦИЯ ←\nАЗС / FUEL →',[17,3,0.3],[220,8,155],'#ccd5bd','#405c4d');
  part(gas,'WayfindingPost',[0.3,8,0.3],[220,4,155],P.steel,'Metal');
}
