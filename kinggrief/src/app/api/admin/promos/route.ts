import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { deletePromo, listPromos, logAudit, savePromo } from '@/lib/repo';
import { promoInputSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  return NextResponse.json({ promos: listPromos() });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const body = await readJson<{ action?: string; code?: string; promo?: unknown }>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  if (body.action === 'delete') {
    if (!body.code) return NextResponse.json({ error: 'Не указан код' }, { status: 400 });
    deletePromo(body.code);
    logAudit(guard.admin.username, 'promo_delete', `Удалён промокод ${body.code}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'save') {
    const parsed = promoInputSchema.safeParse(body.promo);
    if (!parsed.success)
      return NextResponse.json({ error: parsed.error.issues[0]?.message ?? 'Проверьте поля' }, { status: 400 });
    const saved = savePromo(parsed.data);
    logAudit(guard.admin.username, 'promo_save', `Промокод ${saved.code}`, clientIp(req));
    return NextResponse.json({ ok: true, promo: saved });
  }

  return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
}
