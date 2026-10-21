import type { Job, Profile, Session } from '../services/types';
export function formatBytes(bytes: number): string { if (!Number.isFinite(bytes) || bytes < 0) return '—'; if (bytes < 1024) return `${bytes} B`; const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), 4); return `${(bytes / 1024 ** unit).toFixed(unit > 1 ? 1 : 0)} ${['B', 'KB', 'MB', 'GB', 'TB'][unit]}`; }
export function duration(seconds: number): string { if (seconds < 60) return `${Math.floor(seconds)} с`; if (seconds < 3600) return `${Math.floor(seconds / 60)} мин`; return `${Math.floor(seconds / 3600)} ч ${Math.floor(seconds % 3600 / 60)} мин`; }
export function compact(number: number): string { return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(number); }
export function date(timestamp: number | null, locale = 'ru'): string { if (!timestamp) return 'Ещё не запускался'; return new Date(timestamp * 1000).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' }); }
export function progress(job: Job): number | null { if (job.total_bytes > 0) return Math.min(100, Math.max(0, Math.floor(job.completed_bytes * 100 / job.total_bytes))); if (job.total_files > 0) return Math.min(100, Math.floor(job.completed_files * 100 / job.total_files)); return null; }
export function newProfile(loader: Profile['loader'] = 'vanilla', version = ''): Profile { return { id: crypto.randomUUID(), name: loader === 'vanilla' ? 'Мой новый мир' : `${loader.charAt(0).toUpperCase() + loader.slice(1)} · Моя сборка`, game_version: version, loader, loader_version: null, min_memory_mb: 1024, max_memory_mb: 4096, java_path: null, java_args: [], width: 1280, height: 720, fullscreen: false, created_at: Math.floor(Date.now() / 1000), last_played: null, play_seconds: 0, installed: false, modpack: null }; }
export function fileKey(f: { profile_id: string; provider: string; project_id: string }): string { return `${f.profile_id}:${f.provider}:${f.project_id}`; }
export function dailyPlaytime(sessions: Session[], days = 7, now = new Date()): { label: string; seconds: number; today: boolean }[] {
  return Array.from({ length: days }, (_, i) => {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - days + 1 + i);
    const end = new Date(start); end.setDate(end.getDate() + 1);
    const a = start.getTime() / 1000; const b = end.getTime() / 1000;
    const seconds = sessions.reduce((sum, s) => sum + Math.max(0, Math.min(b, s.started_at + s.duration) - Math.max(a, s.started_at)), 0);
    return { label: start.toLocaleDateString('ru', { weekday: 'short' }), seconds, today: i === days - 1 };
  });
}
