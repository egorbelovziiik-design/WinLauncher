'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Database, Trash2 } from 'lucide-react';
import { adminPost } from './api-client';
import { AdminCard } from './kit';
import { useToast } from '../providers';

export function DemoCard({ demoCount }: { demoCount: number }) {
  const router = useRouter();
  const { push } = useToast();
  const [count, setCount] = useState(30);
  const [busy, setBusy] = useState(false);

  const run = async (action: 'generate' | 'clear') => {
    setBusy(true);
    const res = await adminPost('/api/admin/demo', { action, count });
    setBusy(false);
    if (res.ok) {
      push({
        tone: 'success',
        title: action === 'generate' ? 'Демо-заказы созданы' : 'Демо-заказы удалены',
        description: action === 'generate' ? `${res.data.created ?? count} шт. (помечены is_demo)` : 'Реальные заказы не затронуты',
      });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <AdminCard
      id="demo"
      title="Демонстрационные заказы"
      description={`В базе: ${demoCount} демо-заказов. Они исключены из реальной аналитики и помечены в списке.`}
    >
      <p className="text-[0.74rem] leading-relaxed text-white/45">
        Используйте для проверки графиков и таблиц на новом магазине. Демо-заказы создаются со статусом «выдан» и
        нулевой реальной оплатой. Никогда не выдавайте их как настоящие продажи.
      </p>
      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="w-28">
          <span className="field-label">Количество</span>
          <input type="number" min={1} max={120} value={count} onChange={(e) => setCount(Number(e.target.value))} className="field h-9" />
        </label>
        <button onClick={() => run('generate')} disabled={busy} className="btn btn-primary btn-sm"><Database size={13} /> Сгенерировать</button>
        <button onClick={() => run('clear')} disabled={busy} className="btn btn-ghost btn-sm hover:!text-danger"><Trash2 size={13} /> Удалить демо</button>
      </div>
    </AdminCard>
  );
}
