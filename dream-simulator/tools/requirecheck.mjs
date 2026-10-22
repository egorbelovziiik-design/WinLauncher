/**
 * requirecheck.mjs — проверяет, что каждый require() в собранном месте
 * указывает на существующий ModuleScript.
 *
 * Ошибка в require — самая частая причина «игра вообще не запускается»:
 * поток падает на первой строке, и ни один контроллер не стартует.
 *
 * Запуск: node tools/requirecheck.mjs [build/DreamSimulator.rbxl]
 */
import fs from 'node:fs';
import path from 'node:path';
import { readBinary, readXml } from 'rbx-dom';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const target = process.argv[2] || path.join(ROOT, 'build', 'DreamSimulator.rbxl');
const raw = fs.readFileSync(target);
const dom = raw.subarray(0, 6).toString() === '<roblox' ? readXml(raw) : readBinary(raw);
const snap = dom.snapshot();

const byRef = new Map();
for (const inst of Object.values(snap.instances)) byRef.set(inst.referent, inst);

/** Путь инстанса: Service/Name/Name/... */
const pathOf = (inst) => {
  const chain = [];
  let node = inst;
  while (node) {
    chain.unshift(node.name);
    node = node.parent ? byRef.get(node.parent) : null;
  }
  return chain.join('/');
};

const scripts = [];
for (const inst of byRef.values()) {
  if (inst.className !== 'LuaSourceContainer') {
    if (inst.className !== 'Script' && inst.className !== 'LocalScript' && inst.className !== 'ModuleScript') continue;
  }
  const source = inst.properties?.Source?.String ?? '';
  if (!source) continue;
  scripts.push({ inst, path: pathOf(inst), source, className: inst.className });
}

const exists = (parentPath, name) => {
  for (const inst of byRef.values()) {
    if (inst.name !== name) continue;
    const parent = inst.parent ? byRef.get(inst.parent) : null;
    if (!parent) continue;
    const parentPathFull = pathOf(parent);
    if (parentPathFull === parentPath || parentPathFull.endsWith('/' + parentPath)) return true;
  }
  return false;
};

const problems = [];
const notes = [];

for (const script of scripts) {
  const folder = script.path.split('/').slice(0, -1).join('/');

  // require(script.Parent.Foo) / require(script.Parent:WaitForChild("Foo"))
  const siblingPatterns = [
    /require\(\s*script\.Parent\.([A-Za-z0-9_]+)\s*\)/g,
    /require\(\s*script\.Parent:WaitForChild\(\s*"([A-Za-z0-9_]+)"\s*\)\s*\)/g,
    /require\(\s*script\.Parent:FindFirstChild\(\s*"([A-Za-z0-9_]+)"\s*\)\s*\)/g,
  ];
  for (const pattern of siblingPatterns) {
    for (const match of script.source.matchAll(pattern)) {
      if (!exists(folder, match[1])) {
        problems.push(`${script.path}: require(script.Parent.${match[1]}) — модуля нет рядом`);
      }
    }
  }

  // require(script.Parent.Modules.Foo)
  for (const match of script.source.matchAll(/require\(\s*script\.Parent\.Modules\.([A-Za-z0-9_]+)\s*\)/g)) {
    if (!exists(`${folder}/Modules`, match[1])) {
      problems.push(`${script.path}: require(...Modules.${match[1]}) — модуля нет в Modules`);
    }
  }

  // require(ReplicatedStorage.SharedModules.Foo)
  for (const match of script.source.matchAll(/require\(\s*ReplicatedStorage\.SharedModules\.([A-Za-z0-9_]+)\s*\)/g)) {
    if (!exists('ReplicatedStorage/SharedModules', match[1])) {
      problems.push(`${script.path}: require(SharedModules.${match[1]}) — модуля нет`);
    }
  }

  // require(SharedModules:WaitForChild("Foo"))
  for (const match of script.source.matchAll(/require\(\s*SharedModules:WaitForChild\(\s*"([A-Za-z0-9_]+)"\s*\)\s*\)/g)) {
    if (!exists('ReplicatedStorage/SharedModules', match[1])) {
      problems.push(`${script.path}: require(SharedModules:WaitForChild("${match[1]}")) — модуля нет`);
    }
  }

  // require(<переменная>) — сообщаем, что проверить нельзя
  for (const match of script.source.matchAll(/require\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)/g)) {
    const name = match[1];
    const known = byRef.values();
    let found = false;
    for (const inst of known) {
      if (inst.className === 'ModuleScript' && inst.name === name) {
        found = true;
        break;
      }
    }
    if (!found && name !== 'module') notes.push(`${script.path}: require(${name}) — динамический модуль`);
  }
}

const modules = [...byRef.values()].filter((inst) => inst.className === 'ModuleScript');
notes.push(`ModuleScript в месте: ${modules.length}, скриптов с кодом: ${scripts.length}`);

for (const note of notes) console.log('  · ' + note);
if (problems.length) {
  console.log(`\nПроблемы require (${problems.length}):`);
  for (const problem of [...new Set(problems)]) console.log('  ✗ ' + problem);
  process.exitCode = 1;
} else {
  console.log('\n✓ Все require() указывают на существующие модули.');
}
