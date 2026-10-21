export type Loader = 'vanilla' | 'fabric' | 'forge' | 'neoforge' | 'quilt';
export type Page = 'home' | 'profiles' | 'versions' | 'mods' | 'modpacks' | 'accounts' | 'themes' | 'statistics' | 'logs' | 'settings' | 'worlds';
export type Provider = 'modrinth' | 'curseforge';
export type ContentKind = 'mod' | 'modpack' | 'resourcepack' | 'shader' | 'datapack';
export interface DiscordSettings { enabled: boolean; application_id: string; show_version: boolean; show_pack: boolean; show_elapsed: boolean; show_download: boolean; show_launcher: boolean; hide_while_playing: boolean; show_website: boolean }
export interface Settings { theme: string; language: 'ru' | 'en' | 'uk'; effects: boolean; selected_profile: string | null; selected_account: string | null; microsoft_client_id: string; data_dir: string; download_concurrency: number; download_limit_kib: number; show_snapshots: boolean; close_to_tray: boolean; launch_hotkey: string; check_updates: boolean; favorite_versions: string[]; favorite_projects: string[]; discord: DiscordSettings }
export interface Profile { id: string; name: string; game_version: string; loader: Loader; loader_version: string | null; min_memory_mb: number; max_memory_mb: number; java_path: string | null; java_args: string[]; width: number; height: number; fullscreen: boolean; created_at: number; last_played: number | null; play_seconds: number; installed: boolean; modpack: string | null }
export interface Account { kind: 'microsoft' | 'offline'; id: string; name: string; skin_url: string | null; added_at: number }
export interface InstalledFile { profile_id: string; provider: string; project_id: string; version_id: string; name: string; filename: string; kind: string; sha: string; hash_algorithm: string; enabled: boolean; download_url: string; size: number }
export interface Session { id: string; profile_id: string; account_id: string; version: string; loader: string; started_at: number; duration: number; exit_code: number | null }
export interface Job { stage: string; detail: string; completed_bytes: number; total_bytes: number; completed_files: number; total_files: number }
export interface RunningGame { pid: number; profile_id: string; version: string; loader: string; profile_name: string; started_at: number }
export interface LogLine { timestamp: number; level: string; source: string; message: string }
export interface SystemInfo { cpu: string; gpu: string; os: string; arch: string; total_memory_mb: number; available_memory_mb: number; free_disk_bytes: number }
export interface JavaRuntime { path: string; version: string; major: number; architecture: string; managed: boolean }
export interface Snapshot { settings: Settings; profiles: Profile[]; accounts: Account[]; installed: InstalledFile[]; sessions: Session[]; job: Job | null; running: RunningGame | null; discord_status: string; curseforge_configured: boolean; version: string }
export interface VersionEntry { id: string; kind: string; url: string; sha1: string; release_time: string }
export interface Manifest { latest: Record<string, string>; versions: VersionEntry[] }
export interface SearchProject { id: string; provider: Provider; title: string; description: string; icon_url: string | null; downloads: number; author: string; categories: string[]; project_type: ContentKind; slug: string }
export interface SearchResults { hits: SearchProject[]; total: number; offset: number }
export interface ProjectDetail { project: SearchProject; body: string; gallery: string[]; website: string }
export interface LocalFile { name: string; size: number; modified: number }
export interface Challenge { id: string; user_code: string; verification_uri: string; expires_in: number; interval: number }
export interface PollResult { pending: boolean; interval: number; account: Account | null }
export interface Notification { id: string; kind: 'success' | 'error' | 'info'; message: string; timestamp: number }
