'use client';

import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { formatPrice } from '@/lib/utils';

type Point = { label: string; revenue: number; orders: number };

const axis = { stroke: 'rgba(255,255,255,0.25)', fontSize: 10, fontFamily: 'var(--font-mono)' };

function DarkTooltip({ active, payload, label }: { active?: boolean; payload?: Array<{ value: number; name: string }>; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-neon/30 bg-[#0d0b18] px-3 py-2 shadow-2xl">
      <p className="font-mono text-[0.58rem] uppercase tracking-widest text-white/35">{label}</p>
      {payload.map((entry) => (
        <p key={entry.name} className="mt-0.5 text-[0.78rem] font-bold text-white">
          {entry.name === 'revenue' ? formatPrice(entry.value) : `${entry.value} заказов`}
        </p>
      ))}
    </div>
  );
}

export function RevenueChart({ data, height = 260 }: { data: Point[]; height?: number }) {
  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
          <defs>
            <linearGradient id="revFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#a855f7" stopOpacity={0.55} />
              <stop offset="100%" stopColor="#a855f7" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="rgba(168,85,247,0.10)" strokeDasharray="3 6" vertical={false} />
          <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={28} />
          <YAxis tick={axis} tickLine={false} axisLine={false} width={46} tickFormatter={(v) => `${v}`} />
          <Tooltip content={<DarkTooltip />} cursor={{ stroke: 'rgba(168,85,247,0.35)' }} />
          <Area type="monotone" dataKey="revenue" stroke="#c084fc" strokeWidth={2} fill="url(#revFill)" name="revenue" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function OrdersChart({ data, height = 220 }: { data: Point[]; height?: number }) {
  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -22, bottom: 0 }}>
          <CartesianGrid stroke="rgba(168,85,247,0.10)" strokeDasharray="3 6" vertical={false} />
          <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={24} />
          <YAxis tick={axis} tickLine={false} axisLine={false} width={36} allowDecimals={false} />
          <Tooltip content={<DarkTooltip />} cursor={{ fill: 'rgba(168,85,247,0.08)' }} />
          <Bar dataKey="orders" name="orders" radius={[4, 4, 0, 0]}>
            {data.map((entry, index) => (
              <Cell key={index} fill={entry.orders > 0 ? '#a855f7' : 'rgba(255,255,255,0.06)'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function TopProductsChart({
  data,
}: {
  data: Array<{ name: string; qty: number; color: string }>;
}) {
  return (
    <div className="space-y-2.5">
      {data.map((item) => {
        const max = Math.max(...data.map((d) => d.qty), 1);
        return (
          <div key={item.name}>
            <div className="flex items-baseline justify-between gap-2 text-[0.74rem]">
              <span className="truncate text-white/65">{item.name}</span>
              <span className="stat-num shrink-0 font-bold text-white/80">{item.qty} шт.</span>
            </div>
            <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-white/5">
              <div
                className="h-full rounded-full"
                style={{
                  width: `${Math.max((item.qty / max) * 100, 3)}%`,
                  background: `linear-gradient(90deg, ${item.color}, ${item.color}66)`,
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
