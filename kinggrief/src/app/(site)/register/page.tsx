import type { Metadata } from 'next';
import { Suspense } from 'react';
import { redirect } from 'next/navigation';
import { currentUser } from '@/lib/auth';
import { RegisterForm } from '@/components/auth-forms';

export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: 'Регистрация',
  description: 'Создайте аккаунт KingGrief: история покупок, быстрый чекаут и управление профилем.',
  robots: { index: false, follow: true },
};

export default async function RegisterPage() {
  const user = await currentUser();
  if (user) redirect('/account');

  return (
    <section className="container-x py-10 md:py-14">
      <div className="mx-auto max-w-xl">
        <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
          <span className="text-neon">Регистрация</span>
        </nav>
        <div className="rounded-3xl border border-neon/15 bg-[#0c0b16]/80 p-6 sm:p-8">
          <h1 className="display text-3xl text-white">
            Создать <span className="gold-text">аккаунт</span>
          </h1>
          <p className="mb-6 mt-2 text-[0.8rem] leading-relaxed text-white/40">
            Аккаунт нужен, чтобы хранить историю покупок и не вводить никнейм каждый раз. Покупки по-прежнему
            зачисляются на никнейм в игре.
          </p>
          <Suspense fallback={<div className="skeleton h-80 rounded-xl" />}>
            <RegisterForm />
          </Suspense>
        </div>
      </div>
    </section>
  );
}
