'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Gamepad2, Pencil, Plus, Ticket, Trash2 } from 'lucide-react';
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
  serverEnabled: boolean;
  serverCoins: number;
  serverRelics: number;
  serverCaseId: string;
  serverCaseQty: number;
  serverRankId: string;
};

const CATEGORY_OPTIONS = [
  { value: 'rank', label: 'Привилегии' },
  { value: 'case', label: 'Кейсы' },
  { value: 'relic', label: 'Рилики' },
  { value: 'other', label: 'Услуги' },
];

const SERVER_CASE_OPTIONS = [
  { value: '', label: 'Без кейса' },
  { value: 'donate', label: 'Donate Кейс' },
  { value: 'sdonate', label: 'Супер Donate' },
  { value: 'legendary', label: 'Легендарный' },
  { value: 'reallydev', label: 'ReallyDev' },
  { value: 'riliks', label: 'Рилики Кейс' },
  { value: 'allorno', label: 'Всё или Ничего' },
  { value: 'ditems', label: 'Донат-Вещи' },
  { value: 'kits', label: 'Кейсы с Китами' },
  { value: 'money', label: 'Монетный Кейс' },
  { value: 'djamshut', label: 'Кейс Джамшута' },
  { value: 'summer', label: 'Новогодний (Summer)' },
];

function blank(): Draft {
  const inMonth = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  return {
    code: '',
    type: 'percent',
    value: 15,
    minTotal: 0,
    maxDiscount: null,
    usageLimit: null,
    expiresAt: inMonth,
    active: true,
    categories: '',
    serverEnabled: true,
    serverCoins: 25000,
    serverRelics: 25,
    serverCaseId: 'money',
    serverCaseQty: 1,
    serverRankId: '',
  };
}

function toDraft(promo: Promo): Draft {
  const sr = promo.serverReward ?? {
    enabled: true,
    coins: Math.max(10000, promo.value * 1000),
    relics: Math.max(10, promo.value),
    caseId: '',
    caseQty: 1,
    rankId: '',
  };
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
    serverEnabled: sr.enabled,
    serverCoins: sr.coins,
    serverRelics: sr.relics,
    serverCaseId: sr.caseId,
    serverCaseQty: sr.caseQty || 1,
    serverRankId: sr.rankId,
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
      serverReward: {
        enabled: draft.serverEnabled,
        coins: Math.max(0, Number(draft.serverCoins) || 0),
        relics: Math.max(0, Number(draft.serverRelics) || 0),
        caseId: draft.serverCaseId.trim().toLowerCase(),
        caseQty: draft.serverCaseId ? Math.max(1, Number(draft.serverCaseQty) || 1) : 0,
        rankId: draft.serverRankId.trim().toLowerCase(),
      },
    });
    setSaving(false);
    if (!res.ok) return setError(res.data.error ?? 'Не удалось сохранить');
    push({
      tone: 'success',
      title: 'Промокод синхронизирован (Сайт + Сервер)',
      description: `${draft.code.toUpperCase()} теперь работает в корзине и в /promo на сервере!`,
    });
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
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">маркетинг + minecraft sync</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Промокоды (Сайт + Сервер)</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">
            Каждый промокод работает как на скидку в корзине сайта, так и напрямую на Minecraft-сервере через команду <code className="text-neon">/promo &lt;КОД&gt;</code>!
          </p>
        </div>
        <button onClick={() => { setDraft(blank()); setEditingCode(null); }} className="btn btn-primary btn-sm"><Plus size={14} /> Создать промокод</button>
      </div>

      <AdminCard>
        <div className="overflow-x-auto">
          <table className="admin-table min-w-[820px]">
            <thead><tr><th>Код</th><th>Скидка на сайте</th><th>Награда в /promo на сервере</th><th>Лимит</th><th>Действует до</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {promos.map((promo) => {
                const expired = promo.expiresAt ? new Date(promo.expiresAt).getTime() < Date.now() : false;
                const exhausted = promo.usageLimit != null && promo.usedCount >= promo.usageLimit;
                const sr = promo.serverReward;
                return (
                  <tr key={promo.code}>
                    <td className="font-mono text-[0.76rem] font-bold text-white">{promo.code}</td>
                    <td className="text-white/70">{promo.type === 'percent' ? `${promo.value}%` : formatPrice(promo.value)}{promo.maxDiscount ? ` (макс. ${formatPrice(promo.maxDiscount)})` : ''}</td>
                    <td className="text-[0.68rem] text-emerald-300/85">
                      {sr?.enabled === false ? (
                        <span className="text-white/30">только сайт</span>
                      ) : (
                        <span>
                          {sr?.coins ? `+${sr.coins.toLocaleString('ru-RU')}$ ` : ''}
                          {sr?.relics ? `+${sr.relics}❖ ` : ''}
                          {sr?.caseId ? `+${sr.caseQty || 1}×[${sr.caseId}] ` : ''}
                          {sr?.rankId ? `[${sr.rankId.toUpperCase()}]` : ''}
                        </span>
                      )}
                    </td>
                    <td className="font-mono text-[0.66rem] text-white/50">{promo.usedCount} / {promo.usageLimit ?? '∞'}</td>
                    <td className="font-mono text-[0.64rem] text-white/40">{promo.expiresAt ? formatDate(promo.expiresAt) : 'бессрочно'}</td>
                    <td>
                      {expired || exhausted || !promo.active ? (
                        <Pill color="#EF4444">{expired ? 'истёк' : exhausted ? 'лимит' : 'выкл'}</Pill>
                      ) : (
                        <Pill color="#22C55E">сайт + сервер</Pill>
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

      <Modal
        open={Boolean(draft)}
        onClose={() => { setDraft(null); setEditingCode(null); }}
        title={editingCode ? `Промокод ${editingCode}` : 'Новый промокод (Сайт + Сервер)'}
        footer={<>
          <button onClick={() => { setDraft(null); setEditingCode(null); }} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={save} disabled={saving} className="btn btn-primary btn-sm">{saving ? '…' : 'Сохранить и синхронизировать'}</button>
        </>}
      >
        {draft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Код" hint="Латиница, цифры, - и _. Работает на сайте и в /promo <КОД> в игре." className="sm:col-span-2">
              <input
                value={draft.code}
                onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })}
                className="field font-mono tracking-widest"
                maxLength={32}
                disabled={Boolean(editingCode)}
              />
            </Field>
            <Field label="Тип скидки на сайте">
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
              <input type="number" min={1} value={draft.usageLimit ?? ''} onChange={(e) => setDraft({ ...draft, usageLimit: e.target.value ? Number(draft.usageLimit) : null })} className="field" />
            </Field>
            <Field label="Действует до" hint="Пусто — бессрочно.">
              <input type="date" value={draft.expiresAt} onChange={(e) => setDraft({ ...draft, expiresAt: e.target.value })} className="field" />
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

            <div className="sm:col-span-2 mt-1 rounded-xl border border-emerald-500/25 bg-emerald-500/[0.06] p-3 space-y-3">
              <label className="flex items-center justify-between gap-2">
                <span className="flex items-center gap-1.5 text-[0.78rem] font-bold text-emerald-300">
                  <Gamepad2 size={15} /> Награда на Minecraft-сервере (/promo {draft.code || 'КОД'})
                </span>
                <input
                  type="checkbox"
                  checked={draft.serverEnabled}
                  onChange={(e) => setDraft({ ...draft, serverEnabled: e.target.checked })}
                  className="h-4 w-4 accent-emerald-500"
                />
              </label>
              {draft.serverEnabled && (
                <div className="grid gap-2.5 sm:grid-cols-2">
                  <Field label="Монеты на сервере ($)">
                    <input type="number" min={0} value={draft.serverCoins} onChange={(e) => setDraft({ ...draft, serverCoins: Number(e.target.value) })} className="field" />
                  </Field>
                  <Field label="Рилики на сервере (❖)">
                    <input type="number" min={0} value={draft.serverRelics} onChange={(e) => setDraft({ ...draft, serverRelics: Number(e.target.value) })} className="field" />
                  </Field>
                  <Field label="Бонусный Кейс">
                    <select value={draft.serverCaseId} onChange={(e) => setDraft({ ...draft, serverCaseId: e.target.value })} className="field">
                      {SERVER_CASE_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value} className="bg-panel">{opt.label}</option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Кол-во кейсов">
                    <input type="number" min={1} max={64} value={draft.serverCaseQty} onChange={(e) => setDraft({ ...draft, serverCaseQty: Number(e.target.value) })} className="field" />
                  </Field>
                  <Field label="Привилегия (опционально, например pegas / hero / bunny)" className="sm:col-span-2">
                    <input value={draft.serverRankId} onChange={(e) => setDraft({ ...draft, serverRankId: e.target.value.toLowerCase() })} className="field font-mono text-xs" placeholder="Пусто — без выдачи привилегии" />
                  </Field>
                </div>
              )}
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
        <p className="text-[0.8rem] text-white/55">Код {toDelete?.code} перестанет работать на сайте и на сервере.</p>
      </Modal>
    </div>
  );
}
