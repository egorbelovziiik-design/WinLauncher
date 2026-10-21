import type { AppSettings } from './types';

export function hexToRgb(hex: string): [number, number, number] {
  const m = hex.trim().match(/^#?([0-9a-f]{6})$/i);
  if (!m) return [88, 214, 141];
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function rgbTriplet(hex: string): string {
  return hexToRgb(hex).join(' ');
}

function mix(hex: string, target: number, amount: number): string {
  const [r, g, b] = hexToRgb(hex);
  const c = (v: number) => Math.round(v + (target - v) * amount);
  return `${c(r)} ${c(g)} ${c(b)}`;
}

/** Applies all appearance settings to the document root. */
export function applyAppearance(s: AppSettings) {
  const root = document.documentElement;
  root.dataset.theme = s.theme;
  root.dataset.anim = s.animations === 'full' ? 'full' : s.animations;
  root.style.setProperty('--anim', s.animations === 'off' ? '0' : s.animations === 'low' ? '0.5' : '1');
  root.style.setProperty('--ui-scale', String(s.uiScale || 1));
  root.lang = s.locale;

  if (s.theme === 'custom') {
    const bg = s.customTheme.background;
    const accent = s.customTheme.accent;
    const accent2 = s.customTheme.accent2;
    root.style.setProperty('--c-accent', rgbTriplet(accent));
    root.style.setProperty('--c-accent2', rgbTriplet(accent2));
    root.style.setProperty('--c-bg', rgbTriplet(bg));
    root.style.setProperty('--c-surface', mix(bg, 255, 0.035));
    root.style.setProperty('--c-panel', mix(bg, 255, 0.055));
    root.style.setProperty('--c-elevated', mix(bg, 255, 0.09));
    root.style.setProperty('--hero-fade', rgbTriplet(bg));
    root.style.setProperty('--glass', String(Math.min(0.97, Math.max(0.25, s.customTheme.transparency / 100))));
  } else {
    root.style.removeProperty('--c-accent');
    root.style.removeProperty('--c-accent2');
    root.style.removeProperty('--c-bg');
    root.style.removeProperty('--c-surface');
    root.style.removeProperty('--c-panel');
    root.style.removeProperty('--c-elevated');
    root.style.removeProperty('--hero-fade');
    root.style.removeProperty('--glass');
  }
}

/** Reads a CSS theme token as a hex color (for the custom theme pickers). */
export function tokenHex(name: string): string {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  const parts = raw.split(/\s+/).map((n) => parseInt(n, 10));
  if (parts.length !== 3 || parts.some(Number.isNaN)) return '#58d68d';
  return `#${parts.map((p) => p.toString(16).padStart(2, '0')).join('')}`;
}
