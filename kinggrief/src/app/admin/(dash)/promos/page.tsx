import { listPromos } from '@/lib/repo';
import { PromosManager } from '@/components/admin/promos-manager';

export const dynamic = 'force-dynamic';

export default function AdminPromosPage() {
  return <PromosManager promos={listPromos()} />;
}
