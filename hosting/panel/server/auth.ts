import crypto from 'node:crypto';
import type { Request, Response, NextFunction } from 'express';
import type { MinecraftServer, ServerPermission, UserPublic, UserRole } from '../src/types.js';

const JWT_SECRET =
  process.env.JWT_SECRET || 'winhost-dev-hmac-secret-key-ddr5-2026-change-in-prod';

export interface StoredUser extends UserPublic {
  passwordHash: string;
  totpSecret: string | null;
  pendingTotpSecret: string | null;
  recoveryCodes: string[];
}

export interface TokenPayload {
  sub: string;
  email: string;
  role: UserRole;
  purpose: 'session' | '2fa_challenge';
  exp: number;
}

export function hashPassword(password: string): string {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(password, salt, 64).toString('hex');
  return `scrypt$${salt}$${derived}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const parts = stored.split('$');
  if (parts.length !== 3 || parts[0] !== 'scrypt') return false;
  const [, salt, hashHex] = parts;
  const derived = crypto.scryptSync(password, salt, 64);
  const expected = Buffer.from(hashHex, 'hex');
  if (derived.length !== expected.length) return false;
  return crypto.timingSafeEqual(derived, expected);
}

export function signToken(
  payload: Omit<TokenPayload, 'exp'>,
  expiresInSeconds = 60 * 60 * 24 * 7
): string {
  const fullPayload: TokenPayload = {
    ...payload,
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  };
  const body = Buffer.from(JSON.stringify(fullPayload)).toString('base64url');
  const sig = crypto.createHmac('sha256', JWT_SECRET).update(body).digest('base64url');
  return `${body}.${sig}`;
}

export function verifyToken(token: string): TokenPayload | null {
  if (!token || !token.includes('.')) return null;
  const [body, sig] = token.split('.');
  if (!body || !sig) return null;

  const expectedSig = crypto.createHmac('sha256', JWT_SECRET).update(body).digest('base64url');
  const a = Buffer.from(sig);
  const b = Buffer.from(expectedSig);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return null;
  }

  try {
    const decoded = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as TokenPayload;
    if (decoded.exp < Math.floor(Date.now() / 1000)) {
      return null;
    }
    return decoded;
  } catch {
    return null;
  }
}

export function sanitizeUser(u: StoredUser): UserPublic {
  return {
    id: u.id,
    email: u.email,
    username: u.username,
    role: u.role,
    totpEnabled: u.totpEnabled,
    maxServersQuota: u.maxServersQuota,
    maxRamMbQuota: u.maxRamMbQuota,
    suspended: u.suspended,
    createdAt: u.createdAt,
    lastLoginAt: u.lastLoginAt,
    lastLoginIp: u.lastLoginIp,
  };
}

// Защита аккаунтов от перебора паролей и 2FA-кодов (Rate Limiter в памяти)
interface LoginAttemptRecord {
  count: number;
  lockedUntil: number;
}
const loginAttempts = new Map<string, LoginAttemptRecord>();

export function checkRateLimit(key: string): { allowed: boolean; retryAfterSec: number } {
  const rec = loginAttempts.get(key);
  if (!rec) return { allowed: true, retryAfterSec: 0 };
  const now = Date.now();
  if (rec.lockedUntil > now) {
    return { allowed: false, retryAfterSec: Math.ceil((rec.lockedUntil - now) / 1000) };
  }
  if (rec.lockedUntil !== 0 && rec.lockedUntil <= now) {
    loginAttempts.delete(key);
  }
  return { allowed: true, retryAfterSec: 0 };
}

export function recordFailedAttempt(key: string): void {
  const now = Date.now();
  const rec = loginAttempts.get(key) || { count: 0, lockedUntil: 0 };
  rec.count += 1;
  if (rec.count >= 6) {
    rec.lockedUntil = now + 5 * 60 * 1000; // Блокировка на 5 минут после 6 неудачных попыток
  }
  loginAttempts.set(key, rec);
}

export function clearFailedAttempts(key: string): void {
  loginAttempts.delete(key);
}

export interface AuthedRequest extends Request {
  user?: StoredUser;
}

export function makeAuthMiddleware(getUserById: (id: string) => StoredUser | undefined) {
  const requireAuth = (req: AuthedRequest, res: Response, next: NextFunction) => {
    const header = req.headers.authorization || '';
    const queryToken = typeof req.query.token === 'string' ? req.query.token : '';
    const rawToken = header.startsWith('Bearer ') ? header.slice(7) : queryToken;

    const payload = verifyToken(rawToken);
    if (!payload || payload.purpose !== 'session') {
      res.status(401).json({ error: 'Требуется авторизация в панели управления.' });
      return;
    }

    const user = getUserById(payload.sub);
    if (!user) {
      res.status(401).json({ error: 'Сессия устарела или пользователь удалён.' });
      return;
    }
    if (user.suspended) {
      res.status(403).json({ error: 'Аккаунт приостановлен администратором хостинга.' });
      return;
    }

    req.user = user;
    next();
  };

  const requireAdmin = (req: AuthedRequest, res: Response, next: NextFunction) => {
    if (!req.user || req.user.role !== 'admin') {
      res.status(403).json({ error: 'Доступ разрешён только администраторам платформы.' });
      return;
    }
    next();
  };

  return { requireAuth, requireAdmin };
}

export function canAccessServer(
  user: StoredUser,
  server: MinecraftServer,
  permission?: ServerPermission
): boolean {
  if (user.role === 'admin' || server.ownerId === user.id) {
    return true;
  }
  const sub = server.subusers.find(
    (s) => s.userId === user.id || s.email.toLowerCase() === user.email.toLowerCase()
  );
  if (!sub) return false;
  if (!permission) return true;
  return sub.permissions.includes(permission);
}
