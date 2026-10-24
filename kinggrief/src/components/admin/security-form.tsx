'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { KeyRound, Loader2, Lock, ShieldCheck, ShieldOff } from 'lucide-react';
import { adminPost } from './api-client';
import { AdminCard, Field } from './kit';
import { useToast } from '../providers';

export function SecurityForm({ totpEnabled, username }: { totpEnabled: boolean; username: string }) {
  const router = useRouter();
  const { push } = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const [setupSecret, setSetupSecret] = useState('');
  const [setupUri, setSetupUri] = useState('');
  const [token, setToken] = useState('');
  const [disablePassword, setDisablePassword] = useState('');

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (next.length < 10) return setError('Новый пароль — минимум 10 символов');
    if (next !== confirm) return setError('Пароли не совпадают');
    setBusy(true);
    const res = await adminPost('/api/admin/security', { action: 'password', current, next });
    setBusy(false);
    if (!res.ok) return setError(res.data.error ?? 'Ошибка');
    setCurrent(''); setNext(''); setConfirm('');
    push({ tone: 'success', title: 'Пароль обновлён' });
  };

  const initTotp = async () => {
    setBusy(true);
    const res = await adminPost<{ secret: string; otpauth: string }>('/api/admin/security', { action: 'totp-init' });
    setBusy(false);
    if (!res.ok) return push({ tone: 'error', title: 'Ошибка', description: res.data.error });
    setSetupSecret(res.data.secret);
    setSetupUri(res.data.otpauth);
  };

  const enableTotp = async () => {
    setBusy(true);
    const res = await adminPost('/api/admin/security', { action: 'totp-enable', secret: setupSecret, token });
    setBusy(false);
    if (!res.ok) return push({ tone: 'error', title: 'Не удалось включить 2FA', description: res.data.error });
    push({ tone: 'success', title: '2FA включена', description: 'При следующем входе потребуется код.' });
    setSetupSecret(''); setSetupUri(''); setToken('');
    router.refresh();
  };

  const disableTotp = async () => {
    setBusy(true);
    const res = await adminPost('/api/admin/security', { action: 'totp-disable', password: disablePassword });
    setBusy(false);
    if (!res.ok) return push({ tone: 'error', title: 'Ошибка', description: res.data.error });
    push({ tone: 'info', title: '2FA отключена' });
    setDisablePassword('');
    router.refresh();
  };

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <AdminCard title="Смена пароля" description={`Аккаунт: ${username}`}>
        <form onSubmit={changePassword} className="space-y-3">
          <Field label="Текущий пароль">
            <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} className="field" autoComplete="current-password" required />
          </Field>
          <Field label="Новый пароль" hint="Минимум 10 символов. Пароль хранится как scrypt-хэш.">
            <input type="password" value={next} onChange={(e) => setNext(e.target.value)} className="field" autoComplete="new-password" required />
          </Field>
          <Field label="Повторите пароль">
            <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} className="field" autoComplete="new-password" required />
          </Field>
          {error && <p className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.72rem] text-[#fca5a5]">{error}</p>}
          <button className="btn btn-primary btn-sm w-full" disabled={busy}>
            {busy ? <Loader2 size={13} className="animate-spin" /> : <Lock size={13} />} Обновить пароль
          </button>
        </form>
      </AdminCard>

      <AdminCard
        title="Двухфакторная аутентификация"
        description="TOTP: Google Authenticator, Aegis, 1Password"
        action={<span className={`font-mono text-[0.56rem] font-bold uppercase tracking-widest ${totpEnabled ? 'text-emerald' : 'text-amber'}`}>{totpEnabled ? 'включена' : 'выключена'}</span>}
      >
        {totpEnabled ? (
          <div className="space-y-3">
            <p className="flex items-start gap-2 rounded-lg border border-emerald/25 bg-emerald/[0.06] px-3 py-2 text-[0.72rem] leading-relaxed text-white/55">
              <ShieldCheck size={14} className="mt-0.5 shrink-0 text-emerald" />
              2FA активна. При входе потребуется шестизначный код из приложения.
            </p>
            <Field label="Пароль для отключения">
              <input type="password" value={disablePassword} onChange={(e) => setDisablePassword(e.target.value)} className="field" />
            </Field>
            <button onClick={disableTotp} disabled={busy || !disablePassword} className="btn btn-ghost btn-sm w-full hover:!text-danger">
              <ShieldOff size={13} /> Отключить 2FA
            </button>
          </div>
        ) : setupSecret ? (
          <div className="space-y-3">
            <p className="text-[0.72rem] leading-relaxed text-white/45">
              Добавьте ключ в приложение-аутентификатор вручную (TOTP, 6 цифр, шаг 30 сек), затем введите код.
            </p>
            <div className="rounded-lg border border-neon/25 bg-void/60 p-3">
              <p className="font-mono text-[0.54rem] uppercase tracking-widest text-white/30">секрет</p>
              <p className="mt-1 break-all font-mono text-[0.82rem] font-bold tracking-[0.15em] text-neon">{setupSecret}</p>
            </div>
            <a href={setupUri} className="block break-all rounded-lg border border-white/8 bg-void/40 px-2.5 py-2 font-mono text-[0.56rem] text-white/30 hover:text-white/60">
              {setupUri}
            </a>
            <Field label="Код из приложения">
              <input value={token} onChange={(e) => setToken(e.target.value.replace(/\D/g, ''))} maxLength={6} inputMode="numeric" className="field text-center tracking-[0.4em]" placeholder="000000" />
            </Field>
            <div className="flex gap-2">
              <button onClick={() => { setSetupSecret(''); setSetupUri(''); }} className="btn btn-ghost btn-sm flex-1">Отмена</button>
              <button onClick={enableTotp} disabled={busy || token.length !== 6} className="btn btn-primary btn-sm flex-1">Включить</button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-[0.74rem] leading-relaxed text-white/45">
              Включите второй фактор, чтобы защитить панель даже при утечке пароля. Это рекомендуемая настройка для владельца.
            </p>
            <button onClick={initTotp} disabled={busy} className="btn btn-primary btn-sm w-full">
              {busy ? <Loader2 size={13} className="animate-spin" /> : <KeyRound size={13} />} Настроить 2FA
            </button>
          </div>
        )}
      </AdminCard>
    </div>
  );
}
