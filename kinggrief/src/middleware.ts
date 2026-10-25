import { NextResponse, type NextRequest } from 'next/server';

/**
 * Проверяем подпись cookie прямо в middleware — до рендера страницы.
 * Это убирает мигание закрытых разделов и бесконечные редиректы при просроченной cookie.
 * Существование самого аккаунта всё равно проверяется на сервере (currentUser/currentAdmin).
 */

function secret() {
  return process.env.ADMIN_SESSION_SECRET || 'kinggrief-dev-secret-change-me';
}

function base64urlToBytes(value: string) {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function validToken(token: string | undefined, kind: 'admin' | 'user') {
  if (!token) return false;
  const [body, signature] = token.split('.');
  if (!body || !signature) return false;
  try {
    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(secret()),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify'],
    );
    const ok = await crypto.subtle.verify(
      'HMAC',
      key,
      base64urlToBytes(signature),
      new TextEncoder().encode(body),
    );
    if (!ok) return false;
    const payload = JSON.parse(atob(body.replace(/-/g, '+').replace(/_/g, '/'))) as {
      kind?: string;
      exp?: number;
    };
    return payload.kind === kind && typeof payload.exp === 'number' && payload.exp > Date.now();
  } catch {
    return false;
  }
}

/**
 * Абсолютный адрес редиректа собираем из forwarded-заголовков: за reverse-proxy
 * (превью-домен, Cloudflare, Vercel) внутренний origin приложения недоступен извне.
 * Хост санитизируем, чтобы заголовок Host нельзя было подменить на чужой домен.
 */
function externalOrigin(req: NextRequest) {
  const rawHost = (req.headers.get('x-forwarded-host') || req.headers.get('host') || req.nextUrl.host).split(',')[0].trim();
  const host = /^[A-Za-z0-9.\-]+(:\d{1,5})?$/.test(rawHost) ? rawHost : req.nextUrl.host;
  const forwarded = (req.headers.get('x-forwarded-proto') || '').split(',')[0].trim().toLowerCase();
  const proto = forwarded === 'https' || forwarded === 'http' ? forwarded : req.nextUrl.protocol.replace(':', '');
  return `${proto}://${host}`;
}

function redirectTo(req: NextRequest, path: string) {
  return NextResponse.redirect(new URL(path, externalOrigin(req)));
}

export async function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  const isAccount = pathname === '/account' || pathname.startsWith('/account/');
  if (isAccount && !(await validToken(req.cookies.get('kg_user')?.value, 'user'))) {
    return redirectTo(req, `/login?next=${encodeURIComponent(pathname)}`);
  }

  const isAdminLogin = pathname === '/admin/login';
  const isAdminPage = pathname === '/admin' || (pathname.startsWith('/admin/') && !isAdminLogin);
  if (isAdminPage && !(await validToken(req.cookies.get('kg_admin')?.value, 'admin'))) {
    return redirectTo(req, '/admin/login');
  }
  if (isAdminLogin && (await validToken(req.cookies.get('kg_admin')?.value, 'admin'))) {
    return redirectTo(req, '/admin');
  }

  const isAuthPage = pathname === '/login' || pathname === '/register';
  if (isAuthPage && (await validToken(req.cookies.get('kg_user')?.value, 'user'))) {
    return redirectTo(req, '/account');
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/account', '/account/:path*', '/admin', '/admin/:path*', '/login', '/register'],
};
