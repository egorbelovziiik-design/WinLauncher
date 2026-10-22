/**
 * domcheck.mjs — проверка собранного .rbxl по базе反射 Roblox (API dump).
 *
 * Для каждого инстанса и каждого свойства:
 *  • свойство вообще существует у класса (с учётом наследования);
 *  • тип значения в файле совпадает с типом из API-дампа.
 *
 * Это ловит «место, которое не открывается»: неизвестные классы, опечатки
 * в именах свойств, числа вместо векторов и т.п.
 *
 * Запуск: node tools/domcheck.mjs [build/DreamSimulator.rbxl]
 */
import fs from 'node:fs';
import path from 'node:path';
import { readBinary, readXml, reflection } from 'rbx-dom';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const target = process.argv[2] || path.join(ROOT, 'build', 'DreamSimulator.rbxl');
const raw = fs.readFileSync(target);
const dom = raw.subarray(0, 6).toString() === '<roblox' ? readXml(raw) : readBinary(raw);
const snap = dom.snapshot();
const instances = Object.values(snap.instances);

// DataType из дампа -> тег в снапшоте rbx-dom
const TAG_BY_DATATYPE = {
  bool: ['Bool'],
  float: ['Float32', 'Float64'],
  double: ['Float64', 'Float32'],
  int: ['Int32', 'Int64'],
  int64: ['Int64', 'Int32'],
  string: ['String', 'SharedString'],
  Vector2: ['Vector2'],
  Vector3: ['Vector3'],
  CFrame: ['CFrame'],
  Color3: ['Color3', 'Color3uint8'],
  Color3uint8: ['Color3'],
  UDim: ['UDim'],
  UDim2: ['UDim2'],
  BrickColor: ['BrickColor'],
  NumberRange: ['NumberRange'],
  NumberSequence: ['NumberSequence'],
  ColorSequence: ['ColorSequence'],
  Rect: ['Rect'],
  Content: ['Content', 'String'],
  Enum: ['Enum'],
  Ref: ['Ref'],
  PhysicalProperties: ['PhysicalProperties'],
  Font: ['Font'],
};

const descriptor = (className, propName) => {
  const chain = reflection.superclasses(className) || [className];
  for (const name of chain) {
    const property = reflection.property(name, propName);
    if (property) return property;
  }
  return null;
};

const problems = new Map();
const add = (msg) => problems.set(msg, (problems.get(msg) || 0) + 1);

let checkedProps = 0;
const classNames = new Set();

for (const inst of instances) {
  classNames.add(inst.className);
  if (!reflection.class(inst.className) && !(reflection.superclasses(inst.className) || []).length) {
    add(`Неизвестный класс: ${inst.className} (${inst.name})`);
    continue;
  }
  for (const [propName, value] of Object.entries(inst.properties || {})) {
    checkedProps += 1;
    const prop = descriptor(inst.className, propName);
    if (!prop) {
      add(`${inst.className}.${propName} — такого свойства нет в API (${inst.name})`);
      continue;
    }
    const datatype = prop.DataType?.Value;
    const enumName = prop.DataType?.Enum;

    // Enum: значение должно существовать в перечислении
    if (enumName) {
      const number = value && typeof value === 'object' ? value.Enum : undefined;
      if (typeof number !== 'number') {
        add(`${inst.className}.${propName} — ожидался Enum ${enumName}, в файле ${JSON.stringify(value)}`);
        continue;
      }
      const items = reflection.enumItems(enumName) || {};
      const values = Object.values(items);
      if (!values.includes(number)) {
        add(`${inst.className}.${propName} — значение ${number} отсутствует в Enum.${enumName}`);
      }
      continue;
    }

    const expected = TAG_BY_DATATYPE[datatype];
    if (!expected) continue; // экзотические типы не проверяем
    const tag = value && typeof value === 'object' ? Object.keys(value)[0] : typeof value;
    if (!expected.includes(tag)) {
      add(`${inst.className}.${propName} — ${datatype}, а в файле ${tag} (${inst.name})`);
      continue;
    }

    // NaN/бесконечность в координатах ломают рендер и физику
    const numbers = [];
    const collect = (node) => {
      if (typeof node === 'number') numbers.push(node);
      else if (Array.isArray(node)) node.forEach(collect);
      else if (node && typeof node === 'object') Object.values(node).forEach(collect);
    };
    collect(value);
    for (const number of numbers) {
      if (!Number.isFinite(number)) {
        add(`${inst.className}.${propName} — нечисловое значение (${inst.name})`);
        break;
      }
    }
  }
}

const list = [...problems.entries()];
console.log(`Классов: ${classNames.size}, инстансов: ${instances.length}, свойств проверено: ${checkedProps}`);
if (list.length) {
  console.log(`\nПроблемы (${list.length}):`);
  for (const [msg, count] of list.slice(0, 60)) {
    console.log(`  ✗ ${msg}${count > 1 ? ` ×${count}` : ''}`);
  }
  if (list.length > 60) console.log(`  … и ещё ${list.length - 60}`);
  process.exitCode = 1;
} else {
  console.log('\n✓ Все классы и свойства совпадают с API Roblox.');
}
