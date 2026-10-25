export type UserRole = 'admin' | 'client';

export type ServerCore = 'paper' | 'purpur' | 'fabric' | 'neoforge' | 'forge';

export type ServerStatus = 'offline' | 'starting' | 'running' | 'stopping' | 'restarting';

export type JvmProfile = 'aikar_g1' | 'zgc_gen' | 'custom';

export type ServerPermission =
  | 'console.read'
  | 'console.send'
  | 'power.control'
  | 'files.read'
  | 'files.write'
  | 'addons.manage'
  | 'backups.manage'
  | 'ports.manage'
  | 'settings.manage'
  | 'subusers.manage';

export interface UserPublic {
  id: string;
  email: string;
  username: string;
  role: UserRole;
  totpEnabled: boolean;
  maxServersQuota: number;
  maxRamMbQuota: number;
  suspended: boolean;
  createdAt: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
}

export interface PortAllocation {
  id: string;
  nodeId: string;
  ip: string;
  port: number;
  protocol: 'tcp+udp' | 'tcp' | 'udp';
  serverId: string | null;
  isPrimary: boolean;
  notes: string;
}

export interface ComputeNode {
  id: string;
  name: string;
  location: string;
  fqdn: string;
  publicIp: string;
  cpuModel: string;
  cpuCores: number;
  cpuThreads: number;
  boostClockGhz: number;
  ramTotalMb: number;
  ramType: string;
  ramOverallocatePercent: number;
  diskTotalGb: number;
  diskType: string;
  networkSpeedGbps: number;
  dockerMode: 'docker_socket' | 'remote_wings_tls' | 'local_supervisor';
  status: 'online' | 'maintenance';
  daemonTokenPreview: string;
  portRangeStart: number;
  portRangeEnd: number;
  createdAt: string;
  allocatedRamMb?: number;
  allocatedDiskGb?: number;
  serversCount?: number;
}

export interface InstalledAddon {
  id: string;
  catalogId?: string;
  name: string;
  type: 'plugin' | 'mod';
  version: string;
  fileName: string;
  enabled: boolean;
  sizeBytes: number;
  installedAt: string;
}

export interface ServerSubuser {
  id: string;
  userId: string;
  email: string;
  username: string;
  permissions: ServerPermission[];
  addedAt: string;
}

export interface BackupSchedule {
  enabled: boolean;
  intervalHours: 6 | 12 | 24;
  keepCount: number;
  lastRunAt?: string;
}

export interface ServerBackup {
  id: string;
  serverId: string;
  name: string;
  fileName: string;
  sizeBytes: number;
  sha256: string;
  locked: boolean;
  automatic: boolean;
  createdAt: string;
}

export interface ServerMetricsPoint {
  timestamp: number;
  cpuPercent: number;       // от 0 до cpuLimitPercent (напр. 400%)
  ramUsedMb: number;
  diskUsedMb: number;
  tps: number;              // 0 - 20.0
  mspt: number;             // миллисекунды на тик (бюджет <= 50.0 мс)
  playersOnline: number;
  playersMax: number;
  netRxKbps: number;
  netTxKbps: number;
}

export interface MinecraftServer {
  id: string;
  shortId: string;
  name: string;
  description: string;
  ownerId: string;
  ownerEmail?: string;
  nodeId: string;
  nodeName?: string;
  core: ServerCore;
  mcVersion: string;
  coreBuild: string;
  javaVersion: 21 | 17 | 8;
  jvmProfile: JvmProfile;
  customJvmArgs: string;
  status: ServerStatus;
  limits: {
    cpuPercent: number;     // 100% = 1 поток, 400% = 4 потока Zen 5
    ramMb: number;          // напр. 8192 МБ DDR5 ECC
    diskGb: number;         // напр. 80 ГБ NVMe PCIe 5.0
    backupsLimit: number;
    portsLimit: number;
  };
  primaryAddress: string;
  primaryPort: number;
  allocations: PortAllocation[];
  installedAddons: InstalledAddon[];
  subusers: ServerSubuser[];
  backupSchedule: BackupSchedule;
  dockerContainerId: string;
  eulaAccepted: boolean;
  createdAt: string;
  metrics?: ServerMetricsPoint;
}

export interface FileEntry {
  name: string;
  path: string;
  isDirectory: boolean;
  sizeBytes: number;
  modifiedAt: string;
}

export interface CoreDefinition {
  id: ServerCore;
  name: string;
  tagline: string;
  addonType: 'plugin' | 'mod';
  addonFolder: 'plugins' | 'mods';
  supportedVersions: {
    mcVersion: string;
    latestBuild: string;
    recommendedJava: 21 | 17 | 8;
    upstreamUrl: string;
  }[];
}

export interface CatalogAddon {
  id: string;
  name: string;
  slug: string;
  type: 'plugin' | 'mod';
  category: 'Оптимизация' | 'Администрирование' | 'Приват и Мир' | 'Геймплей и Моды' | 'Сеть и Голос';
  supportedCores: ServerCore[];
  version: string;
  author: string;
  downloads: string;
  summary: string;
  sizeBytes: number;
}

export interface HostingPlan {
  id: string;
  name: string;
  badge: string;
  priceMonthlyUsd: number;
  priceMonthlyRub: number;
  recommendedCore: ServerCore;
  cpuPercent: number;
  ramMb: number;
  diskGb: number;
  backupsLimit: number;
  portsLimit: number;
  recommendedJvm: JvmProfile;
  features: string[];
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  actorEmail: string;
  ip: string;
  action: string;
  target: string;
  severity: 'info' | 'warning' | 'security';
}

export interface HostSystemTelemetry {
  hostname: string;
  kernel: string;
  uptimeSeconds: number;
  loadAvg1m: number;
  hostRamTotalMb: number;
  hostRamUsedMb: number;
  dockerSocketAvailable: boolean;
  engineMode: 'docker' | 'supervisor';
  httpsConfigured: boolean;
}
