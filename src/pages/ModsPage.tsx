import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Download,
  ExternalLink,
  FileArchive,
  FolderOpen,
  Loader2,
  Package,
  Puzzle,
  RefreshCw,
  SearchX,
  Trash2,
  Upload,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, openExternal, openPath, pickFile } from '../lib/bridge';
import type { InstalledMod, ModrinthHit, ModrinthSearchResult, ModrinthVersion } from '../lib/types';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Modal,
  Page,
  PageTitle,
  SearchInput,
  Select,
  Tabs,
  Toggle,
  formatNumber,
  cn,
  useConfirm,
} from '../components/ui';

const SORTS = [
  { value: 'relevance', key: 'mods.sortRelevance' },
  { value: 'downloads', key: 'mods.sortDownloads' },
  { value: 'newest', key: 'mods.sortNewest' },
  { value: 'updated', key: 'mods.sortUpdated' },
  { value: 'follows', key: 'mods.sortFollows' },
];

function ModIcon({ hit, size = 48 }: { hit: { iconUrl: string | null; title: string }; size?: number }) {
  const [broken, setBroken] = useState(false);
  if (hit.iconUrl && !broken) {
    return (
      <img
        src={hit.iconUrl}
        alt=""
        style={{ width: size, height: size }}
        className="shrink-0 rounded-xl bg-elevated object-cover"
        onError={() => setBroken(true)}
      />
    );
  }
  return (
    <div
      style={{ width: size, height: size }}
      className="flex shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-accent/25 to-accent2/25 font-display text-lg font-extrabold text-accent"
    >
      {hit.title.slice(0, 1).toUpperCase()}
    </div>
  );
}

export function ModsPage() {
  const t = useT();
  const confirm = useConfirm();
  const {
    settings,
    profiles,
    downloads,
    toast,
    toastError,
    setActiveProfile,
  } = useApp();
  const [tab, setTab] = useState<'catalog' | 'installed'>('catalog');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState('relevance');
  const [loaderFilter, setLoaderFilter] = useState('all');
  const [hits, setHits] = useState<ModrinthHit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<ModrinthHit | null>(null);
  const [detailBody, setDetailBody] = useState<string>('');
  const [versions, setVersions] = useState<ModrinthVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [installing, setInstalling] = useState<string | null>(null);
  const [installed, setInstalled] = useState<InstalledMod[]>([]);
  const [installedLoading, setInstalledLoading] = useState(false);

  const profile = profiles.find((p) => p.id === settings.activeProfileId) ?? profiles[0] ?? null;
  const hasLoader = !!profile && profile.loader.kind !== 'vanilla';

  const search = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await call<ModrinthSearchResult>('modrinth_search', {
        query,
        limit: 24,
        offset: 0,
        sort,
        loader: loaderFilter === 'all' ? (hasLoader ? profile?.loader.kind : null) : loaderFilter,
        gameVersion: profile?.mcVersion ?? null,
      });
      setHits(res.hits);
      setTotal(res.totalHits);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('mods.searchError'));
    } finally {
      setLoading(false);
    }
  }, [query, sort, loaderFilter, hasLoader, profile?.loader.kind, profile?.mcVersion, t]);

  useEffect(() => {
    const id = setTimeout(() => void search(), 280);
    return () => clearTimeout(id);
  }, [search]);

  const loadInstalled = useCallback(async () => {
    if (!profile) return;
    setInstalledLoading(true);
    try {
      setInstalled(await call<InstalledMod[]>('mods_installed', { profileId: profile.id }));
    } catch {
      setInstalled([]);
    } finally {
      setInstalledLoading(false);
    }
  }, [profile]);

  useEffect(() => {
    void loadInstalled();
  }, [loadInstalled]);

  const openDetail = async (hit: ModrinthHit) => {
    setDetail(hit);
    setDetailBody('');
    setVersions([]);
    setVersionsLoading(true);
    void call<ModrinthHit>('modrinth_project', { idOrSlug: hit.projectId })
      .then((full) => setDetailBody(full.body ?? ''))
      .catch(() => {});
    try {
      const list = await call<ModrinthVersion[]>('modrinth_versions', {
        projectId: hit.projectId,
        gameVersion: profile?.mcVersion ?? null,
        loader: profile?.loader.kind === 'vanilla' ? null : profile?.loader.kind ?? null,
      });
      setVersions(list);
    } catch (e) {
      toastError(e);
    } finally {
      setVersionsLoading(false);
    }
  };

  const installVersion = async (hit: ModrinthHit, versionId?: string) => {
    if (!profile) return toast('error', t('mods.requiresProfile'));
    if (!hasLoader) return toast('error', t('mods.requiresLoader'));
    setInstalling(hit.projectId);
    try {
      if (!versionId) {
        const list = await call<ModrinthVersion[]>('modrinth_versions', {
          projectId: hit.projectId,
          gameVersion: profile.mcVersion,
          loader: profile.loader.kind,
        });
        if (list.length === 0) {
          toast('error', t('mods.requiresGameVersion', { version: profile.mcVersion, loader: profile.loader.kind }));
          return;
        }
        versionId = list[0].id;
      }
      await call('mod_install', { profileId: profile.id, projectId: hit.projectId, versionId });
      toast('success', t('mods.installedToast', { name: hit.title }));
      void loadInstalled();
    } catch (e) {
      toastError(e);
    } finally {
      setInstalling(null);
    }
  };

  const removeMod = async (mod: InstalledMod) => {
    if (!profile) return;
    const ok = await confirm({
      title: t('common.delete'),
      message: `${mod.projectName ?? mod.fileName}`,
      danger: true,
      confirmLabel: t('common.delete'),
    });
    if (!ok) return;
    try {
      await call('mod_delete', { profileId: profile.id, fileName: mod.fileName });
      toast('success', t('mods.deletedToast'));
      void loadInstalled();
    } catch (e) {
      toastError(e);
    }
  };

  const toggleMod = async (mod: InstalledMod) => {
    if (!profile) return;
    try {
      await call('mod_toggle', { profileId: profile.id, fileName: mod.fileName, enabled: !mod.enabled });
      setInstalled((list) => list.map((m) => (m.fileName === mod.fileName ? { ...m, enabled: !m.enabled } : m)));
    } catch (e) {
      toastError(e);
    }
  };

  const importJar = async () => {
    if (!profile) return toast('error', t('mods.requiresProfile'));
    try {
      const path = await pickFile([{ name: 'Mods', extensions: ['jar'] }]);
      if (!path) return;
      await call('mod_import', { profileId: profile.id, path });
      toast('success', t('mods.installedToast', { name: path.split(/[\\/]/).pop() ?? '' }));
      void loadInstalled();
    } catch (e) {
      toastError(e);
    }
  };

  const activeDownloads = downloads.filter((d) => d.status === 'running' || d.status === 'queued');
  const installedIds = useMemo(() => new Set(installed.map((m) => m.projectId).filter(Boolean)), [installed]);

  return (
    <Page>
      <PageTitle
        title={t('mods.title')}
        subtitle={t('mods.subtitle')}
        actions={
          <>
            <Select
              value={profile?.id ?? ''}
              onChange={(id) => void setActiveProfile(id)}
              options={profiles.map((p) => ({
                value: p.id,
                label: `${p.name} · ${p.mcVersion}${p.loader.kind !== 'vanilla' ? ` · ${p.loader.kind}` : ''}`,
              }))}
              className="w-64"
            />
            <Button icon={<Upload size={15} />} onClick={() => void importJar()}>
              {t('mods.importJar')}
            </Button>
          </>
        }
      />

      {!profile && (
        <Card className="mb-4 border-warning/25 bg-warning/8 p-4 text-[12.5px] text-warning">
          {t('mods.requiresProfile')}
        </Card>
      )}
      {profile && !hasLoader && (
        <Card className="mb-4 border-warning/25 bg-warning/8 p-4 text-[12.5px] text-warning">
          {t('mods.requiresLoader')}
        </Card>
      )}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Tabs
          value={tab}
          onChange={setTab}
          tabs={[
            { value: 'catalog', label: t('mods.tabCatalog'), icon: <Puzzle size={13} /> },
            { value: 'installed', label: t('mods.tabInstalled'), icon: <Package size={13} />, count: installed.length },
          ]}
        />
        {activeDownloads.length > 0 && (
          <div className="flex items-center gap-2 text-[11px] text-muted">
            <Loader2 size={12} className="animate-spin text-accent" />
            {activeDownloads.length} · {t('downloads.running')}
          </div>
        )}
      </div>

      {tab === 'catalog' ? (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <SearchInput value={query} onChange={setQuery} placeholder={t('mods.searchPlaceholder')} className="min-w-[260px] flex-1" />
            <Select
              value={loaderFilter}
              onChange={setLoaderFilter}
              options={[
                { value: 'all', label: t('mods.allLoaders') },
                { value: 'fabric', label: 'Fabric' },
                { value: 'forge', label: 'Forge' },
                { value: 'neoforge', label: 'NeoForge' },
                { value: 'quilt', label: 'Quilt' },
              ]}
              className="w-44"
            />
            <Select
              value={sort}
              onChange={setSort}
              options={SORTS.map((s) => ({ value: s.value, label: t(s.key) }))}
              className="w-48"
            />
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
              Array.from({ length: 6 }).map((_, i) => <div key={i} className="skeleton h-[150px] rounded-2xl" />)}
            {!loading && hits.length === 0 && (
              <div className="md:col-span-2 xl:col-span-3">
                <EmptyState icon={<SearchX size={26} />} title={t('mods.noResults')} />
              </div>
            )}
            {hits.map((hit) => {
              const isInstalled = installedIds.has(hit.projectId);
              return (
                <Card key={hit.projectId} hover className="flex flex-col p-4">
                  <div className="flex items-start gap-3">
                    <ModIcon hit={hit} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <h3 className="truncate font-display text-[14px] font-bold">{hit.title}</h3>
                        {isInstalled && <Badge tone="success">{t('mods.installedBadge')}</Badge>}
                      </div>
                      <div className="text-[11px] text-muted">{t('mods.byAuthor', { author: hit.author })}</div>
                      <div className="mt-1 flex items-center gap-2 text-[10.5px] text-muted">
                        <Download size={10} /> {formatNumber(hit.downloads)}
                      </div>
                    </div>
                  </div>
                  <p className="mt-2.5 line-clamp-2 min-h-[32px] text-[11.5px] leading-relaxed text-muted">{hit.description}</p>
                  <div className="mt-2 flex flex-wrap gap-1">
                    {hit.categories.slice(0, 3).map((c) => (
                      <span key={c} className="chip">{c}</span>
                    ))}
                    {hit.versions.slice(0, 1).map((v) => (
                      <span key={v} className="chip text-accent2">{v}+</span>
                    ))}
                  </div>
                  <div className="mt-3 flex items-center gap-1.5">
                    <Button
                      size="sm"
                      variant="primary"
                      className="flex-1"
                      loading={installing === hit.projectId}
                      disabled={!hasLoader}
                      onClick={() => void installVersion(hit)}
                      icon={<Download size={13} />}
                    >
                      {t('mods.install')}
                    </Button>
                    <Button size="sm" onClick={() => void openDetail(hit)}>
                      {t('common.details')}
                    </Button>
                    <Button
                      size="sm"
                      onClick={() => void openExternal(`https://modrinth.com/mod/${hit.slug}`)}
                      title={t('mods.openPage')}
                    >
                      <ExternalLink size={13} />
                    </Button>
                  </div>
                </Card>
              );
            })}
          </div>
        </>
      ) : (
        <Card className="overflow-hidden">
          {installedLoading ? (
            <div className="space-y-2 p-4">
              {Array.from({ length: 4 }).map((_, i) => <div key={i} className="skeleton h-12" />)}
            </div>
          ) : installed.length === 0 ? (
            <div className="p-6">
              <EmptyState
                icon={<FileArchive size={26} />}
                title={t('mods.noInstalled')}
                hint={t('mods.importHint')}
                action={<Button icon={<Upload size={14} />} onClick={() => void importJar()}>{t('mods.importJar')}</Button>}
              />
            </div>
          ) : (
            <div className="divide-y divide-line/6">
              {installed.map((mod) => (
                <div key={mod.fileName} className="flex items-center gap-3 px-4 py-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-ink/6">
                    <FileArchive size={16} className="text-muted" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-[13px] font-semibold">{mod.projectName ?? mod.fileName}</span>
                      {mod.versionName && <Badge>{mod.versionName}</Badge>}
                      {!mod.enabled && <Badge tone="warning">{t('mods.disabledMark')}</Badge>}
                      {mod.updateVersionId && <Badge tone="info">{t('mods.updateAvailable')}</Badge>}
                    </div>
                    <div className="truncate text-[10.5px] text-muted">{mod.fileName}</div>
                  </div>
                  <Toggle checked={mod.enabled} onChange={() => void toggleMod(mod)} label={t('mods.toggleOn')} />
                  <Button
                    size="sm"
                    onClick={() =>
                      void call('profile_open_folder', { profileId: profile?.id }).catch(() => {
                        if (profile) void openPath(`${profile.gameDir}\\mods`).catch(() => {});
                      })
                    }
                    title={t('common.openFolder')}
                  >
                    <FolderOpen size={13} />
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => void removeMod(mod)} title={t('common.delete')}>
                    <Trash2 size={13} />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}

      {/* Mod detail ------------------------------------------------------- */}
      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail?.title}
        subtitle={detail ? t('mods.byAuthor', { author: detail.author }) : undefined}
        width="max-w-3xl"
        footer={
          detail && (
            <>
              <Button
                onClick={() => void openExternal(`https://modrinth.com/mod/${detail.slug}`)}
                icon={<ExternalLink size={14} />}
              >
                {t('mods.openPage')}
              </Button>
              <Button
                variant="primary"
                loading={installing === detail.projectId}
                disabled={!hasLoader}
                icon={<Download size={14} />}
                onClick={() => void installVersion(detail)}
              >
                {t('mods.install')}
              </Button>
            </>
          )
        }
      >
        {detail && (
          <div className="space-y-4">
            <div className="flex items-start gap-4">
              <ModIcon hit={detail} size={64} />
              <div className="min-w-0 flex-1">
                <p className="text-[12.5px] leading-relaxed text-muted">{detail.description}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-muted">
                  <span className="inline-flex items-center gap-1"><Download size={11} /> {formatNumber(detail.downloads)}</span>
                  {detail.categories.map((c) => <span key={c} className="chip">{c}</span>)}
                </div>
              </div>
            </div>

            {detail.gallery.length > 0 && (
              <div className="grid grid-cols-2 gap-2">
                {detail.gallery.slice(0, 4).map((src) => (
                  <img key={src} src={src} alt="" className="h-32 w-full rounded-xl object-cover" />
                ))}
              </div>
            )}

            {detailBody && (
              <div className="max-h-40 overflow-hidden rounded-xl border border-line/10 bg-ink/3 p-3 text-[11.5px] leading-relaxed text-muted">
                {detailBody.replace(/[#*_`>]/g, '').slice(0, 1200)}
              </div>
            )}

            <div>
              <div className="mb-2 flex items-center justify-between">
                <h4 className="text-[12px] font-bold uppercase tracking-wide text-muted">{t('mods.chooseVersion')}</h4>
                {profile && (
                  <Badge tone="info">
                    {profile.mcVersion} · {profile.loader.kind}
                  </Badge>
                )}
              </div>
              {versionsLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 3 }).map((_, i) => <div key={i} className="skeleton h-10" />)}
                </div>
              ) : versions.length === 0 ? (
                <div className="rounded-xl border border-warning/20 bg-warning/8 p-3 text-[12px] text-warning">
                  {t('mods.requiresGameVersion', {
                    version: profile?.mcVersion ?? '—',
                    loader: profile?.loader.kind ?? '—',
                  })}
                </div>
              ) : (
                <div className="max-h-52 space-y-1.5 overflow-y-auto pr-1">
                  {versions.map((v) => (
                    <div key={v.id} className="flex items-center gap-2 rounded-lg border border-line/8 bg-ink/3 px-3 py-2">
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[12px] font-semibold">{v.name || v.versionNumber}</div>
                        <div className="flex flex-wrap gap-1 text-[10px] text-muted">
                          <span>{v.versionNumber}</span>
                          <span>· {v.gameVersions.slice(0, 3).join(', ')}</span>
                          <span>· {v.loaders.join(', ')}</span>
                        </div>
                      </div>
                      <Button size="sm" variant="ghost" onClick={() => void installVersion(detail, v.id)}>
                        {t('mods.install')}
                      </Button>
                    </div>
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
