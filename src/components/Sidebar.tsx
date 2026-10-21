import {
  Boxes,
  Download,
  Home,
  Package,
  Puzzle,
  Settings,
  Users,
  ChevronRight,
  HardDriveDownload,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { useRoute, type Route } from '../lib/router';
import { cn } from './ui';
import { IS_TAURI } from '../lib/bridge';

const NAV: { key: Route; icon: LucideIcon; label: string }[] = [
  { key: 'home', icon: Home, label: 'nav.home' },
  { key: 'versions', icon: Boxes, label: 'nav.versions' },
  { key: 'mods', icon: Puzzle, label: 'nav.mods' },
  { key: 'modpacks', icon: Package, label: 'nav.modpacks' },
  { key: 'downloads', icon: Download, label: 'nav.downloads' },
  { key: 'accounts', icon: Users, label: 'nav.accounts' },
  { key: 'settings', icon: Settings, label: 'nav.settings' },
];

export function Sidebar() {
  const t = useT();
  const [route, setRoute] = useRoute();
  const { accounts, settings, downloads, launch, info } = useApp();
  const account = accounts.find((a) => a.id === settings.activeAccountId) ?? accounts[0] ?? null;
  const activeDownloads = downloads.filter((d) => d.status === 'running' || d.status === 'queued').length;

  return (
    <aside
      className="relative z-30 flex h-full shrink-0 flex-col border-r border-line/8 bg-surface/55 px-3.5 pb-3.5 pt-2 backdrop-blur-2xl"
      style={{ width: 'var(--sidebar-w)' }}
    >
      <button
        onClick={() => setRoute('home')}
        className="mb-5 flex items-center gap-3 rounded-2xl px-2 py-2 text-left transition-colors hover:bg-ink/4"
      >
        <img src="/logo.png" alt="WinLaucher" className="h-10 w-10 rounded-xl shadow-glow" />
        <div className="min-w-0">
          <div className="font-display text-[15px] font-extrabold leading-tight tracking-tight">WinLaucher</div>
          <div className="truncate text-[10px] font-medium uppercase tracking-[0.14em] text-muted/80">
            {t('app.tagline')}
          </div>
        </div>
      </button>

      <nav className="flex flex-col gap-1">
        {NAV.map((item) => {
          const Icon = item.icon;
          const active = route === item.key;
          return (
            <button
              key={item.key}
              onClick={() => setRoute(item.key)}
              className={cn('nav-item', active && 'active')}
            >
              <Icon size={17} className={cn('transition-colors', active && 'text-accent')} />
              <span className="flex-1 text-left">{t(item.label)}</span>
              {item.key === 'downloads' && activeDownloads > 0 && (
                <span className="rounded-md bg-accent/20 px-1.5 py-0.5 text-[10px] font-bold text-accent">
                  {activeDownloads}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      <div className="mt-auto flex flex-col gap-2.5">
        {activeDownloads > 0 && (
          <button
            onClick={() => setRoute('downloads')}
            className="flex items-center gap-2 rounded-xl border border-accent/20 bg-accent/8 px-3 py-2 text-left transition-colors hover:bg-accent/12"
          >
            <HardDriveDownload size={15} className="text-accent" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[11px] font-semibold text-accent">
                {launch.state === 'running' ? t('launch.running') : t('home.activeDownloads')}
              </div>
              <div className="truncate text-[10px] text-muted">
                {downloads.find((d) => d.status === 'running')?.title ?? `${activeDownloads}`}
              </div>
            </div>
            <ChevronRight size={13} className="text-muted" />
          </button>
        )}

        <button
          onClick={() => setRoute('accounts')}
          className="flex items-center gap-2.5 rounded-xl border border-line/10 bg-ink/4 p-2 text-left transition-colors hover:bg-ink/8"
        >
          {account?.kind === 'microsoft' && account.skins?.[0]?.url ? (
            <img
              src={account.skins[0].url}
              alt=""
              className="h-8 w-8 rounded-lg bg-elevated object-cover"
              onError={(e) => ((e.currentTarget.style.display = 'none'), undefined)}
            />
          ) : (
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-accent/30 to-accent2/30 text-[12px] font-bold uppercase">
              {account?.name?.[0] ?? '?'}
            </div>
          )}
          <div className="min-w-0 flex-1">
            <div className="truncate text-[12px] font-semibold">{account?.name ?? t('home.selectAccount')}</div>
            <div className="truncate text-[10px] text-muted">
              {account
                ? account.kind === 'microsoft'
                  ? t('accounts.premium')
                  : t('accounts.offlineBadge')
                : t('accounts.noAccounts')}
            </div>
          </div>
          <ChevronRight size={13} className="shrink-0 text-muted" />
        </button>

        <div className="px-1 text-[10px] text-muted/60">
          v{info?.version ?? '1.0.0'}
          {IS_TAURI ? '' : ' · web preview'}
        </div>
      </div>
    </aside>
  );
}
