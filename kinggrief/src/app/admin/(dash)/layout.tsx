import { redirect } from 'next/navigation';
import { currentAdmin } from '@/lib/auth';
import { getSettings } from '@/lib/repo';
import { bridgeStatus } from '@/lib/delivery';
import { AdminShell } from '@/components/admin/shell';

export const dynamic = 'force-dynamic';

export const metadata = {
  title: 'Админ-панель',
  robots: { index: false, follow: false },
};

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const admin = await currentAdmin();
  if (!admin) redirect('/admin/login');
  const settings = getSettings();
  const bridge = bridgeStatus();

  return (
    <AdminShell
      username={admin.username}
      role={admin.role}
      bridgeConnected={bridge.connected}
      testMode={settings.kassa_test_mode === 'true'}
      provider={settings.kassa_provider || 'mock'}
    >
      {children}
    </AdminShell>
  );
}
