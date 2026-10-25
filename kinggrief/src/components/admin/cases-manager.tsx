'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  AlertTriangle,
  Check,
  Download,
  Gift,
  Pencil,
  Plus,
  Trash2,
} from 'lucide-react';
import type { CloudBridgeState, CloudDeliveryRow } from '@/lib/delivery';
import { RARITY_META, type CaseGroup, type CaseItem, type Product, type Rarity } from '@/lib/types';
import { formatPrice } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminGrantPanel } from './admin-grant-panel';
import { AdminCard, ColorInput, Field, Modal, Pill } from './kit';
import { useToast } from '../providers';

export type CaseGroupView = CaseGroup & { items: CaseItem[]; total: number };
type CasePackage = { id: string; name: string; price: number; active: boolean };

const RARITIES: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary'];

export function CasesManager({
  groups,
  packages,
  rankProducts = [],
  bridge,
  initialTemplates,
  initialGrants = [],
  initialRecentNicks = [],
}: {
  groups: CaseGroupView[];
  packages: Record<string, CasePackage[]>;
  rankProducts?: Product[];
  bridge?: (CloudBridgeState & { secretConfigured?: boolean }) | null;
  bridgeConfigured?: boolean;
  initialCaseTemplate?: string;
  initialTemplates?: Record<string, string>;
  initialGrants?: CloudDeliveryRow[];
  initialRecentNicks?: string[];
}) {
  const router = useRouter();
  const { push } = useToast();
  const [groupDraft, setGroupDraft] = useState<CaseGroup | null>(null);
  const [itemDraft, setItemDraft] = useState<(Partial<CaseItem> & { caseId: string }) | null>(null);
  const [removeTarget, setRemoveTarget] = useState<{ type: 'group' | 'item'; id: string; name: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const saveGroup = async () => {
    if (!groupDraft) return;
    setSaving(true);
    setError('');
    const res = await adminPost('/api/admin/cases', { action: 'save-group', group: groupDraft });
    setSaving(false);
    if (!res.ok) {
      setError(res.data.error ?? 'Ошибка');
      return;
    }
    push({ tone: 'success', title: 'Кейс сохранён', description: groupDraft.name });
    setGroupDraft(null);
    router.refresh();
  };

  const saveItem = async () => {
    if (!itemDraft) return;
    setSaving(true);
    setError('');
    const res = await adminPost('/api/admin/cases', { action: 'save-item', item: itemDraft });
    setSaving(false);
    if (!res.ok) {
      setError(res.data.error ?? 'Ошибка');
      return;
    }
    push({ tone: 'success', title: 'Награда сохранена', description: itemDraft.name ?? '' });
    setItemDraft(null);
    router.refresh();
  };

  const remove = async () => {
    if (!removeTarget) return;
    const body =
      removeTarget.type === 'group'
        ? { action: 'delete-group', id: removeTarget.id }
        : { action: 'delete-item', itemId: Number(removeTarget.id) };
    const res = await adminPost('/api/admin/cases', body);
    if (res.ok) {
      push({ tone: 'info', title: 'Удалено', description: removeTarget.name });
      setRemoveTarget(null);
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">кейсы и выдача</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">
            Кейсы, привилегии и шансы
          </h1>
          <p className="mt-1 text-[0.76rem] text-white/35">
            Все 11 серверных кейсов синхронизированы с kinggrief.pickme.cloud. Выдавайте безлимитно на любые ники.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <a href="/KingGriefBridge.jar" download className="btn btn-ghost btn-sm">
            <Download size={13} /> KingGriefBridge.jar
          </a>
          <button
            onClick={() =>
              setGroupDraft({
                id: 'newcase',
                name: 'Новый кейс',
                description: '',
                color: '#A855F7',
                icon: 'box',
                sort: groups.length + 1,
                active: true,
              })
            }
            className="btn btn-primary btn-sm"
          >
            <Plus size={14} /> Новый кейс
          </button>
        </div>
      </div>

      <div id="grant-cases">
        <AdminGrantPanel
          defaultTab="case"
          caseGroups={groups}
          rankProducts={rankProducts}
          initialBridge={bridge}
          initialGrants={initialGrants}
          initialRecentNicks={initialRecentNicks}
          initialTemplates={initialTemplates}
        />
      </div>

      {groups.map((group) => {
        const ok = Math.abs(group.total - 100) < 0.01;
        return (
          <AdminCard
            key={group.id}
            title={group.name}
            description={`Серверный ID: ${group.id} · сумма шансов: ${group.total}%`}
            action={
              <div className="flex flex-wrap items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => {
                    document.getElementById('grant-cases')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  }}
                  className="btn btn-ghost btn-sm !h-7 !px-2.5 text-[0.66rem]"
                >
                  <Gift size={11} /> К выдаче ({group.id})
                </button>
                <Pill color={ok ? '#22C55E' : '#EF4444'}>
                  {ok ? (
                    <>
                      <Check size={9} className="mr-1" /> 100%
                    </>
                  ) : (
                    <>
                      <AlertTriangle size={9} className="mr-1" /> {group.total}%
                    </>
                  )}
                </Pill>
                {!group.active && <Pill color="#94A3B8">скрыт</Pill>}
                <button
                  onClick={() => setGroupDraft(group)}
                  className="btn btn-ghost btn-icon !h-7 !w-7"
                  aria-label="Редактировать кейс"
                >
                  <Pencil size={12} />
                </button>
                <button
                  onClick={() => setRemoveTarget({ type: 'group', id: group.id, name: group.name })}
                  className="btn btn-ghost btn-icon !h-7 !w-7 hover:!text-danger"
                  aria-label="Удалить кейс"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            }
          >
            <div className="mb-3 grid gap-2 sm:grid-cols-3">
              {[
                { label: 'Наград', value: `${group.items.length}` },
                { label: 'Товаров-наборов', value: `${packages[group.id]?.length ?? 0}` },
                {
                  label: 'Мин. цена',
                  value: packages[group.id]?.length
                    ? formatPrice(Math.min(...packages[group.id].map((p) => p.price)))
                    : '—',
                },
              ].map((stat) => (
                <div key={stat.label} className="rounded-lg border border-white/6 bg-void/30 px-2.5 py-2">
                  <p className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">{stat.label}</p>
                  <p className="stat-num text-[0.82rem] font-bold text-white">{stat.value}</p>
                </div>
              ))}
            </div>

            <div className="overflow-x-auto">
              <table className="admin-table min-w-[600px]">
                <thead>
                  <tr>
                    <th>Награда</th>
                    <th>Редкость</th>
                    <th>Шанс</th>
                    <th>Сортировка</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {group.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <p className="font-semibold text-white/80">{item.name}</p>
                        {item.description && (
                          <p className="mt-0.5 line-clamp-1 max-w-[260px] text-[0.66rem] text-white/30">
                            {item.description}
                          </p>
                        )}
                      </td>
                      <td>
                        <Pill color={RARITY_META[item.rarity]?.color ?? '#94A3B8'}>
                          {RARITY_META[item.rarity]?.label ?? item.rarity}
                        </Pill>
                      </td>
                      <td className="stat-num text-white/85">{item.chance}%</td>
                      <td className="font-mono text-[0.62rem] text-white/30">{item.sort}</td>
                      <td>
                        <div className="flex justify-end gap-1">
                          <button
                            onClick={() => setItemDraft({ ...item })}
                            className="btn btn-ghost btn-icon !h-7 !w-7"
                            aria-label="Редактировать"
                          >
                            <Pencil size={12} />
                          </button>
                          <button
                            onClick={() => setRemoveTarget({ type: 'item', id: String(item.id), name: item.name })}
                            className="btn btn-ghost btn-icon !h-7 !w-7 hover:!text-danger"
                            aria-label="Удалить"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <button
              onClick={() =>
                setItemDraft({
                  caseId: group.id,
                  name: 'Новая награда',
                  description: '',
                  rarity: 'rare',
                  chance: 1,
                  icon: 'sparkles',
                  color: RARITY_META.rare.color,
                  sort: (group.items[group.items.length - 1]?.sort ?? 0) + 1,
                })
              }
              className="btn btn-ghost btn-sm mt-3"
            >
              <Plus size={13} /> Добавить награду
            </button>

            {!ok && (
              <p className="mt-3 flex items-center gap-1.5 rounded-lg border border-danger/25 bg-danger/8 px-3 py-2 text-[0.7rem] text-[#fca5a5]">
                <AlertTriangle size={12} /> Сумма шансов не равна 100%. На сайте это отображается честно — исправьте значения.
              </p>
            )}
          </AdminCard>
        );
      })}

      <Modal
        open={Boolean(groupDraft)}
        onClose={() => setGroupDraft(null)}
        title="Настройки кейса"
        footer={
          <>
            <button onClick={() => setGroupDraft(null)} className="btn btn-ghost btn-sm">
              Отмена
            </button>
            <button onClick={saveGroup} disabled={saving} className="btn btn-primary btn-sm">
              {saving ? '…' : 'Сохранить'}
            </button>
          </>
        }
      >
        {groupDraft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="ID (slug)" hint="Должен совпадать с названием кейса на сервере (например donate, sdonate, summer)">
              <input
                value={groupDraft.id}
                onChange={(e) => setGroupDraft({ ...groupDraft, id: e.target.value })}
                className="field font-mono text-xs"
              />
            </Field>
            <Field label="Название">
              <input
                value={groupDraft.name}
                onChange={(e) => setGroupDraft({ ...groupDraft, name: e.target.value })}
                className="field"
              />
            </Field>
            <Field label="Описание" className="sm:col-span-2">
              <textarea
                value={groupDraft.description}
                onChange={(e) => setGroupDraft({ ...groupDraft, description: e.target.value })}
                rows={3}
                className="field h-auto resize-none py-2"
              />
            </Field>
            <Field label="Цвет">
              <ColorInput value={groupDraft.color} onChange={(color) => setGroupDraft({ ...groupDraft, color })} />
            </Field>
            <Field label="Иконка">
              <input
                value={groupDraft.icon}
                onChange={(e) => setGroupDraft({ ...groupDraft, icon: e.target.value })}
                className="field"
              />
            </Field>
            <Field label="Сортировка">
              <input
                type="number"
                value={groupDraft.sort}
                onChange={(e) => setGroupDraft({ ...groupDraft, sort: Number(e.target.value) })}
                className="field"
              />
            </Field>
            <label className="flex items-center gap-2 pt-5">
              <input
                type="checkbox"
                checked={groupDraft.active}
                onChange={(e) => setGroupDraft({ ...groupDraft, active: e.target.checked })}
                className="h-4 w-4 accent-[#a855f7]"
              />
              <span className="text-[0.76rem] text-white/60">Показывать кейс</span>
            </label>
            {error && <p className="sm:col-span-2 text-[0.72rem] text-danger">{error}</p>}
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(itemDraft)}
        onClose={() => setItemDraft(null)}
        title={itemDraft?.id ? 'Редактирование награды' : 'Новая награда'}
        footer={
          <>
            <button onClick={() => setItemDraft(null)} className="btn btn-ghost btn-sm">
              Отмена
            </button>
            <button onClick={saveItem} disabled={saving} className="btn btn-primary btn-sm">
              {saving ? '…' : 'Сохранить'}
            </button>
          </>
        }
      >
        {itemDraft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Название" className="sm:col-span-2">
              <input
                value={itemDraft.name ?? ''}
                onChange={(e) => setItemDraft({ ...itemDraft, name: e.target.value })}
                className="field"
              />
            </Field>
            <Field label="Описание награды" hint="Показывается игроку в списке дропа и при открытии кейса." className="sm:col-span-2">
              <textarea
                value={itemDraft.description ?? ''}
                onChange={(e) => setItemDraft({ ...itemDraft, description: e.target.value })}
                rows={2}
                className="field h-auto resize-none py-2"
                maxLength={300}
              />
            </Field>
            <Field label="Ссылка (необязательно)" hint="Например /privileges/king" className="sm:col-span-2">
              <input
                value={itemDraft.link ?? ''}
                onChange={(e) => setItemDraft({ ...itemDraft, link: e.target.value || null })}
                className="field font-mono text-xs"
                placeholder="/privileges/king"
              />
            </Field>
            <Field label="Редкость">
              <select
                value={itemDraft.rarity ?? 'common'}
                onChange={(e) => {
                  const rarity = e.target.value as Rarity;
                  setItemDraft({ ...itemDraft, rarity, color: RARITY_META[rarity].color });
                }}
                className="field"
              >
                {RARITIES.map((r) => (
                  <option key={r} value={r} className="bg-panel">
                    {RARITY_META[r].label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Шанс, %" hint="0–100. Сумма по кейсу должна равняться 100.">
              <input
                type="number"
                step="0.01"
                min={0}
                max={100}
                value={itemDraft.chance ?? 1}
                onChange={(e) => setItemDraft({ ...itemDraft, chance: Number(e.target.value) })}
                className="field"
              />
            </Field>
            <Field label="Цвет">
              <ColorInput value={itemDraft.color ?? '#94A3B8'} onChange={(color) => setItemDraft({ ...itemDraft, color })} />
            </Field>
            <Field label="Иконка">
              <input
                value={itemDraft.icon ?? 'coins'}
                onChange={(e) => setItemDraft({ ...itemDraft, icon: e.target.value })}
                className="field font-mono text-xs"
              />
            </Field>
            <Field label="Сортировка">
              <input
                type="number"
                value={itemDraft.sort ?? 0}
                onChange={(e) => setItemDraft({ ...itemDraft, sort: Number(e.target.value) })}
                className="field"
              />
            </Field>
            {error && <p className="sm:col-span-2 text-[0.72rem] text-danger">{error}</p>}
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(removeTarget)}
        onClose={() => setRemoveTarget(null)}
        title="Удалить безвозвратно?"
        footer={
          <>
            <button onClick={() => setRemoveTarget(null)} className="btn btn-ghost btn-sm">
              Отмена
            </button>
            <button onClick={remove} className="btn btn-sm" style={{ background: '#ef4444', color: '#fff' }}>
              Удалить
            </button>
          </>
        }
      >
        <p className="text-[0.8rem] leading-relaxed text-white/55">
          {removeTarget?.type === 'group'
            ? `Кейс «${removeTarget.name}» и все его награды будут удалены. Товары-наборы останутся в каталоге — скройте их отдельно.`
            : `Награда «${removeTarget?.name}» будет удалена, сумма шансов изменится.`}
        </p>
      </Modal>
    </div>
  );
}
