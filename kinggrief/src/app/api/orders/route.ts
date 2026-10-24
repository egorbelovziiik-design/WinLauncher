import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { createOrderSchema, firstError } from '@/lib/validation';
import { createCheckout, OrderError } from '@/lib/order-service';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`order:${ip}`, 10, 10 * 60_000);
  if (!limit.ok) {
    return NextResponse.json(
      { error: 'Слишком много попыток оформления. Попробуйте через несколько минут.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfter) } },
    );
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });
  }

  const parsed = createOrderSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: firstError(parsed.error as ZodError) }, { status: 400 });
  }

  try {
    const result = await createCheckout({
      nickname: parsed.data.nickname,
      email: parsed.data.email || null,
      items: parsed.data.items,
      promoCode: parsed.data.promoCode ?? null,
      note: parsed.data.note ?? null,
      ip,
    });
    return NextResponse.json(
      {
        publicId: result.order.publicId,
        total: result.order.total,
        paymentUrl: result.paymentUrl,
        provider: result.provider,
        demo: result.demo,
      },
      { status: 201 },
    );
  } catch (error) {
    if (error instanceof OrderError) return NextResponse.json({ error: error.message }, { status: 400 });
    console.error('order error', error);
    return NextResponse.json({ error: 'Не удалось создать заказ. Попробуйте позже.' }, { status: 500 });
  }
}
