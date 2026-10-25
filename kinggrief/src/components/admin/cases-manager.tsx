'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  AlertTriangle,
  Check,
  Copy,
  Download,
  Gift,
  Pencil,
  Plus,
  RefreshCw,
  Send,
  Server,
  Terminal,
  Trash2,
  UserCheck,
} from 'lucide-react';
import type { CaseGroup, CaseItem, Delivery, Rarity } from '@/lib/types';
import { RARITY_META } from '@/lib/types';
import { formatDate, formatPrice } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, ColorInput, Field, Modal, Pill } from './kit';
import { useToast } from '../providers';

type GroupWithMeta = CaseGroup & { items: CaseItem[]; total: number };
type ProductLite = { id: string; name: string; price: number; active: boolean };
type BridgeState = {
  connected: boolean;
  lastSeen: string | null;
  mode: string;
  online?: number;
  max?: number;
  mcVersion?: string | null;
  detectedCaseCmd?: string | null;
  plugins?: string | null;
};
type GrantDelivery = Delivery & { executedCommand?: string | null };

const RARITIES = Object.keys(RARITY_META) as Rarity[];
const MY_NICK_STORAGE_KEY = 'kg_admin_my_nick_v1';

const COMMAND_PRESETS: Array<{ label: string; tpl: string; hint: string }> = [
  { label: 'DonateCase (основной)', tpl: 'dc givekey {player} {case} {amount}', hint: 'DonateCase (dc givekey)' },
  { label: 'DC keys give', tpl: 'dc keys give {case} {player} {amount}', hint: 'DonateCase v3 (dc keys give)' },
  { label: 'Cases / RW', tpl: 'cases give {player} {case} {amount}', hint: 'MoonCases / ReallyWorld Cases' },
  { label: 'Cases add', tpl: 'cases add {player} {case} {amount}', hint: 'Альтернативный синтаксис Cases' },
  { label: 'ExcellentCrates', tpl: 'crates key give {player} {case} {amount}', hint: 'ExcellentCrates v5' },
  { label: 'CrazyCrates', tpl: 'cc give v {case} {amount} {player}', hint: 'CrazyCrates виртуальный ключ' },
];

const DELIVERY_STATUS_UI: Record<Delivery['status'], { label: string; color: string }> = {
  pending: { label: 'В очереди моста', color: '#FACC15' },
  sent: { label: 'Отправлено в консоль', color: '#38BDF8' },
  delivered: { label: 'Выдано на сервере', color: '#22C55E' },
  manual: { label: 'Ожидает моста / вручную', color: '#F59E0B' },
  failed: { label: 'Ошибка', color: '#EF4444' },
};

export function CasesManager({
  groups,
  packages,
  bridge: initialBridge,
  bridgeConfigured,
  initialCaseTemplate,
  initialGrants,
}: {
  groups: GroupWithMeta[];
  packages: Record<string, ProductLite[]>;
  bridge: BridgeState;
  bridgeConfigured: boolean;
  initialCaseTemplate: string;
  initialGrants: GrantDelivery[];
}) {
  const router = useRouter();
  const { push } = useToast();
  const [groupDraft, setGroupDraft] = useState<CaseGroup | null>(null);
  const [itemDraft, setItemDraft] = useState<(CaseItem & { id?: number }) | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [removeTarget, setRemoveTarget] = useState<{ type: 'group' | 'item'; id: string; name: string } | null>(null);

  // Состояние выдачи кейсов себе / игроку
  const [mySavedNick, setMySavedNick] = useState('');
  const [grantNick, setGrantNick] = useState('');
  const [grantCaseId, setGrantCaseId] = useState<string>(groups[0]?.id ?? 'donate');
  const [grantAmount, setGrantAmount] = useState<number>(5);
  const [commandTpl, setCommandTpl] = useState<string>(initialCaseTemplate || 'dc givekey {player} {case} {amount}');
  const [saveTplDefault, setSaveTplDefault] = useState<boolean>(true);
  const [showAdvancedCmd, setShowAdvancedCmd] = useState<boolean>(false);
  const [granting, setGranting] = useState<boolean>(false);
  const [grants, setGrants] = useState<GrantDelivery[]>(initialGrants);
  const [bridge, setBridge] = useState<BridgeState>(initialBridge);

  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(MY_NICK_STORAGE_KEY) ?? '';
      if (saved) {
        setMySavedNick(saved);
        setGrantNick((prev) => prev || saved);
      }
    } catch {
      // ignore storage errors
    }
  }, []);

  const rememberMyNick = () => {
    const clean = grantNick.trim();
    if (!clean) {
      push({ tone: 'error', title: 'Введите ваш никнейм', description: 'Укажите ник в поле получателя и нажмите «Запомнить мой ник».' });
      return;
    }
    try {
      window.localStorage.setItem(MY_NICK_STORAGE_KEY, clean);
      setMySavedNick(clean);
      push({ tone: 'success', title: 'Ваш ник сохранён', description: `Теперь кнопка «Себе (${clean})» подставляет ник в 1 клик.` });
    } catch {
      // ignore
    }
  };

  const previewCommand = (commandTpl || 'dc givekey {player} {case} {amount}')
    .replace(/^\/+/, '')
    .replaceAll('{player}', grantNick.trim() || 'ВашНик')
    .replaceAll('{case}', grantCaseId.trim().toLowerCase() || 'donate')
    .replaceAll('{amount}', String(Math.max(1, Math.floor(Number(grantAmount) || 1))));

  const submitGrant = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!grantNick.trim()) {
      push({ tone: 'error', title: 'Укажите никнейм', description: 'Введите свой никнейм или ник игрока на сервере.' });
      return;
    }
    setGranting(true);
    const res = await adminPost<{
      error?: string;
      command?: string;
      queuedForBridge?: boolean;
      bridge?: BridgeState;
      recentGrants?: Delivery[];
    }>('/api/admin/cases', {
      action: 'grant-case',
      grant: {
        nickname: grantNick.trim(),
        caseId: grantCaseId.trim().toLowerCase(),
        amount: Math.max(1, Math.floor(Number(grantAmount) || 1)),
        commandTemplate: commandTpl.trim(),
        saveTemplate: saveTplDefault,
      },
    });
    setGranting(false);
    if (!res.ok) {
      push({ tone: 'error', title: 'Не удалось выдать кейсы', description: res.data.error ?? 'Проверьте данные' });
      return;
    }
    if (res.data.bridge) setBridge(res.data.bridge);
    if (res.data.recentGrants) setGrants(res.data.recentGrants);
    push({
      tone: 'success',
      title: res.data.queuedForBridge ? 'Кейсы отправлены в очередь сервера!' : 'Задача выдачи создана',
      description: `/${res.data.command ?? previewCommand}`,
    });
    router.refresh();
  };

  const refreshGrants = async () => {
    try {
      const res = await fetch('/api/admin/cases', { cache: 'no-store' });
      if (!res.ok) return;
      const data = (await res.json()) as { bridge?: BridgeState; recentGrants?: GrantDelivery[] };
      if (data.bridge) setBridge(data.bridge);
      if (data.recentGrants) setGrants(data.recentGrants);
    } catch {
      // ignore
    }
  };

  // Авто-обновление каждые 5 сек., чтобы статус «В очереди моста → Выдано на сервере» обновлялся вживую
  useEffect(() => {
    const hasOpen = grants.some((g) => g.status === 'pending' || g.status === 'sent');
    if (!hasOpen && bridge.connected) return;
    const timer = window.setInterval(() => {
      void refreshGrants();
    }, 5000);
    return () => window.clearInterval(timer);
  }, [grants, bridge.connected]);

  const retryDelivery = async (id: string, action: 'retry' | 'deliver') => {
    const res = await adminPost('/api/admin/deliveries', { id, action });
    if (res.ok) {
      push({ tone: 'success', title: action === 'retry' ? 'Отправлено повторно в мост' : 'Отмечено выданным' });
      await refreshGrants();
      router.refresh();
    } else {
      push({ tone: 'error', title: 'Ошибка', description: res.data.error });
    }
  };

  const saveGroup = async () => {
    if (!groupDraft) return;
    setSaving(true);
    setError('');
    const res = await adminPost('/api/admin/cases', { action: 'save-group', group: groupDraft });
    setSaving(false);
    if (!res.ok) return setError(res.data.error ?? 'Ошибка сохранения');
    push({ tone: 'success', title: 'Кейс сохранён', description: groupDraft.name });
    setGroupDraft(null);
    router.refresh();
  };

  const saveItem = async () => {
    if (!itemDraft) return;
    setSaving(true);
    setError('');
    const res = await adminPost('/api/admin/cases', {
      action: 'save-item',
      item: {
        ...itemDraft,
        chance: Number(itemDraft.chance),
        sort: Number(itemDraft.sort),
      },
    });
    setSaving(false);
    if (!res.ok) return setError(res.data.error ?? 'Ошибка сохранения');
    push({ tone: 'success', title: 'Награда сохранена', description: `${itemDraft.name} · ${itemDraft.chance}%` });
    setItemDraft(null);
    router.refresh();
  };

  const remove = async () => {
    if (!removeTarget) return;
    const res = await adminPost('/api/admin/cases', {
      action: removeTarget.type === 'group' ? 'delete-group' : 'delete-item',
      id: removeTarget.type === 'group' ? removeTarget.id : undefined,
      itemId: removeTarget.type === 'item' ? Number(removeTarget.id) : undefined,
    });
    if (res.ok) {
      push({ tone: 'success', title: 'Удалено', description: removeTarget.name });
      setRemoveTarget(null);
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">кейсы · kinggrief.pickme.cloud</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Кейсы и выдача ключей</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">
            Мгновенная выдача любых серверных кейсов себе или игрокам + настройка наград и шансов (сумма = 100%).
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <a href="/KingGriefBridge.jar" download className="btn btn-gold btn-sm">
            <Download size={14} /> Скачать KingGriefBridge.jar
          </a>
          <button
            onClick={() =>
              setGroupDraft({
                id: `case-${Date.now().toString().slice(-5)}`,
                name: 'Новый кейс',
                description: '',
                color: '#A855F7',
                icon: 'box',
                sort: groups.length + 1,
                active: true,
              })
            }
            className="btn btn-ghost btn-sm"
          >
            <Plus size={14} /> Новый кейс
          </button>
          <Link href="/admin/products?kind=case" className="btn btn-primary btn-sm">Цены наборов</Link>
        </div>
      </div>

      {/* ───────── БЛОК БЫСТРОЙ ВЫДАЧИ КЕЙСОВ СЕБЕ / ИГРОКУ ───────── */}
      <AdminCard
        id="grant-cases"
        title="Выдать кейсы себе или игроку"
        description="Отправляет команду выдачи ключей напрямую в консоль сервера kinggrief.pickme.cloud через плагин KingGriefBridge (без оплаты и без искажения статистики выручки)."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Pill color={bridge.connected ? '#22C55E' : bridgeConfigured ? '#FACC15' : '#EF4444'}>
              <Server size={10} className="mr-1" />
              {bridge.connected
                ? 'Сервер подключён (онлайн)'
                : bridgeConfigured
                  ? 'Ожидает плагин в /plugins'
                  : 'Секрет моста не задан'}
            </Pill>
            <button type="button" onClick={refreshGrants} className="btn btn-ghost btn-icon !h-7 !w-7" title="Обновить статус выдачи">
              <RefreshCw size={12} />
            </button>
          </div>
        }
      >
        <form onSubmit={submitGrant} className="space-y-4">
          <div className="grid gap-3 lg:grid-cols-[1.1fr_1.1fr_0.8fr]">
            <Field
              label="Никнейм в игре (получатель)"
              hint="Латиница, цифры и _ (3–16 символов)"
            >
              <div className="flex gap-1.5">
                <input
                  value={grantNick}
                  onChange={(e) => setGrantNick(e.target.value)}
                  placeholder="ВашНикнейм"
                  maxLength={16}
                  className="field flex-1 font-mono text-xs"
                  required
                />
                {mySavedNick && grantNick.trim() !== mySavedNick ? (
                  <button
                    type="button"
                    onClick={() => setGrantNick(mySavedNick)}
                    className="btn btn-ghost btn-sm shrink-0 !px-2.5"
                    title="Подставить сохранённый ник"
                  >
                    <UserCheck size={13} /> Себе ({mySavedNick})
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={rememberMyNick}
                    className="btn btn-ghost btn-sm shrink-0 !px-2.5"
                    title="Запомнить этот ник как ваш для выдачи в 1 клик"
                  >
                    <UserCheck size={13} /> Мой ник
                  </button>
                )}
              </div>
            </Field>

            <Field label="Серверный ID кейса" hint="Точное совпадение с таб-комплитом на сервере">
              <div className="flex gap-1.5">
                <select
                  value={groups.some((g) => g.id === grantCaseId) ? grantCaseId : '__custom__'}
                  onChange={(e) => {
                    if (e.target.value !== '__custom__') setGrantCaseId(e.target.value);
                  }}
                  className="field flex-1"
                >
                  {groups.map((g) => (
                    <option key={g.id} value={g.id} className="bg-panel">
                      {g.name} ({g.id})
                    </option>
                  ))}
                  <option value="__custom__" className="bg-panel">Другой ID вручную…</option>
                </select>
                <input
                  value={grantCaseId}
                  onChange={(e) => setGrantCaseId(e.target.value.toLowerCase())}
                  className="field w-28 font-mono text-xs"
                  placeholder="donate"
                  aria-label="Точный серверный ID кейса"
                />
              </div>
            </Field>

            <Field label="Количество ключей" hint="От 1 до 1000 шт.">
              <div className="flex gap-1.5">
                <input
                  type="number"
                  min={1}
                  max={1000}
                  value={grantAmount}
                  onChange={(e) => setGrantAmount(Number(e.target.value))}
                  className="field w-24 font-mono text-xs"
                />
                <div className="flex flex-1 flex-wrap gap-1">
                  {[1, 3, 5, 10, 25, 50].map((qty) => (
                    <button
                      key={qty}
                      type="button"
                      onClick={() => setGrantAmount(qty)}
                      className={`rounded-md border px-2 py-1 font-mono text-[0.65rem] font-bold transition ${
                        grantAmount === qty
                          ? 'border-neon/60 bg-neon/20 text-white'
                          : 'border-white/8 bg-void/40 text-white/50 hover:border-white/20 hover:text-white'
                      }`}
                    >
                      ×{qty}
                    </button>
                  ))}
                </div>
              </div>
            </Field>
          </div>

          {/* Быстрые чипы всех 11 кейсов сервера */}
          <div>
            <p className="mb-1.5 font-mono text-[0.55rem] uppercase tracking-widest text-white/30">
              Быстрый выбор кейса сервера:
            </p>
            <div className="flex flex-wrap gap-1.5">
              {groups.map((g) => {
                const active = grantCaseId === g.id;
                return (
                  <button
                    key={g.id}
                    type="button"
                    onClick={() => setGrantCaseId(g.id)}
                    className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-[0.7rem] font-semibold transition"
                    style={{
                      borderColor: active ? g.color : 'rgba(255,255,255,0.08)',
                      background: active ? `${g.color}24` : 'rgba(10,9,18,0.45)',
                      color: active ? '#fff' : 'rgba(255,255,255,0.65)',
                    }}
                  >
                    <span className="h-2 w-2 rounded-full" style={{ background: g.color }} />
                    <span>{g.name}</span>
                    <code className="font-mono text-[0.6rem] opacity-60">{g.id}</code>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Превью консольной команды + переключение шаблона плагина кейсов */}
          <div className="rounded-xl border border-white/8 bg-void/50 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2 font-mono text-xs text-neon">
                <Terminal size={14} className="shrink-0" />
                <span className="text-white/40">Консоль:</span>
                <code className="rounded bg-black/50 px-2 py-0.5 text-white">/{previewCommand}</code>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard?.writeText(previewCommand);
                    push({ tone: 'success', title: 'Команда скопирована', description: previewCommand });
                  }}
                  className="btn btn-ghost btn-sm !h-7 !px-2 text-[0.66rem]"
                >
                  <Copy size={11} /> Копировать
                </button>
                <button
                  type="button"
                  onClick={() => setShowAdvancedCmd((v) => !v)}
                  className="btn btn-ghost btn-sm !h-7 !px-2 text-[0.66rem]"
                >
                  {showAdvancedCmd ? 'Скрыть шаблон' : 'Изменить команду плагина'}
                </button>
              </div>
            </div>

            {showAdvancedCmd && (
              <div className="mt-3 space-y-2 border-t border-white/8 pt-3">
                <div className="flex flex-wrap gap-1.5">
                  {COMMAND_PRESETS.map((preset) => (
                    <button
                      key={preset.tpl}
                      type="button"
                      onClick={() => setCommandTpl(preset.tpl)}
                      title={preset.hint}
                      className={`rounded-md border px-2 py-1 font-mono text-[0.62rem] transition ${
                        commandTpl === preset.tpl
                          ? 'border-neon/60 bg-neon/20 text-white'
                          : 'border-white/8 bg-black/30 text-white/55 hover:text-white'
                      }`}
                    >
                      {preset.label}: {preset.tpl}
                    </button>
                  ))}
                </div>
                <div className="grid gap-2 sm:grid-cols-[1fr_auto] sm:items-center">
                  <input
                    value={commandTpl}
                    onChange={(e) => setCommandTpl(e.target.value)}
                    className="field h-9 font-mono text-xs"
                    placeholder="dc givekey {player} {case} {amount}"
                  />
                  <label className="flex cursor-pointer items-center gap-2 text-[0.7rem] text-white/65">
                    <input
                      type="checkbox"
                      checked={saveTplDefault}
                      onChange={(e) => setSaveTplDefault(e.target.checked)}
                      className="h-4 w-4 accent-[#a855f7]"
                    />
                    Сохранить для всех покупок кейсов
                  </label>
                </div>
              </div>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="space-y-0.5 text-[0.68rem] text-white/45">
              <p>
                Плагин <code className="font-mono text-white/75">KingGriefBridge.jar</code> в папке <code className="font-mono text-white/75">/plugins</code> опрашивает облачную очередь каждые 5 сек. и автоматически подстраивает команду под ваш плагин кейсов (<code className="font-mono text-neon/90">DonateCase</code>, <code className="font-mono text-white/70">Cases</code>, <code className="font-mono text-white/70">ExcellentCrates</code>).
              </p>
              {bridge.detectedCaseCmd && (
                <p className="text-emerald-400/90">
                  На сервере обнаружен синтаксис: <code className="font-mono">/{bridge.detectedCaseCmd}</code>
                  {bridge.plugins ? ` (${bridge.plugins})` : ''}
                </p>
              )}
            </div>
            <button type="submit" disabled={granting} className="btn btn-primary">
              <Send size={14} />
              {granting ? 'Отправляем…' : `Выдать ×${Math.max(1, Math.floor(Number(grantAmount) || 1))} (${grantCaseId})`}
            </button>
          </div>
        </form>

        {grants.length > 0 && (
          <div className="mt-4 border-t border-white/8 pt-3">
            <p className="mb-2 font-mono text-[0.55rem] uppercase tracking-widest text-white/30">
              Последние выдачи кейсов:
            </p>
            <div className="overflow-x-auto">
              <table className="admin-table min-w-[580px]">
                <thead>
                  <tr>
                    <th>Игрок</th>
                    <th>Команда консоли</th>
                    <th>Статус</th>
                    <th>Время</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {grants.map((d) => {
                    const st = DELIVERY_STATUS_UI[d.status] ?? { label: d.status, color: '#94A3B8' };
                    return (
                      <tr key={d.id}>
                        <td className="font-mono text-xs font-bold text-white">{d.nickname}</td>
                        <td>
                          <code className="font-mono text-[0.68rem] text-neon/90">
                            /{d.executedCommand || d.command}
                          </code>
                          {d.lastError && <p className="mt-0.5 text-[0.6rem] text-white/35">{d.lastError}</p>}
                        </td>
                        <td>
                          <Pill color={st.color}>{st.label}</Pill>
                        </td>
                        <td className="font-mono text-[0.62rem] text-white/35">{formatDate(d.createdAt)}</td>
                        <td>
                          <div className="flex justify-end gap-1">
                            {d.status !== 'delivered' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => retryDelivery(d.id, 'retry')}
                                  className="btn btn-ghost btn-sm !h-7 !px-2 text-[0.62rem]"
                                  title="Повторно поставить в очередь плагина"
                                >
                                  <RefreshCw size={11} /> В очередь
                                </button>
                                <button
                                  type="button"
                                  onClick={() => retryDelivery(d.id, 'deliver')}
                                  className="btn btn-ghost btn-sm !h-7 !px-2 text-[0.62rem]"
                                  title="Отметить выданным вручную"
                                >
                                  <Check size={11} />
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </AdminCard>

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
                    setGrantCaseId(group.id);
                    document.getElementById('grant-cases')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  }}
                  className="btn btn-ghost btn-sm !h-7 !px-2.5 text-[0.66rem]"
                >
                  <Gift size={11} /> Выдать ({group.id})
                </button>
                <Pill color={ok ? '#22C55E' : '#EF4444'}>
                  {ok ? <><Check size={9} className="mr-1" /> 100%</> : <><AlertTriangle size={9} className="mr-1" /> {group.total}%</>}
                </Pill>
                {!group.active && <Pill color="#94A3B8">скрыт</Pill>}
                <button onClick={() => setGroupDraft(group)} className="btn btn-ghost btn-icon !h-7 !w-7" aria-label="Редактировать кейс"><Pencil size={12} /></button>
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
                { label: 'Мин. цена', value: packages[group.id]?.length ? formatPrice(Math.min(...packages[group.id].map((p) => p.price))) : '—' },
              ].map((stat) => (
                <div key={stat.label} className="rounded-lg border border-white/6 bg-void/30 px-2.5 py-2">
                  <p className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">{stat.label}</p>
                  <p className="stat-num text-[0.82rem] font-bold text-white">{stat.value}</p>
                </div>
              ))}
            </div>

            <div className="overflow-x-auto">
              <table className="admin-table min-w-[600px]">
                <thead><tr><th>Награда</th><th>Редкость</th><th>Шанс</th><th>Сортировка</th><th /></tr></thead>
                <tbody>
                  {group.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <p className="font-semibold text-white/80">{item.name}</p>
                        {item.description && <p className="mt-0.5 line-clamp-1 max-w-[260px] text-[0.66rem] text-white/30">{item.description}</p>}
                      </td>
                      <td><Pill color={RARITY_META[item.rarity]?.color ?? '#94A3B8'}>{RARITY_META[item.rarity]?.label ?? item.rarity}</Pill></td>
                      <td className="stat-num text-white/85">{item.chance}%</td>
                      <td className="font-mono text-[0.62rem] text-white/30">{item.sort}</td>
                      <td>
                        <div className="flex justify-end gap-1">
                          <button onClick={() => setItemDraft({ ...item })} className="btn btn-ghost btn-icon !h-7 !w-7" aria-label="Редактировать"><Pencil size={12} /></button>
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
        footer={<>
          <button onClick={() => setGroupDraft(null)} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={saveGroup} disabled={saving} className="btn btn-primary btn-sm">{saving ? '…' : 'Сохранить'}</button>
        </>}
      >
        {groupDraft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="ID (slug)" hint="Должен совпадать с названием кейса на сервере (например donate, sdonate, summer)">
              <input value={groupDraft.id} onChange={(e) => setGroupDraft({ ...groupDraft, id: e.target.value })} className="field font-mono text-xs" />
            </Field>
            <Field label="Название">
              <input value={groupDraft.name} onChange={(e) => setGroupDraft({ ...groupDraft, name: e.target.value })} className="field" />
            </Field>
            <Field label="Описание" className="sm:col-span-2">
              <textarea value={groupDraft.description} onChange={(e) => setGroupDraft({ ...groupDraft, description: e.target.value })} rows={3} className="field h-auto resize-none py-2" />
            </Field>
            <Field label="Цвет">
              <ColorInput value={groupDraft.color} onChange={(color) => setGroupDraft({ ...groupDraft, color })} />
            </Field>
            <Field label="Иконка"><input value={groupDraft.icon} onChange={(e) => setGroupDraft({ ...groupDraft, icon: e.target.value })} className="field" /></Field>
            <Field label="Сортировка"><input type="number" value={groupDraft.sort} onChange={(e) => setGroupDraft({ ...groupDraft, sort: Number(e.target.value) })} className="field" /></Field>
            <label className="flex items-center gap-2 pt-5">
              <input type="checkbox" checked={groupDraft.active} onChange={(e) => setGroupDraft({ ...groupDraft, active: e.target.checked })} className="h-4 w-4 accent-[#a855f7]" />
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
        footer={<>
          <button onClick={() => setItemDraft(null)} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={saveItem} disabled={saving} className="btn btn-primary btn-sm">{saving ? '…' : 'Сохранить'}</button>
        </>}
      >
        {itemDraft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Название" className="sm:col-span-2">
              <input value={itemDraft.name} onChange={(e) => setItemDraft({ ...itemDraft, name: e.target.value })} className="field" />
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
                value={itemDraft.rarity}
                onChange={(e) => {
                  const rarity = e.target.value as Rarity;
                  setItemDraft({ ...itemDraft, rarity, color: RARITY_META[rarity].color });
                }}
                className="field"
              >
                {RARITIES.map((r) => <option key={r} value={r} className="bg-panel">{RARITY_META[r].label}</option>)}
              </select>
            </Field>
            <Field label="Шанс, %" hint="0–100. Сумма по кейсу должна равняться 100.">
              <input type="number" step="0.01" min={0} max={100} value={itemDraft.chance} onChange={(e) => setItemDraft({ ...itemDraft, chance: Number(e.target.value) })} className="field" />
            </Field>
            <Field label="Цвет">
              <ColorInput value={itemDraft.color} onChange={(color) => setItemDraft({ ...itemDraft, color })} />
            </Field>
            <Field label="Иконка"><input value={itemDraft.icon} onChange={(e) => setItemDraft({ ...itemDraft, icon: e.target.value })} className="field font-mono text-xs" /></Field>
            <Field label="Сортировка"><input type="number" value={itemDraft.sort} onChange={(e) => setItemDraft({ ...itemDraft, sort: Number(e.target.value) })} className="field" /></Field>
            {error && <p className="sm:col-span-2 text-[0.72rem] text-danger">{error}</p>}
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(removeTarget)}
        onClose={() => setRemoveTarget(null)}
        title="Удалить безвозвратно?"
        footer={<>
          <button onClick={() => setRemoveTarget(null)} className="btn btn-ghost btn-sm">Отмена</button>
          <button onClick={remove} className="btn btn-sm" style={{ background: '#ef4444', color: '#fff' }}>Удалить</button>
        </>}
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
