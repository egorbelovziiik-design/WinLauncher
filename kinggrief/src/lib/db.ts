import { DatabaseSync } from 'node:sqlite';
import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {
  CASE_GROUPS,
  CASE_ITEMS,
  DEFAULT_SETTINGS,
  PRODUCTS,
} from './catalog';

const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  name TEXT NOT NULL,
  subtitle TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  price INTEGER NOT NULL,
  old_price INTEGER,
  color TEXT NOT NULL DEFAULT '#A855F7',
  icon TEXT NOT NULL DEFAULT 'crown',
  badge TEXT,
  features TEXT NOT NULL DEFAULT '[]',
  meta TEXT NOT NULL DEFAULT '{}',
  sort INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS cases (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  color TEXT NOT NULL DEFAULT '#A855F7',
  icon TEXT NOT NULL DEFAULT 'box',
  sort INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS case_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  case_id TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  link TEXT,
  rarity TEXT NOT NULL DEFAULT 'common',
  chance REAL NOT NULL DEFAULT 0,
  icon TEXT NOT NULL DEFAULT 'coins',
  color TEXT NOT NULL DEFAULT '#94A3B8',
  sort INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_case_items_case ON case_items(case_id);

CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,
  public_id TEXT NOT NULL UNIQUE,
  nickname TEXT NOT NULL,
  email TEXT,
  status TEXT NOT NULL DEFAULT 'pending_payment',
  subtotal INTEGER NOT NULL,
  discount INTEGER NOT NULL DEFAULT 0,
  total INTEGER NOT NULL,
  promo_code TEXT,
  provider TEXT NOT NULL DEFAULT 'mock',
  payment_id TEXT,
  payment_url TEXT,
  items TEXT NOT NULL,
  note TEXT,
  is_demo INTEGER NOT NULL DEFAULT 1,
  user_id INTEGER,
  ip TEXT,
  created_at TEXT NOT NULL,
  paid_at TEXT,
  delivered_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at);

CREATE TABLE IF NOT EXISTS payments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_payment_id TEXT,
  amount INTEGER NOT NULL,
  status TEXT NOT NULL,
  raw TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);

CREATE TABLE IF NOT EXISTS deliveries (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  nickname TEXT NOT NULL,
  command TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TEXT NOT NULL,
  sent_at TEXT,
  delivered_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_deliveries_status ON deliveries(status);

CREATE TABLE IF NOT EXISTS promos (
  code TEXT PRIMARY KEY,
  type TEXT NOT NULL DEFAULT 'percent',
  value INTEGER NOT NULL,
  min_total INTEGER NOT NULL DEFAULT 0,
  max_discount INTEGER,
  usage_limit INTEGER,
  used_count INTEGER NOT NULL DEFAULT 0,
  categories TEXT,
  expires_at TEXT,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS promo_uses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL,
  order_id TEXT NOT NULL,
  discount INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reviews (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nickname TEXT NOT NULL,
  rating INTEGER NOT NULL DEFAULT 5,
  text TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL COLLATE NOCASE UNIQUE,
  email TEXT NOT NULL COLLATE NOCASE UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  last_login_at TEXT
);

CREATE TABLE IF NOT EXISTS admin_users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'owner',
  totp_secret TEXT,
  totp_enabled INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  admin_id INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  ip TEXT,
  ua TEXT
);

CREATE TABLE IF NOT EXISTS audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor TEXT NOT NULL,
  action TEXT NOT NULL,
  details TEXT NOT NULL DEFAULT '',
  ip TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS event_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL,
  message TEXT NOT NULL,
  created_at TEXT NOT NULL
);
`;

export function dbPath() {
  // На serverless-хостингах (Vercel) файловая система только для чтения, кроме /tmp.
  const fallback = process.env.VERCEL ? '/tmp/kinggrief.db' : './data/kinggrief.db';
  const p = process.env.DATABASE_PATH || fallback;
  return path.isAbsolute(p) ? p : path.join(process.cwd(), p);
}

export function hashPassword(password: string): string {
  const salt = randomBytes(16).toString('hex');
  const hash = scryptSync(password, salt, 64).toString('hex');
  return `scrypt$${salt}$${hash}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  try {
    const [algo, salt, hash] = stored.split('$');
    if (algo !== 'scrypt' || !salt || !hash) return false;
    const candidate = scryptSync(password, salt, 64);
    const expected = Buffer.from(hash, 'hex');
    return candidate.length === expected.length && timingSafeEqual(candidate, expected);
  } catch {
    return false;
  }
}

function now() {
  return new Date().toISOString();
}

function hasColumn(db: DatabaseSync, table: string, column: string) {
  const rows = db.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name?: unknown }>;
  return rows.some((row) => String(row.name) === column);
}

/** Догоняющие миграции для баз, созданных более ранними версиями. */
function migrate(db: DatabaseSync) {
  if (!hasColumn(db, 'case_items', 'description')) {
    db.exec("ALTER TABLE case_items ADD COLUMN description TEXT NOT NULL DEFAULT ''");
  }
  if (!hasColumn(db, 'case_items', 'link')) {
    db.exec('ALTER TABLE case_items ADD COLUMN link TEXT');
  }
  if (!hasColumn(db, 'orders', 'user_id')) {
    db.exec('ALTER TABLE orders ADD COLUMN user_id INTEGER');
  }
  if (!hasColumn(db, 'deliveries', 'sent_at')) {
    // Нужна, чтобы повторять выдачу, если плагин не подтвердил выполнение команды.
    db.exec('ALTER TABLE deliveries ADD COLUMN sent_at TEXT');
  }
  db.exec('CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id)');
}

export function openDb(): DatabaseSync {
  const file = dbPath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec(SCHEMA);
  migrate(db);
  seed(db);
  return db;
}

function seed(db: DatabaseSync) {
  const settingsCount = db.prepare('SELECT COUNT(*) AS c FROM settings').get() as { c: number };
  if (settingsCount.c === 0) {
    const insert = db.prepare('INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)');
    for (const [k, v] of Object.entries(DEFAULT_SETTINGS)) insert.run(k, v);
  } else {
    // Обновляем устаревший дефолтный IP play.kinggrief.net → kinggrief.pickme.cloud.
    db.prepare("UPDATE settings SET value = 'kinggrief.pickme.cloud' WHERE key = 'server_ip' AND value = 'play.kinggrief.net'").run();
    db.prepare(
      "UPDATE settings SET value = REPLACE(value, 'play.kinggrief.net', 'kinggrief.pickme.cloud') WHERE key = 'announcement' AND value LIKE '%play.kinggrief.net%'",
    ).run();
    db.prepare(
      "UPDATE settings SET value = REPLACE(value, 'cases give {player} {case} {amount}', 'dc givekey {player} {case} {amount}') WHERE key = 'delivery_commands' AND value LIKE '%cases give {player} {case} {amount}%'",
    ).run();
  }

  const productCount = db.prepare('SELECT COUNT(*) AS c FROM products').get() as { c: number };
  if (productCount.c === 0) {
    const insert = db.prepare(`INSERT INTO products
      (id, kind, name, subtitle, description, price, old_price, color, icon, badge, features, meta, sort, active)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`);
    for (const p of PRODUCTS) {
      insert.run(
        p.id,
        p.kind,
        p.name,
        p.subtitle,
        p.description,
        p.price,
        p.oldPrice,
        p.color,
        p.icon,
        p.badge,
        JSON.stringify(p.features),
        JSON.stringify(p.meta),
        p.sort,
        p.active ? 1 : 0,
      );
    }
  } else {
    // Синхронизируем реальные серверные кейсы (donate, sdonate, legendary, riliks, ditems, kits, money, allorno, djamshut, reallydev, summer).
    const hasLegacySuper = db.prepare("SELECT 1 FROM cases WHERE id IN ('super', 'mega') LIMIT 1").get();
    if (hasLegacySuper) {
      db.exec("DELETE FROM case_items WHERE case_id IN ('super', 'mega')");
      db.exec("DELETE FROM cases WHERE id IN ('super', 'mega')");
      db.exec("DELETE FROM products WHERE id LIKE 'case-super-%' OR id LIKE 'case-mega-%'");
    }
    const insertProduct = db.prepare(`INSERT OR IGNORE INTO products
      (id, kind, name, subtitle, description, price, old_price, color, icon, badge, features, meta, sort, active)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`);
    for (const p of PRODUCTS.filter((x) => x.kind === 'case')) {
      insertProduct.run(
        p.id,
        p.kind,
        p.name,
        p.subtitle,
        p.description,
        p.price,
        p.oldPrice,
        p.color,
        p.icon,
        p.badge,
        JSON.stringify(p.features),
        JSON.stringify(p.meta),
        p.sort,
        p.active ? 1 : 0,
      );
    }
  }

  const caseCount = db.prepare('SELECT COUNT(*) AS c FROM cases').get() as { c: number };
  if (caseCount.c === 0) {
    const insert = db.prepare('INSERT INTO cases (id, name, description, color, icon, sort, active) VALUES (?, ?, ?, ?, ?, ?, ?)');
    for (const c of CASE_GROUPS) insert.run(c.id, c.name, c.description, c.color, c.icon, c.sort, c.active ? 1 : 0);
    const insertItem = db.prepare(
      'INSERT INTO case_items (case_id, name, description, link, rarity, chance, icon, color, sort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
    );
    for (const i of CASE_ITEMS)
      insertItem.run(i.caseId, i.name, i.description, i.link ?? null, i.rarity, i.chance, i.icon, i.color, i.sort);
  } else {
    const insertCase = db.prepare(
      'INSERT OR IGNORE INTO cases (id, name, description, color, icon, sort, active) VALUES (?, ?, ?, ?, ?, ?, ?)',
    );
    const countCaseItems = db.prepare('SELECT COUNT(*) AS c FROM case_items WHERE case_id = ?');
    const insertItem = db.prepare(
      'INSERT INTO case_items (case_id, name, description, link, rarity, chance, icon, color, sort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
    );
    for (const c of CASE_GROUPS) {
      insertCase.run(c.id, c.name, c.description, c.color, c.icon, c.sort, c.active ? 1 : 0);
      const existingItems = (countCaseItems.get(c.id) as { c: number }).c;
      if (existingItems === 0) {
        for (const i of CASE_ITEMS.filter((item) => item.caseId === c.id)) {
          insertItem.run(i.caseId, i.name, i.description, i.link ?? null, i.rarity, i.chance, i.icon, i.color, i.sort);
        }
      }
    }
    // Базы старых версий: дописываем описания наград, сохраняя отредактированные вручную шансы.
    const update = db.prepare(
      "UPDATE case_items SET description = ?, link = COALESCE(link, ?) WHERE case_id = ? AND name = ? AND (description IS NULL OR description = '')",
    );
    for (const i of CASE_ITEMS) update.run(i.description, i.link ?? null, i.caseId, i.name);
  }

  const adminCount = db.prepare('SELECT COUNT(*) AS c FROM admin_users').get() as { c: number };
  if (adminCount.c === 0) {
    const username = process.env.ADMIN_USERNAME || 'admin';
    const password = process.env.ADMIN_PASSWORD || 'KingGrief-2026';
    db.prepare(
      'INSERT INTO admin_users (username, password_hash, role, totp_enabled, created_at) VALUES (?, ?, ?, 0, ?)',
    ).run(username, hashPassword(password), 'owner', now());
  }

  const promoCount = db.prepare('SELECT COUNT(*) AS c FROM promos').get() as { c: number };
  if (promoCount.c === 0) {
    const expires = new Date(Date.now() + 1000 * 60 * 60 * 24 * 120).toISOString();
    const insert = db.prepare(`INSERT INTO promos
      (code, type, value, min_total, max_discount, usage_limit, used_count, categories, expires_at, active)
      VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, 1)`);
    insert.run('KING10', 'percent', 10, 0, 500, 1000, null, expires);
    insert.run('WELCOME', 'percent', 15, 300, 700, null, null, expires);
    insert.run('GRIEF20', 'percent', 20, 500, 1500, 500, null, expires);
  }
}

const globalForDb = globalThis as unknown as { __kgDb?: DatabaseSync };

export function getDb(): DatabaseSync {
  if (!globalForDb.__kgDb) globalForDb.__kgDb = openDb();
  return globalForDb.__kgDb;
}
