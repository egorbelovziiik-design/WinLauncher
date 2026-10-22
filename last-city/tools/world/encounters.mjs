import {part,folder,sign,instance,palette as P,color} from './primitives.mjs';

// Compact authored arena: readable boundary/cover plus a hidden server spawn marker.
// Bosses select one of these candidates per lifecycle; the other sites remain useful
// cover rather than fake encounter buttons.
export function bossSite(world,prefix,index,x,z,label) {
  const m=folder(world.Cities,`BossSite_${prefix}_${index}`,true);
  part(m,'ScorchedGround',[34,0.22,34],[x,0.12,z],'#45433d','Slate');
  for(const [dx,dz,w,d] of [[0,-16,34,1],[0,16,34,1],[-16,0,1,34],[16,0,1,34]])
    part(m,'WarningRail',[w,0.22,d],[x+dx,0.42,z+dz],P.orange,'Neon',0,{CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false});
  for(const [dx,dz,yaw] of [[-10,-7,0.28],[9,5,-0.34],[0,12,0]]) {
    part(m,'ArenaCover',[9,3.6,1.6],[x+dx,1.8,z+dz],'#5d625d','Concrete',yaw);
    part(m,'CoverStripe',[8.4,0.3,0.12],[x+dx,2.7,z+dz-0.85],P.orange,'Neon',yaw,{CanCollide:false,CanTouch:false,CanQuery:false});
  }
  const beacon=part(m,'ThreatBeacon',[1,7,1],[x,3.5,z],P.dark,'Metal');
  const light=instance('PointLight');light.Color=color('#e0523f');light.Range=24;light.Brightness=1.2;light.Shadows=false;light.Parent=beacon;
  const plaque=sign(m,label,[20,2.4,0.3],[x,4.2,z-16.7],P.orange,P.dark);
  plaque.CanCollide=false;plaque.CanTouch=false;plaque.CanQuery=false;
  const hidden={Transparency:1,CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
  part(world.ZombieSpawns,`${prefix}BossSpawn${index}`,[1,1,1],[x,0.5,z],P.orange,'Plastic',0,hidden);
  return m;
}
