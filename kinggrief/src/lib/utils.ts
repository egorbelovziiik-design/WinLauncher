import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatPrice(value: number): string {
  return `${new Intl.NumberFormat('ru-RU').format(Math.round(value))} ₽`;
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('ru-RU').format(value);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'только что';
  if (min < 60) return `${min} мин назад`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours} ч назад`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} дн назад`;
  return formatDate(iso);
}

export function maskNickname(nick: string): string {
  if (nick.length <= 3) return nick[0] + '**';
  return nick.slice(0, 2) + '***' + nick.slice(-2);
}

export function discountPercent(price: number, oldPrice: number | null): number | null {
  if (!oldPrice || oldPrice <= price) return null;
  return Math.round(((oldPrice - price) / oldPrice) * 100);
}

export const statusLabels: Record<string, string> = {
  pending_payment: 'Ожидает оплаты',
  paid: 'Оплачен',
  delivering: 'Выдаётся',
  delivered: 'Выдан',
  cancelled: 'Отменён',
  failed: 'Ошибка',
  pending: 'В очереди',
  sent: 'Отправлен',
  manual: 'Вручную',
};

export const statusColors: Record<string, string> = {
  pending_payment: '#F59E0B',
  paid: '#3B82F6',
  delivering: '#A855F7',
  delivered: '#22C55E',
  cancelled: '#6B7280',
  failed: '#EF4444',
  pending: '#F59E0B',
  sent: '#A855F7',
  manual: '#94A3B8',
};
