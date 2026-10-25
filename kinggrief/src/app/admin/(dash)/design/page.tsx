import { getSettings } from '@/lib/repo';
import { SettingsForm, type SettingField } from '@/components/admin/settings-form';
import { AdminCard } from '@/components/admin/kit';

export const dynamic = 'force-dynamic';

const BRAND: SettingField[] = [
  { key: 'site_name', label: 'Название бренда', hint: 'Используется в подписях и метаданных.' },
  { key: 'logo_url', label: 'URL логотипа', hint: 'Пусто — используется встроенный знак KG.' },
  { key: 'site_tagline', label: 'Слоган', span: 2 },
  { key: 'site_description', label: 'Краткое описание', type: 'textarea', span: 2, rows: 3 },
  { key: 'hero_subtitle', label: 'Подзаголовок главного экрана', span: 2 },
  { key: 'hero_note', label: 'Строка под кнопками', span: 2 },
];

const SERVER: SettingField[] = [
  { key: 'server_ip', label: 'IP сервера', hint: 'Показывается в шапке, футере и блоке подключения.' },
  { key: 'server_version', label: 'Поддерживаемые версии' },
  { key: 'online_base', label: 'Онлайн (базовое значение)', type: 'number', hint: 'Показывается, пока плагин моста не подключён.' },
  { key: 'online_peak', label: 'Максимум слотов', type: 'number' },
  { key: 'support_email', label: 'Email поддержки' },
  { key: 'discord_url', label: 'Discord URL' },
  { key: 'telegram_url', label: 'Telegram URL' },
  { key: 'announcement', label: 'Объявление', span: 2, hint: 'Короткая строка-анонс. Можно оставить пустой.' },
];

const BANNER: SettingField[] = [
  { key: 'banner_active', label: 'Показывать баннер', type: 'toggle' },
  { key: 'banner_title', label: 'Заголовок баннера' },
  { key: 'banner_subtitle', label: 'Подзаголовок баннера' },
  { key: 'banner_href', label: 'Ссылка баннера', type: 'url', hint: 'Например /privileges или https://…' },
  { key: 'banner_image_url', label: 'URL фонового изображения', type: 'url', span: 2 },
];

const PALETTE = [
  { name: 'Void', hex: '#080810' },
  { name: 'Panel', hex: '#101020' },
  { name: 'Card', hex: '#151527' },
  { name: 'Violet', hex: '#7C3AED' },
  { name: 'Neon', hex: '#A855F7' },
  { name: 'Pink', hex: '#EC4899' },
  { name: 'Blue', hex: '#3B82F6' },
  { name: 'Gold', hex: '#FACC15' },
  { name: 'Emerald', hex: '#22C55E' },
  { name: 'Danger', hex: '#EF4444' },
];

export default function AdminDesignPage() {
  const settings = getSettings();
  return (
    <div className="space-y-4">
      <div>
        <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">внешний вид</p>
        <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Дизайн и контент</h1>
        <p className="mt-1 text-[0.76rem] text-white/35">Тексты, IP, соцсети и баннер. Изменения применяются сразу после сохранения.</p>
      </div>

      <SettingsForm title="Бренд и главный экран" description="Заголовки на главной странице сайта." fields={BRAND} values={settings} />
      <SettingsForm title="Сервер и контакты" description="IP, версии, ссылки и онлайн по умолчанию." fields={SERVER} values={settings} />
      <SettingsForm title="Баннер" description="Промо-баннер на сайте (если включён)." fields={BANNER} values={settings} />

      <AdminCard title="Палитра бренда" description="Токены дизайн-системы. Используются в компонентах и карточках товаров.">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          {PALETTE.map((color) => (
            <div key={color.hex} className="overflow-hidden rounded-lg border border-white/8">
              <div className="h-12" style={{ background: color.hex }} />
              <div className="px-2 py-1.5">
                <p className="text-[0.68rem] font-semibold text-white/70">{color.name}</p>
                <p className="font-mono text-[0.56rem] uppercase text-white/30">{color.hex}</p>
              </div>
            </div>
          ))}
        </div>
        <p className="mt-3 text-[0.66rem] text-white/25">
          Цвет карточки товара задаётся отдельно в разделе «Товары и цены».
        </p>
      </AdminCard>
    </div>
  );
}
