import { createHash, randomUUID } from 'node:crypto';
import { getSetting } from './repo';
import type { Order } from './types';

export type ProviderId = 'mock' | 'yookassa' | 'robokassa' | 'card2card';

/* ─────────────── v6.1: CARD-TO-CARD (Visa Украина / Visa Europe) ───────────────
 * Реквизиты карты получения хранятся ТОЛЬКО в server-env Vercel (PAYOUT_CARD_PAN,
 * тип sensitive/encrypted): в Git, в клиентский бандл и в логи они не попадают.
 * В UI везде показывается маска; полный номер открывается только на странице
 * оплаты конкретного заказа (как в любом P2P-переводе) и больше нигде.
 * Сайт НЕ собирает и НЕ хранит платёжные данные плательщика → вне scope PCI DSS.
 */
export type CardMethod = 'visa_ua' | 'visa_eu';

const METHOD_META: Record<CardMethod, { currency: string; symbol: string; rateEnv: string; defRate: number; label: string; hint: string }> = {
  visa_ua: {
    currency: 'UAH',
    symbol: '₴',
    rateEnv: 'RATE_UAH_PER_RUB',
    defRate: 0.45,
    label: 'Visa Украина · перевод с карты на карту',
    hint: 'Гривна (UAH) · карты Visa/Mastercard банков Украины',
  },
  visa_eu: {
    currency: 'EUR',
    symbol: '€',
    rateEnv: 'RATE_EUR_PER_RUB',
    defRate: 0.0102,
    label: 'Visa Europe · card-to-card',
    hint: 'Евро (EUR) · карты Visa/Mastercard европейских банков',
  },
};

export function payoutCardPan(): string {
  return (process.env.PAYOUT_CARD_PAN || '').replace(/\D/g, '');
}

export function cardMethodsEnabled(): boolean {
  return payoutCardPan().length >= 12;
}

export function maskPan(pan: string): string {
  const d = pan.replace(/\D/g, '');
  if (d.length < 8) return '•••• •••• •••• ••••';
  return `${d.slice(0, 4)} •••• •••• ${d.slice(-4)}`;
}

export function formatPan(pan: string): string {
  return pan.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').trim();
}

export function methodMeta(method: CardMethod) {
  return METHOD_META[method];
}

export function isCardMethod(provider: string): boolean {
  return provider.startsWith('card2card:');
}

export function methodOfProvider(provider: string): CardMethod | null {
  const tail = provider.split(':')[1];
  return tail === 'visa_ua' || tail === 'visa_eu' ? tail : null;
}

/** Конвертация суммы заказа в валюту выбранного card-to-card метода. */
export function convertForMethod(totalRub: number, method: CardMethod) {
  const meta = METHOD_META[method];
  const rate = Number(process.env[meta.rateEnv] || meta.defRate);
  const min = meta.currency === 'UAH' ? 5 : 1;
  const value = Math.max(min, Math.round(totalRub * rate * 100) / 100);
  return { amount: value.toFixed(2), currency: meta.currency, symbol: meta.symbol, label: meta.label, hint: meta.hint, rate };
}

export function activeProvider(): ProviderId {
  const fromEnv = (process.env.PAYMENT_PROVIDER || '').toLowerCase();
  const fromDb = (getSetting('kassa_provider', 'mock') || 'mock').toLowerCase();
  const value = fromEnv || fromDb;
  if (value === 'yookassa' || value === 'robokassa' || value === 'mock') return value;
  return 'mock';
}

export function isTestMode(): boolean {
  if (process.env.PAYMENTS_TEST_MODE === 'true') return true;
  if (process.env.PAYMENTS_TEST_MODE === 'false') return false;
  return getSetting('kassa_test_mode', 'true') === 'true';
}

export function siteUrl() {
  return (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(/\/$/, '');
}

export type InitPayment = {
  provider: ProviderId;
  providerPaymentId: string;
  url: string;
  demo: boolean;
};

/** Создаёт платёж у выбранного провайдера. Реальные деньги не списываются без ключей. */
export async function initPayment(order: Order, invId?: number): Promise<InitPayment> {
  const provider = activeProvider();
  const test = isTestMode();

  // Card-to-card: внешнего провайдера нет — инструкция по переводу открывается
  // на странице заказа /checkout/<publicId> (серверный рендер реквизитов).
  if (isCardMethod(order.provider)) {
    return {
      provider: order.provider,
      providerPaymentId: `c2c-${order.publicId}`,
      url: `/checkout/${order.publicId}`,
      demo: false,
    };
  }

  if (provider === 'yookassa') {
    const shopId = process.env.YOOKASSA_SHOP_ID;
    const key = process.env.YOOKASSA_SECRET_KEY;
    if (!shopId || !key) {
      throw new PaymentConfigError('ЮKassa не настроена: задайте YOOKASSA_SHOP_ID и YOOKASSA_SECRET_KEY');
    }
    const base = process.env.YOOKASSA_API_URL || 'https://api.yookassa.ru/v3';
    const res = await fetch(`${base}/payments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotence-Key': order.id,
        Authorization: 'Basic ' + Buffer.from(`${shopId}:${key}`).toString('base64'),
      },
      body: JSON.stringify({
        amount: { value: order.total.toFixed(2), currency: 'RUB' },
        capture: true,
        confirmation: {
          type: 'redirect',
          return_url: `${siteUrl()}/checkout/${order.publicId}`,
        },
        description: `${getSetting('kassa_label', 'KINGGRIEF')} · заказ ${order.publicId}`,
        metadata: { orderId: order.id, publicId: order.publicId },
      }),
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new PaymentConfigError(`ЮKassa отклонила платёж (${res.status}): ${text.slice(0, 300)}`);
    }
    const data = (await res.json()) as { id: string; confirmation?: { confirmation_url?: string } };
    const url = data.confirmation?.confirmation_url;
    if (!url) throw new PaymentConfigError('ЮKassa не вернула ссылку на оплату');
    return { provider, providerPaymentId: data.id, url, demo: false };
  }

  if (provider === 'robokassa') {
    const login = process.env.ROBOKASSA_LOGIN;
    const pass1 = test
      ? process.env.ROBOKASSA_TEST_PASSWORD1 || process.env.ROBOKASSA_PASSWORD1
      : process.env.ROBOKASSA_PASSWORD1;
    if (!login || !pass1) {
      throw new PaymentConfigError('Robokassa не настроена: задайте ROBOKASSA_LOGIN и ROBOKASSA_PASSWORD1');
    }
    const id = invId ?? Math.floor(Date.now() / 1000) % 2_000_000_000;
    const outSum = order.total.toFixed(2);
    const signature = md5(`${login}:${outSum}:${id}:${pass1}`).toUpperCase();
    const params = new URLSearchParams({
      MerchantLogin: login,
      OutSum: outSum,
      InvId: String(id),
      Description: `KINGGRIEF заказ ${order.publicId}`,
      SignatureValue: signature,
      Culture: 'ru',
      Encoding: 'utf-8',
      SuccessURL: `${siteUrl()}/checkout/${order.publicId}`,
      FailURL: `${siteUrl()}/checkout/${order.publicId}?failed=1`,
    });
    if (test) params.set('IsTest', '1');
    return {
      provider,
      providerPaymentId: String(id),
      url: `https://auth.robokassa.ru/Merchant/Index.aspx?${params.toString()}`,
      demo: test,
    };
  }

  return {
    provider: 'mock',
    providerPaymentId: `mock_${randomUUID()}`,
    url: `/checkout/${order.publicId}?demo=1`,
    demo: true,
  };
}

export class PaymentConfigError extends Error {}

export function md5(value: string) {
  return createHash('md5').update(value).digest('hex');
}

export type VerifiedPayment = {
  ok: boolean;
  status: 'succeeded' | 'pending' | 'canceled' | 'unknown';
  amount: number;
  orderId: string | null;
  providerPaymentId: string;
  raw?: string;
};

/** Проверяет платёж напрямую в API ЮKassa — вебхуку без проверки не доверяем. */
export async function verifyYookassa(paymentId: string): Promise<VerifiedPayment> {
  const shopId = process.env.YOOKASSA_SHOP_ID;
  const key = process.env.YOOKASSA_SECRET_KEY;
  if (!shopId || !key) throw new PaymentConfigError('ЮKassa не настроена');
  const base = process.env.YOOKASSA_API_URL || 'https://api.yookassa.ru/v3';
  const res = await fetch(`${base}/payments/${encodeURIComponent(paymentId)}`, {
    headers: { Authorization: 'Basic ' + Buffer.from(`${shopId}:${key}`).toString('base64') },
    cache: 'no-store',
  });
  if (!res.ok) return { ok: false, status: 'unknown', amount: 0, orderId: null, providerPaymentId: paymentId };
  const data = (await res.json()) as {
    id: string;
    status: string;
    paid: boolean;
    amount: { value: string };
    metadata?: { orderId?: string };
  };
  const status =
    data.status === 'succeeded' && data.paid
      ? 'succeeded'
      : data.status === 'canceled'
        ? 'canceled'
        : 'pending';
  return {
    ok: status === 'succeeded',
    status,
    amount: Number(data.amount?.value ?? 0),
    orderId: data.metadata?.orderId ?? null,
    providerPaymentId: data.id,
    raw: JSON.stringify(data),
  };
}

export function verifyRobokassaSignature(params: {
  outSum: string;
  invId: string;
  signature: string;
}): boolean {
  const test = isTestMode();
  const pass2 = test
    ? process.env.ROBOKASSA_TEST_PASSWORD2 || process.env.ROBOKASSA_PASSWORD2
    : process.env.ROBOKASSA_PASSWORD2;
  if (!pass2) return false;
  const expected = md5(`${params.outSum}:${params.invId}:${pass2}`).toUpperCase();
  return expected === params.signature.toUpperCase();
}
