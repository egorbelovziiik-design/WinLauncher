import type { Metadata } from 'next';
import Link from 'next/link';
import { getSettings } from '@/lib/repo';
import { activeProvider, isTestMode } from '@/lib/payments';
import { CheckoutForm } from '@/components/checkout-form';
import { currentUser } from '@/lib/auth';

export const metadata: Metadata = {
  title: 'Оформление заказа',
  description: 'Безопасное оформление заказа KingGrief: серверный пересчёт цены, проверка платежа и автоматическая выдача.',
  robots: { index: false, follow: false },
};

export const dynamic = 'force-dynamic';

export default async function CheckoutPage() {
  const provider = activeProvider();
  const testMode = isTestMode();
  const settings = getSettings();
  const user = await currentUser();

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span>
        <Link href="/cart" className="hover:text-white">Корзина</Link><span>/</span>
        <span className="text-neon">Оформление</span>
      </nav>
      <div className="mb-7">
        <h1 className="display text-3xl text-white sm:text-4xl">Оформление заказа</h1>
        <p className="mt-1 max-w-xl text-[0.82rem] leading-relaxed text-white/40">
          Укажите никнейм получателя и подтвердите заказ. Покупка зачислится на сервер {settings.server_ip} после
          подтверждения оплаты.
        </p>
      </div>
      <CheckoutForm
        provider={provider}
        testMode={testMode}
        user={user ? { username: user.username, email: user.email } : null}
      />
    </section>
  );
}
