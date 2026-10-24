# Привилегии, Ранги и Наборы (/donate, /kits)

Вся иерархия привилегий настроена одновременно в `ranks.yml` (встроенный движок прав `ReallyCore`) и в `server/plugins/LuckPerms/yaml-storage/groups/*.yml`.

## 1. Таблица привилегий и возможностей

| Ранг | Префикс | Вес | Цена (Риллики) | Домов | Слотов `/ah` | Множитель монет PvP | Подарков `/grant` | Ключевые команды (наследуются вверх) |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `PLAYER` | `[ИГРОК]` | 10 | Бесплатно | 2 | 3 | `x1` | 0 | `/menu`, `/spawn`, `/rtp`, `/home`, `/shop`, `/buyer`, `/ah`, `/clan`, `/kit start` |
| `HERO` | `[HERO]` | 20 | `99 ❖` | 4 | 5 | `x1` | 0 | `/craft`, `/recipe`, `/getpos`, `/kit hero` |
| `TITAN` | `[TITAN]` | 30 | `199 ❖` | 6 | 7 | `x2` | 0 | `/hat`, `/feed`, `/rtpfar`, `/itemdb`, `/kit titan` |
| `AVENGER` | `[AVENGER]` | 40 | `349 ❖` | 8 | 9 | `x2` | 0 | `/enderchest`, `/ext`, `/ignore`, `/clear`, `/kit avenger` |
| `RABBIT` | `[BUNNY]` | 45 | `449 ❖` | 10 | 12 | `x2` | 0 | Сезонная привилегия + `/kit rabbit` |
| `OVERLORD` | `[OVERLORD]` | 50 | `549 ❖` | 12 | 14 | `x2` | 0 | `/prefix`, `/repair`, `/near`, `/ptime`, `/kit overlord` |
| `MAGISTER` | `[MAGISTER]` | 60 | `799 ❖` | 16 | 18 | `x4` | 0 | `/nick`, `/invsee` *(только чтение!)*, `/repair all`, `/heal`, `/tptoggle`, `/kit magister` |
| `IMPERATOR` | `[IMPERATOR]` | 70 | `1 199 ❖` | 22 | 22 | `x4` | 0 | `/kit plus`, `/broadcast`, `/realname`, `/lightning` *(визуальная молния без урона)*, `/kit imperator` |
| `DRAGON` | `[DRAGON]` | 80 | `1 799 ❖` | 32 | 36 | `x4` | 2 | `/fly` *(отключается в PvP)*, `/grant` *(только HERO/TITAN)*, `/kit pluspro`, `/day`, `/night`, `/sun`, `/rain` |
| `COBRA` | `[COBRA]` | 85 | `2 199 ❖` | 40 | 45 | `x4` | 3 | Эксклюзивная привилегия + `/kit cobra` |
| `GOD` | `[GOD]` | 90 | `2 999 ❖` | 64 | 64 | `x6` | 5 | Высший игровой статус, `x6` монет за киллы, `/kit god` |
| `HELPER` | `[HELPER]` | 95 | Персонал | 64 | 64 | `x4` | 0 | Модерация чата, проверка игроков (`reallycore.staff`) |
| `ADMIN` | `[ADMIN]` | 100 | Владелец | 999 | 999 | `x6` | 99 | Полное управление сервером (`reallycore.admin`) |

---

## 2. Гарантии безопасности донат-прав
1. **Запрет опасных прав:** ни один покупаемый донат-ранг **не содержит** `op`, `*`, `reallycore.admin`, `worldedit.*`, `worldguard.*`, `luckperms.*`, `/gamemode`, `/give` или `/ban`.
2. **Аутентичное наследование китов:** как и на оригинальном ReallyWorld, старшие привилегии наследуют общие бонусные наборы `/kit plus` и `/kit pluspro`, но **не получают одновременно все младшие именные киты** (`/kit hero` + `/kit titan` + ...), что исключает засорение экономики.
3. **Умная доплата в `/donate`:** если у игрока уже куплен `TITAN` (`199 ❖`), покупка `OVERLORD` (`549 ❖`) автоматически спишет только разницу (`350 ❖`)!
