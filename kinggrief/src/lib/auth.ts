import { cookies } from 'next/headers';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { createSession, deleteSession, getSession } from './repo';
import type { AdminUser } from './types';

export const ADMIN_COOKIE = 'kg_admin';

/** Secure-cookie флаг: по умолчанию включён в production, но отключается для локального http. */
function cookieSecure() {
  if (process.env.COOKIE_SECURE === 'false') return false;
  if (process.env.COOKIE_SECURE === 'true') return true;
  return process.env.NODE_ENV === 'production';
}
export const CSRF_COOKIE = 'kg_csrf';

function secret() {
  return process.env.ADMIN_SESSION_SECRET || 'kinggrief-dev-secret-change-me';
}

export function csrfTokenFor(sessionId: string) {
  return createHmac('sha256', secret()).update(sessionId).digest('hex').slice(0, 40);
}

export function safeEqual(a: string, b: string) {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}

export async function startAdminSession(adminId: number, ip: string | null, ua: string | null) {
  const { id, expires } = createSession(adminId, ip, ua);
  const jar = await cookies();
  jar.set(ADMIN_COOKIE, id, {
    httpOnly: true,
    sameSite: 'lax',
    secure: cookieSecure(),
    path: '/',
    expires: new Date(expires),
  });
  jar.set(CSRF_COOKIE, csrfTokenFor(id), {
    httpOnly: false,
    sameSite: 'strict',
    secure: cookieSecure(),
    path: '/',
    expires: new Date(expires),
  });
  return id;
}

export async function endAdminSession() {
  const jar = await cookies();
  const token = jar.get(ADMIN_COOKIE)?.value;
  if (token) deleteSession(token);
  jar.delete(ADMIN_COOKIE);
  jar.delete(CSRF_COOKIE);
}

export async function currentAdmin(): Promise<AdminUser | null> {
  const jar = await cookies();
  const token = jar.get(ADMIN_COOKIE)?.value;
  if (!token) return null;
  const session = getSession(token);
  return session?.admin ?? null;
}

export async function currentSessionToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(ADMIN_COOKIE)?.value ?? null;
}

/** Проверка CSRF: заголовок должен совпасть с токеном, привязанным к сессии. */
export async function verifyCsrf(req: Request): Promise<boolean> {
  const jar = await cookies();
  const session = jar.get(ADMIN_COOKIE)?.value;
  if (!session) return false;
  if (!getSession(session)) return false;
  const header = req.headers.get('x-csrf-token') || '';
  return safeEqual(header, csrfTokenFor(session));
}

export function sameOrigin(req: Request): boolean {
  const origin = req.headers.get('origin');
  if (!origin) return true; // same-origin fetch from server / curl without Origin is fine for non-browser
  try {
    const host = req.headers.get('host');
    return new URL(origin).host === host;
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
