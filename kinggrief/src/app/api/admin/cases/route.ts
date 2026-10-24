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
import { caseItemSchema } from '@/lib/validation';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

type Body = {
  action?: string;
  id?: string;
  itemId?: number;
  group?: { id: string; name?: string; description?: string; color?: string; icon?: string; sort?: number; active?: boolean };
  item?: unknown;
};

export async function GET(req: Request) {
  const guard = await guardAdmin(req);
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  return NextResponse.json({
    groups: listCaseGroups().map((g) => ({ ...g, items: listCaseItems(g.id), total: caseChanceTotal(g.id) })),
  });
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });
  const body = await readJson<Body>(req);
  if (!body?.action) return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });

  switch (body.action) {
    case 'save-group': {
      if (!body.group?.id) return NextResponse.json({ error: 'Не указан кейс' }, { status: 400 });
      const saved = saveCaseGroup(body.group);
      logAudit(guard.admin.username, 'case_save', `Кейс ${saved.name}`, clientIp(req));
      return NextResponse.json({ ok: true, group: saved });
    }
    case 'delete-group': {
      if (!body.id) return NextResponse.json({ error: 'Не указан кейс' }, { status: 400 });
      deleteCaseGroup(body.id);
      logAudit(guard.admin.username, 'case_delete', `Удалён кейс ${body.id}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }
    case 'save-item': {
      const parsed = caseItemSchema.safeParse(body.item);
      if (!parsed.success)
        return NextResponse.json({ error: parsed.error.issues[0]?.message ?? 'Проверьте поля' }, { status: 400 });
      const id = saveCaseItem(parsed.data);
      logAudit(guard.admin.username, 'case_item_save', `${parsed.data.name} · ${parsed.data.chance}%`, clientIp(req));
      return NextResponse.json({ ok: true, id, total: caseChanceTotal(parsed.data.caseId) });
    }
    case 'delete-item': {
      if (!body.itemId) return NextResponse.json({ error: 'Не указана награда' }, { status: 400 });
      deleteCaseItem(body.itemId);
      logAudit(guard.admin.username, 'case_item_delete', `Награда #${body.itemId}`, clientIp(req));
      return NextResponse.json({ ok: true });
    }
    default:
      return NextResponse.json({ error: 'Неизвестное действие' }, { status: 400 });
  }
}
