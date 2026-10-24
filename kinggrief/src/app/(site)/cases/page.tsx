import type { Metadata } from 'next';
import Link from 'next/link';
import { ArrowRight, Gift, KeyRound, PackageOpen, Sparkles } from 'lucide-react';
import { mergePackagesForCases } from '@/lib/cases';
import { formatPrice } from '@/lib/utils';
import { CaseProductCard } from '@/components/product-card';
import { Chest3D, Particles } from '@/components/voxel';
import { Reveal, SectionHeading } from '@/components/ui';
import { RARITY_META } from '@/lib/types';
import { DropTable } from '@/components/drop-table';

export const metadata: Metadata = {
  title: 'Кейсы — Супер, Легендарный и Мега',
  description:
    'Кейсы KingGrief с открытыми вероятностями: Супер от 29 ₽, Легендарный от 49 ₽, Мега от 79 ₽. Награды, шансы и демонстрация открытия.',
  alternates: { canonical: '/cases' },
};

const STEPS = [
  { icon: Gift, title: 'Выбери кейс', text: 'Купи один ключ или набор со скидкой. Чем больше ключей, тем ниже цена за штуку.' },
  { icon: KeyRound, title: 'Получи ключ', text: 'После оплаты ключи автоматически попадают в игровой инвентарь через /crates.' },
  { icon: PackageOpen, title: 'Открой в игре', text: 'Используй ключ на арене кейсов. Все награды видны заранее, шансы не меняются.' },
];

export default function CasesPage() {
  const cases = mergePackagesForCases();

  return (
    <>
      <section className="relative overflow-hidden border-b border-neon/10">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-50" />
        <Particles density={26} className="absolute inset-0 h-full w-full opacity-70" />
        <div className="container-x relative py-10 md:py-14">
          <nav aria-label="Хлебные крошки" className="mb-6 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Кейсы</span>
          </nav>
          <div className="max-w-2xl">
            <span className="chip mb-4 !border-gold/30 !bg-gold/10 !text-gold"><Gift size={12} /> 3 кейса · 9 наборов</span>
            <h1 className="display text-[2.6rem] leading-[0.9] text-white sm:text-5xl lg:text-6xl">
              Кейсы <span className="gold-text">KingGrief</span>
            </h1>
            <p className="mt-4 text-[0.9rem] leading-relaxed text-white/45">
              Сундуки с привилегиями, риликами и ключами. Вероятности хранятся в базе, не меняются во время акций и
              показаны на странице каждого кейса.
            </p>
          </div>
          <div className="mt-6 grid gap-3 sm:grid-cols-3">
            {STEPS.map((step, i) => (
              <Reveal key={step.title} delay={i * 0.05}>
                <div className="flex h-full gap-3 rounded-xl border border-white/6 bg-panel/40 p-3.5">
                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-neon/12 text-neon">
                    <step.icon size={17} />
                  </span>
                  <div>
                    <p className="text-[0.78rem] font-bold text-white/85">{step.title}</p>
                    <p className="mt-0.5 text-[0.68rem] leading-relaxed text-white/40">{step.text}</p>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {cases.map((item, index) => (
        <section
          key={item.group.id}
          id={item.group.id}
          className={`relative scroll-mt-24 border-b border-white/5 py-12 md:py-16 ${index % 2 === 1 ? 'bg-abyss/40' : ''}`}
        >
          <div className="container-x">
            <div className="grid gap-8 lg:grid-cols-[0.42fr_0.58fr] lg:items-start">
              <Reveal>
                <div className="relative flex flex-col items-center overflow-hidden rounded-2xl border border-white/8 bg-gradient-to-b from-[#171330] to-[#0a0912] px-6 py-8">
                  <div className="pointer-events-none absolute inset-x-0 top-0 h-40 opacity-30 blur-3xl" style={{ background: item.group.color }} />
                  <div className="relative">
                    <Chest3D color={item.group.color} size={230} />
                  </div>
                  <h2 className="relative mt-3 font-display text-2xl font-extrabold uppercase tracking-tight text-white">
                    {item.group.name}
                  </h2>
                  <p className="relative mt-2 max-w-xs text-center text-[0.78rem] leading-relaxed text-white/45">{item.group.description}</p>
                  <div className="relative mt-4 flex items-center gap-2 font-mono text-[0.58rem] uppercase tracking-widest text-white/30">
                    <span style={{ color: item.group.color }}>от {formatPrice(item.minPrice)}</span>
                    <span>·</span>
                    <span>{item.chanceTotal}% суммарно</span>
                  </div>
                  <Link href={`/cases/${item.group.id}`} className="btn btn-ghost btn-sm relative mt-5 w-full">
                    Все награды и шансы <ArrowRight size={13} />
                  </Link>
                </div>
              </Reveal>

              <div>
                <div className="mb-4 flex items-end justify-between">
                  <h3 className="font-display text-lg font-bold uppercase tracking-tight text-white/80">Наборы ключей</h3>
                  <span className="font-mono text-[0.58rem] uppercase tracking-widest text-white/25">цена за штуку ниже в наборе</span>
                </div>
                <div className="grid gap-3 sm:grid-cols-3">
                  {item.packages.map((pkg) => (
                    <CaseProductCard key={pkg.id} product={pkg} />
                  ))}
                </div>

                <div className="mt-5 rounded-xl border border-white/6 bg-panel/40 p-4">
                  <div className="mb-3 flex items-center justify-between">
                    <p className="font-display text-[0.8rem] font-bold uppercase tracking-widest text-white/60">Топ награды</p>
                    <span className="font-mono text-[0.55rem] text-white/25">{item.items.length} позиций · 100%</span>
                  </div>
                  <DropTable items={item.items} compact />
                </div>
              </div>
            </div>
          </div>
        </section>
      ))}

      <section className="container-x py-14">
        <div className="rounded-2xl border border-gold/20 bg-gradient-to-r from-[#1d1710] to-[#0c0b16] p-6">
          <SectionHeading
            eyebrow="важно знать"
            title="Честные кейсы — без подкруток"
            description="Сумма вероятностей каждого кейса равна 100%. Шансы не зависят от суммы покупок, времени суток или «удачи» аккаунта. Если администрация меняет состав кейса, новые шансы сразу отображаются здесь."
          />
          <div className="grid gap-3 sm:grid-cols-3">
            {(['common', 'epic', 'legendary', 'mythic'] as const).map((rarity) => (
              <div key={rarity} className="flex items-center gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2">
                <span className="h-2 w-2 rounded-full" style={{ background: RARITY_META[rarity].color }} />
                <span className="text-[0.72rem] font-semibold text-white/60">{RARITY_META[rarity].label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
