import { NextResponse } from 'next/server';
import { loginSchema } from '@/lib/validation';
import { checkAdminCredentials, getAdminByUsername, logAudit } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { startAdminSession } from '@/lib/auth';
import { clientIp, rateLimit } from '@/lib/rate-limit';
import { verifyTotp } from '@/lib/totp';

export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`login:${ip}`, 25, 10 * 60_000);
  if (!limit.ok) {
    return NextResponse.json(
      { error: 'Слишком много попыток входа. Повторите позже.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfter) } },
    );
  }

  await cloudSyncLocalDb();
  const parsed = loginSchema.safeParse(await req.json().catch(() => null));
  if (!parsed.success) return NextResponse.json({ error: 'Заполните логин и пароль' }, { status: 400 });

  const admin = checkAdminCredentials(parsed.data.username, parsed.data.password);
  if (!admin) {
    logAudit(parsed.data.username, 'login_failed', 'Неверный логин или пароль', ip);
    return NextResponse.json({ error: 'Неверный логин или пароль' }, { status: 401 });
  }

  if (admin.totpEnabled) {
    const record = getAdminByUsername(admin.username);
    if (!record?.totpSecret) return NextResponse.json({ error: '2FA настроена некорректно' }, { status: 500 });
    if (!parsed.data.totp || !verifyTotp(record.totpSecret, parsed.data.totp)) {
      return NextResponse.json({ error: 'Введите актуальный код из приложения-аутентификатора' }, { status: 401 });
    }
  }

  await startAdminSession(admin.id, ip, req.headers.get('user-agent'));
  logAudit(admin.username, 'login', 'Успешный вход в админ-панель', ip);
  return NextResponse.json({ ok: true, username: admin.username });
}
