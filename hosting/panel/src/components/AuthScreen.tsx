import React, { useEffect, useRef, useState } from 'react';
import {
  ShieldCheck,
  Cpu,
  HardDrive,
  Lock,
  KeyRound,
  Server,
  Sparkles,
  CheckCircle2,
  Rocket,
  Terminal,
  Zap,
  Globe,
  Layers,
} from 'lucide-react';
import { apiFetch, setStoredToken } from '../api';
import type { ComputeNode, HostingPlan, UserPublic } from '../types';

interface Props {
  onAuthenticated: (user: UserPublic) => void;
}

export const AuthScreen: React.FC<Props> = ({ onAuthenticated }) => {
  const [mode, setMode] = useState<'login' | 'register' | '2fa'>('login');
  const [email, setEmail] = useState('admin@winhost.gg');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('Admin#DDR5!2026');
  const [challengeToken, setChallengeToken] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [plans, setPlans] = useState<HostingPlan[]>([]);
  const [nodes, setNodes] = useState<ComputeNode[]>([]);
  const authCardRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    void apiFetch<{ plans: HostingPlan[]; nodes: ComputeNode[] }>('/api/public/showcase')
      .then((res) => {
        setPlans(res.plans);
        setNodes(res.nodes);
      })
      .catch(() => {});
  }, []);

  const performLogin = async (loginEmail: string, loginPass: string) => {
    setError(null);
    setLoading(true);
    try {
      const res = await apiFetch<{
        requiresTwoFactor: boolean;
        challengeToken?: string;
        token?: string;
        user?: UserPublic;
      }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: loginEmail, password: loginPass }),
      });

      if (res.requiresTwoFactor && res.challengeToken) {
        setChallengeToken(res.challengeToken);
        setMode('2fa');
        authCardRef.current?.scrollIntoView({ behavior: 'smooth' });
      } else if (res.token && res.user) {
        setStoredToken(res.token);
        onAuthenticated(res.user);
      }
    } catch (err) {
      setError((err as Error).message);
      authCardRef.current?.scrollIntoView({ behavior: 'smooth' });
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    await performLogin(email, password);
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await apiFetch<{ token: string; user: UserPublic }>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, username, password }),
      });
      setStoredToken(res.token);
      onAuthenticated(res.user);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const handleVerify2fa = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await apiFetch<{ token: string; user: UserPublic }>('/api/auth/verify-2fa', {
        method: 'POST',
        body: JSON.stringify({ challengeToken, code: totpCode }),
      });
      setStoredToken(res.token);
      onAuthenticated(res.user);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const quickEnter = (role: 'admin' | 'client') => {
    const targetEmail = role === 'admin' ? 'admin@winhost.gg' : 'alex@winhost.gg';
    const targetPass = role === 'admin' ? 'Admin#DDR5!2026' : 'Client#MC!2026';
    setEmail(targetEmail);
    setPassword(targetPass);
    void performLogin(targetEmail, targetPass);
  };

  return (
    <div className="min-h-screen bg-[#060a11] text-slate-100">
      {/* Навигационная шапка сайта WinHost */}
      <header className="sticky top-0 z-40 backdrop-blur-md bg-[#060a11]/85 border-b border-slate-800/80">
        <div className="max-w-7xl mx-auto px-5 py-3.5 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
              <Server className="w-5 h-5" />
            </div>
            <div>
              <div className="text-lg font-extrabold tracking-tight text-white">WinHost</div>
              <div className="text-[11px] text-emerald-400 font-medium">
                Premium DDR5 Minecraft Cloud
              </div>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-6 text-xs font-semibold text-slate-300">
            <a href="#plans" className="hover:text-emerald-400 transition">
              Тарифы DDR5
            </a>
            <a href="#hardware" className="hover:text-emerald-400 transition">
              Оборудование Zen 5
            </a>
            <a href="#features" className="hover:text-emerald-400 transition">
              Веб-панель
            </a>
          </nav>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              disabled={loading}
              onClick={() => quickEnter('admin')}
              className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition cursor-pointer"
            >
              Открыть панель WinHost
            </button>
          </div>
        </div>
      </header>

      {/* Главный Hero-экран + Форма входа в панель */}
      <section className="relative overflow-hidden border-b border-slate-800/80">
        <div className="absolute -top-32 left-1/4 w-[500px] h-[500px] bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-32 right-1/4 w-[450px] h-[450px] bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="max-w-7xl mx-auto px-5 py-14 lg:py-20 grid lg:grid-cols-12 gap-10 items-center relative z-10">
          <div className="lg:col-span-7 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs font-semibold">
              <Zap className="w-3.5 h-3.5" /> AMD Zen 5 до 5.7 ГГц • DDR5-6400 ECC • NVMe PCIe 5.0
            </div>

            <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight text-white leading-[1.12]">
              WinHost — премиальный хостинг серверов{' '}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-cyan-400">
                Minecraft без лагов
              </span>
            </h1>

            <p className="text-slate-400 text-base sm:text-lg leading-relaxed max-w-2xl">
              Собственная облачная платформа на процессорах AMD Threadripper PRO 9975WX и EPYC 4585PX (3D V-Cache), регистровой памяти DDR5 ECC до 512 ГБ и накопителях Micron PCIe 5.0 с защитой от потери питания.
            </p>

            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                disabled={loading}
                onClick={() => quickEnter('admin')}
                className="inline-flex items-center gap-2 px-6 py-3.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-sm transition cursor-pointer shadow-lg shadow-emerald-500/20"
              >
                <Rocket className="w-4 h-4" /> Войти в демо-панель (Владелец)
              </button>
              <a
                href="#plans"
                className="inline-flex items-center gap-2 px-6 py-3.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-700 text-white font-bold text-sm transition"
              >
                Смотреть тарифы
              </a>
            </div>

            <div className="grid grid-cols-3 gap-4 pt-4 max-w-xl">
              <div className="p-3.5 rounded-xl bg-slate-900/70 border border-slate-800">
                <div className="text-xl font-extrabold text-white">20.0 TPS</div>
                <div className="text-xs text-slate-400">Эталонный тик &lt;12 MSPT</div>
              </div>
              <div className="p-3.5 rounded-xl bg-slate-900/70 border border-slate-800">
                <div className="text-xl font-extrabold text-emerald-400">6400 MT/s</div>
                <div className="text-xs text-slate-400">8-канальная DDR5 ECC</div>
              </div>
              <div className="p-3.5 rounded-xl bg-slate-900/70 border border-slate-800">
                <div className="text-xl font-extrabold text-cyan-400">25 Гбит/с</div>
                <div className="text-xs text-slate-400">Mellanox SFP28 + Anti-DDoS</div>
              </div>
            </div>
          </div>

          {/* Правая карточка входа в панель управления WinHost */}
          <div
            ref={authCardRef}
            className="lg:col-span-5 bg-[#0c1322] border border-slate-800 rounded-2xl p-6 sm:p-7 shadow-2xl"
          >
            <div className="flex items-center justify-between mb-4">
              <div className="text-sm font-bold text-white">Панель управления WinHost</div>
              <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400 font-semibold">
                <Lock className="w-3 h-3" /> TLS 1.3 + 2FA
              </span>
            </div>

            {mode !== '2fa' && (
              <div className="flex rounded-xl bg-slate-950 p-1 mb-5 border border-slate-800/80">
                <button
                  type="button"
                  onClick={() => {
                    setMode('login');
                    setError(null);
                  }}
                  className={`flex-1 py-2 text-xs font-bold rounded-lg transition cursor-pointer ${
                    mode === 'login'
                      ? 'bg-emerald-500 text-slate-950'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Вход в аккаунт
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMode('register');
                    setEmail('');
                    setPassword('');
                    setError(null);
                  }}
                  className={`flex-1 py-2 text-xs font-bold rounded-lg transition cursor-pointer ${
                    mode === 'register'
                      ? 'bg-emerald-500 text-slate-950'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Регистрация клиента
                </button>
              </div>
            )}

            {error && (
              <div className="mb-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/40 text-rose-300 text-xs">
                {error}
              </div>
            )}

            {mode === 'login' && (
              <form onSubmit={handleLogin} className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Электронная почта
                  </label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                    placeholder="admin@winhost.gg"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">Пароль</label>
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                    placeholder="••••••••••••"
                  />
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 font-bold text-sm transition cursor-pointer"
                >
                  {loading ? 'Вход в WinHost...' : 'Войти в веб-панель'}
                </button>

                <div className="pt-4 border-t border-slate-800/80">
                  <div className="text-[11px] uppercase tracking-wider text-slate-500 font-semibold mb-2">
                    Мгновенный демо-вход в 1 клик:
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    <button
                      type="button"
                      onClick={() => quickEnter('admin')}
                      className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left transition cursor-pointer"
                    >
                      <div className="text-xs font-bold text-emerald-400">Владелец / Админ</div>
                      <div className="text-[11px] text-slate-400 truncate">admin@winhost.gg</div>
                    </button>
                    <button
                      type="button"
                      onClick={() => quickEnter('client')}
                      className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-left transition cursor-pointer"
                    >
                      <div className="text-xs font-bold text-cyan-400">Клиент хостинга</div>
                      <div className="text-[11px] text-slate-400 truncate">alex@winhost.gg</div>
                    </button>
                  </div>
                </div>
              </form>
            )}

            {mode === 'register' && (
              <form onSubmit={handleRegister} className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Ваш никнейм
                  </label>
                  <input
                    type="text"
                    required
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                    placeholder="SteveAdmin"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Электронная почта
                  </label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                    placeholder="you@domain.com"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Пароль (от 8 символов)
                  </label>
                  <input
                    type="password"
                    required
                    minLength={8}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                  />
                </div>
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 font-bold text-sm transition cursor-pointer"
                >
                  {loading ? 'Создание аккаунта...' : 'Создать аккаунт в WinHost'}
                </button>
              </form>
            )}

            {mode === '2fa' && (
              <form onSubmit={handleVerify2fa} className="space-y-4">
                <div className="flex items-center gap-3 mb-2">
                  <div className="w-10 h-10 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
                    <KeyRound className="w-5 h-5" />
                  </div>
                  <div>
                    <h2 className="text-base font-bold text-white">Двухфакторная защита (2FA)</h2>
                    <p className="text-xs text-slate-400">
                      Введите 6-значный код из приложения или резервный код
                    </p>
                  </div>
                </div>

                <input
                  type="text"
                  required
                  autoFocus
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                  className="w-full px-4 py-3 rounded-xl bg-slate-950 border border-slate-700 text-center font-mono text-lg tracking-widest text-white focus:outline-none focus:border-emerald-500"
                  placeholder="000000 или XXXXX-XXXXX"
                />

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-sm transition cursor-pointer"
                >
                  Подтвердить и войти
                </button>
              </form>
            )}
          </div>
        </div>
      </section>

      {/* Секция тарифов хостинга WinHost */}
      <section id="plans" className="max-w-7xl mx-auto px-5 py-16 space-y-8">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <div className="text-xs font-bold uppercase tracking-widest text-emerald-400">
            Тарифная линейка DDR5 ECC
          </div>
          <h2 className="text-3xl font-extrabold text-white">
            Честные выделенные ресурсы без оверселлинга
          </h2>
          <p className="text-sm text-slate-400">
            Развёртывание контейнера за 3 секунды. Полная поддержка Paper, Purpur, Fabric, NeoForge и Forge любых версий.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {plans.map((p, idx) => (
            <div
              key={p.id}
              className={`p-6 rounded-2xl border flex flex-col justify-between space-y-5 ${
                idx === 1
                  ? 'bg-gradient-to-b from-emerald-950/30 to-[#0c1322] border-emerald-500/60 shadow-xl shadow-emerald-500/5'
                  : 'bg-[#0c1322] border-slate-800'
              }`}
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 text-[10px] font-bold uppercase">
                    {p.badge}
                  </span>
                  <span className="text-xs font-mono text-slate-400 uppercase">
                    {p.recommendedCore}
                  </span>
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">{p.name}</h3>
                  <div className="text-3xl font-extrabold text-white mt-1">
                    {p.priceMonthlyRub} ₽
                    <span className="text-xs font-normal text-slate-400">
                      /мес (${p.priceMonthlyUsd})
                    </span>
                  </div>
                </div>
                <ul className="space-y-2 text-xs text-slate-300 pt-2 border-t border-slate-800/80">
                  {p.features.map((f) => (
                    <li key={f} className="flex items-start gap-2">
                      <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <button
                type="button"
                onClick={() => quickEnter('admin')}
                className={`w-full py-2.5 rounded-xl font-bold text-xs transition cursor-pointer ${
                  idx === 1
                    ? 'bg-emerald-500 hover:bg-emerald-400 text-slate-950'
                    : 'bg-slate-900 hover:bg-slate-800 border border-slate-700 text-white'
                }`}
              >
                Создать сервер в панели
              </button>
            </div>
          ))}
        </div>
      </section>

      {/* Секция серверного оборудования и нод */}
      <section
        id="hardware"
        className="border-t border-b border-slate-800/80 bg-[#090f1b] py-16"
      >
        <div className="max-w-7xl mx-auto px-5 space-y-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div>
              <div className="text-xs font-bold uppercase tracking-widest text-cyan-400 mb-1">
                Bare-Metal Инфраструктура
              </div>
              <h2 className="text-3xl font-extrabold text-white">
                Физические ноды кластера WinHost
              </h2>
            </div>
            <div className="text-xs text-slate-400">
              Debian 12/13 Kernel 6.12 LTS • CPU Governor Performance • TCP BBR 25GbE
            </div>
          </div>

          <div className="grid md:grid-cols-2 gap-6">
            {nodes.map((n) => (
              <div
                key={n.id}
                className="p-6 rounded-2xl bg-[#0c1322] border border-slate-800 space-y-4"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <Globe className="w-5 h-5 text-emerald-400" />
                    <div>
                      <h3 className="text-base font-bold text-white">{n.name}</h3>
                      <div className="text-xs text-slate-400">{n.location}</div>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 rounded-full bg-emerald-500/15 text-emerald-400 text-xs font-bold">
                    ONLINE • {n.networkSpeedGbps} Gbps
                  </span>
                </div>

                <div className="grid sm:grid-cols-2 gap-3 text-xs bg-slate-950 p-4 rounded-xl border border-slate-800/80">
                  <div>
                    <div className="text-slate-500">Процессор</div>
                    <div className="text-white font-semibold mt-0.5">
                      {n.cpuModel} (до {n.boostClockGhz} ГГц)
                    </div>
                  </div>
                  <div>
                    <div className="text-slate-500">Память</div>
                    <div className="text-cyan-300 font-semibold mt-0.5">{n.ramType}</div>
                  </div>
                  <div className="sm:col-span-2">
                    <div className="text-slate-500">Хранилище NVMe PCIe 5.0</div>
                    <div className="text-slate-200 font-semibold mt-0.5">{n.diskType}</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Секция возможностей веб-панели */}
      <section id="features" className="max-w-7xl mx-auto px-5 py-16 space-y-8">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <h2 className="text-3xl font-extrabold text-white">
            Собственная облачная панель WinHost Panel
          </h2>
          <p className="text-sm text-slate-400">
            Современная замена устаревшим панелям: мгновенный отклик, встроенный каталог ядер, модов и плагинов.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {[
            {
              icon: Terminal,
              title: 'Живая консоль и графики TPS',
              desc: 'Интерактивный терминал сервера, быстрые макросы команд и мониторинг CPU, DDR5 RAM, NVMe и MSPT.',
            },
            {
              icon: Layers,
              title: 'Paper, Purpur, Fabric, NeoForge, Forge',
              desc: 'Переключение ядра и версии Minecraft (1.12.2–1.21.4) в 1 клик с профилями Generational ZGC и Aikar G1GC.',
            },
            {
              icon: Sparkles,
              title: 'Маркетплейс плагинов и модов',
              desc: 'Автоустановка Spark, LuckPerms, EssentialsX, Lithium, FerriteCore, Create, AE2 и Simple Voice Chat.',
            },
            {
              icon: HardDrive,
              title: 'Файловый менеджер и Авто-бэкапы',
              desc: 'Встроенный редактор конфигов на диске и атомарные архивы .tar.gz с проверкой целостности SHA-256.',
            },
            {
              icon: Cpu,
              title: 'Docker cgroups v2 + XFS pquota',
              desc: 'Строгая аппаратная изоляция потоков CPU, памяти DDR5 (85% Anti-OOM Heap) и дискового пространства.',
            },
            {
              icon: ShieldCheck,
              title: 'Двухфакторная защита 2FA и RBAC',
              desc: 'TOTP аутентификация (Google Authenticator / Authy), резервные коды, суб-пользователи и админ-панель.',
            },
          ].map((feat) => {
            const Icon = feat.icon;
            return (
              <div
                key={feat.title}
                className="p-5 rounded-2xl bg-[#0c1322] border border-slate-800 space-y-2.5"
              >
                <div className="w-9 h-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
                  <Icon className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white">{feat.title}</h3>
                <p className="text-xs text-slate-400 leading-relaxed">{feat.desc}</p>
              </div>
            );
          })}
        </div>
      </section>

      <footer className="border-t border-slate-800/80 py-8 text-center text-xs text-slate-500">
        © 2026 WinHost — Премиальная платформа Minecraft-хостинга (DDR5 ECC • PCIe 5.0 NVMe • 25GbE).
      </footer>
    </div>
  );
};
