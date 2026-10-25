import { listCaseItems, listCaseGroups, listProducts } from '@/lib/repo';
import { bridgeStatusCloud, listRecentAdminGrantsCloud, templatesCloud } from '@/lib/delivery';
import { CasesManager } from '@/components/admin/cases-manager';

export const dynamic = 'force-dynamic';

export default async function AdminCasesPage() {
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

  const [bridge, tplMap, initialGrants] = await Promise.all([
    bridgeStatusCloud(),
    templatesCloud(),
    listRecentAdminGrantsCloud(12),
  ]);

  return (
    <CasesManager
      groups={groups}
      packages={packages}
      bridge={bridge}
      bridgeConfigured={Boolean(process.env.MINECRAFT_BRIDGE_SECRET)}
      initialCaseTemplate={tplMap.case}
      initialGrants={initialGrants}
    />
  );
}
