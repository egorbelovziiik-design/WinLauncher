'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { KeyRound, Loader2, Lock, ShieldCheck, User } from 'lucide-react';

export function LoginForm() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [totp, setTotp] = useState('');
  const [error, setError] = useState('');
  const [blocked, setCookieBlocked] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const res = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), password, totp: totp.trim() || undefined }),
      });
      const data = (await res.json()) as { error?: string; ok?: boolean };
      if (!res.ok) {
        setError(data.error ?? 'Не удалось войти');
        return;
      }
      // Пароль верный, но браузер мог не сохранить cookie (например, сайт открыт
      // в iframe на другом домене). Проверяем, что сессия реально дошла до сервера.
      const check = await fetch('/api/admin/session', { cache: 'no-store' });
      if (!check.ok) {
        setCookieBlocked(true);
        return;
      }
      router.push('/admin');
      router.refresh();
    } catch {
      setError('Сетевая ошибка. Попробуйте снова.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <label className="block">
        <span className="field-label">Логин</span>
        <div className="relative">
          <User size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input value={username} onChange={(e) => setUsername(e.target.value)} className="field pl-9" autoComplete="username" required />
        </div>
      </label>
      <label className="block">
        <span className="field-label">Пароль</span>
        <div className="relative">
          <Lock size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="field pl-9"
            autoComplete="current-password"
            required
          />
        </div>
      </label>
      <label className="block">
        <span className="field-label">Код 2FA <span className="normal-case tracking-normal text-white/25">(если включена)</span></span>
        <div className="relative">
          <KeyRound size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            value={totp}
            onChange={(e) => setTotp(e.target.value.replace(/\D/g, ''))}
            className="field pl-9 tracking-[0.35em]"
            inputMode="numeric"
            maxLength={6}
            placeholder="000000"
            autoComplete="one-time-code"
          />
        </div>
      </label>

      {blocked && (
        <div className="rounded-lg border border-amber/30 bg-amber/10 px-3 py-2 text-[0.76rem] leading-relaxed text-amber">
          Логин и пароль верные, но браузер не сохранил сессию в этом окне (так бывает внутри встроенного превью).
          Откройте панель в отдельной вкладке — вход уже выполнен.
          <button
            type="button"
            onClick={() => window.open(`${window.location.origin}/admin`, '_blank', 'noopener')}
            className="btn btn-sm btn-gold mt-2 w-full"
          >
            Открыть админку в новой вкладке
          </button>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.76rem] font-semibold text-[#fca5a5]">
          {error}
        </p>
      )}

      <button type="submit" disabled={busy} className="btn btn-primary w-full">
        {busy ? <Loader2 size={16} className="animate-spin" /> : <ShieldCheck size={16} />}
        {busy ? 'Проверяем…' : 'Войти в панель'}
      </button>

      <p className="text-center text-[0.66rem] leading-relaxed text-white/25">
        Доступ только для администрации. Все входы записываются в журнал безопасности вместе с IP.
      </p>
    </form>
  );
}
