import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft, Info, Sparkles } from 'lucide-react';
import { caseById } from '@/lib/cases';
import { formatPrice } from '@/lib/utils';
import { RARITY_META } from '@/lib/types';
import { CaseProductCard } from '@/components/product-card';
import { CaseOpeningDemo } from '@/components/case-demo';
import { Chest3D, Particles } from '@/components/voxel';
import { Reveal } from '@/components/ui';
import { DropTable } from '@/components/drop-table';

type Params = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { id } = await params;
  const data = caseById(id);
  if (!data) return { title: 'Кейс не найден' };
  return {
    title: `${data.group.name} — награды и шансы`,
    description: `${data.group.description} Ключи от ${formatPrice(data.minPrice)}. Все вероятности открыты.`,
    alternates: { canonical: `/cases/${data.group.id}` },
  };
}

export default async function CasePage({ params }: Params) {
  const { id } = await params;
  const data = caseById(id);
  if (!data) notFound();
  const { group, items, packages, minPrice, chanceTotal } = data;

  const allCases = (await import('@/lib/cases')).mergePackagesForCases().filter((c) => c.group.id !== group.id);

  return (
    <>
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />
        <div className="pointer-events-none absolute -left-20 top-0 h-80 w-80 rounded-full blur-[110px]" style={{ background: group.color, opacity: 0.18 }} />
        <Particles density={24} className="absolute inset-0 h-full w-full opacity-60" />
        <div className="container-x relative py-8 md:py-12">
          <nav aria-label="Хлебные крошки" className="mb-6 flex flex-wrap items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link><span>/</span>
            <Link href="/cases" className="hover:text-white">Кейсы</Link><span>/</span>
            <span style={{ color: group.color }}>{group.name}</span>
          </nav>

          <div className="grid gap-8 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
            <Reveal>
              <div className="relative flex justify-center rounded-2xl border border-white/8 bg-gradient-to-b from-[#171330] to-[#0a0912] px-6 py-10">
                <div className="pointer-events-none absolute inset-0 opacity-30 blur-3xl" style={{ background: `radial-gradient(circle at 50% 40%, ${group.color}, transparent 65%)` }} />
                <Chest3D color={group.color} size={280} />
              </div>
            </Reveal>
            <Reveal delay={0.05}>
              <span
                className="inline-flex items-center gap-1.5 rounded px-2 py-1 font-mono text-[0.58rem] font-bold uppercase tracking-widest"
                style={{ background: `${group.color}18`, color: group.color, boxShadow: `inset 0 0 0 1px ${group.color}40` }}
              >
                <Sparkles size={11} /> {items.length} наград · шансы из базы
              </span>
              <h1 className="mt-4 display text-[2.5rem] leading-[0.9] text-white sm:text-5xl" style={{ textShadow: `0 0 60px ${group.color}44` }}>
                {group.name}
              </h1>
              <p className="mt-4 max-w-lg text-[0.9rem] leading-relaxed text-white/45">{group.description}</p>
              <dl className="mt-6 grid max-w-md grid-cols-3 gap-2">
                {[
                  { label: 'Цена от', value: formatPrice(minPrice) },
                  { label: 'Наград', value: `${items.length}` },
                  { label: 'Сумма шансов', value: `${chanceTotal}%` },
                ].map((s) => (
                  <div key={s.label} className="rounded-lg border border-white/6 bg-white/[0.02] px-2.5 py-2">
                    <dt className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">{s.label}</dt>
                    <dd className="stat-num mt-0.5 text-sm font-bold text-white">{s.value}</dd>
                  </div>
                ))}
              </dl>
              <div className="mt-5 flex items-center gap-2">
                <Link href="#packages" className="btn btn-primary">Купить ключи</Link>
                <Link href="/cases" className="btn btn-ghost"><ArrowLeft size={14} /> Все кейсы</Link>
              </div>
            </Reveal>
          </div>
        </div>
      </section>

      <section className="container-x py-10" id="packages">
        <h2 className="mb-4 font-display text-lg font-bold uppercase tracking-tight text-white">Наборы ключей</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {packages.map((pkg) => (
            <CaseProductCard key={pkg.id} product={pkg} />
          ))}
        </div>
      </section>

      <section className="container-x grid gap-6 pb-12 lg:grid-cols-[1.05fr_0.95fr]">
        <Reveal>
          <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Содержимое кейса</h2>
              <span className="font-mono text-[0.56rem] uppercase tracking-widest text-emerald">итого {chanceTotal}%</span>
            </div>
            <DropTable items={items} />
            <div className="mt-4 flex flex-wrap gap-2">
              {(Object.keys(RARITY_META) as Array<keyof typeof RARITY_META>).map((rarity) => (
                <span
                  key={rarity}
                  className="inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 font-mono text-[0.52rem] uppercase tracking-wide"
                  style={{ background: `${RARITY_META[rarity].color}14`, color: RARITY_META[rarity].color }}
                >
                  <span className="h-1.5 w-1.5 rounded-full" style={{ background: RARITY_META[rarity].color }} />
                  {RARITY_META[rarity].label}
                </span>
              ))}
            </div>
          </div>
        </Reveal>

        <div className="space-y-4">
          <Reveal delay={0.05}>
            <CaseOpeningDemo items={items} caseName={group.name} />
          </Reveal>
          <Reveal delay={0.1}>
            <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
              <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Как это работает</h2>
              <ol className="mt-3 space-y-2 text-[0.76rem] leading-relaxed text-white/45">
                <li className="flex gap-2"><span className="font-mono text-neon">01</span> Покупаете ключи — они приходят на никнейм после подтверждения оплаты.</li>
                <li className="flex gap-2"><span className="font-mono text-neon">02</span> Открываете раздел кейсов в игре командой <code className="rounded bg-void px-1 text-[0.7rem] text-white/70">/crates</code>.</li>
                <li className="flex gap-2"><span className="font-mono text-neon">03</span> Награда выдаётся сразу и сохраняется в истории открытий.</li>
              </ol>
              <p className="mt-3 flex items-start gap-1.5 rounded-lg border border-white/6 bg-void/40 px-2.5 py-2 text-[0.66rem] leading-relaxed text-white/30">
                <Info size={12} className="mt-0.5 shrink-0" />
                Шансы из этого списка — реальные значения из базы. Администрация не может изменить шанс конкретного
                открытия: система выбирает награду только по весам.
              </p>
            </div>
          </Reveal>
        </div>
      </section>

      <section className="container-x pb-16">
        <h2 className="mb-4 font-display text-lg font-bold uppercase tracking-tight text-white">Другие кейсы</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {allCases.map((c) => (
            <Link
              key={c.group.id}
              href={`/cases/${c.group.id}`}
              className="group flex items-center gap-4 rounded-xl border border-white/7 bg-panel/40 p-4 transition hover:border-white/15 hover:bg-white/[0.04]"
            >
              <Chest3D color={c.group.color} size={84} />
              <div>
                <p className="font-display text-base font-extrabold uppercase text-white">{c.group.name}</p>
                <p className="mt-0.5 text-[0.72rem] text-white/35">от {formatPrice(c.minPrice)} · {c.items.length} наград</p>
              </div>
              <span className="ml-auto font-mono text-[0.6rem] text-white/25 transition group-hover:text-neon">→</span>
            </Link>
          ))}
        </div>
      </section>
    </>
  );
}
