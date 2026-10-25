#!/usr/bin/env node
/**
 * Продакшн-запуск standalone-сборки Next.js.
 *
 * `next start` не поддерживает output: 'standalone', поэтому копируем статику
 * в standalone-каталог (как это делает Dockerfile) и запускаем сервер напрямую.
 *
 *   npm run build && npm start
 *   PORT=8080 BIND_HOST=0.0.0.0 npm start
 */
import { spawn } from 'node:child_process';
import { cpSync, existsSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const standalone = path.join(root, '.next', 'standalone');
const server = path.join(standalone, 'server.js');

if (!existsSync(server)) {
  console.error('Standalone-сборка не найдена. Сначала выполните: npm run build');
  process.exit(1);
}

const publicDir = path.join(root, 'public');
if (existsSync(publicDir)) cpSync(publicDir, path.join(standalone, 'public'), { recursive: true });

const staticDir = path.join(root, '.next', 'static');
if (existsSync(staticDir)) cpSync(staticDir, path.join(standalone, '.next', 'static'), { recursive: true });

const port = process.env.PORT || '3000';
const host = process.env.BIND_HOST || '0.0.0.0';
console.log(`KINGGRIEF → http://${host}:${port}`);

const child = spawn(process.execPath, [server], {
  stdio: 'inherit',
  env: { ...process.env, PORT: port, HOSTNAME: host },
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
child.on('exit', (code) => process.exit(code ?? 0));
