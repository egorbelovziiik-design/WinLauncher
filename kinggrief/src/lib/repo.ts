import { randomBytes, randomUUID } from 'node:crypto';
import { hashPassword, verifyPassword, getDb } from './db';
import type {
  AdminUser,
  AuditEntry,
  CaseGroup,
  CaseItem,
  Delivery,
  Order,
  OrderItem,
  OrderStatus,
  Product,
  Promo,
  PublicUser,
  Review,
  UserRecord,
  SettingsMap,
} from './types';

type Row = Record<string, unknown>;
const now = () => new Date().toISOString();
const num = (v: unknown) => Number(v ?? 0);
const str = (v: unknown) => (v == null ? '' : String(v));
const bool = (v: unknown) => Boolean(v);

/* ─────────────────────────── settings ─────────────────────────── */

export function getSettings(): SettingsMap {
  const rows = getDb().prepare('SELECT key, value FROM settings').all() as Row[];
  const map: SettingsMap = {};
  for (const r of rows) map[str(r.key)] = str(r.value);
  return map;
}

export function getSetting(key: string, fallback = ''): string {
  const row = getDb().prepare('SELECT value FROM settings WHERE key = ?').get(key) as Row | undefined;
  return row ? str(row.value) : fallback;
}

export function setSettings(patch: SettingsMap) {
  const stmt = getDb().prepare(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
  );
  for (const [k, v] of Object.entries(patch)) stmt.run(k, String(v));
}

/* ─────────────────────────── products ─────────────────────────── */

function mapProduct(r: Row): Product {
  return {
    id: str(r.id),
    kind: str(r.kind) as Product['kind'],
    name: str(r.name),
    subtitle: str(r.subtitle),
    description: str(r.description),
    price: num(r.price),
    oldPrice: r.old_price == null ? null : num(r.old_price),
    color: str(r.color),
    icon: str(r.icon),
    badge: r.badge == null ? null : str(r.badge),
    features: JSON.parse(str(r.features) || '[]'),
    meta: JSON.parse(str(r.meta) || '{}'),
    sort: num(r.sort),
    active: bool(r.active),
  };
}

export function listProducts(opts: { kind?: string; activeOnly?: boolean } = {}): Product[] {
  let sql = 'SELECT * FROM products';
  const where: string[] = [];
  const params: Array<string | number> = [];
  if (opts.kind) {
    where.push('kind = ?');
    params.push(opts.kind);
  }
  if (opts.activeOnly) where.push('active = 1');
  if (where.length) sql += ' WHERE ' + where.join(' AND ');
  sql += ' ORDER BY sort ASC, price ASC';
  return (getDb().prepare(sql).all(...params) as Row[]).map(mapProduct);
}

export function getProduct(id: string): Product | null {
  const row = getDb().prepare('SELECT * FROM products WHERE id = ?').get(id) as Row | undefined;
  return row ? mapProduct(row) : null;
}

export type ProductInput = Partial<Omit<Product, 'meta'>> & { meta?: Record<string, unknown> };

export function saveProduct(input: ProductInput & { id: string }) {
  const existing = getProduct(input.id);
  const merged: Product = {
    id: input.id,
    kind: input.kind ?? existing?.kind ?? 'rank',
    name: input.name ?? existing?.name ?? 'Новый товар',
    subtitle: input.subtitle ?? existing?.subtitle ?? '',
    description: input.description ?? existing?.description ?? '',
    price: Math.max(0, Math.round(input.price ?? existing?.price ?? 0)),
    oldPrice: input.oldPrice === undefined ? (existing?.oldPrice ?? null) : input.oldPrice,
    color: input.color ?? existing?.color ?? '#A855F7',
    icon: input.icon ?? existing?.icon ?? 'crown',
    badge: input.badge === undefined ? (existing?.badge ?? null) : input.badge,
    features: input.features ?? existing?.features ?? [],
    meta: input.meta ?? existing?.meta ?? {},
    sort: input.sort ?? existing?.sort ?? 500,
    active: input.active ?? existing?.active ?? true,
  };
  getDb()
    .prepare(
      `INSERT INTO products (id, kind, name, subtitle, description, price, old_price, color, icon, badge, features, meta, sort, active)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET kind=excluded.kind, name=excluded.name, subtitle=excluded.subtitle,
         description=excluded.description, price=excluded.price, old_price=excluded.old_price, color=excluded.color,
         icon=excluded.icon, badge=excluded.badge, features=excluded.features, meta=excluded.meta,
         sort=excluded.sort, active=excluded.active`,
    )
    .run(
      merged.id,
      merged.kind,
      merged.name,
      merged.subtitle,
      merged.description,
      merged.price,
      merged.oldPrice,
      merged.color,
      merged.icon,
      merged.badge,
      JSON.stringify(merged.features),
      JSON.stringify(merged.meta),
      merged.sort,
      merged.active ? 1 : 0,
    );
  return merged;
}

export function deleteProduct(id: string) {
  getDb().prepare('DELETE FROM products WHERE id = ?').run(id);
}

/* ─────────────────────────── cases ─────────────────────────── */

export function listCaseGroups(activeOnly = false): CaseGroup[] {
  const rows = getDb()
    .prepare(`SELECT * FROM cases ${activeOnly ? 'WHERE active = 1' : ''} ORDER BY sort ASC`)
    .all() as Row[];
  return rows.map((r) => ({
    id: str(r.id),
    name: str(r.name),
    description: str(r.description),
    color: str(r.color),
    icon: str(r.icon),
    sort: num(r.sort),
    active: bool(r.active),
  }));
}

export function getCaseGroup(id: string): CaseGroup | null {
  return listCaseGroups().find((c) => c.id === id) ?? null;
}

export function saveCaseGroup(input: Partial<CaseGroup> & { id: string }) {
  const existing = getCaseGroup(input.id);
  const merged = {
    id: input.id,
    name: input.name ?? existing?.name ?? 'Кейс',
    description: input.description ?? existing?.description ?? '',
    color: input.color ?? existing?.color ?? '#A855F7',
    icon: input.icon ?? existing?.icon ?? 'box',
    sort: input.sort ?? existing?.sort ?? 99,
    active: input.active ?? existing?.active ?? true,
  };
  getDb()
    .prepare(
      `INSERT INTO cases (id, name, description, color, icon, sort, active) VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET name=excluded.name, description=excluded.description, color=excluded.color,
       icon=excluded.icon, sort=excluded.sort, active=excluded.active`,
    )
    .run(merged.id, merged.name, merged.description, merged.color, merged.icon, merged.sort, merged.active ? 1 : 0);
  return merged;
}

export function deleteCaseGroup(id: string) {
  getDb().prepare('DELETE FROM case_items WHERE case_id = ?').run(id);
  getDb().prepare('DELETE FROM cases WHERE id = ?').run(id);
}

function mapCaseItem(r: Row): CaseItem {
  return {
    id: num(r.id),
    caseId: str(r.case_id),
    name: str(r.name),
    description: str(r.description),
    link: r.link == null ? null : str(r.link),
    rarity: str(r.rarity) as CaseItem['rarity'],
    chance: num(r.chance),
    icon: str(r.icon),
    color: str(r.color),
    sort: num(r.sort),
  };
}

export function listCaseItems(caseId?: string): CaseItem[] {
  const sql = caseId
    ? 'SELECT * FROM case_items WHERE case_id = ? ORDER BY sort ASC, chance DESC'
    : 'SELECT * FROM case_items ORDER BY case_id ASC, sort ASC';
  const rows = (caseId ? getDb().prepare(sql).all(caseId) : getDb().prepare(sql).all()) as Row[];
  return rows.map(mapCaseItem);
}

export function saveCaseItem(item: Omit<CaseItem, 'id'> & { id?: number }) {
  const db = getDb();
  if (item.id) {
    db.prepare(
      'UPDATE case_items SET name=?, description=?, link=?, rarity=?, chance=?, icon=?, color=?, sort=? WHERE id=?',
    ).run(
      item.name,
      item.description ?? '',
      item.link ?? null,
      item.rarity,
      item.chance,
      item.icon,
      item.color,
      item.sort,
      item.id,
    );
    return item.id;
  }
  const res = db
    .prepare(
      'INSERT INTO case_items (case_id, name, description, link, rarity, chance, icon, color, sort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
    )
    .run(
      item.caseId,
      item.name,
      item.description ?? '',
      item.link ?? null,
      item.rarity,
      item.chance,
      item.icon,
      item.color,
      item.sort,
    );
  return Number(res.lastInsertRowid);
}

export function deleteCaseItem(id: number) {
  getDb().prepare('DELETE FROM case_items WHERE id = ?').run(id);
}

export function casesWithItems() {
  return listCaseGroups().map((group) => ({
    ...group,
    items: listCaseItems(group.id),
    products: listProducts({ kind: 'case' }).filter((p) => p.meta.group === group.id),
  }));
}

export function caseChanceTotal(caseId: string) {
  return listCaseItems(caseId).reduce((sum, i) => sum + i.chance, 0);
}

/* ─────────────────────────── promos ─────────────────────────── */

export function getServerPromoRewardsMap(): Record<string, NonNullable<Promo['serverReward']>> {
  const raw = getSetting('server_promo_rewards', '');
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, NonNullable<Promo['serverReward']>>;
  } catch {
    return {};
  }
}

export function saveServerPromoReward(
  code: string,
  reward: Partial<NonNullable<Promo['serverReward']>>,
): Record<string, NonNullable<Promo['serverReward']>> {
  const map = getServerPromoRewardsMap();
  const key = code.trim().toUpperCase();
  map[key] = {
    enabled: reward.enabled ?? true,
    coins: Math.max(0, Math.round(Number(reward.coins ?? 15000))),
    relics: Math.max(0, Math.round(Number(reward.relics ?? 15))),
    caseId: String(reward.caseId ?? '').trim().toLowerCase(),
    caseQty: Math.max(0, Math.round(Number(reward.caseQty ?? (reward.caseId ? 1 : 0)))),
    rankId: String(reward.rankId ?? '').trim().toLowerCase(),
  };
  setSettings({ server_promo_rewards: JSON.stringify(map) });
  return map;
}

export function deleteServerPromoReward(code: string): Record<string, NonNullable<Promo['serverReward']>> {
  const map = getServerPromoRewardsMap();
  delete map[code.trim().toUpperCase()];
  setSettings({ server_promo_rewards: JSON.stringify(map) });
  return map;
}

function mapPromo(r: Row): Promo {
  const code = str(r.code);
  const val = num(r.value);
  const rewardsMap = getServerPromoRewardsMap();
  const sr = rewardsMap[code.toUpperCase()] ?? {
    enabled: true,
    coins: Math.max(10000, val * 1000),
    relics: Math.max(10, val),
    caseId: '',
    caseQty: 0,
    rankId: '',
  };
  return {
    code,
    type: str(r.type) as Promo['type'],
    value: val,
    minTotal: num(r.min_total),
    maxDiscount: r.max_discount == null ? null : num(r.max_discount),
    usageLimit: r.usage_limit == null ? null : num(r.usage_limit),
    usedCount: num(r.used_count),
    categories: r.categories ? JSON.parse(str(r.categories)) : null,
    expiresAt: r.expires_at == null ? null : str(r.expires_at),
    active: bool(r.active),
    serverReward: sr,
  };
}

export function listPromos(): Promo[] {
  return (getDb().prepare('SELECT * FROM promos ORDER BY code ASC').all() as Row[]).map(mapPromo);
}

export function getPromo(code: string): Promo | null {
  const row = getDb().prepare('SELECT * FROM promos WHERE UPPER(code) = UPPER(?)').get(code) as Row | undefined;
  return row ? mapPromo(row) : null;
}

export function savePromo(input: Partial<Promo> & { code: string }) {
  const code = input.code.trim().toUpperCase();
  const existing = getPromo(code);
  const merged: Promo = {
    code,
    type: input.type ?? existing?.type ?? 'percent',
    value: Math.max(0, Math.round(input.value ?? existing?.value ?? 0)),
    minTotal: Math.max(0, Math.round(input.minTotal ?? existing?.minTotal ?? 0)),
    maxDiscount: input.maxDiscount === undefined ? (existing?.maxDiscount ?? null) : input.maxDiscount,
    usageLimit: input.usageLimit === undefined ? (existing?.usageLimit ?? null) : input.usageLimit,
    usedCount: existing?.usedCount ?? 0,
    categories: input.categories === undefined ? (existing?.categories ?? null) : input.categories,
    expiresAt: input.expiresAt === undefined ? (existing?.expiresAt ?? null) : input.expiresAt,
    active: input.active ?? existing?.active ?? true,
  };
  getDb()
    .prepare(
      `INSERT INTO promos (code, type, value, min_total, max_discount, usage_limit, used_count, categories, expires_at, active)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(code) DO UPDATE SET type=excluded.type, value=excluded.value, min_total=excluded.min_total,
       max_discount=excluded.max_discount, usage_limit=excluded.usage_limit, categories=excluded.categories,
       expires_at=excluded.expires_at, active=excluded.active`,
    )
    .run(
      merged.code,
      merged.type,
      merged.value,
      merged.minTotal,
      merged.maxDiscount,
      merged.usageLimit,
      merged.usedCount,
      merged.categories ? JSON.stringify(merged.categories) : null,
      merged.expiresAt,
      merged.active ? 1 : 0,
    );
  return merged;
}

export function deletePromo(code: string) {
  getDb().prepare('DELETE FROM promos WHERE UPPER(code) = UPPER(?)').run(code);
}

export type PromoResult =
  | { ok: true; promo: Promo; discount: number }
  | { ok: false; error: string };

export function applyPromo(code: string, subtotal: number, kinds: string[]): PromoResult {
  const promo = getPromo(code.trim());
  if (!promo) return { ok: false, error: 'Промокод не найден' };
  if (!promo.active) return { ok: false, error: 'Промокод отключён' };
  if (promo.expiresAt && new Date(promo.expiresAt).getTime() < Date.now())
    return { ok: false, error: 'Срок действия промокода истёк' };
  if (promo.usageLimit != null && promo.usedCount >= promo.usageLimit)
    return { ok: false, error: 'Лимит использований промокода исчерпан' };
  if (subtotal < promo.minTotal)
    return { ok: false, error: `Минимальная сумма заказа — ${promo.minTotal} ₽` };
  if (promo.categories && promo.categories.length > 0) {
    const applicable = kinds.some((k) => promo.categories!.includes(k));
    if (!applicable) return { ok: false, error: 'Промокод не действует для этих товаров' };
  }
  let discount = promo.type === 'percent' ? Math.floor((subtotal * promo.value) / 100) : promo.value;
  if (promo.maxDiscount != null) discount = Math.min(discount, promo.maxDiscount);
  discount = Math.min(discount, subtotal);
  if (discount <= 0) return { ok: false, error: 'Промокод не даёт скидку для этого заказа' };
  return { ok: true, promo, discount };
}

export function registerPromoUse(code: string, orderId: string, discount: number) {
  const db = getDb();
  db.prepare('UPDATE promos SET used_count = used_count + 1 WHERE UPPER(code) = UPPER(?)').run(code);
  db.prepare('INSERT INTO promo_uses (code, order_id, discount, created_at) VALUES (?, ?, ?, ?)').run(
    code.toUpperCase(),
    orderId,
    discount,
    now(),
  );
}

/* ─────────────────────────── orders ─────────────────────────── */

function mapOrder(r: Row): Order {
  return {
    id: str(r.id),
    publicId: str(r.public_id),
    nickname: str(r.nickname),
    email: r.email == null ? null : str(r.email),
    status: str(r.status) as OrderStatus,
    subtotal: num(r.subtotal),
    discount: num(r.discount),
    total: num(r.total),
    promoCode: r.promo_code == null ? null : str(r.promo_code),
    provider: str(r.provider),
    paymentId: r.payment_id == null ? null : str(r.payment_id),
    paymentUrl: r.payment_url == null ? null : str(r.payment_url),
    items: JSON.parse(str(r.items) || '[]'),
    note: r.note == null ? null : str(r.note),
    userId: r.user_id == null ? null : num(r.user_id),
    isDemo: bool(r.is_demo),
    createdAt: str(r.created_at),
    paidAt: r.paid_at == null ? null : str(r.paid_at),
    deliveredAt: r.delivered_at == null ? null : str(r.delivered_at),
  };
}

export function publicOrderId() {
  const stamp = Date.now().toString(36).toUpperCase().slice(-5);
  const suffix = randomBytes(2).toString('hex').toUpperCase();
  return `KG-${stamp}${suffix}`;
}

export function createOrder(input: {
  nickname: string;
  email?: string | null;
  items: OrderItem[];
  subtotal: number;
  discount: number;
  total: number;
  promoCode?: string | null;
  provider: string;
  isDemo: boolean;
  note?: string | null;
  ip?: string | null;
  userId?: number | null;
}): Order {
  const db = getDb();
  const id = randomUUID();
  const createdAt = now();
  db.prepare(
    `INSERT INTO orders (id, public_id, nickname, email, status, subtotal, discount, total, promo_code, provider,
      payment_id, payment_url, items, note, is_demo, user_id, ip, created_at)
     VALUES (?, ?, ?, ?, 'pending_payment', ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?)`,
  ).run(
    id,
    publicOrderId(),
    input.nickname,
    input.email ?? null,
    input.subtotal,
    input.discount,
    input.total,
    input.promoCode ?? null,
    input.provider,
    JSON.stringify(input.items),
    input.note ?? null,
    input.isDemo ? 1 : 0,
    input.userId ?? null,
    input.ip ?? null,
    createdAt,
  );
  return getOrder(id)!;
}

export function getOrder(id: string): Order | null {
  const row = getDb().prepare('SELECT * FROM orders WHERE id = ? OR public_id = ?').get(id, id) as Row | undefined;
  return row ? mapOrder(row) : null;
}

export function listOrders(opts: { status?: string; q?: string; limit?: number } = {}): Order[] {
  const where: string[] = [];
  const params: Array<string | number> = [];
  if (opts.status && opts.status !== 'all') {
    where.push('status = ?');
    params.push(opts.status);
  }
  if (opts.q) {
    where.push('(nickname LIKE ? OR public_id LIKE ?)');
    params.push(`%${opts.q}%`, `%${opts.q}%`);
  }
  let sql = 'SELECT * FROM orders';
  if (where.length) sql += ' WHERE ' + where.join(' AND ');
  sql += ' ORDER BY created_at DESC LIMIT ?';
  params.push(opts.limit ?? 200);
  return (getDb().prepare(sql).all(...params) as Row[]).map(mapOrder);
}

export function setOrderPayment(orderId: string, patch: { paymentId?: string; paymentUrl?: string; provider?: string }) {
  const db = getDb();
  if (patch.paymentId !== undefined)
    db.prepare('UPDATE orders SET payment_id = ? WHERE id = ?').run(patch.paymentId, orderId);
  if (patch.paymentUrl !== undefined)
    db.prepare('UPDATE orders SET payment_url = ? WHERE id = ?').run(patch.paymentUrl, orderId);
  if (patch.provider !== undefined)
    db.prepare('UPDATE orders SET provider = ? WHERE id = ?').run(patch.provider, orderId);
}

export function updateOrderStatus(orderId: string, status: OrderStatus) {
  const db = getDb();
  db.prepare('UPDATE orders SET status = ? WHERE id = ?').run(status, orderId);
  if (status === 'paid') db.prepare('UPDATE orders SET paid_at = ? WHERE id = ? AND paid_at IS NULL').run(now(), orderId);
  if (status === 'delivered')
    db.prepare('UPDATE orders SET delivered_at = ? WHERE id = ?').run(now(), orderId);
}

export function recordPayment(input: {
  orderId: string;
  provider: string;
  providerPaymentId: string | null;
  amount: number;
  status: string;
  raw?: string;
}): number {
  const res = getDb()
    .prepare(
      'INSERT INTO payments (order_id, provider, provider_payment_id, amount, status, raw, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    )
    .run(input.orderId, input.provider, input.providerPaymentId, input.amount, input.status, input.raw ?? null, now());
  return Number(res.lastInsertRowid);
}

export function updatePaymentRecord(
  id: number,
  patch: { providerPaymentId?: string | null; status?: string; raw?: string | null },
) {
  const db = getDb();
  if (patch.providerPaymentId !== undefined)
    db.prepare('UPDATE payments SET provider_payment_id = ? WHERE id = ?').run(patch.providerPaymentId, id);
  if (patch.status !== undefined) db.prepare('UPDATE payments SET status = ? WHERE id = ?').run(patch.status, id);
  if (patch.raw !== undefined) db.prepare('UPDATE payments SET raw = ? WHERE id = ?').run(patch.raw, id);
}

export function latestPaymentForOrder(orderId: string) {
  const row = getDb()
    .prepare('SELECT * FROM payments WHERE order_id = ? ORDER BY id DESC LIMIT 1')
    .get(orderId) as Row | undefined;
  if (!row) return null;
  return { id: num(row.id), provider: str(row.provider), status: str(row.status), amount: num(row.amount) };
}

export function findPaymentByProviderId(provider: string, providerPaymentId: string) {
  const row = getDb()
    .prepare('SELECT * FROM payments WHERE provider = ? AND provider_payment_id = ? ORDER BY id DESC LIMIT 1')
    .get(provider, providerPaymentId) as Row | undefined;
  if (!row) return null;
  return { id: num(row.id), orderId: str(row.order_id), amount: num(row.amount), status: str(row.status) };
}

export function findPaymentById(id: number) {
  const row = getDb().prepare('SELECT * FROM payments WHERE id = ?').get(id) as Row | undefined;
  if (!row) return null;
  return { id: num(row.id), orderId: str(row.order_id), amount: num(row.amount), status: str(row.status) };
}

export function listPayments(orderId?: string) {
  const rows = orderId
    ? (getDb().prepare('SELECT * FROM payments WHERE order_id = ? ORDER BY id DESC').all(orderId) as Row[])
    : (getDb().prepare('SELECT * FROM payments ORDER BY id DESC LIMIT 300').all() as Row[]);
  return rows.map((r) => ({
    id: num(r.id),
    orderId: str(r.order_id),
    provider: str(r.provider),
    providerPaymentId: r.provider_payment_id == null ? null : str(r.provider_payment_id),
    amount: num(r.amount),
    status: str(r.status),
    createdAt: str(r.created_at),
  }));
}

/* ─────────────────────────── deliveries ─────────────────────────── */

function mapDelivery(r: Row): Delivery {
  return {
    id: str(r.id),
    orderId: str(r.order_id),
    productId: str(r.product_id),
    nickname: str(r.nickname),
    command: str(r.command),
    status: str(r.status) as Delivery['status'],
    attempts: num(r.attempts),
    lastError: r.last_error == null ? null : str(r.last_error),
    createdAt: str(r.created_at),
    deliveredAt: r.delivered_at == null ? null : str(r.delivered_at),
  };
}

export function createDelivery(input: {
  orderId: string;
  productId: string;
  nickname: string;
  command: string;
}): Delivery | null {
  const db = getDb();
  const existing = db
    .prepare('SELECT * FROM deliveries WHERE order_id = ? AND product_id = ?')
    .get(input.orderId, input.productId) as Row | undefined;
  if (existing) return mapDelivery(existing);
  const id = randomUUID();
  db.prepare(
    'INSERT INTO deliveries (id, order_id, product_id, nickname, command, status, attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?)',
  ).run(id, input.orderId, input.productId, input.nickname, input.command, 'pending', now());
  return mapDelivery(db.prepare('SELECT * FROM deliveries WHERE id = ?').get(id) as Row);
}

export function listDeliveries(opts: { status?: string; orderId?: string; limit?: number } = {}): Delivery[] {
  const where: string[] = [];
  const params: Array<string | number> = [];
  if (opts.status && opts.status !== 'all') {
    where.push('status = ?');
    params.push(opts.status);
  }
  if (opts.orderId) {
    where.push('order_id = ?');
    params.push(opts.orderId);
  }
  let sql = 'SELECT * FROM deliveries';
  if (where.length) sql += ' WHERE ' + where.join(' AND ');
  sql += ' ORDER BY created_at DESC LIMIT ?';
  params.push(opts.limit ?? 200);
  return (getDb().prepare(sql).all(...params) as Row[]).map(mapDelivery);
}

export function updateDelivery(
  id: string,
  patch: { status?: Delivery['status']; attempts?: number; lastError?: string | null },
) {
  const db = getDb();
  if (patch.status === 'delivered') {
    db.prepare('UPDATE deliveries SET status = ?, delivered_at = ?, attempts = attempts + 1 WHERE id = ?').run(
      patch.status,
      now(),
      id,
    );
    return;
  }
  if (patch.status) db.prepare('UPDATE deliveries SET status = ? WHERE id = ?').run(patch.status, id);
  if (patch.attempts !== undefined) db.prepare('UPDATE deliveries SET attempts = ? WHERE id = ?').run(patch.attempts, id);
  if (patch.lastError !== undefined)
    db.prepare('UPDATE deliveries SET last_error = ? WHERE id = ?').run(patch.lastError, id);
}

/** Отмечает выдачу отправленной плагину и запоминает время отправки. */
export function markDeliverySent(id: string) {
  getDb()
    .prepare("UPDATE deliveries SET status = 'sent', attempts = attempts + 1, sent_at = ? WHERE id = ?")
    .run(now(), id);
}

/**
 * Возвращает в очередь выдачи, которые плагин получил, но не подтвердил.
 * Без этого команда терялась бы при рестарте сервера между опросом и выполнением.
 * После `maxAttempts` попыток заказ уходит в ручную выдачу, чтобы покупка не пропала молча.
 */
export function requeueStaleDeliveries(timeoutSeconds = 120, maxAttempts = 5): { requeued: number; manual: number } {
  const db = getDb();
  const deadline = new Date(Date.now() - timeoutSeconds * 1000).toISOString();
  const requeued = db
    .prepare(
      "UPDATE deliveries SET status = 'pending', sent_at = NULL, last_error = 'Плагин не подтвердил выдачу — повтор' WHERE status = 'sent' AND sent_at IS NOT NULL AND sent_at < ? AND attempts < ?",
    )
    .run(deadline, maxAttempts);
  const manual = db
    .prepare(
      "UPDATE deliveries SET status = 'manual', last_error = 'Плагин не подтвердил выдачу' WHERE status = 'sent' AND sent_at IS NOT NULL AND sent_at < ? AND attempts >= ?",
    )
    .run(deadline, maxAttempts);
  return { requeued: Number(requeued.changes ?? 0), manual: Number(manual.changes ?? 0) };
}

/* ─────────────────────────── reviews ─────────────────────────── */

export function listReviews(status?: Review['status']): Review[] {
  const rows = (
    status
      ? getDb().prepare('SELECT * FROM reviews WHERE status = ? ORDER BY created_at DESC').all(status)
      : getDb().prepare('SELECT * FROM reviews ORDER BY created_at DESC').all()
  ) as Row[];
  return rows.map((r) => ({
    id: num(r.id),
    nickname: str(r.nickname),
    rating: num(r.rating),
    text: str(r.text),
    status: str(r.status) as Review['status'],
    createdAt: str(r.created_at),
  }));
}

export function createReview(input: { nickname: string; rating: number; text: string }) {
  getDb()
    .prepare("INSERT INTO reviews (nickname, rating, text, status, created_at) VALUES (?, ?, ?, 'pending', ?)")
    .run(input.nickname, input.rating, input.text, now());
}

export function setReviewStatus(id: number, status: Review['status']) {
  getDb().prepare('UPDATE reviews SET status = ? WHERE id = ?').run(status, id);
}

export function deleteReview(id: number) {
  getDb().prepare('DELETE FROM reviews WHERE id = ?').run(id);
}

/* ─────────────────────────── admin & audit ─────────────────────────── */

/* ─────────────────────────── игроки (аккаунты) ─────────────────────────── */

function mapUser(r: Row): PublicUser {
  return {
    id: num(r.id),
    username: str(r.username),
    email: str(r.email),
    createdAt: str(r.created_at),
    lastLoginAt: r.last_login_at == null ? null : str(r.last_login_at),
  };
}

function mapUserRecord(r: Row): UserRecord {
  return { ...mapUser(r), passwordHash: str(r.password_hash) };
}

export type CreateUserResult =
  | { ok: true; user: PublicUser }
  | { ok: false; error: string };

export function createUser(input: { username: string; email: string; password: string }): CreateUserResult {
  const db = getDb();
  const username = input.username.trim();
  const email = input.email.trim().toLowerCase();
  const taken = db
    .prepare('SELECT username, email FROM users WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE')
    .get(username, email) as Row | undefined;
  if (taken) {
    const nameMatches = str(taken.username).toLowerCase() === username.toLowerCase();
    return { ok: false, error: nameMatches ? 'Этот никнейм уже зарегистрирован' : 'Этот email уже зарегистрирован' };
  }
  try {
    const res = db
      .prepare('INSERT INTO users (username, email, password_hash, created_at) VALUES (?, ?, ?, ?)')
      .run(username, email, hashPassword(input.password), now());
    const user = getUserById(Number(res.lastInsertRowid));
    if (!user) return { ok: false, error: 'Не удалось создать аккаунт' };
    return { ok: true, user };
  } catch {
    return { ok: false, error: 'Этот никнейм или email уже зарегистрирован' };
  }
}

export function getUserById(id: number): PublicUser | null {
  const row = getDb().prepare('SELECT * FROM users WHERE id = ?').get(id) as Row | undefined;
  return row ? mapUser(row) : null;
}

export function getUserRecordByLogin(login: string): UserRecord | null {
  const value = login.trim();
  const row = getDb()
    .prepare('SELECT * FROM users WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE')
    .get(value, value.toLowerCase()) as Row | undefined;
  return row ? mapUserRecord(row) : null;
}

export function checkUserCredentials(login: string, password: string): PublicUser | null {
  const record = getUserRecordByLogin(login);
  if (!record) return null;
  if (!verifyPassword(password, record.passwordHash)) return null;
  getDb().prepare('UPDATE users SET last_login_at = ? WHERE id = ?').run(now(), record.id);
  const { passwordHash: _hash, ...user } = record;
  return { ...user, lastLoginAt: now() };
}

export function updateUserEmail(userId: number, email: string): { ok: boolean; error?: string } {
  const value = email.trim().toLowerCase();
  const taken = getDb()
    .prepare('SELECT id FROM users WHERE email = ? COLLATE NOCASE AND id <> ?')
    .get(value, userId) as Row | undefined;
  if (taken) return { ok: false, error: 'Этот email уже используется' };
  getDb().prepare('UPDATE users SET email = ? WHERE id = ?').run(value, userId);
  return { ok: true };
}

export function updateUserPassword(userId: number, password: string): boolean {
  getDb().prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hashPassword(password), userId);
  return true;
}

export function verifyUserPassword(userId: number, password: string): boolean {
  const record = getDb().prepare('SELECT password_hash FROM users WHERE id = ?').get(userId) as Row | undefined;
  return record ? verifyPassword(password, str(record.password_hash)) : false;
}

export function listUserOrders(userId: number, limit = 50): Order[] {
  return (
    getDb()
      .prepare('SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ?')
      .all(userId, limit) as Row[]
  ).map(mapOrder);
}

export function countUsers(): number {
  const row = getDb().prepare('SELECT COUNT(*) AS c FROM users').get() as Row;
  return num(row.c);
}

export function listUsers(limit = 100): PublicUser[] {
  return (getDb().prepare('SELECT * FROM users ORDER BY id DESC LIMIT ?').all(limit) as Row[]).map(mapUser);
}

export function userHasOrder(userId: number, orderId: string): boolean {
  const row = getDb()
    .prepare('SELECT id FROM orders WHERE user_id = ? AND (id = ? OR public_id = ?)')
    .get(userId, orderId, orderId) as Row | undefined;
  return Boolean(row);
}

export type AdminRow = AdminUser & { passwordHash: string; totpSecret: string | null };

export function getAdminByUsername(username: string): AdminRow | null {
  const r = getDb().prepare('SELECT * FROM admin_users WHERE username = ?').get(username) as Row | undefined;
  if (!r) return null;
  return {
    id: num(r.id),
    username: str(r.username),
    passwordHash: str(r.password_hash),
    role: str(r.role) as AdminUser['role'],
    totpEnabled: bool(r.totp_enabled),
    totpSecret: r.totp_secret == null ? null : str(r.totp_secret),
  };
}

export function getAdminById(id: number): AdminRow | null {
  const r = getDb().prepare('SELECT * FROM admin_users WHERE id = ?').get(id) as Row | undefined;
  if (!r) return null;
  return {
    id: num(r.id),
    username: str(r.username),
    passwordHash: str(r.password_hash),
    role: str(r.role) as AdminUser['role'],
    totpEnabled: bool(r.totp_enabled),
    totpSecret: r.totp_secret == null ? null : str(r.totp_secret),
  };
}

export function countAdmins() {
  return num((getDb().prepare('SELECT COUNT(*) AS c FROM admin_users').get() as Row).c);
}

export function createAdmin(username: string, password: string, role: AdminUser['role'] = 'admin') {
  getDb()
    .prepare('INSERT INTO admin_users (username, password_hash, role, totp_enabled, created_at) VALUES (?, ?, ?, 0, ?)')
    .run(username, hashPassword(password), role, now());
}

export function checkAdminCredentials(username: string, password: string): AdminRow | null {
  const admin = getAdminByUsername(username);
  if (!admin) return null;
  return verifyPassword(password, admin.passwordHash) ? admin : null;
}

export function setAdminTotpSecret(adminId: number, secret: string | null, enabled: boolean) {
  getDb()
    .prepare('UPDATE admin_users SET totp_secret = ?, totp_enabled = ? WHERE id = ?')
    .run(secret, enabled ? 1 : 0, adminId);
}

export function updateAdminPassword(adminId: number, password: string) {
  getDb().prepare('UPDATE admin_users SET password_hash = ? WHERE id = ?').run(hashPassword(password), adminId);
}

export function createSession(adminId: number, ip: string | null, ua: string | null) {
  const id = randomBytes(32).toString('hex');
  const expires = new Date(Date.now() + 1000 * 60 * 60 * 12).toISOString();
  getDb()
    .prepare('INSERT INTO sessions (id, admin_id, created_at, expires_at, ip, ua) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, adminId, now(), expires, ip, ua);
  return { id, expires };
}

export function getSession(token: string): { admin: AdminUser; expiresAt: string } | null {
  const row = getDb()
    .prepare(
      `SELECT s.id, s.expires_at, a.id AS admin_id, a.username, a.role, a.totp_enabled
       FROM sessions s JOIN admin_users a ON a.id = s.admin_id WHERE s.id = ?`,
    )
    .get(token) as Row | undefined;
  if (!row) return null;
  if (new Date(str(row.expires_at)).getTime() < Date.now()) {
    getDb().prepare('DELETE FROM sessions WHERE id = ?').run(token);
    return null;
  }
  return {
    admin: {
      id: num(row.admin_id),
      username: str(row.username),
      role: str(row.role) as AdminUser['role'],
      totpEnabled: bool(row.totp_enabled),
    },
    expiresAt: str(row.expires_at),
  };
}

export function deleteSession(token: string) {
  getDb().prepare('DELETE FROM sessions WHERE id = ?').run(token);
}

export function logAudit(actor: string, action: string, details: string, ip?: string | null) {
  getDb()
    .prepare('INSERT INTO audit_log (actor, action, details, ip, created_at) VALUES (?, ?, ?, ?, ?)')
    .run(actor, action, details, ip ?? null, now());
}

export function listAudit(limit = 100): AuditEntry[] {
  const rows = getDb().prepare('SELECT * FROM audit_log ORDER BY id DESC LIMIT ?').all(limit) as Row[];
  return rows.map((r) => ({
    id: num(r.id),
    actor: str(r.actor),
    action: str(r.action),
    details: str(r.details),
    ip: r.ip == null ? null : str(r.ip),
    createdAt: str(r.created_at),
  }));
}

export function logEvent(kind: string, message: string) {
  getDb().prepare('INSERT INTO event_log (kind, message, created_at) VALUES (?, ?, ?)').run(kind, message, now());
}

/* ─────────────────────────── analytics ─────────────────────────── */

const PAID_STATUSES = "('paid','delivering','delivered')";

export function revenueTotals(includeDemo = false) {
  const db = getDb();
  const demo = includeDemo ? '' : 'AND is_demo = 0';
  const row = db
    .prepare(
      `SELECT COUNT(*) AS orders, COALESCE(SUM(total), 0) AS revenue FROM orders WHERE status IN ${PAID_STATUSES} ${demo}`,
    )
    .get() as Row;
  const all = db.prepare(`SELECT COUNT(*) AS c FROM orders WHERE 1=1 ${demo}`).get() as Row;
  const startOfDay = new Date();
  startOfDay.setHours(0, 0, 0, 0);
  const today = db
    .prepare(`SELECT COALESCE(SUM(total), 0) AS revenue, COUNT(*) AS orders FROM orders
      WHERE status IN ${PAID_STATUSES} ${demo} AND created_at >= ?`)
    .get(startOfDay.toISOString()) as Row;
  const weekAgo = new Date(Date.now() - 7 * 86400000).toISOString();
  const week = db
    .prepare(
      `SELECT COALESCE(SUM(total), 0) AS revenue FROM orders WHERE status IN ${PAID_STATUSES} ${demo} AND created_at >= ?`,
    )
    .get(weekAgo) as Row;
  const monthAgo = new Date(Date.now() - 30 * 86400000).toISOString();
  const month = db
    .prepare(
      `SELECT COALESCE(SUM(total), 0) AS revenue FROM orders WHERE status IN ${PAID_STATUSES} ${demo} AND created_at >= ?`,
    )
    .get(monthAgo) as Row;
  return {
    totalRevenue: num(row.revenue),
    paidOrders: num(row.orders),
    allOrders: num(all.c),
    todayRevenue: num(today.revenue),
    todayOrders: num(today.orders),
    weekRevenue: num(week.revenue),
    monthRevenue: num(month.revenue),
  };
}

export function revenueSeries(days = 30, includeDemo = false) {
  const demo = includeDemo ? '' : 'AND is_demo = 0';
  const rows = getDb()
    .prepare(
      `SELECT substr(created_at, 1, 10) AS day, SUM(total) AS revenue, COUNT(*) AS orders
       FROM orders WHERE status IN ${PAID_STATUSES} ${demo} AND created_at >= ? GROUP BY day ORDER BY day ASC`,
    )
    .all(new Date(Date.now() - days * 86400000).toISOString()) as Row[];
  const map = new Map(rows.map((r) => [str(r.day), { revenue: num(r.revenue), orders: num(r.orders) }]));
  const out: Array<{ day: string; label: string; revenue: number; orders: number }> = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000);
    const key = d.toISOString().slice(0, 10);
    const hit = map.get(key);
    out.push({
      day: key,
      label: `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}`,
      revenue: hit?.revenue ?? 0,
      orders: hit?.orders ?? 0,
    });
  }
  return out;
}

export function popularProducts(limit = 6, includeDemo = false) {
  const demo = includeDemo ? '' : 'AND is_demo = 0';
  const orders = getDb()
    .prepare(`SELECT items FROM orders WHERE status IN ${PAID_STATUSES} ${demo}`)
    .all() as Row[];
  const counter = new Map<string, { name: string; kind: string; qty: number; revenue: number; color: string }>();
  for (const row of orders) {
    const items = JSON.parse(str(row.items) || '[]') as OrderItem[];
    for (const item of items) {
      const entry = counter.get(item.productId) ?? {
        name: item.name,
        kind: item.kind,
        qty: 0,
        revenue: 0,
        color: '#A855F7',
      };
      entry.qty += item.quantity;
      entry.revenue += item.total;
      counter.set(item.productId, entry);
    }
  }
  const products = new Map(listProducts().map((p) => [p.id, p]));
  return [...counter.entries()]
    .map(([id, v]) => ({ id, ...v, color: products.get(id)?.color ?? v.color }))
    .sort((a, b) => b.qty - a.qty)
    .slice(0, limit);
}

export function topPromos() {
  const rows = getDb()
    .prepare('SELECT code, COUNT(*) AS uses, SUM(discount) AS saved FROM promo_uses GROUP BY code ORDER BY uses DESC LIMIT 8')
    .all() as Row[];
  return rows.map((r) => ({ code: str(r.code), uses: num(r.uses), saved: num(r.saved) }));
}

export function topCustomers(limit = 8, includeDemo = false) {
  const demo = includeDemo ? '' : 'AND is_demo = 0';
  const rows = getDb()
    .prepare(
      `SELECT nickname, COUNT(*) AS orders, SUM(total) AS spent, MAX(created_at) AS last_at
       FROM orders WHERE status IN ${PAID_STATUSES} ${demo} GROUP BY nickname ORDER BY spent DESC LIMIT ?`,
    )
    .all(limit) as Row[];
  return rows.map((r) => ({
    nickname: str(r.nickname),
    orders: num(r.orders),
    spent: num(r.spent),
    lastAt: str(r.last_at),
  }));
}

export function recentPublicOrders(limit = 8, includeDemo = false) {
  const demo = includeDemo ? '' : 'AND is_demo = 0';
  const rows = getDb()
    .prepare(
      `SELECT public_id, nickname, total, items, created_at FROM orders
       WHERE status IN ('paid','delivering','delivered') ${demo} ORDER BY created_at DESC LIMIT ?`,
    )
    .all(limit) as Row[];
  return rows.map((r) => {
    const items = JSON.parse(str(r.items) || '[]') as OrderItem[];
    return {
      publicId: str(r.public_id),
      nickname: str(r.nickname),
      total: num(r.total),
      itemName: items[0]?.name ?? 'Заказ',
      createdAt: str(r.created_at),
    };
  });
}

export function ordersByDayCount(days = 14, includeDemo = false) {
  return revenueSeries(days, includeDemo).map((d) => ({ label: d.label, orders: d.orders, revenue: d.revenue }));
}

export function listDemoOrders(limit = 50) {
  return (getDb().prepare('SELECT * FROM orders WHERE is_demo = 1 ORDER BY created_at DESC LIMIT ?').all(limit) as Row[]).map(
    mapOrder,
  );
}
