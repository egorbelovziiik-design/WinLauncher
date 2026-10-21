import overworld from '../assets/overworld.webp';
import nether from '../assets/nether.webp';
import end from '../assets/the-end.webp';
import deepDark from '../assets/deep-dark.webp';
import cherry from '../assets/cherry-grove.webp';
export function worldImage(id: string): string { return ({ nether, 'the-end': end, 'deep-dark': deepDark, 'cherry-grove': cherry } as Record<string, string>)[id] ?? overworld; }
export interface Theme { id: string; name: string; subtitle: string; accent: string; background: string; sidebar: string; card: string; glow: string; hue: string; biome: string; tag: string }
export const themes: Theme[] = [
  { id: 'overworld', name: 'Overworld', subtitle: 'Там, где всё начинается', accent: '#55ff88', background: '#0d1117', sidebar: '#151b23', card: '#1c2530', glow: '#55ff882a', hue: 'none', biome: 'Леса, горы и новые истории', tag: 'МИР ВОЗМОЖНОСТЕЙ' },
  { id: 'nether', name: 'Nether', subtitle: 'По ту сторону портала', accent: '#ff876a', background: '#180e10', sidebar: '#211317', card: '#2b191d', glow: '#ff5a2840', hue: 'none', biome: 'Огонь, базальт и приключения', tag: 'НЕИЗВЕДАННЫЕ ГЛУБИНЫ' },
  { id: 'the-end', name: 'The End', subtitle: 'За пределами привычного', accent: '#bc9aff', background: '#100e1b', sidebar: '#191526', card: '#211c30', glow: '#ae5aff40', hue: 'none', biome: 'Тишина между мирами', tag: 'БЕСКОНЕЧНОСТЬ БЛИЖЕ' },
  { id: 'diamond', name: 'Diamond', subtitle: 'Твоя самая ценная находка', accent: '#6cdef2', background: '#0b141a', sidebar: '#101e27', card: '#152a34', glow: '#38bdf840', hue: 'hue-rotate(55deg) saturate(.7)', biome: 'Холодный свет кристаллов', tag: 'НАЙДИ СВОЁ СОКРОВИЩЕ' },
  { id: 'cherry-grove', name: 'Cherry Grove', subtitle: 'Остановись на мгновение', accent: '#f6b4d1', background: '#1a1118', sidebar: '#241922', card: '#2c202b', glow: '#f599c140', hue: 'none', biome: 'Сады под розовым небом', tag: 'КРАСОТА В КАЖДОМ БЛОКЕ' },
  { id: 'deep-dark', name: 'Deep Dark', subtitle: 'Слушай тишину', accent: '#65c9c1', background: '#070e14', sidebar: '#0b1720', card: '#10222b', glow: '#2199ac40', hue: 'none', biome: 'Таинственное свечение скалка', tag: 'ГЛУБЖЕ, ЧЕМ КАЖЕТСЯ' },
  { id: 'obsidian', name: 'Obsidian', subtitle: 'Ничего лишнего', accent: '#c7c6e5', background: '#0d0d12', sidebar: '#15151c', card: '#1d1d27', glow: '#8783bb40', hue: 'grayscale(.85) brightness(.65)', biome: 'Спокойствие тёмной стороны', tag: 'СОСРЕДОТОЧЬСЯ НА ГЛАВНОМ' },
  { id: 'classic', name: 'Classic Minecraft', subtitle: 'Помнишь свой первый блок?', accent: '#89cd53', background: '#111610', sidebar: '#1a2117', card: '#242c1e', glow: '#78b53d40', hue: 'saturate(1.3) sepia(.1)', biome: 'Большие идеи с первого блока', tag: 'ВОЗВРАЩЕНИЕ К ИСТОКАМ' },
  { id: 'snow-biome', name: 'Snow Biome', subtitle: 'Тихая зимняя история', accent: '#c3e7f2', background: '#10171f', sidebar: '#18232f', card: '#202f3d', glow: '#9bd9f040', hue: 'saturate(.25) brightness(1.15) hue-rotate(50deg)', biome: 'Снег, сосны и уют', tag: 'ВРЕМЯ НОВЫХ ОТКРЫТИЙ' },
  { id: 'ocean-monument', name: 'Ocean Monument', subtitle: 'Погрузись в приключение', accent: '#58dfc5', background: '#091917', sidebar: '#102523', card: '#16332f', glow: '#29b79c40', hue: 'hue-rotate(45deg) saturate(1.1) brightness(.8)', biome: 'Секреты подводного мира', tag: 'ЗА ГОРИЗОНТОМ ЕСТЬ БОЛЬШЕ' },
];
export function getTheme(id: string): Theme { return themes.find(t => t.id === id) ?? themes[0]; }
