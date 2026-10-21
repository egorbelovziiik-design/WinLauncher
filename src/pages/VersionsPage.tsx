import { useEffect, useMemo, useState } from 'react';
import {
  Boxes,
  Download,
  FolderOpen,
  Loader2,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  TriangleAlert,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, openPath } from '../lib/bridge';
import type { McVersion, Profile } from '../lib/types';
import type { Route } from '../lib/router';
import { navigate } from '../lib/router';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Page,
  PageTitle,
  SearchInput,
  Segmented,
  Tabs,
  formatDate,
  formatNumber,
  cn,
  useConfirm,
} from '../components/ui';
import { ProfileForm } from '../components/ProfileForm';

type Filter = 'all' | 'release' | 'snapshot' | 'beta' | 'alpha' | 'installed';

function loaderTone(kind: Profile['loader']['kind']) {
  switch (kind) {
    case 'fabric':
      return 'bg-[#dbb69a]/15 text-[#dbb69a]';
    case 'forge':
      return 'bg-[#7f9fd0]/15 text-[#7f9fd0]';
    case 'neoforge':
      return 'bg-[#d0a07f]/15 text-[#d0a07f]';
    case 'quilt':
      return 'bg-[#c99bd8]/15 text-[#c99bd8]';
    default:
      return 'bg-ink/8 text-muted';
  }
}

function VersionTypeBadge({ type }: { type: McVersion['type'] }) {
  const t = useT();
  const map: Record<string, { tone: 'success' | 'info' | 'warning' | 'danger' | 'default'; label: string }> = {
    release: { tone: 'success', label: t('home.release') },
    snapshot: { tone: 'info', label: t('home.snapshot') },
    old_beta: { tone: 'warning', label: 'Beta' },
    old_alpha: { tone: 'danger', label: 'Alpha' },
    unknown: { tone: 'default', label: t('common.unknown') },
  };
  const item = map[type] ?? map.unknown;
  return <Badge tone={item.tone}>{item.label}</Badge>;
}

export function VersionsPage({ initialFilter }: { initialFilter?: Filter }) {
  const t = useT();
  const confirm = useConfirm();
  const {
    versions,
    versionsLoading,
    versionsError,
    profiles,
    settings,
    downloads,
    refreshVersions,
    refreshProfiles,
    setActiveProfile,
    toast,
    toastError,
    saveSettings,
  } = useApp();
  const [filter, setFilter] = useState<Filter>(initialFilter ?? 'all');
  const [query, setQuery] = useState('');
  const [visible, setVisible] = useState(60);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Profile | null>(null);
  const [presetVersion, setPresetVersion] = useState<string | null>(null);
  const [installing, setInstalling] = useState<string | null>(null);

  useEffect(() => {
    if (versions.length === 0 && !versionsLoading) void refreshVersions();
  }, [versions.length, versionsLoading, refreshVersions]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return versions.filter((v) => {
      if (q && !v.id.toLowerCase().includes(q)) return false;
      switch (filter) {
        case 'installed':
          return !!v.installed;
        case 'release':
          return v.type === 'release';
        case 'snapshot':
          return v.type === 'snapshot';
        case 'beta':
          return v.type === 'old_beta';
        case 'alpha':
          return v.type === 'old_alpha';
        default:
          return true;
      }
    });
  }, [versions, filter, query]);

  const installedCount = versions.filter((v) => v.installed).length;
  const busyVersions = new Set(
    downloads.filter((d) => d.status === 'running' || d.status === 'queued').map((d) => d.title),
  );

  const createProfile = () => {
    setEditing(null);
    setPresetVersion(null);
    setFormOpen(true);
  };

  const installVersion = async (v: McVersion) => {
    setInstalling(v.id);
    try {
      await call('install_version', { mcVersion: v.id });
      toast('success', t('versions.statusReady'));
      void refreshVersions();
      void refreshProfiles();
    } catch (e) {
      toastError(e);
    } finally {
      setInstalling(null);
    }
  };

  const deleteVersion = async (v: McVersion) => {
    const ok = await confirm({
      title: t('versions.deleteFiles'),
      message: t('versions.deleteFilesConfirm', { version: v.id }),
      danger: true,
      confirmLabel: t('common.delete'),
    });
    if (!ok) return;
    try {
      await call('delete_version', { mcVersion: v.id });
      toast('success', t('versions.filesDeleted'));
      void refreshVersions();
    } catch (e) {
      toastError(e);
    }
  };

  const deleteProfile = async (p: Profile) => {
    const ok = await confirm({
      title: t('versions.deleteProfile'),
      message: t('versions.deleteProfileConfirm', { name: p.name }),
      danger: true,
      confirmLabel: t('common.delete'),
    });
    if (!ok) return;
    try {
      await call('profile_delete', { profileId: p.id });
      if (settings.activeProfileId === p.id) {
        saveSettings({ activeProfileId: profiles.find((x) => x.id !== p.id)?.id ?? null });
      }
      toast('success', t('versions.profileDeleted'));
      void refreshProfiles();
    } catch (e) {
      toastError(e);
    }
  };

  const openFolder = async (p: Profile) => {
    try {
      await openPath(p.gameDir);
    } catch {
      try {
        await call('profile_open_folder', { profileId: p.id });
      } catch (e) {
        toastError(e);
      }
    }
  };

  const play = (p: Profile) => {
    void setActiveProfile(p.id);
    navigate('versions' as Route);
    navigate('home');
  };

  return (
    <Page>
      <PageTitle
        title={t('versions.title')}
        subtitle={t('versions.subtitle')}
        actions={
          <>
            <Button icon={<RefreshCw size={15} />} loading={versionsLoading} onClick={() => void refreshVersions()}>
              {t('common.refresh')}
            </Button>
            <Button variant="primary" icon={<Plus size={15} />} onClick={createProfile}>
              {t('versions.createProfile')}
            </Button>
          </>
        }
      />

      {/* Profiles ---------------------------------------------------------- */}
      <section className="mb-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-[16px] font-bold">{t('versions.profiles')}</h2>
          <span className="text-[11px] text-muted">
            {profiles.length} · {t('versions.installedCount')} {installedCount}
          </span>
        </div>
        {profiles.length === 0 ? (
          <EmptyState
            icon={<Boxes size={28} />}
            title={t('versions.noProfiles')}
            action={
              <Button variant="primary" icon={<Plus size={15} />} onClick={createProfile}>
                {t('versions.createProfile')}
              </Button>
            }
          />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {profiles.map((p) => {
              const version = versions.find((v) => v.id === p.mcVersion);
              const installed = version?.installed ?? false;
              const active = settings.activeProfileId === p.id;
              return (
                <Card
                  key={p.id}
                  hover
                  className={cn('relative overflow-hidden p-4', active && 'ring-1 ring-accent/40')}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <h3 className="truncate font-display text-[15px] font-bold">{p.name}</h3>
                        {active && <Badge tone="accent">{t('accounts.primary')}</Badge>}
                      </div>
                      <div className="mt-1 flex flex-wrap items-center gap-1.5">
                        <Badge tone="info">{p.mcVersion}</Badge>
                        <span className={cn('chip capitalize', loaderTone(p.loader.kind))}>
                          {p.loader.kind === 'vanilla' ? 'Vanilla' : p.loader.kind}
                          {p.loader.version ? ` ${p.loader.version}` : ''}
                        </span>
                        <Badge>{p.ramMb} MB</Badge>
                      </div>
                    </div>
                    {!installed && (
                      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-warning/12 text-warning" title={t('versions.loaderMissing')}>
                        <TriangleAlert size={14} />
                      </span>
                    )}
                  </div>

                  <div className="mt-3 text-[11px] text-muted">
                    {p.lastPlayedAt
                      ? `${t('versions.lastPlayed')}: ${formatDate(p.lastPlayedAt, settings.locale)}`
                      : t('versions.neverPlayed')}
                  </div>

                  <div className="mt-3 flex items-center gap-1.5">
                    <Button size="sm" variant="primary" icon={<Play size={13} className="fill-current" />} onClick={() => play(p)}>
                      {t('common.play')}
                    </Button>
                    <Button
                      size="sm"
                      icon={<Pencil size={13} />}
                      onClick={() => {
                        setEditing(p);
                        setPresetVersion(null);
                        setFormOpen(true);
                      }}
                    >
                      {t('common.edit')}
                    </Button>
                    <Button size="sm" onClick={() => void openFolder(p)} title={t('common.openFolder')}>
                      <FolderOpen size={13} />
                    </Button>
                    <Button size="sm" variant="danger" className="ml-auto" onClick={() => void deleteProfile(p)} title={t('common.delete')}>
                      <Trash2 size={13} />
                    </Button>
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </section>

      {/* Catalog ----------------------------------------------------------- */}
      <section>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-display text-[16px] font-bold">{t('versions.total')}</h2>
          <div className="flex flex-wrap items-center gap-2">
            <Segmented
              value={filter}
              onChange={(v) => setFilter(v)}
              size="sm"
              options={[
                { value: 'all' as Filter, label: t('versions.filterAll') },
                { value: 'release' as Filter, label: t('versions.filterRelease') },
                { value: 'snapshot' as Filter, label: t('versions.filterSnapshot') },
                { value: 'beta' as Filter, label: t('versions.filterBeta') },
                { value: 'alpha' as Filter, label: t('versions.filterAlpha') },
                { value: 'installed' as Filter, label: t('versions.filterInstalled') },
              ]}
            />
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={t('versions.searchPlaceholder')}
              className="w-56"
            />
          </div>
        </div>

        {versionsError && (
          <Card className="mb-4 flex items-center gap-3 border-danger/25 p-3.5 text-[12px] text-danger">
            <TriangleAlert size={15} />
            <span className="flex-1">{versionsError}</span>
            <Button size="sm" onClick={() => void refreshVersions()}>
              {t('common.retry')}
            </Button>
          </Card>
        )}

        <Card className="overflow-hidden">
          <div className="grid grid-cols-[1.5fr_auto_1fr_auto] items-center gap-3 border-b border-line/10 px-4 py-2.5 text-[10px] font-bold uppercase tracking-[0.14em] text-muted">
            <span>{t('common.version')}</span>
            <span>{t('common.type')}</span>
            <span>{t('versions.releaseDate')}</span>
            <span className="text-right">{t('common.actions')}</span>
          </div>
          {versionsLoading && versions.length === 0 && (
            <div className="space-y-2 p-4">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="skeleton h-9" />
              ))}
            </div>
          )}
          {!versionsLoading && filtered.length === 0 && (
            <div className="p-6">
              <EmptyState icon={<Search size={24} />} title={t('versions.noVersions')} />
            </div>
          )}
          <div className="divide-y divide-line/6">
            {filtered.slice(0, visible).map((v) => {
              const busy = installing === v.id || busyVersions.has(v.id);
              return (
                <div
                  key={v.id}
                  className="grid grid-cols-[1.5fr_auto_1fr_auto] items-center gap-3 px-4 py-2.5 transition-colors hover:bg-ink/3"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[12.5px] font-semibold">{v.id}</span>
                    {v.installed && <Badge tone="success">{t('common.installed')}</Badge>}
                  </div>
                  <VersionTypeBadge type={v.type} />
                  <span className="text-[11.5px] text-muted">{formatDate(v.releaseTime, settings.locale)}</span>
                  <div className="flex items-center justify-end gap-1">
                    {v.installed ? (
                      <Button size="sm" variant="danger" onClick={() => void deleteVersion(v)} icon={<Trash2 size={12} />}>
                        {t('common.delete')}
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="primary"
                        loading={busy}
                        onClick={() => void installVersion(v)}
                        icon={busy ? <Loader2 size={12} /> : <Download size={12} />}
                      >
                        {t('common.install')}
                      </Button>
                    )}
                    <Button
                      size="sm"
                      onClick={() => {
                        setEditing(null);
                        setPresetVersion(v.id);
                        setFormOpen(true);
                      }}
                      title={t('versions.createProfile')}
                    >
                      <Plus size={13} />
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
          {filtered.length > visible && (
            <div className="border-t border-line/10 p-3 text-center">
              <Button size="sm" onClick={() => setVisible((n) => n + 60)}>
                {t('common.next')} ({filtered.length - visible})
              </Button>
            </div>
          )}
        </Card>
      </section>

      <ProfileForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        initial={editing}
        presetVersion={presetVersion}
        onSaved={() => void refreshProfiles()}
      />
    </Page>
  );
}
