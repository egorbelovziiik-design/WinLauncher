'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { Calculator, Coins, Minus, Plus, Sparkles } from 'lucide-react';
import type { Product } from '@/lib/types';
import { estimateRelicPrice, pricePerThousand, type RelicPricing } from '@/lib/relic-pricing';
import { formatNumber, formatPrice } from '@/lib/utils';
import { AddToCartButton } from './product-card';
import { Coin } from './voxel';

export function RelicCalculator({ pricing, products }: { pricing: RelicPricing; products: Product[] }) {
  const [amount, setAmount] = useState(25000);
  const [inputValue, setInputValue] = useState('25000');

  const price = useMemo(() => estimateRelicPrice(amount, pricing), [amount, pricing]);
  const perK = pricePerThousand(amount, price);

  const nearest = useMemo(() => {
    return products.reduce((best, p) => {
      const diff = Math.abs(Number(p.meta.amount) - amount);
      const bestDiff = Math.abs(Number(best.meta.amount) - amount);
      return diff < bestDiff ? p : best;
    }, products[0]);
  }, [amount, products]);

  const setBoth = (value: number) => {
    const clamped = Math.min(Math.max(Math.round(value), pricing.minAmount), pricing.maxAmount);
    setAmount(clamped);
    setInputValue(String(clamped));
  };

  const maxPerK = 19;
  const currentDiscount = Math.max(0, Math.round((1 - perK / maxPerK) * 100));

  return (
    <div className="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
      <div className="relative overflow-hidden rounded-2xl border border-gold/20 bg-gradient-to-br from-[#1e1810] via-[#141126] to-[#0a0912] p-5 sm:p-6">
        <div className="pointer-events-none absolute -right-10 -top-10 opacity-20">
          <Coin size={140} />
        </div>
        <div className="relative">
          <span className="eyebrow !text-gold/80"><Calculator size={12} /> калькулятор риликов</span>
          <h2 className="mt-2 display text-2xl text-white">Сколько нужно?</h2>
          <p className="mt-1.5 max-w-sm text-[0.78rem] leading-relaxed text-white/40">
            Выберите количество от 1 до {formatNumber(pricing.maxAmount)}. Цена считается по формуле сервера — без
            скрытых доплат.
          </p>

          <div className="mt-5 flex flex-wrap gap-2">
            {[1000, 5000, 10000, 25000, 100000].map((preset) => (
              <button
                key={preset}
                onClick={() => setBoth(preset)}
                className={`rounded-lg border px-2.5 py-1.5 font-mono text-[0.66rem] font-bold transition ${
                  amount === preset
                    ? 'border-gold/60 bg-gold/15 text-gold'
                    : 'border-white/8 bg-white/[0.02] text-white/45 hover:border-white/20 hover:text-white'
                }`}
              >
                {formatNumber(preset)}
              </button>
            ))}
          </div>

          <label className="mt-5 block">
            <span className="field-label">Количество риликов</span>
            <div className="flex items-center gap-2">
              <button onClick={() => setBoth(amount - 1000)} className="btn btn-ghost btn-icon" aria-label="Уменьшить на 1000">
                <Minus size={15} />
              </button>
              <input
                type="number"
                min={pricing.minAmount}
                max={pricing.maxAmount}
                value={inputValue}
                onChange={(e) => {
                  setInputValue(e.target.value);
                  const parsed = Number(e.target.value);
                  if (Number.isFinite(parsed) && parsed > 0) setAmount(Math.min(parsed, pricing.maxAmount));
                }}
                onBlur={() => setBoth(Number(inputValue) || pricing.minAmount)}
                className="field h-11 flex-1 text-center font-display text-lg font-bold"
                aria-label="Количество риликов"
              />
              <button onClick={() => setBoth(amount + 1000)} className="btn btn-ghost btn-icon" aria-label="Увеличить на 1000">
                <Plus size={15} />
              </button>
            </div>
          </label>

          <label className="mt-4 block">
            <span className="sr-only">Ползунок количества риликов</span>
            <input
              type="range"
              min={pricing.minAmount}
              max={pricing.maxAmount}
              step={500}
              value={amount}
              onChange={(e) => setBoth(Number(e.target.value))}
              className="h-1.5 w-full cursor-pointer appearance-none rounded-full bg-white/10 accent-[#facc15]"
              style={{
                background: `linear-gradient(90deg, #facc15 ${((amount - pricing.minAmount) / (pricing.maxAmount - pricing.minAmount)) * 100}%, rgba(255,255,255,0.09) ${((amount - pricing.minAmount) / (pricing.maxAmount - pricing.minAmount)) * 100}%)`,
              }}
            />
            <div className="mt-1 flex justify-between font-mono text-[0.55rem] text-white/25">
              <span>{pricing.minAmount}</span>
              <span>{formatNumber(pricing.maxAmount)}</span>
            </div>
          </label>
        </div>
      </div>

      <div className="relative flex flex-col overflow-hidden rounded-2xl border border-gold/30 bg-[#0e0b16] p-5 sm:p-6">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-gold/15 to-transparent" />
        <div className="relative">
          <p className="font-mono text-[0.56rem] uppercase tracking-[0.2em] text-gold/70">итоговая стоимость</p>
          <div className="mt-1 flex items-end gap-2">
            <span className="stat-num text-4xl font-extrabold text-gold">{formatPrice(price)}</span>
          </div>
          <p className="mt-1 font-mono text-[0.6rem] text-white/35">
            {formatNumber(amount)} риликов · {perK.toFixed(2).replace('.', ',')} ₽ за 1 000
          </p>

          <div className="mt-4 flex items-center gap-2 rounded-lg border border-emerald/20 bg-emerald/8 px-3 py-2">
            <Sparkles size={13} className="text-emerald" />
            <span className="text-[0.7rem] font-semibold text-emerald">
              {currentDiscount > 0 ? `выгода ${currentDiscount}% относительно минимального пакета` : 'базовая цена'}
            </span>
          </div>

          <div className="mt-4 rounded-xl border border-white/7 bg-white/[0.02] p-3">
            <p className="font-mono text-[0.54rem] uppercase tracking-widest text-white/30">ближайший пакет</p>
            <div className="mt-1 flex items-center justify-between gap-2">
              <div>
                <p className="text-[0.82rem] font-bold text-white">{nearest.name}</p>
                <p className="text-[0.66rem] text-white/35">{formatPrice(nearest.price)} · выгода {Math.round(((nearest.oldPrice ?? nearest.price) - nearest.price) / (nearest.oldPrice ?? nearest.price) * 100)}%</p>
              </div>
              <AddToCartButton product={nearest} className="!h-8 !px-2.5 !text-[0.66rem]" variant="gold" />
            </div>
          </div>

          <p className="mt-3 text-[0.64rem] leading-relaxed text-white/30">
            Калькулятор оценивает стоимость. Покупка оформляется пакетами — после оплаты рилики зачисляются на никнейм
            автоматически.
          </p>
          <Link href="#packs" className="btn btn-gold mt-4 w-full">Выбрать пакет</Link>
        </div>
      </div>
    </div>
  );
}
