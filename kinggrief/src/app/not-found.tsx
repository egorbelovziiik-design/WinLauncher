import Link from 'next/link';
import { ArrowLeft, Search, Skull } from 'lucide-react';
import { LogoMark } from '@/components/icons';

export default function NotFound() {
  return (
    <div className="relative flex min-h-dvh flex-col items-center justify-center overflow-hidden px-4 text-center">
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-96 w-96 -translate-x-1/2 -translate-y-1/2 rounded-full bg-violet/15 blur-[120px]" />
      <div className="relative">
        <div className="mb-6 flex justify-center">
          <LogoMark size={56} />
        </div>
        <p className="font-mono text-[0.62rem] uppercase tracking-[0.3em] text-neon">error 404</p>
        <h1 className="mt-3 display text-4xl text-white sm:text-6xl">Территория <span className="gold-text">не найдена</span></h1>
        <p className="mx-auto mt-3 max-w-md text-[0.85rem] leading-relaxed text-white/40">
          Похоже, эта страница ушла в другой мир. Проверьте адрес или вернитесь в империю.
        </p>
        <div className="mt-7 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link href="/" className="btn btn-primary"><ArrowLeft size={15} /> На главную</Link>
          <Link href="/privileges" className="btn btn-ghost"><Search size={15} /> В магазин</Link>
        </div>
        <div className="mt-8 flex items-center justify-center gap-2 font-mono text-[0.58rem] uppercase tracking-widest text-white/20">
          <Skull size={12} /> kinggrief · 404
        </div>
      </div>
    </div>
  );
}
