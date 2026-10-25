#!/usr/bin/env node
/**
 * KINGGRIEF — end-to-end проверка работающего сервера по HTTP.
 *
 * Запуск:  BASE_URL=http://127.0.0.1:3000 ADMIN_PASSWORD=... node scripts/e2e.mjs
 * Требования: сервер запущен в тестовом режиме (PAYMENT_PROVIDER=mock, PAYMENTS_TEST_MODE=true).
 * Скрипт ничего не мокает: он ходит в реальные API-роуты и проверяет реальную БД.
 */
import process from 'node:process';

const BASE = (process.env.BASE_URL || 'http://127.0.0.1:3000').replace(/\/$/, '');
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'KingGrief-2026';
const BRIDGE_SECRET = process.env.MINECRAFT_BRIDGE_SECRET || 'dev-bridge-secret';

/**
 * У каждого прогона свой «IP» в x-forwarded-for: лимиты частоты не мешают повторным
 * запускам и не конфликтуют с ручными проверками на том же сервере.
 */
const E2E_IP = process.env.E2E_IP || `10.${Math.floor(Math.random() * 250) + 1}.${Math.floor(Math.random() * 250) + 1}.${Math.floor(Math.random() * 250) + 1}`;

const jar = new Map();
let passed = 0;
const failures = [];

function log(message) {
  process.stdout.write(`${message}\n`);
}

function ok(name, extra = '') {
  passed += 1;
  log(`  ✓ ${name}${extra ? ` — ${extra}` : ''}`);
}

function fail(name, detail) {
  failures.push(`${name}: ${detail}`);
  log(`  ✗ ${name} — ${detail}`);
}

function assert(name, condition, detail = '') {
  if (condition) ok(name, detail);
  else fail(name, detail || 'условие не выполнено');
  return Boolean(condition);
}

function cookieHeader() {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
}

async function call(method, path, { body, headers = {}, auth = true, raw = false } = {}) {
  const requestHeaders = { 'x-forwarded-for': E2E_IP, ...headers };
  if (body !== undefined) requestHeaders['content-type'] = 'application/json';
  if (auth && jar.size > 0) requestHeaders.cookie = cookieHeader();
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  });
  for (const cookie of response.headers.getSetCookie?.() ?? []) {
    const [pair] = cookie.split(';');
    const index = pair.indexOf('=');
    const name = pair.slice(0, index).trim();
    const value = pair.slice(index + 1).trim();
    if (value === '' || /max-age=0/i.test(cookie)) jar.delete(name);
    else jar.set(name, value);
  }
  if (raw) return response;
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: response.status, text, json, headers: response.headers };
}

function csrf() {
  return jar.get('kg_csrf') || '';
}

async function main() {
  log(`\nKINGGRIEF E2E → ${BASE}\n`);

  log('1. Публичные страницы');
  const publicPages = [
    '/',
    '/privileges',
    '/privileges/rank-king',
    '/privileges/rank-downer',
    '/cases',
    '/cases/mega',
    '/relics',
    '/other',
    '/cart',
    '/checkout',
    '/support',
    '/rules',
    '/reviews',
    '/profile',
    '/not-a-real-page',
  ];
  for (const page of publicPages) {
    const response = await call('GET', page, { auth: false, raw: true });
    const expected = page === '/not-a-real-page' ? 404 : 200;
    assert(`GET ${page} = ${expected}`, response.status === expected, `получено ${response.status}`);
  }

  log('\n2. Защита админки');
  const adminAnon = await call('GET', '/admin', { auth: false, raw: true });
  assert('/admin без сессии → редирект на login', [307, 302, 308].includes(adminAnon.status), `получено ${adminAnon.status}`);
  const apiAnon = await call('GET', '/api/admin/orders', { auth: false });
  assert('/api/admin/orders без сессии → 401', apiAnon.status === 401, `получено ${apiAnon.status}`);

  log('\n3. Вход администратора');
  const wrong = await call('POST', '/api/admin/login', {
    auth: false,
    body: { username: ADMIN_USERNAME, password: 'definitely-wrong-password' },
  });
  assert('неверный пароль отклонён', wrong.status === 401, `получено ${wrong.status}`);
  const login = await call('POST', '/api/admin/login', {
    auth: false,
    body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
  });
  assert('вход с верным паролем', login.status === 200 && login.json?.ok === true, `получено ${login.status}`);
  assert('выдан CSRF-cookie', Boolean(csrf()));

  const adminPages = [
    '/admin',
    '/admin/orders',
    '/admin/products',
    '/admin/cases',
    '/admin/promos',
    '/admin/customers',
    '/admin/analytics',
    '/admin/reviews',
    '/admin/design',
    '/admin/settings',
    '/admin/security',
  ];
  for (const page of adminPages) {
    const response = await call('GET', page, { raw: true });
    assert(`GET ${page} = 200`, response.status === 200, `получено ${response.status}`);
  }

  log('\n4. CSRF-защита');
  const noCsrf = await call('POST', '/api/admin/settings', { body: { settings: { online_base: '300' } } });
  assert('запись без CSRF-токена отклонена', noCsrf.status === 403, `получено ${noCsrf.status}`);
  const withCsrf = await call('POST', '/api/admin/settings', {
    body: { settings: { online_base: '312' } },
    headers: { 'x-csrf-token': csrf() },
  });
  assert('запись с CSRF-токеном принята', withCsrf.status === 200, `получено ${withCsrf.status}`);

  log('\n5. Серверный расчёт корзины');
  const price = await call('POST', '/api/price', {
    auth: false,
    body: { items: [{ productId: 'rank-king', quantity: 1 }], promoCode: 'KING10' },
  });
  assert('цена KING с промокодом KING10 = 180 ₽', price.json?.total === 180, `получено ${price.json?.total}`);
  const badPrice = await call('POST', '/api/price', {
    auth: false,
    body: { items: [{ productId: 'rank-does-not-exist', quantity: 1 }] },
  });
  assert('неизвестный товар отклонён', badPrice.status === 400, `получено ${badPrice.status}`);
  const greedyQty = await call('POST', '/api/price', {
    auth: false,
    body: { items: [{ productId: 'rank-king', quantity: 99 }] },
  });
  assert('99 привилегий в одном заказе отклонены', greedyQty.status === 400, `получено ${greedyQty.status}`);

  log('\n6. Заказ и тестовая оплата');
  const order = await call('POST', '/api/orders', {
    auth: false,
    body: {
      nickname: 'E2E_Player',
      items: [
        { productId: 'rank-king', quantity: 1 },
        { productId: 'case-mega-2', quantity: 1 },
      ],
      promoCode: 'KING10',
      agree: true,
    },
  });
  if (!assert('заказ создан', order.status === 201 && Boolean(order.json?.publicId), `получено ${order.status} ${order.text?.slice(0, 160)}`)) {
    return;
  }
  const publicId = order.json.publicId;
  assert('сумма заказа положительная', order.json.total > 0, `${order.json.total} ₽`);
  assert('тестовый режим промаркирован', order.json.demo === true);

  const beforePay = await call('GET', `/api/orders/${publicId}`, { auth: false });
  assert('до оплаты статус pending_payment', beforePay.json?.status === 'pending_payment', beforePay.json?.status);

  const payment = await call('POST', '/api/payments/mock/confirm', { auth: false, body: { publicId } });
  assert('тестовая оплата подтверждена', payment.status === 200 && payment.json?.ok === true, `получено ${payment.status}`);

  const afterPay = await call('GET', `/api/orders/${publicId}`, { auth: false });
  assert('после оплаты созданы задачи выдачи', ['paid', 'delivering'].includes(afterPay.json?.status), afterPay.json?.status);

  log('\n7. Мост Minecraft');
  const badPoll = await call('POST', '/api/minecraft/poll', {
    auth: false,
    body: { online: 1 },
  });
  assert('poll без секрета → 401', badPoll.status === 401, `получено ${badPoll.status}`);
  const poll = await call('POST', '/api/minecraft/poll', {
    auth: false,
    headers: { 'x-bridge-secret': BRIDGE_SECRET },
    body: { online: 312, max: 500, tps: 19.8, version: 'Paper 1.20.6', serverId: 'main' },
  });
  assert('poll с секретом → 200', poll.status === 200, `получено ${poll.status}`);
  const commands = poll.json?.commands ?? [];
  assert('очередь выдала команды', commands.length > 0, `${commands.length} шт.`);
  const ours = commands.filter((command) => command.orderId === publicId || command.nickname === 'E2E_Player');
  assert('в очереди есть команды нашего заказа', ours.length >= 1, `${ours.length} шт.`);

  if (commands.length > 0) {
    const ack = await call('POST', '/api/minecraft/ack', {
      auth: false,
      headers: { 'x-bridge-secret': BRIDGE_SECRET },
      body: { results: commands.map((command) => ({ id: command.id, ok: true })) },
    });
    assert('ack подтвердил выполнение', ack.status === 200 && ack.json?.processed === commands.length, `обработано ${ack.json?.processed}`);
  }

  const delivered = await call('GET', `/api/orders/${publicId}`, { auth: false });
  assert('заказ закрыт как выданный', delivered.json?.status === 'delivered', delivered.json?.status);

  const status = await call('GET', '/api/status', { auth: false });
  assert('/api/status отвечает данными моста', status.status === 200 && status.json?.source === 'bridge', `${status.json?.source} / ${status.json?.online} онлайн`);

  log('\n8. Админ-API и экспорт');
  const ordersList = await call('GET', `/api/admin/orders?q=${encodeURIComponent(publicId)}`);
  assert('поиск заказа в админке работает', ordersList.status === 200 && JSON.stringify(ordersList.json).includes(publicId), `получено ${ordersList.status}`);
  const exportResponse = await call('GET', '/api/admin/export', { raw: true });
  const exportBytes = new Uint8Array(await exportResponse.arrayBuffer());
  const exportText = new TextDecoder('utf-8').decode(exportBytes);
  const contentType = exportResponse.headers.get('content-type') || '';
  assert('CSV-экспорт доступен', exportResponse.status === 200 && contentType.includes('csv'), `получено ${exportResponse.status} ${contentType}`);
  assert('CSV содержит заголовок заказов', exportText.includes('Номер') && exportText.includes('Никнейм'));
  assert('CSV в UTF-8 с BOM', exportBytes[0] === 0xef && exportBytes[1] === 0xbb && exportBytes[2] === 0xbf);

  const logout = await call('POST', '/api/admin/logout', { body: {} });
  assert('выход из админки', logout.status === 200, `получено ${logout.status}`);
  const afterLogout = await call('GET', '/api/admin/orders', { auth: false });
  assert('после выхода API закрыт', afterLogout.status === 401, `получено ${afterLogout.status}`);

  log('\n9. Аккаунты игроков');
  const loginPage = await call('GET', '/login', { auth: false, raw: true });
  assert('страница входа доступна', loginPage.status === 200, `получено ${loginPage.status}`);
  const registerPage = await call('GET', '/register', { auth: false, raw: true });
  assert('страница регистрации доступна', registerPage.status === 200, `получено ${registerPage.status}`);
  const accountAnon = await call('GET', '/account', { auth: false, raw: true });
  assert('/account без входа → редирект', [307, 302, 308].includes(accountAnon.status), `получено ${accountAnon.status}`);

  const nick = `E2E_${Date.now().toString(36).slice(-6)}`;
  const email = `${nick.toLowerCase()}@example.com`;
  const weak = await call('POST', '/api/auth/register', {
    auth: false,
    body: { username: nick, email, password: 'short', confirm: 'short', agree: true },
  });
  assert('слабый пароль отклонён', weak.status === 400, `получено ${weak.status}`);

  const register = await call('POST', '/api/auth/register', {
    body: { username: nick, email, password: 'Password123', confirm: 'Password123', agree: true },
  });
  assert('регистрация аккаунта', register.status === 201 && register.json?.user?.username === nick, `получено ${register.status}`);

  const me = await call('GET', '/api/auth/me');
  assert('сессия игрока активна', me.status === 200 && me.json?.user?.username === nick, `получено ${me.status}`);
  const account = await call('GET', '/account', { raw: true });
  assert('/account открывается после регистрации', account.status === 200, `получено ${account.status}`);

  const duplicate = await call('POST', '/api/auth/register', {
    body: { username: nick, email: `other-${email}`, password: 'Password123', confirm: 'Password123', agree: true },
  });
  assert('повторная регистрация ника отклонена', duplicate.status === 409, `получено ${duplicate.status}`);

  const userOrder = await call('POST', '/api/orders', {
    body: { nickname: nick, items: [{ productId: 'rank-god', quantity: 1 }], agree: true },
  });
  assert('заказ из аккаунта создан', userOrder.status === 201, `получено ${userOrder.status}`);
  await call('POST', '/api/payments/mock/confirm', { body: { publicId: userOrder.json.publicId } });
  const accountHtml = await (await call('GET', '/account', { raw: true })).text();
  assert('заказ виден в личном кабинете', accountHtml.includes(userOrder.json.publicId));

  const logoutUser = await call('POST', '/api/auth/logout', { body: {} });
  assert('выход из аккаунта', logoutUser.status === 200);
  const meAfter = await call('GET', '/api/auth/me');
  assert('после выхода сессия игрока закрыта', meAfter.json?.user === null);

  const badLogin = await call('POST', '/api/auth/login', { body: { login: nick, password: 'WrongPass1' } });
  assert('неверный пароль игрока отклонён', badLogin.status === 401, `получено ${badLogin.status}`);
  const goodLogin = await call('POST', '/api/auth/login', { body: { login: email, password: 'Password123' } });
  assert('вход по email работает', goodLogin.status === 200 && goodLogin.json?.user?.username === nick, `получено ${goodLogin.status}`);
  const profile = await call('PATCH', '/api/auth/profile', { body: { email: `updated-${email}` } });
  assert('смена email в профиле', profile.status === 200, `получено ${profile.status}`);

  log('\n10. Cookie админской сессии');
  const rawLogin = await fetch(`${BASE}/api/admin/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD }),
  });
  const sessionCookie = (rawLogin.headers.getSetCookie?.() ?? []).find((c) => c.startsWith('kg_admin=')) ?? '';
  assert('админская cookie выдана', sessionCookie.length > 0);
  const insecureHttp = BASE.startsWith('http://');
  assert(
    insecureHttp ? 'на http флаг Secure не ставится' : 'на https cookie защищена Secure',
    insecureHttp ? !/;\s*secure/i.test(sessionCookie) : /;\s*secure/i.test(sessionCookie),
    sessionCookie.slice(0, 120),
  );
  assert('cookie сессии недоступна из JS (HttpOnly)', /httponly/i.test(sessionCookie), sessionCookie.slice(0, 80));

  // Тот же вход, но как будто запрос пришёл через HTTPS-прокси (превью в iframe, Vercel).
  const proxied = await fetch(`${BASE}/api/admin/login`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-forwarded-proto': 'https',
      'x-forwarded-host': 'preview.example.com',
      'x-forwarded-for': E2E_IP,
    },
    body: JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD }),
  });
  const proxiedCookie = (proxied.headers.getSetCookie?.() ?? []).find((c) => c.startsWith('kg_admin=')) ?? '';
  assert('за HTTPS-прокси cookie уходит с Secure', /;\s*secure/i.test(proxiedCookie), proxiedCookie.slice(0, 120));
  assert('за HTTPS-прокси SameSite=None (работает в iframe)', /samesite=none/i.test(proxiedCookie), proxiedCookie.slice(0, 120));
  assert('cookie помечена Partitioned (CHIPS)', /partitioned/i.test(proxiedCookie), proxiedCookie.slice(0, 120));

  log(`\nИтог: ${passed} проверок пройдено, ${failures.length} с ошибками.`);
  if (failures.length > 0) {
    log('\nОшибки:');
    failures.forEach((failure) => log(` - ${failure}`));
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(`\nE2E прерван: сервер ${BASE} недоступен (${error.message}).`);
  console.error('Запустите сервер: npm run dev (или docker compose up) и повторите.');
  process.exitCode = 1;
});
