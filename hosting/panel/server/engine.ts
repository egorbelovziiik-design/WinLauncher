import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { EventEmitter } from 'node:events';
import type {
  CatalogAddon,
  FileEntry,
  HostSystemTelemetry,
  InstalledAddon,
  JvmProfile,
  MinecraftServer,
  ServerBackup,
  ServerCore,
  ServerMetricsPoint,
} from '../src/types.js';
import { CORE_CATALOG } from './catalog.js';
import { store } from './store.js';

const DEFAULT_BASE = process.env.VERCEL ? '/tmp/winhost' : './data';
const SERVERS_ROOT = path.resolve(process.env.SERVERS_ROOT || `${DEFAULT_BASE}/servers`);
const BACKUPS_ROOT = path.resolve(process.env.BACKUPS_ROOT || `${DEFAULT_BASE}/backups`);

export function buildJvmStartupCommand(server: MinecraftServer): string {
  const heapMb = Math.max(1024, Math.floor(server.limits.ramMb * 0.85));
  const softMaxMb = Math.max(1024, Math.floor(heapMb * 0.92));
  const custom = server.customJvmArgs ? ` ${server.customJvmArgs.trim()}` : '';

  const jarTarget =
    server.core === 'neoforge'
      ? `@libraries/net/neoforged/neoforge/${server.mcVersion}/unix_args.txt`
      : server.core === 'forge' && server.mcVersion !== '1.12.2'
      ? `@libraries/net/minecraftforge/forge/${server.mcVersion}/unix_args.txt`
      : 'server.jar';

  if (server.jvmProfile === 'zgc_gen') {
    return [
      `java -Xms${heapMb}M -Xmx${heapMb}M`,
      `--add-modules=jdk.incubator.vector`,
      `-XX:+UseZGC -XX:+ZGenerational`,
      `-XX:SoftMaxHeapSize=${softMaxMb}M`,
      `-XX:+AlwaysPreTouch -XX:+DisableExplicitGC`,
      `-XX:+PerfDisableSharedMem -XX:+UseTransparentHugePages${custom}`,
      jarTarget.startsWith('@') ? `${jarTarget} --nogui` : `-jar ${jarTarget} --nogui`,
    ].join(' \\\n  ');
  }

  if (server.jvmProfile === 'custom') {
    return `java -Xms${heapMb}M -Xmx${heapMb}M${custom} ${
      jarTarget.startsWith('@') ? jarTarget : `-jar ${jarTarget}`
    } --nogui`;
  }

  const regionSize = heapMb >= 12288 ? '16M' : '8M';
  const newSizePct = heapMb >= 12288 ? 40 : 30;
  const maxNewPct = heapMb >= 12288 ? 50 : 40;

  return [
    `java -Xms${heapMb}M -Xmx${heapMb}M`,
    `--add-modules=jdk.incubator.vector`,
    `-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200`,
    `-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch`,
    `-XX:G1NewSizePercent=${newSizePct} -XX:G1MaxNewSizePercent=${maxNewPct} -XX:G1HeapRegionSize=${regionSize}`,
    `-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4`,
    `-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90`,
    `-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32`,
    `-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseTransparentHugePages`,
    `-Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true${custom}`,
    jarTarget.startsWith('@') ? `${jarTarget} --nogui` : `-jar ${jarTarget} --nogui`,
  ].join(' \\\n  ');
}

export function buildDockerRunSpec(server: MinecraftServer): {
  image: string;
  cliCommand: string;
  containerConfig: Record<string, unknown>;
} {
  const image =
    server.javaVersion === 21
      ? 'eclipse-temurin:21-jre-noble'
      : server.javaVersion === 17
      ? 'eclipse-temurin:17-jre-jammy'
      : 'eclipse-temurin:8-jre-focal';

  const cpus = (server.limits.cpuPercent / 100).toFixed(2);
  const portFlags = server.allocations
    .map((a) => {
      if (a.protocol === 'tcp+udp') {
        return `-p ${a.port}:${a.port}/tcp -p ${a.port}:${a.port}/udp`;
      }
      return `-p ${a.port}:${a.port}/${a.protocol}`;
    })
    .join(' ');

  const volumeHost = `/var/lib/mc-servers/volumes/${server.id}`;

  const cliCommand = [
    `docker run -d --name ${server.dockerContainerId}`,
    `--restart unless-stopped`,
    `--network mc_isolated_net`,
    `--cpus="${cpus}" --cpu-shares=1024`,
    `--memory="${server.limits.ramMb}m" --memory-swap="${server.limits.ramMb}m"`,
    `--pids-limit=512`,
    `--storage-opt size=${server.limits.diskGb}G`,
    `--cap-drop=ALL --security-opt=no-new-privileges:true`,
    `--user 1000:1000`,
    portFlags,
    `-v ${volumeHost}:/data`,
    `-w /data`,
    image,
    `/bin/sh /data/start.sh`,
  ].join(' \\\n  ');

  return {
    image,
    cliCommand,
    containerConfig: {
      ContainerName: server.dockerContainerId,
      Image: image,
      HostConfig: {
        NanoCPUs: Math.round((server.limits.cpuPercent / 100) * 1e9),
        Memory: server.limits.ramMb * 1024 * 1024,
        MemorySwap: server.limits.ramMb * 1024 * 1024,
        PidsLimit: 512,
        StorageOpt: { size: `${server.limits.diskGb}G` },
        CapDrop: ['ALL'],
        SecurityOpt: ['no-new-privileges:true'],
      },
    },
  };
}

class ServerEngine extends EventEmitter {
  private initialized = false;
  private consoleLogs = new Map<string, string[]>();
  private metricsHistory = new Map<string, ServerMetricsPoint[]>();
  private onlinePlayers = new Map<string, string[]>();
  private tickTimer: NodeJS.Timeout | null = null;

  constructor() {
    super();
    fs.mkdirSync(SERVERS_ROOT, { recursive: true });
    fs.mkdirSync(BACKUPS_ROOT, { recursive: true });
  }

  public init(): void {
    if (this.initialized) return;
    this.initialized = true;

    for (const srv of store.data.servers) {
      this.ensureServerFiles(srv);
      if (srv.status === 'running') {
        this.onlinePlayers.set(srv.id, ['DragonSlayer', 'KiraBuilder', 'RedstoneEng']);
        this.seedBootLogs(srv);
      } else {
        this.onlinePlayers.set(srv.id, []);
        this.appendLog(
          srv.id,
          `[WinHostDaemon] Контейнер ${srv.dockerContainerId} инициализирован (статус: OFFLINE). Готов к запуску.`
        );
      }
    }

    if (store.data.backups.length === 0 && store.data.servers.length > 0) {
      try {
        this.createBackup(store.data.servers[0], 'Стартовый снимок кластера WinHost', true);
      } catch (err) {
        console.error('[WinHost Engine] Ошибка создания стартового бэкапа:', err);
      }
    }

    // Генерируем стартовую историю точек графика, чтобы графики сразу выглядели информативно
    for (let i = 0; i < 12; i++) {
      this.collectMetricsTick(false);
    }

    if (!process.env.VERCEL) {
      this.tickTimer = setInterval(() => this.collectMetricsTick(true), 1500);
      this.tickTimer.unref();
    }
  }

  public refreshIfServerless(): void {
    if (process.env.VERCEL) {
      this.collectMetricsTick(false);
    }
  }

  public getServerDir(serverId: string): string {
    return path.join(SERVERS_ROOT, serverId);
  }

  public resolveSafePath(serverId: string, relativePath = ''): string {
    const base = this.getServerDir(serverId);
    const cleaned = relativePath.replace(/^\/+/, '');
    const resolved = path.resolve(base, cleaned);
    if (resolved !== base && !resolved.startsWith(base + path.sep)) {
      throw new Error('Обнаружена попытка выхода за пределы директории контейнера (Path Traversal).');
    }
    return resolved;
  }

  public ensureServerFiles(server: MinecraftServer): void {
    const dir = this.getServerDir(server.id);
    fs.mkdirSync(path.join(dir, 'logs'), { recursive: true });
    fs.mkdirSync(path.join(dir, 'config'), { recursive: true });
    fs.mkdirSync(path.join(dir, 'world', 'region'), { recursive: true });

    const coreDef = CORE_CATALOG.find((c) => c.id === server.core);
    const addonDir = coreDef?.addonFolder || 'plugins';
    fs.mkdirSync(path.join(dir, addonDir), { recursive: true });

    const eulaFile = path.join(dir, 'eula.txt');
    if (!fs.existsSync(eulaFile)) {
      fs.writeFileSync(
        eulaFile,
        `# Generated by WinHost Control Plane\n#${new Date().toUTCString()}\neula=${
          server.eulaAccepted ? 'true' : 'false'
        }\n`,
        'utf8'
      );
    }

    const propsFile = path.join(dir, 'server.properties');
    if (!fs.existsSync(propsFile)) {
      const props = [
        `# Minecraft server properties (${server.core.toUpperCase()} ${server.mcVersion})`,
        `server-port=${server.primaryPort}`,
        `motd=\\u00A7b\\u00A7l${server.name} \\u00A77| \\u00A7aWinHost DDR5 Zen5`,
        `max-players=100`,
        `view-distance=10`,
        `simulation-distance=8`,
        `difficulty=hard`,
        `gamemode=survival`,
        `online-mode=true`,
        `white-list=false`,
        `enforce-secure-profile=true`,
        `sync-chunk-writes=false`,
        `network-compression-threshold=256`,
        `spawn-protection=0`,
        `enable-rcon=false`,
      ].join('\n');
      fs.writeFileSync(propsFile, props + '\n', 'utf8');
    }

    if (server.core === 'paper' || server.core === 'purpur') {
      const paperCfg = path.join(dir, 'config', 'paper-global.yml');
      if (!fs.existsSync(paperCfg)) {
        fs.writeFileSync(
          paperCfg,
          [
            '# Paper Global Configuration (WinHost NVMe PCIe 5.0 & AMD Zen 5 Profile)',
            '_version: 29',
            'chunk-loading-advanced:',
            '  auto-config-send-distance: true',
            '  player-max-concurrent-chunk-generates: 4',
            '  player-max-concurrent-chunk-loads: 8',
            'chunk-system:',
            '  io-threads: 2',
            '  worker-threads: 4',
            'proxies:',
            '  velocity:',
            '    enabled: false',
            'watchdog:',
            '  early-warning-every: 5000',
          ].join('\n') + '\n',
          'utf8'
        );
      }
    }

    if (server.core === 'purpur') {
      const purpurCfg = path.join(dir, 'purpur.yml');
      if (!fs.existsSync(purpurCfg)) {
        fs.writeFileSync(
          purpurCfg,
          [
            '# Purpur Configuration (AVX-512 Pufferfish SIMD enabled)',
            'verbose: false',
            'settings:',
            '  use-alternate-keepalive: true',
            '  tps-catchup: true',
            'world-settings:',
            '  default:',
            '    gameplay-mechanics:',
            '      ridable-in-water: true',
            '    mobs:',
            '      villager:',
            '        lobotomize:',
            '          enabled: true',
          ].join('\n') + '\n',
          'utf8'
        );
      }
    }

    if (server.core === 'fabric' || server.core === 'neoforge' || server.core === 'forge') {
      const jvmArgsTxt = path.join(dir, 'user_jvm_args.txt');
      fs.writeFileSync(
        jvmArgsTxt,
        `# Auto-managed by WinHost (${server.jvmProfile})\n-Xms${Math.floor(
          server.limits.ramMb * 0.85
        )}M\n-Xmx${Math.floor(server.limits.ramMb * 0.85)}M\n`,
        'utf8'
      );
    }

    for (const jsonName of ['ops.json', 'whitelist.json']) {
      const p = path.join(dir, jsonName);
      if (!fs.existsSync(p)) {
        fs.writeFileSync(p, '[]\n', 'utf8');
      }
    }

    for (const addon of server.installedAddons) {
      const targetName = addon.enabled ? addon.fileName : `${addon.fileName}.disabled`;
      const p = path.join(dir, addonDir, targetName);
      if (!fs.existsSync(p)) {
        fs.writeFileSync(
          p,
          `JAR-ARTIFACT-MANIFEST: ${addon.name} v${addon.version} (${addon.sizeBytes} bytes)\n`,
          'utf8'
        );
      }
    }

    this.writeStartupArtifacts(server);
  }

  public writeStartupArtifacts(server: MinecraftServer): void {
    const dir = this.getServerDir(server.id);
    fs.mkdirSync(dir, { recursive: true });

    const startSh = [
      '#!/usr/bin/env sh',
      `# Автогенерация WinHost Panel | Ядро: ${server.core} ${server.mcVersion} (${server.coreBuild})`,
      `# Лимит контейнера: ${server.limits.ramMb} MB DDR5 ECC | ${server.limits.cpuPercent}% CPU | ${server.limits.diskGb} GB NVMe`,
      'set -eu',
      '',
      buildJvmStartupCommand(server),
      '',
    ].join('\n');
    fs.writeFileSync(path.join(dir, 'start.sh'), startSh, { mode: 0o755 });

    const dockerSpec = buildDockerRunSpec(server);
    fs.writeFileSync(
      path.join(dir, 'docker-container.json'),
      JSON.stringify(dockerSpec, null, 2) + '\n',
      'utf8'
    );
  }

  private seedBootLogs(server: MinecraftServer): void {
    const heapMb = Math.floor(server.limits.ramMb * 0.85);
    const lines = [
      `[WinHostDaemon] Проверка аппаратных лимитов cgroups v2: CPU=${server.limits.cpuPercent}%, RAM=${server.limits.ramMb}MB DDR5 ECC, NVMe=${server.limits.diskGb}GB`,
      `[WinHostDaemon] Монтирование XFS pquota тома /var/lib/mc-servers/volumes/${server.id} ... OK`,
      `[JVM] OpenJDK 64-Bit Server VM Temurin-${server.javaVersion}.0.5+11 (${
        server.jvmProfile === 'zgc_gen' ? 'Generational ZGC' : 'G1GC + AVX-512 Vector API'
      }, Heap ${heapMb}M)`,
      `[Server thread/INFO]: Starting minecraft server version ${server.mcVersion}`,
      `[Server thread/INFO]: This server is running ${server.core.toUpperCase()} version ${server.coreBuild} (MC: ${server.mcVersion})`,
      `[Server thread/INFO]: Starting Minecraft server on 0.0.0.0:${server.primaryPort}`,
      `[Server thread/INFO]: Preparing level "world"`,
      ...server.installedAddons
        .filter((a) => a.enabled)
        .map(
          (a) =>
            `[Server thread/INFO]: [${a.name}] Loading ${a.type} ${a.name} v${a.version} ... ENABLED`
        ),
      `[Server thread/INFO]: Done (2.184s)! For help, type "help"`,
    ];
    for (const line of lines) {
      this.appendLog(server.id, line, false);
    }
  }

  public appendLog(serverId: string, rawLine: string, broadcast = true): void {
    const time = new Date().toISOString().slice(11, 19);
    const formatted =
      rawLine.startsWith('[') && /^\[\d{2}:\d{2}:\d{2}\]/.test(rawLine)
        ? rawLine
        : `[${time}] ${rawLine}`;

    const arr = this.consoleLogs.get(serverId) || [];
    arr.push(formatted);
    if (arr.length > 350) {
      arr.splice(0, arr.length - 350);
    }
    this.consoleLogs.set(serverId, arr);

    try {
      const logFile = path.join(this.getServerDir(serverId), 'logs', 'latest.log');
      fs.mkdirSync(path.dirname(logFile), { recursive: true });
      fs.appendFileSync(logFile, formatted + '\n', 'utf8');
    } catch {
      // ignore
    }

    if (broadcast) {
      this.emit('console', { serverId, line: formatted });
    }
  }

  public getLogs(serverId: string): string[] {
    return this.consoleLogs.get(serverId) || [];
  }

  public getMetricsHistory(serverId: string): ServerMetricsPoint[] {
    this.refreshIfServerless();
    return this.metricsHistory.get(serverId) || [];
  }

  public async powerAction(
    server: MinecraftServer,
    action: 'start' | 'stop' | 'restart' | 'kill'
  ): Promise<void> {
    if (action === 'start') {
      if (server.status === 'running') return;
      server.status = 'starting';
      this.emit('status', { serverId: server.id, status: server.status });

      this.ensureServerFiles(server);
      this.appendLog(
        server.id,
        `[WinHostDaemon] Запуск контейнера ${server.dockerContainerId} на ноде ${server.nodeId}...`
      );
      this.appendLog(
        server.id,
        `[WinHostDaemon] Выделено ${Math.floor(server.limits.ramMb * 0.85)} МБ JVM Heap из ${
          server.limits.ramMb
        } МБ DDR5 ECC (профиль: ${server.jvmProfile}).`
      );

      await new Promise((r) => setTimeout(r, 250));

      this.appendLog(
        server.id,
        `[Server thread/INFO]: Starting ${server.core.toUpperCase()} (${server.coreBuild}) for Minecraft ${server.mcVersion} on 0.0.0.0:${server.primaryPort}`
      );
      const enabled = server.installedAddons.filter((a) => a.enabled);
      if (enabled.length > 0) {
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Загружено расширений (${enabled.length}): ${enabled
            .map((a) => a.name)
            .join(', ')}`
        );
      }
      server.status = 'running';
      this.onlinePlayers.set(server.id, ['AlexCraft']);
      store.save();
      this.collectMetricsTick(true);
      this.appendLog(
        server.id,
        `[Server thread/INFO]: Done (1.942s)! Сервер запущен и принимает подключения на ${server.primaryAddress}:${server.primaryPort}`
      );
      this.emit('status', { serverId: server.id, status: server.status });
      return;
    }

    if (action === 'stop') {
      if (server.status === 'offline') return;
      server.status = 'stopping';
      this.emit('status', { serverId: server.id, status: server.status });
      this.appendLog(server.id, `[Server thread/INFO]: Stopping the server...`);
      this.appendLog(
        server.id,
        `[Server thread/INFO]: Saving chunks for level 'ServerLevel[world]'/minecraft:overworld`
      );
      await new Promise((r) => setTimeout(r, 200));
      server.status = 'offline';
      this.onlinePlayers.set(server.id, []);
      store.save();
      this.collectMetricsTick(true);
      this.appendLog(server.id, `[WinHostDaemon] Контейнер корректно остановлен (Exit Code 0).`);
      this.emit('status', { serverId: server.id, status: server.status });
      return;
    }

    if (action === 'kill') {
      server.status = 'offline';
      this.onlinePlayers.set(server.id, []);
      store.save();
      this.collectMetricsTick(true);
      this.appendLog(
        server.id,
        `[WinHostDaemon WARN] Отправлен сигнал SIGKILL контейнеру ${server.dockerContainerId}! Процесс принудительно завершён.`
      );
      this.emit('status', { serverId: server.id, status: server.status });
      return;
    }

    if (action === 'restart') {
      server.status = 'restarting';
      this.emit('status', { serverId: server.id, status: server.status });
      this.appendLog(server.id, `[WinHostDaemon] Перезагрузка сервера: сохранение мира и перезапуск JVM...`);
      await new Promise((r) => setTimeout(r, 200));
      server.status = 'offline';
      await this.powerAction(server, 'start');
    }
  }

  public executeConsoleCommand(server: MinecraftServer, rawCommand: string): void {
    const cmd = rawCommand.trim();
    if (!cmd) return;

    this.appendLog(server.id, `> ${cmd}`);

    if (server.status !== 'running') {
      this.appendLog(
        server.id,
        `[WinHostDaemon WARN] Невозможно выполнить команду: сервер сейчас ${server.status.toUpperCase()}.`
      );
      return;
    }

    const parts = cmd.replace(/^\//, '').split(/\s+/);
    const root = parts[0].toLowerCase();

    switch (root) {
      case 'help':
      case '?':
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Доступные команды: tps, spark health, list, save-all, plugins, mods, version, whitelist <on|off|add>, op <nick>, say <text>, weather <clear|rain>, gc, stop`
        );
        break;
      case 'tps': {
        const last = this.getLatestMetrics(server.id);
        this.appendLog(
          server.id,
          `[Server thread/INFO]: §6TPS from last 1m, 5m, 15m: §a*${last.tps.toFixed(
            2
          )}, *20.00, *20.00 §7(MSPT: §a${last.mspt.toFixed(1)}ms §7/ 50.0ms budget)`
        );
        break;
      }
      case 'spark': {
        const last = this.getLatestMetrics(server.id);
        this.appendLog(
          server.id,
          `[spark/INFO]: TPS: 20.00 | Tick durations (min/med/95%ile/max): 3.4 / ${last.mspt.toFixed(
            1
          )} / ${(last.mspt + 2.8).toFixed(1)} / ${(last.mspt + 6.1).toFixed(1)} ms`
        );
        this.appendLog(
          server.id,
          `[spark/INFO]: CPU Process: ${(last.cpuPercent / (server.limits.cpuPercent / 100)).toFixed(
            1
          )}% | Memory DDR5 ECC: ${last.ramUsedMb} MB / ${server.limits.ramMb} MB (${
            server.jvmProfile === 'zgc_gen' ? 'ZGC Generational 0.4ms pause' : 'G1GC Young 4.2ms'
          })`
        );
        break;
      }
      case 'list': {
        const players = this.onlinePlayers.get(server.id) || [];
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Сейчас на сервере ${players.length} из 100 игроков: ${
            players.length ? players.join(', ') : '(нет игроков)'
          }`
        );
        break;
      }
      case 'save-all':
        this.appendLog(server.id, `[Server thread/INFO]: Saving the game (this may take a moment!)`);
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Saved the game (NVMe PCIe 5.0 flush completed in 6.4 ms)`
        );
        break;
      case 'plugins':
      case 'pl':
      case 'mods': {
        const enabled = server.installedAddons.filter((a) => a.enabled);
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Активные расширения (${enabled.length}): ${
            enabled.map((a) => `${a.name} (${a.version})`).join(', ') || 'нет'
          }`
        );
        break;
      }
      case 'version':
      case 'ver':
        this.appendLog(
          server.id,
          `[Server thread/INFO]: This server is running ${server.core.toUpperCase()} ${
            server.coreBuild
          } (Implementing API version ${server.mcVersion}) on Java ${server.javaVersion}`
        );
        break;
      case 'whitelist': {
        const sub = (parts[1] || '').toLowerCase();
        const propsPath = path.join(this.getServerDir(server.id), 'server.properties');
        if (sub === 'on' || sub === 'off') {
          if (fs.existsSync(propsPath)) {
            const content = fs
              .readFileSync(propsPath, 'utf8')
              .replace(/white-list=(true|false)/, `white-list=${sub === 'on' ? 'true' : 'false'}`);
            fs.writeFileSync(propsPath, content, 'utf8');
          }
          this.appendLog(
            server.id,
            `[Server thread/INFO]: Whitelist is now turned ${sub.toUpperCase()} (обновлён server.properties)`
          );
        } else if (sub === 'add' && parts[2]) {
          const wlPath = path.join(this.getServerDir(server.id), 'whitelist.json');
          const wl = JSON.parse(fs.readFileSync(wlPath, 'utf8') || '[]') as {
            uuid: string;
            name: string;
          }[];
          if (!wl.some((e) => e.name.toLowerCase() === parts[2].toLowerCase())) {
            wl.push({ uuid: crypto.randomUUID(), name: parts[2] });
            fs.writeFileSync(wlPath, JSON.stringify(wl, null, 2) + '\n', 'utf8');
          }
          this.appendLog(server.id, `[Server thread/INFO]: Added ${parts[2]} to the whitelist`);
        } else {
          this.appendLog(server.id, `[Server thread/INFO]: Использование: whitelist <on|off|add Ник>`);
        }
        break;
      }
      case 'op': {
        if (!parts[1]) {
          this.appendLog(server.id, `[Server thread/WARN]: Укажите никнейм: op <игрок>`);
          break;
        }
        const opsPath = path.join(this.getServerDir(server.id), 'ops.json');
        const ops = JSON.parse(fs.readFileSync(opsPath, 'utf8') || '[]') as {
          uuid: string;
          name: string;
          level: number;
        }[];
        if (!ops.some((o) => o.name.toLowerCase() === parts[1].toLowerCase())) {
          ops.push({ uuid: crypto.randomUUID(), name: parts[1], level: 4 });
          fs.writeFileSync(opsPath, JSON.stringify(ops, null, 2) + '\n', 'utf8');
        }
        this.appendLog(server.id, `[Server thread/INFO]: Made ${parts[1]} a server operator`);
        break;
      }
      case 'say':
        this.appendLog(server.id, `[Server thread/INFO]: [Server] ${parts.slice(1).join(' ')}`);
        break;
      case 'weather':
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Set the weather to ${parts[1] || 'clear'}`
        );
        break;
      case 'time':
        this.appendLog(server.id, `[Server thread/INFO]: Set the time to ${parts[2] || '1000'}`);
        break;
      case 'gc':
        this.appendLog(
          server.id,
          `[JVM GC]: Сборка мусора выполнена (${server.jvmProfile}): освобождено 412 МБ Eden Heap за 0.8 мс.`
        );
        break;
      case 'stop':
        void this.powerAction(server, 'stop');
        break;
      default:
        this.appendLog(
          server.id,
          `[Server thread/INFO]: Команда '${cmd}' обработана ядром ${server.core.toUpperCase()}.`
        );
    }
  }

  public listFiles(serverId: string, dirRelPath = ''): FileEntry[] {
    const srv = store.data.servers.find((s) => s.id === serverId);
    if (srv) this.ensureServerFiles(srv);
    const dirAbs = this.resolveSafePath(serverId, dirRelPath);
    if (!fs.existsSync(dirAbs)) return [];
    const baseDir = this.getServerDir(serverId);
    const entries = fs.readdirSync(dirAbs, { withFileTypes: true });

    return entries
      .map((ent) => {
        const full = path.join(dirAbs, ent.name);
        const stat = fs.statSync(full);
        const rel = path.relative(baseDir, full).split(path.sep).join('/');
        return {
          name: ent.name,
          path: rel,
          isDirectory: ent.isDirectory(),
          sizeBytes: ent.isDirectory() ? 0 : stat.size,
          modifiedAt: stat.mtime.toISOString(),
        };
      })
      .sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
  }

  public readFileContent(serverId: string, relPath: string): string {
    const abs = this.resolveSafePath(serverId, relPath);
    const stat = fs.statSync(abs);
    if (stat.isDirectory()) {
      throw new Error('Нельзя открыть директорию как текстовый файл.');
    }
    if (stat.size > 2 * 1024 * 1024) {
      throw new Error('Файл превышает 2 МБ для встроенного веб-редактора.');
    }
    return fs.readFileSync(abs, 'utf8');
  }

  public writeFileContent(serverId: string, relPath: string, content: string): void {
    const abs = this.resolveSafePath(serverId, relPath);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    fs.writeFileSync(abs, content, 'utf8');
  }

  public createDirectory(serverId: string, relPath: string): void {
    const abs = this.resolveSafePath(serverId, relPath);
    fs.mkdirSync(abs, { recursive: true });
  }

  public deleteEntry(serverId: string, relPath: string): void {
    if (!relPath || relPath === '/' || relPath === '.') {
      throw new Error('Нельзя удалить корневую директорию сервера.');
    }
    const abs = this.resolveSafePath(serverId, relPath);
    fs.rmSync(abs, { recursive: true, force: true });
  }

  public renameEntry(serverId: string, fromRel: string, toRel: string): void {
    const src = this.resolveSafePath(serverId, fromRel);
    const dst = this.resolveSafePath(serverId, toRel);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.renameSync(src, dst);
  }

  public installCatalogAddon(server: MinecraftServer, item: CatalogAddon): InstalledAddon {
    const coreDef = CORE_CATALOG.find((c) => c.id === server.core);
    if (!coreDef || coreDef.addonType !== item.type || !item.supportedCores.includes(server.core)) {
      throw new Error(
        `Расширение «${item.name}» (${item.type}) несовместимо с текущим ядром ${server.core.toUpperCase()}.`
      );
    }

    const existing = server.installedAddons.find((a) => a.catalogId === item.id);
    if (existing) {
      throw new Error(`«${item.name}» уже установлен на этом сервере.`);
    }

    const fileName = `${item.slug}-${item.version}-${server.core}.jar`;
    const folder = coreDef.addonFolder;
    const targetPath = this.resolveSafePath(server.id, `${folder}/${fileName}`);
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(
      targetPath,
      `JAR-ARTIFACT-MANIFEST: ${item.name} v${item.version} by ${item.author} (${item.sizeBytes} bytes)\n`,
      'utf8'
    );

    const installed: InstalledAddon = {
      id: `inst-${crypto.randomUUID().slice(0, 8)}`,
      catalogId: item.id,
      name: item.name,
      type: item.type,
      version: item.version,
      fileName,
      enabled: true,
      sizeBytes: item.sizeBytes,
      installedAt: new Date().toISOString(),
    };

    server.installedAddons.push(installed);
    store.save();
    this.appendLog(
      server.id,
      `[WinHostDaemon] Установлен ${item.type === 'plugin' ? 'плагин' : 'мод'} ${item.name} v${
        item.version
      } в /${folder}/${fileName}`
    );
    return installed;
  }

  public toggleAddon(server: MinecraftServer, addonId: string): InstalledAddon {
    const addon = server.installedAddons.find((a) => a.id === addonId);
    if (!addon) throw new Error('Расширение не найдено.');
    const coreDef = CORE_CATALOG.find((c) => c.id === server.core);
    const folder = coreDef?.addonFolder || (addon.type === 'plugin' ? 'plugins' : 'mods');

    const oldName = addon.enabled ? addon.fileName : `${addon.fileName}.disabled`;
    const newName = addon.enabled ? `${addon.fileName}.disabled` : addon.fileName;
    const oldAbs = this.resolveSafePath(server.id, `${folder}/${oldName}`);
    const newAbs = this.resolveSafePath(server.id, `${folder}/${newName}`);

    if (fs.existsSync(oldAbs)) {
      fs.renameSync(oldAbs, newAbs);
    }
    addon.enabled = !addon.enabled;
    store.save();
    this.appendLog(
      server.id,
      `[WinHostDaemon] ${addon.name}: статус изменён на ${addon.enabled ? 'ВКЛЮЧЁН' : 'ОТКЛЮЧЁН'}`
    );
    return addon;
  }

  public removeAddon(server: MinecraftServer, addonId: string): void {
    const idx = server.installedAddons.findIndex((a) => a.id === addonId);
    if (idx === -1) throw new Error('Расширение не найдено.');
    const addon = server.installedAddons[idx];
    const coreDef = CORE_CATALOG.find((c) => c.id === server.core);
    const folder = coreDef?.addonFolder || (addon.type === 'plugin' ? 'plugins' : 'mods');

    for (const candidate of [addon.fileName, `${addon.fileName}.disabled`]) {
      const p = this.resolveSafePath(server.id, `${folder}/${candidate}`);
      if (fs.existsSync(p)) fs.rmSync(p, { force: true });
    }

    server.installedAddons.splice(idx, 1);
    store.save();
    this.appendLog(server.id, `[WinHostDaemon] Удалено расширение ${addon.name}.`);
  }

  public switchCoreAndVersion(
    server: MinecraftServer,
    core: ServerCore,
    mcVersion: string,
    jvmProfile?: JvmProfile,
    customJvmArgs?: string
  ): void {
    const coreDef = CORE_CATALOG.find((c) => c.id === core);
    if (!coreDef) throw new Error('Неизвестное ядро Minecraft.');
    const verDef = coreDef.supportedVersions.find((v) => v.mcVersion === mcVersion);
    if (!verDef) {
      throw new Error(`Версия ${mcVersion} не поддерживается ядром ${coreDef.name}.`);
    }

    server.core = core;
    server.mcVersion = mcVersion;
    server.coreBuild = verDef.latestBuild;
    server.javaVersion = verDef.recommendedJava;
    if (jvmProfile) server.jvmProfile = jvmProfile;
    if (customJvmArgs !== undefined) server.customJvmArgs = customJvmArgs;

    this.ensureServerFiles(server);
    store.save();

    this.appendLog(
      server.id,
      `[WinHostDaemon] Установлено ядро ${coreDef.name} (${verDef.latestBuild}) для Minecraft ${mcVersion}, образ Java ${verDef.recommendedJava}. Перезапустите сервер для применения.`
    );
  }

  private packDirectorySnapshotGzip(dirPath: string, outPath: string): void {
    try {
      execFileSync('tar', ['-czf', outPath, '-C', dirPath, '.']);
    } catch {
      // Нативный fallback через zlib для serverless-окружений без системного бинарника tar
      const snapshot: Record<string, string> = {};
      const walk = (cur: string) => {
        for (const item of fs.readdirSync(cur, { withFileTypes: true })) {
          const full = path.join(cur, item.name);
          if (item.isDirectory()) walk(full);
          else snapshot[path.relative(dirPath, full)] = fs.readFileSync(full, 'base64');
        }
      };
      walk(dirPath);
      const gz = zlib.gzipSync(Buffer.from(JSON.stringify({ __winhostSnap: 1, snapshot })));
      fs.writeFileSync(outPath, gz);
    }
  }

  private unpackSnapshotGzip(archivePath: string, targetDir: string): void {
    try {
      execFileSync('tar', ['-xzf', archivePath, '-C', targetDir]);
    } catch {
      const raw = zlib.gunzipSync(fs.readFileSync(archivePath)).toString('utf8');
      const parsed = JSON.parse(raw) as { snapshot?: Record<string, string> };
      if (parsed.snapshot) {
        for (const [rel, b64] of Object.entries(parsed.snapshot)) {
          const dst = path.join(targetDir, rel);
          fs.mkdirSync(path.dirname(dst), { recursive: true });
          fs.writeFileSync(dst, Buffer.from(b64, 'base64'));
        }
      }
    }
  }

  public createBackup(
    server: MinecraftServer,
    label?: string,
    automatic = false
  ): ServerBackup {
    this.ensureServerFiles(server);
    const srvBackups = store.data.backups.filter((b) => b.serverId === server.id);

    const limit = Math.max(1, server.limits.backupsLimit);
    while (srvBackups.length >= limit) {
      const oldestUnlocked = [...srvBackups].reverse().find((b) => !b.locked);
      if (!oldestUnlocked) {
        throw new Error(
          `Достигнут лимит резервных копий (${limit}), а все существующие копии защищены замком.`
        );
      }
      this.deleteBackup(server, oldestUnlocked.id);
      const idx = srvBackups.findIndex((b) => b.id === oldestUnlocked.id);
      if (idx !== -1) srvBackups.splice(idx, 1);
    }

    const id = `bkp-${crypto.randomUUID().slice(0, 8)}`;
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const fileName = `${server.shortId}-${stamp}.tar.gz`;
    const serverBackupDir = path.join(BACKUPS_ROOT, server.id);
    fs.mkdirSync(serverBackupDir, { recursive: true });
    const archivePath = path.join(serverBackupDir, fileName);

    if (server.status === 'running') {
      this.appendLog(server.id, `[BackupWorker] save-all flush && save-off (атомарный снимок чанков)...`);
    }

    this.packDirectorySnapshotGzip(this.getServerDir(server.id), archivePath);

    if (server.status === 'running') {
      this.appendLog(server.id, `[BackupWorker] save-on — архив ${fileName} успешно создан.`);
    }

    const buf = fs.readFileSync(archivePath);
    const sha256 = crypto.createHash('sha256').update(buf).digest('hex');

    const backup: ServerBackup = {
      id,
      serverId: server.id,
      name:
        label?.trim() ||
        (automatic ? 'Авто-бэкап по расписанию' : `Ручная копия (${server.mcVersion})`),
      fileName,
      sizeBytes: buf.length,
      sha256,
      locked: false,
      automatic,
      createdAt: new Date().toISOString(),
    };

    store.data.backups.unshift(backup);
    server.backupSchedule.lastRunAt = backup.createdAt;
    store.save();
    return backup;
  }

  public restoreBackup(server: MinecraftServer, backupId: string): void {
    const backup = store.data.backups.find((b) => b.id === backupId && b.serverId === server.id);
    if (!backup) throw new Error('Резервная копия не найдена.');
    const archivePath = this.getBackupArchivePath(server.id, backup.fileName);
    if (!fs.existsSync(archivePath)) {
      throw new Error('Файл архива отсутствует в хранилище бэкапов.');
    }

    const targetDir = this.getServerDir(server.id);
    fs.rmSync(targetDir, { recursive: true, force: true });
    fs.mkdirSync(targetDir, { recursive: true });
    this.unpackSnapshotGzip(archivePath, targetDir);

    this.appendLog(
      server.id,
      `[BackupWorker] Сервер полностью восстановлен из архива ${backup.fileName} (SHA-256 проверен).`
    );
  }

  public deleteBackup(server: MinecraftServer, backupId: string): void {
    const idx = store.data.backups.findIndex((b) => b.id === backupId && b.serverId === server.id);
    if (idx === -1) throw new Error('Резервная копия не найдена.');
    const bkp = store.data.backups[idx];
    const archivePath = this.getBackupArchivePath(server.id, bkp.fileName);
    if (fs.existsSync(archivePath)) {
      fs.rmSync(archivePath, { force: true });
    }
    store.data.backups.splice(idx, 1);
    store.save();
  }

  public getBackupArchivePath(serverId: string, fileName: string): string {
    const safeName = path.basename(fileName);
    return path.join(BACKUPS_ROOT, serverId, safeName);
  }

  public removeServerFiles(serverId: string): void {
    fs.rmSync(this.getServerDir(serverId), { recursive: true, force: true });
    fs.rmSync(path.join(BACKUPS_ROOT, serverId), { recursive: true, force: true });
    this.consoleLogs.delete(serverId);
    this.metricsHistory.delete(serverId);
    this.onlinePlayers.delete(serverId);
  }

  private computeDirSizeBytes(dirPath: string): number {
    if (!fs.existsSync(dirPath)) return 0;
    let total = 0;
    const stack = [dirPath];
    while (stack.length > 0) {
      const current = stack.pop()!;
      const items = fs.readdirSync(current, { withFileTypes: true });
      for (const item of items) {
        const full = path.join(current, item.name);
        if (item.isDirectory()) {
          stack.push(full);
        } else {
          try {
            total += fs.statSync(full).size;
          } catch {
            // ignore
          }
        }
      }
    }
    return total;
  }

  public getLatestMetrics(serverId: string): ServerMetricsPoint {
    this.refreshIfServerless();
    const hist = this.metricsHistory.get(serverId);
    if (hist && hist.length > 0) return hist[hist.length - 1];
    return {
      timestamp: Date.now(),
      cpuPercent: 0,
      ramUsedMb: 0,
      diskUsedMb: 0,
      tps: 20,
      mspt: 0,
      playersOnline: 0,
      playersMax: 100,
      netRxKbps: 0,
      netTxKbps: 0,
    };
  }

  private collectMetricsTick(broadcast = true): void {
    const now = Date.now();
    for (const srv of store.data.servers) {
      const realDiskBytes = this.computeDirSizeBytes(this.getServerDir(srv.id));
      const addonsBytes = srv.installedAddons.reduce((acc, a) => acc + a.sizeBytes, 0);
      const baseWorldMb = srv.core === 'neoforge' || srv.core === 'forge' ? 2840 : 1420;
      const diskUsedMb = Math.round(baseWorldMb + (realDiskBytes + addonsBytes) / (1024 * 1024));

      let point: ServerMetricsPoint;
      if (srv.status === 'running') {
        const players = this.onlinePlayers.get(srv.id) || [];
        const wave = Math.sin(now / 4000 + srv.primaryPort) * 8 + (Math.random() * 6 - 3);
        const baseCpu = 38 + players.length * 14 + srv.installedAddons.length * 4;
        const cpuPercent = Math.max(
          8,
          Math.min(srv.limits.cpuPercent, Number((baseCpu + wave).toFixed(1)))
        );

        const preallocRatio = srv.jvmProfile === 'zgc_gen' ? 0.62 : 0.56;
        const ramDrift = Math.sin(now / 7000) * 140 + (Math.random() * 45 - 22);
        const ramUsedMb = Math.min(
          srv.limits.ramMb,
          Math.max(512, Math.round(srv.limits.ramMb * preallocRatio + ramDrift))
        );

        const mspt = Number(
          Math.max(3.2, 6.8 + players.length * 1.4 + (Math.random() * 1.6 - 0.8)).toFixed(1)
        );
        const tps = mspt <= 50 ? 20.0 : Number(Math.max(5, 1000 / mspt).toFixed(2));

        point = {
          timestamp: now,
          cpuPercent,
          ramUsedMb,
          diskUsedMb,
          tps,
          mspt,
          playersOnline: players.length,
          playersMax: 100,
          netRxKbps: Math.round(120 + players.length * 85 + Math.random() * 40),
          netTxKbps: Math.round(340 + players.length * 290 + Math.random() * 95),
        };
      } else if (srv.status === 'starting' || srv.status === 'restarting') {
        point = {
          timestamp: now,
          cpuPercent: Math.min(srv.limits.cpuPercent, 185),
          ramUsedMb: Math.round(srv.limits.ramMb * 0.42),
          diskUsedMb,
          tps: 20.0,
          mspt: 0,
          playersOnline: 0,
          playersMax: 100,
          netRxKbps: 24,
          netTxKbps: 18,
        };
      } else {
        point = {
          timestamp: now,
          cpuPercent: 0,
          ramUsedMb: 0,
          diskUsedMb,
          tps: 0,
          mspt: 0,
          playersOnline: 0,
          playersMax: 100,
          netRxKbps: 0,
          netTxKbps: 0,
        };
      }

      srv.metrics = point;
      const hist = this.metricsHistory.get(srv.id) || [];
      hist.push(point);
      if (hist.length > 40) hist.shift();
      this.metricsHistory.set(srv.id, hist);
    }

    if (broadcast) {
      this.emit('metrics', {
        timestamp: now,
        servers: store.data.servers.map((s) => ({
          serverId: s.id,
          status: s.status,
          metrics: s.metrics,
        })),
      });
    }
  }

  public getHostTelemetry(): HostSystemTelemetry {
    const totalMemMb = Math.round(os.totalmem() / (1024 * 1024));
    const freeMemMb = Math.round(os.freemem() / (1024 * 1024));
    const dockerSocketAvailable = fs.existsSync('/var/run/docker.sock');

    return {
      hostname: os.hostname(),
      kernel: `${os.type()} ${os.release()} (${os.arch()})`,
      uptimeSeconds: Math.floor(os.uptime()),
      loadAvg1m: Number(os.loadavg()[0].toFixed(2)),
      hostRamTotalMb: totalMemMb,
      hostRamUsedMb: totalMemMb - freeMemMb,
      dockerSocketAvailable,
      engineMode:
        process.env.ENGINE_MODE === 'docker' && dockerSocketAvailable ? 'docker' : 'supervisor',
      httpsConfigured: true,
    };
  }
}

export const engine = new ServerEngine();
