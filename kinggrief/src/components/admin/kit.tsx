'use client';

import { useEffect, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

export function AdminCard({
  title,
  description,
  action,
  children,
  className = '',
  id,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}) {
  return (
    <section id={id} className={cn('rounded-xl border border-white/7 bg-panel/40', className)}>
      {(title || action) && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-white/6 px-4 py-3">
          <div>
            {title && <h2 className="font-display text-sm font-bold text-white">{title}</h2>}
            {description && <p className="mt-0.5 text-[0.68rem] text-white/30">{description}</p>}
          </div>
          {action}
        </div>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

export function Field({
  label,
  hint,
  children,
  className = '',
}: {
  label: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={cn('block', className)}>
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-[0.62rem] leading-snug text-white/25">{hint}</span>}
    </label>
  );
}

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  wide = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (open) window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[85] flex items-end justify-center bg-black/75 p-3 backdrop-blur-sm sm:items-center">
          <motion.div
            initial={{ opacity: 0, y: 30, scale: 0.99 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20 }}
            className={cn('flex max-h-[92vh] w-full flex-col overflow-hidden rounded-2xl border border-neon/20 bg-[#0c0b16]', wide ? 'max-w-3xl' : 'max-w-xl')}
            role="dialog"
            aria-modal="true"
            aria-label={title}
          >
            <div className="flex items-start justify-between gap-3 border-b border-white/8 px-4 py-3">
              <div>
                <h3 className="font-display text-sm font-bold text-white">{title}</h3>
                {description && <p className="mt-0.5 text-[0.66rem] text-white/30">{description}</p>}
              </div>
              <button onClick={onClose} className="text-white/35 transition hover:text-white" aria-label="Закрыть">
                <X size={17} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-4">{children}</div>
            {footer && <div className="flex justify-end gap-2 border-t border-white/8 px-4 py-3">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}

export function Pill({ color, children }: { color: string; children: ReactNode }) {
  return (
    <span
      className="inline-flex items-center rounded-full px-1.5 py-0.5 font-mono text-[0.54rem] font-bold uppercase tracking-wide"
      style={{ background: `${color}1a`, color }}
    >
      {children}
    </span>
  );
}

export function ColorInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <div className="flex items-center gap-2">
      <input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value.toUpperCase())}
        className="h-10 w-12 cursor-pointer rounded-lg border border-white/10 bg-transparent p-1"
        aria-label="Выбор цвета"
      />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value.toUpperCase())}
        className="field h-10 font-mono text-xs uppercase"
        maxLength={7}
        aria-label="HEX-цвет"
      />
    </div>
  );
}

export function SectionTabs({
  tabs,
  active,
}: {
  tabs: Array<{ href: string; label: string; count?: number }>;
  active: string;
}) {
  return (
    <div className="scrollbar-none -mx-1 mb-4 flex gap-1 overflow-x-auto px-1">
      {tabs.map((tab) => (
        <a
          key={tab.href}
          href={tab.href}
          className={cn(
            'shrink-0 rounded-lg border px-3 py-1.5 text-[0.72rem] font-semibold transition',
            active === tab.href.split('kind=')[1] || (!tab.href.includes('kind=') && active === 'all')
              ? 'border-neon/50 bg-neon/15 text-white'
              : 'border-white/8 bg-white/[0.02] text-white/45 hover:border-white/20 hover:text-white',
          )}
        >
          {tab.label}
          {tab.count != null && <span className="ml-1.5 font-mono text-[0.58rem] text-white/30">{tab.count}</span>}
        </a>
      ))}
    </div>
  );
}
