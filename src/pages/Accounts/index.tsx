import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Check, Copy, ExternalLink, KeyRound, LogOut, Plus, Shield, ShieldCheck, UserRound } from 'lucide-react';
import { command, isDesktop, openExternal } from '../../services/api';
import type { Challenge, PollResult } from '../../services/types';
import { perform, useLauncher } from '../../hooks/useLauncher';
import { useUI } from '../../stores/ui';
import { useT } from '../../locales';
import { date, duration } from '../../utils/format';
import { Button, IconButton, MicrosoftMark, Modal, PageHeading, Tag } from '../../components/ui';

export default function Accounts() {
  const { snapshot, account, updateSettings, busy } = useLauncher(); const { setPage, setSettingsTab, confirm, showNativeNotice } = useUI(); const client = useQueryClient(); const t = useT();
  const [challenge, setChallenge] = useState<Challenge | null>(null); const [starting, setStarting] = useState(false); const [authError, setAuthError] = useState(''); const [remaining, setRemaining] = useState(0); const active = useRef<string | null>(null);
  useEffect(() => {
    if (!challenge) return; let cancelled = false; let timer: ReturnType<typeof setTimeout>; const expires = Date.now() + challenge.expires_in * 1000; active.current = challenge.id;
    const clock = setInterval(() => setRemaining(Math.max(0, Math.ceil((expires - Date.now()) / 1000))), 1000);
    const poll = async () => {
      if (cancelled) return;
      try {
        const result = await command<PollResult>('poll_login', { id: challenge.id });
        if (cancelled) return;
        if (result.pending) timer = setTimeout(() => void poll(), result.interval * 1000);
        else { active.current = null; setChallenge(null); await client.invalidateQueries({ queryKey: ['snapshot'] }); }
      } catch (e) { if (!cancelled) setAuthError(e instanceof Error ? e.message : String(e)); }
    };
    timer = setTimeout(() => void poll(), challenge.interval * 1000);
    return () => { cancelled = true; clearTimeout(timer); clearInterval(clock); if (active.current === challenge.id) void command('cancel_login', { id: challenge.id }).catch(() => {}); };
  }, [challenge, client]);
  const signIn = async () => {
    if (!isDesktop) { showNativeNotice(true); return; }
    if (!snapshot.settings.microsoft_client_id) { setSettingsTab('integrations'); setPage('settings'); return; }
    setStarting(true); setAuthError('');
    const code = await perform(() => command<Challenge>('begin_login')); if (code) { setChallenge(code); setRemaining(code.expires_in); }
    setStarting(false);
  };
  return <div><PageHeading eyebrow="ТВОЯ ЛИЧНОСТЬ В МИРЕ БЛОКОВ" title={t('Аккаунты')} description="Один лаунчер. Все твои приключения." action={<Button variant="primary" busy={starting} disabled={busy} onClick={() => void signIn()}><Plus size={17} />Добавить аккаунт</Button>} />
    {snapshot.accounts.length ? <div className="accounts-grid">{snapshot.accounts.map(a => { const sessions = snapshot.sessions.filter(s => s.account_id === a.id); return <article key={a.id} className={`account-card ${a.id === account?.id ? 'account-selected' : ''}`}><div className="account-card-header"><div className="large-avatar">{a.skin_url ? <div className="skin-head" style={{ backgroundImage: `url("${a.skin_url.replaceAll('"', '%22')}")` }} /> : <UserRound size={34} />}</div><div><h2>{a.name}</h2><Tag color="green"><ShieldCheck size={12} /> Microsoft · лицензия проверена</Tag></div>{a.id === account?.id && <span className="selected-check"><Check size={18} /></span>}</div><label className="uuid-field"><span>MINECRAFT UUID</span><code>{a.id}</code><IconButton label="Скопировать UUID" onClick={() => void perform(() => navigator.clipboard.writeText(a.id), 'UUID скопирован')}><Copy size={15} /></IconButton></label><div className="account-stat-row"><div><span>Всего в игре</span><strong>{duration(sessions.reduce((sum, s) => sum + s.duration, 0))}</strong></div><div><span>Последний запуск</span><strong>{sessions.length ? date(Math.max(...sessions.map(s => s.started_at))) : 'Ещё не было'}</strong></div></div><div className="account-card-actions"><Button disabled={busy || a.id === account?.id} onClick={() => void perform(() => updateSettings({ selected_account: a.id }))}>{a.id === account?.id ? <><Check size={16} />Активный аккаунт</> : 'Использовать аккаунт'}</Button><IconButton disabled={busy} label="Выйти из аккаунта" onClick={() => confirm({ title: `Выйти из ${a.name}?`, description: 'Токены будут удалены из Windows Credential Manager. Миры и статистика останутся на устройстве.', danger: true, label: 'Выйти', action: async () => { await command('remove_account', { id: a.id }); await client.invalidateQueries({ queryKey: ['snapshot'] }); } })}><LogOut size={18} /></IconButton></div></article>; })}</div> : <section className="account-welcome"><div className="account-art"><div className="avatar-pixel"><i /><i /><i /><i /><i /><i /></div><span className="account-art-shield"><ShieldCheck size={24} /></span><span className="orbit-square orbit-one" /><span className="orbit-square orbit-two" /></div><p className="eyebrow">ТВОЯ ИСТОРИЯ НАЧИНАЕТСЯ ЗДЕСЬ</p><h2>Знакомые миры.<br /><span>Твой настоящий аккаунт.</span></h2><p>Войди через Microsoft, чтобы играть в Minecraft Java Edition.<br />Мы проверим лицензию — пароль останется только у Microsoft.</p><Button variant="primary" busy={starting} onClick={() => void signIn()}><MicrosoftMark />Войти через Microsoft<ArrowUpRight size={17} /></Button><button className="text-button" onClick={() => void perform(() => openExternal('https://www.minecraft.net/store/minecraft-java-bedrock-edition-pc'))}>Ещё нет Minecraft? <ExternalLink size={13} /></button></section>}
    <div className="trust-grid"><div><Shield size={23} /><h3>Без паролей</h3><p>Официальный OAuth 2.0 Device Code. Пароль вводится только на сайте Microsoft.</p></div><div><KeyRound size={23} /><h3>Под защитой Windows</h3><p>Токены хранятся в Credential Manager. Не в SQLite, не в логах и не в браузере.</p></div><div><ShieldCheck size={23} /><h3>Только лицензионная игра</h3><p>Проверка Minecraft Entitlements перед каждым запуском. Без обхода лицензии.</p></div></div>
    {challenge && <Modal title="Вход через Microsoft" subtitle="Авторизация на официальном сайте — без передачи пароля лаунчеру." onClose={() => setChallenge(null)}><div className="login-dialog"><MicrosoftMark /><ol><li>Открой официальный сайт Microsoft по кнопке ниже.</li><li>Введи этот одноразовый код и выбери свой аккаунт.</li><li>Вернись сюда — подключение завершится автоматически.</li></ol><div className="device-code"><code>{challenge.user_code}</code><IconButton label="Копировать код" onClick={() => void perform(() => navigator.clipboard.writeText(challenge.user_code), 'Код скопирован')}><Copy size={20} /></IconButton></div><Button variant="primary" onClick={() => void perform(() => openExternal(challenge.verification_uri))}>Открыть Microsoft<ExternalLink size={16} /></Button><p>{authError ? <span className="text-danger">{authError}</span> : `Ожидаем подтверждения · код истечёт через ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}`}</p><div className="info-box"><ShieldCheck size={18} /><span>Не сообщай этот код другим людям. Он предназначен только для входа в твою копию WinLauncher.</span></div></div></Modal>}
  </div>;
}
