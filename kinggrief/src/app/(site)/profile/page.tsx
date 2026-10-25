import type { Metadata } from 'next';
import Link from 'next/link';
import { ProfileClient } from '@/components/profile-client';

export const metadata: Metadata = {
  title: 'Мои заказы',
  description: 'История заказов KingGrief и поиск заказа по номеру.',
  robots: { index: false, follow: false },
};

export default function ProfilePage() {
  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Мои заказы</span>
      </nav>
      <div className="mb-7">
        <h1 className="display text-3xl text-white sm:text-4xl">Мои заказы</h1>
        <p className="mt-1 max-w-xl text-[0.82rem] leading-relaxed text-white/40">
          Здесь хранятся заказы, оформленные в этом браузере. Номер заказа можно найти и вручную — вход не требуется.
        </p>
      </div>
      <ProfileClient />
    </section>
  );
}
