import { confirmPaidOrder } from '@/lib/order-service';
import { isTestMode, verifyRobokassaSignature } from '@/lib/payments';
import { findPaymentById, getOrder, logEvent, updatePaymentRecord } from '@/lib/repo';

/** ResultURL Robokassa: проверяем подпись, сумму и только затем выдаём товар. */
async function handle(req: Request) {
  const url = new URL(req.url);
  const params = url.searchParams;
  if (req.method === 'POST') {
    const form = await req.formData().catch(() => null);
    form?.forEach((value, key) => params.set(key, String(value)));
  }

  const outSum = params.get('OutSum') ?? '';
  const invId = params.get('InvId') ?? '';
  const signature = params.get('SignatureValue') ?? '';

  if (!outSum || !invId || !signature) return new Response('bad request', { status: 400 });
  if (!verifyRobokassaSignature({ outSum, invId, signature })) {
    logEvent('payment_error', `Robokassa: неверная подпись InvId=${invId}`);
    return new Response('bad signature', { status: 400 });
  }

  const payment = findPaymentById(Number(invId));
  if (!payment) return new Response('payment not found', { status: 404 });
  const order = getOrder(payment.orderId);
  if (!order) return new Response('order not found', { status: 404 });

  const amount = Number(outSum.replace(',', '.'));
  if (!Number.isFinite(amount)) return new Response('bad amount', { status: 400 });

  const result = confirmPaidOrder({
    orderId: order.id,
    provider: 'robokassa',
    providerPaymentId: invId,
    amount,
    raw: JSON.stringify({ outSum, invId, test: isTestMode() }),
  });
  if (!result.ok) {
    logEvent('payment_error', `Robokassa ${invId}: ${result.error}`);
    return new Response('amount mismatch', { status: 400 });
  }
  updatePaymentRecord(payment.id, { providerPaymentId: invId, status: 'succeeded' });
  return new Response(`OK${invId}`, { status: 200, headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
}

export async function GET(req: Request) {
  return handle(req);
}
export async function POST(req: Request) {
  return handle(req);
}
