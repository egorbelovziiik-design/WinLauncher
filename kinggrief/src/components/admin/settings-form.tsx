'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Save } from 'lucide-react';
import { adminPost } from './api-client';
import { AdminCard, Field } from './kit';
import { useToast } from '../providers';

export type SettingField = {
  key: string;
  label: string;
  type?: 'text' | 'textarea' | 'number' | 'select' | 'toggle' | 'url' | 'json' | 'commands';
  hint?: string;
  options?: Array<{ value: string; label: string }>;
  placeholder?: string;
  span?: 1 | 2;
  rows?: number;
};

const COMMAND_FIELDS: Array<{ key: string; label: string; hint: string }> = [
  { key: 'rank', label: 'Выдача привилегии', hint: 'Можно использовать {player}, {rank}, {order}' },
  { key: 'rank_replace', label: 'Замена привилегии (если нужно set вместо add)', hint: 'Не используется автоматически, если не настроено' },
  { key: 'relic', label: 'Выдача риликов', hint: '{player}, {amount}' },
  { key: 'case', label: 'Выдача ключей кейса', hint: '{player}, {case}, {amount}' },
  { key: 'unban', label: 'Разбан', hint: '{player}' },
  { key: 'unmute', label: 'Размут', hint: '{player}' },
  { key: 'unban_ip', label: 'Разбан по IP', hint: '{player}' },
  { key: 'unmute_ip', label: 'Размут по IP', hint: '{player}' },
];

function parseCommands(raw: string): Record<string, string> {
  try {
    const parsed = JSON.parse(raw || '{}') as Record<string, string>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function SettingsForm({
  title,
  description,
  fields,
  values,
  cardId,
}: {
  title: string;
  description?: string;
  fields: SettingField[];
  values: Record<string, string>;
  cardId?: string;
}) {
  const router = useRouter();
  const { push } = useToast();
  const [state, setState] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {};
    fields.forEach((field) => {
      if (field.type === 'commands') return;
      initial[field.key] = values[field.key] ?? '';
    });
    return initial;
  });
  const [commands, setCommands] = useState<Record<string, string>>(() =>
    parseCommands(values.delivery_commands ?? '{}'),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setError('');
    const payload: Record<string, string> = { ...state };
    for (const field of fields) {
      if (field.type === 'json') {
        try {
          JSON.parse(state[field.key] || '{}');
        } catch {
          setError(`«${field.label}»: некорректный JSON`);
          return;
        }
      }
    }
    if (fields.some((f) => f.type === 'commands')) {
      payload.delivery_commands = JSON.stringify(commands);
    }
    setSaving(true);
    const res = await adminPost('/api/admin/settings', { settings: payload });
    setSaving(false);
    if (!res.ok) {
      setError(res.data.error ?? 'Не удалось сохранить');
      return;
    }
    setSaved(true);
    setTimeout(() => setSaved(false), 2500);
    push({ tone: 'success', title: 'Настройки сохранены', description: title });
    router.refresh();
  };

  return (
    <AdminCard
      id={cardId}
      title={title}
      description={description}
      action={
        <button onClick={save} disabled={saving} className={`btn btn-sm ${saved ? 'btn-gold' : 'btn-primary'}`}>
          <Save size={13} /> {saving ? 'Сохраняем…' : saved ? 'Сохранено' : 'Сохранить'}
        </button>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => {
          if (field.type === 'commands') {
            return (
              <div key={field.key} className="sm:col-span-2">
                <p className="field-label">{field.label}</p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {COMMAND_FIELDS.map((cmd) => (
                    <Field key={cmd.key} label={cmd.label} hint={cmd.hint}>
                      <input
                        value={commands[cmd.key] ?? ''}
                        onChange={(e) => setCommands({ ...commands, [cmd.key]: e.target.value })}
                        className="field h-9 font-mono text-[0.7rem]"
                        placeholder="команда без слэша"
                      />
                    </Field>
                  ))}
                </div>
                {field.hint && <p className="mt-1.5 text-[0.62rem] leading-snug text-white/25">{field.hint}</p>}
              </div>
            );
          }
          if (field.type === 'toggle') {
            return (
              <label key={field.key} className={`flex cursor-pointer items-center justify-between gap-3 rounded-lg border border-white/7 bg-void/30 px-3 py-2.5 ${field.span === 2 ? 'sm:col-span-2' : ''}`}>
                <span>
                  <span className="block text-[0.76rem] font-semibold text-white/75">{field.label}</span>
                  {field.hint && <span className="mt-0.5 block text-[0.62rem] text-white/25">{field.hint}</span>}
                </span>
                <input
                  type="checkbox"
                  checked={state[field.key] === 'true'}
                  onChange={(e) => setState({ ...state, [field.key]: e.target.checked ? 'true' : 'false' })}
                  className="h-4 w-4 shrink-0 accent-[#a855f7]"
                />
              </label>
            );
          }
          return (
            <Field key={field.key} label={field.label} hint={field.hint} className={field.span === 2 ? 'sm:col-span-2' : ''}>
              {field.type === 'textarea' || field.type === 'json' ? (
                <textarea
                  value={state[field.key] ?? ''}
                  onChange={(e) => setState({ ...state, [field.key]: e.target.value })}
                  rows={field.rows ?? (field.type === 'json' ? 8 : 3)}
                  className={`field h-auto resize-y py-2 ${field.type === 'json' ? 'font-mono text-[0.68rem]' : ''}`}
                  placeholder={field.placeholder}
                />
              ) : field.type === 'select' ? (
                <select
                  value={state[field.key] ?? ''}
                  onChange={(e) => setState({ ...state, [field.key]: e.target.value })}
                  className="field"
                >
                  {field.options?.map((option) => (
                    <option key={option.value} value={option.value} className="bg-panel">{option.label}</option>
                  ))}
                </select>
              ) : (
                <input
                  type={field.type === 'number' ? 'number' : 'text'}
                  value={state[field.key] ?? ''}
                  onChange={(e) => setState({ ...state, [field.key]: e.target.value })}
                  className="field"
                  placeholder={field.placeholder}
                />
              )}
            </Field>
          );
        })}
        {error && <p className="sm:col-span-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[0.72rem] text-[#fca5a5]">{error}</p>}
      </div>
    </AdminCard>
  );
}
