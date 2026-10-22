import { R, part, tree, folder, palette as P } from './primitives.mjs';
import {roadNetwork,SETTLEMENT_ROADS} from './roads.mjs';
export function landscape(world) {
  part(world.TerrainBase,'Ground',[1200,8,1200],[0,-4,-50],P.ground,'Ground');
  roadNetwork(world.Roads,'SettlementRoads',SETTLEMENT_ROADS,0);
  // Visible ridge limits and low-poly vegetation, instead of empty infinite terrain.
  let seed = 12021; const random = () => { seed=(seed*16807)%2147483647; return seed/2147483647; };
  for (let i=0;i<88;i++) {
    const side = i%2 ? -1 : 1;
    const x = side*(270+random()*190); const z = -470+random()*940;
    if(x>230&&x<325&&z>50&&z<185)continue; // Keep the fuel station and its driveway free of trees.
    tree(world.Forests,x,z,18+random()*15,i%3===0);
  }
  for(let i=0;i<36;i++) {
    const side=i%2===0?-1:1, x=side*(410+random()*140), z=-480+random()*970;
    const rock=part(world.Forests,'RockOutcrop',[18+random()*25,12+random()*22,16+random()*20],[x,5,z],'#5f6960','Rock',random());
    rock.Shape=R.PartType.Ball;
  }
  for(let i=0;i<12;i++) {
    const mound=part(world.Forests,'DistantHills',[170,85+random()*65,190],[-560+i*102,-20,535],'#53624e','Grass',random());
    mound.Shape=R.PartType.Ball;
  }
  // Hard edge stays behind the landscape. No accidental falling off the map.
  for(const x of [-585,585]) part(world.TerrainBase,'WorldBounds',[8,240,1200],[x,100,-50],P.dark,'Plastic',0,{Transparency:1});
  for(const z of [-635,535]) part(world.TerrainBase,'WorldBounds',[1200,240,8],[0,100,z],P.dark,'Plastic',0,{Transparency:1});
}
