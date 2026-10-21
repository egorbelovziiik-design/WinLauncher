import { useEffect, useState } from 'react';
import { AnimatePresence, MotionConfig, motion } from 'framer-motion';
import { Toaster, toast } from 'sonner';
import { ChevronRight, Download, LoaderCircle, Maximize2, Minus, Monitor, ShieldCheck, X } from 'lucide-react';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { useLaunch, useLauncher, useNativeEvents, perform } from './hooks/useLauncher';
import { command, isDesktop, openExternal } from './services/api';
import { useUI } from './stores/ui';
import { getTheme } from './themes';
import { formatBytes, progress } from './utils/format';
import Sidebar from './components/Sidebar';
import Header, { CommandPalette } from './components/Header';
import Overlays from './components/Overlays';
import { Button, IconButton, Logo, Modal } from './components/ui';
import Home from './pages/Home';
import Profiles from './pages/Profiles';
import Versions from './pages/Versions';
import Mods from './pages/Mods';
import Accounts from './pages/Accounts';
import Themes from './pages/Themes';
import Statistics from './pages/Statistics';
import Logs from './pages/Logs';
import Settings from './pages/Settings';
import Worlds from './pages/Worlds';
import { useT } from './locales';

export default function App() {
  const { snapshot, error, isPending, refetch } = useLauncher(); const { page, setPage, setSettingsTab } = useUI(); const launch = useLaunch(); const t = useT();
  const [palette, setPalette] = useState(false); const [onboarding, setOnboarding] = useState(() => isDesktop && !localStorage.getItem('winlauncher.onboarded'));
  useNativeEvents();
  useEffect(() => {
    const theme = getTheme(snapshot.settings.theme); const root = document.documentElement;
    root.dataset.theme = theme.id; root.dataset.effects = String(snapshot.settings.effects); root.lang = snapshot.settings.language;
    for (const [name, value] of Object.entries({ accent: theme.accent, background: theme.background, sidebar: theme.sidebar, card: theme.card, glow: theme.glow, 'world-filter': theme.hue })) root.style.setProperty(`--${name}`, value);
  }, [snapshot.settings.theme, snapshot.settings.effects, snapshot.settings.language]);
  useEffect(() => { if (isDesktop) void command('set_activity', { page }).catch(() => {}); }, [page]);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setPalette(v => !v); return; }
      if ((event.target as HTMLElement)?.closest('input,textarea,select,[role="dialog"]')) return;
      if (event.ctrlKey && ((snapshot.settings.launch_hotkey === 'Ctrl+Enter' && event.key === 'Enter') || (snapshot.settings.launch_hotkey === 'Ctrl+P' && event.key.toLowerCase() === 'p'))) { event.preventDefault(); launch(); }
    };
    window.addEventListener('keydown', onKey); return () => window.removeEventListener('keydown', onKey);
  }, [snapshot.settings.launch_hotkey, launch]);
  useEffect(() => {
    if (!isDesktop || !snapshot.settings.check_updates) return;
    let cancelled = false;
    void command<{ available: boolean; version: string; url?: string }>('check_launcher_update').then(update => { if (!cancelled && update.available && update.url) toast.info(`Доступен WinLauncher ${update.version}`, { duration: 12000, action: { label: 'Открыть релиз', onClick: () => void perform(() => openExternal(update.url!)) } }); }).catch(() => { /* Network failure is shown on explicit update check, never as a fake success. */ });
    return () => { cancelled = true; };
  }, [snapshot.settings.check_updates]);
  const pages = { home: <Home />, profiles: <Profiles />, versions: <Versions />, mods: <Mods key="mods" />, modpacks: <Mods key="packs" packs />, accounts: <Accounts />, themes: <Themes />, statistics: <Statistics />, logs: <Logs />, settings: <Settings />, worlds: <Worlds /> };
  const jobProgress = snapshot.job ? progress(snapshot.job) : null;
  if (isDesktop && isPending) return <div className="boot-screen"><Logo /><LoaderCircle className="spin" size={25} /><p>Готовим место для твоих приключений…</p></div>;
  if (isDesktop && error) return <div className="boot-screen"><Logo /><h2>Не удалось открыть локальные данные</h2><p>{error.message}</p><Button onClick={() => void refetch()}>Повторить</Button></div>;
  return <MotionConfig reducedMotion={snapshot.settings.effects ? 'user' : 'always'}><div className="app-frame"><div className="title-bar" data-tauri-drag-region><span data-tauri-drag-region>WINLAUNCHER <span>/</span> YOUR NEXT ADVENTURE</span>{isDesktop ? <div className="window-controls"><IconButton label="Свернуть" onClick={() => void perform(() => getCurrentWindow().minimize())}><Minus size={13} /></IconButton><IconButton label="Развернуть" onClick={() => void perform(() => getCurrentWindow().toggleMaximize())}><Maximize2 size={11} /></IconButton><IconButton label="Закрыть приложение" className="window-close" onClick={() => void perform(() => getCurrentWindow().close())}><X size={14} /></IconButton></div> : <span className="preview-indicator"><Monitor size={10} />WINDOWS APP · WEB PREVIEW</span>}</div><div className="app-layout"><Sidebar /><div className="workspace"><Header /><main className="main-scroll" id="main-content" key={page}><AnimatePresence mode="wait"><motion.div className="page-content" key={page} initial={snapshot.settings.effects ? { opacity: 0, y: 7 } : false} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: snapshot.settings.effects ? .18 : 0 }}>{pages[page]}</motion.div></AnimatePresence><footer className="app-footer"><span><span className="quiet-dot" />{t(isDesktop ? 'Работает локально' : 'Веб-предпросмотр')}<i />WinLauncher 1.0.0</span><span>Not an official Minecraft product.<ShieldCheck size={12} /></span></footer></main>{snapshot.job && <div className="download-dock"><span className="download-dock-icon"><Download size={21} /></span><div><strong>{snapshot.job.detail || snapshot.job.stage}</strong><span>{snapshot.job.completed_files} / {snapshot.job.total_files || '…'} файлов · {formatBytes(snapshot.job.completed_bytes)}{snapshot.job.total_bytes > 0 && ` / ${formatBytes(snapshot.job.total_bytes)}`}</span><div className={`download-track ${jobProgress === null ? 'indeterminate' : ''}`}><i style={{ width: jobProgress !== null ? `${jobProgress}%` : '25%' }} /></div></div><b>{jobProgress !== null ? `${jobProgress}%` : <LoaderCircle size={18} className="spin" />}</b><IconButton label="Отменить загрузку" onClick={() => void perform(() => command('cancel_task'))}><X size={19} /></IconButton></div>}</div></div></div><Overlays />{palette && <CommandPalette onClose={() => setPalette(false)} />}{onboarding && <Modal title="Добро пожаловать в WinLauncher" subtitle="У каждого большого мира есть первый блок." onClose={() => { localStorage.setItem('winlauncher.onboarded', '1'); setOnboarding(false); }}><div className="onboarding"><Logo /><h2>Твой новый путь<br />в Minecraft.</h2><p>Подключи свой Microsoft Application ID и лицензионный аккаунт. Создай профиль. Всё остальное — Java, игровые файлы и библиотеки — мы подготовим при запуске.</p><div className="onboarding-steps"><span><b>01</b> Microsoft</span><ChevronRight size={13} /><span><b>02</b> Профиль</span><ChevronRight size={13} /><span><b>03</b> Играть</span></div><div className="info-box"><ShieldCheck size={19} /><span>Твои миры и настройки остаются на твоём устройстве. Без паролей и скрытой телеметрии.</span></div><Button variant="primary" onClick={() => { localStorage.setItem('winlauncher.onboarded', '1'); setOnboarding(false); setSettingsTab('integrations'); setPage('settings'); }}>Настроить Microsoft-вход<ChevronRight size={17} /></Button></div></Modal>}<Toaster theme="dark" position="bottom-right" richColors closeButton toastOptions={{ className: 'launcher-toast', duration: 5000 }} /></MotionConfig>;
}
