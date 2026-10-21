/* ------------------------------------------------------------------
 * WinLaucher — shared types between the Rust core and the React UI.
 * Every structure mirrors a serde (camelCase) type in src-tauri.
 * ------------------------------------------------------------------ */

export type VersionType = 'release' | 'snapshot' | 'old_beta' | 'old_alpha' | 'unknown';

export interface McVersion {
  id: string;
  type: VersionType;
  releaseTime: string;
  url: string;
  sha1?: string | null;
  complianceLevel?: number | null;
  /** Present in the installed-version listings; filled by the backend. */
  installed?: boolean;
}

export type LoaderKind = 'vanilla' | 'fabric' | 'forge' | 'neoforge' | 'quilt';

export interface LoaderRef {
  kind: LoaderKind;
  version: string | null;
}

export interface Profile {
  id: string;
  name: string;
  mcVersion: string;
  loader: LoaderRef;
  gameDir: string;
  ramMb: number;
  javaPath: string | null;
  jvmArgs: string;
  width: number;
  height: number;
  fullscreen: boolean;
  createdAt: string;
  lastPlayedAt: string | null;
  source: 'manual' | 'modpack';
  modpack?: { projectId: string; versionId: string; name: string } | null;
}

export interface ProfileDraft {
  name: string;
  mcVersion: string;
  loader: LoaderRef;
  gameDir?: string | null;
  ramMb?: number | null;
  javaPath?: string | null;
  jvmArgs?: string | null;
  width?: number | null;
  height?: number | null;
  fullscreen?: boolean | null;
  source?: 'manual' | 'modpack';
  modpack?: { projectId: string; versionId: string; name: string } | null;
}

export type AccountKind = 'offline' | 'microsoft';

export interface Account {
  id: string;
  kind: AccountKind;
  name: string;
  uuid: string;
  createdAt: string;
  lastUsedAt: string | null;
  owned: boolean | null;
  skins: { id: string; state: string; url: string; variant: string }[];
  primary: boolean;
}

export interface DeviceCodeInfo {
  deviceCode: string;
  userCode: string;
  verificationUri: string;
  expiresIn: number;
  interval: number;
}

export interface LaunchAccount {
  name: string;
  uuid: string;
  accessToken: string;
  userType: 'msa' | 'legacy' | 'mojang';
  xuid: string;
  userProperties: { name: string; value: string }[];
}

export type LaunchState =
  | 'idle'
  | 'checking'
  | 'downloading'
  | 'installing'
  | 'preparingJava'
  | 'starting'
  | 'running'
  | 'exited'
  | 'error';

export interface LaunchStatus {
  profileId: string | null;
  state: LaunchState;
  message: string;
  code: number | null;
  startedAt: string | null;
}

export interface JavaRuntime {
  path: string;
  version: string;
  major: number;
  vendor: string;
  arch: string;
  source: 'system' | 'managed' | 'custom';
}

export interface ProfileStatus {
  profileId: string;
  versionInstalled: boolean;
  loaderInstalled: boolean;
  loaderName: string;
  missingFiles: number;
  totalFiles: number;
  installedBytes: number;
  requiredJava: number;
  java: JavaRuntime | null;
  ready: boolean;
  blocking: string[];
}

export type DownloadStatus = 'queued' | 'running' | 'done' | 'error' | 'cancelled';

export interface DownloadItem {
  id: string;
  title: string;
  detail: string;
  status: DownloadStatus;
  current: number;
  total: number;
  bytes: number;
  totalBytes: number;
  speed: number;
  startedAt: number;
  finishedAt: number | null;
  error: string | null;
  profileId: string | null;
}

export type ThemeId =
  | 'dark'
  | 'light'
  | 'minecraft'
  | 'purple'
  | 'ocean'
  | 'emerald'
  | 'midnight'
  | 'custom';

export interface CustomTheme {
  accent: string;
  accent2: string;
  background: string;
  transparency: number;
}

export type AnimLevel = 'full' | 'low' | 'off';

export interface AppSettings {
  locale: string;
  localeTouched: boolean;
  theme: ThemeId;
  customTheme: CustomTheme;
  animations: AnimLevel;
  uiScale: number;
  minecraftDir: string;
  defaultRamMb: number;
  javaPath: string | null;
  jvmArgs: string;
  resolutionWidth: number;
  resolutionHeight: number;
  fullscreen: boolean;
  maxConcurrentDownloads: number;
  speedLimitKbps: number;
  autoUpdate: boolean;
  launchOnStartup: boolean;
  minimizeOnLaunch: boolean;
  closeOnLaunch: boolean;
  onboarded: boolean;
  activeProfileId: string | null;
  activeAccountId: string | null;
  curseforgeKeySet: boolean;
}

export interface NewsItem {
  id: string;
  title: string;
  text: string;
  image: string;
  category: string;
  date: string;
  url: string;
}

export interface ModrinthHit {
  projectId: string;
  slug: string;
  title: string;
  description: string;
  categories: string[];
  projectType: string;
  downloads: number;
  follows: number;
  iconUrl: string | null;
  author: string;
  versions: string[];
  dateModified: string;
  gallery: string[];
  body?: string | null;
  sourceUrl?: string | null;
}

export interface ModrinthSearchResult {
  hits: ModrinthHit[];
  totalHits: number;
  offset: number;
  limit: number;
}

export interface ModrinthVersion {
  id: string;
  projectId: string;
  name: string;
  versionNumber: string;
  versionType: string;
  gameVersions: string[];
  loaders: string[];
  datePublished: string;
  downloads: number;
  files: {
    filename: string;
    url: string;
    size: number;
    hashes: Record<string, string>;
    primary: boolean;
  }[];
  dependencies: { projectId: string | null; versionId: string | null; dependencyType: string }[];
}

export interface InstalledMod {
  fileName: string;
  path: string;
  enabled: boolean;
  size: number;
  projectId: string | null;
  projectName: string | null;
  versionName: string | null;
  iconUrl: string | null;
  source: 'modrinth' | 'curseforge' | 'local';
  updateVersionId?: string | null;
  updateVersionName?: string | null;
}

export interface LoaderVersion {
  version: string;
  stable: boolean;
  mcVersion: string | null;
}

export interface ModpackHit {
  projectId: string;
  slug: string;
  title: string;
  description: string;
  iconUrl: string | null;
  downloads: number;
  author: string;
  categories: string[];
  gallery: string[];
  dateModified: string;
}

export interface CurseForgeMod {
  id: number;
  name: string;
  summary: string;
  downloadCount: number;
  logoUrl: string | null;
  categories: string[];
  authors: string[];
  websiteUrl: string | null;
  latestFileId: number | null;
  latestFileName: string | null;
  gameVersions: string[];
  dateModified: string;
}

export interface LogLine {
  ts: string;
  level: string;
  source: 'launcher' | 'game';
  line: string;
}

export interface AppInfo {
  version: string;
  os: string;
  arch: string;
  dataDir: string;
  totalRamMb: number;
  availableRamMb: number;
  tauri: boolean;
  preview: boolean;
}

export interface SkinInfo {
  dataUrl: string;
  width: number;
  height: number;
}

export interface EventMessage {
  type: string;
  [key: string]: unknown;
}
