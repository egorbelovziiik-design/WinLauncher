import { ru, en } from './ru';
import { uk } from './uk';
import { de } from './de';
import { fr } from './fr';
import { es } from './es';
import { pt } from './pt';
import { pl } from './pl';
import { tr } from './tr';
import { zh } from './zh';
import { ja } from './ja';
import type { Dict } from './ru';

export type { Dict, TranslationKey } from './ru';

export interface LocaleMeta {
  code: string;
  label: string;
  english: string;
  flag: string;
  dict: Dict;
}

export const LOCALES: LocaleMeta[] = [
  { code: 'ru', label: 'Русский', english: 'Russian', flag: '🇷🇺', dict: ru },
  { code: 'en', label: 'English', english: 'English', flag: '🇬🇧', dict: en },
  { code: 'uk', label: 'Українська', english: 'Ukrainian', flag: '🇺🇦', dict: uk },
  { code: 'de', label: 'Deutsch', english: 'German', flag: '🇩🇪', dict: de },
  { code: 'fr', label: 'Français', english: 'French', flag: '🇫🇷', dict: fr },
  { code: 'es', label: 'Español', english: 'Spanish', flag: '🇪🇸', dict: es },
  { code: 'pt', label: 'Português', english: 'Portuguese', flag: '🇵🇹', dict: pt },
  { code: 'pl', label: 'Polski', english: 'Polish', flag: '🇵🇱', dict: pl },
  { code: 'tr', label: 'Türkçe', english: 'Turkish', flag: '🇹🇷', dict: tr },
  { code: 'zh', label: '中文', english: 'Chinese', flag: '🇨🇳', dict: zh },
  { code: 'ja', label: '日本語', english: 'Japanese', flag: '🇯🇵', dict: ja },
];

export const LOCALE_CODES = LOCALES.map((l) => l.code);

export const RU_DICT: Dict = ru as unknown as Dict;

export function dictFor(code: string): Dict {
  return LOCALES.find((l) => l.code === code)?.dict ?? RU_DICT;
}

/** Best-effort match of a system language ("de-DE", "zh-Hans", …) to a bundled locale. */
export function matchSystemLocale(raw: string | null | undefined): string {
  if (!raw) return 'ru';
  const lower = raw.toLowerCase().replace('_', '-');
  const exact = LOCALE_CODES.find((c) => lower === c || lower.startsWith(c + '-'));
  if (exact) return exact;
  if (lower.startsWith('nb') || lower.startsWith('nn')) return 'en';
  if (lower.startsWith('zh')) return 'zh';
  return 'en';
}
