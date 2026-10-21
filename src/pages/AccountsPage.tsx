import { useEffect, useRef, useState } from 'react';
import {
  BadgeCheck,
  Check,
  Copy,
  Crown,
  ExternalLink,
  KeyRound,
  Plus,
  RefreshCw,
  ShieldQuestion,
  Star,
  Trash2,
  UserRound,
  Wifi,
} from 'lucide-react';
import { useApp } from '../store/app';
import { useT } from '../i18n';
import { call, openExternal } from '../lib/bridge';
import type { Account, DeviceCodeInfo } from '../lib/types';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  Modal,
  Page,
  PageTitle,
  Spinner,
  cn,
  useConfirm,
  useCopy,
} from '../components/ui';

function SkinAvatar({ account, size = 40 }: { account: Account; size?: number }) {
  const url = account.skins?.[0]?.url;
  return (
    <div
      className="relative flex shrink-0 items-center justify-center overflow-hidden rounded-lg bg-elevated font-display font-bold text-accent"
      style={{ width: size, height: size }}
    >
      <span>{(account.name[0] ?? '?').toUpperCase()}</span>
      {url && (
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: `url("${url}")`,
            backgroundSize: `${size * 8}px ${size * 8}px`,
            backgroundPosition: `-${size}px -${size}px`,
            imageRendering: 'pixelated',
          }}
        />
      )}
    </div>
  );
}

export function AccountsPage() {
  const t = useT();
  const confirm = useConfirm();
  const copy = useCopy();
  const {
    accounts,
    settings,
    refreshAccounts,
    toast,
    toastError,
    setActiveAccount,
  } = useApp();

  const [addOpen, setAddOpen] = useState(false);
  const [offlineOpen, setOfflineOpen] = useState(false);
  const [nickname, setNickname] = useState('');
  const [nickError, setNickError] = useState('');
  const [saving, setSaving] = useState(false);

  const [msOpen, setMsOpen] = useState(false);
  const [device, setDevice] = useState<DeviceCodeInfo | null>(null);
  const [msError, setMsError] = useState('');
  const [msWaiting, setMsWaiting] = useState(false);
  const pollRef = useRef<number | null>(null);
  const stopped = useRef(false);

  const activeId = settings.activeAccountId;

  useEffect(() => {
    return () => {
      stopped.current = true;
      if (pollRef.current) window.clearTimeout(pollRef.current);
    };
  }, []);

  const startMicrosoft = async () => {
    setAddOpen(false);
    setMsOpen(true);
    setMsError('');
    setDevice(null);
    setMsWaiting(true);
    stopped.current = false;
    try {
      const info = await call<DeviceCodeInfo>('ms_begin', {});
      setDevice(info);
      await openExternal(info.verificationUri).catch(() => {});
      poll(info);
    } catch (e) {
      setMsWaiting(false);
      setMsError(e instanceof Error ? e.message : t('accounts.authError'));
    }
  };

  const poll = (info: DeviceCodeInfo) => {
    let interval = Math.max(2, info.interval || 5);
    const started = Date.now();
    const tick = async () => {
      if (stopped.current) return;
      if (Date.now() - started > info.expiresIn * 1000) {
        setMsError(t('errors.timeout'));
        setMsWaiting(false);
        return;
      }
      try {
        const account = await call<Account | null>('ms_poll', { deviceCode: info.deviceCode });
        if (account) {
          setMsWaiting(false);
          setMsOpen(false);
          toast('success', t('accounts.authSuccess', { name: account.name }));
          await refreshAccounts();
          useApp.getState().saveSettings({ activeAccountId: account.id });
          return;
        }
      } catch (e) {
        setMsError(e instanceof Error ? e.message : t('accounts.authError'));
        setMsWaiting(false);
        return;
      }
      pollRef.current = window.setTimeout(() => void tick(), interval * 1000);
    };
    pollRef.current = window.setTimeout(() => void tick(), interval * 1000);
  };

  const closeMs = () => {
    stopped.current = true;
    if (pollRef.current) window.clearTimeout(pollRef.current);
    setMsOpen(false);
  };

  const createOffline = async () => {
    const name = nickname.trim();
    setNickError('');
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
      setNickError(t('accounts.invalidName'));
      return;
    }
    setSaving(true);
    try {
      const account = await call<Account>('account_add_offline', { name });
      toast('success', t('accounts.authSuccess', { name: account.name }));
      setOfflineOpen(false);
      setNickname('');
      await refreshAccounts();
      useApp.getState().saveSettings({ activeAccountId: account.id });
    } catch (e) {
      const msg = e instanceof Error ? e.message : '';
      if (msg.includes('already') || msg.includes('exists')) setNickError(t('accounts.exists'));
      else toastError(e);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (account: Account) => {
    const ok = await confirm({
      title: t('accounts.delete'),
      message: t('accounts.deleteConfirm', { name: account.name }),
      danger: true,
      confirmLabel: t('common.delete'),
    });
    if (!ok) return;
    try {
      await call('account_delete', { id: account.id });
      toast('success', t('accounts.removed'));
      if (activeId === account.id) useApp.getState().saveSettings({ activeAccountId: null });
      await refreshAccounts();
    } catch (e) {
      toastError(e);
    }
  };

  const makePrimary = async (account: Account) => {
    try {
      await call('account_set_primary', { id: account.id });
      await refreshAccounts();
    } catch (e) {
      toastError(e);
    }
  };

  const refreshToken = async (account: Account) => {
    try {
      await call('ms_refresh', { id: account.id });
      toast('success', t('accounts.refreshed'));
      await refreshAccounts();
    } catch (e) {
      toastError(e);
    }
  };

  return (
    <Page>
      <PageTitle
        title={t('accounts.title')}
        subtitle={t('accounts.subtitle')}
        actions={
          <Button variant="primary" icon={<Plus size={15} />} onClick={() => setAddOpen(true)}>
            {t('accounts.add')}
          </Button>
        }
      />

      {accounts.length === 0 ? (
        <EmptyState
          icon={<UserRound size={28} />}
          title={t('accounts.noAccounts')}
          hint={t('accounts.noAccountsHint')}
          action={
            <Button variant="primary" icon={<Plus size={15} />} onClick={() => setAddOpen(true)}>
              {t('accounts.add')}
            </Button>
          }
        />
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {accounts.map((account) => (
            <Card
              key={account.id}
              hover
              className={cn('relative overflow-hidden p-4', activeId === account.id && 'ring-1 ring-accent/40')}
            >
              <div className="flex items-start gap-3">
                <SkinAvatar account={account} size={44} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <h3 className="truncate font-display text-[15px] font-bold">{account.name}</h3>
                    {account.primary && <Crown size={13} className="shrink-0 text-warning" />}
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-1.5">
                    {account.kind === 'microsoft' ? (
                      <Badge tone="info">
                        <BadgeCheck size={11} /> {t('accounts.microsoft')}
                      </Badge>
                    ) : (
                      <Badge tone="warning">
                        <Wifi size={11} /> {t('accounts.offline')}
                      </Badge>
                    )}
                    {account.kind === 'microsoft' && account.owned !== null && (
                      <Badge tone={account.owned ? 'success' : 'danger'}>
                        {account.owned ? t('accounts.owned') : t('accounts.notOwned')}
                      </Badge>
                    )}
                    {activeId === account.id && <Badge tone="accent">{t('accounts.sessionReady')}</Badge>}
                  </div>
                  <div className="mt-1.5 truncate font-mono text-[10px] text-muted">{account.uuid}</div>
                </div>
              </div>

              {account.kind === 'offline' && (
                <p className="mt-3 rounded-lg border border-line/10 bg-ink/4 p-2 text-[10.5px] leading-relaxed text-muted">
                  {t('accounts.offlineNotice')}
                </p>
              )}

              <div className="mt-3 flex flex-wrap items-center gap-1.5">
                <Button
                  size="sm"
                  variant={activeId === account.id ? 'primary' : 'ghost'}
                  icon={activeId === account.id ? <Check size={13} /> : <Star size={13} />}
                  onClick={() => void setActiveAccount(account.id)}
                >
                  {activeId === account.id ? t('common.select') : t('common.select')}
                </Button>
                {!account.primary && (
                  <Button size="sm" onClick={() => void makePrimary(account)} icon={<Crown size={13} />}>
                    {t('accounts.makePrimary')}
                  </Button>
                )}
                {account.kind === 'microsoft' && (
                  <Button size="sm" onClick={() => void refreshToken(account)} title={t('accounts.refresh')}>
                    <RefreshCw size={13} />
                  </Button>
                )}
                <Button size="sm" variant="danger" className="ml-auto" onClick={() => void remove(account)} title={t('accounts.delete')}>
                  <Trash2 size={13} />
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Add account chooser ------------------------------------------------ */}
      <Modal open={addOpen} onClose={() => setAddOpen(false)} title={t('accounts.add')} width="max-w-2xl">
        <div className="grid gap-3 sm:grid-cols-2">
          <button
            onClick={() => void startMicrosoft()}
            className="group rounded-2xl border border-line/12 bg-ink/3 p-5 text-left transition-all hover:border-accent/40 hover:bg-accent/5"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent2/15 text-accent2">
              <BadgeCheck size={20} />
            </div>
            <h3 className="mt-3 font-display text-[15px] font-bold">{t('accounts.microsoft')}</h3>
            <p className="mt-1 text-[11.5px] leading-relaxed text-muted">{t('accounts.microsoftHint')}</p>
            <div className="mt-3 inline-flex items-center gap-1 text-[11px] font-semibold text-accent2">
              {t('accounts.signIn')} <ExternalLink size={11} />
            </div>
          </button>
          <button
            onClick={() => {
              setAddOpen(false);
              setNickname('');
              setNickError('');
              setOfflineOpen(true);
            }}
            className="group rounded-2xl border border-line/12 bg-ink/3 p-5 text-left transition-all hover:border-accent/40 hover:bg-accent/5"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent/15 text-accent">
              <Wifi size={20} />
            </div>
            <h3 className="mt-3 font-display text-[15px] font-bold">{t('accounts.offline')}</h3>
            <p className="mt-1 text-[11.5px] leading-relaxed text-muted">{t('accounts.offlineHint')}</p>
            <div className="mt-3 inline-flex items-center gap-1 text-[11px] font-semibold text-accent">
              {t('accounts.addOffline')} <Plus size={11} />
            </div>
          </button>
        </div>
      </Modal>

      {/* Offline profile ---------------------------------------------------- */}
      <Modal
        open={offlineOpen}
        onClose={() => setOfflineOpen(false)}
        title={t('accounts.offline')}
        subtitle={t('accounts.uuidHint')}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOfflineOpen(false)}>{t('common.cancel')}</Button>
            <Button variant="primary" loading={saving} onClick={() => void createOffline()} icon={<Plus size={14} />}>
              {t('accounts.addOffline')}
            </Button>
          </>
        }
      >
        <Input
          label={t('accounts.nickname')}
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder={t('accounts.nicknamePlaceholder')}
          hint={t('accounts.nicknameHint')}
          error={nickError}
          maxLength={16}
          autoFocus
        />
        <div className="mt-4 rounded-xl border border-warning/20 bg-warning/8 p-3 text-[11px] leading-relaxed text-warning/90">
          {t('accounts.offlineNotice')}
        </div>
      </Modal>

      {/* Microsoft device code --------------------------------------------- */}
      <Modal open={msOpen} onClose={closeMs} title={t('accounts.signIn')} subtitle={t('accounts.deviceHint')}>
        {msError ? (
          <div className="rounded-xl border border-danger/25 bg-danger/10 p-4 text-[12px] leading-relaxed text-danger">
            {msError}
          </div>
        ) : !device ? (
          <div className="flex flex-col items-center gap-3 py-8">
            <Spinner size={26} />
            <span className="text-[12px] text-muted">{t('common.loading')}</span>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="rounded-2xl border border-line/12 bg-ink/4 p-5 text-center">
              <div className="text-[10px] font-bold uppercase tracking-[0.18em] text-muted">{t('accounts.deviceCode')}</div>
              <div className="mt-2 select-text font-mono text-[30px] font-extrabold tracking-[0.22em] text-accent">
                {device.userCode}
              </div>
              <div className="mt-3 flex items-center justify-center gap-2">
                <Button size="sm" icon={<Copy size={13} />} onClick={() => void copy(device.userCode)}>
                  {t('common.copy')}
                </Button>
                <Button
                  size="sm"
                  variant="primary"
                  icon={<ExternalLink size={13} />}
                  onClick={() => void openExternal(device.verificationUri)}
                >
                  {t('accounts.openPage')}
                </Button>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-xl border border-accent/20 bg-accent/8 p-3">
              {msWaiting ? <Spinner size={16} /> : <ShieldQuestion size={16} className="text-muted" />}
              <div className="min-w-0 flex-1">
                <div className="text-[12px] font-semibold text-accent">{t('accounts.waiting')}</div>
                <div className="text-[10.5px] text-muted">
                  {t('accounts.waitingHint', { min: Math.round(device.expiresIn / 60) })}
                </div>
              </div>
            </div>
            <div className="text-[10.5px] leading-relaxed text-muted">
              <KeyRound size={11} className="mr-1 inline" />
              {t('accounts.microsoftHint')}
            </div>
          </div>
        )}
      </Modal>
    </Page>
  );
}
