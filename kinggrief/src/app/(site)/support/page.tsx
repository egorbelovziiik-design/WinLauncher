import type { Metadata } from 'next';
import Link from 'next/link';
import { BookOpen, CreditCard, Headphones, LifeBuoy, Mail, MessagesSquare, MonitorPlay, Search, ShieldQuestion, Wallet } from 'lucide-react';
import { getSettings, listReviews } from '@/lib/repo';
import { Accordion, CopyButton, Reveal, SectionHeading } from '@/components/ui';
import { DiscordGlyph, TelegramGlyph } from '@/components/icons';

export const metadata: Metadata = {
  title: 'Поддержка — помощь с заказом и подключением',
  description: 'Поддержка KingGrief: как подключиться к серверу, что делать если не пришла покупка, возвраты и оплата.',
  alternates: { canonical: '/support' },
};

const FAQ = [
  {
    q: 'Покупка оплачена, но не пришла',
    a: 'Откройте страницу заказа в разделе «Мои заказы» или по номеру из чека. Если статус «Оплачен», а выдачи нет — сервер мог быть офлайн. Напишите в поддержку номер заказа: оператор увидит задачу выдачи и завершит её вручную.',
  },
  {
    q: 'Как подключиться к серверу',
    a: 'Запустите Minecraft Java Edition (1.16.5–1.21.4), выберите «Сетевая игра» → «Добавить сервер», вставьте IP из блока ниже и зайдите. Лицензия и пиратка поддерживаются, белый список не требуется.',
  },
  {
    q: 'Не проходит оплата картой',
    a: 'Проверьте лимиты карты и 3-D Secure. Если провайдер отклонил платёж, заказ останется в статусе «Ожидает оплаты» — можно попробовать снова или выбрать другой способ. Деньги за отклонённые платежи не списываются.',
  },
  {
    q: 'Хочу вернуть деньги',
    a: 'Цифровые товары выдаются моментально, поэтому возврат возможен при технической невозможности выдачи или если наказание не подлежит снятию. Напишите в поддержку с номером заказа.',
  },
  {
    q: 'Купил не на тот никнейм',
    a: 'Если товар ещё не выдан, мы можем изменить получателя до выдачи. После выдачи перенос возможен только по решению администрации. Указывайте никнейм внимательно — он проверяется автоматически.',
  },
  {
    q: 'Есть ли скидки и промокоды',
    a: 'Да. Промокоды действуют на корзину: KING10 (10%), WELCOME (15% от 300 ₽), GRIEF20 (20% от 500 ₽). Актуальные акции публикуются в Discord.',
  },
];

const CONTACTS = [
  { icon: MessagesSquare, title: 'Discord', text: 'Самый быстрый канал. Тикеты и живые ответы.', hrefKey: 'discord_url', color: '#5865F2' },
  { icon: Headphones, title: 'Telegram', text: 'Дежурный оператор и уведомления об акциях.', hrefKey: 'telegram_url', color: '#22D3EE' },
  { icon: Mail, title: 'Email', text: 'Для юридических вопросов и возвратов.', hrefKey: 'support_email', color: '#A855F7', mail: true },
];

export default function SupportPage() {
  const settings = getSettings();
  const reviewsCount = listReviews('approved').length;

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Поддержка</span>
      </nav>

      <div className="mb-8 max-w-2xl">
        <span className="chip mb-3"><LifeBuoy size={12} /> поддержка 24/7</span>
        <h1 className="display text-3xl text-white sm:text-4xl">Чем <span className="text-neon">помочь?</span></h1>
        <p className="mt-2 text-[0.85rem] leading-relaxed text-white/45">
          Сначала проверьте статус заказа — там видно, оплачен ли он и передана ли задача на сервер. Если вопрос не
          решается, напишите нам: ответ в среднем за 15 минут.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Reveal>
          <Link href="/profile" className="group flex h-full flex-col rounded-2xl border border-white/7 bg-gradient-to-b from-[#191631] to-[#0d0c18] p-5 transition hover:border-neon/30">
            <span className="grid h-10 w-10 place-items-center rounded-lg bg-neon/12 text-neon"><Search size={18} /></span>
            <p className="mt-3 font-display text-sm font-bold text-white">Проверить заказ</p>
            <p className="mt-1 text-[0.74rem] leading-relaxed text-white/40">Найти по номеру KG-XXXXX и посмотреть статус выдачи.</p>
            <span className="mt-auto pt-3 font-mono text-[0.6rem] text-neon opacity-0 transition group-hover:opacity-100">открыть →</span>
          </Link>
        </Reveal>
        {CONTACTS.map((contact, i) => {
          const href = contact.mail ? `mailto:${settings[contact.hrefKey]}` : settings[contact.hrefKey] || '#';
          return (
            <Reveal key={contact.title} delay={(i + 1) * 0.05}>
              <a href={href} target={contact.mail ? undefined : '_blank'} rel="noreferrer noopener" className="group flex h-full flex-col rounded-2xl border border-white/7 bg-panel/40 p-5 transition hover:border-white/15">
                <span className="grid h-10 w-10 place-items-center rounded-lg" style={{ background: `${contact.color}18`, color: contact.color }}>
                  <contact.icon size={18} />
                </span>
                <p className="mt-3 font-display text-sm font-bold text-white">{contact.title}</p>
                <p className="mt-1 text-[0.74rem] leading-relaxed text-white/40">{contact.text}</p>
                <span className="mt-auto pt-3 font-mono text-[0.6rem] text-white/30 transition group-hover:text-white/70">написать →</span>
              </a>
            </Reveal>
          );
        })}
      </div>

      <div className="mt-10 grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="rounded-2xl border border-neon/20 bg-gradient-to-br from-[#1b1734] to-[#0b0a14] p-5">
          <div className="flex items-center gap-2">
            <MonitorPlay size={17} className="text-neon" />
            <h2 className="font-display text-base font-bold uppercase tracking-tight text-white">Как подключиться</h2>
          </div>
          <ol className="mt-4 space-y-3">
            {[
              'Установите Minecraft Java Edition и запустите нужную версию.',
              'Откройте «Сетевая игра» → «Добавить сервер».',
              'Вставьте IP и название KingGrief, сохраните.',
              'Подключитесь и введите /menu для быстрого старта.',
            ].map((step, i) => (
              <li key={step} className="flex gap-3">
                <span className="grid h-6 w-6 shrink-0 place-items-center rounded-md bg-neon/15 font-mono text-[0.62rem] font-bold text-neon">
                  {i + 1}
                </span>
                <span className="text-[0.8rem] leading-relaxed text-white/55">{step}</span>
              </li>
            ))}
          </ol>
          <div className="mt-4 flex flex-col gap-2 rounded-xl border border-white/8 bg-void/50 p-3 sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1">
              <p className="font-mono text-[0.54rem] uppercase tracking-widest text-white/30">IP сервера</p>
              <p className="truncate font-display text-sm font-bold text-white">{settings.server_ip}</p>
            </div>
            <CopyButton value={settings.server_ip} label="Копировать" copyTitle="IP сервера скопирован!" variant="primary" className="btn-sm" />
          </div>
          <p className="mt-2 font-mono text-[0.58rem] uppercase tracking-widest text-white/25">Java {settings.server_version}</p>
        </div>

        <div className="space-y-4">
          <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
            <div className="flex items-center gap-2">
              <CreditCard size={16} className="text-neon" />
              <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/75">Оплата и выдача</h2>
            </div>
            <ul className="mt-3 space-y-2 text-[0.78rem] leading-relaxed text-white/45">
              <li className="flex gap-2"><Wallet size={13} className="mt-0.5 shrink-0 text-emerald" /> Оплата: ЮKassa и Robokassa — карты, СБП, кошельки.</li>
              <li className="flex gap-2"><ShieldQuestion size={13} className="mt-0.5 shrink-0 text-emerald" /> Товар выдаётся только после подтверждения платежа провайдером.</li>
              <li className="flex gap-2"><BookOpen size={13} className="mt-0.5 shrink-0 text-emerald" /> Если статус «Оплачен» больше 10 минут — напишите нам номер заказа.</li>
            </ul>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {['ЮKassa', 'Robokassa', 'СБП', 'Карты РФ'].map((p) => (
                <span key={p} className="rounded border border-white/8 px-1.5 py-0.5 font-mono text-[0.55rem] text-white/35">{p}</span>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-white/8 bg-panel/40 p-5">
            <div className="flex items-center gap-2">
              <ShieldQuestion size={16} className="text-amber" />
              <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/75">Сроки ответа</h2>
            </div>
            <dl className="mt-3 space-y-1.5 text-[0.76rem]">
              <div className="flex justify-between text-white/40"><dt>Discord-тикет</dt><dd className="font-semibold text-white/70">≈ 15 минут</dd></div>
              <div className="flex justify-between text-white/40"><dt>Telegram</dt><dd className="font-semibold text-white/70">≈ 30 минут</dd></div>
              <div className="flex justify-between text-white/40"><dt>Email / возвраты</dt><dd className="font-semibold text-white/70">до 3 рабочих дней</dd></div>
            </dl>
            <p className="mt-3 text-[0.64rem] text-white/25">Отзывов опубликовано: {reviewsCount}. Читайте реальные истории покупок.</p>
            <Link href="/reviews" className="btn btn-ghost btn-sm mt-3 w-full">Смотреть отзывы</Link>
          </div>
        </div>
      </div>

      <div className="mt-10">
        <SectionHeading eyebrow="faq" title="Частые вопросы" description="Не нашли ответ? Напишите в Discord — разберёмся вместе." />
        <Accordion items={FAQ} />
      </div>
    </section>
  );
}
