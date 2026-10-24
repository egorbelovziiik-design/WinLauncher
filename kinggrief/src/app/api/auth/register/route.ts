import { NextResponse } from 'next/server';
import { ZodError } from 'zod';
import { registerSchema, firstError } from '@/lib/validation';
import { createUser, logEvent } from '@/lib/repo';
import { startUserSession } from '@/lib/auth';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export async function POST(req: Request) {
  const ip = clientIp(req);
  const limit = rateLimit(`register:${ip}`, 5, 30 * 60_000);
  if (!limit.ok) {
    return NextResponse.json(
      { error: 'Слишком много попыток регистрации. Попробуйте позже.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfter) } },
    );
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });
  }

  const parsed = registerSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: firstError(parsed.error as ZodError) }, { status: 400 });
  }

  const result = createUser({
    username: parsed.data.username,
    email: parsed.data.email,
    password: parsed.data.password,
  });
  if (!result.ok) return NextResponse.json({ error: result.error }, { status: 409 });

  await startUserSession(result.user.id);
  logEvent('user_register', `Новый аккаунт: ${result.user.username}`);
  return NextResponse.json({ ok: true, user: result.user }, { status: 201 });
}
