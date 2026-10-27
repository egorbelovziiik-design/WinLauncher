'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Check, RefreshCw, RotateCcw, X } from 'lucide-react';
import type { Delivery, Order, OrderStatus } from '@/lib/types';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, Pill } from './kit';
import { useToast } from '../providers';

const STATUSES: OrderStatus[] = ['pending_payment', 'paid', 'delivering', 'delivered', 'cancelled', 'failed'];

type PaymentRow = { id: number; provider: string; providerPaymentId: string | null; amount: number; status: string; createdAt: string };

export function OrderDetail({
  order,
  deliveries,
  payments,
  claim = false,
}: {
  order: Order;
  deliveries: Delivery[];
  payments: PaymentRow[];
  claim?: boolean;
}) {
  const router = useRouter();
  const { push } = useToast();
  const [busy, setBusy] = useState<string | null>(null);

  const run = async (body: Record<string, unknown>, message: string) => {
    setBusy(String(body.deliveryId ?? body.action));
    const res = await adminPost('/api/admin/orders', { ...body, orderId: order.id });
    setBusy(null);
    if (res.ok) {
      push({ tone: 'success', title: message });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  const deliveryAction = async (delivery: Delivery, action: 'deliver' | 'retry' | 'fail' | 'repeat') => {
    setBusy(delivery.id);
    const res = await adminPost('/api/admin/deliveries', { id: delivery.id, action });
    setBusy(null);
    if (res.ok) {
      push({
        tone: 'success',
        title:
          action === 'repeat'
            ? `Отправлено повторно игроку ${delivery.nickname}`
            : action === 'retry'
              ? 'Задача возвращена в очередь'
              : action === 'deliver'
                ? 'Помечено выданным'
                : 'Помечено ошибкой',
      });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[1.4fr_0.9fr]">
      <div className="space-y-4">
        <AdminCard title="Состав заказа" description={`${order.items.length} позиций · создан ${formatDate(order.createdAt)}`}>
          <ul className="divide-y divide-white/6">
            {order.items.map((item) => (
              <li key={item.productId} className="flex items-center justify-between gap-3 py-2.5">
                <div>
                  <p className="text-[0.8rem] font-semibold text-white/85">{item.name}</p>
                  <p className="font-mono text-[0.6rem] text-white/30">{item.productId} · {item.quantity} × {formatPrice(item.unitPrice)}</p>
                </div>
                <span className="stat-num text-[0.82rem] font-bold text-white">{formatPrice(item.total)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-3 space-y-1 border-t border-white/6 pt-3 text-[0.76rem]">
            <div className="flex justify-between text-white/45"><dt>Товары</dt><dd>{formatPrice(order.subtotal)}</dd></div>
            {order.discount > 0 && (
              <div className="flex justify-between text-emerald"><dt>Скидка {order.promoCode}</dt><dd>−{formatPrice(order.discount)}</dd></div>
            )}
            <div className="flex justify-between font-bold text-white"><dt>Итого</dt><dd className="stat-num">{formatPrice(order.total)}</dd></div>
          </dl>
        </AdminCard>

        <AdminCard
          title="Задачи выдачи"
          description="Очередь для плагина KingGriefBridge. Повторная выдача защищена уникальностью задачи."
          action={<Pill color={deliveries.some((d) => d.status !== 'delivered') ? '#F59E0B' : '#22C55E'}>{deliveries.filter((d) => d.status === 'delivered').length}/{deliveries.length} выдано</Pill>}
        >
          {deliveries.length === 0 ? (
            <p className="py-4 text-center text-[0.74rem] text-white/30">Задачи появятся после подтверждения оплаты.</p>
          ) : (
            <ul className="space-y-2">
              {deliveries.map((delivery) => (
                <li key={delivery.id} className="rounded-lg border border-white/6 bg-void/40 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <code className="min-w-0 flex-1 truncate font-mono text-[0.7rem] text-neon/90">{delivery.command}</code>
                    <div className="flex items-center gap-1.5">
                      <Pill color={statusColors[delivery.status] ?? '#94A3B8'}>{statusLabels[delivery.status] ?? delivery.status}</Pill>
                      <button
                        onClick={() => deliveryAction(delivery, 'repeat')}
                        disabled={busy === delivery.id}
                        className="rounded border border-neon/40 bg-neon/10 px-2 py-0.5 text-[0.62rem] font-semibold text-neon hover:bg-neon/25"
                        title="Создать новую задачу и выдать ещё раз"
                      >
                        ↻ Ещё раз
                      </button>
                      {delivery.status !== 'delivered' && delivery.status !== 'pending' && delivery.status !== 'sent' && (
                        <button onClick={() => deliveryAction(delivery, 'retry')} disabled={busy === delivery.id} className="btn btn-ghost btn-icon !h-6 !w-6" title="Вернуть в очередь" aria-label="Повторить"><RotateCcw size={11} /></button>
                      )}
                      {delivery.status === 'manual' && (
                        <button onClick={() => deliveryAction(delivery, 'deliver')} disabled={busy === delivery.id} className="btn btn-ghost btn-icon !h-6 !w-6" title="Подтвердить ручную выдачу" aria-label="Пометить выданным"><Check size={11} /></button>
                      )}
                      <button onClick={() => deliveryAction(delivery, 'fail')} disabled={busy === delivery.id} className="btn btn-ghost btn-icon !h-6 !w-6 hover:!text-danger" title="Отметить ошибкой" aria-label="Ошибка"><X size={11} /></button>
                    </div>
                  </div>
                  <p className="mt-1 font-mono text-[0.58rem] text-white/25">
                    попыток: {delivery.attempts} · создана {formatDate(delivery.createdAt)}
                    {delivery.deliveredAt ? ` · выдана ${formatDate(delivery.deliveredAt)}` : ''}
                  </p>
                  {delivery.lastError && <p className="mt-1 text-[0.64rem] text-[#fca5a5]">{delivery.lastError}</p>}
                </li>
              ))}
            </ul>
          )}
          <p className="mt-3 text-[0.64rem] leading-relaxed text-white/25">
            Если мост не подключён, задачи находятся в статусе «Вручную». После подключения плагина нажмите «Повторить» —
            команда уйдёт в очередь.
          </p>
        </AdminCard>

        <AdminCard title="Платежи" description="Журнал попыток оплаты по заказу">
          {payments.length === 0 ? (
            <p className="py-3 text-center text-[0.74rem] text-white/30">Платежей нет.</p>
          ) : (
            <ul className="space-y-2">
              {payments.map((payment) => (
                <li key={payment.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/6 bg-void/30 px-3 py-2 text-[0.72rem]">
                  <span className="font-mono text-white/50">#{payment.id} · {payment.provider}</span>
                  <span className="stat-num text-white/75">{formatPrice(payment.amount)}</span>
                  <Pill color={payment.status === 'succeeded' ? '#22C55E' : payment.status === 'failed' ? '#EF4444' : '#F59E0B'}>
                    {payment.status === 'succeeded' ? 'успешен' : payment.status === 'sent' ? 'заявка: переведён' : payment.status === 'failed' ? 'ошибка' : payment.status}
                  </Pill>
                  <span className="font-mono text-[0.58rem] text-white/25">{formatDate(payment.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </AdminCard>
      </div>

      <aside className="space-y-4">
        <AdminCard
          title="Управление"
          action={claim && order.status === 'pending_payment' ? <Pill color="#F59E0B">заявка: «я оплатил(а)»</Pill> : undefined}
        >
          {(order.status === 'pending_payment' || claim) && order.status !== 'delivered' && (
            <button
              onClick={() => run({ action: 'confirm-payment' }, 'Оплата подтверждена, задачи выдачи созданы')}
              disabled={busy === 'confirm-payment'}
              className="btn btn-primary btn-sm w-full"
            >
              <Check size={13} /> Подтвердить оплату и выдать
            </button>
          )}
          {order.status !== 'cancelled' && order.status !== 'delivered' && (
            <button
              onClick={() => run({ action: 'status', status: 'cancelled' }, 'Заказ отменён')}
              disabled={busy === 'status'}
              className="btn btn-ghost btn-sm mt-2 w-full hover:!text-danger"
            >
              <X size={13} /> Отменить заказ
            </button>
          )}
          <dl className="space-y-2 text-[0.76rem] mt-3">
            <div className="flex justify-between gap-2"><dt className="text-white/40">Номер</dt><dd className="font-mono text-white/80">{order.publicId}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Никнейм</dt><dd className="font-bold text-white">{order.nickname}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Email</dt><dd className="truncate text-white/60">{order.email ?? '—'}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Провайдер</dt><dd className="font-mono text-white/70">{order.provider}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Демо</dt><dd>{order.isDemo ? <Pill color="#F59E0B">да</Pill> : <Pill color="#22C55E">нет</Pill>}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Оплачен</dt><dd className="font-mono text-[0.62rem] text-white/60">{order.paidAt ? formatDate(order.paidAt) : '—'}</dd></div>
            <div className="flex justify-between gap-2"><dt className="text-white/40">Выдан</dt><dd className="font-mono text-[0.62rem] text-white/60">{order.deliveredAt ? formatDate(order.deliveredAt) : '—'}</dd></div>
          </dl>

          <label className="mt-4 block">
            <span className="field-label">Статус заказа</span>
            <select
              value={order.status}
              onChange={(e) => run({ action: 'status', status: e.target.value }, 'Статус обновлён')}
              className="field h-9 text-[0.76rem]"
              disabled={busy === 'status'}
            >
              {STATUSES.map((s) => <option key={s} value={s} className="bg-panel">{statusLabels[s]}</option>)}
            </select>
          </label>

          <button onClick={() => run({ action: 'mark-delivered' }, 'Заказ помечен выданным')} className="btn btn-primary btn-sm mt-3 w-full">
            <Check size={13} /> Отметить всё выданным
          </button>

          {order.note && (
            <p className="mt-3 rounded-lg border border-white/8 bg-void/40 px-2.5 py-2 text-[0.68rem] text-white/45">Комментарий: {order.note}</p>
          )}
        </AdminCard>

        <AdminCard title="Выдача вручную">
          <p className="text-[0.7rem] leading-relaxed text-white/40">
            Скопируйте команды из списка задач и выполните их от имени консоли сервера. После этого отметьте задачи
            выданными — заказ закроется автоматически.
          </p>
          <button onClick={() => router.refresh()} className="btn btn-ghost btn-sm mt-3 w-full"><RefreshCw size={12} /> Обновить данные</button>
        </AdminCard>
      </aside>
    </div>
  );
}
