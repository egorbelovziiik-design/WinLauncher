import { useMemo, useState } from 'react';
import { Download, Loader2, RotateCw, Trash2, XCircle } from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call } from '../lib/bridge';
import type { DownloadItem } from '../lib/types';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Page,
  PageTitle,
  Progress,
  Tabs,
  formatBytes,
  formatEta,
  formatSpeed,
} from '../components/ui';

type Tab = 'active' | 'completed' | 'failed' | 'all';

export function DownloadsPage() {
  const t = useT();
  const { downloads, settings, refreshDownloads, toast, toastError } = useApp();
  const [tab, setTab] = useState<Tab>('active');

  const groups = useMemo(() => {
    const active = downloads.filter((d) => d.status === 'running' || d.status === 'queued');
    const completed = downloads.filter((d) => d.status === 'done');
    const failed = downloads.filter((d) => d.status === 'error' || d.status === 'cancelled');
    return { active, completed, failed };
  }, [downloads]);

  const list = tab === 'active' ? groups.active : tab === 'completed' ? groups.completed : tab === 'failed' ? groups.failed : downloads;

  const cancel = async (d: DownloadItem) => {
    try {
      await call('downloads_cancel', { id: d.id });
    } catch (e) {
      toastError(e);
    }
  };

  const retry = async (d: DownloadItem) => {
    try {
      await call('downloads_retry', { id: d.id });
    } catch (e) {
      toastError(e);
    }
  };

  const clear = async () => {
    try {
      await call('downloads_clear');
      toast('success', t('downloads.cleared'));
      await refreshDownloads();
    } catch (e) {
      toastError(e);
    }
  };

  return (
    <Page>
      <PageTitle
        title={t('downloads.title')}
        subtitle={t('downloads.subtitle')}
        actions={
          <>
            <Button onClick={() => void refreshDownloads()}>{t('common.refresh')}</Button>
            <Button variant="danger" icon={<Trash2 size={15} />} onClick={() => void clear()} disabled={downloads.length === 0}>
              {t('downloads.clearFinished')}
            </Button>
          </>
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Tabs
          value={tab}
          onChange={setTab}
          tabs={[
            { value: 'active', label: t('downloads.active'), count: groups.active.length },
            { value: 'completed', label: t('downloads.completed'), count: groups.completed.length },
            { value: 'failed', label: t('downloads.failed'), count: groups.failed.length },
            { value: 'all', label: t('common.all'), count: downloads.length },
          ]}
        />
        <div className="text-[11px] text-muted">
          {t('downloads.queueInfo', { n: settings.maxConcurrentDownloads })}
        </div>
      </div>

      {list.length === 0 ? (
        <EmptyState
          icon={<Download size={28} />}
          title={tab === 'active' ? t('downloads.noActive') : t('downloads.empty')}
          hint={t('downloads.subtitle')}
        />
      ) : (
        <div className="space-y-2.5">
          {list.map((d) => {
            const pct = d.total > 0 ? (d.current / d.total) * 100 : d.totalBytes > 0 ? (d.bytes / d.totalBytes) * 100 : 0;
            const eta = d.speed > 0 ? (d.totalBytes - d.bytes) / d.speed : 0;
            return (
              <Card key={d.id} className="p-4">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-ink/6">
                    {d.status === 'running' || d.status === 'queued' ? (
                      <Loader2 size={16} className="animate-spin text-accent" />
                    ) : (
                      <Download size={16} className={d.status === 'error' ? 'text-danger' : 'text-muted'} />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-2">
                        <span className="truncate text-[13px] font-semibold">{d.title}</span>
                        <Badge
                          tone={
                            d.status === 'done'
                              ? 'success'
                              : d.status === 'error'
                                ? 'danger'
                                : d.status === 'cancelled'
                                  ? 'warning'
                                  : 'info'
                          }
                        >
                          {d.status === 'running'
                            ? t('downloads.running')
                            : d.status === 'queued'
                              ? t('downloads.queued')
                              : d.status === 'done'
                                ? t('downloads.done')
                                : d.status === 'cancelled'
                                  ? t('downloads.cancelled')
                                  : t('downloads.error')}
                        </Badge>
                      </div>
                      <div className="flex items-center gap-1">
                        {(d.status === 'running' || d.status === 'queued') && (
                          <Button size="sm" variant="danger" icon={<XCircle size={12} />} onClick={() => void cancel(d)}>
                            {t('downloads.cancel')}
                          </Button>
                        )}
                        {(d.status === 'error' || d.status === 'cancelled') && (
                          <Button size="sm" icon={<RotateCw size={12} />} onClick={() => void retry(d)}>
                            {t('downloads.retry')}
                          </Button>
                        )}
                      </div>
                    </div>
                    <div className="mt-0.5 truncate text-[10.5px] text-muted">{d.detail}</div>
                    {(d.status === 'running' || d.status === 'queued') && (
                      <>
                        <Progress value={d.current} max={Math.max(1, d.total)} className="mt-2.5 h-1.5" />
                        <div className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-[10.5px] text-muted">
                          <span>
                            {d.total > 0 ? t('downloads.filesProgress', { current: d.current, total: d.total }) : `${Math.round(pct)}%`}
                            {' · '}
                            {formatBytes(d.bytes)} / {formatBytes(d.totalBytes)}
                          </span>
                          <span className="flex items-center gap-3">
                            {d.speed > 0 && <span>{formatSpeed(d.speed)}</span>}
                            {eta > 0 && <span>{t('common.remaining')}: {formatEta(eta)}</span>}
                            <span className="font-semibold text-accent">{Math.round(pct)}%</span>
                          </span>
                        </div>
                      </>
                    )}
                    {d.error && (
                      <div className="mt-2 rounded-lg border border-danger/20 bg-danger/8 px-2.5 py-1.5 text-[11px] text-danger">
                        {d.error}
                      </div>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </Page>
  );
}
