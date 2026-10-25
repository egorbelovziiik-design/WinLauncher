import { listCaseItems, listCaseGroups, listProducts } from '@/lib/repo';
import { bridgeStatus, listRecentAdminGrants, templates } from '@/lib/delivery';
import { CasesManager } from '@/components/admin/cases-manager';

export const dynamic = 'force-dynamic';

export default function AdminCasesPage() {
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
  return (
    <CasesManager
      groups={groups}
      packages={packages}
      bridge={bridgeStatus()}
      bridgeConfigured={Boolean(process.env.MINECRAFT_BRIDGE_SECRET)}
      initialCaseTemplate={templates().case}
      initialGrants={listRecentAdminGrants(10)}
    />
  );
}
