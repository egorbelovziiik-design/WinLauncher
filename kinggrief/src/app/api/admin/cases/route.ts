import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import {
  caseChanceTotal,
  deleteCaseGroup,
  deleteCaseItem,
  listCaseGroups,
  listCaseItems,
  logAudit,
  saveCaseGroup,
  saveCaseItem,
} from '@/lib/repo';
import {
  bridgeStatusCloud,
  grantAdminCaseCloud,
  listRecentAdminGrantsCloud,
  listRecentRecipientNicksCloud,
  templatesCloud,
} from '@/lib/delivery';
import { cloudSaveTable, cloudSyncLocalDb } from '@/lib/cloud-store';
import { caseItemSchema, firstError, nicknameSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

export const dynamic = 'force-dynamic';

type Body = {
  action?: string;
  id?: string;
  itemId?: number;
  group?: { id: string; name?: string; description?: string; color?: string; icon?: string; sort?: number; active?: boolean };
  item?: unknown;
  grant?: {
    nickname?: string;
    caseId?: string;
    amount?: number;
    commandTemplate?: string;
    saveTemplate?: boolean;
  };
};

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();
  const [bridge, tplMap, recentGrants, recentNicks] = await Promise.all([
    bridgeStatusCloud(),
    templatesCloud(),
    listRecentAdminGrantsCloud(20),
    listRecentRecipientNicksCloud(14),
  ]);
  return NextResponse.json({
    groups: listCaseGroups().map((g) => ({ ...g, items: listCaseItems(g.id), total: caseChanceTotal(g.id) })),
    bridge,
    bridgeConfigured: Boolean(process.env.MINECRAFT_BRIDGE_SECRET),
    caseCommandTemplate: tplMap.case,
    recentGrants,
    recentNicks,
  });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  await cloudSyncLocalDb();
  const body = await readJson<Body>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  switch (body.action) {
    case 'grant-case': {
      const nickParsed = nicknameSchema.safeParse(body.grant?.nickname ?? '');
      if (!nickParsed.success) {
        return NextResponse.json({ error: firstError(nickParsed.error) }, { status: 400 });
      }
      const rawCaseId = String(body.grant?.caseId ?? '').trim().toLowerCase();
      if (!/^[a-z0-9_-]{2,32}$/.test(rawCaseId)) {
        return NextResponse.json({ error: 'Некорректный ID кейса (только латиница, цифры, - и _)' }, { status: 400 });
      }
      const amount = Math.floor(Number(body.grant?.amount ?? 1));
      if (!Number.isFinite(amount) || amount < 1 || amount > 10000) {
        return NextResponse.json({ error: 'Количество ключей: от 1 до 10 000' }, { status: 400 });
      }
      const result = await grantAdminCaseCloud({
        nickname: nickParsed.data,
        caseId: rawCaseId,
        amount,
        commandTemplate: body.grant?.commandTemplate,
        saveTemplate: Boolean(body.grant?.saveTemplate),
        actor: guard.admin.username,
        ip: clientIp(req),
      });
      const [bridge, recentGrants, recentNicks] = await Promise.all([
        bridgeStatusCloud(),
        listRecentAdminGrantsCloud(20),
        listRecentRecipientNicksCloud(14),
      ]);
      return NextResponse.json({
        ok: true,
        ...result,
        bridge,
        recentGrants,
        recentNicks,
      });
    }
    case 'save-group': {
      if (!body.group?.id) return NextResponse.json({ error: 'Не указан кейс' }, { status: 400 });
      const saved = saveCaseGroup(body.group);
      await cloudSaveTable('cases');
      logAudit(guard.admin.username, 'case_save', `Кейс ${saved.name}`, clientIp(req));
      return NextResponse.json({ ok: true, group: saved });
    }
    case 'delete-group': {
      if (!body.id) return NextResponse.json({ error: 'Не указан кейс' }, { status: 400 });
      deleteCaseGroup(body.id);
      await Promise.all([cloudSaveTable('cases'), cloudSaveTable('case_items')]);
      logAudit(guard.admin.username, 'case_delete', `Удалён кейс ${body.id}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }
    case 'save-item': {
      const parsed = caseItemSchema.safeParse(body.item);
      if (!parsed.success)
        return NextResponse.json({ error: firstError(parsed.error) }, { status: 400 });
      const id = saveCaseItem(parsed.data);
      await cloudSaveTable('case_items');
      logAudit(guard.admin.username, 'case_item_save', `${parsed.data.name} · ${parsed.data.chance}%`, clientIp(req));
      return NextResponse.json({ ok: true, id, total: caseChanceTotal(parsed.data.caseId) });
    }
    case 'delete-item': {
      if (!body.itemId) return NextResponse.json({ error: 'Не указана награда' }, { status: 400 });
      deleteCaseItem(body.itemId);
      await cloudSaveTable('case_items');
      logAudit(guard.admin.username, 'case_item_delete', `Награда #${body.itemId}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }
    default:
      return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
  }
}
