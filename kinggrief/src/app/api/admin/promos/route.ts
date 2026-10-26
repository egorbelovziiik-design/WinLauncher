import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import {
  deletePromo,
  deleteServerPromoReward,
  getSetting,
  listPromos,
  logAudit,
  savePromo,
  saveServerPromoReward,
} from '@/lib/repo';
import { cloudSaveTable, cloudSetSettings, cloudSyncLocalDb, isCloudEnabled } from '@/lib/cloud-store';
import { firstError, promoInputSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';
import type { ServerPromoReward } from '@/lib/types';

export const dynamic = 'force-dynamic';

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();
  return NextResponse.json({ promos: listPromos() });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb({ force: true });
  const body = await readJson<{
    action?: string;
    code?: string;
    promo?: unknown;
    serverReward?: Partial<ServerPromoReward>;
  }>(req);
  if (!body) return NextResponse.json({ error: 'Некорректный JSON' }, { status: 400 });

  if (body.action === 'delete') {
    if (!body.code) return NextResponse.json({ error: 'Не указан код' }, { status: 400 });
    deletePromo(body.code);
    deleteServerPromoReward(body.code);
    await cloudSaveTable('promos');
    if (isCloudEnabled()) {
      await cloudSetSettings({ server_promo_rewards: getSetting('server_promo_rewards', '{}') });
    }
    logAudit(guard.admin.username, 'promo_delete', `Удалён промокод ${body.code}`, clientIp(req));
    return NextResponse.json({ ok: true });
  }

  const parsed = promoInputSchema.safeParse(body.promo ?? body);
  if (!parsed.success)
    return NextResponse.json({ error: firstError(parsed.error) }, { status: 400 });
  const saved = savePromo(parsed.data);
  if (body.serverReward) {
    saveServerPromoReward(saved.code, body.serverReward);
  }
  await cloudSaveTable('promos');
  if (isCloudEnabled() && body.serverReward) {
    await cloudSetSettings({ server_promo_rewards: getSetting('server_promo_rewards', '{}') });
  }
  logAudit(guard.admin.username, 'promo_save', `Промокод ${saved.code}`, clientIp(req));
  return NextResponse.json({ ok: true, promo: saved });
}
