import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';
import express from 'express';
import {
  canAccessServer,
  checkRateLimit,
  clearFailedAttempts,
  hashPassword,
  makeAuthMiddleware,
  recordFailedAttempt,
  sanitizeUser,
  signToken,
  verifyPassword,
  verifyToken,
  type AuthedRequest,
  type StoredUser,
} from './auth.js';
import {
  buildOtpAuthUri,
  computeTotpCode,
  generateQrSvg,
  generateRecoveryCodes,
  generateTotpSecret,
  verifyTotpCode,
} from './totp.js';
import { ADDONS_CATALOG, CORE_CATALOG } from './catalog.js';
import { store } from './store.js';
import { buildDockerRunSpec, buildJvmStartupCommand, engine } from './engine.js';
import type {
  ComputeNode,
  JvmProfile,
  MinecraftServer,
  PortAllocation,
  ServerCore,
  ServerPermission,
} from '../src/types.js';

engine.init();

export const app = express();
app.disable('x-powered-by');
const jsonBodyParser = express.json({ limit: '4mb' });

// Совместимость путей и pre-parsed req.body для Vercel Serverless Rewrites и прямого запуска
app.use((req, res, next) => {
  engine.init();
  engine.refreshIfServerless();

  if (req.url) {
    const parsed = new URL(req.url, 'http://winhost.local');
    const rewrittenSubpath = parsed.searchParams.get('__pathname');
    if (rewrittenSubpath !== null) {
      parsed.searchParams.delete('__pathname');
      const cleanSub = rewrittenSubpath.replace(/^\/+/, '');
      const qs = parsed.searchParams.toString();
      if (parsed.pathname === '/api' || parsed.pathname === '/api/') {
        req.url = `/api/${cleanSub}${qs ? `?${qs}` : ''}`;
      } else {
        req.url = `${parsed.pathname}${qs ? `?${qs}` : ''}`;
      }
    }
  }

  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');

  if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') {
    next();
    return;
  }

  const existingBody = (req as unknown as { body?: unknown }).body;
  if (existingBody !== undefined && existingBody !== null) {
    if (typeof existingBody === 'string' && existingBody.length > 0) {
      try {
        req.body = JSON.parse(existingBody);
      } catch {
        // leave as string
      }
    } else if (Buffer.isBuffer(existingBody)) {
      try {
        req.body = JSON.parse(existingBody.toString('utf8'));
      } catch {
        // leave as buffer
      }
    }
    next();
    return;
  }

  jsonBodyParser(req, res, next);
});

const getClientIp = (req: express.Request): string => {
  const xff = req.headers['x-forwarded-for'];
  if (typeof xff === 'string' && xff.length > 0) return xff.split(',')[0].trim();
  return req.socket.remoteAddress || '127.0.0.1';
};

const { requireAuth, requireAdmin } = makeAuthMiddleware((id) =>
  store.data.users.find((u) => u.id === id)
);

export function enrichServer(srv: MinecraftServer): MinecraftServer {
  const node = store.data.nodes.find((n) => n.id === srv.nodeId);
  const owner = store.data.users.find((u) => u.id === srv.ownerId);
  const allocations = store.data.allocations.filter((a) => a.serverId === srv.id);
  srv.allocations = allocations;
  return {
    ...srv,
    nodeName: node?.name || srv.nodeId,
    ownerEmail: owner?.email || 'unknown',
    metrics: engine.getLatestMetrics(srv.id),
  };
}

export function enrichNode(node: ComputeNode): ComputeNode {
  const nodeServers = store.data.servers.filter((s) => s.nodeId === node.id);
  const allocatedRamMb = nodeServers.reduce((sum, s) => sum + s.limits.ramMb, 0);
  const allocatedDiskGb = nodeServers.reduce((sum, s) => sum + s.limits.diskGb, 0);
  return {
    ...node,
    allocatedRamMb,
    allocatedDiskGb,
    serversCount: nodeServers.length,
  };
}

// Публичная витрина сайта WinHost (тарифы, ядра и доступность нод без авторизации)
app.get('/api/public/showcase', (_req, res) => {
  res.json({
    brand: 'WinHost',
    plans: store.data.plans,
    cores: CORE_CATALOG,
    nodes: store.data.nodes.map(enrichNode),
  });
});

// ============================================================================
// 1. АВТОРИЗАЦИЯ И ЗАЩИТА АККАУНТОВ (2FA TOTP, Scrypt, Rate-Limit)
// ============================================================================

app.post('/api/auth/login', (req, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  const password = String(req.body?.password || '');
  const ip = getClientIp(req);
  const rateKey = `${ip}:${email}`;

  const rl = checkRateLimit(rateKey);
  if (!rl.allowed) {
    res.status(429).json({
      error: `Слишком много попыток входа. Повторите через ${rl.retryAfterSec} сек.`,
    });
    return;
  }

  const user = store.data.users.find((u) => u.email.toLowerCase() === email);
  if (!user || !verifyPassword(password, user.passwordHash)) {
    recordFailedAttempt(rateKey);
    store.addAudit(email || 'unknown', ip, 'Неудачная попытка входа', 'WinHost Auth', 'warning');
    res.status(401).json({ error: 'Неверный email или пароль.' });
    return;
  }

  if (user.suspended) {
    res.status(403).json({ error: 'Ваш аккаунт приостановлен администратором.' });
    return;
  }

  clearFailedAttempts(rateKey);

  if (user.totpEnabled && user.totpSecret) {
    const challengeToken = signToken(
      { sub: user.id, email: user.email, role: user.role, purpose: '2fa_challenge' },
      300
    );
    res.json({
      requiresTwoFactor: true,
      challengeToken,
      email: user.email,
    });
    return;
  }

  user.lastLoginAt = new Date().toISOString();
  user.lastLoginIp = ip;
  store.addAudit(user.email, ip, 'Успешный вход в WinHost', 'Сессия открыта', 'info');

  const token = signToken({
    sub: user.id,
    email: user.email,
    role: user.role,
    purpose: 'session',
  });

  res.json({
    requiresTwoFactor: false,
    token,
    user: sanitizeUser(user),
  });
});

app.post('/api/auth/verify-2fa', (req, res) => {
  const challengeToken = String(req.body?.challengeToken || '');
  const code = String(req.body?.code || '').trim().toUpperCase();
  const ip = getClientIp(req);

  const payload = verifyToken(challengeToken);
  if (!payload || payload.purpose !== '2fa_challenge') {
    res.status(401).json({ error: 'Время проверки 2FA истекло. Войдите заново.' });
    return;
  }

  const rl = checkRateLimit(`2fa:${payload.sub}`);
  if (!rl.allowed) {
    res.status(429).json({
      error: `Превышен лимит ввода 2FA. Подождите ${rl.retryAfterSec} сек.`,
    });
    return;
  }

  const user = store.data.users.find((u) => u.id === payload.sub);
  if (!user || !user.totpSecret) {
    res.status(401).json({ error: 'Пользователь не найден.' });
    return;
  }

  let verified = verifyTotpCode(user.totpSecret, code);
  let usedRecovery = false;

  if (!verified && user.recoveryCodes.includes(code)) {
    verified = true;
    usedRecovery = true;
    user.recoveryCodes = user.recoveryCodes.filter((c) => c !== code);
  }

  if (!verified) {
    recordFailedAttempt(`2fa:${payload.sub}`);
    store.addAudit(user.email, ip, 'Неверный код 2FA', 'TOTP / Recovery', 'security');
    res.status(401).json({ error: 'Неверный 6-значный код 2FA или резервный код.' });
    return;
  }

  clearFailedAttempts(`2fa:${payload.sub}`);
  user.lastLoginAt = new Date().toISOString();
  user.lastLoginIp = ip;
  store.addAudit(
    user.email,
    ip,
    usedRecovery ? 'Вход по резервному коду 2FA' : 'Успешный вход с 2FA (TOTP)',
    'Двухфакторная защита',
    'security'
  );

  const token = signToken({
    sub: user.id,
    email: user.email,
    role: user.role,
    purpose: 'session',
  });

  res.json({
    token,
    user: sanitizeUser(user),
    usedRecovery,
  });
});

app.post('/api/auth/register', (req, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  const username = String(req.body?.username || '').trim();
  const password = String(req.body?.password || '');
  const ip = getClientIp(req);

  if (!email.includes('@') || username.length < 3) {
    res.status(400).json({ error: 'Укажите корректный email и имя пользователя (от 3 символов).' });
    return;
  }
  if (password.length < 8) {
    res.status(400).json({ error: 'Пароль должен содержать минимум 8 символов.' });
    return;
  }
  if (store.data.users.some((u) => u.email.toLowerCase() === email)) {
    res.status(409).json({ error: 'Пользователь с таким email уже зарегистрирован.' });
    return;
  }

  const newUser: StoredUser = {
    id: `usr-${crypto.randomUUID().slice(0, 8)}`,
    email,
    username,
    role: 'client',
    passwordHash: hashPassword(password),
    totpEnabled: false,
    totpSecret: null,
    pendingTotpSecret: null,
    recoveryCodes: [],
    maxServersQuota: 3,
    maxRamMbQuota: 24576,
    suspended: false,
    createdAt: new Date().toISOString(),
    lastLoginAt: new Date().toISOString(),
    lastLoginIp: ip,
  };

  store.data.users.push(newUser);
  store.addAudit(email, ip, 'Регистрация нового клиентского аккаунта', username, 'info');

  const token = signToken({
    sub: newUser.id,
    email: newUser.email,
    role: newUser.role,
    purpose: 'session',
  });

  res.status(201).json({
    token,
    user: sanitizeUser(newUser),
  });
});

app.get('/api/auth/me', requireAuth, (req: AuthedRequest, res) => {
  res.json({
    user: sanitizeUser(req.user!),
    hostTelemetry: engine.getHostTelemetry(),
  });
});

app.post('/api/account/password', requireAuth, (req: AuthedRequest, res) => {
  const currentPassword = String(req.body?.currentPassword || '');
  const newPassword = String(req.body?.newPassword || '');
  const user = req.user!;

  if (!verifyPassword(currentPassword, user.passwordHash)) {
    res.status(400).json({ error: 'Текущий пароль введён неверно.' });
    return;
  }
  if (newPassword.length < 8) {
    res.status(400).json({ error: 'Новый пароль должен содержать не менее 8 символов.' });
    return;
  }

  user.passwordHash = hashPassword(newPassword);
  store.addAudit(user.email, getClientIp(req), 'Смена пароля аккаунта', 'Account Security', 'security');
  res.json({ ok: true });
});

app.post('/api/account/2fa/setup', requireAuth, async (req: AuthedRequest, res) => {
  const user = req.user!;
  const secret = generateTotpSecret();
  user.pendingTotpSecret = secret;
  store.save();

  const otpauthUri = buildOtpAuthUri(user.email, secret, 'WinHost');
  const qrSvg = await generateQrSvg(otpauthUri);
  const currentPreviewCode = computeTotpCode(secret);

  res.json({
    secret,
    otpauthUri,
    qrSvg,
    currentPreviewCode,
  });
});

app.post('/api/account/2fa/enable', requireAuth, (req: AuthedRequest, res) => {
  const user = req.user!;
  const code = String(req.body?.code || '');

  if (!user.pendingTotpSecret) {
    res.status(400).json({ error: 'Сначала сгенерируйте секретный ключ 2FA.' });
    return;
  }
  if (!verifyTotpCode(user.pendingTotpSecret, code)) {
    res.status(400).json({ error: 'Неверный 6-значный код подтверждения. Проверьте время на устройстве.' });
    return;
  }

  const recoveryCodes = generateRecoveryCodes(8);
  user.totpSecret = user.pendingTotpSecret;
  user.pendingTotpSecret = null;
  user.totpEnabled = true;
  user.recoveryCodes = recoveryCodes;
  store.addAudit(user.email, getClientIp(req), 'Включена двухфакторная аутентификация (2FA TOTP)', 'Account Security', 'security');

  res.json({
    user: sanitizeUser(user),
    recoveryCodes,
  });
});

app.post('/api/account/2fa/disable', requireAuth, (req: AuthedRequest, res) => {
  const user = req.user!;
  const password = String(req.body?.password || '');

  if (!verifyPassword(password, user.passwordHash)) {
    res.status(400).json({ error: 'Для отключения 2FA необходимо ввести текущий пароль аккаунта.' });
    return;
  }

  user.totpEnabled = false;
  user.totpSecret = null;
  user.pendingTotpSecret = null;
  user.recoveryCodes = [];
  store.addAudit(user.email, getClientIp(req), 'Отключена двухфакторная аутентификация (2FA)', 'Account Security', 'warning');

  res.json({ user: sanitizeUser(user) });
});

// ============================================================================
// 2. КАТАЛОГ ЯДЕР, ПЛАГИНОВ, МОДОВ, ТАРИФОВ И НОД
// ============================================================================

app.get('/api/catalog', requireAuth, (_req, res) => {
  res.json({
    cores: CORE_CATALOG,
    addons: ADDONS_CATALOG,
    plans: store.data.plans,
    nodes: store.data.nodes.map(enrichNode),
  });
});

// ============================================================================
// 3. УПРАВЛЕНИЕ MINECRAFT-СЕРВЕРАМИ
// ============================================================================

app.get('/api/servers', requireAuth, (req: AuthedRequest, res) => {
  const user = req.user!;
  const visible = store.data.servers
    .filter((s) => canAccessServer(user, s))
    .map(enrichServer);
  res.json({ servers: visible });
});

app.post('/api/servers', requireAuth, async (req: AuthedRequest, res) => {
  const user = req.user!;
  const name = String(req.body?.name || '').trim();
  const description = String(req.body?.description || '').trim();
  const nodeId = String(req.body?.nodeId || '');
  const core = String(req.body?.core || 'paper') as ServerCore;
  const mcVersion = String(req.body?.mcVersion || '1.21.4');
  const jvmProfile = (req.body?.jvmProfile || 'aikar_g1') as JvmProfile;
  const cpuPercent = Math.max(50, Math.min(3200, Number(req.body?.cpuPercent || 200)));
  const ramMb = Math.max(1024, Math.min(131072, Number(req.body?.ramMb || 6144)));
  const diskGb = Math.max(10, Math.min(2000, Number(req.body?.diskGb || 40)));
  const ownerId =
    user.role === 'admin' && req.body?.ownerId ? String(req.body.ownerId) : user.id;

  if (name.length < 2) {
    res.status(400).json({ error: 'Название сервера должно содержать минимум 2 символа.' });
    return;
  }

  const targetOwner = store.data.users.find((u) => u.id === ownerId);
  if (!targetOwner) {
    res.status(404).json({ error: 'Владелец сервера не найден.' });
    return;
  }

  if (user.role !== 'admin') {
    const owned = store.data.servers.filter((s) => s.ownerId === user.id);
    if (owned.length >= user.maxServersQuota) {
      res.status(403).json({
        error: `Достигнут лимит серверов на вашем аккаунте (${user.maxServersQuota}).`,
      });
      return;
    }
    const usedRam = owned.reduce((acc, s) => acc + s.limits.ramMb, 0);
    if (usedRam + ramMb > user.maxRamMbQuota) {
      res.status(403).json({
        error: `Превышена квота DDR5 RAM вашего аккаунта: доступно ${
          user.maxRamMbQuota - usedRam
        } МБ.`,
      });
      return;
    }
  }

  const node = store.data.nodes.find((n) => n.id === nodeId);
  if (!node) {
    res.status(404).json({ error: 'Выбранная физическая нода не найдена.' });
    return;
  }
  if (node.status !== 'online') {
    res.status(400).json({ error: 'Нода находится на техническом обслуживании.' });
    return;
  }

  const enrichedNode = enrichNode(node);
  const maxAllowedNodeRam = Math.floor(
    node.ramTotalMb * (1 + node.ramOverallocatePercent / 100)
  );
  if ((enrichedNode.allocatedRamMb || 0) + ramMb > maxAllowedNodeRam) {
    res.status(400).json({
      error: `Недостаточно свободной DDR5 памяти на ноде ${node.name}.`,
    });
    return;
  }

  const coreDef = CORE_CATALOG.find((c) => c.id === core);
  const verDef = coreDef?.supportedVersions.find((v) => v.mcVersion === mcVersion);
  if (!coreDef || !verDef) {
    res.status(400).json({ error: 'Неподдерживаемая комбинация ядра и версии Minecraft.' });
    return;
  }

  let freeAlloc = store.data.allocations.find(
    (a) => a.nodeId === node.id && a.serverId === null && a.protocol === 'tcp+udp'
  );
  if (!freeAlloc) {
    const usedPorts = new Set(
      store.data.allocations.filter((a) => a.nodeId === node.id).map((a) => a.port)
    );
    let candidate = node.portRangeStart;
    while (usedPorts.has(candidate)) candidate++;
    freeAlloc = {
      id: `alloc-${crypto.randomUUID().slice(0, 8)}`,
      nodeId: node.id,
      ip: node.publicIp,
      port: candidate,
      protocol: 'tcp+udp',
      serverId: null,
      isPrimary: false,
      notes: 'Основной игровой порт',
    };
    store.data.allocations.push(freeAlloc);
  }

  const shortId = crypto.randomUUID().replace(/-/g, '').slice(0, 8);
  const srvId = `srv-${shortId}`;
  freeAlloc.serverId = srvId;
  freeAlloc.isPrimary = true;

  const newServer: MinecraftServer = {
    id: srvId,
    shortId,
    name,
    description: description || `${coreDef.name} ${mcVersion} сервер на DDR5 ECC`,
    ownerId: targetOwner.id,
    nodeId: node.id,
    core,
    mcVersion,
    coreBuild: verDef.latestBuild,
    javaVersion: verDef.recommendedJava,
    jvmProfile,
    customJvmArgs: '',
    status: 'offline',
    limits: {
      cpuPercent,
      ramMb,
      diskGb,
      backupsLimit: Number(req.body?.backupsLimit || 5),
      portsLimit: Number(req.body?.portsLimit || 4),
    },
    primaryAddress: freeAlloc.ip,
    primaryPort: freeAlloc.port,
    allocations: [freeAlloc],
    installedAddons: [],
    subusers: [],
    backupSchedule: {
      enabled: true,
      intervalHours: 12,
      keepCount: 5,
    },
    dockerContainerId: `winhost_${shortId}_${core}`,
    eulaAccepted: true,
    createdAt: new Date().toISOString(),
  };

  store.data.servers.unshift(newServer);
  engine.ensureServerFiles(newServer);
  store.addAudit(
    user.email,
    getClientIp(req),
    `Создан Minecraft-сервер «${name}»`,
    `${coreDef.name} ${mcVersion} • ${ramMb} МБ RAM @ ${node.name}`,
    'info'
  );

  if (req.body?.autoStart !== false) {
    await engine.powerAction(newServer, 'start');
  }

  res.status(201).json({ server: enrichServer(newServer) });
});

function loadAuthorizedServer(
  req: AuthedRequest,
  res: express.Response,
  perm?: ServerPermission
): MinecraftServer | null {
  const srv = store.data.servers.find((s) => s.id === req.params.id);
  if (!srv) {
    res.status(404).json({ error: 'Сервер не найден.' });
    return null;
  }
  if (!canAccessServer(req.user!, srv, perm)) {
    res.status(403).json({ error: 'Недостаточно прав для выполнения этого действия на сервере.' });
    return null;
  }
  return srv;
}

app.get('/api/servers/:id', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res);
  if (!srv) return;
  res.json({
    server: enrichServer(srv),
    startupCommand: buildJvmStartupCommand(srv),
    dockerSpec: buildDockerRunSpec(srv),
    logs: engine.getLogs(srv.id),
    metricsHistory: engine.getMetricsHistory(srv.id),
  });
});

app.delete('/api/servers/:id', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res);
  if (!srv) return;
  const user = req.user!;
  if (user.role !== 'admin' && srv.ownerId !== user.id) {
    res.status(403).json({ error: 'Удалить сервер может только владелец или администратор.' });
    return;
  }

  for (const alloc of store.data.allocations) {
    if (alloc.serverId === srv.id) {
      alloc.serverId = null;
      alloc.isPrimary = false;
    }
  }
  store.data.backups = store.data.backups.filter((b) => b.serverId !== srv.id);
  store.data.servers = store.data.servers.filter((s) => s.id !== srv.id);
  engine.removeServerFiles(srv.id);
  store.addAudit(user.email, getClientIp(req), 'Удалён сервер', `${srv.name} (${srv.shortId})`, 'warning');

  res.json({ ok: true });
});

app.post('/api/servers/:id/power', requireAuth, async (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'power.control');
  if (!srv) return;
  const action = String(req.body?.action || '') as 'start' | 'stop' | 'restart' | 'kill';
  if (!['start', 'stop', 'restart', 'kill'].includes(action)) {
    res.status(400).json({ error: 'Недопустимая команда питания.' });
    return;
  }
  await engine.powerAction(srv, action);
  res.json({
    server: enrichServer(srv),
    logs: engine.getLogs(srv.id),
  });
});

app.post('/api/servers/:id/command', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'console.send');
  if (!srv) return;
  const command = String(req.body?.command || '');
  engine.executeConsoleCommand(srv, command);
  res.json({
    ok: true,
    server: enrichServer(srv),
    logs: engine.getLogs(srv.id),
  });
});

app.post('/api/servers/:id/core', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'settings.manage');
  if (!srv) return;
  try {
    const core = String(req.body?.core || srv.core) as ServerCore;
    const mcVersion = String(req.body?.mcVersion || srv.mcVersion);
    const jvmProfile = req.body?.jvmProfile as JvmProfile | undefined;
    const customJvmArgs =
      typeof req.body?.customJvmArgs === 'string' ? req.body.customJvmArgs : undefined;

    engine.switchCoreAndVersion(srv, core, mcVersion, jvmProfile, customJvmArgs);
    store.addAudit(
      req.user!.email,
      getClientIp(req),
      `Смена ядра/версии сервера «${srv.name}»`,
      `${core.toUpperCase()} ${mcVersion}`,
      'info'
    );
    res.json({
      server: enrichServer(srv),
      startupCommand: buildJvmStartupCommand(srv),
      dockerSpec: buildDockerRunSpec(srv),
    });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.patch('/api/servers/:id/limits', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'settings.manage');
  if (!srv) return;
  const user = req.user!;
  if (user.role !== 'admin' && srv.ownerId !== user.id) {
    res.status(403).json({ error: 'Изменять лимиты ресурсов может только владелец или администратор.' });
    return;
  }

  const newRam = req.body?.ramMb ? Math.max(1024, Number(req.body.ramMb)) : srv.limits.ramMb;
  const newCpu = req.body?.cpuPercent
    ? Math.max(50, Number(req.body.cpuPercent))
    : srv.limits.cpuPercent;
  const newDisk = req.body?.diskGb ? Math.max(10, Number(req.body.diskGb)) : srv.limits.diskGb;

  if (user.role !== 'admin') {
    const otherRam = store.data.servers
      .filter((s) => s.ownerId === user.id && s.id !== srv.id)
      .reduce((acc, s) => acc + s.limits.ramMb, 0);
    if (otherRam + newRam > user.maxRamMbQuota) {
      res.status(403).json({ error: 'Превышена квота RAM вашего аккаунта.' });
      return;
    }
  }

  srv.limits.ramMb = newRam;
  srv.limits.cpuPercent = newCpu;
  srv.limits.diskGb = newDisk;
  if (req.body?.backupsLimit !== undefined) {
    srv.limits.backupsLimit = Math.max(1, Number(req.body.backupsLimit));
  }
  if (req.body?.portsLimit !== undefined) {
    srv.limits.portsLimit = Math.max(1, Number(req.body.portsLimit));
  }
  if (typeof req.body?.name === 'string' && req.body.name.trim().length >= 2) {
    srv.name = req.body.name.trim();
  }

  engine.writeStartupArtifacts(srv);
  store.save();

  res.json({
    server: enrichServer(srv),
    startupCommand: buildJvmStartupCommand(srv),
    dockerSpec: buildDockerRunSpec(srv),
  });
});

// ============================================================================
// 4. УПРАВЛЕНИЕ ФАЙЛАМИ СЕРВЕРА
// ============================================================================

app.get('/api/servers/:id/files', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.read');
  if (!srv) return;
  try {
    const rel = String(req.query.path || '');
    const entries = engine.listFiles(srv.id, rel);
    res.json({ path: rel, entries });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.get('/api/servers/:id/files/content', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.read');
  if (!srv) return;
  try {
    const rel = String(req.query.path || '');
    if (req.query.download === '1') {
      const abs = engine.resolveSafePath(srv.id, rel);
      res.download(abs, path.basename(abs));
      return;
    }
    const content = engine.readFileContent(srv.id, rel);
    res.json({ path: rel, content });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.put('/api/servers/:id/files/content', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.write');
  if (!srv) return;
  try {
    const rel = String(req.body?.path || '');
    const content = String(req.body?.content ?? '');
    engine.writeFileContent(srv.id, rel, content);
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.post('/api/servers/:id/files/mkdir', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.write');
  if (!srv) return;
  try {
    const rel = String(req.body?.path || '');
    engine.createDirectory(srv.id, rel);
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.post('/api/servers/:id/files/rename', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.write');
  if (!srv) return;
  try {
    engine.renameEntry(srv.id, String(req.body?.from || ''), String(req.body?.to || ''));
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.delete('/api/servers/:id/files', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'files.write');
  if (!srv) return;
  try {
    engine.deleteEntry(srv.id, String(req.query.path || ''));
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

// ============================================================================
// 5. ПЛАГИНЫ И МОДЫ
// ============================================================================

app.post('/api/servers/:id/addons/install', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'addons.manage');
  if (!srv) return;
  try {
    const catalogId = String(req.body?.catalogId || '');
    const item = ADDONS_CATALOG.find((a) => a.id === catalogId);
    if (!item) {
      res.status(404).json({ error: 'Плагин или мод не найден в каталоге.' });
      return;
    }
    const installed = engine.installCatalogAddon(srv, item);
    res.status(201).json({ addon: installed, server: enrichServer(srv) });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.post('/api/servers/:id/addons/:addonId/toggle', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'addons.manage');
  if (!srv) return;
  try {
    engine.toggleAddon(srv, req.params.addonId);
    res.json({ server: enrichServer(srv) });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.delete('/api/servers/:id/addons/:addonId', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'addons.manage');
  if (!srv) return;
  try {
    engine.removeAddon(srv, req.params.addonId);
    res.json({ server: enrichServer(srv) });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

// ============================================================================
// 6. АВТОМАТИЧЕСКИЕ И РУЧНЫЕ РЕЗЕРВНЫЕ КОПИИ
// ============================================================================

app.get('/api/servers/:id/backups', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  const backups = store.data.backups.filter((b) => b.serverId === srv.id);
  res.json({ backups, schedule: srv.backupSchedule });
});

app.post('/api/servers/:id/backups', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  try {
    const backup = engine.createBackup(srv, String(req.body?.name || ''), false);
    res.status(201).json({ backup });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.patch('/api/servers/:id/backups/schedule', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  srv.backupSchedule.enabled = Boolean(req.body?.enabled);
  const hours = Number(req.body?.intervalHours);
  if (hours === 6 || hours === 12 || hours === 24) {
    srv.backupSchedule.intervalHours = hours;
  }
  if (req.body?.keepCount) {
    srv.backupSchedule.keepCount = Math.max(1, Math.min(20, Number(req.body.keepCount)));
  }
  store.save();
  res.json({ schedule: srv.backupSchedule });
});

app.post('/api/servers/:id/backups/:backupId/lock', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  const bkp = store.data.backups.find(
    (b) => b.id === req.params.backupId && b.serverId === srv.id
  );
  if (!bkp) {
    res.status(404).json({ error: 'Резервная копия не найдена.' });
    return;
  }
  bkp.locked = !bkp.locked;
  store.save();
  res.json({ backup: bkp });
});

app.post('/api/servers/:id/backups/:backupId/restore', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  try {
    engine.restoreBackup(srv, req.params.backupId);
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

app.get('/api/servers/:id/backups/:backupId/download', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  const bkp = store.data.backups.find(
    (b) => b.id === req.params.backupId && b.serverId === srv.id
  );
  if (!bkp) {
    res.status(404).json({ error: 'Копия не найдена.' });
    return;
  }
  const archive = engine.getBackupArchivePath(srv.id, bkp.fileName);
  if (!fs.existsSync(archive)) {
    res.status(404).json({ error: 'Архив на диске не найден.' });
    return;
  }
  res.download(archive, bkp.fileName);
});

app.delete('/api/servers/:id/backups/:backupId', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'backups.manage');
  if (!srv) return;
  const bkp = store.data.backups.find(
    (b) => b.id === req.params.backupId && b.serverId === srv.id
  );
  if (bkp?.locked) {
    res.status(400).json({ error: 'Снимите защиту (замок) перед удалением этой резервной копии.' });
    return;
  }
  try {
    engine.deleteBackup(srv, req.params.backupId);
    res.json({ ok: true });
  } catch (err) {
    res.status(400).json({ error: (err as Error).message });
  }
});

// ============================================================================
// 7. УПРАВЛЕНИЕ ПОРТАМИ (ALLOCATIONS) И СУБ-ПОЛЬЗОВАТЕЛЯМИ
// ============================================================================

app.post('/api/servers/:id/ports', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'ports.manage');
  if (!srv) return;

  const currentAllocs = store.data.allocations.filter((a) => a.serverId === srv.id);
  if (currentAllocs.length >= srv.limits.portsLimit) {
    res.status(400).json({
      error: `Достигнут лимит выделенных портов тарифа (${srv.limits.portsLimit}).`,
    });
    return;
  }

  const notes = String(req.body?.notes || 'Доп. порт (VoiceChat / RCON / Geyser)').trim();
  const protocol = (req.body?.protocol || 'tcp+udp') as PortAllocation['protocol'];
  const requestedPort = req.body?.port ? Number(req.body.port) : null;

  const node = store.data.nodes.find((n) => n.id === srv.nodeId)!;
  let alloc: PortAllocation | undefined;

  if (requestedPort) {
    const existing = store.data.allocations.find(
      (a) => a.nodeId === node.id && a.port === requestedPort
    );
    if (existing && existing.serverId !== null) {
      res.status(409).json({ error: `Порт ${requestedPort} уже занят на ноде ${node.name}.` });
      return;
    }
    if (existing) {
      alloc = existing;
    } else {
      alloc = {
        id: `alloc-${crypto.randomUUID().slice(0, 8)}`,
        nodeId: node.id,
        ip: node.publicIp,
        port: requestedPort,
        protocol,
        serverId: null,
        isPrimary: false,
        notes,
      };
      store.data.allocations.push(alloc);
    }
  } else {
    alloc = store.data.allocations.find((a) => a.nodeId === node.id && a.serverId === null);
    if (!alloc) {
      const used = new Set(
        store.data.allocations.filter((a) => a.nodeId === node.id).map((a) => a.port)
      );
      let nextPort = node.portRangeStart;
      while (used.has(nextPort)) nextPort++;
      alloc = {
        id: `alloc-${crypto.randomUUID().slice(0, 8)}`,
        nodeId: node.id,
        ip: node.publicIp,
        port: nextPort,
        protocol,
        serverId: null,
        isPrimary: false,
        notes,
      };
      store.data.allocations.push(alloc);
    }
  }

  alloc.serverId = srv.id;
  alloc.notes = notes;
  alloc.protocol = protocol;
  engine.writeStartupArtifacts(srv);
  store.save();

  res.status(201).json({ server: enrichServer(srv) });
});

app.post('/api/servers/:id/ports/:allocId/primary', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'ports.manage');
  if (!srv) return;
  const serverAllocs = store.data.allocations.filter((a) => a.serverId === srv.id);
  const target = serverAllocs.find((a) => a.id === req.params.allocId);
  if (!target) {
    res.status(404).json({ error: 'Порт не найден.' });
    return;
  }

  for (const a of serverAllocs) {
    a.isPrimary = a.id === target.id;
  }
  srv.primaryPort = target.port;
  srv.primaryAddress = target.ip;

  try {
    const propsFile = engine.resolveSafePath(srv.id, 'server.properties');
    if (fs.existsSync(propsFile)) {
      const updated = fs
        .readFileSync(propsFile, 'utf8')
        .replace(/server-port=\d+/, `server-port=${target.port}`);
      fs.writeFileSync(propsFile, updated, 'utf8');
    }
  } catch {
    // ignore
  }

  engine.writeStartupArtifacts(srv);
  store.save();
  res.json({ server: enrichServer(srv) });
});

app.delete('/api/servers/:id/ports/:allocId', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'ports.manage');
  if (!srv) return;
  const target = store.data.allocations.find(
    (a) => a.id === req.params.allocId && a.serverId === srv.id
  );
  if (!target) {
    res.status(404).json({ error: 'Порт не найден.' });
    return;
  }
  if (target.isPrimary) {
    res.status(400).json({ error: 'Нельзя отвязать основной игровой порт сервера.' });
    return;
  }

  target.serverId = null;
  target.isPrimary = false;
  engine.writeStartupArtifacts(srv);
  store.save();
  res.json({ server: enrichServer(srv) });
});

app.post('/api/servers/:id/subusers', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'subusers.manage');
  if (!srv) return;
  const email = String(req.body?.email || '').trim().toLowerCase();
  const permissions = (Array.isArray(req.body?.permissions)
    ? req.body.permissions
    : ['console.read', 'power.control']) as ServerPermission[];

  if (!email.includes('@')) {
    res.status(400).json({ error: 'Введите корректный email пользователя.' });
    return;
  }
  if (srv.subusers.some((s) => s.email.toLowerCase() === email)) {
    res.status(409).json({ error: 'Этот пользователь уже добавлен к серверу.' });
    return;
  }

  const matchedUser = store.data.users.find((u) => u.email.toLowerCase() === email);
  srv.subusers.push({
    id: `sub-${crypto.randomUUID().slice(0, 8)}`,
    userId: matchedUser ? matchedUser.id : `pending-${email}`,
    email,
    username: matchedUser ? matchedUser.username : email.split('@')[0],
    permissions,
    addedAt: new Date().toISOString(),
  });
  store.save();

  res.status(201).json({ server: enrichServer(srv) });
});

app.delete('/api/servers/:id/subusers/:subId', requireAuth, (req: AuthedRequest, res) => {
  const srv = loadAuthorizedServer(req, res, 'subusers.manage');
  if (!srv) return;
  srv.subusers = srv.subusers.filter((s) => s.id !== req.params.subId);
  store.save();
  res.json({ server: enrichServer(srv) });
});

// ============================================================================
// 8. ПАНЕЛЬ АДМИНИСТРАТОРА WINHOST
// ============================================================================

app.get('/api/admin/overview', requireAuth, requireAdmin, (_req, res) => {
  res.json({
    nodes: store.data.nodes.map(enrichNode),
    users: store.data.users.map(sanitizeUser),
    servers: store.data.servers.map(enrichServer),
    plans: store.data.plans,
    auditLogs: store.data.auditLogs,
    hostTelemetry: engine.getHostTelemetry(),
  });
});

app.post('/api/admin/nodes', requireAuth, requireAdmin, (req: AuthedRequest, res) => {
  const name = String(req.body?.name || '').trim();
  const location = String(req.body?.location || '').trim();
  const fqdn = String(req.body?.fqdn || '').trim();
  const publicIp = String(req.body?.publicIp || '').trim();
  const cpuModel = String(req.body?.cpuModel || 'AMD Ryzen Threadripper PRO 9975WX').trim();

  if (!name || !fqdn || !publicIp) {
    res.status(400).json({ error: 'Заполните название ноды, FQDN и публичный IP.' });
    return;
  }

  const portStart = Number(req.body?.portRangeStart || 25565);
  const portEnd = Number(req.body?.portRangeEnd || portStart + 20);
  const rawToken = `winh_live_${crypto.randomBytes(18).toString('hex')}`;

  const node: ComputeNode = {
    id: `node-${crypto.randomUUID().slice(0, 8)}`,
    name,
    location: location || 'Европа (Tier III+)',
    fqdn,
    publicIp,
    cpuModel,
    cpuCores: Number(req.body?.cpuCores || 32),
    cpuThreads: Number(req.body?.cpuThreads || 64),
    boostClockGhz: Number(req.body?.boostClockGhz || 5.4),
    ramTotalMb: Number(req.body?.ramTotalMb || 262144),
    ramType: String(req.body?.ramType || 'DDR5-6400 ECC RDIMM'),
    ramOverallocatePercent: Number(req.body?.ramOverallocatePercent || 0),
    diskTotalGb: Number(req.body?.diskTotalGb || 4000),
    diskType: String(req.body?.diskType || 'Enterprise NVMe PCIe 5.0 RAID-1'),
    networkSpeedGbps: Number(req.body?.networkSpeedGbps || 25),
    dockerMode: 'remote_wings_tls',
    status: 'online',
    daemonTokenPreview: `${rawToken.slice(0, 14)}...${rawToken.slice(-4)}`,
    portRangeStart: portStart,
    portRangeEnd: portEnd,
    createdAt: new Date().toISOString(),
  };

  store.data.nodes.push(node);
  for (let p = portStart; p <= Math.min(portEnd, portStart + 30); p++) {
    store.data.allocations.push({
      id: `alloc-${crypto.randomUUID().slice(0, 8)}`,
      nodeId: node.id,
      ip: node.publicIp,
      port: p,
      protocol: 'tcp+udp',
      serverId: null,
      isPrimary: false,
      notes: '',
    });
  }

  store.addAudit(
    req.user!.email,
    getClientIp(req),
    'Подключён новый физический сервер в кластер WinHost',
    `${node.name} (${node.publicIp})`,
    'info'
  );

  res.status(201).json({
    node: enrichNode(node),
    daemonInstallToken: rawToken,
  });
});

app.patch('/api/admin/nodes/:nodeId', requireAuth, requireAdmin, (req: AuthedRequest, res) => {
  const node = store.data.nodes.find((n) => n.id === req.params.nodeId);
  if (!node) {
    res.status(404).json({ error: 'Нода не найдена.' });
    return;
  }
  if (req.body?.status === 'online' || req.body?.status === 'maintenance') {
    node.status = req.body.status;
  }
  if (req.body?.ramOverallocatePercent !== undefined) {
    node.ramOverallocatePercent = Math.max(0, Math.min(50, Number(req.body.ramOverallocatePercent)));
  }
  store.save();
  res.json({ node: enrichNode(node) });
});

app.post('/api/admin/users', requireAuth, requireAdmin, (req: AuthedRequest, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  const username = String(req.body?.username || '').trim();
  const password = String(req.body?.password || '');
  const role = req.body?.role === 'admin' ? 'admin' : 'client';

  if (!email.includes('@') || username.length < 2 || password.length < 8) {
    res.status(400).json({ error: 'Проверьте email, имя пользователя и пароль (от 8 символов).' });
    return;
  }
  if (store.data.users.some((u) => u.email.toLowerCase() === email)) {
    res.status(409).json({ error: 'Email уже занят.' });
    return;
  }

  const u: StoredUser = {
    id: `usr-${crypto.randomUUID().slice(0, 8)}`,
    email,
    username,
    role,
    passwordHash: hashPassword(password),
    totpEnabled: false,
    totpSecret: null,
    pendingTotpSecret: null,
    recoveryCodes: [],
    maxServersQuota: Number(req.body?.maxServersQuota || 5),
    maxRamMbQuota: Number(req.body?.maxRamMbQuota || 32768),
    suspended: false,
    createdAt: new Date().toISOString(),
  };

  store.data.users.push(u);
  store.addAudit(req.user!.email, getClientIp(req), 'Создан аккаунт клиента/админа', `${email} (${role})`, 'info');
  res.status(201).json({ user: sanitizeUser(u) });
});

app.patch('/api/admin/users/:userId', requireAuth, requireAdmin, (req: AuthedRequest, res) => {
  const target = store.data.users.find((u) => u.id === req.params.userId);
  if (!target) {
    res.status(404).json({ error: 'Пользователь не найден.' });
    return;
  }
  if (typeof req.body?.suspended === 'boolean') {
    if (target.id === req.user!.id && req.body.suspended) {
      res.status(400).json({ error: 'Нельзя заблокировать собственный административный аккаунт.' });
      return;
    }
    target.suspended = req.body.suspended;
  }
  if (req.body?.maxServersQuota !== undefined) {
    target.maxServersQuota = Math.max(0, Number(req.body.maxServersQuota));
  }
  if (req.body?.maxRamMbQuota !== undefined) {
    target.maxRamMbQuota = Math.max(1024, Number(req.body.maxRamMbQuota));
  }
  store.save();
  res.json({ user: sanitizeUser(target) });
});
