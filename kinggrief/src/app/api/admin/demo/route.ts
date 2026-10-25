import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { getDb } from '@/lib/db';
import {
  createDelivery,
  createOrder,
  getOrder,
  listProducts,
  logAudit,
  updateDelivery,
} from '@/lib/repo';
import { renderCommand } from '@/lib/delivery';
import { cloudDeleteDemoOrders, cloudSyncLocalDb, cloudUpsertOrdersBatch, isCloudEnabled } from '@/lib/cloud-store';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';
import type { Order, OrderItem } from '@/lib/types';

export const dynamic = 'force-dynamic';

const NICKNAMES = ['KosmoKot', 'MiraFox', 'GriefKing', 'Nikitos_228', 'Artemiy_', 'xXDeD_MorozXx', 'SanyaPvP', 'LunaWolf', 'DedInside', 'CraftPro'];

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();
  const body = await readJson<{ action?: string; count?: number }>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  if (body.action === 'clear') {
    getDb().prepare('DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE is_demo = 1)').run();
    getDb().prepare('DELETE FROM deliveries WHERE order_id IN (SELECT id FROM orders WHERE is_demo = 1)').run();
    getDb().prepare('DELETE FROM orders WHERE is_demo = 1').run();
    if (isCloudEnabled()) {
      await cloudDeleteDemoOrders();
    }
    logAudit(guard.admin.username, 'demo_clear', 'Удалены демо-заказы', clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'generate') {
    const count = Math.min(Math.max(Number(body.count ?? 24), 1), 120);
    const products = listProducts({ activeOnly: true }).filter((p) => p.kind === 'rank' || p.kind === 'relic' || p.kind === 'case');
    if (products.length === 0) return NextResponse.json({ error: 'Нет активных товаров' }, { status: 400 });
    const createdOrders: Order[] = [];

    for (let i = 0; i < count; i++) {
      const product = products[Math.floor(Math.random() * products.length)];
      const quantity = product.kind === 'rank' ? 1 : 1 + Math.floor(Math.random() * 3);
      const item: OrderItem = {
        productId: product.id,
        name: product.name,
        kind: product.kind,
        quantity,
        unitPrice: product.price,
        total: product.price * quantity,
        meta: product.meta,
      };
      const order = createOrder({
        nickname: NICKNAMES[Math.floor(Math.random() * NICKNAMES.length)],
        email: null,
        items: [item],
        subtotal: item.total,
        discount: 0,
        total: item.total,
        promoCode: null,
        provider: 'mock',
        isDemo: true,
        note: 'Демонстрационный заказ (не реальная оплата)',
        ip: null,
      });
      const createdAt = new Date(Date.now() - Math.floor(Math.random() * 20 * 86400000)).toISOString();
      getDb().prepare('UPDATE orders SET created_at = ?, paid_at = ?, delivered_at = ?, status = ? WHERE id = ?').run(createdAt, createdAt, createdAt, 'delivered', order.id);
      const del = createDelivery({
        orderId: order.id,
        productId: product.id,
        nickname: order.nickname,
        command: renderCommand(item, order.nickname, order),
      });
      if (del) updateDelivery(del.id, { status: 'delivered' });
      const fresh = getOrder(order.id);
      if (fresh) createdOrders.push(fresh);
    }

    if (isCloudEnabled() && createdOrders.length > 0) {
      await cloudUpsertOrdersBatch(createdOrders);
    }

    logAudit(guard.admin.username, 'demo_generate', `Создано ${count} демо-заказов`, clientIp(req));
    return NextResponse.json({ ok: true, created: count });
  }

  return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
}
