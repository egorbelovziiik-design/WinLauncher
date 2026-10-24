import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { deleteProduct, listProducts, logAudit, saveProduct } from '@/lib/repo';
import { firstError, productSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  return NextResponse.json({ products: listProducts() });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });

  const body = await readJson<{ action?: string; id?: string; product?: unknown }>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  if (body.action === 'delete') {
    if (!body.id) return NextResponse.json({ error: 'Не указан товар' }, { status: 400 });
    deleteProduct(body.id);
    logAudit(guard.admin.username, 'product_delete', `Удалён товар ${body.id}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'toggle') {
    if (!body.id) return NextResponse.json({ error: 'Не указан товар' }, { status: 400 });
    const product = listProducts().find((p) => p.id === body.id);
    if (!product) return NextResponse.json({ error: 'Товар не найден' }, { status: 404 });
    saveProduct({ ...product, active: !product.active });
    logAudit(guard.admin.username, 'product_toggle', `${product.name}: ${!product.active ? 'вкл' : 'выкл'}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  if (body.action === 'save') {
    const parsed = productSchema.safeParse(body.product);
    if (!parsed.success)
      return NextResponse.json({ error: firstError(parsed.error) }, { status: 400 });
    const saved = saveProduct({
      ...parsed.data,
      features: parsed.data.features ?? [],
      meta: parsed.data.meta ?? {},
      oldPrice: parsed.data.oldPrice ?? null,
      badge: parsed.data.badge ?? null,
      subtitle: parsed.data.subtitle ?? '',
      description: parsed.data.description ?? '',
      active: parsed.data.active ?? true,
    });
    logAudit(guard.admin.username, 'product_save', `${saved.name} · ${saved.price} ₽`, clientIp(req));
    return NextResponse.json({ ok: true, product: saved });
  }

  return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
}
