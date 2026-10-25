import { listCaseGroups, listProducts } from '@/lib/repo';
import {
  bridgeStatusCloud,
  listRecentAdminGrantsCloud,
  listRecentRecipientNicksCloud,
  templatesCloud,
} from '@/lib/delivery';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { ProductsManager } from '@/components/admin/products-manager';

export const dynamic = 'force-dynamic';

export default async function AdminProductsPage({
  searchParams,
}: {
  searchParams: Promise<{ kind?: string; new?: string }>;
}) {
  await cloudSyncLocalDb();
  const { kind, new: isNew } = await searchParams;
  const products = listProducts();
  const caseGroups = listCaseGroups();
  const [bridge, tplMap, initialGrants, recentNicks] = await Promise.all([
    bridgeStatusCloud(),
    templatesCloud(),
    listRecentAdminGrantsCloud(20),
    listRecentRecipientNicksCloud(14),
  ]);

  return (
    <ProductsManager
      products={products}
      caseGroups={caseGroups}
      activeKind={kind}
      openNew={isNew === '1'}
      bridge={bridge}
      initialGrants={initialGrants}
      initialRecentNicks={recentNicks}
      initialTemplates={tplMap}
    />
  );
}
