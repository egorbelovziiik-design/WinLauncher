import {
  createDelivery,
  getOrder,
  getProduct,
  getSetting,
  listDeliveries,
  logEvent,
  updateDelivery,
  updateOrderStatus,
} from './repo';
import type { Order, OrderItem } from './types';

export type TemplateMap = Record<string, string>;

export const DEFAULT_TEMPLATES: TemplateMap = {
  rank: 'lp user {player} parent add {rank}',
  rank_replace: 'lp user {player} parent set {rank}',
  relic: 'relics add {player} {amount}',
  case: 'crates key {player} {case} {amount}',
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
    item.kind === 'relic' ? Number(meta.amount ?? 0) * item.quantity : item.quantity;
  return template
    .replaceAll('{player}', nickname)
    .replaceAll('{rank}', rankName)
    .replaceAll('{amount}', String(amount))
    .replaceAll('{relics}', String(Number(meta.amount ?? 0) * item.quantity))
    .replaceAll('{case}', String(meta.group ?? 'super'))
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
