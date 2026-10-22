import {R,instance,part,orientedPart,folder,sign,loot,value,lamp,color,palette as P} from './primitives.mjs';
import {enterable,apartment,compactHome} from './architecture.mjs';
import {bossSite} from './encounters.mjs';

const trim={CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
export const FRONTIER_JUNCTION_ROADS = [
  {name:'FrontierMainRoadWest',w:171,d:28,x:820.5,z:220},
  {name:'FrontierMainRoadEast',w:231,d:28,x:1049.5,z:220},
  {name:'FrontierCrossRoadSouth',w:28,d:410,x:920,z:1},
  {name:'FrontierCrossRoadNorth',w:28,d:131,x:920,z:299.5},
  {name:'FrontierJunction',w:28,d:28,x:920,z:220},
];
export const FRONTIER_RESIDENTIAL_ROAD = {name:'FrontierResidentialRoad',w:430,d:22,x:950,z:-215};
export const FRONTIER_SIDEWALKS = [
  ...[202,238].flatMap(z=>[{x:817,z,w:164,d:6},{x:1053,z,w:224,d:6}]),
  ...[902,938].flatMap(x=>[{x,z:-4.5,w:6,d:399},{x,z:305,w:6,d:120}]),
  {x:817,z:-200.5,w:164,d:5},{x:1053,z:-200.5,w:224,d:5},
];
function helicopter(parent,name,x,z,yaw=0) {
  const m=folder(parent,name,true); const c=Math.cos(yaw),s=Math.sin(yaw);
  const p=(n,size,o,tint=P.steel,mat='Metal',extra={})=>part(m,n,size,[x+o[0]*c+o[2]*s,o[1],z-o[0]*s+o[2]*c],tint,mat,yaw,extra);
  const body=p('Body',[8,4.2,13],[0,5,0],'#455c50');
  p('Cockpit',[7.2,3.2,4.8],[0,5.8,-5.1],'#29444a','Glass');
  p('TailBoom',[2.2,2.2,13],[0,5.2,12],'#41574d');p('TailFin',[0.5,5.8,4],[0,8,17],'#526b5d');
  p('MainRotorMast',[0.5,3,0.5],[0,8.4,0],P.dark);
  p('MainRotor',[1,0.18,30],[0,10,0],P.dark,'Metal',trim);p('MainRotor',[30,0.18,1],[0,10,0],P.dark,'Metal',trim);
  p('TailRotor',[0.18,8,0.6],[1.2,7.6,17],P.dark,'Metal',trim);p('TailRotor',[0.18,0.6,8],[1.2,7.6,17],P.dark,'Metal',trim);
  // Skids touch the 0.4-stud helipad surface instead of leaving the aircraft visibly suspended.
  for(const side of [-1,1]) {p('LandingSkid',[0.45,0.45,12],[side*3.8,0.64,0],P.dark);for(const zoff of [-3.5,3.5])p('SkidStrut',[0.35,3.5,0.35],[side*2.4,2.25,zoff],P.dark);}
  p('Door',[0.2,3,4.2],[-4.1,5,0.5],'#344c43');p('RescueMark',[0.12,1.4,1.4],[-4.24,5.5,0.5],P.orange,'Neon',trim);
  m.PrimaryPart=body; return m;
}
function helipad(parent,name,x,z,label) {
  const pad=part(parent,name,[26,0.4,26],[x,0.2,z],'#4a504c','Concrete');
  part(parent,'PadRing',[22,0.05,1],[x,0.43,z-10.5],P.amber,'Neon',0,trim);part(parent,'PadRing',[22,0.05,1],[x,0.43,z+10.5],P.amber,'Neon',0,trim);
  part(parent,'PadRing',[1,0.05,22],[x-10.5,0.43,z],P.amber,'Neon',0,trim);part(parent,'PadRing',[1,0.05,22],[x+10.5,0.43,z],P.amber,'Neon',0,trim);
  sign(parent,label,[20,3,0.4],[x,3,z+13],P.amber,P.dark,Math.PI);
  return pad;
}
function prismCave(world) {
  const cave=folder(world.Cities,'18_PrismCave',true);const x=1140,z=-76;
  part(cave,'CaveFloor',[70,0.55,86],[x,0.16,z],'#3f4245','Slate');
  part(cave,'CaveBack',[70,14,3],[x,7,z+41.5],'#4b4d50','Rock');
  for(const side of [-1,1])part(cave,'CaveWall',[3,14,86],[x+side*34,7,z],'#4b4d50','Rock');
  for(const side of [-1,1])part(cave,'EntranceRock',[21,14,3],[x+side*24.5,7,z-41.5],'#4b4d50','Rock');
  part(cave,'CaveRoof',[72,1.2,88],[x,14,z],'#3b3f42','Rock');
  for(const [i,dx,dz,col] of [[1,-25,-18,'#49d8ff'],[2,24,-8,'#e65cff'],[3,-27,18,'#72f59c'],[4,22,24,'#ffd95c'],[5,-15,34,'#9b7bff'],[6,12,-28,'#ff789d']]) {
    const crystal=part(cave,`PrismCrystal${i}`,[1.5,6+i%3,1.5],[x+dx,3.2,z+dz],col,'Neon',i*0.22,{CanCollide:false,CanTouch:false});
    const glow=instance('PointLight');glow.Color=color(col);glow.Range=16;glow.Brightness=0.75;glow.Shadows=false;glow.Parent=crystal;
  }
  sign(cave,'PRISM CAVE  /  РАДУЖНЫЙ СТРАЖ',[26,3,0.4],[x,9,z-43.1],'#e879f9',P.dark);
  for(const px of [x-15,x+15])part(cave,'WarningPost',[0.8,10,0.8],[px,5,z-43],P.steel,'Metal');
  loot(world.LootSpawns,'prism_shard',3,x-22,z+28,'prism_cave_shards',240);
  loot(world.LootSpawns,'electronics',6,x+22,z+29,'prism_cave_electronics',240);
  loot(world.LootSpawns,'rifle_ammo',24,x-24,z-4,'prism_cave_ammo',240);
  const hidden={Transparency:1,CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
  part(world.ZombieSpawns,'RainbowBossSpawn',[1,1,1],[x,0.5,z+8],P.orange,'Plastic',0,hidden);
}
function questCache(world,name,at,label,questId='harbor_aid',event='harbor_medical',target='harbor') {
  const node=part(world.LootSpawns,name,[2.8,1.8,2.5],at,'#d7ddd0','Metal');
  value(node,'QuestId',questId);value(node,'QuestEvent',event);value(node,'QuestTarget',target);value(node,'QuestLabel',label);
  part(node,questId==='harbor_aid'?'MedicalMark':'SurveyMark',[1.7,0.18,0.08],[at[0],at[1]+0.4,at[2]-1.29],questId==='harbor_aid'?'#be4844':P.amber,'Neon',0,trim);
}
function guardTower(parent,x,z) {
  const m=folder(parent,'GarrisonTower',true);
  for(const dx of [-4,4])for(const dz of [-4,4])part(m,'TowerLeg',[0.7,16,0.7],[x+dx,8,z+dz],P.steel,'Metal');
  part(m,'TowerDeck',[10,0.7,10],[x,14,z],P.steel,'Metal');part(m,'TowerRoof',[11,0.5,11],[x,19,z],P.dark,'Metal');
  for(const dx of [-4,4])part(m,'RoofPost',[0.35,5,0.35],[x+dx,16.5,z],P.dark,'Metal');
  const lens=part(m,'GuardLight',[2.5,0.5,1.4],[x,17.5,z-4.6],P.amber,'Neon');
  const light=instance('SpotLight');light.Face=R.NormalId.Front;light.Range=65;light.Angle=75;light.Brightness=2;light.Color=color(P.amber);light.Parent=lens;
}
export function secondCity(world) {
  const district=folder(world.Cities,'11_NewFrontier',true);
  // A separated streamed district: the existing x=585 collision ridge prevents walking across the void.
  part(world.TerrainBase,'SecondCityGround',[490,8,700],[950,-4,80],'#3d4741','Ground');
  for(const x of [705,1195])part(world.TerrainBase,'SecondCityBounds',[8,240,700],[x,100,80],P.dark,'Plastic',0,{Transparency:1});
  for(const z of [-270,430])part(world.TerrainBase,'SecondCityBounds',[490,240,8],[950,100,z],P.dark,'Plastic',0,{Transparency:1});
  // Four road arms meet one junction slab. Nothing overlaps at the same elevation:
  // this fixes the old double asphalt/crossing geometry instead of covering it up.
  for(const road of FRONTIER_JUNCTION_ROADS)
    part(world.Roads,road.name,[road.w,0.28,road.d],[road.x,0.02,road.z],'#343938','Asphalt');
  // Sidewalks stop cleanly at the junction. The old full-length strips crossed both
  // carriageways, producing raised concrete bars and the broken road silhouette.
  for(const walk of FRONTIER_SIDEWALKS)
    part(world.Roads,'FrontierWalk',[walk.w,0.42,walk.d],[walk.x,0.2,walk.z],'#777b73','Concrete');
  for(let x=755;x<1145;x+=22)if(Math.abs(x-920)>28)part(world.Roads,'LanePaint',[8,0.03,0.24],[x,0.18,220],'#b5af8c','SmoothPlastic',0,trim);
  for(let z=-180;z<370;z+=22)if(Math.abs(z-220)>28)part(world.Roads,'LanePaint',[0.24,0.03,8],[920,0.18,z],'#b5af8c','SmoothPlastic',0,trim);
  // A connected southern residential street with six small enterable homes.
  part(world.Roads,FRONTIER_RESIDENTIAL_ROAD.name,[FRONTIER_RESIDENTIAL_ROAD.w,0.28,FRONTIER_RESIDENTIAL_ROAD.d],[FRONTIER_RESIDENTIAL_ROAD.x,0.02,FRONTIER_RESIDENTIAL_ROAD.z],'#343938','Asphalt');
  for(const [index,x] of [[1,760],[2,825],[3,875],[4,970],[5,1035],[6,1110]])
    compactHome(world,`17_FrontierHome_${index}`,x,-180,0,index%2?'#68766e':'#746f62');
  bossSite(world,'City2',1,750,395,'ОЧАГ БОССА  /  SOUTH WATCH');
  bossSite(world,'City2',2,1150,395,'ОЧАГ БОССА  /  EAST YARD');
  bossSite(world,'City2',3,1150,-245,'ОЧАГ БОССА  /  DEAD TERMINAL');
  prismCave(world);

  const base=folder(district,'FrontierGarrison',true);
  part(base,'GarrisonGround',[286,0.34,160],[920,0.1,95],'#555f56','Concrete');
  sign(base,'НОВЫЙ РУБЕЖ  /  FRONTIER-2\nУКРЕПЛЁННЫЙ РАЙОН',[74,7,0.8],[920,18,12],P.amber,P.dark,Math.PI);
  for(const x of [782,1058]) {part(base,'PerimeterWall',[4,13,164],[x,6.5,95],P.concrete,'Concrete');part(base,'WallCap',[4.5,0.7,164],[x,13,95],P.dark,'Metal');}
  for(const x of [830,1010])for(const z of [16,174])part(base,'PerimeterWall',[92,13,4],[x,6.5,z],P.concrete,'Concrete');
  for(const x of [891,949])for(const z of [16,174])part(base,'GateWing',[34,13,4],[x,6.5,z],P.concrete,'Concrete');
  for(const z of [16,174]) {part(base,'GatePost',[1.2,18,1.2],[874,9,z],P.steel,'Metal');part(base,'GatePost',[1.2,18,1.2],[966,9,z],P.steel,'Metal');}
  guardTower(base,800,34);guardTower(base,1040,34);guardTower(base,800,156);guardTower(base,1040,156);
  const command=enterable(world,'12_FrontierCommand',888,77,44,34,'#53685a');
  sign(command,'КОМАНДОВАНИЕ  /  МАЙОР МАКАР',[32,3,0.4],[888,11,59.7],'#e4d9b5',P.dark,Math.PI);
  part(command,'MapTable',[14,0.7,7],[888,3,82],P.wood,'WoodPlanks');part(command,'RadioRack',[4,7,3],[903,3.8,88],P.dark,'Metal');
  const clinic=enterable(world,'13_FrontierClinic',956,81,46,34,'#718078');
  sign(clinic,'ПОЛЕВОЙ ГОСПИТАЛЬ  /  НОРА',[34,3,0.4],[956,11,63.7],'#dce2d4','#44524e',Math.PI);
  for(const x of [946,958,970])part(clinic,'ClinicCot',[7,0.7,2.5],[x,1.5,88],'#d6dacf','Fabric');
  const supply=enterable(world,'14_GarrisonSupply',850,121,42,32,'#5d684f');
  sign(supply,'СКЛАД ГАРНИЗОНА  /  FELIX',[30,3,0.4],[850,11,104.7],P.amber,P.dark,Math.PI);
  for(const x of [840,850,860])for(const z of [120,128])part(supply,'SupplyShelf',[7,5,1.5],[x,2.7,z],P.steel,'Metal');
  // Civilian blocks use corrected non-overlapping facade shells from architecture.mjs.
  apartment(world,1085,105,54,56,48,'#6c7972',31);
  apartment(world,1085,315,58,62,61,'#73786b',32);
  apartment(world,805,315,56,60,54,'#68766e',33);
  const workshop=enterable(world,'15_FrontierWorkshop',1015,315,52,38,'#596b65');
  sign(workshop,'FORT-2  /  РЕМОНТ И БЕТОН',[38,3,0.4],[1015,11,295.7],P.amber,P.dark,Math.PI);
  for(const x of [1001,1015,1029])part(workshop,'Fabricator',[8,5,5],[x,2.7,320],P.steel,'DiamondPlate');

  for(const [x,z] of [[780,220],[850,220],[990,220],[1100,220],[920,350],[920,-120]])lamp(world.Roads,x,z);
  for(const [id,n,x,z,key] of [
    ['concrete',8,1000,302,'frontier_concrete'],['steel_plate',4,1028,324,'frontier_steel'],['glass',6,1070,287,'frontier_glass'],
    ['rifle_ammo',18,844,126,'frontier_ammo'],['battery',2,902,88,'frontier_battery'],['chemicals',4,969,88,'frontier_chemicals'],
    // High-risk second-city crates carry visibly better stacks than Meridian loot.
    ['steel_plate',8,760,270,'frontier_elite_steel'],['concrete',16,865,260,'frontier_elite_concrete'],
    ['glass',12,1010,255,'frontier_elite_glass'],['electronics',8,1125,185,'frontier_elite_electronics'],
    ['rifle_ammo',42,1090,155,'frontier_elite_rifle'],['shells',24,785,-145,'frontier_elite_shells'],
    ['medkit',3,975,66,'frontier_elite_medical'],['chemicals',10,1130,25,'frontier_elite_chemicals'],
    ['prism_shard',1,1118,-126,'frontier_prism_trace'],
  ])loot(world.LootSpawns,id,n,x,z,key,240);
  questCache(world,'HarborMedical1',[1065,1.2,96],'Контейнер хирургии');
  questCache(world,'HarborMedical2',[1098,1.2,300],'Контейнер антибиотиков');
  questCache(world,'HarborMedical3',[792,1.2,302],'Контейнер перевязки');
  questCache(world,'FrontierHomeSurvey1',[760,1.2,-178],'Журнал дома 1','frontier_homes','frontier_home','residential');
  questCache(world,'FrontierHomeSurvey2',[825,1.2,-178],'Журнал дома 2','frontier_homes','frontier_home','residential');
  questCache(world,'FrontierHomeSurvey3',[970,1.2,-178],'Журнал дома 3','frontier_homes','frontier_home','residential');
  questCache(world,'FrontierHomeSurvey4',[1110,1.2,-178],'Журнал дома 4','frontier_homes','frontier_home','residential');

  const hidden={Transparency:1,CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
  let i=0;for(const [x,z] of [[760,220],[820,255],[1045,220],[1120,245],[1105,340],[790,350],[920,-105],[1140,105]])
    part(world.ZombieSpawns,`HarborSpawn${++i}`,[1,1,1],[x,0.5,z],P.orange,'Plastic',0,hidden);

  const transport=world.Transports;
  helipad(transport,'City1Pad',118,-120,'МАРШРУТ 02  /  НОВЫЙ РУБЕЖ');
  part(transport,'City1Arrival',[1,1,1],[105,0.5,-120],P.orange,'Plastic',0,hidden);
  helicopter(transport,'City1Helicopter',118,-120,Math.PI/2);
  helipad(transport,'City2Pad',820,54,'МАРШРУТ 01  /  МЕРИДИАН');
  part(transport,'City2Arrival',[1,1,1],[837,0.5,54],P.orange,'Plastic',0,hidden);
  helicopter(transport,'City2Helicopter',820,54,-Math.PI/2);
}
