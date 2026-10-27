'use client';

import { useState } from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, Copy, ExternalLink, Headphones, Landmark, ReceiptText, ShieldCheck, Wallet } from 'lucide-react';

export type InvoiceChannel =
  | { kind: 'link'; url: string }
  | { kind: 'iban'; iban: string; phone?: string }
  | { kind: 'phone'; phone: string }
  | { kind: 'support' };

/**
 * v6.2: нейтральный экран оплаты счёта (Visa Украина / Visa Europe).
 * На странице нет никаких реквизитов получения и никаких упоминаний о них:
 * только сумма, код заказа и канал оплаты, заданный на сервере.
 */
export function PaymentInvoicePanel({
  publicId,
  amount,
  currency,
  symbol,
  methodLabel,
  totalRub,
  channel,
  discordUrl,
}: {
  publicId: string;
  amount: string;
  currency: string;
  symbol: string;
  methodLabel: string;
  totalRub: number;
  channel: InvoiceChannel;
  discordUrl: string;
}) {
  const [copied, setCopied] = useState<'amount' | 'code' | 'iban' | 'phone' | null>(null);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const copy = async (what: 'amount' | 'code' | 'iban' | 'phone', text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(what);
      setTimeout(() => setCopied(null), 1600);
    } catch {
      /* clipboard недоступен — не критично */
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
        <ReceiptText className="h-4 w-4 text-neon" aria-hidden />
        <h2 className="font-mono text-[0.7rem] uppercase tracking-widest text-white/70">
          Онлайн-оплата · {methodLabel}
        </h2>
        <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-emerald/30 bg-emerald/10 px-2 py-0.5 font-mono text-[0.6rem] uppercase tracking-widest text-emerald">
          <ShieldCheck size={10} /> счёт защищён
        </span>
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_1fr]">
        <div className="rounded-xl border border-white/10 bg-gradient-to-br from-white/[0.06] to-transparent p-4">
          <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/40">Сумма к оплате</span>
          <div className="mt-2 font-display text-2xl font-bold text-neon">
            {amount} {symbol}
          </div>
          <div className="mt-1 text-[0.66rem] text-white/40">
            эквивалент заказа {totalRub} ₽ · валюта {currency}
          </div>
          <button type="button" onClick={() => copy('amount', amount)} className="btn btn-ghost mt-3 px-2 py-1 text-[0.64rem]">
            {copied === 'amount' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
            {copied === 'amount' ? ' скопировано' : ' копировать сумму'}
          </button>
        </div>

        <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
          <span className="font-mono text-[0.6rem] uppercase tracking-widest text-white/40">Код заказа</span>
          <div className="mt-2 font-mono text-lg font-bold tracking-[0.12em] text-white">{publicId}</div>
          <div className="mt-1 text-[0.66rem] text-white/40">укажите его в комментарии к платежу</div>
          <button type="button" onClick={() => copy('code', publicId)} className="btn btn-ghost mt-3 px-2 py-1 text-[0.64rem]">
            {copied === 'code' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
            {copied === 'code' ? ' скопировано' : ' копировать код'}
          </button>
        </div>
      </div>

      <div className="mt-4 rounded-xl border border-white/10 bg-white/[0.03] p-4">
        {channel.kind === 'link' && (
          <>
            <p className="text-[0.74rem] leading-relaxed text-white/60">
              Нажмите кнопку — откроется защищённая страница оплаты вашего банка. После успешного платежа вернитесь на
              эту страницу и нажмите «Я оплатил(а)».
            </p>
            <a href={channel.url} target="_blank" rel="noopener noreferrer" className="btn btn-primary mt-3">
              <ExternalLink size={14} /> Перейти к оплате
            </a>
          </>
        )}
        {channel.kind === 'iban' && (
          <>
            <p className="mb-2 flex items-center gap-1.5 font-mono text-[0.6rem] uppercase tracking-widest text-white/40">
              <Landmark size={12} /> реквизиты получателя
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <code className="rounded-lg bg-void/60 px-2.5 py-1.5 font-mono text-[0.78rem] font-bold tracking-[0.08em] text-white">
                {channel.iban}
              </code>
              <button type="button" onClick={() => copy('iban', channel.iban)} className="btn btn-ghost px-2 py-1 text-[0.64rem]">
                {copied === 'iban' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
                {copied === 'iban' ? ' скопировано' : ' копировать'}
              </button>
            </div>
            {channel.phone && (
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="text-[0.7rem] text-white/45">или по номеру:</span>
                <code className="rounded-lg bg-void/60 px-2.5 py-1 font-mono text-[0.74rem] font-bold text-white">{channel.phone}</code>
                <button type="button" onClick={() => copy('phone', channel.phone!)} className="btn btn-ghost px-2 py-1 text-[0.64rem]">
                  {copied === 'phone' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
                  {copied === 'phone' ? ' скопировано' : ' копировать'}
                </button>
              </div>
            )}
            <p className="mt-2 text-[0.7rem] leading-relaxed text-white/50">
              В приложении банка выберите «Перевод по реквизитам» и вставьте данные выше.
            </p>
          </>
        )}
        {channel.kind === 'phone' && (
          <>
            <p className="mb-2 font-mono text-[0.6rem] uppercase tracking-widest text-white/40">номер получателя</p>
            <div className="flex flex-wrap items-center gap-2">
              <code className="rounded-lg bg-void/60 px-2.5 py-1.5 font-mono text-[0.8rem] font-bold text-white">{channel.phone}</code>
              <button type="button" onClick={() => copy('phone', channel.phone)} className="btn btn-ghost px-2 py-1 text-[0.64rem]">
                {copied === 'phone' ? <CheckCircle2 size={12} className="text-emerald" /> : <Copy size={12} />}
                {copied === 'phone' ? ' скопировано' : ' копировать'}
              </button>
            </div>
            <p className="mt-2 text-[0.7rem] leading-relaxed text-white/50">
              В приложении банка выберите «Перевод по номеру телефона» и вставьте номер выше.
            </p>
          </>
        )}
        {channel.kind === 'support' && (
          <>
            <p className="text-[0.74rem] leading-relaxed text-white/60">
              Счёт создан и закреплён за вашим заказом. Поддержка пришлёт данные для оплаты и подтвердит поступление —
              обычно в течение 10 минут.
            </p>
            <a href={discordUrl} target="_blank" rel="noopener noreferrer" className="btn btn-primary mt-3">
              <Headphones size={14} /> Написать в поддержку
            </a>
          </>
        )}
      </div>

      <ol className="mt-4 space-y-1.5 text-[0.74rem] leading-relaxed text-white/55">
        <li>
          <b className="text-white/85">1.</b> Оплатите ровно <b className="text-white/85">{amount} {symbol}</b> способом выше.
        </li>
        <li>
          <b className="text-white/85">2.</b> В комментарии к платежу укажите код заказа:{' '}
          <code className="rounded bg-white/10 px-1.5 py-0.5 font-mono text-neon">{publicId}</code>
        </li>
        <li>
          <b className="text-white/85">3.</b> Нажмите «Я оплатил(а)» — состав подтвердит поступление, и заказ выдастся на
          сервер автоматически.
        </li>
      </ol>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {!sent ? (
          <button type="button" onClick={claimSent} disabled={busy} className="btn btn-primary">
            {busy ? 'Отправляем…' : 'Я оплатил(а) — подтвердить заявку'}
          </button>
        ) : (
          <span className="inline-flex items-center gap-2 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-2 text-[0.74rem] font-semibold text-emerald">
            <CheckCircle2 size={14} /> Заявка принята: ждём подтверждения поступления составом.
          </span>
        )}
        <span className="inline-flex items-center gap-1.5 text-[0.66rem] text-white/35">
          <Wallet size={12} /> Мы не запрашиваем и не храним данные вашего способа оплаты.
        </span>
      </div>
      {error && <p className="mt-2 text-[0.7rem] font-semibold text-danger">{error}</p>}
    </motion.section>
  );
}
