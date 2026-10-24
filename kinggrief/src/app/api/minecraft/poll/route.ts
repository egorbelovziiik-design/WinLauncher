import { NextResponse } from 'next/server';
import {
  bridgeSecretMatches,
  syncOrderDeliveryStatus,
} from '@/lib/delivery';
import { listDeliveries, logEvent, setSettings, updateDelivery } from '@/lib/repo';

/**
 * Endpoint для плагина KingGriefBridge (Paper/Purpur/Spigot).
 * Плагин опрашивает очередь каждые несколько секунд, авторизуясь секретом.
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
  };
  const patch: Record<string, string> = { bridge_last_seen: new Date().toISOString() };
  if (typeof body.online === 'number') patch.bridge_online = String(Math.max(0, Math.floor(body.online)));
  if (typeof body.max === 'number') patch.bridge_max = String(Math.max(0, Math.floor(body.max)));
  if (typeof body.tps === 'number') patch.bridge_tps = body.tps.toFixed(2);
  if (typeof body.version === 'string') patch.bridge_version = body.version.slice(0, 32);
  if (typeof body.serverId === 'string') patch.bridge_server_id = body.serverId.slice(0, 64);
  setSettings(patch);

  const pending = listDeliveries({ status: 'pending', limit: 25 });
  const commands = pending.map((d) => {
    updateDelivery(d.id, { status: 'sent', attempts: d.attempts + 1 });
    return { id: d.id, command: d.command, nickname: d.nickname, orderId: d.orderId };
  });
  return NextResponse.json({ ok: true, commands, interval: 5 });
}
