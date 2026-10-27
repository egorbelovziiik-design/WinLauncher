'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, ShoppingCart, User, X } from 'lucide-react';
import { LogoMark, Wordmark, DiscordGlyph, TelegramGlyph } from './icons';
import { CartCountBubble, useCart } from './providers';
import { cn } from '@/lib/utils';

const NAV = [
  { href: '/privileges', label: 'Привилегии' },
  { href: '/cases', label: 'Кейсы' },
  { href: '/relics', label: 'Рилики' },
  { href: '/other', label: 'Другое' },
  { href: '/reviews', label: 'Отзывы' },
  { href: '/rules', label: 'Правила' },
  { href: '/support', label: 'Поддержка' },
];

export function OnlinePill({ initialOnline, initialMax }: { initialOnline: number; initialMax: number }) {
  const [state, setState] = useState({ online: initialOnline, max: initialMax, connected: false, source: 'fallback' });

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const res = await fetch('/api/status', { cache: 'no-store' });
        if (!res.ok) return;
        const data = (await res.json()) as { online: number; max: number; connected: boolean; source: string };
        if (alive) setState({ online: data.online, max: data.max, connected: data.connected, source: data.source });
      } catch {
        /* keep last known */
      }
    };
    load();
    const timer = setInterval(load, 30_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  const live = state.connected;
  return (
    <Link
      href="/support"
      className="group inline-flex items-center gap-2 rounded-full border border-emerald/25 bg-emerald/8 px-3 py-1.5 transition hover:border-emerald/50 hover:bg-emerald/15"
      title={live ? 'Данные от плагина сервера' : 'Мост Minecraft ещё не подключён — показано базовое значение из настроек'}
    >
      <span className="relative flex h-2 w-2">
        <span className={cn('absolute inline-flex h-full w-full rounded-full opacity-70', live ? 'animate-ping bg-emerald' : 'bg-amber')} />
        <span className={cn('relative inline-flex h-2 w-2 rounded-full', live ? 'bg-emerald' : 'bg-amber')} />
      </span>
      <span className="font-mono text-[0.68rem] font-bold tracking-wide text-white/85">
        {state.max > 0 ? (
          <>
            {state.online.toLocaleString('ru-RU')}
            <span className="text-white/35"> / {state.max.toLocaleString('ru-RU')}</span>
          </>
        ) : (
          <span className="text-white/45">— / —</span>
        )}
      </span>
      <span className="hidden text-[0.6rem] font-bold uppercase tracking-widest text-emerald/80 lg:inline">
        {live ? 'online' : 'скоро'}
      </span>
    </Link>
  );
}

export function Header({
  online,
  max,
  user,
}: {
  online: number;
  max: number;
  user?: { username: string } | null;
}) {
  const pathname = usePathname();
  const { count, setDrawerOpen } = useCart();
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 14);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    document.body.style.overflow = menuOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [menuOpen]);

  return (
    <>
      <header
        className={cn(
          'fixed inset-x-0 top-0 z-50 transition-all duration-300',
          scrolled ? 'border-b border-neon/12 bg-void/85 backdrop-blur-xl shadow-[0_18px_44px_-30px_rgba(0,0,0,1)]' : 'bg-transparent',
        )}
      >
        <div className="container-x flex h-[68px] items-center justify-between gap-3">
          <Link href="/" className="group flex items-center gap-2.5" aria-label="KINGGRIEF — на главную">
            <span className="transition group-hover:scale-105">
              <LogoMark size={36} />
            </span>
            <span className="flex flex-col">
              <Wordmark />
              <span className="mt-0.5 hidden font-mono text-[0.52rem] uppercase tracking-[0.3em] text-white/30 sm:block">
                империя грифа
              </span>
            </span>
          </Link>

          <nav className="hidden items-center gap-6 lg:flex" aria-label="Основная навигация">
            {NAV.map((item) => {
              const active = pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href));
              return (
                <Link key={item.href} href={item.href} className="link-line text-[0.8rem] font-semibold" data-active={active}>
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            <div className="hidden sm:block">
              <OnlinePill initialOnline={online} initialMax={max} />
            </div>
            {user ? (
              <Link
                href="/account"
                className="btn btn-ghost hidden items-center gap-2 sm:inline-flex"
                aria-label={`Личный кабинет ${user.username}`}
                title="Личный кабинет"
              >
                <span className="grid h-5 w-5 place-items-center rounded bg-gradient-to-br from-violet to-neon font-display text-[0.55rem] font-extrabold text-white">
                  {user.username.slice(0, 2).toUpperCase()}
                </span>
                <span className="max-w-[7rem] truncate text-[0.72rem] font-bold text-white/80">{user.username}</span>
              </Link>
            ) : (
              <Link
                href="/login"
                className="btn btn-ghost hidden items-center gap-1.5 sm:inline-flex"
                aria-label="Войти в аккаунт"
                title="Войти в аккаунт"
              >
                <User size={15} />
                <span className="text-[0.72rem] font-bold">Войти</span>
              </Link>
            )}
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              className="btn btn-ghost btn-icon relative"
              aria-label={`Корзина: ${count} товаров`}
            >
              <ShoppingCart size={17} />
              <CartCountBubble />
            </button>
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              className="btn btn-ghost btn-icon lg:hidden"
              aria-label={menuOpen ? 'Закрыть меню' : 'Открыть меню'}
              aria-expanded={menuOpen}
            >
              {menuOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
          </div>
        </div>
      </header>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-40 bg-void/95 backdrop-blur-xl lg:hidden"
          >
            <div className="container-x flex h-full flex-col pb-8 pt-24">
              <div className="mb-5 flex items-center justify-between">
                <span className="eyebrow">Навигация</span>
                <OnlinePill initialOnline={online} initialMax={max} />
              </div>
              <nav className="flex flex-col divide-y divide-white/5 overflow-y-auto" aria-label="Мобильная навигация">
                {[
                  { href: '/', label: 'Главная' },
                  ...NAV,
                  { href: '/profile', label: 'Поиск заказов' },
                  user ? { href: '/account', label: 'Личный кабинет' } : { href: '/login', label: 'Войти / Регистрация' },
                ].map((item, i) => (
                  <motion.div
                    key={item.href}
                    initial={{ opacity: 0, x: -12 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.03 * i }}
                  >
                    <Link
                      href={item.href}
                      className="flex items-center justify-between py-3.5 font-display text-xl font-bold uppercase tracking-tight text-white/85 transition hover:text-neon"
                    >
                      {item.label}
                      <span className="font-mono text-[0.6rem] text-white/25">0{i + 1}</span>
                    </Link>
                  </motion.div>
                ))}
              </nav>
              <div className="mt-auto flex items-center gap-2 pt-6">
                <Link href="/privileges" className="btn btn-primary flex-1" onClick={() => setMenuOpen(false)}>
                  Перейти в магазин
                </Link>
                <a href="https://discord.gg/7Cugj4X3eU" target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon" aria-label="Discord">
                  <DiscordGlyph size={17} />
                </a>
                <a href="https://t.me/kinggrief" target="_blank" rel="noreferrer noopener" className="btn btn-ghost btn-icon" aria-label="Telegram">
                  <TelegramGlyph size={17} />
                </a>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
