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
  cloudGetDelivery,
  cloudGetOrder,
  cloudGetSetting,
  cloudListDeliveries,
  cloudListOrders,
  cloudSetSettings,
  cloudSyncOrderDeliveryStatus,
  cloudUpdateOrderStatus,
  cloudUpsertOrder,
  isCloudEnabled,
  type CloudBridgeState,
  type CloudDeliveryRow,
} from './cloud-store';
import type { Delivery, Order, OrderItem } from './types';

export const DEFAULT_TEMPLATES: Record<string, string> = {
  rank: 'lp user {player} parent set {rank}',
  rank_replace: 'lp user {player} parent set {rank}',
  relic: 'p give {player} {amount}',
  case: 'cases give {player} {case} {amount}',
  money: 'money give {player} {amount}',
  unban: 'unban {player}',
  unmute: 'unmute {player}',
  unban_ip: 'unbanip {player}',
  unmute_ip: 'unmuteip {player}',
};

export const CLOUD_SERVER_DEFAULTS: Record<string, string> = {
  rank: 'lp user {player} parent set {rank}',
  rank_replace: 'lp user {player} parent set {rank}',
  relic: 'p give {player} {amount}',
  case: 'cases give {player} {case} {amount}',
  money: 'money give {player} {amount}',
  unban: 'pardon {player}',
  unmute: 'unmute {player}',
  unban_ip: 'pardon-ip {player}',
  unmute_ip: 'unmuteip {player}',
};

export function templates(): Record<string, string> {
  const raw = getSetting('delivery_commands', '');
  try {
    return { ...DEFAULT_TEMPLATES, ...(raw ? JSON.parse(raw) : {}) };
  } catch {
    return { ...DEFAULT_TEMPLATES };
  }
}

export async function templatesCloud(): Promise<Record<string, string>> {
  if (!isCloudEnabled()) return templates();
  try {
    const raw = await cloudGetSetting('delivery_commands', '');
    if (raw) {
      return { ...CLOUD_SERVER_DEFAULTS, ...JSON.parse(raw) };
    }
  } catch {
    /* fallback */
  }
  return { ...CLOUD_SERVER_DEFAULTS };
}

/**
 * Вычисляет точный LuckPerms ID группы привилегии (например, D.ST.MODER -> dstmoder).
 */
export function resolveRankGroupId(item: Pick<OrderItem, 'productId' | 'name' | 'meta'>, productRankId?: unknown): string {
  const meta = item.meta ?? {};
  if (typeof meta.rankId === 'string' && meta.rankId.trim()) {
    return meta.rankId.trim().toLowerCase();
  }
  if (typeof productRankId === 'string' && productRankId.trim()) {
    return productRankId.trim().toLowerCase();
  }
  if (item.productId.startsWith('rank-')) {
    return item.productId.slice(5).trim().toLowerCase();
  }
  if (item.productId.startsWith('admin-rank-')) {
    return item.productId.slice(11).trim().toLowerCase();
  }
  return item.name.toLowerCase().replace(/[^a-z0-9_-]/g, '') || 'default';
}

export function extractDeliveryItemMeta(item: OrderItem): {
  kind: string;
  caseId: string | null;
  amount: number;
} {
  const product = getProduct(item.productId);
  const meta = { ...(product?.meta ?? {}), ...(item.meta ?? {}) };
  const perPack = Number(meta.amount ?? meta.quantity ?? 1) || 1;
  const amount = Math.max(1, perPack * item.quantity);
  if (item.kind === 'case') {
    const caseId = String(meta.group ?? item.productId.replace(/^(admin-)?case-/, '').split('-')[0] ?? 'donate')
      .trim()
      .toLowerCase();
    return { kind: 'case', caseId, amount };
  }
  if (item.kind === 'rank') {
    const prodRankId = (product?.meta as Record<string, unknown> | undefined)?.rankId;
    return { kind: 'rank', caseId: resolveRankGroupId(item, prodRankId), amount: 1 };
  }
  return { kind: item.kind, caseId: null, amount };
}

/** Подставляет переменные в шаблон команды выдачи. */
export function renderCommand(
  item: OrderItem,
  nickname: string,
  order: Pick<Order, 'publicId'>,
  opts: { replaceRank?: boolean; map?: Record<string, string>; useRankSlug?: boolean } = {},
): string {
  const map = opts.map ?? templates();
  const product = getProduct(item.productId);
  const meta = { ...(product?.meta ?? {}), ...(item.meta ?? {}) };
  let template = '';
  let amount = item.quantity;
  let caseId = '';
  let rankName = '';

  switch (item.kind) {
    case 'rank': {
      template = opts.replaceRank ? map.rank_replace : map.rank;
      const prodRankId = (product?.meta as Record<string, unknown> | undefined)?.rankId;
      rankName = opts.useRankSlug ? resolveRankGroupId(item, prodRankId) : (product?.name ?? item.name);
      break;
    }
    case 'relic':
      template = map.relic;
      amount = Number(meta.amount ?? 0) * item.quantity;
      break;
    case 'case':
      template = map.case;
      caseId = String(meta.group ?? item.productId);
      amount = Number(meta.quantity ?? 1) * item.quantity;
      break;
    case 'other': {
      const action = String(meta.action ?? 'unban');
      template = map[action] ?? map.unban;
      break;
    }
  }

  return template
    .replace(/^\/+/, '')
    .replaceAll('{player}', nickname)
    .replaceAll('{rank}', rankName)
    .replaceAll('{amount}', String(amount))
    .replaceAll('{case}', caseId)
    .replaceAll('{order}', order.publicId)
    .replaceAll('{product}', item.productId);
}

/** Состояние моста Minecraft — для плашки в админке и проверки доставки. */
export function bridgeStatus(): CloudBridgeState & { secretConfigured: boolean } {
  const lastSeen = getSetting('bridge_last_seen', '');
  const ageMs = lastSeen ? Date.now() - new Date(lastSeen).getTime() : Infinity;
  const connected = ageMs < 90_000;
  return {
    connected,
    lastSeen: lastSeen || null,
    online: Number(getSetting('bridge_online', '0')) || 0,
    max: Number(getSetting('bridge_max', '0')) || 0,
    tps: getSetting('bridge_tps', '') ? Number(getSetting('bridge_tps', '20')) : null,
    mcVersion: getSetting('bridge_mc_version', '') || null,
    serverId: getSetting('bridge_server_id', '') || null,
    detectedCaseCmd: getSetting('bridge_detected_case_cmd', '') || null,
    plugins: getSetting('bridge_plugins', '') || null,
    onlinePlayers: [],
    lpGroups: [],
    secretConfigured: Boolean(process.env.MINECRAFT_BRIDGE_SECRET),
  };
}

export async function bridgeStatusCloud(): Promise<CloudBridgeState & { secretConfigured: boolean }> {
  const secretConfigured = Boolean(process.env.MINECRAFT_BRIDGE_SECRET);
  if (!isCloudEnabled()) {
    return bridgeStatus();
  }
  try {
    const state = await cloudGetBridgeState();
    return { ...state, secretConfigured };
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
    const command = renderCommand(item, order.nickname, order, { replaceRank, map, useRankSlug: true });
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
  const qty = Math.max(1, Math.min(10000, Math.floor(Number(input.amount) || 1)));

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
 * Вспомогательное создание безлимитной админ-выдачи (для кейсов, привилегий, риликов и услуг).
 */
async function createUnlimitedAdminGrant(params: {
  nickname: string;
  item: OrderItem;
  productId: string;
  kind: 'case' | 'rank' | 'relic' | 'other';
  caseOrGroupId: string | null;
  amount: number;
  command: string;
  auditAction: string;
  auditDetail: string;
  eventLabel: string;
  actor: string;
  ip?: string | null;
}): Promise<{ order: Order; delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean }> {
  const cleanNick = params.nickname.trim();

  if (!isCloudEnabled()) {
    const order = createOrder({
      nickname: cleanNick,
      items: [params.item],
      subtotal: 0,
      discount: 0,
      total: 0,
      provider: 'admin',
      isDemo: true,
      note: `Выдано администратором ${params.actor}`,
      ip: params.ip ?? null,
    });
    updateOrderStatus(order.id, 'paid');
    const finalCmd = params.command.replaceAll('{order}', order.publicId);
    const created = createDelivery({
      orderId: order.id,
      productId: params.productId,
      nickname: cleanNick,
      command: finalCmd,
    })!;
    const queuedForBridge = bridgeQueueEnabled();
    if (!queuedForBridge) {
      updateDelivery(created.id, {
        status: 'manual',
        lastError: process.env.MINECRAFT_BRIDGE_SECRET
          ? 'Включён ручной режим выдачи'
          : 'Не задан MINECRAFT_BRIDGE_SECRET',
      });
    } else {
      updateOrderStatus(order.id, 'delivering');
    }
    const refreshed = listDeliveries({ orderId: order.id, limit: 1 })[0] ?? created;
    logAudit(params.actor, params.auditAction, params.auditDetail, params.ip ?? undefined);
    logEvent('admin_grant', params.eventLabel);
    return { order: getOrder(order.id)!, delivery: refreshed, command: finalCmd, queuedForBridge };
  }

  const orderId = crypto.randomUUID();
  const publicId = `KG-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
  const nowIso = new Date().toISOString();
  const queuedForBridge = await bridgeQueueEnabledCloud();
  const finalCmd = params.command.replaceAll('{order}', publicId);

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
    items: [params.item],
    note: `Выдано администратором ${params.actor}`,
    isDemo: true,
    userId: null,
    createdAt: nowIso,
    paidAt: nowIso,
    deliveredAt: null,
  };

  await cloudUpsertOrder(order, params.ip ?? null);

  const delivery = await cloudCreateDelivery({
    orderId: order.id,
    productId: params.productId,
    nickname: cleanNick,
    command: finalCmd,
    kind: params.kind,
    caseId: params.caseOrGroupId,
    amount: params.amount,
    status: queuedForBridge ? 'pending' : 'manual',
    skipDedupe: true,
    lastError: queuedForBridge
      ? null
      : process.env.MINECRAFT_BRIDGE_SECRET
        ? 'Включён ручной режим выдачи (переключите на bridge в Настройках)'
        : 'Не задан MINECRAFT_BRIDGE_SECRET — выполните команду вручную или подключите мост',
  });

  logAudit(params.actor, params.auditAction, params.auditDetail, params.ip ?? undefined);
  logEvent('admin_grant', params.eventLabel);

  return { order, delivery, command: finalCmd, queuedForBridge };
}

/**
 * Безлимитная облачная выдача кейсов любому нику.
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
  const qty = Math.max(1, Math.min(10000, Math.floor(Number(input.amount) || 1)));

  const currentMap = await templatesCloud();
  const rawTpl = (input.commandTemplate?.trim() || currentMap.case || CLOUD_SERVER_DEFAULTS.case).replace(/^\/+/, '');
  if (input.saveTemplate && rawTpl) {
    const nextJson = JSON.stringify({ ...currentMap, case: rawTpl });
    setSettings({ delivery_commands: nextJson });
    await cloudSetSettings({ delivery_commands: nextJson });
  }

  const group = getCaseGroup(cleanCase);
  const caseTitle = group?.name ?? `Кейс ${cleanCase}`;
  const productId = `admin-case-${cleanCase}`;

  const command = rawTpl
    .replaceAll('{player}', cleanNick)
    .replaceAll('{case}', cleanCase)
    .replaceAll('{amount}', String(qty))
    .replaceAll('{product}', productId);

  return createUnlimitedAdminGrant({
    nickname: cleanNick,
    productId,
    kind: 'case',
    caseOrGroupId: cleanCase,
    amount: qty,
    command,
    item: {
      productId,
      name: `${caseTitle} ×${qty}`,
      kind: 'case',
      quantity: 1,
      unitPrice: 0,
      total: 0,
      meta: { group: cleanCase, groupName: caseTitle, quantity: qty, adminGrant: true },
    },
    auditAction: 'case_grant',
    auditDetail: `${cleanNick} ← ${caseTitle} (${cleanCase}) ×${qty} · /${command}`,
    eventLabel: `Админ ${input.actor} выдал ${cleanNick}: ${caseTitle} ×${qty}`,
    actor: input.actor,
    ip: input.ip,
  });
}

export type PrivilegeGrantMode = 'set' | 'add' | 'temp30d' | 'temp90d' | 'remove' | 'reset';

const LP_MODE_TEMPLATES: Record<PrivilegeGrantMode, string> = {
  set: 'lp user {player} parent set {rank}',
  add: 'lp user {player} parent add {rank}',
  temp30d: 'lp user {player} parent addtemp {rank} 30d accumulate',
  temp90d: 'lp user {player} parent addtemp {rank} 90d accumulate',
  remove: 'lp user {player} parent remove {rank}',
  reset: 'lp user {player} parent set default',
};

/**
 * Безлимитная выдача / смена привилегий (LuckPerms) любому игроку без ограничений.
 */
export async function grantAdminPrivilegeCloud(input: {
  nickname: string;
  rankId: string;
  mode?: PrivilegeGrantMode;
  commandTemplate?: string;
  saveTemplate?: boolean;
  actor: string;
  ip?: string | null;
}): Promise<{ order: Order; delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean }> {
  const cleanNick = input.nickname.trim();
  const rawRank = input.rankId.trim().replace(/^rank-/, '');
  const cleanRank = rawRank.toLowerCase().replace(/[^a-z0-9_.-]/g, '') || 'default';
  const mode: PrivilegeGrantMode = input.mode ?? 'set';

  const currentMap = await templatesCloud();
  const baseTpl = input.commandTemplate?.trim() || LP_MODE_TEMPLATES[mode] || LP_MODE_TEMPLATES.set;
  const rawTpl = baseTpl.replace(/^\/+/, '');

  if (input.saveTemplate && rawTpl) {
    const nextJson = JSON.stringify({ ...currentMap, rank: rawTpl, rank_replace: rawTpl });
    setSettings({ delivery_commands: nextJson });
    if (isCloudEnabled()) {
      await cloudSetSettings({ delivery_commands: nextJson });
    }
  }

  const product = getProduct(`rank-${cleanRank}`);
  const rankTitle =
    mode === 'reset'
      ? 'Сброс привилегии (default)'
      : mode === 'remove'
        ? `Снятие привилегии ${product?.name ?? cleanRank.toUpperCase()}`
        : `Привилегия ${product?.name ?? cleanRank.toUpperCase()}`;
  const productId = `admin-rank-${mode === 'reset' ? 'default' : cleanRank}`;
  const effectiveRank = mode === 'reset' ? 'default' : cleanRank;

  const command = rawTpl
    .replaceAll('{player}', cleanNick)
    .replaceAll('{rank}', effectiveRank)
    .replaceAll('{amount}', '1')
    .replaceAll('{product}', productId);

  return createUnlimitedAdminGrant({
    nickname: cleanNick,
    productId,
    kind: 'rank',
    caseOrGroupId: effectiveRank,
    amount: 1,
    command,
    item: {
      productId,
      name: rankTitle,
      kind: 'rank',
      quantity: 1,
      unitPrice: 0,
      total: 0,
      meta: { rankId: effectiveRank, mode, adminGrant: true },
    },
    auditAction: 'rank_grant',
    auditDetail: `${cleanNick} ← ${rankTitle} (${effectiveRank}, ${mode}) · /${command}`,
    eventLabel: `Админ ${input.actor} выдал ${cleanNick}: ${rankTitle}`,
    actor: input.actor,
    ip: input.ip,
  });
}

/**
 * Безлимитная выдача риликов / донат-валюты (PlayerPoints / Vault) любому нику.
 */
export async function grantAdminRelicCloud(input: {
  nickname: string;
  amount: number;
  commandTemplate?: string;
  saveTemplate?: boolean;
  actor: string;
  ip?: string | null;
}): Promise<{ order: Order; delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean }> {
  const cleanNick = input.nickname.trim();
  const qty = Math.max(1, Math.min(10_000_000, Math.floor(Number(input.amount) || 1000)));
  const currentMap = await templatesCloud();
  const rawTpl = (input.commandTemplate?.trim() || currentMap.relic || CLOUD_SERVER_DEFAULTS.relic).replace(/^\/+/, '');

  if (input.saveTemplate && rawTpl) {
    const nextJson = JSON.stringify({ ...currentMap, relic: rawTpl });
    setSettings({ delivery_commands: nextJson });
    if (isCloudEnabled()) {
      await cloudSetSettings({ delivery_commands: nextJson });
    }
  }

  const productId = `admin-relic-${qty}`;
  const command = rawTpl
    .replaceAll('{player}', cleanNick)
    .replaceAll('{amount}', String(qty))
    .replaceAll('{product}', productId);

  return createUnlimitedAdminGrant({
    nickname: cleanNick,
    productId,
    kind: 'relic',
    caseOrGroupId: null,
    amount: qty,
    command,
    item: {
      productId,
      name: `${qty.toLocaleString('ru-RU')} риликов`,
      kind: 'relic',
      quantity: 1,
      unitPrice: 0,
      total: 0,
      meta: { amount: qty, adminGrant: true },
    },
    auditAction: 'relic_grant',
    auditDetail: `${cleanNick} ← ${qty} риликов · /${command}`,
    eventLabel: `Админ ${input.actor} выдал ${cleanNick}: ${qty} риликов`,
    actor: input.actor,
    ip: input.ip,
  });
}

/**
 * Выдача сервисных действий (разбан, размут, кит) любому нику.
 */
export async function grantAdminServiceCloud(input: {
  nickname: string;
  serviceAction: 'pardon' | 'unban' | 'unmute' | 'pardon_ip' | 'kit';
  kitName?: string;
  actor: string;
  ip?: string | null;
}): Promise<{ order: Order; delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean }> {
  const cleanNick = input.nickname.trim();
  const kit = (input.kitName || 'start').trim().replace(/[^a-zA-Z0-9_-]/g, '') || 'start';
  const mapByAction: Record<typeof input.serviceAction, { label: string; cmd: string }> = {
    pardon: { label: 'Разбан (pardon)', cmd: `pardon ${cleanNick}` },
    unban: { label: 'Разбан (unban)', cmd: `unban ${cleanNick}` },
    unmute: { label: 'Размут (unmute)', cmd: `unmute ${cleanNick}` },
    pardon_ip: { label: 'Разбан IP', cmd: `pardon-ip ${cleanNick}` },
    kit: { label: `Кит ${kit}`, cmd: `kit ${kit} ${cleanNick}` },
  };
  const chosen = mapByAction[input.serviceAction] ?? mapByAction.pardon;
  const productId = `admin-other-${input.serviceAction}`;

  return createUnlimitedAdminGrant({
    nickname: cleanNick,
    productId,
    kind: 'other',
    caseOrGroupId: input.serviceAction === 'kit' ? kit : null,
    amount: 1,
    command: chosen.cmd,
    item: {
      productId,
      name: chosen.label,
      kind: 'other',
      quantity: 1,
      unitPrice: 0,
      total: 0,
      meta: { action: input.serviceAction, adminGrant: true },
    },
    auditAction: 'service_grant',
    auditDetail: `${cleanNick} ← ${chosen.label} · /${chosen.cmd}`,
    eventLabel: `Админ ${input.actor}: ${chosen.label} для ${cleanNick}`,
    actor: input.actor,
    ip: input.ip,
  });
}

/**
 * Повторно создаёт НОВУЮ задачу выдачи на основе любой предыдущей задачи (даже уже выданной!),
 * чтобы можно было в 1 клик «Выдать ещё раз» тот же кейс или привилегию без ограничений.
 */
export async function repeatDeliveryCloud(input: {
  deliveryId: string;
  actor: string;
  ip?: string | null;
}): Promise<{ delivery: CloudDeliveryRow; command: string; queuedForBridge: boolean } | null> {
  if (!isCloudEnabled()) {
    const existing = listDeliveries({ limit: 300 }).find((d) => d.id === input.deliveryId);
    if (!existing) return null;
    const order = createOrder({
      nickname: existing.nickname,
      items: [
        {
          productId: existing.productId,
          name: `Повторная выдача (${existing.productId})`,
          kind: 'other',
          quantity: 1,
          unitPrice: 0,
          total: 0,
        },
      ],
      subtotal: 0,
      discount: 0,
      total: 0,
      provider: 'admin',
      isDemo: true,
      note: `Повторная выдача админом ${input.actor}`,
      ip: input.ip ?? null,
    });
    const queuedForBridge = bridgeQueueEnabled();
    const created = createDelivery({
      orderId: order.id,
      productId: existing.productId,
      nickname: existing.nickname,
      command: existing.command,
    })!;
    if (!queuedForBridge) {
      updateDelivery(created.id, { status: 'manual', lastError: 'Мост Minecraft не подключён' });
    }
    return {
      delivery: listDeliveries({ orderId: order.id, limit: 1 })[0] ?? created,
      command: existing.command,
      queuedForBridge,
    };
  }

  const existing = await cloudGetDelivery(input.deliveryId);
  if (!existing) return null;

  const orderId = crypto.randomUUID();
  const publicId = `KG-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
  const nowIso = new Date().toISOString();
  const queuedForBridge = await bridgeQueueEnabledCloud();

  await cloudUpsertOrder(
    {
      id: orderId,
      publicId,
      nickname: existing.nickname,
      email: null,
      status: queuedForBridge ? 'delivering' : 'paid',
      subtotal: 0,
      discount: 0,
      total: 0,
      promoCode: null,
      provider: 'admin',
      paymentId: null,
      paymentUrl: null,
      items: [
        {
          productId: existing.productId,
          name: `Повтор: ${existing.command}`,
          kind: (existing.kind as OrderItem['kind']) || 'case',
          quantity: 1,
          unitPrice: 0,
          total: 0,
        },
      ],
      note: `Повторная выдача администратором ${input.actor}`,
      isDemo: true,
      userId: null,
      createdAt: nowIso,
      paidAt: nowIso,
      deliveredAt: null,
    },
    input.ip ?? null,
  );

  const delivery = await cloudCreateDelivery({
    orderId,
    productId: existing.productId,
    nickname: existing.nickname,
    command: existing.command,
    kind: existing.kind ?? 'case',
    caseId: existing.caseId ?? null,
    amount: existing.amount ?? 1,
    status: queuedForBridge ? 'pending' : 'manual',
    skipDedupe: true,
  });

  logAudit(input.actor, 'delivery_repeat', `${existing.nickname} ← /${existing.command}`, input.ip ?? undefined);
  return { delivery, command: existing.command, queuedForBridge };
}

export function listRecentAdminGrants(limit = 16): Delivery[] {
  return listDeliveries({ limit: 150 }).slice(0, limit);
}

export async function listRecentAdminGrantsCloud(limit = 20, kindFilter?: string): Promise<CloudDeliveryRow[]> {
  if (!isCloudEnabled()) {
    const local = listRecentAdminGrants(limit);
    return kindFilter ? local.filter((d) => d.productId.includes(kindFilter)) : local;
  }
  try {
    const rows = await cloudListDeliveries({ limit: 150 });
    const filtered = kindFilter ? rows.filter((d) => d.kind === kindFilter || d.productId.includes(kindFilter)) : rows;
    return filtered.slice(0, limit);
  } catch {
    return listRecentAdminGrants(limit);
  }
}

export async function listRecentRecipientNicksCloud(limit = 12): Promise<string[]> {
  const seen = new Set<string>();
  const result: string[] = [];
  const add = (nick: string | null | undefined) => {
    const clean = (nick || '').trim();
    if (!clean || clean.length < 3) return;
    const key = clean.toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);
    result.push(clean);
  };

  if (isCloudEnabled()) {
    try {
      const [bridge, deliveries, orders] = await Promise.all([
        cloudGetBridgeState(),
        cloudListDeliveries({ limit: 80 }),
        cloudListOrders(80),
      ]);
      for (const p of bridge.onlinePlayers) add(p);
      for (const d of deliveries) add(d.nickname);
      for (const o of orders) add(o.nickname);
    } catch {
      /* fallback */
    }
  } else {
    for (const d of listDeliveries({ limit: 80 })) add(d.nickname);
  }
  return result.slice(0, limit);
}

export type { CloudBridgeState, CloudDeliveryRow };
