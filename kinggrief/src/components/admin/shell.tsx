'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  BarChart3, Crown, ExternalLink, Gift, LayoutDashboard, LogOut, Menu, Package, Palette, Settings,
  ShieldCheck, ShoppingBag, Ticket, Users, X, Zap,
} from 'lucide-react';
import { LogoMark } from '../icons';
import { useToast } from '../providers';

const NAV = [
  { href: '/admin', label: 'Главная', icon: LayoutDashboard },
  { href: '/admin/orders', label: 'Заказы', icon: ShoppingBag },
  { href: '/admin/products?kind=rank', label: 'Привилегии', icon: Crown, match: '/admin/products' },
  { href: '/admin/cases', label: 'Кейсы', icon: Gift },
  { href: '/admin/products?kind=relic', label: 'Рилики', icon: Zap, match: '/admin/products' },
  { href: '/admin/products?kind=other', label: 'Другие товары', icon: Package, match: '/admin/products' },
  { href: '/admin/customers', label: 'Покупатели', icon: Users },
  { href: '/admin/promos', label: 'Промокоды', icon: Ticket },
  { href: '/admin/analytics', label: 'Аналитика', icon: BarChart3 },
  { href: '/admin/reviews', label: 'Отзывы', icon: ShieldCheck },
  { href: '/admin/design', label: 'Дизайн', icon: Palette },
  { href: '/admin/settings', label: 'Настройки', icon: Settings },
  { href: '/admin/security', label: 'Безопасность', icon: ShieldCheck },
];

export function AdminShell({
  username,
  role,
  bridgeConnected,
  testMode,
  provider,
  children,
}: {
  username: string;
  role: string;
  bridgeConnected: boolean;
  testMode: boolean;
  provider: string;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const { push } = useToast();
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [pathname]);

  const logout = async () => {
    await fetch('/api/admin/logout', { method: 'POST' });
    push({ tone: 'info', title: 'Вы вышли из панели' });
    router.push('/admin/login');
    router.refresh();
  };

  const nav = (
    <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-2 py-3" aria-label="Разделы админ-панели">
      {NAV.map((item) => {
        const active = item.href === '/admin' ? pathname === '/admin' : pathname.startsWith(item.match ?? item.href.split('?')[0]);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`group flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[0.78rem] font-semibold transition ${
              active ? 'bg-neon/15 text-white shadow-[inset_0_0_0_1px_rgba(168,85,247,0.25)]' : 'text-white/45 hover:bg-white/5 hover:text-white/85'
            }`}
          >
            <item.icon size={15} className={active ? 'text-neon' : 'text-white/30 group-hover:text-white/60'} />
            {item.label}
            {active && <span className="ml-auto h-1 w-1 rounded-full bg-neon" />}
          </Link>
        );
      })}
      {role === 'owner' && (
        <Link
          href="/lp-editor"
          className="mt-1 flex items-center gap-2.5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-2 text-[0.78rem] font-bold text-emerald-300 transition hover:bg-emerald-500/20"
        >
          <Zap size={15} className="text-emerald-400" />
          ⚡ LP Editor (OWNER)
        </Link>
      )}
    </nav>
  );

  return (
    <div className="min-h-dvh bg-[#070610]">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-60 flex-col border-r border-neon/12 bg-[#0a0914] lg:flex">
        <Link href="/admin" className="flex items-center gap-2.5 border-b border-white/6 px-4 py-4">
          <LogoMark size={34} />
          <span>
            <span className="block font-display text-[0.8rem] font-extrabold uppercase tracking-tight text-white">KINGGRIEF</span>
            <span className="block font-mono text-[0.52rem] uppercase tracking-[0.2em] text-neon/70">admin panel</span>
          </span>
        </Link>
        {nav}
        <div className="border-t border-white/6 p-3">
          <div className="rounded-lg border border-white/6 bg-white/[0.02] p-2.5">
            <p className="font-mono text-[0.52rem] uppercase tracking-widest text-white/25">администратор</p>
            <p className="mt-0.5 truncate text-[0.78rem] font-bold text-white">{username}</p>
            <p className="font-mono text-[0.55rem] uppercase tracking-wide text-neon/70">{role}</p>
          </div>
          <div className="mt-2 flex gap-1.5">
            <Link href="/" target="_blank" className="btn btn-ghost btn-sm flex-1 !h-8 !text-[0.68rem]">
              <ExternalLink size={12} /> Сайт
            </Link>
            <button onClick={logout} className="btn btn-ghost btn-sm !h-8 !px-2" aria-label="Выйти">
              <LogOut size={13} />
            </button>
          </div>
        </div>
      </aside>

      <div className="sticky top-0 z-30 flex items-center justify-between gap-2 border-b border-white/6 bg-[#0a0914]/95 px-4 py-3 backdrop-blur lg:hidden">
        <button onClick={() => setOpen(true)} className="btn btn-ghost btn-icon !h-9 !w-9" aria-label="Открыть меню">
          <Menu size={17} />
        </button>
        <span className="font-display text-[0.78rem] font-extrabold uppercase tracking-tight text-white">KINGGRIEF admin</span>
        <button onClick={logout} className="btn btn-ghost btn-icon !h-9 !w-9" aria-label="Выйти">
          <LogOut size={15} />
        </button>
      </div>

      <AnimatePresence>
        {open && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <motion.button
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="absolute inset-0 bg-black/70" onClick={() => setOpen(false)} aria-label="Закрыть меню"
            />
            <motion.aside
              initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }}
              transition={{ type: 'spring', stiffness: 320, damping: 34 }}
              className="absolute inset-y-0 left-0 flex w-64 flex-col border-r border-neon/15 bg-[#0a0914]"
            >
              <div className="flex items-center justify-between border-b border-white/6 px-4 py-4">
                <Link href="/admin" className="flex items-center gap-2">
                  <LogoMark size={32} />
                  <span className="font-display text-[0.78rem] font-extrabold text-white">ADMIN</span>
                </Link>
                <button onClick={() => setOpen(false)} className="text-white/40" aria-label="Закрыть"><X size={18} /></button>
              </div>
              {nav}
            </motion.aside>
          </div>
        )}
      </AnimatePresence>

      <div className="lg:pl-60">
        <header className="sticky top-0 z-20 hidden items-center justify-between gap-3 border-b border-white/6 bg-[#070610]/85 px-6 py-3 backdrop-blur lg:flex">
          <div className="flex items-center gap-2 font-mono text-[0.6rem] uppercase tracking-widest text-white/30">
            <Crown size={12} className="text-neon" /> панель управления
            <span className="text-white/15">/</span>
            <span className="text-white/60">{NAV.find((n) => n.href === pathname)?.label ?? pathname.replace('/admin/', '')}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-1 font-mono text-[0.55rem] font-bold uppercase tracking-widest ${bridgeConnected ? 'bg-emerald/12 text-emerald' : 'bg-amber/12 text-amber'}`}>
              <span className={`h-1.5 w-1.5 rounded-full ${bridgeConnected ? 'bg-emerald' : 'bg-amber'}`} />
              {bridgeConnected ? 'мост online' : 'мост offline'}
            </span>
            <span className="rounded-full bg-white/5 px-2 py-1 font-mono text-[0.55rem] uppercase tracking-widest text-white/40">
              {provider}{testMode ? ' · test' : ''}
            </span>
          </div>
        </header>
        <main className="min-h-[calc(100dvh-56px)] px-4 py-5 sm:px-6 sm:py-7">{children}</main>
      </div>
    </div>
  );
}
