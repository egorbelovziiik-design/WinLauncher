'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { AnimatePresence, motion } from 'framer-motion';
import { ArrowUpDown, Check, Crown, Search, Sparkles, X } from 'lucide-react';
import type { Product } from '@/lib/types';
import { formatPrice } from '@/lib/utils';
import { RankCard } from './product-card';

type Sort = 'tier' | 'price-asc' | 'price-desc' | 'discount';

export function PrivilegesExplorer({ ranks }: { ranks: Product[] }) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<Sort>('tier');
  const [budget, setBudget] = useState<number | null>(null);
  const [compare, setCompare] = useState<string[]>([]);
  const [showCompare, setShowCompare] = useState(false);

  const filtered = useMemo(() => {
    let list = ranks.filter((r) => {
      const matches = query.trim()
        ? `${r.name} ${r.subtitle} ${r.features.join(' ')}`.toLowerCase().includes(query.trim().toLowerCase())
        : true;
      const inBudget = budget == null ? true : r.price <= budget;
      return matches && inBudget;
    });
    list = [...list].sort((a, b) => {
      switch (sort) {
        case 'price-asc':
          return a.price - b.price;
        case 'price-desc':
          return b.price - a.price;
        case 'discount': {
          const da = a.oldPrice ? (a.oldPrice - a.price) / a.oldPrice : 0;
          const db = b.oldPrice ? (b.oldPrice - b.price) / b.oldPrice : 0;
          return db - da;
        }
        default:
          return Number(a.meta.tier) - Number(b.meta.tier);
      }
    });
    return list;
  }, [ranks, query, sort, budget]);

  const selected = ranks.filter((r) => compare.includes(r.id));
  const toggle = (id: string) =>
    setCompare((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : prev.length >= 3 ? prev : [...prev, id]));

  const allFeatures = Array.from(new Set(selected.flatMap((r) => r.features)));

  return (
    <div>
      <div className="sticky top-[68px] z-30 -mx-2 mb-6 rounded-xl border border-white/6 bg-void/85 px-3 py-3 backdrop-blur-xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <label className="relative flex-1">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Найти привилегию или возможность…"
              className="field h-10 pl-9 text-[0.82rem]"
              aria-label="Поиск привилегии"
            />
          </label>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1.5 rounded-lg border border-white/8 bg-white/[0.03] px-2 py-1">
              <ArrowUpDown size={13} className="text-white/30" />
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value as Sort)}
                className="bg-transparent py-1 text-[0.76rem] font-semibold text-white/75 outline-none"
                aria-label="Сортировка"
              >
                <option value="tier" className="bg-panel">По уровню</option>
                <option value="price-asc" className="bg-panel">Сначала дешёвые</option>
                <option value="price-desc" className="bg-panel">Сначала дорогие</option>
                <option value="discount" className="bg-panel">Больше скидка</option>
              </select>
            </div>

            {[null, 99, 249, 599].map((value) => (
              <button
                key={String(value)}
                onClick={() => setBudget(value)}
                className={`rounded-lg border px-2.5 py-1.5 text-[0.72rem] font-semibold transition ${
                  budget === value
                    ? 'border-neon/50 bg-neon/15 text-white'
                    : 'border-white/8 bg-white/[0.02] text-white/50 hover:border-white/20 hover:text-white'
                }`}
              >
                {value == null ? 'Все' : `до ${formatPrice(value)}`}
              </button>
            ))}
          </div>
        </div>
        {compare.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-white/6 pt-2">
            <span className="font-mono text-[0.56rem] uppercase tracking-widest text-white/30">Сравнение:</span>
            {selected.map((r) => (
              <span
                key={r.id}
                className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[0.62rem] font-bold"
                style={{ background: `${r.color}1f`, color: r.color }}
              >
                {r.name}
                <button onClick={() => toggle(r.id)} aria-label={`Убрать ${r.name} из сравнения`}><X size={10} /></button>
              </span>
            ))}
            <button onClick={() => setShowCompare(true)} className="ml-auto btn btn-primary !h-7 !px-2.5 !text-[0.66rem]">
              Сравнить {compare.length}
            </button>
            <button onClick={() => setCompare([])} className="text-[0.66rem] text-white/35 hover:text-white">сбросить</button>
          </div>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {filtered.map((rank, i) => (
          <motion.div
            key={rank.id}
            layout
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: Math.min(i * 0.03, 0.25) }}
            className="relative flex flex-col"
          >
            <RankCard product={rank} />
            <button
              onClick={() => toggle(rank.id)}
              aria-pressed={compare.includes(rank.id)}
              className={`mt-2 inline-flex items-center justify-center gap-1 self-end rounded-md border px-2 py-1 font-mono text-[0.54rem] uppercase tracking-widest transition ${
                compare.includes(rank.id)
                  ? 'border-neon/60 bg-neon/25 text-white'
                  : 'border-white/10 bg-white/[0.02] text-white/40 hover:border-neon/40 hover:text-white'
              }`}
            >
              {compare.includes(rank.id) ? <Check size={10} /> : null} сравнить
            </button>
          </motion.div>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="rounded-2xl border border-dashed border-neon/20 p-10 text-center">
          <p className="font-display text-lg font-bold text-white">Ничего не найдено</p>
          <p className="mt-1 text-sm text-white/40">Попробуйте другой запрос или сбросьте фильтр по цене.</p>
          <button
            onClick={() => { setQuery(''); setBudget(null); }}
            className="btn btn-ghost btn-sm mt-4"
          >
            Сбросить фильтры
          </button>
        </div>
      )}

      <AnimatePresence>
        {showCompare && selected.length > 0 && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[80] flex items-end justify-center bg-black/75 p-3 backdrop-blur-sm sm:items-center"
            role="dialog"
            aria-modal="true"
            aria-label="Сравнение привилегий"
          >
            <motion.div
              initial={{ y: 40, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: 30, opacity: 0 }}
              className="panel max-h-[86vh] w-full max-w-4xl overflow-hidden"
            >
              <div className="flex items-center justify-between border-b border-white/8 px-4 py-3">
                <div>
                  <p className="font-display text-base font-bold text-white">Сравнение привилегий</p>
                  <p className="font-mono text-[0.58rem] uppercase tracking-widest text-white/30">
                    {selected.length} из 3 · возможности настраиваются администрацией
                  </p>
                </div>
                <button onClick={() => setShowCompare(false)} className="btn btn-ghost btn-icon !h-8 !w-8" aria-label="Закрыть">
                  <X size={15} />
                </button>
              </div>
              <div className="max-h-[calc(86vh-64px)] overflow-auto p-4">
                <table className="w-full min-w-[560px] border-collapse text-left">
                  <thead>
                    <tr>
                      <th className="sticky left-0 z-10 bg-[#12101f] px-3 py-2 font-mono text-[0.56rem] uppercase tracking-widest text-white/30">
                        Возможность
                      </th>
                      {selected.map((r) => (
                        <th key={r.id} className="px-3 py-2" style={{ borderBottom: `2px solid ${r.color}` }}>
                          <span className="font-display text-sm font-extrabold uppercase" style={{ color: r.color }}>{r.name}</span>
                          <span className="mt-0.5 block font-mono text-[0.6rem] text-white/40">{formatPrice(r.price)}</span>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {allFeatures.map((feature) => (
                      <tr key={feature} className="border-b border-white/5">
                        <td className="sticky left-0 z-10 bg-[#12101f] px-3 py-2 text-[0.72rem] text-white/55">{feature}</td>
                        {selected.map((r) => (
                          <td key={r.id} className="px-3 py-2">
                            {r.features.includes(feature) ? (
                              <Check size={13} style={{ color: r.color }} strokeWidth={3} />
                            ) : (
                              <span className="text-white/15">—</span>
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                    <tr>
                      <td className="sticky left-0 z-10 bg-[#12101f] px-3 py-3 font-mono text-[0.56rem] uppercase tracking-widest text-white/30">
                        Купить
                      </td>
                      {selected.map((r) => (
                        <td key={r.id} className="px-3 py-3">
                          <Link href={`/privileges/${r.meta.rankId}`} className="btn btn-ghost btn-sm w-full">Открыть</Link>
                        </td>
                      ))}
                    </tr>
                  </tbody>
                </table>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="mt-8 flex flex-wrap items-center gap-3 rounded-xl border border-neon/12 bg-neon/[0.04] px-4 py-3 text-[0.74rem] text-white/45">
        <Sparkles size={14} className="text-neon" />
        Возможности привилегий редактируются в админ-панели. Мы не публикуем выдуманные права — перед покупкой проверьте
        актуальный состав в таблице на странице привилегии.
        <Crown size={14} className="ml-auto text-gold" />
      </div>
    </div>
  );
}
