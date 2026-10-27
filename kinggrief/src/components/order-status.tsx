'use client';

import { useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { motion } from 'framer-motion';
import { Check, CircleDollarSign, Clock, ExternalLink, Loader2, RefreshCw, ShieldCheck, Truck } from 'lucide-react';
import type { Order } from '@/lib/types';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { useToast } from './providers';

const STEPS = [
  { key: 'created', label: 'Заказ создан', icon: Clock },
  { key: 'paid', label: 'Оплачен', icon: CircleDollarSign },
  { key: 'delivering', label: 'Выдача', icon: Truck },
  { key: 'delivered', label: 'Выдан', icon: Check },
] as const;

function stepIndex(status: Order['status']) {
  switch (status) {
    case 'pending_payment': return 0;
    case 'paid': return 2;
    case 'delivering': return 2;
    case 'delivered': return 4;
    case 'cancelled':
    case 'failed': return -1;
    default: return 0;
  }
}

export function OrderStatusView({ initialOrder }: { initialOrder: Order }) {
  const [order, setOrder] = useState(initialOrder);
  const [checking, setChecking] = useState(false);
  const [paying, setPaying] = useState(false);
  const [isPending, startTransition] = useTransition();
  const router = useRouter();
  const { push } = useToast();

  const active = stepIndex(order.status);
  const pending = order.status === 'pending_payment';
  const waitingDelivery = order.status === 'paid' || order.status === 'delivering';

  useEffect(() => {
    if (!pending && !waitingDelivery) return;
    const timer = setInterval(async () => {
      try {
        const res = await fetch(`/api/orders/${order.publicId}`, { cache: 'no-store' });
        if (!res.ok) return;
        const data = (await res.json()) as Partial<Order>;
        if (data.status && data.status !== order.status) {
          setOrder((prev) => ({ ...prev, ...data } as Order));
          if (order.status === 'pending_payment' && data.status !== 'pending_payment') {
            push({ tone: 'success', title: 'Оплата подтверждена!', description: 'Заказ передан на выдачу в игру.' });
          } else if (data.status === 'delivered') {
            push({ tone: 'success', title: 'Выдано на сервере!', description: `Донат доставлен игроку ${order.nickname}.` });
          }
          startTransition(() => router.refresh());
        }
      } catch {
        /* ignore */
      }
    }, 3500);
    return () => clearInterval(timer);
  }, [order.nickname, order.publicId, order.status, pending, waitingDelivery, push, router]);

  const check = async () => {
    setChecking(true);
    try {
      const res = await fetch('/api/payments/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ publicId: order.publicId }),
      });
      const data = (await res.json()) as { paid?: boolean; status?: string; error?: string };
      if (data.paid) {
        push({ tone: 'success', title: 'Оплата найдена', description: 'Заказ передан на выдачу.' });
        startTransition(() => router.refresh());
        setOrder((prev) => ({ ...prev, status: (data.status as Order['status']) ?? 'paid' }));
      } else {
        push({ tone: 'info', title: 'Оплата пока не подтверждена', description: data.error ?? 'Обновите страницу через минуту.' });
      }
    } finally {
      setChecking(false);
    }
  };

  const demoPay = async () => {
    setPaying(true);
    try {
      const res = await fetch('/api/payments/mock/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ publicId: order.publicId }),
      });
      const data = (await res.json()) as { ok?: boolean; error?: string };
      if (res.ok) {
        push({ tone: 'success', title: 'Демо-оплата прошла', description: 'Заказ помечен оплаченным (без реальных денег).' });
        setOrder((prev) => ({ ...prev, status: 'paid' }));
        startTransition(() => router.refresh());
      } else {
        push({ tone: 'error', title: 'Не удалось', description: data.error });
      }
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
      <div className="space-y-4">
        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-white/30">заказ</p>
              <p className="font-display text-xl font-extrabold text-white">{order.publicId}</p>
              <p className="mt-1 text-[0.72rem] text-white/35">создан {formatDate(order.createdAt)}</p>
            </div>
            <span
              className="rounded-full px-2.5 py-1 font-mono text-[0.6rem] font-bold uppercase tracking-widest"
              style={{ background: `${statusColors[order.status]}1f`, color: statusColors[order.status] }}
            >
              {statusLabels[order.status] ?? order.status}
            </span>
          </div>

          <ol className="mt-6 grid grid-cols-4 gap-1.5">
            {STEPS.map((step, i) => {
              const done = active >= 0 && i < active;
              const current = active === i;
              return (
                <li key={step.key} className="relative">
                  <div className={`h-1 rounded-full ${done || (active >= 4) ? 'bg-emerald' : current ? 'bg-neon' : 'bg-white/8'}`} />
                  <div className="mt-2 flex items-center gap-1.5">
                    <step.icon size={12} className={done || active >= 4 ? 'text-emerald' : current ? 'text-neon' : 'text-white/20'} />
                    <span className={`text-[0.62rem] font-semibold ${done || active >= 4 ? 'text-white/70' : current ? 'text-white' : 'text-white/25'}`}>
                      {step.label}
                    </span>
                  </div>
                </li>
              );
            })}
          </ol>

          {(order.status === 'cancelled' || order.status === 'failed') && (
            <p className="mt-4 rounded-lg border border-danger/25 bg-danger/8 px-3 py-2 text-[0.72rem] text-[#fca5a5]">
              Платёж не был подтверждён. Если деньги списались — напишите в поддержку с номером заказа.
            </p>
          )}

          {pending && (
            <div className="mt-5 flex flex-col gap-2 border-t border-white/6 pt-4 sm:flex-row">
              {order.paymentUrl && /^https?:\/\//i.test(order.paymentUrl) && (
                <a href={order.paymentUrl} className="btn btn-primary flex-1" rel="noreferrer noopener">
                  Перейти к оплате <ExternalLink size={14} />
                </a>
              )}
              {order.isDemo && order.provider === 'mock' && (
                <button onClick={demoPay} disabled={paying} className="btn btn-gold flex-1">
                  {paying ? <Loader2 size={14} className="animate-spin" /> : <ShieldCheck size={14} />}
                  Оплатить демо
                </button>
              )}
              <button onClick={check} disabled={checking || isPending} className="btn btn-ghost flex-1">
                {checking || isPending ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
                Я оплатил
              </button>
            </div>
          )}

          {!pending && (
            <div className="mt-5 flex flex-wrap gap-2 border-t border-white/6 pt-4">
              <Link href="/cases" className="btn btn-primary btn-sm flex-1">
                Купить ещё кейсы
              </Link>
              <Link href="/privileges" className="btn btn-ghost btn-sm flex-1">
                Купить привилегию
              </Link>
              <button onClick={check} disabled={checking || isPending} className="btn btn-ghost btn-sm">
                {checking || isPending ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />}
                Обновить
              </button>
            </div>
          )}

          {order.isDemo && (
            <p className="mt-3 flex items-start gap-1.5 text-[0.64rem] leading-relaxed text-amber/75">
              <ShieldCheck size={11} className="mt-0.5 shrink-0" />
              Это демонстрационный заказ: оплата не списывает реальные деньги. Статус «выдан» означает, что задача выдачи
              создана, а не что товар уже в игре.
            </p>
          )}
        </div>

        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Состав заказа</h2>
          <ul className="mt-3 divide-y divide-white/6">
            {order.items.map((item) => (
              <li key={item.productId} className="flex items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-[0.82rem] font-semibold text-white/85">{item.name}</p>
                  <p className="font-mono text-[0.6rem] text-white/30">
                    {item.quantity} × {formatPrice(item.unitPrice)} · {item.kind === 'rank' ? 'привилегия' : item.kind === 'case' ? 'кейс' : item.kind === 'relic' ? 'рилики' : 'услуга'}
                  </p>
                </div>
                <span className="stat-num text-sm font-bold text-white/75">{formatPrice(item.total)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-3 space-y-1.5 border-t border-white/6 pt-3 text-[0.78rem]">
            {order.discount > 0 && (
              <div className="flex justify-between text-emerald">
                <dt>Скидка {order.promoCode ? `(${order.promoCode})` : ''}</dt>
                <dd className="stat-num">−{formatPrice(order.discount)}</dd>
              </div>
            )}
            <div className="flex justify-between font-bold text-white">
              <dt>Итого</dt><dd className="stat-num">{formatPrice(order.total)}</dd>
            </div>
          </dl>
        </div>
      </div>

      <aside className="space-y-4">
        <div className="rounded-2xl border border-white/8 bg-gradient-to-b from-[#181530] to-[#0c0b16] p-5">
          <p className="font-mono text-[0.56rem] uppercase tracking-[0.2em] text-white/30">получатель</p>
          <p className="mt-1 font-display text-lg font-bold text-white">{order.nickname}</p>
          {order.email && <p className="text-[0.72rem] text-white/35">{order.email}</p>}
          <div className="mt-4 space-y-2 border-t border-white/6 pt-4 text-[0.72rem]">
            <p className="flex items-center justify-between text-white/40">
              <span>Способ оплаты</span>
              <span className="font-semibold text-white/70">{order.provider === 'mock' ? 'демо' : order.provider === 'yookassa' ? 'ЮKassa' : order.provider.startsWith('card2card:') ? (order.provider.endsWith('visa_eu') ? 'Visa Europe' : order.provider.endsWith('sber_ru') ? 'Сбербанк' : 'Visa Украина') : 'Robokassa'}</span>
            </p>
            <p className="flex items-center justify-between text-white/40">
              <span>Оплачен</span>
              <span className="font-semibold text-white/70">{order.paidAt ? formatDate(order.paidAt) : '—'}</span>
            </p>
            <p className="flex items-center justify-between text-white/40">
              <span>Выдан</span>
              <span className="font-semibold text-white/70">{order.deliveredAt ? formatDate(order.deliveredAt) : '—'}</span>
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <p className="text-[0.78rem] font-bold text-white">Нужна помощь?</p>
          <p className="mt-1 text-[0.72rem] leading-relaxed text-white/40">
            Напишите в поддержку и укажите номер заказа {order.publicId}. Оператор увидит статус выдачи.
          </p>
          <div className="mt-3 flex gap-2">
            <Link href="/support" className="btn btn-ghost btn-sm flex-1">Поддержка</Link>
            <Link href="/profile" className="btn btn-ghost btn-sm flex-1">Мои заказы</Link>
          </div>
        </div>

        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-center">
          <Link href="/privileges" className="text-[0.7rem] font-semibold text-white/35 hover:text-white">
            Вернуться в магазин →
          </Link>
        </motion.div>
      </aside>
    </div>
  );
}
