import { guardAdmin } from '@/lib/auth';
import { listOrders } from '@/lib/repo';
import { cloudListOrders, isCloudEnabled } from '@/lib/cloud-store';

export const dynamic = 'force-dynamic';

function csvCell(value: unknown) {
  const text = String(value ?? '').replace(/"/g, '""');
  return /^[=+\-@]/.test(text) ? `"'${text}"` : `"${text}"`;
}

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return new Response(JSON.stringify({ error: guard.error }), { status: guard.status });
  const orders = isCloudEnabled() ? await cloudListOrders(500) : listOrders({ limit: 5000 });
  const header = ['Номер', 'Дата', 'Никнейм', 'Статус', 'Сумма', 'Скидка', 'Промокод', 'Провайдер', 'Позиции', 'Демо'];
  const rows = orders.map((o) =>
    [
      o.publicId,
      o.createdAt,
      o.nickname,
      o.status,
      o.total,
      o.discount,
      o.promoCode ?? '',
      o.provider,
      o.items.map((i) => `${i.name} ×${i.quantity}`).join('; '),
      o.isDemo ? 'да' : 'нет',
    ]
      .map(csvCell)
      .join(','),
  );
  const csv = '\uFEFF' + [header.map(csvCell).join(','), ...rows].join('\n');
  return new Response(csv, {
    headers: {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="kinggrief-orders-${new Date().toISOString().slice(0, 10)}.csv"`,
    },
  });
}
