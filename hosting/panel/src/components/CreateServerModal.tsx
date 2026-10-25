import React, { useState } from 'react';
import { X, Cpu, HardDrive, MemoryStick, Rocket, Sparkles } from 'lucide-react';
import { apiFetch } from '../api';
import type {
  ComputeNode,
  CoreDefinition,
  HostingPlan,
  JvmProfile,
  MinecraftServer,
  ServerCore,
} from '../types';

interface Props {
  cores: CoreDefinition[];
  nodes: ComputeNode[];
  plans: HostingPlan[];
  onClose: () => void;
  onCreated: (server: MinecraftServer) => void;
}

export const CreateServerModal: React.FC<Props> = ({
  cores,
  nodes,
  plans,
  onClose,
  onCreated,
}) => {
  const onlineNodes = nodes.filter((n) => n.status === 'online');
  const [name, setName] = useState('Мой новый SMP 1.21.4');
  const [description, setDescription] = useState('Премиум сервер на DDR5-6400 ECC');
  const [nodeId, setNodeId] = useState(onlineNodes[0]?.id || nodes[0]?.id || '');
  const [core, setCore] = useState<ServerCore>('purpur');
  const [mcVersion, setMcVersion] = useState('1.21.4');
  const [jvmProfile, setJvmProfile] = useState<JvmProfile>('aikar_g1');
  const [cpuPercent, setCpuPercent] = useState(400);
  const [ramMb, setRamMb] = useState(12288);
  const [diskGb, setDiskGb] = useState(80);
  const [selectedPlanId, setSelectedPlanId] = useState<string>('plan-pro');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeCoreDef = cores.find((c) => c.id === core) || cores[0];

  const applyPlan = (plan: HostingPlan) => {
    setSelectedPlanId(plan.id);
    setCore(plan.recommendedCore);
    const def = cores.find((c) => c.id === plan.recommendedCore);
    if (def && !def.supportedVersions.some((v) => v.mcVersion === mcVersion)) {
      setMcVersion(def.supportedVersions[0].mcVersion);
    }
    setCpuPercent(plan.cpuPercent);
    setRamMb(plan.ramMb);
    setDiskGb(plan.diskGb);
    setJvmProfile(plan.recommendedJvm);
  };

  const handleCoreChange = (newCore: ServerCore) => {
    setCore(newCore);
    const def = cores.find((c) => c.id === newCore);
    if (def && !def.supportedVersions.some((v) => v.mcVersion === mcVersion)) {
      setMcVersion(def.supportedVersions[0].mcVersion);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await apiFetch<{ server: MinecraftServer }>('/api/servers', {
        method: 'POST',
        body: JSON.stringify({
          name,
          description,
          nodeId,
          core,
          mcVersion,
          jvmProfile,
          cpuPercent,
          ramMb,
          diskGb,
          autoStart: true,
        }),
      });
      onCreated(res.server);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="w-full max-w-4xl bg-[#0c1322] border border-slate-800 rounded-2xl shadow-2xl overflow-hidden my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-950/60">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
              <Rocket className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">
                Развернуть новый Minecraft-сервер (Docker cgroups v2)
              </h2>
              <p className="text-xs text-slate-400">
                Мгновенная инициализация на NVMe PCIe 5.0 с автоназначением порта
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/70 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6 max-h-[82vh] overflow-y-auto">
          {error && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/40 text-rose-300 text-xs">
              {error}
            </div>
          )}

          {/* 1. Пресеты тарифов */}
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3">
              <Sparkles className="w-3.5 h-3.5 text-emerald-400" /> Быстрый выбор тарифного профиля
            </div>
            <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {plans.map((p) => {
                const active = selectedPlanId === p.id;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => applyPlan(p)}
                    className={`p-3.5 rounded-xl border text-left transition cursor-pointer ${
                      active
                        ? 'bg-emerald-500/10 border-emerald-500 text-white'
                        : 'bg-slate-950/70 border-slate-800 text-slate-300 hover:border-slate-700'
                    }`}
                  >
                    <div className="text-[10px] font-bold uppercase tracking-wider text-emerald-400 mb-0.5">
                      {p.badge}
                    </div>
                    <div className="font-bold text-sm">{p.name}</div>
                    <div className="text-xs text-slate-400 mt-1">
                      {(p.ramMb / 1024).toFixed(0)} ГБ DDR5 • {p.cpuPercent}% CPU • {p.diskGb} ГБ NVMe
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 2. Имя сервера и физическая нода */}
          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Название сервера
              </label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Физический сервер (Нода кластера)
              </label>
              <select
                value={nodeId}
                onChange={(e) => setNodeId(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
              >
                {onlineNodes.map((n) => {
                  const freeGb = ((n.ramTotalMb - (n.allocatedRamMb || 0)) / 1024).toFixed(0);
                  return (
                    <option key={n.id} value={n.id}>
                      {n.name} — {n.boostClockGhz} ГГц • свободно {freeGb} ГБ DDR5 ECC
                    </option>
                  );
                })}
              </select>
            </div>
          </div>

          {/* 3. Выбор ядра и версии Minecraft */}
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-2">
              Серверное ядро / Модлоадер
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 mb-3">
              {cores.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => handleCoreChange(c.id)}
                  className={`py-2.5 px-3 rounded-xl border text-center transition cursor-pointer ${
                    core === c.id
                      ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300 font-bold'
                      : 'bg-slate-950 border-slate-800 text-slate-300 hover:border-slate-700'
                  }`}
                >
                  <div className="text-sm">{c.name}</div>
                  <div className="text-[10px] text-slate-400">
                    {c.addonType === 'plugin' ? 'Плагины' : 'Моды'}
                  </div>
                </button>
              ))}
            </div>
            <p className="text-xs text-slate-400 mb-3">{activeCoreDef?.tagline}</p>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Версия Minecraft
                </label>
                <select
                  value={mcVersion}
                  onChange={(e) => setMcVersion(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                >
                  {activeCoreDef?.supportedVersions.map((v) => (
                    <option key={v.mcVersion} value={v.mcVersion}>
                      Minecraft {v.mcVersion} ({v.latestBuild} • Java {v.recommendedJava})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Профиль сборщика мусора JVM
                </label>
                <select
                  value={jvmProfile}
                  onChange={(e) => setJvmProfile(e.target.value as JvmProfile)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white focus:outline-none focus:border-emerald-500"
                >
                  <option value="aikar_g1">Aikar&apos;s G1GC + AVX-512 (оптимально до 14 ГБ)</option>
                  <option value="zgc_gen">Java 21 Generational ZGC (&lt;1мс пауз, 16–64 ГБ)</option>
                </select>
              </div>
            </div>
          </div>

          {/* 4. Ползунки лимитов ресурсов cgroups v2 */}
          <div className="grid sm:grid-cols-3 gap-4 p-4 rounded-xl bg-slate-950/80 border border-slate-800">
            <div>
              <div className="flex items-center justify-between text-xs mb-2">
                <span className="flex items-center gap-1.5 text-slate-300 font-medium">
                  <Cpu className="w-3.5 h-3.5 text-emerald-400" /> Лимит CPU
                </span>
                <span className="font-mono font-bold text-emerald-400">{cpuPercent}%</span>
              </div>
              <input
                type="range"
                min={100}
                max={1600}
                step={100}
                value={cpuPercent}
                onChange={(e) => {
                  setCpuPercent(Number(e.target.value));
                  setSelectedPlanId('custom');
                }}
                className="w-full accent-emerald-500"
              />
              <div className="text-[11px] text-slate-500 mt-1">
                {(cpuPercent / 100).toFixed(0)} потоков AMD Zen 5
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between text-xs mb-2">
                <span className="flex items-center gap-1.5 text-slate-300 font-medium">
                  <MemoryStick className="w-3.5 h-3.5 text-cyan-400" /> Память DDR5 ECC
                </span>
                <span className="font-mono font-bold text-cyan-400">
                  {(ramMb / 1024).toFixed(0)} ГБ ({ramMb} МБ)
                </span>
              </div>
              <input
                type="range"
                min={2048}
                max={65536}
                step={2048}
                value={ramMb}
                onChange={(e) => {
                  setRamMb(Number(e.target.value));
                  setSelectedPlanId('custom');
                }}
                className="w-full accent-cyan-500"
              />
              <div className="text-[11px] text-slate-500 mt-1">
                JVM Heap (85%): {Math.floor(ramMb * 0.85)} МБ
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between text-xs mb-2">
                <span className="flex items-center gap-1.5 text-slate-300 font-medium">
                  <HardDrive className="w-3.5 h-3.5 text-indigo-400" /> Квота NVMe PCIe 5.0
                </span>
                <span className="font-mono font-bold text-indigo-400">{diskGb} ГБ</span>
              </div>
              <input
                type="range"
                min={20}
                max={500}
                step={10}
                value={diskGb}
                onChange={(e) => {
                  setDiskGb(Number(e.target.value));
                  setSelectedPlanId('custom');
                }}
                className="w-full accent-indigo-500"
              />
              <div className="text-[11px] text-slate-500 mt-1">XFS Project Quota + RAID-1 PLP</div>
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 text-sm font-medium cursor-pointer"
            >
              Отмена
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-6 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 font-bold text-sm transition cursor-pointer"
            >
              {loading ? 'Развёртывание контейнера...' : 'Создать и запустить сервер'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
