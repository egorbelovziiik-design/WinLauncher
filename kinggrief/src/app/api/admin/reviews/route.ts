import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { deleteReview, listReviews, logAudit, setReviewStatus } from '@/lib/repo';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  return NextResponse.json({ reviews: listReviews() });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const body = await readJson<{ id?: number; action?: string }>(req);
  if (!body?.id || !body.action) return NextResponse.json({ error: 'Нет данных' }, { status: 400 });
  if (body.action === 'approve') setReviewStatus(body.id, 'approved');
  else if (body.action === 'reject') setReviewStatus(body.id, 'rejected');
  else if (body.action === 'delete') deleteReview(body.id);
  else return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
  logAudit(guard.admin.username, `review_${body.action}`, `Отзыв #${body.id}`, clientIp(req));
  return NextResponse.json({ ok: true });
}
