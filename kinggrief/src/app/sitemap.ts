import type { MetadataRoute } from 'next';
import { listCaseGroups, listProducts } from '@/lib/repo';

export default function sitemap(): MetadataRoute.Sitemap {
  const base = (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(/\/$/, '');
  const staticPages = ['', '/privileges', '/cases', '/relics', '/other', '/reviews', '/rules', '/support', '/cart', '/checkout'].map(
    (path) => ({
      url: `${base}${path}`,
      lastModified: new Date(),
      changeFrequency: (path === '' ? 'daily' : 'weekly') as 'daily' | 'weekly',
      priority: path === '' ? 1 : path === '/privileges' || path === '/cases' ? 0.9 : 0.6,
    }),
  );
  const ranks = listProducts({ kind: 'rank', activeOnly: true }).map((product) => ({
    url: `${base}/privileges/${String(product.meta.rankId ?? product.id)}`,
    lastModified: new Date(),
    changeFrequency: 'weekly' as const,
    priority: 0.8,
  }));
  const cases = listCaseGroups(true).map((group) => ({
    url: `${base}/cases/${group.id}`,
    lastModified: new Date(),
    changeFrequency: 'weekly' as const,
    priority: 0.8,
  }));
  return [...staticPages, ...ranks, ...cases];
}
