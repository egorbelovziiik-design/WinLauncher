import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => { await page.goto('/'); });

test('home is explicitly a preview, Play never simulates Minecraft', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Твой мир. Твои правила.' })).toBeVisible();
  await expect(page.getByText('WINDOWS APP · WEB PREVIEW')).toBeVisible();
  await page.getByRole('button', { name: 'ИГРАТЬ', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Нативная функция Windows' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Настоящая игра — в настоящем приложении.')).toBeVisible();
  await expect(page.locator('.download-dock')).toHaveCount(0);
});

test('all primary pages navigate without client errors', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  const nav = page.getByLabel('Боковая панель');
  for (const [button, heading] of [
    ['Мои профили','Мои профили'], ['Версии Minecraft','Версии Minecraft'], ['Моды и сборки','Моды и сборки'],
    ['Миры и скриншоты','Миры и скриншоты'], ['Темы оформления','Найди свой биом'],
    ['Статистика','Твоя история в блоках'], ['Логи','Консоль и логи'], ['Настройки','Настрой всё под себя'],
  ]) {
    await nav.getByRole('button', { name: button, exact: true }).click();
    await expect(page.getByRole('heading', { name: heading, exact: true }).first()).toBeVisible();
  }
  expect(errors).toEqual([]);
});

test('theme preview is separate from apply; actual preferences survive reload', async ({ page }) => {
  await page.getByRole('button', { name: 'Темы оформления', exact: true }).click();
  await page.locator('.theme-tile').filter({ hasText: 'Nether' }).click();
  await expect(page.getByRole('heading', { name: 'Nether', exact: true })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'overworld');
  await expect(page.locator('.theme-preview > img')).toHaveAttribute('src', /nether/);
  await page.getByRole('button', { name: 'Применить тему', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'nether');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'nether');
  await expect(page.locator('.hero-image')).toHaveAttribute('src', /nether/);
});

test('effects switch really removes particles and persists', async ({ page }) => {
  await expect(page.locator('.hero-particles')).toHaveCount(1);
  await page.getByRole('button', { name: 'Темы оформления', exact: true }).click();
  await page.getByRole('switch', { name: 'Эффекты окружения' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-effects', 'false');
  await page.getByRole('button', { name: 'Главная', exact: true }).click();
  await expect(page.locator('.hero-particles')).toHaveCount(0);
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-effects', 'false');
});

test('English and Ukrainian navigation persist as real preferences', async ({ page }) => {
  await page.getByRole('button', { name: 'Настройки', exact: true }).click();
  await page.getByRole('combobox', { name: 'Язык интерфейса' }).selectOption('en');
  await expect(page.getByRole('button', { name: 'Home', exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Interface language' }).selectOption('uk');
  await expect(page.getByRole('button', { name: 'Головна', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'uk');
  await expect(page.getByRole('button', { name: 'Моди та збірки', exact: true })).toBeVisible();
});

test('profile editor is interactive but never invents a saved native profile', async ({ page }) => {
  await page.getByRole('button', { name: 'Мои профили', exact: true }).click();
  await page.getByRole('button', { name: 'Создать профиль', exact: true }).first().click();
  const dialog = page.getByRole('dialog', { name: 'Создать профиль' });
  await dialog.getByLabel('Название профиля').fill('Честный тест');
  await dialog.getByLabel('Загрузчик модов').selectOption('fabric');
  await dialog.getByRole('button', { name: 'Modded', exact: true }).click();
  await expect(dialog.getByRole('slider', { name: 'Максимум памяти Minecraft' })).toHaveValue('6144');
  await dialog.getByRole('button', { name: 'Создать профиль', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Нативная функция Windows' })).toBeVisible();
  await expect(page.locator('.profile-card')).toHaveCount(0);
});

test('catalog and CurseForge never display a fabricated dataset', async ({ page }) => {
  await page.getByRole('button', { name: 'Моды и сборки', exact: true }).click();
  await page.getByRole('textbox', { name: 'Поиск модов' }).fill('Sodium');
  await expect(page.locator('.project-card')).toHaveCount(0);
  await expect(page.getByText('В предпросмотре нет фейкового каталога или симуляции скачивания.')).toBeVisible();
  await page.getByRole('button', { name: 'CurseForge', exact: true }).click();
  await expect(page.locator('.intro-curseforge')).toBeVisible();
  await page.getByRole('button', { name: 'Установленные 0' }).click();
  await expect(page.getByRole('heading', { name: 'Здесь будут твои дополнения' })).toBeVisible();
});

test('no invented logs, sessions, playtime or process PID', async ({ page }) => {
  await page.getByRole('button', { name: 'Логи', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Здесь нет выдуманных логов' })).toBeVisible();
  await expect(page.locator('.log-line')).toHaveCount(0);
  await expect(page.getByText('Minecraft не запущен', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Статистика', exact: true }).click();
  await expect(page.locator('.stat-card h2')).toHaveText(['0 с','0 с','0','0']);
  await expect(page.locator('.session-row')).toHaveCount(0);
});

test('secrets input is disabled in the web preview', async ({ page }) => {
  await page.getByRole('button', { name: 'Настройки', exact: true }).click();
  await page.getByRole('button', { name: 'Интеграции', exact: true }).click();
  await expect(page.getByPlaceholder('Вставьте действительный ключ CurseForge')).toBeDisabled();
  await expect(page.getByText('НЕ ПОДКЛЮЧЁН', { exact: true })).toBeVisible();
});

test('command palette supports keyboard search and navigation', async ({ page }) => {
  await page.keyboard.press('Control+k');
  const dialog = page.getByRole('dialog', { name: 'Быстрый переход' });
  await expect(dialog).toBeVisible();
  await dialog.getByPlaceholder('Страница или профиль…').fill('темы');
  await dialog.getByRole('button', { name: 'Темы оформления', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Найди свой биом' })).toBeVisible();
  await expect(dialog).not.toBeVisible();
});

test('notifications start empty and do not fake successful downloads', async ({ page }) => {
  await page.getByRole('button', { name: 'Уведомления', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Пока всё спокойно' })).toBeVisible();
  await expect(page.locator('.notification-success')).toHaveCount(0);
});

test('minimum desktop width has no horizontal overflow and accessible Play', async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 760 });
  await expect(page.getByRole('button', { name: 'ИГРАТЬ', exact: true })).toBeInViewport();
  const bounds = await page.evaluate(() => ({ page: document.body.scrollWidth, window: innerWidth }));
  expect(bounds.page).toBeLessThanOrEqual(bounds.window);
  await expect(page.getByLabel('Боковая панель')).toBeInViewport();
});

test('offline account form validates nicknames and never fakes a native account in preview', async ({ page }) => {
  await page.getByRole('button', { name: /Войти в аккаунт/ }).click();
  await page.getByRole('button', { name: 'Добавить офлайн-аккаунт', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Добавить офлайн-аккаунт' });
  const create = dialog.getByRole('button', { name: 'Создать офлайн-аккаунт' });
  await expect(create).toBeDisabled();
  await dialog.getByLabel('Никнейм', { exact: true }).fill('Bad Name');
  await expect(create).toBeDisabled();
  await dialog.getByLabel('Никнейм', { exact: true }).fill('Player_123');
  await expect(create).toBeEnabled();
  await expect(dialog.getByText(/Это не Microsoft-аккаунт/)).toBeVisible();
  await create.click();
  await expect(page.getByRole('dialog', { name: 'Нативная функция Windows' })).toBeVisible();
  await expect(page.locator('.account-card')).toHaveCount(0);
});
