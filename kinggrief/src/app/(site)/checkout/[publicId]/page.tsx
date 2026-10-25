import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getOrder } from '@/lib/repo';
import { cloudGetOrder, isCloudEnabled } from '@/lib/cloud-store';
import { OrderStatusView } from '@/components/order-status';

export const dynamic = 'force-dynamic';

type Params = { params: Promise<{ publicId: string }> };

export const metadata: Metadata = {
  title: 'Статус заказа',
  robots: { index: false, follow: false },
};

export default async function OrderPage({ params }: Params) {
  const { publicId } = await params;
  const order = (isCloudEnabled() ? await cloudGetOrder(publicId).catch(() => null) : null) ?? getOrder(publicId);
  if (!order) notFound();

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span>
        <Link href="/profile" className="hover:text-white">Мои заказы</Link><span>/</span>
        <span className="text-neon">{order.publicId}</span>
      </nav>
      <div className="mb-6">
        <h1 className="display text-3xl text-white sm:text-4xl">Статус заказа</h1>
        <p className="mt-1 text-[0.82rem] text-white/40">
          Страница обновляется автоматически. Сохраните ссылку — она содержит номер заказа и открывается без входа.
        </p>
      </div>
      <OrderStatusView
        initialOrder={{
          ...order,
          items: order.items,
        }}
      />
    </section>
  );
}
