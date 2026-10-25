import { listReviews } from '@/lib/repo';
import { cloudSyncLocalDb } from '@/lib/cloud-store';
import { ReviewsManager } from '@/components/admin/reviews-manager';

export const dynamic = 'force-dynamic';

export default async function AdminReviewsPage() {
  await cloudSyncLocalDb();
  return <ReviewsManager reviews={listReviews()} />;
}
