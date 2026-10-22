import { el, part, v3, rgb, screenGui, ui, cf, buildPlace, light, moduleScript, serverScript, clientScript, hex, udim2, font } from './rbxkit.mjs';
import fs from 'node:fs';
fs.mkdirSync('src/Smoke', { recursive: true });
fs.writeFileSync('src/Smoke/Test.server.luau', 'print("hello from script")\n');
fs.writeFileSync('src/Smoke/Test.client.luau', 'print("client")\n');
fs.writeFileSync('src/Smoke/Mod.luau', 'return { value = 42 }\n');

const workspace = el('Workspace', { Name: 'Workspace' }, [
  part({ Name: 'Floor', pos: [0, 0, 0], size: [40, 1, 40], color: [0.2, 0.2, 0.25], material: 'Concrete' }),
  part({ Name: 'Glow', pos: [0, 3, 0], size: [2, 2, 2], color: [0.6, 0.4, 1], material: 'Neon' }, [light({ Brightness: 3 })]),
]);

const gui2 = screenGui('TestGui2', {}, [
  ui.frame({ Name: 'Root', Size: udim2(1, 1), BackgroundColor3: rgb(8, 10, 20) }, [
    ui.label('DREAM SIMULATOR', { Name: 'Title', Size: udim2(1, [0, 40]), TextSize: 32, FontFace: font('rbxasset://fonts/families/GothamSSm.json', 'Bold') }),
    ui.corner(16), ui.stroke(), ui.gradient([hex('#2a1a4a'), hex('#0a0a18')]),
  ]),
]);

const tree = {
  className: 'DataModel',
  children: [
    workspace,
    el('Lighting', {}, [
      el('Atmosphere', { Density: 0.3, Haze: 2, Glare: 0.1, Color: rgb(120, 110, 160), Decay: rgb(80, 70, 120) }),
      el('BloomEffect', { Intensity: 0.6, Size: 28, Threshold: 1.2 }),
      el('ColorCorrectionEffect', { Brightness: -0.05, Contrast: 0.1, Saturation: -0.15, TintColor: rgb(200, 190, 255) }),
      el('DepthOfFieldEffect', { FarIntensity: 0.5, FocusDistance: 20, InFocusRadius: 30, NearIntensity: 0.2 }),
    ]),
    el('ReplicatedStorage', {}, [
      moduleScript('Mod', 'Smoke/Mod.luau'),
      serverScript('Test', 'Smoke/Test.server.luau'),
      clientScript('TestClient', 'Smoke/Test.client.luau'),
    ]),
    el('ServerScriptService', {}, [serverScript('Main', 'Smoke/Test.server.luau')]),
    el('StarterGui', {}, [gui2]),
    el('SoundService', { AmbientReverb: 'Cave' }),
  ],
};

const res = buildPlace(tree, 'build/Smoke');
console.log('instances', res.instances, 'binary', res.binary.length, 'xml', res.xml.length);
