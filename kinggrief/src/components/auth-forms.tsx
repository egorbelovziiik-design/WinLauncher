'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { AlertTriangle, CheckCircle2, Loader2, Lock, LogIn, Mail, User, UserPlus } from 'lucide-react';

const NICK_RE = /^[A-Za-z0-9_]{3,16}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

function Alert({ tone, children }: { tone: 'error' | 'success'; children: React.ReactNode }) {
  const error = tone === 'error';
  return (
    <p
      role="alert"
      className={
        error
          ? 'flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.76rem] font-semibold text-[#fca5a5]'
          : 'flex items-start gap-2 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-2 text-[0.76rem] font-semibold text-emerald'
      }
    >
      {error ? <AlertTriangle size={14} className="mt-0.5 shrink-0" /> : <CheckCircle2 size={14} className="mt-0.5 shrink-0" />}
      <span>{children}</span>
    </p>
  );
}

function safeNext(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/account';
  return value;
}

export function UserLoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get('next'));
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBlocked('');
    setBusy(true);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: login.trim(), password }),
      });
      const data = (await res.json()) as { error?: string };
      if (!res.ok) {
        setError(data.error ?? 'Не удалось войти');
        return;
      }
      // Проверяем, что браузер сохранил cookie: во встроенном превью он может их блокировать.
      const check = await fetch('/api/auth/me', { cache: 'no-store' });
      const me = (await check.json().catch(() => null)) as { user?: unknown } | null;
      if (!me?.user) {
        setBlocked(next);
        return;
      }
      router.push(next);
      router.refresh();
    } catch {
      setError('Сеть недоступна. Проверьте соединение и попробуйте снова.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <label className="block">
        <span className="field-label">Никнейм или email</span>
        <div className="relative">
          <User size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            className="field pl-9"
            autoComplete="username"
            placeholder="Steve_2026"
            required
          />
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

      {error && <Alert tone="error">{error}</Alert>}

      {blocked && (
        <div className="rounded-lg border border-amber/30 bg-amber/10 px-3 py-2 text-[0.76rem] leading-relaxed text-amber">
          Логин и пароль верные, но браузер не сохранил сессию в этом окне (бывает во встроенном превью).
          Откройте кабинет в отдельной вкладке — вход уже выполнен.
          <button
            type="button"
            onClick={() => window.open(`${window.location.origin}${blocked}`, '_blank', 'noopener')}
            className="btn btn-sm btn-gold mt-2 w-full"
          >
            Открыть кабинет в новой вкладке
          </button>
        </div>
      )}

      <button type="submit" disabled={busy} className="btn btn-primary w-full">
        {busy ? <Loader2 size={16} className="animate-spin" /> : <LogIn size={16} />}
        {busy ? 'Проверяем…' : 'Войти в аккаунт'}
      </button>

      <p className="text-center text-[0.72rem] text-white/40">
        Нет аккаунта?{' '}
        <Link href="/register" className="font-semibold text-neon hover:underline">
          Зарегистрироваться
        </Link>
      </p>
    </form>
  );
}

export function RegisterForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get('next'));
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [blocked, setBlocked] = useState('');
  const [info, setInfo] = useState('');
  const [agree, setAgree] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!NICK_RE.test(username.trim())) {
      setError('Никнейм: 3–16 символов, латиница, цифры и _');
      return;
    }
    if (!EMAIL_RE.test(email.trim())) {
      setError('Проверьте формат email');
      return;
    }
    if (password.length < 8 || !/\d/.test(password)) {
      setError('Пароль: минимум 8 символов и хотя бы одна цифра');
      return;
    }
    if (password !== confirm) {
      setError('Пароли не совпадают');
      return;
    }
    if (!agree) {
      setError('Подтвердите согласие с правилами');
      return;
    }

    setBusy(true);
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          email: email.trim(),
          password,
          confirm,
          agree: true,
        }),
      });
      const data = (await res.json()) as { error?: string };
      if (!res.ok) {
        setError(data.error ?? 'Не удалось создать аккаунт');
        return;
      }
      const check = await fetch('/api/auth/me', { cache: 'no-store' });
      const me = (await check.json().catch(() => null)) as { user?: unknown } | null;
      if (!me?.user) {
        setBlocked(next);
        setInfo(`Аккаунт ${username.trim()} создан, но браузер не сохранил сессию в этом окне.`);
        return;
      }
      router.push(next);
      router.refresh();
    } catch {
      setError('Сеть недоступна. Проверьте соединение и попробуйте снова.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <label className="block">
        <span className="field-label">Minecraft-никнейм *</span>
        <div className="relative">
          <User size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="field pl-9"
            autoComplete="username"
            placeholder="Steve_2026"
            maxLength={16}
            required
          />
        </div>
        <span className="mt-1 block text-[0.66rem] text-white/30">Такой же, как в игре — на него придут покупки.</span>
      </label>
      <label className="block">
        <span className="field-label">Email *</span>
        <div className="relative">
          <Mail size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="field pl-9"
            autoComplete="email"
            placeholder="you@example.com"
            required
          />
        </div>
      </label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="field-label">Пароль *</span>
          <div className="relative">
            <Lock size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="field pl-9"
              autoComplete="new-password"
              required
            />
          </div>
        </label>
        <label className="block">
          <span className="field-label">Повторите пароль *</span>
          <div className="relative">
            <Lock size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className="field pl-9"
              autoComplete="new-password"
              required
            />
          </div>
        </label>
      </div>
      <p className="text-[0.66rem] text-white/30">Минимум 8 символов, хотя бы одна буква и одна цифра.</p>

      <label className="flex cursor-pointer items-start gap-2 text-[0.74rem] text-white/45">
        <input
          type="checkbox"
          checked={agree}
          onChange={(e) => setAgree(e.target.checked)}
          className="mt-0.5 h-4 w-4 shrink-0 accent-[#a855f7]"
        />
        <span>
          Согласен с{' '}
          <Link href="/rules" className="text-neon hover:underline">
            правилами сервера
          </Link>{' '}
          и обработкой данных.
        </span>
      </label>

      {error && <Alert tone="error">{error}</Alert>}
      {info && <Alert tone="success">{info}</Alert>}

      {blocked && (
        <div className="rounded-lg border border-amber/30 bg-amber/10 px-3 py-2 text-[0.76rem] leading-relaxed text-amber">
          Браузер не сохранил сессию в этом окне — откройте кабинет в отдельной вкладке и войдите там.
          <button
            type="button"
            onClick={() => window.open(`${window.location.origin}/login`, '_blank', 'noopener')}
            className="btn btn-sm btn-gold mt-2 w-full"
          >
            Открыть вход в новой вкладке
          </button>
        </div>
      )}

      <button type="submit" disabled={busy} className="btn btn-primary w-full">
        {busy ? <Loader2 size={16} className="animate-spin" /> : <UserPlus size={16} />}
        {busy ? 'Создаём аккаунт…' : 'Создать аккаунт'}
      </button>

      <p className="text-center text-[0.72rem] text-white/40">
        Уже есть аккаунт?{' '}
        <Link href="/login" className="font-semibold text-neon hover:underline">
          Войти
        </Link>
      </p>
    </form>
  );
}
