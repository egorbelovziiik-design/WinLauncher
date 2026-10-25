import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft, ArrowRight, Check, Crown, Info, ShieldCheck, Sparkles } from 'lucide-react';
import { listProducts } from '@/lib/repo';
import { formatPrice } from '@/lib/utils';
import { AddToCartButton, RankCard } from '@/components/product-card';
import { Icon } from '@/components/icons';
import { CrownLarge, Particles } from '@/components/voxel';
import { Reveal } from '@/components/ui';

type Params = { params: Promise<{ id: string }> };

function getRank(slug: string) {
  return listProducts({ kind: 'rank', activeOnly: true }).find((r) => r.meta.rankId === slug || r.id === slug) ?? null;
}

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { id } = await params;
  const rank = getRank(id);
  if (!rank) return { title: 'Привилегия не найдена' };
  return {
    title: `${rank.name} — ${formatPrice(rank.price)}`,
    description: `${rank.name}: ${rank.description}`,
    alternates: { canonical: `/privileges/${rank.meta.rankId}` },
    openGraph: { title: `${rank.name} · KINGGRIEF`, description: rank.description },
  };
}

export default async function RankPage({ params }: Params) {
  const { id } = await params;
  const rank = getRank(id);
  if (!rank) notFound();

  const ranks = listProducts({ kind: 'rank', activeOnly: true });
  const tier = Number(rank.meta.tier ?? 0);
  const others = ranks.filter((r) => r.id !== rank.id && Math.abs(Number(r.meta.tier) - tier) <= 2).slice(0, 3);
  const accent = rank.color;
  const rainbow = Boolean(rank.meta.rainbow);
  const inherited = rank.meta.inherits ? String(rank.meta.inherits) : null;

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: `Привилегия ${rank.name}`,
    description: rank.description,
    brand: { '@type': 'Brand', name: 'KINGGRIEF' },
    offers: {
      '@type': 'Offer',
      priceCurrency: 'RUB',
      price: rank.price,
      availability: 'https://schema.org/InStock',
    },
  };

  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />

      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />
        <div className="pointer-events-none absolute -right-32 -top-16 h-[24rem] w-[24rem] rounded-full blur-[110px]" style={{ background: accent, opacity: 0.18 }} />
        <Particles density={22} className="absolute inset-0 h-full w-full opacity-60" />

        <div className="container-x relative py-8 md:py-12">
          <nav aria-label="Хлебные крошки" className="mb-6 flex flex-wrap items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
            <Link href="/" className="hover:text-white">Главная</Link><span>/</span>
            <Link href="/privileges" className="hover:text-white">Привилегии</Link><span>/</span>
            <span style={{ color: accent }}>{rank.name}</span>
          </nav>

          <div className="grid gap-8 lg:grid-cols-[1.15fr_0.85fr]">
            <div>
              <Reveal>
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className="inline-flex items-center gap-1.5 rounded px-2 py-1 font-mono text-[0.58rem] font-bold uppercase tracking-widest"
                    style={{ background: `${accent}18`, color: accent, boxShadow: `inset 0 0 0 1px ${accent}40` }}
                  >
                    <Crown size={11} /> tier {String(tier).padStart(2, '0')}
                  </span>
                  {rank.badge && (
                    <span className="rounded bg-gold/15 px-2 py-1 font-mono text-[0.58rem] font-bold uppercase tracking-widest text-gold">
                      {rank.badge}
                    </span>
                  )}
                  <span className="rounded bg-emerald/12 px-2 py-1 font-mono text-[0.58rem] font-bold uppercase tracking-widest text-emerald">
                    в наличии
                  </span>
                </div>
                <h1
                  className={`mt-4 display text-[3rem] leading-[0.86] sm:text-6xl lg:text-7xl ${rainbow ? 'gold-text' : ''}`}
                  style={rainbow ? undefined : { color: accent, textShadow: `0 0 60px ${accent}55` }}
                >
                  {rank.name}
                </h1>
                <p className="mt-3 font-display text-sm font-bold uppercase tracking-[0.16em] text-white/60">{rank.subtitle}</p>
                <p className="mt-4 max-w-xl text-[0.9rem] leading-relaxed text-white/45">{rank.description}</p>
              </Reveal>

              <Reveal delay={0.08}>
                <div className="mt-8 rounded-2xl border border-white/8 bg-panel/40 p-5">
                  <div className="mb-4 flex items-center justify-between">
                    <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Что входит</h2>
                    <span className="font-mono text-[0.58rem] text-white/30">{rank.features.length} возможностей</span>
                  </div>
                  <ul className="grid gap-2 sm:grid-cols-2">
                    {rank.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2 rounded-lg bg-white/[0.02] px-3 py-2 text-[0.78rem] text-white/65">
                        <span className="mt-0.5 grid h-4 w-4 shrink-0 place-items-center rounded-full" style={{ background: `${accent}22`, color: accent }}>
                          <Check size={10} strokeWidth={3.5} />
                        </span>
                        {feature}
                      </li>
                    ))}
                  </ul>
                  {inherited && (
                    <p className="mt-4 flex items-start gap-2 rounded-lg border border-white/6 bg-void/50 px-3 py-2 text-[0.72rem] leading-relaxed text-white/40">
                      <Sparkles size={13} className="mt-0.5 shrink-0 text-neon" />
                      Привилегия наследует все возможности уровня {inherited}.
                    </p>
                  )}
                  <p className="mt-3 flex items-start gap-2 text-[0.7rem] leading-relaxed text-white/30">
                    <Info size={13} className="mt-0.5 shrink-0" />
                    Точный состав прав устанавливает администрация сервера и может менять его. Актуальный список — на этой
                    странице и в таблице привилегий.
                  </p>
                </div>
              </Reveal>
            </div>

            <Reveal delay={0.12}>
              <div
                className={`sticky top-24 overflow-hidden rounded-2xl border bg-gradient-to-b from-[#1a1730] to-[#0c0b16] p-6 ${rainbow ? 'rainbow-border border-transparent' : 'border-white/8'}`}
              >
                <div className="absolute -right-10 -top-12 h-40 w-40 rounded-full opacity-30 blur-3xl" style={{ background: accent }} />
                <div className="relative flex items-center justify-center py-2">
                  <span className="absolute h-32 w-32 rounded-full blur-2xl" style={{ background: `${accent}40` }} />
                  {rainbow ? <CrownLarge size={168} rainbow /> : <Icon name={rank.icon} size={88} className="relative" />}
                </div>

                <div className="relative mt-4">
                  <p className="font-mono text-[0.55rem] uppercase tracking-[0.2em] text-white/30">Стоимость</p>
                  <div className="mt-1 flex items-end gap-2">
                    <span className="stat-num text-3xl font-extrabold" style={{ color: rainbow ? '#facc15' : accent }}>
                      {formatPrice(rank.price)}
                    </span>
                    {rank.oldPrice && (
                      <span className="stat-num mb-1 text-sm text-white/30 line-through">{formatPrice(rank.oldPrice)}</span>
                    )}
                  </div>
                  {rank.oldPrice && (
                    <p className="mt-1 font-mono text-[0.6rem] uppercase tracking-widest text-emerald">
                      выгода {formatPrice(rank.oldPrice - rank.price)} · {Math.round(((rank.oldPrice - rank.price) / rank.oldPrice) * 100)}%
                    </p>
                  )}

                  <div className="mt-5 space-y-2">
                    <AddToCartButton product={rank} className="w-full" label="Добавить в корзину" variant={rainbow ? 'gold' : 'primary'} />
                    <Link href="/checkout" className="btn btn-ghost w-full">Перейти к оформлению</Link>
                  </div>

                  <ul className="mt-5 space-y-1.5 border-t border-white/6 pt-4 text-[0.7rem] text-white/40">
                    <li className="flex items-center gap-2"><ShieldCheck size={12} className="text-emerald" /> Выдача после подтверждения оплаты</li>
                    <li className="flex items-center gap-2"><Check size={12} className="text-emerald" /> Никнейм получателя указывается при оформлении</li>
                    <li className="flex items-center gap-2"><Check size={12} className="text-emerald" /> Поддержка в Discord 24/7</li>
                  </ul>
                </div>
              </div>
            </Reveal>
          </div>
        </div>
      </section>

      <section className="container-x py-12">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="font-display text-lg font-bold uppercase tracking-tight text-white">Соседние уровни</h2>
          <Link href="/privileges" className="inline-flex items-center gap-1.5 text-xs font-semibold text-white/45 hover:text-white">
            <ArrowLeft size={13} /> Все привилегии
          </Link>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {others.map((other) => (
            <RankCard key={other.id} product={other} compact />
          ))}
        </div>
        <div className="mt-8 flex justify-end">
          <Link href="/privileges" className="btn btn-primary">
            Сравнить все привилегии <ArrowRight size={15} />
          </Link>
        </div>
      </section>
    </>
  );
}
