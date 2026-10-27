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
  server_ip: 'kinggrief.pickme.cloud',
  server_version: '1.16.5 — 1.21.11',
  discord_url: 'https://discord.gg/7Cugj4X3eU',
  telegram_url: 'https://t.me/kinggrief',
  support_email: 'support@kinggrief.net',
  rules_url: '/rules',
  online_base: '0',
  online_peak: '0',
  online_updated_at: '',
  banner_title: 'СЕЗОН v5.8 · БЕСПЛАТНЫЙ DRAGON ПО /FREE DRAGON',
  banner_subtitle: '12 3D Кирок, Молот 10×10, Зимний Дворец 3 этажа, Рабочий ТНТ и Кейсы!',
  banner_href: '/privileges',
  banner_active: 'true',
  announcement: 'Зайди на сервер и введи /free dragon для бесплатной привилегии DRAGON!',
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
    case: 'dc givekey {player} {case} {amount}',
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
    id: 'dragon',
    name: 'DRAGON',
    price: 15,
    oldPrice: 29,
    color: '#D946EF',
    icon: 'flame',
    subtitle: 'Стартовый ранг · Бесплатно по /free dragon',
    description:
      'Самая начальная привилегия сервера ниже всех остальных донатов! Её можно получить совершенно бесплатно прямо на сервере командой /free dragon или приобрести на сайте.',
    features: [
      'Бесплатно в игре по команде /free dragon',
      'Анимированный префикс DRAGON в чате и TAB',
      'Боевой стартовый набор /kit dragon',
      '2 точки /sethome и базовый комфорт выживания',
    ],
    meta: { tier: 1, freeCommand: '/free dragon' },
  },
  {
    id: 'pegas',
    name: 'PEGAS',
    price: 19,
    oldPrice: 39,
    color: '#22C55E',
    icon: 'feather',
    subtitle: 'Первый шаг к трону',
    description:
      'Улучшенная стартовая привилегия выше DRAGON. Даёт люкс-префикс PEGAS, расширенный кит и дополнительные точки дома.',
    features: [
      'Всё из DRAGON',
      'Префикс PEGAS в чате и TAB',
      'Расширенный набор /kit pegas',
      '3 точки /sethome и цветной чат',
    ],
    meta: { tier: 2, inherits: 'DRAGON' },
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
      '4 точки /sethome и /kit cobra',
      'Доступ к /hat и /workbench',
    ],
    meta: { tier: 3, inherits: 'PEGAS' },
  },
  {
    id: 'dracula',
    name: 'DRACULA',
    price: 49,
    oldPrice: 79,
    color: '#DC2626',
    icon: 'droplet',
    subtitle: 'Ночной охотник',
    description:
      'Кроваво-алая привилегия перед BUNNY. Открывает вампирский стиль в чате, усиленный набор /kit dracula и /enderchest.',
    features: [
      'Всё из COBRA',
      'Алый префикс DRACULA в чате и TAB',
      '5 точек /sethome и /kit dracula',
      'Доступ к /enderchest (/ec)',
    ],
    meta: { tier: 4, inherits: 'COBRA' },
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
      'Лёгкая привилегия с акцентом на мобильность и косметику. Стоит выше DRACULA и наследует все её возможности.',
    features: [
      'Всё из DRACULA',
      'Розовый префикс BUNNY в чате',
      '6 точек /sethome и /kit bunny',
      'Доступ к /back после смерти и /feed',
    ],
    meta: { tier: 5, inherits: 'DRACULA' },
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
      'RABBIT добавляет ускорение и расширяет набор команд телепортации и PvP-снаряжения. Наследует BUNNY.',
    features: [
      'Всё из BUNNY',
      'Префикс RABBIT в чате',
      '8 точек /sethome и /kit rabbit',
      'Улучшенный /rtp и цветной чат',
    ],
    meta: { tier: 6, inherits: 'BUNNY' },
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
      'Агрессивная привилегия для PvP. Даёт усиленный боевой кит со сферой, расходниками и приоритетный вход.',
    features: [
      'Всё из RABBIT',
      'Префикс VAMPIRE в чате',
      '10 точек /sethome и /kit vampire',
      'Доступ к /heal и /clear',
    ],
    meta: { tier: 7, inherits: 'RABBIT' },
  },
  {
    id: 'tiger',
    name: 'TIGER',
    price: 119,
    oldPrice: 179,
    color: '#FB923C',
    icon: 'flame',
    subtitle: 'Ярость хищника',
    description:
      'Хищная привилегия для активных рейдеров и дуэлянтов. Включает /kit tiger с кастомным снаряжением и починку /repair.',
    features: [
      'Всё из VAMPIRE',
      'Огненный префикс TIGER',
      '11 точек /sethome и /kit tiger',
      'Починка предметов /repair',
    ],
    meta: { tier: 8, inherits: 'VAMPIRE' },
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
      'Золотая привилегия высокого уровня. Открывает полёт /fly на спавне, мощный незеритовый кит и золотой префикс.',
    features: [
      'Всё из TIGER',
      'Золотой префикс GOD',
      '12 точек /sethome и /kit god',
      'Доступ к /fly и расширенному /rtp near',
    ],
    meta: { tier: 9, inherits: 'TIGER' },
  },
  {
    id: 'imperator',
    name: 'IMPERATOR',
    price: 179,
    oldPrice: 269,
    color: '#F43F5E',
    icon: 'crown',
    subtitle: 'Власть Императора',
    description:
      'Величественная императорская привилегия между GOD и KING! Включает элитный /kit imperator с кастомными предметами, талисманом и шалкером.',
    features: [
      'Всё из GOD',
      'Императорский рубиновый префикс IMPERATOR',
      '13 точек /sethome и мощный /kit imperator',
      'Починка всего инвентаря /repair all и /near',
    ],
    meta: { tier: 10, inherits: 'GOD' },
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
      'Королевская привилегия выше IMPERATOR. Королевская косметика, мощнейший PvP-кит с шалкером и приоритет во всём.',
    features: [
      'Всё из IMPERATOR',
      'Королевский префикс KING',
      '14 точек /sethome и /kit king',
      'Полный набор команд комфорта и PvP-расходников',
    ],
    meta: { tier: 11, inherits: 'IMPERATOR' },
  },
  {
    id: 'sponsor',
    name: 'SPONSOR',
    price: 215,
    oldPrice: 319,
    color: '#38BDF8',
    icon: 'gem',
    subtitle: 'Меценат империи',
    description:
      'Топовая игровая привилегия перед веткой ДКП. Эксклюзивный бриллиантовый префикс SPONSOR и богатый кит.',
    features: [
      'Всё из KING',
      'Бриллиантовый префикс SPONSOR',
      '16 точек /sethome и /kit sponsor',
      'Повышенные награды и все мирные команды',
    ],
    meta: { tier: 12, inherits: 'KING' },
  },
  {
    id: 'dhelper',
    name: 'D.HELPER',
    price: 219,
    oldPrice: 339,
    color: '#2DD4BF',
    icon: 'shield',
    subtitle: 'Вход в Донат-Персонал',
    description:
      'Первая ступень Донатной Команды Проекта (ДКП). Открывает префикс D.HELPER и стартовый кит персонала.',
    features: [
      'Всё из SPONSOR',
      'Префикс D.HELPER в чате и TAB',
      'Специальный набор /kit dhelper',
      'Отметка ДКП в списке игроков',
    ],
    meta: { tier: 13, inherits: 'SPONSOR' },
  },
  {
    id: 'dkp',
    name: 'D.KP',
    price: 229,
    oldPrice: 359,
    color: '#E879F9',
    icon: 'shield',
    subtitle: 'Донат Команда Проекта',
    description:
      'Особый ранг ДКП перед D.MODER. Фирменный неоновый префикс D.KP и усиленный боевой комплект /kit dkp.',
    features: [
      'Всё из D.HELPER',
      'Префикс D.KP в чате и TAB',
      'Усиленный боевой кит /kit dkp',
      'Расширенные лимиты приватов и домов',
    ],
    meta: { tier: 14, inherits: 'D.HELPER' },
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
      'Донатный модератор выше D.KP. Доступ к базовым инструментам наблюдения и мощному киту /kit dmoder.',
    features: [
      'Всё из D.KP',
      'Префикс D.MODER',
      'Набор /kit dmoder с кастомной бронёй',
      'Базовые команды наблюдения по регламенту',
    ],
    meta: { tier: 15, inherits: 'D.KP' },
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
      'Старший донат-модератор. Расширенные возможности и усиленный кит /kit dstmoder.',
    features: [
      'Всё из D.MODER',
      'Префикс D.ST.MODER',
      'Мощный боевой /kit dstmoder',
      'Доступ к расширенному /rtp near',
    ],
    meta: { tier: 16, inherits: 'D.MODER' },
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
      'Глобальный донат-модератор. Курирует миры и получает премиальный комплект /kit dglmoder.',
    features: [
      'Всё из D.ST.MODER',
      'Префикс D.GLMODER',
      'Элитный комплект /kit dglmoder',
      'Расширенные возможности во всех мирах',
    ],
    meta: { tier: 17, inherits: 'D.ST.MODER' },
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
      'Младший донат-администратор. Мощный рейд-комплект с кастомным ТНТ, бронёй и сферами.',
    features: [
      'Всё из D.GLMODER',
      'Префикс D.ML.ADMIN',
      'Рейд-набор /kit dmladmin с шалкером',
      'Повышенные лимиты на все системы сервера',
    ],
    meta: { tier: 18, inherits: 'D.GLMODER' },
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
      'Донат-Администратор сервера. Открывает спавнер мировых ивентов /events и двухшалкерный кит /kit dadmin!',
    features: [
      'Всё из D.ML.ADMIN',
      'Красный префикс D.ADMIN',
      'Доступ к запуску мировых ивентов и боссов (/events)',
      'Два полных шалкера ресурсов в /kit dadmin',
    ],
    meta: { tier: 19, inherits: 'D.ML.ADMIN' },
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
      'Легендарная привилегия для избранных. Доступ к /gm, запуску ивентов /events и 20 точкам дома.',
    features: [
      'Всё из D.ADMIN',
      'Золото-неоновый префикс OPKA',
      '20 точек /sethome и /kit opka (2 шалкера + кастом сет)',
      'Доступ к /gm и управлению ивентами /events',
    ],
    meta: { tier: 20, inherits: 'D.ADMIN' },
  },
  {
    id: 'downer',
    name: 'D.OWNER',
    price: 1499,
    oldPrice: 1999,
    color: '#FACC15',
    icon: 'crown-gold',
    subtitle: 'Максимальная донат-привилегия',
    description:
      'Вершина донат-привилегий KingGrief: королевский префикс D.OWNER, безлимитные дома, /events, /gm и максимальный донат-кит.',
    features: [
      'Всё из OPKA',
      'Люкс-префикс D.OWNER в стиле OWNER',
      'Безлимитные /sethome и топовый /kit downer',
      'Запуск любых боссов и ивентов (/events)',
      'Максимальный приоритет на всём сервере',
    ],
    meta: { tier: 21, inherits: 'OPKA', rainbow: true, crown: true, top: true },
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
    id: 'donate',
    name: 'Донат-кейс',
    description:
      'Классический сундук с привилегиями от PEGAS до D.ADMIN. Честные шансы и гарантированная привилегия при каждом открытии.',
    color: '#A855F7',
    icon: 'crown',
    sort: 1,
    active: true,
  },
  {
    id: 'sdonate',
    name: 'Супер Донат-кейс',
    description:
      'Усиленный донат-сундук без начальных групп: только старшие привилегии от GOD до D.OWNER.',
    color: '#EC4899',
    icon: 'sparkles',
    sort: 2,
    active: true,
  },
  {
    id: 'legendary',
    name: 'Легендарный кейс',
    description:
      'Золотой сундук с огненными искрами. Повышенный шанс на привилегии GOD, KING, D.MODER и крупные пачки риликов.',
    color: '#FACC15',
    icon: 'trophy',
    sort: 3,
    active: true,
  },
  {
    id: 'riliks',
    name: 'Кейс с риликами',
    description:
      'Чистая донат-валюта сервера: от 250 до 50 000 риликов для магазина ресурсов, сфер и прокачки.',
    color: '#F59E0B',
    icon: 'coins',
    sort: 4,
    active: true,
  },
  {
    id: 'ditems',
    name: 'Кейс с донат-вещами',
    description:
      'Боевой арсенал грифа: сферы, талисманы крушителя, трапки, пласты и зачарованная броня.',
    color: '#EF4444',
    icon: 'swords',
    sort: 5,
    active: true,
  },
  {
    id: 'kits',
    name: 'Кейс с китами',
    description:
      'Готовые наборы снаряжения старших привилегий — от кита KING до полного комплекта D.OWNER.',
    color: '#3B82F6',
    icon: 'shield',
    sort: 6,
    active: true,
  },
  {
    id: 'money',
    name: 'Денежный кейс',
    description:
      'Игровая валюта для аукциона и торговли с игроками: пачки от 50 000$ до 10 000 000$.',
    color: '#22C55E',
    icon: 'wallet',
    sort: 7,
    active: true,
  },
  {
    id: 'allorno',
    name: 'Кейс «Всё или ничего»',
    description:
      'Максимальный азарт 50/50: либо скромное утешение, либо мощный куш из риликов и топовых привилегий.',
    color: '#DC2626',
    icon: 'flame',
    sort: 8,
    active: true,
  },
  {
    id: 'djamshut',
    name: 'Кейс Джамшута',
    description:
      'Фирменный гриферский кейс: легендарная лопата Джамшута, стройблоки для базы, рилики и привилегии.',
    color: '#FB923C',
    icon: 'wrench',
    sort: 9,
    active: true,
  },
  {
    id: 'reallydev',
    name: 'Кейс ReallyDev',
    description:
      'Эксклюзивный сундук разработчиков с максимальными шансами на OPKA, D.OWNER, редкие сферы и крупные рилики.',
    color: '#8B5CF6',
    icon: 'gem',
    sort: 10,
    active: true,
  },
  {
    id: 'summer',
    name: 'Летний кейс',
    description:
      'Яркий сезонный сундук: солнечные талисманы, летние наборы риликов, дополнительные ключи и привилегии.',
    color: '#38BDF8',
    icon: 'zap',
    sort: 11,
    active: true,
  },
];

const RAW_CASE_ITEMS: Array<Omit<CaseItem, 'description' | 'link'> & { customDesc?: string; customLink?: string | null }> = [
  // 1. donate — 100% (26+20+16+12+10+7+5+3+1 = 100)
  { caseId: 'donate', name: 'Привилегия PEGAS', rarity: 'common', chance: 26, icon: 'feather', color: '#22C55E', sort: 1 },
  { caseId: 'donate', name: 'Привилегия COBRA', rarity: 'common', chance: 20, icon: 'snake', color: '#84CC16', sort: 2 },
  { caseId: 'donate', name: 'Привилегия BUNNY', rarity: 'uncommon', chance: 16, icon: 'rabbit', color: '#EC4899', sort: 3 },
  { caseId: 'donate', name: 'Привилегия RABBIT', rarity: 'rare', chance: 12, icon: 'rabbit', color: '#38BDF8', sort: 4 },
  { caseId: 'donate', name: 'Привилегия VAMPIRE', rarity: 'rare', chance: 10, icon: 'droplet', color: '#EF4444', sort: 5 },
  { caseId: 'donate', name: 'Привилегия GOD', rarity: 'epic', chance: 7, icon: 'zap', color: '#FACC15', sort: 6 },
  { caseId: 'donate', name: 'Привилегия KING', rarity: 'legendary', chance: 5, icon: 'crown', color: '#A855F7', sort: 7 },
  { caseId: 'donate', name: 'Привилегия D.MODER', rarity: 'legendary', chance: 3, icon: 'shield', color: '#14B8A6', sort: 8 },
  { caseId: 'donate', name: 'Привилегия D.ADMIN', rarity: 'mythic', chance: 1, icon: 'gavel', color: '#DC2626', sort: 9 },

  // 2. sdonate — 100% (24+20+16+14+10+8+5+3 = 100)
  { caseId: 'sdonate', name: 'Привилегия GOD', rarity: 'common', chance: 24, icon: 'zap', color: '#FACC15', sort: 1 },
  { caseId: 'sdonate', name: 'Привилегия KING', rarity: 'uncommon', chance: 20, icon: 'crown', color: '#A855F7', sort: 2 },
  { caseId: 'sdonate', name: 'Привилегия D.MODER', rarity: 'rare', chance: 16, icon: 'shield', color: '#14B8A6', sort: 3 },
  { caseId: 'sdonate', name: 'Привилегия D.ST.MODER', rarity: 'epic', chance: 14, icon: 'shield-check', color: '#06B6D4', sort: 4 },
  { caseId: 'sdonate', name: 'Привилегия D.GLMODER', rarity: 'epic', chance: 10, icon: 'shield-half', color: '#3B82F6', sort: 5 },
  { caseId: 'sdonate', name: 'Привилегия D.ML.ADMIN', rarity: 'legendary', chance: 8, icon: 'wrench', color: '#F97316', sort: 6 },
  { caseId: 'sdonate', name: 'Привилегия D.ADMIN', rarity: 'legendary', chance: 5, icon: 'gavel', color: '#DC2626', sort: 7 },
  { caseId: 'sdonate', name: 'Привилегия D.OWNER', rarity: 'mythic', chance: 3, icon: 'crown-gold', color: '#FACC15', sort: 8 },

  // 3. legendary — 100% (28+22+16+12+9+6+4.5+2.5 = 100)
  { caseId: 'legendary', name: '500 риликов', rarity: 'common', chance: 28, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'legendary', name: '1 000 риликов', rarity: 'uncommon', chance: 22, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'legendary', name: '2 500 риликов', rarity: 'rare', chance: 16, icon: 'coins', color: '#3B82F6', sort: 3 },
  { caseId: 'legendary', name: 'Привилегия RABBIT', rarity: 'epic', chance: 12, icon: 'rabbit', color: '#38BDF8', sort: 4 },
  { caseId: 'legendary', name: '5 000 риликов', rarity: 'epic', chance: 9, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'legendary', name: 'Привилегия GOD', rarity: 'legendary', chance: 6, icon: 'zap', color: '#FACC15', sort: 6 },
  { caseId: 'legendary', name: 'Привилегия D.MODER', rarity: 'legendary', chance: 4.5, icon: 'shield', color: '#14B8A6', sort: 7 },
  { caseId: 'legendary', name: '15 000 риликов', rarity: 'mythic', chance: 2.5, icon: 'coins', color: '#EF4444', sort: 8 },

  // 4. riliks — 100% (28+22+18+13+9+5.5+3+1.5 = 100)
  { caseId: 'riliks', name: '250 риликов', rarity: 'common', chance: 28, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'riliks', name: '500 риликов', rarity: 'common', chance: 22, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'riliks', name: '1 000 риликов', rarity: 'uncommon', chance: 18, icon: 'coins', color: '#38BDF8', sort: 3 },
  { caseId: 'riliks', name: '2 500 риликов', rarity: 'rare', chance: 13, icon: 'coins', color: '#3B82F6', sort: 4 },
  { caseId: 'riliks', name: '5 000 риликов', rarity: 'epic', chance: 9, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'riliks', name: '10 000 риликов', rarity: 'legendary', chance: 5.5, icon: 'coins', color: '#F59E0B', sort: 6 },
  { caseId: 'riliks', name: '25 000 риликов', rarity: 'legendary', chance: 3, icon: 'coins', color: '#FACC15', sort: 7 },
  { caseId: 'riliks', name: '50 000 риликов', rarity: 'mythic', chance: 1.5, icon: 'coins', color: '#EF4444', sort: 8 },

  // 5. ditems — 100% (26+22+18+14+10+6+3+1 = 100)
  { caseId: 'ditems', name: 'Набор трапок и пластов ×8', rarity: 'common', chance: 26, icon: 'box', color: '#94A3B8', sort: 1, customDesc: 'Тактический набор из 8 трапок и пластов для мгновенной застройки в PvP.' },
  { caseId: 'ditems', name: 'Божья аура и дезориентация ×4', rarity: 'uncommon', chance: 22, icon: 'sparkles', color: '#22C55E', sort: 2, customDesc: 'Расходники для рейдов и анти-таргета в замесах на арене.' },
  { caseId: 'ditems', name: 'Незеритовый сет Защита V', rarity: 'rare', chance: 18, icon: 'shield', color: '#3B82F6', sort: 3, customDesc: 'Полный комплект укреплённой незеритовой брони с повышенной прочностью.' },
  { caseId: 'ditems', name: 'Меч Крушителя (Острота VI)', rarity: 'epic', chance: 14, icon: 'swords', color: '#A855F7', sort: 4, customDesc: 'Донатный клинок с увеличенным уроном и вампиризмом в ближнем бою.' },
  { caseId: 'ditems', name: 'Сфера Андромеды Ур. 2', rarity: 'epic', chance: 10, icon: 'gem', color: '#EC4899', sort: 5, customDesc: 'Боевая сфера в левую руку: бонус к урону, скорости и сопротивлению.' },
  { caseId: 'ditems', name: 'Талисман Крушителя', rarity: 'legendary', chance: 6, icon: 'flame', color: '#F59E0B', sort: 6, customDesc: 'Редкий талисман с постоянным усилением силы и защитой от взрывов.' },
  { caseId: 'ditems', name: 'Талисман Грани', rarity: 'legendary', chance: 3, icon: 'trophy', color: '#FACC15', sort: 7, customDesc: 'Топовый артефакт сезона для выживания в самых жёстких дуэлях.' },
  { caseId: 'ditems', name: 'Сфера Химеры MAX', rarity: 'mythic', chance: 1, icon: 'crown-gold', color: '#EF4444', sort: 8, customDesc: 'Мифическая сфера максимального уровня с лучшими характеристиками сервера.' },

  // 6. kits — 100% (28+22+18+14+9+5+3+1 = 100)
  { caseId: 'kits', name: 'Кит VAMPIRE', rarity: 'common', chance: 28, icon: 'droplet', color: '#94A3B8', sort: 1, customDesc: 'Полный боевой набор привилегии VAMPIRE с зельями и бронёй.', customLink: '/privileges/vampire' },
  { caseId: 'kits', name: 'Кит GOD', rarity: 'uncommon', chance: 22, icon: 'zap', color: '#22C55E', sort: 2, customDesc: 'Снаряжение уровня GOD для комфортного старта и PvP.', customLink: '/privileges/god' },
  { caseId: 'kits', name: 'Кит IMPERATOR', rarity: 'rare', chance: 18, icon: 'crown', color: '#F43F5E', sort: 3, customDesc: 'Императорский комплект брони, оружия и расходников.', customLink: '/privileges/imperator' },
  { caseId: 'kits', name: 'Кит D.MODER', rarity: 'epic', chance: 14, icon: 'shield', color: '#14B8A6', sort: 4, customDesc: 'Усиленный набор D.MODER с расходниками и тотемом бессмертия.', customLink: '/privileges/dmoder' },
  { caseId: 'kits', name: 'Кит D.GLMODER', rarity: 'epic', chance: 9, icon: 'shield-half', color: '#A855F7', sort: 5, customDesc: 'Редкий комплект старшего модератора с зачарованными предметами.', customLink: '/privileges/dglmoder' },
  { caseId: 'kits', name: 'Кит D.ADMIN', rarity: 'legendary', chance: 5, icon: 'gavel', color: '#F59E0B', sort: 6, customDesc: 'Мощный администраторский кит для доминации на сервере.', customLink: '/privileges/dadmin' },
  { caseId: 'kits', name: 'Кит OPKA', rarity: 'legendary', chance: 3, icon: 'flame', color: '#C026D3', sort: 7, customDesc: 'Элитный комплект OPKA с топовыми сферами и талисманом.', customLink: '/privileges/opka' },
  { caseId: 'kits', name: 'Кит D.OWNER', rarity: 'mythic', chance: 1, icon: 'crown-gold', color: '#FACC15', sort: 8, customDesc: 'Максимальный комплект владельца со всеми лучшими ресурсами сезона.', customLink: '/privileges/downer' },

  // 7. money — 100% (30+24+18+12+8+5+2+1 = 100)
  { caseId: 'money', name: '50 000$ монет', rarity: 'common', chance: 30, icon: 'wallet', color: '#94A3B8', sort: 1, customDesc: 'Стартовый капитал игровой валюты для покупок на /ah.' },
  { caseId: 'money', name: '150 000$ монет', rarity: 'common', chance: 24, icon: 'wallet', color: '#22C55E', sort: 2, customDesc: 'Уверенная сумма монет для закупки ресурсов и зелий.' },
  { caseId: 'money', name: '350 000$ монет', rarity: 'uncommon', chance: 18, icon: 'coins', color: '#38BDF8', sort: 3, customDesc: 'Солидный баланс для торговли на аукционе игроков.' },
  { caseId: 'money', name: '750 000$ монет', rarity: 'rare', chance: 12, icon: 'coins', color: '#3B82F6', sort: 4, customDesc: 'Почти миллион игровых монет сразу на счёт персонажа.' },
  { caseId: 'money', name: '1 500 000$ монет', rarity: 'epic', chance: 8, icon: 'gem', color: '#A855F7', sort: 5, customDesc: 'Полтора миллиона для скупки редких лотов на аукционе.' },
  { caseId: 'money', name: '3 500 000$ монет', rarity: 'legendary', chance: 5, icon: 'trophy', color: '#F59E0B', sort: 6, customDesc: 'Крупный банк игровой валюты для развития клана и базы.' },
  { caseId: 'money', name: '6 000 000$ монет', rarity: 'legendary', chance: 2, icon: 'sparkles', color: '#FACC15', sort: 7, customDesc: 'Огромное состояние в 6 миллионов игровых долларов.' },
  { caseId: 'money', name: '10 000 000$ монет', rarity: 'mythic', chance: 1, icon: 'crown-gold', color: '#EF4444', sort: 8, customDesc: 'Главный денежный джекпот сервера — 10 000 000$ на баланс!' },

  // 8. allorno — 100% (50+20+14+9+5+2 = 100)
  { caseId: 'allorno', name: '50 риликов (утешительный приз)', rarity: 'common', chance: 50, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'allorno', name: '2 000 риликов', rarity: 'rare', chance: 20, icon: 'coins', color: '#3B82F6', sort: 2 },
  { caseId: 'allorno', name: 'Привилегия KING', rarity: 'epic', chance: 14, icon: 'crown', color: '#A855F7', sort: 3 },
  { caseId: 'allorno', name: '7 500 риликов', rarity: 'legendary', chance: 9, icon: 'coins', color: '#F59E0B', sort: 4 },
  { caseId: 'allorno', name: 'Привилегия D.ADMIN', rarity: 'legendary', chance: 5, icon: 'gavel', color: '#DC2626', sort: 5 },
  { caseId: 'allorno', name: 'Привилегия OPKA', rarity: 'mythic', chance: 2, icon: 'flame', color: '#FACC15', sort: 6 },

  // 9. djamshut — 100% (28+22+18+14+10+5+2+1 = 100)
  { caseId: 'djamshut', name: 'Набор строителя базы ×64', rarity: 'common', chance: 28, icon: 'box', color: '#94A3B8', sort: 1, customDesc: 'Обсидиан и блоки для быстрой отстройки неприступной базы.' },
  { caseId: 'djamshut', name: '300 риликов', rarity: 'common', chance: 22, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'djamshut', name: 'Кирка Джамшута (Эффективность VII)', rarity: 'uncommon', chance: 18, icon: 'wrench', color: '#FB923C', sort: 3, customDesc: 'Фирменный инструмент с моментальным вскапыванием блоков.' },
  { caseId: 'djamshut', name: 'Лопата и Топор Мастера', rarity: 'rare', chance: 14, icon: 'wrench', color: '#38BDF8', sort: 4, customDesc: 'Улучшенные инструменты с починкой и авто-плавкой.' },
  { caseId: 'djamshut', name: '1 500 риликов', rarity: 'epic', chance: 10, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'djamshut', name: 'Привилегия GOD', rarity: 'legendary', chance: 5, icon: 'zap', color: '#FACC15', sort: 6 },
  { caseId: 'djamshut', name: 'Привилегия D.MODER', rarity: 'legendary', chance: 2, icon: 'shield', color: '#14B8A6', sort: 7 },
  { caseId: 'djamshut', name: 'Привилегия D.ADMIN', rarity: 'mythic', chance: 1, icon: 'gavel', color: '#EF4444', sort: 8 },

  // 10. reallydev — 100% (25+21+18+14+10+6.5+3.5+2 = 100)
  { caseId: 'reallydev', name: '2 500 риликов', rarity: 'common', chance: 25, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'reallydev', name: '5 000 риликов', rarity: 'uncommon', chance: 21, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'reallydev', name: 'Привилегия D.MODER', rarity: 'rare', chance: 18, icon: 'shield', color: '#38BDF8', sort: 3 },
  { caseId: 'reallydev', name: 'Привилегия D.GLMODER', rarity: 'epic', chance: 14, icon: 'shield-half', color: '#A855F7', sort: 4 },
  { caseId: 'reallydev', name: '15 000 риликов', rarity: 'epic', chance: 10, icon: 'coins', color: '#EC4899', sort: 5 },
  { caseId: 'reallydev', name: 'Привилегия D.ADMIN', rarity: 'legendary', chance: 6.5, icon: 'gavel', color: '#DC2626', sort: 6 },
  { caseId: 'reallydev', name: 'Привилегия OPKA', rarity: 'legendary', chance: 3.5, icon: 'flame', color: '#C026D3', sort: 7 },
  { caseId: 'reallydev', name: 'Привилегия D.OWNER', rarity: 'mythic', chance: 2, icon: 'crown-gold', color: '#FACC15', sort: 8 },

  // 11. summer — 100% (27+23+17+13+10+6+3+1 = 100)
  { caseId: 'summer', name: '350 риликов', rarity: 'common', chance: 27, icon: 'coins', color: '#94A3B8', sort: 1 },
  { caseId: 'summer', name: '750 риликов', rarity: 'uncommon', chance: 23, icon: 'coins', color: '#22C55E', sort: 2 },
  { caseId: 'summer', name: 'Донат-кейс ×2', rarity: 'rare', chance: 17, icon: 'gift', color: '#38BDF8', sort: 3, customDesc: 'Два ключа от Донат-кейса — двойной шанс забрать привилегию.', customLink: '/cases/donate' },
  { caseId: 'summer', name: 'Летний талисман Солнца', rarity: 'epic', chance: 13, icon: 'sparkles', color: '#F59E0B', sort: 4, customDesc: 'Сезонный талисман с огнестойкостью, скоростью и бонусом к урону.' },
  { caseId: 'summer', name: '3 000 риликов', rarity: 'epic', chance: 10, icon: 'coins', color: '#A855F7', sort: 5 },
  { caseId: 'summer', name: 'Привилегия KING', rarity: 'legendary', chance: 6, icon: 'crown', color: '#A855F7', sort: 6 },
  { caseId: 'summer', name: 'Привилегия D.MODER', rarity: 'legendary', chance: 3, icon: 'shield', color: '#14B8A6', sort: 7 },
  { caseId: 'summer', name: '20 000 риликов', rarity: 'mythic', chance: 1, icon: 'crown-gold', color: '#FACC15', sort: 8 },
];

/**
 * Награды кейсов с человеческим описанием: что именно выпало и что это даёт.
 * Привилегии получают ссылку на свою страницу, рилики — на пакеты валюты.
 */
export const CASE_ITEMS: CaseItem[] = RAW_CASE_ITEMS.map(({ customDesc, customLink, ...item }) => {
  if (customDesc) {
    return {
      ...item,
      description: customDesc,
      link: customLink ?? null,
    };
  }
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
    donate: [
      [1, 29, 39],
      [5, 119, 145],
      [10, 199, 290],
    ],
    sdonate: [
      [1, 99, 139],
      [3, 259, 297],
      [5, 399, 495],
    ],
    legendary: [
      [1, 59, 79],
      [3, 149, 177],
      [5, 229, 295],
    ],
    riliks: [
      [1, 39, 55],
      [5, 169, 195],
      [10, 299, 390],
    ],
    ditems: [
      [1, 49, 69],
      [3, 129, 147],
      [5, 199, 245],
    ],
    kits: [
      [1, 35, 49],
      [3, 89, 105],
      [5, 139, 175],
    ],
    money: [
      [1, 19, 29],
      [5, 79, 95],
      [10, 139, 190],
    ],
    allorno: [
      [1, 45, 65],
      [3, 119, 135],
      [5, 179, 225],
    ],
    djamshut: [
      [1, 25, 39],
      [5, 99, 125],
      [10, 179, 250],
    ],
    reallydev: [
      [1, 129, 179],
      [3, 339, 387],
      [5, 529, 645],
    ],
    summer: [
      [1, 39, 59],
      [3, 99, 117],
      [5, 149, 195],
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
        features: [`${qty} × ${g.name}`, `Серверный кейс: ${g.id}`, 'Содержимое и шансы — на странице кейса'],
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

