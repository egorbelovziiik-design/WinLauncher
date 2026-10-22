import { part, tree, folder, palette as P } from './primitives.mjs';
export function landscape(world) {
  part(world.TerrainBase,'Ground',[1200,8,1200],[0,-4,-50],P.ground,'Ground');
  const road = folder(world.Roads,'SettlementRoads',true);
  part(road,'Main',[18,0.12,290],[0,0,-310],'#414744','Asphalt');
  for (const z of [-185,-324,-466]) part(road,'Cross',[440,0.15,16],[0,0,z],'#414744','Asphalt');
  // Visible ridge limits and low-poly vegetation, instead of empty infinite terrain.
  let seed = 12021; const random = () => { seed=(seed*16807)%2147483647; return seed/2147483647; };
  for (let i=0;i<62;i++) {
    const side = i%2 ? -1 : 1;
    const x = side*(270+random()*190); const z = -470+random()*940;
    tree(world.Forests,x,z,16+random()*16,true);
  }
  for(let i=0;i<22;i++) {
    const x=-550+i*53;
    part(world.Forests,'NorthernRidge',[85,55+random()*70,100],[x,25,490+random()*40],'#4c5954','Slate',random());
  }
  for(const x of [-540,540]) for(let z=-520;z<500;z+=80) part(world.Forests,'BoundaryRidge',[100,60,110],[x,25,z],'#4c5954','Slate',0.2);
  part(world.Forests,'SouthernRidge',[1100,60,90],[0,25,-550],'#4c5954','Slate');
  // Hard edge stays behind the landscape. No accidental falling off the map.
  for(const x of [-585,585]) part(world.TerrainBase,'WorldBounds',[8,240,1200],[x,100,-50],P.dark,'Plastic',0,{Transparency:1});
  for(const z of [-635,535]) part(world.TerrainBase,'WorldBounds',[1200,240,8],[0,100,z],P.dark,'Plastic',0,{Transparency:1});
}
