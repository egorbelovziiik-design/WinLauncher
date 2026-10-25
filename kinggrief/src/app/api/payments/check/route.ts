import { NextResponse } from 'next/server';
import { z } from 'zod';
import { confirmPaidOrder } from '@/lib/order-service';
import { confirmPaidOrderCloud } from '@/lib/delivery';
import { cloudGetOrder, isCloudEnabled } from '@/lib/cloud-store';
import { activeProvider, verifyYookassa } from '@/lib/payments';
import { getOrder } from '@/lib/repo';
import { clientIp, rateLimit } from '@/lib/rate-limit';

const schema = z.object({ publicId: z.string().min(3).max(40) });

/** Ручная проверка статуса платежа (кнопка «Я оплатил» на странице заказа). */
export async function POST(req: Request) {
  const limit = rateLimit(`pay-check:${clientIp(req)}`, 20, 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много запросов' }, { status: 429 });
  const parsed = schema.safeParse(await req.json().catch(() => null));
  if (!parsed.success) return NextResponse.json({ error: 'Некорректные данные' }, { status: 400 });

  const order = (isCloudEnabled() ? await cloudGetOrder(parsed.data.publicId).catch(() => null) : null) ?? getOrder(parsed.data.publicId);
  if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });
  if (['paid', 'delivering', 'delivered'].includes(order.status))
    return NextResponse.json({ status: order.status, paid: true });

  const provider = activeProvider();
  if (provider === 'yookassa' && order.paymentId) {
    const verified = await verifyYookassa(order.paymentId);
    if (verified.ok) {
      if (isCloudEnabled()) {
        const result = await confirmPaidOrderCloud({
          idOrPublicId: order.id,
          provider: 'yookassa',
          providerPaymentId: order.paymentId,
          amount: verified.amount,
        });
        return NextResponse.json({ status: result.order?.status ?? 'delivering', paid: result.ok });
      }
      const result = confirmPaidOrder({
        orderId: order.id,
        provider: 'yookassa',
        providerPaymentId: order.paymentId,
        amount: verified.amount,
        raw: verified.raw,
      });
      return NextResponse.json({ status: getOrder(order.id)?.status, paid: result.ok });
    }
    return NextResponse.json({ status: order.status, paid: false, providerStatus: verified.status });
  }
  return NextResponse.json({ status: order.status, paid: false });
}
