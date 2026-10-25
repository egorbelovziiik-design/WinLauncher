import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import { getOrder, listDeliveries, listPayments } from '@/lib/repo';
import { cloudGetOrder, cloudListDeliveries, isCloudEnabled } from '@/lib/cloud-store';
import { OrderDetail } from '@/components/admin/order-detail';

export const dynamic = 'force-dynamic';

export default async function AdminOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const order = (isCloudEnabled() ? await cloudGetOrder(id).catch(() => null) : null) ?? getOrder(id);
  if (!order) notFound();
  const deliveries = isCloudEnabled()
    ? await cloudListDeliveries({ orderId: order.id, limit: 100 }).catch(() => listDeliveries({ orderId: order.id, limit: 100 }))
    : listDeliveries({ orderId: order.id, limit: 100 });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Link href="/admin/orders" className="inline-flex items-center gap-1.5 font-mono text-[0.6rem] uppercase tracking-widest text-white/35 hover:text-white">
            <ArrowLeft size={11} /> все заказы
          </Link>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Заказ {order.publicId}</h1>
        </div>
      </div>
      <OrderDetail order={order} deliveries={deliveries} payments={listPayments(order.id)} />
    </div>
  );
}
