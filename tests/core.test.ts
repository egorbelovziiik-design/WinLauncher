import { describe, expect, it } from 'vitest';
import { dailyPlaytime, fileKey, formatBytes, progress } from '../src/utils/format';
import { getTheme, themes } from '../src/themes';
import { translate } from '../src/locales';
import type { Job, Session } from '../src/services/types';

describe('real progress', () => {
  const job: Job = { stage: 'assets', detail: '', completed_bytes: 45, total_bytes: 100, completed_files: 0, total_files: 0 };
  it('reports actual byte progress, never a timer', () => expect(progress(job)).toBe(45));
  it('is indeterminate for unknown totals', () => expect(progress({ ...job, completed_bytes: 0, total_bytes: 0 })).toBeNull());
  it('clamps invalid network totals', () => expect(progress({ ...job, completed_bytes: 110 })).toBe(100));
  it('can use verified file count', () => expect(progress({ ...job, total_bytes: 0, completed_files: 2, total_files: 4 })).toBe(50));
});
describe('data formatting and identity', () => {
  it('formats real bytes', () => { expect(formatBytes(0)).toBe('0 B'); expect(formatBytes(1024 ** 3)).toBe('1.0 GB'); expect(formatBytes(NaN)).toBe('—'); });
  it('keeps provider and profile isolation', () => { expect(fileKey({ profile_id: 'a', provider: 'modrinth', project_id: 'x' })).not.toBe(fileKey({ profile_id: 'b', provider: 'modrinth', project_id: 'x' })); });
});
describe('statistics', () => {
  it('has no fabricated playtime', () => expect(dailyPlaytime([]).every(d => d.seconds === 0)).toBe(true));
  it('splits an overnight game across local days', () => {
    const now = new Date(2026, 9, 21, 12, 0, 0); const start = new Date(2026, 9, 20, 23, 30, 0).getTime() / 1000;
    const session: Session = { id: '1', profile_id: '1', account_id: '1', version: '1.21.4', loader: 'vanilla', started_at: start, duration: 3600, exit_code: 0 };
    const data = dailyPlaytime([session], 7, now); expect(data[5].seconds).toBe(1800); expect(data[6].seconds).toBe(1800);
  });
});
describe('theme engine and locales', () => {
  it('has ten distinct themes and a safe default', () => { expect(themes).toHaveLength(10); expect(new Set(themes.map(t => t.accent)).size).toBe(10); expect(getTheme('invalid').id).toBe('overworld'); });
  it('supports Russian, English and Ukrainian', () => { expect(translate('Главная', 'ru')).toBe('Главная'); expect(translate('Главная', 'en')).toBe('Home'); expect(translate('Главная', 'uk')).toBe('Головна'); });
});
