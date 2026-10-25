import React, { useEffect, useState } from 'react';
import {
  Server,
  Users,
  ShieldAlert,
  CreditCard,
  Plus,
  Cpu,
  MemoryStick,
  HardDrive,
  CheckCircle2,
  Wrench,
  Copy,
  Rocket,
} from 'lucide-react';
import { apiFetch } from '../api';
import type {
  AuditLogEntry,
  ComputeNode,
  HostingPlan,
  HostSystemTelemetry,
  MinecraftServer,
  UserPublic,
} from '../types';

interface Props {
  onServerProvisioned: (server: MinecraftServer) => void;
  notify: (msg: string, type?: 'success' | 'error') => void;
}

export const AdminPanel: React.FC<Props> = ({ onServerProvisioned, notify }) => {
  const [subTab, setSubTab] = useState<'nodes' | 'plans' | 'users' | 'audit'>('nodes');
  const [nodes, setNodes] = useState<ComputeNode[]>([]);
  const [users, setUsers] = useState<UserPublic[]>([]);
  const [plans, setPlans] = useState<HostingPlan[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [hostTelemetry, setHostTelemetry] = useState<HostSystemTelemetry | null>(null);

  // Форма добавления физической ноды
  const [showAddNode, setShowAddNode] = useState(false);
  const [nodeName, setNodeName] = useState('NL-AMS-03 • WinHost EPYC Turin');
  const [nodeLocation, setNodeLocation] = useState('Амстердам, Нидерланды (Tier IV)');
  const [nodeFqdn, setNodeFqdn] = useState('nl-ams-03.nodes.winhost.gg');
  const [nodeIp, setNodeIp] = useState('45.88.112.40');
  const [nodeCpu, setNodeCpu] = useState('AMD Ryzen Threadripper PRO 9985WX (64C/128T, 5.4 ГГц)');
  const [nodeRamGb, setNodeRamGb] = useState(512);
  const [nodeDiskGb, setNodeDiskGb] = useState(7680);
  const [generatedDaemonToken, setGeneratedDaemonToken] = useState<string | null>(null);

  // Форма создания пользователя
  const [newUserEmail, setNewUserEmail] = useState('');
  const [newUsername, setNewUsername] = useState('');
  const [newUserPassword, setNewUserPassword] = useState('');
  const [newUserRole, setNewUserRole] = useState<'client' | 'admin'>('client');
  const [newUserRamGb, setNewUserRamGb] = useState(32);

  // Выдача тарифа клиенту
  const [targetClientId, setTargetClientId] = useState('');
  const [targetNodeId, setTargetNodeId] = useState('');

  const loadAdminOverview = async () => {
    try {
      const res = await apiFetch<{
        nodes: ComputeNode[];
        users: UserPublic[];
        plans: HostingPlan[];
        auditLogs: AuditLogEntry[];
        hostTelemetry: HostSystemTelemetry;
      }>('/api/admin/overview');
      setNodes(res.nodes);
      setUsers(res.users);
      setPlans(res.plans);
      setAuditLogs(res.auditLogs);
      setHostTelemetry(res.hostTelemetry);
      if (!targetClientId && res.users.length > 0) {
        const client = res.users.find((u) => u.role === 'client') || res.users[0];
        setTargetClientId(client.id);
      }
      if (!targetNodeId && res.nodes.length > 0) {
        setTargetNodeId(res.nodes[0].id);
      }
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  useEffect(() => {
    void loadAdminOverview();
  }, []);

  const handleCreateNode = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch<{ node: ComputeNode; daemonInstallToken: string }>(
        '/api/admin/nodes',
        {
          method: 'POST',
          body: JSON.stringify({
            name: nodeName,
            location: nodeLocation,
            fqdn: nodeFqdn,
            publicIp: nodeIp,
            cpuModel: nodeCpu,
            ramTotalMb: nodeRamGb * 1024,
            ramType: `${nodeRamGb} ГБ DDR5-6400 ECC RDIMM`,
            diskTotalGb: nodeDiskGb,
            diskType: 'Enterprise NVMe PCIe 5.0 RAID-1 (PLP)',
            networkSpeedGbps: 25,
          }),
        }
      );
      setGeneratedDaemonToken(res.daemonInstallToken);
      setShowAddNode(false);
      await loadAdminOverview();
      notify(`Физический сервер ${res.node.name} добавлен в кластер!`);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const toggleNodeMaintenance = async (node: ComputeNode) => {
    try {
      await apiFetch(`/api/admin/nodes/${node.id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: node.status === 'online' ? 'maintenance' : 'online',
        }),
      });
      await loadAdminOverview();
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiFetch('/api/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          email: newUserEmail,
          username: newUsername,
          password: newUserPassword,
          role: newUserRole,
          maxServersQuota: 8,
          maxRamMbQuota: newUserRamGb * 1024,
        }),
      });
      setNewUserEmail('');
      setNewUsername('');
      setNewUserPassword('');
      await loadAdminOverview();
      notify('Аккаунт пользователя создан.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const toggleSuspendUser = async (u: UserPublic) => {
    try {
      await apiFetch(`/api/admin/users/${u.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ suspended: !u.suspended }),
      });
      await loadAdminOverview();
      notify(u.suspended ? 'Аккаунт разблокирован' : 'Аккаунт приостановлен');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const deployPlanForCustomer = async (plan: HostingPlan) => {
    try {
      const client = users.find((u) => u.id === targetClientId);
      const res = await apiFetch<{ server: MinecraftServer }>('/api/servers', {
        method: 'POST',
        body: JSON.stringify({
          name: `${plan.name} • ${client?.username || 'Client'}`,
          description: `Коммерческий тариф ${plan.name} ($${plan.priceMonthlyUsd}/мес)`,
          nodeId: targetNodeId,
          ownerId: targetClientId,
          core: plan.recommendedCore,
          mcVersion: plan.recommendedCore === 'neoforge' ? '1.21.1' : '1.21.4',
          jvmProfile: plan.recommendedJvm,
          cpuPercent: plan.cpuPercent,
          ramMb: plan.ramMb,
          diskGb: plan.diskGb,
          backupsLimit: plan.backupsLimit,
          portsLimit: plan.portsLimit,
          autoStart: true,
        }),
      });
      notify(`Тариф «${plan.name}» развёрнут для клиента ${client?.email}!`);
      onServerProvisioned(res.server);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const totalRamGb = Math.round(nodes.reduce((s, n) => s + n.ramTotalMb, 0) / 1024);
  const allocRamGb = Math.round(nodes.reduce((s, n) => s + (n.allocatedRamMb || 0), 0) / 1024);
  const totalDiskTb = (nodes.reduce((s, n) => s + n.diskTotalGb, 0) / 1000).toFixed(1);
  const totalThreads = nodes.reduce((s, n) => s + n.cpuThreads, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">Панель администратора кластера</h1>
          <p className="text-sm text-slate-400">
            Управление физическими серверами DDR5 ECC, квотами клиентов, коммерческими тарифами и безопасностью.
          </p>
        </div>
        {hostTelemetry && (
          <div className="px-3.5 py-2 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-300 font-mono">
            Host: {hostTelemetry.kernel} • Load: {hostTelemetry.loadAvg1m}
          </div>
        )}
      </div>

      {/* Глобальная сводка мощностей */}
      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs text-emerald-400 font-semibold mb-1">
            <Cpu className="w-4 h-4" /> Потоки AMD Zen 5
          </div>
          <div className="text-2xl font-extrabold text-white">{totalThreads} vCPU</div>
          <div className="text-xs text-slate-400 mt-0.5">
            {nodes.length} физических нод (до 5.7 ГГц)
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs text-cyan-400 font-semibold mb-1">
            <MemoryStick className="w-4 h-4" /> Пул памяти DDR5 ECC
          </div>
          <div className="text-2xl font-extrabold text-white">
            {allocRamGb} / {totalRamGb} ГБ
          </div>
          <div className="text-xs text-slate-400 mt-0.5">
            RDIMM 6400 MT/s + UDIMM 5600 MT/s
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold mb-1">
            <HardDrive className="w-4 h-4" /> Массивы NVMe PCIe 5.0
          </div>
          <div className="text-2xl font-extrabold text-white">{totalDiskTb} ТБ</div>
          <div className="text-xs text-slate-400 mt-0.5">ZFS / MDADM RAID-1 + PLP защита</div>
        </div>

        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs text-amber-400 font-semibold mb-1">
            <Users className="w-4 h-4" /> Пользователи платформы
          </div>
          <div className="text-2xl font-extrabold text-white">{users.length}</div>
          <div className="text-xs text-slate-400 mt-0.5">Мультитенантная изоляция RBAC</div>
        </div>
      </div>

      {/* Подвкладки Админ-панели */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {[
          { id: 'nodes', label: `Физические серверы (${nodes.length})`, icon: Server },
          { id: 'plans', label: 'Продажа хостинга и Тарифы', icon: CreditCard },
          { id: 'users', label: `Пользователи и Квоты (${users.length})`, icon: Users },
          { id: 'audit', label: 'Журнал безопасности', icon: ShieldAlert },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              onClick={() => setSubTab(item.id as typeof subTab)}
              className={`inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold border transition cursor-pointer ${
                subTab === item.id
                  ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-300'
                  : 'bg-[#0d1422] border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              <Icon className="w-4 h-4" /> {item.label}
            </button>
          );
        })}
      </div>

      {/* 1. ФИЗИЧЕСКИЕ СЕРВЕРЫ (МУЛЬТИ-НОДЫ) */}
      {subTab === 'nodes' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <div className="text-xs text-slate-400">
              Каждая физическая нода соединяется с веб-панелью по защищённому TLS 1.3 каналу (WinHostWings / Docker API).
            </div>
            <button
              type="button"
              onClick={() => setShowAddNode(!showAddNode)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
            >
              <Plus className="w-4 h-4" /> Подключить физический сервер
            </button>
          </div>

          {generatedDaemonToken && (
            <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/40 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-300">
                  Токен привязки агента физической ноды (сохраните в /etc/winhost-wings/config.yml):
                </span>
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(generatedDaemonToken);
                    notify('Токен ноды скопирован!');
                  }}
                  className="inline-flex items-center gap-1 text-xs text-emerald-400 cursor-pointer"
                >
                  <Copy className="w-3.5 h-3.5" /> Копировать
                </button>
              </div>
              <div className="font-mono text-xs text-white bg-slate-950 p-2.5 rounded-lg break-all">
                {generatedDaemonToken}
              </div>
            </div>
          )}

          {showAddNode && (
            <form
              onSubmit={handleCreateNode}
              className="p-5 rounded-2xl bg-[#0d1422] border border-emerald-500/40 grid sm:grid-cols-2 lg:grid-cols-3 gap-4"
            >
              <div>
                <label className="block text-xs text-slate-300 mb-1">Имя узла</label>
                <input
                  type="text"
                  required
                  value={nodeName}
                  onChange={(e) => setNodeName(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-300 mb-1">Локация ЦОД</label>
                <input
                  type="text"
                  required
                  value={nodeLocation}
                  onChange={(e) => setNodeLocation(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-300 mb-1">FQDN домен узла</label>
                <input
                  type="text"
                  required
                  value={nodeFqdn}
                  onChange={(e) => setNodeFqdn(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-300 mb-1">Публичный IPv4</label>
                <input
                  type="text"
                  required
                  value={nodeIp}
                  onChange={(e) => setNodeIp(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-300 mb-1">Модель CPU</label>
                <input
                  type="text"
                  required
                  value={nodeCpu}
                  onChange={(e) => setNodeCpu(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-xs text-slate-300 mb-1">DDR5 (ГБ)</label>
                  <input
                    type="number"
                    min={64}
                    value={nodeRamGb}
                    onChange={(e) => setNodeRamGb(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-300 mb-1">NVMe (ГБ)</label>
                  <input
                    type="number"
                    min={500}
                    value={nodeDiskGb}
                    onChange={(e) => setNodeDiskGb(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                  />
                </div>
              </div>
              <div className="sm:col-span-2 lg:col-span-3 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddNode(false)}
                  className="px-4 py-2 rounded-xl bg-slate-900 text-xs text-slate-300 cursor-pointer"
                >
                  Отмена
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-emerald-500 text-slate-950 font-bold text-xs cursor-pointer"
                >
                  Зарегистрировать ноду и выпустить TLS-токен
                </button>
              </div>
            </form>
          )}

          <div className="grid lg:grid-cols-2 gap-4">
            {nodes.map((n) => {
              const ramPct = Math.min(
                100,
                Math.round(((n.allocatedRamMb || 0) / n.ramTotalMb) * 100)
              );
              return (
                <div
                  key={n.id}
                  className="p-5 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="text-base font-bold text-white">{n.name}</h3>
                        {n.status === 'online' ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 text-[11px] font-bold">
                            <CheckCircle2 className="w-3 h-3" /> ONLINE
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 text-[11px] font-bold">
                            <Wrench className="w-3 h-3" /> ТЕХРАБОТЫ
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-slate-400 mt-0.5">
                        {n.location} • <span className="font-mono text-slate-300">{n.publicIp}</span> •{' '}
                        {n.networkSpeedGbps} Гбит/с SFP28
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => toggleNodeMaintenance(n)}
                      className="px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs text-slate-300 cursor-pointer"
                    >
                      {n.status === 'online' ? 'В режим техработ' : 'Вернуть в строй'}
                    </button>
                  </div>

                  <div className="grid sm:grid-cols-2 gap-2.5 text-xs bg-slate-950/70 p-3.5 rounded-xl border border-slate-800/80">
                    <div>
                      <div className="text-slate-500">Процессор</div>
                      <div className="text-slate-200 font-medium">
                        {n.cpuModel} (до {n.boostClockGhz} ГГц)
                      </div>
                    </div>
                    <div>
                      <div className="text-slate-500">Память</div>
                      <div className="text-slate-200 font-medium">{n.ramType}</div>
                    </div>
                    <div className="sm:col-span-2">
                      <div className="text-slate-500">Дисковая подсистема</div>
                      <div className="text-slate-200 font-medium">{n.diskType}</div>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs mb-1.5">
                      <span className="text-slate-400">
                        Выделено памяти контейнерам ({n.serversCount || 0} серверов)
                      </span>
                      <span className="font-mono text-cyan-300">
                        {((n.allocatedRamMb || 0) / 1024).toFixed(0)} /{' '}
                        {(n.ramTotalMb / 1024).toFixed(0)} ГБ ({ramPct}%)
                      </span>
                    </div>
                    <div className="w-full h-2 rounded-full bg-slate-900 overflow-hidden">
                      <div
                        className="h-full bg-cyan-500 rounded-full"
                        style={{ width: `${ramPct}%` }}
                      />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 2. ПРОДАЖА ХОСТИНГА И ТАРИФНАЯ СЕТКА */}
      {subTab === 'plans' && (
        <div className="space-y-5">
          <div className="p-5 rounded-2xl bg-[#0d1422] border border-slate-800 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
            <div className="space-y-1">
              <h2 className="text-base font-bold text-white">
                Автоматическая выдача сервера покупателю (Provisioning)
              </h2>
              <p className="text-xs text-slate-400">
                Выберите клиента и целевую физическую ноду, затем нажмите «Развернуть по тарифу» — или подключите вебхук Paymenter / Stripe / YooKassa к <code>POST /api/servers</code>.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Клиент</label>
                <select
                  value={targetClientId}
                  onChange={(e) => setTargetClientId(e.target.value)}
                  className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                >
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.username} ({u.email})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Целевая нода</label>
                <select
                  value={targetNodeId}
                  onChange={(e) => setTargetNodeId(e.target.value)}
                  className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                >
                  {nodes
                    .filter((n) => n.status === 'online')
                    .map((n) => (
                      <option key={n.id} value={n.id}>
                        {n.name}
                      </option>
                    ))}
                </select>
              </div>
            </div>
          </div>

          <div className="grid sm:grid-cols-2 xl:grid-cols-4 gap-4">
            {plans.map((p) => (
              <div
                key={p.id}
                className="p-5 rounded-2xl bg-[#0d1422] border border-slate-800 flex flex-col justify-between space-y-4"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-[10px] font-bold uppercase">
                      {p.badge}
                    </span>
                    <span className="text-xs font-mono text-slate-400 uppercase">
                      {p.recommendedCore}
                    </span>
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-white">{p.name}</h3>
                    <div className="text-2xl font-extrabold text-emerald-400 mt-1">
                      ${p.priceMonthlyUsd}
                      <span className="text-xs font-normal text-slate-400">
                        /мес (~{p.priceMonthlyRub} ₽)
                      </span>
                    </div>
                  </div>
                  <ul className="space-y-1.5 text-xs text-slate-300">
                    {p.features.map((f) => (
                      <li key={f} className="flex items-start gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{f}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <button
                  type="button"
                  onClick={() => deployPlanForCustomer(p)}
                  className="w-full inline-flex items-center justify-center gap-1.5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
                >
                  <Rocket className="w-3.5 h-3.5" /> Развернуть клиенту
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 3. УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ */}
      {subTab === 'users' && (
        <div className="grid lg:grid-cols-12 gap-5">
          <div className="lg:col-span-8 p-5 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-3">
            <h2 className="text-base font-bold text-white">Зарегистрированные аккаунты</h2>
            <div className="space-y-2.5">
              {users.map((u) => (
                <div
                  key={u.id}
                  className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white">{u.username}</span>
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                          u.role === 'admin'
                            ? 'bg-emerald-500/15 text-emerald-400'
                            : 'bg-cyan-500/15 text-cyan-300'
                        }`}
                      >
                        {u.role === 'admin' ? 'Администратор' : 'Клиент'}
                      </span>
                      {u.totpEnabled && (
                        <span className="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 text-[10px] font-semibold">
                          2FA Активна
                        </span>
                      )}
                      {u.suspended && (
                        <span className="px-2 py-0.5 rounded bg-rose-500/20 text-rose-300 text-[10px] font-bold">
                          Заблокирован
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-slate-400 mt-0.5">
                      {u.email} • Квота: {u.maxServersQuota} серверов /{' '}
                      {(u.maxRamMbQuota / 1024).toFixed(0)} ГБ DDR5
                    </div>
                  </div>

                  {u.role !== 'admin' && (
                    <button
                      type="button"
                      onClick={() => toggleSuspendUser(u)}
                      className="px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs text-slate-300 cursor-pointer"
                    >
                      {u.suspended ? 'Разблокировать' : 'Приостановить'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <form
            onSubmit={handleCreateUser}
            className="lg:col-span-4 p-5 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-3.5"
          >
            <h2 className="text-base font-bold text-white">Создать клиента / админа</h2>
            <input
              type="text"
              required
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              placeholder="Имя пользователя"
              className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
            />
            <input
              type="email"
              required
              value={newUserEmail}
              onChange={(e) => setNewUserEmail(e.target.value)}
              placeholder="client@domain.com"
              className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
            />
            <input
              type="password"
              required
              minLength={8}
              value={newUserPassword}
              onChange={(e) => setNewUserPassword(e.target.value)}
              placeholder="Пароль (от 8 символов)"
              className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
            />
            <div className="grid grid-cols-2 gap-2">
              <select
                value={newUserRole}
                onChange={(e) => setNewUserRole(e.target.value as 'client' | 'admin')}
                className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              >
                <option value="client">Клиент</option>
                <option value="admin">Администратор</option>
              </select>
              <input
                type="number"
                min={2}
                max={512}
                value={newUserRamGb}
                onChange={(e) => setNewUserRamGb(Number(e.target.value))}
                placeholder="Квота RAM ГБ"
                className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
            >
              Создать аккаунт
            </button>
          </form>
        </div>
      )}

      {/* 4. ЖУРНАЛ АУДИТА БЕЗОПАСНОСТИ */}
      {subTab === 'audit' && (
        <div className="p-5 rounded-2xl bg-[#0d1422] border border-slate-800 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400">
                <th className="py-2.5 px-3">Время (UTC)</th>
                <th className="py-2.5 px-3">Субъект</th>
                <th className="py-2.5 px-3">IP-адрес</th>
                <th className="py-2.5 px-3">Событие</th>
                <th className="py-2.5 px-3">Объект</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {auditLogs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-900/40">
                  <td className="py-2.5 px-3 font-mono text-slate-400">
                    {log.timestamp.replace('T', ' ').slice(0, 19)}
                  </td>
                  <td className="py-2.5 px-3 text-slate-200">{log.actorEmail}</td>
                  <td className="py-2.5 px-3 font-mono text-slate-400">{log.ip}</td>
                  <td className="py-2.5 px-3 font-semibold text-emerald-300">{log.action}</td>
                  <td className="py-2.5 px-3 text-slate-300">{log.target}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
