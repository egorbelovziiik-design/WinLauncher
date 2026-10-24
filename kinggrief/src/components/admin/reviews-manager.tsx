'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Check, Star, Trash2, X } from 'lucide-react';
import type { Review } from '@/lib/types';
import { formatDate } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, Pill } from './kit';
import { useToast } from '../providers';

const TABS = [
  { key: 'pending', label: 'На проверке' },
  { key: 'approved', label: 'Опубликованы' },
  { key: 'rejected', label: 'Отклонены' },
  { key: 'all', label: 'Все' },
];

export function ReviewsManager({ reviews }: { reviews: Review[] }) {
  const router = useRouter();
  const { push } = useToast();
  const [tab, setTab] = useState('pending');
  const [busy, setBusy] = useState<number | null>(null);

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: reviews.length };
    ['pending', 'approved', 'rejected'].forEach((s) => (map[s] = reviews.filter((r) => r.status === s).length));
    return map;
  }, [reviews]);

  const filtered = reviews.filter((r) => (tab === 'all' ? true : r.status === tab));

  const act = async (review: Review, action: 'approve' | 'reject' | 'delete') => {
    setBusy(review.id);
    const res = await adminPost('/api/admin/reviews', { id: review.id, action });
    setBusy(null);
    if (res.ok) {
      push({ tone: 'success', title: action === 'delete' ? 'Отзыв удалён' : action === 'approve' ? 'Отзыв опубликован' : 'Отзыв отклонён' });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  return (
    <div className="space-y-4">
      <div>
        <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">контент</p>
        <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Отзывы</h1>
        <p className="mt-1 text-[0.76rem] text-white/35">Публикуйте только реальные отзывы. Спам и реклама отклоняются.</p>
      </div>

      <div className="flex flex-wrap gap-1">
        {TABS.map((item) => (
          <button
            key={item.key}
            onClick={() => setTab(item.key)}
            className={`rounded-lg border px-2.5 py-1.5 text-[0.7rem] font-semibold transition ${
              tab === item.key ? 'border-neon/50 bg-neon/15 text-white' : 'border-white/8 text-white/45 hover:text-white'
            }`}
          >
            {item.label} <span className="ml-1 font-mono text-[0.56rem] text-white/30">{counts[item.key] ?? 0}</span>
          </button>
        ))}
      </div>

      <AdminCard>
        {filtered.length === 0 ? (
          <p className="py-10 text-center text-[0.76rem] text-white/30">Отзывов в этой категории нет.</p>
        ) : (
          <ul className="space-y-2">
            {filtered.map((review) => (
              <li key={review.id} className="rounded-lg border border-white/6 bg-void/30 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-white/85">{review.nickname}</span>
                    <span className="inline-flex items-center gap-0.5">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star key={i} size={11} className={i < review.rating ? 'text-gold' : 'text-white/15'} fill={i < review.rating ? 'currentColor' : 'none'} />
                      ))}
                    </span>
                    <Pill color={review.status === 'approved' ? '#22C55E' : review.status === 'pending' ? '#F59E0B' : '#EF4444'}>
                      {review.status === 'approved' ? 'опубликован' : review.status === 'pending' ? 'на проверке' : 'отклонён'}
                    </Pill>
                  </div>
                  <span className="font-mono text-[0.58rem] text-white/25">{formatDate(review.createdAt)}</span>
                </div>
                <p className="mt-2 text-[0.76rem] leading-relaxed text-white/55">{review.text}</p>
                <div className="mt-2 flex gap-1.5">
                  {review.status !== 'approved' && (
                    <button onClick={() => act(review, 'approve')} disabled={busy === review.id} className="btn btn-ghost btn-sm !h-7 !text-[0.66rem]"><Check size={11} /> Опубликовать</button>
                  )}
                  {review.status !== 'rejected' && (
                    <button onClick={() => act(review, 'reject')} disabled={busy === review.id} className="btn btn-ghost btn-sm !h-7 !text-[0.66rem]"><X size={11} /> Отклонить</button>
                  )}
                  <button onClick={() => act(review, 'delete')} disabled={busy === review.id} className="btn btn-ghost btn-sm !h-7 !text-[0.66rem] hover:!text-danger"><Trash2 size={11} /> Удалить</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </AdminCard>
    </div>
  );
}
