import { useEffect, useMemo } from 'react';
import { AlertOctagon, RefreshCw } from 'lucide-react';
import { useApp } from './store/app';
import { useRoute } from './lib/router';
import { useT } from './i18n';
import { TitleBar } from './components/TitleBar';
import { Sidebar } from './components/Sidebar';
import { Button, ConfirmProvider, Toasts } from './components/ui';
import { HomePage } from './pages/HomePage';
import { VersionsPage } from './pages/VersionsPage';
import { ModsPage } from './pages/ModsPage';
import { ModpacksPage } from './pages/ModpacksPage';
import { DownloadsPage } from './pages/DownloadsPage';
import { AccountsPage } from './pages/AccountsPage';
import { SettingsPage } from './pages/SettingsPage';
import { Onboarding } from './pages/Onboarding';
import { LogsDrawer } from './components/LogsDrawer';

function Splash() {
  const t = useT();
  return (
    <div className="flex h-full flex-col items-center justify-center gap-6 bg-bg">
      <div className="relative">
        <div className="absolute -inset-8 rounded-full bg-accent/20 blur-3xl" />
        <img src="/logo.png" alt="WinLaucher" className="relative h-24 w-24 animate-float rounded-3xl shadow-glow" />
      </div>
      <div className="text-center">
        <div className="font-display text-2xl font-extrabold tracking-tight gradient-text">WinLaucher</div>
        <div className="mt-1.5 text-xs text-muted">{t('common.loading')}</div>
      </div>
      <div className="h-1 w-40 overflow-hidden rounded-full bg-ink/10">
        <div className="h-full w-1/3 animate-shimmer rounded-full bg-gradient-to-r from-accent to-accent2 bg-[length:200%_100%]" />
      </div>
    </div>
  );
}

function CrashScreen({ error }: { error: string }) {
  const t = useT();
  return (
    <div className="flex h-full items-center justify-center p-8">
      <div className="card max-w-lg p-8 text-center">
        <AlertOctagon size={36} className="mx-auto text-danger" />
        <h1 className="mt-4 font-display text-xl font-bold">{t('errors.generic')}</h1>
        <p className="mt-2 break-words text-[13px] text-muted">{error}</p>
        <Button className="mt-5" variant="primary" icon={<RefreshCw size={15} />} onClick={() => window.location.reload()}>
          {t('common.retry')}
        </Button>
      </div>
    </div>
  );
}

function Shell() {
  const [route] = useRoute();
  const page = useMemo(() => {
    switch (route) {
      case 'versions':
        return <VersionsPage />;
      case 'mods':
        return <ModsPage />;
      case 'modpacks':
        return <ModpacksPage />;
      case 'downloads':
        return <DownloadsPage />;
      case 'accounts':
        return <AccountsPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <HomePage />;
    }
  }, [route]);

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <TitleBar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main key={route} className="relative min-w-0 flex-1 animate-fade-in overflow-hidden">
          <div className="scroll-area h-full">{page}</div>
        </main>
      </div>
      <LogsDrawer />
      <Toasts />
    </div>
  );
}

export default function App() {
  const { ready, bootError, settings, init } = useApp();

  useEffect(() => {
    void init();
  }, [init]);

  const content = !ready ? (
    <Splash />
  ) : bootError ? (
    <CrashScreen error={bootError} />
  ) : !settings.onboarded ? (
    <Onboarding />
  ) : (
    <>
      <Shell />
      <Toasts />
    </>
  );

  return <ConfirmProvider>{content}</ConfirmProvider>;
}
