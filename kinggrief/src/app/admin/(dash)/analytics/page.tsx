import Link from 'next/link';
import { ArrowUpRight, Download, Ticket, TrendingUp } from 'lucide-react';
import {
  ordersByDayCount, popularProducts, revenueSeries, revenueTotals, topCustomers, topPromos,
} from '@/lib/repo';
import { formatDate, formatPrice } from '@/lib/utils';
import { OrdersChart, RevenueChart, TopProductsChart } from '@/components/admin/charts';
import { AdminCard, Pill } from '@/components/admin/kit';

export const dynamic = 'force-dynamic';

export default async function AdminAnalyticsPage({ searchParams }: { searchParams: Promise<{ demo?: string }> }) {
  const { demo } = await searchParams;
  const includeDemo = demo === '1';
  const totals = revenueTotals(includeDemo);
  const series = revenueSeries(30, includeDemo);
  const products = popularProducts(10, includeDemo);
  const customers = topCustomers(10, includeDemo);
  const promos = topPromos();
  const days = ordersByDayCount(30, includeDemo);
  const conversion = totals.allOrders > 0 ? Math.round((totals.paidOrders / totals.allOrders) * 100) : 0;
  const bestDay = [...days].sort((a, b) => b.revenue - a.revenue)[0];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">отчёты</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Аналитика</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">
            {includeDemo ? 'Включая демо-заказы (помечены is_demo).' : 'Только реальные оплаченные заказы.'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href={includeDemo ? '/admin/analytics' : '/admin/analytics?demo=1'} className={`btn btn-sm ${includeDemo ? 'btn-gold' : 'btn-ghost'}`}>
            {includeDemo ? 'Только реальные' : 'Показать демо'}
          </Link>
          <a href="/api/admin/export" className="btn btn-ghost btn-sm"><Download size={13} /> CSV</a>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Сегодня', value: formatPrice(totals.todayRevenue), hint: `${totals.todayOrders} заказов` },
          { label: '7 дней', value: formatPrice(totals.weekRevenue), hint: 'оплаченные' },
          { label: '30 дней', value: formatPrice(totals.monthRevenue), hint: 'оплаченные' },
          { label: 'Всего', value: formatPrice(totals.totalRevenue), hint: `${totals.paidOrders} оплаченных` },
        ].map((item) => (
          <div key={item.label} className="rounded-xl border border-white/7 bg-gradient-to-b from-[#141226] to-[#0b0a14] p-4">
            <p className="font-mono text-[0.55rem] uppercase tracking-widest text-white/30">{item.label}</p>
            <p className="stat-num mt-1 text-xl font-extrabold text-white">{item.value}</p>
            <p className="text-[0.62rem] text-white/30">{item.hint}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.5fr_1fr]">
        <AdminCard title="Доход по дням" description="Последние 30 дней">
          <RevenueChart data={series} height={280} />
        </AdminCard>
        <div className="space-y-4">
          <AdminCard title="Конверсия в оплату" description="Доля оплаченных заказов от созданных">
            <div className="flex items-end justify-between">
              <p className="stat-num text-3xl font-extrabold text-emerald">{conversion}%</p>
              <p className="text-[0.68rem] text-white/35">{totals.paidOrders} / {totals.allOrders}</p>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-white/6">
              <div className="h-full rounded-full bg-gradient-to-r from-emerald to-neon" style={{ width: `${Math.max(conversion, 1)}%` }} />
            </div>
            {bestDay && bestDay.revenue > 0 && (
              <p className="mt-3 flex items-center gap-1.5 text-[0.7rem] text-white/40">
                <TrendingUp size={12} className="text-neon" /> Лучший день: {bestDay.label} · {formatPrice(bestDay.revenue)}
              </p>
            )}
          </AdminCard>
          <AdminCard title="Заказы по дням">
            <OrdersChart data={series.slice(-14)} height={170} />
          </AdminCard>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <AdminCard title="Популярные товары" description="По количеству проданных единиц">
          {products.length === 0 ? <p className="py-6 text-center text-[0.74rem] text-white/30">Нет данных.</p> : <TopProductsChart data={products} />}
        </AdminCard>
        <AdminCard title="Топ покупатели" description="По сумме покупок">
          {customers.length === 0 ? (
            <p className="py-6 text-center text-[0.74rem] text-white/30">Нет данных.</p>
          ) : (
            <ul className="divide-y divide-white/6">
              {customers.map((customer, i) => (
                <li key={customer.nickname} className="flex items-center justify-between gap-2 py-2 text-[0.76rem]">
                  <span className="flex items-center gap-2">
                    <span className="font-mono text-[0.6rem] text-white/25">{String(i + 1).padStart(2, '0')}</span>
                    <span className="text-white/75">{customer.nickname}</span>
                  </span>
                  <span className="stat-num text-white/85">{formatPrice(customer.spent)}</span>
                </li>
              ))}
            </ul>
          )}
        </AdminCard>
      </div>

      <AdminCard title="Промокоды" description="Использования и суммарная скидка">
        {promos.length === 0 ? (
          <p className="py-6 text-center text-[0.74rem] text-white/30">Промокоды ещё не применялись.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="admin-table min-w-[420px]">
              <thead><tr><th>Код</th><th>Использований</th><th>Скидка всего</th></tr></thead>
              <tbody>
                {promos.map((promo) => (
                  <tr key={promo.code}>
                    <td><span className="inline-flex items-center gap-1.5 font-mono text-[0.74rem] font-bold text-white"><Ticket size={11} className="text-neon" /> {promo.code}</span></td>
                    <td><Pill color="#A855F7">{promo.uses}</Pill></td>
                    <td className="stat-num text-white/80">{formatPrice(promo.saved)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Link href="/admin/promos" className="mt-3 inline-flex items-center gap-1 text-[0.68rem] font-semibold text-neon hover:underline">
          Управлять промокодами <ArrowUpRight size={11} />
        </Link>
      </AdminCard>

      <p className="text-[0.64rem] leading-relaxed text-white/25">
        Данные строятся по журналу заказов. Демо-заказы помечены флагом is_demo=1 и исключены из отчётов по умолчанию.
        Последний заказ в базе: {series[series.length - 1]?.label ?? formatDate(new Date().toISOString())}.
      </p>
    </div>
  );
}
