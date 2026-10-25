import { listProducts } from '@/lib/repo';
import { ProductsManager } from '@/components/admin/products-manager';

export const dynamic = 'force-dynamic';

export default async function AdminProductsPage({
  searchParams,
}: {
  searchParams: Promise<{ kind?: string; new?: string }>;
}) {
  const { kind, new: isNew } = await searchParams;
  return <ProductsManager products={listProducts()} activeKind={kind} openNew={isNew === '1'} />;
}
