import { NextResponse } from 'next/server';
import { userLoginSchema } from '@/lib/validation';
import { checkUserCredentials, logEvent } from '@/lib/repo';
import { startUserSession } from '@/lib/auth';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`user-login:${ip}`, 10, 10 * 60_000);
  if (!limit.ok) {
    return NextResponse.json(
      { error: 'Слишком много попыток входа. Повторите через несколько минут.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfter) } },
    );
  }

  const parsed = userLoginSchema.safeParse(await req.json().catch(() => null));
  if (!parsed.success) return NextResponse.json({ error: 'Введите никнейм и пароль' }, { status: 400 });

  const user = checkUserCredentials(parsed.data.login, parsed.data.password);
  if (!user) {
    logEvent('user_login_failed', `Неудачный вход: ${parsed.data.login.slice(0, 32)}`);
    return NextResponse.json({ error: 'Неверный никнейм или пароль' }, { status: 401 });
  }

  await startUserSession(user.id);
  logEvent('user_login', `Вход в аккаунт: ${user.username}`);
  return NextResponse.json({ ok: true, user });
}
