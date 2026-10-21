import { useEffect, useMemo, useState } from 'react';
import {
  Brush,
  Check,
  Coffee,
  Cpu,
  Download,
  ExternalLink,
  FileText,
  FolderOpen,
  Gauge,
  Info,
  KeyRound,
  Languages,
  ListChecks,
  MemoryStick,
  Monitor,
  Palette,
  RefreshCw,
  RotateCcw,
  Save,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  UserRound,
  Wrench,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, IS_TAURI, openExternal, openPath, pickDirectory } from '../lib/bridge';
import { LOCALES } from '../i18n/locales';
import type { AppSettings as Settings, JavaRuntime, ProfileStatus, ThemeId } from '../lib/types';
import {
  Badge,
  Button,
  Card,
  Input,
  Page,
  PageTitle,
  Segmented,
  Select,
  SettingRow,
  Slider,
  Tabs,
  Toggle,
  cn,
  formatBytes,
  useConfirm,
} from '../components/ui';

type Tab = 'general' | 'minecraft' | 'appearance' | 'downloadsTab' | 'accountsTab' | 'advanced';

const THEMES: { id: ThemeId; name: string; accent: string; bg: string; accent2: string }[] = [
  { id: 'dark', name: 'Dark', accent: '#58d68d', bg: '#0f1118', accent2: '#489eff' },
  { id: 'light', name: 'Light', accent: '#16a34a', bg: '#f0f3f9', accent2: '#2563eb' },
  { id: 'minecraft', name: 'Minecraft', accent: '#6cc53f', bg: '#0c140c', accent2: '#c48c4a' },
  { id: 'purple', name: 'Purple', accent: '#a776ff', bg: '#110d1c', accent2: '#ff69b4' },
  { id: 'ocean', name: 'Ocean', accent: '#22d3ee', bg: '#06131e', accent2: '#3b82f6' },
  { id: 'emerald', name: 'Emerald', accent: '#10b981', bg: '#071411', accent2: '#2dd4bf' },
  { id: 'midnight', name: 'Midnight', accent: '#818cf8', bg: '#080a12', accent2: '#38bdf8' },
  { id: 'custom', name: 'Custom', accent: '#58d68d', bg: '#10121a', accent2: '#489eff' },
];

export function SettingsPage() {
  const t = useT();
  const confirm = useConfirm();
  const {
    settings,
    accounts,
    profiles,
    info,
    saveSettings,
    setLocale,
    toast,
    toastError,
    refreshAccounts,
  } = useApp();
  const [tab, setTab] = useState<Tab>('general');
  const [javas, setJavas] = useState<JavaRuntime[]>([]);
  const [javaLoading, setJavaLoading] = useState(false);
  const [javaMajor, setJavaMajor] = useState('21');
  const [cfKey, setCfKey] = useState('');
  const [cfAvailable, setCfAvailable] = useState(settings.curseforgeKeySet);
  const [verifyBusy, setVerifyBusy] = useState(false);
  const [cacheSize, setCacheSize] = useState<number | null>(null);
  const [autoStart, setAutoStart] = useState(false);

  const activeProfile = profiles.find((p) => p.id === settings.activeProfileId) ?? profiles[0] ?? null;

  const loadJavas = async () => {
    setJavaLoading(true);
    try {
      const [managed, detected] = await Promise.all([
        call<JavaRuntime[]>('java_list').catch(() => []),
        call<JavaRuntime[]>('java_detect').catch(() => []),
      ]);
      const byPath = new Map<string, JavaRuntime>();
      [...managed, ...detected].forEach((j) => byPath.set(j.path, j));
      setJavas([...byPath.values()]);
    } finally {
      setJavaLoading(false);
    }
  };

  useEffect(() => {
    if (tab === 'minecraft') void loadJavas();
  }, [tab]);

  useEffect(() => {
    if (tab === 'advanced') {
      void call<number>('cache_size').then(setCacheSize).catch(() => setCacheSize(null));
      void call<boolean>('autostart_get').then(setAutoStart).catch(() => setAutoStart(false));
    }
  }, [tab]);

  const maxRam = Math.max(1024, Math.min(65536, Math.round((info?.totalRamMb ?? 8192) * 0.9)));

  const themeTabs = useMemo(
    () =>
      THEMES.map((theme) => ({ ...theme })),
    [],
  );

  const change = (patch: Partial<Settings>) => saveSettings(patch);

  const detect = async () => {
    await loadJavas();
    toast('success', t('settings.detected'));
  };

  const addJava = async () => {
    try {
      const path = await pickDirectory();
      if (!path) return;
      const java = await call<JavaRuntime>('java_add', { path });
      toast('success', `${t('settings.javaAdded')} · ${java.version}`);
      await loadJavas();
    } catch (e) {
      toastError(e);
    }
  };

  const downloadJava = async () => {
    try {
      const runtime = await call<JavaRuntime>('java_download', { major: Number(javaMajor) });
      toast('success', t('settings.javaDownloaded', { major: runtime.major }));
      await loadJavas();
    } catch (e) {
      toastError(e);
    }
  };

  const verify = async () => {
    if (!activeProfile) return;
    setVerifyBusy(true);
    try {
      const status = await call<ProfileStatus>('verify_files', { profileId: activeProfile.id });
      toast('success', `${t('versions.statusReady')} · ${status.missingFiles} / ${status.totalFiles}`);
    } catch (e) {
      toastError(e);
    } finally {
      setVerifyBusy(false);
    }
  };

  const clearCache = async () => {
    try {
      const freed = await call<number>('cache_clear');
      toast('success', `${t('settings.cacheCleared')} · ${formatBytes(freed)}`);
      setCacheSize(0);
    } catch (e) {
      toastError(e);
    }
  };

  const saveCurseForge = async () => {
    try {
      await call('curseforge_save_key', { key: cfKey.trim() });
      const status = await call<{ available: boolean }>('curseforge_status');
      setCfAvailable(status.available);
      setCfKey('');
      toast('success', status.available ? t('settings.curseforgeActive') : t('errors.curseforgeKey'));
    } catch (e) {
      toastError(e);
    }
  };

  const reset = async () => {
    const ok = await confirm({
      title: t('settings.reset'),
      message: t('settings.resetConfirm'),
      danger: true,
      confirmLabel: t('common.reset'),
    });
    if (!ok) return;
    try {
      const fresh = await call<Settings>('settings_reset');
      useApp.setState({ settings: fresh });
      toast('success', t('settings.resetDone'));
    } catch (e) {
      toastError(e);
    }
  };

  return (
    <Page>
      <PageTitle
        title={t('settings.title')}
        subtitle={t('settings.subtitle')}
        actions={<Badge tone="accent">{t('settings.autosave')}</Badge>}
      />

      <Tabs
        value={tab}
        onChange={setTab}
        className="mb-5"
        tabs={[
          { value: 'general', label: t('settings.general'), icon: <Settings2 size={13} /> },
          { value: 'minecraft', label: t('settings.minecraft'), icon: <Cpu size={13} /> },
          { value: 'appearance', label: t('settings.appearance'), icon: <Palette size={13} /> },
          { value: 'downloadsTab', label: t('settings.downloadsTab'), icon: <Download size={13} /> },
          { value: 'accountsTab', label: t('settings.accountsTab'), icon: <UserRound size={13} /> },
          { value: 'advanced', label: t('settings.advanced'), icon: <Wrench size={13} /> },
        ]}
      />

      {/* ------------------------------------------------------------ */}
      {tab === 'general' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="p-5">
            <SectionTitle icon={<Languages size={15} />} title={t('settings.language')} hint={t('settings.languageHint')} />
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
              {LOCALES.map((locale) => (
                <button
                  key={locale.code}
                  onClick={() => {
                    setLocale(locale.code);
                    toast('success', t('settings.languageChanged'));
                  }}
                  className={cn(
                    'flex items-center gap-2.5 rounded-xl border px-3 py-2.5 text-left text-[12.5px] font-semibold transition-all',
                    settings.locale === locale.code
                      ? 'border-accent/50 bg-accent/10 text-accent'
                      : 'border-line/10 bg-ink/3 text-muted hover:text-ink',
                  )}
                >
                  <span className="text-base">{locale.flag}</span>
                  <span className="flex-1 truncate">{locale.label}</span>
                  {settings.locale === locale.code && <Check size={13} />}
                </button>
              ))}
            </div>
          </Card>

          <Card className="divide-y divide-line/6 px-5">
            <SettingRow title={t('settings.autoUpdates')} hint={IS_TAURI ? undefined : t('settings.startupHint')}>
              <Toggle checked={settings.autoUpdate} onChange={(v) => change({ autoUpdate: v })} />
            </SettingRow>
            <SettingRow title={t('settings.launchWithWindows')}>
              <Toggle
                checked={autoStart}
                onChange={(v) => {
                  setAutoStart(v);
                  void call('autostart_set', { enabled: v }).catch(() => {
                    setAutoStart(!v);
                    toast('error', t('settings.startupHint'));
                  });
                }}
              />
            </SettingRow>
            <SettingRow title={t('settings.minimize')}>
              <Toggle checked={settings.minimizeOnLaunch} onChange={(v) => change({ minimizeOnLaunch: v })} />
            </SettingRow>
            <SettingRow title={t('settings.closeAfter')}>
              <Toggle checked={settings.closeOnLaunch} onChange={(v) => change({ closeOnLaunch: v })} />
            </SettingRow>
          </Card>

          <Card className="flex items-center gap-4 p-5">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-accent/12 text-accent">
              <Monitor size={19} />
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-[13px] font-semibold">{t('settings.scaleHint')}</div>
              <div className="mt-0.5 text-[11px] text-muted">
                {t('settings.systemInfo', { os: info?.os ?? '—', ram: info?.totalRamMb ?? '—' })}
              </div>
            </div>
            <div className="w-56">
              <Slider
                value={Math.round(settings.uiScale * 100)}
                min={80}
                max={125}
                step={5}
                onChange={(v) => change({ uiScale: v / 100 })}
                suffix="%"
              />
            </div>
          </Card>
        </div>
      )}

      {/* ------------------------------------------------------------ */}
      {tab === 'minecraft' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="p-5">
            <SectionTitle icon={<MemoryStick size={15} />} title={t('settings.memory')} hint={t('settings.memoryHint')} />
            <Slider
              value={settings.defaultRamMb}
              min={1024}
              max={maxRam}
              step={256}
              onChange={(v) => change({ defaultRamMb: v })}
              suffix="MB"
            />
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              {[2048, 4096, 6144, 8192, 12288, 16384].filter((n) => n <= maxRam).map((n) => (
                <button
                  key={n}
                  onClick={() => change({ defaultRamMb: n })}
                  className={cn(
                    'rounded-md px-2.5 py-1 text-[11px] font-semibold transition-colors',
                    settings.defaultRamMb === n ? 'bg-accent/15 text-accent' : 'bg-ink/5 text-muted hover:text-ink',
                  )}
                >
                  {n / 1024} GB
                </button>
              ))}
              <span className="ml-auto text-[11px] text-muted">
                {t('settings.available')}: {info?.availableRamMb ?? '—'} MB
              </span>
            </div>
            {settings.defaultRamMb > (info?.availableRamMb ?? 16384) * 0.6 && (
              <div className="mt-2 text-[11px] text-warning">{t('settings.ramWarning')}</div>
            )}
          </Card>

          <Card className="divide-y divide-line/6 p-5 pt-4">
            <div className="pb-4">
              <Input
                label={t('settings.minecraftDir')}
                hint={t('settings.minecraftDirHint')}
                value={settings.minecraftDir}
                onChange={(e) => change({ minecraftDir: e.target.value })}
              />
              <div className="mt-2 flex gap-2">
                <Button
                  size="sm"
                  icon={<FolderOpen size={13} />}
                  onClick={() =>
                    void pickDirectory()
                      .then((p) => p && change({ minecraftDir: p }))
                      .catch(() => toast('error', t('common.notAvailable')))
                  }
                >
                  {t('common.browse')}
                </Button>
                <Button
                  size="sm"
                  icon={<ExternalLink size={13} />}
                  onClick={() => void openPath(settings.minecraftDir).catch(() => toastError(t('common.notAvailable')))}
                >
                  {t('common.openFolder')}
                </Button>
              </div>
            </div>
            <SettingRow title={t('settings.jvmArgs')} hint={t('settings.jvmHint')}>
              <input
                className="input w-[300px] text-right font-mono text-[11.5px]"
                value={settings.jvmArgs}
                onChange={(e) => change({ jvmArgs: e.target.value })}
                placeholder="-XX:+UseG1GC"
              />
            </SettingRow>
            <SettingRow title={t('settings.resolution')}>
              <div className="flex items-center gap-1.5">
                <input
                  type="number"
                  className="input w-[76px] px-2 py-1.5 text-center text-[12px]"
                  value={settings.resolutionWidth}
                  onChange={(e) => change({ resolutionWidth: Number(e.target.value) || 1280 })}
                />
                <span className="text-muted">×</span>
                <input
                  type="number"
                  className="input w-[76px] px-2 py-1.5 text-center text-[12px]"
                  value={settings.resolutionHeight}
                  onChange={(e) => change({ resolutionHeight: Number(e.target.value) || 720 })}
                />
              </div>
            </SettingRow>
            <SettingRow title={t('settings.fullscreen')}>
              <Toggle checked={settings.fullscreen} onChange={(v) => change({ fullscreen: v })} />
            </SettingRow>
          </Card>

          <Card className="p-5">
            <SectionTitle
              icon={<Coffee size={15} />}
              title={t('settings.javaPath')}
              hint={t('settings.javaAuto')}
              right={
                <div className="flex items-center gap-1.5">
                  <Button size="sm" loading={javaLoading} icon={<RefreshCw size={13} />} onClick={() => void detect()}>
                    {t('settings.detect')}
                  </Button>
                  <Button size="sm" onClick={() => void addJava()}>{t('settings.browseJava')}</Button>
                </div>
              }
            />
            {settings.javaPath && (
              <div className="mb-3 flex items-center gap-2 rounded-xl border border-accent/25 bg-accent/8 px-3 py-2 text-[11.5px]">
                <ShieldCheck size={13} className="text-accent" />
                <span className="flex-1 truncate font-mono">{settings.javaPath}</span>
                <Button size="sm" variant="subtle" onClick={() => change({ javaPath: null })}>
                  {t('settings.javaAuto')}
                </Button>
              </div>
            )}
            <div className="space-y-1.5">
              {javas.length === 0 && !javaLoading && (
                <div className="rounded-xl border border-line/10 bg-ink/3 px-3 py-4 text-center text-[11.5px] text-muted">
                  {t('setup.javaNone')}
                </div>
              )}
              {javas.map((java) => (
                <button
                  key={java.path}
                  onClick={() => change({ javaPath: java.path })}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left transition-colors',
                    settings.javaPath === java.path
                      ? 'border-accent/40 bg-accent/8'
                      : 'border-line/8 bg-ink/3 hover:bg-ink/6',
                  )}
                >
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent2/12 text-accent2">
                    <Coffee size={14} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="text-[12px] font-semibold">
                      Java {java.major} · {java.vendor}
                    </div>
                    <div className="truncate font-mono text-[10px] text-muted">{java.path}</div>
                  </div>
                  <Badge tone={java.source === 'managed' ? 'accent' : 'default'}>
                    {java.source === 'managed' ? t('settings.managed') : java.arch}
                  </Badge>
                  {settings.javaPath === java.path && <Check size={14} className="text-accent" />}
                </button>
              ))}
            </div>
            <div className="mt-4 flex flex-wrap items-center gap-2 rounded-xl border border-line/10 bg-ink/3 p-3">
              <Coffee size={14} className="text-muted" />
              <span className="text-[11.5px] text-muted">{t('settings.downloadJava', { major: javaMajor })}</span>
              <div className="ml-auto flex items-center gap-2">
                <Select
                  value={javaMajor}
                  onChange={setJavaMajor}
                  options={[
                    { value: '8', label: 'Java 8' },
                    { value: '17', label: 'Java 17' },
                    { value: '21', label: 'Java 21' },
                  ]}
                  className="w-32"
                />
                <Button size="sm" variant="primary" icon={<Download size={13} />} onClick={() => void downloadJava()}>
                  {t('common.install')}
                </Button>
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* ------------------------------------------------------------ */}
      {tab === 'appearance' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="p-5">
            <SectionTitle icon={<Palette size={15} />} title={t('settings.theme')} hint={t('settings.themeHint')} />
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
              {themeTabs.map((theme) => (
                <button
                  key={theme.id}
                  onClick={() => {
                    change({ theme: theme.id });
                    toast('success', t('settings.themeChanged'));
                  }}
                  className={cn(
                    'group relative overflow-hidden rounded-xl border text-left transition-all duration-200',
                    settings.theme === theme.id
                      ? 'border-accent/60 ring-1 ring-accent/40'
                      : 'border-line/10 hover:border-line/25',
                  )}
                >
                  <div className="h-16 w-full p-2" style={{ background: theme.bg }}>
                    <div className="flex h-full flex-col justify-between rounded-md p-1.5" style={{ background: `${theme.bg}dd` }}>
                      <div className="flex gap-1">
                        <span className="h-1.5 w-6 rounded-full" style={{ background: theme.accent }} />
                        <span className="h-1.5 w-3 rounded-full" style={{ background: theme.accent2 }} />
                      </div>
                      <div className="flex items-end gap-1">
                        <span className="h-2 w-8 rounded-sm" style={{ background: `${theme.accent}55` }} />
                        <span className="h-2 w-4 rounded-sm" style={{ background: `${theme.accent2}55` }} />
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center justify-between px-2.5 py-1.5">
                    <span className="text-[11.5px] font-semibold">{theme.name}</span>
                    {settings.theme === theme.id && <Check size={12} className="text-accent" />}
                  </div>
                </button>
              ))}
            </div>
          </Card>

          {settings.theme === 'custom' && (
            <Card className="p-5">
              <SectionTitle icon={<Brush size={15} />} title={t('settings.customTheme')} />
              <div className="grid gap-4 sm:grid-cols-3">
                {(
                  [
                    ['accent', t('settings.accent')],
                    ['accent2', t('settings.accent2')],
                    ['background', t('settings.background')],
                  ] as const
                ).map(([key, label]) => (
                  <label key={key} className="block">
                    <span className="label">{label}</span>
                    <div className="flex items-center gap-2 rounded-xl border border-line/10 bg-ink/4 p-1.5">
                      <input
                        type="color"
                        value={settings.customTheme[key]}
                        onChange={(e) =>
                          change({ customTheme: { ...settings.customTheme, [key]: e.target.value } })
                        }
                        className="h-8 w-10 cursor-pointer rounded border-0 bg-transparent"
                      />
                      <input
                        className="w-full bg-transparent font-mono text-[12px] outline-none"
                        value={settings.customTheme[key]}
                        onChange={(e) =>
                          change({ customTheme: { ...settings.customTheme, [key]: e.target.value } })
                        }
                      />
                    </div>
                  </label>
                ))}
              </div>
              <div className="mt-4">
                <span className="label">{t('settings.transparency')}</span>
                <Slider
                  value={settings.customTheme.transparency}
                  min={25}
                  max={97}
                  onChange={(v) => change({ customTheme: { ...settings.customTheme, transparency: v } })}
                  suffix="%"
                />
              </div>
            </Card>
          )}

          <Card className="divide-y divide-line/6 px-5">
            <SettingRow title={t('settings.animations')}>
              <Segmented
                value={settings.animations}
                onChange={(v) => change({ animations: v })}
                options={[
                  { value: 'full', label: t('settings.animFull') },
                  { value: 'low', label: t('settings.animLow') },
                  { value: 'off', label: t('settings.animOff') },
                ]}
              />
            </SettingRow>
            <SettingRow title={t('settings.uiScale')} hint={t('settings.scaleHint')}>
              <div className="w-64">
                <Slider
                  value={Math.round(settings.uiScale * 100)}
                  min={80}
                  max={125}
                  step={5}
                  onChange={(v) => change({ uiScale: v / 100 })}
                  suffix="%"
                />
              </div>
            </SettingRow>
          </Card>
        </div>
      )}

      {/* ------------------------------------------------------------ */}
      {tab === 'downloadsTab' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="divide-y divide-line/6 px-5">
            <SettingRow title={t('settings.maxDownloads')}>
              <div className="w-64">
                <Slider
                  value={settings.maxConcurrentDownloads}
                  min={1}
                  max={32}
                  onChange={(v) => change({ maxConcurrentDownloads: v })}
                />
              </div>
            </SettingRow>
            <SettingRow title={t('settings.speedLimit')} hint={t('settings.speedUnlimited')}>
              <div className="flex items-center gap-2">
                <div className="w-52">
                  <Slider
                    value={settings.speedLimitKbps}
                    min={0}
                    max={102400}
                    step={512}
                    onChange={(v) => change({ speedLimitKbps: v })}
                  />
                </div>
                <span className="w-20 text-right text-[11px] text-muted">
                  {settings.speedLimitKbps ? `${(settings.speedLimitKbps / 1024).toFixed(0)} MB/s` : '∞'}
                </span>
              </div>
            </SettingRow>
            <SettingRow title={t('settings.clearCache')} hint={t('settings.cacheSize', { size: cacheSize != null ? formatBytes(cacheSize) : '—' })}>
              <Button size="sm" variant="danger" icon={<Trash2 size={13} />} onClick={() => void clearCache()}>
                {t('settings.clearCache')}
              </Button>
            </SettingRow>
          </Card>
        </div>
      )}

      {/* ------------------------------------------------------------ */}
      {tab === 'accountsTab' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="p-5">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-[13px] font-semibold">{t('accounts.title')}</div>
                <div className="text-[11px] text-muted">{t('accounts.subtitle')}</div>
              </div>
              <Button size="sm" onClick={() => (window.location.hash = '#/accounts')}>
                {t('accounts.add')}
              </Button>
            </div>
            <div className="mt-4 space-y-1.5">
              {accounts.length === 0 && (
                <div className="rounded-xl border border-line/10 bg-ink/3 px-3 py-4 text-center text-[11.5px] text-muted">
                  {t('accounts.noAccountsHint')}
                </div>
              )}
              {accounts.map((a) => (
                <div key={a.id} className="flex items-center gap-3 rounded-xl border border-line/8 bg-ink/3 px-3 py-2">
                  <UserRound size={14} className="text-muted" />
                  <div className="min-w-0 flex-1">
                    <div className="text-[12px] font-semibold">{a.name}</div>
                    <div className="truncate font-mono text-[10px] text-muted">{a.uuid}</div>
                  </div>
                  <Badge tone={a.kind === 'microsoft' ? 'info' : 'warning'}>
                    {a.kind === 'microsoft' ? 'Microsoft' : t('accounts.offline')}
                  </Badge>
                  {a.primary && <Badge tone="success">{t('accounts.primary')}</Badge>}
                  <Button
                    size="sm"
                    variant="subtle"
                    onClick={() =>
                      void call('account_set_primary', { id: a.id }).then(refreshAccounts).catch(toastError)
                    }
                  >
                    {t('accounts.setPrimary')}
                  </Button>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}

      {/* ------------------------------------------------------------ */}
      {tab === 'advanced' && (
        <div className="space-y-4 animate-fade-up">
          <Card className="p-5">
            <SectionTitle icon={<FileText size={15} />} title={t('settings.logs')} />
            <div className="flex flex-wrap gap-2">
              <Button size="sm" icon={<FolderOpen size={13} />} onClick={() => void call('app_open_logs_dir').catch(toastError)}>
                {t('settings.openLogs')}
              </Button>
              <Button size="sm" icon={<Save size={13} />} onClick={() => void call('logs_export').then(() => toast('success', t('settings.logsExported'))).catch(toastError)}>
                {t('settings.exportLogs')}
              </Button>
              <Button size="sm" variant="danger" icon={<Trash2 size={13} />} onClick={() => void call('logs_clear').then(() => toast('success', t('settings.logsCleared'))).catch(toastError)}>
                {t('settings.clearLogs')}
              </Button>
            </div>
          </Card>

          <Card className="p-5">
            <SectionTitle icon={<ShieldCheck size={15} />} title={t('settings.curseforge')} hint={t('settings.curseforgeHint')} />
            <div className="flex flex-wrap items-center gap-2">
              <input
                className="input max-w-md flex-1 font-mono text-[12px]"
                type="password"
                placeholder={t('settings.curseforgeKey')}
                value={cfKey}
                onChange={(e) => setCfKey(e.target.value)}
              />
              <Button size="sm" variant="primary" icon={<KeyRound size={13} />} disabled={!cfKey.trim()} onClick={() => void saveCurseForge()}>
                {t('settings.curseforgeSave')}
              </Button>
              {cfAvailable && (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    void call('curseforge_save_key', { key: '' }).then(() => setCfAvailable(false)).catch(toastError)
                  }
                >
                  {t('settings.curseforgeRemove')}
                </Button>
              )}
              <Badge tone={cfAvailable ? 'success' : 'warning'}>
                {cfAvailable ? t('settings.curseforgeActive') : t('settings.curseforgeMissing')}
              </Badge>
            </div>
            <div className="mt-2 flex items-center gap-1 text-[10.5px] text-muted">
              <Info size={11} />
              <span>
                {t('settings.curseforgeHint')}{' '}
                <button
                  className="text-accent underline-offset-2 hover:underline"
                  onClick={() => void openExternal('https://console.curseforge.com/')}
                >
                  console.curseforge.com
                </button>
              </span>
            </div>
          </Card>

          <Card className="p-5">
            <SectionTitle icon={<ListChecks size={15} />} title={t('settings.verifyFiles')} />
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" loading={verifyBusy} icon={<ListChecks size={13} />} onClick={() => void verify()} disabled={!activeProfile}>
                {t('settings.verifyFiles')}
              </Button>
              <Button size="sm" icon={<FolderOpen size={13} />} onClick={() => void call('app_open_data_dir').catch(toastError)}>
                {t('settings.dataDir')}
              </Button>
              {activeProfile && <Badge>{activeProfile.name}</Badge>}
            </div>
          </Card>

          <Card className="flex flex-wrap items-center justify-between gap-3 border-danger/20 p-5">
            <div>
              <div className="text-[13px] font-semibold">{t('settings.reset')}</div>
              <div className="text-[11px] text-muted">{t('settings.resetConfirm')}</div>
            </div>
            <Button variant="danger" icon={<RotateCcw size={14} />} onClick={() => void reset()}>
              {t('settings.reset')}
            </Button>
          </Card>

          <Card className="p-5">
            <div className="flex items-start gap-4">
              <img src="/logo.png" alt="" className="h-14 w-14 rounded-2xl shadow-glow" />
              <div className="min-w-0 flex-1">
                <div className="font-display text-[15px] font-bold">WinLaucher {info?.version ?? '1.0.0'}</div>
                <p className="mt-1 max-w-xl text-[11.5px] leading-relaxed text-muted">{t('settings.aboutText')}</p>
                <div className="mt-2 flex flex-wrap gap-3 text-[11px] text-muted">
                  <span>{t('settings.dataDir')}: <span className="font-mono">{info?.dataDir ?? '—'}</span></span>
                  <span>{info?.os} · {info?.arch}</span>
                </div>
              </div>
              <Button
                size="sm"
                icon={<ExternalLink size={13} />}
                onClick={() => void openExternal('https://github.com/egorbelovziiik-design/WinLauncher')}
              >
                GitHub
              </Button>
            </div>
          </Card>
        </div>
      )}
    </Page>
  );
}

function SectionTitle({
  icon,
  title,
  hint,
  right,
}: {
  icon: React.ReactNode;
  title: string;
  hint?: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className="text-accent">{icon}</span>
        <div>
          <div className="text-[13px] font-bold">{title}</div>
          {hint && <div className="text-[11px] text-muted">{hint}</div>}
        </div>
      </div>
      {right}
    </div>
  );
}
