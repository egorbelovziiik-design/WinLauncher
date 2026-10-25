import crypto from 'node:crypto';
import { getDb } from './db';
import type { Delivery, Order, OrderItem, OrderStatus } from './types';

export type CloudBridgeState = {
  connected: boolean;
  lastSeen: string | null;
  online: number;
  max: number;
  tps: number | null;
  mcVersion: string | null;
  serverId: string | null;
  detectedCaseCmd: string | null;
  plugins: string | null;
  onlinePlayers: string[];
  lpGroups: string[];
};

export type CloudDeliveryRow = Delivery & {
  kind?: string;
  caseId?: string | null;
  amount?: number | null;
  executedCommand?: string | null;
  sentAt?: string | null;
};

function supabaseConfig(): { url: string; key: string } | null {
  const url = process.env.SUPABASE_URL?.trim().replace(/\/+$/, '');
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY?.trim();
  if (!url || !key) return null;
  return { url, key };
}

export function isCloudEnabled(): boolean {
  return supabaseConfig() !== null;
}

async function sbFetch<T = unknown>(
  path: string,
  init: RequestInit & { prefer?: string } = {},
): Promise<T> {
  const cfg = supabaseConfig();
  if (!cfg) throw new Error('Supabase cloud store is not configured');

  const headers: Record<string, string> = {
    apikey: cfg.key,
    Authorization: `Bearer ${cfg.key}`,
    'Content-Type': 'application/json',
    ...(init.prefer ? { Prefer: init.prefer } : {}),
    ...((init.headers as Record<string, string> | undefined) ?? {}),
  };

  const res = await fetch(`${cfg.url}/rest/v1/${path}`, {
    ...init,
    headers,
    cache: 'no-store',
  });

  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`Supabase ${init.method ?? 'GET'} ${path} failed (${res.status}): ${errText.slice(0, 240)}`);
  }

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

/* ---------------- Settings ---------------- */

export async function cloudGetSettings(includeInternal = false): Promise<Record<string, string>> {
  const rows = await sbFetch<Array<{ key: string; value: string }>>('kg_settings?select=key,value');
  const map: Record<string, string> = {};
  for (const r of rows ?? []) {
    if (!includeInternal && r.key.startsWith('__sync_')) continue;
    map[r.key] = r.value;
  }
  return map;
}

export async function cloudGetSetting(key: string, fallback = ''): Promise<string> {
  const rows = await sbFetch<Array<{ value: string }>>(
    `kg_settings?key=eq.${encodeURIComponent(key)}&select=value&limit=1`,
  );
  return rows?.[0]?.value ?? fallback;
}

export async function cloudSetSettings(entries: Record<string, string>): Promise<void> {
  const payload = Object.entries(entries).map(([key, value]) => ({
    key,
    value: String(value),
    updated_at: new Date().toISOString(),
  }));
  if (payload.length === 0) return;
  await sbFetch('kg_settings?on_conflict=key', {
    method: 'POST',
    prefer: 'resolution=merge-duplicates,return=minimal',
    body: JSON.stringify(payload),
  });
  lastSyncAt = 0;
}

/* ---------------- Bridge Heartbeat ---------------- */

type RawBridgeRow = {
  server_id: string;
  last_seen: string;
  online: number;
  max_players: number;
  tps: number | string | null;
  mc_version: string | null;
  detected_case_cmd: string | null;
  plugins: string | null;
  online_players?: string | null;
  lp_groups?: string | null;
};

function splitCsv(raw: string | null | undefined): string[] {
  if (!raw) return [];
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

export async function cloudUpsertBridgeHeartbeat(input: {
  serverId?: string;
  online?: number;
  max?: number;
  tps?: number;
  version?: string;
  detectedCaseCmd?: string;
  plugins?: string;
  onlinePlayers?: string[] | string;
  lpGroups?: string[] | string;
}): Promise<void> {
  const row: Record<string, unknown> = {
    server_id: (input.serverId || 'grief-1').slice(0, 64),
    last_seen: new Date().toISOString(),
  };
  if (typeof input.online === 'number' && Number.isFinite(input.online)) {
    row.online = Math.max(0, Math.floor(input.online));
  }
  if (typeof input.max === 'number' && Number.isFinite(input.max)) {
    row.max_players = Math.max(0, Math.floor(input.max));
  }
  if (typeof input.tps === 'number' && Number.isFinite(input.tps)) {
    row.tps = Number(input.tps.toFixed(2));
  }
  if (typeof input.version === 'string' && input.version.trim()) {
    row.mc_version = input.version.trim().slice(0, 48);
  }
  if (typeof input.detectedCaseCmd === 'string' && input.detectedCaseCmd.trim()) {
    row.detected_case_cmd = input.detectedCaseCmd.trim().slice(0, 160);
  }
  if (typeof input.plugins === 'string' && input.plugins.trim()) {
    row.plugins = input.plugins.trim().slice(0, 500);
  }
  if (input.onlinePlayers !== undefined) {
    const val = Array.isArray(input.onlinePlayers) ? input.onlinePlayers.join(',') : String(input.onlinePlayers);
    row.online_players = val.slice(0, 1500);
  }
  if (input.lpGroups !== undefined) {
    const val = Array.isArray(input.lpGroups) ? input.lpGroups.join(',') : String(input.lpGroups);
    row.lp_groups = val.slice(0, 1500);
  }

  await sbFetch('kg_bridge_state?on_conflict=server_id', {
    method: 'POST',
    prefer: 'resolution=merge-duplicates,return=minimal',
    body: JSON.stringify([row]),
  });
}

export async function cloudGetBridgeState(): Promise<CloudBridgeState> {
  const rows = await sbFetch<RawBridgeRow[]>(
    'kg_bridge_state?select=*&order=last_seen.desc&limit=1',
  );
  const latest = rows?.[0];
  if (!latest) {
    return {
      connected: false,
      lastSeen: null,
      online: 0,
      max: 0,
      tps: null,
      mcVersion: null,
      serverId: null,
      detectedCaseCmd: null,
      plugins: null,
      onlinePlayers: [],
      lpGroups: [],
    };
  }
  const connected = Date.now() - new Date(latest.last_seen).getTime() < 90_000;
  return {
    connected,
    lastSeen: latest.last_seen,
    online: Number(latest.online ?? 0),
    max: Number(latest.max_players ?? 0),
    tps: latest.tps !== null && latest.tps !== undefined ? Number(latest.tps) : null,
    mcVersion: latest.mc_version ?? null,
    serverId: latest.server_id ?? null,
    detectedCaseCmd: latest.detected_case_cmd ?? null,
    plugins: latest.plugins ?? null,
    onlinePlayers: splitCsv(latest.online_players),
    lpGroups: splitCsv(latest.lp_groups),
  };
}

/* ---------------- Orders ---------------- */

type RawOrderRow = {
  id: string;
  public_id: string;
  nickname: string;
  email: string | null;
  status: OrderStatus;
  subtotal: number;
  discount: number;
  total: number;
  promo_code: string | null;
  provider: string;
  payment_id: string | null;
  payment_url: string | null;
  items: OrderItem[] | string;
  note: string | null;
  is_demo: boolean;
  user_id: number | null;
  ip: string | null;
  created_at: string;
  paid_at: string | null;
  delivered_at: string | null;
};

function mapCloudOrder(row: RawOrderRow): Order {
  let items: OrderItem[] = [];
  if (Array.isArray(row.items)) items = row.items;
  else if (typeof row.items === 'string') {
    try {
      items = JSON.parse(row.items) as OrderItem[];
    } catch {
      items = [];
    }
  }
  return {
    id: row.id,
    publicId: row.public_id,
    nickname: row.nickname,
    email: row.email,
    status: row.status,
    subtotal: Number(row.subtotal ?? 0),
    discount: Number(row.discount ?? 0),
    total: Number(row.total ?? 0),
    promoCode: row.promo_code,
    provider: row.provider,
    paymentId: row.payment_id,
    paymentUrl: row.payment_url,
    items,
    note: row.note,
    isDemo: Boolean(row.is_demo),
    userId: row.user_id,
    createdAt: row.created_at,
    paidAt: row.paid_at,
    deliveredAt: row.delivered_at,
  };
}

export async function cloudUpsertOrder(order: Order, ip?: string | null): Promise<void> {
  const payload = {
    id: order.id,
    public_id: order.publicId,
    nickname: order.nickname,
    email: order.email ?? null,
    status: order.status,
    subtotal: order.subtotal,
    discount: order.discount,
    total: order.total,
    promo_code: order.promoCode ?? null,
    provider: order.provider,
    payment_id: order.paymentId ?? null,
    payment_url: order.paymentUrl ?? null,
    items: order.items,
    note: order.note ?? null,
    is_demo: Boolean(order.isDemo),
    user_id: order.userId ?? null,
    ip: ip ?? null,
    created_at: order.createdAt || new Date().toISOString(),
    paid_at: order.paidAt ?? null,
    delivered_at: order.deliveredAt ?? null,
  };
  await sbFetch('kg_orders?on_conflict=id', {
    method: 'POST',
    prefer: 'resolution=merge-duplicates,return=minimal',
    body: JSON.stringify([payload]),
  });
}

export async function cloudUpsertOrdersBatch(orders: Order[]): Promise<void> {
  if (orders.length === 0) return;
  const payload = orders.map((order) => ({
    id: order.id,
    public_id: order.publicId,
    nickname: order.nickname,
    email: order.email ?? null,
    status: order.status,
    subtotal: order.subtotal,
    discount: order.discount,
    total: order.total,
    promo_code: order.promoCode ?? null,
    provider: order.provider,
    payment_id: order.paymentId ?? null,
    payment_url: order.paymentUrl ?? null,
    items: order.items,
    note: order.note ?? null,
    is_demo: Boolean(order.isDemo),
    user_id: order.userId ?? null,
    ip: null,
    created_at: order.createdAt || new Date().toISOString(),
    paid_at: order.paidAt ?? null,
    delivered_at: order.deliveredAt ?? null,
  }));
  await sbFetch('kg_orders?on_conflict=id', {
    method: 'POST',
    prefer: 'resolution=merge-duplicates,return=minimal',
    body: JSON.stringify(payload),
  });
}

export async function cloudDeleteDemoOrders(): Promise<void> {
  const demoOrders = await sbFetch<Array<{ id: string }>>('kg_orders?is_demo=eq.true&note=like.*Демонстрационный*&select=id&limit=500');
  for (const row of demoOrders ?? []) {
    await sbFetch(`kg_deliveries?order_id=eq.${encodeURIComponent(row.id)}`, {
      method: 'DELETE',
      prefer: 'return=minimal',
    });
  }
  await sbFetch('kg_orders?is_demo=eq.true&note=like.*Демонстрационный*', {
    method: 'DELETE',
    prefer: 'return=minimal',
  });
}

export async function cloudGetOrder(idOrPublicId: string): Promise<Order | null> {
  const encoded = encodeURIComponent(idOrPublicId);
  const byPublic = await sbFetch<RawOrderRow[]>(
    `kg_orders?public_id=eq.${encoded}&select=*&limit=1`,
  );
  if (byPublic?.[0]) return mapCloudOrder(byPublic[0]);

  const byId = await sbFetch<RawOrderRow[]>(
    `kg_orders?id=eq.${encoded}&select=*&limit=1`,
  );
  if (byId?.[0]) return mapCloudOrder(byId[0]);
  return null;
}

export async function cloudUpdateOrderStatus(
  orderId: string,
  status: OrderStatus,
  extra: { paidAt?: string; deliveredAt?: string } = {},
): Promise<void> {
  const patch: Record<string, unknown> = { status };
  if (status === 'paid' || extra.paidAt) patch.paid_at = extra.paidAt ?? new Date().toISOString();
  if (status === 'delivered' || extra.deliveredAt) patch.delivered_at = extra.deliveredAt ?? new Date().toISOString();
  await sbFetch(`kg_orders?id=eq.${encodeURIComponent(orderId)}`, {
    method: 'PATCH',
    prefer: 'return=minimal',
    body: JSON.stringify(patch),
  });
}

export async function cloudListOrders(limit = 200): Promise<Order[]> {
  const rows = await sbFetch<RawOrderRow[]>(
    `kg_orders?select=*&order=created_at.desc&limit=${Math.max(1, Math.min(500, limit))}`,
  );
  return (rows ?? []).map(mapCloudOrder);
}

/* ---------------- Deliveries ---------------- */

type RawDeliveryRow = {
  id: string;
  order_id: string;
  product_id: string;
  nickname: string;
  command: string;
  kind: string | null;
  case_id: string | null;
  amount: number | null;
  status: Delivery['status'];
  attempts: number;
  last_error: string | null;
  executed_command: string | null;
  created_at: string;
  sent_at: string | null;
  delivered_at: string | null;
};

function mapCloudDelivery(row: RawDeliveryRow): CloudDeliveryRow {
  return {
    id: row.id,
    orderId: row.order_id,
    productId: row.product_id,
    nickname: row.nickname,
    command: row.command,
    kind: row.kind ?? 'other',
    caseId: row.case_id,
    amount: row.amount,
    status: row.status,
    attempts: Number(row.attempts ?? 0),
    lastError: row.last_error,
    executedCommand: row.executed_command,
    createdAt: row.created_at,
    sentAt: row.sent_at,
    deliveredAt: row.delivered_at,
  };
}

export async function cloudCreateDelivery(input: {
  id?: string;
  orderId: string;
  productId: string;
  nickname: string;
  command: string;
  kind?: string;
  caseId?: string | null;
  amount?: number | null;
  status?: Delivery['status'];
  lastError?: string | null;
  skipDedupe?: boolean;
}): Promise<CloudDeliveryRow> {
  if (!input.skipDedupe) {
    const existing = await sbFetch<RawDeliveryRow[]>(
      `kg_deliveries?order_id=eq.${encodeURIComponent(input.orderId)}&product_id=eq.${encodeURIComponent(input.productId)}&select=*&limit=5`,
    );
    const same = existing?.find((r) => r.command === input.command);
    if (same) return mapCloudDelivery(same);
  }

  const id = input.id ?? crypto.randomUUID();
  const payload = {
    id,
    order_id: input.orderId,
    product_id: input.productId,
    nickname: input.nickname,
    command: input.command,
    kind: input.kind ?? 'case',
    case_id: input.caseId ?? null,
    amount: input.amount ?? null,
    status: input.status ?? 'pending',
    attempts: 0,
    last_error: input.lastError ?? null,
    created_at: new Date().toISOString(),
  };

  const inserted = await sbFetch<RawDeliveryRow[]>('kg_deliveries', {
    method: 'POST',
    prefer: 'return=representation',
    body: JSON.stringify([payload]),
  });
  return mapCloudDelivery(inserted[0] ?? (payload as unknown as RawDeliveryRow));
}

export async function cloudListDeliveries(
  opts: { status?: string; orderId?: string; productPrefix?: string; limit?: number } = {},
): Promise<CloudDeliveryRow[]> {
  const filters: string[] = ['select=*'];
  if (opts.status && opts.status !== 'all') {
    filters.push(`status=eq.${encodeURIComponent(opts.status)}`);
  }
  if (opts.orderId) {
    filters.push(`order_id=eq.${encodeURIComponent(opts.orderId)}`);
  }
  if (opts.productPrefix) {
    filters.push(`product_id=like.${encodeURIComponent(opts.productPrefix)}*`);
  }
  filters.push('order=created_at.desc');
  filters.push(`limit=${Math.max(1, Math.min(500, opts.limit ?? 100))}`);
  const rows = await sbFetch<RawDeliveryRow[]>(`kg_deliveries?${filters.join('&')}`);
  return (rows ?? []).map(mapCloudDelivery);
}

export async function cloudGetDelivery(id: string): Promise<CloudDeliveryRow | null> {
  const rows = await sbFetch<RawDeliveryRow[]>(
    `kg_deliveries?id=eq.${encodeURIComponent(id)}&select=*&limit=1`,
  );
  return rows?.[0] ? mapCloudDelivery(rows[0]) : null;
}

export async function cloudUpdateDelivery(
  id: string,
  patch: {
    status?: Delivery['status'];
    lastError?: string | null;
    executedCommand?: string | null;
  },
): Promise<void> {
  const body: Record<string, unknown> = {};
  if (patch.status) {
    body.status = patch.status;
    if (patch.status === 'delivered') {
      body.delivered_at = new Date().toISOString();
      body.last_error = null;
    }
    if (patch.status === 'pending') {
      body.sent_at = null;
    }
  }
  if (patch.lastError !== undefined) body.last_error = patch.lastError;
  if (patch.executedCommand !== undefined) body.executed_command = patch.executedCommand;

  await sbFetch(`kg_deliveries?id=eq.${encodeURIComponent(id)}`, {
    method: 'PATCH',
    prefer: 'return=minimal',
    body: JSON.stringify(body),
  });
}

export async function cloudRequeueStaleDeliveries(): Promise<{ requeued: number; manual: number }> {
  const cutoffIso = new Date(Date.now() - 45_000).toISOString();
  const stale = await sbFetch<RawDeliveryRow[]>(
    `kg_deliveries?status=eq.sent&sent_at=lt.${encodeURIComponent(cutoffIso)}&select=*&limit=50`,
  );
  let requeued = 0;
  let manual = 0;
  for (const row of stale ?? []) {
    if ((row.attempts ?? 0) >= 5) {
      await sbFetch(`kg_deliveries?id=eq.${encodeURIComponent(row.id)}`, {
        method: 'PATCH',
        prefer: 'return=minimal',
        body: JSON.stringify({
          status: 'manual',
          last_error: 'Превышен лимит попыток ответа от плагина (5)',
        }),
      });
      manual += 1;
    } else {
      await sbFetch(`kg_deliveries?id=eq.${encodeURIComponent(row.id)}`, {
        method: 'PATCH',
        prefer: 'return=minimal',
        body: JSON.stringify({ status: 'pending', sent_at: null }),
      });
      requeued += 1;
    }
  }
  return { requeued, manual };
}

export async function cloudClaimPendingDeliveries(limit = 25): Promise<CloudDeliveryRow[]> {
  const rows = await sbFetch<RawDeliveryRow[]>(
    `kg_deliveries?status=eq.pending&select=*&order=created_at.asc&limit=${Math.max(1, Math.min(50, limit))}`,
  );
  const claimed: CloudDeliveryRow[] = [];
  const nowIso = new Date().toISOString();

  for (const row of rows ?? []) {
    const updated = await sbFetch<RawDeliveryRow[]>(
      `kg_deliveries?id=eq.${encodeURIComponent(row.id)}&status=eq.pending`,
      {
        method: 'PATCH',
        prefer: 'return=representation',
        body: JSON.stringify({
          status: 'sent',
          attempts: Number(row.attempts ?? 0) + 1,
          sent_at: nowIso,
        }),
      },
    );
    if (updated?.[0]) {
      claimed.push(mapCloudDelivery(updated[0]));
    }
  }
  return claimed;
}

export async function cloudSyncOrderDeliveryStatus(orderId: string): Promise<void> {
  const order = await cloudGetOrder(orderId);
  if (!order) return;
  if (order.status === 'cancelled' || order.status === 'pending_payment' || order.status === 'failed') return;

  const list = await cloudListDeliveries({ orderId, limit: 100 });
  if (list.length === 0) return;

  const open = list.filter((d) => d.status !== 'delivered' && d.status !== 'failed');
  if (open.length === 0) {
    if (order.status !== 'delivered') {
      await cloudUpdateOrderStatus(orderId, 'delivered');
    }
  } else if (order.status === 'paid' && !open.every((d) => d.status === 'manual')) {
    await cloudUpdateOrderStatus(orderId, 'delivering');
  }
}

/* ---------------- Cross-Lambda SQLite Hydration ---------------- */

export type SyncableTable = 'products' | 'cases' | 'case_items' | 'promos' | 'reviews' | 'users' | 'admin_users';

let lastSyncAt = 0;
let lastOrdersSyncAt = 0;

/**
 * Сохраняет таблицу локальной SQLite в Supabase kg_settings (__sync_<table>),
 * чтобы изменения в админке / профиле мгновенно работали во всех Vercel Lambda контейнерах.
 */
export async function cloudSaveTable(table: SyncableTable): Promise<void> {
  if (!isCloudEnabled()) return;
  try {
    const db = getDb();
    const rows = db.prepare(`SELECT * FROM ${table}`).all();
    await cloudSetSettings({ [`__sync_${table}`]: JSON.stringify(rows) });
    lastSyncAt = 0;
  } catch {
    /* ignore transient sync failure */
  }
}

function replaceLocalTable(table: SyncableTable, rawJson: string) {
  let rows: Array<Record<string, unknown>>;
  try {
    rows = JSON.parse(rawJson) as Array<Record<string, unknown>>;
    if (!Array.isArray(rows)) return;
  } catch {
    return;
  }
  const db = getDb();
  if (table === 'products') {
    db.prepare('DELETE FROM products').run();
    const ins = db.prepare(
      `INSERT INTO products (id, kind, name, subtitle, description, price, old_price, color, icon, badge, features, meta, sort, active)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        String(r.id ?? ''),
        String(r.kind ?? 'rank'),
        String(r.name ?? ''),
        String(r.subtitle ?? ''),
        String(r.description ?? ''),
        Number(r.price ?? 0),
        r.old_price == null ? null : Number(r.old_price),
        String(r.color ?? '#A855F7'),
        String(r.icon ?? 'crown'),
        r.badge == null ? null : String(r.badge),
        String(r.features ?? '[]'),
        String(r.meta ?? '{}'),
        Number(r.sort ?? 0),
        Number(r.active ?? 1),
      );
    }
  } else if (table === 'cases') {
    db.prepare('DELETE FROM cases').run();
    const ins = db.prepare(
      `INSERT INTO cases (id, name, description, color, icon, sort, active) VALUES (?, ?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        String(r.id ?? ''),
        String(r.name ?? ''),
        String(r.description ?? ''),
        String(r.color ?? '#A855F7'),
        String(r.icon ?? 'box'),
        Number(r.sort ?? 0),
        Number(r.active ?? 1),
      );
    }
  } else if (table === 'case_items') {
    db.prepare('DELETE FROM case_items').run();
    const ins = db.prepare(
      `INSERT INTO case_items (id, case_id, name, description, link, rarity, chance, icon, color, sort)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        Number(r.id ?? 0) || null,
        String(r.case_id ?? ''),
        String(r.name ?? ''),
        String(r.description ?? ''),
        r.link == null ? null : String(r.link),
        String(r.rarity ?? 'common'),
        Number(r.chance ?? 0),
        String(r.icon ?? 'coins'),
        String(r.color ?? '#94A3B8'),
        Number(r.sort ?? 0),
      );
    }
  } else if (table === 'promos') {
    db.prepare('DELETE FROM promos').run();
    const ins = db.prepare(
      `INSERT INTO promos (code, type, value, min_total, max_discount, usage_limit, used_count, categories, expires_at, active)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        String(r.code ?? ''),
        String(r.type ?? 'percent'),
        Number(r.value ?? 0),
        Number(r.min_total ?? 0),
        r.max_discount == null ? null : Number(r.max_discount),
        r.usage_limit == null ? null : Number(r.usage_limit),
        Number(r.used_count ?? 0),
        r.categories == null ? null : String(r.categories),
        r.expires_at == null ? null : String(r.expires_at),
        Number(r.active ?? 1),
      );
    }
  } else if (table === 'reviews') {
    db.prepare('DELETE FROM reviews').run();
    const ins = db.prepare(
      `INSERT INTO reviews (id, nickname, rating, text, status, created_at) VALUES (?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        Number(r.id ?? 0) || null,
        String(r.nickname ?? ''),
        Number(r.rating ?? 5),
        String(r.text ?? ''),
        String(r.status ?? 'pending'),
        String(r.created_at ?? new Date().toISOString()),
      );
    }
  } else if (table === 'users') {
    db.prepare('DELETE FROM users').run();
    const ins = db.prepare(
      `INSERT INTO users (id, username, email, password_hash, created_at, last_login_at) VALUES (?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        Number(r.id ?? 0) || null,
        String(r.username ?? ''),
        String(r.email ?? ''),
        String(r.password_hash ?? ''),
        String(r.created_at ?? new Date().toISOString()),
        r.last_login_at == null ? null : String(r.last_login_at),
      );
    }
  } else if (table === 'admin_users') {
    if (rows.length === 0) return;
    db.prepare('DELETE FROM admin_users').run();
    const ins = db.prepare(
      `INSERT INTO admin_users (id, username, password_hash, role, totp_secret, totp_enabled, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    );
    for (const r of rows) {
      ins.run(
        Number(r.id ?? 0) || null,
        String(r.username ?? 'admin'),
        String(r.password_hash ?? ''),
        String(r.role ?? 'owner'),
        r.totp_secret == null ? null : String(r.totp_secret),
        Number(r.totp_enabled ?? 0),
        String(r.created_at ?? new Date().toISOString()),
      );
    }
  }
}

/**
 * Синхронизирует локальную SQLite текущего serverless-воркера с Supabase.
 * Кэшируется на 1.5 сек внутри одного воркера, чтобы SSR страницы делали ровно 1 быстрый запрос.
 */
export async function cloudSyncLocalDb(opts: { includeOrders?: boolean; force?: boolean } = {}): Promise<void> {
  if (!isCloudEnabled()) return;
  const nowMs = Date.now();
  try {
    if (opts.force || nowMs - lastSyncAt > 1500) {
      const all = await cloudGetSettings(true);
      const db = getDb();
      const setStmt = db.prepare(
        'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
      );
      for (const [k, v] of Object.entries(all)) {
        if (k.startsWith('__sync_')) continue;
        setStmt.run(k, String(v));
      }
      const tables: SyncableTable[] = ['products', 'cases', 'case_items', 'promos', 'reviews', 'users', 'admin_users'];
      for (const t of tables) {
        const raw = all[`__sync_${t}`];
        if (raw) replaceLocalTable(t, raw);
      }
      lastSyncAt = nowMs;
    }

    if (opts.includeOrders && (opts.force || nowMs - lastOrdersSyncAt > 1500)) {
      const [orders, deliveries] = await Promise.all([
        cloudListOrders(400),
        cloudListDeliveries({ limit: 300 }),
      ]);
      const db = getDb();
      db.prepare('DELETE FROM orders').run();
      const insOrder = db.prepare(
        `INSERT INTO orders (id, public_id, nickname, email, status, subtotal, discount, total, promo_code,
           provider, payment_id, payment_url, items, note, is_demo, user_id, ip, created_at, paid_at, delivered_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      );
      for (const o of orders) {
        insOrder.run(
          o.id,
          o.publicId,
          o.nickname,
          o.email,
          o.status,
          o.subtotal,
          o.discount,
          o.total,
          o.promoCode,
          o.provider,
          o.paymentId,
          o.paymentUrl,
          JSON.stringify(o.items),
          o.note,
          o.isDemo ? 1 : 0,
          o.userId,
          null,
          o.createdAt,
          o.paidAt,
          o.deliveredAt,
        );
      }

      db.prepare('DELETE FROM deliveries').run();
      const insDel = db.prepare(
        `INSERT INTO deliveries (id, order_id, product_id, nickname, command, status, attempts, last_error, created_at, sent_at, delivered_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      );
      for (const d of deliveries) {
        insDel.run(
          d.id,
          d.orderId,
          d.productId,
          d.nickname,
          d.command,
          d.status,
          d.attempts,
          d.lastError,
          d.createdAt,
          d.sentAt ?? null,
          d.deliveredAt,
        );
      }
      lastOrdersSyncAt = nowMs;
    }
  } catch {
    /* non-fatal: local SQLite defaults stay intact */
  }
}
