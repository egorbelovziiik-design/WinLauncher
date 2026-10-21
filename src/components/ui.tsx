import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, Search, X, Check, AlertTriangle, Info, Loader2 } from 'lucide-react';
import { useT } from '../i18n';
import { useApp } from '../store/app';

export function cn(...parts: (string | false | null | undefined | number)[]): string {
  return parts.filter(Boolean).join(' ');
}

/* -------------------------------- Button -------------------------------- */
type ButtonVariant = 'primary' | 'ghost' | 'danger' | 'subtle';
type ButtonSize = 'sm' | 'md' | 'lg';

export function Button({
  variant = 'ghost',
  size = 'md',
  loading,
  icon,
  children,
  className,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: React.ReactNode;
}) {
  const sizes: Record<ButtonSize, string> = {
    sm: 'px-3 py-1.5 text-[12px] rounded-lg',
    md: 'px-4 py-2.5 text-sm rounded-xl',
    lg: 'px-6 py-3.5 text-base rounded-2xl',
  };
  const variants: Record<ButtonVariant, string> = {
    primary: 'btn-primary',
    ghost: 'btn-ghost',
    danger: 'btn-danger',
    subtle: 'bg-transparent text-muted hover:text-ink hover:bg-ink/5',
  };
  return (
    <button
      className={cn('btn', variants[variant], sizes[size], className)}
      disabled={rest.disabled || loading}
      {...rest}
    >
      {loading ? <Loader2 size={16} className="animate-spin" /> : icon}
      {children}
    </button>
  );
}

export function IconButton({
  className,
  children,
  active,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { active?: boolean }) {
  return (
    <button
      className={cn(
        'inline-flex h-9 w-9 items-center justify-center rounded-xl text-muted transition-all duration-200 hover:bg-ink/8 hover:text-ink',
        active && 'bg-ink/10 text-ink',
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}

/* -------------------------------- Surface -------------------------------- */
export function Card({
  children,
  className,
  hover,
  ...rest
}: React.HTMLAttributes<HTMLDivElement> & { hover?: boolean }) {
  return (
    <div className={cn('card', hover && 'card-hover', className)} {...rest}>
      {children}
    </div>
  );
}

export function SectionHeader({
  title,
  subtitle,
  right,
  className,
}: {
  title: string;
  subtitle?: string;
  right?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('mb-4 flex items-end justify-between gap-4', className)}>
      <div>
        <h2 className="h-title text-[17px]">{title}</h2>
        {subtitle && <p className="h-sub mt-1">{subtitle}</p>}
      </div>
      {right}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  hint,
  action,
}: {
  icon?: React.ReactNode;
  title: string;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-line/15 px-6 py-14 text-center">
      {icon && <div className="text-muted/60">{icon}</div>}
      <div className="text-sm font-semibold text-ink/80">{title}</div>
      {hint && <div className="max-w-md text-xs text-muted">{hint}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/* -------------------------------- Badges -------------------------------- */
export function Badge({
  children,
  tone = 'default',
  className,
}: {
  children: React.ReactNode;
  tone?: 'default' | 'accent' | 'success' | 'warning' | 'danger' | 'info';
  className?: string;
}) {
  const tones: Record<string, string> = {
    default: 'bg-ink/6 text-muted',
    accent: 'bg-accent/15 text-accent',
    success: 'bg-success/15 text-success',
    warning: 'bg-warning/15 text-warning',
    danger: 'bg-danger/15 text-danger',
    info: 'bg-accent2/15 text-accent2',
  };
  return <span className={cn('chip', tones[tone], className)}>{children}</span>;
}

export function Dot({ tone = 'success' }: { tone?: 'success' | 'warning' | 'danger' | 'muted' }) {
  const map = { success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger', muted: 'bg-muted' };
  return <span className={cn('inline-block h-1.5 w-1.5 rounded-full', map[tone])} />;
}

/* -------------------------------- Progress -------------------------------- */
export function Progress({
  value,
  max = 100,
  indeterminate,
  className,
  tone,
}: {
  value: number;
  max?: number;
  indeterminate?: boolean;
  className?: string;
  tone?: 'accent' | 'success';
}) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <div className={cn('h-2 w-full overflow-hidden rounded-full bg-ink/10', className)}>
      <div
        className={cn(
          'h-full rounded-full transition-[width] duration-300 ease-out',
          tone === 'success'
            ? 'bg-success'
            : 'bg-gradient-to-r from-accent to-accent2',
          indeterminate && 'animate-shimmer bg-[linear-gradient(90deg,transparent,rgb(var(--c-accent)/.8),transparent)] bg-[length:200%_100%]',
        )}
        style={indeterminate ? undefined : { width: `${pct}%` }}
      />
    </div>
  );
}

export function Spinner({ size = 18, className }: { size?: number; className?: string }) {
  return <Loader2 size={size} className={cn('animate-spin text-accent', className)} />;
}

/* -------------------------------- Toggle -------------------------------- */
export function Toggle({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative h-6 w-11 shrink-0 rounded-full border transition-colors duration-200',
        checked
          ? 'border-transparent bg-gradient-to-r from-accent to-accent2'
          : 'border-line/15 bg-ink/10',
        disabled && 'opacity-40',
      )}
    >
      <span
        className={cn(
          'toggle-thumb absolute top-0.5 h-4.5 w-4.5 rounded-full bg-white shadow',
          checked ? 'translate-x-[22px]' : 'translate-x-0.5',
        )}
        style={{ height: 18, width: 18 }}
      />
    </button>
  );
}

export function SettingRow({
  title,
  hint,
  children,
  className,
}: {
  title: string;
  hint?: string;
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex items-center justify-between gap-6 py-3.5', className)}>
      <div className="min-w-0">
        <div className="text-sm font-semibold">{title}</div>
        {hint && <div className="mt-0.5 text-xs leading-relaxed text-muted">{hint}</div>}
      </div>
      <div className="flex shrink-0 items-center gap-2">{children}</div>
    </div>
  );
}

/* -------------------------------- Inputs -------------------------------- */
export function Input({
  label,
  hint,
  error,
  className,
  icon,
  ...rest
}: React.InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  hint?: string;
  error?: string;
  icon?: React.ReactNode;
}) {
  return (
    <label className="block">
      {label && <span className="label">{label}</span>}
      <div className="relative">
        {icon && (
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted">
            {icon}
          </span>
        )}
        <input
          className={cn('input', icon && 'pl-9', error && 'border-danger/50', className)}
          {...rest}
        />
      </div>
      {error ? (
        <span className="mt-1.5 block text-[11px] text-danger">{error}</span>
      ) : (
        hint && <span className="mt-1.5 block text-[11px] text-muted">{hint}</span>
      )}
    </label>
  );
}

export function SearchInput({
  value,
  onChange,
  placeholder,
  className,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
}) {
  return (
    <div className={cn('relative', className)}>
      <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
      <input
        className="input pl-10 pr-9"
        value={value}
        placeholder={placeholder}
        autoFocus={autoFocus}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button
          className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted hover:text-ink"
          onClick={() => onChange('')}
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}

export function Select({
  label,
  value,
  onChange,
  options,
  className,
  disabled,
}: {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  className?: string;
  disabled?: boolean;
}) {
  return (
    <label className={cn('block', className)}>
      {label && <span className="label">{label}</span>}
      <div className="relative">
        <select
          className="input appearance-none pr-9"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value} className="bg-surface text-ink">
              {o.label}
            </option>
          ))}
        </select>
        <ChevronDown size={15} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted" />
      </div>
    </label>
  );
}

/* -------------------------------- Combobox -------------------------------- */
export function Combobox({
  label,
  value,
  onChange,
  options,
  placeholder,
  hint,
  disabled,
  emptyText,
}: {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string; hint?: string }[];
  placeholder?: string;
  hint?: string;
  disabled?: boolean;
  emptyText?: string;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const selected = options.find((o) => o.value === value);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      {label && <span className="label">{label}</span>}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        className={cn('input flex items-center justify-between gap-2 text-left', disabled && 'opacity-50')}
      >
        <span className={cn('truncate', !selected && 'text-muted/70')}>
          {selected ? selected.label : placeholder ?? '—'}
        </span>
        <ChevronDown size={15} className={cn('shrink-0 text-muted transition-transform', open && 'rotate-180')} />
      </button>
      {hint && <span className="mt-1.5 block text-[11px] text-muted">{hint}</span>}
      {open && (
        <div className="absolute z-50 mt-2 w-full overflow-hidden rounded-xl border border-line/12 bg-panel/95 shadow-soft backdrop-blur-xl animate-scale-in">
          <div className="border-b border-line/10 p-2">
            <input
              autoFocus
              className="input py-1.5 text-[13px]"
              placeholder={placeholder}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          <div className="scroll-area max-h-64 p-1.5">
            {filtered.length === 0 && (
              <div className="px-3 py-6 text-center text-xs text-muted">{emptyText ?? '—'}</div>
            )}
            {filtered.map((o) => (
              <button
                key={o.value}
                type="button"
                onClick={() => {
                  onChange(o.value);
                  setOpen(false);
                  setQuery('');
                }}
                className={cn(
                  'flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-[13px] transition-colors hover:bg-ink/6',
                  o.value === value && 'bg-accent/10 text-accent',
                )}
              >
                <span className="truncate">{o.label}</span>
                {o.hint && <span className="shrink-0 text-[11px] text-muted">{o.hint}</span>}
                {o.value === value && <Check size={14} className="shrink-0" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* -------------------------------- Slider -------------------------------- */
export function Slider({
  value,
  min,
  max,
  step = 1,
  onChange,
  suffix,
}: {
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  suffix?: string;
}) {
  return (
    <div className="flex items-center gap-3">
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="flex-1"
      />
      <div className="flex w-28 items-center justify-end gap-1 rounded-lg border border-line/10 bg-ink/5 px-2 py-1.5">
        <input
          type="number"
          className="w-full bg-transparent text-right text-[13px] font-semibold outline-none"
          value={value}
          min={min}
          max={max}
          step={step}
          onChange={(e) => onChange(Math.max(min, Math.min(max, Number(e.target.value) || min)))}
        />
        {suffix && <span className="text-[11px] text-muted">{suffix}</span>}
      </div>
    </div>
  );
}

/* -------------------------------- Tabs -------------------------------- */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
  className,
}: {
  tabs: { value: T; label: string; icon?: React.ReactNode; count?: number }[];
  value: T;
  onChange: (v: T) => void;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-wrap items-center gap-1 rounded-xl border border-line/10 bg-ink/4 p-1', className)}>
      {tabs.map((tab) => (
        <button
          key={tab.value}
          onClick={() => onChange(tab.value)}
          className={cn(
            'inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-[13px] font-semibold transition-all duration-200',
            value === tab.value
              ? 'bg-panel text-ink shadow-sm'
              : 'text-muted hover:text-ink',
          )}
        >
          {tab.icon}
          {tab.label}
          {typeof tab.count === 'number' && (
            <span className="rounded-md bg-ink/8 px-1.5 py-0.5 text-[10px]">{tab.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  size = 'md',
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
  size?: 'sm' | 'md';
}) {
  return (
    <div className="inline-flex items-center gap-0.5 rounded-xl border border-line/10 bg-ink/5 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            'rounded-lg font-semibold transition-all duration-200',
            size === 'sm' ? 'px-2.5 py-1 text-[11px]' : 'px-3 py-1.5 text-[12px]',
            value === o.value ? 'bg-panel text-ink shadow-sm' : 'text-muted hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/* -------------------------------- Modal -------------------------------- */
export function Modal({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  width = 'max-w-lg',
}: {
  open: boolean;
  onClose: () => void;
  title?: React.ReactNode;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  width?: string;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-5">
      <div className="absolute inset-0 bg-black/55 backdrop-blur-sm animate-fade-in" onClick={onClose} />
      <div
        className={cn(
          'relative flex max-h-[88vh] w-full flex-col overflow-hidden rounded-2xl border border-line/12 bg-panel/95 shadow-2xl backdrop-blur-2xl animate-scale-in',
          width,
        )}
      >
        {(title || subtitle) && (
          <div className="flex items-start justify-between gap-6 border-b border-line/10 px-6 py-5">
            <div>
              {title && <h3 className="font-display text-lg font-bold">{title}</h3>}
              {subtitle && <p className="mt-1 text-[12px] leading-relaxed text-muted">{subtitle}</p>}
            </div>
            <IconButton onClick={onClose} aria-label="close">
              <X size={17} />
            </IconButton>
          </div>
        )}
        <div className="scroll-area flex-1 px-6 py-5">{children}</div>
        {footer && <div className="flex items-center justify-end gap-2 border-t border-line/10 px-6 py-4">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

/* -------------------------------- Confirm -------------------------------- */
interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}
const ConfirmContext = createContext<(opts: ConfirmOptions) => Promise<boolean>>(async () => false);

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const t = useT();
  const [state, setState] = useState<(ConfirmOptions & { resolve: (v: boolean) => void }) | null>(null);

  const open = useCallback((opts: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => setState({ ...opts, resolve }));
  }, []);

  const close = (value: boolean) => {
    state?.resolve(value);
    setState(null);
  };

  return (
    <ConfirmContext.Provider value={open}>
      {children}
      <Modal
        open={!!state}
        onClose={() => close(false)}
        title={state?.title}
        width="max-w-md"
        footer={
          <>
            <Button variant="ghost" onClick={() => close(false)}>
              {state?.cancelLabel ?? t('common.cancel')}
            </Button>
            <Button variant={state?.danger ? 'danger' : 'primary'} onClick={() => close(true)}>
              {state?.confirmLabel ?? t('common.confirm')}
            </Button>
          </>
        }
      >
        <p className="text-sm leading-relaxed text-muted">{state?.message}</p>
      </Modal>
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  return useContext(ConfirmContext);
}

/* -------------------------------- Toasts -------------------------------- */
export function Toasts() {
  const t = useT();
  const { toasts, dismissToast } = useApp();
  return createPortal(
    <div className="pointer-events-none fixed bottom-5 right-5 z-[100] flex w-[360px] flex-col gap-2.5">
      {toasts.map((toast) => {
        const text = toast.key ? t(toast.key, {}) : toast.message;
        const icon =
          toast.kind === 'success' ? (
            <Check size={16} className="text-success" />
          ) : toast.kind === 'error' ? (
            <AlertTriangle size={16} className="text-danger" />
          ) : (
            <Info size={16} className="text-accent2" />
          );
        return (
          <div
            key={toast.id}
            className="pointer-events-auto flex items-start gap-3 rounded-xl border border-line/12 bg-panel/95 p-3.5 shadow-soft backdrop-blur-xl animate-fade-up"
          >
            <span className="mt-0.5 shrink-0">{icon}</span>
            <div className="min-w-0 flex-1 text-[13px] leading-relaxed text-ink/90">{text}</div>
            <button className="shrink-0 text-muted hover:text-ink" onClick={() => dismissToast(toast.id)}>
              <X size={14} />
            </button>
          </div>
        );
      })}
    </div>,
    document.body,
  );
}

/* -------------------------------- Misc -------------------------------- */
export function formatBytes(bytes: number, digits = 1): string {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : digits)} ${units[i]}`;
}

export function formatSpeed(bytesPerSec: number): string {
  return `${formatBytes(bytesPerSec)}/s`;
}

export function formatEta(seconds: number): string {
  if (!isFinite(seconds) || seconds <= 0) return '—';
  if (seconds < 60) return `${Math.round(seconds)}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.round((seconds % 3600) / 60)}m`;
}

export function formatNumber(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

export function formatDate(iso: string | null | undefined, locale: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(d);
}

export function useCopy() {
  const { toast } = useApp();
  const t = useT();
  return useCallback(
    async (text: string) => {
      try {
        await navigator.clipboard.writeText(text);
        toast('success', t('common.copied'));
      } catch {
        toast('error', t('errors.generic'));
      }
    },
    [toast, t],
  );
}

export function Page({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('mx-auto w-full max-w-[1180px] px-7 pb-10 pt-5', className)}>{children}</div>;
}

export function PageTitle({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4 animate-fade-up">
      <div>
        <h1 className="font-display text-[26px] font-extrabold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-[13px] leading-relaxed text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Highlight({ text, query }: { text: string; query: string }) {
  if (!query.trim()) return <>{text}</>;
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, idx)}
      <mark className="rounded bg-accent/25 px-0.5 text-accent">{text.slice(idx, idx + query.length)}</mark>
      {text.slice(idx + query.length)}
    </>
  );
}
