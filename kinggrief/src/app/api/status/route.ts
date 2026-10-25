import { NextResponse } from 'next/server';
import { getSettings } from '@/lib/repo';
import { bridgeStatusCloud } from '@/lib/delivery';
import { cloudGetSettings, isCloudEnabled } from '@/lib/cloud-store';

export const dynamic = 'force-dynamic';

export async function GET() {
  const localSettings = getSettings();
  const cloudSettings = isCloudEnabled() ? await cloudGetSettings().catch(() => ({} as Record<string, string>)) : {};
  const settings = { ...localSettings, ...cloudSettings };
  const bridge = await bridgeStatusCloud();
  const base = Number(settings.online_base || 0);
  const online = bridge.connected ? Number(bridge.online ?? settings.bridge_online ?? 0) : base;
  const max =
    bridge.connected && Number(bridge.max ?? 0) > 0
      ? Number(bridge.max)
      : Number(settings.bridge_max || settings.online_peak || 0);

  return NextResponse.json({
    online,
    max,
    ip: settings.server_ip,
    version: settings.server_version,
    source: bridge.connected ? 'bridge' : 'fallback',
    connected: bridge.connected,
    lastSeen: bridge.lastSeen,
    mcVersion: bridge.mcVersion ?? null,
    detectedCaseCmd: bridge.detectedCaseCmd ?? null,
    announcement: settings.announcement || '',
    discord: settings.discord_url || '',
    telegram: settings.telegram_url || '',
    maintenance: settings.maintenance === 'true',
  });
}
