import Link from 'next/link';
import { AlertTriangle, Check, Download, Server, ShieldCheck } from 'lucide-react';
import { getSettings, listDemoOrders } from '@/lib/repo';
import { cardMethodsEnabled, convertForMethod, maskPan, methodEnabled, payoutCardPan, payoutPanFor, payoutChannel, payoutChannelTitle } from '@/lib/payments';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { SettingsForm, type SettingField } from '@/components/admin/settings-form';
import { AdminCard } from '@/components/admin/kit';
import { DemoCard } from '@/components/admin/demo-card';
import { CopyButton } from '@/components/ui';

export const dynamic = 'force-dynamic';

const PAYMENT: SettingField[] = [
  {
    key: 'kassa_provider',
    label: 'Платёжный провайдер',
    type: 'select',
    hint: 'Ключи задаются в .env. Провайдер mock не списывает деньги.',
    options: [
      { value: 'mock', label: 'Mock (демо-оплата)' },
      { value: 'yookassa', label: 'ЮKassa' },
      { value: 'robokassa', label: 'Robokassa' },
    ],
  },
  { key: 'kassa_test_mode', label: 'Тестовый режим', type: 'toggle', hint: 'Демо-оплата и тестовые пароли Robokassa.' },
  { key: 'kassa_label', label: 'Название в описании платежа', span: 2 },
];

const DELIVERY: SettingField[] = [
  {
    key: 'online_mode',
    label: 'Режим выдачи',
    type: 'select',
    options: [
      { value: 'bridge', label: 'Автоматически через плагин (bridge)' },
      { value: 'manual', label: 'Вручную администратором' },
    ],
    hint: 'В режиме bridge задачи уходят плагину при его опросе.',
  },
  { key: 'delivery_commands', label: 'Команды выдачи', type: 'commands', span: 2, hint: 'Плейсхолдеры: {player}, {rank}, {amount}, {case}, {order}, {product}.' },
];

const SHOP: SettingField[] = [
  { key: 'reviews_enabled', label: 'Приём отзывов', type: 'toggle', hint: 'Отзывы публикуются после модерации.' },
  { key: 'maintenance', label: 'Режим обслуживания', type: 'toggle', hint: 'Показывает статус на сайте (покупка не блокируется).' },
  { key: 'admin_note', label: 'Заметка администратора', type: 'textarea', span: 2, rows: 2 },
  { key: 'relic_pricing', label: 'Формула риликов (JSON)', type: 'json', span: 2, rows: 9, hint: 'points: [[количество, цена]]. Линейная интерполяция между точками.' },
];

export default async function AdminSettingsPage() {
  await cloudSyncLocalDb({ includeOrders: true });
  const settings = getSettings();
  const demoCount = listDemoOrders(500).length;
  const yookassaConfigured = Boolean(process.env.YOOKASSA_SHOP_ID && process.env.YOOKASSA_SECRET_KEY);
  const robokassaConfigured = Boolean(process.env.ROBOKASSA_LOGIN && process.env.ROBOKASSA_PASSWORD1);
  const bridgeConfigured = Boolean(process.env.MINECRAFT_BRIDGE_SECRET);
  const cardOk = cardMethodsEnabled();
  const cardMasked = maskPan(payoutCardPan());
  const sberOk = methodEnabled('sber_ru');
  const sberMasked = maskPan(payoutPanFor('sber_ru'));
  const sampleUah = convertForMethod(1000, 'visa_ua');
  const sampleEur = convertForMethod(1000, 'visa_eu');
  const endpoints = ['/api/minecraft/poll', '/api/minecraft/ack'];
  const site = process.env.NEXT_PUBLIC_SITE_URL || 'https://kinggrief.vercel.app';

  return (
    <div className="space-y-4">
      <div>
        <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">конфигурация</p>
        <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Настройки</h1>
        <p className="mt-1 text-[0.76rem] text-white/35">Оплата, выдача, поведение магазина и демо-данные.</p>
      </div>

      <SettingsForm title="Оплата" description="Провайдер и тестовый режим. Секретные ключи хранятся только в .env." fields={PAYMENT} values={settings} cardId="payments" />

      <AdminCard title="Состояние интеграций" description="Проверка переменных окружения сервера">
        <div className="grid gap-2 sm:grid-cols-3">
          {[
            { label: 'ЮKassa', ok: yookassaConfigured, keys: 'YOOKASSA_SHOP_ID + SECRET_KEY' },
            { label: 'Robokassa', ok: robokassaConfigured, keys: 'ROBOKASSA_LOGIN + PASSWORD1' },
            { label: 'Minecraft bridge', ok: bridgeConfigured, keys: 'MINECRAFT_BRIDGE_SECRET' },
            { label: 'Visa UA / EU · счёт', ok: cardOk, keys: 'PAYOUT_CARD_PAN' },
            { label: 'Сбербанк · счёт', ok: sberOk, keys: 'PAYOUT_SBER_PAN' },
          ].map((item) => (
            <div key={item.label} className={`rounded-lg border px-3 py-2 ${item.ok ? 'border-emerald/25 bg-emerald/[0.06]' : 'border-amber/25 bg-amber/[0.06]'}`}>
              <p className="flex items-center gap-1.5 text-[0.74rem] font-bold text-white">
                {item.ok ? <Check size={12} className="text-emerald" /> : <AlertTriangle size={12} className="text-amber" />}
                {item.label}
              </p>
              <p className="mt-0.5 font-mono text-[0.56rem] text-white/35">{item.ok ? 'настроено' : item.keys}</p>
            </div>
          ))}
        </div>
        {process.env.PAYMENT_PROVIDER && (
          <p className="mt-3 rounded-lg border border-amber/25 bg-amber/[0.06] px-3 py-2 text-[0.7rem] text-amber/90">
            В окружении задан PAYMENT_PROVIDER={process.env.PAYMENT_PROVIDER}. Переменная имеет приоритет над настройкой в панели.
          </p>
        )}
      </AdminCard>

      <AdminCard title="Реквизиты приёма платежей (Visa Украина / Visa Europe)" description="Хранятся в зашифрованной server-env Vercel; на сайте не показываются и не упоминаются">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2">
            <span className="text-[0.72rem] text-white/50">Счёт получения (маска, только для админа):</span>
            <code className="font-mono text-[0.78rem] font-bold tracking-[0.12em] text-neon">{cardMasked}</code>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2">
            <span className="text-[0.72rem] text-white/50">Счёт получения Сбербанк (маска, только для админа):</span>
            <code className="font-mono text-[0.78rem] font-bold tracking-[0.12em] text-neon">{sberMasked}</code>
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.7rem] text-white/50">
              <b className="text-white/85">Visa Украина (UAH)</b> · курс {sampleUah.rate} ₴/₽ · 1000 ₽ → {sampleUah.amount} ₴
            </div>
            <div className="rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.7rem] text-white/50">
              <b className="text-white/85">Visa Europe (EUR)</b> · курс {sampleEur.rate} €/₽ · 1000 ₽ → {sampleEur.amount} €
            </div>
          </div>
          <div className="rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.7rem] text-white/50">
            Канал оплаты на странице заказа: <b className="text-white/85">{payoutChannelTitle(payoutChannel('visa_ua'))}</b> (UAH) ·{' '}
            <b className="text-white/85">{payoutChannelTitle(payoutChannel('visa_eu'))}</b> (EUR) ·{' '}
            <b className="text-white/85">{payoutChannelTitle(payoutChannel('sber_ru'))}</b> (RUB)
            <span className="mt-1 block text-[0.64rem] leading-relaxed text-white/35">
              Задаётся env: PAYMENT_LINK_UAH / PAYMENT_LINK_EUR (платёжная ссылка банка), PAYOUT_IBAN (перевод по
              реквизитам), PAYOUT_PHONE (перевод по номеру). Без них оплата идёт через поддержку.
            </span>
          </div>
          <p className="rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.68rem] leading-relaxed text-white/40">
            Покупатель видит только сумму, валюту и код заказа — никаких реквизитов на сайте нет. Заявки «Я оплатил(а)»
            падают в журнал событий (payment_sent_claim) и подсвечиваются в «Заказах»; подтверждение оплаты создаёт
            задачи выдачи автоматически.
          </p>
        </div>
      </AdminCard>

      <SettingsForm title="Minecraft-интеграция" description="Выдача доната через плагин KingGriefBridge (Paper/Purpur/Spigot) и LuckPerms." fields={DELIVERY} values={settings} cardId="minecraft" />

      <AdminCard title="Подключение плагина" description="Плагин опрашивает очередь и подтверждает выполнение команд">
        <div className="space-y-2">
          {endpoints.map((endpoint) => (
            <div key={endpoint} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2">
              <code className="font-mono text-[0.7rem] text-neon/90">POST {site}{endpoint}</code>
              <CopyButton value={`${site}${endpoint}`} label="Копировать" copyTitle="Endpoint скопирован" variant="plain" />
            </div>
          ))}
        </div>
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-white/6 bg-void/40 px-3 py-2 text-[0.68rem] leading-relaxed text-white/40">
          <Server size={13} className="mt-0.5 shrink-0 text-neon" />
          Секрет передаётся в заголовке <code className="mx-1 rounded bg-black/40 px-1 font-mono text-[0.64rem] text-white/70">X-Bridge-Secret</code>
          и должен совпадать с MINECRAFT_BRIDGE_SECRET в .env и config.yml плагина. Исходник плагина — в папке
          <code className="mx-1 rounded bg-black/40 px-1 font-mono text-[0.64rem] text-white/70">minecraft-plugin/</code>.
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <a href="/KingGriefCore.jar" download className="btn btn-primary btn-sm">
            <Download size={13} /> Скачать ядро KingGriefCore.jar (v6.4.0)
          </a>
          <a href="/KingGriefBridge.jar" download className="btn btn-gold btn-sm">
            <Download size={13} /> Скачать KingGriefBridge.jar
          </a>
          <a href="/KingHub.jar" download className="btn btn-primary btn-sm">
            <Download size={13} /> Плагин №1 для Хаба: KingHub.jar (Новогодняя Карта + Компас + НПС 1 Гриф)
          </a>
          <a href="/KingConnect.jar" download className="btn btn-primary btn-sm">
            <Download size={13} /> Плагин №2 для Сервера: KingConnect.jar (Связка с Хабом)
          </a>
          <a href="/KingAC.jar" download="KingАЧ.jar" className="btn btn-primary btn-sm">
            <Download size={13} /> Нейро-Античит KingАЧ v8.1 — 12 Схем Инжекта (KingAC.jar)
          </a>
          <a href="/KingClear.jar" download className="btn btn-ghost btn-sm">
            <Download size={13} /> Очистка инвентаря KingClear.jar (/clear)
          </a>
          <a href="/kinggrief-mega-rp.zip" download className="btn btn-ghost btn-sm">
            <Download size={13} /> Мега-РП v5.9 (kinggrief-mega-rp.zip)
          </a>
          <a href="/rw-newyear-full-assembly.zip" download className="btn btn-ghost btn-sm">
            <Download size={13} /> Вся сборка v6.4.0 (.zip)
          </a>
          <a href="/kinggrief-admin-panel.zip" download className="btn btn-ghost btn-sm">
            <Download size={13} /> Сайт + Админ-панель v5.9 (.zip)
          </a>
          <Link href="/admin/cases" className="btn btn-ghost btn-sm">Выдать кейсы игроку</Link>
        </div>
      </AdminCard>

      <SettingsForm title="Магазин" description="Отзывы, обслуживание и формула риликов." fields={SHOP} values={settings} cardId="shop" />

      {demoCount >= 0 && <DemoCard demoCount={demoCount} />}

      <AdminCard title="Данные" description="Экспорт и резервные копии">
        <div className="flex flex-wrap gap-2">
          <a href="/api/admin/export" className="btn btn-ghost btn-sm"><Download size={13} /> Экспорт заказов (CSV)</a>
          <Link href="/admin/security" className="btn btn-ghost btn-sm"><ShieldCheck size={13} /> Журнал действий</Link>
        </div>
        <p className="mt-3 text-[0.66rem] leading-relaxed text-white/25">
          База данных — SQLite (файл DATABASE_PATH, по умолчанию ./data/kinggrief.db). Для резервной копии остановите запись
          или скопируйте файл вместе с -wal/-shm. В Docker база хранится в томе kg_data.
        </p>
      </AdminCard>
    </div>
  );
}
