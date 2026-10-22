#!/usr/bin/env node
// Синтаксическая проверка всех Luau-файлов проекта через luau-parser.
import fs from 'node:fs';
import path from 'node:path';
import { parse } from 'luau-parser';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', 'src');
const files = [];
(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith('.luau')) files.push(full);
  }
})(ROOT);

let failures = 0;
for (const file of files.sort()) {
  const rel = path.relative(ROOT, file);
  const source = fs.readFileSync(file, 'utf8');
  try {
    parse(source);
  } catch (error) {
    failures += 1;
    const loc = error.loc || {};
    const line = loc.line ?? '?';
    const column = loc.column ?? '?';
    console.error(`✗ ${rel}:${line}:${column}  ${String(error.message).split('\n')[0]}`);
    const lines = source.split('\n');
    const start = Math.max(0, (Number(line) || 1) - 3);
    for (let i = start; i < Math.min(lines.length, start + 4); i++) {
      console.error(`   ${String(i + 1).padStart(4)} | ${lines[i]}`);
    }
  }
}
console.log(`\nПроверено файлов: ${files.length}, ошибок: ${failures}`);
process.exit(failures === 0 ? 0 : 1);
