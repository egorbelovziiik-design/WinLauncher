import {R,instance,part,orientedPart,folder,sign,loot,lamp,color,palette as P} from './primitives.mjs';

const trim={CanCollide:false,CanTouch:false,CanQuery:false};
function lantern(parent,x,y,z,range=25) {
  const cage=part(parent,'MineLamp',[1.1,0.55,0.7],[x,y,z],P.amber,'Neon',0,trim);
  const light=instance('PointLight');light.Color=color(P.amber);light.Brightness=1.25;light.Range=range;light.Shadows=true;light.Parent=cage;
  part(parent,'LampGuard',[1.45,0.12,0.85],[x,y+0.38,z],P.steel,'Metal',0,trim);
}
function rock(parent,x,y,z,size,tint='#4e514b') {
  const p=part(parent,'Rock',[size,size*0.72,size*0.9],[x,y,z],tint,'Rock',x*0.17);p.Shape=R.PartType.Ball;return p;
}
function support(parent,x,z) {
  for(const side of [-1,1])part(parent,'TunnelPost',[1.1,11,1.1],[x,5.5,z+side*9.7],P.wood,'WoodPlanks');
  part(parent,'TunnelBeam',[1.2,1.1,20.5],[x,10.7,z],P.wood,'WoodPlanks');
  for(const side of [-1,1])orientedPart(parent,'Brace',[0.55,7,0.55],[x,7.5,z+side*7.4],[side*0.63,0,0],P.wood,'WoodPlanks');
}
function railLine(parent,from,to,z) {
  part(parent,'MineRail',[to-from,0.18,0.24],[(from+to)/2,0.42,z],P.steel,'Metal',0,trim);
}
function cart(parent,x,z) {
  const m=folder(parent,'OreCart',true);
  part(m,'CartBody',[5.2,2.4,4],[x,2.3,z],P.rust,'Metal');
  part(m,'CartLip',[5.5,0.3,4.3],[x,3.65,z],P.steel,'Metal');
  for(const dx of [-1.8,1.8])for(const dz of [-1.7,1.7]) {
    const wheel=part(m,'CartWheel',[0.55,1.25,1.25],[x+dx,0.95,z+dz],'#242a2b','Metal');wheel.Shape=R.PartType.Cylinder;
  }
  for(const [dx,dz,s] of [[-1.4,-0.7,1.2],[0.2,0.4,1.5],[1.3,-0.4,1]])rock(m,x+dx,3.9,z+dz,s,'#766957');
}
export function mine(world) {
  const quarry=folder(world.Cities,'10_BlackVeinQuarry',true);
  // The open east road is a real continuation of Meridian's street at z=160.
  part(world.Roads,'EastQuarryRoad',[101,0.24,26],[282.5,0,160],'#343938','Asphalt');
  for(const z of [142.5,177.5])part(world.Roads,'EastQuarryWalk',[101,0.4,5],[282.5,0.2,z],'#777b73','Concrete');
  for(const z of [147,173])part(world.Roads,'EastQuarryCurb',[101,0.5,0.55],[282.5,0.25,z],'#8e9187','Concrete');
  for(let x=240;x<330;x+=19)part(world.Roads,'LanePaint',[6,0.03,0.22],[x,0.16,160],'#b5af8c','SmoothPlastic',0,trim);
  lamp(world.Roads,250,141);lamp(world.Roads,302,179);

  part(quarry,'QuarryApron',[70,0.32,80],[330,0.05,160],'#57594f','Slate');
  sign(quarry,'BLACK VEIN  /  MINE 14\nШАХТА «ЧЁРНЫЙ ПЛАСТ»',[50,5,0.7],[319,12,132],P.amber,P.dark);
  part(quarry,'SignPosts',[1,12,1],[296,6,132],P.steel,'Metal');part(quarry,'SignPosts',[1,12,1],[342,6,132],P.steel,'Metal');
  // Working-looking administration shed with an open, unobstructed doorway.
  const office=folder(quarry,'QuarryOffice',true);
  part(office,'Floor',[31,0.35,22],[292,0.2,190],P.dark,'Concrete');
  part(office,'Back',[31,9,0.7],[292,4.5,200.7],'#5e6b64','Metal');
  for(const x of [276.8,307.2])part(office,'Side',[0.7,9,22],[x,4.5,190],'#5e6b64','Metal');
  for(const x of [280.5,303.5])part(office,'Front',[7.5,9,0.7],[x,4.5,179.3],'#5e6b64','Metal');
  part(office,'DoorLintel',[15.5,2.2,0.7],[292,7.9,179.3],'#5e6b64','Metal');
  part(office,'Roof',[33,0.6,24],[292,9.5,190],P.dark,'Metal');
  sign(office,'КОНТОРА  /  СМЕНА B',[24,2.2,0.35],[292,7.2,178.9],'#d9d2b5',P.dark,Math.PI);
  part(office,'Desk',[10,0.7,3],[291,3,196],P.wood,'WoodPlanks');
  part(office,'Radio',[2.2,1.4,1.4],[294,4,196],P.steel,'Metal');
  loot(world.LootSpawns,'battery',1,283,194,'quarry_office',210);
  loot(world.LootSpawns,'painkillers',2,300,194,'quarry_office',180);

  // Crusher, conveyor and stored material make the expansion useful rather than decorative.
  const crusher=folder(quarry,'OreCrusher',true);
  part(crusher,'CrusherBase',[17,1.4,14],[354,0.7,124],P.concrete,'Concrete');
  part(crusher,'CrusherBody',[12,9,10],[354,5.8,124],P.rust,'Metal');
  part(crusher,'Hopper',[15,3,13],[354,11,124],P.steel,'Metal');
  for(const x of [363,375,387])part(crusher,'ConveyorSupport',[0.7,5,8],[x,2.5,124],P.steel,'Metal');
  orientedPart(crusher,'Conveyor',[38,1,7],[375,5,124],[0,0,-0.08],P.dark,'Metal');
  loot(world.LootSpawns,'scrap',8,348,116,'crusher_scrap',140);
  loot(world.LootSpawns,'coal',6,386,130,'crusher_coal',160);

  // Entrance portal and fully roofed, walkable tunnel. Nothing relies on teleportation.
  part(quarry,'PortalLeft',[4,14,5],[326,7,147],P.concrete,'Slate');
  part(quarry,'PortalRight',[4,14,5],[326,7,173],P.concrete,'Slate');
  part(quarry,'PortalTop',[6,4,31],[326,13,160],P.concrete,'Slate');
  sign(quarry,'ВЫРАБОТКА 14\nОПАСНО: ЗАРАЖЕНИЕ',[3.8,8,20],[322.9,8,160],P.orange,P.dark,Math.PI/2);
  const tunnel=folder(quarry,'TraversableMine',true);
  part(tunnel,'TunnelFloor',[82,0.5,23],[367,0.12,160],'#454640','Slate');
  part(tunnel,'NorthWall',[82,12,2],[367,6,148.5],'#4b4d48','Rock');
  part(tunnel,'SouthWall',[82,12,2],[367,6,171.5],'#4b4d48','Rock');
  part(tunnel,'TunnelRoof',[82,1.2,25],[367,12.1,160],'#41443f','Rock');
  for(const x of [334,352,370,388,404]) {support(tunnel,x,160);lantern(tunnel,x,9.1,x%36===10?169.8:150.2,23);}
  railLine(tunnel,328,408,157);railLine(tunnel,328,408,163);
  for(let x=331;x<=405;x+=6)part(tunnel,'RailTie',[1.1,0.16,8],[x,0.33,160],P.wood,'WoodPlanks',0,trim);
  cart(tunnel,365,160);
  for(const [x,z,s] of [[341,151,2.2],[348,169,1.5],[381,150,2],[396,170,1.7]])rock(tunnel,x,s*0.35,z,s,'#625d50');
  loot(world.LootSpawns,'ore',6,344,154,'mine_ore_1',150);
  loot(world.LootSpawns,'coal',6,384,166,'mine_coal_1',150);
  loot(world.LootSpawns,'chemicals',2,400,154,'mine_chemicals',190);

  // Boss chamber has a wide west opening continuous with the tunnel and enough cover to kite safely.
  const chamber=folder(quarry,'ForemanChamber',true);
  part(chamber,'ChamberFloor',[52,0.55,52],[432,0.14,160],'#41423d','Slate');
  part(chamber,'NorthWall',[52,14,2],[432,7,133.5],'#484a45','Rock');
  part(chamber,'SouthWall',[52,14,2],[432,7,186.5],'#484a45','Rock');
  part(chamber,'EastWall',[2,14,52],[458.5,7,160],'#484a45','Rock');
  part(chamber,'WestWallNorth',[2,14,16],[405.5,7,141.5],'#484a45','Rock');
  part(chamber,'WestWallSouth',[2,14,16],[405.5,7,178.5],'#484a45','Rock');
  part(chamber,'ChamberRoof',[54,1.2,54],[432,14.1,160],'#3d403b','Rock');
  for(const [x,z] of [[414,143],[450,143],[414,177],[450,177]]) {
    part(chamber,'SupportColumn',[2.4,13,2.4],[x,6.5,z],P.wood,'WoodPlanks');
    lantern(chamber,x,10.5,z,29);
  }
  for(const [x,z,s] of [[418,150,3.2],[447,169,3.7],[442,143,2.4],[416,174,2.2]])rock(chamber,x,s*0.36,z,s,'#685f4e');
  sign(chamber,'БРИГАДА 14\nЖЕТОНЫ СДАТЬ БРИГАДИРУ',[20,4,0.5],[456.8,8,160],'#c9b36f',P.dark,Math.PI/2);
  loot(world.LootSpawns,'ore',8,450,151,'boss_ore',210);
  loot(world.LootSpawns,'parts',4,447,177,'boss_parts',210);
  loot(world.LootSpawns,'shells',8,414,178,'boss_shells',210);

  const hidden={Transparency:1,CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
  for(const [i,x,z] of [[1,340,160],[2,378,160],[3,414,145],[4,449,176]])
    part(world.ZombieSpawns,`MineSpawn${i}`,[1,1,1],[x,0.5,z],P.orange,'Plastic',0,hidden);
  for(const [i,x,z] of [[1,420,148],[2,446,148],[3,446,174]])
    part(world.ZombieSpawns,`MineMinionSpawn${i}`,[1,1,1],[x,0.5,z],P.orange,'Plastic',0,hidden);
  part(world.ZombieSpawns,'MineBossSpawn',[1,1,1],[434,0.5,160],P.orange,'Plastic',0,hidden);
}
