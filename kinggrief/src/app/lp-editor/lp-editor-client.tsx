'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';

interface PermEntry {
  node: string;
  value: boolean;
}

interface LpGroup {
  id: string;
  label: string;
  weight: number;
  prefix: string;
  tier: string;
  perms: PermEntry[];
  isCustom?: boolean;
}

interface LpUser {
  nick: string;
  primaryGroup: string;
  customPrefix: string;
  perms: PermEntry[];
}

const INITIAL_GROUPS: LpGroup[] = [
  { id: 'owner', label: 'OWNER', weight: 300, prefix: '&#FF1744&lOWNER &f', tier: 'Владелец', perms: [{ node: '*', value: true }, { node: 'kinggrief.spawn.edit', value: true }, { node: 'kinggrief.lp', value: true }] },
  { id: 'techadmin', label: 'Тех.Админ', weight: 290, prefix: '&#00E5FF&l[Тех.Админ] &f', tier: 'КП', perms: [{ node: 'kinggrief.admin', value: true }, { node: 'kinggrief.staff', value: true }] },
  { id: 'curator', label: 'CURATOR', weight: 275, prefix: '&#22C55E&lCURATOR &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.vanish', value: true }] },
  { id: 'admin', label: 'Админ (КП)', weight: 268, prefix: '&#EF4444&l[Админ] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.ban', value: true }, { node: 'kinggrief.events', value: true }] },
  { id: 'mladmin', label: 'Мл.Админ (КП)', weight: 260, prefix: '&#F97316&l[Мл.Админ] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.ban', value: true }] },
  { id: 'glmoder', label: 'Гл.Модер', weight: 250, prefix: '&#7C3AED&l[Гл.Модер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.ban', value: true }] },
  { id: 'stmoder', label: 'Ст.Модер', weight: 240, prefix: '&#4F46E5&l[Ст.Модер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.mute', value: true }] },
  { id: 'moder', label: 'Модер', weight: 230, prefix: '&#2563EB&l[Модер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.tphere', value: true }] },
  { id: 'mlmoder', label: 'Мл.Модер', weight: 220, prefix: '&#38BDF8&l[Мл.Модер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }, { node: 'kinggrief.tphere', value: true }] },
  { id: 'sthelper', label: 'Ст.Хелпер', weight: 214, prefix: '&#0EA5E9&l[Ст.Хелпер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }] },
  { id: 'helper', label: 'Хелпер', weight: 208, prefix: '&#22C55E&l[Хелпер] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }] },
  { id: 'kp', label: 'КП (Стажёр КП)', weight: 200, prefix: '&#EF4444&l[КП] &f', tier: 'КП', perms: [{ node: 'kinggrief.staff', value: true }] },
  { id: 'downer', label: 'D.OWNER (1499₽)', weight: 190, prefix: '&#FACC15&lD.OWNER &f', tier: 'Донат-Админ', perms: [{ node: 'kinggrief.gm', value: true }, { node: 'kinggrief.fly', value: true }, { node: 'kinggrief.events', value: true }] },
  { id: 'opka', label: 'OPKA (999₽)', weight: 178, prefix: '&#C026D3&lOPKA &f', tier: 'Донат-Админ', perms: [{ node: 'kinggrief.gm', value: true }, { node: 'kinggrief.fly', value: true }, { node: 'kinggrief.events', value: true }] },
  { id: 'dadmin', label: 'D.ADMIN (799₽)', weight: 162, prefix: '&#DC2626&lD.ADMIN &f', tier: 'ДКП', perms: [{ node: 'kinggrief.events', value: true }, { node: 'kinggrief.fly', value: true }] },
  { id: 'dmladmin', label: 'D.ML.ADMIN (599₽)', weight: 152, prefix: '&#F97316&lD.ML.ADMIN &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'dglmoder', label: 'D.GLMODER (449₽)', weight: 142, prefix: '&#8B5CF6&lD.GLMODER &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'dstmoder', label: 'D.ST.MODER (349₽)', weight: 132, prefix: '&#3B82F6&lD.ST.MODER &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'dmoder', label: 'D.MODER (249₽)', weight: 124, prefix: '&#14B8A6&lD.MODER &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'dkp', label: 'D.KP (229₽)', weight: 118, prefix: '&#E879F9&lD.KP &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'dhelper', label: 'D.HELPER (219₽)', weight: 112, prefix: '&#2DD4BF&lD.HELPER &f', tier: 'ДКП', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'mediaplus', label: 'MEDIA+', weight: 108, prefix: '&#F43F5E&lMEDIA+ &f', tier: 'Медиа', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'media', label: 'MEDIA', weight: 106, prefix: '&#EC4899&lMEDIA &f', tier: 'Медиа', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'yt', label: 'YOUTUBE', weight: 104, prefix: '&#FF0000&lYOUTUBE &f', tier: 'Медиа', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'twitch', label: 'TWITCH', weight: 102, prefix: '&#9146FF&lTWITCH &f', tier: 'Медиа', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'tiktok', label: 'TikTok', weight: 100, prefix: '&#FF0050&lTikTok &f', tier: 'Медиа', perms: [{ node: 'kinggrief.fly', value: true }] },
  { id: 'sponsor', label: 'SPONSOR (215₽)', weight: 92, prefix: '&#38BDF8&lSPONSOR &f', tier: 'Донат', perms: [{ node: 'kinggrief.fly', value: true }, { node: 'kinggrief.repair', value: true }] },
  { id: 'king', label: 'KING (199₽)', weight: 85, prefix: '&#A855F7&lKING &f', tier: 'Донат', perms: [{ node: 'kinggrief.fly', value: true }, { node: 'kinggrief.repair', value: true }] },
  { id: 'imperator', label: 'IMPERATOR (179₽)', weight: 75, prefix: '&#F43F5E&lIMPERATOR &f', tier: 'Донат', perms: [{ node: 'kinggrief.fly', value: true }, { node: 'kinggrief.repair', value: true }] },
  { id: 'god', label: 'GOD (149₽)', weight: 65, prefix: '&#FACC15&lGOD &f', tier: 'Донат', perms: [{ node: 'kinggrief.heal', value: true }, { node: 'kinggrief.enderchest', value: true }] },
  { id: 'tiger', label: 'TIGER (119₽)', weight: 58, prefix: '&#FB923C&lTIGER &f', tier: 'Донат', perms: [{ node: 'kinggrief.feed', value: true }] },
  { id: 'vampire', label: 'VAMPIRE (99₽)', weight: 52, prefix: '&#EF4444&lVAMPIRE &f', tier: 'Донат', perms: [{ node: 'kinggrief.feed', value: true }] },
  { id: 'rabbit', label: 'RABBIT (89₽)', weight: 45, prefix: '&#38BDF8&lRABBIT &f', tier: 'Донат', perms: [{ node: 'kinggrief.workbench', value: true }] },
  { id: 'bunny', label: 'BUNNY (59₽)', weight: 36, prefix: '&#EC4899&lBUNNY &f', tier: 'Донат', perms: [{ node: 'kinggrief.workbench', value: true }] },
  { id: 'dracula', label: 'DRACULA (49₽)', weight: 30, prefix: '&#DC2626&lDRACULA &f', tier: 'Донат', perms: [{ node: 'kinggrief.enderchest', value: true }] },
  { id: 'cobra', label: 'COBRA (39₽)', weight: 24, prefix: '&#84CC16&lCOBRA &f', tier: 'Донат', perms: [] },
  { id: 'pegas', label: 'PEGAS (19₽)', weight: 18, prefix: '&#22C55E&lPEGAS &f', tier: 'Донат', perms: [] },
  { id: 'dragon', label: 'DRAGON (15₽ / /free dragon)', weight: 12, prefix: '&#D946EF&lDRAGON &f', tier: 'Донат', perms: [] },
  { id: 'default', label: 'Игрок (default)', weight: 5, prefix: '&#B0BEC5', tier: 'Базовая', perms: [] },
];

const QUICK_PERMS = [
  '*',
  'kinggrief.admin',
  'kinggrief.lp',
  'kinggrief.spawn.edit',
  'kinggrief.staff',
  'kinggrief.fly',
  'kinggrief.gm',
  'kinggrief.vanish',
  'kinggrief.heal',
  'kinggrief.feed',
  'kinggrief.repair',
  'kinggrief.enderchest',
  'kinggrief.workbench',
  'kinggrief.ban',
  'kinggrief.mute',
];

function toBase64Url(str: string): string {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function LpEditorClient() {
  const [groups, setGroups] = useState<LpGroup[]>(INITIAL_GROUPS);
  const [users, setUsers] = useState<LpUser[]>([
    { nick: 'domep444543', primaryGroup: 'owner', customPrefix: '', perms: [{ node: '*', value: true }] },
  ]);
  const [tab, setTab] = useState<'groups' | 'users' | 'tracks'>('groups');
  const [selectedGroupId, setSelectedGroupId] = useState<string>('owner');
  const [selectedUserNick, setSelectedUserNick] = useState<string>('domep444543');
  const [search, setSearch] = useState('');
  const [newPermNode, setNewPermNode] = useState('');
  const [newUserNick, setNewUserNick] = useState('');
  const [newGroupId, setNewGroupId] = useState('');
  const [commandsLog, setCommandsLog] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [cloudStatus, setCloudStatus] = useState<string | null>(null);

  const pushCmd = (cmd: string) => {
    setCommandsLog((prev) => [...prev.filter((x) => x !== cmd), cmd]);
  };

  const activeGroup = useMemo(
    () => groups.find((g) => g.id === selectedGroupId) ?? groups[0],
    [groups, selectedGroupId],
  );

  const activeUser = useMemo(
    () => users.find((u) => u.nick === selectedUserNick) ?? users[0],
    [users, selectedUserNick],
  );

  const filteredGroups = useMemo(
    () =>
      groups.filter(
        (g) =>
          g.id.toLowerCase().includes(search.toLowerCase()) ||
          g.label.toLowerCase().includes(search.toLowerCase()),
      ),
    [groups, search],
  );

  const applyCommandString = useMemo(() => {
    if (commandsLog.length === 0) return '';
    return `/lp applyedits LP4_${toBase64Url(commandsLog.join('\n'))}`;
  }, [commandsLog]);

  const addGroupPermission = (nodeRaw: string) => {
    const node = nodeRaw.trim().toLowerCase();
    if (!node || !activeGroup) return;
    setGroups((prev) =>
      prev.map((g) =>
        g.id === activeGroup.id
          ? { ...g, perms: [...g.perms.filter((p) => p.node !== node), { node, value: true }] }
          : g,
      ),
    );
    pushCmd(`lp group ${activeGroup.id} permission set ${node} true`);
    setNewPermNode('');
  };

  const toggleGroupPermission = (node: string, nextVal: boolean) => {
    if (!activeGroup) return;
    setGroups((prev) =>
      prev.map((g) =>
        g.id === activeGroup.id
          ? { ...g, perms: g.perms.map((p) => (p.node === node ? { ...p, value: nextVal } : p)) }
          : g,
      ),
    );
    pushCmd(`lp group ${activeGroup.id} permission set ${node} ${nextVal}`);
  };

  const removeGroupPermission = (node: string) => {
    if (!activeGroup) return;
    setGroups((prev) =>
      prev.map((g) =>
        g.id === activeGroup.id ? { ...g, perms: g.perms.filter((p) => p.node !== node) } : g,
      ),
    );
    pushCmd(`lp group ${activeGroup.id} permission unset ${node}`);
  };

  const updateGroupMeta = (weight: number, prefix: string) => {
    if (!activeGroup) return;
    setGroups((prev) =>
      prev.map((g) => (g.id === activeGroup.id ? { ...g, weight, prefix } : g)),
    );
    pushCmd(`lp group ${activeGroup.id} setweight ${weight}`);
    pushCmd(`lp group ${activeGroup.id} meta setprefix "${prefix}"`);
  };

  const handleCreateGroup = () => {
    const clean = newGroupId.trim().toLowerCase().replace(/[^a-z0-9_.-]/g, '');
    if (!clean || groups.some((g) => g.id === clean)) return;
    const created: LpGroup = {
      id: clean,
      label: clean.toUpperCase(),
      weight: 28,
      prefix: `&#38BDF8&l[${clean.toUpperCase()}] &f`,
      tier: 'Кастомная',
      perms: [],
      isCustom: true,
    };
    setGroups((prev) => [created, ...prev]);
    setSelectedGroupId(clean);
    pushCmd(`lp creategroup ${clean} 28 ${created.prefix}`);
    setNewGroupId('');
  };

  const handleAddUser = () => {
    const clean = newUserNick.trim();
    if (!clean) return;
    if (!users.some((u) => u.nick.toLowerCase() === clean.toLowerCase())) {
      setUsers((prev) => [...prev, { nick: clean, primaryGroup: 'default', customPrefix: '', perms: [] }]);
    }
    setSelectedUserNick(clean);
    setTab('users');
    setNewUserNick('');
  };

  const updateUserGroup = (group: string) => {
    if (!activeUser) return;
    setUsers((prev) =>
      prev.map((u) => (u.nick === activeUser.nick ? { ...u, primaryGroup: group } : u)),
    );
    pushCmd(`lp user ${activeUser.nick} parent set ${group}`);
  };

  const addUserPerm = (nodeRaw: string) => {
    const node = nodeRaw.trim().toLowerCase();
    if (!node || !activeUser) return;
    setUsers((prev) =>
      prev.map((u) =>
        u.nick === activeUser.nick
          ? { ...u, perms: [...u.perms.filter((p) => p.node !== node), { node, value: true }] }
          : u,
      ),
    );
    pushCmd(`lp user ${activeUser.nick} permission set ${node} true`);
    setNewPermNode('');
  };

  const copyCommand = async () => {
    if (!applyCommandString) return;
    await navigator.clipboard.writeText(applyCommandString);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  const pushViaCloud = async () => {
    if (commandsLog.length === 0) return;
    setCloudStatus('Отправка в очередь сервера...');
    try {
      const res = await fetch('/api/lp-editor', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          commands: commandsLog,
          targetNick: tab === 'users' && activeUser ? activeUser.nick : 'CONSOLE',
        }),
      });
      const data = (await res.json()) as { ok?: boolean; queued?: number; error?: string };
      if (data.ok) {
        setCloudStatus(`✔ Отправлено команд в мост сервера: ${data.queued}!`);
      } else {
        setCloudStatus(`ℹ ${data.error ?? 'Скопируйте команду /lp applyedits ниже!'}`);
      }
    } catch {
      setCloudStatus('ℹ Вставьте скопированную команду /lp applyedits в чат Minecraft!');
    }
  };

  return (
    <div className="min-h-screen bg-[#090d14] text-slate-100 flex flex-col">
      {/* Top LuckPerms Header */}
      <header className="border-b border-emerald-500/25 bg-[#0d1420]/95 backdrop-blur px-6 py-3.5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/20 border border-emerald-400/40 flex items-center justify-center text-emerald-400 font-black text-lg">
            LP
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-black tracking-wide text-emerald-400 text-lg">KINGGRIEF LP EDITOR</span>
              <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                v4.0 ReallyWorld
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Веб-редактор 40 привилегий, префиксов, весов и прав · Работает с <code className="text-emerald-300">/lp editor</code> и <code className="text-emerald-300">/lp applyedits</code>
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Link
            href="/"
            className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 transition"
          >
            ← В магазин KINGGRIEF
          </Link>
          <a
            href="/rw-newyear-full-assembly-v7.2.0.zip"
            className="px-3.5 py-2 rounded-lg bg-sky-500/20 border border-sky-400/40 hover:bg-sky-500/30 text-xs font-bold text-sky-300 transition"
          >
            ⬇ Скачать сборку KINGGRIEF v7.2.0
          </a>
          <button
            onClick={copyCommand}
            disabled={commandsLog.length === 0}
            className="px-4 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 disabled:opacity-40 text-slate-950 font-extrabold text-xs transition"
          >
            {copied ? '✔ СКОПИРОВАНО В БУФЕР!' : `💾 Скопировать /lp applyedits (${commandsLog.length})`}
          </button>
          <button
            onClick={pushViaCloud}
            disabled={commandsLog.length === 0}
            className="px-3.5 py-2 rounded-lg bg-amber-500/20 border border-amber-400/50 hover:bg-amber-500/30 disabled:opacity-40 text-amber-300 font-bold text-xs transition"
          >
            ⚡ Отправить на сервер (Cloud)
          </button>
        </div>
      </header>

      {/* Apply-Edits Banner when changes exist */}
      {commandsLog.length > 0 && (
        <div className="bg-emerald-950/50 border-b border-emerald-500/30 px-6 py-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex-1 min-w-[260px]">
            <div className="text-xs font-bold text-emerald-300 mb-1">
              Готовая команда для вставки в чат или консоль Minecraft ({commandsLog.length} изм.):
            </div>
            <input
              readOnly
              value={applyCommandString}
              onClick={(e) => (e.target as HTMLInputElement).select()}
              className="w-full bg-slate-950 border border-emerald-500/40 rounded-lg px-3 py-1.5 text-xs font-mono text-emerald-200"
            />
          </div>
          <div className="flex items-center gap-2">
            {cloudStatus && <span className="text-xs text-amber-300">{cloudStatus}</span>}
            <button
              onClick={() => setCommandsLog([])}
              className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-xs text-slate-300"
            >
              Очистить лог
            </button>
          </div>
        </div>
      )}

      {/* Main Workspace */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-12">
        {/* Sidebar */}
        <aside className="lg:col-span-3 border-r border-slate-800 bg-[#0c121d] p-4 flex flex-col gap-4">
          <div className="grid grid-cols-3 gap-1.5 bg-slate-900 p-1 rounded-xl">
            <button
              onClick={() => setTab('groups')}
              className={`py-2 rounded-lg text-xs font-bold transition ${tab === 'groups' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:text-white'}`}
            >
              Группы ({groups.length})
            </button>
            <button
              onClick={() => setTab('users')}
              className={`py-2 rounded-lg text-xs font-bold transition ${tab === 'users' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:text-white'}`}
            >
              Игроки ({users.length})
            </button>
            <button
              onClick={() => setTab('tracks')}
              className={`py-2 rounded-lg text-xs font-bold transition ${tab === 'tracks' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:text-white'}`}
            >
              Ветки (4)
            </button>
          </div>

          {tab === 'groups' && (
            <>
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Поиск группы (owner, king, opka...)"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white"
              />
              <div className="flex gap-1.5">
                <input
                  value={newGroupId}
                  onChange={(e) => setNewGroupId(e.target.value)}
                  placeholder="Новая группа (vipplus)"
                  className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                />
                <button
                  onClick={handleCreateGroup}
                  className="px-3 py-1.5 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs font-bold hover:bg-emerald-500/30"
                >
                  + Создать
                </button>
              </div>
              <div className="flex-1 overflow-y-auto max-h-[540px] space-y-1 pr-1">
                {filteredGroups.map((g) => (
                  <button
                    key={g.id}
                    onClick={() => setSelectedGroupId(g.id)}
                    className={`w-full text-left px-3 py-2 rounded-lg flex items-center justify-between text-xs transition ${
                      selectedGroupId === g.id
                        ? 'bg-emerald-500/20 border border-emerald-400/50 text-emerald-200 font-bold'
                        : 'hover:bg-slate-800/70 text-slate-300'
                    }`}
                  >
                    <span>
                      <span className="text-emerald-400 font-mono mr-2">{g.id}</span>
                      <span className="text-slate-400">{g.label}</span>
                    </span>
                    <span className="text-[11px] px-1.5 py-0.5 rounded bg-slate-900 text-slate-400">
                      w:{g.weight}
                    </span>
                  </button>
                ))}
              </div>
            </>
          )}

          {tab === 'users' && (
            <>
              <div className="flex gap-1.5">
                <input
                  value={newUserNick}
                  onChange={(e) => setNewUserNick(e.target.value)}
                  placeholder="Ник игрока..."
                  className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs"
                />
                <button
                  onClick={handleAddUser}
                  className="px-3 py-1.5 rounded-lg bg-emerald-500 text-slate-950 text-xs font-extrabold"
                >
                  + Игрок
                </button>
              </div>
              <div className="space-y-1">
                {users.map((u) => (
                  <button
                    key={u.nick}
                    onClick={() => setSelectedUserNick(u.nick)}
                    className={`w-full text-left px-3 py-2.5 rounded-lg flex items-center justify-between text-xs ${
                      selectedUserNick === u.nick
                        ? 'bg-emerald-500/20 border border-emerald-400/50 text-white font-bold'
                        : 'hover:bg-slate-800 text-slate-300'
                    }`}
                  >
                    <span>👤 {u.nick}</span>
                    <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono">
                      {u.primaryGroup}
                    </span>
                  </button>
                ))}
              </div>
            </>
          )}

          {tab === 'tracks' && (
            <div className="space-y-3 text-xs">
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <div className="font-bold text-amber-300 mb-1">Ветка: donate</div>
                <p className="text-slate-400 leading-relaxed">
                  default → dragon → pegas → cobra → dracula → bunny → rabbit → vampire → tiger → god → imperator → king → sponsor
                </p>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <div className="font-bold text-orange-400 mb-1">Ветка: dkp</div>
                <p className="text-slate-400 leading-relaxed">
                  dhelper → dkp → dmoder → dstmoder → dglmoder → dmladmin → dadmin → opka → downer
                </p>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <div className="font-bold text-emerald-400 mb-1">Ветка: kp</div>
                <p className="text-slate-400 leading-relaxed">
                  kp → helper → sthelper → mlmoder → moder → stmoder → glmoder → mladmin → admin → curator → techadmin → owner
                </p>
              </div>
            </div>
          )}
        </aside>

        {/* Main Editor Area */}
        <main className="lg:col-span-9 p-6 space-y-6">
          {tab === 'groups' && activeGroup && (
            <>
              <div className="p-5 rounded-2xl bg-[#101827] border border-slate-800 flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="text-xs uppercase tracking-wider text-emerald-400 font-bold">
                    Редактирование группы привилегии · {activeGroup.tier}
                  </div>
                  <h1 className="text-2xl font-black mt-0.5">
                    {activeGroup.label}{' '}
                    <span className="text-sm font-mono text-slate-400">({activeGroup.id})</span>
                  </h1>
                </div>

                <div className="flex flex-wrap items-end gap-3">
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Вес (Weight)</label>
                    <input
                      type="number"
                      value={activeGroup.weight}
                      onChange={(e) =>
                        updateGroupMeta(Number(e.target.value) || 10, activeGroup.prefix)
                      }
                      className="w-24 bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs font-mono"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">
                      Префикс чата и ТАБа (HEX &#RRGGBB)
                    </label>
                    <input
                      value={activeGroup.prefix}
                      onChange={(e) => updateGroupMeta(activeGroup.weight, e.target.value)}
                      className="w-72 bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs font-mono"
                    />
                  </div>
                </div>
              </div>

              {/* Add Permission Bar */}
              <div className="p-5 rounded-2xl bg-[#101827] border border-slate-800 space-y-3">
                <div className="text-sm font-bold text-slate-200">Добавить право группе (Permission Node)</div>
                <div className="flex gap-2">
                  <input
                    value={newPermNode}
                    onChange={(e) => setNewPermNode(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && addGroupPermission(newPermNode)}
                    placeholder="Например: kinggrief.fly или essentials.god или *"
                    className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-sm font-mono"
                  />
                  <button
                    onClick={() => addGroupPermission(newPermNode)}
                    className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-sm"
                  >
                    + Добавить право
                  </button>
                </div>
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {QUICK_PERMS.map((qp) => (
                    <button
                      key={qp}
                      onClick={() => addGroupPermission(qp)}
                      className="px-2.5 py-1 rounded-md bg-slate-900 hover:bg-emerald-500/20 border border-slate-700 hover:border-emerald-500/40 text-xs font-mono text-slate-300 hover:text-emerald-300 transition"
                    >
                      + {qp}
                    </button>
                  ))}
                </div>
              </div>

              {/* Permissions Table */}
              <div className="rounded-2xl bg-[#101827] border border-slate-800 overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-800 flex items-center justify-between text-xs font-bold text-slate-400">
                  <span>УЗЕЛ ПРАВА (PERMISSION)</span>
                  <span>ЗНАЧЕНИЕ (TRUE / FALSE)</span>
                </div>
                {activeGroup.perms.length === 0 ? (
                  <div className="p-8 text-center text-sm text-slate-500">
                    У группы пока только базовые встроенные права ранга. Добавьте дополнительные права выше!
                  </div>
                ) : (
                  <div className="divide-y divide-slate-800/70">
                    {activeGroup.perms.map((p) => (
                      <div key={p.node} className="px-5 py-3 flex items-center justify-between">
                        <span className="font-mono text-sm text-emerald-300">{p.node}</span>
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => toggleGroupPermission(p.node, !p.value)}
                            className={`px-3 py-1 rounded-lg text-xs font-extrabold ${
                              p.value
                                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                                : 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
                            }`}
                          >
                            {p.value ? 'TRUE' : 'FALSE'}
                          </button>
                          <button
                            onClick={() => removeGroupPermission(p.node)}
                            className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-rose-500/30 text-slate-400 hover:text-rose-200 text-xs"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}

          {tab === 'users' && activeUser && (
            <div className="space-y-5">
              <div className="p-5 rounded-2xl bg-[#101827] border border-slate-800 flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="text-xs text-emerald-400 font-bold uppercase">Управление игроком</div>
                  <h2 className="text-2xl font-black">👤 {activeUser.nick}</h2>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Привилегия (parent set)</label>
                    <select
                      value={activeUser.primaryGroup}
                      onChange={(e) => updateUserGroup(e.target.value)}
                      className="bg-slate-950 border border-emerald-500/40 rounded-lg px-3 py-2 text-xs font-bold text-emerald-300"
                    >
                      {groups.map((g) => (
                        <option key={g.id} value={g.id}>
                          {g.id.toUpperCase()} — {g.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Личный префикс</label>
                    <input
                      value={activeUser.customPrefix}
                      onChange={(e) => {
                        const v = e.target.value;
                        setUsers((prev) =>
                          prev.map((u) => (u.nick === activeUser.nick ? { ...u, customPrefix: v } : u)),
                        );
                        pushCmd(
                          v.trim()
                            ? `lp user ${activeUser.nick} meta setprefix "${v}"`
                            : `lp user ${activeUser.nick} meta clearprefix`,
                        );
                      }}
                      placeholder="Оставьте пустым для префикса группы"
                      className="w-64 bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono"
                    />
                  </div>
                </div>
              </div>

              <div className="p-5 rounded-2xl bg-[#101827] border border-slate-800 space-y-3">
                <div className="text-sm font-bold">Личные права игрока {activeUser.nick}</div>
                <div className="flex gap-2">
                  <input
                    value={newPermNode}
                    onChange={(e) => setNewPermNode(e.target.value)}
                    placeholder="Право игрока (например kinggrief.fly или *)"
                    className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-sm font-mono"
                  />
                  <button
                    onClick={() => addUserPerm(newPermNode)}
                    className="px-4 py-2 rounded-xl bg-emerald-500 text-slate-950 font-extrabold text-xs"
                  >
                    + Выдать право
                  </button>
                </div>
                <div className="space-y-1.5 pt-2">
                  {activeUser.perms.map((p) => (
                    <div
                      key={p.node}
                      className="px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between text-xs font-mono"
                    >
                      <span className="text-emerald-300">{p.node}</span>
                      <span className="text-emerald-400 font-bold">TRUE</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {tab === 'tracks' && (
            <div className="p-6 rounded-2xl bg-[#101827] border border-slate-800 space-y-3">
              <h2 className="text-xl font-black text-emerald-400">Иерархия привилегий и защиты Спавна v4.0</h2>
              <p className="text-sm text-slate-300">
                На сервере KINGGRIEF v4.0 настроено 40 рангов ReallyWorld. Ломать и строить блоки в отдельном мире{' '}
                <code className="text-emerald-300">spawn_world</code> может <b>только ранг OWNER в режиме CREATIVE</b>, а все киты в{' '}
                <code className="text-emerald-300">/kits</code> выдаются строго по привилегиям игроков.
              </p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
