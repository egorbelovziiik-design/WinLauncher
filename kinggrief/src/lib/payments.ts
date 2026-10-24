import { createHash, randomUUID } from 'node:crypto';
import { getSetting } from './repo';
import type { Order } from './types';

export type ProviderId = 'mock' | 'yookassa' | 'robokassa';

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
