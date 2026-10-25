'use client';

import { useState } from 'react';
import { Star } from 'lucide-react';
import { useToast } from './providers';

export function ReviewForm() {
  const { push } = useToast();
  const [nickname, setNickname] = useState('');
  const [rating, setRating] = useState(5);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!/^[A-Za-z0-9_]{3,16}$/.test(nickname.trim())) {
      setError('Никнейм: 3–16 символов, латиница, цифры и _');
      return;
    }
    if (text.trim().length < 12) {
      setError('Расскажите чуть подробнее — минимум 12 символов');
      return;
    }
    setBusy(true);
    try {
      const res = await fetch('/api/reviews', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname: nickname.trim(), rating, text: text.trim() }),
      });
      const data = (await res.json()) as { error?: string; message?: string };
      if (!res.ok) {
        setError(data.error ?? 'Не удалось отправить отзыв');
        return;
      }
      setDone(true);
      push({ tone: 'success', title: 'Отзыв отправлен', description: data.message });
    } catch {
      setError('Сеть недоступна, попробуйте позже');
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <div className="rounded-2xl border border-emerald/25 bg-emerald/8 p-5 text-center">
        <p className="font-display text-base font-bold text-white">Спасибо за отзыв!</p>
        <p className="mt-1 text-[0.78rem] text-white/45">Он появится на сайте после проверки модератором.</p>
        <button onClick={() => { setDone(false); setText(''); }} className="btn btn-ghost btn-sm mt-4">Оставить ещё</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="rounded-2xl border border-white/8 bg-panel/40 p-5">
      <p className="font-display text-sm font-bold uppercase tracking-widest text-white/75">Оставить отзыв</p>
      <p className="mt-1 text-[0.7rem] text-white/30">Публикуется после проверки. Без оскорблений и рекламы.</p>

      <label className="mt-4 block">
        <span className="field-label">Ваш никнейм</span>
        <input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="Steve_" className="field" maxLength={16} />
      </label>

      <div className="mt-3">
        <span className="field-label">Оценка</span>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setRating(value)}
              aria-label={`${value} из 5`}
              aria-pressed={rating === value}
              className="transition hover:scale-110"
            >
              <Star size={24} className={value <= rating ? 'text-gold' : 'text-white/15'} fill={value <= rating ? 'currentColor' : 'none'} />
            </button>
          ))}
        </div>
      </div>

      <label className="mt-3 block">
        <span className="field-label">Отзыв</span>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={4}
          maxLength={600}
          placeholder="Что понравилось, как быстро пришла покупка…"
          className="field h-auto resize-none py-2.5"
        />
        <span className="mt-1 block text-right font-mono text-[0.58rem] text-white/25">{text.length}/600</span>
      </label>

      {error && <p className="text-[0.72rem] font-semibold text-danger">{error}</p>}

      <button className="btn btn-primary mt-2 w-full" disabled={busy}>
        {busy ? 'Отправляем…' : 'Отправить отзыв'}
      </button>
    </form>
  );
}
