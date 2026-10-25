import Link from 'next/link';
import { ArrowUpRight, Mail } from 'lucide-react';
import { DiscordGlyph, LogoMark, TelegramGlyph, Wordmark } from './icons';
import { CopyButton } from './ui';

const columns = [
  {
    title: 'Магазин',
    links: [
      { href: '/privileges', label: 'Привилегии' },
      { href: '/cases', label: 'Кейсы' },
      { href: '/relics', label: 'Рилики' },
      { href: '/other', label: 'Разбан и размут' },
      { href: '/cart', label: 'Корзина' },
    ],
  },
  {
    title: 'Сервер',
    links: [
      { href: '/rules', label: 'Правила' },
      { href: '/support', label: 'Поддержка' },
      { href: '/reviews', label: 'Отзывы' },
      { href: '/profile', label: 'Мои заказы' },
      { href: '/admin', label: 'Админ-панель' },
    ],
  },
];

export function Footer({
  ip,
  version,
  discord,
  telegram,
  supportEmail,
}: {
  ip: string;
  version: string;
  discord: string;
  telegram: string;
  supportEmail: string;
}) {
  return (
    <footer className="relative mt-24 overflow-hidden border-t border-neon/12 bg-abyss/70">
      <div
        className="pointer-events-none absolute -top-32 left-1/2 h-64 w-[42rem] -translate-x-1/2 rounded-full opacity-40 blur-3xl"
        style={{ background: 'radial-gradient(circle, rgba(124,58,237,0.5), transparent 70%)' }}
      />
      <div className="container-x relative py-12">
        <div className="grid gap-10 md:grid-cols-[1.4fr_1fr_1fr_1.2fr]">
          <div>
            <Link href="/" className="inline-flex items-center gap-2.5">
              <LogoMark size={40} />
              <Wordmark />
            </Link>
            <p className="mt-4 max-w-xs text-[0.82rem] leading-relaxed text-white/45">
              Игровая платформа Minecraft-сервера KingGrief. Привилегии, кейсы, рилики и честная автоматическая выдача
              после подтверждения оплаты.
            </p>
            <div className="mt-5 flex items-center gap-2">
              <a href={discord} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon" aria-label="Discord">
                <DiscordGlyph size={17} />
              </a>
              <a href={telegram} target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon" aria-label="Telegram">
                <TelegramGlyph size={17} />
              </a>
              <a href={`mailto:${supportEmail}`} className="btn btn-ghost btn-icon" aria-label="Написать на email">
                <Mail size={17} />
              </a>
            </div>
          </div>

          {columns.map((col) => (
            <div key={col.title}>
              <p className="mb-3 font-mono text-[0.62rem] uppercase tracking-[0.22em] text-white/30">{col.title}</p>
              <ul className="space-y-2">
                {col.links.map((link) => (
                  <li key={link.href}>
                    <Link href={link.href} className="group inline-flex items-center gap-1 text-[0.82rem] text-white/55 transition hover:text-white">
                      {link.label}
                      <ArrowUpRight size={11} className="opacity-0 transition group-hover:opacity-60" />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          <div>
            <p className="mb-3 font-mono text-[0.62rem] uppercase tracking-[0.22em] text-white/30">Подключиться</p>
            <div className="rounded-xl border border-neon/15 bg-void/60 p-3">
              <p className="font-mono text-[0.6rem] uppercase tracking-widest text-white/35">IP сервера</p>
              <p className="mt-1 truncate font-display text-sm font-bold text-white">{ip}</p>
              <CopyButton
                value={ip}
                label="Скопировать IP"
                copyTitle="IP сервера скопирован!"
                variant="plain"
                className="mt-2"
              />
              <p className="mt-2 border-t border-white/6 pt-2 font-mono text-[0.62rem] text-white/35">Java {version}</p>
            </div>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {['ЮKassa', 'Robokassa', 'СБП'].map((p) => (
                <span key={p} className="rounded border border-white/8 bg-white/[0.03] px-1.5 py-0.5 font-mono text-[0.56rem] text-white/35">
                  {p}
                </span>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-3 border-t border-white/6 pt-5 text-[0.68rem] text-white/30 md:flex-row md:items-center md:justify-between">
          <p>© {new Date().getFullYear()} KINGGRIEF. Все права защищены.</p>
          <p className="max-w-lg leading-relaxed">
            Не является официальным продуктом Minecraft. Не одобрено Mojang Studios и не связано с Mojang AB.
            Оплата — добровольная поддержка проекта.
          </p>
        </div>
      </div>
    </footer>
  );
}
