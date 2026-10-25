#!/usr/bin/env node
/**
 * Полный сброс локальной базы: удаляет файл SQLite вместе с WAL.
 * Схема и стартовый каталог пересоздаются автоматически при следующем запросе к сайту.
 *
 * Использование:
 *   npm run db:reset          # удалить текущую базу
 *   npm run db:reset -- --yes # без подтверждения (для CI)
 */
import { existsSync, rmSync } from 'node:fs';
import { createInterface } from 'node:readline';

const dbPath = process.env.DATABASE_PATH || './data/kinggrief.db';
const files = [dbPath, `${dbPath}-wal`, `${dbPath}-shm`, `${dbPath}-journal`].filter(existsSync);

if (files.length === 0) {
  console.log(`База не найдена: ${dbPath}. Ничего не удалено.`);
  process.exit(0);
}

console.log('Будут удалены файлы:');
files.forEach((file) => console.log(`  - ${file}`));

async function confirm() {
  if (process.argv.includes('--yes') || process.env.CI) return true;
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const answer = await new Promise((resolve) => rl.question('Удалить данные? [y/N] ', resolve));
  rl.close();
  return /^y(es)?$/i.test(String(answer).trim());
}

const approved = await confirm();
if (!approved) {
  console.log('Отменено.');
  process.exit(1);
}

for (const file of files) {
  rmSync(file, { force: true });
  console.log(`Удалено: ${file}`);
}
console.log('\nГотово. Каталог, настройки и администратор будут созданы заново при первом запросе к сайту.');
