#!/usr/bin/env node
/**
 * Резервная копия SQLite без остановки сервиса: VACUUM INTO создаёт консистентный снимок.
 *   npm run db:backup            → backups/kinggrief-<timestamp>.db
 *   npm run db:backup -- path.db
 */
import { DatabaseSync } from 'node:sqlite';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';

const source = process.env.DATABASE_PATH || './data/kinggrief.db';
if (!existsSync(source)) {
  console.error(`База не найдена: ${source}`);
  process.exit(1);
}

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const target = process.argv[2] || path.join('backups', `kinggrief-${stamp}.db`);
mkdirSync(path.dirname(target), { recursive: true });

const db = new DatabaseSync(source);
db.exec(`VACUUM INTO '${target.replace(/'/g, "''")}'`);
db.close();
console.log(`Резервная копия создана: ${target}`);
