/**
 * build.mjs — сборка DreamSimulator.rbxl (и .rbxlx) из src/ и tools/maps.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import {
  el, v3, v2, cf, rgb, hex, nr, ns, cs, content, font,
  moduleScript, serverScript, clientScript, buildPlace, ROOT,
} from './rbxkit.mjs';
import * as GUI from './gui.mjs';
import { lobby } from './maps/lobby.mjs';
import { allDreamMaps } from './maps/maps.mjs';
import { sanitizeMap } from './maps/sanitize.mjs';

const OUT = process.argv[2] || 'build/DreamSimulator';

/* ------------------------------------------------------------------ */
/* чтение списка каналов из модуля Remotes                              */
/* ------------------------------------------------------------------ */
const remotesSrc = fs.readFileSync(
  path.join(ROOT, 'src/ReplicatedStorage/SharedModules/Remotes.luau'), 'utf8');
const listOf = (field) => {
  const start = remotesSrc.indexOf(`Remotes.${field} = {`);
  const body = remotesSrc.slice(start, remotesSrc.indexOf('}', start));
  return [...body.matchAll(/"([A-Za-z]+)"/g)].map((m) => m[1]);
};
const EVENTS = listOf('EVENTS');
const FUNCTIONS = listOf('FUNCTIONS');

/* ------------------------------------------------------------------ */
/* Shared modules                                                      */
/* ------------------------------------------------------------------ */
const SHARED = [
  'Config', 'Util', 'Remotes', 'ItemDefinitions', 'DreamDefinitions',
  'MonsterDefinitions', 'QuestDefinitions', 'AchievementDefinitions',
];

const sharedFolder = el('Folder', { Name: 'SharedModules' },
  SHARED.map((name) => moduleScript(name, `ReplicatedStorage/SharedModules/${name}.luau`)));

const remotes = el('Folder', { Name: 'Remotes' }, [
  ...EVENTS.map((name) => el('RemoteEvent', { Name: name })),
  ...FUNCTIONS.map((name) => el('RemoteFunction', { Name: name })),
]);

const replicatedStorage = el('ReplicatedStorage', {}, [sharedFolder, remotes]);

/* ------------------------------------------------------------------ */
/* Server modules                                                      */
/* ------------------------------------------------------------------ */
const SERVER = [
  'MapService', 'DataManager', 'EconomyManager', 'AchievementManager',
  'QuestManager', 'NPCManager', 'MonsterManager', 'DreamManager',
];

const serverScriptService = el('ServerScriptService', {}, [
  ...SERVER.map((name) => moduleScript(name, `ServerScriptService/${name}.luau`)),
  serverScript('Main', 'ServerScriptService/Main.server.luau'),
]);

/* ------------------------------------------------------------------ */
/* Client controllers                                                  */
/* ------------------------------------------------------------------ */
const CLIENT = [
  'ClientState', 'SoundController', 'CameraController', 'InputController',
  'HUDController', 'MenuController', 'EffectsController', 'CutsceneController',
  'DiagnosticsController',
];

const starterPlayerScripts = el('StarterPlayerScripts', { Name: 'StarterPlayerScripts' }, [
  clientScript('BootMenu', 'StarterPlayer/StarterPlayerScripts/BootMenu.client.luau'),
  clientScript('Splash', 'StarterPlayer/StarterPlayerScripts/Splash.client.luau'),
  clientScript('ClientMain', 'StarterPlayer/StarterPlayerScripts/ClientMain.client.luau'),
  el('Folder', { Name: 'Modules' },
    CLIENT.map((name) => moduleScript(name, `StarterPlayer/StarterPlayerScripts/Modules/${name}.luau`))),
]);

const starterPlayer = el('StarterPlayer', {}, [
  starterPlayerScripts,
  el('StarterPlayerScripts', {
    Name: 'StarterPlayerScriptsDup',
    Archivable: false,
  }),
]);
// второй узел не нужен — убираем, чтобы не плодить мусор
starterPlayer.children = [starterPlayerScripts];

/* ------------------------------------------------------------------ */
/* GUI                                                                 */
/* ------------------------------------------------------------------ */
const GUI_ORDER = [
  ['MainMenu', 'mainMenu'],
  ['DifficultyGUI', 'difficultyGui'],
  ['ShopGUI', 'shopGui'],
  ['InventoryGUI', 'inventoryGui'],
  ['AchievementsGUI', 'achievementsGui'],
  ['StatsGUI', 'statsGui'],
  ['SettingsGUI', 'settingsGui'],
  ['DreamHUD', 'dreamHud'],
  ['ResultGUI', 'resultGui'],
  ['CutsceneGUI', 'cutsceneGui'],
  ['TopBar', 'topBarGui'],
];

const GUI_DISPLAY = { TopBar: 90, CutsceneGUI: 80, MainMenu: 5 };

const starterGui = el('StarterGui', {}, GUI_ORDER.map(([name, key], index) => {
  const gui = GUI[key]();
  gui.props = {
    ...gui.props,
    ResetOnSpawn: false,
    DisplayOrder: GUI_DISPLAY[name] || index + 1,
  };
  return gui;
}));

/* ------------------------------------------------------------------ */
/* ServerStorage: карты, монстры, NPC, предметы                        */
/* ------------------------------------------------------------------ */
const dreamMaps = el('Folder', { Name: 'DreamMaps' }, allDreamMaps().map((map) => {
  const stats = sanitizeMap(map);
  console.log(`  карта ${String(map.name).padEnd(16)} маркеров=${stats.markers} сдвинуто=${stats.moved} на месте=${stats.kept} без места=${stats.dropped}`);
  return map;
}));

function bodyPart(name, size, offset, color, extra = {}) {
  return el('Part', {
    Name: name,
    Size: size,
    CFrame: cf(offset),
    Anchored: true,
    CanCollide: false,
    CanQuery: true,
    Material: 'SmoothPlastic',
    Color: color,
    ...extra,
  });
}

function monsterTemplate(name, opts) {
  const color = rgb(...(opts.color || [30, 30, 40]));
  const eye = rgb(...(opts.eye || [255, 40, 40]));
  const torso = bodyPart('Root', v3(opts.width || 3, opts.height || 4.4, 1.6), [0, 4.2, 0], color, {
    CanQuery: true,
    Transparency: opts.transparency || 0,
    Material: opts.material || 'SmoothPlastic',
    Reflectance: opts.reflectance || 0,
  });
  const head = bodyPart('Head', v3(1.8, 1.8, 1.8), [0, 7.1, 0], color, {
    Transparency: opts.transparency || 0,
    Material: opts.material || 'SmoothPlastic',
    Shape: 'Ball',
  });
  const eyeL = bodyPart('EyeLeft', v3(0.34, 0.34, 0.2), [-0.42, 7.25, -0.92], eye, {
    Material: 'Neon', Shape: 'Ball',
  });
  const eyeR = bodyPart('EyeRight', v3(0.34, 0.34, 0.2), [0.42, 7.25, -0.92], eye, {
    Material: 'Neon', Shape: 'Ball',
  });
  const armL = bodyPart('ArmLeft', v3(0.7, 4.6, 0.7), [-1.9, 4.0, 0], color, { Transparency: opts.transparency || 0 });
  const armR = bodyPart('ArmRight', v3(0.7, 4.6, 0.7), [1.9, 4.0, 0], color, { Transparency: opts.transparency || 0 });
  const legL = bodyPart('LegLeft', v3(0.8, 3.4, 0.8), [-0.8, 1.7, 0], color, { Transparency: opts.transparency || 0 });
  const legR = bodyPart('LegRight', v3(0.8, 3.4, 0.8), [0.8, 1.7, 0], color, { Transparency: opts.transparency || 0 });

  const glow = el('PointLight', {
    Color: eye, Brightness: 1.6, Range: 22, Shadows: false,
  });
  const aura = el('ParticleEmitter', {
    Texture: 'rbxassetid://243660364', Rate: opts.auraRate || 8,
    Lifetime: nr(0.6, 1.4), Speed: nr(0.2, 1.2), Size: ns([[0, 1.4], [1, 0]]),
    Transparency: ns([[0, 0.3], [1, 1]]), Color: cs([[0, eye], [1, eye]]),
    LightEmission: 0.6, LightInfluence: 0.4, SpreadAngle: v2(180, 180), RotSpeed: nr(-40, 40),
    ZOffset: 0.2, Enabled: true,
  });
  head.children.push(glow, aura);

  const model = el('Model', { Name: name }, [torso, head, eyeL, eyeR, armL, armR, legL, legR]);
  return model;
}

const monsterTemplates = el('Folder', { Name: 'MonsterTemplates' }, [
  monsterTemplate('Stalker', { behavior: 'stalker', color: [24, 24, 32], eye: [255, 60, 60], transparency: 0.35, auraRate: 6, material: 'SmoothPlastic', displayName: 'Тень' }),
  monsterTemplate('Chaser', { behavior: 'chaser', color: [80, 20, 20], eye: [255, 210, 60], width: 3, height: 4.6, material: 'Slate', displayName: 'Бегун' }),
  monsterTemplate('Watcher', { behavior: 'watcher', color: [40, 46, 60], eye: [120, 220, 255], height: 5.6, width: 3.4, material: 'Marble', displayName: 'Наблюдатель' }),
  monsterTemplate('Mimic', { behavior: 'mimic', color: [60, 60, 70], eye: [255, 120, 220], material: 'Plastic', displayName: 'Обманщик' }),
  monsterTemplate('Hunter', { behavior: 'hunter', color: [30, 40, 30], eye: [140, 255, 120], width: 3.4, height: 4.8, material: 'CorrodedMetal', displayName: 'Кошмар' }),
]);

/* NPC: гуманоидная фигура из нескольких частей */
function npcTemplate() {
  const cloth = rgb(70, 60, 90);
  const skin = rgb(200, 190, 180);
  const torso = bodyPart('Root', v3(2, 2.4, 1), [0, 3.6, 0], cloth, { CanCollide: false });
  const head = bodyPart('Head', v3(1.3, 1.3, 1.3), [0, 5.5, 0], skin, { Shape: 'Ball' });
  const armL = bodyPart('ArmLeft', v3(0.6, 2.2, 0.6), [-1.3, 3.7, 0], cloth);
  const armR = bodyPart('ArmRight', v3(0.6, 2.2, 0.6), [1.3, 3.7, 0], cloth);
  const legL = bodyPart('LegLeft', v3(0.7, 2.2, 0.7), [-0.5, 1.2, 0], rgb(40, 40, 50));
  const legR = bodyPart('LegRight', v3(0.7, 2.2, 0.7), [0.5, 1.2, 0], rgb(40, 40, 50));
  const lamp = el('PointLight', { Color: rgb(255, 220, 160), Brightness: 0.9, Range: 14 });
  head.children.push(lamp);
  const model = el('Model', { Name: 'NPC' }, [torso, head, armL, armR, legL, legR]);
  return model;
}

const npcTemplates = el('Folder', { Name: 'NPCTemplates' }, [npcTemplate()]);

/* ItemTemplates: коллекционные предметы */
function itemTemplate(name, color, glow) {
  const core = el('Part', {
    Name: 'Root',
    Size: v3(1.4, 1.4, 1.4),
    CFrame: cf([0, 2, 0]),
    Anchored: true,
    CanCollide: false,
    Material: glow ? 'Neon' : 'Glass',
    Color: color,
    Shape: 'Ball',
    Transparency: glow ? 0.05 : 0.25,
    Reflectance: 0.2,
  });
  const light = el('PointLight', { Color: color, Brightness: 2, Range: 16 });
  const sparkle = el('ParticleEmitter', {
    Texture: 'rbxassetid://243660364', Rate: 12, Lifetime: nr(0.4, 0.9),
    Speed: nr(0.4, 1.6), Size: ns([[0, 0.7], [1, 0]]),
    Transparency: ns([[0, 0.2], [1, 1]]), Color: cs([[0, color], [1, color]]),
    LightEmission: 0.9, SpreadAngle: v2(180, 180), Enabled: true,
  });
  core.children.push(light, sparkle);
  return el('Model', { Name: name }, [core]);
}

const itemTemplates = el('Folder', { Name: 'ItemTemplates' }, [
  itemTemplate('Fragment', rgb(120, 230, 255), true),
  itemTemplate('Battery', rgb(255, 220, 120), true),
  itemTemplate('Key', rgb(255, 200, 60), false),
  itemTemplate('Relic', rgb(220, 120, 255), true),
  itemTemplate('Candle', rgb(255, 160, 90), true),
]);

const serverStorage = el('ServerStorage', {}, [dreamMaps, monsterTemplates, npcTemplates, itemTemplates]);

/* ------------------------------------------------------------------ */
/* Workspace                                                           */
/* ------------------------------------------------------------------ */
const menuBedroom = lobby();
const workspaceNode = el('Workspace', {
  Name: 'Workspace',
  Gravity: 196.2,
  FallenPartsDestroyHeight: -500,
  StreamingEnabled: false,
}, [
  el('Folder', { Name: 'ActiveDreams' }),
  menuBedroom,
]);

/* ------------------------------------------------------------------ */
/* Lighting                                                            */
/* ------------------------------------------------------------------ */
const lighting = el('Lighting', {
  Ambient: rgb(58, 58, 82),
  OutdoorAmbient: rgb(38, 38, 58),
  Brightness: 1.15,
  ClockTime: 0.2,
  GeographicLatitude: 20,
  FogStart: 40,
  FogEnd: 320,
  FogColor: rgb(16, 16, 24),
  GlobalShadows: true,
  EnvironmentDiffuseScale: 0.3,
  EnvironmentSpecularScale: 0.4,
  ShadowSoftness: 0.4,
  Technology: 'ShadowMap',
}, [
  el('Atmosphere', {
    Name: 'MenuAtmosphere',
    Density: 0.36,
    Offset: 0.1,
    Color: rgb(70, 74, 100),
    Decay: rgb(30, 32, 50),
    Glare: 0.1,
    Haze: 1.6,
  }),
  el('BloomEffect', { Name: 'MenuBloom', Intensity: 0.5, Size: 24, Threshold: 1.1 }),
  el('ColorCorrectionEffect', {
    Name: 'MenuColor', Brightness: -0.03, Contrast: 0.16, Saturation: -0.18,
    TintColor: rgb(200, 210, 240),
  }),
  el('DepthOfFieldEffect', { Name: 'MenuDOF', FarIntensity: 0.3, FocusDistance: 30, InFocusRadius: 18, NearIntensity: 0.2 }),
]);

/* ------------------------------------------------------------------ */
/* Итоговое дерево                                                     */
/* ------------------------------------------------------------------ */
const tree = {
  className: 'DataModel',
  children: [
    workspaceNode,
    replicatedStorage,
    serverScriptService,
    serverStorage,
    starterGui,
    starterPlayer,
    lighting,
    el('SoundService', {
      Name: 'SoundService',
      AmbientReverb: 'Hallway',
      DistanceFactor: 3.33,
      DopplerScale: 1,
      RolloffScale: 1,
    }),
    el('Players', { Name: 'Players', CharacterAutoLoads: true }),
    el('StarterPack', { Name: 'StarterPack' }),
    el('Teams', { Name: 'Teams' }),
    el('Chat', { Name: 'Chat' }),
    el('TextChatService', { Name: 'TextChatService', ChatVersion: 'TextChatService' }),
  ],
};

// Гейт качества: не собираем место, если данные или «провода» сломаны.
const gate = (label, file) => {
  try {
    execFileSync(process.execPath, [path.join(ROOT, 'tools', file)], { stdio: 'pipe' });
    console.log(`✓ ${label}`);
  } catch (error) {
    console.error(`✗ ${label} — сборка остановлена`);
    console.error(String(error.stdout || '') + String(error.stderr || ''));
    process.exit(1);
  }
};
gate('данные контента', 'contentcheck.mjs');

const result = buildPlace(tree, OUT);

// Проверки по готовому файлу: если что-то не так — файл не выпускаем.
const postGate = (label, file) => {
  try {
    execFileSync(process.execPath, [path.join(ROOT, 'tools', file), `${OUT}.rbxl`], { stdio: 'pipe' });
    console.log(`✓ ${label}`);
  } catch (error) {
    console.error(`✗ ${label} — файл удалён, сборка остановлена`);
    console.error(String(error.stdout || '') + String(error.stderr || ''));
    for (const suffix of ['.rbxl', '.rbxlx']) {
      try { fs.unlinkSync(`${OUT}${suffix}`); } catch (_) {}
    }
    process.exit(1);
  }
};
postGate('связи интерфейса и каналов', 'wirecheck.mjs');
postGate('API-совместимость места', 'domcheck.mjs');
postGate('require-цепочки', 'requirecheck.mjs');
console.log(`✓ ${OUT}.rbxl  instances=${result.instances}  binary=${(result.binary.length / 1024 / 1024).toFixed(2)} MB`);
console.log(`  remotes: events=${EVENTS.length} functions=${FUNCTIONS.length}`);
console.log(`  scripts: server=${SERVER.length + 1} client=${CLIENT.length + 1}`);
