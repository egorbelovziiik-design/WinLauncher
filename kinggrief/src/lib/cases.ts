import { listCaseGroups, listCaseItems, listProducts } from './repo';

/** Кейсы + их товарные наборы, награды и минимальная цена. */
export function mergePackagesForCases(groups = listCaseGroups(true)) {
  const caseProducts = listProducts({ kind: 'case', activeOnly: true });
  const items = listCaseItems();
  return groups.map((group) => {
    const packages = caseProducts
      .filter((p) => p.meta.group === group.id)
      .sort((a, b) => Number(a.meta.quantity) - Number(b.meta.quantity));
    const groupItems = items.filter((i) => i.caseId === group.id).sort((a, b) => b.chance - a.chance);
    return {
      group,
      packages,
      items: groupItems,
      minPrice: packages.length ? Math.min(...packages.map((p) => p.price)) : 0,
      chanceTotal: Math.round(groupItems.reduce((sum, i) => sum + i.chance, 0) * 100) / 100,
    };
  });
}

export function caseById(id: string) {
  return mergePackagesForCases().find((c) => c.group.id === id) ?? null;
}
