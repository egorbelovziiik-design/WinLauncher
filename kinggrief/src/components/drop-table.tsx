import { Sparkles } from 'lucide-react';
import { RARITY_META } from '@/lib/types';
import type { CaseItem } from '@/lib/types';

export function DropTable({ items, compact = false }: { items: CaseItem[]; compact?: boolean }) {
  const max = Math.max(...items.map((i) => i.chance), 1);
  return (
    <ul className="space-y-1.5">
      {items.slice(0, compact ? 5 : 50).map((item) => {
        const rarity = RARITY_META[item.rarity] ?? RARITY_META.common;
        return (
          <li key={`${item.caseId}-${item.name}`}>
            <div className="flex items-center gap-2.5 rounded-lg border border-white/5 bg-white/[0.02] px-2.5 py-2">
              <span
                className="grid h-7 w-7 shrink-0 place-items-center rounded-md"
                style={{ background: `${item.color}18`, color: item.color, boxShadow: `inset 0 0 0 1px ${item.color}30` }}
              >
                <Sparkles size={13} />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="truncate text-[0.76rem] font-semibold text-white/80">{item.name}</span>
                  <span className="stat-num shrink-0 text-[0.7rem] font-bold" style={{ color: rarity.color }}>
                    {item.chance}%
                  </span>
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <div className="h-1 flex-1 overflow-hidden rounded-full bg-white/6">
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${Math.max((item.chance / max) * 100, 2)}%`,
                        background: `linear-gradient(90deg, ${item.color}, ${rarity.color})`,
                      }}
                    />
                  </div>
                  <span className="font-mono text-[0.48rem] uppercase tracking-widest" style={{ color: item.color }}>
                    {rarity.label}
                  </span>
                </div>
              </div>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
