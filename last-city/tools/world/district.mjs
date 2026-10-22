import {R,part,orientedPart,folder,sign,loot,value,instance,color,bus,barricade,tree,palette as P} from './primitives.mjs';

const noCollision={CanCollide:false,CanTouch:false,CanQuery:false};
function tent(parent,x,z,yaw,tint) {
  const m=folder(parent,'ReliefTent',true);
  part(m,'TentFloor',[13,0.25,12],[x,0.13,z],'#555b50','Fabric',yaw);
  orientedPart(m,'TentRoofLeft',[7.3,0.36,12.5],[x-2.65,4,z],[0,yaw,0.69],tint,'Fabric');
  orientedPart(m,'TentRoofRight',[7.3,0.36,12.5],[x+2.65,4,z],[0,yaw,-0.69],tint,'Fabric');
  for(const dx of [-6,6])for(const dz of [-5.5,5.5])part(m,'TentPole',[0.32,6.2,0.32],[x+dx,3.1,z+dz],P.dark,'Metal',yaw);
  part(m,'RidgePole',[0.3,0.3,12.8],[x,6.15,z],P.dark,'Metal',yaw);
  part(m,'Bedroll',[4.8,0.55,2.2],[x-2.4,0.55,z+2.8],'#596a67','Fabric',yaw);
  part(m,'SupplyTable',[5.5,0.45,2.3],[x+2.7,2.6,z+3],P.wood,'WoodPlanks',yaw);
  for(const dx of [0.7,4.7])part(m,'TableLeg',[0.3,2.5,1.8],[x+dx,1.3,z+3],P.dark,'Metal',yaw);
  return m;
}
function questCache(world,name,at,label) {
  const p=part(world.LootSpawns,name,[2.7,1.6,2.3],at,'#6f7e68','Metal');
  value(p,'QuestId','relief_recon');value(p,'QuestEvent','relief');value(p,'QuestTarget','camp');value(p,'QuestLabel',label);
  part(p,'ReliefMark',[1.8,0.2,0.08],[at[0],at[1]+0.35,at[2]-1.19],P.orange,'Neon',0,noCollision);
}
function firePit(parent,x,z) {
  const base=part(parent,'CampfireBase',[3.8,0.35,3.8],[x,0.18,z],'#373b37','Slate');base.Shape=R.PartType.Cylinder;
  for(const yaw of [0,Math.PI/2])part(parent,'Firewood',[0.55,0.55,3.2],[x,0.65,z],P.wood,'Wood',yaw);
  const ember=part(parent,'CampfireEmbers',[1.4,0.22,1.4],[x,0.62,z],P.orange,'Neon',0,noCollision);
  const flame=instance('Fire');flame.Color=color('#f59e42');flame.SecondaryColor=color('#d84b24');flame.Size=2.4;flame.Heatxml=3;flame.Parent=ember;
}
function streetFurniture(parent) {
  for(const [x,z,yaw] of [[-103,61,0],[-103,183,0],[-103,305,0],[103,61,Math.PI],[103,183,Math.PI],[103,305,Math.PI]]) {
    part(parent,'BusStopBench',[8,0.6,2],[x,1.8,z],P.wood,'WoodPlanks',yaw);
    for(const dx of [-3,3])part(parent,'BenchLeg',[0.45,1.5,1.6],[x+dx,0.8,z],P.dark,'Metal',yaw);
  }
  for(const [x,z] of [[-18,75],[18,145],[-18,267],[18,389]]) {
    const hydrant=part(parent,'FireHydrant',[1.1,2.4,1.1],[x,1.2,z],'#9a4f3e','Metal');hydrant.Shape=R.PartType.Cylinder;
    part(parent,'HydrantCap',[1.4,0.4,1.4],[x,2.5,z],'#b46a50','Metal');
  }
}
export function district(world) {
  // A lived-in, playable transition between the shelter gate and the city grid.
  const camp=folder(world.Cities,'08_MeridianReliefCamp',true);
  part(camp,'CampGround',[83,0.28,39],[-62,0.08,5],'#545c50','Ground');
  part(camp,'GravelEntry',[16,0.3,17],[-62,0.14,-16],'#77786d','Pebble');
  sign(camp,'MERIDIAN RELIEF POINT\nЭВАКУАЦИЯ  /  СЕКТОР C',[58,4,0.5],[-62,10.5,23.5],'#d8d4b5','#45564c',Math.PI);
  for(const [x,z,yaw,tint] of [[-89,3,0.05,'#68745f'],[-63,5,-0.04,'#5b706c'],[-37,3,0.08,'#766b55']])tent(camp,x,z,yaw,tint);
  firePit(camp,-76,-7);firePit(camp,-48,-7);
  for(const x of [-100,-24]) {part(camp,'CampMast',[0.5,9,0.5],[x,4.5,15],P.steel,'Metal');part(camp,'WorkLight',[2.4,0.45,1.6],[x,8.6,15],P.amber,'Neon');}
  for(const [x,z] of [[-99,-8],[-27,-8],[-99,18],[-27,18]])part(camp,'SandbagStack',[7,1.4,2.4],[x,0.7,z],'#756e58','Fabric',0,{CanCollide:false});
  questCache(world,'ReliefRoster',[-94,1.25,7],'Список эвакуации');
  questCache(world,'ReliefTransmitter',[-63,1.25,8],'Аварийный передатчик');
  questCache(world,'ReliefTags',[-32,1.25,6],'Жетоны спасателей');
  loot(world.LootSpawns,'cloth',5,-92,-5,'relief_cloth',130);loot(world.LootSpawns,'food',3,-63,-6,'relief_food',140);loot(world.LootSpawns,'water',3,-34,-5,'relief_water',140);
  bus(world.Cities,62,5,Math.PI/2,'#66725f');
  const convoy=folder(world.Cities,'09_EvacuationConvoy',true);
  sign(convoy,'EVAC  07\nНЕ ПЕРЕКРЫВАТЬ',[15,3,0.4],[62,8.8,20],'#d6c789','#3b4844',Math.PI);
  for(const [x,z] of [[37,-11],[87,-11],[38,20],[88,20]])part(convoy,'TrafficCone',[1.2,2.2,1.2],[x,1.1,z],P.orange,'Plastic',0,{CanCollide:false});

  // Every former road cut-off is now an intentional quarantine closure with physical cover.
  for(const [x,z] of [[-235,38],[-235,160],[-235,282],[-235,404],[235,38],[235,282],[235,404]]) {
    barricade(world.MilitaryBases,x,z,Math.PI/2);
    part(world.MilitaryBases,'ClosureRubble',[5.5,1.2,4],[x+(x<0?4:-4),0.6,z+7],'#686b62','Slate',0.35,{CanCollide:false});
  }
  for(const x of [-72,72]) {
    part(world.Roads,'SouthGravelTransition',[14,0.22,58],[x,0.05,-503],'#55584e','Pebble');
    barricade(world.MilitaryBases,x,-531,0);
  }
  sign(world.MilitaryBases,'ЮЖНЫЙ ПЕРИМЕТР\nДАЛЬШЕ НЕТ БЕЗОПАСНОГО МАРШРУТА',[52,6,0.8],[0,12,-536],P.amber,P.dark);
  for(const x of [-112,-35,35,112])tree(world.Forests,x,-548,19+(Math.abs(x)%7),true);
  streetFurniture(world.Cities);
}
