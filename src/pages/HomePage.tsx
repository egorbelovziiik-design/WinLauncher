import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronDown,
  CircleCheck,
  CircleDashed,
  Coffee,
  Cpu,
  DownloadCloud,
  Newspaper,
  Play,
  RotateCw,
  Server,
  Square,
  TriangleAlert,
  UserRound,
  Zap,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, IS_TAURI } from '../lib/bridge';
import type { NewsItem } from '../lib/types';
import { navigate } from '../lib/router';
import {
  Badge,
  Button,
  Card,
  Dot,
  EmptyState,
  Page,
  Progress,
  formatBytes,
  formatSpeed,
  cn,
} from '../components/ui';

function useElapsed(startedAt: string | null, active: boolean) {
  const [, tick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [active]);
  if (!startedAt) return '';
  const s = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000));
  const m = Math.floor(s / 60);
  return `${m}:${String(s % 60).padStart(2, '0')}`;
}

function Picker({
  items,
  value,
  onChange,
  placeholder,
  icon,
  wide,
}: {
  items: { id: string; label: string; hint?: string }[];
  value: string | null;
  onChange: (id: string) => void;
  placeholder: string;
  icon?: React.ReactNode;
  wide?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const selected = items.find((i) => i.id === value);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className={cn(
          'flex h-10 items-center gap-2 rounded-xl border border-line/12 bg-black/20 px-3 text-[13px] font-semibold backdrop-blur-md transition-colors hover:bg-black/30',
          wide ? 'w-full justify-between' : 'max-w-[220px]',
        )}
      >
        <span className="text-accent">{icon}</span>
        <span className="truncate">{selected?.label ?? placeholder}</span>
        <ChevronDown size={14} className={cn('shrink-0 text-muted transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <div className="absolute bottom-12 left-0 z-40 w-72 overflow-hidden rounded-xl border border-line/12 bg-panel/95 p-1.5 shadow-soft backdrop-blur-2xl animate-scale-in">
          <div className="scroll-area max-h-64">
            {items.length === 0 && <div className="px-3 py-4 text-center text-xs text-muted">{placeholder}</div>}
            {items.map((item) => (
              <button
                key={item.id}
                onClick={() => {
                  onChange(item.id);
                  setOpen(false);
                }}
                className={cn(
                  'flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left transition-colors hover:bg-ink/8',
                  item.id === value && 'bg-accent/10',
                )}
              >
                <div className="min-w-0">
                  <div className="truncate text-[13px] font-semibold">{item.label}</div>
                  {item.hint && <div className="truncate text-[11px] text-muted">{item.hint}</div>}
                </div>
                {item.id === value && <CircleCheck size={14} className="shrink-0 text-accent" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

const STATE_LABEL: Record<string, string> = {
  idle: 'common.play',
  checking: 'launch.checking',
  downloading: 'launch.downloading',
  installing: 'launch.installing',
  preparingJava: 'launch.preparingJava',
  starting: 'launch.starting',
  running: 'launch.running',
  exited: 'common.playAgain',
  error: 'launch.error',
};

export function HomePage() {
  const t = useT();
  const {
    settings,
    profiles,
    accounts,
    statuses,
    downloads,
    launch,
    progress,
    info,
    setActiveProfile,
    setActiveAccount,
    launchGame,
    stopGame,
    refreshStatus,
    refreshDownloads,
  } = useApp();
  const [news, setNews] = useState<NewsItem[]>([]);
  const [newsLoading, setNewsLoading] = useState(true);
  const activeProfile = profiles.find((p) => p.id === settings.activeProfileId) ?? profiles[0] ?? null;
  const activeAccount = accounts.find((a) => a.id === settings.activeAccountId) ?? accounts[0] ?? null;
  const status = activeProfile ? statuses[activeProfile.id] : undefined;
  const elapsed = useElapsed(launch.startedAt, launch.state === 'running');

  useEffect(() => {
    let alive = true;
    void call<NewsItem[]>('news_list')
      .then((items) => alive && setNews(items.slice(0, 4)))
      .catch(() => {})
      .finally(() => alive && setNewsLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (activeProfile) void refreshStatus(activeProfile.id);
  }, [activeProfile?.id, refreshStatus]);

  useEffect(() => {
    if (launch.state === 'exited' || launch.state === 'error') {
      void refreshDownloads();
    }
  }, [launch.state, refreshDownloads]);

  const busy = ['checking', 'downloading', 'installing', 'preparingJava', 'starting'].includes(launch.state);
  const running = launch.state === 'running';
  const pct = progress.total > 0 ? (progress.current / progress.total) * 100 : 0;
  const activeDownloads = downloads.filter((d) => d.status === 'running' || d.status === 'queued');
  const ready = Boolean(status?.ready);
  const playLabel = busy || running || launch.state === 'error'
    ? t(STATE_LABEL[launch.state] ?? 'common.play')
    : t('common.play');

  const javaLabel = useMemo(() => {
    if (!status) return '—';
    if (status.java) return `Java ${status.java.major}`;
    return t('home.javaMissing');
  }, [status, t]);

  return (
    <Page className="pt-4">
      {/* ---------------------------------------------------------------- */}
      {/* Hero                                                              */}
      {/* ---------------------------------------------------------------- */}
      <div className="relative overflow-hidden rounded-[26px] border border-line/10 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.9)] animate-fade-up">
        <img
          src="/hero.jpg"
          alt=""
          className="absolute inset-0 h-full w-full scale-[1.02] object-cover"
          draggable={false}
        />
        <div className="absolute inset-0 bg-gradient-to-r from-[rgb(var(--hero-fade))]/95 via-[rgb(var(--hero-fade))]/70 to-[rgb(var(--hero-fade))]/10" />
        <div className="absolute inset-0 bg-gradient-to-t from-[rgb(var(--hero-fade))]/95 via-transparent to-[rgb(var(--hero-fade))]/30" />

        <div className="relative flex min-h-[392px] flex-col justify-between gap-6 p-7 md:p-8">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.22em] text-white/55">
                <Zap size={12} className="text-accent" />
                Minecraft Java Edition
              </div>
              <h1 className="mt-2 font-display text-[34px] font-extrabold leading-none tracking-tight text-white drop-shadow-lg">
                WinLaucher
              </h1>
            </div>
            <div className="hidden items-center gap-2 rounded-full border border-white/10 bg-black/25 px-3 py-1.5 text-[11px] font-medium text-white/70 backdrop-blur-md sm:flex">
              <Dot tone={ready ? 'success' : 'warning'} />
              {ready ? t('home.ready') : t('home.notReady')}
            </div>
          </div>

          <div className="flex flex-wrap items-end justify-between gap-6">
            <div className="min-w-0 max-w-[560px] flex-1">
              {activeProfile ? (
                <>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[13px] font-semibold text-white/60">{t('common.profile')}</span>
                    <Picker
                      items={profiles.map((p) => ({
                        id: p.id,
                        label: p.name,
                        hint: `${p.mcVersion} · ${p.loader.kind === 'vanilla' ? 'Vanilla' : p.loader.kind}`,
                      }))}
                      value={activeProfile.id}
                      onChange={(id) => void setActiveProfile(id)}
                      placeholder={t('home.selectProfile')}
                      icon={<Server size={14} />}
                    />
                    <Picker
                      items={accounts.map((a) => ({
                        id: a.id,
                        label: a.name,
                        hint: a.kind === 'microsoft' ? t('accounts.premium') : t('accounts.offlineBadge'),
                      }))}
                      value={activeAccount?.id ?? null}
                      onChange={(id) => void setActiveAccount(id)}
                      placeholder={t('home.selectAccount')}
                      icon={<UserRound size={14} />}
                    />
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <span className="rounded-lg bg-white/10 px-2 py-1 text-[11px] font-bold text-white backdrop-blur-sm">
                      {activeProfile.mcVersion}
                    </span>
                    <span className="rounded-lg bg-white/10 px-2 py-1 text-[11px] font-bold capitalize text-white backdrop-blur-sm">
                      {activeProfile.loader.kind === 'vanilla' ? 'Vanilla' : activeProfile.loader.kind}
                    </span>
                    <span className="rounded-lg bg-white/10 px-2 py-1 text-[11px] font-bold text-white backdrop-blur-sm">
                      {activeProfile.ramMb} MB
                    </span>
                    {status?.loaderName && status.loaderName !== 'Vanilla' && (
                      <span className="rounded-lg bg-accent/25 px-2 py-1 text-[11px] font-bold text-accent">
                        {status.loaderName}
                      </span>
                    )}
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-3">
                    <button
                      onClick={() => (running ? void stopGame() : void launchGame())}
                      disabled={busy}
                      className={cn(
                        'group relative inline-flex h-[60px] min-w-[230px] items-center justify-center gap-3 overflow-hidden rounded-2xl px-8 font-display text-[18px] font-extrabold uppercase tracking-[0.06em] transition-all duration-300 ease-smooth',
                        running
                          ? 'bg-black/45 text-white ring-1 ring-white/25 backdrop-blur-md hover:bg-black/60'
                          : 'text-[#07120b] shadow-[0_20px_50px_-16px_rgba(88,214,141,0.85)] hover:brightness-110 active:scale-[0.985]',
                        busy && 'cursor-wait opacity-95',
                      )}
                      style={
                        running || launch.state === 'error'
                          ? undefined
                          : { backgroundImage: 'linear-gradient(135deg, rgb(var(--c-accent)), rgb(var(--c-accent2)))' }
                      }
                    >
                      {!running && (
                        <span className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/35 to-transparent transition-transform duration-700 group-hover:translate-x-full" />
                      )}
                      {busy ? (
                        <RotateCw size={22} className="animate-spin" />
                      ) : running ? (
                        <Square size={20} />
                      ) : launch.state === 'error' ? (
                        <RotateCw size={20} />
                      ) : (
                        <Play size={22} className="fill-current" />
                      )}
                      {running ? `${playLabel} · ${elapsed}` : playLabel}
                    </button>

                    {status && !status.ready && launch.state === 'idle' && (
                      <div className="max-w-[260px] text-[11px] leading-relaxed text-white/60">
                        {t('home.readyHint')}
                      </div>
                    )}
                  </div>

                  {busy && (
                    <div className="mt-4 max-w-[420px] animate-fade-in">
                      <div className="mb-1.5 flex items-center justify-between text-[11px] font-medium text-white/70">
                        <span className="truncate pr-3">{progress.message || t(STATE_LABEL[launch.state])}</span>
                        <span className="shrink-0">
                          {progress.total > 0
                            ? `${progress.current}/${progress.total}`
                            : progress.totalBytes > 0
                              ? `${formatBytes(progress.bytes)} / ${formatBytes(progress.totalBytes)}`
                              : ''}
                        </span>
                      </div>
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/15">
                        <div
                          className="h-full rounded-full bg-gradient-to-r from-accent to-accent2 transition-[width] duration-300"
                          style={{ width: `${progress.total > 0 ? pct : 8}%` }}
                        />
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div className="max-w-[460px]">
                  <h2 className="font-display text-[24px] font-extrabold text-white">{t('home.noProfiles')}</h2>
                  <p className="mt-1 text-[13px] text-white/65">{t('home.createFirst')}</p>
                  <Button
                    variant="primary"
                    size="lg"
                    className="mt-4"
                    icon={<Play size={18} className="fill-current" />}
                    onClick={() => navigate('versions')}
                  >
                    {t('home.createProfile')}
                  </Button>
                </div>
              )}
            </div>

            {/* Status card */}
            <div className="w-full max-w-[290px] rounded-2xl border border-white/12 bg-black/35 p-4 backdrop-blur-xl">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-white/50">
                  {t('common.status')}
                </span>
                {ready ? (
                  <Badge tone="success" className="bg-success/20 text-success">
                    <CircleCheck size={11} /> {t('launch.ready')}
                  </Badge>
                ) : (
                  <Badge tone="warning" className="bg-warning/20 text-warning">
                    <CircleDashed size={11} /> {t('launch.blocked')}
                  </Badge>
                )}
              </div>
              <div className="mt-3 space-y-2.5">
                <StatusLine
                  icon={<Coffee size={13} />}
                  label={t('home.java')}
                  value={javaLabel}
                  ok={!!status?.java}
                />
                <StatusLine
                  icon={<DownloadCloud size={13} />}
                  label={t('home.files')}
                  value={
                    status
                      ? status.versionInstalled
                        ? `${formatBytes(status.installedBytes)}`
                        : t('home.versionMissing')
                      : '—'
                  }
                  ok={!!status?.versionInstalled}
                />
                <StatusLine
                  icon={<UserRound size={13} />}
                  label={t('home.account')}
                  value={activeAccount?.name ?? t('home.selectAccount')}
                  ok={!!activeAccount}
                />
                <StatusLine
                  icon={<Cpu size={13} />}
                  label={t('home.ram')}
                  value={`${activeProfile?.ramMb ?? settings.defaultRamMb} MB`}
                  ok
                />
              </div>
              {status && status.blocking.length > 0 && !busy && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/20 bg-warning/10 px-2.5 py-2 text-[10.5px] leading-relaxed text-warning/90">
                  <TriangleAlert size={13} className="mt-0.5 shrink-0" />
                  <span>
                    {status.blocking.includes('loader')
                      ? t('versions.loaderHint')
                      : status.blocking.includes('java')
                        ? t('errors.javaMissing', { major: status.requiredJava })
                        : t('home.versionMissing')}
                  </span>
                </div>
              )}
              {!IS_TAURI && (
                <div className="mt-3 rounded-lg border border-accent/20 bg-accent/10 px-2.5 py-2 text-[10.5px] leading-relaxed text-accent/90">
                  {t('common.previewHint')}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ---------------------------------------------------------------- */}
      {/* News + downloads                                                  */}
      {/* ---------------------------------------------------------------- */}
      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-display text-[16px] font-bold">
              <Newspaper size={16} className="text-accent" />
              {t('home.news')}
            </h2>
            <span className="text-[11px] text-muted">minecraft.net</span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {newsLoading &&
              [0, 1].map((i) => <div key={i} className="skeleton h-[136px] rounded-2xl" />)}
            {!newsLoading && news.length === 0 && (
              <div className="sm:col-span-2">
                <EmptyState icon={<Newspaper size={26} />} title={t('home.newsEmpty')} />
              </div>
            )}
            {news.map((item) => (
              <a
                key={item.id}
                href={item.url || 'https://www.minecraft.net'}
                target="_blank"
                rel="noreferrer"
                onClick={(e) => {
                  e.preventDefault();
                  void import('../lib/bridge').then((m) => m.openExternal(item.url || 'https://www.minecraft.net'));
                }}
                className="group relative flex min-h-[136px] flex-col justify-end overflow-hidden rounded-2xl border border-line/10 bg-elevated p-4 transition-all duration-300 hover:-translate-y-0.5 hover:border-accent/30"
              >
                {item.image ? (
                  <img
                    src={item.image}
                    alt=""
                    className="absolute inset-0 h-full w-full object-cover opacity-60 transition-transform duration-500 group-hover:scale-105"
                  />
                ) : (
                  <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_10%,rgb(var(--c-accent)/.28),transparent_60%),radial-gradient(circle_at_10%_90%,rgb(var(--c-accent2)/.25),transparent_55%)]" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent" />
                <div className="relative">
                  <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-accent">{item.category}</div>
                  <div className="mt-1 line-clamp-2 font-display text-[14px] font-bold leading-snug text-white">
                    {item.title}
                  </div>
                  <div className="mt-1 line-clamp-2 text-[11px] leading-relaxed text-white/60">{item.text}</div>
                </div>
              </a>
            ))}
          </div>
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-display text-[16px] font-bold">
              <DownloadCloud size={16} className="text-accent2" />
              {t('home.activeDownloads')}
            </h2>
            <button
              className="text-[11px] font-semibold text-muted transition-colors hover:text-ink"
              onClick={() => navigate('downloads')}
            >
              {t('home.allDownloads')}
            </button>
          </div>
          <Card className="p-4">
            {activeDownloads.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-6 text-center">
                <DownloadCloud size={24} className="text-muted/50" />
                <div className="text-[12px] text-muted">{t('downloads.noActive')}</div>
              </div>
            ) : (
              <div className="space-y-3">
                {activeDownloads.slice(0, 4).map((d) => (
                  <div key={d.id}>
                    <div className="mb-1 flex items-center justify-between gap-2 text-[11.5px]">
                      <span className="truncate font-semibold">{d.title}</span>
                      <span className="shrink-0 text-muted">
                        {d.speed > 0 ? formatSpeed(d.speed) : `${Math.round((d.current / Math.max(1, d.total)) * 100)}%`}
                      </span>
                    </div>
                    <Progress value={d.current} max={Math.max(1, d.total)} className="h-1.5" />
                    <div className="mt-1 truncate text-[10px] text-muted">{d.detail}</div>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-4 divider" />
            <div className="mt-3 flex items-center justify-between text-[11px] text-muted">
              <span>{t('settings.systemInfo', { os: info?.os ?? '—', ram: info?.totalRamMb ?? '—' })}</span>
            </div>
          </Card>
        </section>
      </div>
    </Page>
  );
}

function StatusLine({
  icon,
  label,
  value,
  ok,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  ok: boolean;
}) {
  return (
    <div className="flex items-center gap-2.5">
      <span className={cn('flex h-6 w-6 items-center justify-center rounded-md', ok ? 'bg-accent/15 text-accent' : 'bg-white/8 text-white/45')}>
        {icon}
      </span>
      <span className="flex-1 text-[11px] text-white/55">{label}</span>
      <span className={cn('max-w-[140px] truncate text-[11px] font-semibold', ok ? 'text-white/90' : 'text-white/50')}>
        {value}
      </span>
    </div>
  );
}
