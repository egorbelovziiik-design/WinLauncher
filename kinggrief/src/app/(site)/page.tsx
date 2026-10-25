import Link from 'next/link';
import type { Metadata } from 'next';
import {
  ArrowRight, BadgeCheck, Clock, Flame, Gauge, Globe, Headphones, Quote, ShieldCheck, Sparkles, Swords, Wallet,
} from 'lucide-react';
import { getSettings, listCaseGroups, listCaseItems, listProducts, listReviews, recentPublicOrders } from '@/lib/repo';
import { bridgeStatus } from '@/lib/delivery';
import { mergePackagesForCases } from '@/lib/cases';
import { formatNumber, formatPrice, relativeTime } from '@/lib/utils';
import { HeroScene, Chest3D, Coin } from '@/components/voxel';
import { RankCard, AddToCartButton, RelicCard } from '@/components/product-card';
import { Accordion, CopyButton, Marquee, Reveal, SectionHeading, Stars } from '@/components/ui';
import { DiscordGlyph, TelegramGlyph, Icon } from '@/components/icons';
import { OnlinePanel } from '@/components/online-panel';

export const metadata: Metadata = {
  title: 'KINGGRIEF — Твоя империя грифа',
  description:
    'Донат-магазин Minecraft-сервера KingGrief: 14 привилегий от 19 ₽, 3 кейса, рилики, разбаны. Мгновенная выдача, честные шансы, оплата ЮKassa и Robokassa.',
  alternates: { canonical: '/' },
};

const WHY = [
  {
    icon: Wallet,
    title: 'Выдача за секунды',
    text: 'После подтверждения оплаты заказ попадает в очередь сервера и выдаётся автоматически через плагин KingGriefBridge.',
    color: '#A855F7',
  },
  {
    icon: ShieldCheck,
    title: 'Проверка каждого платежа',
    text: 'Цена пересчитывается на сервере, вебхуки проверяются по API провайдера, повторная выдача исключена.',
    color: '#22C55E',
  },
  {
    icon: Sparkles,
    title: 'Честные шансы',
    text: 'Вероятности кейсов хранятся в базе и видны на странице каждого кейса. Никаких «динамических» подкруток.',
    color: '#FACC15',
  },
  {
    icon: Headphones,
    title: 'Живая поддержка',
    text: 'Discord и Telegram, ответ в течение 15 минут. Если автоматика дала сбой — оператор выдаст вручную.',
    color: '#3B82F6',
  },
  {
    icon: Gauge,
    title: 'Без лагов',
    text: 'Paper/Purpur, выделенные ядра и оптимизированные чанки. Стабильный TPS даже на массовых ивентах.',
    color: '#EC4899',
  },
  {
    icon: Swords,
    title: 'Сезоны и ивенты',
    text: 'Каждый сезон — новые механики, кейсы и награды. Прогресс привилегий сохраняется между сезонами.',
    color: '#22D3EE',
  },
];

const FAQ = [
  {
    q: 'Как быстро придёт покупка?',
    a: 'Автоматически — сразу после подтверждения оплаты платёжной системой. Обычно это 5–30 секунд. Если сервер офлайн или плагин не подключён, заказ попадёт в очередь и будет выдан при следующем подключении, либо вручную оператором.',
  },
  {
    q: 'Что делать, если товар не пришёл?',
    a: 'Откройте страницу заказа по ссылке из чека — там виден статус. Если статус «оплачен», но выдачи нет, напишите в поддержку с номером заказа. Мы проверим задачу выдачи и завершим её вручную.',
  },
  {
    q: 'Можно ли купить привилегию другому игроку?',
    a: 'Да. В оформлении заказа укажите никнейм получателя — именно на него будет выдан товар. Никнейм проверяется на допустимые символы и не может быть изменён после оплаты.',
  },
  {
    q: 'Какие способы оплаты доступны?',
    a: 'ЮKassa и Robokassa: банковские карты, СБП, электронные кошельки. В тестовом режиме доступна демонстрационная оплата без списания денег — такие заказы помечаются как демо.',
  },
  {
    q: 'Возврат средств возможен?',
    a: 'Цифровые товары выдаются моментально, поэтому возврат возможен только при технической ошибке и невозможности выдачи. Обращения рассматриваются индивидуально через поддержку.',
  },
  {
    q: 'Это официальный сервер Minecraft?',
    a: 'Нет. KINGGRIEF — независимый проект. Мы не связаны с Mojang Studios и Microsoft. Оплата — добровольная поддержка сервера и не даёт прав на игру.',
  },
];

const PAYMENT_LABELS = ['ЮKassa', 'Robokassa', 'СБП', 'Карты РФ', 'Демо-режим'];

export default async function HomePage() {
  const settings = getSettings();
  const products = listProducts({ activeOnly: true });
  const ranks = products.filter((p) => p.kind === 'rank');
  const cases = mergePackagesForCases(listCaseGroups(true));
  const caseItems = listCaseItems();
  const relics = products.filter((p) => p.kind === 'relic');
  const reviews = listReviews('approved').slice(0, 3);
  const recent = recentPublicOrders(6);
  const bridge = bridgeStatus();
  const online = bridge.connected ? Number(settings.bridge_online || 0) : Number(settings.online_base || 0);
  const max = Number(settings.bridge_max || settings.online_peak || 0);
  const featured = ['rank-king', 'rank-god', 'rank-opka', 'rank-downer']
    .map((id) => ranks.find((r) => r.id === id))
    .filter((r): r is NonNullable<typeof r> => Boolean(r));
  const topRelics = relics.filter((r) => [10000, 25000, 100000].includes(Number(r.meta.amount)));

  const jsonLd = {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Organization',
        name: 'KINGGRIEF',
        url: process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000',
        slogan: settings.site_tagline,
        sameAs: [settings.discord_url, settings.telegram_url].filter(Boolean),
      },
      {
        '@type': 'FAQPage',
        mainEntity: FAQ.map((item) => ({
          '@type': 'Question',
          name: item.q,
          acceptedAnswer: { '@type': 'Answer', text: item.a },
        })),
      },
    ],
  };

  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />

      {/* ───────── hero ───────── */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 grid-bg opacity-70" />
        <div className="pointer-events-none absolute -left-40 top-0 h-[28rem] w-[28rem] rounded-full bg-violet/20 blur-[110px]" />
        <div className="pointer-events-none absolute -right-32 top-20 h-[26rem] w-[26rem] rounded-full bg-pink/12 blur-[120px]" />
        <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-neon/40 to-transparent" />

        <div className="container-x relative grid items-center gap-10 pb-14 pt-12 lg:grid-cols-[1.05fr_0.95fr] lg:gap-6 lg:pb-20 lg:pt-16">
          <div className="relative z-10">
            <Reveal>
              <div className="mb-5 flex flex-wrap items-center gap-2">
                <span className="chip !bg-neon/12 !text-[#e9d5ff]">
                  <Flame size={12} /> Сезон I · открыт
                </span>
                <span className="chip !border-emerald/25 !bg-emerald/8 !text-emerald">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald" /> Сервер онлайн
                </span>
              </div>
            </Reveal>

            <Reveal delay={0.05}>
              <h1 className="display text-[clamp(2.9rem,10vw,5.6rem)] leading-[0.86]">
                <span className="gradient-text glow-text block">KING</span>
                <span className="relative block">
                  <span className="gradient-text glow-text">GRIEF</span>
                  <span className="absolute -right-2 top-1 hidden font-mono text-[0.6rem] font-normal tracking-widest text-neon/60 sm:block">
                    /S1
                  </span>
                </span>
              </h1>
            </Reveal>

            <Reveal delay={0.1}>
              <p className="mt-5 max-w-lg font-display text-[0.78rem] font-bold uppercase leading-relaxed tracking-[0.18em] text-white/70 sm:text-[0.86rem]">
                {settings.hero_subtitle || 'ТВОЯ ИМПЕРИЯ ГРИФА НАЧИНАЕТСЯ ЗДЕСЬ.'}
              </p>
            </Reveal>

            <Reveal delay={0.15}>
              <p className="mt-4 max-w-md text-[0.88rem] leading-relaxed text-white/45">
                {settings.site_description}
              </p>
            </Reveal>

            <Reveal delay={0.2}>
              <div className="mt-7 flex flex-col gap-3 sm:flex-row">
                <Link href="/privileges" className="btn btn-primary btn-lg">
                  Перейти в магазин <ArrowRight size={17} />
                </Link>
                <a href="#connect" className="btn btn-ghost btn-lg">
                  <Globe size={16} /> Начать играть
                </a>
              </div>
            </Reveal>

            <Reveal delay={0.25}>
              <dl className="mt-9 grid max-w-lg grid-cols-3 gap-3">
                {[
                  { label: 'Привилегий', value: `${ranks.length}`, accent: '#A855F7' },
                  { label: 'Кейсов', value: `${cases.length}`, accent: '#FACC15' },
                  { label: 'Онлайн', value: max > 0 ? `${online}/${max}` : '—', accent: '#22C55E' },
                ].map((stat) => (
                  <div key={stat.label} className="rounded-xl border border-white/6 bg-white/[0.02] px-3 py-2.5">
                    <dt className="font-mono text-[0.55rem] uppercase tracking-[0.16em] text-white/30">{stat.label}</dt>
                    <dd className="stat-num mt-1 text-base font-extrabold" style={{ color: stat.accent }}>
                      {stat.value}
                    </dd>
                  </div>
                ))}
              </dl>
            </Reveal>

            <Reveal delay={0.3}>
              <div className="mt-5 flex flex-wrap items-center gap-2 text-[0.7rem] text-white/35">
                <BadgeCheck size={14} className="text-emerald" />
                Оплата через {PAYMENT_LABELS.slice(0, 2).join(' и ')} · выдача после подтверждения
              </div>
            </Reveal>
          </div>

          <div className="relative z-0">
            <HeroScene />
          </div>
        </div>

        <Marquee
          items={[
            'мгновенная выдача',
            'честные шансы',
            '14 привилегий',
            '3 кейса',
            '9 пакетов риликов',
            'оплата картой и СБП',
            'поддержка 24/7',
          ]}
        />
      </section>

      {/* ───────── featured ranks ───────── */}
      <section className="container-x py-16 md:py-24" id="ranks">
        <SectionHeading
          eyebrow="01 / привилегии"
          title={<>Выбор <span className="gradient-text">игроков</span></>}
          description="Четыре привилегии, которые чаще всего берут на KingGrief. Всего в магазине 14 уровней — от PEGAS за 19 ₽ до D.OWNER с 3D-короной."
          action={
            <Link href="/privileges" className="btn btn-ghost">
              Все привилегии <ArrowRight size={15} />
            </Link>
          }
        />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {featured.map((product, i) => (
            <Reveal key={product.id} delay={i * 0.05}>
              <RankCard product={product} compact />
            </Reveal>
          ))}
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-white/6 bg-white/[0.02] px-4 py-3 text-[0.72rem] text-white/40">
          <ShieldCheck size={14} className="text-neon" />
          Полный состав возможностей каждой привилегии открыт на её странице — без скрытых пунктов и «сюрпризов» после оплаты.
          <Link href="/privileges" className="text-neon hover:underline">сравнить привилегии</Link>.
        </div>
      </section>

      {/* ───────── cases ───────── */}
      <section className="relative overflow-hidden border-y border-neon/10 bg-abyss/50 py-16 md:py-24">
        <div className="pointer-events-none absolute left-1/2 top-0 h-72 w-[36rem] -translate-x-1/2 rounded-full bg-violet/15 blur-[100px]" />
        <div className="container-x relative">
          <SectionHeading
            eyebrow="02 / кейсы"
            title={<>Открой <span className="gold-text">свой шанс</span></>}
            description="Три кейса с открытыми вероятностями. Выпадение привилегий, риликов и дополнительных ключей — всё честно и видно заранее."
            action={
              <Link href="/cases" className="btn btn-ghost">
                Все кейсы <ArrowRight size={15} />
              </Link>
            }
          />
          <div className="grid gap-4 md:grid-cols-3">
            {cases.map((item, i) => (
              <Reveal key={item.group.id} delay={i * 0.06}>
                <div
                  className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-white/[0.07] bg-gradient-to-b from-[#191631] to-[#0b0a14] p-6"
                  style={{ boxShadow: `0 30px 70px -52px ${item.group.color}` }}
                >
                  <div
                    className="pointer-events-none absolute -right-16 -top-16 h-44 w-44 rounded-full opacity-20 blur-3xl transition group-hover:opacity-40"
                    style={{ background: item.group.color }}
                  />
                  <div className="relative flex justify-center py-2">
                    <Chest3D color={item.group.color} size={168} />
                  </div>
                  <h3 className="font-display text-xl font-extrabold uppercase tracking-tight text-white">{item.group.name}</h3>
                  <p className="mt-2 text-[0.78rem] leading-relaxed text-white/45">{item.group.description}</p>
                  <div className="mt-4 flex flex-wrap gap-1.5">
                    {caseItems
                      .filter((ci) => ci.caseId === item.group.id)
                      .slice(-3)
                      .reverse()
                      .map((ci) => (
                        <span
                          key={`${ci.caseId}-${ci.name}`}
                          className="rounded px-1.5 py-0.5 font-mono text-[0.56rem] uppercase tracking-wide"
                          style={{ background: `${ci.color}18`, color: ci.color }}
                        >
                          {ci.name}
                        </span>
                      ))}
                  </div>
                  <div className="mt-5 flex items-end justify-between border-t border-white/6 pt-4">
                    <div>
                      <p className="font-mono text-[0.55rem] uppercase tracking-widest text-white/30">от</p>
                      <p className="stat-num text-xl font-extrabold text-white">{formatPrice(item.minPrice)}</p>
                    </div>
                    <div className="flex gap-2">
                      <Link href={`/cases/${item.group.id}`} className="btn btn-ghost btn-sm">
                        Состав
                      </Link>
                      <Link href={`/cases#${item.group.id}`} className="btn btn-primary btn-sm">
                        Открыть
                      </Link>
                    </div>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ───────── relics ───────── */}
      <section className="container-x py-16 md:py-24">
        <SectionHeading
          eyebrow="03 / рилики"
          title={<>Игровая <span className="gold-text">валюта</span></>}
          description="Рилики нужны для аукциона, прокачки и ресурсов. Чем больше пакет — тем ниже цена за 1 000. Есть калькулятор для любого количества."
          action={
            <Link href="/relics" className="btn btn-ghost">
              Все пакеты <ArrowRight size={15} />
            </Link>
          }
        />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_0.9fr]">
          {topRelics.map((product, i) => (
            <Reveal key={product.id} delay={i * 0.05} className="h-full">
              <RelicCard product={product} featured={Number(product.meta.amount) === 100000} />
            </Reveal>
          ))}
          <Reveal delay={0.2} className="h-full">
            <div className="relative flex h-full flex-col justify-between overflow-hidden rounded-2xl border border-gold/25 bg-gradient-to-br from-[#221a10] to-[#0d0b16] p-5">
              <div className="pointer-events-none absolute -right-8 -top-8 opacity-30">
                <Coin size={110} />
              </div>
              <div>
                <p className="eyebrow !text-gold/80">калькулятор</p>
                <p className="mt-2 font-display text-lg font-extrabold uppercase leading-tight text-white">
                  Нужна точная сумма?
                </p>
                <p className="mt-2 text-[0.75rem] leading-relaxed text-white/45">
                  Выберите любое количество риликов от 1 до 100 000 — посчитаем цену по формуле сервера.
                </p>
              </div>
              <Link href="/relics#calculator" className="btn btn-gold mt-4 w-full !h-10 !text-[0.78rem]">
                Рассчитать
              </Link>
            </div>
          </Reveal>
        </div>
      </section>

      {/* ───────── why ───────── */}
      <section className="relative border-y border-neon/10 bg-abyss/40 py-16 md:py-24">
        <div className="container-x">
          <SectionHeading
            eyebrow="04 / почему мы"
            title={<>Сервер, на котором <span className="gradient-text">хочется остаться</span></>}
            description="Мы вложились в инфраструктуру и автоматизацию, чтобы вы тратили время на игру, а не на ожидание выдачи."
          />
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {WHY.map((item, i) => (
              <Reveal key={item.title} delay={i * 0.04}>
                <div className="group h-full rounded-2xl border border-white/6 bg-white/[0.02] p-5 transition hover:border-white/12 hover:bg-white/[0.035]">
                  <span
                    className="grid h-10 w-10 place-items-center rounded-lg"
                    style={{ background: `${item.color}18`, color: item.color, boxShadow: `inset 0 0 0 1px ${item.color}30` }}
                  >
                    <item.icon size={19} />
                  </span>
                  <h3 className="mt-4 font-display text-[0.95rem] font-bold text-white">{item.title}</h3>
                  <p className="mt-1.5 text-[0.78rem] leading-relaxed text-white/42">{item.text}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ───────── connect / online ───────── */}
      <section className="container-x py-16 md:py-24" id="connect">
        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Reveal>
            <div className="relative overflow-hidden rounded-2xl border border-neon/20 bg-gradient-to-br from-[#1b1734] via-[#121022] to-[#0b0a14] p-6 sm:p-8">
              <div className="pointer-events-none absolute -right-20 -top-20 h-56 w-56 rounded-full bg-neon/20 blur-3xl" />
              <p className="eyebrow">05 / подключение</p>
              <h2 className="mt-3 display text-2xl text-white sm:text-3xl">Заходи в империю</h2>
              <p className="mt-2 max-w-sm text-[0.82rem] leading-relaxed text-white/45">
                Java Edition {settings.server_version}. Лицензия и пиратка, вход без белого списка. IP можно скопировать
                одним нажатием.
              </p>

              <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center">
                <div className="flex flex-1 items-center gap-3 rounded-xl border border-white/8 bg-void/70 px-4 py-3">
                  <span className="grid h-9 w-9 place-items-center rounded-lg bg-neon/15 text-neon">
                    <Icon name="server" size={17} />
                  </span>
                  <div className="min-w-0">
                    <p className="font-mono text-[0.55rem] uppercase tracking-[0.18em] text-white/30">IP сервера</p>
                    <p className="truncate font-display text-sm font-bold text-white">{settings.server_ip}</p>
                  </div>
                </div>
                <CopyButton
                  value={settings.server_ip}
                  label="Копировать IP"
                  copyTitle="IP сервера скопирован!"
                  variant="primary"
                  className="btn-lg"
                />
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-2">
                <Link href="/rules" className="btn btn-ghost btn-sm">Правила сервера</Link>
                <Link href="/support" className="btn btn-ghost btn-sm">Как подключиться</Link>
                <span className="ml-auto inline-flex items-center gap-1.5 font-mono text-[0.62rem] text-white/30">
                  <Clock size={12} /> 24/7
                </span>
              </div>
            </div>
          </Reveal>

          <Reveal delay={0.08}>
            <OnlinePanel
              online={online}
              max={max}
              connected={bridge.connected}
              version={settings.server_version}
              discord={settings.discord_url}
              telegram={settings.telegram_url}
              lastSeen={bridge.lastSeen}
            />
          </Reveal>
        </div>
      </section>

      {/* ───────── recent + reviews ───────── */}
      <section className="border-y border-neon/10 bg-abyss/40 py-16 md:py-24">
        <div className="container-x grid gap-10 lg:grid-cols-[0.95fr_1.05fr]">
          <div>
            <SectionHeading eyebrow="06 / покупки" title={<>Последние <span className="gradient-text">покупки</span></>} />
            {recent.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-neon/20 bg-panel/30 p-6 text-center">
                <p className="font-display text-sm font-bold text-white">Пока нет реальных покупок</p>
                <p className="mt-1 text-xs leading-relaxed text-white/40">
                  Как только появятся оплаченные заказы, они отобразятся здесь. Мы не подставляем вымышленные данные.
                </p>
                <Link href="/privileges" className="btn btn-ghost btn-sm mt-4">Стать первым</Link>
              </div>
            ) : (
              <ul className="divide-y divide-white/6 overflow-hidden rounded-xl border border-white/6 bg-panel/30">
                {recent.map((order) => (
                  <li key={order.publicId} className="flex items-center gap-3 px-4 py-3">
                    <span className="grid h-8 w-8 place-items-center rounded-lg bg-neon/10 text-neon">
                      <Sparkles size={14} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[0.78rem] font-semibold text-white/85">
                        <span className="text-white">{order.nickname}</span> купил {order.itemName}
                      </p>
                      <p className="font-mono text-[0.6rem] text-white/30">{relativeTime(order.createdAt)} · {order.publicId}</p>
                    </div>
                    <span className="stat-num text-xs font-bold text-white/70">{formatPrice(order.total)}</span>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-3 flex items-center gap-1.5 font-mono text-[0.6rem] uppercase tracking-widest text-white/25">
              <ShieldCheck size={11} /> только подтверждённые оплаты
            </p>
          </div>

          <div>
            <SectionHeading
              eyebrow="07 / отзывы"
              title={<>Говорят <span className="gradient-text">игроки</span></>}
              action={<Link href="/reviews" className="btn btn-ghost btn-sm">Все отзывы</Link>}
            />
            <div className="space-y-3">
              {reviews.length === 0 ? (
                <p className="rounded-xl border border-white/6 bg-panel/30 p-4 text-xs text-white/40">
                  Отзывов пока нет. Оставить первый можно на странице «Отзывы».
                </p>
              ) : (
                reviews.map((review) => (
                  <figure key={review.id} className="rounded-xl border border-white/6 bg-panel/30 p-4">
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="grid h-7 w-7 place-items-center rounded-md bg-gradient-to-br from-violet to-neon font-display text-[0.6rem] font-extrabold text-white">
                          {review.nickname.slice(0, 2).toUpperCase()}
                        </span>
                        <figcaption className="text-[0.78rem] font-bold text-white/85">{review.nickname}</figcaption>
                      </div>
                      <Stars value={review.rating} />
                    </div>
                    <blockquote className="mt-2 text-[0.78rem] leading-relaxed text-white/50">«{review.text}»</blockquote>
                  </figure>
                ))
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ───────── faq ───────── */}
      <section className="container-x py-16 md:py-24">
        <div className="grid gap-8 lg:grid-cols-[0.85fr_1.15fr]">
          <div>
            <SectionHeading eyebrow="08 / faq" title={<>Частые <span className="gradient-text">вопросы</span></>} />
            <div className="rounded-2xl border border-white/6 bg-panel/30 p-5">
              <Quote size={20} className="text-neon/60" />
              <p className="mt-2 text-[0.8rem] leading-relaxed text-white/45">
                Не нашли ответ? Напишите в Discord — поддержка отвечает в среднем за 15 минут и видит статус вашего
                заказа.
              </p>
              <div className="mt-4 flex gap-2">
                <a href={settings.discord_url} target="_blank" rel="noreferrer noopener" className="btn btn-primary btn-sm">
                  <DiscordGlyph size={14} /> Discord
                </a>
                <a href={settings.telegram_url} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-sm">
                  <TelegramGlyph size={14} /> Telegram
                </a>
              </div>
            </div>
          </div>
          <Accordion items={FAQ} />
        </div>
      </section>

      {/* ───────── final cta ───────── */}
      <section className="container-x pb-20">
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl border border-gold/25 bg-gradient-to-br from-[#2a1f0e] via-[#1a1330] to-[#0b0a14] px-6 py-12 text-center sm:px-10">
            <div className="pointer-events-none absolute inset-0 grid-bg opacity-30" />
            <div className="pointer-events-none absolute left-1/2 top-0 h-52 w-[30rem] -translate-x-1/2 rounded-full bg-gold/15 blur-[80px]" />
            <div className="relative">
              <p className="eyebrow justify-center !text-gold/70">твой ход</p>
              <h2 className="mx-auto mt-3 max-w-2xl display text-3xl leading-[0.95] text-white sm:text-5xl">
                Стань <span className="gold-text">легендой</span> KingGrief
              </h2>
              <p className="mx-auto mt-3 max-w-md text-sm leading-relaxed text-white/45">
                14 привилегий, 3 кейса и рилики — всё, чтобы начать доминировать с первого дня.
              </p>
              <div className="mt-7 flex flex-col items-center justify-center gap-3 sm:flex-row">
                <Link href="/privileges" className="btn btn-primary btn-lg">
                  Выбрать привилегию <ArrowRight size={17} />
                </Link>
                <AddToCartButton
                  product={ranks.find((r) => r.id === 'rank-downer') ?? ranks[ranks.length - 1]}
                  label="Забрать D.OWNER"
                  variant="gold"
                  className="btn-lg"
                />
              </div>
              <p className="mt-4 font-mono text-[0.58rem] uppercase tracking-[0.2em] text-white/25">
                {formatNumber(ranks.length)} привилегий · {formatNumber(relics.length)} пакетов риликов · выдача после оплаты
              </p>
            </div>
          </div>
        </Reveal>
      </section>
    </>
  );
}
