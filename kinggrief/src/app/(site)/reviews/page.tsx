import type { Metadata } from 'next';
import Link from 'next/link';
import { MessageCircle, Star } from 'lucide-react';
import { getSetting, listReviews } from '@/lib/repo';
import { formatDate } from '@/lib/utils';
import { Stars } from '@/components/ui';
import { ReviewForm } from '@/components/review-form';
import { EmptyState } from '@/components/ui';

export const metadata: Metadata = {
  title: 'Отзывы игроков',
  description: 'Реальные отзывы о магазине KingGrief: скорость выдачи, поддержка, кейсы и привилегии.',
  alternates: { canonical: '/reviews' },
};

export default function ReviewsPage() {
  const reviews = listReviews('approved');
  const enabled = getSetting('reviews_enabled', 'true') === 'true';
  const average = reviews.length ? reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length : 0;

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Отзывы</span>
      </nav>

      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="display text-3xl text-white sm:text-4xl">Отзывы <span className="text-neon">игроков</span></h1>
          <p className="mt-7 max-w-lg text-[0.82rem] leading-relaxed text-white/40">
            Только проверенные модератором отзывы. Мы не удаляем критику и не пишем отзывы за игроков.
          </p>
        </div>
        <div className="flex items-center gap-3 rounded-xl border border-white/7 bg-panel/40 px-4 py-3">
          <span className="stat-num text-3xl font-extrabold text-gold">{average ? average.toFixed(1) : '—'}</span>
          <div>
            <Stars value={average} size={13} />
            <p className="font-mono text-[0.56rem] uppercase tracking-widest text-white/30">{reviews.length} отзывов</p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.4fr_0.6fr]">
        <div className="space-y-3">
          {reviews.length === 0 ? (
            <EmptyState icon={<MessageCircle size={24} />} title="Отзывов пока нет" description="Станьте первым, кто расскажет о покупке." />
          ) : (
            reviews.map((review) => (
              <article key={review.id} className="rounded-2xl border border-white/7 bg-gradient-to-b from-[#171430] to-[#0d0c18] p-4">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2.5">
                    <span className="grid h-9 w-9 place-items-center rounded-lg bg-gradient-to-br from-violet to-neon font-display text-[0.7rem] font-extrabold text-white">
                      {review.nickname.slice(0, 2).toUpperCase()}
                    </span>
                    <div>
                      <p className="text-[0.82rem] font-bold text-white/90">{review.nickname}</p>
                      <p className="font-mono text-[0.56rem] uppercase tracking-widest text-white/25">{formatDate(review.createdAt)}</p>
                    </div>
                  </div>
                  <Stars value={review.rating} />
                </div>
                <p className="mt-3 text-[0.82rem] leading-relaxed text-white/55">{review.text}</p>
              </article>
            ))
          )}
        </div>

        <div className="lg:sticky lg:top-24 lg:h-fit">
          {enabled ? (
            <ReviewForm />
          ) : (
            <div className="rounded-2xl border border-white/8 bg-panel/40 p-5 text-center">
              <Star size={20} className="mx-auto text-white/30" />
              <p className="mt-2 text-[0.8rem] font-bold text-white">Отзывы временно закрыты</p>
              <p className="mt-1 text-[0.72rem] text-white/40">Администрация отключила приём отзывов.</p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
