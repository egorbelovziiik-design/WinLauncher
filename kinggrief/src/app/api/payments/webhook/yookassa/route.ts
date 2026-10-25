import { NextResponse } from 'next/server';
import { confirmPaidOrder } from '@/lib/order-service';
import { PaymentConfigError, verifyYookassa } from '@/lib/payments';
import { findPaymentByProviderId, getOrder, logEvent, updatePaymentRecord } from '@/lib/repo';

/**
 * Вебхук ЮKassa. Телу запроса не доверяем: статус и сумма перепроверяются
 * запросом к API ЮKassa по id платежа.
 */
export async function POST(req: Request) {
  let payload: { event?: string; object?: { id?: string } };
  try {
    payload = await req.json();
  } catch {
    return NextResponse.json({ error: 'bad json' }, { status: 400 });
  }
  const paymentId = payload?.object?.id;
  if (!paymentId) return NextResponse.json({ error: 'no payment id' }, { status: 400 });

  try {
    const verified = await verifyYookassa(paymentId);
    const local = findPaymentByProviderId('yookassa', paymentId);
    const orderId = verified.orderId || local?.orderId || null;

    if (verified.status === 'canceled') {
      if (local) updatePaymentRecord(local.id, { status: 'canceled' });
      logEvent('payment_canceled', `ЮKassa: платёж ${paymentId} отменён`);
      return NextResponse.json({ ok: true });
    }
    if (!verified.ok) return NextResponse.json({ ok: true, pending: true });

    if (!orderId) return NextResponse.json({ error: 'order not found' }, { status: 404 });
    const order = getOrder(orderId);
    if (!order) return NextResponse.json({ error: 'order not found' }, { status: 404 });

    const result = confirmPaidOrder({
      orderId: order.id,
      provider: 'yookassa',
      providerPaymentId: paymentId,
      amount: verified.amount,
      raw: verified.raw,
    });
    if (!result.ok) {
      logEvent('payment_error', `ЮKassa ${paymentId}: ${result.error}`);
      return NextResponse.json({ error: result.error }, { status: 400 });
    }
    return NextResponse.json({ ok: true });
  } catch (error) {
    if (error instanceof PaymentConfigError) {
      return NextResponse.json({ error: 'provider not configured' }, { status: 503 });
    }
    console.error('yookassa webhook', error);
    return NextResponse.json({ error: 'internal' }, { status: 500 });
  }
}
