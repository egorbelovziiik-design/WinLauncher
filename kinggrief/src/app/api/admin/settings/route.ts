import { NextResponse } from 'next/server';
import { guardAdmin } from '@/lib/auth';
import { logAudit, setSettings } from '@/lib/repo';
import { clientIp } from '@/lib/rate-limit';
import { readJson } from '@/lib/api';

const URL_KEYS = new Set(['discord_url', 'telegram_url', 'banner_href', 'logo_url', 'banner_image_url', 'hero_image_url']);

function sanitize(key: string, value: string) {
  if (URL_KEYS.has(key)) {
    if (value === '') return '';
    if (value.startsWith('/')) return value;
    if (/^https?:\/\//i.test(value)) return value;
    if (key === 'discord_url' && /^https:\/\/(discord\.gg|discord\.com)\//i.test(value)) return value;
    if (key === 'discord_url' && value.startsWith('discord.gg/')) return `https://${value}`;
    if (key === 'telegram_url' && /^https:\/\/t\.me\//i.test(value)) return value;
    if (key === 'telegram_url' && value.startsWith('t.me/')) return `https://${value}`;
    return '';
  }
  return value.slice(0, 4000);
}

export async function POST(req: Request) {
  const guard = await guardAdmin(req, { csrf: true });
  if (!guard.ok) return NextResponse.json({ error: guard.error }, { status: guard.status });

  const body = await readJson<{ settings?: Record<string, unknown> }>(req);
  if (!body?.settings || typeof body.settings !== 'object')
    return NextResponse.json({ error: 'Нет данных для сохранения' }, { status: 400 });

  const patch: Record<string, string> = {};
  for (const [key, value] of Object.entries(body.settings)) {
    if (!/^[a-z0-9_]{2,64}$/i.test(key)) continue;
    if (value === undefined || value === null) continue;
    patch[key] = sanitize(key, typeof value === 'string' ? value : String(value));
  }
  if (Object.keys(patch).length === 0) return NextResponse.json({ error: 'Нет валидных полей' }, { status: 400 });

  setSettings(patch);
  logAudit(guard.admin.username, 'settings_save', `Обновлены: ${Object.keys(patch).join(', ')}`, clientIp(req));
  return NextResponse.json({ ok: true, saved: Object.keys(patch) });
}
