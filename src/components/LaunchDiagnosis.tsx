import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, ExternalLink, FolderOpen, RefreshCw, Settings2 } from 'lucide-react';
import { command, isDesktop, openExternal } from '../services/api';
import { perform, useLauncher } from '../hooks/useLauncher';
import { useUI } from '../stores/ui';
import { Button, ErrorState, Tag } from './ui';

type Diagnosis = { profile_id: string; timestamp: number; exit_code: number | null; kind: string; title: string; detail: string; advice: string[]; evidence: string[]; report_path: string | null; java_path: string | null };

export default function LaunchDiagnosis() {
  const { profile, busy, snapshot } = useLauncher(); const { confirm, editProfile } = useUI(); const client = useQueryClient();
  const query = useQuery<Diagnosis | null>({ queryKey: ['launch-diagnosis', profile?.id], queryFn: () => command('get_launch_diagnosis', { profileId: profile!.id }), enabled: isDesktop && !!profile && !snapshot.running, staleTime: 5000 });
  if (!profile || snapshot.running) return null;
  const report = query.data;
  const repair = () => confirm({ title: `Восстановить «${profile.name}»?`, description: 'Будет выбрана управляемая Temurin и текущая совместимая версия загрузчика, повторно проверены игровые файлы. Ручной путь Java и дополнительные JVM-аргументы сбросятся. Миры, моды и скриншоты не удаляются. Несовместимые моды и видеодрайвер автоматически не исправляются.', label: 'Восстановить профиль', action: async () => { await command('repair_game_profile', { profileId: profile.id }); await client.invalidateQueries({ queryKey: ['snapshot'] }); } });
  return <section className={`launch-diagnosis ${report && !['closed', 'stopped'].includes(report.kind) ? 'has-failure' : ''}`} aria-label="Диагностика запуска">
    <div className="diagnosis-heading"><AlertTriangle size={21} /><div><h2>{report?.title ?? 'Проверка и восстановление запуска'}</h2><p>{profile.name} · {profile.game_version} · {profile.loader}</p></div>{report && <Tag>{report.exit_code === null ? 'ОТЧЁТ JVM' : `КОД ${report.exit_code}`}</Tag>}<Button aria-label="Обновить диагностику" busy={query.isFetching} onClick={() => void query.refetch()}><RefreshCw size={15} /></Button></div>
    {query.error && <ErrorState error={query.error} />}
    {report && <><p className="diagnosis-detail">{report.detail}</p>{report.advice.length > 0 && <ol>{report.advice.map(line => <li key={line}>{line}</li>)}</ol>}{report.java_path && <p className="diagnosis-path">Java: <code>{report.java_path}</code></p>}{report.report_path && <p className="diagnosis-path">Отчёт: <code>{report.report_path}</code></p>}{report.evidence.length > 0 && <details><summary>Подтверждение из журнала · {new Date(report.timestamp).toLocaleString()}</summary><pre>{report.evidence.join('\n')}</pre></details>}</>}
    <div className="button-group"><Button disabled={busy} variant="primary" onClick={repair}><RefreshCw size={15} />{profile.loader === 'fabric' ? 'Восстановить Fabric / Java' : 'Восстановить профиль / Java'}</Button><Button disabled={busy} onClick={() => editProfile({ profile })}><Settings2 size={15} />Настройки профиля</Button>{report?.report_path && <Button onClick={() => void perform(() => command('open_crash_report_folder', { profileId: profile.id }))}><FolderOpen size={15} />Папка отчёта</Button>}{report?.kind === 'amd_opengl' && <Button onClick={() => void perform(() => openExternal('https://www.amd.com/en/support/download/drivers.html'))}>Драйверы AMD <ExternalLink size={14} /></Button>}<Button disabled={busy} onClick={() => editProfile({ loader: 'vanilla', version: profile.game_version })}>Чистый профиль Vanilla</Button></div>
  </section>;
}
