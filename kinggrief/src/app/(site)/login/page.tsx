import type { Metadata } from 'next';
import { Suspense } from 'react';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { currentUser } from '@/lib/auth';
import { UserLoginForm } from '@/components/auth-forms';

export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: 'Вход в аккаунт',
  description: 'Войдите в аккаунт KingGrief, чтобы видеть свои заказы и оформлять покупки в один клик.',
  robots: { index: false, follow: true },
};

export default async function LoginPage() {
  const user = await currentUser();
  if (user) redirect('/account');

  return (
    <section className="container-x py-10 md:py-16">
      <div className="mx-auto grid w-full max-w-4xl overflow-hidden rounded-3xl border border-neon/15 bg-[#0c0b16]/80 md:grid-cols-[1fr_0.9fr]">
        <div className="hidden flex-col justify-between border-r border-white/6 bg-gradient-to-br from-[#1a1433] to-[#0a0912] p-8 md:flex">
          <p className="eyebrow">Аккаунт игрока</p>
          <div>
            <h1 className="display text-3xl leading-tight text-white">
              Твой профиль <span className="gold-text">в империи</span>
            </h1>
            <ul className="mt-5 space-y-2.5 text-[0.8rem] text-white/45">
              {[
                'История заказов и статусы выдачи',
                'Никнейм и email подставляются в оформление',
                'Смена пароля и почты в одном месте',
              ].map((item) => (
                <li key={item} className="flex items-start gap-2">
                  <ShieldCheck size={14} className="mt-0.5 shrink-0 text-emerald" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
          <Link href="/support" className="inline-flex items-center gap-2 text-[0.72rem] font-semibold text-neon hover:underline">
            Забыли пароль? Напишите в поддержку <ArrowRight size={13} />
          </Link>
        </div>

        <div className="p-6 sm:p-8">
          <h2 className="font-display text-xl font-extrabold uppercase tracking-tight text-white">Вход</h2>
          <p className="mb-5 mt-1 text-[0.78rem] text-white/40">Введите никнейм или email и пароль.</p>
          <Suspense fallback={<div className="skeleton h-64 rounded-xl" />}>
            <UserLoginForm />
          </Suspense>
        </div>
      </div>
    </section>
  );
}
