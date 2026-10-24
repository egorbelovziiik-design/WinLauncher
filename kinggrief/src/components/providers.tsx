'use client';

import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode,
} from 'react';
import { AnimatePresence, motion, MotionConfig } from 'framer-motion';
import { Check, Info, ShieldAlert, ShoppingCart, X } from 'lucide-react';
import type { ProductKind } from '@/lib/types';

/* ───────────────────────── toasts ───────────────────────── */

type Tone = 'success' | 'error' | 'info';
type Toast = { id: number; title: string; description?: string; tone: Tone };

const ToastCtx = createContext<{ push: (t: Omit<Toast, 'id'>) => void }>({ push: () => {} });
export const useToast = () => useContext(ToastCtx);

const toneIcon: Record<Tone, ReactNode> = {
  success: <Check size={16} strokeWidth={3} />,
  error: <ShieldAlert size={16} />,
  info: <Info size={16} />,
};
const toneColor: Record<Tone, string> = { success: '#22C55E', error: '#EF4444', info: '#A855F7' };

function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const counter = useRef(0);

  const push = useCallback((t: Omit<Toast, 'id'>) => {
    const id = ++counter.current;
    setToasts((prev) => [...prev.slice(-3), { ...t, id }]);
    setTimeout(() => setToasts((prev) => prev.filter((x) => x.id !== id)), 4200);
  }, []);

  return (
    <ToastCtx.Provider value={{ push }}>
      {children}
      <div className="pointer-events-none fixed inset-x-3 bottom-4 z-[90] flex flex-col items-center gap-2 sm:inset-x-auto sm:right-6 sm:bottom-6 sm:items-end">
        <AnimatePresence initial={false}>
          {toasts.map((toast) => (
            <motion.div
              key={toast.id}
              layout
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 30, scale: 0.95 }}
              transition={{ type: 'spring', stiffness: 380, damping: 30 }}
              className="glass pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl px-3.5 py-3 shadow-2xl"
              style={{ borderColor: `${toneColor[toast.tone]}44` }}
              role="status"
            >
              <span
                className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-full text-white"
                style={{ background: toneColor[toast.tone] }}
              >
                {toneIcon[toast.tone]}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[0.82rem] font-bold leading-tight text-white">{toast.title}</p>
                {toast.description && <p className="mt-0.5 text-[0.74rem] leading-snug text-white/55">{toast.description}</p>}
              </div>
              <button
                onClick={() => setToasts((prev) => prev.filter((x) => x.id !== toast.id))}
                className="text-white/35 transition hover:text-white"
                aria-label="Закрыть уведомление"
              >
                <X size={14} />
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastCtx.Provider>
  );
}

/* ───────────────────────── cart ───────────────────────── */

export type CartItem = {
  productId: string;
  quantity: number;
  name: string;
  subtitle: string;
  price: number;
  color: string;
  icon: string;
  kind: ProductKind;
  meta: Record<string, unknown>;
};

export type PromoState = { code: string; discount: number; error?: string } | null;

type CartCtxValue = {
  items: CartItem[];
  count: number;
  subtotal: number;
  discount: number;
  total: number;
  promo: PromoState;
  ready: boolean;
  lastAdded: string | null;
  drawerOpen: boolean;
  add: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  remove: (productId: string) => void;
  setQuantity: (productId: string, quantity: number) => void;
  clear: () => void;
  setDrawerOpen: (open: boolean) => void;
  applyPromo: (code: string) => Promise<void>;
  clearPromo: () => void;
  nickname: string;
  setNickname: (value: string) => void;
  rememberOrder: (publicId: string) => void;
  orderIds: string[];
};

const CartCtx = createContext<CartCtxValue | null>(null);
export function useCart() {
  const ctx = useContext(CartCtx);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}

const CART_KEY = 'kg_cart_v2';
const PROMO_KEY = 'kg_promo_v1';
const NICK_KEY = 'kg_nick_v1';
const ORDERS_KEY = 'kg_orders_v1';

function readJson<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function CartProvider({ children }: { children: ReactNode }) {
  const { push } = useToast();
  const [items, setItems] = useState<CartItem[]>([]);
  const [promo, setPromo] = useState<PromoState>(null);
  const [ready, setReady] = useState(false);
  const [lastAdded, setLastAdded] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [nickname, setNicknameState] = useState('');
  const [orderIds, setOrderIds] = useState<string[]>([]);

  useEffect(() => {
    setItems(readJson<CartItem[]>(CART_KEY, []));
    setPromo(readJson<PromoState>(PROMO_KEY, null));
    setNicknameState(readJson<string>(NICK_KEY, ''));
    setOrderIds(readJson<string[]>(ORDERS_KEY, []));
    setReady(true);
  }, []);

  useEffect(() => {
    if (!ready) return;
    window.localStorage.setItem(CART_KEY, JSON.stringify(items.filter((i) => i.quantity > 0)));
    window.localStorage.setItem(PROMO_KEY, JSON.stringify(promo));
  }, [items, promo, ready]);

  useEffect(() => {
    const open = () => setDrawerOpen(true);
    window.addEventListener('kg:open-cart', open);
    return () => window.removeEventListener('kg:open-cart', open);
  }, []);

  const add = useCallback(
    (item: Omit<CartItem, 'quantity'>, quantity = 1) => {
      setItems((prev) => {
        const existing = prev.find((i) => i.productId === item.productId);
        if (existing) {
          const maxQty = item.kind === 'rank' ? 1 : 20;
          return prev.map((i) =>
            i.productId === item.productId ? { ...i, quantity: Math.min(maxQty, i.quantity + quantity) } : i,
          );
        }
        return [...prev, { ...item, quantity }];
      });
      setLastAdded(item.productId);
      setPromo(null);
      push({
        tone: 'success',
        title: item.kind === 'rank' ? `${item.name} в корзине` : `Добавлено: ${item.name}`,
        description:
          item.kind === 'rank'
            ? 'Привилегия в корзине. Осталось оформить заказ.'
            : `${quantity} шт. · ${new Intl.NumberFormat('ru-RU').format(item.price * quantity)} ₽`,
      });
      setTimeout(() => setLastAdded(null), 900);
    },
    [push],
  );

  const remove = useCallback((productId: string) => {
    setItems((prev) => prev.filter((i) => i.productId !== productId));
    setPromo(null);
  }, []);

  const setQuantity = useCallback((productId: string, quantity: number) => {
    setItems((prev) =>
      prev.map((i) => {
        if (i.productId !== productId) return i;
        const max = i.kind === 'rank' ? 1 : 20;
        return { ...i, quantity: Math.max(0, Math.min(max, quantity)) };
      }),
    );
    setPromo(null);
  }, []);

  const clear = useCallback(() => {
    setItems([]);
    setPromo(null);
  }, []);

  const subtotal = useMemo(() => items.reduce((sum, i) => sum + i.price * i.quantity, 0), [items]);
  const discount = promo?.discount ?? 0;
  const total = Math.max(0, subtotal - discount);
  const count = useMemo(() => items.reduce((sum, i) => sum + i.quantity, 0), [items]);

  const applyPromo = useCallback(
    async (code: string) => {
      const trimmed = code.trim();
      if (!trimmed) return;
      if (items.length === 0) {
        setPromo({ code: trimmed, discount: 0, error: 'Сначала добавьте товары' });
        return;
      }
      try {
        const res = await fetch('/api/price', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            items: items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
            promoCode: trimmed,
          }),
        });
        const data = (await res.json()) as { discount?: number; promoError?: string | null; error?: string };
        if (!res.ok) {
          setPromo({ code: trimmed, discount: 0, error: data.error ?? 'Не удалось проверить промокод' });
          push({ tone: 'error', title: 'Промокод не применён', description: data.error });
          return;
        }
        if (data.promoError) {
          setPromo({ code: trimmed, discount: 0, error: data.promoError });
          push({ tone: 'error', title: 'Промокод не применён', description: data.promoError });
          return;
        }
        setPromo({ code: trimmed, discount: data.discount ?? 0 });
        push({
          tone: 'success',
          title: 'Промокод применён',
          description: `Скидка ${new Intl.NumberFormat('ru-RU').format(data.discount ?? 0)} ₽`,
        });
      } catch {
        setPromo({ code: trimmed, discount: 0, error: 'Сеть недоступна' });
      }
    },
    [items, push],
  );

  const setNickname = useCallback((value: string) => {
    setNicknameState(value);
    window.localStorage.setItem(NICK_KEY, JSON.stringify(value));
  }, []);

  const rememberOrder = useCallback((publicId: string) => {
    setOrderIds((prev) => {
      const next = [publicId, ...prev.filter((id) => id !== publicId)].slice(0, 30);
      window.localStorage.setItem(ORDERS_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const value: CartCtxValue = {
    items, count, subtotal, discount, total, promo, ready, lastAdded, drawerOpen,
    add, remove, setQuantity, clear, setDrawerOpen, applyPromo, clearPromo: () => setPromo(null),
    nickname, setNickname, rememberOrder, orderIds,
  };

  return <CartCtx.Provider value={value}>{children}</CartCtx.Provider>;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <MotionConfig reducedMotion="user">
      <ToastProvider>
        <CartProvider>{children}</CartProvider>
      </ToastProvider>
    </MotionConfig>
  );
}

export function CartCountBubble({ className = '' }: { className?: string }) {
  const { count } = useCart();
  return (
    <AnimatePresence>
      {count > 0 && (
        <motion.span
          key="count"
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          exit={{ scale: 0 }}
          className={`absolute -right-1.5 -top-1.5 grid h-[1.15rem] min-w-[1.15rem] place-items-center rounded-full bg-pink px-1 font-mono text-[0.62rem] font-bold text-white shadow-[0_0_12px_rgba(236,72,153,0.9)] ${className}`}
        >
          {count > 9 ? '9+' : count}
        </motion.span>
      )}
    </AnimatePresence>
  );
}

export function CartIcon() {
  return <ShoppingCart size={18} />;
}
