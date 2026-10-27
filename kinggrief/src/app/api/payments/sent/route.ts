import { NextResponse } from 'next/server';
import { z } from 'zod';
import { getOrder, latestPaymentForOrder, updatePaymentRecord, logEvent } from '@/lib/repo';
import { isCardMethod } from '@/lib/payments';
import { rateLimit, clientIp } from '@/lib/rate-limit';

const schema = z.object({ publicId: z.string().trim().min(4).max(40) });

/** Заявка плательщика «я перевёл» по card-to-card заказу: платёж -> status=sent. */
export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`pay-sent:${ip}`, 20, 60_000);
  if (!limit.ok) {
    return NextResponse.json({ error: 'Слишком часто. Повторите через минуту.' }, { status: 429 });
  }
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });
  }
  const parsed = schema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: 'Некорректный номер заказа' }, { status: 400 });

  const order = getOrder(parsed.data.publicId);
  if (!order || !isCardMethod(order.provider)) {
    return NextResponse.json({ error: 'Заказ не найден или оплачивается другим способом' }, { status: 404 });
  }
  const payment = latestPaymentForOrder(order.id);
  if (payment) {
    updatePaymentRecord(payment.id, {
      status: 'sent',
      raw: JSON.stringify({ claimedAt: new Date().toISOString(), method: order.provider }),
    });
  }
  logEvent('payment_sent_claim', `Заказ ${order.publicId}: плательщик заявил перевод (${order.provider})`);
  return NextResponse.json({ ok: true });
}
