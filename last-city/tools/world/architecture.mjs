import {R,part,folder,sign,instance,cf,vec,color,loot,palette as P} from './primitives.mjs';
const trim='#565e5b',frame='#343f40',glass='#253c42';
function decorative(parent,name,size,at,col,mat='Concrete',yaw=0) {
  return part(parent,name,size,at,col,mat,yaw,{CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false});
}
export function apartment(world,x,z,w,d,h,tint,index) {
  const b=folder(world.Cities,`RuinedBlock_${index}`,true);
  part(b,'Foundation',[w+2,0.7,d+2],[x,0.35,z],'#666b65','Concrete');
  // The upper shell begins exactly above the ground storey. Earlier full-height shells
  // overlapped this facade volume and could shimmer like broken/missing textures.
  part(b,'Structure',[w,h-8,d],[x,(h+8)/2,z],tint,index%3===0?'Brick':'Concrete');
  part(b,'GroundStorey',[w,8,d],[x,4,z],'#505a57','Concrete');
  part(b,'FoundationSkirt',[w+1,1.1,d+1],[x,0.58,z],'#59615c','Slate');
  part(b,'RoofDeck',[w+1,0.7,d+1],[x,h+0.2,z],'#414b48','Concrete');
  for(const side of [-1,1]) {
    part(b,'RoofParapet',[w+1,2.4,0.55],[x,h+1.4,z+side*d/2],trim);
    part(b,'RoofParapet',[0.55,2.4,d],[x+side*w/2,h+1.4,z],trim);
  }
  // Four-sided windows sit outside the shell with a real sill and lintel.
  for(const face of [0,1,2,3]) {
    const alongX=face<2, side=face%2?1:-1, length=alongX?w:d, depth=alongX?d:w;
    for(let y=12;y<h-4;y+=11) {
      for(let a=-length/2+6;a<length/2-4;a+=15) {
        const at=alongX?[x+a,y,z+side*(depth/2+0.13)]:[x+side*(depth/2+0.13),y,z+a];
        const yaw=alongX?0:Math.PI/2; const seed=index*13+face*7+Math.round(y+a);
        decorative(b,'WindowRecess',[5.3,6,0.12],at,frame,'Metal',yaw);
        const outside=[at[0]+(alongX?0:side*0.13),at[1],at[2]+(alongX?side*0.13:0)];
        const pane=decorative(b,'WindowPane',[4.6,5.2,0.08],outside,seed%11===0?'#8d8160':glass,seed%11===0?'SmoothPlastic':'Glass',yaw);
        pane.Reflectance=0.13;
        decorative(b,'WindowSill',[5.7,0.34,0.72],[at[0],y-3.05,at[2]],'#97988b','Concrete',yaw);
        if(seed%2===0)decorative(b,'Mullion',[0.13,5.3,0.19],outside,'#65706a','Metal',yaw);
        if(seed%9===0) {
          decorative(b,'BoardedWindow',[4.9,0.8,0.28],[outside[0],y+0.8,outside[2]],'#776a51','WoodPlanks',yaw);
          decorative(b,'BoardedWindow',[4.9,0.8,0.28],[outside[0],y-0.8,outside[2]],'#665c49','WoodPlanks',yaw);
        }
        if(seed%17===0)decorative(b,'RainStain',[3.4,2.1,0.05],[at[0],y-4.25,at[2]],'#515b53','Slate',yaw);
      }
    }
    for(let y=9;y<h;y+=18)decorative(b,'StoreyJoint',alongX?[w+0.2,0.18,0.1]:[0.1,0.18,d+0.2],alongX?[x,y,z+side*(d/2+0.16)]:[x+side*(w/2+0.16),y,z],trim);
    if(face<2)for(const dx of [-w/2+1,w/2-1])part(b,'FacadePier',[1.3,h,0.5],[x+dx,h/2,z+side*(d/2+0.2)],'#83877a');
  }
  part(b,'Entrance',[6,7,0.25],[x,3.5,z-d/2-0.2],'#202c2d','Metal');
  part(b,'EntranceAwning',[9,0.45,4],[x,7.4,z-d/2-1.7],trim,'Metal');
  sign(b,`BLOCK ${String(index).padStart(2,'0')}  /  EVACUATED`,[12,1.8,0.3],[x,9,z-d/2-0.28],'#a9afa0','#333e3d');
  for(const offset of [-11,8]) {
    part(b,'HVAC',[8,4,7],[x+offset,h+2.7,z+7],P.steel,'Metal');
    for(let slat=0;slat<5;slat++)decorative(b,'VentGrille',[6.8,0.1,0.08],[x+offset,h+1.4+slat*0.65,z+3.45],'#273535','Metal');
  }
  part(b,'WaterTankBase',[9,1,9],[x-12,h+0.9,z-12],trim,'Metal');
  const tank=part(b,'WaterTank',[7,7,7],[x-12,h+4.8,z-12],'#65716a','Metal');tank.Shape=R.PartType.Cylinder; tank.CFrame=new R.CFrame(vec(x-12,h+4.8,z-12),[0,-1,0,1,0,0,0,0,1]);
  // Stair enclosure, aerial and collapsed parapet replace random solid roof blocks.
  part(b,'StairEnclosure',[10,6,12],[x+12,h+3.5,z-10],tint);
  part(b,'Aerial',[0.12,9,0.12],[x+12,h+10.5,z-10],'#687b7d','Metal');
  part(b,'AerialCross',[5,0.12,0.12],[x+12,h+13,z-10],'#687b7d','Metal');
  if(index===1||index===3) {
    const ladder=instance('TrussPart'); ladder.Name='RoofAccessLadder'; ladder.Size=vec(2,h+6,2);
    ladder.CFrame=cf(x+w/2+2.4,(h+6)/2,z+9);ladder.Anchored=true;ladder.Color3uint8=color('#655e4c');ladder.Material=R.Material.Metal;ladder.Parent=b;
    sign(b,'ROOF ACCESS  ↑',[7,2,0.3],[x+w/2+2.4,4,z+6],'#c8c3a4','#3b4946');
    const cache=loot(world.LootSpawns,'ammo',20,x-5,z-6,`roof_${index}`,180);cache.CFrame=cf(x-5,h+1.7,z-6);
  }
  for(let floor=21;floor<h-10;floor+=27) {
    part(b,'Balcony',[11,0.45,4.5],[x-w/2+10,floor,z-d/2-2],'#727a6e','Concrete');
    part(b,'BalconyRail',[11,0.13,0.13],[x-w/2+10,floor+2.7,z-d/2-4.2],trim,'Metal');
    for(const dx of [-4.8,-1.6,1.6,4.8])part(b,'RailPost',[0.12,2.7,0.12],[x-w/2+10+dx,floor+1.4,z-d/2-4.2],trim,'Metal');
  }
  if(index%2===0)for(let i=0;i<4;i++)part(b,'FallenMasonry',[3+i,0.7+i*0.3,2+i],[x-w/2+4+i*3,0.6,z-d/2-3],'#787a6a','Slate',i*0.51);
  return b;
}
// Low-part, genuinely enterable residential home. Local offsets are rotated so rows
// can face their access lane without duplicating geometry or sealing the doorway.
export function compactHome(world,name,x,z,yaw=0,theme='#6f796d') {
  const m=folder(world.Cities,name,true); const c=Math.cos(yaw),s=Math.sin(yaw);
  const at=(dx,y,dz)=>[x+dx*c+dz*s,y,z-dx*s+dz*c];
  const p=(n,size,dx,y,dz,col=theme,mat='Brick',extra={})=>part(m,n,size,at(dx,y,dz),col,mat,yaw,extra);
  const trim={CanCollide:false,CanTouch:false,CanQuery:false,CastShadow:false};
  p('HomeFloor',[30,0.45,25],0,0.23,0,'#676d66','Concrete');
  p('BackWall',[30,11,0.65],0,5.5,12.2);p('SideWall',[0.65,11,25],-14.7,5.5,0);p('SideWall',[0.65,11,25],14.7,5.5,0);
  p('FrontWing',[10.8,11,0.65],-9.6,5.5,-12.2);p('FrontWing',[10.8,11,0.65],9.6,5.5,-12.2);
  p('DoorLintel',[8.4,2.5,0.65],0,9.75,-12.2);p('Roof',[31,0.55,26],0,11.25,0,'#46514e','Metal');
  p('Porch',[10,0.35,4],0,0.3,-14,'#73786f','Concrete');
  for(const dx of [-9,9])p('Window',[5.2,4.2,0.12],dx,6,-12.58,'#36515a','Glass',trim);
  p('BedFrame',[7,0.8,3.2],-8,1.15,7,'#766348','WoodPlanks');p('BedRoll',[6.5,0.55,2.8],-8,1.85,7,'#899181','Fabric');
  p('Table',[5,0.55,3],7,2.25,6,'#795f42','WoodPlanks');p('TableBase',[0.6,3.4,0.6],7,1.25,6,'#4b5350','Metal');
  const lamp=p('CeilingLamp',[2.5,0.18,0.7],0,9.9,1,'#d4c892','Neon',trim);
  const light=instance('PointLight');light.Range=19;light.Brightness=0.65;light.Color=color('#e6d9aa');light.Shadows=false;light.Parent=lamp;
  const plaque=sign(m,'ЖИЛОЙ ДОМ  /  SAFE ROOM',[8,1.4,0.2],at(0,8.9,-12.6),'#d9c788','#34403e',yaw);
  plaque.CanCollide=false;plaque.CanTouch=false;plaque.CanQuery=false;plaque.CastShadow=false;
  return m;
}

export function enterable(world,name,x,z,w,d,theme,roof=true) {
  const m=folder(world.Cities,name,true);
  part(m,'Floor',[w,0.44,d],[x,0.22,z],'#6b7069','Concrete');
  part(m,'BackWall',[w,13,0.65],[x,6.5,z+d/2],theme,'Brick');
  for(const side of [-1,1])part(m,'SideWall',[0.65,13,d],[x+side*w/2,6.5,z],theme,'Brick');
  const wing=(w-10)/2;
  for(const side of [-1,1])part(m,'FrontWing',[wing,13,0.65],[x+side*(5+wing/2),6.5,z-d/2],theme,'Brick');
  part(m,'OpenDoorLintel',[10,3,0.65],[x,11.5,z-d/2],theme,'Concrete');
  if(roof)part(m,'Roof',[w+1,0.55,d+1],[x,13.25,z],'#4d5854','Metal');
  for(const side of [-1,1]) {
    part(m,'DoorFrame',[0.35,10,0.6],[x+side*5,5,z-d/2-0.2],'#acafa0','Metal');
    decorative(m,'ShopWindow',[9,5,0.15],[x+side*(w/2-8),7.5,z-d/2-0.4],glass,'Glass');
  }
  for(const [dx,dz] of w>45?[[-14,-10],[14,-10],[0,15]]:[[0,0]]) {
    const lens=part(m,'InteriorStrip',[7,0.12,0.45],[x+dx,11.8,z+dz],'#bfc8ac','Neon');
    const light=instance('PointLight');light.Range=26;light.Brightness=0.8;light.Color=color('#dbe4cc');light.Shadows=false;light.Parent=lens;
  }
  return m;
}
