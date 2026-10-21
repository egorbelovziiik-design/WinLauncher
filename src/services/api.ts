import { invoke, isTauri } from '@tauri-apps/api/core';
import type { Settings, Snapshot } from './types';

export const isDesktop = isTauri();
export const defaultSettings: Settings = {
  theme: 'overworld', language: 'ru', effects: true, selected_profile: null, selected_account: null,
  microsoft_client_id: '', data_dir: '', download_concurrency: 6, download_limit_kib: 0,
  show_snapshots: false, close_to_tray: false, launch_hotkey: 'Ctrl+Enter', check_updates: true,
  favorite_versions: [], favorite_projects: [],
  discord: { enabled: false, application_id: '', show_version: true, show_pack: true, show_elapsed: true, show_download: true, show_launcher: true, hide_while_playing: false, show_website: false },
};
const previewKey = 'winlauncher.preview.preferences';
export function previewSnapshot(): Snapshot {
  let prefs: Partial<Settings> = {};
  try { const parsed = JSON.parse(localStorage.getItem(previewKey) ?? '{}'); prefs = { theme: typeof parsed.theme === 'string' ? parsed.theme : 'overworld', language: ['ru', 'en', 'uk'].includes(parsed.language) ? parsed.language : 'ru', effects: parsed.effects !== false }; } catch { /* A corrupt appearance preference must not prevent startup. */ }
  return { settings: { ...defaultSettings, ...prefs }, profiles: [], accounts: [], installed: [], sessions: [], job: null, running: null, discord_status: 'not_configured', curseforge_configured: false, version: '1.0.0' };
}
export class NativeRequiredError extends Error {
  constructor() { super('Эта функция работает в Windows-приложении. Браузерный предпросмотр не запускает Java и не имитирует установки.'); this.name = 'NativeRequiredError'; }
}
export async function command<T>(name: string, args?: Record<string, unknown>): Promise<T> {
  if (!isDesktop) throw new NativeRequiredError();
  try { return await invoke<T>(name, args); } catch (error) { throw new Error(String(error)); }
}
export async function getSnapshot(): Promise<Snapshot> { return isDesktop ? command('get_snapshot') : previewSnapshot(); }
export async function persistSettings(current: Settings, patch: Partial<Settings>): Promise<void> {
  const next = { ...current, ...patch };
  if (isDesktop) return command('save_settings', { settings: next });
  if (Object.keys(patch).some(k => !['theme', 'language', 'effects'].includes(k))) throw new NativeRequiredError();
  localStorage.setItem(previewKey, JSON.stringify({ theme: next.theme, language: next.language, effects: next.effects }));
}
export async function openExternal(url: string): Promise<void> {
  if (isDesktop) return command('open_external', { url });
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || !['github.com', 'www.minecraft.net', 'minecraft.net', 'microsoft.com', 'www.microsoft.com', 'portal.azure.com', 'learn.microsoft.com', 'discord.com', 'modrinth.com', 'curseforge.com', 'www.curseforge.com', 'console.curseforge.com', 'support.curseforge.com'].includes(parsed.hostname)) throw new Error('Недопустимый внешний адрес');
  window.open(parsed.href, '_blank', 'noopener,noreferrer');
}
export async function chooseFile(extensions: string[]): Promise<string | null> {
  if (!isDesktop) throw new NativeRequiredError();
  const { open } = await import('@tauri-apps/plugin-dialog');
  const result = await open({ multiple: false, filters: [{ name: extensions.join(', '), extensions }] });
  return typeof result === 'string' ? result : null;
}
export async function saveFile(defaultPath: string, extensions: string[]): Promise<string | null> {
  if (!isDesktop) throw new NativeRequiredError();
  const { save } = await import('@tauri-apps/plugin-dialog');
  return save({ defaultPath, filters: [{ name: extensions.join(', '), extensions }] });
}
export async function chooseDirectory(): Promise<string | null> {
  if (!isDesktop) throw new NativeRequiredError();
  const { open } = await import('@tauri-apps/plugin-dialog');
  const path = await open({ directory: true, multiple: false });
  return typeof path === 'string' ? path : null;
}
