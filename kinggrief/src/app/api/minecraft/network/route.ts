import crypto from 'node:crypto';
import { NextResponse } from 'next/server';
import { cloudGetSetting, cloudSetSettings, isCloudEnabled } from '@/lib/cloud-store';
import { getSetting, setSettings } from '@/lib/repo';

export const dynamic = 'force-dynamic';

type NetworkNode = {
  id: string;
  name: string;
  host: string;
  port: number;
  proxyName: string;
  online: number;
  max: number;
  tps: number;
  maintenance: boolean;
  players: string[];
  updatedAt: number;
};

type NetworkSsoTicket = {
  nick: string;
  ipHash: string;
  rank: string;
  fromServer: string;
  expiresAt: number;
};

type NetworkEvent = {
  id: string;
  type: 'staff_chat' | 'global_alert' | 'send_player' | 'unban';
  fromServer: string;
  sender: string;
  target?: string;
  destination?: string;
  message?: string;
  createdAt: number;
};

type NetworkBan = {
  nick: string;
  reason: string;
  staff: string;
  untilMs: number;
  updatedAt: number;
};

type NetworkState = {
  nodes: Record<string, NetworkNode>;
  sso: Record<string, NetworkSsoTicket>;
  ranks: Record<string, { rank: string; updatedAt: number }>;
  bans: Record<string, NetworkBan>;
  events: NetworkEvent[];
};

function emptyState(): NetworkState {
  return { nodes: {}, sso: {}, ranks: {}, bans: {}, events: [] };
}

async function loadState(nsKey: string): Promise<NetworkState> {
  try {
    const raw = isCloudEnabled() ? await cloudGetSetting(nsKey, '') : getSetting(nsKey, '');
    if (!raw) return emptyState();
    const parsed = JSON.parse(raw) as Partial<NetworkState>;
    return {
      nodes: parsed.nodes ?? {},
      sso: parsed.sso ?? {},
      ranks: parsed.ranks ?? {},
      bans: parsed.bans ?? {},
      events: Array.isArray(parsed.events) ? parsed.events : [],
    };
  } catch {
    return emptyState();
  }
}

async function saveState(nsKey: string, state: NetworkState): Promise<void> {
  const serialized = JSON.stringify(state);
  if (isCloudEnabled()) {
    await cloudSetSettings({ [nsKey]: serialized });
  } else {
    setSettings({ [nsKey]: serialized });
  }
}

export async function POST(req: Request) {
  const secret = (req.headers.get('x-network-secret') || req.headers.get('x-bridge-secret') || '').trim();
  if (!secret || secret.length < 6) {
    return NextResponse.json({ ok: false, error: 'missing_network_secret' }, { status: 401 });
  }

  const roomHash = crypto.createHash('sha256').update(`kingconnect:${secret}`).digest('hex').slice(0, 20);
  const nsKey = `__sync_knet_${roomHash}`;

  const body = (await req.json().catch(() => ({}))) as {
    serverId?: string;
    serverName?: string;
    host?: string;
    port?: number;
    proxyName?: string;
    online?: number;
    max?: number;
    tps?: number;
    maintenance?: boolean;
    players?: string[];
    pushSso?: Array<{ nick: string; ipHash: string; rank: string; ttlMs?: number }>;
    pushRanks?: Record<string, string>;
    pushBans?: Array<{ nick: string; reason: string; staff: string; untilMs: number }>;
    pushUnbans?: string[];
    pushEvents?: Array<{
      type: 'staff_chat' | 'global_alert' | 'send_player' | 'unban';
      sender: string;
      target?: string;
      destination?: string;
      message?: string;
    }>;
  };

  const now = Date.now();
  const state = await loadState(nsKey);
  const srvId = String(body.serverId || 'server').trim().toLowerCase().slice(0, 32);

  // Auto-detect real public IP of the Minecraft server container from HTTP request headers!
  const rawFwd = (req.headers.get('x-forwarded-for') || req.headers.get('x-real-ip') || '')
    .split(',')[0]
    .trim()
    .replace(/^::ffff:/i, '');
  const detectedIp =
    rawFwd && rawFwd !== '127.0.0.1' && rawFwd !== '::1' && rawFwd !== '0.0.0.0' ? rawFwd.slice(0, 64) : '';

  const rawBodyHost = String(body.host || '').trim();
  const isLoopbackHost =
    !rawBodyHost ||
    rawBodyHost === '127.0.0.1' ||
    rawBodyHost.toLowerCase() === 'localhost' ||
    rawBodyHost === '0.0.0.0';
  const effectiveNodeHost = (isLoopbackHost ? detectedIp : rawBodyHost).slice(0, 96);

  // 1. Update current server heartbeat
  if (srvId) {
    state.nodes[srvId] = {
      id: srvId,
      name: String(body.serverName || srvId).slice(0, 64),
      host: effectiveNodeHost,
      port: typeof body.port === 'number' && body.port > 0 ? Math.floor(body.port) : 25565,
      proxyName: String(body.proxyName || srvId).slice(0, 48),
      online: Math.max(0, Math.floor(Number(body.online || 0))),
      max: Math.max(1, Math.floor(Number(body.max || 100))),
      tps: Number(body.tps || 20),
      maintenance: Boolean(body.maintenance),
      players: Array.isArray(body.players) ? body.players.slice(0, 250).map((s) => String(s).slice(0, 24)) : [],
      updatedAt: now,
    };
  }

  // Prune stale nodes (> 120s old removed from dynamic registry; > 20s considered offline by client)
  for (const [k, node] of Object.entries(state.nodes)) {
    if (now - node.updatedAt > 120_000) {
      delete state.nodes[k];
    }
  }

  // 2. SSO Tickets (valid up to 90s)
  if (Array.isArray(body.pushSso)) {
    for (const t of body.pushSso.slice(0, 50)) {
      if (!t?.nick) continue;
      const key = t.nick.toLowerCase();
      state.sso[key] = {
        nick: t.nick.slice(0, 24),
        ipHash: String(t.ipHash || '').slice(0, 64),
        rank: String(t.rank || 'default').slice(0, 32),
        fromServer: srvId,
        expiresAt: now + Math.min(120_000, Math.max(15_000, Number(t.ttlMs || 60_000))),
      };
    }
  }
  for (const [k, sso] of Object.entries(state.sso)) {
    if (now > sso.expiresAt) delete state.sso[k];
  }

  // 3. Ranks Sync
  if (body.pushRanks && typeof body.pushRanks === 'object') {
    for (const [nick, rank] of Object.entries(body.pushRanks)) {
      if (!nick || !rank) continue;
      const k = nick.toLowerCase().slice(0, 24);
      state.ranks[k] = { rank: String(rank).toLowerCase().slice(0, 32), updatedAt: now };
    }
    const rankKeys = Object.keys(state.ranks);
    if (rankKeys.length > 800) {
      rankKeys
        .sort((a, b) => state.ranks[a].updatedAt - state.ranks[b].updatedAt)
        .slice(0, rankKeys.length - 800)
        .forEach((k) => delete state.ranks[k]);
    }
  }

  // 4. Global Bans / Unbans Sync
  if (Array.isArray(body.pushUnbans)) {
    for (const rawNick of body.pushUnbans.slice(0, 40)) {
      const k = String(rawNick || '').toLowerCase();
      if (k) delete state.bans[k];
    }
  }
  if (Array.isArray(body.pushBans)) {
    for (const b of body.pushBans.slice(0, 40)) {
      if (!b?.nick || !b?.reason) continue;
      const k = b.nick.toLowerCase().slice(0, 24);
      state.bans[k] = {
        nick: b.nick.slice(0, 24),
        reason: String(b.reason).slice(0, 160),
        staff: String(b.staff || 'KingАЧ').slice(0, 32),
        untilMs: Number(b.untilMs || -1),
        updatedAt: now,
      };
    }
  }
  for (const [k, ban] of Object.entries(state.bans)) {
    if (ban.untilMs > 0 && now > ban.untilMs) delete state.bans[k];
  }

  // 5. Cross-Server Events (kept for 30s)
  if (Array.isArray(body.pushEvents)) {
    for (const ev of body.pushEvents.slice(0, 25)) {
      if (!ev?.type) continue;
      state.events.push({
        id: `${now}-${crypto.randomBytes(4).toString('hex')}`,
        type: ev.type,
        fromServer: srvId,
        sender: String(ev.sender || 'Server').slice(0, 32),
        target: ev.target ? String(ev.target).slice(0, 32) : undefined,
        destination: ev.destination ? String(ev.destination).slice(0, 32) : undefined,
        message: ev.message ? String(ev.message).slice(0, 260) : undefined,
        createdAt: now,
      });
    }
  }
  state.events = state.events.filter((e) => now - e.createdAt < 30_000).slice(-80);

  await saveState(nsKey, state);

  if (req.headers.get('x-response-format') === 'lines') {
    const clean = (v: unknown) =>
      String(v ?? '')
        .replace(/[\r\n\t]+/g, ' ')
        .trim();
    const lines: string[] = [`OK\t${now}`];
    for (const n of Object.values(state.nodes)) {
      const ageMs = Math.max(0, now - n.updatedAt);
      lines.push(
        `NODE\t${clean(n.id)}\t${clean(n.name)}\t${clean(n.host)}\t${n.port}\t${clean(n.proxyName)}\t${n.online}\t${n.max}\t${n.tps.toFixed(1)}\t${n.maintenance ? 1 : 0}\t${ageMs}\t${n.players.map(clean).join(',')}`,
      );
    }
    for (const s of Object.values(state.sso)) {
      lines.push(`SSO\t${clean(s.nick)}\t${clean(s.ipHash)}\t${clean(s.rank)}\t${clean(s.fromServer)}`);
    }
    for (const [nick, r] of Object.entries(state.ranks)) {
      lines.push(`RANK\t${clean(nick)}\t${clean(r.rank)}`);
    }
    for (const b of Object.values(state.bans)) {
      lines.push(`BAN\t${clean(b.nick)}\t${clean(b.reason)}\t${clean(b.staff)}\t${b.untilMs}`);
    }
    for (const ev of state.events) {
      lines.push(
        `EVENT\t${clean(ev.id)}\t${clean(ev.type)}\t${clean(ev.fromServer)}\t${clean(ev.sender)}\t${clean(ev.target)}\t${clean(ev.destination)}\t${clean(ev.message)}`,
      );
    }
    return new NextResponse(lines.join('\n'), {
      status: 200,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }

  return NextResponse.json({
    ok: true,
    now,
    nodes: state.nodes,
    sso: state.sso,
    ranks: state.ranks,
    bans: state.bans,
    events: state.events,
  });
}
