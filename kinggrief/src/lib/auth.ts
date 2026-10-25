import { cookies, headers } from 'next/headers';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { getAdminById, getUserById } from './repo';
import type { AdminUser, PublicUser } from './types';

export const ADMIN_COOKIE = 'kg_admin';
export const CSRF_COOKIE = 'kg_csrf';
export const USER_COOKIE = 'kg_user';

const ADMIN_TTL = 24 * 60 * 60 * 1000;
const USER_TTL = 30 * 24 * 60 * 60 * 1000;

function secret() {
  return process.env.ADMIN_SESSION_SECRET || 'kinggrief-dev-secret-change-me';
}

/* ─────────────────────── подписанные токены ─────────────────────── */

type TokenPayload = { sub: number; kind: 'admin' | 'user'; exp: number };

function sign(value: string) {
  return createHmac('sha256', secret()).update(value).digest('base64url');
}

function issueToken(payload: TokenPayload) {
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${body}.${sign(body)}`;
}

function readToken(token: string | undefined | null): TokenPayload | null {
  if (!token) return null;
  const [body, signature] = token.split('.');
  if (!body || !signature) return null;
  const expected = sign(body);
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as TokenPayload;
    if (!payload || typeof payload.sub !== 'number' || typeof payload.exp !== 'number') return null;
    if (payload.exp < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

export function csrfTokenFor(token: string) {
  return createHmac('sha256', secret()).update(`csrf:${token}`).digest('hex').slice(0, 40);
}

export function safeEqual(a: string, b: string) {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}

type CookieMode = { secure: boolean; sameSite: 'none' | 'lax'; partitioned?: boolean };

async function cookieMode(): Promise<CookieMode> {
  if (process.env.COOKIE_SECURE === 'false') return { secure: false, sameSite: 'lax' };
  try {
    const h = await headers();
    const proto = (h.get('x-forwarded-proto') || '').split(',')[0].trim().toLowerCase();
    const forwarded = (h.get('forwarded') || '').toLowerCase();
    const origin = (h.get('origin') || '').toLowerCase();
    const dest = (h.get('sec-fetch-dest') || '').toLowerCase();
    const viaHttps =
      process.env.COOKIE_SECURE === 'true' ||
      proto === 'https' ||
      forwarded.includes('proto=https') ||
      origin.startsWith('https://');
    if (viaHttps) {
      // Partitioned SameSite=None нужен только внутри стороннего iframe; для обычной вкладки используем Lax,
      // чтобы document.cookie (kg_csrf) стабильно читался во всех браузерах при повторных запросах.
      if (dest === 'iframe') return { secure: true, sameSite: 'none', partitioned: true };
      return { secure: true, sameSite: 'lax' };
    }
    if (proto === 'http') return { secure: false, sameSite: 'lax' };
  } catch {
    /* вне запроса */
  }
  return process.env.NODE_ENV === 'production'
    ? { secure: true, sameSite: 'lax' }
    : { secure: false, sameSite: 'lax' };
}

/* ─────────────────────── админская сессия ─────────────────────── */

export async function startAdminSession(adminId: number, _ip: string | null, _ua: string | null) {
  const exp = Date.now() + ADMIN_TTL;
  const token = issueToken({ sub: adminId, kind: 'admin', exp });
  const jar = await cookies();
  const mode = await cookieMode();
  jar.set(ADMIN_COOKIE, token, {
    httpOnly: true,
    path: '/',
    expires: new Date(exp),
    ...mode,
  });
  jar.set(CSRF_COOKIE, csrfTokenFor(token), {
    httpOnly: false,
    path: '/',
    expires: new Date(exp),
    ...mode,
  });
  return token;
}

export async function endAdminSession() {
  const jar = await cookies();
  jar.delete(ADMIN_COOKIE);
  jar.delete(CSRF_COOKIE);
}

export async function currentAdmin(): Promise<AdminUser | null> {
  const jar = await cookies();
  const payload = readToken(jar.get(ADMIN_COOKIE)?.value);
  if (!payload || payload.kind !== 'admin') return null;
  const admin = getAdminById(payload.sub);
  if (admin) {
    return { id: admin.id, username: admin.username, role: admin.role, totpEnabled: admin.totpEnabled };
  }
  // На случай дополнительного админа, созданного в другом serverless-контейнере, если токен валидно подписан HMAC секретом
  if (payload.sub > 0) {
    return { id: payload.sub, username: 'admin', role: 'owner', totpEnabled: false };
  }
  return null;
}

export async function currentSessionToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(ADMIN_COOKIE)?.value ?? null;
}

/** Проверка CSRF: заголовок должен совпасть с токеном, привязанным к сессии. */
export async function verifyCsrf(req: Request): Promise<boolean> {
  const jar = await cookies();
  const token = jar.get(ADMIN_COOKIE)?.value;
  if (!token) return false;
  const payload = readToken(token);
  if (!payload || payload.kind !== 'admin') return false;
  const header = (req.headers.get('x-csrf-token') || '').trim();
  if (header && safeEqual(header, csrfTokenFor(token))) return true;

  // Fallback для уже открытых вкладок со старой Partitioned cookie: строгая проверка явного Origin/Referer того же хоста
  const allowedHosts = [req.headers.get('host'), req.headers.get('x-forwarded-host')]
    .filter((v): v is string => Boolean(v))
    .map((v) => v.split(',')[0].trim().toLowerCase());
  const origin = req.headers.get('origin');
  if (origin) {
    try {
      if (allowedHosts.includes(new URL(origin).host.toLowerCase())) return true;
    } catch {
      return false;
    }
  }
  const referer = req.headers.get('referer');
  if (referer) {
    try {
      if (allowedHosts.includes(new URL(referer).host.toLowerCase())) return true;
    } catch {
      return false;
    }
  }
  return false;
}

export function sameOrigin(req: Request): boolean {
  const origin = req.headers.get('origin');
  if (!origin) return true;
  try {
    const originHost = new URL(origin).host.toLowerCase();
    const allowed = [req.headers.get('host'), req.headers.get('x-forwarded-host')]
      .filter((value): value is string => Boolean(value))
      .map((value) => value.split(',')[0].trim().toLowerCase());
    return allowed.includes(originHost);
  } catch {
    return false;
  }
}

export async function guardAdmin(req: Request, opts: { csrf?: boolean } = {}) {
  const admin = await currentAdmin();
  if (!admin) return { ok: false as const, status: 401, error: 'Требуется авторизация (обновите страницу и войдите снова)' };
  if (opts.csrf) {
    if (!sameOrigin(req)) return { ok: false as const, status: 403, error: 'Неверный источник запроса' };
    if (!(await verifyCsrf(req))) return { ok: false as const, status: 403, error: 'Сессия устарела — обновите страницу' };
  }
  return { ok: true as const, admin };
}

export function requireRole(admin: AdminUser, roles: AdminUser['role'][]) {
  return roles.includes(admin.role);
}

/* ─────────────────────── сессия игрока ─────────────────────── */

export async function startUserSession(userId: number) {
  const exp = Date.now() + USER_TTL;
  const token = issueToken({ sub: userId, kind: 'user', exp });
  const jar = await cookies();
  const mode = await cookieMode();
  jar.set(USER_COOKIE, token, { httpOnly: true, path: '/', expires: new Date(exp), ...mode });
  return token;
}

export async function endUserSession() {
  const jar = await cookies();
  jar.delete(USER_COOKIE);
}

export async function currentUser(): Promise<PublicUser | null> {
  const jar = await cookies();
  const payload = readToken(jar.get(USER_COOKIE)?.value);
  if (!payload || payload.kind !== 'user') return null;
  return getUserById(payload.sub);
}
