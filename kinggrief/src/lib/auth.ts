import { cookies, headers } from 'next/headers';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { getAdminById, getUserById } from './repo';
import type { AdminUser, PublicUser } from './types';

export const ADMIN_COOKIE = 'kg_admin';
export const CSRF_COOKIE = 'kg_csrf';
export const USER_COOKIE = 'kg_user';

const ADMIN_TTL = 12 * 60 * 60 * 1000;
const USER_TTL = 30 * 24 * 60 * 60 * 1000;

function secret() {
  return process.env.ADMIN_SESSION_SECRET || 'kinggrief-dev-secret-change-me';
}

/* ─────────────────────── подписанные токены ─────────────────────── */

type TokenPayload = { sub: number; kind: 'admin' | 'user'; exp: number };

function sign(value: string) {
  return createHmac('sha256', secret()).update(value).digest('base64url');
}

/**
 * Сессия хранится в самом cookie как HMAC-подписанный токен.
 * Это работает и на нескольких инстансах сервера (serverless), где общая БД недоступна.
 */
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

/**
 * Cookie-настройки под текущий запрос.
 *
 * В превью и на части хостингов сайт открывается внутри iframe на другом домене —
 * браузер не сохранит обычную SameSite=Lax cookie в стороннем контексте, и вход
 * «срабатывает», но сессия не приходит со следующим запросом. Поэтому при HTTPS
 * ставим SameSite=None; Secure, а на локальном HTTP — обычный Lax.
 */
async function cookieMode(): Promise<{ secure: boolean; sameSite: 'none' | 'lax' }> {
  if (process.env.COOKIE_SECURE === 'false') return { secure: false, sameSite: 'lax' };
  if (process.env.COOKIE_SECURE === 'true') return { secure: true, sameSite: 'none' };
  try {
    const h = await headers();
    const proto = (h.get('x-forwarded-proto') || '').split(',')[0].trim().toLowerCase();
    const forwarded = (h.get('forwarded') || '').toLowerCase();
    const origin = (h.get('origin') || '').toLowerCase();
    const viaHttps = proto === 'https' || forwarded.includes('proto=https') || origin.startsWith('https://');
    if (viaHttps) return { secure: true, sameSite: 'none' };
    if (proto === 'http') return { secure: false, sameSite: 'lax' };
  } catch {
    /* вне запроса */
  }
  return process.env.NODE_ENV === 'production'
    ? { secure: true, sameSite: 'none' }
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
  if (!admin) return null;
  return { id: admin.id, username: admin.username, role: admin.role, totpEnabled: admin.totpEnabled };
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
  const header = req.headers.get('x-csrf-token') || '';
  return safeEqual(header, csrfTokenFor(token));
}

export function sameOrigin(req: Request): boolean {
  const origin = req.headers.get('origin');
  if (!origin) return true;
  try {
    const originHost = new URL(origin).host;
    // За reverse-proxy Host может быть внутренним, а Origin — публичным доменом.
    const allowed = [req.headers.get('host'), req.headers.get('x-forwarded-host')]
      .filter((value): value is string => Boolean(value))
      .map((value) => value.split(',')[0].trim());
    return allowed.includes(originHost);
  } catch {
    return false;
  }
}

export async function guardAdmin(req: Request, opts: { csrf?: boolean } = {}) {
  const admin = await currentAdmin();
  if (!admin) return { ok: false as const, status: 401, error: 'Требуется авторизация' };
  if (opts.csrf) {
    if (!sameOrigin(req)) return { ok: false as const, status: 403, error: 'Неверный источник запроса' };
    if (!(await verifyCsrf(req))) return { ok: false as const, status: 403, error: 'CSRF-токен недействителен' };
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
