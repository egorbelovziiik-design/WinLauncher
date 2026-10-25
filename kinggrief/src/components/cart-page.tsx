'use client';

import Link from 'next/link';
import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ArrowRight, Minus, Plus, ShieldCheck, ShoppingCart, Tag, Trash2 } from 'lucide-react';
import { formatPrice } from '@/lib/utils';
import type { Product } from '@/lib/types';
import { useCart } from './providers';
import { Icon } from './icons';
import { AddToCartButton } from './product-card';
import { EmptyState } from './ui';

export function CartPageClient({ suggestions }: { suggestions: Product[] }) {
  const { items, subtotal, discount, total, promo, applyPromo, clearPromo, setQuantity, remove, clear, ready } = useCart();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);

  if (!ready) {
    return <div className="skeleton h-64 rounded-2xl" />;
  }

  if (items.length === 0) {
    return (
      <div className="space-y-10">
        <EmptyState
          icon={<ShoppingCart size={24} />}
          title="Корзина пуста"
          description="Добавьте привилегию, кейс или пакет риликов — и оформите заказ за пару шагов."
          action={
            <Link href="/privileges" className="btn btn-primary">
              Выбрать привилегию <ArrowRight size={15} />
            </Link>
          }
        />
        {suggestions.length > 0 && (
          <div>
            <h2 className="mb-3 font-display text-base font-bold uppercase tracking-tight text-white/80">С этим часто покупают</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {suggestions.map((product) => (
                <div key={product.id} className="flex items-center gap-3 rounded-xl border border-white/7 bg-panel/40 p-3">
                  <span
                    className="grid h-10 w-10 shrink-0 place-items-center rounded-lg"
                    style={{ background: `${product.color}18`, color: product.color, boxShadow: `inset 0 0 0 1px ${product.color}30` }}
                  >
                    <Icon name={product.icon} size={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[0.78rem] font-bold text-white">{product.name}</p>
                    <p className="stat-num text-[0.7rem] text-white/40">{formatPrice(product.price)}</p>
                  </div>
                  <AddToCartButton product={product} showIcon={false} label="+" className="!h-7 !w-7 !px-0 !text-[0.8rem]" />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
      <div className="space-y-3">
        <AnimatePresence initial={false}>
          {items.map((item) => (
            <motion.div
              key={item.productId}
              layout
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, x: 30, height: 0 }}
              className="flex flex-col gap-3 rounded-2xl border border-white/7 bg-gradient-to-b from-[#181530] to-[#0d0c18] p-4 sm:flex-row sm:items-center"
            >
              <span
                className="grid h-14 w-14 shrink-0 place-items-center rounded-xl"
                style={{ background: `${item.color}16`, color: item.color, boxShadow: `inset 0 0 0 1px ${item.color}35` }}
              >
                <Icon name={item.icon} size={26} />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-[0.9rem] font-bold text-white">{item.name}</p>
                  <span className="rounded bg-white/6 px-1.5 py-0.5 font-mono text-[0.54rem] uppercase tracking-widest text-white/40">
                    {item.kind === 'rank' ? 'привилегия' : item.kind === 'case' ? 'кейс' : item.kind === 'relic' ? 'рилики' : 'услуга'}
                  </span>
                </div>
                <p className="mt-0.5 text-[0.72rem] text-white/35">{item.subtitle}</p>
                <p className="mt-1 font-mono text-[0.62rem] text-white/25">{formatPrice(item.price)} / шт.</p>
              </div>

              <div className="flex items-center justify-between gap-4 sm:justify-end">
                {item.kind === 'rank' ? (
                  <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/30">1 экземпляр</span>
                ) : (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setQuantity(item.productId, item.quantity - 1)}
                      className="grid h-8 w-8 place-items-center rounded-lg border border-white/10 text-white/60 transition hover:border-neon/40 hover:text-white"
                      aria-label="Уменьшить количество"
                    >
                      <Minus size={13} />
                    </button>
                    <input
                      type="number"
                      value={item.quantity}
                      onChange={(e) => setQuantity(item.productId, Number(e.target.value))}
                      className="h-8 w-12 rounded-lg border border-white/10 bg-void text-center font-mono text-xs text-white outline-none focus:border-neon/50"
                      aria-label={`Количество ${item.name}`}
                      min={1}
                      max={20}
                    />
                    <button
                      onClick={() => setQuantity(item.productId, item.quantity + 1)}
                      className="grid h-8 w-8 place-items-center rounded-lg border border-white/10 text-white/60 transition hover:border-neon/40 hover:text-white"
                      aria-label="Увеличить количество"
                    >
                      <Plus size={13} />
                    </button>
                  </div>
                )}
                <div className="w-24 text-right">
                  <p className="stat-num text-base font-extrabold text-white">{formatPrice(item.price * item.quantity)}</p>
                </div>
                <button
                  onClick={() => remove(item.productId)}
                  className="text-white/25 transition hover:text-danger"
                  aria-label={`Удалить ${item.name}`}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>

        <div className="flex items-center justify-between pt-1">
          <Link href="/privileges" className="text-[0.74rem] font-semibold text-white/45 hover:text-white">← Продолжить покупки</Link>
          <button onClick={clear} className="text-[0.74rem] font-semibold text-white/30 transition hover:text-danger">Очистить корзину</button>
        </div>
      </div>

      <aside className="lg:sticky lg:top-24 lg:h-fit">
        <div className="rounded-2xl border border-white/8 bg-panel/50 p-5">
          <h2 className="font-display text-base font-bold uppercase tracking-widest text-white/75">Итого</h2>

          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              await applyPromo(code);
              setBusy(false);
            }}
            className="mt-4"
          >
            {promo?.code && !promo.error ? (
              <div className="flex items-center justify-between rounded-lg border border-emerald/25 bg-emerald/8 px-3 py-2">
                <span className="inline-flex items-center gap-1.5 text-[0.74rem] font-bold text-emerald">
                  <Tag size={12} /> {promo.code} · −{formatPrice(promo.discount)}
                </span>
                <button type="button" onClick={clearPromo} className="text-[0.68rem] text-white/40 hover:text-white">убрать</button>
              </div>
            ) : (
              <div className="flex gap-2">
                <input
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="Промокод"
                  className="field h-10 flex-1 text-[0.78rem] uppercase tracking-widest"
                  aria-label="Промокод"
                  maxLength={32}
                />
                <button className="btn btn-ghost btn-sm !h-10" disabled={busy || !code.trim()}>
                  {busy ? '…' : 'ОК'}
                </button>
              </div>
            )}
            {promo?.error && <p className="mt-1.5 text-[0.68rem] text-danger">{promo.error}</p>}
          </form>

          <dl className="mt-4 space-y-2 text-[0.8rem]">
            <div className="flex justify-between text-white/45">
              <dt>Товары</dt><dd className="stat-num text-white/80">{formatPrice(subtotal)}</dd>
            </div>
            {discount > 0 && (
              <div className="flex justify-between text-emerald">
                <dt>Скидка</dt><dd className="stat-num">−{formatPrice(discount)}</dd>
              </div>
            )}
            <div className="flex items-end justify-between border-t border-white/8 pt-2.5">
              <dt className="text-[0.72rem] font-bold uppercase tracking-widest text-white/55">К оплате</dt>
              <dd className="stat-num text-2xl font-extrabold text-white">{formatPrice(total)}</dd>
            </div>
          </dl>

          <Link href="/checkout" className="btn btn-primary mt-4 w-full">
            Оформить заказ <ArrowRight size={15} />
          </Link>

          <p className="mt-3 flex items-start gap-1.5 text-[0.66rem] leading-relaxed text-white/30">
            <ShieldCheck size={12} className="mt-0.5 shrink-0 text-emerald" />
            Цена пересчитывается на сервере при оформлении. Выдача — только после подтверждения оплаты провайдером.
          </p>
        </div>
      </aside>
    </div>
  );
}
