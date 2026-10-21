import { useEffect, useMemo, useState } from 'react';
import { FolderOpen, Save } from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, pickDirectory } from '../lib/bridge';
import type { LoaderKind, LoaderVersion, McVersion, Profile, ProfileDraft } from '../lib/types';
import { Button, Combobox, Input, Modal, Segmented, Slider, Toggle, cn } from './ui';

const LOADERS: { value: LoaderKind; label: string }[] = [
  { value: 'vanilla', label: 'Vanilla' },
  { value: 'fabric', label: 'Fabric' },
  { value: 'forge', label: 'Forge' },
  { value: 'neoforge', label: 'NeoForge' },
  { value: 'quilt', label: 'Quilt' },
];

export function ProfileForm({
  open,
  onClose,
  onSaved,
  initial,
  presetVersion,
}: {
  open: boolean;
  onClose: () => void;
  onSaved: (p: Profile) => void;
  initial?: Profile | null;
  presetVersion?: string | null;
}) {
  const t = useT();
  const { versions, settings, info, toast, toastError } = useApp();
  const [name, setName] = useState('');
  const [mcVersion, setMcVersion] = useState('');
  const [loader, setLoader] = useState<LoaderKind>('vanilla');
  const [loaderVersion, setLoaderVersion] = useState('');
  const [loaderList, setLoaderList] = useState<LoaderVersion[]>([]);
  const [loaderLoading, setLoaderLoading] = useState(false);
  const [loaderError, setLoaderError] = useState('');
  const [ram, setRam] = useState(settings.defaultRamMb || 4096);
  const [gameDir, setGameDir] = useState('');
  const [javaPath, setJavaPath] = useState('');
  const [jvmArgs, setJvmArgs] = useState('');
  const [width, setWidth] = useState(1280);
  const [height, setHeight] = useState(720);
  const [fullscreen, setFullscreen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const maxRam = Math.max(1024, Math.min(65536, Math.round((info?.totalRamMb ?? 8192) * 0.9)));

  const versionOptions = useMemo(() => {
    const rel = versions.filter((v) => v.type === 'release').map((v) => ({ value: v.id, label: v.id, hint: t('home.release') }));
    const rest = versions
      .filter((v) => v.type !== 'release')
      .map((v) => ({ value: v.id, label: v.id, hint: v.type.replace('old_', '').toUpperCase() }));
    return [...rel, ...rest];
  }, [versions, t]);

  useEffect(() => {
    if (!open) return;
    setError('');
    if (initial) {
      setName(initial.name);
      setMcVersion(initial.mcVersion);
      setLoader(initial.loader.kind);
      setLoaderVersion(initial.loader.version ?? '');
      setRam(initial.ramMb);
      setGameDir(initial.gameDir);
      setJavaPath(initial.javaPath ?? '');
      setJvmArgs(initial.jvmArgs);
      setWidth(initial.width);
      setHeight(initial.height);
      setFullscreen(initial.fullscreen);
    } else {
      const latest = presetVersion ?? versions.find((v) => v.type === 'release')?.id ?? '';
      setName(t('versions.profileNamePlaceholder'));
      setMcVersion(latest);
      setLoader('vanilla');
      setLoaderVersion('');
      setRam(settings.defaultRamMb || 4096);
      setGameDir('');
      setJavaPath(settings.javaPath ?? '');
      setJvmArgs(settings.jvmArgs ?? '');
      setWidth(settings.resolutionWidth);
      setHeight(settings.resolutionHeight);
      setFullscreen(settings.fullscreen);
    }
  }, [open, initial, presetVersion, versions, settings, t]);

  useEffect(() => {
    if (!open) return;
    setLoaderVersion('');
    setLoaderList([]);
    setLoaderError('');
    if (loader === 'vanilla' || !mcVersion) return;
    let alive = true;
    setLoaderLoading(true);
    void call<LoaderVersion[]>('loader_versions', { kind: loader, mcVersion })
      .then((list) => {
        if (!alive) return;
        setLoaderList(list);
        setLoaderVersion(list[0]?.version ?? '');
        if (list.length === 0) setLoaderError(t('versions.noLoaderVersions'));
      })
      .catch((e) => {
        if (!alive) return;
        setLoaderError(e instanceof Error ? e.message : t('versions.loaderVersionsError'));
      })
      .finally(() => alive && setLoaderLoading(false));
    return () => {
      alive = false;
    };
  }, [loader, mcVersion, open, t]);

  const submit = async () => {
    setError('');
    if (!name.trim()) return setError(t('common.name'));
    if (!mcVersion) return setError(t('versions.minecraftVersion'));
    if (ram > maxRam) return setError(t('settings.ramTooHigh', { max: maxRam }));
    setSaving(true);
    const draft: ProfileDraft = {
      name: name.trim(),
      mcVersion,
      loader: { kind: loader, version: loader === 'vanilla' ? null : loaderVersion || null },
      gameDir: gameDir.trim() || null,
      ramMb: ram,
      javaPath: javaPath.trim() || null,
      jvmArgs,
      width,
      height,
      fullscreen,
      source: initial?.source ?? 'manual',
      modpack: initial?.modpack ?? null,
    };
    try {
      const saved = initial
        ? await call<Profile>('profile_update', { profile: { ...initial, ...draft } })
        : await call<Profile>('profile_create', { draft });
      toast('success', initial ? t('versions.profileUpdated') : t('versions.profileCreated'));
      onSaved(saved);
      onClose();
    } catch (e) {
      toastError(e);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initial ? t('versions.editProfile') : t('versions.createProfile')}
      subtitle={t('versions.dirHint')}
      width="max-w-2xl"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="primary"
            loading={saving}
            disabled={loaderLoading}
            icon={<Save size={15} />}
            onClick={() => void submit()}
          >
            {initial ? t('versions.saveChanges') : t('versions.create')}
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label={t('versions.profileName')} value={name} onChange={(e) => setName(e.target.value)} maxLength={48} />
          <Combobox
            label={t('versions.minecraftVersion')}
            value={mcVersion}
            onChange={setMcVersion}
            options={versionOptions}
            placeholder={t('versions.searchPlaceholder')}
            emptyText={t('versions.noVersions')}
          />
        </div>

        <div>
          <span className="label">{t('versions.modLoader')}</span>
          <div className="grid grid-cols-5 gap-2">
            {LOADERS.map((l) => (
              <button
                key={l.value}
                type="button"
                onClick={() => setLoader(l.value)}
                className={cn(
                  'rounded-xl border px-2 py-2.5 text-[12px] font-semibold transition-all',
                  loader === l.value
                    ? 'border-accent/50 bg-accent/12 text-accent'
                    : 'border-line/10 bg-ink/4 text-muted hover:text-ink',
                )}
              >
                {l.label}
              </button>
            ))}
          </div>
        </div>

        {loader !== 'vanilla' && (
          <Combobox
            label={t('versions.loaderVersion')}
            value={loaderVersion}
            onChange={setLoaderVersion}
            options={loaderList.map((v) => ({
              value: v.version,
              label: v.version,
              hint: v.stable ? t('common.latest') : undefined,
            }))}
            placeholder={t('versions.searchLoader')}
            hint={
              loaderLoading
                ? t('common.loading')
                : loaderError || t('versions.loaderHint')
            }
            emptyText={t('versions.noLoaderVersions')}
            disabled={loaderLoading || loaderList.length === 0}
          />
        )}

        <div>
          <div className="mb-2 flex items-center justify-between">
            <span className="label mb-0">{t('versions.ram')}</span>
            <span className="text-[11px] text-muted">{t('versions.ramHint', { total: info?.totalRamMb ?? '—' })}</span>
          </div>
          <Slider value={ram} min={1024} max={maxRam} step={256} onChange={setRam} suffix="MB" />
          <div className="mt-2 flex gap-1.5">
            {[2048, 4096, 6144, 8192, 12288, 16384].filter((n) => n <= maxRam).map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setRam(n)}
                className={cn(
                  'rounded-md px-2 py-1 text-[10.5px] font-semibold transition-colors',
                  ram === n ? 'bg-accent/15 text-accent' : 'bg-ink/5 text-muted hover:text-ink',
                )}
              >
                {n / 1024} GB
              </button>
            ))}
          </div>
          {ram > (info?.availableRamMb ?? 8192) * 0.6 && (
            <div className="mt-2 text-[11px] text-warning">{t('settings.ramWarning')}</div>
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Input
              label={t('versions.gameDir')}
              value={gameDir}
              onChange={(e) => setGameDir(e.target.value)}
              placeholder={t('versions.gameDirHint')}
            />
          </div>
          <div className="flex items-end">
            <Button
              variant="ghost"
              icon={<FolderOpen size={15} />}
              className="w-full"
              onClick={() =>
                void pickDirectory()
                  .then((p) => p && setGameDir(p))
                  .catch(() => toast('error', t('common.notAvailable')))
              }
            >
              {t('common.browse')}
            </Button>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input label={t('versions.javaPath')} value={javaPath} onChange={(e) => setJavaPath(e.target.value)} placeholder={t('versions.javaAuto')} />
          <Input label={t('versions.jvmArgs')} value={jvmArgs} onChange={(e) => setJvmArgs(e.target.value)} placeholder="-XX:+UseG1GC" />
        </div>

        <div className="rounded-xl border border-line/10 bg-ink/3 p-1.5">
          <div className="flex flex-wrap items-center justify-between gap-3 px-2.5 py-2">
            <span className="text-[12px] font-semibold">{t('versions.resolution')}</span>
            <div className="flex items-center gap-2">
              <input
                type="number"
                className="input w-20 px-2 py-1 text-center text-[12px]"
                value={width}
                min={640}
                max={7680}
                onChange={(e) => setWidth(Number(e.target.value) || 1280)}
              />
              <span className="text-muted">×</span>
              <input
                type="number"
                className="input w-20 px-2 py-1 text-center text-[12px]"
                value={height}
                min={480}
                max={4320}
                onChange={(e) => setHeight(Number(e.target.value) || 720)}
              />
              <Toggle checked={fullscreen} onChange={setFullscreen} label={t('versions.fullscreen')} />
              <span className="text-[11px] text-muted">{t('versions.fullscreen')}</span>
            </div>
          </div>
        </div>

        {error && (
          <div className="rounded-lg border border-danger/25 bg-danger/10 px-3 py-2 text-[12px] text-danger">{error}</div>
        )}

        <div className="flex items-center gap-2 text-[11px] text-muted">
          <Segmented
            options={[
              { value: 'auto', label: t('versions.javaAuto') },
            ]}
            value="auto"
            onChange={() => {}}
            size="sm"
          />
          <span>{t('versions.dirHint')}</span>
        </div>
      </div>
    </Modal>
  );
}
