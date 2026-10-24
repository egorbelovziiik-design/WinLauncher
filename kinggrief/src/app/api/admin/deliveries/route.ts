import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { listDeliveries, logAudit, updateDelivery } from '@/lib/repo';
import { syncOrderDeliveryStatus } from '@/lib/delivery';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const status = new URL(req.url).searchParams.get('status') ?? 'all';
  return NextResponse.json({ deliveries: listDeliveries({ status, limit: 300 }) });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const body = await readJson<{ id?: string; action?: string }>(req);
  if (!body?.id || !body.action) return NextResponse.json({ error: 'Нет данных' }, { status: 400 });
  const delivery = listDeliveries({ limit: 1000 }).find((d) => d.id === body.id);
  if (!delivery) return NextResponse.json({ error: 'Задача не найдена' }, { status: 404 });

  if (body.action === 'deliver') updateDelivery(delivery.id, { status: 'delivered' });
  else if (body.action === 'retry') updateDelivery(delivery.id, { status: 'pending', lastError: null });
  else if (body.action === 'fail') updateDelivery(delivery.id, { status: 'failed', lastError: 'Отмечено вручную' });
  else return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  syncOrderDeliveryStatus(delivery.orderId);
  logAudit(guard.admin.username, `delivery_${body.action}`, `${delivery.command}`, clientIp(req));
  return NextResponse.json({ ok: true });
}
