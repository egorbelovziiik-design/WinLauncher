'use client';

import { useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { ArrowRight, Info, Play, RotateCcw, Sparkles } from 'lucide-react';
import type { CaseItem } from '@/lib/types';
import { RARITY_META } from '@/lib/types';
import { Icon } from './icons';

function weightedPick(items: CaseItem[]): CaseItem {
  const total = items.reduce((sum, i) => sum + i.chance, 0);
  let roll = Math.random() * total;
  for (const item of items) {
    roll -= item.chance;
    if (roll <= 0) return item;
  }
  return items[items.length - 1];
}

export function CaseOpeningDemo({ items, caseName }: { items: CaseItem[]; caseName: string }) {
  const [spinning, setSpinning] = useState(false);
  const [result, setResult] = useState<CaseItem | null>(null);
  const reelRef = useRef<HTMLDivElement>(null);

  const reel = useMemo(() => {
    if (items.length === 0) return [];
    return Array.from({ length: 32 }, () => items[Math.floor(Math.random() * items.length)]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.length, spinning]);

  const spin = () => {
    if (items.length === 0 || spinning) return;
    setResult(null);
    setSpinning(true);
    setTimeout(() => {
      setResult(weightedPick(items));
      setSpinning(false);
    }, 2100);
  };

  return (
    <div className="relative overflow-hidden rounded-2xl border border-white/8 bg-gradient-to-b from-[#15122a] to-[#0a0912] p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Демонстрация открытия</p>
          <p className="mt-0.5 font-mono text-[0.58rem] uppercase tracking-widest text-white/25">{caseName} · визуальный тест</p>
        </div>
        <button onClick={spin} disabled={spinning || items.length === 0} className="btn btn-primary btn-sm">
          {result ? <RotateCcw size={13} /> : <Play size={13} />}
          {spinning ? 'Крутим…' : result ? 'Ещё раз' : 'Открыть демо'}
        </button>
      </div>

      <div className="relative overflow-hidden rounded-xl border border-white/6 bg-void/70">
        <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-12 bg-gradient-to-r from-void to-transparent" />
        <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-12 bg-gradient-to-l from-void to-transparent" />
        <motion.div
          ref={reelRef}
          className="flex gap-2 py-3 pl-3"
          animate={spinning ? { x: [0, -180, -420, -980, -1380] } : { x: 0 }}
          transition={spinning ? { duration: 2.1, ease: [0.12, 0.7, 0.15, 1], times: [0, 0.25, 0.5, 0.8, 1] } : { duration: 0.2 }}
        >
          {(spinning ? reel : result ? [result] : reel.slice(0, 6)).map((item, i) => {
            const rarity = RARITY_META[item.rarity];
            return (
              <div
                key={`${item.name}-${i}`}
                className="flex h-24 w-20 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border px-1 text-center"
                style={{
                  borderColor: `${item.color}45`,
                  background: `linear-gradient(160deg, ${item.color}14, transparent)`,
                  boxShadow: `inset 0 0 22px ${item.color}12`,
                }}
              >
                <Icon name={item.icon} size={22} className="text-white/85" />
                <span className="line-clamp-2 text-[0.58rem] font-bold leading-tight text-white/80">{item.name}</span>
                <span className="font-mono text-[0.48rem] uppercase tracking-wide" style={{ color: rarity.color }}>
                  {rarity.label}
                </span>
              </div>
            );
          })}
        </motion.div>
      </div>

      <div className="mt-3 min-h-[3rem]">
        {result ? (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            className="rounded-lg border px-3 py-2"
            style={{ borderColor: `${result.color}55`, background: `${result.color}12` }}
          >
            <p className="font-mono text-[0.56rem] uppercase tracking-widest" style={{ color: result.color }}>
              в демо выпало · {RARITY_META[result.rarity].label}
            </p>
            <p className="mt-0.5 text-sm font-bold text-white">{result.name}</p>
            {result.description && (
              <p className="mt-1 text-[0.7rem] leading-snug text-white/55">{result.description}</p>
            )}
            {result.link && (
              <Link
                href={result.link}
                className="mt-1.5 inline-flex items-center gap-1 text-[0.66rem] font-bold text-neon hover:underline"
              >
                Посмотреть подробнее <ArrowRight size={11} />
              </Link>
            )}
          </motion.div>
        ) : (
          <p className="flex items-start gap-1.5 text-[0.66rem] leading-relaxed text-white/30">
            <Info size={12} className="mt-0.5 shrink-0" />
            Это визуальная демонстрация шансов из базы. Настоящее открытие кейса и выдача награды происходят в игре.
          </p>
        )}
      </div>

      <p className="mt-2 flex items-center gap-1.5 font-mono text-[0.54rem] uppercase tracking-widest text-white/20">
        <Sparkles size={10} /> демо не влияет на покупки и не создаёт наград
      </p>
    </div>
  );
}
