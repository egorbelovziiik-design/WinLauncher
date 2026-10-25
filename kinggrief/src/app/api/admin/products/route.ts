import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { deleteProduct, getProduct, listProducts, logAudit, saveProduct } from '@/lib/repo';
import { cloudSaveTable, cloudSyncLocalDb } from '@/lib/cloud-store';
import { firstError, productSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export const dynamic = 'force-dynamic';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();
  const kind = new URL(req.url).searchParams.get('kind') ?? undefined;
  return NextResponse.json({ products: listProducts({ kind: kind || undefined }) });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb({ force: true });
  const body = await readJson<{ action?: string; product?: unknown; id?: string }>(req);
  if (!body) return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });

  if (body.action === 'delete') {
    if (!body.id) return NextResponse.json({ error: 'Не указан товар' }, { status: 400 });
    const existing = getProduct(body.id);
    deleteProduct(body.id);
    await cloudSaveTable('products');
    logAudit(guard.admin.username, 'product_delete', `${existing?.name ?? body.id}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'toggle') {
    if (!body.id) return NextResponse.json({ error: 'Не указан товар' }, { status: 400 });
    const existing = getProduct(body.id);
    if (!existing) return NextResponse.json({ error: 'Товар не найден' }, { status: 404 });
    const saved = saveProduct({ id: existing.id, active: !existing.active });
    await cloudSaveTable('products');
    logAudit(guard.admin.username, 'product_toggle', `${saved.name} → ${saved.active ? 'вкл' : 'выкл'}`, clientIp(req));
    return NextResponse.json({ ok: true, product: saved });
  }

  const parsed = productSchema.safeParse(body.product ?? body);
  if (!parsed.success)
    return NextResponse.json({ error: firstError(parsed.error) }, { status: 400 });
  const saved = saveProduct(parsed.data);
  await cloudSaveTable('products');
  logAudit(guard.admin.username, 'product_save', `${saved.name} (${saved.price} ₽)`, clientIp(req));
  return NextResponse.json({ ok: true, product: saved });
}
