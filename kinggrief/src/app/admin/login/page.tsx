import type { Metadata } from 'next';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import { ArrowLeft, Crown, Lock, ShieldCheck } from 'lucide-react';
import { currentAdmin } from '@/lib/auth';
import { LoginForm } from '@/components/admin/login-form';
import { CrownLarge, Particles } from '@/components/voxel';
import { LogoMark } from '@/components/icons';

export const dynamic = 'force-dynamic';
export const metadata: Metadata = { title: 'Вход в админ-панель', robots: { index: false, follow: false } };

export default async function AdminLoginPage() {
  const admin = await currentAdmin();
  if (admin) redirect('/admin');

  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden px-4 py-10">
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />
      <div className="pointer-events-none absolute -left-32 top-0 h-96 w-96 rounded-full bg-violet/20 blur-[120px]" />
      <div className="pointer-events-none absolute -right-20 bottom-0 h-80 w-80 rounded-full bg-pink/10 blur-[110px]" />
      <Particles density={26} className="absolute inset-0 h-full w-full opacity-60" />

      <div className="relative grid w-full max-w-4xl overflow-hidden rounded-3xl border border-neon/20 bg-[#0c0b16]/90 backdrop-blur-xl md:grid-cols-[1fr_0.95fr]">
        <div className="relative hidden flex-col justify-between overflow-hidden border-r border-white/6 bg-gradient-to-br from-[#1b1533] to-[#0a0912] p-8 md:flex">
          <Link href="/" className="inline-flex items-center gap-2.5">
            <LogoMark size={38} />
            <span className="font-display text-sm font-extrabold uppercase tracking-tight text-white">KINGGRIEF</span>
          </Link>
          <div className="relative my-6 flex justify-center">
            <div className="absolute inset-0 blur-3xl" style={{ background: 'radial-gradient(circle, rgba(250,204,21,0.25), transparent 65%)' }} />
            <CrownLarge size={210} />
          </div>
          <div>
            <p className="font-display text-lg font-extrabold uppercase leading-tight text-white">
              Управляй <span className="gold-text">империей</span>
            </p>
            <p className="mt-2 text-[0.76rem] leading-relaxed text-white/40">
              Товары, цены, кейсы, промокоды, выдача и аналитика — в одной панели. Доступ защищён паролем и 2FA.
            </p>
            <div className="mt-4 flex items-center gap-1.5 font-mono text-[0.56rem] uppercase tracking-widest text-emerald/80">
              <ShieldCheck size={11} /> журнал действий ведётся
            </div>
          </div>
        </div>

        <div className="p-6 sm:p-8">
          <div className="mb-6">
            <span className="mb-3 inline-flex items-center gap-1.5 rounded-full border border-neon/25 bg-neon/10 px-2.5 py-1 font-mono text-[0.56rem] uppercase tracking-widest text-neon">
              <Lock size={11} /> protected area
            </span>
            <h1 className="font-display text-2xl font-extrabold uppercase tracking-tight text-white">Вход в панель</h1>
            <p className="mt-1 text-[0.76rem] text-white/35">Введите данные администратора KingGrief.</p>
          </div>
          <LoginForm />
          <div className="mt-5 flex items-center justify-between border-t border-white/6 pt-4">
            <Link href="/" className="inline-flex items-center gap-1.5 text-[0.68rem] font-semibold text-white/35 hover:text-white">
              <ArrowLeft size={12} /> На сайт
            </Link>
            <span className="inline-flex items-center gap-1 font-mono text-[0.56rem] uppercase tracking-widest text-white/20">
              <Crown size={10} /> kinggrief
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
