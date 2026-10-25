export type RelicPricing = {
  minAmount: number;
  maxAmount: number;
  minPrice: number;
  points: Array<[number, number]>;
};

export const FALLBACK_PRICING: RelicPricing = {
  minAmount: 1,
  maxAmount: 100000,
  minPrice: 5,
  points: [
    [1000, 19], [2500, 39], [5000, 69], [10000, 119], [15000, 169],
    [25000, 249], [50000, 449], [75000, 649], [100000, 799],
  ],
};

export function parseRelicPricing(raw: string | undefined): RelicPricing {
  try {
    const parsed = JSON.parse(raw ?? '') as RelicPricing;
    if (!Array.isArray(parsed.points) || parsed.points.length < 2) return FALLBACK_PRICING;
    const points = parsed.points
      .map((p) => [Number(p[0]), Number(p[1])] as [number, number])
      .filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]) && p[0] > 0)
      .sort((a, b) => a[0] - b[0]);
    if (points.length < 2) return FALLBACK_PRICING;
    return {
      minAmount: Math.max(1, Number(parsed.minAmount ?? 1)),
      maxAmount: Math.max(points[points.length - 1][0], Number(parsed.maxAmount ?? 100000)),
      minPrice: Math.max(1, Number(parsed.minPrice ?? 5)),
      points,
    };
  } catch {
    return FALLBACK_PRICING;
  }
}

/** Линейная интерполяция между точками прайса, округление вверх до рубля. */
export function estimateRelicPrice(amount: number, pricing: RelicPricing): number {
  const safeAmount = Math.min(Math.max(Math.floor(amount), pricing.minAmount), pricing.maxAmount);
  const points = pricing.points;
  const first = points[0];
  if (safeAmount <= first[0]) {
    return Math.max(pricing.minPrice, Math.ceil((safeAmount / first[0]) * first[1]));
  }
  for (let i = 0; i < points.length - 1; i++) {
    const [x1, y1] = points[i];
    const [x2, y2] = points[i + 1];
    if (safeAmount >= x1 && safeAmount <= x2) {
      const ratio = (safeAmount - x1) / (x2 - x1);
      return Math.ceil(y1 + ratio * (y2 - y1));
    }
  }
  const last = points[points.length - 1];
  const rate = last[1] / last[0];
  return Math.ceil(safeAmount * rate);
}

export function pricePerThousand(amount: number, price: number) {
  if (amount <= 0) return 0;
  return Math.round((price / (amount / 1000)) * 100) / 100;
}
