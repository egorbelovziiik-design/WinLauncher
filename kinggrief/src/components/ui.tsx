'use client';

import { useEffect, useId, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Check, ChevronDown, Copy, Star } from 'lucide-react';
import { cn, formatPrice } from '@/lib/utils';
import { useToast } from './providers';

export function Reveal({
  children,
  delay = 0,
  className = '',
  y = 24,
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
  y?: number;
}) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.15 }}
      transition={{ duration: 0.65, delay, ease: [0.2, 0.7, 0.25, 1] }}
    >
      {children}
    </motion.div>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  description,
  action,
  align = 'left',
}: {
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  align?: 'left' | 'center';
}) {
  return (
    <div
      className={cn(
        'mb-8 flex flex-col gap-4 md:mb-10',
        align === 'center' ? 'items-center text-center' : 'md:flex-row md:items-end md:justify-between',
      )}
    >
      <div className={cn('max-w-2xl', align === 'center' && 'mx-auto')}>
        {eyebrow && (
          <span className="eyebrow mb-3 block">
            <span className="inline-block h-px w-6 bg-gradient-to-r from-neon to-transparent" />
            {eyebrow}
          </span>
        )}
        <h2 className="display text-[1.9rem] leading-[0.95] text-white sm:text-4xl lg:text-[2.9rem]">{title}</h2>
        {description && <p className="mt-3 max-w-xl text-sm leading-relaxed text-white/50 sm:text-[0.95rem]">{description}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function DiscountBadge({ price, oldPrice }: { price: number; oldPrice: number | null }) {
  if (!oldPrice || oldPrice <= price) return null;
  const percent = Math.round(((oldPrice - price) / oldPrice) * 100);
  return (
    <span className="inline-flex items-center rounded-md bg-danger/15 px-1.5 py-0.5 font-mono text-[0.62rem] font-bold tracking-wide text-[#fca5a5] ring-1 ring-danger/30">
      −{percent}%
    </span>
  );
}

export function PriceTag({
  price,
  oldPrice,
  size = 'md',
  color,
}: {
  price: number;
  oldPrice?: number | null;
  size?: 'sm' | 'md' | 'lg';
  color?: string;
}) {
  return (
    <span className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
      <span
        className={cn(
          'stat-num font-extrabold leading-none',
          size === 'lg' ? 'text-[2rem]' : size === 'sm' ? 'text-base' : 'text-xl',
        )}
        style={{ color: color ?? '#fff' }}
      >
        {formatPrice(price)}
      </span>
      {oldPrice && oldPrice > price && (
        <span className={cn('stat-num text-white/30 line-through', size === 'lg' ? 'text-base' : 'text-xs')}>
          {formatPrice(oldPrice)}
        </span>
      )}
      <DiscountBadge price={price} oldPrice={oldPrice ?? null} />
    </span>
  );
}

export function Stars({ value, size = 14, className = '' }: { value: number; size?: number; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-0.5', className)} aria-label={`Оценка ${value} из 5`}>
      {Array.from({ length: 5 }).map((_, i) => (
        <Star
          key={i}
          size={size}
          className={i < Math.round(value) ? 'text-gold' : 'text-white/15'}
          fill={i < Math.round(value) ? 'currentColor' : 'none'}
          strokeWidth={1.5}
        />
      ))}
    </span>
  );
}

export function CopyButton({
  value,
  label = 'Скопировать',
  copyTitle = 'Скопировано',
  className = '',
  variant = 'ghost',
  icon,
}: {
  value: string;
  label?: string;
  copyTitle?: string;
  className?: string;
  variant?: 'ghost' | 'primary' | 'plain';
  icon?: ReactNode;
}) {
  const [copied, setCopied] = useState(false);
  const { push } = useToast();

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      const textarea = document.createElement('textarea');
      textarea.value = value;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      textarea.remove();
    }
    setCopied(true);
    push({ tone: 'success', title: copyTitle, description: value });
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button
      type="button"
      onClick={copy}
      className={cn(
        variant === 'primary' && 'btn btn-primary',
        variant === 'ghost' && 'btn btn-ghost',
        variant === 'plain' && 'inline-flex items-center gap-1.5 text-xs font-semibold text-white/60 transition hover:text-white',
        className,
      )}
      aria-live="polite"
    >
      {copied ? <Check size={15} className="text-emerald" /> : (icon ?? <Copy size={15} />)}
      <span>{copied ? 'Скопировано!' : label}</span>
    </button>
  );
}

export function Accordion({ items }: { items: Array<{ q: string; a: ReactNode }> }) {
  const [open, setOpen] = useState<number | null>(0);
  const baseId = useId();
  return (
    <div className="divide-y divide-neon/10 overflow-hidden rounded-2xl border border-neon/12 bg-panel/40">
      {items.map((item, i) => {
        const active = open === i;
        return (
          <div key={item.q}>
            <button
              type="button"
              onClick={() => setOpen(active ? null : i)}
              aria-expanded={active}
              aria-controls={`${baseId}-${i}`}
              className="flex w-full items-center justify-between gap-4 px-4 py-4 text-left transition hover:bg-neon/5 sm:px-6 sm:py-5"
            >
              <span className="text-sm font-bold text-white sm:text-[0.95rem]">{item.q}</span>
              <span
                className={cn(
                  'grid h-7 w-7 shrink-0 place-items-center rounded-full border transition',
                  active ? 'rotate-180 border-neon/50 bg-neon/20 text-white' : 'border-white/10 text-white/45',
                )}
              >
                <ChevronDown size={15} />
              </span>
            </button>
            <AnimatePresence initial={false}>
              {active && (
                <motion.div
                  id={`${baseId}-${i}`}
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.28, ease: [0.2, 0.7, 0.3, 1] }}
                  className="overflow-hidden"
                >
                  <div className="px-4 pb-5 text-[0.82rem] leading-relaxed text-white/55 sm:px-6">{item.a}</div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        );
      })}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-neon/20 bg-panel/30 px-6 py-14 text-center">
      <div className="mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-neon/10 text-neon ring-1 ring-neon/25">
        {icon}
      </div>
      <p className="font-display text-lg font-bold text-white">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-sm text-white/45">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function Marquee({ items }: { items: string[] }) {
  const reduced = useReducedMotion();
  const row = [...items, ...items];
  return (
    <div className="relative overflow-hidden border-y border-neon/10 bg-abyss/60 py-3">
      <div
        className="flex w-max gap-8 whitespace-nowrap"
        style={reduced ? undefined : { animation: 'marquee 38s linear infinite' }}
      >
        {row.map((item, i) => (
          <span key={i} className="flex items-center gap-8 font-mono text-[0.68rem] uppercase tracking-[0.22em] text-white/40">
            {item}
            <span className="text-neon">◆</span>
          </span>
        ))}
      </div>
    </div>
  );
}

export function GoBack() {
  return (
    <Link href="/" className="inline-flex items-center gap-1.5 text-xs font-semibold text-white/45 transition hover:text-white">
      ← На главную
    </Link>
  );
}

export function useMounted() {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}
