import type { Metadata } from 'next';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import { ArrowRight, CalendarDays, Package, ShoppingBag, Sparkles } from 'lucide-react';
import { currentUser } from '@/lib/auth';
import { listUserOrders } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { AccountForms, LogoutButton } from '@/components/account-panel';

export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: 'Личный кабинет',
  robots: { index: false, follow: false },
};

export default async function AccountPage() {
  await cloudSyncLocalDb({ includeOrders: true });
  const user = await currentUser();
  if (!user) redirect('/login?next=/account');

  const orders = listUserOrders(user.id);
  const paid = orders.filter((o) => o.status !== 'pending_payment' && o.status !== 'cancelled' && o.status !== 'failed');
  const spent = paid.reduce((sum, o) => sum + o.total, 0);

  return (
    <section className="container-x py-8 md:py-12">
      <nav aria-label="Хлебные крошки" className="mb-5 flex items-center gap-2 font-mono text-[0.62rem] uppercase tracking-widest text-white/30">
        <Link href="/" className="hover:text-white">Главная</Link><span>/</span>
        <span className="text-neon">Личный кабинет</span>
      </nav>

      <div className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br from-violet to-neon font-display text-lg font-extrabold text-white">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <div>
            <h1 className="display text-2xl text-white sm:text-3xl">{user.username}</h1>
            <p className="text-[0.76rem] text-white/40">{user.email}</p>
          </div>
        </div>
        <LogoutButton />
      </div>

      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        {[
          { label: 'Заказов', value: String(orders.length), icon: ShoppingBag },
          { label: 'Оплачено', value: formatPrice(spent), icon: Package },
          { label: 'С нами с', value: formatDate(user.createdAt), icon: CalendarDays },
        ].map((s) => (
          <div key={s.label} className="rounded-xl border border-white/7 bg-gradient-to-b from-[#141226] to-[#0b0a14] p-4">
            <div className="flex items-center justify-between">
              <span className="font-mono text-[0.56rem] uppercase tracking-[0.14em] text-white/35">{s.label}</span>
              <s.icon size={14} className="text-neon" />
            </div>
            <p className="stat-num mt-2 text-lg font-extrabold text-white">{s.value}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
        <div>
          <h2 className="mb-3 font-display text-sm font-bold uppercase tracking-widest text-white/70">Мои заказы</h2>
          {orders.length === 0 ? (
            <div className="rounded-2xl border border-white/8 bg-panel/40 p-6 text-center">
              <Sparkles size={22} className="mx-auto text-neon" />
              <p className="mt-2 font-display text-sm font-bold text-white">Покупок пока нет</p>
              <p className="mt-1 text-[0.76rem] text-white/40">Выберите привилегию, кейс или рилики — заказ появится здесь.</p>
              <Link href="/privileges" className="btn btn-primary mt-4">
                В магазин <ArrowRight size={14} />
              </Link>
            </div>
          ) : (
            <ul className="space-y-2">
              {orders.map((order) => (
                <li key={order.id}>
                  <Link
                    href={`/checkout/${order.publicId}`}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/7 bg-panel/40 px-3.5 py-3 transition hover:border-neon/40 hover:bg-neon/5"
                  >
                    <div className="min-w-0">
                      <p className="font-mono text-[0.68rem] font-bold text-white/80">{order.publicId}</p>
                      <p className="truncate text-[0.72rem] text-white/40">
                        {order.items.map((i) => `${i.name}${i.quantity > 1 ? ` ×${i.quantity}` : ''}`).join(', ')}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span
                        className="rounded-full px-2 py-0.5 font-mono text-[0.56rem] font-bold uppercase tracking-widest"
                        style={{
                          background: `${statusColors[order.status]}18`,
                          color: statusColors[order.status],
                        }}
                      >
                        {statusLabels[order.status]}
                      </span>
                      <span className="stat-num text-sm font-bold text-white">{formatPrice(order.total)}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="space-y-4">
          <h2 className="font-display text-sm font-bold uppercase tracking-widest text-white/70">Профиль</h2>
          <AccountForms email={user.email} />
          <Link href="/profile" className="btn btn-ghost w-full">
            Найти заказы по никнейму
          </Link>
        </div>
      </div>
    </section>
  );
}
