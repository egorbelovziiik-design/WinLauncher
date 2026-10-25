import { NextResponse } from 'next/server';
import { z } from 'zod';
import { guardAdmin } from '@/lib/auth';
import { getAdminById, logAudit, setAdminTotpSecret, updateAdminPassword } from '@/lib/repo';
import { verifyPassword } from '@/lib/db';
import { generateTotpSecret, otpauthUrl, verifyTotp } from '@/lib/totp';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';
import { firstError } from '@/lib/validation';

const passwordSchema = z.object({
  action: z.literal('password'),
  current: z.string().min(1),
  next: z.string().min(10, 'Минимум 10 символов').max(200),
});

const totpEnableSchema = z.object({
  action: z.literal('totp-enable'),
  secret: z.string().min(16).max(64),
  token: z.string().min(6).max(8),
});

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const admin = getAdminById(guard.admin.id);
  if (!admin) return NextResponse.json({ error: 'Администратор не найден' }, { status: 404 });

  const body = await readJson<Record<string, unknown>>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  if (body.action === 'password') {
    const parsed = passwordSchema.safeParse(body);
    if (!parsed.success)
      return NextResponse.json({ error: firstError(parsed.error) }, { status: 400 });
    if (!verifyPassword(parsed.data.current, admin.passwordHash))
      return NextResponse.json({ error: 'Текущий пароль неверен' }, { status: 401 });
    updateAdminPassword(admin.id, parsed.data.next);
    logAudit(admin.username, 'password_change', 'Пароль изменён', clientIp(req));
    return NextResponse.json({ ok: true, message: 'Пароль обновлён' });
  }

  if (body.action === 'totp-init') {
    const secret = generateTotpSecret();
    return NextResponse.json({ ok: true, secret, otpauth: otpauthUrl(secret, admin.username) });
  }

  if (body.action === 'totp-enable') {
    const parsed = totpEnableSchema.safeParse(body);
    if (!parsed.success) return NextResponse.json({ error: 'Некорректные данные' }, { status: 400 });
    if (!verifyTotp(parsed.data.secret, parsed.data.token))
      return NextResponse.json({ error: 'Код не подошёл. Проверьте время на устройстве.' }, { status: 400 });
    setAdminTotpSecret(admin.id, parsed.data.secret, true);
    logAudit(admin.username, 'totp_enable', '2FA включена', clientIp(req));
    return NextResponse.json({ ok: true, message: '2FA включена' });
  }

  if (body.action === 'totp-disable') {
    const password = String(body.password ?? '');
    if (!verifyPassword(password, admin.passwordHash))
      return NextResponse.json({ error: 'Пароль неверен' }, { status: 401 });
    setAdminTotpSecret(admin.id, null, false);
    logAudit(admin.username, 'totp_disable', '2FA отключена', clientIp(req));
    return NextResponse.json({ ok: true, message: '2FA отключена' });
  }

  return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
}
