import type { Metadata } from 'next';
import Link from 'next/link';
import { AlertTriangle, Ban, BookOpen, Gavel, ScrollText, ShieldCheck, Skull } from 'lucide-react';
import { getSettings } from '@/lib/repo';
import { Reveal } from '@/components/ui';
import { DiscordGlyph, TelegramGlyph } from '@/components/icons';

export const metadata: Metadata = {
  title: 'Правила сервера',
  description: 'Правила Minecraft-сервера KingGrief: что запрещено, сроки наказаний, условия разбана и апелляций.',
  alternates: { canonical: '/rules' },
};

const SECTIONS = [
  {
    id: 'general',
    title: 'Общие положения',
    icon: ScrollText,
    items: [
      'Заходя на сервер, вы автоматически соглашаетесь с этими правилами и с тем, что администрация может их обновлять.',
      'Незнание правил не освобождает от наказания. Актуальная версия всегда опубликована на этой странице.',
      'Администрация вправе выдать наказание за действия, прямо не описанные в правилах, если они портят игру другим.',
      'Решение администрации можно оспорить только через апелляцию в поддержке.',
    ],
  },
  {
    id: 'forbidden',
    title: 'Запрещено',
    icon: Ban,
    items: [
      'Читы, макросы, автокликеры, X-Ray и любые модификации, дающие преимущество без разрешения администрации.',
      'Дюп предметов, использование багов экономики и сокрытие найденных уязвимостей.',
      'Гриферство чужих построек вне зоны PvP и вне разрешённых механик грифа.',
      'Оскорбления, травля, угрозы, доксинг, разжигание ненависти и любой NSFW-контент.',
      'Реклама сторонних серверов, продажа аккаунтов и предметов за реальные деньги вне магазина.',
      'Обход блокировки через другие аккаунты или IP, а также попытки обмануть систему наказаний.',
      'Мошенничество при сделках между игроками, включая обман через магазин и аукцион.',
    ],
  },
  {
    id: 'pvp',
    title: 'PvP и гриф',
    icon: Skull,
    items: [
      'PvP разрешён везде, где он не отключён плагином или зоной мира.',
      'Гриф разрешён в открытом мире, но запрещён в спавн-зоне, на ивент-аренах и в защищённых регионах.',
      'Убийство игрока без экипировки допустимо, но систематическая травля (спавн-килл, преследование) — нет.',
      'Строить ловушки, ведущие к потере вещей за пределами PvP-зон, запрещено.',
    ],
  },
  {
    id: 'chat',
    title: 'Чат и голос',
    icon: BookOpen,
    items: [
      'Основной язык общения — русский. Другие языки допустимы в личных сообщениях.',
      'Флуд, спам, капс и злоупотребление цветами чата наказываются мутом.',
      'Запрещено выдавать себя за администрацию и использовать ники, похожие на служебные.',
      'Публичное обсуждение наказаний и действий администрации — только через поддержку.',
    ],
  },
];

const PUNISHMENTS = [
  { violation: 'Флуд / спам в чате', first: 'Мут 1 час', repeat: 'Мут 24 часа', buyout: 'Да' },
  { violation: 'Оскорбления игроков', first: 'Мут 3 часа', repeat: 'Мут 7 дней', buyout: 'Да' },
  { violation: 'Гриферство построек', first: 'Бан 1 день', repeat: 'Бан 7 дней', buyout: 'Да' },
  { violation: 'Читы / X-Ray', first: 'Бан 30 дней', repeat: 'Перманентный бан', buyout: 'Нет' },
  { violation: 'Дюп / баг экономики', first: 'Бан 30 дней', repeat: 'Перманентный бан', buyout: 'Нет' },
  { violation: 'Реклама / NSFW', first: 'Перманентный бан', repeat: '—', buyout: 'Нет' },
  { violation: 'Обход блокировки', first: 'Перманентный бан по IP', repeat: '—', buyout: 'Нет' },
];

export default function RulesPage() {
  const settings = getSettings();
  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span><span className="text-neon">Правила</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-[0.32fr_0.68fr]">
        <aside className="lg:sticky lg:top-24 lg:h-fit">
          <span className="chip mb-3"><Gavel size={12} /> версия 1.1 · сезон I</span>
          <h1 className="display text-3xl text-white sm:text-4xl">Правила <span className="text-neon">сервера</span></h1>
          <p className="mt-2 text-[0.82rem] leading-relaxed text-white/40">
            Документ действует на всех режимах KingGrief. Наказания выносятся по регламенту, а не по настроению.
          </p>
          <nav className="mt-5 space-y-1.5" aria-label="Разделы правил">
            {SECTIONS.map((section) => (
              <a key={section.id} href={`#${section.id}`} className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-[0.76rem] text-white/45 transition hover:bg-white/5 hover:text-white">
                <section.icon size={14} className="text-neon" /> {section.title}
              </a>
            ))}
            <a href="#punishments" className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-[0.76rem] text-white/45 transition hover:bg-white/5 hover:text-white">
              <AlertTriangle size={14} className="text-amber" /> Наказания
            </a>
            <a href="#buyout" className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-[0.76rem] text-white/45 transition hover:bg-white/5 hover:text-white">
              <ShieldCheck size={14} className="text-emerald" /> Разбан и апелляции
            </a>
          </nav>
          <div className="mt-5 rounded-xl border border-neon/15 bg-neon/[0.04] p-3">
            <p className="text-[0.72rem] font-bold text-white">Остались вопросы?</p>
            <p className="mt-1 text-[0.68rem] leading-relaxed text-white/40">Поддержка отвечает в Discord и Telegram.</p>
            <div className="mt-2 flex gap-1.5">
              <a href={settings.discord_url} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon !h-8 !w-8" aria-label="Discord"><DiscordGlyph size={14} /></a>
              <a href={settings.telegram_url} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon !h-8 !w-8" aria-label="Telegram"><TelegramGlyph size={14} /></a>
            </div>
          </div>
        </aside>

        <div className="space-y-6">
          {SECTIONS.map((section, i) => (
            <Reveal key={section.id} delay={i * 0.04}>
              <article id={section.id} className="scroll-mt-24 rounded-2xl border border-white/7 bg-panel/40 p-5">
                <div className="flex items-center gap-2.5">
                  <span className="grid h-8 w-8 place-items-center rounded-lg bg-neon/12 text-neon"><section.icon size={16} /></span>
                  <h2 className="font-display text-base font-bold uppercase tracking-tight text-white">{section.title}</h2>
                </div>
                <ul className="mt-4 space-y-2">
                  {section.items.map((item) => (
                    <li key={item} className="flex gap-2.5 text-[0.82rem] leading-relaxed text-white/50">
                      <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-neon" />
                      {item}
                    </li>
                  ))}
                </ul>
              </article>
            </Reveal>
          ))}

          <Reveal>
            <article id="punishments" className="scroll-mt-24 overflow-hidden rounded-2xl border border-white/7 bg-panel/40">
              <div className="flex items-center gap-2.5 p-5 pb-3">
                <span className="grid h-8 w-8 place-items-center rounded-lg bg-amber/12 text-amber"><AlertTriangle size={16} /></span>
                <h2 className="font-display text-base font-bold uppercase tracking-tight text-white">Сроки наказаний</h2>
              </div>
              <div className="overflow-x-auto">
                <table className="admin-table min-w-[560px]">
                  <thead>
                    <tr>
                      <th>Нарушение</th><th>Первое</th><th>Повторное</th><th>Снятие за донат</th>
                    </tr>
                  </thead>
                  <tbody>
                    {PUNISHMENTS.map((row) => (
                      <tr key={row.violation}>
                        <td className="text-white/70">{row.violation}</td>
                        <td className="text-white/50">{row.first}</td>
                        <td className="text-white/50">{row.repeat}</td>
                        <td>
                          <span className={`font-mono text-[0.62rem] font-bold uppercase ${row.buyout === 'Да' ? 'text-emerald' : 'text-danger'}`}>
                            {row.buyout}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="px-5 pb-4 pt-2 text-[0.66rem] leading-relaxed text-white/25">
                Администрация может изменить срок при отягчающих обстоятельствах. Приобретение разбана не гарантирует
                снятие наказания: заказ проверяется по регламенту.
              </p>
            </article>
          </Reveal>

          <Reveal>
            <article id="buyout" className="scroll-mt-24 rounded-2xl border border-emerald/20 bg-emerald/[0.04] p-5">
              <div className="flex items-center gap-2.5">
                <span className="grid h-8 w-8 place-items-center rounded-lg bg-emerald/12 text-emerald"><ShieldCheck size={16} /></span>
                <h2 className="font-display text-base font-bold uppercase tracking-tight text-white">Разбан и апелляции</h2>
              </div>
              <ul className="mt-4 space-y-2 text-[0.82rem] leading-relaxed text-white/50">
                <li>• Снять можно только наказания, отмеченные как доступные к выкупу. Читы, дюп и обход бана — нет.</li>
                <li>• Разбан по IP снимает блокировку адреса, с которого играл указанный никнейм.</li>
                <li>• Ошибочное наказание оспаривается бесплатно: напишите в поддержку до покупки разбана.</li>
                <li>• Если услугу невозможно оказать, деньги возвращаются на исходный способ оплаты.</li>
              </ul>
              <div className="mt-4 flex flex-wrap gap-2">
                <Link href="/other" className="btn btn-primary btn-sm">Разбан и размут</Link>
                <Link href="/support" className="btn btn-ghost btn-sm">Подать апелляцию</Link>
              </div>
            </article>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
