'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Search, User, Package, Loader2 } from 'lucide-react';
import type { Order } from '@/lib/types';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { useCart, useToast } from './providers';
import { EmptyState } from './ui';

type PublicOrder = Pick<Order, 'publicId' | 'nickname' | 'status' | 'total' | 'createdAt' | 'items'>;

export function ProfileClient() {
  const { nickname, setNickname, orderIds, ready } = useCart();
  const { push } = useToast();
  const [nick, setNick] = useState(nickname);
  const [query, setQuery] = useState('');
  const [orders, setOrders] = useState<PublicOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);

  const load = useCallback(async (ids: string[]) => {
    if (ids.length === 0) {
      setOrders([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    const results = await Promise.all(
      ids.map(async (id) => {
        try {
          const res = await fetch(`/api/orders/${id}`, { cache: 'no-store' });
          if (!res.ok) return null;
          return (await res.json()) as PublicOrder;
        } catch {
          return null;
        }
      }),
    );
    setOrders(results.filter((o): o is PublicOrder => Boolean(o)));
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!ready) return;
    load(orderIds);
  }, [ready, orderIds, load]);

  const search = async (e: React.FormEvent) => {
    e.preventDefault();
    const id = query.trim().toUpperCase();
    if (!id) return;
    setSearching(true);
    try {
      const res = await fetch(`/api/orders/${id}`, { cache: 'no-store' });
      if (!res.ok) {
        push({ tone: 'error', title: 'Заказ не найден', description: 'Проверьте номер вида KG-XXXXX.' });
        return;
      }
      const order = (await res.json()) as PublicOrder;
      setOrders((prev) => [order, ...prev.filter((o) => o.publicId !== order.publicId)]);
      push({ tone: 'success', title: `Заказ ${order.publicId}`, description: statusLabels[order.status] });
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
      <aside className="space-y-4">
        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <div className="flex items-center gap-3">
            <span className="grid h-12 w-12 place-items-center rounded-xl bg-gradient-to-br from-violet to-neon text-white">
              <User size={22} />
            </span>
            <div>
              <p className="font-display text-base font-bold text-white">Мой профиль</p>
              <p className="font-mono text-[0.58rem] uppercase tracking-widest text-white/30">локально в браузере</p>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              setNickname(nick.trim());
              push({ tone: 'success', title: 'Никнейм сохранён', description: 'Он будет подставлен при оформлении заказа.' });
            }}
            className="mt-5"
          >
            <label className="block">
              <span className="field-label">Minecraft-никнейм</span>
              <input
                value={nick}
                onChange={(e) => setNick(e.target.value)}
                placeholder="Steve"
                maxLength={16}
                className="field"
              />
            </label>
            <button className="btn btn-primary btn-sm mt-3 w-full">Сохранить</button>
          </form>

          <p className="mt-3 text-[0.66rem] leading-relaxed text-white/30">
            Мы не создаём пароль для магазина: покупки привязаны к никнейму в игре, а история заказов хранится по номерам.
            Не очищайте данные браузера, если хотите сохранить список.
          </p>
        </div>

        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <p className="text-[0.78rem] font-bold text-white">Найти заказ по номеру</p>
          <form onSubmit={search} className="mt-2 flex gap-2">
            <div className="relative flex-1">
              <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-white/25" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value.toUpperCase())}
                placeholder="KG-XXXXX"
                className="field h-10 pl-8 font-mono text-xs tracking-wide"
                aria-label="Номер заказа"
              />
            </div>
            <button className="btn btn-ghost btn-sm !h-10" disabled={searching || !query.trim()}>
              {searching ? <Loader2 size={13} className="animate-spin" /> : 'Найти'}
            </button>
          </form>
          <p className="mt-2 text-[0.64rem] text-white/25">Номер приходит при создании заказа и есть в чеке.</p>
        </div>
      </aside>

      <div>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-display text-lg font-bold uppercase tracking-tight text-white">Мои заказы</h2>
          <span className="font-mono text-[0.6rem] text-white/25">{orders.length} шт.</span>
        </div>

        {loading ? (
          <div className="space-y-3">
            {[0, 1, 2].map((i) => <div key={i} className="skeleton h-24 rounded-xl" />)}
          </div>
        ) : orders.length === 0 ? (
          <EmptyState
            icon={<Package size={24} />}
            title="Заказов пока нет"
            description="Купите что-нибудь или найдите заказ по номеру — история появится здесь."
            action={<Link href="/privileges" className="btn btn-primary">В магазин</Link>}
          />
        ) : (
          <ul className="space-y-3">
            {orders.map((order) => (
              <li key={order.publicId} className="rounded-2xl border border-white/8 bg-panel/40 p-4 transition hover:border-neon/25">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-display text-sm font-bold text-white">{order.publicId}</p>
                    <p className="mt-0.5 text-[0.68rem] text-white/35">
                      {order.nickname} · {formatDate(order.createdAt)}
                    </p>
                  </div>
                  <span
                    className="rounded-full px-2 py-0.5 font-mono text-[0.56rem] font-bold uppercase tracking-widest"
                    style={{ background: `${statusColors[order.status]}1f`, color: statusColors[order.status] }}
                  >
                    {statusLabels[order.status] ?? order.status}
                  </span>
                </div>
                <ul className="mt-2 flex flex-wrap gap-1.5">
                  {order.items.map((item) => (
                    <li key={item.productId} className="rounded bg-white/5 px-1.5 py-0.5 text-[0.66rem] text-white/50">
                      {item.name} ×{item.quantity}
                    </li>
                  ))}
                </ul>
                <div className="mt-3 flex items-center justify-between border-t border-white/6 pt-2.5">
                  <span className="stat-num text-sm font-bold text-white">{formatPrice(order.total)}</span>
                  <Link href={`/checkout/${order.publicId}`} className="text-[0.7rem] font-semibold text-neon hover:underline">
                    Открыть заказ →
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
