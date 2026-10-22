import test from 'node:test';
import assert from 'node:assert/strict';
import {CITY_ROADS,SETTLEMENT_ROADS,planRoads,intersects,subtractIntervals} from '../tools/world/roads.mjs';
import {FRONTIER_JUNCTION_ROADS,FRONTIER_RESIDENTIAL_ROAD,FRONTIER_SIDEWALKS} from '../tools/world/secondCity.mjs';
import {WEST_ANNEX_ROADS,WEST_ANNEX_SIDEWALKS} from '../tools/world/city.mjs';

test('crossroads are paved once, not overlapping slabs at the same height',()=>{
  for(const roads of [CITY_ROADS,SETTLEMENT_ROADS]){
    const p=planRoads(roads,5);
    for(let i=0;i<p.asphalt.length;i++)for(let j=i+1;j<p.asphalt.length;j++)assert.equal(intersects(p.asphalt[i],p.asphalt[j]),false,JSON.stringify([p.asphalt[i],p.asphalt[j]]));
  }
});
test('no sidewalk crosses a driving lane',()=>{
  const p=planRoads(CITY_ROADS,5);
  for(const pavement of p.pavement)for(const lane of p.asphalt)assert.equal(intersects(pavement,lane),false,JSON.stringify([pavement,lane]));
});
test('curbs never run through intersection asphalt',()=>{
  const p=planRoads(CITY_ROADS,5);
  for(const curb of p.curbs)for(const lane of p.asphalt)assert.equal(intersects(curb,lane),false);
});
test('settlement roads do not cut through any private plot',()=>{
  const p=planRoads(SETTLEMENT_ROADS,0);
  for(const x of [-144,0,144])for(const z of [-252,-396,-540])for(const lane of p.asphalt)assert.equal(intersects({x,z,w:100,d:100},lane),false);
});
test('all twelve city crossroads have a continuous paved center',()=>{
  const p=planRoads(CITY_ROADS,5);assert.equal(p.junctions.length,12);
  for(const j of p.junctions)assert.equal(p.asphalt.filter(a=>intersects({x:j.x,z:j.z,w:0.1,d:0.1},a)).length,1);
});
test('subtraction clips merged cuts and never emits zero or negative rectangles',()=>{
  assert.deepEqual(subtractIntervals(0,100,[[-10,10],[5,20],[40,60],[90,120]]),[[20,40],[60,90]]);
  for(const r of [...planRoads(CITY_ROADS,5).asphalt,...planRoads(CITY_ROADS,5).pavement])assert.ok(r.w>0&&r.d>0);
});

test('city-to-settlement joins have no overlapping asphalt',()=>{
  const all=[...CITY_ROADS,...SETTLEMENT_ROADS];
  const paved=[...planRoads(CITY_ROADS,5,all).asphalt,...planRoads(SETTLEMENT_ROADS,0,all).asphalt];
  for(let i=0;i<paved.length;i++)for(let j=i+1;j<paved.length;j++)assert.equal(intersects(paved[i],paved[j]),false,JSON.stringify([paved[i],paved[j]]));
  for(const sidewalk of planRoads(CITY_ROADS,5,all).pavement)for(const road of paved)assert.equal(intersects(sidewalk,road),false);
});

test('New Frontier roads and sidewalks meet only at footprint edges',()=>{
  assert.equal(FRONTIER_JUNCTION_ROADS.length,5);
  const roads=[...FRONTIER_JUNCTION_ROADS,FRONTIER_RESIDENTIAL_ROAD];
  for(let i=0;i<roads.length;i++)for(let j=i+1;j<roads.length;j++)
    assert.equal(intersects(roads[i],roads[j]),false,JSON.stringify([roads[i],roads[j]]));
  for(const sidewalk of FRONTIER_SIDEWALKS)for(const road of roads)
    assert.equal(intersects(sidewalk,road),false,JSON.stringify([sidewalk,road]));
  const junction=FRONTIER_JUNCTION_ROADS.find(r=>r.name==='FrontierJunction');
  for(const point of [[906,220],[934,220],[920,206],[920,234]])
    assert.ok(FRONTIER_JUNCTION_ROADS.some(r=>point[0]>=r.x-r.w/2&&point[0]<=r.x+r.w/2&&point[1]>=r.z-r.d/2&&point[1]<=r.z+r.d/2),`unpaved junction edge ${point}`);
  assert.deepEqual({x:junction.x,z:junction.z,w:junction.w,d:junction.d},{x:920,z:220,w:28,d:28});
  assert.equal(FRONTIER_JUNCTION_ROADS.find(r=>r.name==='FrontierCrossRoadSouth').z-FRONTIER_JUNCTION_ROADS.find(r=>r.name==='FrontierCrossRoadSouth').d/2,FRONTIER_RESIDENTIAL_ROAD.z+FRONTIER_RESIDENTIAL_ROAD.d/2);
});

test('West Annex connector only touches its lane and sidewalks at footprint edges',()=>{
  assert.equal(intersects(WEST_ANNEX_ROADS[0],WEST_ANNEX_ROADS[1]),false);
  for(const sidewalk of WEST_ANNEX_SIDEWALKS)for(const road of WEST_ANNEX_ROADS)
    assert.equal(intersects(sidewalk,road),false,JSON.stringify([sidewalk,road]));
  assert.equal(WEST_ANNEX_ROADS[0].x+WEST_ANNEX_ROADS[0].w/2,WEST_ANNEX_ROADS[1].x-WEST_ANNEX_ROADS[1].w/2);
});
