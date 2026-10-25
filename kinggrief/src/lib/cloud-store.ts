import crypto from 'node:crypto';
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

export async function cloudGetSettings(): Promise<Record<string, string>> {
  const rows = await sbFetch<Array<{ key: string; value: string }>>('kg_settings?select=key,value');
  const map: Record<string, string> = {};
  for (const r of rows ?? []) map[r.key] = r.value;
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
};

export async function cloudUpsertBridgeHeartbeat(input: {
  serverId?: string;
  online?: number;
  max?: number;
  tps?: number;
  version?: string;
  detectedCaseCmd?: string;
  plugins?: string;
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
}): Promise<CloudDeliveryRow> {
  // Идемпотентность по (order_id, product_id, command)
  const existing = await sbFetch<RawDeliveryRow[]>(
    `kg_deliveries?order_id=eq.${encodeURIComponent(input.orderId)}&product_id=eq.${encodeURIComponent(input.productId)}&select=*&limit=5`,
  );
  const same = existing?.find((r) => r.command === input.command);
  if (same) return mapCloudDelivery(same);

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

/**
 * Возвращает зависшие задачи ('sent' старше 45 секунд) обратно в 'pending',
 * чтобы при рестарте сервера или сетевом сбое ни один кейс не пропал.
 */
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

/**
 * Забирает пачку pending-задач для плагина KingGriefBridge и помечает их как 'sent'.
 */
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
