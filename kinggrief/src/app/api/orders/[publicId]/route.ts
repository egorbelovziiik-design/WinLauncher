import { NextResponse } from 'next/server';
import { getOrder } from '@/lib/repo';
import { cloudGetOrder, isCloudEnabled } from '@/lib/cloud-store';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export const dynamic = 'force-dynamic';

export async function GET(req: Request, ctx: { params: Promise<{ publicId: string }> }) {
  const limit = rateLimit(`order-get:${clientIp(req)}`, 60, 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много запросов' }, { status: 429 });
  const { publicId } = await ctx.params;
  const order = (isCloudEnabled() ? await cloudGetOrder(publicId).catch(() => null) : null) ?? getOrder(publicId);
  if (!order) return NextResponse.json({ error: 'Заказ не найден' }, { status: 404 });
  return NextResponse.json({
    publicId: order.publicId,
    nickname: order.nickname,
    status: order.status,
    total: order.total,
    discount: order.discount,
    promoCode: order.promoCode,
    provider: order.provider,
    paymentUrl: order.paymentUrl,
    isDemo: order.isDemo,
    items: order.items.map((i) => ({ name: i.name, kind: i.kind, quantity: i.quantity, total: i.total })),
    createdAt: order.createdAt,
    paidAt: order.paidAt,
    deliveredAt: order.deliveredAt,
  });
}
