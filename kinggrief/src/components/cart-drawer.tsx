'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Minus, Plus, ShoppingCart, Tag, Trash2, X } from 'lucide-react';
import { useCart } from './providers';
import { Icon } from './icons';
import { formatPrice } from '@/lib/utils';

export function CartDrawer() {
  const {
    items, drawerOpen, setDrawerOpen, subtotal, discount, total, promo, applyPromo, clearPromo,
    setQuantity, remove, clear, count, ready,
  } = useCart();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawerOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [setDrawerOpen]);

  useEffect(() => {
    document.body.style.overflow = drawerOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [drawerOpen]);

  const submitPromo = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    await applyPromo(code);
    setBusy(false);
  };

  return (
    <AnimatePresence>
      {drawerOpen && (
        <div className="fixed inset-0 z-[70]">
          <motion.button
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setDrawerOpen(false)}
            className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            aria-label="Закрыть корзину"
            tabIndex={-1}
          />
          <motion.aside
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 320, damping: 34 }}
            className="absolute right-0 top-0 flex h-full w-full max-w-[26rem] flex-col border-l border-neon/20 bg-[#0b0a14] shadow-2xl"
            role="dialog"
            aria-modal="true"
            aria-label="Корзина"
          >
            <div className="flex items-center justify-between border-b border-white/8 px-5 py-4">
              <div>
                <p className="font-display text-lg font-bold text-white">Корзина</p>
                <p className="font-mono text-[0.62rem] uppercase tracking-widest text-white/35">
                  {count > 0 ? `${count} товаров` : 'пока пусто'}
                </p>
              </div>
              <button onClick={() => setDrawerOpen(false)} className="btn btn-ghost btn-icon" aria-label="Закрыть">
                <X size={17} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-4">
              {!ready ? (
                <div className="space-y-3">
                  {[0, 1].map((i) => (
                    <div key={i} className="skeleton h-20 rounded-xl" />
                  ))}
                </div>
              ) : items.length === 0 ? (
                <div className="flex h-full flex-col items-center justify-center text-center">
                  <div className="mb-4 grid h-16 w-16 place-items-center rounded-2xl border border-neon/20 bg-neon/8 text-neon">
                    <ShoppingCart size={26} />
                  </div>
                  <p className="font-display text-base font-bold text-white">Корзина пуста</p>
                  <p className="mt-1 max-w-[15rem] text-xs leading-relaxed text-white/40">
                    Добавьте привилегию, кейс или рилики — и они появятся здесь.
                  </p>
                  <Link href="/privileges" onClick={() => setDrawerOpen(false)} className="btn btn-primary btn-sm mt-5">
                    В магазин
                  </Link>
                </div>
              ) : (
                <ul className="space-y-3">
                  <AnimatePresence initial={false}>
                    {items.map((item) => (
                      <motion.li
                        key={item.productId}
                        layout
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, x: 40, height: 0 }}
                        className="relative overflow-hidden rounded-xl border border-white/6 bg-white/[0.025] p-3"
                      >
                        <div className="flex gap-3">
                          <span
                            className="grid h-11 w-11 shrink-0 place-items-center rounded-lg"
                            style={{
                              background: `linear-gradient(140deg, ${item.color}30, ${item.color}08)`,
                              color: item.color,
                              boxShadow: `inset 0 0 0 1px ${item.color}38`,
                            }}
                          >
                            <Icon name={item.icon} size={20} />
                          </span>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-start justify-between gap-2">
                              <div className="min-w-0">
                                <p className="truncate text-[0.82rem] font-bold text-white">{item.name}</p>
                                <p className="mt-0.5 truncate text-[0.68rem] text-white/40">{item.subtitle}</p>
                              </div>
                              <button
                                onClick={() => remove(item.productId)}
                                className="text-white/25 transition hover:text-danger"
                                aria-label={`Удалить ${item.name}`}
                              >
                                <Trash2 size={14} />
                              </button>
                            </div>
                            <div className="mt-2 flex items-center justify-between">
                              {item.kind === 'rank' ? (
                                <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/30">1 экземпляр</span>
                              ) : (
                                <div className="flex items-center gap-1.5">
                                  <button
                                    onClick={() => setQuantity(item.productId, item.quantity - 1)}
                                    className="grid h-6 w-6 place-items-center rounded border border-white/10 text-white/60 transition hover:border-neon/40 hover:text-white"
                                    aria-label="Уменьшить количество"
                                  >
                                    <Minus size={11} />
                                  </button>
                                  <span className="stat-num w-6 text-center text-xs font-bold text-white">{item.quantity}</span>
                                  <button
                                    onClick={() => setQuantity(item.productId, item.quantity + 1)}
                                    className="grid h-6 w-6 place-items-center rounded border border-white/10 text-white/60 transition hover:border-neon/40 hover:text-white"
                                    aria-label="Увеличить количество"
                                  >
                                    <Plus size={11} />
                                  </button>
                                </div>
                              )}
                              <span className="stat-num text-sm font-extrabold text-white">{formatPrice(item.price * item.quantity)}</span>
                            </div>
                          </div>
                        </div>
                      </motion.li>
                    ))}
                  </AnimatePresence>
                </ul>
              )}
            </div>

            {items.length > 0 && (
              <div className="border-t border-white/8 px-5 py-4">
                {promo?.code && !promo.error ? (
                  <div className="mb-3 flex items-center justify-between rounded-lg border border-emerald/25 bg-emerald/8 px-3 py-2">
                    <span className="inline-flex items-center gap-1.5 text-[0.72rem] font-bold text-emerald">
                      <Tag size={12} /> {promo.code} · −{formatPrice(promo.discount)}
                    </span>
                    <button onClick={clearPromo} className="text-[0.68rem] text-white/40 hover:text-white">убрать</button>
                  </div>
                ) : (
                  <form onSubmit={submitPromo} className="mb-3 flex gap-2">
                    <input
                      value={code}
                      onChange={(e) => setCode(e.target.value.toUpperCase())}
                      placeholder="Промокод"
                      className="field h-9 flex-1 text-xs uppercase tracking-widest"
                      maxLength={32}
                      aria-label="Промокод"
                    />
                    <button type="submit" className="btn btn-ghost btn-sm" disabled={busy || !code.trim()}>
                      {busy ? '...' : 'Применить'}
                    </button>
                  </form>
                )}
                {promo?.error && <p className="mb-2 text-[0.68rem] text-danger">{promo.error}</p>}

                <dl className="mb-3 space-y-1.5 text-[0.78rem]">
                  <div className="flex justify-between text-white/50">
                    <dt>Товары</dt>
                    <dd className="stat-num text-white/80">{formatPrice(subtotal)}</dd>
                  </div>
                  {discount > 0 && (
                    <div className="flex justify-between text-emerald">
                      <dt>Скидка</dt>
                      <dd className="stat-num">−{formatPrice(discount)}</dd>
                    </div>
                  )}
                  <div className="flex items-end justify-between border-t border-white/8 pt-2">
                    <dt className="text-xs font-bold uppercase tracking-widest text-white/60">Итого</dt>
                    <dd className="stat-num text-xl font-extrabold text-white">{formatPrice(total)}</dd>
                  </div>
                </dl>

                <Link href="/checkout" onClick={() => setDrawerOpen(false)} className="btn btn-primary w-full">
                  Оформить заказ
                </Link>
                <div className="mt-2 flex items-center justify-between">
                  <button onClick={clear} className="text-[0.68rem] font-semibold text-white/35 transition hover:text-danger">
                    Очистить корзину
                  </button>
                  <Link href="/cart" onClick={() => setDrawerOpen(false)} className="text-[0.68rem] font-semibold text-white/50 hover:text-white">
                    Открыть страницу корзины →
                  </Link>
                </div>
              </div>
            )}
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  );
}
