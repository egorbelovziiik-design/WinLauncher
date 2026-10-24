import type { CaseGroup, CaseItem, Product, SettingsMap } from './types';

/**
 * Каталог по умолчанию. При первом запуске он записывается в базу,
 * после чего любые изменения делаются в админ-панели (/admin).
 * Цены PEGAS / COBRA / BUNNY / RABBIT зафиксированы по требованию бренда.
 */
export const DEFAULT_SETTINGS: SettingsMap = {
  site_name: 'KINGGRIEF',
  site_tagline: 'ТВОЯ ИМПЕРИЯ ГРИФА',
  site_description:
    'Открой новые возможности на KingGrief! Покупай привилегии, получай рилики, открывай кейсы и покоряй игровой мир.',
  hero_title: 'KINGGRIEF',
  hero_subtitle: 'ТВОЯ ИМПЕРИЯ ГРИФА НАЧИНАЕТСЯ ЗДЕСЬ.',
  hero_note: 'Вход по лицензии и пиратке · 24/7 · без лагов',
  server_ip: 'play.kinggrief.net',
  server_version: '1.16.5 — 1.21.4',
  discord_url: 'https://discord.gg/kinggrief',
  telegram_url: 'https://t.me/kinggrief',
  support_email: 'support@kinggrief.net',
  rules_url: '/rules',
  online_base: '0',
  online_peak: '0',
  online_updated_at: '',
  banner_title: 'СЕЗОН I · ВОСХОЖДЕНИЕ КОРОЛЯ',
  banner_subtitle: 'Обновлённые кейсы, рилики и привилегии сезона',
  banner_href: '/privileges',
  banner_active: 'true',
  announcement: 'Сезон I уже в игре — заходи на play.kinggrief.net',
  admin_note: '',
  relic_pricing: JSON.stringify({
    minAmount: 1,
    maxAmount: 100000,
    minPrice: 5,
    points: [
      [1000, 19],
      [2500, 39],
      [5000, 69],
      [10000, 119],
      [15000, 169],
      [25000, 249],
      [50000, 449],
      [75000, 649],
      [100000, 799],
    ],
  }),
  delivery_commands: JSON.stringify({
    rank: 'lp user {player} parent add {rank}',
    rank_replace: 'lp user {player} parent set {rank}',
    relic: 'relics add {player} {amount}',
    case: 'crates key {player} {case} {amount}',
    unban: 'unban {player}',
    unmute: 'unmute {player}',
    unban_ip: 'unbanip {player}',
    unmute_ip: 'unmuteip {player}',
  }),
  kassa_provider: 'mock',
  kassa_test_mode: 'true',
  kassa_label: 'KINGGRIEF',
  maintenance: 'false',
  online_mode: 'bridge',
  reviews_enabled: 'true',
};

/** Человекочитаемые заголовки настраиваемых возможностей привилегий. */
export const RANKS: Array<{
  id: string;
  name: string;
  price: number;
  oldPrice: number;
  color: string;
  icon: string;
  subtitle: string;
  description: string;
  features: string[];
  meta: Record<string, unknown>;
}> = [
  {
    id: 'pegas',
    name: 'PEGAS',
    price: 19,
    oldPrice: 39,
    color: '#22C55E',
    icon: 'feather',
    subtitle: 'Первый шаг к трону',
    description:
      'Стартовая привилегия для тех, кто только пришёл на сервер. Забирает префикс, стартовый набор и доступ к базовым удобствам.',
    features: [
      'Префикс PEGAS в чате',
      'Стартовый набор ресурсов при входе',
      '2 точки /sethome',
      'Цветной ник в списке игроков',
    ],
    meta: { tier: 1 },
  },
  {
    id: 'cobra',
    name: 'COBRA',
    price: 39,
    oldPrice: 59,
    color: '#84CC16',
    icon: 'snake',
    subtitle: 'Тихий укус',
    description:
      'COBRA открывает быстрый доступ к экономике и снимает часть ограничений. Наследует все возможности PEGAS.',
    features: [
      'Всё из PEGAS',
      'Префикс COBRA в чате',
      '4 точки /sethome',
      'Доступ к /hat',
      'Ускоренное восстановление после смерти',
    ],
    meta: { tier: 2, inherits: 'PEGAS' },
  },
  {
    id: 'bunny',
    name: 'BUNNY',
    price: 59,
    oldPrice: 89,
    color: '#EC4899',
    icon: 'rabbit',
    subtitle: 'Прыжок выше',
    description:
      'Лёгкая привилегия с акцентом на мобильность и косметику. Наследует возможности COBRA.',
    features: [
      'Всё из COBRA',
      'Префикс BUNNY в чате',
      '6 точек /sethome',
      'Косметические частицы при движении',
      'Доступ к /back после смерти',
    ],
    meta: { tier: 3, inherits: 'COBRA' },
  },
  {
    id: 'rabbit',
    name: 'RABBIT',
    price: 89,
    oldPrice: 129,
    color: '#38BDF8',
    icon: 'rabbit',
    subtitle: 'Скорость как стиль',
    description:
      'RABBIT добавляет ускорение в лобби и расширяет набор команд телепортации. Наследует BUNNY.',
    features: [
      'Всё из BUNNY',
      'Префикс RABBIT в чате',
      '8 точек /sethome',
      'Ускорение в хабе',
      'Свой цвет чата',
    ],
    meta: { tier: 4, inherits: 'BUNNY' },
  },
  {
    id: 'vampire',
    name: 'VAMPIRE',
    price: 99,
    oldPrice: 159,
    color: '#EF4444',
    icon: 'droplet',
    subtitle: 'Кровь и власть',
    description:
      'Агрессивная привилегия для PvP. Даёт визуальные эффекты, доступ к закрытому чату и усиленный набор в бою.',
    features: [
      'Всё из RABBIT',
      'Префикс VAMPIRE в чате',
      '10 точек /sethome',
      'Кровавые частицы и след',
      'Доступ к закрытому чату',
      'Приоритетный вход при полном сервере',
    ],
    meta: { tier: 5, inherits: 'RABBIT' },
  },
  {
    id: 'god',
    name: 'GOD',
    price: 149,
    oldPrice: 229,
    color: '#FACC15',
    icon: 'zap',
    subtitle: 'Сила на твоей стороне',
    description:
      'Золотая привилегия среднего уровня. Открывает кейсы раз в сутки и золотые косметические эффекты.',
    features: [
      'Всё из VAMPIRE',
      'Золотой префикс GOD',
      '12 точек /sethome',
      'Ежедневный бонус к риликам',
      'Золотые частицы и нимб',
      'Доступ к /feed',
    ],
    meta: { tier: 6, inherits: 'VAMPIRE' },
  },
  {
    id: 'king',
    name: 'KING',
    price: 199,
    oldPrice: 299,
    color: '#A855F7',
    icon: 'crown',
    subtitle: 'Корона примеряет тебя',
    description:
      'Флагманская привилегия для игроков без персонала. Королевская косметика, расширенные лимиты и приоритет во всём.',
    features: [
      'Всё из GOD',
      'Фиолетовый префикс KING',
      '14 точек /sethome',
      'Собственный титул в чате',
      'Королевский след и частицы',
      'Доступ к /workbench и /enderchest',
    ],
    meta: { tier: 7, inherits: 'GOD' },
  },
  {
    id: 'dmoder',
    name: 'D.MODER',
    price: 249,
    oldPrice: 399,
    color: '#14B8A6',
    icon: 'shield',
    subtitle: 'Порядок начинается с тебя',
    description:
      'Первый уровень команды сервера. Доступ к базовым инструментам модерации и отдельному чату персонала.',
    features: [
      'Всё из KING',
      'Префикс D.MODER',
      'Доступ к чату персонала',
      'Базовые команды модерации по регламенту',
      'Отметка в списке игроков',
    ],
    meta: { tier: 8, inherits: 'KING' },
  },
  {
    id: 'dstmoder',
    name: 'D.ST.MODER',
    price: 349,
    oldPrice: 499,
    color: '#3B82F6',
    icon: 'shield-check',
    subtitle: 'Старший над порядком',
    description:
      'Старший модератор. Расширенные инструменты наблюдения и право вести жалобы игроков.',
    features: [
      'Всё из D.MODER',
      'Префикс D.ST.MODER',
      'Расширенные команды модерации',
      'Доступ к очереди жалоб',
      'Собственный цвет в чате персонала',
    ],
    meta: { tier: 9, inherits: 'D.MODER' },
  },
  {
    id: 'dglmoder',
    name: 'D.GLMODER',
    price: 449,
    oldPrice: 599,
    color: '#8B5CF6',
    icon: 'shield-half',
    subtitle: 'Глобальный порядок',
    description:
      'Глобальный модератор. Курирует несколько миров и следит за соблюдением правил на всех режимах.',
    features: [
      'Всё из D.ST.MODER',
      'Префикс D.GLMODER',
      'Инструменты наблюдения во всех мирах',
      'Право выдавать базовые наказания',
      'Доступ к служебному транспорту',
    ],
    meta: { tier: 10, inherits: 'D.ST.MODER' },
  },
  {
    id: 'dmladmin',
    name: 'D.ML.ADMIN',
    price: 599,
    oldPrice: 799,
    color: '#F97316',
    icon: 'wrench',
    subtitle: 'Правая рука короны',
    description:
      'Младший администратор. Управление событиями, кейсами и мини-играми, плюс все возможности модерации.',
    features: [
      'Всё из D.GLMODER',
      'Префикс D.ML.ADMIN',
      'Запуск ивентов и мини-игр',
      'Доступ к настройкам ивент-арены',
      'Расширенные наказания',
    ],
    meta: { tier: 11, inherits: 'D.GLMODER' },
  },
  {
    id: 'dadmin',
    name: 'D.ADMIN',
    price: 799,
    oldPrice: 1099,
    color: '#DC2626',
    icon: 'gavel',
    subtitle: 'Полномочия без компромиссов',
    description:
      'Администратор сервера. Полный набор инструментов управления игровой частью и персоналом.',
    features: [
      'Всё из D.ML.ADMIN',
      'Красный префикс D.ADMIN',
      'Управление составом модерации',
      'Доступ к экономике и админ-складу',
      'Собственный канал в Discord',
    ],
    meta: { tier: 12, inherits: 'D.ML.ADMIN' },
  },
  {
    id: 'opka',
    name: 'OPKA',
    price: 999,
    oldPrice: 1399,
    color: '#C026D3',
    icon: 'flame',
    subtitle: 'Неоновый ультиматум',
    description:
      'Легендарная привилегия для избранных. Максимум косметики, максимальные лимиты, неоновое свечение вокруг ника.',
    features: [
      'Всё из D.ADMIN',
      'Неоновый префикс OPKA',
      '20 точек /sethome',
      'Уникальный неоновый ореол',
      'Доступ к закрытым ивентам',
      'Личный сундук-хранилище',
    ],
    meta: { tier: 13, inherits: 'D.ADMIN' },
  },
  {
    id: 'downer',
    name: 'D.OWNER',
    price: 1499,
    oldPrice: 1999,
    color: '#FACC15',
    icon: 'crown-gold',
    subtitle: 'Максимальная привилегия',
    description:
      'Владелец империи. Абсолютная вершина KingGrief: радужный префикс, 3D-корона, золотые частицы и максимальные лимиты на всё.',
    features: [
      'Всё из OPKA',
      'Радужный префикс D.OWNER',
      '3D-корона над персонажем',
      'Безлимитные /sethome',
      'Золотые частицы и аура владельца',
      'Доступ к закрытому совету сервера',
      'Персональный менеджер поддержки',
    ],
    meta: { tier: 14, inherits: 'OPKA', rainbow: true, crown: true, top: true },
  },
];

export const OTHER_PRODUCTS: Array<{
  id: string;
  name: string;
  price: number;
  oldPrice: number;
  color: string;
  icon: string;
  subtitle: string;
  description: string;
  features: string[];
  meta: Record<string, unknown>;
}> = [
  {
    id: 'unban',
    name: 'Разбан',
    price: 99,
    oldPrice: 149,
    color: '#22C55E',
    icon: 'unlock',
    subtitle: 'Сними блокировку аккаунта',
    description:
      'Снимает бан с указанного никнейма. Доступно только для наказаний, которые можно выкупить по правилам сервера. Читы, дюпы и обход бана не снимаются.',
    features: ['Снятие бана с аккаунта', 'Выдача в течение 5 минут', 'Проверка нарушения перед выдачей'],
    meta: { service: 'unban', requiresNickname: true },
  },
  {
    id: 'unmute',
    name: 'Размут',
    price: 49,
    oldPrice: 79,
    color: '#38BDF8',
    icon: 'message-square-off',
    subtitle: 'Верни себе голос',
    description:
      'Снимает мут с никнейма. Не распространяется на наказания за угрозы, травлю и рекламу — такие случаи рассматривает только администрация.',
    features: ['Снятие мута', 'Выдача в течение 5 минут', 'Кроме нарушений из списка исключений'],
    meta: { service: 'unmute', requiresNickname: true },
  },
  {
    id: 'unban-ip',
    name: 'Разбан по IP',
    price: 199,
    oldPrice: 279,
    color: '#A855F7',
    icon: 'network',
    subtitle: 'Разблокировка сети',
    description:
      'Снимает IP-бан с адреса, с которого играл указанный никнейм. Если адрес заблокирован за бот-атаку — сначала свяжитесь с поддержкой.',
    features: ['Снятие IP-бана', 'Проверка адреса администрацией', 'Выдача в течение 30 минут'],
    meta: { service: 'unban_ip', requiresNickname: true },
  },
  {
    id: 'unmute-ip',
    name: 'Размут по IP',
    price: 99,
    oldPrice: 149,
    color: '#F59E0B',
    icon: 'volume-x',
    subtitle: 'Тишина снимается',
    description:
      'Снимает IP-мут. Услуга доступна, если блокировка не связана с обходом наказания или рекламой сторонних проектов.',
    features: ['Снятие IP-мута', 'Проверка истории наказаний', 'Выдача в течение 30 минут'],
    meta: { service: 'unmute_ip', requiresNickname: true },
  },
];

export const RELIC_AMOUNTS = [1000, 2500, 5000, 10000, 15000, 25000, 50000, 75000, 100000];
export const RELIC_PRICES = [19, 39, 69, 119, 169, 249, 449, 649, 799];
export const RELIC_OLD_PRICES = [29, 59, 99, 169, 239, 349, 649, 899, 1099];

export const CASE_GROUPS: CaseGroup[] = [
  {
    id: 'super',
    name: 'Супер кейс',
    description:
      'Фиолетовый сундук с магическими частицами. Базовые награды, ресурсы и шанс на среднюю привилегию.',
    color: '#A855F7',
    icon: 'box',
    sort: 1,
    active: true,
  },
  {
    id: 'legendary',
    name: 'Легендарный кейс',
    description:
      'Золотой сундук с огненными искрами. Повышенный шанс на привилегии GOD и D.MODER и крупные пачки риликов.',
    color: '#FACC15',
    icon: 'sparkles',
    sort: 2,
    active: true,
  },
  {
    id: 'mega',
    name: 'Мега кейс',
    description:
      'Красно-чёрный сундук с электрическими разрядами. Здесь лежат OPKA, D.ADMIN и 50 000 риликов.',
    color: '#EF4444',
    icon: 'flame',
    sort: 3,
    active: true,
  },
];

const RAW_CASE_ITEMS: Array<Omit<CaseItem, 'description' | 'link'>> = [
  // Super — 100%
  { caseId: 'super', name: '100 риликов', rarity: 'common', chance: 30, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'super', name: '250 риликов', rarity: 'common', chance: 22, icon: 'coins', color: '#94A3B8', sort: 2 },
  { caseId: 'super', name: '500 риликов', rarity: 'uncommon', chance: 16, icon: 'coins', color: '#22C55E', sort: 3 },
  { caseId: 'super', name: '1 000 риликов', rarity: 'rare', chance: 12, icon: 'coins', color: '#3B82F6', sort: 4 },
  { caseId: 'super', name: 'Легендарный кейс ×1', rarity: 'rare', chance: 8, icon: 'box', color: '#FACC15', sort: 5 },
  { caseId: 'super', name: 'Привилегия BUNNY', rarity: 'epic', chance: 6, icon: 'rabbit', color: '#EC4899', sort: 6 },
  { caseId: 'super', name: '2 500 риликов', rarity: 'epic', chance: 3.8, icon: 'coins', color: '#A855F7', sort: 7 },
  { caseId: 'super', name: 'Привилегия KING', rarity: 'legendary', chance: 2, icon: 'crown', color: '#A855F7', sort: 8 },
  { caseId: 'super', name: '10 000 риликов', rarity: 'mythic', chance: 0.2, icon: 'coins', color: '#EF4444', sort: 9 },

  // Legendary — 100%
  { caseId: 'legendary', name: '500 риликов', rarity: 'common', chance: 30, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'legendary', name: '1 000 риликов', rarity: 'uncommon', chance: 22, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'legendary', name: '2 500 риликов', rarity: 'rare', chance: 16, icon: 'coins', color: '#3B82F6', sort: 3 },
  { caseId: 'legendary', name: 'Привилегия RABBIT', rarity: 'epic', chance: 12, icon: 'rabbit', color: '#38BDF8', sort: 4 },
  { caseId: 'legendary', name: '5 000 риликов', rarity: 'epic', chance: 8, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'legendary', name: 'Привилегия GOD', rarity: 'legendary', chance: 6, icon: 'zap', color: '#FACC15', sort: 6 },
  { caseId: 'legendary', name: 'Привилегия D.MODER', rarity: 'legendary', chance: 4, icon: 'shield', color: '#14B8A6', sort: 7 },
  { caseId: 'legendary', name: '15 000 риликов', rarity: 'mythic', chance: 2, icon: 'coins', color: '#EF4444', sort: 8 },

  // Mega — 100%
  { caseId: 'mega', name: '1 000 риликов', rarity: 'common', chance: 28, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'mega', name: '2 500 риликов', rarity: 'uncommon', chance: 22, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'mega', name: '5 000 риликов', rarity: 'rare', chance: 16, icon: 'coins', color: '#3B82F6', sort: 3 },
  { caseId: 'mega', name: 'Привилегия VAMPIRE', rarity: 'epic', chance: 12, icon: 'droplet', color: '#EF4444', sort: 4 },
  { caseId: 'mega', name: '10 000 риликов', rarity: 'epic', chance: 8, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'mega', name: 'Привилегия D.ADMIN', rarity: 'legendary', chance: 6, icon: 'gavel', color: '#DC2626', sort: 6 },
  { caseId: 'mega', name: 'Привилегия OPKA', rarity: 'legendary', chance: 4, icon: 'flame', color: '#C026D3', sort: 7 },
  { caseId: 'mega', name: '50 000 риликов', rarity: 'mythic', chance: 3, icon: 'coins', color: '#EF4444', sort: 8 },
  { caseId: 'mega', name: 'Привилегия D.OWNER', rarity: 'mythic', chance: 1, icon: 'crown-gold', color: '#FACC15', sort: 9 },
];

/**
 * Награды кейсов с человеческим описанием: что именно выпало и что это даёт.
 * Привилегии получают ссылку на свою страницу, рилики — на пакеты валюты.
 */
export const CASE_ITEMS: CaseItem[] = RAW_CASE_ITEMS.map((item) => {
  const rank = RANKS.find((r) => item.name === `Привилегия ${r.name}`);
  if (rank) {
    return {
      ...item,
      description: `${rank.subtitle}. ${rank.features.slice(0, 3).join(' · ')}.`,
      link: `/privileges/${rank.id}`,
    };
  }
  if (/рилик/.test(item.name)) {
    return {
      ...item,
      description: `${item.name} игровой валюты «рилики» — зачисляются на баланс ника сразу после открытия.`,
      link: '/relics',
    };
  }
  return {
    ...item,
    description: 'Ключ от кейса — ещё одна попытка испытать удачу, выдаётся в игре.',
    link: '/cases',
  };
});

function buildProducts(): Product[] {
  const ranks: Product[] = RANKS.map((r, i) => ({
    id: `rank-${r.id}`,
    kind: 'rank',
    name: r.name,
    subtitle: r.subtitle,
    description: r.description,
    price: r.price,
    oldPrice: r.oldPrice,
    color: r.color,
    icon: r.icon,
    badge: r.meta.top ? 'MAX' : i === 0 ? 'START' : null,
    features: r.features,
    meta: { ...r.meta, rankId: r.id },
    sort: i + 1,
    active: true,
  }));

  const casePacks: Record<string, Array<[number, number, number]>> = {
    super: [
      [1, 29, 39],
      [5, 119, 145],
      [10, 199, 290],
    ],
    legendary: [
      [1, 49, 69],
      [2, 89, 98],
      [3, 129, 147],
    ],
    mega: [
      [1, 79, 99],
      [2, 139, 158],
      [3, 199, 237],
    ],
  };

  const cases: Product[] = [];
  CASE_GROUPS.forEach((g) => {
    casePacks[g.id].forEach(([qty, price, oldPrice], idx) => {
      cases.push({
        id: `case-${g.id}-${qty}`,
        kind: 'case',
        name: `${g.name} ×${qty}`,
        subtitle: qty === 1 ? g.name : `${g.name} · набор из ${qty}`,
        description: g.description,
        price,
        oldPrice,
        color: g.color,
        icon: g.icon,
        badge: qty === Math.max(...casePacks[g.id].map((p) => p[0])) ? 'ВЫГОДНО' : null,
        features: [`${qty} × ${g.name}`, 'Открывается в игре через /crates', 'Содержимое и шансы — на странице кейса'],
        meta: { group: g.id, groupName: g.name, quantity: qty, unitPrice: Math.round((price / qty) * 100) / 100 },
        sort: 100 + idx + g.sort * 10,
        active: true,
      });
    });
  });

  const relics: Product[] = RELIC_AMOUNTS.map((amount, i) => {
    const price = RELIC_PRICES[i];
    const perK = price / (amount / 1000);
    return {
      id: `relic-${amount}`,
      kind: 'relic',
      name: `${amount.toLocaleString('ru-RU')} риликов`,
      subtitle: `${perK.toFixed(2).replace('.', ',')} ₽ за 1 000`,
      description:
        'Игровая валюта KingGrief. Используется в аукционе, магазине ресурсов и для прокачки. Зачисляется на баланс никнейма сразу после оплаты.',
      price,
      oldPrice: RELIC_OLD_PRICES[i],
      color: amount === 100000 ? '#FACC15' : '#F59E0B',
      icon: 'coins',
      badge: amount === 100000 ? 'МАКС. ВЫГОДА' : i >= 5 ? 'ВЫГОДНО' : null,
      features: ['Мгновенное зачисление', 'Работает на всех режимах', 'Не сгорает между сезонами'],
      meta: { amount },
      sort: 200 + i,
      active: true,
    };
  });

  const others: Product[] = OTHER_PRODUCTS.map((o, i) => ({
    id: `other-${o.id}`,
    kind: 'other',
    name: o.name,
    subtitle: o.subtitle,
    description: o.description,
    price: o.price,
    oldPrice: o.oldPrice,
    color: o.color,
    icon: o.icon,
    badge: null,
    features: o.features,
    meta: o.meta,
    sort: 300 + i,
    active: true,
  }));

  return [...ranks, ...cases, ...relics, ...others];
}

export const PRODUCTS: Product[] = buildProducts();

