import { R, part, wedge, tree, palette as P } from './primitives.mjs';
import {roadNetwork,SETTLEMENT_ROADS} from './roads.mjs';
export function landscape(world) {
  // A lowered far plane and layered ridges hide the old square void without extending the playable bounds.
  part(world.TerrainBase,'FarGround',[1900,12,1900],[0,-16,-40],'#303934','Ground');
  part(world.TerrainBase,'Ground',[1200,8,1200],[0,-4,-50],P.ground,'Ground');
  roadNetwork(world.Roads,'SettlementRoads',SETTLEMENT_ROADS,0);
  let seed = 12021; const random = () => { seed=(seed*16807)%2147483647; return seed/2147483647; };
  const reserved=(x,z)=>(x>230&&x<325&&z>45&&z<190)
    ||(x>-365&&x<-235&&z>45&&z<255)
    ||(Math.abs(x+455)<38&&(Math.abs(z-40)<38||Math.abs(z-325)<38))
    ||(Math.abs(x-470)<38&&Math.abs(z-340)<38);
  for (let i=0;i<92;i++) {
    const side = i%2 ? -1 : 1;
    const x = side*(270+random()*190); const z = -485+random()*965;
    if(reserved(x,z))continue;
    tree(world.Forests,x,z,17+random()*15,i%3===0);
  }
  for(let i=0;i<34;i++) {
    const side=i%2===0?-1:1, x=side*(415+random()*135), z=-485+random()*960;
    if(reserved(x,z))continue;
    const rock=part(world.Forests,'RockOutcrop',[14+random()*24,9+random()*18,13+random()*20],[x,3.8,z],'#5f6960','Rock',random());
    rock.Shape=R.PartType.Ball;
  }
  // Angular, staggered ridges replace the conspicuous row of giant spherical hills.
  for(let i=0;i<19;i++) {
    const x=-585+i*65+random()*14, h=42+random()*52, d=72+random()*58;
    wedge(world.Forests,'NorthRidge',[82+random()*32,h,d],[x,h/2-8,510+random()*38],i%3===0?'#4a5a48':'#53624e','Rock',i%2===0?0:Math.PI);
  }
  for(let i=0;i<14;i++) {
    const x=-655+i*98+random()*18,h=55+random()*70;
    wedge(world.Forests,'FarRidge',[125,h,120],[x,h/2-12,600+random()*35],'#445244','Rock',i%2===0?0:Math.PI);
  }
  for(const side of [-1,1])for(let i=0;i<11;i++) {
    const z=-525+i*98+random()*20,h=32+random()*48;
    wedge(world.Forests,'SideRidge',[86,h,105],[side*(555+random()*24),h/2-9,z],i%2?'#53604f':'#48564a','Rock',side>0?Math.PI/2:-Math.PI/2);
  }
  // Sparse dead trunks break the horizon silhouette without expensive foliage clusters.
  for(let i=0;i<18;i++) {
    const side=i%2?-1:1,x=side*(470+random()*80),z=-440+random()*850,h=9+random()*12;
    const trunk=part(world.Forests,'DeadTree',[h,0.65,0.65],[x,h/2,z],'#514b3e','Wood',0.2+random());
    trunk.Shape=R.PartType.Cylinder;trunk.CFrame=new R.CFrame(trunk.CFrame.Position,[0,-1,0,1,0,0,0,0,1]);
  }
  // Hard collision stays invisible and behind the landscape. Players cannot fall from the place.
  for(const x of [-585,585]) part(world.TerrainBase,'WorldBounds',[8,240,1200],[x,100,-50],P.dark,'Plastic',0,{Transparency:1});
  for(const z of [-635,535]) part(world.TerrainBase,'WorldBounds',[1200,240,8],[0,100,z],P.dark,'Plastic',0,{Transparency:1});
}
