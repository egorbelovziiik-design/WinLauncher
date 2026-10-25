import { listCaseItems, listCaseGroups, listProducts } from '@/lib/repo';
import {
  bridgeStatusCloud,
  listRecentAdminGrantsCloud,
  listRecentRecipientNicksCloud,
  templatesCloud,
} from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { CasesManager } from '@/components/admin/cases-manager';

export const dynamic = 'force-dynamic';

export default async function AdminCasesPage() {
  await cloudSyncLocalDb();

  const groups = listCaseGroups().map((group) => {
    const items = listCaseItems(group.id);
    return {
      ...group,
      items,
      total: Math.round(items.reduce((sum, item) => sum + item.chance, 0) * 100) / 100,
    };
  });
  const packages: Record<string, Array<{ id: string; name: string; price: number; active: boolean }>> = {};
  for (const product of listProducts({ kind: 'case' })) {
    const groupId = String(product.meta.group ?? '');
    packages[groupId] = [
      ...(packages[groupId] ?? []),
      { id: product.id, name: product.name, price: product.price, active: product.active },
    ];
  }

  const rankProducts = listProducts({ kind: 'rank' });
  const [bridge, tplMap, initialGrants, recentNicks] = await Promise.all([
    bridgeStatusCloud(),
    templatesCloud(),
    listRecentAdminGrantsCloud(20),
    listRecentRecipientNicksCloud(14),
  ]);

  return (
    <CasesManager
      groups={groups}
      packages={packages}
      rankProducts={rankProducts}
      bridge={bridge}
      bridgeConfigured={Boolean(process.env.MINECRAFT_BRIDGE_SECRET)}
      initialTemplates={tplMap}
      initialGrants={initialGrants}
      initialRecentNicks={recentNicks}
    />
  );
}
