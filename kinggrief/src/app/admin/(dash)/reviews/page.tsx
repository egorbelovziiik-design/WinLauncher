import { listReviews } from '@/lib/repo';
import { ReviewsManager } from '@/components/admin/reviews-manager';

export const dynamic = 'force-dynamic';

export default function AdminReviewsPage() {
  return <ReviewsManager reviews={listReviews()} />;
}
