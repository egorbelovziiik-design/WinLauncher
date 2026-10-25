import { NextResponse } from 'next/server';
import { currentAdmin } from '@/lib/auth';

export const dynamic = 'force-dynamic';

/**
 * Проверка «сессия действительно доходит до сервера».
 * Нужна клиенту после входа: если браузер заблокировал cookie (сторонний iframe),
 * форма покажет понятную подсказку вместо бесконечного возврата на экран входа.
 */
export async function GET() {
  const admin = await currentAdmin();
  if (!admin) return NextResponse.json({ ok: false }, { status: 401, headers: { 'Cache-Control': 'no-store' } });
  return NextResponse.json(
    { ok: true, username: admin.username, role: admin.role, totpEnabled: admin.totpEnabled },
    { headers: { 'Cache-Control': 'no-store' } },
  );
}
