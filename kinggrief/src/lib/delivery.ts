import crypto from 'node:crypto';
import {
  createDelivery,
  createOrder,
  getCaseGroup,
  getOrder,
  getProduct,
  getSetting,
  listDeliveries,
  logAudit,
  logEvent,
  setSettings,
  updateDelivery,
  updateOrderStatus,
} from './repo';
import {
  cloudCreateDelivery,
  cloudGetBridgeState,
  cloudGetOrder,
  cloudGetSetting,
  cloudListDeliveries,
  cloudSetSettings,
  cloudSyncOrderDeliveryStatus,
  cloudUpdateOrderStatus,
  cloudUpsertOrder,
  isCloudEnabled,
  type CloudBridgeState,
  type CloudDeliveryRow,
} from './cloud-store';
import type { Delivery, Order, OrderItem } from './types';

export type TemplateMap = Record<string, string>;

export const DEFAULT_TEMPLATES: TemplateMap = {
  rank: 'lp user {player} parent add {rank}',
  rank_replace: 'lp user {player} parent set {rank}',
  relic: 'relics add {player} {amount}',
  case: 'dc givekey {player} {case} {amount}',
  unban: 'unban {player}',
  unmute: 'unmute {player}',
  unban_ip: 'unbanip {player}',
  unmute_ip: 'unmuteip {player}',
};

export function templates(): TemplateMap {
  try {
    return { ...DEFAULT_TEMPLATES, ...(JSON.parse(getSetting('delivery_commands', '{}')) as TemplateMap) };
  } catch {
    return { ...DEFAULT_TEMPLATES };
  }
}

export async function templatesCloud(): Promise<TemplateMap> {
  if (!isCloudEnabled()) return templates();
  try {
    const raw = await cloudGetSetting('delivery_commands', '');
    if (!raw) return templates();
    const parsed = JSON.parse(raw) as TemplateMap;
    // Автомиграция старого дефолта cases give → dc givekey
    if (parsed.case === 'cases give {player} {case} {amount}') {
      parsed.case = DEFAULT_TEMPLATES.case;
    }
    return { ...DEFAULT_TEMPLATES, ...parsed };
  } catch {
    return templates();
  }
}

export function extractDeliveryItemMeta(item: OrderItem): {
  kind: OrderItem['kind'];
  caseId: string | null;
  amount: number;
} {
  const product = getProduct(item.productId);
  const meta = (product?.meta ?? item.meta ?? {}) as Record<string, unknown>;
  if (item.kind === 'case') {
    const perPack = Math.max(1, Number(meta.quantity ?? 1));
    return {
      kind: 'case',
      caseId: String(meta.group ?? 'donate').toLowerCase(),
      amount: perPack * item.quantity,
    };
  }
  if (item.kind === 'relic') {
    return {
      kind: 'relic',
      caseId: null,
      amount: Number(meta.amount ?? 0) * item.quantity,
    };
  }
  return {
    kind: item.kind,
    caseId: null,
    amount: item.quantity,
  };
}

export function renderCommand(
  item: OrderItem,
  nickname: string,
  order: Order,
  opts: { replaceRank?: boolean; map?: TemplateMap } = {},
): string {
  const map = opts.map ?? templates();
  const product = getProduct(item.productId);
  const meta = (product?.meta ?? item.meta ?? {}) as Record<string, unknown>;
  let template: string;
  let rankName = '';
  switch (item.kind) {
    case 'rank':
      template = opts.replaceRank ? map.rank_replace : map.rank;
      rankName = product?.name ?? item.name;
      break;
    case 'relic':
      template = map.relic;
      break;
    case 'case':
      template = map.case;
      break;
    default: {
      const service = String(meta.service ?? 'unban');
      template = map[service] ?? map.unban;
    }
  }
  const amount =
    item.kind === 'relic'
      ? Number(meta.amount ?? 0) * item.quantity
      : item.kind === 'case'
        ? Math.max(1, Number(meta.quantity ?? 1)) * item.quantity
        : item.quantity;
  return template
    .replaceAll('{player}', nickname)
    .replaceAll('{rank}', rankName)
    .replaceAll('{amount}', String(amount))
    .replaceAll('{relics}', String(Number(meta.amount ?? 0) * item.quantity))
    .replaceAll('{case}', String(meta.group ?? 'donate'))
    .replaceAll('{order}', order.publicId)
    .replaceAll('{product}', item.productId);
}

export type BridgeStatusInfo = {
  connected: boolean;
  lastSeen: string | null;
  mode: string;
  online?: number;
  max?: number;
  tps?: number | null;
  mcVersion?: string | null;
  serverId?: string | null;
  detectedCaseCmd?: string | null;
  plugins?: string | null;
};

export function bridgeStatus(): BridgeStatusInfo {
  const last = getSetting('bridge_last_seen', '');
  const connected = Boolean(last) && Date.now() - new Date(last).getTime() < 90_000;
  return { connected, lastSeen: last || null, mode: getSetting('online_mode', 'bridge') };
}

export async function bridgeStatusCloud(): Promise<BridgeStatusInfo> {
  if (!isCloudEnabled()) return bridgeStatus();
  try {
    const [state, mode] = await Promise.all([
      cloudGetBridgeState(),
      cloudGetSetting('online_mode', getSetting('online_mode', 'bridge')),
    ]);
    return {
      connected: state.connected,
      lastSeen: state.lastSeen,
      mode: mode || 'bridge',
      online: state.online,
      max: state.max,
      tps: state.tps,
      mcVersion: state.mcVersion,
      serverId: state.serverId,
      detectedCaseCmd: state.detectedCaseCmd,
      plugins: state.plugins,
    };
  } catch {
    return bridgeStatus();
  }
}

/** Закрывает заказ, когда все задачи выдачи выполнены. */
export function syncOrderDeliveryStatus(orderId: string) {
  const order = getOrder(orderId);
  if (!order) return;
  if (order.status === 'cancelled' || order.status === 'pending_payment' || order.status === 'failed') return;
  const list = listDeliveries({ orderId, limit: 200 });
  if (list.length === 0) return;
  const open = list.filter((d) => d.status !== 'delivered' && d.status !== 'failed');
  if (open.length === 0) {
    if (order.status !== 'delivered') {
      updateOrderStatus(orderId, 'delivered');
      logEvent('delivery', `Заказ ${order.publicId} полностью выдан`);
    }
  } else if (order.status === 'paid' && !open.every((d) => d.status === 'manual')) {
    updateOrderStatus(orderId, 'delivering');
  }
}

export function bridgeQueueEnabled() {
  return getSetting('online_mode', 'bridge') === 'bridge' && Boolean(process.env.MINECRAFT_BRIDGE_SECRET);
}

export async function bridgeQueueEnabledCloud(): Promise<boolean> {
  if (!process.env.MINECRAFT_BRIDGE_SECRET) return false;
  if (!isCloudEnabled()) return bridgeQueueEnabled();
  try {
    const mode = await cloudGetSetting('online_mode', 'bridge');
    return (mode || 'bridge') === 'bridge';
  } catch {
    return true;
  }
}

export function bridgeSecretMatches(header: string | null) {
  const expected = process.env.MINECRAFT_BRIDGE_SECRET;
  if (!expected || !header) return false;
  return header.trim() === expected.trim();
}

/** После подтверждения оплаты: статус «оплачен», создаются задачи выдачи (идемпотентно). */
export function markPaidAndQueue(orderId: string) {
  const order = getOrder(orderId);
  if (!order) return;
  if (order.status === 'delivered') return;
  if (order.status === 'pending_payment') updateOrderStatus(orderId, 'paid');

  const priceOf = (item: OrderItem) => getProduct(item.productId)?.price ?? item.unitPrice;
  const items = [...order.items].sort((a, b) => {
    if (a.kind !== 'rank' || b.kind !== 'rank') return 0;
    return priceOf(a) - priceOf(b);
  });
  const replaceRank = items.filter((item) => item.kind === 'rank').length > 1;

  for (const item of items) {
    createDelivery({
      orderId: order.id,
      productId: item.productId,
      nickname: order.nickname,
      command: renderCommand(item, order.nickname, order, { replaceRank }),
    });
  }
  const deliveries = listDeliveries({ orderId: order.id, limit: 200 });
  if (!bridgeQueueEnabled()) {
    for (const d of deliveries) {
      if (d.status === 'pending') updateDelivery(d.id, { status: 'manual', lastError: 'Мост Minecraft не подключён' });
    }
  }
  logEvent('payment', `Оплачен заказ ${order.publicId} на ${order.total} ₽`);
  syncOrderDeliveryStatus(orderId);
}

/**
 * Облачное подтверждение оплаты и постановка выдачи в единую очередь Supabase,
 * доступную всем серверным функциям Vercel и плагину KingGriefBridge.
 */
export async function confirmPaidOrderCloud(input: {
  idOrPublicId: string;
  provider: string;
  providerPaymentId: string | null;
  amount?: number;
}): Promise<{ ok: boolean; already?: boolean; error?: string; order?: Order }> {
  if (!isCloudEnabled()) {
    const local = getOrder(input.idOrPublicId);
    if (!local) return { ok: false, error: 'Заказ не найден' };
    if (['paid', 'delivering', 'delivered'].includes(local.status)) {
      return { ok: true, already: true, order: local };
    }
    if (input.amount !== undefined && Math.round(input.amount) !== local.total) {
      return { ok: false, error: 'Сумма платежа не совпадает с заказом' };
    }
    markPaidAndQueue(local.id);
    return { ok: true, order: getOrder(local.id) ?? local };
  }

  let order = await cloudGetOrder(input.idOrPublicId);
  if (!order) {
    const local = getOrder(input.idOrPublicId);
    if (local) {
      await cloudUpsertOrder(local);
      order = local;
    }
  }
  if (!order) return { ok: false, error: 'Заказ не найден' };

  if (['paid', 'delivering', 'delivered'].includes(order.status)) {
    return { ok: true, already: true, order };
  }
  if (input.amount !== undefined && Math.round(input.amount) !== order.total) {
    return { ok: false, error: 'Сумма платежа не совпадает с заказом' };
  }

  const map = await templatesCloud();
  const queueOn = await bridgeQueueEnabledCloud();

  const priceOf = (item: OrderItem) => getProduct(item.productId)?.price ?? item.unitPrice;
  const items = [...order.items].sort((a, b) => {
    if (a.kind !== 'rank' || b.kind !== 'rank') return 0;
    return priceOf(a) - priceOf(b);
  });
  const replaceRank = items.filter((item) => item.kind === 'rank').length > 1;

  const paidNow = new Date().toISOString();
  await cloudUpdateOrderStatus(order.id, queueOn ? 'delivering' : 'paid', { paidAt: paidNow });

  for (const item of items) {
    const command = renderCommand(item, order.nickname, order, { replaceRank, map });
    const meta = extractDeliveryItemMeta(item);
    await cloudCreateDelivery({
      orderId: order.id,
      productId: item.productId,
      nickname: order.nickname,
      command,
      kind: meta.kind,
      caseId: meta.caseId,
      amount: meta.amount,
      status: queueOn ? 'pending' : 'manual',
      lastError: queueOn ? null : 'Не задан MINECRAFT_BRIDGE_SECRET или включён ручной режим',
    });
  }

  // Зеркалим локально, если запись есть в этом инстансе
  if (getOrder(order.id)) {
    markPaidAndQueue(order.id);
  }
  await cloudSyncOrderDeliveryStatus(order.id);
  const refreshed = (await cloudGetOrder(order.id)) ?? order;
  return { ok: true, order: refreshed };
}

/**
 * Синхронная выдача кейсов (для локального режима и unit-тестов).
 */
export function grantAdminCase(input: {
  nickname: string;
  caseId: string;
  amount: number;
  commandTemplate?: string;
  saveTemplate?: boolean;
  actor: string;
  ip?: string | null;
}): { order: Order; delivery: Delivery; command: string; queuedForBridge: boolean } {
  const cleanNick = input.nickname.trim();
  const cleanCase = input.caseId.trim().toLowerCase();
  const qty = Math.max(1, Math.min(1000, Math.floor(Number(input.amount) || 1)));

  const currentMap = templates();
  const rawTpl = (input.commandTemplate?.trim() || currentMap.case || DEFAULT_TEMPLATES.case).replace(/^\/+/, '');
  if (input.saveTemplate && rawTpl) {
    setSettings({
      delivery_commands: JSON.stringify({
        ...currentMap,
        case: rawTpl,
      }),
    });
  }

  const group = getCaseGroup(cleanCase);
  const caseTitle = group?.name ?? `Кейс ${cleanCase}`;
  const productId = `admin-case-${cleanCase}`;

  const item: OrderItem = {
    productId,
    name: `${caseTitle} ×${qty}`,
    kind: 'case',
    quantity: 1,
    unitPrice: 0,
    total: 0,
    meta: { group: cleanCase, groupName: caseTitle, quantity: qty, adminGrant: true },
  };

  const order = createOrder({
    nickname: cleanNick,
    items: [item],
    subtotal: 0,
    discount: 0,
    total: 0,
    provider: 'admin',
    isDemo: true,
    note: `Выдано администратором ${input.actor}`,
    ip: input.ip ?? null,
  });
  updateOrderStatus(order.id, 'paid');

  const command = rawTpl
    .replaceAll('{player}', cleanNick)
    .replaceAll('{case}', cleanCase)
    .replaceAll('{amount}', String(qty))
    .replaceAll('{order}', order.publicId)
    .replaceAll('{product}', productId);

  const created = createDelivery({
    orderId: order.id,
    productId,
    nickname: cleanNick,
    command,
  })!;

  const queuedForBridge = bridgeQueueEnabled();
  if (!queuedForBridge) {
    updateDelivery(created.id, {
      status: 'manual',
      lastError: process.env.MINECRAFT_BRIDGE_SECRET
        ? 'Включён ручной режим выдачи (переключите на bridge в Настройках)'
        : 'Не задан MINECRAFT_BRIDGE_SECRET — выполните команду вручную или подключите мост',
    });
  } else {
    updateOrderStatus(order.id, 'delivering');
  }

  const refreshed = listDeliveries({ orderId: order.id, limit: 1 })[0] ?? created;
  logAudit(
    input.actor,
    'case_grant',
    `${cleanNick} ← ${caseTitle} (${cleanCase}) ×${qty} · /${command}`,
    input.ip ?? undefined,
  );
  logEvent('admin_grant', `Админ ${input.actor} выдал ${cleanNick}: ${caseTitle} ×${qty}`);

  return { order: getOrder(order.id)!, delivery: refreshed, command, queuedForBridge };
}

/**
 * Облачная мгновенная выдача кейсов из админ-панели (гарантированно попадает в единую
 * очередь Supabase, которую опрашивает KingGriefBridge со всех воркеров Vercel).
 */
export async function grantAdminCaseCloud(input: {
  nickname: string;
  caseId: string;
  amount: number;
  commandTemplate?: string;
  saveTemplate?: boolean;
  actor: string;
  ip?: string | null;
}): Promise<{ order: Order; delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean }> {
  if (!isCloudEnabled()) {
    return grantAdminCase(input);
  }

  const cleanNick = input.nickname.trim();
  const cleanCase = input.caseId.trim().toLowerCase();
  const qty = Math.max(1, Math.min(1000, Math.floor(Number(input.amount) || 1)));

  const currentMap = await templatesCloud();
  const rawTpl = (input.commandTemplate?.trim() || currentMap.case || DEFAULT_TEMPLATES.case).replace(/^\/+/, '');
  if (input.saveTemplate && rawTpl) {
    const nextJson = JSON.stringify({ ...currentMap, case: rawTpl });
    setSettings({ delivery_commands: nextJson });
    await cloudSetSettings({ delivery_commands: nextJson });
  }

  const group = getCaseGroup(cleanCase);
  const caseTitle = group?.name ?? `Кейс ${cleanCase}`;
  const productId = `admin-case-${cleanCase}`;
  const orderId = crypto.randomUUID();
  const publicId = `KG-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
  const nowIso = new Date().toISOString();

  const item: OrderItem = {
    productId,
    name: `${caseTitle} ×${qty}`,
    kind: 'case',
    quantity: 1,
    unitPrice: 0,
    total: 0,
    meta: { group: cleanCase, groupName: caseTitle, quantity: qty, adminGrant: true },
  };

  const queuedForBridge = await bridgeQueueEnabledCloud();
  const order: Order = {
    id: orderId,
    publicId,
    nickname: cleanNick,
    email: null,
    status: queuedForBridge ? 'delivering' : 'paid',
    subtotal: 0,
    discount: 0,
    total: 0,
    promoCode: null,
    provider: 'admin',
    paymentId: null,
    paymentUrl: null,
    items: [item],
    note: `Выдано администратором ${input.actor}`,
    isDemo: true,
    userId: null,
    createdAt: nowIso,
    paidAt: nowIso,
    deliveredAt: null,
  };

  await cloudUpsertOrder(order, input.ip ?? null);

  const command = rawTpl
    .replaceAll('{player}', cleanNick)
    .replaceAll('{case}', cleanCase)
    .replaceAll('{amount}', String(qty))
    .replaceAll('{order}', order.publicId)
    .replaceAll('{product}', productId);

  const delivery = await cloudCreateDelivery({
    orderId: order.id,
    productId,
    nickname: cleanNick,
    command,
    kind: 'case',
    caseId: cleanCase,
    amount: qty,
    status: queuedForBridge ? 'pending' : 'manual',
    lastError: queuedForBridge
      ? null
      : process.env.MINECRAFT_BRIDGE_SECRET
        ? 'Включён ручной режим выдачи (переключите на bridge в Настройках)'
        : 'Не задан MINECRAFT_BRIDGE_SECRET — выполните команду вручную или подключите мост',
  });

  logAudit(
    input.actor,
    'case_grant',
    `${cleanNick} ← ${caseTitle} (${cleanCase}) ×${qty} · /${command}`,
    input.ip ?? undefined,
  );
  logEvent('admin_grant', `Админ ${input.actor} выдал ${cleanNick}: ${caseTitle} ×${qty}`);

  return { order, delivery, command, queuedForBridge };
}

export function listRecentAdminGrants(limit = 12): Delivery[] {
  return listDeliveries({ limit: 150 })
    .filter((d) => d.productId.startsWith('admin-case-') || d.productId.startsWith('case-'))
    .slice(0, limit);
}

export async function listRecentAdminGrantsCloud(limit = 12): Promise<CloudDeliveryRow[]> {
  if (!isCloudEnabled()) return listRecentAdminGrants(limit);
  try {
    const rows = await cloudListDeliveries({ limit: 120 });
    return rows
      .filter((d) => d.kind === 'case' || d.productId.startsWith('admin-case-') || d.productId.startsWith('case-'))
      .slice(0, limit);
  } catch {
    return listRecentAdminGrants(limit);
  }
}

export type { CloudBridgeState, CloudDeliveryRow };
