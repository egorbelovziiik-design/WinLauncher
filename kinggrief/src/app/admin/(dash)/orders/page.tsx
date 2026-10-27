import { listOrders, latestPaymentForOrder } from '@/lib/repo';
import { cloudListOrders, isCloudEnabled } from '@/lib/cloud-store';
import { OrdersManager } from '@/components/admin/orders-manager';

export const dynamic = 'force-dynamic';

export default async function AdminOrdersPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = await searchParams;
  const orders = isCloudEnabled()
    ? await cloudListOrders(300).catch(() => listOrders({ limit: 300 }))
    : listOrders({ limit: 300 });
  const claims = Object.fromEntries(orders.map((o) => [o.id, latestPaymentForOrder(o.id)?.status === 'sent']));
  return <OrdersManager orders={orders} initialQuery={q ?? ''} claims={claims} />;
}
