import { useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { AlertCircle, ArrowUpRight, LoaderCircle, X } from 'lucide-react';
import clsx from 'clsx';
import { useT } from '../locales';

export function Button({ children, variant = 'secondary', busy, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; busy?: boolean }) {
  return <button type="button" className={clsx('button', `button-${variant}`, className)} {...props} disabled={props.disabled || busy}>{busy && <LoaderCircle size={16} className="spin" />}{children}</button>;
}
export function IconButton({ label, children, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) { return <button type="button" aria-label={label} title={label} className={clsx('icon-button', className)} {...props}>{children}</button>; }
export function Tag({ children, color = '', className }: { children: ReactNode; color?: string; className?: string }) { return <span className={clsx('tag', color && `tag-${color}`, className)}>{children}</span>; }
export function Toggle({ checked, onChange, label, description, disabled }: { checked: boolean; onChange: (value: boolean) => void; label: string; description?: string; disabled?: boolean }) {
  return <label className={clsx('toggle-row', disabled && 'is-disabled')}><span><strong>{label}</strong>{description && <small>{description}</small>}</span><button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled} onClick={() => onChange(!checked)} className={clsx('toggle', checked && 'checked')}><span /></button></label>;
}
export function PageHeading({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) { return <div className="page-heading"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{description && <p className="page-description">{description}</p>}</div>{action}</div>; }
export function EmptyState({ icon, title, description, action, compact }: { icon: ReactNode; title: string; description?: string; action?: ReactNode; compact?: boolean }) { return <div className={clsx('empty-state', compact && 'empty-compact')}><div className="empty-icon">{icon}</div><h3>{title}</h3>{description && <p>{description}</p>}{action}</div>; }
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) { const t = useT(); return <div className="error-state" role="alert"><AlertCircle size={20} /><div><strong>Не удалось получить данные</strong><p>{error instanceof Error ? error.message : String(error)}</p></div>{retry && <Button onClick={retry}>{t('Повторить')}</Button>}</div>; }
export function Skeleton({ rows = 3 }: { rows?: number }) { return <div className="skeleton-list" aria-label="Загрузка" role="status">{Array.from({ length: rows }, (_, i) => <div className="skeleton" key={i} />)}</div>; }
export function Modal({ title, subtitle, children, onClose, wide, className }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; wide?: boolean; className?: string }) {
  const ref = useRef<HTMLDivElement>(null); const id = useId(); const close = useRef(onClose); close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const modal = ref.current; const focusable = () => Array.from(modal?.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href],[tabindex="0"]') ?? []).filter(el => el.getClientRects().length > 0);
    const timer = window.setTimeout(() => (focusable()[0] ?? modal)?.focus(), 20);
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); close.current(); }
      if (e.key === 'Tab') { const nodes = focusable(); const first = nodes[0]; const last = nodes[nodes.length - 1]; if (!nodes.length) { e.preventDefault(); modal?.focus(); } else if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); } }
    };
    modal?.addEventListener('keydown', key);
    return () => { clearTimeout(timer); modal?.removeEventListener('keydown', key); previous?.focus(); };
  }, []);
  return createPortal(<motion.div className="modal-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}><motion.div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={id} className={clsx('modal', wide && 'modal-wide', className)} initial={{ y: 12, scale: .98 }} animate={{ y: 0, scale: 1 }}><div className="modal-header"><div><h2 id={id}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><IconButton label="Закрыть" onClick={onClose}><X size={20} /></IconButton></div>{children}</motion.div></motion.div>, document.body);
}
export function MicrosoftMark() { return <span className="microsoft-mark" aria-hidden="true"><i /><i /><i /><i /></span>; }
export function Logo({ small }: { small?: boolean }) { return <div className={clsx('logo', small && 'logo-small')}><svg viewBox="0 0 64 64" aria-hidden="true"><path d="M7 13h10v26h8V24h14v15h8V13h10v38H35V35h-6v16H7z" fill="currentColor" /></svg>{!small && <span>WIN<span>LAUNCHER</span></span>}</div>; }
export function ExternalArrow() { return <ArrowUpRight size={16} />; }
