import crypto from 'node:crypto';
import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { bridgeQueueEnabledCloud } from '@/lib/delivery';
import { cloudCreateDelivery, cloudUpsertOrder, isCloudEnabled } from '@/lib/cloud-store';
import type { Order } from '@/lib/types';

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: false });
  if (!guard.ok) {
    return NextResponse.json(
      { ok: false, error: 'Для прямой облачной отправки войдите в /admin (или используйте скопированную команду /lp applyedits в игре!)' },
      { status: 401 },
    );
  }

  let body: { commands?: unknown; targetNick?: unknown } = {};
  try {
    body = (await req.json()) as { commands?: unknown; targetNick?: unknown };
  } catch {
    return NextResponse.json({ ok: false, error: 'Некорректный JSON' }, { status: 400 });
  }

  const rawList = Array.isArray(body.commands) ? body.commands : [];
  const commands = rawList
    .map((c) => (typeof c === 'string' ? c.trim().replace(/^\/+/, '') : ''))
    .filter((c) => c.toLowerCase().startsWith('lp ') && c.length <= 400)
    .slice(0, 50);

  if (commands.length === 0) {
    return NextResponse.json({ ok: false, error: 'Нет изменений для отправки' }, { status: 400 });
  }

  if (!isCloudEnabled()) {
    return NextResponse.json({ ok: true, queued: 0, note: 'Локальный режим — примените /lp applyedits в игре' });
  }

  const queuedForBridge = await bridgeQueueEnabledCloud();
  const orderId = crypto.randomUUID();
  const publicId = `LP-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
  const nowIso = new Date().toISOString();
  const nick = typeof body.targetNick === 'string' && body.targetNick.trim() ? body.targetNick.trim() : 'CONSOLE';

  const order: Order = {
    id: orderId,
    publicId,
    nickname: nick,
    email: null,
    status: queuedForBridge ? 'delivering' : 'paid',
    subtotal: 0,
    discount: 0,
    total: 0,
    promoCode: null,
    provider: 'admin',
    paymentId: null,
    paymentUrl: null,
    items: [
      {
        productId: 'lp-web-editor',
        name: `LP Web Editor (${commands.length} изм.)`,
        kind: 'other',
        quantity: 1,
        unitPrice: 0,
        total: 0,
        meta: { lpEditor: true },
      },
    ],
    note: `Web LP Editor · ${guard.admin.username}`,
    isDemo: true,
    userId: null,
    createdAt: nowIso,
    paidAt: nowIso,
    deliveredAt: null,
  };

  await cloudUpsertOrder(order, null);

  let queued = 0;
  for (const cmd of commands) {
    await cloudCreateDelivery({
      orderId,
      productId: 'lp-web-editor',
      nickname: nick,
      command: cmd,
      kind: 'service',
      caseId: 'lp',
      amount: 1,
      status: queuedForBridge ? 'pending' : 'manual',
      skipDedupe: true,
      lastError: null,
    });
    queued++;
  }

  return NextResponse.json({ ok: true, queued, queuedForBridge, publicId });
}
