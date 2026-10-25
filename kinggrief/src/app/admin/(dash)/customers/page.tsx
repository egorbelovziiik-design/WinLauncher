import Link from 'next/link';
import { Users } from 'lucide-react';
import { countUsers, listOrders, listUsers, topCustomers } from '@/lib/repo';
import { formatDate, formatPrice } from '@/lib/utils';
import { AdminCard, Pill } from '@/components/admin/kit';

export const dynamic = 'force-dynamic';

export default async function AdminCustomersPage({ searchParams }: { searchParams: Promise<{ demo?: string }> }) {
  const { demo } = await searchParams;
  const includeDemo = demo === '1';
  const customers = topCustomers(60, includeDemo);
  const orders = listOrders({ limit: 500 });
  const users = listUsers(100);
  const accounts = countUsers();
  const totalSpent = customers.reduce((sum, c) => sum + c.spent, 0);
  const avg = customers.length ? Math.round(totalSpent / customers.length) : 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">клиенты</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Покупатели</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">Агрегация по никнеймам из оплаченных заказов.</p>
        </div>
        <Link href={includeDemo ? '/admin/customers' : '/admin/customers?demo=1'} className={`btn btn-sm ${includeDemo ? 'btn-gold' : 'btn-ghost'}`}>
          {includeDemo ? 'Скрыть демо' : 'Учитывать демо'}
        </Link>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        {[
          { label: 'Покупателей', value: `${customers.length}` },
          { label: 'Средний чек', value: formatPrice(avg) },
          { label: 'Заказов в базе', value: `${orders.length}` },
          { label: 'Аккаунтов на сайте', value: `${accounts}` },
        ].map((stat) => (
          <div key={stat.label} className="rounded-xl border border-white/7 bg-panel/40 p-3">
            <p className="font-mono text-[0.55rem] uppercase tracking-widest text-white/30">{stat.label}</p>
            <p className="stat-num mt-1 text-lg font-bold text-white">{stat.value}</p>
          </div>
        ))}
      </div>

      <AdminCard>
        {customers.length === 0 ? (
          <div className="py-12 text-center">
            <Users size={24} className="mx-auto text-white/20" />
            <p className="mt-2 text-[0.8rem] font-bold text-white/60">Покупателей пока нет</p>
            <p className="mt-1 text-[0.72rem] text-white/30">Таблица заполнится после реальных оплат{includeDemo ? '' : ' (демо-заказы скрыты)'}.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="admin-table min-w-[600px]">
              <thead><tr><th>Никнейм</th><th>Заказов</th><th>Потрачено</th><th>Последняя покупка</th><th /></tr></thead>
              <tbody>
                {customers.map((customer) => (
                  <tr key={customer.nickname}>
                    <td>
                      <div className="flex items-center gap-2">
                        <span className="grid h-7 w-7 place-items-center rounded bg-neon/12 font-display text-[0.6rem] font-bold text-neon">
                          {customer.nickname.slice(0, 2).toUpperCase()}
                        </span>
                        <span className="font-semibold text-white/85">{customer.nickname}</span>
                      </div>
                    </td>
                    <td><Pill color="#A855F7">{customer.orders}</Pill></td>
                    <td className="stat-num text-white/85">{formatPrice(customer.spent)}</td>
                    <td className="font-mono text-[0.62rem] text-white/35">{formatDate(customer.lastAt)}</td>
                    <td>
                      <Link href={`/admin/orders?q=${encodeURIComponent(customer.nickname)}`} className="text-[0.66rem] font-semibold text-neon hover:underline">заказы →</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AdminCard>

      <AdminCard
        title="Зарегистрированные аккаунты"
        description="Игроки, создавшие аккаунт на сайте (не путать с покупателями по никнейму)"
      >
        {users.length === 0 ? (
          <div className="py-10 text-center">
            <Users size={22} className="mx-auto text-white/20" />
            <p className="mt-2 text-[0.8rem] font-bold text-white/60">Аккаунтов пока нет</p>
            <p className="mt-1 text-[0.72rem] text-white/30">Игроки могут зарегистрироваться на странице /register.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="admin-table min-w-[560px]">
              <thead><tr><th>Никнейм</th><th>Email</th><th>Регистрация</th><th>Последний вход</th><th /></tr></thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td className="font-semibold text-white/85">{user.username}</td>
                    <td className="text-[0.72rem] text-white/50">{user.email}</td>
                    <td className="font-mono text-[0.62rem] text-white/35">{formatDate(user.createdAt)}</td>
                    <td className="font-mono text-[0.62rem] text-white/35">{user.lastLoginAt ? formatDate(user.lastLoginAt) : '—'}</td>
                    <td>
                      <Link href={`/admin/orders?q=${encodeURIComponent(user.username)}`} className="text-[0.66rem] font-semibold text-neon hover:underline">
                        заказы →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AdminCard>
    </div>
  );
}
