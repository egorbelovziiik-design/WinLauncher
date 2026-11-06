import { RANKS } from './catalog';
import type { Product } from './types';

/** Partner is a manually assigned media role, never a purchasable rank product. */
export function isNonSaleRank(product: Pick<Product, 'id' | 'kind' | 'meta' | 'name'>): boolean {
  if (product.kind !== 'rank') return false;
  const ids = [product.id, product.meta.rankId];
  // Delivery also falls back to the product name when there is no rankId.
  if (product.name.toLowerCase().replace(/[^a-z0-9_-]/g, '') === 'partner') return true;
  return ids.some((raw) => {
    if (typeof raw !== 'string') return false;
    const value = raw.trim().toLowerCase().replace(/^(?:admin-)?rank-/, '');
    return value === 'partner';
  });
}

/** Render the capability for existing databases without rewriting admin-edited records. */
const creativeRanks = new Set(RANKS.filter((rank) => Number(rank.meta.tier) >= 10).map((rank) => rank.id));
export const CREATIVE_FEATURE = 'Личный творческий режим: /gm 1 (вернуться: /gm 0)';
export function withCreativeFeature(product: Product): Product {
  if (product.kind !== 'rank') return product;
  const raw = typeof product.meta.rankId === 'string' && product.meta.rankId.trim()
    ? product.meta.rankId.trim().toLowerCase()
    : product.id.startsWith('rank-') ? product.id.slice(5).toLowerCase()
      : product.name.toLowerCase().replace(/[^a-z0-9_-]/g, '');
  if (!creativeRanks.has(raw) || product.features.some((text) => text.includes('/gm 1'))) return product;
  return { ...product, features: [...product.features, CREATIVE_FEATURE] };
}
