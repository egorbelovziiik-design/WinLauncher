import { part, folder } from './primitives.mjs';

export const CITY_ROADS = [
  ...[-122, 122].map(x => ({axis:'z', center:x, from:-185, to:430, width:26})),
  {axis:'z', center:0, from:-25, to:430, width:26},
  ...[38,160,282,404].map(z => ({axis:'x', center:z, from:-232, to:232, width:26})),
];
export const SETTLEMENT_ROADS = [
  ...[-72,72].map(x => ({axis:'z',center:x,from:-635,to:-185,width:16})),
  ...[-185,-324,-466,-608].map(z => ({axis:'x',center:z,from:-232,to:232,width:16})),
  {axis:'z',center:0,from:-193,to:-175,width:18},
];
export const ROAD_SURFACE = 0.12;
export function subtractIntervals(start,end,cuts) {
  let pieces=[[start,end]];
  for(const [a,b] of [...cuts].sort((a,b)=>a[0]-b[0])) {
    pieces=pieces.flatMap(([lo,hi])=>b<=lo||a>=hi?[[lo,hi]]:[[lo,Math.min(a,hi)],[Math.max(b,lo),hi]].filter(([x,y])=>y-x>0.001));
  }
  return pieces;
}
export function rect(road,a,b,width=road.width,offset=0) {
  return road.axis==='z'?{x:road.center+offset,z:(a+b)/2,w:width,d:b-a}:{x:(a+b)/2,z:road.center+offset,w:b-a,d:width};
}
export function intersects(a,b,epsilon=0.0001) {
  return Math.min(a.x+a.w/2,b.x+b.w/2)-Math.max(a.x-a.w/2,b.x-b.w/2)>epsilon
    && Math.min(a.z+a.d/2,b.z+b.d/2)-Math.max(a.z-a.d/2,b.z-b.d/2)>epsilon;
}
export function planRoads(roads,sidewalk=5,context=roads) {
  const asphalt=[],pavement=[],curbs=[],junctions=[];
  for(const r of roads) {
    const cross=context.filter(c=>c.axis!==r.axis&&r.center>=c.from&&r.center<=c.to&&c.center>=r.from&&c.center<=r.to);
    // Horizontal pavement owns each crossing; vertical pavement stops at its edges.
    const cuts=r.axis==='z'?cross.map(c=>[c.center-c.width/2,c.center+c.width/2]):[];
    for(const [a,b] of subtractIntervals(r.from,r.to,cuts))asphalt.push(rect(r,a,b));
    if(!sidewalk)continue;
    for(const side of [-1,1]) {
      for(const [a,b] of subtractIntervals(r.from,r.to,cross.map(c=>[c.center-c.width/2-sidewalk,c.center+c.width/2+sidewalk])))
        pavement.push(rect(r,a,b,sidewalk,side*(r.width/2+sidewalk/2)));
      for(const [a,b] of subtractIntervals(r.from,r.to,cross.map(c=>[c.center-c.width/2-0.4,c.center+c.width/2+0.4])))
        curbs.push(rect(r,a,b,0.55,side*(r.width/2+0.275)));
    }
    if(r.axis==='z')for(const c of cross) {
      junctions.push({x:r.center,z:c.center,w:r.width,d:c.width});
      for(const sx of [-1,1])for(const sz of [-1,1])pavement.push({x:r.center+sx*(r.width/2+sidewalk/2),z:c.center+sz*(c.width/2+sidewalk/2),w:sidewalk,d:sidewalk});
    }
  }
  return {asphalt,pavement,curbs,junctions};
}
function tile(parent,name,r,height,top,tint,material,extra={}) {
  return part(parent,name,[r.w,height,r.d],[r.x,top-height/2,r.z],tint,material,0,extra);
}
export function roadNetwork(parent,name,roads,sidewalk=5) {
  const model=folder(parent,name,true); const plan=planRoads(roads,sidewalk,[...CITY_ROADS,...SETTLEMENT_ROADS]);
  for(const r of plan.asphalt)tile(model,'RoadSurface',r,0.24,ROAD_SURFACE,'#343938','Asphalt');
  for(const r of plan.pavement)tile(model,'Sidewalk',r,0.42,0.44,'#777b73','Concrete');
  for(const r of plan.curbs)tile(model,'Curb',r,0.5,0.52,'#8e9187','Concrete');
  const paint={CanCollide:false,CanTouch:false,CastShadow:false};
  for(const r of roads) {
    for(let at=r.from+9;at<r.to-5;at+=19) {
      const mark=rect(r,at,at+6,0.22);
      if(plan.junctions.some(j=>intersects({...j,w:j.w+15,d:j.d+15},mark)))continue;
      tile(model,'LanePaint',mark,0.024,ROAD_SURFACE+0.035,'#b5af8c','SmoothPlastic',paint);
    }
  }
  for(const j of plan.junctions) {
    if(j.w<24||j.d<24)continue;
    for(const side of [-1,1]) {
      for(let offset=-9;offset<=9;offset+=3) {
        tile(model,'CrosswalkPaint',{x:j.x+offset,z:j.z+side*(j.d/2+3),w:1.25,d:4.4},0.024,ROAD_SURFACE+0.035,'#b8b9a9','SmoothPlastic',paint);
      }
      tile(model,'StopLine',{x:j.x+side*(j.w/2+7),z:j.z+6.5,w:0.45,d:10},0.024,ROAD_SURFACE+0.035,'#b8b9a9','SmoothPlastic',paint);
    }
  }
  return {model,plan};
}
