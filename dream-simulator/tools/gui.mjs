/**
 * gui.mjs — весь интерфейс DREAM SIMULATOR: NIGHTMARE WORLDS.
 * Имена элементов здесь — контракт для клиентских контроллеров (tools/checkgui.mjs проверяет).
 */
import { el, hex, udim2, udim, font, ui, screenGui, v2, v3 } from './rbxkit.mjs';

const C = {
  bg: hex('#07080f'),
  panel: hex('#101124'),
  panel2: hex('#171a33'),
  accent: hex('#a68cff'),
  accent2: hex('#6f5bd6'),
  text: hex('#eceeff'),
  dim: hex('#9ea1c6'),
  gold: hex('#ffd166'),
  danger: hex('#ff5c6c'),
  success: hex('#78eba0'),
  moon: hex('#f0ecff'),
};

const FONT = 'rbxasset://fonts/families/GothamSSm.json';
const FONT_B = 'rbxasset://fonts/families/GothamSSm.json';

const text = (content, props = {}, children = []) => {
  const { maxTextSize, ...rest } = props;
  return ui.label(content, {
    FontFace: font(FONT),
    TextColor3: C.text,
    TextScaled: true,
    TextWrapped: false,
    ...rest,
  }, [
    ...children,
    ...(maxTextSize === false ? [] : [
      el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: maxTextSize || 28 }),
    ]),
  ]);
};

const bold = (content, props = {}, children = []) =>
  text(content, { ...props, FontFace: font(FONT_B, 'Bold') }, children);

const panel = (props = {}, children = []) =>
  ui.frame({
    BackgroundColor3: C.panel,
    BackgroundTransparency: 0.05,
    Size: udim2(0, 620, 0, 420),
    AnchorPoint: v2(0.5, 0.5),
    Position: udim2(0.5, 0.5),
    ...props,
  }, [
    ...children,
    ui.corner(props.corner || 22),
    ui.stroke({ Color: C.accent, Transparency: 0.55, Thickness: 1.5 }),
    ui.gradient([hex('#171a33'), hex('#0b0c18')], { Rotation: 90 }),
  ]);

const backdrop = (props = {}) =>
  ui.frame({
    Name: 'Backdrop',
    Size: udim2(1, 1),
    BackgroundColor3: C.bg,
    BackgroundTransparency: 0.12,
    ZIndex: 1,
    ...props,
  }, [
    ui.gradient([hex('#0a0b18'), hex('#05060d')], { Rotation: 90 }),
  ]);

const button = (name, label, props = {}, children = []) =>
  ui.button(label, {
    Name: name,
    Size: udim2(0, 420, 0, 58),
    BackgroundColor3: C.panel2,
    BackgroundTransparency: 0.1,
    TextColor3: C.text,
    FontFace: font(FONT_B, 'Bold'),
    TextScaled: true,
    ...props,
  }, [
    ...children,
    ui.corner(14),
    ui.stroke({ Color: C.accent, Transparency: 0.62, Thickness: 1.2 }),
    el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 24 }),
  ]);

const divider = (props = {}) =>
  ui.frame({ Name: 'Divider', Size: udim2(0, 360, 0, 1), BackgroundColor3: C.accent, BackgroundTransparency: 0.55, ...props });

const statRow = (labelText, valueName, layoutOrder = 1) =>
  ui.frame({ Name: 'Row_' + valueName, Size: udim2(1, 0, 0, 46), BackgroundColor3: C.panel2, BackgroundTransparency: 0.35, LayoutOrder: layoutOrder }, [
    ui.corner(10),
    text(labelText, { Name: 'Label', Position: udim2(0, 16, 0, 0), Size: udim2(0.6, 0, 1, 0), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 20 }),
    bold('0', { Name: valueName, Position: udim2(0.6, 0, 0, 0), Size: udim2(0.4, -18, 1, 0), TextXAlignment: 'Right', TextColor3: C.text, maxTextSize: 20 }),
  ]);

const slider = (name, labelText, value) =>
  ui.frame({ Name: name, Size: udim2(1, 0, 0, 52), BackgroundTransparency: 1, LayoutOrder: 1 }, [
    text(labelText, { Name: 'Title', Position: udim2(0, 0, 0, 0), Size: udim2(0.7, 0, 0, 20), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 17 }),
    bold(Math.round(value * 100) + '%', { Name: 'Value', Position: udim2(0.7, 0, 0, 0), Size: udim2(0.3, 0, 0, 20), TextXAlignment: 'Right', TextColor3: C.text, maxTextSize: 17 }),
    ui.frame({ Name: 'Track', Position: udim2(0, 0, 0, 30), Size: udim2(1, 0, 0, 12), BackgroundColor3: hex('#242744'), BackgroundTransparency: 0.15 }, [
      ui.corner(6),
      ui.frame({ Name: 'Fill', Size: udim2(value, 0, 1, 0), BackgroundColor3: C.accent, BackgroundTransparency: 0 }, [
        ui.corner(6),
        ui.gradient([C.accent2, C.accent], { Rotation: 0 }),
      ]),
      ui.frame({ Name: 'Knob', AnchorPoint: v2(0.5, 0.5), Position: udim2(value, 0, 0.5, 0), Size: udim2(0, 20, 0, 20), BackgroundColor3: C.moon, ZIndex: 3 }, [
        el('UICorner', { CornerRadius: udim(0.5, 0) }),
        ui.stroke({ Color: C.accent, Transparency: 0, Thickness: 2 }),
      ]),
    ]),
  ]);

const toggle = (name, labelText) =>
  ui.button(labelText + ': ВКЛ', {
    Name: name,
    Size: udim2(1, 0, 0, 48),
    BackgroundColor3: C.panel2,
    BackgroundTransparency: 0.25,
    TextColor3: C.text,
    TextScaled: true,
    FontFace: font(FONT_B, 'Bold'),
    LayoutOrder: 1,
  }, [
    ui.corner(12),
    ui.stroke({ Color: C.accent, Transparency: 0.6, Thickness: 1.2 }),
    el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 20 }),
  ]);

const closeButton = () =>
  button('CloseButton', '✕  ЗАКРЫТЬ', {
    Size: udim2(0, 200, 0, 46),
    AnchorPoint: v2(1, 1),
    Position: udim2(1, -22, 1, -20),
    BackgroundColor3: hex('#2a1e3d'),
    TextColor3: hex('#e8dcff'),
    ZIndex: 5,
  });

const header = (title, subtitle, coinsName) =>
  ui.frame({ Name: 'Header', Size: udim2(1, 0, 0, 96), BackgroundTransparency: 1 }, [
    bold(title, { Name: 'Title', Position: udim2(0, 34, 0, 18), Size: udim2(0.55, 0, 0, 46), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 34 }),
    text(subtitle, { Name: 'Subtitle', Position: udim2(0, 36, 0, 62), Size: udim2(0.5, 0, 0, 22), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 16 }),
    ...(coinsName
      ? [ui.frame({ Name: 'CoinChipBig', AnchorPoint: v2(1, 0), Position: udim2(1, -160, 0, 24), Size: udim2(0, 260, 0, 44), BackgroundColor3: hex('#231b3d'), BackgroundTransparency: 0.15 }, [
          ui.corner(22),
          ui.stroke({ Color: C.gold, Transparency: 0.4, Thickness: 1.4 }),
          text('🪙', { Position: udim2(0, 12, 0, 0), Size: udim2(0, 32, 1, 0), TextColor3: C.gold, maxTextSize: 24 }),
          bold('0 DREAM COINS', { Name: coinsName, Position: udim2(0, 46, 0, 0), Size: udim2(1, -56, 1, 0), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 18 }),
        ])]
      : []),
  ]);

/* ------------------------------------------------------------------ */
/* 1. Главное меню                                                     */
/* ------------------------------------------------------------------ */
export const mainMenu = () => screenGui('MainMenu', { DisplayOrder: 5, IgnoreGuiInset: true }, [
  backdrop(),
  // Луна и звёзды
  text('🌙', { Name: 'Moon', Position: udim2(0.78, 0, 0.12, 0), Size: udim2(0, 190, 0, 190), TextColor3: C.moon, ZIndex: 2, maxTextSize: 160 }),
  text('✦  ✧  ✦', { Name: 'Stars', Position: udim2(0.72, 0, 0.34, 0), Size: udim2(0, 260, 0, 60), TextColor3: hex('#cbbdff'), TextTransparency: 0.45, ZIndex: 2, maxTextSize: 34 }),
  text('✧        ✦', { Name: 'Stars2', Position: udim2(0.55, 0, 0.08, 0), Size: udim2(0, 300, 0, 40), TextColor3: hex('#9f92e8'), TextTransparency: 0.55, ZIndex: 2, maxTextSize: 26 }),
  ui.frame({ Name: 'Vignette', Size: udim2(1, 1), BackgroundColor3: hex('#000000'), BackgroundTransparency: 0.62, ZIndex: 2 }, [
    ui.gradient([hex('#000000'), hex('#000000')], { Rotation: 90 }),
  ]),

  /* ---------------- левая колонка: заголовок и кнопки ---------------- */
  ui.frame({ Name: 'Content', Position: udim2(0, 64, 0.09, 0), Size: udim2(0, 560, 0.86, 0), BackgroundTransparency: 1, ZIndex: 3 }, [
    ui.list({ Padding: udim(0, 12), SortOrder: 'LayoutOrder' }),
    text('🌙 DREAM SIMULATOR', { Name: 'Title', Size: udim2(1, 0, 0, 72), TextXAlignment: 'Left', TextColor3: C.moon, FontFace: font(FONT_B, 'Bold'), LayoutOrder: 1, maxTextSize: 52 }),
    text('Каждый сон — новая история...', { Name: 'Subtitle', Size: udim2(1, 0, 0, 30), TextXAlignment: 'Left', TextColor3: C.dim, LayoutOrder: 2, maxTextSize: 21 }),
    divider({ Size: udim2(0.7, 0, 0, 1), LayoutOrder: 3 }),

    ui.frame({ Name: 'Buttons', Size: udim2(1, 0, 0, 0), AutomaticSize: 'Y', BackgroundTransparency: 1, LayoutOrder: 4 }, [
      ui.list({ Padding: udim(0, 8), SortOrder: 'LayoutOrder' }),

      button('StartButton', '▶  НАЧАТЬ СОН', {
        LayoutOrder: 1, Size: udim2(0, 460, 0, 64),
        BackgroundColor3: hex('#4a2f9e'), TextColor3: hex('#ffffff'),
      }),
      text('Сложность сна:', { Name: 'DiffCaptions', Size: udim2(1, 0, 0, 20), TextXAlignment: 'Left', TextColor3: C.dim, LayoutOrder: 2, maxTextSize: 16 }),

      // Быстрый выбор сложности прямо в меню
      ui.frame({ Name: 'DifficultyChips', Size: udim2(0, 460, 0, 44), BackgroundTransparency: 1, LayoutOrder: 3 }, [
        ui.list({ Padding: udim(0, 8), FillDirection: 'Horizontal', SortOrder: 'LayoutOrder', HorizontalAlignment: 'Left' }),
        ui.button('🟢 ЛЁГКИЙ', { Name: 'DiffEasy', Size: udim2(0, 108, 1, 0), BackgroundColor3: hex('#16281f'), TextColor3: C.success, FontFace: font(FONT_B, 'Bold'), TextScaled: true, LayoutOrder: 1 }, [
          ui.corner(10), ui.stroke({ Color: C.success, Transparency: 0.55, Thickness: 1.2 }),
          el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 15 }),
        ]),
        ui.button('🟡 ОБЫЧНЫЙ', { Name: 'DiffNormal', Size: udim2(0, 108, 1, 0), BackgroundColor3: hex('#2a2416'), TextColor3: C.gold, FontFace: font(FONT_B, 'Bold'), TextScaled: true, LayoutOrder: 2 }, [
          ui.corner(10), ui.stroke({ Color: C.gold, Transparency: 0.55, Thickness: 1.2 }),
          el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 15 }),
        ]),
        ui.button('🔴 СЛОЖНЫЙ', { Name: 'DiffHard', Size: udim2(0, 108, 1, 0), BackgroundColor3: hex('#2b1618'), TextColor3: C.danger, FontFace: font(FONT_B, 'Bold'), TextScaled: true, LayoutOrder: 3 }, [
          ui.corner(10), ui.stroke({ Color: C.danger, Transparency: 0.55, Thickness: 1.2 }),
          el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 15 }),
        ]),
        ui.button('💀 ЭКСТРИМ', { Name: 'DiffExtreme', Size: udim2(0, 108, 1, 0), BackgroundColor3: hex('#2a1030'), TextColor3: C.accent, FontFace: font(FONT_B, 'Bold'), TextScaled: true, LayoutOrder: 4 }, [
          ui.corner(10), ui.stroke({ Color: C.accent, Transparency: 0.55, Thickness: 1.2 }),
          el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 15 }),
        ]),
      ]),

      bold('ВЫБРАНО: ОБЫЧНЫЙ · 🏆 250 монет', {
        Name: 'SelectedDifficulty', Size: udim2(1, 0, 0, 22), TextXAlignment: 'Left',
        TextColor3: hex('#cbbcff'), LayoutOrder: 4, maxTextSize: 16,
      }),

      button('PlayButton', '🎲  ВЫБРАТЬ ДРУГОЙ СОН', { LayoutOrder: 5, Size: udim2(0, 460, 0, 50) }),
      button('ShopButton', '🛒  МАГАЗИН', { LayoutOrder: 6, Size: udim2(0, 460, 0, 50) }),
      button('InventoryButton', '🎒  ИНВЕНТАРЬ', { LayoutOrder: 7, Size: udim2(0, 460, 0, 50) }),
      button('AchievementsButton', '🏆  ДОСТИЖЕНИЯ', { LayoutOrder: 8, Size: udim2(0, 460, 0, 50) }),
      button('StatsButton', '📊  СТАТИСТИКА', { LayoutOrder: 9, Size: udim2(0, 460, 0, 50) }),
      button('SettingsButton', '⚙  НАСТРОЙКИ', { LayoutOrder: 10, Size: udim2(0, 460, 0, 50) }),
      button('HowToButton', '❓  КАК ИГРАТЬ', { LayoutOrder: 11, Size: udim2(0, 460, 0, 46), BackgroundColor3: hex('#1a1630') }),
    ]),
  ]),

  /* ---------------- правая карточка профиля ---------------- */
  ui.frame({
    Name: 'InfoCard', AnchorPoint: v2(1, 0), Position: udim2(1, -54, 0.16, 0),
    Size: udim2(0, 348, 0, 470), BackgroundColor3: hex('#0e1022'),
    BackgroundTransparency: 0.08, ZIndex: 3,
  }, [
    ui.corner(20),
    ui.stroke({ Color: C.accent, Transparency: 0.5, Thickness: 1.4 }),
    ui.gradient([hex('#1a1d38'), hex('#0a0b16')], { Rotation: 90 }),
    bold('ТВОЙ ПРОФИЛЬ', { Name: 'CardTitle', Position: udim2(0, 20, 0, 16), Size: udim2(1, -40, 0, 24), TextXAlignment: 'Left', TextColor3: C.accent, maxTextSize: 18 }),
    bold('Сновидец', { Name: 'ProfileName', Position: udim2(0, 20, 0, 44), Size: udim2(1, -40, 0, 30), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 24 }),
    text('Уровень сна: 1', { Name: 'ProfileLevel', Position: udim2(0, 20, 0, 76), Size: udim2(1, -40, 0, 20), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 15 }),
    ui.frame({ Name: 'CardDivider', Position: udim2(0, 20, 0, 104), Size: udim2(1, -40, 0, 1), BackgroundColor3: C.accent, BackgroundTransparency: 0.6 }),
    text('СНЫ', { Position: udim2(0, 20, 0, 116), Size: udim2(0.5, 0, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    bold('0', { Name: 'ProfileDreams', Position: udim2(0, 20, 0, 134), Size: udim2(0.5, 0, 0, 26), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 22 }),
    text('ПОБЕДЫ', { Position: udim2(0.5, 0, 0, 116), Size: udim2(0.5, -20, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    bold('0', { Name: 'ProfileWins', Position: udim2(0.5, 0, 0, 134), Size: udim2(0.5, -20, 0, 26), TextXAlignment: 'Left', TextColor3: C.success, maxTextSize: 22 }),
    text('КВЕСТЫ', { Position: udim2(0, 20, 0, 168), Size: udim2(0.5, 0, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    bold('0', { Name: 'ProfileQuests', Position: udim2(0, 20, 0, 186), Size: udim2(0.5, 0, 0, 26), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 22 }),
    text('ДОСТИЖЕНИЯ', { Position: udim2(0.5, 0, 0, 168), Size: udim2(0.5, -20, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    bold('0 / 16', { Name: 'ProfileAchievements', Position: udim2(0.5, 0, 0, 186), Size: udim2(0.5, -20, 0, 26), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 20 }),
    ui.frame({ Name: 'CardDivider2', Position: udim2(0, 20, 0, 222), Size: udim2(1, -40, 0, 1), BackgroundColor3: C.accent, BackgroundTransparency: 0.6 }),
    text('ПОСЛЕДНИЙ СОН', { Position: udim2(0, 20, 0, 234), Size: udim2(1, -40, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    bold('—', { Name: 'LastDreamLabel', Position: udim2(0, 20, 0, 252), Size: udim2(1, -40, 0, 24), TextXAlignment: 'Left', TextColor3: hex('#cbbcff'), maxTextSize: 18 }),
    text('Лучшее время: —', { Name: 'BestTimeLabel', Position: udim2(0, 20, 0, 278), Size: udim2(1, -40, 0, 20), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 14 }),
    ui.frame({ Name: 'CardDivider3', Position: udim2(0, 20, 0, 306), Size: udim2(1, -40, 0, 1), BackgroundColor3: C.accent, BackgroundTransparency: 0.6 }),
    text('УПРАВЛЕНИЕ', { Position: udim2(0, 20, 0, 318), Size: udim2(1, -40, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    text('WASD — движение\nSHIFT — бег (шумно!)\nE — взаимодействие\nF — фонарик · TAB — задания', {
      Name: 'ControlsText', Position: udim2(0, 20, 0, 338), Size: udim2(1, -40, 0, 96),
      TextXAlignment: 'Left', TextYAlignment: 'Top', TextWrapped: true, TextColor3: hex('#b9bce0'), maxTextSize: 14,
    }),
  ]),

  /* ---------------- подсказка, монеты, версия ---------------- */
  ui.frame({ Name: 'CoinChip', AnchorPoint: v2(1, 0), Position: udim2(1, -28, 0, 24), Size: udim2(0, 220, 0, 44), BackgroundColor3: hex('#231b3d'), BackgroundTransparency: 0.12, ZIndex: 4 }, [
    ui.corner(22),
    ui.stroke({ Color: C.gold, Transparency: 0.45, Thickness: 1.4 }),
    text('🪙', { Position: udim2(0, 12, 0, 0), Size: udim2(0, 30, 1, 0), TextColor3: C.gold, maxTextSize: 22, ZIndex: 4 }),
    bold('0', { Name: 'CoinValue', Position: udim2(0, 44, 0, 0), Size: udim2(1, -54, 1, 0), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 20, ZIndex: 4 }),
  ]),
  text('WASD — движение   ·   SHIFT — бег   ·   E — взаимодействие   ·   F — фонарик   ·   TAB — задания', {
    Name: 'Hint', AnchorPoint: v2(0, 1), Position: udim2(0, 64, 1, -28), Size: udim2(0, 720, 0, 24), TextXAlignment: 'Left', TextColor3: C.dim, TextTransparency: 0.25, maxTextSize: 15, ZIndex: 4,
  }),
  text('v1.0.0  ·  NIGHTMARE WORLDS', { Name: 'Version', AnchorPoint: v2(1, 1), Position: udim2(1, -28, 1, -26), Size: udim2(0, 320, 0, 20), TextXAlignment: 'Right', TextColor3: C.dim, TextTransparency: 0.4, maxTextSize: 14, ZIndex: 4 }),
  ui.label('', { Name: 'MenuToast', AnchorPoint: v2(0.5, 1), Position: udim2(0.5, 0, 1, -80), Size: udim2(0, 620, 0, 30), BackgroundTransparency: 1, TextTransparency: 1, TextColor3: C.gold, TextScaled: true, FontFace: font(FONT_B, 'Bold'), ZIndex: 6 }, [
    el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 20 }),
  ]),

  /* ---------------- панель «как играть» ---------------- */
  ui.frame({ Name: 'TutorialPanel', Visible: false, Size: udim2(1, 1), BackgroundColor3: hex('#05060d'), BackgroundTransparency: 0.25, ZIndex: 15 }, [
    ui.frame({ Name: 'TutorialCard', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.5, 0), Size: udim2(0, 860, 0, 600), BackgroundColor3: hex('#101124'), BackgroundTransparency: 0.02, ZIndex: 16 }, [
      ui.corner(22),
      ui.stroke({ Color: C.accent, Transparency: 0.35, Thickness: 2 }),
      ui.gradient([hex('#1a1d38'), hex('#0a0b16')], { Rotation: 90 }),
      bold('КАК ИГРАТЬ', { Name: 'TutorialTitle', Position: udim2(0, 34, 0, 26), Size: udim2(1, -68, 0, 40), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 32 }),
      text('Один сон длится 10 минут. Найди портал пробуждения и не потеряй рассудок.', {
        Name: 'TutorialSubtitle', Position: udim2(0, 36, 0, 68), Size: udim2(1, -72, 0, 24), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 16,
      }),
      text(
        '🎯  ЦЕЛЬ\nВыполни обязательное задание сна — откроется портал пробуждения. Войди в него (E), чтобы проснуться и забрать награду.\n\n' +
        '🗺  КАРТЫ\n10 миров: тёмный лес, школа, пустой город, больница, цирк, затопленный город, ледяной мир, фабрика, станция, замок. Каждый сон выбирается случайно.\n\n' +
        '💬  NPC\nПодойди к NPC и нажми E: можно взять задание, попросить подсказку, купить лечение или обменять предметы.\n\n' +
        '👁  МОНСТРЫ\nТень, Бегун, Наблюдатель, Обманщик, Кошмар. Они слышат бег и видят свет фонарика. Прячься и не смотри Наблюдателю в глаза.\n\n' +
        '🧠  РАССУДОК И ВЫНОСЛИВОСТЬ\nБег тратит выносливость и привлекает монстров. В темноте рассудок падает — включи фонарик или выпей зелье.\n\n' +
        '⚙  НАСТРОЙКИ\nМожно уменьшить интенсивность скримеров, отключить тряску камеры, настроить громкость и качество графики.',
        {
          Name: 'TutorialBody', Position: udim2(0, 36, 0, 104), Size: udim2(1, -72, 0, 420),
          TextXAlignment: 'Left', TextYAlignment: 'Top', TextWrapped: true, TextColor3: hex('#c6c9ea'), maxTextSize: 16,
        }),
      button('TutorialClose', 'ПОНЯТНО', {
        AnchorPoint: v2(1, 1), Position: udim2(1, -28, 1, -24), Size: udim2(0, 220, 0, 46),
        BackgroundColor3: hex('#4a2f9e'), ZIndex: 17,
      }),
    ]),
  ]),

  ui.frame({ Name: 'Fade', Size: udim2(1, 1), BackgroundColor3: hex('#000000'), BackgroundTransparency: 1, ZIndex: 20, Visible: false }),
]);

/* ------------------------------------------------------------------ */
/* 2. Сложность                                                        */
/* ------------------------------------------------------------------ */
const difficultyCard = (name, key, icon, title, desc, reward, accentColor) =>
  ui.button('', {
    Name: name,
    Size: udim2(0, 300, 0, 380),
    BackgroundColor3: hex('#12142a'),
    BackgroundTransparency: 0.05,
    Text: '',
    LayoutOrder: 1,
  }, [
    ui.corner(20),
    ui.stroke({ Color: accentColor, Transparency: 0.35, Thickness: 2 }),
    ui.gradient([hex('#191c38'), hex('#0a0b16')], { Rotation: 90 }),
    text(icon, { Name: 'Icon', Position: udim2(0, 0, 0, 22), Size: udim2(1, 0, 0, 54), TextColor3: accentColor, maxTextSize: 46 }),
    bold(title, { Name: 'Name', Position: udim2(0, 0, 0, 82), Size: udim2(1, 0, 0, 34), TextColor3: accentColor, maxTextSize: 26 }),
    text(desc, { Name: 'Desc', Position: udim2(0, 20, 0, 128), Size: udim2(1, -40, 0, 120), TextWrapped: true, TextColor3: C.dim, TextYAlignment: 'Top', maxTextSize: 16, TextScaled: true }),
    bold(reward, { Name: 'Reward', Position: udim2(0, 0, 1, -88), Size: udim2(1, 0, 0, 26), TextColor3: C.gold, maxTextSize: 20 }),
    text('⏱ 10 минут сна', { Name: 'Time', Position: udim2(0, 0, 1, -58), Size: udim2(1, 0, 0, 22), TextColor3: C.dim, maxTextSize: 16 }),
    bold('ВЫБРАТЬ', { Position: udim2(0, 0, 1, -34), Size: udim2(1, 0, 0, 22), TextColor3: accentColor, maxTextSize: 16 }),
  ]);

export const difficultyGui = () => screenGui('DifficultyGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('ВЫБЕРИ СЛОЖНОСТЬ', 'Один сон длится ровно 10 минут. Успей проснуться.'),
  ui.frame({ Name: 'Cards', Position: udim2(0.5, -660, 0, 140), Size: udim2(0, 1320, 0, 400), BackgroundTransparency: 1 }, [
    ui.list({ Padding: udim(0, 24), FillDirection: 'Horizontal', HorizontalAlignment: 'Center', SortOrder: 'LayoutOrder' }),
    difficultyCard('EasyCard', 'Easy', '🟢', 'ЛЁГКИЙ', 'Медленные монстры, простые квесты и много подсказок. Хороший первый сон.', '🏆 100 монет', C.success),
    difficultyCard('NormalCard', 'Normal', '🟡', 'ОБЫЧНЫЙ', 'Средние монстры и более сложные задания. Сбалансированный кошмар.', '🏆 250 монет', C.gold),
    difficultyCard('HardCard', 'Hard', '🔴', 'СЛОЖНЫЙ', 'Быстрые монстры, сложные головоломки и мало подсказок.', '🏆 500 монет', C.danger),
    difficultyCard('ExtremeCard', 'Extreme', '💀', 'ЭКСТРИМ', 'Очень опасные монстры, почти нет подсказок и максимум скримеров.', '🏆 1000 монет', C.accent),
  ]),
  bold('', { Name: 'DreamPreview', Position: udim2(0.5, -400, 1, -130), Size: udim2(0, 800, 0, 60), TextColor3: hex('#c6bcff'), TextWrapped: true, maxTextSize: 18 }),
  button('BackButton', '← НАЗАД', { Position: udim2(0, 34, 1, -80), Size: udim2(0, 200, 0, 48), BackgroundColor3: hex('#1a1630') }),
  button('CloseButton', '✕ ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 3. Магазин                                                          */
/* ------------------------------------------------------------------ */
const shopCardTemplate = () =>
  ui.frame({ Name: 'CardTemplate', Visible: false, Size: udim2(0, 340, 0, 214), BackgroundColor3: hex('#14162c'), BackgroundTransparency: 0.08 }, [
    ui.corner(16),
    ui.stroke({ Color: C.accent, Transparency: 0.45, Thickness: 1.4 }),
    text('🔦', { Name: 'Icon', Position: udim2(0, 14, 0, 12), Size: udim2(0, 52, 0, 52), TextColor3: C.moon, maxTextSize: 40 }),
    bold('Фонарик', { Name: 'Name', Position: udim2(0, 76, 0, 16), Size: udim2(1, -90, 0, 26), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 20 }),
    text('Обычный', { Name: 'Rarity', Position: udim2(0, 76, 0, 42), Size: udim2(1, -90, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    text('Описание предмета', { Name: 'Desc', Position: udim2(0, 16, 0, 76), Size: udim2(1, -32, 0, 66), TextWrapped: true, TextYAlignment: 'Top', TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 14 }),
    bold('🪙 150', { Name: 'Price', Position: udim2(0, 16, 1, -46), Size: udim2(0, 120, 0, 22), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 18 }),
    button('BuyButton', 'КУПИТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -14, 1, -14), Size: udim2(0, 140, 0, 38), BackgroundColor3: hex('#3c2e78'), FontFace: font(FONT_B, 'Bold') }),
    bold('КУПЛЕНО', { Name: 'OwnedBadge', Visible: false, AnchorPoint: v2(1, 0), Position: udim2(1, -14, 0, 16), Size: udim2(0, 100, 0, 20), TextColor3: C.success, maxTextSize: 14 }),
  ]);

export const shopGui = () => screenGui('ShopGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('МАГАЗИН СНОВ', 'Предметы сохраняются навсегда и работают во всех снах.', 'CoinsLabelShop'),
  ui.scroll({ Name: 'ItemList', Position: udim2(0, 40, 0, 116), Size: udim2(1, -80, 1, -190), CanvasSize: udim2(0, 0, 0, 0), AutomaticCanvasSize: 'Y' }, [
    ui.grid({ CellSize: udim2(0, 340, 0, 214), CellPadding: udim2(0, 16, 0, 16), SortOrder: 'LayoutOrder', HorizontalAlignment: 'Left' }),
    ui.padding(6),
    shopCardTemplate(),
  ]),
  button('CloseButton', '✕  ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 4. Инвентарь                                                        */
/* ------------------------------------------------------------------ */
const inventoryCardTemplate = () =>
  ui.frame({ Name: 'CardTemplate', Visible: false, Size: udim2(0, 340, 0, 200), BackgroundColor3: hex('#14162c'), BackgroundTransparency: 0.08 }, [
    ui.corner(16),
    ui.stroke({ Color: C.accent, Transparency: 0.45, Thickness: 1.4 }),
    text('🔦', { Name: 'Icon', Position: udim2(0, 14, 0, 12), Size: udim2(0, 52, 0, 52), TextColor3: C.moon, maxTextSize: 40 }),
    bold('Фонарик', { Name: 'Name', Position: udim2(0, 76, 0, 16), Size: udim2(1, -90, 0, 26), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 20 }),
    text('Обычный', { Name: 'Count', Position: udim2(0, 76, 0, 42), Size: udim2(1, -90, 0, 18), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    text('Описание', { Name: 'Desc', Position: udim2(0, 16, 0, 74), Size: udim2(1, -32, 0, 60), TextWrapped: true, TextYAlignment: 'Top', TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 14 }),
    bold('', { Name: 'StateLabel', Position: udim2(0, 16, 1, -42), Size: udim2(0, 150, 0, 20), TextXAlignment: 'Left', TextColor3: C.success, maxTextSize: 14 }),
    button('ToggleButton', 'НАДЕТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -14, 1, -14), Size: udim2(0, 150, 0, 36), BackgroundColor3: hex('#3c2e78'), FontFace: font(FONT_B, 'Bold') }),
  ]);

export const inventoryGui = () => screenGui('InventoryGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('ИНВЕНТАРЬ', 'Экипируй предметы и косметику. Изменения сохраняются автоматически.'),
  text('Экипировано: ничего', { Name: 'EquippedLabel', Position: udim2(0, 40, 0, 100), Size: udim2(1, -80, 0, 24), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 17 }),
  ui.scroll({ Name: 'ItemList', Position: udim2(0, 40, 0, 132), Size: udim2(1, -80, 1, -206), AutomaticCanvasSize: 'Y' }, [
    ui.grid({ CellSize: udim2(0, 340, 0, 200), CellPadding: udim2(0, 16, 0, 16), SortOrder: 'LayoutOrder', HorizontalAlignment: 'Left' }),
    ui.padding(6),
    inventoryCardTemplate(),
  ]),
  text('Инвентарь пуст. Загляни в магазин!', { Name: 'EmptyLabel', Visible: false, Position: udim2(0.5, -300, 0.5, -20), Size: udim2(0, 600, 0, 40), TextColor3: C.dim, maxTextSize: 22 }),
  button('CloseButton', '✕  ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 5. Достижения                                                       */
/* ------------------------------------------------------------------ */
export const achievementsGui = () => screenGui('AchievementsGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('ДОСТИЖЕНИЯ', 'Каждое открытое достижение приносит 150 Dream Coins.'),
  text('Открыто: 0 / 12', { Name: 'ProgressLabel', Position: udim2(0, 40, 0, 100), Size: udim2(0, 400, 0, 24), TextXAlignment: 'Left', TextColor3: C.gold, maxTextSize: 18 }),
  ui.scroll({ Name: 'List', Position: udim2(0, 40, 0, 136), Size: udim2(1, -80, 1, -210), AutomaticCanvasSize: 'Y' }, [
    ui.list({ Padding: udim(0, 10), SortOrder: 'LayoutOrder' }),
    ui.padding(6),
    ui.frame({ Name: 'RowTemplate', Visible: false, Size: udim2(1, -20, 0, 86), BackgroundColor3: hex('#14162c'), BackgroundTransparency: 0.25, LayoutOrder: 1 }, [
      ui.corner(14),
      ui.stroke({ Color: C.accent, Transparency: 0.6, Thickness: 1.2 }),
      text('🏆', { Name: 'Icon', Position: udim2(0, 14, 0, 12), Size: udim2(0, 62, 0, 62), TextColor3: C.gold, maxTextSize: 40 }),
      bold('Название', { Name: 'Name', Position: udim2(0, 90, 0, 14), Size: udim2(0.7, 0, 0, 24), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 19 }),
      text('Описание достижения', { Name: 'Desc', Position: udim2(0, 90, 0, 40), Size: udim2(0.72, 0, 0, 32), TextXAlignment: 'Left', TextWrapped: true, TextColor3: C.dim, maxTextSize: 14 }),
      bold('🔒 ЗАКРЫТО', { Name: 'State', AnchorPoint: v2(1, 0.5), Position: udim2(1, -20, 0.5, 0), Size: udim2(0, 180, 0, 24), TextColor3: C.dim, maxTextSize: 15 }),
    ]),
  ]),
  button('CloseButton', '✕  ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 6. Статистика                                                       */
/* ------------------------------------------------------------------ */
export const statsGui = () => screenGui('StatsGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('СТАТИСТИКА', 'Твоя история снов.'),
  ui.frame({ Name: 'Columns', Position: udim2(0.5, -520, 0, 130), Size: udim2(0, 1040, 0, 560), BackgroundTransparency: 1 }, [
    ui.frame({ Name: 'Left', Size: udim2(0.5, -10, 1, 0), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 10), SortOrder: 'LayoutOrder' }),
      text('СНЫ', { Size: udim2(1, 0, 0, 24), TextXAlignment: 'Left', TextColor3: C.accent, maxTextSize: 16, LayoutOrder: 0 }),
      statRow('Всего снов', 'DreamsValue', 1),
      statRow('Победы', 'WinsValue', 2),
      statRow('Поражения', 'LossesValue', 3),
      statRow('Процент побед', 'WinrateValue', 4),
      statRow('Выполнено заданий', 'QuestsValue', 5),
      statRow('Лучшее время', 'BestTimeValue', 6),
    ]),
    ui.frame({ Name: 'Right', Position: udim2(0.5, 10, 0, 0), Size: udim2(0.5, -10, 1, 0), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 10), SortOrder: 'LayoutOrder' }),
      text('ПРОГРЕСС', { Size: udim2(1, 0, 0, 24), TextXAlignment: 'Left', TextColor3: C.accent, maxTextSize: 16, LayoutOrder: 0 }),
      statRow('Заработано монет', 'CoinsValue', 1),
      statRow('Время в игре', 'TimeValue', 2),
      statRow('Открыто достижений', 'AchievementsValue', 3),
      statRow('Посещено снов', 'DreamsVisitedValue', 4),
    ]),
  ]),
  button('CloseButton', '✕  ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 7. Настройки                                                        */
/* ------------------------------------------------------------------ */
export const settingsGui = () => screenGui('SettingsGUI', { DisplayOrder: 6, Enabled: false }, [
  backdrop(),
  header('НАСТРОЙКИ', 'Настрой игру под себя.'),
  panel({ Name: 'SettingsPanel', Size: udim2(0, 760, 0, 600), Position: udim2(0.5, 0, 0.54, 0) }, [
    ui.frame({ Name: 'Sliders', Position: udim2(0, 34, 0, 28), Size: udim2(1, -68, 0, 250), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 14), SortOrder: 'LayoutOrder' }),
      slider('MusicSlider', 'Громкость музыки', 0.5),
      slider('SfxSlider', 'Громкость звуков', 0.75),
      slider('SensitivitySlider', 'Чувствительность мыши', 0.5),
      slider('ScareSlider', 'Интенсивность скримеров', 1),
    ]),
    ui.frame({ Name: 'Toggles', Position: udim2(0, 34, 0, 300), Size: udim2(1, -68, 0, 168), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 12), SortOrder: 'LayoutOrder' }),
      toggle('HintsToggle', 'Подсказки'),
      toggle('CameraShakeToggle', 'Тряска камеры'),
      toggle('ScareToggle', 'Скримеры'),
    ]),
    text('КАЧЕСТВО ГРАФИКИ', { Position: udim2(0, 34, 0, 486), Size: udim2(0, 300, 0, 22), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 15 }),
    ui.frame({ Name: 'Quality', Position: udim2(0, 34, 0, 514), Size: udim2(1, -68, 0, 44), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 10), FillDirection: 'Horizontal', SortOrder: 'LayoutOrder' }),
      button('QualityLow', 'НИЗКОЕ', { Size: udim2(0, 200, 1, 0), LayoutOrder: 1, FontFace: font(FONT, 'Bold') }),
      button('QualityMedium', 'СРЕДНЕЕ', { Size: udim2(0, 200, 1, 0), LayoutOrder: 2, FontFace: font(FONT, 'Bold') }),
      button('QualityHigh', 'ВЫСОКОЕ', { Size: udim2(0, 200, 1, 0), LayoutOrder: 3, FontFace: font(FONT, 'Bold') }),
      button('ResetButton', 'СБРОС', { Size: udim2(0, 150, 1, 0), LayoutOrder: 4, FontFace: font(FONT, 'Bold'), BackgroundColor3: hex('#3a2230') }),
    ]),
  ]),
  button('CloseButton', '✕  ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -34, 1, -20), Size: udim2(0, 200, 0, 46), BackgroundColor3: hex('#2a1e3d') }),
]);

/* ------------------------------------------------------------------ */
/* 8. HUD сна                                                          */
/* ------------------------------------------------------------------ */
const vitalBar = (name, labelText, color, position) =>
  ui.frame({ Name: name, Position: position, Size: udim2(0, 250, 0, 34), BackgroundTransparency: 1 }, [
    text(labelText, { Position: udim2(0, 0, 0, 0), Size: udim2(0, 250, 0, 16), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 13 }),
    ui.frame({ Name: 'Track', Position: udim2(0, 0, 0, 18), Size: udim2(1, 0, 0, 10), BackgroundColor3: hex('#1b1d33'), BackgroundTransparency: 0.15 }, [
      ui.corner(5),
      ui.frame({ Name: 'Fill', Size: udim2(1, 0, 1, 0), BackgroundColor3: color, BackgroundTransparency: 0 }, [
        ui.corner(5),
        ui.gradient([color, color], { Rotation: 0 }),
      ]),
    ]),
  ]);

const mobileButton = (name, icon, position, color) =>
  ui.button(icon, {
    Name: name,
    Position: position,
    Size: udim2(0, 74, 0, 74),
    BackgroundColor3: color,
    BackgroundTransparency: 0.25,
    TextColor3: C.moon,
    FontFace: font(FONT, 'Bold'),
    TextScaled: true,
  }, [
    el('UICorner', { CornerRadius: udim(0.5, 0) }),
    ui.stroke({ Color: C.accent, Transparency: 0.2, Thickness: 2 }),
    el('UITextSizeConstraint', { Name: 'SizeConstraint', MaxTextSize: 32 }),
  ]);

export const dreamHud = () => screenGui('DreamHUD', { DisplayOrder: 10, Enabled: false }, [
  ui.frame({ Name: 'Vignette', Size: udim2(1, 1), BackgroundColor3: hex('#08000f'), BackgroundTransparency: 1, Visible: false, ZIndex: 2 }),
  ui.frame({ Name: 'TopBar', AnchorPoint: v2(0.5, 0), Position: udim2(0.5, 0, 0, 18), Size: udim2(0, 620, 0, 74), BackgroundTransparency: 1, ZIndex: 5 }, [
    bold('🌙 ДО ПРОБУЖДЕНИЯ: 10:00', { Name: 'TimerLabel', Size: udim2(1, 0, 0, 30), TextColor3: C.moon, maxTextSize: 26 }),
    ui.frame({ Name: 'TimerBar', Position: udim2(0, 20, 0, 46), Size: udim2(1, -40, 0, 8), BackgroundColor3: hex('#1c1b33'), BackgroundTransparency: 0.1 }, [
      ui.corner(4),
      ui.frame({ Name: 'Fill', Size: udim2(1, 0, 1, 0), BackgroundColor3: C.accent, BackgroundTransparency: 0 }, [
        ui.corner(4),
        ui.gradient([C.accent2, C.accent], { Rotation: 0 }),
      ]),
    ]),
  ]),
  ui.frame({ Name: 'Vitals', Position: udim2(0, 26, 0, 26), Size: udim2(0, 250, 0, 150), BackgroundTransparency: 1, ZIndex: 5 }, [
    vitalBar('HealthBar', '❤ ЗДОРОВЬЕ', C.danger, udim2(0, 0, 0, 0)),
    vitalBar('SanityBar', '🧠 РАССУДОК', C.accent, udim2(0, 0, 0, 44)),
    vitalBar('StaminaBar', '⚡ ВЫНОСЛИВОСТЬ', hex('#6edcff'), udim2(0, 0, 0, 88)),
  ]),
  ui.frame({ Name: 'QuestTracker', Position: udim2(1, -366, 0, 26), Size: udim2(0, 340, 0, 190), BackgroundColor3: hex('#0d0e1c'), BackgroundTransparency: 0.35, ZIndex: 5 }, [
    ui.corner(14),
    ui.stroke({ Color: C.accent, Transparency: 0.6, Thickness: 1.2 }),
    bold('ЗАДАНИЯ', { Name: 'QuestTitle', Position: udim2(0, 14, 0, 10), Size: udim2(1, -28, 0, 22), TextXAlignment: 'Left', TextColor3: C.accent, maxTextSize: 16 }),
    ui.frame({ Name: 'ObjectiveList', Position: udim2(0, 14, 0, 38), Size: udim2(1, -28, 1, -48), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 4), SortOrder: 'LayoutOrder' }),
      text('• Цель', { Name: 'ObjectiveTemplate', Visible: false, Size: udim2(1, 0, 0, 18), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 14, LayoutOrder: 1 }),
    ]),
  ]),
  ui.frame({ Name: 'Compass', AnchorPoint: v2(0.5, 0), Position: udim2(0.5, 0, 0, 100), Size: udim2(0, 140, 0, 44), BackgroundTransparency: 1, Visible: false, ZIndex: 5 }, [
    bold('➤', { Name: 'CompassArrow', Position: udim2(0.5, -16, 0, 0), Size: udim2(0, 32, 0, 32), AnchorPoint: v2(0.5, 0), TextColor3: C.accent, maxTextSize: 30 }),
    text('0 м', { Name: 'DistanceLabel', Position: udim2(0, 0, 0, 30), Size: udim2(1, 0, 0, 16), TextColor3: C.dim, maxTextSize: 13 }),
  ]),
  ui.frame({ Name: 'InteractPrompt', AnchorPoint: v2(0.5, 1), Position: udim2(0.5, 0, 1, -140), Size: udim2(0, 460, 0, 54), BackgroundColor3: hex('#12132a'), BackgroundTransparency: 0.15, Visible: false, ZIndex: 6 }, [
    ui.corner(14),
    ui.stroke({ Color: C.accent, Transparency: 0.25, Thickness: 1.6 }),
    ui.frame({ Name: 'KeyCap', Position: udim2(0, 12, 0, 11), Size: udim2(0, 34, 0, 32), BackgroundColor3: C.accent, BackgroundTransparency: 0.1 }, [
      ui.corner(7),
      bold('E', { Name: 'KeyLabel', Size: udim2(1, 1), TextColor3: hex('#111022'), maxTextSize: 20 }),
    ]),
    text('Взаимодействовать', { Name: 'PromptText', Position: udim2(0, 58, 0, 0), Size: udim2(1, -70, 1, 0), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 19 }),
  ]),
  ui.frame({ Name: 'ToastArea', AnchorPoint: v2(1, 1), Position: udim2(1, -26, 1, -26), Size: udim2(0, 380, 0, 300), BackgroundTransparency: 1, ZIndex: 7 }, [
    ui.list({ Padding: udim(0, 8), VerticalAlignment: 'Bottom', HorizontalAlignment: 'Right', SortOrder: 'LayoutOrder' }),
    ui.frame({ Name: 'ToastTemplate', Visible: false, Size: udim2(1, 0, 0, 46), BackgroundColor3: hex('#121327'), BackgroundTransparency: 0.12, LayoutOrder: 1 }, [
      ui.corner(12),
      ui.stroke({ Color: C.accent, Transparency: 0.35, Thickness: 1.2 }),
      text('✨', { Name: 'Icon', Position: udim2(0, 10, 0, 0), Size: udim2(0, 32, 1, 0), TextColor3: C.gold, maxTextSize: 20 }),
      text('Уведомление', { Name: 'Text', Position: udim2(0, 48, 0, 0), Size: udim2(1, -58, 1, 0), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 15 }),
    ]),
  ]),
  ui.frame({ Name: 'EventBanner', AnchorPoint: v2(0.5, 0), Position: udim2(0.5, 0, 0, 112), Size: udim2(0, 520, 0, 96), BackgroundColor3: hex('#1a1030'), BackgroundTransparency: 0.1, Visible: false, ZIndex: 6 }, [
    ui.corner(16),
    ui.stroke({ Color: C.accent, Transparency: 0.35, Thickness: 1.6 }),
    text('⚡', { Name: 'EventIcon', Position: udim2(0, 18, 0, 12), Size: udim2(0, 54, 0, 54), TextColor3: C.gold, maxTextSize: 40 }),
    bold('СОБЫТИЕ', { Name: 'EventTitle', Position: udim2(0, 80, 0, 14), Size: udim2(1, -100, 0, 26), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 22 }),
    text('Описание', { Name: 'EventDesc', Position: udim2(0, 80, 0, 42), Size: udim2(1, -100, 0, 40), TextXAlignment: 'Left', TextWrapped: true, TextColor3: C.dim, maxTextSize: 15 }),
  ]),
  ui.frame({ Name: 'MonsterAlert', AnchorPoint: v2(0.5, 0), Position: udim2(0.5, 0, 0.24, 0), Size: udim2(0, 520, 0, 46), BackgroundColor3: hex('#3a0f1c'), BackgroundTransparency: 0.25, Visible: false, ZIndex: 6 }, [
    ui.corner(10),
    ui.stroke({ Color: C.danger, Transparency: 0.2, Thickness: 1.6 }),
    bold('ОНО РЯДОМ', { Name: 'MonsterName', Size: udim2(1, 0, 1, 0), TextColor3: C.danger, maxTextSize: 22 }),
  ]),
  ui.frame({ Name: 'PortalHint', AnchorPoint: v2(0.5, 0), Position: udim2(0.5, 0, 0.74, 0), Size: udim2(0, 620, 0, 40), BackgroundColor3: hex('#1d1436'), BackgroundTransparency: 0.3, Visible: false, ZIndex: 6 }, [
    ui.corner(10),
    text('🌀 Портал пробуждения открыт', { Name: 'Label', Size: udim2(1, 0, 1, 0), TextColor3: hex('#cfc0ff'), maxTextSize: 18 }),
  ]),
  ui.frame({ Name: 'HintLabel', AnchorPoint: v2(0.5, 1), Position: udim2(0.5, 0, 1, -210), Size: udim2(0, 760, 0, 28), BackgroundTransparency: 1, Visible: false, ZIndex: 6 }, [
    text('Подсказка', { Name: 'Label', Size: udim2(1, 0, 1, 0), TextColor3: hex('#c9c2ff'), TextTransparency: 0.15, maxTextSize: 17 }),
  ]),
  ui.frame({ Name: 'Crosshair', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.5, 0), Size: udim2(0, 6, 0, 6), BackgroundColor3: C.moon, BackgroundTransparency: 0.6, ZIndex: 6 }, [
    el('UICorner', { CornerRadius: udim(0.5, 0) }),
  ]),
  ui.frame({ Name: 'WhisperLabel', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.62, 0), Size: udim2(0, 800, 0, 40), BackgroundTransparency: 1, Visible: false, ZIndex: 6 }, [
    bold('', { Name: 'Label', Size: udim2(1, 0, 1, 0), TextColor3: hex('#d8c8ff'), TextTransparency: 0.2, maxTextSize: 26 }),
  ]),
  ui.frame({ Name: 'Jumpscare', Size: udim2(1, 1), BackgroundColor3: hex('#1a0008'), BackgroundTransparency: 1, Visible: false, ZIndex: 9 }, [
    text('👁️', { Name: 'JumpscareIcon', Size: udim2(1, 0, 0.55, 0), TextColor3: hex('#ff6b81'), maxTextSize: 220 }),
    bold('БЕГИ', { Name: 'JumpscareName', Position: udim2(0, 0, 0.58, 0), Size: udim2(1, 0, 0, 90), TextColor3: hex('#ffd0d6'), maxTextSize: 64 }),
  ]),
  // Диалог
  ui.frame({ Name: 'Dialog', Size: udim2(1, 1), BackgroundColor3: hex('#05060c'), BackgroundTransparency: 0.55, Visible: false, ZIndex: 8 }, [
    panel({ Name: 'Panel', Size: udim2(0, 940, 0, 402), AnchorPoint: v2(0.5, 1), Position: udim2(0.5, 0, 0.95, 0) }, [
      ui.frame({ Name: 'Portrait', Position: udim2(0, 26, 0, 24), Size: udim2(0, 110, 0, 110), BackgroundColor3: hex('#221a3d') }, [
        ui.corner(16),
        ui.stroke({ Color: C.accent, Transparency: 0.3, Thickness: 1.6 }),
        text('👤', { Name: 'Icon', Size: udim2(1, 0, 1, 0), TextColor3: C.moon, maxTextSize: 64 }),
      ]),
      bold('NPC', { Name: 'NpcName', Position: udim2(0, 156, 0, 22), Size: udim2(1, -190, 0, 32), TextXAlignment: 'Left', TextColor3: C.accent, maxTextSize: 26 }),
      text('Диалог', { Name: 'DialogText', Position: udim2(0, 156, 0, 60), Size: udim2(1, -190, 0, 90), TextXAlignment: 'Left', TextYAlignment: 'Top', TextWrapped: true, TextColor3: C.text, maxTextSize: 19 }),
      ui.scroll({ Name: 'Options', Position: udim2(0, 26, 0, 162), Size: udim2(1, -52, 0, 190), BackgroundTransparency: 1, CanvasSize: udim2(0, 0, 0, 0), AutomaticCanvasSize: 'Y', ScrollBarThickness: 5, ScrollingDirection: 'Y' }, [
        ui.list({ Padding: udim(0, 8), SortOrder: 'LayoutOrder' }),
        ui.padding(2),
        ui.button('', { Name: 'OptionTemplate', Visible: false, Size: udim2(1, 0, 0, 42), BackgroundColor3: hex('#1b1d38'), BackgroundTransparency: 0.85, Text: '', TextColor3: C.text, LayoutOrder: 1 }, [
          ui.corner(10),
          ui.stroke({ Color: C.accent, Transparency: 0.5, Thickness: 1.2 }),
          text('Вариант ответа', { Name: 'Label', Position: udim2(0, 16, 0, 0), Size: udim2(1, -32, 1, 0), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 17 }),
        ]),
      ]),
      button('CloseButton', '✕', { AnchorPoint: v2(1, 0), Position: udim2(1, -18, 0, 18), Size: udim2(0, 42, 0, 42), BackgroundColor3: hex('#2a1e3d') }),
    ]),
  ]),
  // Мобильные кнопки
  ui.frame({ Name: 'MobileControls', Size: udim2(1, 1), BackgroundTransparency: 1, Visible: false, ZIndex: 7 }, [
    mobileButton('SprintButton', '🏃', udim2(1, -110, 0.55, 0), hex('#2b2a55')),
    mobileButton('InteractButton', 'E', udim2(1, -110, 0.72, 0), hex('#3a2e6d')),
    mobileButton('FlashlightButton', '🔦', udim2(1, -200, 0.63, 0), hex('#2b2a55')),
    mobileButton('UseItemButton', '🧪', udim2(1, -200, 0.8, 0), hex('#2b2a55')),
  ]),
  // Лог заданий (TAB)
  ui.frame({ Name: 'QuestLog', Size: udim2(1, 1), BackgroundColor3: hex('#05060c'), BackgroundTransparency: 0.35, Visible: false, ZIndex: 7 }, [
    panel({ Name: 'Panel', Size: udim2(0, 860, 0, 620) }, [
      bold('ЗАДАНИЯ ЭТОГО СНА', { Position: udim2(0, 30, 0, 20), Size: udim2(1, -60, 0, 34), TextXAlignment: 'Left', TextColor3: C.moon, maxTextSize: 26 }),
      text('TAB — закрыть · ❗ обязательные · ⭐ дополнительные', { Position: udim2(0, 30, 0, 54), Size: udim2(1, -60, 0, 20), TextXAlignment: 'Left', TextColor3: C.dim, maxTextSize: 14 }),
      ui.scroll({ Name: 'QuestLogList', Position: udim2(0, 30, 0, 88), Size: udim2(1, -60, 1, -150), AutomaticCanvasSize: 'Y' }, [
        ui.list({ Padding: udim(0, 10), SortOrder: 'LayoutOrder' }),
        ui.padding(4),
        ui.frame({ Name: 'QuestEntryTemplate', Visible: false, Size: udim2(1, -20, 0, 104), BackgroundColor3: hex('#14162c'), BackgroundTransparency: 0.1, LayoutOrder: 1 }, [
          ui.corner(12),
          ui.frame({ Name: 'Accent', Position: udim2(0, 0, 0, 12), Size: udim2(0, 4, 0, 80), BackgroundColor3: C.danger }),
          bold('Задание', { Name: 'Title', Position: udim2(0, 18, 0, 12), Size: udim2(1, -36, 0, 24), TextXAlignment: 'Left', TextColor3: C.text, maxTextSize: 18 }),
          text('Цели', { Name: 'Body', Position: udim2(0, 18, 0, 40), Size: udim2(1, -36, 0, 54), TextXAlignment: 'Left', TextYAlignment: 'Top', TextWrapped: true, TextColor3: C.dim, maxTextSize: 14 }),
        ]),
      ]),
      bold('', { Name: 'EmptyLabel', Position: udim2(0, 30, 0.5, 0), Size: udim2(1, -60, 0, 30), TextColor3: C.dim, maxTextSize: 18 }),
      button('QuestLogClose', 'ЗАКРЫТЬ', { AnchorPoint: v2(1, 1), Position: udim2(1, -26, 1, -20), Size: udim2(0, 200, 0, 44), BackgroundColor3: hex('#2a1e3d') }),
    ]),
  ]),
]);

/* ------------------------------------------------------------------ */
/* 9. Катсцены                                                         */
/* ------------------------------------------------------------------ */
export const cutsceneGui = () => screenGui('CutsceneGUI', { DisplayOrder: 20, Enabled: true, IgnoreGuiInset: true }, [
  ui.frame({ Name: 'LetterboxTop', AnchorPoint: v2(0, 0), Position: udim2(0, 0, 0, 0), Size: udim2(1, 0, 0, 0), BackgroundColor3: hex('#000000'), ZIndex: 3 }),
  ui.frame({ Name: 'LetterboxBottom', AnchorPoint: v2(0, 1), Position: udim2(0, 0, 1, 0), Size: udim2(1, 0, 0, 0), BackgroundColor3: hex('#000000'), ZIndex: 3 }),
  bold('ЗАСЫПАНИЕ...', { Name: 'DreamText', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.72, 0), Size: udim2(0, 900, 0, 56), TextColor3: C.moon, TextTransparency: 1, Visible: false, ZIndex: 4, maxTextSize: 40 }),
  text('', { Name: 'Subtitle', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.8, 0), Size: udim2(0, 800, 0, 30), TextColor3: C.dim, TextTransparency: 0.1, ZIndex: 4, maxTextSize: 20 }),
  ui.frame({ Name: 'Fade', Size: udim2(1, 1), BackgroundColor3: hex('#000000'), BackgroundTransparency: 1, ZIndex: 8, Visible: false }),
  ui.frame({ Name: 'WhiteFlash', Size: udim2(1, 1), BackgroundColor3: hex('#ffffff'), BackgroundTransparency: 1, ZIndex: 9, Visible: false }),
]);

/* ------------------------------------------------------------------ */
/* 10. Экран результата                                                */
/* ------------------------------------------------------------------ */
export const resultGui = () => screenGui('ResultGUI', { DisplayOrder: 30, Enabled: false }, [
  backdrop({ BackgroundTransparency: 0.05 }),
  ui.frame({ Name: 'Card', AnchorPoint: v2(0.5, 0.5), Position: udim2(0.5, 0, 0.5, 0), Size: udim2(0, 780, 0, 600), BackgroundColor3: hex('#0f1122'), BackgroundTransparency: 0.05 }, [
    ui.corner(26),
    ui.stroke({ Color: C.accent, Transparency: 0.35, Thickness: 2 }),
    ui.gradient([hex('#1a1d3a'), hex('#080913')], { Rotation: 90 }),
    bold('🌟 ТЫ ПРОСНУЛСЯ!', { Name: 'OutcomeTitle', Position: udim2(0, 0, 0, 30), Size: udim2(1, 0, 0, 54), TextColor3: C.success, maxTextSize: 42 }),
    text('Кошмар остался позади.', { Name: 'OutcomeSubtitle', Position: udim2(0, 40, 0, 84), Size: udim2(1, -80, 0, 40), TextWrapped: true, TextColor3: C.dim, maxTextSize: 18 }),
    ui.frame({ Name: 'Stats', Position: udim2(0, 46, 0, 138), Size: udim2(1, -92, 0, 300), BackgroundTransparency: 1 }, [
      ui.list({ Padding: udim(0, 8), SortOrder: 'LayoutOrder' }),
      statRow('Сон', 'DreamValue', 1),
      statRow('Сложность', 'DifficultyValue', 2),
      statRow('Время выживания', 'TimeValue', 3),
      statRow('Выполнено заданий', 'QuestsValue', 4),
      statRow('Получено монет', 'CoinsValue', 5),
      statRow('Причина', 'ReasonValue', 6),
    ]),
    button('MenuButton', '🏠  В МЕНЮ', { Position: udim2(0, 46, 1, -86), Size: udim2(0, 320, 0, 56), BackgroundColor3: hex('#2a2450') }),
    button('RetryButton', '🔄  ЕЩЁ СОН', { Position: udim2(1, -366, 1, -86), Size: udim2(0, 320, 0, 56), BackgroundColor3: hex('#3c2e78') }),
  ]),
]);
