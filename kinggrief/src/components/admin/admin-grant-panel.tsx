'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Check,
  Coins,
  Copy,
  Crown,
  Download,
  Gift,
  RefreshCw,
  RotateCcw,
  Send,
  ShieldCheck,
  Sparkles,
  Terminal,
  UserCheck,
  Users,
  Wifi,
  WifiOff,
  X,
} from 'lucide-react';
import type { CloudBridgeState, CloudDeliveryRow, PrivilegeGrantMode } from '@/lib/delivery';
import type { CaseGroup, Product } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, Pill } from './kit';
import { useToast } from '../providers';

export type GrantTab = 'case' | 'rank' | 'relic' | 'service';

const CASE_PRESETS: Array<{ label: string; cmd: string; hint: string }> = [
  { label: 'NCases (ваш сервер)', cmd: 'cases give {player} {case} {amount}', hint: 'NCases / Cases' },
  { label: 'DonateCase (dc)', cmd: 'dc givekey {player} {case} {amount}', hint: 'DonateCase' },
  { label: 'ExcellentCrates', cmd: 'crates key give {player} {case} {amount}', hint: 'ExcellentCrates' },
  { label: 'CrazyCrates', cmd: 'cc give physical {case} {amount} {player}', hint: 'CrazyCrates' },
];

const RELIC_PRESETS: Array<{ label: string; cmd: string }> = [
  { label: 'PlayerPoints (p give)', cmd: 'p give {player} {amount}' },
  { label: 'PlayerPoints (points give)', cmd: 'points give {player} {amount}' },
  { label: 'Vault / Essentials (eco)', cmd: 'eco give {player} {amount}' },
];

const LP_MODES: Array<{ key: PrivilegeGrantMode; label: string; desc: string; tpl: string }> = [
  {
    key: 'set',
    label: 'Основная привилегия (parent set)',
    desc: 'Сразу меняет привилегию и префикс игрока. Работает безлимитно при любой повторной выдаче.',
    tpl: 'lp user {player} parent set {rank}',
  },
  {
    key: 'add',
    label: 'Добавить группу (parent add)',
    desc: 'Добавляет права группы поверх уже имеющихся у игрока.',
    tpl: 'lp user {player} parent add {rank}',
  },
  {
    key: 'temp30d',
    label: 'Временно на 30 дней',
    desc: 'Выдаёт привилегию на 30 дней (при повторной выдаче срок суммируется).',
    tpl: 'lp user {player} parent addtemp {rank} 30d accumulate',
  },
  {
    key: 'temp90d',
    label: 'Временно на 90 дней',
    desc: 'Выдаёт привилегию на 90 дней (при повторной выдаче срок суммируется).',
    tpl: 'lp user {player} parent addtemp {rank} 90d accumulate',
  },
  {
    key: 'remove',
    label: 'Снять конкретную группу',
    desc: 'Удаляет выбранную привилегию у игрока.',
    tpl: 'lp user {player} parent remove {rank}',
  },
  {
    key: 'reset',
    label: 'Сбросить до обычного игрока',
    desc: 'Сбрасывает все донат-группы игрока до default.',
    tpl: 'lp user {player} parent set default',
  },
];

const DEFAULT_RANK_OPTIONS: Array<{ id: string; name: string; color: string; price?: number }> = [
  // Игровые и Донат-привилегии (в точном порядке 39-ранговой лестницы v5.7)
  { id: 'dragon', name: 'DRAGON (/free dragon)', color: '#D946EF', price: 15 },
  { id: 'pegas', name: 'PEGAS', color: '#22C55E', price: 19 },
  { id: 'cobra', name: 'COBRA', color: '#84CC16', price: 39 },
  { id: 'dracula', name: 'DRACULA', color: '#DC2626', price: 49 },
  { id: 'bunny', name: 'BUNNY', color: '#EC4899', price: 59 },
  { id: 'rabbit', name: 'RABBIT', color: '#38BDF8', price: 89 },
  { id: 'vampire', name: 'VAMPIRE', color: '#EF4444', price: 99 },
  { id: 'tiger', name: 'TIGER', color: '#FB923C', price: 119 },
  { id: 'god', name: 'GOD', color: '#FACC15', price: 149 },
  { id: 'imperator', name: 'IMPERATOR', color: '#F43F5E', price: 179 },
  { id: 'king', name: 'KING', color: '#A855F7', price: 199 },
  { id: 'sponsor', name: 'SPONSOR', color: '#38BDF8', price: 215 },
  // ДКП (Донатная Команда Проекта) и Топ-Донат
  { id: 'dhelper', name: 'D.HELPER (ДКП)', color: '#2DD4BF', price: 219 },
  { id: 'dkp', name: 'D.KP (ДКП)', color: '#E879F9', price: 229 },
  { id: 'dmoder', name: 'D.MODER (ДКП)', color: '#14B8A6', price: 249 },
  { id: 'dstmoder', name: 'D.ST.MODER (ДКП)', color: '#3B82F6', price: 349 },
  { id: 'dglmoder', name: 'D.GLMODER (ДКП)', color: '#8B5CF6', price: 449 },
  { id: 'dmladmin', name: 'D.ML.ADMIN (ДКП)', color: '#F97316', price: 599 },
  { id: 'dadmin', name: 'D.ADMIN (ДКП)', color: '#DC2626', price: 799 },
  { id: 'opka', name: 'OPKA', color: '#C026D3', price: 999 },
  { id: 'downer', name: 'D.OWNER', color: '#FACC15', price: 1499 },
  // МЕДИА (Media)
  { id: 'tiktok', name: 'TIKTOK (Медиа)', color: '#00F2EA' },
  { id: 'twitch', name: 'TWITCH (Медиа)', color: '#9146FF' },
  { id: 'yt', name: 'YOUTUBE (Медиа)', color: '#FF0000' },
  { id: 'media', name: 'MEDIA', color: '#EC4899' },
  { id: 'mediaplus', name: 'MEDIA+', color: '#F43F5E' },
  // КП (Команда Проекта / Персонал)
  { id: 'kp', name: 'КП (Стажёр КП)', color: '#EF4444' },
  { id: 'helper', name: 'Хелпер (КП)', color: '#22C55E' },
  { id: 'sthelper', name: 'Ст.Хелпер (КП)', color: '#0EA5E9' },
  { id: 'mlmoder', name: 'Мл.Модератор (КП)', color: '#38BDF8' },
  { id: 'moder', name: 'Модератор (КП)', color: '#2563EB' },
  { id: 'stmoder', name: 'Ст.Модератор (КП)', color: '#4F46E5' },
  { id: 'glmoder', name: 'Гл.Модератор (КП)', color: '#7C3AED' },
  { id: 'mladmin', name: 'Мл.Админ (КП)', color: '#F97316' },
  { id: 'admin', name: 'Админ (КП)', color: '#EF4444' },
  { id: 'curator', name: 'CURATOR (Куратор КП)', color: '#22C55E' },
  { id: 'techadmin', name: 'Тех.Админ (КП)', color: '#00E5FF' },
  { id: 'owner', name: 'OWNER (Владелец)', color: '#EF4444' },
];

const DEFAULT_CASE_OPTIONS: Array<{ id: string; name: string }> = [
  { id: 'donate', name: 'Донат-кейс' },
  { id: 'sdonate', name: 'Супер Донат-кейс' },
  { id: 'legendary', name: 'Легендарный кейс' },
  { id: 'riliks', name: 'Кейс с риликами' },
  { id: 'ditems', name: 'Кейс донат-вещей' },
  { id: 'kits', name: 'Кейс с китами' },
  { id: 'money', name: 'Денежный кейс' },
  { id: 'allorno', name: 'Всё или ничего' },
  { id: 'djamshut', name: 'Кейс Джамшута' },
  { id: 'reallydev', name: 'Кейс ReallyDev' },
  { id: 'summer', name: 'Летний кейс' },
];

const NICK_STORAGE_KEY = 'kg_admin_my_nick';
const RECENT_NICKS_STORAGE_KEY = 'kg_admin_recent_nicks';

export function AdminGrantPanel({
  defaultTab = 'case',
  caseGroups,
  rankProducts,
  initialBridge,
  initialGrants = [],
  initialRecentNicks = [],
  initialTemplates,
}: {
  defaultTab?: GrantTab;
  caseGroups?: Array<Pick<CaseGroup, 'id' | 'name' | 'color'>>;
  rankProducts?: Product[];
  initialBridge?: (CloudBridgeState & { secretConfigured?: boolean }) | null;
  initialGrants?: CloudDeliveryRow[];
  initialRecentNicks?: string[];
  initialTemplates?: Record<string, string>;
}) {
  const { push } = useToast();
  const [tab, setTab] = useState<GrantTab>(defaultTab);
  const [bridge, setBridge] = useState<(CloudBridgeState & { secretConfigured?: boolean }) | null>(
    initialBridge ?? null,
  );
  const [grants, setGrants] = useState<CloudDeliveryRow[]>(initialGrants);
  const [recentNicks, setRecentNicks] = useState<string[]>(initialRecentNicks);

  // Ник получателя (любой игрок!)
  const [nick, setNick] = useState('');
  const [mySavedNick, setMySavedNick] = useState('');

  // Кейсы
  const casesList = useMemo(
    () => (caseGroups && caseGroups.length > 0 ? caseGroups : DEFAULT_CASE_OPTIONS),
    [caseGroups],
  );
  const [caseId, setCaseId] = useState<string>(casesList[0]?.id ?? 'donate');
  const [customCaseId, setCustomCaseId] = useState('');
  const [useCustomCase, setUseCustomCase] = useState(false);
  const [caseAmount, setCaseAmount] = useState<number>(1);
  const [caseTpl, setCaseTpl] = useState<string>(
    initialTemplates?.case || 'cases give {player} {case} {amount}',
  );

  // Привилегии (товары с сайта + полный состав КП / ДКП / Медиа / доп. привилегий сборки)
  const ranksList = useMemo(() => {
    if (rankProducts && rankProducts.length > 0) {
      const fromDb = rankProducts.map((p) => ({
        id: String((p.meta as Record<string, unknown>)?.rankId || p.id.replace(/^rank-/, '')).toLowerCase(),
        name: p.name,
        color: p.color,
        price: p.price,
      }));
      const seen = new Set(fromDb.map((r) => r.id));
      const extras = DEFAULT_RANK_OPTIONS.filter((r) => !seen.has(r.id));
      return [...fromDb, ...extras];
    }
    return DEFAULT_RANK_OPTIONS;
  }, [rankProducts]);
  const [rankId, setRankId] = useState<string>('king');
  const [customRankId, setCustomRankId] = useState('');
  const [useCustomRank, setUseCustomRank] = useState(false);
  const [lpMode, setLpMode] = useState<PrivilegeGrantMode>('set');

  // Рилики
  const [relicAmount, setRelicAmount] = useState<number>(5000);
  const [relicTpl, setRelicTpl] = useState<string>(
    initialTemplates?.relic || 'p give {player} {amount}',
  );

  // Услуги
  const [serviceAction, setServiceAction] = useState<'pardon' | 'unban' | 'unmute' | 'pardon_ip' | 'kit'>('pardon');
  const [kitName, setKitName] = useState('start');

  // Состояния отправки и сохранения шаблона
  const [saveAsDefault, setSaveAsDefault] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [busyRowId, setBusyRowId] = useState<string | null>(null);
  const [copiedText, setCopiedText] = useState<string | null>(null);
  const [lastResultBanner, setLastResultBanner] = useState<{
    nick: string;
    command: string;
    label: string;
    count: number;
  } | null>(null);

  useEffect(() => {
    setTab(defaultTab);
  }, [defaultTab]);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(NICK_STORAGE_KEY) || '';
      if (saved) setMySavedNick(saved);
      const localRecents = JSON.parse(localStorage.getItem(RECENT_NICKS_STORAGE_KEY) || '[]') as string[];
      if (Array.isArray(localRecents) && localRecents.length > 0) {
        setRecentNicks((prev) => Array.from(new Set([...localRecents, ...prev])).slice(0, 14));
      }
      // Подставляем стартовый ник только 1 раз при первом открытии, если поле пустое
      const firstNick = saved || localRecents[0] || initialRecentNicks[0] || '';
      if (firstNick) setNick((curr) => (curr ? curr : firstNick));
    } catch {
      /* ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rememberRecipientNick = (value: string) => {
    const clean = value.trim();
    if (!clean) return;
    setRecentNicks((prev) => {
      const next = [clean, ...prev.filter((n) => n.toLowerCase() !== clean.toLowerCase())].slice(0, 14);
      try {
        localStorage.setItem(RECENT_NICKS_STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* ignore */
      }
      return next;
    });
  };

  const saveAsMyNick = () => {
    const clean = nick.trim();
    if (!clean) return;
    setMySavedNick(clean);
    try {
      localStorage.setItem(NICK_STORAGE_KEY, clean);
    } catch {
      /* ignore */
    }
    push({ tone: 'success', title: 'Сохранён ваш быстрый ник', description: clean });
  };

  const pollStatus = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/grant', { cache: 'no-store', credentials: 'same-origin' });
      if (!res.ok) return;
      const data = (await res.json()) as {
        bridge?: CloudBridgeState & { secretConfigured?: boolean };
        recentGrants?: CloudDeliveryRow[];
        recentNicks?: string[];
        templates?: Record<string, string>;
      };
      if (data.bridge) setBridge(data.bridge);
      if (data.recentGrants) setGrants(data.recentGrants);
      if (data.recentNicks) {
        setRecentNicks((prev) => Array.from(new Set([...prev, ...(data.recentNicks ?? [])])).slice(0, 14));
      }
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    pollStatus();
    const timer = setInterval(pollStatus, 4000);
    return () => clearInterval(timer);
  }, [pollStatus]);

  const effectiveCaseId = (useCustomCase ? customCaseId : caseId).trim().toLowerCase() || 'donate';
  const effectiveRankId =
    lpMode === 'reset'
      ? 'default'
      : (useCustomRank ? customRankId : rankId).trim().toLowerCase() || 'king';

  const commandPreview = useMemo(() => {
    const p = nick.trim() || 'PlayerNick';
    if (tab === 'case') {
      return (caseTpl.trim() || 'cases give {player} {case} {amount}')
        .replace(/^\/+/, '')
        .replaceAll('{player}', p)
        .replaceAll('{case}', effectiveCaseId)
        .replaceAll('{amount}', String(Math.max(1, Math.floor(caseAmount || 1))));
    }
    if (tab === 'rank') {
      const foundMode = LP_MODES.find((m) => m.key === lpMode) ?? LP_MODES[0];
      return foundMode.tpl.replaceAll('{player}', p).replaceAll('{rank}', effectiveRankId);
    }
    if (tab === 'relic') {
      return (relicTpl.trim() || 'p give {player} {amount}')
        .replace(/^\/+/, '')
        .replaceAll('{player}', p)
        .replaceAll('{amount}', String(Math.max(1, Math.floor(relicAmount || 1000))));
    }
    if (serviceAction === 'kit') return `kit ${(kitName || 'start').trim()} ${p}`;
    if (serviceAction === 'pardon_ip') return `pardon-ip ${p}`;
    return `${serviceAction} ${p}`;
  }, [tab, nick, caseTpl, effectiveCaseId, caseAmount, lpMode, effectiveRankId, relicTpl, relicAmount, serviceAction, kitName]);

  const copyCommand = async (raw: string) => {
    const withSlash = raw.startsWith('/') ? raw : `/${raw}`;
    try {
      await navigator.clipboard.writeText(withSlash);
      setCopiedText(raw);
      setTimeout(() => setCopiedText(null), 1800);
      push({ tone: 'success', title: 'Команда скопирована', description: withSlash });
    } catch {
      push({ tone: 'error', title: 'Не удалось скопировать' });
    }
  };

  const submitGrant = async () => {
    const cleanNick = nick.trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(cleanNick)) {
      push({
        tone: 'error',
        title: 'Проверьте никнейм получателя',
        description: 'От 3 до 16 символов: латиница, цифры и знак _ (можно указать ник любого игрока)',
      });
      return;
    }

    let payload: Record<string, unknown>;
    let summaryLabel = '';

    if (tab === 'case') {
      const qty = Math.max(1, Math.min(10000, Math.floor(Number(caseAmount) || 1)));
      payload = {
        action: 'grant-case',
        nickname: cleanNick,
        caseId: effectiveCaseId,
        amount: qty,
        commandTemplate: caseTpl,
        saveTemplate: saveAsDefault,
      };
      summaryLabel = `Кейс ${effectiveCaseId} ×${qty}`;
    } else if (tab === 'rank') {
      payload = {
        action: 'grant-privilege',
        nickname: cleanNick,
        rankId: effectiveRankId,
        mode: lpMode,
        saveTemplate: saveAsDefault && lpMode === 'set',
      };
      summaryLabel =
        lpMode === 'reset'
          ? 'Сброс привилегии → default'
          : `Привилегия ${effectiveRankId.toUpperCase()} (${lpMode})`;
    } else if (tab === 'relic') {
      const qty = Math.max(1, Math.min(10_000_000, Math.floor(Number(relicAmount) || 1000)));
      payload = {
        action: 'grant-relic',
        nickname: cleanNick,
        amount: qty,
        commandTemplate: relicTpl,
        saveTemplate: saveAsDefault,
      };
      summaryLabel = `${qty.toLocaleString('ru-RU')} риликов`;
    } else {
      payload = {
        action: 'grant-service',
        nickname: cleanNick,
        serviceAction,
        kitName,
      };
      summaryLabel = `Команда ${serviceAction}`;
    }

    setSubmitting(true);
    const res = await adminPost<{
      ok?: boolean;
      command?: string;
      delivery?: CloudDeliveryRow;
      bridge?: CloudBridgeState & { secretConfigured?: boolean };
      recentGrants?: CloudDeliveryRow[];
      recentNicks?: string[];
      queuedForBridge?: boolean;
      error?: string;
    }>('/api/admin/grant', payload);
    setSubmitting(false);

    if (!res.ok) {
      push({ tone: 'error', title: 'Не удалось выдать', description: res.data.error });
      return;
    }

    rememberRecipientNick(cleanNick);
    if (res.data.bridge) setBridge(res.data.bridge);
    if (res.data.recentGrants) setGrants(res.data.recentGrants);
    else if (res.data.delivery) setGrants((prev) => [res.data.delivery!, ...prev].slice(0, 20));

    setLastResultBanner((prev) => ({
      nick: cleanNick,
      command: res.data.command ?? commandPreview,
      label: summaryLabel,
      count: (prev?.count ?? 0) + 1,
    }));

    push({
      tone: 'success',
      title: `Отправлено игроку ${cleanNick}`,
      description: `${summaryLabel} · /${res.data.command ?? commandPreview}`,
    });
  };

  const repeatRow = async (d: CloudDeliveryRow) => {
    setBusyRowId(d.id);
    const res = await adminPost<{
      ok?: boolean;
      command?: string;
      bridge?: CloudBridgeState & { secretConfigured?: boolean };
      recentGrants?: CloudDeliveryRow[];
      error?: string;
    }>('/api/admin/grant', {
      action: 'repeat',
      deliveryId: d.id,
    });
    setBusyRowId(null);
    if (res.ok) {
      if (res.data.bridge) setBridge(res.data.bridge);
      if (res.data.recentGrants) setGrants(res.data.recentGrants);
      push({
        tone: 'success',
        title: `Повторно отправлено игроку ${d.nickname}`,
        description: `/${res.data.command ?? d.command}`,
      });
    } else {
      push({ tone: 'error', title: 'Ошибка повторной выдачи', description: res.data.error });
    }
  };

  const requeueFailedRow = async (d: CloudDeliveryRow) => {
    setBusyRowId(d.id);
    const res = await adminPost('/api/admin/deliveries', { id: d.id, action: 'retry' });
    setBusyRowId(null);
    if (res.ok) {
      push({ tone: 'success', title: 'Возвращено в очередь моста' });
      pollStatus();
    } else {
      push({ tone: 'error', title: 'Ошибка', description: res.data.error });
    }
  };

  const onlineSet = useMemo(
    () => new Set((bridge?.onlinePlayers ?? []).map((p) => p.toLowerCase())),
    [bridge?.onlinePlayers],
  );

  const chipNicks = useMemo(() => {
    const merged: string[] = [];
    const seen = new Set<string>();
    const pushNick = (n: string) => {
      const c = n.trim();
      if (!c) return;
      const k = c.toLowerCase();
      if (seen.has(k)) return;
      seen.add(k);
      merged.push(c);
    };
    for (const p of bridge?.onlinePlayers ?? []) pushNick(p);
    if (mySavedNick) pushNick(mySavedNick);
    for (const p of recentNicks) pushNick(p);
    for (const g of grants) pushNick(g.nickname);
    return merged.slice(0, 12);
  }, [bridge?.onlinePlayers, mySavedNick, recentNicks, grants]);

  const extraLpGroups = useMemo(() => {
    const known = new Set(ranksList.map((r) => r.id.toLowerCase()));
    return (bridge?.lpGroups ?? []).filter((g) => !known.has(g.toLowerCase()));
  }, [bridge?.lpGroups, ranksList]);

  return (
    <AdminCard
      title="Безлимитная выдача на сервер (любому игроку)"
      description="Мгновенная выдача кейсов, привилегий LuckPerms, риликов и разбана на любые ники без лимитов — напрямую через KingGriefBridge."
      action={
        <div className="flex flex-wrap items-center gap-2">
          <Pill color={bridge?.connected ? '#22C55E' : '#F59E0B'}>
            <span className="inline-flex items-center gap-1.5">
              {bridge?.connected ? <Wifi size={11} /> : <WifiOff size={11} />}
              {bridge?.connected
                ? `Сервер онлайн (${bridge.online}/${bridge.max}${bridge.tps ? ` · TPS ${bridge.tps}` : ''})`
                : 'Ожидание сигнала моста'}
            </span>
          </Pill>
          <button type="button" onClick={pollStatus} className="btn btn-ghost btn-sm" title="Обновить статус очереди">
            <RefreshCw size={12} /> Статус
          </button>
          <a href="/KingGriefCore-v7.3.0.jar" download="KingGriefCore.jar" className="btn btn-primary btn-sm" title="Скачать KingGriefCore v7.3.0: готовый спавн без замены существующих миров">
            <Download size={12} /> Ядро v7.3.0 (.jar)
          </a>
          <a href="/kinggrief-mega-rp-v72.zip" download className="btn btn-gold btn-sm" title="Скачать Мега-Ресурспак v72: модели шаров, кирок, брони, тотемов и новых предметов">
            <Download size={12} /> Мега-РП v72 (.zip)
          </a>
          <a href="/KingGriefBridge.jar" download className="btn btn-ghost btn-sm" title="Скачать плагин моста KingGriefBridge.jar">
            <Download size={12} /> Мост (.jar)
          </a>
          <a href="/KingHub.jar" download className="btn btn-primary btn-sm" title="Плагин №1 для ХАБА: Новогодняя Карта с Ёлкой, Компас (выбор 1 ГРИФ -> вход) и НПС (ПКМ -> выбор 1 ГРИФ -> вход)">
            <Download size={12} /> №1 Для Хаба: KingHub (.jar)
          </a>
          <a href="/KingConnect.jar" download className="btn btn-primary btn-sm" title="Плагин №2 для СЕРВЕРА (1 ГРИФ): приём переходов из KingHub без повторного /login, /hub, /servers и облачная синхронизация">
            <Download size={12} /> №2 Для Сервера: KingConnect (.jar)
          </a>
          <a href="/KingAC.jar" download="KingАЧ.jar" className="btn btn-primary btn-sm" title="Скачать нейро-античит KingАЧ v8.1 (12 Схем Инжекта: Спина-360°, Сквозь-Стену, Орбита-Вихрь, Купол +85°, Рой x4, Байт-Хитбокс, Отступ, Надир, Двойная-Стена, Обман, Луч, Каскад + 3D Raycast Хитбокса, GCD, TriggerBot, Timer, GuiMove, Phase, AntiKB, Живой Монитор-HUD и Авто-Бан 24/7). Команды: /ac /kac /kingac /inject /ач /античит /report">
            <Download size={12} /> KingАЧ v8.1 (.jar)
          </a>
          <a href="/KingClear.jar" download className="btn btn-ghost btn-sm" title="Скачать отдельный плагин очистки инвентаря KingClear.jar (/clear, /ci, /clear inv, /clear trash, /clear gui, /clear undo)">
            <Download size={12} /> KingClear (.jar)
          </a>
          <a href="/kinggrief-admin-panel.zip" download className="btn btn-ghost btn-sm" title="Скачать архив Сайта и Веб Админ-Панели v5.9">
            <Download size={12} /> Админ-Панель (.zip)
          </a>
        </div>
      }
    >
      {/* Строка диагностики сервера */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-white/8 bg-void/40 px-3.5 py-2 text-[0.68rem] text-white/55">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span>
            IP: <strong className="font-mono text-white">kinggrief.pickme.cloud</strong>
          </span>
          {bridge?.mcVersion && (
            <span>
              Ядро: <strong className="font-mono text-white/85">{bridge.mcVersion}</strong>
            </span>
          )}
          {bridge?.plugins && (
            <span>
              Плагины: <strong className="text-emerald">{bridge.plugins}</strong>
            </span>
          )}
          {bridge?.detectedCaseCmd && (
            <span>
              Кейсы: <code className="font-mono text-neon">/{bridge.detectedCaseCmd}</code>
            </span>
          )}
        </div>
        <span className="font-mono text-[0.62rem] text-white/35">
          Выдача без лимитов · опрос каждые 5 сек
        </span>
      </div>

      {/* Переключатель: Кейсы / Привилегии / Рилики / Услуги */}
      <div className="mb-4 grid grid-cols-2 gap-1.5 sm:grid-cols-4">
        {(
          [
            { key: 'case', label: 'Кейсы (11 шт.)', icon: Gift },
            { key: 'rank', label: 'Привилегии (LuckPerms)', icon: Crown },
            { key: 'relic', label: 'Рилики / Валюта', icon: Coins },
            { key: 'service', label: 'Разбан / Услуги', icon: ShieldCheck },
          ] as const
        ).map((item) => {
          const Icon = item.icon;
          const active = tab === item.key;
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => setTab(item.key)}
              className={`flex items-center justify-center gap-2 rounded-xl border px-3 py-2.5 text-[0.74rem] font-bold transition ${
                active
                  ? 'border-neon/60 bg-neon/20 text-white shadow-[0_0_20px_rgba(168,85,247,0.2)]'
                  : 'border-white/8 bg-white/[0.02] text-white/50 hover:border-white/15 hover:text-white'
              }`}
            >
              <Icon size={14} className={active ? 'text-neon' : 'text-white/40'} />
              <span className="truncate">{item.label}</span>
            </button>
          );
        })}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.2fr_1fr]">
        {/* Левая колонка: кому и что выдаём */}
        <div className="space-y-3.5 rounded-xl border border-neon/20 bg-gradient-to-br from-neon/[0.07] via-panel/60 to-void/70 p-4">
          {/* Блок выбора ЛЮБОГО ника */}
          <div>
            <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
              <label className="field-label !mb-0 flex items-center gap-1.5">
                <Users size={12} className="text-neon" />
                Никнейм получателя (любой игрок на сервере)
              </label>
              <div className="flex items-center gap-1.5">
                {nick.trim() && nick.trim() !== mySavedNick && (
                  <button
                    type="button"
                    onClick={saveAsMyNick}
                    className="rounded-md border border-white/10 bg-white/5 px-2 py-0.5 text-[0.6rem] text-white/60 hover:text-white"
                  >
                    Запомнить как мой ник
                  </button>
                )}
                {mySavedNick && nick.trim() !== mySavedNick && (
                  <button
                    type="button"
                    onClick={() => setNick(mySavedNick)}
                    className="inline-flex items-center gap-1 rounded-md border border-neon/30 bg-neon/10 px-2 py-0.5 font-mono text-[0.6rem] text-neon hover:bg-neon/20"
                  >
                    <UserCheck size={10} /> Мой: {mySavedNick}
                  </button>
                )}
              </div>
            </div>

            <div className="relative">
              <input
                value={nick}
                onChange={(e) => setNick(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    submitGrant();
                  }
                }}
                placeholder="Введите любой ник: domep444543, _qwerty_70 и др."
                maxLength={16}
                className="field pr-9 font-mono text-[0.86rem]"
              />
              {nick && (
                <button
                  type="button"
                  onClick={() => setNick('')}
                  aria-label="Очистить никнейм"
                  title="Очистить и ввести другой ник"
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-white/35 hover:bg-white/10 hover:text-white"
                >
                  <X size={13} />
                </button>
              )}
            </div>

            {chipNicks.length > 0 && (
              <div className="mt-2 flex flex-wrap items-center gap-1.5">
                <span className="text-[0.6rem] text-white/35">Быстрый выбор ника:</span>
                {chipNicks.map((n) => {
                  const isOnline = onlineSet.has(n.toLowerCase());
                  const selected = nick.trim().toLowerCase() === n.toLowerCase();
                  return (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setNick(n)}
                      className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[0.63rem] transition ${
                        selected
                          ? 'border-neon/60 bg-neon/25 text-white'
                          : isOnline
                            ? 'border-emerald/40 bg-emerald/10 text-emerald hover:bg-emerald/20'
                            : 'border-white/10 bg-white/[0.03] text-white/65 hover:border-white/25 hover:text-white'
                      }`}
                    >
                      {isOnline && <span className="h-1.5 w-1.5 rounded-full bg-emerald" title="Сейчас на сервере" />}
                      {n}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Настройки выбранной вкладки */}
          {tab === 'case' && (
            <div className="space-y-3 border-t border-white/8 pt-3">
              <div className="grid gap-3 sm:grid-cols-[1fr_9.5rem]">
                <div>
                  <div className="mb-1 flex items-center justify-between">
                    <span className="field-label !mb-0">Кейс сервера {'{case}'}</span>
                    <button
                      type="button"
                      onClick={() => setUseCustomCase((v) => !v)}
                      className="font-mono text-[0.6rem] text-neon hover:underline"
                    >
                      {useCustomCase ? '← Выбрать из 11 кейсов' : 'Ввести свой ID'}
                    </button>
                  </div>
                  {useCustomCase ? (
                    <input
                      value={customCaseId}
                      onChange={(e) => setCustomCaseId(e.target.value)}
                      placeholder="Например: donate, sdonate, customcase"
                      className="field font-mono"
                    />
                  ) : (
                    <select value={caseId} onChange={(e) => setCaseId(e.target.value)} className="field">
                      {casesList.map((g) => (
                        <option key={g.id} value={g.id}>
                          {g.name} ({g.id})
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                <div>
                  <span className="field-label">Ключей (шт.)</span>
                  <input
                    type="number"
                    min={1}
                    max={10000}
                    value={caseAmount}
                    onChange={(e) => setCaseAmount(Math.max(1, Math.min(10000, Number(e.target.value) || 1)))}
                    className="field font-mono"
                  />
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-1.5">
                <span className="text-[0.62rem] text-white/35">Количество в 1 клик:</span>
                {[1, 3, 5, 10, 25, 50, 100, 250].map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setCaseAmount(n)}
                    className={`rounded-md border px-2 py-0.5 font-mono text-[0.65rem] transition ${
                      caseAmount === n
                        ? 'border-neon/60 bg-neon/20 text-white'
                        : 'border-white/10 bg-white/[0.03] text-white/50 hover:text-white'
                    }`}
                  >
                    ×{n}
                  </button>
                ))}
              </div>

              <div>
                <div className="mb-1 flex flex-wrap items-center justify-between gap-1">
                  <span className="field-label !mb-0">Команда плагина кейсов</span>
                  <div className="flex flex-wrap gap-1">
                    {CASE_PRESETS.map((p) => (
                      <button
                        key={p.label}
                        type="button"
                        onClick={() => setCaseTpl(p.cmd)}
                        className={`rounded border px-1.5 py-0.5 font-mono text-[0.58rem] transition ${
                          caseTpl.trim() === p.cmd
                            ? 'border-neon/50 bg-neon/15 text-neon'
                            : 'border-white/8 bg-white/[0.02] text-white/40 hover:text-white'
                        }`}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>
                <input
                  value={caseTpl}
                  onChange={(e) => setCaseTpl(e.target.value)}
                  className="field font-mono text-[0.74rem]"
                />
              </div>
            </div>
          )}

          {tab === 'rank' && (
            <div className="space-y-3 border-t border-white/8 pt-3">
              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <span className="field-label !mb-0">Привилегия (группа LuckPerms {'{rank}'})</span>
                  <button
                    type="button"
                    onClick={() => setUseCustomRank((v) => !v)}
                    className="font-mono text-[0.6rem] text-neon hover:underline"
                  >
                    {useCustomRank ? '← Выбрать из списка' : 'Ввести любую группу LuckPerms'}
                  </button>
                </div>

                {useCustomRank ? (
                  <input
                    value={customRankId}
                    onChange={(e) => setCustomRankId(e.target.value)}
                    placeholder="Любая группа LuckPerms: king, dstmoder, downer, helper, admin..."
                    className="field font-mono"
                  />
                ) : (
                  <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
                    {ranksList.map((r) => {
                      const active = rankId === r.id && lpMode !== 'reset';
                      return (
                        <button
                          key={r.id}
                          type="button"
                          onClick={() => {
                            setRankId(r.id);
                            if (lpMode === 'reset') setLpMode('set');
                          }}
                          className={`flex items-center justify-between gap-1.5 rounded-lg border px-2.5 py-1.5 text-left transition ${
                            active
                              ? 'border-neon/70 bg-neon/20 text-white'
                              : 'border-white/8 bg-white/[0.02] text-white/65 hover:border-white/20 hover:text-white'
                          }`}
                        >
                          <span className="flex min-w-0 items-center gap-1.5">
                            <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: r.color }} />
                            <span className="truncate text-[0.7rem] font-bold">{r.name}</span>
                          </span>
                          <span className="font-mono text-[0.55rem] text-white/35">{r.id}</span>
                        </button>
                      );
                    })}
                  </div>
                )}

                {extraLpGroups.length > 0 && (
                  <div className="mt-2 flex flex-wrap items-center gap-1">
                    <span className="text-[0.6rem] text-white/35">Группы на сервере:</span>
                    {extraLpGroups.map((g) => (
                      <button
                        key={g}
                        type="button"
                        onClick={() => {
                          setUseCustomRank(true);
                          setCustomRankId(g);
                        }}
                        className="rounded border border-emerald/30 bg-emerald/10 px-1.5 py-0.5 font-mono text-[0.6rem] text-emerald hover:bg-emerald/20"
                      >
                        {g}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <span className="field-label">Режим выдачи в LuckPerms</span>
                <div className="grid gap-1.5 sm:grid-cols-2">
                  {LP_MODES.map((m) => (
                    <button
                      key={m.key}
                      type="button"
                      onClick={() => setLpMode(m.key)}
                      title={m.desc}
                      className={`rounded-lg border px-2.5 py-1.5 text-left transition ${
                        lpMode === m.key
                          ? 'border-gold/60 bg-gold/15 text-white'
                          : 'border-white/8 bg-white/[0.02] text-white/55 hover:text-white'
                      }`}
                    >
                      <p className="text-[0.7rem] font-bold">{m.label}</p>
                      <p className="line-clamp-1 text-[0.58rem] text-white/40">{m.desc}</p>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {tab === 'relic' && (
            <div className="space-y-3 border-t border-white/8 pt-3">
              <div>
                <span className="field-label">Количество риликов / поинтов</span>
                <input
                  type="number"
                  min={1}
                  max={10000000}
                  value={relicAmount}
                  onChange={(e) => setRelicAmount(Math.max(1, Number(e.target.value) || 1000))}
                  className="field font-mono"
                />
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {[500, 1000, 2500, 5000, 10000, 25000, 50000, 100000].map((amt) => (
                    <button
                      key={amt}
                      type="button"
                      onClick={() => setRelicAmount(amt)}
                      className={`rounded-md border px-2 py-0.5 font-mono text-[0.64rem] transition ${
                        relicAmount === amt
                          ? 'border-neon/60 bg-neon/20 text-white'
                          : 'border-white/10 bg-white/[0.03] text-white/50 hover:text-white'
                      }`}
                    >
                      +{amt.toLocaleString('ru-RU')}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <div className="mb-1 flex flex-wrap items-center justify-between gap-1">
                  <span className="field-label !mb-0">Команда валюты на сервере</span>
                  <div className="flex flex-wrap gap-1">
                    {RELIC_PRESETS.map((p) => (
                      <button
                        key={p.label}
                        type="button"
                        onClick={() => setRelicTpl(p.cmd)}
                        className={`rounded border px-1.5 py-0.5 font-mono text-[0.58rem] transition ${
                          relicTpl.trim() === p.cmd
                            ? 'border-neon/50 bg-neon/15 text-neon'
                            : 'border-white/8 bg-white/[0.02] text-white/40 hover:text-white'
                        }`}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>
                <input
                  value={relicTpl}
                  onChange={(e) => setRelicTpl(e.target.value)}
                  className="field font-mono text-[0.74rem]"
                />
              </div>
            </div>
          )}

          {tab === 'service' && (
            <div className="space-y-3 border-t border-white/8 pt-3">
              <span className="field-label">Выберите действие для игрока</span>
              <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3">
                {(
                  [
                    { key: 'pardon', label: 'Разбан (pardon)' },
                    { key: 'unban', label: 'Разбан (unban)' },
                    { key: 'unmute', label: 'Размут (unmute)' },
                    { key: 'pardon_ip', label: 'Разбан IP' },
                    { key: 'kit', label: 'Выдать кит (kit)' },
                  ] as const
                ).map((act) => (
                  <button
                    key={act.key}
                    type="button"
                    onClick={() => setServiceAction(act.key)}
                    className={`rounded-lg border px-2.5 py-2 text-[0.72rem] font-bold transition ${
                      serviceAction === act.key
                        ? 'border-neon/60 bg-neon/20 text-white'
                        : 'border-white/10 bg-white/[0.03] text-white/55 hover:text-white'
                    }`}
                  >
                    {act.label}
                  </button>
                ))}
              </div>
              {serviceAction === 'kit' && (
                <div>
                  <span className="field-label">Название кита</span>
                  <input
                    value={kitName}
                    onChange={(e) => setKitName(e.target.value)}
                    placeholder="start, king, dmoder..."
                    className="field font-mono"
                  />
                </div>
              )}
            </div>
          )}

          {/* Итоговая команда + кнопка безлимитной выдачи */}
          <div className="rounded-lg border border-white/8 bg-black/35 px-3 py-2">
            <div className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="font-mono text-[0.55rem] uppercase tracking-wider text-white/35">
                  Команда для консоли Minecraft:
                </p>
                <code className="block truncate font-mono text-[0.74rem] text-emerald">/{commandPreview}</code>
              </div>
              <button
                type="button"
                onClick={() => copyCommand(commandPreview)}
                className="btn btn-ghost btn-sm shrink-0"
              >
                {copiedText === commandPreview ? <Check size={12} /> : <Copy size={12} />} Копировать
              </button>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            {(tab === 'case' || tab === 'relic' || (tab === 'rank' && lpMode === 'set')) ? (
              <label className="flex cursor-pointer items-center gap-2 text-[0.7rem] text-white/55">
                <input
                  type="checkbox"
                  checked={saveAsDefault}
                  onChange={(e) => setSaveAsDefault(e.target.checked)}
                  className="accent-[#a855f7]"
                />
                Сохранить шаблон для автопокупок на сайте
              </label>
            ) : (
              <span className="text-[0.66rem] text-white/35">Можно нажимать подряд сколько угодно раз</span>
            )}

            <button
              type="button"
              onClick={submitGrant}
              disabled={submitting}
              className="btn btn-primary"
            >
              <Send size={13} />
              {submitting
                ? 'Отправляем…'
                : tab === 'case'
                  ? `Выдать кейсы ×${caseAmount}`
                  : tab === 'rank'
                    ? lpMode === 'reset'
                      ? 'Сбросить привилегию'
                      : `Выдать ${effectiveRankId.toUpperCase()}`
                    : tab === 'relic'
                      ? `Выдать ${relicAmount.toLocaleString('ru-RU')} риликов`
                      : 'Выполнить на сервере'}
            </button>
          </div>

          {lastResultBanner && (
            <div className="flex items-center justify-between gap-2 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-2 text-[0.7rem] text-emerald">
              <span className="inline-flex items-center gap-1.5">
                <Sparkles size={13} />
                Отправлено #{lastResultBanner.count}: <strong>{lastResultBanner.nick}</strong> ← {lastResultBanner.label}
              </span>
              <span className="font-mono text-[0.6rem] text-emerald/80">можно сразу выдавать дальше →</span>
            </div>
          )}
        </div>

        {/* Правая колонка: живая очередь и история выдач с кнопкой «Выдать ещё раз» */}
        <div className="flex flex-col justify-between rounded-xl border border-white/8 bg-void/40 p-4">
          <div>
            <div className="flex items-center justify-between gap-2">
              <p className="inline-flex items-center gap-1.5 text-[0.76rem] font-bold text-white/85">
                <Terminal size={13} className="text-neon" /> Последние выдачи (живой статус)
              </p>
              <span className="font-mono text-[0.58rem] text-white/35">автообновление 4 сек</span>
            </div>
            <p className="mt-1 text-[0.65rem] text-white/40">
              Не нужно нажимать подтверждение вручную — плагин сам заберёт задачу за ~5 секунд и сменит статус на «Выдано».
              Кнопка <strong>«↻ Ещё раз»</strong> мгновенно повторит ту же выдачу.
            </p>

            {grants.length === 0 ? (
              <p className="py-8 text-center text-[0.72rem] text-white/30">
                Пока нет выдач. Выберите ник любого игрока слева и нажмите «Выдать».
              </p>
            ) : (
              <div className="mt-3 max-h-[24rem] space-y-1.5 overflow-y-auto pr-1">
                {grants.slice(0, 14).map((d) => {
                  const isPending = d.status === 'pending' || d.status === 'sent';
                  const isDelivered = d.status === 'delivered';
                  return (
                    <div
                      key={d.id}
                      className="rounded-lg border border-white/8 bg-panel/60 px-2.5 py-2 text-[0.68rem]"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-1.5">
                        <div className="flex min-w-0 items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => setNick(d.nickname)}
                            title="Подставить этот ник в форму выдачи"
                            className="font-mono font-bold text-white hover:text-neon"
                          >
                            {d.nickname}
                          </button>
                          <Pill
                            color={
                              isDelivered
                                ? '#22C55E'
                                : isPending
                                  ? '#F59E0B'
                                  : '#EF4444'
                            }
                          >
                            {isDelivered
                              ? '✓ Выдано на сервере'
                              : isPending
                                ? '⏳ Ждёт мост (~5с)'
                                : d.status === 'manual'
                                  ? 'Вручную'
                                  : 'Ошибка'}
                          </Pill>
                        </div>

                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            disabled={busyRowId === d.id}
                            onClick={() => repeatRow(d)}
                            title="Создать новую задачу и выдать это же ещё раз"
                            className="inline-flex items-center gap-1 rounded-md border border-neon/35 bg-neon/10 px-2 py-0.5 text-[0.62rem] font-semibold text-neon hover:bg-neon/25 disabled:opacity-40"
                          >
                            <RotateCcw size={10} /> Ещё раз
                          </button>
                          {!isDelivered && !isPending && (
                            <button
                              type="button"
                              disabled={busyRowId === d.id}
                              onClick={() => requeueFailedRow(d)}
                              className="rounded-md border border-amber/35 bg-amber/10 px-2 py-0.5 text-[0.62rem] text-amber hover:bg-amber/20"
                            >
                              В очередь
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => copyCommand(d.executedCommand || d.command)}
                            title="Скопировать команду"
                            className="btn btn-ghost btn-icon !h-6 !w-6"
                          >
                            {copiedText === (d.executedCommand || d.command) ? (
                              <Check size={11} className="text-emerald" />
                            ) : (
                              <Copy size={11} />
                            )}
                          </button>
                        </div>
                      </div>

                      <code className="mt-1 block truncate font-mono text-[0.63rem] text-emerald/90">
                        /{d.executedCommand || d.command}
                      </code>
                      <div className="mt-0.5 flex items-center justify-between font-mono text-[0.55rem] text-white/30">
                        <span>{formatDate(d.createdAt)}</span>
                        {d.deliveredAt && <span className="text-emerald/75">доставлено {formatDate(d.deliveredAt)}</span>}
                      </div>
                      {d.lastError && <p className="mt-0.5 text-[0.6rem] text-[#fca5a5]">{d.lastError}</p>}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </AdminCard>
  );
}
