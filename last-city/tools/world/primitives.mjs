import * as R from 'rbxm-parser';
import { instance, boldFont } from '../instances.mjs';
export { instance, boldFont };
export { R };
export const palette = {
  ground: '#3c4540', concrete: '#666d6c', faded: '#9b9c8e', dark: '#252e32',
  steel: '#46545a', rust: '#805b46', orange: '#f97316', amber: '#f2bb68',
  grass: '#59664a', glass: '#263b42', wood: '#73634c', teal: '#497575',
};
export const vec = (x, y, z) => new R.Vector3(x, y, z);
export function color(hex) { const n = parseInt(hex.replace('#', ''), 16); return new R.Color3(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255); }
export function cf(x, y, z, yaw = 0) { const c = Math.cos(yaw), s = Math.sin(yaw); return new R.CFrame(vec(x, y, z), [c, 0, s, 0, 1, 0, -s, 0, c]); }
export function lookAt(position, target) {
  const d=target.map((v,i)=>v-position[i]); const len=Math.hypot(...d); const f=d.map(v=>v/len);
  const r0=[-f[2],0,f[0]]; const rl=Math.hypot(...r0); const r=r0.map(v=>v/rl);
  const u=[r[1]*f[2]-r[2]*f[1],r[2]*f[0]-r[0]*f[2],r[0]*f[1]-r[1]*f[0]];
  return new R.CFrame(vec(...position),[r[0],u[0],-f[0],r[1],u[1],-f[1],r[2],u[2],-f[2]]);
}
export function folder(parent, name, model = false) { const o = model ? instance('Model') : instance('Folder'); o.Name = name; o.Parent = parent; return o; }
export function part(parent, name, size, at, tint = palette.concrete, mat = 'Concrete', yaw = 0, extra = {}) {
  const p = instance('Part'); p.Name = name; p.Size = vec(...size); p.CFrame = cf(...at, yaw);
  p.Color3uint8 = color(tint); p.Material = R.Material[mat]; if (!p.Material) throw new Error(`Unknown material: ${mat}`); p.Anchored = true;
  p.TopSurface = R.SurfaceType.Smooth; p.BottomSurface = R.SurfaceType.Smooth;
  Object.assign(p, extra); p.Parent = parent; return p;
}
export function value(parent, name, val) { const integer=typeof val==='number'&&Number.isInteger(val); const o=instance(integer?'IntValue':typeof val==='number'?'NumberValue':'StringValue'); o.Name=name; o.Value=integer?BigInt(val):val; o.Parent=parent; return o; }
export function sign(parent, text, size, at, tint = palette.orange, bg = palette.dark, yaw = 0) {
  const p = part(parent, 'Sign', size, at, bg, 'Metal', yaw);
  const gui = instance('SurfaceGui'); gui.Name = 'Signage'; gui.Face = R.NormalId.Front; gui.SizingMode = R.SurfaceGuiSizingMode.PixelsPerStud; gui.PixelsPerStud = 28; gui.Parent = p;
  const label = instance('TextLabel'); label.Name = 'Label'; label.Size = new R.UDim2(new R.UDim(1, 0), new R.UDim(1, 0));
  label.BackgroundTransparency = 1; label.Text = text; label.TextColor3 = color(tint); label.FontFace = boldFont(); label.TextScaled = true; label.TextWrapped = true; label.Parent = gui;
  return p;
}
export function lamp(parent, x, z, broken = false) {
  const m = folder(parent, 'StreetLight', true);
  part(m, 'Pole', [0.7, 17, 0.7], [x, 8.5, z], palette.steel, 'Metal');
  part(m, 'Arm', [5, 0.5, 0.6], [x + 2, 17, z], palette.steel, 'Metal');
  const lens = part(m, broken ? 'FlickerLamp' : 'Lens', [3.4, 0.3, 1.5], [x + 3.5, 16.8, z], palette.amber, 'Neon');
  const light = instance('PointLight'); light.Color = color(palette.amber); light.Brightness = 1.5; light.Range = 38; light.Enabled = true; light.Shadows = false; light.Parent = lens;
}
export function car(parent, x, z, yaw, tint = palette.rust) {
  const m = folder(parent, 'AbandonedCar', true);
  const c = Math.cos(yaw), s = Math.sin(yaw);
  const p = (n, size, offset, col, material = 'Metal') => part(m, n, size, [x + offset[0] * c + offset[2] * s, offset[1], z - offset[0] * s + offset[2] * c], col, material, yaw);
  p('Chassis', [6.5, 2, 12], [0, 2, 0], tint); p('Cabin', [5.8, 2.8, 5.5], [0, 4, -0.5], palette.dark);
  p('Roof', [6, 0.4, 5.8], [0, 5.5, -0.5], tint);
  p('Windshield', [5.2, 2, 0.2], [0, 4, -3.35], palette.glass, 'Glass');
  p('Hood', [6.4, 0.3, 3.5], [0, 3.2, -4.2], tint);
  for (const side of [-1, 1]) for (const end of [-1, 1]) {
    const tire=p('Wheel', [1.1, 2.4, 2.4], [side * 3.2, 1.5, end * 3.5], '#161a1c', 'Rubber'); tire.Shape=R.PartType.Cylinder;
    const hub=p('WheelHub',[0.13,1.1,1.1],[side*3.79,1.5,end*3.5],'#68716b','Metal');hub.Shape=R.PartType.Cylinder;
  }
  for(const side of [-1,1]) {
    p('SideWindow',[0.12,1.7,4.5],[side*2.98,4,-0.5],palette.glass,'Glass');
    p('DoorHandle',[0.15,0.15,0.8],[side*3.26,2.9,0.4],'#9aa29b');
    p('Mirror',[0.5,0.5,0.9],[side*3.4,3.7,-2.3],tint);
  }
  p('FrontBumper',[6.7,0.5,0.35],[0,1.5,-6.2],'#606963');
  p('RearBumper',[6.7,0.5,0.35],[0,1.5,6.2],'#606963');
  p('RearWindow',[5.1,1.8,0.15],[0,4,2.35],palette.glass,'Glass');
  for (const side of [-1, 1]) p('BrokenHeadlight', [1.6, 0.5, 0.2], [side * 2, 2.8, -6.1], '#a6a083', 'Glass');
}
export function loot(parent, id, count, x, z, index, cooldown = 90) {
  const p = part(parent, `${id}_${index}`, [3, 2.6, 3], [x, 1.3, z], id === 'electronics' ? palette.teal : id === 'fuel' ? palette.orange : palette.wood, 'WoodPlanks');
  value(p, 'ItemId', id); value(p, 'Amount', count); value(p, 'Cooldown', cooldown);
  return p;
}
export function crate(parent, x, y, z) {
  part(parent, 'Crate', [4, 4, 4], [x, y + 2, z], palette.wood, 'WoodPlanks');
  part(parent, 'Strap', [4.1, 0.5, 4.1], [x, y + 2, z], palette.dark, 'Metal');
}
export function tree(parent, x, z, h, evergreen = false) {
  const m=folder(parent,'Tree',true);
  const trunk=part(m,'Trunk',[h*0.72,1.3,1.3],[x,h*0.36,z],'#5d5444','Wood');
  trunk.Shape=R.PartType.Cylinder; trunk.CFrame=new R.CFrame(vec(x,h*0.36,z),[0,-1,0,1,0,0,0,0,1]);
  for(let i=0;i<(evergreen?4:5);i++) {
    const angle=i*2.399, spread=evergreen?0:h*0.18;
    const width=evergreen?h*(0.65-i*0.1):h*0.46;
    const y=evergreen?h*(0.42+i*0.15):h*(0.68+(i%2)*0.12);
    const leaf=part(m,'Foliage',[width,h*(evergreen?0.32:0.38),width],[x+Math.cos(angle)*spread,y,z+Math.sin(angle)*spread],['#394d38','#465b3d','#556747','#62734d','#465a3c'][i],'LeafyGrass',i*0.7,{CanCollide:false,CanTouch:false});
    leaf.Shape=R.PartType.Ball;
  }
  return m;
}
