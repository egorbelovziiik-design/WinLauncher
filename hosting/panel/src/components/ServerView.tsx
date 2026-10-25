import React, { useEffect, useRef, useState } from 'react';
import {
  Terminal,
  FolderOpen,
  Layers,
  Puzzle,
  Archive,
  Network,
  Users,
  Play,
  Square,
  RotateCw,
  Skull,
  Copy,
  Send,
  Plus,
  Trash2,
  Download,
  Lock,
  Unlock,
  RotateCcw,
  FileText,
  Folder,
  Save,
  ArrowLeft,
  CheckCircle2,
  Cpu,
  MemoryStick,
  HardDrive,
  Activity,
} from 'lucide-react';
import { apiFetch, buildDownloadUrl, buildWsUrl } from '../api';
import type {
  BackupSchedule,
  CatalogAddon,
  CoreDefinition,
  FileEntry,
  JvmProfile,
  MinecraftServer,
  ServerBackup,
  ServerCore,
  ServerMetricsPoint,
  ServerPermission,
} from '../types';

interface Props {
  serverId: string;
  cores: CoreDefinition[];
  addonsCatalog: CatalogAddon[];
  onBack: () => void;
  onServerChanged: () => void;
  onServerDeleted: () => void;
  notify: (msg: string, type?: 'success' | 'error') => void;
}

type TabId =
  | 'console'
  | 'files'
  | 'core'
  | 'addons'
  | 'backups'
  | 'ports'
  | 'access';

const ALL_PERMISSIONS: { id: ServerPermission; label: string }[] = [
  { id: 'console.read', label: 'Чтение консоли' },
  { id: 'console.send', label: 'Отправка команд' },
  { id: 'power.control', label: 'Питание (Старт/Стоп)' },
  { id: 'files.read', label: 'Просмотр файлов' },
  { id: 'files.write', label: 'Редактирование файлов' },
  { id: 'addons.manage', label: 'Плагины и моды' },
  { id: 'backups.manage', label: 'Резервные копии' },
  { id: 'ports.manage', label: 'Управление портами' },
  { id: 'settings.manage', label: 'Ядро и лимиты' },
];

const SparkAreaChart: React.FC<{
  values: number[];
  max: number;
  stroke: string;
  fill: string;
}> = ({ values, max, stroke, fill }) => {
  const pts = values.length >= 2 ? values : [0, ...(values.length ? values : [0])];
  const w = 240;
  const h = 54;
  const safeMax = Math.max(1, max);
  const coords = pts.map((v, idx) => {
    const x = (idx / (pts.length - 1)) * w;
    const y = h - Math.min(1, Math.max(0, v / safeMax)) * (h - 6) - 3;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const linePath = `M ${coords.join(' L ')}`;
  const areaPath = `${linePath} L ${w},${h} L 0,${h} Z`;

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-14 overflow-visible">
      <path d={areaPath} fill={fill} />
      <path d={linePath} fill="none" stroke={stroke} strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
};

export const ServerView: React.FC<Props> = ({
  serverId,
  cores,
  addonsCatalog,
  onBack,
  onServerChanged,
  onServerDeleted,
  notify,
}) => {
  const [tab, setTab] = useState<TabId>('console');
  const [server, setServer] = useState<MinecraftServer | null>(null);
  const [startupCommand, setStartupCommand] = useState('');
  const [dockerCli, setDockerCli] = useState('');
  const [logs, setLogs] = useState<string[]>([]);
  const [metricsHistory, setMetricsHistory] = useState<ServerMetricsPoint[]>([]);
  const [cmdInput, setCmdInput] = useState('');
  const [cmdHistory, setCmdHistory] = useState<string[]>([]);
  const [cmdHistIdx, setCmdHistIdx] = useState(-1);
  const consoleEndRef = useRef<HTMLDivElement | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  // Файлы
  const [currentDir, setCurrentDir] = useState('');
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [editingFile, setEditingFile] = useState<{ path: string; content: string } | null>(null);
  const [newItemName, setNewItemName] = useState('');

  // Ядро и версии
  const [editCore, setEditCore] = useState<ServerCore>('paper');
  const [editVersion, setEditVersion] = useState('1.21.4');
  const [editJvm, setEditJvm] = useState<JvmProfile>('aikar_g1');
  const [editCustomJvm, setEditCustomJvm] = useState('');

  // Плагины и моды
  const [addonSearch, setAddonSearch] = useState('');

  // Бэкапы
  const [backups, setBackups] = useState<ServerBackup[]>([]);
  const [schedule, setSchedule] = useState<BackupSchedule>({
    enabled: true,
    intervalHours: 12,
    keepCount: 5,
  });
  const [backupName, setBackupName] = useState('');
  const [backupBusy, setBackupBusy] = useState(false);

  // Порты
  const [newPortNumber, setNewPortNumber] = useState('');
  const [newPortProtocol, setNewPortProtocol] = useState<'tcp+udp' | 'tcp' | 'udp'>('udp');
  const [newPortNotes, setNewPortNotes] = useState('Simple Voice Chat / Geyser');

  // Суб-пользователи и лимиты
  const [subEmail, setSubEmail] = useState('');
  const [subPerms, setSubPerms] = useState<ServerPermission[]>([
    'console.read',
    'console.send',
    'power.control',
    'files.read',
  ]);
  const [limitCpu, setLimitCpu] = useState(400);
  const [limitRam, setLimitRam] = useState(12288);
  const [limitDisk, setLimitDisk] = useState(80);

  const loadServerDetail = async () => {
    try {
      const res = await apiFetch<{
        server: MinecraftServer;
        startupCommand: string;
        dockerSpec: { cliCommand: string };
        logs: string[];
        metricsHistory: ServerMetricsPoint[];
      }>(`/api/servers/${serverId}`);
      setServer(res.server);
      setStartupCommand(res.startupCommand);
      setDockerCli(res.dockerSpec.cliCommand);
      setLogs(res.logs);
      setMetricsHistory(res.metricsHistory);

      setEditCore(res.server.core);
      setEditVersion(res.server.mcVersion);
      setEditJvm(res.server.jvmProfile);
      setEditCustomJvm(res.server.customJvmArgs);
      setLimitCpu(res.server.limits.cpuPercent);
      setLimitRam(res.server.limits.ramMb);
      setLimitDisk(res.server.limits.diskGb);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  useEffect(() => {
    void loadServerDetail();
  }, [serverId]);

  // WebSocket живой консоли и метрик + автоматический HTTP-фоллбэк для Serverless (Vercel)
  useEffect(() => {
    let wsConnected = false;
    let pollTimer: number | null = null;
    const ws = new WebSocket(buildWsUrl());
    wsRef.current = ws;

    const startPollingFallback = () => {
      if (pollTimer !== null) return;
      pollTimer = window.setInterval(() => {
        void loadServerDetail();
      }, 2500);
    };

    ws.onopen = () => {
      wsConnected = true;
      ws.send(JSON.stringify({ type: 'subscribe', serverId }));
    };

    ws.onerror = () => {
      if (!wsConnected) startPollingFallback();
    };

    ws.onclose = () => {
      startPollingFallback();
    };

    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(String(ev.data)) as {
          type: string;
          serverId?: string;
          line?: string;
          status?: MinecraftServer['status'];
          logs?: string[];
          metricsHistory?: ServerMetricsPoint[];
          servers?: {
            serverId: string;
            status: MinecraftServer['status'];
            metrics: ServerMetricsPoint;
          }[];
        };

        if (msg.type === 'init' && msg.serverId === serverId) {
          if (msg.logs) setLogs(msg.logs);
          if (msg.metricsHistory) setMetricsHistory(msg.metricsHistory);
        } else if (msg.type === 'console' && msg.serverId === serverId && msg.line) {
          setLogs((prev) => [...prev.slice(-349), msg.line!]);
        } else if (msg.type === 'status' && msg.serverId === serverId && msg.status) {
          setServer((prev) => (prev ? { ...prev, status: msg.status! } : prev));
          onServerChanged();
        } else if (msg.type === 'metrics' && Array.isArray(msg.servers)) {
          const found = msg.servers.find((s) => s.serverId === serverId);
          if (found && found.metrics) {
            setServer((prev) =>
              prev ? { ...prev, status: found.status, metrics: found.metrics } : prev
            );
            setMetricsHistory((prev) => [...prev.slice(-39), found.metrics]);
          }
        }
      } catch {
        // ignore
      }
    };

    return () => {
      if (pollTimer !== null) window.clearInterval(pollTimer);
      ws.close();
    };
  }, [serverId]);

  useEffect(() => {
    if (tab === 'console') {
      consoleEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, tab]);

  const loadFiles = async (dir = currentDir) => {
    try {
      const res = await apiFetch<{ path: string; entries: FileEntry[] }>(
        `/api/servers/${serverId}/files?path=${encodeURIComponent(dir)}`
      );
      setCurrentDir(res.path);
      setFiles(res.entries);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const loadBackups = async () => {
    try {
      const res = await apiFetch<{ backups: ServerBackup[]; schedule: BackupSchedule }>(
        `/api/servers/${serverId}/backups`
      );
      setBackups(res.backups);
      setSchedule(res.schedule);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  useEffect(() => {
    if (tab === 'files') void loadFiles('');
    if (tab === 'backups') void loadBackups();
  }, [tab, serverId]);

  const handlePower = async (action: 'start' | 'stop' | 'restart' | 'kill') => {
    try {
      const res = await apiFetch<{ server: MinecraftServer; logs?: string[] }>(
        `/api/servers/${serverId}/power`,
        {
          method: 'POST',
          body: JSON.stringify({ action }),
        }
      );
      setServer(res.server);
      if (res.logs) setLogs(res.logs);
      onServerChanged();
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const sendCommand = async (raw?: string) => {
    const command = (raw ?? cmdInput).trim();
    if (!command) return;
    if (raw === undefined) {
      setCmdHistory((prev) => [command, ...prev.slice(0, 29)]);
      setCmdHistIdx(-1);
      setCmdInput('');
    }

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: 'command', serverId, command }));
    } else {
      try {
        const res = await apiFetch<{ server?: MinecraftServer; logs?: string[] }>(
          `/api/servers/${serverId}/command`,
          {
            method: 'POST',
            body: JSON.stringify({ command }),
          }
        );
        if (res.logs) setLogs(res.logs);
        if (res.server) setServer(res.server);
      } catch (err) {
        notify((err as Error).message, 'error');
      }
    }
  };

  const openFileForEdit = async (entry: FileEntry) => {
    if (entry.isDirectory) {
      setEditingFile(null);
      void loadFiles(entry.path);
      return;
    }
    try {
      const res = await apiFetch<{ path: string; content: string }>(
        `/api/servers/${serverId}/files/content?path=${encodeURIComponent(entry.path)}`
      );
      setEditingFile(res);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const saveEditedFile = async () => {
    if (!editingFile) return;
    try {
      await apiFetch(`/api/servers/${serverId}/files/content`, {
        method: 'PUT',
        body: JSON.stringify(editingFile),
      });
      notify(`Файл ${editingFile.path} сохранён на NVMe-диск.`);
      void loadFiles(currentDir);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const createNewFileOrFolder = async (isFolder: boolean) => {
    const clean = newItemName.trim();
    if (!clean) return;
    const targetPath = currentDir ? `${currentDir}/${clean}` : clean;
    try {
      if (isFolder) {
        await apiFetch(`/api/servers/${serverId}/files/mkdir`, {
          method: 'POST',
          body: JSON.stringify({ path: targetPath }),
        });
      } else {
        await apiFetch(`/api/servers/${serverId}/files/content`, {
          method: 'PUT',
          body: JSON.stringify({ path: targetPath, content: '' }),
        });
      }
      setNewItemName('');
      void loadFiles(currentDir);
      notify(isFolder ? `Папка ${clean} создана` : `Файл ${clean} создан`);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const deleteFileEntry = async (entry: FileEntry) => {
    try {
      await apiFetch(
        `/api/servers/${serverId}/files?path=${encodeURIComponent(entry.path)}`,
        { method: 'DELETE' }
      );
      if (editingFile?.path === entry.path) setEditingFile(null);
      void loadFiles(currentDir);
      notify(`Удалено: ${entry.name}`);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleSaveCore = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch<{
        server: MinecraftServer;
        startupCommand: string;
        dockerSpec: { cliCommand: string };
      }>(`/api/servers/${serverId}/core`, {
        method: 'POST',
        body: JSON.stringify({
          core: editCore,
          mcVersion: editVersion,
          jvmProfile: editJvm,
          customJvmArgs: editCustomJvm,
        }),
      });
      setServer(res.server);
      setStartupCommand(res.startupCommand);
      setDockerCli(res.dockerSpec.cliCommand);
      onServerChanged();
      notify(
        `Установлено ядро ${res.server.core.toUpperCase()} ${res.server.mcVersion} (${res.server.coreBuild})`
      );
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleInstallAddon = async (catalogId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/addons/install`,
        {
          method: 'POST',
          body: JSON.stringify({ catalogId }),
        }
      );
      setServer(res.server);
      notify('Расширение установлено в директорию сервера!');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleToggleAddon = async (addonId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/addons/${addonId}/toggle`,
        { method: 'POST' }
      );
      setServer(res.server);
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleRemoveAddon = async (addonId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/addons/${addonId}`,
        { method: 'DELETE' }
      );
      setServer(res.server);
      notify('Расширение удалено.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleCreateBackup = async (e: React.FormEvent) => {
    e.preventDefault();
    setBackupBusy(true);
    try {
      await apiFetch(`/api/servers/${serverId}/backups`, {
        method: 'POST',
        body: JSON.stringify({ name: backupName }),
      });
      setBackupName('');
      await loadBackups();
      notify('Резервная копия .tar.gz создана и проверена (SHA-256).');
    } catch (err) {
      notify((err as Error).message, 'error');
    } finally {
      setBackupBusy(false);
    }
  };

  const handleSaveSchedule = async () => {
    try {
      const res = await apiFetch<{ schedule: BackupSchedule }>(
        `/api/servers/${serverId}/backups/schedule`,
        {
          method: 'PATCH',
          body: JSON.stringify(schedule),
        }
      );
      setSchedule(res.schedule);
      notify('Расписание автоматических резервных копий сохранено.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleToggleLockBackup = async (backupId: string) => {
    try {
      await apiFetch(`/api/servers/${serverId}/backups/${backupId}/lock`, {
        method: 'POST',
      });
      await loadBackups();
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleRestoreBackup = async (backupId: string) => {
    try {
      await apiFetch(`/api/servers/${serverId}/backups/${backupId}/restore`, {
        method: 'POST',
      });
      notify('Файлы сервера успешно восстановлены из резервной копии!');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleDeleteBackup = async (backupId: string) => {
    try {
      await apiFetch(`/api/servers/${serverId}/backups/${backupId}`, {
        method: 'DELETE',
      });
      await loadBackups();
      notify('Архив резервной копии удалён.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleAllocatePort = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/ports`,
        {
          method: 'POST',
          body: JSON.stringify({
            port: newPortNumber ? Number(newPortNumber) : undefined,
            protocol: newPortProtocol,
            notes: newPortNotes,
          }),
        }
      );
      setServer(res.server);
      setNewPortNumber('');
      notify('Дополнительный сетевой порт выделен контейнеру!');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleMakePrimaryPort = async (allocId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/ports/${allocId}/primary`,
        { method: 'POST' }
      );
      setServer(res.server);
      onServerChanged();
      notify('Основной порт обновлён (server.properties синхронизирован).');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleReleasePort = async (allocId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/ports/${allocId}`,
        { method: 'DELETE' }
      );
      setServer(res.server);
      notify('Порт возвращён в свободный пул ноды.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleAddSubuser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/subusers`,
        {
          method: 'POST',
          body: JSON.stringify({ email: subEmail, permissions: subPerms }),
        }
      );
      setServer(res.server);
      setSubEmail('');
      notify('Совладелец добавлен к управлению сервером.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleRemoveSubuser = async (subId: string) => {
    try {
      const res = await apiFetch<{ server: MinecraftServer }>(
        `/api/servers/${serverId}/subusers/${subId}`,
        { method: 'DELETE' }
      );
      setServer(res.server);
      notify('Доступ пользователя отозван.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleSaveLimits = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch<{
        server: MinecraftServer;
        startupCommand: string;
        dockerSpec: { cliCommand: string };
      }>(`/api/servers/${serverId}/limits`, {
        method: 'PATCH',
        body: JSON.stringify({
          cpuPercent: limitCpu,
          ramMb: limitRam,
          diskGb: limitDisk,
        }),
      });
      setServer(res.server);
      setStartupCommand(res.startupCommand);
      setDockerCli(res.dockerSpec.cliCommand);
      onServerChanged();
      notify('Лимиты cgroups v2 и параметры JVM контейнера обновлены.');
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  const handleDeleteServer = async () => {
    if (!window.confirm(`Удалить сервер «${server?.name}» и все его данные безвозвратно?`)) {
      return;
    }
    try {
      await apiFetch(`/api/servers/${serverId}`, { method: 'DELETE' });
      notify('Сервер и контейнер удалены.');
      onServerDeleted();
    } catch (err) {
      notify((err as Error).message, 'error');
    }
  };

  if (!server) {
    return (
      <div className="p-10 text-center text-slate-400 text-sm">
        Загрузка телеметрии контейнера...
      </div>
    );
  }

  const m = server.metrics || {
    cpuPercent: 0,
    ramUsedMb: 0,
    diskUsedMb: 0,
    tps: 0,
    mspt: 0,
    playersOnline: 0,
    playersMax: 100,
    netRxKbps: 0,
    netTxKbps: 0,
  };

  const selectedCoreObj = cores.find((c) => c.id === editCore) || cores[0];
  const compatibleCatalog = addonsCatalog.filter(
    (item) =>
      item.supportedCores.includes(server.core) &&
      (item.name.toLowerCase().includes(addonSearch.toLowerCase()) ||
        item.summary.toLowerCase().includes(addonSearch.toLowerCase()))
  );

  const statusBadge = {
    running: { label: 'ОНЛАЙН', cls: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40' },
    starting: { label: 'ЗАПУСК...', cls: 'bg-amber-500/15 text-amber-300 border-amber-500/40' },
    restarting: { label: 'ПЕРЕЗАГРУЗКА', cls: 'bg-amber-500/15 text-amber-300 border-amber-500/40' },
    stopping: { label: 'ОСТАНОВКА', cls: 'bg-rose-500/15 text-rose-300 border-rose-500/40' },
    offline: { label: 'ОСТАНОВЛЕН', cls: 'bg-slate-800 text-slate-400 border-slate-700' },
  }[server.status];

  return (
    <div className="space-y-5">
      {/* Верхняя шапка сервера и кнопки питания */}
      <div className="p-5 rounded-2xl bg-[#0d1422] border border-slate-800 flex flex-col xl:flex-row xl:items-center justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <button
              onClick={onBack}
              className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-400 hover:text-white cursor-pointer"
              title="К списку серверов"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <h1 className="text-xl font-bold text-white">{server.name}</h1>
            <span
              className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold border ${statusBadge.cls}`}
            >
              {statusBadge.label}
            </span>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-cyan-500/10 text-cyan-300 border border-cyan-500/30 uppercase">
              {server.core} {server.mcVersion}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400">
            <button
              type="button"
              onClick={() => {
                navigator.clipboard.writeText(`${server.primaryAddress}:${server.primaryPort}`);
                notify('IP:Порт скопирован в буфер обмена!');
              }}
              className="inline-flex items-center gap-1.5 font-mono text-emerald-400 hover:underline cursor-pointer"
            >
              <Copy className="w-3.5 h-3.5" /> {server.primaryAddress}:{server.primaryPort}
            </button>
            <span>Нода: {server.nodeName}</span>
            <span>Контейнер: {server.dockerContainerId}</span>
            <span>
              Игроки: <strong className="text-white">{m.playersOnline}</strong> / {m.playersMax}
            </span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={server.status === 'running' || server.status === 'starting'}
            onClick={() => handlePower('start')}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-35 text-slate-950 font-bold text-xs transition cursor-pointer"
          >
            <Play className="w-3.5 h-3.5" /> Запустить
          </button>
          <button
            type="button"
            disabled={server.status === 'offline'}
            onClick={() => handlePower('restart')}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-amber-500/20 hover:bg-amber-500/30 disabled:opacity-35 border border-amber-500/40 text-amber-200 font-semibold text-xs transition cursor-pointer"
          >
            <RotateCw className="w-3.5 h-3.5" /> Перезагрузить
          </button>
          <button
            type="button"
            disabled={server.status === 'offline'}
            onClick={() => handlePower('stop')}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 disabled:opacity-35 border border-rose-500/40 text-rose-200 font-semibold text-xs transition cursor-pointer"
          >
            <Square className="w-3.5 h-3.5" /> Остановить
          </button>
          <button
            type="button"
            disabled={server.status === 'offline'}
            onClick={() => handlePower('kill')}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-slate-900 hover:bg-rose-950 disabled:opacity-35 border border-slate-800 text-slate-300 text-xs transition cursor-pointer"
            title="Принудительный SIGKILL"
          >
            <Skull className="w-3.5 h-3.5 text-rose-400" /> Kill
          </button>
        </div>
      </div>

      {/* Навигация по 7 функциональным разделам сервера */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        {[
          { id: 'console', label: 'Консоль и Мониторинг', icon: Terminal },
          { id: 'files', label: 'Файлы', icon: FolderOpen },
          { id: 'core', label: 'Ядро и Версии', icon: Layers },
          {
            id: 'addons',
            label:
              server.core === 'paper' || server.core === 'purpur'
                ? `Плагины (${server.installedAddons.length})`
                : `Моды (${server.installedAddons.length})`,
            icon: Puzzle,
          },
          { id: 'backups', label: 'Авто-бэкапы', icon: Archive },
          { id: 'ports', label: `Порты (${server.allocations.length})`, icon: Network },
          { id: 'access', label: 'Доступ и Лимиты', icon: Users },
        ].map((item) => {
          const Icon = item.icon;
          const active = tab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setTab(item.id as TabId)}
              className={`inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold whitespace-nowrap border transition cursor-pointer ${
                active
                  ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-300'
                  : 'bg-[#0d1422] border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              <Icon className="w-4 h-4" />
              {item.label}
            </button>
          );
        })}
      </div>

      {/* ВКЛАДКА 1: КОНСОЛЬ И МОНИТОРИНГ CPU / RAM / NVMe / TPS */}
      {tab === 'console' && (
        <div className="grid xl:grid-cols-3 gap-5">
          <div className="xl:col-span-2 flex flex-col rounded-2xl bg-[#090e18] border border-slate-800 overflow-hidden">
            <div className="px-4 py-2.5 bg-slate-950 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-xs font-mono text-slate-400">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                root@{server.shortId}:~# Interactive WebSocket Stream
              </div>
              <div className="flex flex-wrap gap-1.5">
                {['tps', 'spark health', 'list', 'save-all', 'plugins', 'gc'].map((macro) => (
                  <button
                    key={macro}
                    type="button"
                    onClick={() => sendCommand(macro)}
                    className="px-2 py-1 rounded-md bg-slate-900 hover:bg-slate-800 border border-slate-800 text-[11px] font-mono text-slate-300 cursor-pointer"
                  >
                    /{macro}
                  </button>
                ))}
              </div>
            </div>

            <div className="h-[410px] overflow-y-auto p-4 font-mono text-xs space-y-1 leading-relaxed">
              {logs.map((line, i) => {
                let cls = 'text-slate-300';
                if (line.includes('WARN')) cls = 'text-amber-300';
                else if (line.includes('ERROR') || line.includes('SIGKILL')) cls = 'text-rose-400';
                else if (line.includes('Done (') || line.includes('TPS')) cls = 'text-emerald-300';
                else if (line.includes('[WinHostDaemon]') || line.includes('[BackupWorker]'))
                  cls = 'text-cyan-300';
                else if (line.includes('> ')) cls = 'text-white font-bold';
                return (
                  <div key={i} className={`break-words ${cls}`}>
                    {line}
                  </div>
                );
              })}
              <div ref={consoleEndRef} />
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                void sendCommand();
              }}
              className="p-3 bg-slate-950 border-t border-slate-800 flex items-center gap-2"
            >
              <span className="font-mono text-emerald-400 text-sm pl-1">&gt;</span>
              <input
                type="text"
                value={cmdInput}
                onChange={(e) => setCmdInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowUp' && cmdHistory.length > 0) {
                    e.preventDefault();
                    const nextIdx = Math.min(cmdHistory.length - 1, cmdHistIdx + 1);
                    setCmdHistIdx(nextIdx);
                    setCmdInput(cmdHistory[nextIdx]);
                  } else if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    if (cmdHistIdx <= 0) {
                      setCmdHistIdx(-1);
                      setCmdInput('');
                    } else {
                      setCmdHistIdx(cmdHistIdx - 1);
                      setCmdInput(cmdHistory[cmdHistIdx - 1]);
                    }
                  }
                }}
                placeholder="Введите команду консоли (например: tps, spark health, whitelist add Steve, op Alex, say Привет)..."
                className="flex-1 bg-transparent text-xs font-mono text-white focus:outline-none"
              />
              <button
                type="submit"
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                <Send className="w-3.5 h-3.5" /> Отправить
              </button>
            </form>
          </div>

          {/* Живые графики мониторинга CPU / DDR5 RAM / NVMe / TPS */}
          <div className="space-y-3.5">
            <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
              <div className="flex items-center justify-between text-xs mb-1">
                <span className="flex items-center gap-1.5 text-slate-400 font-medium">
                  <Cpu className="w-4 h-4 text-emerald-400" /> Нагрузка CPU (Zen 5)
                </span>
                <span className="font-mono font-bold text-white">
                  {m.cpuPercent.toFixed(1)}% / {server.limits.cpuPercent}%
                </span>
              </div>
              <SparkAreaChart
                values={metricsHistory.map((p) => p.cpuPercent)}
                max={server.limits.cpuPercent}
                stroke="#10b981"
                fill="rgba(16, 185, 129, 0.14)"
              />
            </div>

            <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
              <div className="flex items-center justify-between text-xs mb-1">
                <span className="flex items-center gap-1.5 text-slate-400 font-medium">
                  <MemoryStick className="w-4 h-4 text-cyan-400" /> Память DDR5 ECC
                </span>
                <span className="font-mono font-bold text-white">
                  {(m.ramUsedMb / 1024).toFixed(2)} / {(server.limits.ramMb / 1024).toFixed(0)} ГБ
                </span>
              </div>
              <SparkAreaChart
                values={metricsHistory.map((p) => p.ramUsedMb)}
                max={server.limits.ramMb}
                stroke="#06b6d4"
                fill="rgba(6, 182, 212, 0.14)"
              />
            </div>

            <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
              <div className="flex items-center justify-between text-xs mb-2">
                <span className="flex items-center gap-1.5 text-slate-400 font-medium">
                  <HardDrive className="w-4 h-4 text-indigo-400" /> Диск NVMe PCIe 5.0
                </span>
                <span className="font-mono font-bold text-white">
                  {(m.diskUsedMb / 1024).toFixed(2)} / {server.limits.diskGb} ГБ
                </span>
              </div>
              <div className="w-full h-2.5 rounded-full bg-slate-900 overflow-hidden">
                <div
                  className="h-full bg-indigo-500 rounded-full transition-all"
                  style={{
                    width: `${Math.min(
                      100,
                      (m.diskUsedMb / (server.limits.diskGb * 1024)) * 100
                    )}%`,
                  }}
                />
              </div>
              <div className="flex justify-between text-[11px] text-slate-500 mt-2">
                <span>XFS pquota активна</span>
                <span>
                  Сеть: ↓{m.netRxKbps} Кбит/с • ↑{m.netTxKbps} Кбит/с
                </span>
              </div>
            </div>

            <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
              <div className="flex items-center justify-between text-xs mb-1">
                <span className="flex items-center gap-1.5 text-slate-400 font-medium">
                  <Activity className="w-4 h-4 text-amber-400" /> TPS и время тика (MSPT)
                </span>
                <span className="font-mono font-bold text-emerald-400">
                  {server.status === 'running'
                    ? `${m.tps.toFixed(1)} TPS (${m.mspt} мс)`
                    : 'ОФФЛАЙН'}
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                Бюджет тика Minecraft: ≤ 50.0 мс. Профиль JVM:{' '}
                <span className="text-slate-200 font-mono">{server.jvmProfile}</span>
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ВКЛАДКА 2: УПРАВЛЕНИЕ ФАЙЛАМИ */}
      {tab === 'files' && (
        <div className="grid lg:grid-cols-12 gap-5">
          <div className="lg:col-span-5 p-5 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <div className="flex items-center justify-between gap-2">
              <div className="text-xs font-mono text-emerald-400 truncate">
                /data/{currentDir}
              </div>
              {currentDir && (
                <button
                  type="button"
                  onClick={() => {
                    const parent = currentDir.split('/').slice(0, -1).join('/');
                    setEditingFile(null);
                    void loadFiles(parent);
                  }}
                  className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-xs text-slate-300 cursor-pointer"
                >
                  ↑ Наверх
                </button>
              )}
            </div>

            <div className="flex gap-1.5">
              <input
                type="text"
                value={newItemName}
                onChange={(e) => setNewItemName(e.target.value)}
                placeholder="Имя нового файла или папки..."
                className="flex-1 px-3 py-1.5 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
              <button
                type="button"
                onClick={() => createNewFileOrFolder(false)}
                className="px-2.5 py-1.5 rounded-xl bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-xs font-semibold cursor-pointer"
              >
                + Файл
              </button>
              <button
                type="button"
                onClick={() => createNewFileOrFolder(true)}
                className="px-2.5 py-1.5 rounded-xl bg-slate-800 text-slate-200 text-xs font-semibold cursor-pointer"
              >
                + Папка
              </button>
            </div>

            <div className="divide-y divide-slate-800/80 max-h-[420px] overflow-y-auto">
              {files.map((f) => (
                <div
                  key={f.path}
                  className={`py-2.5 px-2 flex items-center justify-between gap-2 rounded-lg hover:bg-slate-900/70 ${
                    editingFile?.path === f.path ? 'bg-emerald-500/10' : ''
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => openFileForEdit(f)}
                    className="flex items-center gap-2.5 text-left min-w-0 flex-1 cursor-pointer"
                  >
                    {f.isDirectory ? (
                      <Folder className="w-4 h-4 text-amber-400 shrink-0" />
                    ) : (
                      <FileText className="w-4 h-4 text-cyan-400 shrink-0" />
                    )}
                    <div className="truncate">
                      <div className="text-xs font-medium text-slate-200 truncate">{f.name}</div>
                      {!f.isDirectory && (
                        <div className="text-[10px] text-slate-500">
                          {(f.sizeBytes / 1024).toFixed(1)} КБ
                        </div>
                      )}
                    </div>
                  </button>

                  <div className="flex items-center gap-1">
                    {!f.isDirectory && (
                      <a
                        href={buildDownloadUrl(
                          `/api/servers/${serverId}/files/content?path=${encodeURIComponent(
                            f.path
                          )}&download=1`
                        )}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
                        title="Скачать"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    )}
                    <button
                      type="button"
                      onClick={() => deleteFileEntry(f)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 cursor-pointer"
                      title="Удалить"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="lg:col-span-7 p-5 rounded-2xl bg-[#0d1422] border border-slate-800 flex flex-col">
            {editingFile ? (
              <>
                <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-800">
                  <span className="font-mono text-xs text-cyan-300">/data/{editingFile.path}</span>
                  <button
                    type="button"
                    onClick={saveEditedFile}
                    className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
                  >
                    <Save className="w-3.5 h-3.5" /> Сохранить файл
                  </button>
                </div>
                <textarea
                  value={editingFile.content}
                  onChange={(e) =>
                    setEditingFile({ ...editingFile, content: e.target.value })
                  }
                  spellCheck={false}
                  className="flex-1 min-h-[390px] w-full p-4 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs text-slate-200 leading-relaxed focus:outline-none focus:border-emerald-500"
                />
              </>
            ) : (
              <div className="flex-1 min-h-[390px] flex flex-col items-center justify-center text-center text-slate-500 text-xs p-6">
                <FileText className="w-10 h-10 mb-3 text-slate-700" />
                Выберите файл слева (например, <code className="text-slate-300">server.properties</code>,{' '}
                <code className="text-slate-300">purpur.yml</code>,{' '}
                <code className="text-slate-300">start.sh</code> или{' '}
                <code className="text-slate-300">docker-container.json</code>) для редактирования.
              </div>
            )}
          </div>
        </div>
      )}

      {/* ВКЛАДКА 3: ЯДРА И ВЕРСИИ MINECRAFT + DOCKER МАНИФЕСТ */}
      {tab === 'core' && (
        <div className="grid lg:grid-cols-2 gap-5">
          <form
            onSubmit={handleSaveCore}
            className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4"
          >
            <h2 className="text-base font-bold text-white">
              Управление ядром и версией Minecraft
            </h2>
            <p className="text-xs text-slate-400">
              Переключайте Paper, Purpur, Fabric, Forge и NeoForge в 1 клик с автоподбором образа Java Temurin и пересборкой скрипта запуска.
            </p>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              {cores.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => {
                    setEditCore(c.id);
                    if (!c.supportedVersions.some((v) => v.mcVersion === editVersion)) {
                      setEditVersion(c.supportedVersions[0].mcVersion);
                    }
                  }}
                  className={`py-2.5 px-2 rounded-xl border text-xs font-bold transition cursor-pointer ${
                    editCore === c.id
                      ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300'
                      : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-white'
                  }`}
                >
                  {c.name}
                </button>
              ))}
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Версия Minecraft и сборка
              </label>
              <select
                value={editVersion}
                onChange={(e) => setEditVersion(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white"
              >
                {selectedCoreObj.supportedVersions.map((v) => (
                  <option key={v.mcVersion} value={v.mcVersion}>
                    Minecraft {v.mcVersion} — {v.latestBuild} (Java {v.recommendedJava} LTS)
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Профиль флагов JVM (Garbage Collector)
              </label>
              <select
                value={editJvm}
                onChange={(e) => setEditJvm(e.target.value as JvmProfile)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white"
              >
                <option value="aikar_g1">Aikar&apos;s Flags (G1GC + AVX-512 Vector API)</option>
                <option value="zgc_gen">Java 21 Generational ZGC (паузы &lt; 1 мс, 16+ ГБ)</option>
                <option value="custom">Пользовательские аргументы JVM</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Дополнительные аргументы JVM (необязательно)
              </label>
              <input
                type="text"
                value={editCustomJvm}
                onChange={(e) => setEditCustomJvm(e.target.value)}
                placeholder="-Dpaper.playerconnection.keepalive=60"
                className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs text-white"
              />
            </div>

            <button
              type="submit"
              className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-sm cursor-pointer"
            >
              Установить выбранное ядро и версию
            </button>
          </form>

          <div className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <div>
              <h3 className="text-sm font-bold text-white mb-1.5">
                Сгенерированная команда запуска JVM (85% Anti-OOM Heap)
              </h3>
              <pre className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 text-[11px] font-mono text-emerald-300 overflow-x-auto whitespace-pre-wrap">
                {startupCommand}
              </pre>
            </div>

            <div>
              <h3 className="text-sm font-bold text-white mb-1.5">
                Изолированный Docker-контейнер (cgroups v2 + XFS pquota)
              </h3>
              <pre className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 text-[11px] font-mono text-cyan-300 overflow-x-auto whitespace-pre-wrap">
                {dockerCli}
              </pre>
            </div>
          </div>
        </div>
      )}

      {/* ВКЛАДКА 4: УСТАНОВКА ПЛАГИНОВ И МОДОВ */}
      {tab === 'addons' && (
        <div className="grid lg:grid-cols-12 gap-5">
          <div className="lg:col-span-7 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <h2 className="text-base font-bold text-white">
                  Каталог для ядра {server.core.toUpperCase()} (
                  {server.core === 'paper' || server.core === 'purpur' ? 'Плагины' : 'Моды'})
                </h2>
                <p className="text-xs text-slate-400">
                  Автоматическая проверка совместимости и установка в папку{' '}
                  <code>
                    /{server.core === 'paper' || server.core === 'purpur' ? 'plugins' : 'mods'}
                  </code>
                </p>
              </div>
              <input
                type="text"
                value={addonSearch}
                onChange={(e) => setAddonSearch(e.target.value)}
                placeholder="Поиск плагинов и модов..."
                className="px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
            </div>

            <div className="space-y-2.5 max-h-[460px] overflow-y-auto pr-1">
              {compatibleCatalog.map((item) => {
                const already = server.installedAddons.some((a) => a.catalogId === item.id);
                return (
                  <div
                    key={item.id}
                    className="p-3.5 rounded-xl bg-slate-950/80 border border-slate-800 flex items-start justify-between gap-3"
                  >
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-bold text-white">{item.name}</span>
                        <span className="px-2 py-0.5 rounded bg-slate-800 text-[10px] font-mono text-slate-300">
                          v{item.version}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-emerald-500/10 text-[10px] text-emerald-300">
                          {item.category}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400">{item.summary}</p>
                      <div className="text-[11px] text-slate-500">
                        Автор: {item.author} • Скачиваний: {item.downloads}
                      </div>
                    </div>

                    {already ? (
                      <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-500/10 text-emerald-400 text-xs font-semibold shrink-0">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Установлен
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleInstallAddon(item.id)}
                        className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs font-bold shrink-0 cursor-pointer"
                      >
                        <Plus className="w-3.5 h-3.5" /> Установить
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <div className="lg:col-span-5 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <h2 className="text-base font-bold text-white">
              Установленные на сервере ({server.installedAddons.length})
            </h2>
            {server.installedAddons.length === 0 ? (
              <div className="text-xs text-slate-500 py-8 text-center">
                Пока не установлено ни одного расширения. Выберите из каталога слева.
              </div>
            ) : (
              <div className="space-y-2.5">
                {server.installedAddons.map((inst) => (
                  <div
                    key={inst.id}
                    className="p-3 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between gap-2"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-white truncate">{inst.name}</div>
                      <div className="text-[11px] font-mono text-slate-500 truncate">
                        {inst.fileName}
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => handleToggleAddon(inst.id)}
                        className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold cursor-pointer ${
                          inst.enabled
                            ? 'bg-emerald-500/15 text-emerald-300'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        {inst.enabled ? 'Включён' : 'Отключён'}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleRemoveAddon(inst.id)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-900 cursor-pointer"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ВКЛАДКА 5: АВТОМАТИЧЕСКИЕ РЕЗЕРВНЫЕ КОПИИ */}
      {tab === 'backups' && (
        <div className="grid lg:grid-cols-12 gap-5">
          <div className="lg:col-span-5 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-5">
            <div>
              <h2 className="text-base font-bold text-white mb-1">
                Создать снимок мира (.tar.gz + SHA-256)
              </h2>
              <p className="text-xs text-slate-400">
                Выполняет атомарный сброс чанков <code>save-all flush</code> и упаковывает все файлы сервера без остановки игры.
              </p>
            </div>

            <form onSubmit={handleCreateBackup} className="space-y-3">
              <input
                type="text"
                value={backupName}
                onChange={(e) => setBackupName(e.target.value)}
                placeholder="Метка бэкапа (напр. Перед обновлением спавна)"
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
              <button
                type="submit"
                disabled={backupBusy}
                className="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                {backupBusy ? 'Архивация файлов...' : 'Создать резервную копию сейчас'}
              </button>
            </form>

            <div className="pt-4 border-t border-slate-800 space-y-3">
              <h3 className="text-sm font-bold text-white">Расписание автоматических бэкапов</h3>
              <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={schedule.enabled}
                  onChange={(e) => setSchedule({ ...schedule, enabled: e.target.checked })}
                  className="accent-emerald-500"
                />
                Включить регулярное резервное копирование
              </label>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Интервал</label>
                  <select
                    value={schedule.intervalHours}
                    onChange={(e) =>
                      setSchedule({
                        ...schedule,
                        intervalHours: Number(e.target.value) as 6 | 12 | 24,
                      })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                  >
                    <option value={6}>Каждые 6 часов</option>
                    <option value={12}>Каждые 12 часов</option>
                    <option value={24}>Каждые 24 часа</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">
                    Хранить копий (ротация)
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={server.limits.backupsLimit}
                    value={schedule.keepCount}
                    onChange={(e) =>
                      setSchedule({ ...schedule, keepCount: Number(e.target.value) })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
                  />
                </div>
              </div>

              <button
                type="button"
                onClick={handleSaveSchedule}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-white cursor-pointer"
              >
                Сохранить расписание
              </button>
            </div>
          </div>

          <div className="lg:col-span-7 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-white">
                Хранилище копий ({backups.length} / {server.limits.backupsLimit})
              </h2>
            </div>

            <div className="space-y-3">
              {backups.map((b) => (
                <div
                  key={b.id}
                  className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white">{b.name}</span>
                      {b.automatic && (
                        <span className="px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-300 text-[10px]">
                          Авто
                        </span>
                      )}
                      {b.locked && (
                        <span className="px-2 py-0.5 rounded bg-amber-500/15 text-amber-300 text-[10px]">
                          Защищён
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] font-mono text-slate-400 truncate">
                      {b.fileName} • {(b.sizeBytes / 1024).toFixed(1)} КБ • SHA256:{' '}
                      {b.sha256.slice(0, 16)}…
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleRestoreBackup(b.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 text-xs font-semibold cursor-pointer"
                      title="Восстановить"
                    >
                      <RotateCcw className="w-3.5 h-3.5" /> Восстановить
                    </button>
                    <a
                      href={buildDownloadUrl(
                        `/api/servers/${serverId}/backups/${b.id}/download`
                      )}
                      className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300"
                      title="Скачать .tar.gz"
                    >
                      <Download className="w-4 h-4" />
                    </a>
                    <button
                      type="button"
                      onClick={() => handleToggleLockBackup(b.id)}
                      className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-amber-300 cursor-pointer"
                      title={b.locked ? 'Снять защиту' : 'Защитить от удаления'}
                    >
                      {b.locked ? <Lock className="w-4 h-4" /> : <Unlock className="w-4 h-4" />}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDeleteBackup(b.id)}
                      className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-rose-400 cursor-pointer"
                      title="Удалить"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ВКЛАДКА 6: УПРАВЛЕНИЕ ПОРТАМИ */}
      {tab === 'ports' && (
        <div className="grid lg:grid-cols-12 gap-5">
          <div className="lg:col-span-7 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <h2 className="text-base font-bold text-white">
              Сетевые аллокации ({server.allocations.length} / {server.limits.portsLimit})
            </h2>
            <div className="space-y-2.5">
              {server.allocations.map((a) => (
                <div
                  key={a.id}
                  className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between gap-3"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-bold text-white">
                        {a.ip}:{a.port}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-[10px] uppercase font-mono text-cyan-300">
                        {a.protocol}
                      </span>
                      {a.isPrimary && (
                        <span className="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400 text-[10px] font-bold">
                          Primary (server.properties)
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-slate-400 mt-1">
                      {a.notes || 'Дополнительный порт контейнера'}
                    </div>
                  </div>

                  {!a.isPrimary && (
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleMakePrimaryPort(a.id)}
                        className="px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-xs text-emerald-300 font-semibold cursor-pointer"
                      >
                        Сделать основным
                      </button>
                      <button
                        type="button"
                        onClick={() => handleReleasePort(a.id)}
                        className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-rose-400 cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          <form
            onSubmit={handleAllocatePort}
            className="lg:col-span-5 p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4"
          >
            <h2 className="text-base font-bold text-white">Выделить дополнительный порт</h2>
            <p className="text-xs text-slate-400">
              Используйте дополнительные порты для голосового чата Simple Voice Chat (UDP 24454), кроссплея GeyserMC (UDP 19132) или веб-карты BlueMap.
            </p>
            <div>
              <label className="block text-xs text-slate-300 mb-1">
                Номер порта (оставьте пустым для автовыбора из пула ноды)
              </label>
              <input
                type="number"
                min={1024}
                max={65535}
                value={newPortNumber}
                onChange={(e) => setNewPortNumber(e.target.value)}
                placeholder="Автовыбор (например, 25568)"
                className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-300 mb-1">Протокол</label>
              <select
                value={newPortProtocol}
                onChange={(e) =>
                  setNewPortProtocol(e.target.value as 'tcp+udp' | 'tcp' | 'udp')
                }
                className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              >
                <option value="tcp+udp">TCP + UDP (Универсальный)</option>
                <option value="udp">UDP (Голосовой чат / Geyser)</option>
                <option value="tcp">TCP (RCON / Веб-карта)</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-slate-300 mb-1">Назначение</label>
              <input
                type="text"
                value={newPortNotes}
                onChange={(e) => setNewPortNotes(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
            >
              Выделить порт контейнеру
            </button>
          </form>
        </div>
      )}

      {/* ВКЛАДКА 7: СУБ-ПОЛЬЗОВАТЕЛИ И ЛИМИТЫ РЕСУРСОВ */}
      {tab === 'access' && (
        <div className="grid lg:grid-cols-2 gap-5">
          {/* Совладельцы / Суб-пользователи */}
          <div className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
            <h2 className="text-base font-bold text-white">
              Совместный доступ (Суб-пользователи RBAC)
            </h2>
            <p className="text-xs text-slate-400">
              Выдайте тех-админам или модераторам доступ только к нужным разделам сервера.
            </p>

            <form onSubmit={handleAddSubuser} className="space-y-3">
              <input
                type="email"
                required
                value={subEmail}
                onChange={(e) => setSubEmail(e.target.value)}
                placeholder="Email пользователя (например, dev@project.gg)"
                className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
              />
              <div className="grid grid-cols-2 gap-2">
                {ALL_PERMISSIONS.map((p) => {
                  const checked = subPerms.includes(p.id);
                  return (
                    <label
                      key={p.id}
                      className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer"
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) => {
                          if (e.target.checked) setSubPerms([...subPerms, p.id]);
                          else setSubPerms(subPerms.filter((x) => x !== p.id));
                        }}
                        className="accent-emerald-500"
                      />
                      {p.label}
                    </label>
                  );
                })}
              </div>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                Добавить пользователя
              </button>
            </form>

            <div className="pt-3 border-t border-slate-800 space-y-2">
              {server.subusers.map((s) => (
                <div
                  key={s.id}
                  className="p-3 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between gap-2"
                >
                  <div>
                    <div className="text-xs font-bold text-white">{s.email}</div>
                    <div className="text-[11px] text-slate-400">
                      Прав выдано: {s.permissions.length}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveSubuser(s.id)}
                    className="p-1.5 rounded-lg text-rose-400 hover:bg-slate-900 cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Управление лимитами CPU / DDR5 RAM / NVMe Диска */}
          <div className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 flex flex-col justify-between space-y-6">
            <form onSubmit={handleSaveLimits} className="space-y-4">
              <h2 className="text-base font-bold text-white">
                Разделение ресурсов контейнера (cgroups v2 + XFS)
              </h2>
              <div>
                <label className="block text-xs text-slate-300 mb-1">
                  Лимит CPU (% — 100% = 1 поток Zen 5): <strong>{limitCpu}%</strong>
                </label>
                <input
                  type="range"
                  min={100}
                  max={1600}
                  step={100}
                  value={limitCpu}
                  onChange={(e) => setLimitCpu(Number(e.target.value))}
                  className="w-full accent-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs text-slate-300 mb-1">
                  Лимит памяти DDR5 ECC:{' '}
                  <strong>
                    {(limitRam / 1024).toFixed(0)} ГБ ({limitRam} МБ)
                  </strong>
                </label>
                <input
                  type="range"
                  min={2048}
                  max={65536}
                  step={2048}
                  value={limitRam}
                  onChange={(e) => setLimitRam(Number(e.target.value))}
                  className="w-full accent-cyan-500"
                />
              </div>

              <div>
                <label className="block text-xs text-slate-300 mb-1">
                  Квота дискового пространства NVMe PCIe 5.0: <strong>{limitDisk} ГБ</strong>
                </label>
                <input
                  type="range"
                  min={20}
                  max={500}
                  step={10}
                  value={limitDisk}
                  onChange={(e) => setLimitDisk(Number(e.target.value))}
                  className="w-full accent-indigo-500"
                />
              </div>

              <button
                type="submit"
                className="px-5 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs cursor-pointer"
              >
                Применить новые лимиты ресурсов
              </button>
            </form>

            <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
              <div className="text-xs text-slate-400">
                Полное удаление контейнера, мира и бэкапов
              </div>
              <button
                type="button"
                onClick={handleDeleteServer}
                className="px-4 py-2 rounded-xl bg-rose-500/15 hover:bg-rose-500/25 border border-rose-500/40 text-rose-300 font-bold text-xs cursor-pointer"
              >
                Удалить сервер
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
