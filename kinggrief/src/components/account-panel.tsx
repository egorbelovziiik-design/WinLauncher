'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { CheckCircle2, Loader2, LogOut, Mail, ShieldCheck } from 'lucide-react';

export function AccountForms({ email }: { email: string }) {
  const router = useRouter();
  const [emailValue, setEmailValue] = useState(email);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [message, setMessage] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState<'email' | 'password' | null>(null);

  const patch = async (payload: Record<string, unknown>, mode: 'email' | 'password') => {
    setMessage(null);
    setBusy(mode);
    try {
      const res = await fetch('/api/auth/profile', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = (await res.json()) as { error?: string };
      if (!res.ok) {
        setMessage({ tone: 'error', text: data.error ?? 'Не удалось сохранить' });
        return false;
      }
      setMessage({ tone: 'ok', text: mode === 'email' ? 'Email обновлён' : 'Пароль изменён' });
      router.refresh();
      return true;
    } catch {
      setMessage({ tone: 'error', text: 'Сеть недоступна' });
      return false;
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-4">
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          await patch({ email: emailValue.trim() }, 'email');
        }}
        className="rounded-2xl border border-white/8 bg-panel/40 p-4"
      >
        <h3 className="flex items-center gap-2 font-display text-[0.78rem] font-bold uppercase tracking-widest text-white/80">
          <Mail size={14} className="text-neon" /> Email
        </h3>
        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <input
            type="email"
            value={emailValue}
            onChange={(e) => setEmailValue(e.target.value)}
            className="field flex-1"
            required
          />
          <button type="submit" disabled={busy !== null} className="btn btn-ghost">
            {busy === 'email' ? <Loader2 size={14} className="animate-spin" /> : null}
            Сохранить
          </button>
        </div>
      </form>

      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const ok = await patch({ currentPassword, newPassword }, 'password');
          if (ok) {
            setCurrentPassword('');
            setNewPassword('');
          }
        }}
        className="rounded-2xl border border-white/8 bg-panel/40 p-4"
      >
        <h3 className="flex items-center gap-2 font-display text-[0.78rem] font-bold uppercase tracking-widest text-white/80">
          <ShieldCheck size={14} className="text-neon" /> Смена пароля
        </h3>
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            className="field"
            placeholder="Текущий пароль"
            autoComplete="current-password"
            required
          />
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className="field"
            placeholder="Новый пароль"
            autoComplete="new-password"
            minLength={8}
            required
          />
        </div>
        <p className="mt-2 text-[0.66rem] text-white/30">Минимум 8 символов и хотя бы одна цифра.</p>
        <button type="submit" disabled={busy !== null} className="btn btn-ghost mt-3">
          {busy === 'password' ? <Loader2 size={14} className="animate-spin" /> : null}
          Изменить пароль
        </button>
      </form>

      {message && (
        <p
          role="status"
          className={
            message.tone === 'ok'
              ? 'flex items-center gap-2 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-2 text-[0.76rem] font-semibold text-emerald'
              : 'flex items-center gap-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.76rem] font-semibold text-[#fca5a5]'
          }
        >
          {message.tone === 'ok' && <CheckCircle2 size={14} />}
          {message.text}
        </p>
      )}
    </div>
  );
}

export function LogoutButton() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  return (
    <button
      type="button"
      className="btn btn-ghost"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          await fetch('/api/auth/logout', { method: 'POST' });
          router.push('/');
          router.refresh();
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? <Loader2 size={14} className="animate-spin" /> : <LogOut size={14} />}
      Выйти из аккаунта
    </button>
  );
}
