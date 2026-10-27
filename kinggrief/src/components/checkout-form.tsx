'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { motion } from 'framer-motion';
import { AlertTriangle, ArrowRight, CreditCard, Lock, ShieldCheck, Tag, User, Wallet } from 'lucide-react';
import type { CardMethod } from '@/lib/payments';
import { formatPrice } from '@/lib/utils';
import { useCart, useToast } from './providers';
import { EmptyState } from './ui';

const NICK_RE = /^[A-Za-z0-9_]{3,16}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function CheckoutForm({
  provider,
  testMode,
  user = null,
  methodOptions = [],
}: {
  provider: string;
  testMode: boolean;
  user?: { username: string; email: string } | null;
  methodOptions?: { id: CardMethod; label: string; hint: string }[];
}) {
  const router = useRouter();
  const { items, subtotal, discount, total, promo, applyPromo, clearPromo, clear, nickname, setNickname, rememberOrder, ready, count } = useCart();
  const { push } = useToast();

  const [nick, setNick] = useState(nickname || user?.username || '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [code, setCode] = useState(promo?.code ?? '');
  const [agree, setAgree] = useState(false);
  const [method, setMethod] = useState<'kassa' | CardMethod>(methodOptions[0]?.id ?? 'kassa');
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [promoBusy, setPromoBusy] = useState(false);

  const itemIssue = useMemo(() => items.find((i) => i.quantity < 1), [items]);

  if (ready && items.length === 0) {
    return (
      <EmptyState
        title="Нечего оформлять"
        description="Добавьте хотя бы один товар — и вернитесь к оформлению."
        action={<Link href="/privileges" className="btn btn-primary">В магазин</Link>}
      />
    );
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!NICK_RE.test(nick.trim())) next.nickname = 'Никнейм: 3–16 символов, латиница, цифры и _';
    if (email && !EMAIL_RE.test(email.trim())) next.email = 'Проверьте формат email';
    if (!agree) next.agree = 'Подтвердите согласие с правилами';
    if (items.length === 0) next.items = 'Корзина пуста';
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          nickname: nick.trim(),
          email: email.trim() || undefined,
          items: items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
          promoCode: promo?.code ?? null,
          note: note.trim() || undefined,
          method: method === 'kassa' ? undefined : method,
          agree: true,
        }),
      });
      const data = (await res.json()) as { error?: string; publicId?: string; paymentUrl?: string; demo?: boolean; total?: number };
      if (!res.ok || !data.publicId) {
        push({ tone: 'error', title: 'Заказ не создан', description: data.error });
        setSubmitting(false);
        return;
      }
      setNickname(nick.trim());
      rememberOrder(data.publicId);
      clear();
      push({ tone: 'success', title: `Заказ ${data.publicId} создан`, description: 'Перенаправляем на оплату…' });
      if (data.paymentUrl && /^https?:\/\//i.test(data.paymentUrl)) {
        window.location.href = data.paymentUrl;
      } else if (data.paymentUrl) {
        router.push(data.paymentUrl);
      } else {
        router.push(`/checkout/${data.publicId}`);
      }
    } catch {
      push({ tone: 'error', title: 'Сеть недоступна', description: 'Проверьте соединение и попробуйте снова.' });
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]" noValidate>
      <div className="space-y-5">
        <section className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-neon/15 font-mono text-[0.62rem] font-bold text-neon">1</span>
            <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/80">Получатель</h2>
            {user ? (
              <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald/12 px-2 py-0.5 font-mono text-[0.55rem] font-bold uppercase tracking-widest text-emerald">
                <ShieldCheck size={10} /> заказ на аккаунт {user.username}
              </span>
            ) : (
              <Link href="/login?next=/checkout" className="ml-auto text-[0.68rem] font-semibold text-neon hover:underline">
                Войти в аккаунт
              </Link>
            )}
          </div>

          <label className="block">
            <span className="field-label">Minecraft-никнейм получателя (можно свой или друга) *</span>
            <div className="relative">
              <User size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
              <input
                value={nick}
                onChange={(e) => setNick(e.target.value)}
                placeholder="Любой ник на сервере: Steve_2026"
                className="field pl-9 pr-20"
                autoComplete="username"
                maxLength={16}
                aria-invalid={Boolean(errors.nickname)}
                aria-describedby={errors.nickname ? 'nick-error' : undefined}
              />
              {nick && (
                <button
                  type="button"
                  onClick={() => setNick('')}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md border border-white/10 bg-white/5 px-2 py-0.5 text-[0.6rem] text-white/55 hover:text-white"
                >
                  Другой ник
                </button>
              )}
            </div>
            <span className="mt-1 block text-[0.66rem] text-white/30">Именно на этот никнейм придёт покупка (себе или в подарок любому игроку).</span>
            {errors.nickname && <span id="nick-error" className="mt-1 block text-[0.7rem] font-semibold text-danger">{errors.nickname}</span>}
          </label>

          <label className="mt-4 block">
            <span className="field-label">Email для чека (необязательно)</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@mail.ru"
              className="field"
              autoComplete="email"
              maxLength={120}
              aria-invalid={Boolean(errors.email)}
            />
            <span className="mt-1 block text-[0.66rem] text-white/30">На почту отправит чек платёжная система. Мы не храним платёжные данные.</span>
            {errors.email && <span className="mt-1 block text-[0.7rem] font-semibold text-danger">{errors.email}</span>}
          </label>

          <label className="mt-4 block">
            <span className="field-label">Комментарий (необязательно)</span>
            <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Например: друг попросил KING" className="field" maxLength={300} />
          </label>
        </section>

          <section className="rounded-2xl border border-white/8 bg-panel/40 p-5">
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <Wallet className="h-4 w-4 text-neon" aria-hidden />
              <h2 className="font-mono text-[0.7rem] uppercase tracking-widest text-white/60">Способ оплаты</h2>
            </div>
            <div className="grid gap-2.5 sm:grid-cols-2 xl:grid-cols-4" role="radiogroup" aria-label="Способ оплаты">
              {methodOptions.map((m) => {
                return (
                  <button
                    key={m.id}
                    type="button"
                    role="radio"
                    aria-checked={method === m.id}
                    onClick={() => setMethod(m.id)}
                    className={
                      'rounded-xl border p-3 text-left transition ' +
                      (method === m.id
                        ? 'border-neon/70 bg-neon/10 shadow-[0_0_24px_-8px_var(--neon)]'
                        : 'border-white/10 bg-white/[0.03] hover:border-white/25')
                    }
                  >
                    <span className="block font-mono text-[0.68rem] font-bold uppercase tracking-widest text-white">{m.label}</span>
                    <span className="mt-1 block text-[0.68rem] leading-relaxed text-white/45">{m.hint}</span>
                  </button>
                );
              })}
              <button
                type="button"
                role="radio"
                aria-checked={method === 'kassa'}
                onClick={() => setMethod('kassa')}
                className={
                  'rounded-xl border p-3 text-left transition ' +
                  (method === 'kassa'
                    ? 'border-neon/70 bg-neon/10 shadow-[0_0_24px_-8px_var(--neon)]'
                    : 'border-white/10 bg-white/[0.03] hover:border-white/25')
                }
              >
                <span className="block font-mono text-[0.68rem] font-bold uppercase tracking-widest text-white">Касса</span>
                <span className="mt-1 block text-[0.68rem] leading-relaxed text-white/45">
                  Онлайн-касса провайдера ({provider})
                </span>
              </button>
            </div>
            {methodOptions.length > 0 && method !== 'kassa' && (
              <p className="mt-3 rounded-lg border border-white/8 bg-white/[0.03] p-3 text-[0.7rem] leading-relaxed text-white/50">
                Счёт на оплату создаётся сразу после подтверждения заказа: сумма в выбранной валюте, код заказа и
                инструкция будут на странице заказа, там же кнопка «Я оплатил(а)». Мы не запрашиваем данные вашего
                способа оплаты.
              </p>
            )}
          </section>

        <section className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <div className="mb-4 flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-neon/15 font-mono text-[0.62rem] font-bold text-neon">2</span>
            <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/80">Промокод</h2>
          </div>
          {promo?.code && !promo.error ? (
            <div className="flex items-center justify-between rounded-lg border border-emerald/25 bg-emerald/8 px-3 py-2.5">
              <span className="inline-flex items-center gap-1.5 text-[0.78rem] font-bold text-emerald">
                <Tag size={13} /> {promo.code} · скидка {formatPrice(promo.discount)}
              </span>
              <button type="button" onClick={clearPromo} className="text-[0.7rem] text-white/40 hover:text-white">убрать</button>
            </div>
          ) : (
            <div className="flex gap-2">
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="KING10"
                className="field uppercase tracking-widest"
                maxLength={32}
                aria-label="Промокод"
              />
              <button
                type="button"
                className="btn btn-ghost"
                disabled={promoBusy || !code.trim()}
                onClick={async () => {
                  setPromoBusy(true);
                  await applyPromo(code);
                  setPromoBusy(false);
                }}
              >
                {promoBusy ? '…' : 'Применить'}
              </button>
            </div>
          )}
          {promo?.error && <p className="mt-1.5 text-[0.7rem] text-danger">{promo.error}</p>}
          <p className="mt-2 text-[0.66rem] text-white/25">Промокод пересчитывает сервер — скидка не может быть подменена в браузере.</p>
        </section>

        <section className="rounded-2xl border border-white/8 bg-panel/40 p-5">
          <div className="mb-3 flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-neon/15 font-mono text-[0.62rem] font-bold text-neon">3</span>
            <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/80">Подтверждение</h2>
          </div>
          <label className="flex cursor-pointer items-start gap-2.5">
            <input
              type="checkbox"
              checked={agree}
              onChange={(e) => setAgree(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-white/20 bg-void accent-[#a855f7]"
            />
            <span className="text-[0.74rem] leading-relaxed text-white/45">
              Я согласен с <Link href="/rules" className="text-neon hover:underline">правилами сервера</Link> и понимаю,
              что цифровой товар выдаётся автоматически после подтверждения оплаты и не подлежит возврату, если выдача
              состоялась.
            </span>
          </label>
          {errors.agree && <p className="mt-1.5 text-[0.7rem] font-semibold text-danger">{errors.agree}</p>}

          <div className="mt-4 flex items-center gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.68rem] text-white/35">
            <Lock size={12} className="text-emerald" />
            Оформление защищено: CSRF-проверка, лимиты запросов и серверный пересчёт цены.
          </div>
        </section>
      </div>

      <aside className="lg:sticky lg:top-24 lg:h-fit">
        <div className="rounded-2xl border border-white/8 bg-gradient-to-b from-[#191530] to-[#0c0b16] p-5">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/75">Ваш заказ</h2>
            <span className="font-mono text-[0.58rem] text-white/30">{count} шт.</span>
          </div>

          <ul className="mt-3 max-h-56 space-y-2 overflow-y-auto pr-1">
            {items.map((item) => (
              <li key={item.productId} className="flex items-start justify-between gap-2 text-[0.76rem]">
                <span className="min-w-0">
                  <span className="block truncate font-semibold text-white/80">{item.name}</span>
                  <span className="font-mono text-[0.6rem] text-white/30">{item.quantity} × {formatPrice(item.price)}</span>
                </span>
                <span className="stat-num shrink-0 font-bold text-white/70">{formatPrice(item.price * item.quantity)}</span>
              </li>
            ))}
          </ul>

          <dl className="mt-4 space-y-1.5 border-t border-white/8 pt-3 text-[0.78rem]">
            <div className="flex justify-between text-white/45"><dt>Товары</dt><dd className="stat-num">{formatPrice(subtotal)}</dd></div>
            {discount > 0 && <div className="flex justify-between text-emerald"><dt>Скидка</dt><dd className="stat-num">−{formatPrice(discount)}</dd></div>}
            <div className="flex items-end justify-between pt-1">
              <dt className="text-[0.7rem] font-bold uppercase tracking-widest text-white/55">Итого</dt>
              <dd className="stat-num text-2xl font-extrabold text-white">{formatPrice(total)}</dd>
            </div>
          </dl>

          {itemIssue && <p className="mt-2 text-[0.68rem] text-danger">Исправьте количество товаров в корзине.</p>}

          <button type="submit" disabled={submitting} className="btn btn-primary mt-4 w-full">
            {submitting ? 'Создаём заказ…' : <>Перейти к оплате <ArrowRight size={15} /></>}
          </button>

          <div className="mt-3 flex items-start gap-2 rounded-lg border border-white/6 bg-void/40 px-2.5 py-2">
            <CreditCard size={13} className="mt-0.5 shrink-0 text-neon" />
            <p className="text-[0.64rem] leading-relaxed text-white/35">
              Провайдер: <span className="font-semibold text-white/60">{provider === 'mock' ? 'демо-оплата' : provider === 'yookassa' ? 'ЮKassa' : 'Robokassa'}</span>
              {testMode && <span className="ml-1 rounded bg-amber/15 px-1 py-0.5 font-mono text-[0.55rem] uppercase tracking-wide text-amber">test</span>}
              . Никогда не выдаём товар до подтверждения платежа.
            </p>
          </div>

          {testMode && (
            <p className="mt-2 flex items-start gap-1.5 text-[0.63rem] leading-relaxed text-amber/80">
              <AlertTriangle size={11} className="mt-0.5 shrink-0" />
              Включён тестовый режим: заказ можно оплатить демо-платежом без списания денег.
            </p>
          )}

          <div className="mt-3 flex items-center gap-1.5 text-[0.62rem] text-white/25">
            <ShieldCheck size={11} /> Платёжные данные вводятся на стороне провайдера
          </div>
        </div>

        <motion.p layout className="mt-3 text-center text-[0.62rem] text-white/25">
          Нажимая «Перейти к оплате», вы создаёте заказ. Оплата — следующий шаг.
        </motion.p>
      </aside>
    </form>
  );
}
