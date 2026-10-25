import type { Metadata } from 'next';
import Link from 'next/link';
import { Crown, ShieldCheck, Sparkles } from 'lucide-react';
import { listProducts } from '@/lib/repo';
import { PrivilegesExplorer } from '@/components/privileges-explorer';
import { Reveal } from '@/components/ui';
import { formatPrice } from '@/lib/utils';
import { CrownLarge } from '@/components/voxel';

export const metadata: Metadata = {
  title: 'Привилегии — 14 уровней от PEGAS до D.OWNER',
  description:
    'Все привилегии KingGrief: PEGAS 19 ₽, COBRA 39 ₽, BUNNY 59 ₽, RABBIT 89 ₽, VAMPIRE, GOD, KING, D.OWNER и другие. Сравнение возможностей и покупка в один клик.',
  alternates: { canonical: '/privileges' },
};

export default function PrivilegesPage() {
  const ranks = listProducts({ kind: 'rank', activeOnly: true });
  const minPrice = Math.min(...ranks.map((r) => r.price));
  const maxDiscount = Math.max(
    ...ranks.map((r) => (r.oldPrice ? Math.round(((r.oldPrice - r.price) / r.oldPrice) * 100) : 0)),
  );

  return (
    <>
      <section className="relative overflow-hidden border-b border-neon/10">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-50" />
        <div className="pointer-events-none absolute -right-20 top-0 h-72 w-72 rounded-full bg-violet/20 blur-[100px]" />
        <div className="container-x relative py-10 md:py-14">
          <nav aria-label="Хлебные крошки" className="mb-6 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link>
            <span>/</span>
            <span className="text-neon">Привилегии</span>
          </nav>
          <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <span className="chip mb-4 !bg-gold/10 !border-gold/30 !text-gold">
                <Crown size={12} /> Королевский набор
              </span>
              <h1 className="display text-[2.6rem] leading-[0.9] text-white sm:text-5xl lg:text-6xl">
                Привилегии <span className="text-neon">KingGrief</span>
              </h1>
              <p className="mt-4 max-w-xl text-[0.9rem] leading-relaxed text-white/45">
                14 уровней — от скромного PEGAS до D.OWNER с 3D-короной и радужным префиксом. Каждая привилегия
                наследует возможности предыдущей, а состав настраивается администрацией сервера.
              </p>
              <div className="mt-6 flex flex-wrap items-center gap-4 text-[0.74rem] text-white/40">
                <span className="inline-flex items-center gap-1.5"><Sparkles size={13} className="text-neon" /> от {formatPrice(minPrice)}</span>
                <span className="inline-flex items-center gap-1.5"><ShieldCheck size={13} className="text-emerald" /> скидки до {maxDiscount}%</span>
                <span className="inline-flex items-center gap-1.5"><Crown size={13} className="text-gold" /> 14 уровней</span>
              </div>
            </div>
            <div className="relative hidden shrink-0 lg:block">
              <div className="absolute inset-0 blur-3xl" style={{ background: 'radial-gradient(circle, rgba(250,204,21,0.28), transparent 65%)' }} />
              <CrownLarge size={220} />
            </div>
          </div>
        </div>
      </section>

      <section className="container-x py-10 md:py-14">
        <Reveal>
          <PrivilegesExplorer ranks={ranks} />
        </Reveal>
      </section>
    </>
  );
}
