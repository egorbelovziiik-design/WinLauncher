'use client';

import { useEffect, useState } from 'react';
import { Activity, Clock, Signal, Users } from 'lucide-react';
import { DiscordGlyph, TelegramGlyph } from './icons';
import { relativeTime } from '@/lib/utils';

export function OnlinePanel({
  online: initialOnline,
  max: initialMax,
  connected: initialConnected,
  version,
  discord,
  telegram,
  lastSeen: initialLastSeen,
}: {
  online: number;
  max: number;
  connected: boolean;
  version: string;
  discord: string;
  telegram: string;
  lastSeen: string | null;
}) {
  const [state, setState] = useState({
    online: initialOnline,
    max: initialMax,
    connected: initialConnected,
    lastSeen: initialLastSeen,
  });

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const res = await fetch('/api/status', { cache: 'no-store' });
        if (!res.ok) return;
        const data = (await res.json()) as { online: number; max: number; connected: boolean; lastSeen: string | null };
        if (alive) setState({ online: data.online, max: data.max, connected: data.connected, lastSeen: data.lastSeen });
      } catch {
        /* ignore */
      }
    };
    const timer = setInterval(load, 30_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  const hasData = state.max > 0;
  const percent = hasData ? Math.min(100, Math.round((state.online / state.max) * 100)) : 0;

  return (
    <div className="relative h-full overflow-hidden rounded-2xl border border-white/8 bg-panel/40 p-6">
      <div className="pointer-events-none absolute -right-16 -bottom-16 h-52 w-52 rounded-full bg-emerald/10 blur-3xl" />
      <div className="flex items-center justify-between">
        <p className="eyebrow">Статус сервера</p>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 font-mono text-[0.56rem] font-bold uppercase tracking-widest ${
            state.connected ? 'bg-emerald/12 text-emerald' : 'bg-amber/12 text-amber'
          }`}
        >
          <Signal size={10} /> {state.connected ? 'live' : 'скоро'}
        </span>
      </div>

      <div className="mt-5 flex items-end justify-between">
        <div>
          <p className="stat-num text-4xl font-extrabold leading-none text-white">
            {hasData ? state.online.toLocaleString('ru-RU') : '—'}
          </p>
          <p className="mt-1 font-mono text-[0.6rem] uppercase tracking-widest text-white/30">
            {hasData ? `игроков из ${state.max.toLocaleString('ru-RU')}` : 'данные с сервера'}
          </p>
        </div>
        <div className="text-right">
          <p className="stat-num text-lg font-bold text-emerald">{hasData ? `${percent}%` : '—'}</p>
          <p className="font-mono text-[0.55rem] uppercase tracking-widest text-white/25">заполнено</p>
        </div>
      </div>

      <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/6">
        <div
          className="h-full rounded-full transition-[width] duration-700"
          style={{
            width: `${hasData ? Math.max(percent, 3) : 0}%`,
            background: 'linear-gradient(90deg, #22c55e, #a3e635)',
            boxShadow: '0 0 14px rgba(34,197,94,0.6)',
          }}
        />
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-2 text-[0.72rem]">
        <div className="flex items-center gap-2 rounded-lg border border-white/6 bg-void/40 px-2.5 py-2">
          <Activity size={13} className="text-neon" />
          <div>
            <dt className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">версия</dt>
            <dd className="font-semibold text-white/75">{version}</dd>
          </div>
        </div>
        <div className="flex items-center gap-2 rounded-lg border border-white/6 bg-void/40 px-2.5 py-2">
          <Users size={13} className="text-neon" />
          <div>
            <dt className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">режимы</dt>
            <dd className="font-semibold text-white/75">Grief · PvP</dd>
          </div>
        </div>
      </dl>

      <div className="mt-4 flex items-center justify-between gap-2 border-t border-white/6 pt-4">
        <span className="inline-flex items-center gap-1.5 font-mono text-[0.58rem] text-white/25">
          <Clock size={11} />
          {state.connected && state.lastSeen
            ? `обновлено ${relativeTime(state.lastSeen)}`
            : 'мост Minecraft не подключён'}
        </span>
        <div className="flex gap-2">
          <a href={discord} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon !h-8 !w-8" aria-label="Discord">
            <DiscordGlyph size={14} />
          </a>
          <a href={telegram} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon !h-8 !w-8" aria-label="Telegram">
            <TelegramGlyph size={14} />
          </a>
        </div>
      </div>
    </div>
  );
}
