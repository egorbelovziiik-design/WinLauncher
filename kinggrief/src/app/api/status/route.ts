import { NextResponse } from 'next/server';
import { getSettings } from '@/lib/repo';
import { bridgeStatus } from '@/lib/delivery';

export const dynamic = 'force-dynamic';

export async function GET() {
  const settings = getSettings();
  const bridge = bridgeStatus();
  const base = Number(settings.online_base || 0);
  const online = bridge.connected ? Number(settings.bridge_online || 0) : base;
  return NextResponse.json({
    online,
    max: Number(settings.bridge_max || settings.online_peak || 0),
    ip: settings.server_ip,
    version: settings.server_version,
    source: bridge.connected ? 'bridge' : 'fallback',
    connected: bridge.connected,
    lastSeen: bridge.lastSeen,
    announcement: settings.announcement || '',
    discord: settings.discord_url || '',
    telegram: settings.telegram_url || '',
    maintenance: settings.maintenance === 'true',
  });
}
