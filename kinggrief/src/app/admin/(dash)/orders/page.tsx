import { listOrders } from '@/lib/repo';
import { OrdersManager } from '@/components/admin/orders-manager';

export const dynamic = 'force-dynamic';

export default async function AdminOrdersPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = await searchParams;
  const orders = listOrders({ limit: 300 });
  return <OrdersManager orders={orders} initialQuery={q ?? ''} />;
}
