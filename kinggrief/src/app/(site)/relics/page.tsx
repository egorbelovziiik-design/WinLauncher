import type { Metadata } from 'next';
import Link from 'next/link';
import { ArrowRight, Coins, Gavel, Package, TrendingUp, Zap } from 'lucide-react';
import { getSettings, listProducts } from '@/lib/repo';
import { parseRelicPricing } from '@/lib/relic-pricing';
import { formatNumber, formatPrice } from '@/lib/utils';
import { RelicCard } from '@/components/product-card';
import { RelicCalculator } from '@/components/relic-calculator';
import { Coin, Particles } from '@/components/voxel';
import { Reveal, SectionHeading } from '@/components/ui';

export const metadata: Metadata = {
  title: 'Рилики — игровая валюта от 19 ₽',
  description:
    'Купить рилики KingGrief: 1 000 за 19 ₽, 10 000 за 119 ₽, 100 000 за 799 ₽. Калькулятор риликов и мгновенное зачисление после оплаты.',
  alternates: { canonical: '/relics' },
};

const USE_CASES = [
  { icon: Gavel, title: 'Аукцион', text: 'Эксклюзивные предметы, зачарования и лоты от других игроков — только за рилики.', color: '#FACC15' },
  { icon: TrendingUp, title: 'Прокачка', text: 'Ускорение профессий, покупка уровней и снятие лимитов на крафт.', color: '#A855F7' },
  { icon: Package, title: 'Ресурсы', text: 'Магазин ресурсов и редких блоков в игровом хабе. Цены фиксированы.', color: '#22C55E' },
];

export default function RelicsPage() {
  const settings = getSettings();
  const pricing = parseRelicPricing(settings.relic_pricing);
  const products = listProducts({ kind: 'relic', activeOnly: true }).sort(
    (a, b) => Number(a.meta.amount) - Number(b.meta.amount),
  );
  const cheapest = products[0];
  const biggest = products[products.length - 1];

  return (
    <>
      <section className="relative overflow-hidden border-b border-neon/10">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-50" />
        <Particles density={30} className="absolute inset-0 h-full w-full opacity-70" />
        <div className="pointer-events-none absolute -right-24 top-6 h-80 w-80 rounded-full bg-gold/12 blur-[110px]" />
        <div className="container-x relative py-10 md:py-14">
          <nav aria-label="Хлебные крошки" className="mb-6 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-gold">Рилики</span>
          </nav>
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <span className="chip mb-4 !border-gold/30 !bg-gold/10 !text-gold"><Coins size={12} /> игровая валюта</span>
              <h1 className="display text-[2.6rem] leading-[0.9] text-white sm:text-5xl lg:text-6xl">
                Рилики <span className="gold-text">KingGrief</span>
              </h1>
              <p className="mt-4 text-[0.9rem] leading-relaxed text-white/45">
                9 пакетов с растущей выгодой. Чем больше пакет — тем дешевле 1 000 риликов. Зачисление на баланс никнейма
                сразу после подтверждения оплаты.
              </p>
              <div className="mt-6 flex flex-wrap items-center gap-4 text-[0.74rem] text-white/40">
                <span className="inline-flex items-center gap-1.5"><Coins size={13} className="text-gold" /> от {formatPrice(cheapest?.price ?? 19)}</span>
                <span className="inline-flex items-center gap-1.5"><Zap size={13} className="text-emerald" /> мгновенно</span>
                <span className="inline-flex items-center gap-1.5"><TrendingUp size={13} className="text-neon" /> до {formatNumber(Number(biggest?.meta.amount ?? 100000))} за раз</span>
              </div>
            </div>
            <div className="hidden items-center gap-2 lg:flex">
              {[0, 1, 2].map((i) => (
                <div key={i} style={{ transform: `translateY(${i === 1 ? -14 : 0}px)` }}>
                  <Coin size={74 - i * 12} />
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="container-x py-10 md:py-14" id="calculator">
        <Reveal>
          <RelicCalculator pricing={pricing} products={products} />
        </Reveal>
      </section>

      <section className="container-x pb-14" id="packs">
        <SectionHeading
          eyebrow="все пакеты"
          title={<>Выбери <span className="gold-text">выгоду</span></>}
          description="Все цены фиксированы. Пакет 100 000 риликов — максимальная выгода: дешевле только по акции."
        />
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product, i) => (
            <Reveal key={product.id} delay={Math.min(i * 0.04, 0.3)} className="h-full">
              <RelicCard product={product} featured={Number(product.meta.amount) === 100000} />
            </Reveal>
          ))}
        </div>
      </section>

      <section className="border-y border-white/6 bg-abyss/40 py-12">
        <div className="container-x">
          <SectionHeading eyebrow="зачем" title="Куда тратить рилики" />
          <div className="grid gap-4 sm:grid-cols-3">
            {USE_CASES.map((item, i) => (
              <Reveal key={item.title} delay={i * 0.05}>
                <div className="h-full rounded-2xl border border-white/6 bg-white/[0.02] p-5">
                  <span className="grid h-10 w-10 place-items-center rounded-lg" style={{ background: `${item.color}18`, color: item.color, boxShadow: `inset 0 0 0 1px ${item.color}30` }}>
                    <item.icon size={18} />
                  </span>
                  <p className="mt-3 font-display text-sm font-bold text-white">{item.title}</p>
                  <p className="mt-1 text-[0.76rem] leading-relaxed text-white/42">{item.text}</p>
                </div>
              </Reveal>
            ))}
          </div>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/6 bg-void/40 px-4 py-3">
            <p className="text-[0.72rem] text-white/35">
              Рилики не выводятся в реальные деньги и не подлежат возврату после зачисления.
            </p>
            <Link href="/support" className="btn btn-ghost btn-sm">Вопросы по риликам <ArrowRight size={13} /></Link>
          </div>
        </div>
      </section>
    </>
  );
}
