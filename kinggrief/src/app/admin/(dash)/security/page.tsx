import { ShieldCheck } from 'lucide-react';
import { currentAdmin } from '@/lib/auth';
import { getAdminById, listAudit } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { formatDate } from '@/lib/utils';
import { SecurityForm } from '@/components/admin/security-form';
import { AdminCard, Pill } from '@/components/admin/kit';

export const dynamic = 'force-dynamic';

export default async function AdminSecurityPage() {
  await cloudSyncLocalDb();
  const admin = await currentAdmin();
  const record = admin ? getAdminById(admin.id) : null;
  const audit = listAudit(80);

  return (
    <div className="space-y-4">
      <div>
        <p className="font-mono text-[0.58rem] uppercase tracking-[0.2em] text-neon/70">защита</p>
        <h1 className="mt-1 font-display text-2xl font-extrabold uppercase tracking-tight text-white">Безопасность</h1>
        <p className="mt-1 text-[0.76rem] text-white/35">Пароль, 2FA и журнал действий администраторов.</p>
      </div>

      <div className="grid gap-2 sm:grid-cols-3">
        {[
          'Пароли хранятся как scrypt-хэш с солью',
          'Сессии HttpOnly + CSRF-токен на мутации',
          'Rate limit на вход, заказы и отзывы',
        ].map((text) => (
          <p key={text} className="flex items-start gap-2 rounded-lg border border-white/7 bg-panel/40 px-3 py-2 text-[0.7rem] leading-relaxed text-white/45">
            <ShieldCheck size={13} className="mt-0.5 shrink-0 text-emerald" /> {text}
          </p>
        ))}
      </div>

      {record && <SecurityForm totpEnabled={record.totpEnabled} username={record.username} />}

      <AdminCard title="Журнал действий" description="Последние 80 событий: входы, изменения товаров, настроек и выдачи">
        {audit.length === 0 ? (
          <p className="py-6 text-center text-[0.74rem] text-white/30">Пока нет записей.</p>
        ) : (
          <div className="max-h-[28rem] overflow-y-auto">
            <table className="admin-table min-w-[640px]">
              <thead><tr><th>Время</th><th>Администратор</th><th>Действие</th><th>Детали</th><th>IP</th></tr></thead>
              <tbody>
                {audit.map((entry) => (
                  <tr key={entry.id}>
                    <td className="whitespace-nowrap font-mono text-[0.6rem] text-white/35">{formatDate(entry.createdAt)}</td>
                    <td className="text-white/70">{entry.actor}</td>
                    <td><Pill color={entry.action.includes('failed') ? '#EF4444' : entry.action.includes('login') ? '#22C55E' : '#A855F7'}>{entry.action}</Pill></td>
                    <td className="max-w-[22rem] truncate text-[0.68rem] text-white/45">{entry.details}</td>
                    <td className="font-mono text-[0.6rem] text-white/30">{entry.ip ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AdminCard>

      <p className="text-[0.64rem] leading-relaxed text-white/25">
        Не запускайте панель с паролем по умолчанию: задайте ADMIN_PASSWORD и ADMIN_SESSION_SECRET в .env до публикации.
        Для продакшена используйте HTTPS — cookie сессии помечается Secure автоматически.
      </p>
    </div>
  );
}
