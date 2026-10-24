import { NextResponse } from 'next/server';
import { profileSchema } from '@/lib/validation';
import { currentUser } from '@/lib/auth';
import {
  getUserById,
  logEvent,
  updateUserEmail,
  updateUserPassword,
  verifyUserPassword,
} from '@/lib/repo';
import { clientIp, rateLimit } from '@/lib/rate-limit';

export const dynamic = 'force-dynamic';

export async function PATCH(req: Request) {
  const user = await currentUser();
  if (!user) return NextResponse.json({ error: 'Требуется вход в аккаунт' }, { status: 401 });

  const limit = rateLimit(`profile:${user.id}`, 10, 10 * 60_000);
  if (!limit.ok) return NextResponse.json({ error: 'Слишком много попыток' }, { status: 429 });

  const parsed = profileSchema.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      { error: parsed.error.issues[0]?.message ?? 'Некорректные данные' },
      { status: 400 },
    );
  }

  const { email, currentPassword, newPassword } = parsed.data;

  if (newPassword) {
    if (!currentPassword || !verifyUserPassword(user.id, currentPassword)) {
      return NextResponse.json({ error: 'Текущий пароль указан неверно' }, { status: 400 });
    }
    updateUserPassword(user.id, newPassword);
    logEvent('user_password', `Смена пароля: ${user.username}`);
  }

  if (email && email.toLowerCase() !== user.email.toLowerCase()) {
    const result = updateUserEmail(user.id, email);
    if (!result.ok) return NextResponse.json({ error: result.error }, { status: 409 });
    logEvent('user_email', `Смена email: ${user.username}`);
  }

  return NextResponse.json({ ok: true, user: getUserById(user.id) });
}

export async function GET() {
  const user = await currentUser();
  if (!user) return NextResponse.json({ error: 'Требуется вход в аккаунт' }, { status: 401 });
  return NextResponse.json({ user }, { headers: { 'Cache-Control': 'no-store' } });
}
