import { z } from 'zod';

export const nicknameSchema = z
  .string()
  .trim()
  .min(3, 'Минимум 3 символа')
  .max(16, 'Максимум 16 символов')
  .regex(/^[A-Za-z0-9_]+$/, 'Только латиница, цифры и _');

export const cartItemSchema = z.object({
  productId: z.string().min(1),
  quantity: z.number().int().min(1).max(99),
});

export const createOrderSchema = z.object({
  nickname: nicknameSchema,
  email: z.string().trim().email('Некорректный email').max(120).optional().or(z.literal('')),
  items: z.array(cartItemSchema).min(1, 'Корзина пуста').max(30),
  promoCode: z.string().trim().max(32).optional().nullable(),
  agree: z.literal(true, { errorMap: () => ({ message: 'Подтвердите согласие с правилами' }) }),
  note: z.string().trim().max(300).optional(),
});

export const promoCheckSchema = z.object({
  code: z.string().trim().min(1).max(32),
  items: z.array(cartItemSchema).min(1),
});

export const reviewSchema = z.object({
  nickname: nicknameSchema,
  rating: z.number().int().min(1).max(5),
  text: z.string().trim().min(12, 'Расскажите чуть подробнее').max(600),
});

export const loginSchema = z.object({
  username: z.string().trim().min(2).max(64),
  password: z.string().min(4).max(200),
  totp: z.string().trim().max(8).optional(),
});

export const productSchema = z.object({
  id: z.string().trim().min(2).max(64),
  kind: z.enum(['rank', 'case', 'relic', 'other']),
  name: z.string().trim().min(1).max(80),
  subtitle: z.string().trim().max(160).optional(),
  description: z.string().trim().max(2000).optional(),
  price: z.number().int().min(0).max(1_000_000),
  oldPrice: z.number().int().min(0).max(1_000_000).nullable().optional(),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
  icon: z.string().trim().max(40),
  badge: z.string().trim().max(24).nullable().optional(),
  features: z.array(z.string().trim().max(160)).max(20).optional(),
  meta: z.record(z.unknown()).optional(),
  sort: z.number().int().min(0).max(9999).optional(),
  active: z.boolean().optional(),
});

export const promoInputSchema = z.object({
  code: z.string().trim().min(3).max(32).regex(/^[A-Za-z0-9_-]+$/, 'Только латиница, цифры, - и _'),
  type: z.enum(['percent', 'fixed']),
  value: z.number().int().min(1).max(100_000),
  minTotal: z.number().int().min(0).max(1_000_000).optional(),
  maxDiscount: z.number().int().min(0).max(1_000_000).nullable().optional(),
  usageLimit: z.number().int().min(1).max(1_000_000).nullable().optional(),
  categories: z.array(z.string()).nullable().optional(),
  expiresAt: z.string().nullable().optional(),
  active: z.boolean().optional(),
});

export const caseItemSchema = z.object({
  id: z.number().int().optional(),
  caseId: z.string().min(1),
  name: z.string().trim().min(1).max(100),
  rarity: z.enum(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']),
  chance: z.number().min(0).max(100),
  icon: z.string().trim().max(40).default('coins'),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/).default('#94A3B8'),
  sort: z.number().int().min(0).max(999).default(0),
});

export function firstError(error: z.ZodError): string {
  return error.issues[0]?.message ?? 'Некорректные данные';
}
