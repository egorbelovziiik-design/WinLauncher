import { NextResponse } from 'next/server';
import { currentAdmin, endAdminSession } from '@/lib/auth';
import { logAudit } from '@/lib/repo';
import { clientIp } from '@/lib/rate-limit';

export async function POST(req: Request) {
  const admin = await currentAdmin();
  if (admin) logAudit(admin.username, 'logout', 'Выход из админ-панели', clientIp(req));
  await endAdminSession();
  return NextResponse.json({ ok: true });
}
