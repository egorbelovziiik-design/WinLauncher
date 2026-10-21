import { useCallback, useEffect, useState } from 'react';
import {
  Download,
  ExternalLink,
  Loader2,
  Package,
  PackageOpen,
  RefreshCw,
  Upload,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, openExternal, pickFile } from '../lib/bridge';
import type { ModpackHit, ModrinthSearchResult, ModrinthVersion, Profile } from '../lib/types';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Modal,
  Page,
  PageTitle,
  SearchInput,
  formatDate,
  formatNumber,
  formatBytes,
} from '../components/ui';

export function ModpacksPage() {
  const t = useT();
  const { profiles, settings, refreshProfiles, toast, toastError, downloads } = useApp();
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<ModpackHit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<ModpackHit | null>(null);
  const [versions, setVersions] = useState<ModrinthVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionId, setVersionId] = useState('');
  const [installing, setInstalling] = useState(false);
  const installedPacks = profiles.filter((p) => p.source === 'modpack');

  const search = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await call<ModrinthSearchResult>('modpacks_search', { query, limit: 24, offset: 0 });
      setHits(res.hits as unknown as ModpackHit[]);
      setTotal(res.totalHits);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('mods.searchError'));
    } finally {
      setLoading(false);
    }
  }, [query, t]);

  useEffect(() => {
    const id = setTimeout(() => void search(), 300);
    return () => clearTimeout(id);
  }, [search]);

  const openPack = async (hit: ModpackHit) => {
    setSelected(hit);
    setVersions([]);
    setVersionId('');
    setVersionsLoading(true);
    try {
      const list = await call<ModrinthVersion[]>('modpack_versions', { projectId: hit.projectId });
      setVersions(list);
      setVersionId(list[0]?.id ?? '');
    } catch (e) {
      toastError(e);
    } finally {
      setVersionsLoading(false);
    }
  };

  const install = async () => {
    if (!selected || !versionId) return;
    setInstalling(true);
    try {
      const profile = await call<Profile>('modpack_install', { projectId: selected.projectId, versionId });
      toast('success', t('modpacks.installedToast', { name: selected.title }));
      await refreshProfiles();
      useApp.getState().saveSettings({ activeProfileId: profile.id });
      setSelected(null);
    } catch (e) {
      toastError(e);
    } finally {
      setInstalling(false);
    }
  };

  const importPack = async () => {
    try {
      const path = await pickFile([{ name: 'Modrinth modpack', extensions: ['mrpack', 'zip'] }]);
      if (!path) return;
      const profile = await call<Profile>('modpack_import', { path });
      toast('success', t('modpacks.installedToast', { name: profile.name }));
      await refreshProfiles();
      useApp.getState().saveSettings({ activeProfileId: profile.id });
    } catch (e) {
      toastError(e);
    }
  };

  const running = downloads.filter((d) => d.status === 'running');

  return (
    <Page>
      <PageTitle
        title={t('modpacks.title')}
        subtitle={t('modpacks.subtitle')}
        actions={
          <Button icon={<Upload size={15} />} onClick={() => void importPack()}>
            {t('modpacks.import')}
          </Button>
        }
      />

      {running.length > 0 && (
        <Card className="mb-4 flex items-center gap-3 border-accent/25 bg-accent/8 p-3.5 text-[12px]">
          <Loader2 size={15} className="animate-spin text-accent" />
          <span className="flex-1 font-semibold text-accent">{running[0].detail || t('modpacks.installing')}</span>
          <span className="text-muted">{formatBytes(running[0].bytes)}</span>
        </Card>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <SearchInput value={query} onChange={setQuery} placeholder={t('modpacks.searchPlaceholder')} className="min-w-[260px] flex-1" />
        <Button onClick={() => void search()} loading={loading} icon={<RefreshCw size={14} />}>
          {t('common.refresh')}
        </Button>
      </div>

      {error && (
        <Card className="mb-4 flex items-center gap-3 border-danger/25 p-4 text-[12.5px] text-danger">
          <span className="flex-1">{t('mods.searchError')} · {error}</span>
          <Button size="sm" onClick={() => void search()}>{t('common.retry')}</Button>
        </Card>
      )}

      <div className="mb-3 text-[11px] text-muted">{t('mods.total', { n: formatNumber(total) })}</div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {loading && hits.length === 0 &&
          Array.from({ length: 6 }).map((_, i) => <div key={i} className="skeleton h-[168px] rounded-2xl" />)}
        {!loading && hits.length === 0 && (
          <div className="md:col-span-2 xl:col-span-3">
            <EmptyState icon={<PackageOpen size={26} />} title={t('modpacks.noResults')} />
          </div>
        )}
        {hits.map((hit) => (
          <Card key={hit.projectId} hover className="flex flex-col overflow-hidden">
            <div className="relative h-24 overflow-hidden">
              {hit.gallery?.[0] ? (
                <img src={hit.gallery[0]} alt="" className="h-full w-full object-cover" />
              ) : (
                <div className="h-full w-full bg-[radial-gradient(circle_at_20%_20%,rgb(var(--c-accent)/.3),transparent_60%),radial-gradient(circle_at_90%_80%,rgb(var(--c-accent2)/.28),transparent_55%)]" />
              )}
              <div className="absolute inset-0 bg-gradient-to-t from-surface via-surface/20 to-transparent" />
              <div className="absolute bottom-2 left-3 right-3 flex items-end gap-2">
                {hit.iconUrl ? (
                  <img src={hit.iconUrl} alt="" className="h-11 w-11 shrink-0 rounded-xl border border-line/15 bg-elevated object-cover" />
                ) : (
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-elevated font-display font-bold text-accent">
                    {hit.title.slice(0, 1)}
                  </div>
                )}
                <div className="min-w-0">
                  <h3 className="truncate font-display text-[14px] font-bold">{hit.title}</h3>
                  <div className="text-[10.5px] text-muted">by {hit.author}</div>
                </div>
              </div>
            </div>
            <div className="flex flex-1 flex-col p-3.5 pt-2">
              <p className="line-clamp-2 min-h-[32px] text-[11.5px] leading-relaxed text-muted">{hit.description}</p>
              <div className="mt-2 flex items-center gap-2 text-[10.5px] text-muted">
                <Download size={11} /> {formatNumber(hit.downloads)}
                <span>·</span>
                <span>{formatDate(hit.dateModified, settings.locale)}</span>
              </div>
              <div className="mt-3 flex gap-1.5">
                <Button size="sm" variant="primary" className="flex-1" icon={<Download size={13} />} onClick={() => void openPack(hit)}>
                  {t('modpacks.install')}
                </Button>
                <Button size="sm" onClick={() => void openExternal(`https://modrinth.com/modpack/${hit.slug}`)}>
                  <ExternalLink size={13} />
                </Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <section className="mt-8">
        <h2 className="mb-3 font-display text-[16px] font-bold">{t('modpacks.installedTitle')}</h2>
        {installedPacks.length === 0 ? (
          <EmptyState icon={<Package size={24} />} title={t('modpacks.noneInstalled')} />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {installedPacks.map((p) => (
              <Card key={p.id} className="p-4">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="truncate font-semibold">{p.name}</h3>
                  <Badge tone="success">{t('common.installed')}</Badge>
                </div>
                <div className="mt-1 text-[11px] text-muted">
                  {p.modpack?.name ?? p.name} · {p.mcVersion} · {p.loader.kind}
                </div>
                <div className="mt-3">
                  <Button
                    size="sm"
                    variant="primary"
                    icon={<Download size={13} />}
                    onClick={() => {
                      void useApp.getState().setActiveProfile(p.id);
                      window.location.hash = '#/home';
                    }}
                  >
                    {t('common.play')}
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <Modal
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected?.title}
        subtitle={t('modpacks.profileNote')}
        width="max-w-xl"
        footer={
          <>
            <Button variant="ghost" onClick={() => setSelected(null)}>{t('common.cancel')}</Button>
            <Button
              variant="primary"
              loading={installing}
              disabled={!versionId || versionsLoading}
              icon={<Download size={14} />}
              onClick={() => void install()}
            >
              {t('modpacks.install')}
            </Button>
          </>
        }
      >
        {selected && (
          <div className="space-y-4">
            <div className="flex items-start gap-3">
              {selected.iconUrl ? (
                <img src={selected.iconUrl} alt="" className="h-16 w-16 rounded-xl object-cover" />
              ) : (
                <div className="flex h-16 w-16 items-center justify-center rounded-xl bg-accent/15 font-display text-xl font-bold text-accent">
                  {selected.title.slice(0, 1)}
                </div>
              )}
              <p className="flex-1 text-[12px] leading-relaxed text-muted">{selected.description}</p>
            </div>
            <div>
              <span className="label">{t('modpacks.versionSelect')}</span>
              {versionsLoading ? (
                <div className="skeleton h-10" />
              ) : versions.length === 0 ? (
                <div className="rounded-xl border border-warning/20 bg-warning/8 p-3 text-[12px] text-warning">
                  {t('modpacks.notFound')}
                </div>
              ) : (
                <div className="scroll-area max-h-56 space-y-1.5">
                  {versions.map((v) => (
                    <button
                      key={v.id}
                      onClick={() => setVersionId(v.id)}
                      className={`flex w-full items-center justify-between gap-3 rounded-lg border px-3 py-2 text-left transition-colors ${
                        versionId === v.id ? 'border-accent/50 bg-accent/10' : 'border-line/8 bg-ink/3 hover:bg-ink/6'
                      }`}
                    >
                      <div className="min-w-0">
                        <div className="truncate text-[12px] font-semibold">{v.name || v.versionNumber}</div>
                        <div className="text-[10px] text-muted">
                          {v.gameVersions.slice(0, 2).join(', ')} · {v.loaders.join(', ')} ·{' '}
                          {v.files[0] ? formatBytes(v.files[0].size) : ''}
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <Badge tone={v.versionType === 'release' ? 'success' : 'info'}>{v.versionType}</Badge>
                        <span className="text-[10px] text-muted">{formatDate(v.datePublished, settings.locale)}</span>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </Page>
  );
}
