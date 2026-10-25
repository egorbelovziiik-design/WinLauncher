import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import {
  bridgeStatusCloud,
  grantAdminCaseCloud,
  grantAdminPrivilegeCloud,
  grantAdminRelicCloud,
  grantAdminServiceCloud,
  listRecentAdminGrantsCloud,
  listRecentRecipientNicksCloud,
  repeatDeliveryCloud,
  templatesCloud,
  type PrivilegeGrantMode,
} from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { firstError, nicknameSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export const dynamic = 'force-dynamic';

type GrantBody = {
  action?: 'grant-case' | 'grant-privilege' | 'grant-relic' | 'grant-service' | 'repeat';
  nickname?: string;
  caseId?: string;
  rankId?: string;
  mode?: PrivilegeGrantMode;
  amount?: number;
  serviceAction?: 'pardon' | 'unban' | 'unmute' | 'pardon_ip' | 'kit';
  kitName?: string;
  commandTemplate?: string;
  saveTemplate?: boolean;
  deliveryId?: string;
  kindFilter?: string;
};

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const kindFilter = new URL(req.url).searchParams.get('kind') ?? undefined;
  const [bridge, tplMap, recentGrants, recentNicks] = await Promise.all([
    bridgeStatusCloud(),
    templatesCloud(),
    listRecentAdminGrantsCloud(20, kindFilter),
    listRecentRecipientNicksCloud(14),
  ]);
  return NextResponse.json({
    ok: true,
    bridge,
    templates: tplMap,
    recentGrants,
    recentNicks,
  });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();

  const body = await readJson<GrantBody>(req);
  if (!body?.action) return NextResponse.json({ error: 'Не указано действие выдачи' }, { status: 400 });
  const ip = clientIp(req);
  const actor = guard.admin.username;

  if (body.action === 'repeat') {
    if (!body.deliveryId) return NextResponse.json({ error: 'Не указана задача' }, { status: 400 });
    const repeated = await repeatDeliveryCloud({ deliveryId: body.deliveryId, actor, ip });
    if (!repeated) return NextResponse.json({ error: 'Исходная задача не найдена' }, { status: 404 });
    const [bridge, recentGrants, recentNicks] = await Promise.all([
      bridgeStatusCloud(),
      listRecentAdminGrantsCloud(20, body.kindFilter),
      listRecentRecipientNicksCloud(14),
    ]);
    return NextResponse.json({
      ok: true,
      ...repeated,
      bridge,
      recentGrants,
      recentNicks,
    });
  }

  const nickParsed = nicknameSchema.safeParse(body.nickname ?? '');
  if (!nickParsed.success) {
    return NextResponse.json({ error: firstError(nickParsed.error) }, { status: 400 });
  }
  const nickname = nickParsed.data;

  switch (body.action) {
    case 'grant-case': {
      const caseId = String(body.caseId ?? '').trim().toLowerCase();
      if (!/^[a-z0-9_-]{2,32}$/.test(caseId)) {
        return NextResponse.json({ error: 'Некорректный ID кейса (только латиница, цифры, - и _)' }, { status: 400 });
      }
      const amount = Math.floor(Number(body.amount ?? 1));
      if (!Number.isFinite(amount) || amount < 1 || amount > 10000) {
        return NextResponse.json({ error: 'Количество ключей: от 1 до 10 000' }, { status: 400 });
      }
      const result = await grantAdminCaseCloud({
        nickname,
        caseId,
        amount,
        commandTemplate: body.commandTemplate,
        saveTemplate: Boolean(body.saveTemplate),
        actor,
        ip,
      });
      const [bridge, recentGrants, recentNicks] = await Promise.all([
        bridgeStatusCloud(),
        listRecentAdminGrantsCloud(20, body.kindFilter),
        listRecentRecipientNicksCloud(14),
      ]);
      return NextResponse.json({ ok: true, ...result, bridge, recentGrants, recentNicks });
    }

    case 'grant-privilege': {
      const mode: PrivilegeGrantMode = body.mode ?? 'set';
      const rawRank = mode === 'reset' ? 'default' : String(body.rankId ?? '').trim().replace(/^rank-/, '');
      if (!/^[a-zA-Z0-9_.-]{2,32}$/.test(rawRank)) {
        return NextResponse.json({ error: 'Укажите корректный ID группы привилегии (например, king, dstmoder, downer)' }, { status: 400 });
      }
      const result = await grantAdminPrivilegeCloud({
        nickname,
        rankId: rawRank,
        mode,
        commandTemplate: body.commandTemplate,
        saveTemplate: Boolean(body.saveTemplate),
        actor,
        ip,
      });
      const [bridge, recentGrants, recentNicks] = await Promise.all([
        bridgeStatusCloud(),
        listRecentAdminGrantsCloud(20, body.kindFilter),
        listRecentRecipientNicksCloud(14),
      ]);
      return NextResponse.json({ ok: true, ...result, bridge, recentGrants, recentNicks });
    }

    case 'grant-relic': {
      const amount = Math.floor(Number(body.amount ?? 1000));
      if (!Number.isFinite(amount) || amount < 1 || amount > 10_000_000) {
        return NextResponse.json({ error: 'Количество риликов: от 1 до 10 000 000' }, { status: 400 });
      }
      const result = await grantAdminRelicCloud({
        nickname,
        amount,
        commandTemplate: body.commandTemplate,
        saveTemplate: Boolean(body.saveTemplate),
        actor,
        ip,
      });
      const [bridge, recentGrants, recentNicks] = await Promise.all([
        bridgeStatusCloud(),
        listRecentAdminGrantsCloud(20, body.kindFilter),
        listRecentRecipientNicksCloud(14),
      ]);
      return NextResponse.json({ ok: true, ...result, bridge, recentGrants, recentNicks });
    }

    case 'grant-service': {
      const serviceAction = body.serviceAction ?? 'pardon';
      const result = await grantAdminServiceCloud({
        nickname,
        serviceAction,
        kitName: body.kitName,
        actor,
        ip,
      });
      const [bridge, recentGrants, recentNicks] = await Promise.all([
        bridgeStatusCloud(),
        listRecentAdminGrantsCloud(20, body.kindFilter),
        listRecentRecipientNicksCloud(14),
      ]);
      return NextResponse.json({ ok: true, ...result, bridge, recentGrants, recentNicks });
    }

    default:
      return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
  }
}
