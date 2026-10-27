'use client';

import { useState } from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, Copy, CreditCard, Lock, ShieldCheck, Wallet } from 'lucide-react';

/**
 * v6.1: экран card-to-card перевода (Visa Украина / Visa Europe).
 * Полные реквизиты приходят сюда серверным рендером только для страницы
 * конкретного заказа и нигде больше не показываются и не логируются.
 */
export function CardTransferPanel({
  publicId,
  panFormatted,
  masked,
  amount,
  currency,
  symbol,
  methodLabel,
  totalRub,
}: {
  publicId: string;
  panFormatted: string;
  masked: string;
  amount: string;
  currency: string;
  symbol: string;
  methodLabel: string;
  totalRub: number;
}) {
  const [copied, setCopied] = useState<'pan' | 'amount' | null>(null);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const copy = async (what: 'pan' | 'amount') => {
    const text = what === 'pan' ? panFormatted.replace(/\s/g, '') : amount;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(what);
      setTimeout(() => setCopied(null), 1600);
    } catch {
      /* clipboard может быть недоступен — не критично */
    }
  };

  const claimSent = async () => {
    setBusy(true);
    setError('');
    try {
      const res = await fetch('/api/payments/sent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ publicId }),
      });
      const data = (await res.json()) as { ok?: boolean; error?: string };
      if (!res.ok || !data.ok) throw new Error(data.error || 'Не удалось отправить заявку');
      setSent(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка сети');
    } finally {
      setBusy(false);
    }
  };

  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-2xl border border-neon/30 bg-panel/60 p-5 shadow-[0_0_60px_-24px_var(--neon)]"
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Wallet className="h-4 w-4 text-neon" aria-hidden />
        <h2 className="font-mono text-[0.7rem] uppercase tracking-widest text-white/70">
          Оплата переводом · {methodLabel}
        </h2>
        <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-emerald/30 bg-emerald/10 px-2 py-0.5 font-mono text-[0.6rem] uppercase tracking-widest text-emerald">
          <Lock size={10} /> без сбора данных карты
        </span>
      </div>

      <div className="grid gap-4 md:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-xl border border-white/10 bg-gradient-to-br from-white/[0.06] to-transparent p-4">
          <div className="flex items-center justify-between">
            <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/40">Карта получателя</span>
            <CreditCard className="h-4 w-4 text-white/40" aria-hidden />
          </div>
          <div className="mt-3 font-mono text-lg font-bold tracking-[0.14em] text-white sm:text-xl">{panFormatted}</div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="font-mono text-[0.62rem] text-white/35">{masked}</span>
            <button type="button" onClick={() => copy('pan')} className="btn btn-ghost px-2 py-1 text-[0.64rem]">
              {copied === 'pan' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
              {copied === 'pan' ? ' скопировано' : ' копировать номер'}
            </button>
          </div>
        </div>

        <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
          <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/40">Сумма перевода</span>
          <div className="mt-2 font-display text-2xl font-bold text-neon">
            {amount} {symbol}
          </div>
          <div className="mt-1 text-[0.66rem] text-white/40">
            эквивалент заказа {totalRub} ₽ · валюта {currency}
          </div>
          <button type="button" onClick={() => copy('amount')} className="btn btn-ghost mt-3 px-2 py-1 text-[0.64rem]">
            {copied === 'amount' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
            {copied === 'amount' ? ' скопировано' : ' копировать сумму'}
          </button>
        </div>
      </div>

      <ol className="mt-4 space-y-1.5 text-[0.74rem] leading-relaxed text-white/55">
        <li>
          <b className="text-white/85">1.</b> Откройте приложение своего банка → «Перевод по номеру карты» и вставьте карту
          получателя выше.
        </li>
        <li>
          <b className="text-white/85">2.</b> Переведите ровно <b className="text-white/85">{amount} {symbol}</b>. В комментарии
          перевода укажите код заказа: <code className="rounded bg-white/10 px-1.5 py-0.5 font-mono text-neon">{publicId}</code>
        </li>
        <li>
          <b className="text-white/85">3.</b> Нажмите «Я перевёл(а)» — состав увидит заявку и подтвердит поступление
          (обычно до 10 минут). После подтверждения заказ выдастся на сервер автоматически.
        </li>
      </ol>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {!sent ? (
          <button type="button" onClick={claimSent} disabled={busy} className="btn btn-primary">
            {busy ? 'Отправляем…' : 'Я перевёл(а) — подтвердить заявку'}
          </button>
        ) : (
          <span className="inline-flex items-center gap-2 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-2 text-[0.74rem] font-semibold text-emerald">
            <CheckCircle2 size={14} /> Заявка принята: ждём подтверждения поступления составом.
          </span>
        )}
        <span className="inline-flex items-center gap-1.5 text-[0.66rem] text-white/35">
          <ShieldCheck size={12} /> Реквизиты видны только на этой странице заказа.
        </span>
      </div>
      {error && <p className="mt-2 text-[0.7rem] font-semibold text-danger">{error}</p>}
    </motion.section>
  );
}
