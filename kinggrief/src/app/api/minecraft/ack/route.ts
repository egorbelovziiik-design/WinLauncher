import { NextResponse } from 'next/server';
import { bridgeSecretMatches, syncOrderDeliveryStatus } from '@/lib/delivery';
import {
  cloudGetDelivery,
  cloudSyncOrderDeliveryStatus,
  cloudUpdateDelivery,
  isCloudEnabled,
} from '@/lib/cloud-store';
import { listDeliveries, logEvent, updateDelivery } from '@/lib/repo';

export const dynamic = 'force-dynamic';

type AckItem = {
  id: string;
  ok: boolean;
  error?: string;
  executedCommand?: string;
};

export async function POST(req: Request) {
  if (!bridgeSecretMatches(req.headers.get('x-bridge-secret'))) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = (await req.json().catch(() => null)) as
    | {
        results?: AckItem[];
        id?: string;
        ok?: boolean;
        error?: string;
        executedCommand?: string;
      }
    | null;

  // Поддерживаем как батч-формат { results: [...] }, так и одиночный { id, ok, error } (KingGriefBridge v1.2+).
  const items: AckItem[] = Array.isArray(body?.results)
    ? body.results
    : typeof body?.id === 'string' && typeof body?.ok === 'boolean'
      ? [{ id: body.id, ok: body.ok, error: body.error, executedCommand: body.executedCommand }]
      : [];

  if (items.length === 0) {
    return NextResponse.json({ error: 'bad payload' }, { status: 400 });
  }

  if (isCloudEnabled()) {
    const touched = new Set<string>();
    let processed = 0;
    for (const result of items.slice(0, 50)) {
      const delivery = await cloudGetDelivery(result.id);
      if (!delivery) continue;
      if (result.ok) {
        await cloudUpdateDelivery(delivery.id, {
          status: 'delivered',
          executedCommand: result.executedCommand ? result.executedCommand.slice(0, 240) : delivery.command,
        });
      } else {
        await cloudUpdateDelivery(delivery.id, {
          status: 'failed',
          lastError: (result.error || 'Ошибка выполнения на сервере').slice(0, 300),
          executedCommand: result.executedCommand ? result.executedCommand.slice(0, 240) : null,
        });
      }
      touched.add(delivery.orderId);
      processed += 1;
    }
    for (const orderId of touched) {
      await cloudSyncOrderDeliveryStatus(orderId);
    }
    return NextResponse.json({ ok: true, processed });
  }

  const touched = new Set<string>();
  let processed = 0;
  for (const result of items.slice(0, 50)) {
    const delivery = listDeliveries({ limit: 1000 }).find((d) => d.id === result.id);
    if (!delivery) continue;
    if (result.ok) {
      updateDelivery(delivery.id, { status: 'delivered' });
      logEvent('delivery', `Команда выдана: ${result.executedCommand || delivery.command} (${delivery.nickname})`);
    } else {
      updateDelivery(delivery.id, { status: 'failed', lastError: (result.error || 'Ошибка выполнения').slice(0, 300) });
      logEvent('delivery_error', `Ошибка выдачи ${delivery.id}: ${result.error ?? ''}`);
    }
    touched.add(delivery.orderId);
    processed += 1;
  }
  touched.forEach((orderId) => syncOrderDeliveryStatus(orderId));
  return NextResponse.json({ ok: true, processed });
}
