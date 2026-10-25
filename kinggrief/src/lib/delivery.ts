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
import type { Delivery, Order, OrderItem } from './types';

export type TemplateMap = Record<string, string>;

export const DEFAULT_TEMPLATES: TemplateMap = {
  rank: 'lp user {player} parent add {rank}',
  rank_replace: 'lp user {player} parent set {rank}',
  relic: 'relics add {player} {amount}',
  case: 'cases give {player} {case} {amount}',
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

export function renderCommand(
  item: OrderItem,
  nickname: string,
  order: Order,
  opts: { replaceRank?: boolean } = {},
): string {
  const map = templates();
  const product = getProduct(item.productId);
  const meta = (product?.meta ?? item.meta ?? {}) as Record<string, unknown>;
  let template: string;
  let rankName = '';
  switch (item.kind) {
    case 'rank':
      // Если в заказе несколько привилегий — выдаём их через parent set,
      // чтобы не копились группы, а осталась только самая старшая.
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

export function bridgeStatus() {
  const last = getSetting('bridge_last_seen', '');
  const connected = Boolean(last) && Date.now() - new Date(last).getTime() < 90_000;
  return { connected, lastSeen: last || null, mode: getSetting('online_mode', 'bridge') };
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

export function bridgeSecretMatches(header: string | null) {
  const expected = process.env.MINECRAFT_BRIDGE_SECRET;
  if (!expected || !header) return false;
  return header === expected;
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
 * Мгновенная выдача кейсов из админ-панели (себе или любому игроку).
 * Создаёт служебный заказ (0 ₽, isDemo=true, не влияет на выручку в аналитике)
 * и ставит команду в очередь плагина KingGriefBridge.
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

export function listRecentAdminGrants(limit = 12): Delivery[] {
  return listDeliveries({ limit: 150 })
    .filter((d) => d.productId.startsWith('admin-case-') || d.productId.startsWith('case-'))
    .slice(0, limit);
}

