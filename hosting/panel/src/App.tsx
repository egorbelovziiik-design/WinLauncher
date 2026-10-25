import React, { useEffect, useState } from 'react';
import {
  Server,
  PlusCircle,
  Shield,
  LayoutGrid,
  LogOut,
  Cpu,
  MemoryStick,
  HardDrive,
  Activity,
  Copy,
  Layers,
} from 'lucide-react';
import { apiFetch, getStoredToken, setStoredToken } from './api';
import type {
  CatalogAddon,
  ComputeNode,
  CoreDefinition,
  HostingPlan,
  HostSystemTelemetry,
  MinecraftServer,
  UserPublic,
} from './types';
import { AuthScreen } from './components/AuthScreen';
import { CreateServerModal } from './components/CreateServerModal';
import { ServerView } from './components/ServerView';
import { AdminPanel } from './components/AdminPanel';
import { AccountSecurity } from './components/AccountSecurity';

export const App: React.FC = () => {
  const [user, setUser] = useState<UserPublic | null>(null);
  const [booting, setBooting] = useState(true);
  const [hostTelemetry, setHostTelemetry] = useState<HostSystemTelemetry | null>(null);

  const [view, setView] = useState<'dashboard' | 'server' | 'admin' | 'security'>('dashboard');
  const [selectedServerId, setSelectedServerId] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [servers, setServers] = useState<MinecraftServer[]>([]);
  const [cores, setCores] = useState<CoreDefinition[]>([]);
  const [addons, setAddons] = useState<CatalogAddon[]>([]);
  const [plans, setPlans] = useState<HostingPlan[]>([]);
  const [nodes, setNodes] = useState<ComputeNode[]>([]);

  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' } | null>(null);

  const notify = (msg: string, type: 'success' | 'error' = 'success') => {
    setToast({ msg, type });
    setTimeout(() => {
      setToast((cur) => (cur?.msg === msg ? null : cur));
    }, 3800);
  };

  const loadWorkspaceData = async () => {
    try {
      const [srvRes, catRes] = await Promise.all([
        apiFetch<{ servers: MinecraftServer[] }>('/api/servers'),
        apiFetch<{
          cores: CoreDefinition[];
          addons: CatalogAddon[];
          plans: HostingPlan[];
          nodes: ComputeNode[];
        }>('/api/catalog'),
      ]);
      setServers(srvRes.servers);
      setCores(catRes.cores);
      setAddons(catRes.addons);
      setPlans(catRes.plans);
      setNodes(catRes.nodes);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  useEffect(() => {
    const checkSession = async () => {
      if (!getStoredToken()) {
        setBooting(false);
        return;
      }
      try {
        const res = await apiFetch<{
          user: UserPublic;
          hostTelemetry: HostSystemTelemetry;
        }>('/api/auth/me');
        setUser(res.user);
        setHostTelemetry(res.hostTelemetry);
        await loadWorkspaceData();
      } catch {
        setStoredToken(null);
      } finally {
        setBooting(false);
      }
    };
    void checkSession();
  }, []);

  useEffect(() => {
    if (!user || view !== 'dashboard') return;
    const timer = setInterval(() => {
      void apiFetch<{ servers: MinecraftServer[] }>('/api/servers')
        .then((r) => setServers(r.servers))
        .catch(() => {});
    }, 3000);
    return () => clearInterval(timer);
  }, [user, view]);

  const handleAuthenticated = async (loggedIn: UserPublic) => {
    setUser(loggedIn);
    await loadWorkspaceData();
    try {
      const me = await apiFetch<{ user: UserPublic; hostTelemetry: HostSystemTelemetry }>(
        '/api/auth/me'
      );
      setHostTelemetry(me.hostTelemetry);
    } catch {
      // ignore
    }
  };

  const handleLogout = () => {
    setStoredToken(null);
    setUser(null);
    setSelectedServerId(null);
    setView('dashboard');
  };

  if (booting) {
    return (
      <div className="min-h-screen bg-[#070b12] flex items-center justify-center text-slate-400 text-sm">
        Инициализация WinHost Control Plane...
      </div>
    );
  }

  if (!user) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />;
  }

  return (
    <div className="min-h-screen bg-[#070b12] text-slate-100 flex flex-col lg:flex-row">
      {/* Боковое меню навигации */}
      <aside className="lg:w-68 shrink-0 bg-[#0a101d] border-b lg:border-b-0 lg:border-r border-slate-800/90 p-5 flex flex-col justify-between">
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <button
              onClick={() => {
                setView('dashboard');
                setSelectedServerId(null);
              }}
              className="flex items-center gap-3 text-left cursor-pointer"
            >
              <div className="w-10 h-10 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
                <Server className="w-5 h-5" />
              </div>
              <div>
                <div className="text-base font-extrabold tracking-tight text-white">
                  WinHost
                </div>
                <div className="text-[11px] text-emerald-400 font-medium">
                  DDR5 • PCIe 5.0 • 25GbE
                </div>
              </div>
            </button>
          </div>

          <button
            type="button"
            onClick={() => setShowCreateModal(true)}
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition cursor-pointer"
          >
            <PlusCircle className="w-4 h-4" /> Создать сервер
          </button>

          <nav className="space-y-1">
            <button
              onClick={() => {
                setView('dashboard');
                setSelectedServerId(null);
              }}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer ${
                view === 'dashboard' || view === 'server'
                  ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-900/70'
              }`}
            >
              <LayoutGrid className="w-4 h-4" /> Мои серверы ({servers.length})
            </button>

            {user.role === 'admin' && (
              <button
                onClick={() => {
                  setView('admin');
                  setSelectedServerId(null);
                }}
                className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer ${
                  view === 'admin'
                    ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-900/70'
                }`}
              >
                <Layers className="w-4 h-4" /> Админ-панель и Ноды
              </button>
            )}

            <button
              onClick={() => {
                setView('security');
                setSelectedServerId(null);
              }}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer ${
                view === 'security'
                  ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                  : 'text-slate-400 hover:text-white hover:bg-slate-900/70'
              }`}
            >
              <Shield className="w-4 h-4" /> Безопасность и 2FA
            </button>
          </nav>
        </div>

        <div className="pt-5 mt-6 border-t border-slate-800/80 flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="text-xs font-bold text-white truncate">{user.username}</div>
            <div className="text-[11px] text-slate-400 truncate">{user.email}</div>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-900 cursor-pointer"
            title="Выйти из аккаунта"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </aside>

      {/* Основная рабочая область */}
      <main className="flex-1 p-5 lg:p-8 overflow-y-auto">
        {toast && (
          <div
            className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-xl border shadow-2xl text-xs font-semibold ${
              toast.type === 'error'
                ? 'bg-rose-950/95 border-rose-500/50 text-rose-200'
                : 'bg-emerald-950/95 border-emerald-500/50 text-emerald-200'
            }`}
          >
            {toast.msg}
          </div>
        )}

        {view === 'dashboard' && (
          <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h1 className="text-2xl font-bold text-white">Ваши Minecraft-серверы</h1>
                <p className="text-sm text-slate-400">
                  Управляйте консолью, ядрами Paper / Purpur / Fabric / NeoForge, модами, бэкапами и ресурсами DDR5.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setShowCreateModal(true)}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                <PlusCircle className="w-4 h-4" /> Развернуть сервер
              </button>
            </div>

            <div className="grid xl:grid-cols-2 gap-5">
              {servers.map((srv) => {
                const m = srv.metrics;
                const isRunning = srv.status === 'running';
                return (
                  <div
                    key={srv.id}
                    onClick={() => {
                      setSelectedServerId(srv.id);
                      setView('server');
                    }}
                    className="group p-5 rounded-2xl bg-[#0d1422] hover:bg-[#111a2c] border border-slate-800 hover:border-emerald-500/50 transition cursor-pointer space-y-4"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={`w-2.5 h-2.5 rounded-full ${
                              isRunning ? 'bg-emerald-400 animate-pulse' : 'bg-slate-600'
                            }`}
                          />
                          <h2 className="text-lg font-bold text-white group-hover:text-emerald-300 transition">
                            {srv.name}
                          </h2>
                          <span className="px-2 py-0.5 rounded-md bg-cyan-500/10 border border-cyan-500/30 text-[11px] font-bold uppercase text-cyan-300">
                            {srv.core} {srv.mcVersion}
                          </span>
                        </div>
                        <p className="text-xs text-slate-400 mt-1">{srv.description}</p>
                      </div>

                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          navigator.clipboard.writeText(
                            `${srv.primaryAddress}:${srv.primaryPort}`
                          );
                          notify(`Адрес ${srv.primaryAddress}:${srv.primaryPort} скопирован!`);
                        }}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-950 border border-slate-800 font-mono text-xs text-emerald-400 hover:border-emerald-500/50 cursor-pointer shrink-0"
                      >
                        <Copy className="w-3 h-3" /> {srv.primaryAddress}:{srv.primaryPort}
                      </button>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 pt-2 border-t border-slate-800/80 text-xs">
                      <div className="p-2.5 rounded-xl bg-slate-950/80">
                        <div className="flex items-center gap-1 text-[11px] text-slate-400">
                          <Cpu className="w-3.5 h-3.5 text-emerald-400" /> CPU
                        </div>
                        <div className="font-mono font-bold text-white mt-0.5">
                          {m ? m.cpuPercent.toFixed(0) : 0}% / {srv.limits.cpuPercent}%
                        </div>
                      </div>

                      <div className="p-2.5 rounded-xl bg-slate-950/80">
                        <div className="flex items-center gap-1 text-[11px] text-slate-400">
                          <MemoryStick className="w-3.5 h-3.5 text-cyan-400" /> DDR5
                        </div>
                        <div className="font-mono font-bold text-white mt-0.5">
                          {m ? (m.ramUsedMb / 1024).toFixed(1) : '0.0'} /{' '}
                          {(srv.limits.ramMb / 1024).toFixed(0)} ГБ
                        </div>
                      </div>

                      <div className="p-2.5 rounded-xl bg-slate-950/80">
                        <div className="flex items-center gap-1 text-[11px] text-slate-400">
                          <HardDrive className="w-3.5 h-3.5 text-indigo-400" /> NVMe
                        </div>
                        <div className="font-mono font-bold text-white mt-0.5">
                          {m ? (m.diskUsedMb / 1024).toFixed(1) : '1.4'} / {srv.limits.diskGb} ГБ
                        </div>
                      </div>

                      <div className="p-2.5 rounded-xl bg-slate-950/80">
                        <div className="flex items-center gap-1 text-[11px] text-slate-400">
                          <Activity className="w-3.5 h-3.5 text-amber-400" /> TPS
                        </div>
                        <div className="font-mono font-bold text-emerald-400 mt-0.5">
                          {isRunning && m ? `${m.tps.toFixed(1)} (${m.mspt}ms)` : 'OFFLINE'}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-slate-500">
                      <span>Физический узел: {srv.nodeName}</span>
                      <span>
                        Расширений: {srv.installedAddons.length} • Портов: {srv.allocations.length}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {view === 'server' && selectedServerId && (
          <ServerView
            serverId={selectedServerId}
            cores={cores}
            addonsCatalog={addons}
            onBack={() => {
              setView('dashboard');
              setSelectedServerId(null);
            }}
            onServerChanged={loadWorkspaceData}
            onServerDeleted={() => {
              void loadWorkspaceData();
              setSelectedServerId(null);
              setView('dashboard');
            }}
            notify={notify}
          />
        )}

        {view === 'admin' && user.role === 'admin' && (
          <AdminPanel
            onServerProvisioned={(srv) => {
              void loadWorkspaceData();
              setSelectedServerId(srv.id);
              setView('server');
            }}
            notify={notify}
          />
        )}

        {view === 'security' && (
          <AccountSecurity
            user={user}
            hostTelemetry={hostTelemetry}
            onUserUpdated={setUser}
            notify={notify}
          />
        )}
      </main>

      {showCreateModal && (
        <CreateServerModal
          cores={cores}
          nodes={nodes}
          plans={plans}
          onClose={() => setShowCreateModal(false)}
          onCreated={(created) => {
            setShowCreateModal(false);
            void loadWorkspaceData();
            setSelectedServerId(created.id);
            setView('server');
            notify(`Сервер «${created.name}» создан и запускается!`);
          }}
        />
      )}
    </div>
  );
};
