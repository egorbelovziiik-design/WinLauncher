#!/usr/bin/env bash
set -e
mkdir -p plugins
echo '=== KINGGRIEF v5.7: Установка совместимых вспомогательных плагинов (без конфликтов с ядром KingGriefCore!) ==='
echo 'Примечание: Авторизация, 39 Рангов + Люкс-префиксы, TAB, Scoreboard, 27 НПС (строго по 1 шт.), Кланы (/clan), Аукцион, Талисманы, Броня, Кастом ТНТ, Ивенты и Боссы УЖЕ встроены в KingGriefCore.jar!'
echo " -> PlaceholderAPI-2.12.3.jar..." && curl -fSL "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar" -o "plugins/PlaceholderAPI-2.12.3.jar"
echo " -> Vault.jar..." && curl -fSL "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" -o "plugins/Vault.jar"
echo " -> FastAsyncWorldEdit-Paper-2.16.1.jar..." && curl -fSL "https://github.com/IntellectualSites/FastAsyncWorldEdit/releases/download/2.16.1/FastAsyncWorldEdit-Paper-2.16.1.jar" -o "plugins/FastAsyncWorldEdit-Paper-2.16.1.jar"
echo " -> ViaVersion-5.12.1.jar..." && curl -fSL "https://github.com/ViaVersion/ViaVersion/releases/download/5.12.1/ViaVersion-5.12.1.jar" -o "plugins/ViaVersion-5.12.1.jar"
echo " -> ViaBackwards-5.12.1.jar..." && curl -fSL "https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.1/ViaBackwards-5.12.1.jar" -o "plugins/ViaBackwards-5.12.1.jar"
u=$(curl -fsSL "https://api.modrinth.com/v2/project/luckperms/version" | python3 -c "import sys,json; vs=json.load(sys.stdin); print(next(f['url'] for v in vs if any(l in [x.lower() for x in v.get('loaders',[])] for l in ('paper','bukkit','spigot')) for f in v['files'] if f['filename'].endswith('.jar')))" 2>/dev/null || true)
[ -n "$u" ] && echo " -> LuckPerms.jar..." && curl -fSL "$u" -o "plugins/LuckPerms.jar"
echo '=== ГОТОВО! Запускайте сервер — все системы KINGGRIEF v5.7 работают из коробки! ==='
