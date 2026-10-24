import type { Metadata } from 'next';
import Link from 'next/link';
import { listProducts } from '@/lib/repo';
import { CartPageClient } from '@/components/cart-page';

export const metadata: Metadata = {
  title: 'Корзина',
  description: 'Ваша корзина KingGrief. Проверьте состав заказа и оформите покупку.',
  robots: { index: false, follow: false },
};

export default function CartPage() {
  const products = listProducts({ activeOnly: true });
  const suggestions = ['rank-king', 'case-super-5', 'relic-10000', 'other-unban']
    .map((id) => products.find((p) => p.id === id))
    .filter((p): p is NonNullable<typeof p> => Boolean(p));

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Корзина</span>
      </nav>
      <div className="mb-7 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="display text-3xl text-white sm:text-4xl">Корзина</h1>
          <p className="mt-1 text-[0.82rem] text-white/40">Корзина сохраняется в браузере и не пропадёт после обновления.</p>
        </div>
        <Link href="/support" className="text-[0.72rem] font-semibold text-white/40 hover:text-white">Нужна помощь с заказом?</Link>
      </div>
      <CartPageClient suggestions={suggestions} />
    </section>
  );
}
