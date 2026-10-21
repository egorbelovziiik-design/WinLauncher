import { useCallback, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listen } from '@tauri-apps/api/event';
import { toast } from 'sonner';
import { command, getSnapshot, isDesktop, NativeRequiredError, persistSettings, previewSnapshot } from '../services/api';
import type { Job, LogLine, Notification, RunningGame, Settings, Snapshot } from '../services/types';
import { useUI } from '../stores/ui';

export function useLauncher() {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['snapshot'], queryFn: getSnapshot, staleTime: 20_000, retry: 1 });
  const snapshot = query.data ?? previewSnapshot();
  const profile = snapshot.profiles.find(p => p.id === snapshot.settings.selected_profile) ?? snapshot.profiles[0];
  const account = snapshot.accounts.find(a => a.id === snapshot.settings.selected_account);
  const updateSettings = useCallback(async (patch: Partial<Settings>) => {
    const current = client.getQueryData<Snapshot>(['snapshot']) ?? snapshot;
    await persistSettings(current.settings, patch);
    client.setQueryData<Snapshot>(['snapshot'], { ...current, settings: { ...current.settings, ...patch } });
    await client.invalidateQueries({ queryKey: ['snapshot'] });
  }, [client, snapshot]);
  return { ...query, snapshot, profile, account, updateSettings, busy: Boolean(snapshot.job || snapshot.running) };
}
export async function perform<T>(action: () => Promise<T>, success?: string): Promise<T | undefined> {
  try { const result = await action(); if (success) toast.success(success); return result; }
  catch (error) {
    if (error instanceof NativeRequiredError) useUI.getState().showNativeNotice(true);
    else { const message = error instanceof Error ? error.message : String(error); toast.error(message, { duration: 8000 }); useUI.getState().addNotification({ kind: 'error', message }); }
    return undefined;
  }
}
export function desktopAction(action: () => void) { if (!isDesktop) useUI.getState().showNativeNotice(true); else action(); }
export function useLaunch() {
  const { profile, account, snapshot } = useLauncher();
  const { setPage, editProfile, confirm } = useUI();
  return useCallback(() => {
    if (!isDesktop) { useUI.getState().showNativeNotice(true); return; }
    if (snapshot.running) { setPage('logs'); return; }
    if (snapshot.job) return;
    if (!profile) { editProfile({}); return; }
    if (!account) { setPage('accounts'); toast.info('Войдите через Microsoft или создайте локальный офлайн-аккаунт.'); return; }
    void command('launch_game', { profileId: profile.id, allowMemoryPressure: false }).catch(error => {
      const message = String(error);
      if (message.includes('MEMORY_WARNING')) confirm({ title: 'Недостаточно свободной памяти', description: message.replace(/^.*MEMORY_WARNING:\s*/, ''), label: 'Всё равно запустить', action: async () => { await command('launch_game', { profileId: profile.id, allowMemoryPressure: true }); } });
      else { toast.error(message, { duration: 10000 }); useUI.getState().addNotification({ kind: 'error', message }); }
    });
  }, [profile, account, snapshot.running, snapshot.job, setPage, editProfile, confirm]);
}
export function useNativeEvents() {
  const client = useQueryClient();
  useEffect(() => {
    if (!isDesktop) return;
    let disposed = false; const unlisteners: (() => void)[] = [];
    async function subscribe<T>(event: string, callback: (data: T) => void) {
      const unlisten = await listen<T>(event, e => callback(e.payload));
      if (disposed) unlisten(); else unlisteners.push(unlisten);
    }
    const update = (patch: Partial<Snapshot>) => client.setQueryData<Snapshot>(['snapshot'], s => s ? { ...s, ...patch } : s);
    void subscribe<Job | null>('download-progress', job => update({ job }));
    void subscribe<RunningGame | null>('game-state', running => { update({ running }); void client.invalidateQueries({ queryKey: ['snapshot'] }); });
    void subscribe<string>('discord-state', discord_status => update({ discord_status }));
    void subscribe<unknown>('data-changed', () => { void client.invalidateQueries({ queryKey: ['snapshot'] }); void client.invalidateQueries({ queryKey: ['local-files'] }); void client.invalidateQueries({ queryKey: ['launch-diagnosis'] }); });
    void subscribe<LogLine>('log-line', line => client.setQueryData<LogLine[]>(['logs'], lines => [...(lines ?? []).slice(-4999), line]));
    void subscribe<Omit<Notification, 'id' | 'timestamp'>>('notification', notification => {
      useUI.getState().addNotification(notification);
      if (notification.kind === 'success') toast.success(notification.message); else if (notification.kind === 'error') toast.error(notification.message); else toast.info(notification.message);
    });
    return () => { disposed = true; unlisteners.forEach(fn => fn()); };
  }, [client]);
}
