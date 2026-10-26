import { NextResponse } from 'next/server';
import { listPromos } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';

export const dynamic = 'force-dynamic';

/**
 * Публичный эндпоинт синхронизации промокодов сайта с Minecraft-сервером KingGriefCore v5.0.
 * Формат строк (text/plain):
 * CODE|MAX_USES|COINS|RELICS|CASE_ID|CASE_QTY|RANK_ID
 */
export async function GET() {
  await cloudSyncLocalDb();
  const promos = listPromos();
  const now = Date.now();
  const lines: string[] = ['# KINGGRIEF v5.0 Website <-> Minecraft Promo Sync'];

  for (const p of promos) {
    if (!p.active) continue;
    if (p.expiresAt && new Date(p.expiresAt).getTime() < now) continue;
    const sr = p.serverReward ?? {
      enabled: true,
      coins: Math.max(10000, p.value * 1000),
      relics: Math.max(10, p.value),
      caseId: '',
      caseQty: 0,
      rankId: '',
    };
    if (!sr.enabled) continue;
    const maxUses = p.usageLimit ?? 0;
    const code = p.code.replace(/\|/g, '').trim().toUpperCase();
    const caseId = (sr.caseId ?? '').replace(/\|/g, '').trim().toLowerCase();
    const caseQty = caseId ? Math.max(1, sr.caseQty || 1) : 0;
    const rankId = (sr.rankId ?? '').replace(/\|/g, '').trim().toLowerCase();
    lines.push(`${code}|${maxUses}|${Math.max(0, sr.coins)}|${Math.max(0, sr.relics)}|${caseId}|${caseQty}|${rankId}`);
  }

  return new NextResponse(lines.join('\n') + '\n', {
    status: 200,
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'no-store, max-age=0',
    },
  });
}
