import { listPromos } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { PromosManager } from '@/components/admin/promos-manager';

export const dynamic = 'force-dynamic';

export default async function AdminPromosPage() {
  await cloudSyncLocalDb();
  return <PromosManager promos={listPromos()} />;
}
