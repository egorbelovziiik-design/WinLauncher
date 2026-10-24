import { NextResponse } from 'next/server';
import { reviewSchema } from '@/lib/validation';
import { createReview, getSetting, listReviews } from '@/lib/repo';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export async function GET() {
  const reviews = getSetting('reviews_enabled', 'true') === 'true' ? listReviews('approved') : [];
  return NextResponse.json({ reviews: reviews.slice(0, 24) });
}

export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`review:${ip}`, 3, 30 * 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много отзывов. Попробуйте позже.' }, { status: 429 });
  if (getSetting('reviews_enabled', 'true') !== 'true')
    return NextResponse.json({ error: 'Отзывы временно отключены' }, { status: 403 });

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });
  }
  const parsed = reviewSchema.safeParse(body);
  if (!parsed.success)
    return NextResponse.json({ error: parsed.error.issues[0]?.message ?? 'Проверьте поля' }, { status: 400 });
  createReview(parsed.data);
  return NextResponse.json({ ok: true, message: 'Спасибо! Отзыв появится после проверки модератором.' }, { status: 201 });
}
