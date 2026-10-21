import type { Manifest } from '../services/types';
export const POPULAR_RELEASE_IDS = ['26.2', '1.21.4', '1.20.1', '1.19.2', '1.18.2', '1.16.5', '1.12.2', '1.8.9'] as const;

/** References to real Mojang entries only: never a synthetic catalog. */
export function popularReleases(manifest?: Manifest) {
  const byId = new Map(manifest?.versions.filter(v => v.kind === 'release').map(v => [v.id, v]) ?? []);
  return POPULAR_RELEASE_IDS.flatMap(id => { const entry = byId.get(id); return entry ? [entry] : []; });
}

/** Search the whole manifest, not a recent slice; keep the selected value visible. */
export function profileVersionOptions(manifest: Manifest | undefined, snapshots: boolean, query: string, selected: string) {
  const needle = query.trim().toLowerCase();
  return manifest?.versions.filter(v => v.id === selected || ((snapshots || v.kind === 'release') && v.id.toLowerCase().includes(needle))) ?? [];
}
