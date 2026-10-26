export type ProductKind = 'rank' | 'case' | 'relic' | 'other';

export type RankMeta = {
  tier: number;
  inherits?: string;
  rainbow?: boolean;
  crown?: boolean;
  top?: boolean;
};

export type CaseMeta = {
  group: string;
  groupName: string;
  quantity: number;
  unitPrice: number;
};

export type RelicMeta = { amount: number };

export type OtherMeta = {
  service:
    | 'unban'
    | 'unmute'
    | 'unban_ip'
    | 'unmute_ip';
  requiresNickname: boolean;
};

export type ProductMeta = Partial<RankMeta & CaseMeta & RelicMeta & OtherMeta> & {
  [key: string]: unknown;
};

export type Product = {
  id: string;
  kind: ProductKind;
  name: string;
  subtitle: string;
  description: string;
  price: number;
  oldPrice: number | null;
  color: string;
  icon: string;
  badge: string | null;
  features: string[];
  meta: ProductMeta;
  sort: number;
  active: boolean;
};

export type CaseItem = {
  id?: number;
  caseId: string;
  name: string;
  /** Что именно выпадает и что это даёт игроку. */
  description: string;
  rarity: Rarity;
  chance: number;
  icon: string;
  color: string;
  /** Ссылка на страницу товара, если награда — привилегия или пакет риликов. */
  link?: string | null;
  sort: number;
};

export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'mythic';

export type CaseGroup = {
  id: string;
  name: string;
  description: string;
  color: string;
  icon: string;
  sort: number;
  active: boolean;
};

export type ServerPromoReward = {
  enabled: boolean;
  coins: number;
  relics: number;
  caseId: string;
  caseQty: number;
  rankId: string;
};

export type Promo = {
  code: string;
  type: 'percent' | 'fixed';
  value: number;
  minTotal: number;
  maxDiscount: number | null;
  usageLimit: number | null;
  usedCount: number;
  categories: string[] | null;
  expiresAt: string | null;
  active: boolean;
  serverReward?: ServerPromoReward;
};

export type OrderStatus = 'pending_payment' | 'paid' | 'delivering' | 'delivered' | 'cancelled' | 'failed';

export type OrderItem = {
  productId: string;
  name: string;
  kind: ProductKind;
  quantity: number;
  unitPrice: number;
  total: number;
  meta?: ProductMeta;
};

export type Order = {
  id: string;
  publicId: string;
  nickname: string;
  email: string | null;
  status: OrderStatus;
  subtotal: number;
  discount: number;
  total: number;
  promoCode: string | null;
  provider: string;
  paymentId: string | null;
  paymentUrl: string | null;
  items: OrderItem[];
  note: string | null;
  isDemo: boolean;
  /** Аккаунт игрока, если заказ оформлен из авторизованной сессии. */
  userId: number | null;
  createdAt: string;
  paidAt: string | null;
  deliveredAt: string | null;
};

export type Delivery = {
  id: string;
  orderId: string;
  productId: string;
  nickname: string;
  command: string;
  status: 'pending' | 'sent' | 'delivered' | 'failed' | 'manual';
  attempts: number;
  lastError: string | null;
  createdAt: string;
  deliveredAt: string | null;
};

export type SettingsMap = Record<string, string>;

export type AdminUser = {
  id: number;
  username: string;
  role: 'owner' | 'admin' | 'viewer';
  totpEnabled: boolean;
};

export type AuditEntry = {
  id: number;
  actor: string;
  action: string;
  details: string;
  ip: string | null;
  createdAt: string;
};

/** Зарегистрированный игрок (обычный пользователь сайта). */
export type PublicUser = {
  id: number;
  username: string;
  email: string;
  createdAt: string;
  lastLoginAt: string | null;
};

export type UserRecord = PublicUser & { passwordHash: string };

export type Review = {
  id: number;
  nickname: string;
  rating: number;
  text: string;
  status: 'pending' | 'approved' | 'rejected';
  createdAt: string;
};

export const KIND_LABELS: Record<ProductKind, string> = {
  rank: 'Привилегия',
  case: 'Кейс',
  relic: 'Рилики',
  other: 'Услуга',
};

export const RARITY_META: Record<Rarity, { label: string; color: string; weight: number }> = {
  common: { label: 'Обычный', color: '#94a3b8', weight: 1 },
  uncommon: { label: 'Необычный', color: '#22c55e', weight: 2 },
  rare: { label: 'Редкий', color: '#3b82f6', weight: 3 },
  epic: { label: 'Эпический', color: '#a855f7', weight: 4 },
  legendary: { label: 'Легендарный', color: '#f59e0b', weight: 5 },
  mythic: { label: 'Мифический', color: '#ef4444', weight: 6 },
};
