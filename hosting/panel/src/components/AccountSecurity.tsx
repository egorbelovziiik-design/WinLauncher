import React, { useState } from 'react';
import { ShieldCheck, KeyRound, Lock, CheckCircle2, AlertTriangle, Copy } from 'lucide-react';
import { apiFetch } from '../api';
import type { HostSystemTelemetry, UserPublic } from '../types';

interface Props {
  user: UserPublic;
  hostTelemetry: HostSystemTelemetry | null;
  onUserUpdated: (user: UserPublic) => void;
  notify: (msg: string, type?: 'success' | 'error') => void;
}

export const AccountSecurity: React.FC<Props> = ({
  user,
  hostTelemetry,
  onUserUpdated,
  notify,
}) => {
  const [setupData, setSetupData] = useState<{
    secret: string;
    qrSvg: string;
    currentPreviewCode: string;
  } | null>(null);
  const [verifyCode, setVerifyCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [disablePassword, setDisablePassword] = useState('');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const startTotpSetup = async () => {
    setLoading(true);
    try {
      const res = await apiFetch<{
        secret: string;
        qrSvg: string;
        currentPreviewCode: string;
      }>('/api/account/2fa/setup', { method: 'POST' });
      setSetupData(res);
      setVerifyCode(res.currentPreviewCode);
    } catch (err) {
      notify((err as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const confirmEnableTotp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await apiFetch<{ user: UserPublic; recoveryCodes: string[] }>(
        '/api/account/2fa/enable',
        {
          method: 'POST',
          body: JSON.stringify({ code: verifyCode }),
        }
      );
      onUserUpdated(res.user);
      setRecoveryCodes(res.recoveryCodes);
      setSetupData(null);
      notify('Двухфакторная аутентификация (2FA TOTP) успешно активирована!');
    } catch (err) {
      notify((err as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDisableTotp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await apiFetch<{ user: UserPublic }>('/api/account/2fa/disable', {
        method: 'POST',
        body: JSON.stringify({ password: disablePassword }),
      });
      onUserUpdated(res.user);
      setDisablePassword('');
      setRecoveryCodes(null);
      notify('Двухфакторная аутентификация отключена.');
    } catch (err) {
      notify((err as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await apiFetch('/api/account/password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      setCurrentPassword('');
      setNewPassword('');
      notify('Пароль аккаунта успешно обновлён (Scrypt KDF).');
    } catch (err) {
      notify((err as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Защита аккаунта и 2FA</h1>
        <p className="text-sm text-slate-400 mt-1">
          Управление двухфакторной аутентификацией TOTP (RFC 6238), резервными кодами, паролем и криптографией панели.
        </p>
      </div>

      {/* Сводка криптографической защиты */}
      <div className="grid sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-emerald-400 mb-1">
            <ShieldCheck className="w-4 h-4" /> Двухфакторная защита (TOTP)
          </div>
          <div className="text-base font-bold text-white">
            {user.totpEnabled ? 'Активна (Google Auth / Authy)' : 'Не включена'}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Одноразовые 6-значные коды каждые 30 секунд + 8 резервных кодов.
          </p>
        </div>

        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-cyan-400 mb-1">
            <Lock className="w-4 h-4" /> Транспорт HTTPS / TLS 1.3
          </div>
          <div className="text-base font-bold text-white">
            {hostTelemetry?.httpsConfigured ? 'Caddy TLS 1.3 + HSTS готов' : 'Reverse Proxy TLS'}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Автовыпуск сертификатов Let&apos;s Encrypt, HTTP/3 QUIC и защищённый WSS.
          </p>
        </div>

        <div className="p-4 rounded-2xl bg-[#0d1422] border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-indigo-400 mb-1">
            <KeyRound className="w-4 h-4" /> Защита от брутфорса
          </div>
          <div className="text-base font-bold text-white">Scrypt + Rate-Limit</div>
          <p className="text-xs text-slate-400 mt-1">
            Блокировка перебора после 6 ошибок и криптостойкая соль 128 бит.
          </p>
        </div>
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        {/* Блок 2FA TOTP */}
        <div className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <KeyRound className="w-5 h-5 text-emerald-400" /> Двухфакторная аутентификация (2FA)
            </h2>
            {user.totpEnabled ? (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/15 text-emerald-400 text-xs font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" /> Включена
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/15 text-amber-300 text-xs font-semibold">
                <AlertTriangle className="w-3.5 h-3.5" /> Рекомендуется включить
              </span>
            )}
          </div>

          {!user.totpEnabled && !setupData && (
            <div className="space-y-3">
              <p className="text-xs text-slate-400 leading-relaxed">
                Защитите панель управления вашими Minecraft-серверами и нодами от компрометации пароля. Поддерживаются приложения Google Authenticator, 2FAS, Authy, 1Password и Bitwarden.
              </p>
              <button
                type="button"
                disabled={loading}
                onClick={startTotpSetup}
                className="px-4 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-sm transition cursor-pointer"
              >
                Настроить 2FA с QR-кодом
              </button>
            </div>
          )}

          {setupData && (
            <form onSubmit={confirmEnableTotp} className="space-y-4">
              <div className="flex flex-col sm:flex-row items-center gap-4 p-4 rounded-xl bg-slate-950 border border-slate-800">
                <div
                  className="rounded-xl overflow-hidden border border-slate-800 shrink-0"
                  dangerouslySetInnerHTML={{ __html: setupData.qrSvg }}
                />
                <div className="space-y-2 text-xs">
                  <div className="text-slate-300 font-medium">
                    1. Отсканируйте QR-код в Google Authenticator или введите ключ вручную:
                  </div>
                  <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 font-mono text-emerald-400 break-all">
                    {setupData.secret}
                  </div>
                  <div className="text-slate-400">
                    Текущий расчётный код для быстрой проверки:{' '}
                    <span className="font-mono font-bold text-white">
                      {setupData.currentPreviewCode}
                    </span>
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  2. Введите 6-значный код подтверждения:
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    required
                    value={verifyCode}
                    onChange={(e) => setVerifyCode(e.target.value)}
                    className="flex-1 px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 font-mono text-sm text-white"
                    placeholder="000000"
                  />
                  <button
                    type="submit"
                    disabled={loading}
                    className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-sm cursor-pointer"
                  >
                    Активировать
                  </button>
                </div>
              </div>
            </form>
          )}

          {recoveryCodes && (
            <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/40 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-300">
                  Сохраните одноразовые резервные коды восстановления:
                </span>
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(recoveryCodes.join('\n'));
                    notify('Резервные коды скопированы в буфер обмена');
                  }}
                  className="inline-flex items-center gap-1 text-xs text-emerald-400 hover:underline cursor-pointer"
                >
                  <Copy className="w-3.5 h-3.5" /> Копировать
                </button>
              </div>
              <div className="grid grid-cols-2 gap-1.5 font-mono text-xs text-white bg-slate-950/80 p-3 rounded-lg">
                {recoveryCodes.map((c) => (
                  <div key={c}>{c}</div>
                ))}
              </div>
            </div>
          )}

          {user.totpEnabled && (
            <form onSubmit={handleDisableTotp} className="pt-3 border-t border-slate-800 space-y-3">
              <div className="text-xs text-slate-400">
                Для отключения двухфакторной аутентификации подтвердите ваш текущий пароль:
              </div>
              <div className="flex gap-2">
                <input
                  type="password"
                  required
                  value={disablePassword}
                  onChange={(e) => setDisablePassword(e.target.value)}
                  placeholder="Текущий пароль аккаунта"
                  className="flex-1 px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white"
                />
                <button
                  type="submit"
                  disabled={loading}
                  className="px-4 py-2 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-rose-300 text-xs font-semibold cursor-pointer"
                >
                  Отключить 2FA
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Смена пароля */}
        <div className="p-6 rounded-2xl bg-[#0d1422] border border-slate-800 space-y-4">
          <h2 className="text-base font-bold text-white flex items-center gap-2">
            <Lock className="w-5 h-5 text-cyan-400" /> Смена пароля
          </h2>
          <form onSubmit={handleChangePassword} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Текущий пароль
              </label>
              <input
                type="password"
                required
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                Новый пароль (не менее 8 символов)
              </label>
              <input
                type="password"
                required
                minLength={8}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-sm text-white"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="px-5 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-sm transition cursor-pointer"
            >
              Обновить пароль
            </button>
          </form>

          <div className="pt-4 border-t border-slate-800 text-xs text-slate-400 space-y-1">
            <div>
              Ваш логин: <span className="text-slate-200 font-medium">{user.email}</span>
            </div>
            <div>
              Квота аккаунта:{' '}
              <span className="text-slate-200 font-medium">
                до {user.maxServersQuota} серверов / {(user.maxRamMbQuota / 1024).toFixed(0)} ГБ DDR5 RAM
              </span>
            </div>
            {user.lastLoginIp && (
              <div>
                Последний вход: <span className="font-mono text-slate-300">{user.lastLoginIp}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
