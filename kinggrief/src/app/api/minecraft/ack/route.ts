import { NextResponse } from 'next/server';
import { bridgeSecretMatches, syncOrderDeliveryStatus } from '@/lib/delivery';
import { listDeliveries, logEvent, updateDelivery } from '@/lib/repo';

export async function POST(req: Request) {
  if (!bridgeSecretMatches(req.headers.get('x-bridge-secret'))) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = (await req.json().catch(() => null)) as
    | { results?: Array<{ id: string; ok: boolean; error?: string }> }
    | null;
  if (!body?.results || !Array.isArray(body.results)) {
    return NextResponse.json({ error: 'bad payload' }, { status: 400 });
  }
  const touched = new Set<string>();
  let processed = 0;
  for (const result of body.results.slice(0, 50)) {
    const delivery = listDeliveries({ limit: 1000 }).find((d) => d.id === result.id);
    if (!delivery) continue;
    if (result.ok) {
      updateDelivery(delivery.id, { status: 'delivered' });
      logEvent('delivery', `Команда выдана: ${delivery.command} (${delivery.nickname})`);
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
