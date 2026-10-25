import { NextResponse } from 'next/server';
import { z } from 'zod';
import { confirmPaidOrder } from '@/lib/order-service';
import { activeProvider, isTestMode } from '@/lib/payments';
import { getOrder, logEvent } from '@/lib/repo';
import { clientIp, rateLimit } from '@/lib/rate-limit';

const schema = z.object({ publicId: z.string().min(3).max(40) });

/** Только тестовый режим: имитация успешной оплаты для провайдера mock. */
export async function POST(req: Request) {
  const limit = rateLimit(`mock-pay:${clientIp(req)}`, 20, 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много запросов' }, { status: 429 });

  if (activeProvider() !== 'mock' || !isTestMode()) {
    return NextResponse.json(
      { error: 'Тестовые платежи отключены: активен реальный провайдер или тестовый режим выключен.' },
      { status: 403 },
    );
  }

  const parsed = schema.safeParse(await req.json().catch(() => null));
  if (!parsed.success) return NextResponse.json({ error: 'Некорректные данные' }, { status: 400 });

  const order = getOrder(parsed.data.publicId);
  if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });
  if (order.isDemo === false) return NextResponse.json({ error: 'Заказ не является тестовым' }, { status: 403 });

  const result = confirmPaidOrder({
    orderId: order.id,
    provider: 'mock',
    providerPaymentId: order.paymentId ?? `mock_${order.id}`,
    amount: order.total,
    raw: JSON.stringify({ mock: true, at: new Date().toISOString() }),
  });
  if (!result.ok) return NextResponse.json({ error: result.error }, { status: 400 });
  logEvent('payment_mock', `Тестовая оплата заказа ${order.publicId}`);
  return NextResponse.json({ ok: true, already: result.already ?? false });
}
