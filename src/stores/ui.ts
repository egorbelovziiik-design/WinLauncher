import { create } from 'zustand';
import type { Notification, Page, Profile, Loader } from '../services/types';
interface ProfileEditor { profile?: Profile; loader?: Loader; version?: string }
interface Confirmation { title: string; description: string; action: () => Promise<void>; danger?: boolean; label?: string }
interface UIState {
  page: Page; setPage: (page: Page) => void;
  editor: ProfileEditor | null; editProfile: (editor: ProfileEditor | null) => void;
  nativeNotice: boolean; showNativeNotice: (show: boolean) => void;
  confirmation: Confirmation | null; confirm: (confirmation: Confirmation | null) => void;
  notifications: Notification[]; addNotification: (n: Omit<Notification, 'id' | 'timestamp'>) => void;
  notificationOpen: boolean; toggleNotifications: () => void; clearNotifications: () => void;
  settingsTab: string; setSettingsTab: (tab: string) => void;
  provider: 'modrinth' | 'curseforge'; setProvider: (p: 'modrinth' | 'curseforge') => void;
  modsTab: string; setModsTab: (tab: string) => void;
}
export const useUI = create<UIState>(set => ({
  page: 'home', setPage: page => set({ page }),
  editor: null, editProfile: editor => set({ editor }),
  nativeNotice: false, showNativeNotice: nativeNotice => set({ nativeNotice }),
  confirmation: null, confirm: confirmation => set({ confirmation }),
  notifications: [], addNotification: notification => set(s => ({ notifications: [{ ...notification, id: crypto.randomUUID(), timestamp: Date.now() }, ...s.notifications].slice(0, 100) })),
  notificationOpen: false, toggleNotifications: () => set(s => ({ notificationOpen: !s.notificationOpen })), clearNotifications: () => set({ notifications: [] }),
  settingsTab: 'general', setSettingsTab: settingsTab => set({ settingsTab }),
  provider: 'modrinth', setProvider: provider => set({ provider, modsTab: provider }),
  modsTab: 'modrinth', setModsTab: modsTab => set({ modsTab, ...(['modrinth', 'curseforge'].includes(modsTab) ? { provider: modsTab as 'modrinth' | 'curseforge' } : {}) }),
}));
