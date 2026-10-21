import { useState } from 'react';
import { AlertTriangle, ArrowUpRight, Download, MonitorDown, ShieldCheck } from 'lucide-react';
import { useUI } from '../stores/ui';
import { perform } from '../hooks/useLauncher';
import { openExternal } from '../services/api';
import { useT } from '../locales';
import { Button, Modal } from './ui';
import ProfileEditor from './ProfileEditor';

export default function Overlays() {
  const { nativeNotice, showNativeNotice, confirmation, confirm, editor, editProfile } = useUI(); const [busy, setBusy] = useState(false); const t = useT();
  return <>{nativeNotice && <Modal title={t('Нативная функция Windows')} subtitle="WinLauncher · Minecraft Java Edition" onClose={() => showNativeNotice(false)}><div className="native-notice"><div className="native-icon"><MonitorDown size={40} /></div><h3>Настоящая игра — в настоящем приложении.</h3><p>Сейчас открыт браузерный предпросмотр интерфейса. Он не может запускать Java, сохранять токены в Windows или устанавливать Minecraft.</p><div className="info-box"><ShieldCheck size={19} /><span>Никаких демонстрационных загрузок и поддельных аккаунтов. Эти функции выполняет Rust-backend в Windows 10/11 x64.</span></div><Button variant="primary" onClick={() => void perform(() => openExternal('https://github.com/egorbelovziiik-design/WinLauncher/actions/workflows/windows.yml'))}><Download size={17} />{t('Открыть Windows-сборки')}<ArrowUpRight size={16} /></Button><small>Успешная сборка содержит WinLauncher.exe и WinLauncher-Setup.exe. Для Microsoft-входа нужны Application ID и лицензия. Локальный офлайн-аккаунт создаётся по нику.</small></div></Modal>}
  {confirmation && <Modal title={confirmation.title} onClose={() => { if (!busy) confirm(null); }}><div className="confirm-content"><AlertTriangle size={34} /><p>{confirmation.description}</p></div><div className="modal-actions"><Button disabled={busy} onClick={() => confirm(null)}>{t('Отмена')}</Button><Button variant={confirmation.danger ? 'danger' : 'primary'} busy={busy} onClick={async () => { setBusy(true); try { await perform(confirmation.action); confirm(null); } finally { setBusy(false); } }}>{confirmation.label ?? 'Подтвердить'}</Button></div></Modal>}
  {editor && <ProfileEditor key={editor.profile?.id ?? `${editor.loader}-${editor.version}`} initial={editor} onClose={() => editProfile(null)} />}</>;
}
