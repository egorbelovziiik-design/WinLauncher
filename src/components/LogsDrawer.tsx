import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDownToLine, Copy, Download, Trash2, X } from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call } from '../lib/bridge';
import { Button, Segmented, cn } from './ui';

export function LogsDrawer() {
  const t = useT();
  const { logs, logDrawer, setLogDrawer, toast, toastError, refreshLogs } = useApp();
  const [source, setSource] = useState<'all' | 'launcher' | 'game'>('all');
  const [autoScroll, setAutoScroll] = useState(true);
  const ref = useRef<HTMLDivElement>(null);

  const filtered = useMemo(
    () => (source === 'all' ? logs : logs.filter((l) => (l.source ?? 'launcher') === source)),
    [logs, source],
  );

  useEffect(() => {
    if (autoScroll && ref.current) {
      ref.current.scrollTop = ref.current.scrollHeight;
    }
  }, [filtered, autoScroll, logDrawer]);

  useEffect(() => {
    if (logDrawer) void refreshLogs();
  }, [logDrawer, refreshLogs]);

  if (!logDrawer) return null;

  const copy = async () => {
    const text = filtered.map((l) => `[${l.ts}] ${l.level.toUpperCase()} ${l.line}`).join('\n');
    await navigator.clipboard.writeText(text);
    toast('success', t('common.copied'));
  };

  return (
    <div className="fixed inset-x-0 bottom-0 z-[70] animate-fade-up">
      <div className="mx-0 border-t border-line/15 bg-surface/95 shadow-[0_-20px_60px_-30px_rgba(0,0,0,0.9)] backdrop-blur-2xl">
        <div className="flex items-center gap-3 border-b border-line/10 px-4 py-2">
          <span className="font-display text-[12px] font-bold">{t('logs.title')}</span>
          <Segmented
            size="sm"
            value={source}
            onChange={setSource}
            options={[
              { value: 'all', label: t('common.all') },
              { value: 'launcher', label: t('logs.launcher') },
              { value: 'game', label: t('logs.game') },
            ]}
          />
          <span className="text-[10px] text-muted">{t('logs.subtitle')}</span>
          <div className="ml-auto flex items-center gap-1">
            <button
              onClick={() => setAutoScroll((v) => !v)}
              className={cn(
                'flex items-center gap-1 rounded-lg px-2 py-1 text-[10.5px] font-semibold transition-colors',
                autoScroll ? 'bg-accent/15 text-accent' : 'text-muted hover:text-ink',
              )}
            >
              <ArrowDownToLine size={12} /> {t('logs.autoScroll')}
            </button>
            <Button size="sm" variant="subtle" icon={<Copy size={12} />} onClick={() => void copy()}>
              {t('logs.copy')}
            </Button>
            <Button
              size="sm"
              variant="subtle"
              icon={<Download size={12} />}
              onClick={() =>
                void call('logs_export')
                  .then(() => toast('success', t('settings.logsExported')))
                  .catch(toastError)
              }
            >
              {t('logs.export')}
            </Button>
            <Button
              size="sm"
              variant="subtle"
              icon={<Trash2 size={12} />}
              onClick={() =>
                void call('logs_clear')
                  .then(() => {
                    useApp.setState({ logs: [] });
                    toast('success', t('settings.logsCleared'));
                  })
                  .catch(toastError)
              }
            >
              {t('logs.clear')}
            </Button>
            <button
              className="ml-1 rounded-lg p-1.5 text-muted transition-colors hover:bg-ink/8 hover:text-ink"
              onClick={() => setLogDrawer(false)}
            >
              <X size={14} />
            </button>
          </div>
        </div>
        <div
          ref={ref}
          className="scroll-area h-[38vh] select-text bg-black/25 px-4 py-2 font-mono text-[10.5px] leading-relaxed"
        >
          {filtered.length === 0 && <div className="py-6 text-center text-muted">{t('logs.empty')}</div>}
          {filtered.map((line, i) => (
            <div key={i} className="flex gap-2 whitespace-pre-wrap break-all">
              <span className="shrink-0 text-muted/50">{line.ts?.slice(11, 19)}</span>
              <span
                className={cn(
                  'shrink-0 font-semibold uppercase',
                  line.level === 'error' ? 'text-danger' : line.level === 'warn' ? 'text-warning' : 'text-accent2',
                )}
              >
                {line.source === 'game' ? 'GAME' : 'WIN'}
              </span>
              <span className="text-ink/80">{line.line}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
