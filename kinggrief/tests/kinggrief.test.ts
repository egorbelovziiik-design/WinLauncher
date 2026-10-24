import { existsSync, rmSync } from 'node:fs';
import { describe, expect, it, beforeAll } from 'vitest';

// Чистая тестовая база до первого обращения к репозиторию.
for (const suffix of ['', '-wal', '-shm']) {
  const file = `./data/test-kinggrief.db${suffix}`;
  if (existsSync(file)) rmSync(file);
}

const catalog = await import('@/lib/catalog');
const pricing = await import('@/lib/relic-pricing');
const totp = await import('@/lib/totp');
const db = await import('@/lib/db');
const repo = await import('@/lib/repo');
const service = await import('@/lib/order-service');
const delivery = await import('@/lib/delivery');

beforeAll(() => {
  repo.getSettings();
});

describe('каталог и цены', () => {
  it('содержит 14 привилегий с зафиксированными ценами', () => {
    const ranks = repo.listProducts({ kind: 'rank', activeOnly: true });
    expect(ranks).toHaveLength(14);
    const price = (name: string) => ranks.find((r) => r.name === name)?.price;
    expect(price('PEGAS')).toBe(19);
    expect(price('COBRA')).toBe(39);
    expect(price('BUNNY')).toBe(59);
    expect(price('RABBIT')).toBe(89);
    expect(price('VAMPIRE')).toBe(99);
    expect(price('GOD')).toBe(149);
    expect(price('KING')).toBe(199);
    expect(price('D.MODER')).toBe(249);
    expect(price('D.ST.MODER')).toBe(349);
    expect(price('D.GLMODER')).toBe(449);
    expect(price('D.ML.ADMIN')).toBe(599);
    expect(price('D.ADMIN')).toBe(799);
    expect(price('OPKA')).toBe(999);
    expect(price('D.OWNER')).toBe(1499);
  });

  it('содержит 9 наборов кейсов и 9 пакетов риликов', () => {
    expect(repo.listProducts({ kind: 'case' })).toHaveLength(9);
    expect(repo.listProducts({ kind: 'relic' })).toHaveLength(9);
    expect(repo.listProducts({ kind: 'other' })).toHaveLength(4);
  });

  it('у каждой награды кейса есть описание, привилегии ведут на свою страницу', () => {
    const items = repo.listCaseItems();
    expect(items.length).toBeGreaterThan(20);
    for (const item of items) {
      expect(item.description.length).toBeGreaterThan(10);
    }
    const rankReward = items.find((item) => item.name === 'Привилегия KING')!;
    expect(rankReward.link).toBe('/privileges/king');
    expect(rankReward.description).toContain('KING');
  });

  it('сумма шансов каждого кейса равна 100%', () => {
    for (const group of repo.listCaseGroups()) {
      expect(repo.caseChanceTotal(group.id)).toBeCloseTo(100, 5);
    }
  });

  it('все товары на сайте имеют положительную цену и цвет', () => {
    for (const product of repo.listProducts()) {
      expect(product.price).toBeGreaterThan(0);
      expect(product.color).toMatch(/^#[0-9A-Fa-f]{6}$/);
    }
  });
});

describe('калькулятор риликов', () => {
  const config = pricing.parseRelicPricing(catalog.DEFAULT_SETTINGS.relic_pricing);

  it('возвращает точные значения в опорных точках', () => {
    expect(pricing.estimateRelicPrice(1000, config)).toBe(19);
    expect(pricing.estimateRelicPrice(10000, config)).toBe(119);
    expect(pricing.estimateRelicPrice(100000, config)).toBe(799);
  });

  it('интерполирует между точками', () => {
    expect(pricing.estimateRelicPrice(12000, config)).toBe(139);
  });

  it('не опускается ниже минимальной цены и не выходит за максимум', () => {
    expect(pricing.estimateRelicPrice(1, config)).toBeGreaterThanOrEqual(config.minPrice);
    expect(pricing.estimateRelicPrice(500000, config)).toBe(799);
  });

  it('цена за 1000 снижается с ростом пакета', () => {
    const small = pricing.pricePerThousand(1000, 19);
    const large = pricing.pricePerThousand(100000, 799);
    expect(large).toBeLessThan(small);
  });

  it('переживает битый JSON и использует запасной прайс', () => {
    expect(pricing.parseRelicPricing('{broken').points).toEqual(pricing.FALLBACK_PRICING.points);
  });
});

describe('расчёт корзины и промокоды', () => {
  it('считает сумму по серверным ценам', () => {
    const result = service.priceCart([
      { productId: 'rank-king', quantity: 1 },
      { productId: 'relic-25000', quantity: 2 },
    ]);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.subtotal).toBe(199 + 249 * 2);
  });

  it('запрещает две привилегии в одном заказе', () => {
    const result = service.priceCart([{ productId: 'rank-king', quantity: 2 }]);
    expect(result.ok).toBe(false);
  });

  it('отклоняет неизвестный товар', () => {
    const result = service.priceCart([{ productId: 'does-not-exist', quantity: 1 }]);
    expect(result.ok).toBe(false);
  });

  it('применяет процентный промокод и лимит скидки', () => {
    repo.savePromo({ code: 'TEST20', type: 'percent', value: 20, minTotal: 100, maxDiscount: 50, active: true });
    const applied = repo.applyPromo('TEST20', 1000, ['relic']);
    expect(applied.ok).toBe(true);
    if (applied.ok) expect(applied.discount).toBe(50);
  });

  it('не применяет промокод ниже минимальной суммы', () => {
    const applied = repo.applyPromo('TEST20', 50, ['relic']);
    expect(applied.ok).toBe(false);
  });

  it('учитывает ограничение по категориям', () => {
    repo.savePromo({ code: 'ONLYRANK', type: 'fixed', value: 100, minTotal: 0, categories: ['rank'], active: true });
    expect(repo.applyPromo('ONLYRANK', 500, ['relic']).ok).toBe(false);
    expect(repo.applyPromo('ONLYRANK', 500, ['rank']).ok).toBe(true);
  });

  it('блокирует промокод после исчерпания лимита', () => {
    repo.savePromo({ code: 'LIMIT1', type: 'percent', value: 10, minTotal: 0, usageLimit: 1, active: true });
    repo.registerPromoUse('LIMIT1', 'order-1', 10);
    expect(repo.applyPromo('LIMIT1', 100, ['relic']).ok).toBe(false);
  });

  it('пересчитывает итог с промокодом KING10', () => {
    const result = service.priceCart([{ productId: 'rank-king', quantity: 1 }], 'KING10');
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.discount).toBe(19);
    expect(result.total).toBe(180);
  });
});

describe('заказы и платежи', () => {
  it('создаёт заказ, подтверждает оплату и очередь выдачи', async () => {
    const created = await service.createCheckout({
      nickname: 'TestPlayer',
      items: [
        { productId: 'rank-king', quantity: 1 },
        { productId: 'relic-10000', quantity: 1 },
      ],
      promoCode: 'KING10',
    });
    expect(created.order.total).toBe(199 + 119 - 31);
    expect(created.paymentUrl).toContain('/checkout/');
    expect(created.order.status).toBe('pending_payment');

    const wrongAmount = service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'mock-1',
      amount: 1,
    });
    expect(wrongAmount.ok).toBe(false);

    const paid = service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'mock-1',
      amount: created.order.total,
    });
    expect(paid.ok).toBe(true);

    const order = repo.getOrder(created.order.id)!;
    expect(['paid', 'delivering']).toContain(order.status);
    expect(order.paidAt).not.toBeNull();

    const deliveries = repo.listDeliveries({ orderId: order.id });
    expect(deliveries).toHaveLength(2);
    expect(deliveries.some((d) => d.command.includes('lp user TestPlayer parent add KING'))).toBe(true);
    expect(deliveries.some((d) => d.command.includes('relics add TestPlayer 10000'))).toBe(true);

    const again = service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'mock-1',
      amount: created.order.total,
    });
    expect(again.ok).toBe(true);
    expect(again.already).toBe(true);
    expect(repo.listDeliveries({ orderId: order.id })).toHaveLength(2);
  });

  it('не выдаёт заказ по чужой сумме', async () => {
    const created = await service.createCheckout({
      nickname: 'Cheater',
      items: [{ productId: 'relic-1000', quantity: 1 }],
    });
    const result = service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'x',
      amount: 1,
    });
    expect(result.ok).toBe(false);
    expect(repo.getOrder(created.order.id)!.status).toBe('pending_payment');
  });

  it('закрывает заказ после подтверждения всех задач выдачи', async () => {
    const created = await service.createCheckout({
      nickname: 'DeliveryHero',
      items: [{ productId: 'other-unban', quantity: 1 }],
    });
    service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'p',
      amount: created.order.total,
    });
    const [task] = repo.listDeliveries({ orderId: created.order.id });
    repo.updateDelivery(task.id, { status: 'delivered' });
    delivery.syncOrderDeliveryStatus(created.order.id);
    expect(repo.getOrder(created.order.id)!.status).toBe('delivered');
  });

  it('выдаёт несколько привилегий через parent set, оставляя старшую', async () => {
    const created = await service.createCheckout({
      nickname: 'RankStack',
      items: [
        { productId: 'rank-king', quantity: 1 },
        { productId: 'rank-god', quantity: 1 },
      ],
    });
    service.confirmPaidOrder({
      orderId: created.order.id,
      provider: 'mock',
      providerPaymentId: 'stack',
      amount: created.order.total,
    });
    const deliveries = repo.listDeliveries({ orderId: created.order.id });
    expect(deliveries).toHaveLength(2);
    expect(deliveries.every((d) => d.command.startsWith('lp user RankStack parent set '))).toBe(true);
    const god = deliveries.find((d) => d.command.includes('GOD'))!;
    const king = deliveries.find((d) => d.command.includes('KING'))!;
    expect(god.id).toBeTruthy();
    expect(king.id).toBeTruthy();
  });

  it('проверяет секрет моста', () => {
    expect(delivery.bridgeSecretMatches('test-bridge-secret')).toBe(true);
    expect(delivery.bridgeSecretMatches('wrong')).toBe(false);
    expect(delivery.bridgeSecretMatches(null)).toBe(false);
  });
});

describe('аккаунты игроков', () => {
  it('регистрирует игрока и отклоняет дубликаты', () => {
    const created = repo.createUser({ username: 'Acc_Player', email: 'acc@example.com', password: 'Password123' });
    expect(created.ok).toBe(true);
    if (!created.ok) return;
    expect(created.user.username).toBe('Acc_Player');
    expect(created.user.email).toBe('acc@example.com');

    const duplicateName = repo.createUser({ username: 'acc_player', email: 'other@example.com', password: 'Password123' });
    expect(duplicateName.ok).toBe(false);

    const duplicateEmail = repo.createUser({ username: 'Another_Player', email: 'ACC@example.com', password: 'Password123' });
    expect(duplicateEmail.ok).toBe(false);
  });

  it('проверяет пароль и пускает по нику или email', () => {
    const byName = repo.checkUserCredentials('Acc_Player', 'Password123');
    expect(byName?.username).toBe('Acc_Player');
    const byEmail = repo.checkUserCredentials('acc@example.com', 'Password123');
    expect(byEmail?.id).toBe(byName?.id);
    expect(repo.checkUserCredentials('Acc_Player', 'wrong')).toBeNull();
    expect(repo.checkUserCredentials('nobody', 'Password123')).toBeNull();
  });

  it('меняет email и пароль, проверяя занятость адреса', () => {
    const user = repo.checkUserCredentials('Acc_Player', 'Password123')!;
    repo.createUser({ username: 'Other_Acc', email: 'other-acc@example.com', password: 'Password123' });
    expect(repo.updateUserEmail(user.id, 'other-acc@example.com').ok).toBe(false);
    expect(repo.updateUserEmail(user.id, 'new-acc@example.com').ok).toBe(true);
    repo.updateUserPassword(user.id, 'NewPassword456');
    expect(repo.verifyUserPassword(user.id, 'NewPassword456')).toBe(true);
    expect(repo.checkUserCredentials('Acc_Player', 'Password123')).toBeNull();
    expect(repo.checkUserCredentials('Acc_Player', 'NewPassword456')?.id).toBe(user.id);
  });

  it('связывает заказ с аккаунтом и отдаёт историю', async () => {
    const user = repo.checkUserCredentials('Acc_Player', 'NewPassword456')!;
    const created = await service.createCheckout({
      nickname: 'Acc_Player',
      items: [{ productId: 'relic-1000', quantity: 1 }],
      userId: user.id,
    });
    expect(created.order.userId).toBe(user.id);
    const history = repo.listUserOrders(user.id);
    expect(history.some((order) => order.publicId === created.order.publicId)).toBe(true);
    expect(repo.userHasOrder(user.id, created.order.publicId)).toBe(true);
    expect(repo.countUsers()).toBeGreaterThanOrEqual(2);
    expect(repo.listUsers().some((u) => u.username === 'Acc_Player')).toBe(true);
  });
});

describe('безопасность', () => {
  it('хэширует пароль и проверяет его', () => {
    const hash = db.hashPassword('SuperSecret123');
    expect(hash).not.toContain('SuperSecret123');
    expect(db.verifyPassword('SuperSecret123', hash)).toBe(true);
    expect(db.verifyPassword('wrong', hash)).toBe(false);
  });

  it('проверяет TOTP-код и отклоняет неверный', () => {
    const secret = totp.generateTotpSecret();
    const code = totp.totpCode(secret);
    expect(totp.verifyTotp(secret, code)).toBe(true);
    expect(totp.verifyTotp(secret, '000000')).toBe(false);
    expect(totp.otpauthUrl(secret, 'admin')).toContain('otpauth://totp/');
  });

  it('хранит администратора и пишет аудит', () => {
    const admin = repo.checkAdminCredentials('admin', 'TestPassword-123');
    expect(admin).not.toBeNull();
    expect(repo.checkAdminCredentials('admin', 'nope')).toBeNull();
    repo.logAudit('admin', 'test_action', 'проверка журнала', '127.0.0.1');
    const entries = repo.listAudit(5);
    expect(entries[0].action).toBe('test_action');
  });

  it('сохраняет настройки и отдаёт их сайту', () => {
    repo.setSettings({ server_ip: 'test.kinggrief.net' });
    expect(repo.getSetting('server_ip')).toBe('test.kinggrief.net');
    repo.setSettings({ server_ip: 'play.kinggrief.net' });
  });
});

describe('контент и аналитика', () => {
  it('модерирует отзывы', () => {
    repo.createReview({ nickname: 'Tester', rating: 5, text: 'Отличный магазин, всё пришло быстро.' });
    const pending = repo.listReviews('pending');
    const review = pending.find((r) => r.nickname === 'Tester');
    expect(review).toBeDefined();
    repo.setReviewStatus(review!.id, 'approved');
    expect(repo.listReviews('approved').some((r) => r.id === review!.id)).toBe(true);
  });

  it('исключает демо-заказы из реальных метрик', () => {
    // Тестовый режим платежей помечает заказы как демо — в статистику они не попадают.
    const real = repo.revenueTotals(false);
    const withDemo = repo.revenueTotals(true);
    expect(real.paidOrders).toBe(0);
    expect(real.totalRevenue).toBe(0);
    expect(withDemo.paidOrders).toBeGreaterThan(0);
    expect(withDemo.totalRevenue).toBeGreaterThan(0);

    const series = repo.revenueSeries(7, true);
    expect(series).toHaveLength(7);
    expect(series[series.length - 1].day).toBe(new Date().toISOString().slice(0, 10));
    expect(series[series.length - 1].revenue).toBeGreaterThan(0);
    expect(repo.revenueSeries(7, false).every((point) => point.revenue === 0)).toBe(true);
  });

  it('публичные последние покупки не содержат email и ip', () => {
    const recent = repo.recentPublicOrders(5);
    for (const order of recent) {
      expect(Object.keys(order)).not.toContain('email');
      expect(Object.keys(order)).not.toContain('ip');
    }
  });
});
