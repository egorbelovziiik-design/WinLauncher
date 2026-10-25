import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { getOrder, listOrders, listPayments, logAudit, updateDelivery, updateOrderStatus, listDeliveries } from '@/lib/repo';
import { syncOrderDeliveryStatus } from '@/lib/delivery';
import {
  cloudGetOrder,
  cloudListDeliveries,
  cloudListOrders,
  cloudSyncOrderDeliveryStatus,
  cloudUpdateDelivery,
  cloudUpdateOrderStatus,
  isCloudEnabled,
} from '@/lib/cloud-store';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export const dynamic = 'force-dynamic';

const STATUSES = ['pending_payment', 'paid', 'delivering', 'delivered', 'cancelled', 'failed'];

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const url = new URL(req.url);
  const id = url.searchParams.get('id');

  if (isCloudEnabled()) {
    if (id) {
      const order = (await cloudGetOrder(id)) ?? getOrder(id);
      if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });
      const deliveries = await cloudListDeliveries({ orderId: order.id, limit: 100 });
      return NextResponse.json({
        order,
        payments: listPayments(order.id),
        deliveries,
      });
    }
    const statusFilter = url.searchParams.get('status') ?? 'all';
    const q = (url.searchParams.get('q') ?? '').trim().toLowerCase();
    let orders = await cloudListOrders(300);
    if (statusFilter !== 'all') orders = orders.filter((o) => o.status === statusFilter);
    if (q) {
      orders = orders.filter(
        (o) =>
          o.publicId.toLowerCase().includes(q) ||
          o.nickname.toLowerCase().includes(q) ||
          (o.email ?? '').toLowerCase().includes(q),
      );
    }
    return NextResponse.json({ orders });
  }

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

  if (isCloudEnabled()) {
    const order = (await cloudGetOrder(body.orderId)) ?? getOrder(body.orderId);
    if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });

    if (body.action === 'status') {
      if (!body.status || !STATUSES.includes(body.status))
        return NextResponse.json({ error: 'Некорректный статус' }, { status: 400 });
      await cloudUpdateOrderStatus(order.id, body.status as never);
      logAudit(guard.admin.username, 'order_status', `${order.publicId} → ${body.status}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }

    if (body.action === 'mark-delivered') {
      const list = await cloudListDeliveries({ orderId: order.id, limit: 200 });
      for (const delivery of list) {
        if (delivery.status !== 'delivered') await cloudUpdateDelivery(delivery.id, { status: 'delivered' });
      }
      await cloudSyncOrderDeliveryStatus(order.id);
      logAudit(guard.admin.username, 'order_delivered', `${order.publicId} помечен выданным`, clientIp(req));
      return NextResponse.json({ ok: true });
    }

    if (body.action === 'retry-delivery') {
      const list = await cloudListDeliveries({ orderId: order.id, limit: 200 });
      const delivery = list.find((d) => d.id === body.deliveryId);
      if (!delivery) return NextResponse.json({ error: 'Задача выдачи не найдена' }, { status: 404 });
      await cloudUpdateDelivery(delivery.id, { status: 'pending', lastError: null });
      await cloudSyncOrderDeliveryStatus(order.id);
      logAudit(guard.admin.username, 'delivery_retry', `${order.publicId} · ${delivery.command}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }

    return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
  }

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
