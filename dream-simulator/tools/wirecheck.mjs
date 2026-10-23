/**
 * wirecheck.mjs — статическая проверка «проводов» игры.
 *
 * Ищет самый обидный класс багов, который не видит luaucheck:
 *  1. клиент обращается к элементу UI, которого нет в собранном .rbxl;
 *  2. кнопки/поля существуют, но к ним никто не обращается (мёртвые кнопки);
 *  3. используются Remote-каналы, которых нет в Remotes.luau (и наоборот);
 *  4. сервер пишет игроку атрибут, а клиент его не читает (и наоборот);
 *  5. дублируются определения функций в одном модуле.
 *
 * Запуск: node tools/wirecheck.mjs [build/DreamSimulator.rbxl]
 */
import fs from 'node:fs';
import path from 'node:path';
import { readBinary, readXml } from 'rbx-dom';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const target = process.argv[2] || path.join(ROOT, 'build', 'DreamSimulator.rbxl');

const raw = fs.readFileSync(target);
const dom = raw.subarray(0, 6).toString() === '<roblox' ? readXml(raw) : readBinary(raw);
const snap = dom.snapshot();

// snapshot.instances — это {индекс: instance}, а children ссылаются на referent.
const byRef = new Map();
for (const inst of Object.values(snap.instances)) byRef.set(inst.referent, inst);
const problems = [];
const warnings = [];

/* ------------------------------------------------------------------ */
/* 1. Собираем дерево интерфейса из готового места                     */
/* ------------------------------------------------------------------ */

const allNames = new Set();
for (const inst of byRef.values()) allNames.add(inst.name);

const guiTree = new Map(); // ScreenGui -> Set(имя любого потомка)
const buttonRefs = new Map(); // "Gui/Button" -> true

const childrenOf = (id) => byRef.get(id)?.children ?? [];

for (const inst of byRef.values()) {
  if (inst.className !== 'ScreenGui') continue;
  const names = new Set();
  const stack = [...childrenOf(inst.referent)];
  while (stack.length) {
    const childId = stack.pop();
    const child = byRef.get(childId);
    if (!child) continue;
    names.add(child.name);
    if (child.className === 'TextButton' || child.className === 'ImageButton') {
      buttonRefs.set(`${inst.name}/${child.name}`, false);
    }
    stack.push(...childrenOf(childId));
  }
  guiTree.set(inst.name, names);
}

/* ------------------------------------------------------------------ */
/* 2. Читаем исходники                                                 */
/* ------------------------------------------------------------------ */

const files = [];
const walk = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(abs);
    else if (entry.name.endsWith('.luau')) files.push(abs);
  }
};
walk(path.join(ROOT, 'src'));

const sources = files.map((abs) => ({
  abs,
  rel: path.relative(ROOT, abs),
  src: fs.readFileSync(abs, 'utf8'),
  isClient: abs.includes('StarterPlayer') || abs.includes('StarterGui'),
}));
const clientSources = sources.filter((s) => s.isClient);
const allSource = sources.map((s) => s.src).join('\n');

/* ------------------------------------------------------------------ */
/* 3. ClientState.el("Gui", "Name") и getGui("Gui")                    */
/* ------------------------------------------------------------------ */

const referenced = new Set();

for (const file of clientSources) {
  for (const match of file.src.matchAll(/ClientState\.el\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)/g)) {
    const [, gui, name] = match;
    const names = guiTree.get(gui);
    if (!names) {
      problems.push(`${file.rel}: ClientState.el("${gui}", "${name}") — такого ScreenGui нет`);
      continue;
    }
    if (!names.has(name)) {
      problems.push(`${file.rel}: ClientState.el("${gui}", "${name}") — элемента нет в ${gui}`);
    }
    referenced.add(`${gui}/${name}`);
    if (buttonRefs.has(`${gui}/${name}`)) buttonRefs.set(`${gui}/${name}`, true);
  }

  // getGui("X")
  for (const match of file.src.matchAll(/ClientState\.getGui\(\s*"([^"]+)"\s*\)/g)) {
    if (!guiTree.has(match[1])) {
      problems.push(`${file.rel}: ClientState.getGui("${match[1]}") — такого ScreenGui нет`);
    }
  }

  // FindFirstChild("Name", true) — имя должно существовать хоть где-то в месте
  for (const match of file.src.matchAll(/FindFirstChild\(\s*"([^"]+)"\s*,\s*true\s*\)/g)) {
    const name = match[1];
    if (!allNames.has(name)) {
      warnings.push(`${file.rel}: FindFirstChild("${name}", true) — такого объекта нет ни в интерфейсе, ни в мире`);
    }
  }
}

/* ------------------------------------------------------------------ */
/* 4. Мёртвые кнопки                                                   */
/* ------------------------------------------------------------------ */

// Имена, которые собираются конкатенацией: "Diff" .. key, key .. "Card"
const dynamicPrefixes = new Set();
const dynamicSuffixes = new Set();
for (const match of allSource.matchAll(/"([A-Za-z][A-Za-z0-9_]*)"\s*\.\./g)) dynamicPrefixes.add(match[1]);
for (const match of allSource.matchAll(/\.\.\s*"([A-Za-z][A-Za-z0-9_]*)"/g)) dynamicSuffixes.add(match[1]);

for (const [ref, used] of buttonRefs) {
  const [gui, name] = ref.split('/');
  if (used) continue;
  // Кнопки-шаблоны (Template) клонируются в рантайме — это нормально.
  if (/Template$/i.test(name)) continue;
  // Кнопка может обрабатываться через FindFirstChild/перебор, а не через el().
  if (allSource.includes(`"${name}"`)) continue;
  if (new RegExp(`\\b${name}\\b`).test(allSource)) continue;
  // Имя собирается из частей: Diff + Easy, Easy + Card.
  let dynamic = false;
  for (const prefix of dynamicPrefixes) {
    if (name.startsWith(prefix)) dynamic = true;
  }
  for (const suffix of dynamicSuffixes) {
    if (name.endsWith(suffix)) dynamic = true;
  }
  if (dynamic) continue;
  warnings.push(`Кнопка ${gui}/${name} нигде не используется — возможна мёртвая кнопка`);
}

/* ------------------------------------------------------------------ */
/* 5. Remote-каналы                                                    */
/* ------------------------------------------------------------------ */

const remotesModule = fs.readFileSync(
  path.join(ROOT, 'src', 'ReplicatedStorage', 'SharedModules', 'Remotes.luau'),
  'utf8'
);
const declaredRemotes = new Set();
const NON_CHANNELS = new Set(['ReplicatedStorage', 'Remotes', 'RemoteEvent', 'RemoteFunction', 'ModuleScript', 'Folder']);
for (const match of remotesModule.matchAll(/"([A-Za-z][A-Za-z0-9_]*)"/g)) {
  if (!NON_CHANNELS.has(match[1])) declaredRemotes.add(match[1]);
}

const usedRemotes = new Map();
for (const file of sources) {
  for (const match of file.src.matchAll(/Remotes\.get\(\s*"([^"]+)"\s*\)/g)) {
    if (!usedRemotes.has(match[1])) usedRemotes.set(match[1], new Set());
    usedRemotes.get(match[1]).add(file.rel);
  }
}
for (const [name, where] of usedRemotes) {
  if (!declaredRemotes.has(name)) {
    problems.push(`Remotes.get("${name}") используется, но канал не объявлен (${[...where].join(', ')})`);
  }
}
for (const name of declaredRemotes) {
  if (!usedRemotes.has(name)) warnings.push(`Канал ${name} объявлен, но нигде не используется`);
}

/* ------------------------------------------------------------------ */
/* 6. Общие атрибуты: сервер пишет — клиент читает                     */
/* ------------------------------------------------------------------ */

const setAttrs = new Set();
const getAttrs = new Set();
for (const file of sources) {
  for (const match of file.src.matchAll(/SetAttribute\(\s*"([^"]+)"/g)) setAttrs.add(match[1]);
  for (const match of file.src.matchAll(/GetAttribute\(\s*"([^"]+)"/g)) getAttrs.add(match[1]);
}
for (const name of getAttrs) {
  if (!setAttrs.has(name)) warnings.push(`Атрибут "${name}" читается, но никто его не пишет`);
}

/* ------------------------------------------------------------------ */
/* 6b. Звуковые ключи                                                  */
/* ------------------------------------------------------------------ */

const configSource = fs.readFileSync(path.join(ROOT, 'src', 'ReplicatedStorage', 'SharedModules', 'Config.luau'), 'utf8');
const soundBlock = /Config\.Sounds\s*=\s*{([\s\S]*?)\n}/.exec(configSource);
const soundNames = new Set();
if (soundBlock) {
  for (const match of soundBlock[1].matchAll(/^\s*([A-Za-z][A-Za-z0-9_]*)\s*=/gm)) soundNames.add(match[1]);
}

for (const file of sources) {
  for (const match of file.src.matchAll(/Config\.Sounds\.([A-Za-z0-9_]+)/g)) {
    if (!soundNames.has(match[1])) {
      problems.push(`${file.rel}: Config.Sounds.${match[1]} — такого звука нет в Config.Sounds`);
    }
  }
  for (const match of file.src.matchAll(/(?:playSound|SoundController\.play)\(\s*"([A-Za-z0-9_]+)"/g)) {
    if (!soundNames.has(match[1])) {
      warnings.push(`${file.rel}: звук "${match[1]}" не объявлен в Config.Sounds`);
    }
  }
}

/* ------------------------------------------------------------------ */
/* 7. Дубли определений в модуле                                       */
/* ------------------------------------------------------------------ */

for (const file of sources) {
  const defs = new Map();
  const pattern = /^function\s+([A-Za-z0-9_.]+)\s*\(/gm;
  for (const match of file.src.matchAll(pattern)) {
    if (defs.has(match[1])) {
      problems.push(`${file.rel}: функция ${match[1]} определена дважды`);
    }
    defs.set(match[1], true);
  }
}

/* ------------------------------------------------------------------ */
/* Отчёт                                                               */
/* ------------------------------------------------------------------ */

const unique = (list) => [...new Set(list)];
const hard = unique(problems);
const soft = unique(warnings);

console.log(`Экранов: ${guiTree.size}, кнопок: ${buttonRefs.size}, модулей: ${sources.length}`);
if (hard.length) {
  console.log(`\nОШИБКИ (${hard.length}):`);
  for (const item of hard) console.log('  ✗ ' + item);
}
if (soft.length) {
  console.log(`\nПредупреждения (${soft.length}):`);
  for (const item of soft) console.log('  · ' + item);
}
if (!hard.length) console.log('\n✓ Битых связей не найдено.');
process.exitCode = hard.length ? 1 : 0;
