/* ------------------------------------------------------------------
 * Bridge between the React UI and the Rust core (Tauri 2).
 *
 * In the packaged Windows app every call goes to #[tauri::command]
 * handlers in src-tauri.  In a plain browser (the Arena preview) only
 * read-only calls are answered (live APIs when CORS allows, samples
 * otherwise); everything that would install/launch is rejected with a
 * clear message instead of pretending to work.
 * ------------------------------------------------------------------ */
import type { McVersion, ModrinthHit, NewsItem } from './types';
import {
  PREVIEW_ACCOUNTS,
  PREVIEW_MODS,
  PREVIEW_NEWS,
  PREVIEW_PROFILES,
  PREVIEW_SETTINGS,
  PREVIEW_STATUS,
  PREVIEW_VERSIONS,
} from './preview';

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown;
  }
}

export const IS_TAURI: boolean =
  typeof window !== 'undefined' && typeof window.__TAURI_INTERNALS__ === 'object';

export class AppError extends Error {
  code: string;
  constructor(message: string, code = 'error') {
    super(message);
    this.code = code;
  }
}

type InvokeFn = <T>(cmd: string, args?: Record<string, unknown>) => Promise<T>;
let tauriInvoke: InvokeFn | null = null;

async function getInvoke() {
  if (!IS_TAURI) return null;
  if (!tauriInvoke) {
    const core = await import('@tauri-apps/api/core');
    tauriInvoke = core.invoke as unknown as InvokeFn;
  }
  return tauriInvoke as InvokeFn;
}

export async function call<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const invoke = await getInvoke();
  if (invoke) {
    try {
      return await invoke<T>(cmd, args);
    } catch (e) {
      const msg = typeof e === 'string' ? e : e instanceof Error ? e.message : String(e);
      throw new AppError(msg);
    }
  }
  return previewFallback<T>(cmd, args);
}

/** Preview-mode events are never emitted; `listen` becomes a no-op. */
export async function listen<T>(event: string, cb: (payload: T) => void): Promise<() => void> {
  if (!IS_TAURI) return () => {};
  const { listen: tauriListen } = await import('@tauri-apps/api/event');
  const un = await tauriListen<T>(event, (e) => cb(e.payload));
  return un;
}

export async function openExternal(url: string): Promise<void> {
  if (!/^https?:\/\//i.test(url)) throw new AppError('Only http(s) links are allowed');
  if (IS_TAURI) {
    await call('app_open_url', { url });
  } else {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}

export async function openPath(path: string): Promise<void> {
  if (!IS_TAURI) throw previewError('errors.notInTauri');
  await call('app_open_path', { path });
}

export async function pickDirectory(): Promise<string | null> {
  if (!IS_TAURI) throw previewError('errors.notInTauri');
  const { open } = await import('@tauri-apps/plugin-dialog');
  const res = await open({ directory: true, multiple: false, title: 'WinLaucher' });
  return typeof res === 'string' ? res : null;
}

export async function pickFile(
  filters: { name: string; extensions: string[] }[] = [],
): Promise<string | null> {
  if (!IS_TAURI) throw previewError('errors.notInTauri');
  const { open } = await import('@tauri-apps/plugin-dialog');
  const res = await open({ multiple: false, filters });
  return typeof res === 'string' ? res : null;
}

function previewError(key: string): AppError {
  // The store translates keys like "errors.notInTauri".
  return new AppError(`__i18n__:${key}`, 'preview');
}

/* ------------------------------------------------------------------ */
/* Read-only preview handlers                                          */
/* ------------------------------------------------------------------ */

async function fetchJson<T>(url: string, timeoutMs = 9000): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

function mapManifest(json: {
  versions: { id: string; type: string; releaseTime: string; url: string; sha1?: string; complianceLevel?: number }[];
}): McVersion[] {
  const known = ['release', 'snapshot', 'old_beta', 'old_alpha'];
  return json.versions.map((v) => ({
    id: v.id,
    type: (known.includes(v.type) ? v.type : 'unknown') as McVersion['type'],
    releaseTime: v.releaseTime,
    url: v.url,
    sha1: v.sha1 ?? null,
    complianceLevel: v.complianceLevel ?? null,
  }));
}

async function previewModrinthSearch(args: Record<string, unknown> | undefined): Promise<unknown> {
  const query = String(args?.query ?? '');
  const limit = Number(args?.limit ?? 20);
  const offset = Number(args?.offset ?? 0);
  const loader = args?.loader as string | null | undefined;
  const gameVersion = args?.gameVersion as string | null | undefined;
  const sort = String(args?.sort ?? 'relevance');
  const facets: string[][] = [['project_type:mod']];
  if (loader && loader !== 'vanilla') facets.push([`categories:${loader}`]);
  if (gameVersion) facets.push([`versions:${gameVersion}`]);
  const params = new URLSearchParams({
    query,
    limit: String(limit),
    offset: String(offset),
    index: sort,
    facets: JSON.stringify(facets),
  });
  const data = await fetchJson<{
    hits: {
      project_id: string;
      slug: string;
      title: string;
      description: string;
      categories: string[];
      project_type: string;
      downloads: number;
      follows: number;
      icon_url: string | null;
      author: string;
      versions: string[];
      date_modified: string;
      gallery: string[];
    }[];
    total_hits: number;
    offset: number;
    limit: number;
  }>(`https://api.modrinth.com/v2/search?${params.toString()}`);
  return {
    hits: data.hits.map((h) => ({
      projectId: h.project_id,
      slug: h.slug,
      title: h.title,
      description: h.description,
      categories: h.categories,
      projectType: h.project_type,
      downloads: h.downloads,
      follows: h.follows,
      iconUrl: h.icon_url,
      author: h.author,
      versions: h.versions,
      dateModified: h.date_modified,
      gallery: h.gallery ?? [],
    })),
    totalHits: data.total_hits,
    offset: data.offset,
    limit: data.limit,
  };
}

async function previewFallback<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  switch (cmd) {
    case 'settings_load':
      return PREVIEW_SETTINGS as unknown as T;
    case 'app_boot':
      return {
        info: {
          version: '1.0.0',
          os: 'windows',
          arch: 'x86_64',
          dataDir: 'C:\\Users\\Player\\AppData\\Roaming\\WinLaucher',
          totalRamMb: 16384,
          availableRamMb: 9120,
          tauri: false,
          preview: true,
        },
      } as unknown as T;
    case 'profiles_list':
      return PREVIEW_PROFILES as unknown as T;
    case 'accounts_list':
      return PREVIEW_ACCOUNTS as unknown as T;
    case 'downloads_list':
      return [] as unknown as T;
    case 'logs_tail':
      return [] as unknown as T;
    case 'java_detect':
      return [] as unknown as T;
    case 'java_list':
      return [] as unknown as T;
    case 'profile_status':
      return PREVIEW_STATUS as unknown as T;
    case 'mc_versions':
      try {
        const json = await fetchJson<Parameters<typeof mapManifest>[0]>(
          'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',
        );
        return mapManifest(json).slice(0, 400) as unknown as T;
      } catch {
        return PREVIEW_VERSIONS as unknown as T;
      }
    case 'news_list':
      return PREVIEW_NEWS as unknown as T;
    case 'modrinth_search':
      try {
        return (await previewModrinthSearch(args)) as unknown as T;
      } catch {
        const q = String(args?.query ?? '').toLowerCase();
        const hits: ModrinthHit[] = PREVIEW_MODS.filter(
          (m) => !q || m.title.toLowerCase().includes(q) || m.slug.includes(q),
        );
        return { hits, totalHits: hits.length, offset: 0, limit: 20 } as unknown as T;
      }
    case 'modrinth_tags':
      return { loaders: [], gameVersions: [], categories: [] } as unknown as T;
    case 'modrinth_project_versions':
      return [] as unknown as T;
    case 'modrinth_project':
      return null as unknown as T;
    case 'modpacks_search':
      return { hits: [], totalHits: 0, offset: 0, limit: 20 } as unknown as T;
    case 'curseforge_status':
      return { available: false, reason: 'errors.curseforgeKey' } as unknown as T;
    case 'curseforge_search':
      throw previewError('errors.curseforgeKey');
    default:
      break;
  }
  if (cmd.startsWith('launch_') || cmd.startsWith('install_') || cmd.startsWith('profile_')) {
    throw previewError('errors.notInTauri');
  }
  throw previewError('errors.notInTauri');
}

export function isPreviewError(e: unknown): boolean {
  return e instanceof AppError && e.code === 'preview';
}

export function errorCode(e: unknown): string | null {
  if (e instanceof AppError && e.message.startsWith('__i18n__:')) {
    return e.message.slice('__i18n__:'.length);
  }
  return null;
}
