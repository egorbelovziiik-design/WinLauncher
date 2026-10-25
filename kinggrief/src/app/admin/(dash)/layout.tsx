import { redirect } from 'next/navigation';
import { currentAdmin } from '@/lib/auth';
import { getSettings } from '@/lib/repo';
import { bridgeStatusCloud } from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { AdminShell } from '@/components/admin/shell';

export const dynamic = 'force-dynamic';

export const metadata = {
  title: 'Админ-панель',
  robots: { index: false, follow: false },
};

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await cloudSyncLocalDb({ includeOrders: true });
  const admin = await currentAdmin();
  if (!admin) redirect('/admin/login');
  const settings = getSettings();
  const bridge = await bridgeStatusCloud();

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
