import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import type {
  AuditLogEntry,
  ComputeNode,
  HostingPlan,
  MinecraftServer,
  PortAllocation,
  ServerBackup,
} from '../src/types.js';
import { hashPassword, type StoredUser } from './auth.js';
import { HOSTING_PLANS } from './catalog.js';

export interface DatabaseSchema {
  users: StoredUser[];
  nodes: ComputeNode[];
  allocations: PortAllocation[];
  servers: MinecraftServer[];
  backups: ServerBackup[];
  plans: HostingPlan[];
  auditLogs: AuditLogEntry[];
}

const DEFAULT_BASE = process.env.VERCEL ? '/tmp/winhost' : './data';
const DATA_DIR = path.resolve(process.env.DATA_DIR || `${DEFAULT_BASE}/state`);
const DB_FILE = path.join(DATA_DIR, 'db.json');

class PersistentStore {
  public data: DatabaseSchema;

  constructor() {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    if (fs.existsSync(DB_FILE)) {
      try {
        const raw = fs.readFileSync(DB_FILE, 'utf8');
        const parsed = JSON.parse(raw) as DatabaseSchema;
        if (parsed.users?.some((u) => u.email === 'admin@winhost.gg')) {
          this.data = parsed;
          return;
        }
      } catch (err) {
        console.error('[WinHost Store] Пересоздание базы состояния:', err);
      }
    }
    this.data = this.createSeedData();
    this.save();
  }

  public save(): void {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    const tmpFile = `${DB_FILE}.tmp`;
    fs.writeFileSync(tmpFile, JSON.stringify(this.data, null, 2), 'utf8');
    fs.renameSync(tmpFile, DB_FILE);
  }

  public addAudit(
    actorEmail: string,
    ip: string,
    action: string,
    target: string,
    severity: AuditLogEntry['severity'] = 'info'
  ): void {
    this.data.auditLogs.unshift({
      id: `aud-${crypto.randomUUID().slice(0, 8)}`,
      timestamp: new Date().toISOString(),
      actorEmail,
      ip,
      action,
      target,
      severity,
    });
    if (this.data.auditLogs.length > 250) {
      this.data.auditLogs.length = 250;
    }
    this.save();
  }

  private createSeedData(): DatabaseSchema {
    const now = new Date().toISOString();

    const adminUser: StoredUser = {
      id: 'usr-admin-01',
      email: 'admin@winhost.gg',
      username: 'WinHost_Owner',
      role: 'admin',
      passwordHash: hashPassword('Admin#DDR5!2026'),
      totpEnabled: false,
      totpSecret: null,
      pendingTotpSecret: null,
      recoveryCodes: [],
      maxServersQuota: 100,
      maxRamMbQuota: 524288,
      suspended: false,
      createdAt: now,
    };

    const clientUser: StoredUser = {
      id: 'usr-client-01',
      email: 'alex@winhost.gg',
      username: 'AlexCraft',
      role: 'client',
      passwordHash: hashPassword('Client#MC!2026'),
      totpEnabled: false,
      totpSecret: null,
      pendingTotpSecret: null,
      recoveryCodes: [],
      maxServersQuota: 5,
      maxRamMbQuota: 32768,
      suspended: false,
      createdAt: now,
    };

    const nodeFra: ComputeNode = {
      id: 'node-de-fra-01',
      name: 'DE-FRA-01 • WinHost Flagship WRX90',
      location: 'Франкфурт, Германия (Tier IV)',
      fqdn: 'de-fra-01.nodes.winhost.gg',
      publicIp: '185.214.98.10',
      cpuModel: 'AMD Ryzen Threadripper PRO 9975WX (Zen 5)',
      cpuCores: 32,
      cpuThreads: 64,
      boostClockGhz: 5.4,
      ramTotalMb: 524288,
      ramType: '512 ГБ DDR5-6400 ECC RDIMM (8 каналов)',
      ramOverallocatePercent: 0,
      diskTotalGb: 7680,
      diskType: '2× 7.68 ТБ Micron 9550 PRO NVMe PCIe 5.0 (RAID-1 PLP)',
      networkSpeedGbps: 25,
      dockerMode: 'docker_socket',
      status: 'online',
      daemonTokenPreview: 'winh_live_9f81...e42a',
      portRangeStart: 25565,
      portRangeEnd: 25595,
      createdAt: now,
    };

    const nodeHel: ComputeNode = {
      id: 'node-fi-hel-02',
      name: 'FI-HEL-02 • WinHost 3D V-Cache',
      location: 'Хельсинки, Финляндия (Tier III+)',
      fqdn: 'fi-hel-02.nodes.winhost.gg',
      publicIp: '95.217.144.22',
      cpuModel: 'AMD EPYC 4585PX (Zen 5 + 128MB 3D V-Cache)',
      cpuCores: 16,
      cpuThreads: 32,
      boostClockGhz: 5.7,
      ramTotalMb: 131072,
      ramType: '128 ГБ DDR5-5600 ECC UDIMM (2×64 ГБ 1DPC)',
      ramOverallocatePercent: 0,
      diskTotalGb: 4000,
      diskType: '2× 4 ТБ Crucial T705 / Enterprise NVMe PCIe 5.0 (RAID-1)',
      networkSpeedGbps: 25,
      dockerMode: 'remote_wings_tls',
      status: 'online',
      daemonTokenPreview: 'winh_live_3c19...b710',
      portRangeStart: 25565,
      portRangeEnd: 25585,
      createdAt: now,
    };

    const allocations: PortAllocation[] = [];
    for (let p = 25565; p <= 25580; p++) {
      allocations.push({
        id: `alloc-fra-${p}`,
        nodeId: nodeFra.id,
        ip: nodeFra.publicIp,
        port: p,
        protocol: 'tcp+udp',
        serverId: null,
        isPrimary: false,
        notes: p === 25565 ? 'Основной игровой порт Java' : '',
      });
    }
    allocations.push(
      {
        id: 'alloc-fra-24454',
        nodeId: nodeFra.id,
        ip: nodeFra.publicIp,
        port: 24454,
        protocol: 'udp',
        serverId: null,
        isPrimary: false,
        notes: 'Simple Voice Chat UDP',
      },
      {
        id: 'alloc-fra-19132',
        nodeId: nodeFra.id,
        ip: nodeFra.publicIp,
        port: 19132,
        protocol: 'udp',
        serverId: null,
        isPrimary: false,
        notes: 'GeyserMC Bedrock Crossplay',
      }
    );

    for (let p = 25565; p <= 25575; p++) {
      allocations.push({
        id: `alloc-hel-${p}`,
        nodeId: nodeHel.id,
        ip: nodeHel.publicIp,
        port: p,
        protocol: 'tcp+udp',
        serverId: null,
        isPrimary: false,
        notes: '',
      });
    }

    const srv1Id = 'srv-winhost-smp';
    const srv2Id = 'srv-neoforge-tech';

    const srv1MainAlloc = allocations.find((a) => a.id === 'alloc-fra-25565')!;
    srv1MainAlloc.serverId = srv1Id;
    srv1MainAlloc.isPrimary = true;

    const srv1VoiceAlloc = allocations.find((a) => a.id === 'alloc-fra-24454')!;
    srv1VoiceAlloc.serverId = srv1Id;
    srv1VoiceAlloc.isPrimary = false;

    const srv2MainAlloc = allocations.find((a) => a.id === 'alloc-hel-25565')!;
    srv2MainAlloc.serverId = srv2Id;
    srv2MainAlloc.isPrimary = true;
    srv2MainAlloc.notes = 'Основной порт NeoForge';

    const srv1: MinecraftServer = {
      id: srv1Id,
      shortId: 'a8f4c910',
      name: 'WinHost Survival SMP',
      description: 'Флагманский сервер выживания 1.21.4 на Purpur + AVX-512 SIMD и голосовым чатом',
      ownerId: adminUser.id,
      nodeId: nodeFra.id,
      core: 'purpur',
      mcVersion: '1.21.4',
      coreBuild: 'purpur-1.21.4-2408',
      javaVersion: 21,
      jvmProfile: 'aikar_g1',
      customJvmArgs: '',
      status: 'running',
      limits: {
        cpuPercent: 400,
        ramMb: 12288,
        diskGb: 80,
        backupsLimit: 5,
        portsLimit: 4,
      },
      primaryAddress: nodeFra.publicIp,
      primaryPort: 25565,
      allocations: [srv1MainAlloc, srv1VoiceAlloc],
      installedAddons: [
        {
          id: 'inst-spark-1',
          catalogId: 'pl-spark',
          name: 'spark Profiler',
          type: 'plugin',
          version: '1.10.119',
          fileName: 'spark-1.10.119-paper.jar',
          enabled: true,
          sizeBytes: 3_840_000,
          installedAt: now,
        },
        {
          id: 'inst-lp-1',
          catalogId: 'pl-luckperms',
          name: 'LuckPerms',
          type: 'plugin',
          version: '5.4.150',
          fileName: 'LuckPerms-Bukkit-5.4.150.jar',
          enabled: true,
          sizeBytes: 1_960_000,
          installedAt: now,
        },
        {
          id: 'inst-vc-1',
          catalogId: 'pl-voicechat',
          name: 'Simple Voice Chat (Plugin)',
          type: 'plugin',
          version: '2.5.28',
          fileName: 'voicechat-bukkit-2.5.28.jar',
          enabled: true,
          sizeBytes: 4_150_000,
          installedAt: now,
        },
      ],
      subusers: [
        {
          id: 'sub-01',
          userId: clientUser.id,
          email: clientUser.email,
          username: clientUser.username,
          permissions: [
            'console.read',
            'console.send',
            'power.control',
            'files.read',
            'addons.manage',
          ],
          addedAt: now,
        },
      ],
      backupSchedule: {
        enabled: true,
        intervalHours: 12,
        keepCount: 5,
      },
      dockerContainerId: 'winhost_a8f4c910_purpur21',
      eulaAccepted: true,
      createdAt: now,
    };

    const srv2: MinecraftServer = {
      id: srv2Id,
      shortId: 'f39d72b4',
      name: 'Create & AE2 Industrial',
      description: 'Тяжёлый техно-магический сервер NeoForge 1.21.1 с профилем Generational ZGC (<1мс пауз)',
      ownerId: clientUser.id,
      nodeId: nodeHel.id,
      core: 'neoforge',
      mcVersion: '1.21.1',
      coreBuild: 'neoforge-21.1.133',
      javaVersion: 21,
      jvmProfile: 'zgc_gen',
      customJvmArgs: '-DFmlModLoadingThreads=8',
      status: 'offline',
      limits: {
        cpuPercent: 600,
        ramMb: 20480,
        diskGb: 160,
        backupsLimit: 7,
        portsLimit: 5,
      },
      primaryAddress: nodeHel.publicIp,
      primaryPort: 25565,
      allocations: [srv2MainAlloc],
      installedAddons: [
        {
          id: 'inst-lithium-2',
          catalogId: 'mod-lithium',
          name: 'Lithium',
          type: 'mod',
          version: '0.14.8',
          fileName: 'lithium-neoforge-0.14.8.jar',
          enabled: true,
          sizeBytes: 740_000,
          installedAt: now,
        },
        {
          id: 'inst-ferrite-2',
          catalogId: 'mod-ferritecore',
          name: 'FerriteCore',
          type: 'mod',
          version: '7.1.1',
          fileName: 'ferritecore-7.1.1-neoforge.jar',
          enabled: true,
          sizeBytes: 195_000,
          installedAt: now,
        },
        {
          id: 'inst-create-2',
          catalogId: 'mod-create',
          name: 'Create',
          type: 'mod',
          version: '6.0.2',
          fileName: 'create-1.21.1-6.0.2.jar',
          enabled: true,
          sizeBytes: 17_400_000,
          installedAt: now,
        },
        {
          id: 'inst-ae2-2',
          catalogId: 'mod-ae2',
          name: 'Applied Energistics 2 (AE2)',
          type: 'mod',
          version: '19.2.4',
          fileName: 'appliedenergistics2-19.2.4.jar',
          enabled: true,
          sizeBytes: 9_850_000,
          installedAt: now,
        },
      ],
      subusers: [],
      backupSchedule: {
        enabled: true,
        intervalHours: 6,
        keepCount: 7,
      },
      dockerContainerId: 'winhost_f39d72b4_neoforge21',
      eulaAccepted: true,
      createdAt: now,
    };

    return {
      users: [adminUser, clientUser],
      nodes: [nodeFra, nodeHel],
      allocations,
      servers: [srv1, srv2],
      backups: [],
      plans: HOSTING_PLANS,
      auditLogs: [
        {
          id: 'aud-init-01',
          timestamp: now,
          actorEmail: 'system@winhost.gg',
          ip: '127.0.0.1',
          action: 'Инициализация кластера WinHost DDR5 ECC',
          target: 'DE-FRA-01 (512 ГБ) + FI-HEL-02 (128 ГБ)',
          severity: 'info',
        },
      ],
    };
  }
}

export const store = new PersistentStore();
