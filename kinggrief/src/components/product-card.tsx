'use client';

import Link from 'next/link';
import { useState } from 'react';
import { motion } from 'framer-motion';
import { Check, Crown, Plus, ShoppingCart, Sparkles } from 'lucide-react';
import type { Product } from '@/lib/types';
import { cn, formatPrice } from '@/lib/utils';
import { useCart } from './providers';
import { Icon } from './icons';
import { Chest3D, Coin, CrownLarge } from './voxel';
import { DiscountBadge, PriceTag } from './ui';

export function contrastText(hex: string) {
  const clean = hex.replace('#', '');
  const r = parseInt(clean.slice(0, 2), 16);
  const g = parseInt(clean.slice(2, 4), 16);
  const b = parseInt(clean.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.62 ? '#140f02' : '#ffffff';
}

export function AddToCartButton({
  product,
  quantity = 1,
  className = '',
  label,
  showIcon = true,
  variant = 'primary',
  onAdded,
}: {
  product: Product;
  quantity?: number;
  className?: string;
  label?: string;
  showIcon?: boolean;
  variant?: 'primary' | 'gold' | 'ghost';
  onAdded?: () => void;
}) {
  const { add } = useCart();
  const [pulse, setPulse] = useState(false);

  return (
    <button
      type="button"
      onClick={() => {
        add(
          {
            productId: product.id,
            name: product.name,
            subtitle: product.subtitle,
            price: product.price,
            color: product.color,
            icon: product.icon,
            kind: product.kind,
            meta: product.meta,
          },
          product.kind === 'rank' ? 1 : quantity,
        );
        setPulse(true);
        onAdded?.();
        setTimeout(() => setPulse(false), 900);
      }}
      className={cn('btn', variant === 'gold' ? 'btn-gold' : variant === 'ghost' ? 'btn-ghost' : 'btn-primary', className)}
      aria-label={`Добавить ${product.name} в корзину`}
    >
      {pulse ? <Check size={16} strokeWidth={3} /> : showIcon ? <ShoppingCart size={15} /> : null}
      <span>{pulse ? 'В корзине' : (label ?? (product.kind === 'rank' ? 'Купить' : 'В корзину'))}</span>
    </button>
  );
}

export function RankCard({ product, compact = false }: { product: Product; compact?: boolean }) {
  const rainbow = Boolean(product.meta.rainbow);
  const isTop = Boolean(product.meta.top);
  const accent = product.color;
  const tier = Number(product.meta.tier ?? 0);

  return (
    <motion.article
      whileHover={{ y: -5 }}
      transition={{ type: 'spring', stiffness: 260, damping: 22 }}
      className={cn(
        'group relative flex h-full flex-col overflow-hidden rounded-2xl border bg-gradient-to-b from-[#191631] to-[#0d0c18] p-5 transition-shadow',
        rainbow ? 'rainbow-border border-transparent' : 'border-white/[0.06]',
      )}
      style={
        {
          '--accent': accent,
          boxShadow: `0 26px 70px -46px ${accent}, inset 0 1px 0 rgba(255,255,255,0.05)`,
        } as React.CSSProperties
      }
    >
      <div
        className="pointer-events-none absolute -right-12 -top-14 h-40 w-40 rounded-full opacity-30 blur-2xl transition-opacity duration-500 group-hover:opacity-60"
        style={{ background: accent }}
      />
      {isTop && (
        <div className="pointer-events-none absolute inset-0">
          {[...Array(7)].map((_, i) => (
            <span
              key={i}
              className="absolute h-1 w-1 rounded-full bg-gold"
              style={{
                left: `${12 + i * 12}%`,
                top: `${18 + ((i * 23) % 60)}%`,
                opacity: 0.5,
                boxShadow: '0 0 8px 2px rgba(250,204,21,0.65)',
                animation: `float ${5 + (i % 4)}s ease-in-out ${i * 0.3}s infinite`,
              }}
            />
          ))}
        </div>
      )}

      <div className="relative z-10 flex items-start justify-between gap-2">
        <span
          className="grid h-11 w-11 place-items-center rounded-xl"
          style={{
            background: `linear-gradient(145deg, ${accent}38, ${accent}0d)`,
            color: accent,
            boxShadow: `inset 0 0 0 1px ${accent}45`,
          }}
        >
          {rainbow ? (
            <CrownLarge size={38} rainbow />
          ) : isTop ? (
            <Crown size={22} />
          ) : (
            <Icon name={product.icon} size={22} />
          )}
        </span>
        <div className="flex flex-col items-end gap-1">
          <span className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-white/25">tier {String(tier).padStart(2, '0')}</span>
          {product.badge && (
            <span
              className="rounded px-1.5 py-0.5 font-mono text-[0.55rem] font-bold uppercase tracking-widest"
              style={{ background: `${accent}22`, color: accent, boxShadow: `inset 0 0 0 1px ${accent}44` }}
            >
              {product.badge}
            </span>
          )}
        </div>
      </div>

      <h3
        className={cn('relative z-10 mt-4 font-display text-[1.35rem] font-extrabold uppercase leading-none tracking-tight', rainbow && 'gold-text')}
        style={rainbow ? undefined : { color: accent }}
      >
        {product.name}
      </h3>
      <p className="relative z-10 mt-1 text-[0.72rem] font-medium text-white/35">{product.subtitle}</p>

      <div className="relative z-10 my-4 h-px bg-gradient-to-r from-white/10 to-transparent" />

      <ul className="relative z-10 mb-5 flex-1 space-y-1.5">
        {product.features.slice(0, compact ? 3 : 4).map((feature) => (
          <li key={feature} className="flex items-start gap-2 text-[0.74rem] leading-snug text-white/55">
            <Check size={12} className="mt-0.5 shrink-0" style={{ color: accent }} strokeWidth={3} />
            <span>{feature}</span>
          </li>
        ))}
        {product.features.length > (compact ? 3 : 4) && (
          <li className="pl-5 text-[0.68rem] font-semibold text-white/30">
            +{product.features.length - (compact ? 3 : 4)} возможностей
          </li>
        )}
      </ul>

      <div className="relative z-10 mt-auto">
        <div className="mb-3 flex items-end justify-between">
          <PriceTag price={product.price} oldPrice={product.oldPrice} color={accent} />
        </div>
        <div className="flex gap-2">
          <AddToCartButton product={product} className="flex-1 !h-10 !text-[0.78rem]" />
          <Link
            href={`/privileges/${String(product.meta.rankId ?? product.id.replace('rank-', ''))}`}
            className="btn btn-ghost !h-10 !px-3 !text-[0.72rem]"
          >
            Подробнее
          </Link>
        </div>
      </div>
    </motion.article>
  );
}

export function CaseProductCard({ product }: { product: Product }) {
  const accent = product.color;
  return (
    <motion.article
      whileHover={{ y: -4 }}
      className="group relative flex flex-col overflow-hidden rounded-2xl border border-white/[0.07] bg-gradient-to-b from-[#191631] to-[#0c0b16] p-5"
      style={{ boxShadow: `0 26px 60px -48px ${accent}`, ['--accent' as string]: accent }}
    >
      <div
        className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full opacity-25 blur-2xl transition group-hover:opacity-50"
        style={{ background: accent }}
      />
      <div className="relative z-10 flex items-center justify-between">
        <span className="chip" style={{ borderColor: `${accent}40`, background: `${accent}15`, color: accent }}>
          {String(product.meta.groupName ?? 'Кейс')}
        </span>
        {product.badge && (
          <span className="font-mono text-[0.55rem] font-bold uppercase tracking-widest" style={{ color: accent }}>
            {product.badge}
          </span>
        )}
      </div>
      <div className="relative z-10 my-3 flex justify-center">
        <motion.div whileHover={{ rotate: -4, scale: 1.04 }} transition={{ type: 'spring', stiffness: 200 }}>
          <Chest3D color={accent} size={128} />
        </motion.div>
      </div>
      <div className="relative z-10 mt-auto">
        <p className="font-display text-sm font-bold uppercase tracking-tight text-white">
          {product.meta.quantity} × кейс
        </p>
        <p className="mt-0.5 text-[0.68rem] text-white/35">
          {formatPrice(product.price)} · {formatPrice(product.price / Number(product.meta.quantity ?? 1))} за кейс
        </p>
        <div className="mt-3 flex items-center justify-between gap-2">
          <PriceTag price={product.price} oldPrice={product.oldPrice} size="sm" color="#fff" />
          <AddToCartButton product={product} className="!h-9 !px-3 !text-[0.72rem]" />
        </div>
      </div>
    </motion.article>
  );
}

export function RelicCard({ product, featured = false }: { product: Product; featured?: boolean }) {
  const amount = Number(product.meta.amount ?? 0);
  return (
    <article
      className={cn(
        'group relative flex flex-col overflow-hidden rounded-2xl border p-4 transition hover:-translate-y-1',
        featured
          ? 'border-gold/40 bg-gradient-to-br from-[#2a2011] via-[#1a1426] to-[#0d0b16]'
          : 'border-white/[0.07] bg-gradient-to-b from-[#191631] to-[#0d0c18] hover:border-gold/25',
      )}
      style={featured ? { boxShadow: '0 26px 70px -40px rgba(250,204,21,0.55)' } : undefined}
    >
      {featured && (
        <span className="absolute right-3 top-3 rounded bg-gold px-2 py-0.5 font-mono text-[0.55rem] font-extrabold uppercase tracking-widest text-[#2a1a00]">
          🔥 макс. выгода
        </span>
      )}
      <div className="relative flex items-end gap-1">
        <Coin size={40} />
        <span className="mb-1 font-display text-xl font-extrabold tracking-tight text-white">
          {amount.toLocaleString('ru-RU')}
        </span>
        <span className="mb-1.5 ml-0.5 font-mono text-[0.58rem] uppercase tracking-widest text-white/35">риликов</span>
        {!featured && product.badge && (
          <span className="mb-1.5 ml-auto rounded bg-gold/15 px-1.5 py-0.5 font-mono text-[0.52rem] font-bold uppercase tracking-widest text-gold">
            {product.badge}
          </span>
        )}
      </div>
      <p className="mt-2 text-[0.66rem] text-white/35">{product.subtitle}</p>
      <div className="mt-3 flex items-end justify-between gap-2">
        <PriceTag price={product.price} oldPrice={product.oldPrice} size="sm" color={featured ? '#facc15' : '#fff'} />
        <AddToCartButton product={product} className="!h-8 !px-2.5 !text-[0.68rem]" />
      </div>
    </article>
  );
}

export function ServiceCard({ product }: { product: Product }) {
  const accent = product.color;
  return (
    <article
      className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-white/[0.07] bg-gradient-to-b from-[#181530] to-[#0c0b16] p-5 transition hover:-translate-y-1"
      style={{ ['--accent' as string]: accent }}
    >
      <div className="flex items-start justify-between">
        <span
          className="grid h-12 w-12 place-items-center rounded-xl"
          style={{ background: `${accent}1f`, color: accent, boxShadow: `inset 0 0 0 1px ${accent}40` }}
        >
          <Icon name={product.icon} size={23} />
        </span>
        <DiscountBadge price={product.price} oldPrice={product.oldPrice} />
      </div>
      <h3 className="mt-4 font-display text-lg font-extrabold uppercase tracking-tight text-white">{product.name}</h3>
      <p className="mt-1 text-[0.7rem] font-semibold" style={{ color: accent }}>{product.subtitle}</p>
      <p className="mt-3 flex-1 text-[0.76rem] leading-relaxed text-white/45">{product.description}</p>
      <ul className="mt-3 space-y-1">
        {product.features.map((f) => (
          <li key={f} className="flex items-start gap-1.5 text-[0.7rem] text-white/40">
            <Check size={11} className="mt-0.5 shrink-0" style={{ color: accent }} strokeWidth={3} />
            {f}
          </li>
        ))}
      </ul>
      <div className="mt-4 flex items-center justify-between gap-2 border-t border-white/6 pt-3">
        <PriceTag price={product.price} oldPrice={product.oldPrice} size="md" color="#fff" />
        <AddToCartButton product={product} className="!h-9 !px-3 !text-[0.72rem]" />
      </div>
    </article>
  );
}

export function ProductCard({ product, compact }: { product: Product; compact?: boolean }) {
  switch (product.kind) {
    case 'rank':
      return <RankCard product={product} compact={compact} />;
    case 'case':
      return <CaseProductCard product={product} />;
    case 'relic':
      return <RelicCard product={product} />;
    default:
      return <ServiceCard product={product} />;
  }
}

export function FeaturePill({ children, color = '#A855F7' }: { children: React.ReactNode; color?: string }) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[0.68rem] font-semibold"
      style={{ background: `${color}14`, color, boxShadow: `inset 0 0 0 1px ${color}33` }}
    >
      <Sparkles size={11} /> {children}
    </span>
  );
}

export function PlusQuantity({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button onClick={onClick} className="btn btn-ghost btn-sm" aria-label={label}>
      <Plus size={14} /> Добавить
    </button>
  );
}
