import { useEffect, useState } from 'react';
import { Minus, Square, X, Copy, Globe, TerminalSquare } from 'lucide-react';
import { useApp } from '../store/app';
import { IS_TAURI } from '../lib/bridge';
import { useT } from '../i18n';

/** Custom window chrome for the frameless Tauri window. */
const cnTitle = (active: boolean) =>
  `flex h-7 w-9 items-center justify-center rounded-md transition-colors ${
    active ? 'bg-accent/15 text-accent' : 'text-muted hover:bg-ink/8 hover:text-ink'
  }`;

export function TitleBar() {
  const t = useT();
  const { logDrawer, setLogDrawer } = useApp();
  const [maximized, setMaximized] = useState(false);
  const [win, setWin] = useState<Awaited<ReturnType<typeof import('@tauri-apps/api/window')['getCurrentWindow']>> | null>(null);

  useEffect(() => {
    if (!IS_TAURI) return;
    let unlisten: (() => void) | undefined;
    void (async () => {
      const { getCurrentWindow } = await import('@tauri-apps/api/window');
      const w = getCurrentWindow();
      setWin(w);
      setMaximized(await w.isMaximized());
      unlisten = await w.onResized(async () => setMaximized(await w.isMaximized()));
    })();
    return () => unlisten?.();
  }, []);

  return (
    <div data-tauri-drag-region className="drag relative z-50 flex h-[40px] shrink-0 items-center justify-between border-b border-line/8 bg-bg/70 px-3 backdrop-blur-xl">
      <div className="flex items-center gap-2.5 pl-1">
        <img src="/logo.png" alt="" className="h-5 w-5 rounded-md" draggable={false} />
        <span className="font-display text-[12px] font-extrabold tracking-wide text-ink/90">WinLaucher</span>
        {!IS_TAURI && (
          <span className="ml-1 inline-flex items-center gap-1 rounded-md bg-warning/12 px-1.5 py-0.5 text-[10px] font-semibold text-warning">
            <Globe size={10} /> {t('common.preview')}
          </span>
        )}
      </div>
      <div className="flex items-center gap-1 no-drag">
        <button
          className={cnTitle(logDrawer)}
          onClick={() => setLogDrawer(!logDrawer)}
          title={t('logs.title')}
          aria-label="logs"
        >
          <TerminalSquare size={14} />
        </button>
        {IS_TAURI && win ? (
          <>
            <button
              className="flex h-7 w-10 items-center justify-center rounded-md text-muted transition-colors hover:bg-ink/8 hover:text-ink"
              onClick={() => void win.minimize()}
              aria-label="minimize"
            >
              <Minus size={14} />
            </button>
            <button
              className="flex h-7 w-10 items-center justify-center rounded-md text-muted transition-colors hover:bg-ink/8 hover:text-ink"
              onClick={() => void (maximized ? win.unmaximize() : win.maximize())}
              aria-label="maximize"
            >
              {maximized ? <Copy size={12} className="-scale-x-100" /> : <Square size={12} />}
            </button>
            <button
              className="flex h-7 w-10 items-center justify-center rounded-md text-muted transition-colors hover:bg-danger/90 hover:text-white"
              onClick={() => void win.close()}
              aria-label="close"
            >
              <X size={15} />
            </button>
          </>
        ) : (
          <span className="pr-2 text-[10px] font-medium tracking-wide text-muted/70">preview · 1200×760</span>
        )}
      </div>
    </div>
  );
}
