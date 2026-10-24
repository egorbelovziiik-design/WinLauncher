import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { getOrder, listOrders, listPayments, logAudit, updateDelivery, updateOrderStatus, listDeliveries } from '@/lib/repo';
import { syncOrderDeliveryStatus } from '@/lib/delivery';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

const STATUSES = ['pending_payment', 'paid', 'delivering', 'delivered', 'cancelled', 'failed'];

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const url = new URL(req.url);
  const id = url.searchParams.get('id');
  if (id) {
    const order = getOrder(id);
    if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });
    return NextResponse.json({
      order,
      payments: listPayments(order.id),
      deliveries: listDeliveries({ orderId: order.id, limit: 100 }),
    });
  }
  return NextResponse.json({
    orders: listOrders({ status: url.searchParams.get('status') ?? 'all', q: url.searchParams.get('q') ?? '', limit: 300 }),
  });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const body = await readJson<{ action?: string; orderId?: string; status?: string; deliveryId?: string }>(req);
  if (!body?.action || !body.orderId) return NextResponse.json({ error: 'Не указан заказ' }, { status: 400 });
  const order = getOrder(body.orderId);
  if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });

  if (body.action === 'status') {
    if (!body.status || !STATUSES.includes(body.status))
      return NextResponse.json({ error: 'Некорректный статус' }, { status: 400 });
    updateOrderStatus(order.id, body.status as never);
    logAudit(guard.admin.username, 'order_status', `${order.publicId} → ${body.status}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'mark-delivered') {
    for (const delivery of listDeliveries({ orderId: order.id, limit: 200 })) {
      if (delivery.status !== 'delivered') updateDelivery(delivery.id, { status: 'delivered' });
    }
    syncOrderDeliveryStatus(order.id);
    logAudit(guard.admin.username, 'order_delivered', `${order.publicId} помечен выданным`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'retry-delivery') {
    const delivery = listDeliveries({ orderId: order.id, limit: 200 }).find((d) => d.id === body.deliveryId);
    if (!delivery) return NextResponse.json({ error: 'Задача выдачи не найдена' }, { status: 404 });
    updateDelivery(delivery.id, { status: 'pending', lastError: null });
    syncOrderDeliveryStatus(order.id);
    logAudit(guard.admin.username, 'delivery_retry', `${order.publicId} · ${delivery.command}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
}
