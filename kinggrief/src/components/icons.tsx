'use client';

import type { LucideIcon } from 'lucide-react';
import {
  AlertTriangle, ArrowRight, ArrowUpRight, BadgeCheck, Box, ChartLine, Check, ChevronDown, ChevronRight,
  Clock, Coins, Copy, CreditCard, Crown, Download, Droplet, ExternalLink, Eye, Feather, Flame, Gavel, Gem,
  Gift, Globe, Info, LayoutDashboard, Loader2, Lock, LogOut, Mail, Menu, MessageCircle, MessageSquareOff,
  Minus, Network, Package, Pencil, Plus, QrCode, Rabbit, RefreshCw, Save, Search, Send, Server, Settings,
  Shield, ShieldAlert, ShieldCheck, ShieldHalf, ShoppingCart, Sparkles, Star, Swords, Ticket, Trash2,
  Trophy, Unlock, User, Users, VolumeX, Wallet, Wrench, X, Zap,
} from 'lucide-react';
import type { CSSProperties } from 'react';

const registry: Record<string, LucideIcon> = {
  feather: Feather, droplet: Droplet, rabbit: Rabbit, zap: Zap, crown: Crown, shield: Shield,
  'shield-check': ShieldCheck, 'shield-half': ShieldHalf, wrench: Wrench, gavel: Gavel, flame: Flame,
  unlock: Unlock, 'message-square-off': MessageSquareOff, network: Network, 'volume-x': VolumeX,
  box: Box, sparkles: Sparkles, coins: Coins, gem: Gem, swords: Swords, users: Users,
  cart: ShoppingCart, menu: Menu, x: X, copy: Copy, check: Check, 'chevron-right': ChevronRight,
  'chevron-down': ChevronDown, 'arrow-right': ArrowRight, 'arrow-up-right': ArrowUpRight, star: Star,
  search: Search, plus: Plus, minus: Minus, trash: Trash2, user: User, dashboard: LayoutDashboard,
  package: Package, ticket: Ticket, chart: ChartLine, settings: Settings, lock: Lock, 'shield-alert': ShieldAlert,
  logout: LogOut, external: ExternalLink, globe: Globe, server: Server, clock: Clock, gift: Gift,
  wallet: Wallet, trophy: Trophy, info: Info, alert: AlertTriangle, loader: Loader2, refresh: RefreshCw,
  card: CreditCard, qr: QrCode, verified: BadgeCheck, eye: Eye, download: Download, save: Save,
  pencil: Pencil, mail: Mail, chat: MessageCircle, send: Send,
};

function SnakeGlyph({ size = 24, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
      <path d="M4 17c0-2 1.6-3 3.5-3S11 15 11 17s1.6 3 3.5 3S18 19 18 17s-1.6-3-3.5-3S11 15 11 13s1.6-3 3.5-3S18 11 18 9s-1.6-3-3.5-3S11 4 11 6"
        stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="16.6" cy="9" r="1.4" fill="currentColor" />
    </svg>
  );
}

function GoldCrown({ size = 24, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
      <path d="M3 8.5 7 12l5-7 5 7 4-3.5V18a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5Z"
        fill="url(#gold)" stroke="#fde68a" strokeWidth="1" strokeLinejoin="round" />
      <circle cx="7" cy="9" r="1.4" fill="#fff7cc" /><circle cx="17" cy="9" r="1.4" fill="#fff7cc" />
      <circle cx="12" cy="4.2" r="1.6" fill="#fff7cc" />
      <defs>
        <linearGradient id="gold" x1="3" y1="4" x2="21" y2="19" gradientUnits="userSpaceOnUse">
          <stop stopColor="#fde68a" /><stop offset="0.5" stopColor="#facc15" /><stop offset="1" stopColor="#f59e0b" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function DiscordGlyph({ size = 18, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M20.32 4.57A19.8 19.8 0 0 0 15.45 3l-.37.75a14.6 14.6 0 0 1 4.2 1.6 13.9 13.9 0 0 0-13.06 0 14.7 14.7 0 0 1 4.2-1.6L10.05 3a19.7 19.7 0 0 0-4.87 1.57C2.1 9.13 1.33 13.57 1.72 17.95A19.9 19.9 0 0 0 7.7 21l.75-1.2a12.9 12.9 0 0 1-2.1-1l.52-.4a14.2 14.2 0 0 0 12.26 0l.52.4c-.66.4-1.37.73-2.1 1l.74 1.2a19.9 19.9 0 0 0 5.99-3.05c.46-5.08-.78-9.48-3.96-13.38ZM8.7 15.33c-1.18 0-2.15-1.08-2.15-2.4 0-1.33.95-2.41 2.15-2.41 1.2 0 2.17 1.09 2.15 2.4 0 1.33-.95 2.41-2.15 2.41Zm6.6 0c-1.18 0-2.15-1.08-2.15-2.4 0-1.33.95-2.41 2.15-2.41 1.2 0 2.16 1.09 2.14 2.4 0 1.33-.94 2.41-2.14 2.41Z" />
    </svg>
  );
}

export function TelegramGlyph({ size = 18, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M21.8 4.3 18.7 19c-.24 1.04-.86 1.3-1.73.81l-4.78-3.52-2.3 2.22c-.26.26-.47.47-.96.47l.34-4.86L18.1 6.1c.38-.34-.08-.53-.6-.2L6.2 12.87l-4.7-1.47c-1.02-.32-1.04-1.02.21-1.5l18.4-7.1c.85-.31 1.6.2 1.33 1.5Z" />
    </svg>
  );
}

export function Icon({ name, size = 20, className, strokeWidth = 1.8 }: { name?: string | null; size?: number; className?: string; strokeWidth?: number }) {
  if (name === 'snake') return <SnakeGlyph size={size} className={className} />;
  if (name === 'crown-gold') return <GoldCrown size={size} className={className} />;
  const Cmp = registry[name ?? 'crown'] ?? Crown;
  return <Cmp size={size} className={className} strokeWidth={strokeWidth} aria-hidden="true" />;
}

/** Знак бренда: корона + буква K в воксельной сетке. */
export function LogoMark({ size = 38, className = '', style }: { size?: number; className?: string; style?: CSSProperties }) {
  return (
    <span
      className={`relative inline-grid shrink-0 place-items-center rounded-[0.7rem] ${className}`}
      style={{
        width: size,
        height: size,
        background: 'linear-gradient(150deg, #a855f7 0%, #7c3aed 45%, #4c1d95 100%)',
        boxShadow: '0 0 0 1px rgba(255,255,255,0.18) inset, 0 8px 24px -8px rgba(168,85,247,0.9)',
        ...style,
      }}
    >
      <svg width={size * 0.62} height={size * 0.62} viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <path d="M5 9.5 9.5 12 12 6l4 6 4-6 2.5 6L27 9.5V24a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V9.5Z" fill="rgba(255,255,255,0.22)" />
        <path d="M9 10h3.4v4.2l4-4.2h4.2l-4.1 4.1 4.4 8.9h-4l-2.7-5.6-1.8 1.8V23H9V10Z" fill="#fff" />
      </svg>
      <span className="pointer-events-none absolute inset-x-1 -top-[3px] h-1 rounded-full bg-white/40 blur-[3px]" />
    </span>
  );
}

export function Wordmark({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex items-baseline gap-1 select-none">
      <span
        className="font-display text-[1.02rem] font-extrabold uppercase leading-none tracking-[-0.04em] text-white"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        KING<span className="text-neon">GRIEF</span>
      </span>
      {!compact && (
        <span className="ml-1 hidden rounded border border-gold/30 bg-gold/10 px-1.5 py-0.5 font-mono text-[0.5rem] font-bold uppercase tracking-widest text-gold sm:inline">
          S1
        </span>
      )}
    </span>
  );
}
