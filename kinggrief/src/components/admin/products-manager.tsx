'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Copy, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import type { Product, ProductKind } from '@/lib/types';
import { KIND_LABELS } from '@/lib/types';
import { formatPrice } from '@/lib/utils';
import { adminPost } from './api-client';
import { AdminCard, ColorInput, Field, Modal, Pill, SectionTabs } from './kit';
import { Icon } from '../icons';
import { useToast } from '../providers';

type Draft = Product & { featuresText: string; metaText: string };

const KINDS: ProductKind[] = ['rank', 'case', 'relic', 'other'];

const DEFAULT_META: Record<ProductKind, Record<string, unknown>> = {
  rank: { tier: 15, rankId: 'new-rank' },
  case: { group: 'super', groupName: 'Супер кейс', quantity: 1, unitPrice: 29 },
  relic: { amount: 1000 },
  other: { service: 'unban', requiresNickname: true },
};

function toDraft(product: Product): Draft {
  return {
    ...product,
    oldPrice: product.oldPrice ?? null,
    featuresText: product.features.join('\n'),
    metaText: JSON.stringify(product.meta, null, 2),
  };
}

function blank(kind: ProductKind): Draft {
  const stamp = Date.now().toString().slice(-5);
  return {
    id: `new-${kind}-${stamp}`,
    kind,
    name: 'Новый товар',
    subtitle: '',
    description: '',
    price: 99,
    oldPrice: null,
    color: '#A855F7',
    icon: kind === 'rank' ? 'crown' : kind === 'case' ? 'box' : kind === 'relic' ? 'coins' : 'unlock',
    badge: null,
    features: [],
    meta: DEFAULT_META[kind],
    sort: 600,
    active: true,
    featuresText: '',
    metaText: JSON.stringify(DEFAULT_META[kind], null, 2),
  };
}

export function ProductsManager({
  products,
  activeKind,
  openNew,
}: {
  products: Product[];
  activeKind?: string;
  openNew?: boolean;
}) {
  const router = useRouter();
  const { push } = useToast();
  const [kind, setKind] = useState<string>(activeKind && KINDS.includes(activeKind as ProductKind) ? activeKind : 'all');
  const [query, setQuery] = useState('');
  const [draft, setDraft] = useState<Draft | null>(openNew ? blank((activeKind as ProductKind) || 'rank') : null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [toDelete, setToDelete] = useState<Product | null>(null);

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: products.length };
    KINDS.forEach((k) => (map[k] = products.filter((p) => p.kind === k).length));
    return map;
  }, [products]);

  const filtered = useMemo(
    () =>
      products
        .filter((p) => (kind === 'all' ? true : p.kind === kind))
        .filter((p) => (query.trim() ? `${p.name} ${p.id} ${p.subtitle}`.toLowerCase().includes(query.toLowerCase()) : true)),
    [products, kind, query],
  );

  const save = async () => {
    if (!draft) return;
    setError('');
    let meta: Record<string, unknown>;
    try {
      meta = JSON.parse(draft.metaText || '{}') as Record<string, unknown>;
    } catch {
      setError('Meta — некорректный JSON. Исправьте блок «meta».');
      return;
    }
    if (!/^#[0-9A-Fa-f]{6}$/.test(draft.color)) {
      setError('Цвет должен быть в формате #RRGGBB');
      return;
    }
    if (!draft.name.trim()) {
      setError('Укажите название');
      return;
    }
    setSaving(true);
    const features = draft.featuresText
      .split('\n')
      .map((f) => f.trim())
      .filter(Boolean);
    const payload = {
      id: draft.id.trim(),
      kind: draft.kind,
      name: draft.name.trim(),
      subtitle: draft.subtitle,
      description: draft.description,
      price: Math.round(Number(draft.price)),
      oldPrice: draft.oldPrice ? Math.round(Number(draft.oldPrice)) : null,
      color: draft.color,
      icon: draft.icon,
      badge: draft.badge || null,
      features,
      meta,
      sort: Math.round(Number(draft.sort)) || 0,
      active: draft.active,
    };
    const res = await adminPost('/api/admin/products', { action: 'save', product: payload });
    setSaving(false);
    if (!res.ok) {
      setError(res.data.error ?? 'Не удалось сохранить');
      return;
    }
    push({ tone: 'success', title: 'Товар сохранён', description: `${payload.name} · ${formatPrice(payload.price)}` });
    setDraft(null);
    router.refresh();
  };

  const toggle = async (product: Product) => {
    const res = await adminPost('/api/admin/products', { action: 'toggle', id: product.id });
    if (res.ok) {
      push({ tone: 'info', title: product.active ? 'Товар скрыт' : 'Товар активирован', description: product.name });
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  const remove = async () => {
    if (!toDelete) return;
    const res = await adminPost('/api/admin/products', { action: 'delete', id: toDelete.id });
    if (res.ok) {
      push({ tone: 'success', title: 'Товар удалён', description: toDelete.name });
      setToDelete(null);
      router.refresh();
    } else push({ tone: 'error', title: 'Ошибка', description: res.data.error });
  };

  const duplicate = (product: Product) => {
    const copy = toDraft(product);
    copy.id = `${product.id}-copy-${Date.now().toString().slice(-4)}`;
    copy.name = `${product.name} (копия)`;
    copy.active = false;
    setDraft(copy);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">каталог</p>
          <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Товары и цены</h1>
          <p className="mt-1 text-[0.76rem] text-white/35">Изменения сразу применяются на сайте и в оформлении заказа.</p>
        </div>
        <button onClick={() => setDraft(blank(kind === 'all' ? 'rank' : (kind as ProductKind)))} className="btn btn-primary btn-sm">
          <Plus size={14} /> Добавить товар
        </button>
      </div>

      <SectionTabs
        active={kind}
        tabs={[
          { href: '/admin/products', label: 'Все', count: counts.all },
          ...KINDS.map((k) => ({ href: `/admin/products?kind=${k}`, label: KIND_LABELS[k], count: counts[k] })),
        ]}
      />

      <AdminCard>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <label className="relative flex-1 sm:max-w-xs">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-white/25" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Поиск по названию или id"
              className="field h-9 pl-8 text-[0.76rem]"
            />
          </label>
          <span className="font-mono text-[0.58rem] uppercase tracking-widest text-white/25">{filtered.length} позиций</span>
        </div>

        <div className="overflow-x-auto">
          <table className="admin-table min-w-[760px]">
            <thead>
              <tr><th>Товар</th><th>Тип</th><th>Цена</th><th>Старая</th><th>Статус</th><th>Сортировка</th><th /></tr>
            </thead>
            <tbody>
              {filtered.map((product) => (
                <tr key={product.id}>
                  <td>
                    <div className="flex items-center gap-2">
                      <span
                        className="grid h-8 w-8 shrink-0 place-items-center rounded-lg"
                        style={{ background: `${product.color}18`, color: product.color, boxShadow: `inset 0 0 0 1px ${product.color}30` }}
                      >
                        <Icon name={product.icon} size={15} />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-[0.78rem] font-bold text-white">{product.name}</p>
                        <p className="truncate font-mono text-[0.58rem] text-white/25">{product.id}</p>
                      </div>
                    </div>
                  </td>
                  <td><Pill color="#A855F7">{KIND_LABELS[product.kind]}</Pill></td>
                  <td className="stat-num text-white/85">{formatPrice(product.price)}</td>
                  <td className="stat-num text-[0.72rem] text-white/30 line-through">{product.oldPrice ? formatPrice(product.oldPrice) : '—'}</td>
                  <td>
                    <button
                      onClick={() => toggle(product)}
                      className={`relative h-5 w-9 rounded-full transition ${product.active ? 'bg-emerald/70' : 'bg-white/12'}`}
                      aria-label={product.active ? 'Скрыть товар' : 'Активировать товар'}
                      role="switch"
                      aria-checked={product.active}
                    >
                      <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-all ${product.active ? 'left-[1.15rem]' : 'left-0.5'}`} />
                    </button>
                  </td>
                  <td className="font-mono text-[0.62rem] text-white/35">{product.sort}</td>
                  <td>
                    <div className="flex justify-end gap-1">
                      <button onClick={() => setDraft(toDraft(product))} className="btn btn-ghost btn-icon !h-7 !w-7" aria-label={`Редактировать ${product.name}`}>
                        <Pencil size={12} />
                      </button>
                      <button onClick={() => duplicate(product)} className="btn btn-ghost btn-icon !h-7 !w-7" aria-label={`Дублировать ${product.name}`}>
                        <Copy size={12} />
                      </button>
                      <button onClick={() => setToDelete(product)} className="btn btn-ghost btn-icon !h-7 !w-7 hover:!text-danger" aria-label={`Удалить ${product.name}`}>
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filtered.length === 0 && <p className="py-8 text-center text-[0.74rem] text-white/30">Товары не найдены.</p>}
        </div>
      </AdminCard>

      <p className="text-[0.66rem] leading-relaxed text-white/25">
        Цены PEGAS / COBRA / BUNNY / RABBIT зафиксированы брендом (19 / 39 / 59 / 89 ₽) — менять их не рекомендуется.
        Скрытый товар остаётся в старых заказах, но недоступен для покупки.
      </p>

      <Modal
        open={Boolean(draft)}
        onClose={() => setDraft(null)}
        title={draft?.name ? `Редактирование: ${draft.name}` : 'Новый товар'}
        description="Все поля сохраняются в базу. Изменения появятся на сайте после обновления страницы."
        wide
        footer={
          <>
            <button onClick={() => setDraft(null)} className="btn btn-ghost btn-sm">Отмена</button>
            <button onClick={save} disabled={saving} className="btn btn-primary btn-sm">{saving ? 'Сохраняем…' : 'Сохранить'}</button>
          </>
        }
      >
        {draft && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="ID" hint="Латиница, цифры, дефис. Уникальный.">
              <input value={draft.id} onChange={(e) => setDraft({ ...draft, id: e.target.value })} className="field font-mono text-xs" />
            </Field>
            <Field label="Тип">
              <select
                value={draft.kind}
                onChange={(e) => {
                  const nextKind = e.target.value as ProductKind;
                  setDraft({ ...draft, kind: nextKind, meta: DEFAULT_META[nextKind], metaText: JSON.stringify(DEFAULT_META[nextKind], null, 2) });
                }}
                className="field"
              >
                {KINDS.map((k) => <option key={k} value={k} className="bg-panel">{KIND_LABELS[k]}</option>)}
              </select>
            </Field>
            <Field label="Название">
              <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} className="field" />
            </Field>
            <Field label="Подзаголовок">
              <input value={draft.subtitle} onChange={(e) => setDraft({ ...draft, subtitle: e.target.value })} className="field" maxLength={160} />
            </Field>
            <Field label="Описание" className="sm:col-span-2">
              <textarea value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} rows={3} className="field h-auto resize-none py-2" />
            </Field>
            <Field label="Цена, ₽">
              <input type="number" value={draft.price} onChange={(e) => setDraft({ ...draft, price: Number(e.target.value) })} className="field" min={0} />
            </Field>
            <Field label="Старая цена, ₽" hint="Для зачёркнутой цены и бейджа скидки. Пусто — без скидки.">
              <input
                type="number"
                value={draft.oldPrice ?? ''}
                onChange={(e) => setDraft({ ...draft, oldPrice: e.target.value ? Number(e.target.value) : null })}
                className="field"
                min={0}
              />
            </Field>
            <Field label="Цвет">
              <ColorInput value={draft.color} onChange={(color) => setDraft({ ...draft, color })} />
            </Field>
            <Field label="Иконка" hint="feather, crown, crown-gold, box, coins, unlock, shield, flame…">
              <input value={draft.icon} onChange={(e) => setDraft({ ...draft, icon: e.target.value })} className="field font-mono text-xs" />
            </Field>
            <Field label="Бейдж" hint="Например MAX, ВЫГОДНО, NEW. Пусто — без бейджа.">
              <input value={draft.badge ?? ''} onChange={(e) => setDraft({ ...draft, badge: e.target.value || null })} className="field" maxLength={24} />
            </Field>
            <Field label="Сортировка">
              <input type="number" value={draft.sort} onChange={(e) => setDraft({ ...draft, sort: Number(e.target.value) })} className="field" />
            </Field>
            <Field label="Возможности / состав" hint="Одна строка — одна возможность." className="sm:col-span-2">
              <textarea value={draft.featuresText} onChange={(e) => setDraft({ ...draft, featuresText: e.target.value })} rows={5} className="field h-auto resize-none py-2 text-xs" />
            </Field>
            <Field label="meta (JSON)" hint="Служебные поля: tier/rankId для привилегий, group/quantity для кейсов, amount для риликов." className="sm:col-span-2">
              <textarea value={draft.metaText} onChange={(e) => setDraft({ ...draft, metaText: e.target.value })} rows={5} className="field h-auto resize-none py-2 font-mono text-[0.7rem]" />
            </Field>
            <label className="flex cursor-pointer items-center gap-2 sm:col-span-2">
              <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} className="h-4 w-4 accent-[#a855f7]" />
              <span className="text-[0.76rem] text-white/60">Показывать на сайте</span>
            </label>
            {error && <p className="sm:col-span-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.72rem] text-[#fca5a5]">{error}</p>}
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(toDelete)}
        onClose={() => setToDelete(null)}
        title="Удалить товар?"
        footer={
          <>
            <button onClick={() => setToDelete(null)} className="btn btn-ghost btn-sm">Отмена</button>
            <button onClick={remove} className="btn btn-sm" style={{ background: '#ef4444', color: '#fff' }}>Удалить</button>
          </>
        }
      >
        <p className="text-[0.8rem] leading-relaxed text-white/55">
          Товар «{toDelete?.name}» будет удалён из каталога. Если он уже покупался, лучше скрыть его, а не удалять —
          старые заказы сохранят состав.
        </p>
      </Modal>
    </div>
  );
}
