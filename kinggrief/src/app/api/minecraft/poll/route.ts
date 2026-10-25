import { NextResponse } from 'next/server';
import { bridgeSecretMatches } from '@/lib/delivery';
import {
  cloudClaimPendingDeliveries,
  cloudRequeueStaleDeliveries,
  cloudUpsertBridgeHeartbeat,
  isCloudEnabled,
} from '@/lib/cloud-store';
import { listDeliveries, logEvent, markDeliverySent, requeueStaleDeliveries, setSettings } from '@/lib/repo';

export const dynamic = 'force-dynamic';

/**
 * Endpoint для плагина KingGriefBridge (Paper/Purpur/Spigot).
 * Плагин опрашивает единую облачную очередь каждые 5 секунд, авторизуясь секретом.
 */
export async function POST(req: Request) {
  if (!bridgeSecretMatches(req.headers.get('x-bridge-secret'))) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = (await req.json().catch(() => ({}))) as {
    online?: number;
    max?: number;
    tps?: number;
    version?: string;
    serverId?: string;
    detectedCaseCmd?: string;
    plugins?: string;
  };

  if (isCloudEnabled()) {
    await cloudUpsertBridgeHeartbeat({
      serverId: body.serverId,
      online: body.online,
      max: body.max,
      tps: body.tps,
      version: body.version,
      detectedCaseCmd: body.detectedCaseCmd,
      plugins: body.plugins,
    });
    await cloudRequeueStaleDeliveries();
    const pending = await cloudClaimPendingDeliveries(25);
    const commands = pending.map((d) => ({
      id: d.id,
      command: d.command,
      nickname: d.nickname,
      orderId: d.orderId,
      kind: d.kind ?? 'other',
      caseId: d.caseId ?? null,
      amount: d.amount ?? null,
    }));
    return NextResponse.json({ ok: true, commands, interval: 5 });
  }

  const patch: Record<string, string> = { bridge_last_seen: new Date().toISOString() };
  if (typeof body.online === 'number') patch.bridge_online = String(Math.max(0, Math.floor(body.online)));
  if (typeof body.max === 'number') patch.bridge_max = String(Math.max(0, Math.floor(body.max)));
  if (typeof body.tps === 'number') patch.bridge_tps = body.tps.toFixed(2);
  if (typeof body.version === 'string') patch.bridge_version = body.version.slice(0, 32);
  if (typeof body.serverId === 'string') patch.bridge_server_id = body.serverId.slice(0, 64);
  if (typeof body.detectedCaseCmd === 'string') patch.bridge_case_cmd = body.detectedCaseCmd.slice(0, 160);
  if (typeof body.plugins === 'string') patch.bridge_plugins = body.plugins.slice(0, 500);
  setSettings(patch);

  const retry = requeueStaleDeliveries();
  if (retry.requeued > 0 || retry.manual > 0) {
    logEvent('delivery_retry', `Повтор выдачи: ${retry.requeued}, вручную: ${retry.manual}`);
  }

  const pending = listDeliveries({ status: 'pending', limit: 25 });
  const commands = pending.map((d) => {
    markDeliverySent(d.id);
    return { id: d.id, command: d.command, nickname: d.nickname, orderId: d.orderId };
  });
  return NextResponse.json({ ok: true, commands, interval: 5 });
}
