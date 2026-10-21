import { create } from 'zustand';
import type {
  Account,
  AppInfo,
  AppSettings,
  DownloadItem,
  LaunchStatus,
  LogLine,
  McVersion,
  Profile,
  ProfileStatus,
} from '../lib/types';
import { AppError, call, errorCode, isPreviewError, IS_TAURI, listen } from '../lib/bridge';
import { PREVIEW_SETTINGS } from '../lib/preview';
import { applyAppearance } from '../lib/theme';

export interface Toast {
  id: number;
  kind: 'success' | 'error' | 'info';
  message: string;
  detail?: string;
  key?: string;
}

export interface LaunchProgress {
  message: string;
  current: number;
  total: number;
  bytes: number;
  totalBytes: number;
}

interface Store {
  ready: boolean;
  bootError: string | null;
  info: AppInfo | null;
  settings: AppSettings;
  profiles: Profile[];
  accounts: Account[];
  versions: McVersion[];
  versionsLoading: boolean;
  versionsError: string | null;
  launch: LaunchStatus;
  progress: LaunchProgress;
  statuses: Record<string, ProfileStatus>;
  downloads: DownloadItem[];
  logs: LogLine[];
  toasts: Toast[];
  logDrawer: boolean;
  newsOpen: boolean;

  init: () => Promise<void>;
  saveSettings: (patch: Partial<AppSettings>) => void;
  setLocale: (locale: string) => void;
  toast: (kind: Toast['kind'], message: string, detail?: string) => void;
  toastError: (e: unknown) => void;
  dismissToast: (id: number) => void;
  setLogDrawer: (open: boolean) => void;

  refreshProfiles: () => Promise<void>;
  refreshAccounts: () => Promise<void>;
  refreshDownloads: () => Promise<void>;
  refreshLogs: () => Promise<void>;
  refreshVersions: () => Promise<void>;
  refreshStatus: (profileId: string) => Promise<ProfileStatus | null>;
  setActiveProfile: (id: string) => Promise<void>;
  setActiveAccount: (id: string) => Promise<void>;
  launchGame: () => Promise<void>;
  stopGame: () => Promise<void>;
}

const defaultSettings: AppSettings = {
  ...PREVIEW_SETTINGS,
  onboarded: false,
  activeProfileId: null,
  activeAccountId: null,
};

const emptyLaunch: LaunchStatus = {
  profileId: null,
  state: 'idle',
  message: '',
  code: null,
  startedAt: null,
};

let toastSeq = 1;
let initStarted = false;
let saveTimer: ReturnType<typeof setTimeout> | null = null;

function normalizeSettings(raw: Partial<AppSettings> | null | undefined): AppSettings {
  return { ...defaultSettings, ...(raw ?? {}), customTheme: { ...defaultSettings.customTheme, ...(raw?.customTheme ?? {}) } };
}

export const useApp = create<Store>((set, get) => ({
  ready: false,
  bootError: null,
  info: null,
  settings: defaultSettings,
  profiles: [],
  accounts: [],
  versions: [],
  versionsLoading: false,
  versionsError: null,
  launch: emptyLaunch,
  progress: { message: '', current: 0, total: 0, bytes: 0, totalBytes: 0 },
  statuses: {},
  downloads: [],
  logs: [],
  toasts: [],
  logDrawer: false,
  newsOpen: false,

  async init() {
    if (initStarted) return;
    initStarted = true;
    try {
      const [boot, stored] = await Promise.all([
        call<{ info: AppInfo }>('app_boot').catch(() => null),
        call<AppSettings>('settings_load').catch(() => null),
      ]);
      let settings = normalizeSettings(stored);
      if (!stored?.localeTouched) {
        // WebView2 reports the Windows UI language through navigator.language.
        const { matchSystemLocale } = await import('../i18n/locales');
        settings = { ...settings, locale: matchSystemLocale(navigator.language) };
      }
      set({ info: boot?.info ?? null, settings });
      applyAppearance(settings);

      await Promise.all([get().refreshProfiles(), get().refreshAccounts(), get().refreshDownloads()]);
      await get().refreshLogs();
      void get().refreshVersions();

      if (IS_TAURI) {
        await listen<LaunchStatus>('launch-status', (st) => set({ launch: st }));
        await listen<{ message: string; current: number; total: number; bytes: number; totalBytes: number }>(
          'launch-progress',
          (p) =>
            set({
              progress: {
                message: p.message ?? '',
                current: p.current ?? 0,
                total: p.total ?? 0,
                bytes: p.bytes ?? 0,
                totalBytes: p.totalBytes ?? 0,
              },
            }),
        );
        await listen<DownloadItem[]>('downloads-update', (list) => set({ downloads: list ?? [] }));
        await listen<LogLine>('log-line', (line) => {
          const logs = [...get().logs, line];
          set({ logs: logs.slice(-2500) });
        });
        await listen<{ profileId: string }>('profile-changed', () => {
          void get().refreshProfiles();
        });
      }
      set({ ready: true });
    } catch (e) {
      set({ ready: true, bootError: e instanceof Error ? e.message : String(e) });
    }
  },

  saveSettings(patch) {
    const settings = { ...get().settings, ...patch };
    set({ settings });
    applyAppearance(settings);
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      void call('settings_save', { settings }).catch((e) => {
        if (!isPreviewError(e)) get().toastError(e);
      });
    }, 250);
  },

  setLocale(locale) {
    get().saveSettings({ locale, localeTouched: true });
  },

  toast(kind, message, detail) {
    const id = toastSeq++;
    set({ toasts: [...get().toasts, { id, kind, message, detail }] });
    setTimeout(() => get().dismissToast(id), kind === 'error' ? 7000 : 4000);
  },

  toastError(e) {
    const raw = e instanceof Error ? e.message : typeof e === 'string' ? e : '';
    const candidate = errorCode(e) ?? (/^(errors|accounts|mods|versions|settings)\./.test(raw) ? raw : null);
    const id = toastSeq++;
    const toast: Toast = candidate
      ? { id, kind: 'error', message: candidate, key: candidate }
      : { id, kind: 'error', message: raw || 'errors.generic' };
    set({ toasts: [...get().toasts, toast] });
    setTimeout(() => get().dismissToast(id), 7000);
  },

  dismissToast(id) {
    set({ toasts: get().toasts.filter((t) => t.id !== id) });
  },

  setLogDrawer(open) {
    set({ logDrawer: open });
    if (open) void get().refreshLogs();
  },

  async refreshProfiles() {
    try {
      const profiles = await call<Profile[]>('profiles_list');
      set({ profiles });
      const active = get().settings.activeProfileId;
      if (!active && profiles.length > 0) {
        const chosen = profiles[0].id;
        get().saveSettings({ activeProfileId: chosen });
        void get().refreshStatus(chosen);
      } else if (active) {
        void get().refreshStatus(active);
      }
    } catch (e) {
      get().toastError(e);
    }
  },

  async refreshAccounts() {
    try {
      const accounts = await call<Account[]>('accounts_list');
      set({ accounts });
      const active = get().settings.activeAccountId;
      if (!active && accounts.length > 0) {
        get().saveSettings({ activeAccountId: (accounts.find((a) => a.primary) ?? accounts[0]).id });
      }
    } catch (e) {
      get().toastError(e);
    }
  },

  async refreshDownloads() {
    try {
      set({ downloads: await call<DownloadItem[]>('downloads_list') });
    } catch {
      /* downloads are optional in preview */
    }
  },

  async refreshLogs() {
    try {
      set({ logs: await call<LogLine[]>('logs_tail', { limit: 800 }) });
    } catch {
      /* ignore */
    }
  },

  async refreshVersions() {
    if (get().versionsLoading) return;
    set({ versionsLoading: true, versionsError: null });
    try {
      const versions = await call<McVersion[]>('mc_versions');
      set({ versions, versionsLoading: false });
    } catch (e) {
      set({ versionsLoading: false, versionsError: e instanceof Error ? e.message : String(e) });
    }
  },

  async refreshStatus(profileId) {
    try {
      const status = await call<ProfileStatus>('profile_status', { profileId });
      set({ statuses: { ...get().statuses, [profileId]: status } });
      return status;
    } catch {
      return null;
    }
  },

  async setActiveProfile(id) {
    get().saveSettings({ activeProfileId: id });
    void get().refreshStatus(id);
  },

  async setActiveAccount(id) {
    get().saveSettings({ activeAccountId: id });
  },

  async launchGame() {
    const { settings, profiles, accounts } = get();
    const profile = profiles.find((p) => p.id === settings.activeProfileId);
    const account = accounts.find((a) => a.id === settings.activeAccountId);
    if (!profile) {
      get().toast('error', 'home.noProfiles');
      return;
    }
    if (!account) {
      get().toast('error', 'accounts.noAccounts');
      return;
    }
    try {
      await call('launch_start', { profileId: profile.id, accountId: account.id });
    } catch (e) {
      get().toastError(e);
      if (e instanceof AppError && !isPreviewError(e)) {
        set({ launch: { ...get().launch, profileId: profile.id, state: 'error', message: e.message } });
      }
    }
  },

  async stopGame() {
    try {
      await call('launch_stop', { profileId: get().launch.profileId });
    } catch (e) {
      get().toastError(e);
    }
  },
}));
