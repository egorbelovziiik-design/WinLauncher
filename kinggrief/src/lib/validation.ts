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

export const passwordSchema = z
  .string()
  .min(8, 'Минимум 8 символов')
  .max(200, 'Слишком длинный пароль')
  .regex(/[A-Za-zА-Яа-я]/, 'Добавьте хотя бы одну букву')
  .regex(/\d/, 'Добавьте хотя бы одну цифру');

export const registerSchema = z
  .object({
    username: nicknameSchema,
    email: z.string().trim().email('Некорректный email').max(120),
    password: passwordSchema,
    confirm: z.string().min(1, 'Повторите пароль'),
    agree: z.literal(true, { errorMap: () => ({ message: 'Подтвердите согласие с правилами' }) }),
  })
  .refine((data) => data.password === data.confirm, {
    message: 'Пароли не совпадают',
    path: ['confirm'],
  });

export const userLoginSchema = z.object({
  login: z.string().trim().min(3, 'Укажите никнейм или email').max(120),
  password: z.string().min(1, 'Введите пароль').max(200),
});

export const profileSchema = z
  .object({
    email: z.string().trim().email('Некорректный email').max(120).optional(),
    currentPassword: z.string().max(200).optional(),
    newPassword: z.string().max(200).optional(),
  })
  .refine((data) => !data.newPassword || (data.newPassword.length >= 8 && /\d/.test(data.newPassword)), {
    message: 'Новый пароль: минимум 8 символов и хотя бы одна цифра',
    path: ['newPassword'],
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
  description: z.string().trim().max(300).optional().default(''),
  link: z.string().trim().max(200).nullable().optional(),
  rarity: z.enum(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']),
  chance: z.number().min(0).max(100),
  icon: z.string().trim().max(40).default('coins'),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/).default('#94A3B8'),
  sort: z.number().int().min(0).max(999).default(0),
});

const FIELD_LABELS: Record<string, string> = {
  id: 'Идентификатор',
  kind: 'Тип',
  name: 'Название',
  subtitle: 'Подзаголовок',
  description: 'Описание',
  price: 'Цена',
  oldPrice: 'Старая цена',
  color: 'Цвет',
  icon: 'Иконка',
  badge: 'Бейдж',
  features: 'Возможности',
  meta: 'Метаданные',
  sort: 'Порядок',
  active: 'Активность',
  caseId: 'Кейс',
  chance: 'Шанс',
  rarity: 'Редкость',
  link: 'Ссылка',
  code: 'Код',
  type: 'Тип',
  value: 'Значение',
  minTotal: 'Минимальная сумма',
  maxDiscount: 'Максимальная скидка',
  usageLimit: 'Лимит использований',
  expiresAt: 'Срок действия',
  username: 'Никнейм',
  email: 'Email',
  password: 'Пароль',
  confirm: 'Подтверждение пароля',
  login: 'Никнейм или email',
  currentPassword: 'Текущий пароль',
  newPassword: 'Новый пароль',
  agree: 'Согласие с правилами',
  nickname: 'Никнейм',
  items: 'Товары',
  rating: 'Оценка',
  text: 'Текст',
  note: 'Комментарий',
  settings: 'Настройки',
  product: 'Товар',
  item: 'Награда',
  group: 'Кейс',
  promoCode: 'Промокод',
};

const ZOD_ENGLISH = /^(required|invalid input|expected .*|invalid|string must contain|number must be|too small|too big|invalid email|invalid url)/i;

function humanField(path: Array<string | number>): string {
  const key = path.filter((part) => typeof part === 'string').pop();
  if (!key) return 'Значение';
  return FIELD_LABELS[key] ?? key;
}

/**
 * Сообщение об ошибке для интерфейса на русском: собственные сообщения схемы
 * показываем как есть, а служебные английские тексты Zod переводим в понятную подсказку.
 */
export function firstError(error: z.ZodError): string {
  const issue = error.issues[0];
  if (!issue) return 'Некорректные данные';
  if (issue.message && !ZOD_ENGLISH.test(issue.message.trim())) return issue.message;

  const field = humanField(issue.path);
  switch (issue.code) {
    case 'too_small':
      return `${field}: значение слишком маленькое`;
    case 'too_big':
      return `${field}: значение слишком большое`;
    case 'invalid_type':
      return `Заполните поле «${field}»`;
    case 'invalid_string':
      return `${field}: неверный формат`;
    case 'invalid_enum_value':
    case 'invalid_literal':
      return `${field}: недопустимое значение`;
    default:
      return `${field}: проверьте значение`;
  }
}
