import { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Check,
  Coffee,
  Download,
  Globe2,
  Loader2,
  Palette,
  Play,
  Sparkles,
  UserRound,
  Wifi,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call } from '../lib/bridge';
import { LOCALES } from '../i18n/locales';
import type { Account, JavaRuntime, Profile, ThemeId } from '../lib/types';
import { Badge, Button, Input, Select, cn } from '../components/ui';

const THEMES: { id: ThemeId; name: string; accent: string; bg: string; accent2: string }[] = [
  { id: 'dark', name: 'Dark', accent: '#58d68d', bg: '#0f1118', accent2: '#489eff' },
  { id: 'light', name: 'Light', accent: '#16a34a', bg: '#f0f3f9', accent2: '#2563eb' },
  { id: 'minecraft', name: 'Minecraft', accent: '#6cc53f', bg: '#0c140c', accent2: '#c48c4a' },
  { id: 'purple', name: 'Purple', accent: '#a776ff', bg: '#110d1c', accent2: '#ff69b4' },
  { id: 'ocean', name: 'Ocean', accent: '#22d3ee', bg: '#06131e', accent2: '#3b82f6' },
  { id: 'emerald', name: 'Emerald', accent: '#10b981', bg: '#071411', accent2: '#2dd4bf' },
  { id: 'midnight', name: 'Midnight', accent: '#818cf8', bg: '#080a12', accent2: '#38bdf8' },
];

export function Onboarding() {
  const t = useT();
  const {
    settings,
    versions,
    profiles,
    accounts,
    info,
    saveSettings,
    setLocale,
    refreshProfiles,
    refreshAccounts,
    refreshVersions,
    setActiveProfile,
    setActiveAccount,
    toast,
    toastError,
  } = useApp();

  const [step, setStep] = useState(0);
  const [nickname, setNickname] = useState('');
  const [accountBusy, setAccountBusy] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [javas, setJavas] = useState<JavaRuntime[]>([]);
  const [javaBusy, setJavaBusy] = useState(false);
  const [javaDownloading, setJavaDownloading] = useState(false);
  const [profileName, setProfileName] = useState('Мой Minecraft');
  const [profileVersion, setProfileVersion] = useState('');
  const [creating, setCreating] = useState(false);
  const [createdProfile, setCreatedProfile] = useState<Profile | null>(null);

  const totalSteps = 5;
  const latestRelease = useMemo(() => versions.find((v) => v.type === 'release')?.id ?? '', [versions]);
  const releaseOptions = useMemo(
    () => versions.filter((v) => v.type === 'release').slice(0, 40).map((v) => ({ value: v.id, label: v.id })),
    [versions],
  );

  useEffect(() => {
    if (versions.length === 0) void refreshVersions();
  }, [versions.length, refreshVersions]);

  useEffect(() => {
    if (!profileVersion && latestRelease) setProfileVersion(latestRelease);
  }, [latestRelease, profileVersion]);

  useEffect(() => {
    if (step !== 3) return;
    setJavaBusy(true);
    void Promise.all([
      call<JavaRuntime[]>('java_list').catch(() => []),
      call<JavaRuntime[]>('java_detect').catch(() => []),
    ])
      .then(([a, b]) => setJavas([...a, ...b]))
      .finally(() => setJavaBusy(false));
  }, [step]);

  const finish = () => {
    saveSettings({ onboarded: true });
  };

  const createOffline = async () => {
    setAccountError('');
    if (!/^[A-Za-z0-9_]{3,16}$/.test(nickname.trim())) {
      setAccountError(t('accounts.invalidName'));
      return;
    }
    setAccountBusy(true);
    try {
      const account = await call<Account>('account_add_offline', { name: nickname.trim() });
      await refreshAccounts();
      setActiveAccount(account.id);
      setNickname('');
      toast('success', t('accounts.authSuccess', { name: account.name }));
      setStep(3);
    } catch (e) {
      toastError(e);
    } finally {
      setAccountBusy(false);
    }
  };

  const downloadJava = async () => {
    setJavaDownloading(true);
    try {
      const runtime = await call<JavaRuntime>('java_download', { major: 21 });
      setJavas((list) => [...list, runtime]);
      toast('success', t('settings.javaDownloaded', { major: 21 }));
    } catch (e) {
      toastError(e);
    } finally {
      setJavaDownloading(false);
    }
  };

  const createProfile = async () => {
    setCreating(true);
    try {
      const profile = await call<Profile>('profile_create', {
        draft: {
          name: profileName.trim() || 'Minecraft',
          mcVersion: profileVersion,
          loader: { kind: 'vanilla', version: null },
          ramMb: settings.defaultRamMb,
          gameDir: null,
          javaPath: settings.javaPath,
          jvmArgs: settings.jvmArgs,
          width: settings.resolutionWidth,
          height: settings.resolutionHeight,
          fullscreen: settings.fullscreen,
          source: 'manual',
          modpack: null,
        },
      });
      setCreatedProfile(profile);
      await refreshProfiles();
      setActiveProfile(profile.id);
      toast('success', t('setup.profileCreated', { name: profile.name }));
      setStep(5);
    } catch (e) {
      toastError(e);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="relative flex h-full flex-col overflow-hidden bg-bg">
      <img src="/hero.jpg" alt="" className="absolute inset-0 h-full w-full object-cover opacity-25" />
      <div className="absolute inset-0 bg-gradient-to-b from-bg/80 via-bg/92 to-bg" />
      <div className="absolute -left-32 top-10 h-96 w-96 rounded-full bg-accent/20 blur-[120px]" />
      <div className="absolute -right-24 bottom-0 h-80 w-80 rounded-full bg-accent2/20 blur-[120px]" />

      <div className="relative flex items-center justify-between px-8 pt-7">
        <div className="flex items-center gap-3">
          <img src="/logo.png" alt="" className="h-10 w-10 rounded-xl shadow-glow" />
          <div>
            <div className="font-display text-[15px] font-extrabold leading-tight">WinLaucher</div>
            <div className="text-[10px] uppercase tracking-[0.18em] text-muted">{t('app.tagline')}</div>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          {Array.from({ length: totalSteps }).map((_, i) => (
            <span
              key={i}
              className={cn(
                'h-1.5 rounded-full transition-all duration-300',
                i === Math.min(step, totalSteps - 1) ? 'w-7 bg-accent' : i < step ? 'w-3 bg-accent/50' : 'w-3 bg-ink/15',
              )}
            />
          ))}
        </div>
      </div>

      <div className="relative flex flex-1 items-center justify-center px-6 py-6">
        <div className="w-full max-w-3xl animate-fade-up" key={step}>
          {step === 0 && (
            <div className="text-center">
              <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-3xl border border-accent/25 bg-accent/10 backdrop-blur-md">
                <Sparkles size={34} className="text-accent" />
              </div>
              <h1 className="mt-6 font-display text-[40px] font-extrabold leading-tight tracking-tight">
                {t('setup.welcomeTitle')}
              </h1>
              <p className="mx-auto mt-3 max-w-md text-[14px] leading-relaxed text-muted">{t('setup.welcomeSub')}</p>
              <div className="mx-auto mt-8 flex max-w-md items-center justify-center gap-2">
                <Button size="lg" variant="primary" icon={<ArrowRight size={17} />} onClick={() => setStep(1)}>
                  {t('setup.start')}
                </Button>
                <Button size="lg" onClick={finish}>
                  {t('common.skip')}
                </Button>
              </div>
              <div className="mt-8 flex items-center justify-center gap-6 text-[11px] text-muted">
                <span className="flex items-center gap-1.5"><Play size={11} /> Minecraft Java</span>
                <span className="flex items-center gap-1.5"><Globe2 size={11} /> {LOCALES.length} languages</span>
                <span className="flex items-center gap-1.5"><Palette size={11} /> 8 themes</span>
              </div>
            </div>
          )}

          {step === 1 && (
            <div>
              <StepTitle title={t('setup.languageTitle')} sub={t('setup.languageSub')} />
              <div className="mt-5 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
                {LOCALES.map((l) => (
                  <button
                    key={l.code}
                    onClick={() => {
                      setLocale(l.code);
                      setProfileName(l.code === 'ru' ? 'Мой Minecraft' : 'My Minecraft');
                    }}
                    className={cn(
                      'flex items-center gap-2 rounded-xl border px-3 py-2.5 text-left text-[12.5px] font-semibold transition-all',
                      settings.locale === l.code
                        ? 'border-accent/50 bg-accent/10 text-accent'
                        : 'border-line/10 bg-panel/70 text-muted hover:text-ink',
                    )}
                  >
                    <span className="text-base">{l.flag}</span>
                    <span className="flex-1 truncate">{l.label}</span>
                    {settings.locale === l.code && <Check size={13} />}
                  </button>
                ))}
              </div>
              <StepTitle className="mt-7" title={t('setup.themeTitle')} sub={t('setup.themeSub')} />
              <div className="mt-4 grid grid-cols-4 gap-2 sm:grid-cols-7">
                {THEMES.map((theme) => (
                  <button
                    key={theme.id}
                    onClick={() => saveSettings({ theme: theme.id })}
                    className={cn(
                      'overflow-hidden rounded-xl border transition-all',
                      settings.theme === theme.id ? 'border-accent/60 ring-1 ring-accent/40' : 'border-line/10',
                    )}
                  >
                    <div className="h-14 p-2" style={{ background: theme.bg }}>
                      <div className="flex h-full flex-col justify-between rounded-md p-1.5" style={{ background: `${theme.bg}dd` }}>
                        <span className="h-1.5 w-6 rounded-full" style={{ background: theme.accent }} />
                        <span className="h-2 w-8 rounded-sm" style={{ background: `${theme.accent2}66` }} />
                      </div>
                    </div>
                    <div className="px-2 py-1.5 text-[10.5px] font-semibold">{theme.name}</div>
                  </button>
                ))}
              </div>
              <Nav onBack={undefined} onNext={() => setStep(2)} />
            </div>
          )}

          {step === 2 && (
            <div>
              <StepTitle title={t('setup.accountTitle')} sub={t('setup.accountSub')} />
              <div className="mt-5 grid gap-3 md:grid-cols-2">
                <div className="rounded-2xl border border-line/12 bg-panel/70 p-5 backdrop-blur-md">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent2/15 text-accent2">
                    <BadgeCheck size={20} />
                  </div>
                  <h3 className="mt-3 font-display text-[15px] font-bold">{t('accounts.microsoft')}</h3>
                  <p className="mt-1 text-[11.5px] leading-relaxed text-muted">{t('accounts.microsoftHint')}</p>
                  <Button
                    className="mt-3 w-full"
                    variant="primary"
                    icon={<UserRound size={14} />}
                    onClick={() => {
                      saveSettings({ onboarded: true });
                      window.location.hash = '#/accounts';
                      toast('info', t('accounts.deviceHint'));
                    }}
                  >
                    {t('accounts.signIn')}
                  </Button>
                </div>
                <div className="rounded-2xl border border-line/12 bg-panel/70 p-5 backdrop-blur-md">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent/15 text-accent">
                    <Wifi size={20} />
                  </div>
                  <h3 className="mt-3 font-display text-[15px] font-bold">{t('accounts.offline')}</h3>
                  <Input
                    className="mt-2"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    placeholder={t('accounts.nicknamePlaceholder')}
                    error={accountError}
                    maxLength={16}
                  />
                  <Button
                    className="mt-2 w-full"
                    loading={accountBusy}
                    onClick={() => void createOffline()}
                    icon={<Check size={14} />}
                  >
                    {t('accounts.addOffline')}
                  </Button>
                  {accounts.length > 0 && (
                    <div className="mt-2 text-[10.5px] text-success">
                      {t('accounts.authSuccess', { name: accounts[0].name })}
                    </div>
                  )}
                </div>
              </div>
              <Nav onBack={() => setStep(1)} onNext={() => setStep(3)} nextLabel={t('common.skip')} />
            </div>
          )}

          {step === 3 && (
            <div>
              <StepTitle title={t('setup.javaTitle')} sub={t('setup.javaSub')} />
              <div className="mt-5 rounded-2xl border border-line/12 bg-panel/70 p-5 backdrop-blur-md">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-accent/12 text-accent">
                    <Coffee size={20} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-[13px] font-semibold">
                      {javaBusy ? t('setup.javaSearching') : javas.length ? t('settings.detected') : t('settings.notDetected')}
                      {javaBusy && <Loader2 size={13} className="animate-spin text-accent" />}
                    </div>
                    <div className="text-[11px] text-muted">
                      {javas.length
                        ? t('setup.javaFound', { version: javas[0].version })
                        : t('setup.javaNone')}
                    </div>
                  </div>
                  <Button size="sm" variant="primary" loading={javaDownloading} icon={<Download size={13} />} onClick={() => void downloadJava()}>
                    {t('setup.javaDownload')}
                  </Button>
                </div>
                {javas.length > 0 && (
                  <div className="mt-3 space-y-1.5">
                    {javas.slice(0, 4).map((java) => (
                      <div key={java.path} className="flex items-center gap-2 rounded-lg border border-line/8 bg-ink/4 px-3 py-2">
                        <Badge tone={java.major >= 21 ? 'success' : 'info'}>Java {java.major}</Badge>
                        <span className="min-w-0 flex-1 truncate font-mono text-[10px] text-muted">{java.path}</span>
                      </div>
                    ))}
                  </div>
                )}
                <div className="mt-4 text-[10.5px] leading-relaxed text-muted">{t('setup.javaSub')}</div>
              </div>
              <Nav onBack={() => setStep(2)} onNext={() => setStep(4)} nextLabel={t('common.skip')} />
            </div>
          )}

          {step === 4 && (
            <div>
              <StepTitle title={t('setup.profileTitle')} sub={t('setup.profileSub')} />
              <div className="mt-5 rounded-2xl border border-line/12 bg-panel/70 p-5 backdrop-blur-md">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input label={t('versions.profileName')} value={profileName} onChange={(e) => setProfileName(e.target.value)} />
                  <Select
                    label={t('versions.minecraftVersion')}
                    value={profileVersion}
                    onChange={setProfileVersion}
                    options={releaseOptions.length ? releaseOptions : [{ value: profileVersion, label: profileVersion || '—' }]}
                  />
                </div>
                <div className="mt-4 flex items-center gap-2 text-[11px] text-muted">
                  <Badge tone="info">{settings.defaultRamMb} MB</Badge>
                  <Badge>Vanilla</Badge>
                  <span>{t('versions.dirHint')}</span>
                </div>
                <Button
                  className="mt-4 w-full"
                  size="lg"
                  variant="primary"
                  loading={creating}
                  disabled={!profileVersion}
                  icon={<Play size={18} className="fill-current" />}
                  onClick={() => void createProfile()}
                >
                  {t('versions.create')}
                </Button>
                {profiles.length > 0 && (
                  <div className="mt-3 text-center text-[11px] text-muted">
                    {t('setup.profileCreated', { name: profiles[0].name })}
                  </div>
                )}
                <div className="mt-3 flex justify-center gap-2 text-[10.5px] text-muted">
                  <span>{info?.os}</span>·<span>{info?.totalRamMb} MB RAM</span>·
                  <span>{createdProfile ? createdProfile.id.slice(0, 8) : '—'}</span>
                </div>
              </div>
              <Nav
                onBack={() => setStep(3)}
                onNext={finish}
                nextLabel={profiles.length ? t('common.finish') : t('setup.createLater')}
              />
            </div>
          )}

          {step === 5 && (
            <div className="text-center">
              <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-accent/15">
                <Check size={38} className="text-accent" />
              </div>
              <h1 className="mt-6 font-display text-[34px] font-extrabold">{t('setup.doneTitle')}</h1>
              <p className="mx-auto mt-2 max-w-md text-[13px] text-muted">{t('setup.doneSub')}</p>
              <Button className="mt-7 animate-pulse-glow" size="lg" variant="primary" icon={<Play size={18} className="fill-current" />} onClick={finish}>
                {t('common.play')}
              </Button>
            </div>
          )}
        </div>
      </div>

      <div className="relative flex items-center justify-between px-8 pb-5 text-[10px] text-muted">
        <span>{t('setup.step', { n: Math.min(step + 1, totalSteps), total: totalSteps })}</span>
        <button className="font-semibold transition-colors hover:text-ink" onClick={finish}>
          {t('common.skip')}
        </button>
      </div>
    </div>
  );
}

function StepTitle({ title, sub, className }: { title: string; sub?: string; className?: string }) {
  return (
    <div className={cn('text-center', className)}>
      <h1 className="font-display text-[28px] font-extrabold tracking-tight">{title}</h1>
      {sub && <p className="mx-auto mt-1.5 max-w-xl text-[12.5px] leading-relaxed text-muted">{sub}</p>}
    </div>
  );
}

function Nav({
  onBack,
  onNext,
  nextLabel,
}: {
  onBack?: () => void;
  onNext: () => void;
  nextLabel?: string;
}) {
  const t = useT();
  return (
    <div className="mt-6 flex items-center justify-between">
      {onBack ? (
        <Button icon={<ArrowLeft size={15} />} onClick={onBack}>
          {t('common.back')}
        </Button>
      ) : (
        <span />
      )}
      <Button variant="primary" icon={<ArrowRight size={15} />} onClick={onNext}>
        {nextLabel ?? t('common.next')}
      </Button>
    </div>
  );
}
