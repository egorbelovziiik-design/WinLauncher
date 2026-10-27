'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Check, Download, ExternalLink, RefreshCw, Search, XCircle } from 'lucide-react';
import type { Order, OrderStatus } from '@/lib/types';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, Pill } from './kit';
import { useToast } from '../providers';

const STATUSES: OrderStatus[] = ['pending_payment', 'paid', 'delivering', 'delivered', 'cancelled', 'failed'];

export function OrdersManager({
  orders,
  initialQuery = '',
  claims = {},
}: {
  orders: Order[];
  initialQuery?: string;
  claims?: Record<string, boolean>;
}) {
  const router = useRouter();
  const { push } = useToast();
  const [status, setStatus] = useState<string>('all');
  const [query, setQuery] = useState(initialQuery);
  const [busyId, setBusyId] = useState<string | null>(null);

  const filtered = useMemo(
    () =>
      orders
        .filter((o) => (status === 'all' ? true : o.status === status))
        .filter((o) =>
          query.trim()
            ? `${o.publicId} ${o.nickname} ${o.email ?? ''}`.toLowerCase().includes(query.toLowerCase())
            : true,
        ),
    [orders, status, query],
  );

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: orders.length };
    STATUSES.forEach((s) => (map[s] = orders.filter((o) => o.status === s).length));
    return map;
  }, [orders]);

  const changeStatus = async (order: Order, next: string) => {
    setBusyId(order.id);
    const res = await adminPost('/api/admin/orders', { action: 'status', orderId: order.id, status: next });
    setBusyId(null);
    if (res.ok) {
      push({ tone: 'success', title: 'Статус обновлён', description: `${order.publicId} → ${statusLabels[next] ?? next}` });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  const confirmPayment = async (order: Order) => {
    setBusyId(order.id);
    const res = await adminPost('/api/admin/orders', { action: 'confirm-payment', orderId: order.id });
    setBusyId(null);
    if (res.ok) {
      push({ tone: 'success', title: 'Оплата подтверждена', description: `${order.publicId} → задачи выдачи созданы` });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  const markDelivered = async (order: Order) => {
    setBusyId(order.id);
    const res = await adminPost('/api/admin/orders', { action: 'mark-delivered', orderId: order.id });
    setBusyId(null);
    if (res.ok) {
      push({ tone: 'success', title: 'Заказ помечен выданным', description: order.publicId });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">продажи</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Заказы</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">Оплата, выдача и история. Демо-заказы помечены.</p>
        </div>
        <div className="flex gap-2">
          <a href="/api/admin/export" className="btn btn-ghost btn-sm"><Download size={13} /> CSV</a>
          <button onClick={() => router.refresh()} className="btn btn-primary btn-sm"><RefreshCw size={13} /> Обновить</button>
        </div>
      </div>

      <div className="scrollbar-none -mx-1 flex gap-1 overflow-x-auto px-1">
        {[{ key: 'all', label: 'Все' }, ...STATUSES.map((s) => ({ key: s, label: statusLabels[s] }))].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setStatus(tab.key)}
            className={`shrink-0 rounded-lg border px-2.5 py-1.5 text-[0.7rem] font-semibold transition ${
              status === tab.key ? 'border-neon/50 bg-neon/15 text-white' : 'border-white/8 bg-white/[0.02] text-white/45 hover:text-white'
            }`}
          >
            {tab.label} <span className="ml-1 font-mono text-[0.56rem] text-white/30">{counts[tab.key] ?? 0}</span>
          </button>
        ))}
      </div>

      <AdminCard>
        <label className="relative mb-3 block sm:max-w-xs">
          <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-white/25" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Номер заказа, никнейм, email" className="field h-9 pl-8 text-[0.76rem]" />
        </label>

        <div className="overflow-x-auto">
          <table className="admin-table min-w-[860px]">
            <thead><tr><th>Заказ</th><th>Игрок</th><th>Состав</th><th>Сумма</th><th>Оплата</th><th>Статус</th><th>Дата</th><th /></tr></thead>
            <tbody>
              {filtered.map((order) => (
                <tr key={order.id}>
                  <td>
                    <Link href={`/admin/orders/${order.publicId}`} className="font-mono text-[0.7rem] text-neon hover:underline">{order.publicId}</Link>
                    {order.isDemo && <span className="ml-1.5"><Pill color="#F59E0B">demo</Pill></span>}
                  </td>
                  <td className="text-white/75">{order.nickname}<span className="block font-mono text-[0.56rem] text-white/25">{order.email ?? '—'}</span></td>
                  <td className="max-w-[15rem] truncate text-[0.7rem] text-white/45">{order.items.map((i) => `${i.name} ×${i.quantity}`).join(', ')}</td>
                  <td className="stat-num text-white/85">{formatPrice(order.total)}</td>
                  <td className="font-mono text-[0.6rem] uppercase text-white/40">
                    {order.provider.startsWith('card2card:') ? (order.provider.endsWith('visa_eu') ? 'Visa Europe' : order.provider.endsWith('sber_ru') ? 'Сбербанк' : 'Visa UA') : order.provider}
                    {claims[order.id] && order.status === 'pending_payment' && (
                      <span className="mt-0.5 block"><Pill color="#F59E0B">заявка: «я оплатил(а)»</Pill></span>
                    )}
                  </td>
                  <td>
                    <select
                      value={order.status}
                      onChange={(e) => changeStatus(order, e.target.value)}
                      disabled={busyId === order.id}
                      className="rounded-md border border-white/10 bg-void px-1.5 py-1 font-mono text-[0.62rem] uppercase tracking-wide outline-none"
                      style={{ color: statusColors[order.status] }}
                      aria-label={`Статус заказа ${order.publicId}`}
                    >
                      {STATUSES.map((s) => <option key={s} value={s} className="bg-panel text-white">{statusLabels[s]}</option>)}
                    </select>
                  </td>
                  <td className="font-mono text-[0.6rem] text-white/30">{formatDate(order.createdAt)}</td>
                  <td>
                    <div className="flex flex-wrap justify-end gap-1">
                      {order.status === 'pending_payment' && (
                        <button
                          onClick={() => confirmPayment(order)}
                          disabled={busyId === order.id}
                          className="btn btn-primary !h-7 !px-2 !text-[0.62rem]"
                          title="Подтвердить поступление и создать задачи выдачи"
                        >
                          <Check size={12} /> Подтвердить оплату
                        </button>
                      )}
                      {(order.status === 'paid' || order.status === 'delivering' || order.status === 'failed') && (
                        <button onClick={() => markDelivered(order)} disabled={busyId === order.id} className="btn btn-ghost !h-7 !px-2 !text-[0.62rem]">
                          Выдан
                        </button>
                      )}
                      {order.status !== 'cancelled' && order.status !== 'delivered' && (
                        <button
                          onClick={() => changeStatus(order, 'cancelled')}
                          disabled={busyId === order.id}
                          className="btn btn-ghost !h-7 !px-2 !text-[0.62rem] hover:!text-danger"
                          title="Отменить заказ"
                        >
                          <XCircle size={12} /> Отменить
                        </button>
                      )}
                      <Link href={`/admin/orders/${order.publicId}`} className="btn btn-ghost btn-icon !h-7 !w-7" aria-label="Открыть заказ">
                        <ExternalLink size={12} />
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filtered.length === 0 && <p className="py-10 text-center text-[0.76rem] text-white/30">Заказов с такими параметрами нет.</p>}
        </div>
      </AdminCard>
    </div>
  );
}
