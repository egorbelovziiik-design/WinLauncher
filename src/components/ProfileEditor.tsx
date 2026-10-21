import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, ChevronDown, Coffee, FolderOpen, HardDrive, Info, Settings2 } from 'lucide-react';
import { command, chooseFile, isDesktop } from '../services/api';
import type { Loader, Manifest, Profile, SystemInfo } from '../services/types';
import { newProfile } from '../utils/format';
import { perform, useLauncher } from '../hooks/useLauncher';
import { useT } from '../locales';
import { Button, ErrorState, Modal, Toggle } from './ui';

export default function ProfileEditor({ initial, onClose }: { initial: { profile?: Profile; loader?: Loader; version?: string }; onClose: () => void }) {
  const [profile, setProfile] = useState<Profile>(() => initial.profile ? { ...initial.profile } : newProfile(initial.loader, initial.version));
  const [advanced, setAdvanced] = useState(false); const [snapshots, setSnapshots] = useState(false); const [saving, setSaving] = useState(false); const [jvmText, setJvmText] = useState(profile.java_args.join('\n'));
  const client = useQueryClient(); const t = useT(); const { busy } = useLauncher();
  const manifest = useQuery<Manifest>({ queryKey: ['versions'], queryFn: () => command('version_manifest'), enabled: isDesktop, staleTime: 300000 });
  const system = useQuery<SystemInfo>({ queryKey: ['system'], queryFn: () => command('get_system_info'), enabled: isDesktop, staleTime: 30000 });
  const releases = useQuery<{ id: string; stable: boolean }[]>({ queryKey: ['loader-versions', profile.game_version, profile.loader], queryFn: () => command('loader_versions', { gameVersion: profile.game_version, loader: profile.loader }), enabled: isDesktop && !!profile.game_version && profile.loader !== 'vanilla', staleTime: 300000 });
  const patch = (p: Partial<Profile>) => setProfile(old => ({ ...old, ...p }));
  useEffect(() => { if (!profile.game_version && manifest.data?.latest.release) setProfile(p => ({ ...p, game_version: manifest.data!.latest.release })); }, [manifest.data, profile.game_version]);
  const total = system.data?.total_memory_mb ?? 16384; const max = Math.floor(Math.min(total, 65536) / 256) * 256;
  const save = async () => {
    setSaving(true);
    await perform(async () => { await command<Profile>('save_profile', { profile: { ...profile, name: profile.name.trim(), java_args: jvmText.split('\n').map(a => a.trim()).filter(Boolean) } }); await client.invalidateQueries({ queryKey: ['snapshot'] }); onClose(); }, initial.profile ? 'Профиль сохранён' : 'Профиль создан');
    setSaving(false);
  };
  return <Modal title={t(initial.profile ? 'Изменить' : 'Создать профиль')} subtitle="Свой Minecraft. Свои настройки. Ничего лишнего." onClose={() => { if (!saving) onClose(); }} wide>
    <form className="profile-form" onSubmit={e => { e.preventDefault(); void save(); }}>
      <div className="profile-name-row"><div className={`profile-art profile-art-${profile.loader}`}><Box size={32} /></div><label className="field"><span>{t('Название профиля')}</span><input value={profile.name} maxLength={64} required onChange={e => patch({ name: e.target.value })} placeholder="Моё новое приключение" /></label></div>
      {!isDesktop && <div className="info-box"><Info size={18} /><span>Версии Mojang, создание и сохранение профилей доступны в приложении Windows. Никакие игровые файлы в браузере не создаются.</span></div>}
      <div className="form-grid"><label className="field"><span>{t('Версия Minecraft')}</span><select value={profile.game_version} onChange={e => patch({ game_version: e.target.value, loader_version: null })} disabled={!manifest.data}><option value="">{manifest.isFetching ? 'Загрузка Mojang…' : 'Выберите версию'}</option>{manifest.data?.versions.filter(v => snapshots || v.kind === 'release').map(v => <option key={v.id} value={v.id}>{v.id}{v.kind !== 'release' ? ` · ${v.kind}` : ''}</option>)}</select></label><label className="field"><span>{t('Загрузчик модов')}</span><select value={profile.loader} onChange={e => patch({ loader: e.target.value as Loader, loader_version: null })}>{['vanilla', 'fabric', 'forge', 'neoforge', 'quilt'].map(v => <option key={v} value={v}>{v === 'vanilla' ? 'Vanilla — без модов' : v === 'neoforge' ? 'NeoForge' : v.charAt(0).toUpperCase() + v.slice(1)}</option>)}</select></label></div>
      <label className="checkbox-label"><input type="checkbox" checked={snapshots} onChange={e => setSnapshots(e.target.checked)} />Показывать snapshots и исторические версии</label>
      {manifest.error && <ErrorState error={manifest.error} retry={() => void manifest.refetch()} />}
      {profile.loader !== 'vanilla' && <label className="field"><span>Версия {profile.loader}</span><select value={profile.loader_version ?? ''} onChange={e => patch({ loader_version: e.target.value || null })}><option value="">Автоматически · совместимая стабильная</option>{releases.data?.map(v => <option key={v.id} value={v.id}>{v.id}{v.stable ? '' : ' · beta'}</option>)}</select>{releases.error && <small className="text-warning">{String(releases.error)}</small>}</label>}
      <div className="form-divider" /><div className="form-section-title"><div><HardDrive size={18} /><h3>{t('Оперативная память')}</h3></div><span>{system.data ? `Всего ${(total / 1024).toFixed(1)} GB` : 'Определяется в Windows'}</span></div>
      <div className="performance-presets">{[['Low-End PC', 2048, 512], ['Balanced', 4096, 1024], ['High Performance', 8192, 2048], ['Modded', 6144, 1024]].map(([name, maximum, minimum]) => <button type="button" className={profile.max_memory_mb === maximum ? 'selected' : ''} key={name} onClick={() => patch({ max_memory_mb: Math.min(Number(maximum), max), min_memory_mb: Number(minimum) })}>{name}</button>)}</div>
      <div className="ram-value"><span>{t('Максимум')} (−Xmx)</span><strong>{(profile.max_memory_mb / 1024).toFixed(2).replace(/0+$/, '').replace(/\.$/, '')} <small>GB</small></strong></div><input className="ram-slider" type="range" aria-label="Максимум памяти Minecraft" min={512} max={max} step={256} value={profile.max_memory_mb} onChange={e => { const value = Number(e.target.value); patch({ max_memory_mb: value, min_memory_mb: Math.min(profile.min_memory_mb, value) }); }} /><div className="range-labels"><span>0.5 GB</span><span>{max / 1024} GB</span></div>
      <label className="field field-inline"><span>{t('Минимум')} (−Xms), MB</span><input type="number" min={256} max={profile.max_memory_mb} step={256} value={profile.min_memory_mb} onChange={e => patch({ min_memory_mb: Number(e.target.value) })} /></label>
      {system.data && profile.max_memory_mb + 1024 > system.data.available_memory_mb && <div className="info-box warning"><Info size={17} /><span>Недостаточно свободной памяти. Оставьте хотя бы 1 GB для Windows. Перед запуском потребуется подтверждение.</span></div>}
      <button type="button" className="expand-row" aria-expanded={advanced} onClick={() => setAdvanced(!advanced)}><Settings2 size={17} />{t('Дополнительные настройки')}<ChevronDown size={16} className={advanced ? 'rotate-180' : ''} /></button>
      {advanced && <div className="advanced-form"><label className="field"><span><Coffee size={14} /> Java executable</span><div className="input-with-button"><input value={profile.java_path ?? ''} onChange={e => patch({ java_path: e.target.value || null })} placeholder="Автоматически · Eclipse Temurin" /><Button onClick={() => void perform(async () => { const path = await chooseFile(['exe']); if (path) patch({ java_path: path }); })}><FolderOpen size={17} /></Button></div><small>Пустое поле — поиск и установка нужной Java (8 / 17 / 21 / 25).</small></label><div className="form-grid"><label className="field"><span>Ширина окна</span><input type="number" min={640} max={7680} value={profile.width} onChange={e => patch({ width: Number(e.target.value) })} /></label><label className="field"><span>Высота окна</span><input type="number" min={360} max={4320} value={profile.height} onChange={e => patch({ height: Number(e.target.value) })} /></label></div><Toggle checked={profile.fullscreen} onChange={fullscreen => patch({ fullscreen })} label={t('Полноэкранный режим')} /><label className="field"><span>JVM arguments · по одному на строке</span><textarea rows={3} value={jvmText} onChange={e => setJvmText(e.target.value)} placeholder="-XX:+UseG1GC" /><small>−Xms / −Xmx задаются выше. Java agents и подмена classpath запрещены.</small></label></div>}
      <div className="info-box subtle"><Box size={17} /><span>Моды, миры и скриншоты этого профиля хранятся отдельно. При удалении файлы сохраняются в резервной папке.</span></div>
      <div className="modal-actions"><Button onClick={onClose} disabled={saving}>{t('Отмена')}</Button><Button type="submit" variant="primary" busy={saving} disabled={busy || (isDesktop && !profile.game_version) || !profile.name.trim()}>{t(initial.profile ? 'Сохранить' : 'Создать профиль')}</Button></div>
    </form>
  </Modal>;
}
