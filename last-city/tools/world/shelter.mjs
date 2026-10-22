import { R, part, folder, sign, lamp, crate, loot, color, vec, cf, palette as P } from './primitives.mjs';
export function shelter(world) {
  const m = folder(world.Cities, '01_MeridianShelter', true);
  part(m, 'ConcreteSquare', [156, 0.3, 140], [0, 0.05, -100], '#515957', 'Concrete');
  for (const x of [-80, 80]) {
    part(m, 'Perimeter', [3, 13, 150], [x, 6.5, -100], P.concrete);
    part(m, 'Coping', [3.5, 0.8, 150], [x, 13, -100], P.dark, 'Metal');
  }
  for (const z of [-175, -25]) for (const x of [-47, 47]) {
    part(m, 'GateWall', [66, 13, 3], [x, 6.5, z], P.concrete);
    part(m, 'GateCap', [66, 0.8, 3.5], [x, 13, z], P.dark, 'Metal');
  }
  sign(m, 'MERIDIAN\nSURVIVOR OUTPOST  /  07', [28, 5, 1], [0, 17, -25], '#e0d6bb', P.dark, Math.PI);
  sign(m, 'ЗА ВОРОТАМИ — ЗАРАЖЁННЫЕ', [25, 2, 0.5], [0, 11.5, -25], P.orange, P.dark);
  for (const x of [-14, 14]) part(m, 'GatePost', [1, 18, 1], [x, 9, -25], P.steel, 'Metal');
  for (const x of [-67, 67]) {
    part(m, 'TowerDeck', [13, 1, 12], [x, 17, -36], P.steel, 'Metal');
    for (const dx of [-5, 5]) for (const dz of [-4, 4]) part(m, 'TowerSupport', [0.7, 17, 0.7], [x + dx, 8.5, -36 + dz], P.steel, 'Metal');
    part(m, 'TowerRoof', [14, 0.5, 14], [x, 24, -36], P.dark, 'Metal');
    for (const dx of [-5, 5]) part(m, 'TowerRoofSupport', [0.4, 7, 0.4], [x + dx, 20.5, -36], P.dark, 'Metal');
    part(m, 'FrontRail', [13, 2, 0.4], [x, 18.5, -30], P.steel, 'Metal');
  }
  // Open-sided shelters: navigable interiors, no sealed decorative "doors".
  for (const [x, z, w, label, tint] of [[-42, -74, 28, 'КОМАНДОВАНИЕ', '#4d6358'], [48, -111, 31, 'СНАБЖЕНИЕ', '#8a694b'], [-48, -136, 34, 'ИНЖЕНЕРНАЯ', '#506c76']]) {
    const b = folder(m, label, true);
    part(b, 'Floor', [w, 0.3, 23], [x, 0.2, z], P.dark);
    part(b, 'Back', [w, 10, 0.6], [x, 5, z - 11], tint, 'Metal');
    for (const side of [-1, 1]) part(b, 'Side', [0.6, 10, 23], [x + side * w / 2, 5, z], tint, 'Metal');
    part(b, 'Roof', [w + 2, 0.6, 25], [x, 10.5, z], tint, 'Metal');
    sign(b, label, [w - 1, 2.2, 0.5], [x, 9, z + 11.7], '#dfd8bd', P.dark, Math.PI);
    part(b, 'Table', [11, 0.8, 3.5], [x, 3.2, z - 4], P.wood, 'Wood');
    for (const dx of [-4, 4]) part(b, 'TableLeg', [0.5, 3, 2.5], [x + dx, 1.5, z - 4], P.dark, 'Metal');
    crate(b, x + 9, 0, z - 6);
  }
  sign(m, 'ЖИЛЫЕ УЧАСТКИ  ↓\nВАШ НОМЕР — НА КАРТЕ [M]', [25, 3, 0.6], [0, 10, -168], P.amber, P.dark);
  for (const [x, z] of [[-22,-105],[22,-52],[65,-147],[-65,-147]]) lamp(m,x,z);
  for (const x of [-10,10]) part(m,'Bench',[10,1,2.5],[x,1.3,-124],P.wood,'WoodPlanks');
  loot(world.LootSpawns, 'food', 2, 8, -94, 'shelter', 150);
  loot(world.LootSpawns, 'water', 2, 14, -94, 'shelter', 150);
  const spawn = new R.SpawnLocation(); spawn.Name = 'ShelterSpawn'; spawn.Size = vec(6, 0.2, 6); spawn.CFrame = cf(0, 0.2, -106); spawn.Anchored = true; spawn.Transparency = 1; spawn.Neutral = true; spawn.Duration = 3; spawn.Parent = m;
  const plots = [[-144,-252],[0,-252],[144,-252],[-144,-396],[0,-396],[144,-396]];
  plots.forEach(([x,z],i)=>{
    const p = folder(world.Plots,`Plot${i+1}`,true);
    part(p,'PlotGround',[100,0.2,100],[x,-0.05,z],'#4b5247','Ground');
    for (const dx of [-50,50]) part(p,'Boundary',[0.5,0.2,100],[x+dx,0.2,z],'#b79858','Concrete');
    for (const dz of [-50,50]) part(p,'Boundary',[100,0.2,0.5],[x,0.2,z+dz],'#b79858','Concrete');
    for (const dx of [-50,50]) for (const dz of [-50,50]) part(p,'Marker',[1,2,1],[x+dx,1,z+dz],P.orange,'Metal');
    const terminal = part(p,'Terminal',[4,4,3],[x+6,2,z+38],P.dark,'Metal');
    part(p,'Screen',[3,2,0.2],[x+6,2.7,z+36.4],P.teal,'Neon');
    const gui = new R.BillboardGui(); gui.Name = 'OwnerGui'; gui.Size = new R.UDim2(new R.UDim(0,220),new R.UDim(0,50)); gui.StudsOffset = vec(0,4,0); gui.MaxDistance = 95; gui.Adornee = terminal; gui.Parent = terminal;
    const text = new R.TextLabel(); text.Name = 'Label'; text.Size = new R.UDim2(new R.UDim(1,0),new R.UDim(1,0)); text.BackgroundTransparency = 1; text.Text = `СВОБОДНЫЙ УЧАСТОК\n${i+1}`; text.TextColor3 = color(P.amber); text.Font = R.Font.GothamBold; text.TextSize = 14; text.TextStrokeTransparency = 0.4; text.Parent = gui;
    part(p,'TentBack',[12,6,0.5],[x-22,3,z+39], '#65715b','Fabric');
    part(p,'TentRoof',[13,0.5,10],[x-22,6,z+35], '#65715b','Fabric');
    part(p,'TentPost',[0.4,6,0.4],[x-28,3,z+30],P.dark,'Metal');
    part(p,'TentPost',[0.4,6,0.4],[x-16,3,z+30],P.dark,'Metal');
  });
}
