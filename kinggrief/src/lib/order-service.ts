import { applyPromo, createOrder, getOrder, getProduct, logEvent, recordPayment, registerPromoUse, setOrderPayment, updateOrderStatus, updatePaymentRecord } from './repo';
import { markPaidAndQueue } from './delivery';
import { cloudSaveTable, cloudSyncLocalDb, cloudUpsertOrder, isCloudEnabled } from './cloud-store';
import { activeProvider, cardMethodsEnabled, initPayment, isTestMode, PaymentConfigError, methodEnabled } from './payments';
import type { CardMethod } from './payments';
import type { Order, OrderItem, Product } from './types';

export type CartLine = { productId: string; quantity: number };

const MAX_PER_KIND: Record<Product['kind'], number> = { rank: 1, case: 500, relic: 500, other: 50 };

export type PriceResult =
  | { ok: true; items: OrderItem[]; subtotal: number; discount: number; total: number; promoCode: string | null; promoError: string | null; kinds: string[] }
  | { ok: false; error: string };

export function priceCart(lines: CartLine[], promoCode?: string | null): PriceResult {
  if (!Array.isArray(lines) || lines.length === 0) return { ok: false, error: 'Корзина пуста' };
  if (lines.length > 30) return { ok: false, error: 'Слишком много позиций в заказе' };

  const seen = new Set<string>();
  const items: OrderItem[] = [];

  for (const line of lines) {
    const product = getProduct(line.productId);
    if (!product) return { ok: false, error: 'Товар не найден' };
    if (!product.active) return { ok: false, error: `Товар «${product.name}» недоступен` };
    if (seen.has(product.id)) return { ok: false, error: 'Товар добавлен дважды' };
    seen.add(product.id);

    const qty = Math.floor(Number(line.quantity));
    if (!Number.isFinite(qty) || qty < 1) return { ok: false, error: 'Некорректное количество' };
    if (qty > MAX_PER_KIND[product.kind]) {
      return {
        ok: false,
        error:
          product.kind === 'rank'
            ? 'Привилегию можно купить только в одном экземпляре'
            : `Максимум ${MAX_PER_KIND[product.kind]} шт. одной позиции`,
      };
    }

    items.push({
      productId: product.id,
      name: product.name,
      kind: product.kind,
      quantity: qty,
      unitPrice: product.price,
      total: product.price * qty,
      meta: product.meta,
    });
  }

  const subtotal = items.reduce((sum, i) => sum + i.total, 0);
  if (subtotal < 1) return { ok: false, error: 'Минимальная сумма заказа — 1 ₽' };
  if (subtotal > 2_000_000) return { ok: false, error: 'Слишком большая сумма заказа — свяжитесь с поддержкой' };

  const kinds = [...new Set(items.map((i) => i.kind))];
  let discount = 0;
  let appliedCode: string | null = null;
  let promoError: string | null = null;

  if (promoCode && promoCode.trim()) {
    const promo = applyPromo(promoCode, subtotal, kinds);
    if (promo.ok) {
      discount = promo.discount;
      appliedCode = promo.promo.code;
    } else {
      promoError = promo.error;
    }
  }

  return {
    ok: true,
    items,
    subtotal,
    discount,
    total: Math.max(1, subtotal - discount),
    promoCode: appliedCode,
    promoError,
    kinds,
  };
}

export type CheckoutPayload = {
  nickname: string;
  email?: string | null;
  items: CartLine[];
  promoCode?: string | null;
  note?: string | null;
  ip?: string | null;
  userId?: number | null;
  method?: CardMethod | null;
};

export async function createCheckout(payload: CheckoutPayload): Promise<{
  order: Order;
  paymentUrl: string;
  demo: boolean;
  provider: string;
}> {
  if (isCloudEnabled()) {
    await cloudSyncLocalDb();
  }
  const priced = priceCart(payload.items, payload.promoCode);
  if (!priced.ok) throw new OrderError(priced.error);

  const baseProvider = activeProvider();
  const provider =
    payload.method && methodEnabled(payload.method) ? (`card2card:${payload.method}` as string) : baseProvider;
  const order = createOrder({
    nickname: payload.nickname,
    email: payload.email || null,
    items: priced.items,
    subtotal: priced.subtotal,
    discount: priced.discount,
    total: priced.total,
    promoCode: priced.promoCode,
    provider,
    isDemo: provider.startsWith('card2card:') ? false : provider === 'mock' || isTestMode(),
    note: payload.note || null,
    ip: payload.ip || null,
    userId: payload.userId ?? null,
  });

  // Резервируем запись платежа — для Robokassa её id используется как InvId.
  const paymentRowId = recordPayment({
    orderId: order.id,
    provider,
    providerPaymentId: null,
    amount: order.total,
    status: 'created',
  });

  try {
    const payment = await initPayment(order, paymentRowId);
    updatePaymentRecord(paymentRowId, { providerPaymentId: payment.providerPaymentId, status: 'pending' });
    setOrderPayment(order.id, {
      paymentId: payment.providerPaymentId,
      paymentUrl: payment.url,
      provider: payment.provider,
    });
    const finalOrder = getOrder(order.id)!;
    if (isCloudEnabled()) {
      await cloudUpsertOrder(finalOrder);
    }
    logEvent('payment_init', `Создан заказ ${order.publicId} · ${provider} · ${order.total} ₽`);
    return { order: finalOrder, paymentUrl: payment.url, demo: payment.demo, provider };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Не удалось создать платёж';
    updatePaymentRecord(paymentRowId, { status: 'failed', raw: message });
    updateOrderStatus(order.id, 'failed');
    logEvent('payment_error', `Заказ ${order.publicId}: ${message}`);
    if (error instanceof PaymentConfigError) throw new OrderError(message);
    throw error;
  }
}

export class OrderError extends Error {}

/**
 * Подтверждение оплаты. Сумма сверяется с сохранённым заказом,
 * повторная обработка идемпотентна.
 */
export function confirmPaidOrder(input: {
  orderId: string;
  provider: string;
  providerPaymentId: string | null;
  amount: number;
  raw?: string;
}): { ok: boolean; already?: boolean; error?: string } {
  const order = getOrder(input.orderId);
  if (!order) return { ok: false, error: 'Заказ не найден' };
  if (['paid', 'delivering', 'delivered'].includes(order.status)) {
    return { ok: true, already: true };
  }
  if (Math.round(input.amount) !== order.total) {
    logEvent('payment_mismatch', `Заказ ${order.publicId}: сумма ${input.amount} ≠ ${order.total}`);
    return { ok: false, error: 'Сумма платежа не совпадает с заказом' };
  }
  recordPayment({
    orderId: order.id,
    provider: input.provider,
    providerPaymentId: input.providerPaymentId,
    amount: order.total,
    status: 'succeeded',
    raw: input.raw,
  });
  setOrderPayment(order.id, { paymentId: input.providerPaymentId ?? undefined, provider: input.provider });
  if (order.promoCode) registerPromoUse(order.promoCode, order.id, order.discount);
  markPaidAndQueue(order.id);
  return { ok: true };
}

export function cartCount(lines: CartLine[]) {
  return lines.reduce((sum, l) => sum + l.quantity, 0);
}
