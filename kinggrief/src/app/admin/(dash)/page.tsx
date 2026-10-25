import Link from 'next/link';
import {
  ArrowUpRight, Banknote, CalendarDays, CreditCard, Crown, Gift, Link2, Package, Plus, Server, ShoppingBag,
  TrendingUp, Ticket, Users,
} from 'lucide-react';
import {
  listOrders, popularProducts, revenueSeries, revenueTotals, topCustomers, getSettings,
} from '@/lib/repo';
import { bridgeStatusCloud } from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { formatDate, formatPrice, statusColors, statusLabels } from '@/lib/utils';
import { RevenueChart, TopProductsChart, OrdersChart } from '@/components/admin/charts';

export const dynamic = 'force-dynamic';

function Kpi({
  label,
  value,
  hint,
  icon: Icon,
  color,
}: {
  label: string;
  value: string;
  hint?: string;
  icon: typeof Banknote;
  color: string;
}) {
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/7 bg-gradient-to-b from-[#141226] to-[#0b0a14] p-4">
      <div className="pointer-events-none absolute -right-6 -top-6 h-20 w-20 rounded-full opacity-20 blur-2xl" style={{ background: color }} />
      <div className="flex items-center justify-between">
        <span className="font-mono text-[0.56rem] uppercase tracking-[0.14em] text-white/35">{label}</span>
        <span className="grid h-7 w-7 place-items-center rounded-md" style={{ background: `${color}18`, color }}>
          <Icon size={14} />
        </span>
      </div>
      <p className="stat-num mt-2 text-2xl font-extrabold text-white">{value}</p>
      {hint && <p className="mt-0.5 text-[0.64rem] text-white/30">{hint}</p>}
    </div>
  );
}

export default async function AdminDashboard({
  searchParams,
}: {
  searchParams: Promise<{ demo?: string }>;
}) {
  await cloudSyncLocalDb({ includeOrders: true });
  const { demo } = await searchParams;
  const includeDemo = demo === '1';
  const totals = revenueTotals(includeDemo);
  const series = revenueSeries(30, includeDemo);
  const orders = listOrders({ limit: 8 });
  const products = popularProducts(5, includeDemo);
  const customers = topCustomers(5, includeDemo);
  const bridge = await bridgeStatusCloud();
  const settings = getSettings();
  const hasRealData = totals.paidOrders > 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">обзор</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Главная</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">Реальные показатели магазина. Демо-заказы учитываются только по переключателю.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href="/admin/cases" className="btn btn-primary btn-sm"><Gift size={13} /> Выдать кейсы</Link>
          <Link href="/admin/products?kind=rank" className="btn btn-gold btn-sm"><Crown size={13} /> Выдать привилегию</Link>
          <Link
            href={includeDemo ? '/admin' : '/admin?demo=1'}
            className={`btn btn-sm ${includeDemo ? 'btn-gold' : 'btn-ghost'}`}
          >
            {includeDemo ? 'Скрыть демо-данные' : 'Показать демо-данные'}
          </Link>
          <Link href="/admin/orders" className="btn btn-ghost btn-sm"><ShoppingBag size={13} /> Все заказы</Link>
          <Link href="/admin/products?kind=rank&new=1" className="btn btn-ghost btn-sm"><Plus size={13} /> Новый товар</Link>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="Доход сегодня" value={formatPrice(totals.todayRevenue)} hint={`${totals.todayOrders} заказов`} icon={Banknote} color="#22C55E" />
        <Kpi label="Доход за неделю" value={formatPrice(totals.weekRevenue)} hint="последние 7 дней" icon={CalendarDays} color="#A855F7" />
        <Kpi label="Доход за месяц" value={formatPrice(totals.monthRevenue)} hint="последние 30 дней" icon={TrendingUp} color="#3B82F6" />
        <Kpi label="Общий доход" value={formatPrice(totals.totalRevenue)} hint={`${totals.paidOrders} оплаченных из ${totals.allOrders}`} icon={CreditCard} color="#FACC15" />
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.6fr_0.9fr]">
        <div className="rounded-xl border border-white/7 bg-panel/40 p-4">
          <div className="mb-2 flex items-center justify-between">
            <div>
              <p className="font-display text-sm font-bold text-white">Доход за 30 дней</p>
              <p className="font-mono text-[0.56rem] uppercase tracking-widest text-white/25">
                {includeDemo ? 'включая демо-заказы' : 'только реальные оплаты'}
              </p>
            </div>
            <span className="rounded bg-emerald/10 px-2 py-0.5 font-mono text-[0.56rem] font-bold text-emerald">RUB</span>
          </div>
          <RevenueChart data={series} />
        </div>

        <div className="space-y-4">
          <div className="rounded-xl border border-white/7 bg-panel/40 p-4">
            <p className="mb-2 font-display text-sm font-bold text-white">Популярные товары</p>
            {products.length === 0 ? (
              <p className="py-4 text-center text-[0.72rem] text-white/30">Нет оплаченных заказов — статистика появится после первой продажи.</p>
            ) : (
              <TopProductsChart data={products} />
            )}
          </div>
          <div className={`rounded-xl border p-4 ${bridge.connected ? 'border-emerald/25 bg-emerald/[0.05]' : 'border-amber/25 bg-amber/[0.05]'}`}>
            <div className="flex items-center justify-between">
              <p className="font-display text-sm font-bold text-white">Мост Minecraft</p>
              <span className={`font-mono text-[0.56rem] font-bold uppercase tracking-widest ${bridge.connected ? 'text-emerald' : 'text-amber'}`}>
                {bridge.connected ? 'online' : 'offline'}
              </span>
            </div>
            <p className="mt-1 text-[0.68rem] leading-relaxed text-white/40">
              {bridge.connected
                ? `Последний опрос: ${formatDate(bridge.lastSeen)}`
                : 'Плагин ещё не подключался. Заказы встают в очередь и будут выданы после подключения (или вручную).'}
            </p>
            <div className="mt-2 grid grid-cols-2 gap-2 font-mono text-[0.6rem] text-white/35">
              <span className="flex items-center gap-1"><Server size={11} /> {settings.server_ip}</span>
              <span className="flex items-center gap-1"><Users size={11} /> {settings.bridge_online ?? '—'} online</span>
            </div>
            <Link href="/admin/settings#minecraft" className="mt-2 inline-flex items-center gap-1 text-[0.66rem] font-semibold text-neon hover:underline">
              Настроить интеграцию <ArrowUpRight size={11} />
            </Link>
          </div>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.6fr_0.9fr]">
        <div className="overflow-hidden rounded-xl border border-white/7 bg-panel/40">
          <div className="flex items-center justify-between border-b border-white/6 px-4 py-3">
            <p className="font-display text-sm font-bold text-white">Последние заказы</p>
            <Link href="/admin/orders" className="text-[0.66rem] font-semibold text-neon hover:underline">все →</Link>
          </div>
          {orders.length === 0 ? (
            <p className="px-4 py-8 text-center text-[0.74rem] text-white/30">Заказов пока нет.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="admin-table min-w-[540px]">
                <thead><tr><th>Заказ</th><th>Игрок</th><th>Сумма</th><th>Статус</th><th>Дата</th></tr></thead>
                <tbody>
                  {orders.map((order) => (
                    <tr key={order.id}>
                      <td>
                        <Link href={`/admin/orders/${order.publicId}`} className="font-mono text-[0.7rem] text-neon hover:underline">
                          {order.publicId}
                        </Link>
                      </td>
                      <td className="text-white/70">{order.nickname}</td>
                      <td className="stat-num text-white/80">{formatPrice(order.total)}</td>
                      <td>
                        <span className="rounded-full px-1.5 py-0.5 font-mono text-[0.54rem] font-bold uppercase" style={{ background: `${statusColors[order.status]}1a`, color: statusColors[order.status] }}>
                          {statusLabels[order.status]}
                        </span>
                      </td>
                      <td className="font-mono text-[0.62rem] text-white/30">{formatDate(order.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="space-y-4">
          <div className="rounded-xl border border-white/7 bg-panel/40 p-4">
            <p className="mb-2 font-display text-sm font-bold text-white">Заказы по дням</p>
            <OrdersChart data={series.slice(-14)} height={150} />
          </div>
          <div className="rounded-xl border border-white/7 bg-panel/40 p-4">
            <p className="mb-2 font-display text-sm font-bold text-white">Топ покупателей</p>
            {customers.length === 0 ? (
              <p className="py-3 text-center text-[0.7rem] text-white/30">Пока нет оплаченных заказов.</p>
            ) : (
              <ul className="space-y-2">
                {customers.map((customer) => (
                  <li key={customer.nickname} className="flex items-center justify-between gap-2 text-[0.74rem]">
                    <span className="flex items-center gap-2">
                      <span className="grid h-6 w-6 place-items-center rounded bg-neon/12 font-display text-[0.55rem] font-bold text-neon">
                        {customer.nickname.slice(0, 2).toUpperCase()}
                      </span>
                      <span className="text-white/70">{customer.nickname}</span>
                    </span>
                    <span className="stat-num text-white/80">{formatPrice(customer.spent)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>

      {!hasRealData && !includeDemo && (
        <div className="rounded-xl border border-amber/25 bg-amber/[0.06] p-4">
          <p className="text-[0.78rem] font-bold text-white">В базе пока нет реальных оплат</p>
          <p className="mt-1 text-[0.72rem] leading-relaxed text-white/45">
            Это нормально для нового магазина: метрики показывают 0, а не выдуманные цифры. Для проверки интерфейса можно
            сгенерировать демо-заказы — они помечаются в базе и не влияют на реальные отчёты.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link href="/admin?demo=1" className="btn btn-ghost btn-sm">Посмотреть с демо-данными</Link>
            <Link href="/admin/settings#demo" className="btn btn-ghost btn-sm">Сгенерировать демо-заказы</Link>
          </div>
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { href: '/admin/products?kind=rank', label: 'Товары и цены', icon: Package, text: '14 привилегий, кейсы, рилики' },
          { href: '/admin/promos', label: 'Промокоды', icon: Ticket, text: 'Скидки и лимиты' },
          { href: '/admin/cases', label: 'Кейсы', icon: Link2, text: 'Награды и вероятности' },
          { href: '/admin/security', label: 'Безопасность', icon: Server, text: '2FA и журнал действий' },
        ].map((item) => (
          <Link key={item.href} href={item.href} className="group rounded-xl border border-white/7 bg-white/[0.02] p-4 transition hover:border-neon/30">
            <item.icon size={16} className="text-neon" />
            <p className="mt-2 text-[0.8rem] font-bold text-white">{item.label}</p>
            <p className="mt-0.5 text-[0.66rem] text-white/30">{item.text}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
