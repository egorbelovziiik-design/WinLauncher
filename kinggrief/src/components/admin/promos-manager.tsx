'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Pencil, Plus, Ticket, Trash2 } from 'lucide-react';
import type { Promo } from '@/lib/types';
import { formatDate, formatPrice } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, Field, Modal, Pill } from './kit';
import { useToast } from '../providers';

type Draft = {
  code: string;
  type: 'percent' | 'fixed';
  value: number;
  minTotal: number;
  maxDiscount: number | null;
  usageLimit: number | null;
  expiresAt: string;
  active: boolean;
  categories: string;
};

const CATEGORY_OPTIONS = [
  { value: 'rank', label: 'Привилегии' },
  { value: 'case', label: 'Кейсы' },
  { value: 'relic', label: 'Рилики' },
  { value: 'other', label: 'Услуги' },
];

function blank(): Draft {
  const inMonth = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  return { code: '', type: 'percent', value: 10, minTotal: 0, maxDiscount: null, usageLimit: null, expiresAt: inMonth, active: true, categories: '' };
}

function toDraft(promo: Promo): Draft {
  return {
    code: promo.code,
    type: promo.type,
    value: promo.value,
    minTotal: promo.minTotal,
    maxDiscount: promo.maxDiscount,
    usageLimit: promo.usageLimit,
    expiresAt: promo.expiresAt ? promo.expiresAt.slice(0, 10) : '',
    active: promo.active,
    categories: (promo.categories ?? []).join(','),
  };
}

export function PromosManager({ promos }: { promos: Promo[] }) {
  const router = useRouter();
  const { push } = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [toDelete, setToDelete] = useState<Promo | null>(null);

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setError('');
    const res = await adminPost('/api/admin/promos', {
      action: 'save',
      promo: {
        code: draft.code.trim().toUpperCase(),
        type: draft.type,
        value: Number(draft.value),
        minTotal: Number(draft.minTotal),
        maxDiscount: draft.maxDiscount ? Number(draft.maxDiscount) : null,
        usageLimit: draft.usageLimit ? Number(draft.usageLimit) : null,
        categories: draft.categories ? draft.categories.split(',').map((c) => c.trim()).filter(Boolean) : null,
        expiresAt: draft.expiresAt ? new Date(`${draft.expiresAt}T23:59:59`).toISOString() : null,
        active: draft.active,
      },
    });
    setSaving(false);
    if (!res.ok) return setError(res.data.error ?? 'Не удалось сохранить');
    push({ tone: 'success', title: 'Промокод сохранён', description: draft.code.toUpperCase() });
    setDraft(null);
    setEditingCode(null);
    router.refresh();
  };

  const remove = async () => {
    if (!toDelete) return;
    const res = await adminPost('/api/admin/promos', { action: 'delete', code: toDelete.code });
    if (res.ok) {
      push({ tone: 'success', title: 'Промокод удалён', description: toDelete.code });
      setToDelete(null);
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">маркетинг</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Промокоды</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">Скидка проверяется на сервере. Лимиты и сроки соблюдаются автоматически.</p>
        </div>
        <button onClick={() => { setDraft(blank()); setEditingCode(null); }} className="btn btn-primary btn-sm"><Plus size={14} /> Создать промокод</button>
      </div>

      <AdminCard>
        <div className="overflow-x-auto">
          <table className="admin-table min-w-[720px]">
            <thead><tr><th>Код</th><th>Скидка</th><th>Мин. сумма</th><th>Лимит</th><th>Действует до</th><th>Категории</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {promos.map((promo) => {
                const expired = promo.expiresAt ? new Date(promo.expiresAt).getTime() < Date.now() : false;
                const exhausted = promo.usageLimit != null && promo.usedCount >= promo.usageLimit;
                return (
                  <tr key={promo.code}>
                    <td className="font-mono text-[0.76rem] font-bold text-white">{promo.code}</td>
                    <td className="text-white/70">{promo.type === 'percent' ? `${promo.value}%` : formatPrice(promo.value)}{promo.maxDiscount ? ` (макс. ${formatPrice(promo.maxDiscount)})` : ''}</td>
                    <td className="text-white/50">{formatPrice(promo.minTotal)}</td>
                    <td className="font-mono text-[0.66rem] text-white/50">{promo.usedCount} / {promo.usageLimit ?? '∞'}</td>
                    <td className="font-mono text-[0.64rem] text-white/40">{promo.expiresAt ? formatDate(promo.expiresAt) : 'бессрочно'}</td>
                    <td className="text-[0.66rem] text-white/40">{(promo.categories ?? []).join(', ') || 'все'}</td>
                    <td>
                      {expired || exhausted || !promo.active ? (
                        <Pill color="#EF4444">{expired ? 'истёк' : exhausted ? 'лимит' : 'выкл'}</Pill>
                      ) : (
                        <Pill color="#22C55E">активен</Pill>
                      )}
                    </td>
                    <td>
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => { setDraft(toDraft(promo)); setEditingCode(promo.code); }}
                          className="btn btn-ghost btn-icon !h-7 !w-7"
                          aria-label={`Редактировать ${promo.code}`}
                        >
                          <Pencil size={12} />
                        </button>
                        <button onClick={() => setToDelete(promo)} className="btn btn-ghost btn-icon !h-7 !w-7 hover:!text-danger" aria-label={`Удалить ${promo.code}`}>
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {promos.length === 0 && (
            <div className="py-10 text-center">
              <Ticket size={22} className="mx-auto text-white/20" />
              <p className="mt-2 text-[0.76rem] text-white/30">Промокодов нет. Создайте первый.</p>
            </div>
          )}
        </div>
      </AdminCard>

      <p className="text-[0.66rem] leading-relaxed text-white/25">
        Примеры: KING10 — 10% без ограничений; WELCOME — 15% от 300 ₽; GRIEF20 — 20% от 500 ₽ с максимальной скидкой.
        Один промокод применяется к заказу один раз, повторное использование блокируется на сервере.
      </p>

      <Modal
        open={Boolean(draft)}
        onClose={() => { setDraft(null); setEditingCode(null); }}
        title={editingCode ? `Промокод ${editingCode}` : 'Новый промокод'}
        footer={<>
          <button onClick={() => { setDraft(null); setEditingCode(null); }} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={save} disabled={saving} className="btn btn-primary btn-sm">{saving ? '…' : 'Сохранить'}</button>
        </>}
      >
        {draft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Код" hint="Латиница, цифры, - и _. Приводится к верхнему регистру." className="sm:col-span-2">
              <input
                value={draft.code}
                onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })}
                className="field font-mono tracking-widest"
                maxLength={32}
                disabled={Boolean(editingCode)}
              />
            </Field>
            <Field label="Тип скидки">
              <select value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value as Draft['type'] })} className="field">
                <option value="percent" className="bg-panel">Процент</option>
                <option value="fixed" className="bg-panel">Фиксированная сумма</option>
              </select>
            </Field>
            <Field label={draft.type === 'percent' ? 'Размер, %' : 'Размер, ₽'}>
              <input type="number" min={1} value={draft.value} onChange={(e) => setDraft({ ...draft, value: Number(e.target.value) })} className="field" />
            </Field>
            <Field label="Минимальная сумма, ₽">
              <input type="number" min={0} value={draft.minTotal} onChange={(e) => setDraft({ ...draft, minTotal: Number(e.target.value) })} className="field" />
            </Field>
            <Field label="Максимальная скидка, ₽" hint="Пусто — без ограничения.">
              <input type="number" min={0} value={draft.maxDiscount ?? ''} onChange={(e) => setDraft({ ...draft, maxDiscount: e.target.value ? Number(e.target.value) : null })} className="field" />
            </Field>
            <Field label="Лимит использований" hint="Пусто — без ограничения.">
              <input type="number" min={1} value={draft.usageLimit ?? ''} onChange={(e) => setDraft({ ...draft, usageLimit: e.target.value ? Number(e.target.value) : null })} className="field" />
            </Field>
            <Field label="Действует до" hint="Пусто — бессрочно.">
              <input type="date" value={draft.expiresAt} onChange={(e) => setDraft({ ...draft, expiresAt: e.target.value })} className="field" />
            </Field>
            <Field label="Категории" hint="Через запятую: rank, case, relic, other. Пусто — все." className="sm:col-span-2">
              <input value={draft.categories} onChange={(e) => setDraft({ ...draft, categories: e.target.value })} className="field font-mono text-xs" placeholder="rank,relic" />
            </Field>
            <div className="flex flex-wrap gap-1.5 sm:col-span-2">
              {CATEGORY_OPTIONS.map((cat) => (
                <button
                  key={cat.value}
                  type="button"
                  onClick={() => {
                    const list = draft.categories ? draft.categories.split(',') : [];
                    const next = list.includes(cat.value) ? list.filter((c) => c !== cat.value) : [...list, cat.value];
                    setDraft({ ...draft, categories: next.join(',') });
                  }}
                  className={`rounded border px-2 py-1 text-[0.64rem] font-semibold ${draft.categories.split(',').includes(cat.value) ? 'border-neon/50 bg-neon/15 text-white' : 'border-white/10 text-white/40'}`}
                >
                  {cat.label}
                </button>
              ))}
            </div>
            <label className="flex items-center gap-2 sm:col-span-2">
              <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} className="h-4 w-4 accent-[#a855f7]" />
              <span className="text-[0.76rem] text-white/60">Промокод активен</span>
            </label>
            {error && <p className="sm:col-span-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.72rem] text-[#fca5a5]">{error}</p>}
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(toDelete)}
        onClose={() => setToDelete(null)}
        title="Удалить промокод?"
        footer={<>
          <button onClick={() => setToDelete(null)} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={remove} className="btn btn-sm" style={{ background: '#ef4444', color: '#fff' }}>Удалить</button>
        </>}
      >
        <p className="text-[0.8rem] text-white/55">Код {toDelete?.code} перестанет работать. История использований останется в аналитике.</p>
      </Modal>
    </div>
  );
}
