import { NextResponse } from 'next/server';
import { z } from 'zod';
import { priceCart } from '@/lib/order-service';
import { clientIp, rateLimit } from '@/lib/rate-limit';

const schema = z.object({
  items: z.array(z.object({ productId: z.string().min(1), quantity: z.number().int().min(1).max(99) })).max(30),
  promoCode: z.string().trim().max(32).optional().nullable(),
});

export async function POST(req: Request) {
  const limit = rateLimit(`price:${clientIp(req)}`, 90, 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много запросов' }, { status: 429 });
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });
  }
  const parsed = schema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: 'Некорректные данные' }, { status: 400 });
  const result = priceCart(parsed.data.items, parsed.data.promoCode);
  if (!result.ok) return NextResponse.json({ error: result.error }, { status: 400 });
  return NextResponse.json({
    subtotal: result.subtotal,
    discount: result.discount,
    total: result.total,
    promoCode: result.promoCode,
    promoError: result.promoError,
    items: result.items.map((i) => ({ productId: i.productId, total: i.total, unitPrice: i.unitPrice })),
  });
}
