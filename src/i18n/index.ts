import { useCallback } from 'react';
import { useApp } from '../store/app';
import { dictFor, RU_DICT } from './locales';
import type { TranslationKey } from './locales';

export type TFn = (key: string, vars?: Record<string, string | number>) => string;

function interpolate(text: string, vars?: Record<string, string | number>): string {
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (m, k) => (vars[k] !== undefined ? String(vars[k]) : m));
}

export function translate(locale: string, key: string, vars?: Record<string, string | number>): string {
  const dict = dictFor(locale);
  const text = dict[key] ?? RU_DICT[key] ?? key;
  return interpolate(text, vars);
}

/** Reactive translator bound to the current interface language. */
export function useT(): TFn {
  const locale = useApp((s) => s.settings.locale);
  return useCallback((key: string, vars?: Record<string, string | number>) => translate(locale, key, vars), [locale]);
}

export type { TranslationKey };
