#!/usr/bin/env bash
set -e
mkdir -p plugins
echo '=== Авто-скачивание всех 22 бесплатных официальных плагинов копии ReallyWorld ==='
echo " -> DecentHolograms-2.10.2.jar..." && curl -fSL "https://github.com/DecentSoftware-eu/DecentHolograms/releases/download/2.10.2/DecentHolograms-2.10.2.jar" -o "plugins/DecentHolograms-2.10.2.jar"
echo " -> FancyNpcs-2.4.4.jar..." && curl -fSL "https://github.com/FancyMcPlugins/FancyNpcs/releases/download/v2.4.4/FancyNpcs-2.4.4.jar" -o "plugins/FancyNpcs-2.4.4.jar"
echo " -> CommandPanels-4.2.5.jar..." && curl -fSL "https://github.com/rockyhawk64/CommandPanels/releases/download/4.2.5/CommandPanels-4.2.5.jar" -o "plugins/CommandPanels-4.2.5.jar"
echo " -> TAB.v6.2.1.jar..." && curl -fSL "https://github.com/NEZNAMY/TAB/releases/download/6.2.1/TAB.v6.2.1.jar" -o "plugins/TAB.v6.2.1.jar"
echo " -> PlaceholderAPI-2.12.3.jar..." && curl -fSL "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar" -o "plugins/PlaceholderAPI-2.12.3.jar"
echo " -> EssentialsX-2.22.0.jar..." && curl -fSL "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsX-2.22.0.jar" -o "plugins/EssentialsX-2.22.0.jar"
echo " -> EssentialsXChat-2.22.0.jar..." && curl -fSL "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsXChat-2.22.0.jar" -o "plugins/EssentialsXChat-2.22.0.jar"
echo " -> EssentialsXSpawn-2.22.0.jar..." && curl -fSL "https://github.com/EssentialsX/Essentials/releases/download/2.22.0/EssentialsXSpawn-2.22.0.jar" -o "plugins/EssentialsXSpawn-2.22.0.jar"
echo " -> Vault.jar..." && curl -fSL "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" -o "plugins/Vault.jar"
echo " -> ProtectionStones-2.10.6.jar..." && curl -fSL "https://github.com/espidev/ProtectionStones/releases/download/2.10.6/ProtectionStones-2.10.6.jar" -o "plugins/ProtectionStones-2.10.6.jar"
echo " -> EternalCombat.v2.5.2.jar..." && curl -fSL "https://github.com/EternalCodeTeam/EternalCombat/releases/download/v2.5.2/EternalCombat.v2.5.2.jar" -o "plugins/EternalCombat.v2.5.2.jar"
echo " -> ConditionalEvents.jar..." && curl -fSL "https://github.com/Ajneb97/ConditionalEvents/releases/download/4.65.1/ConditionalEvents.jar" -o "plugins/ConditionalEvents.jar"
echo " -> CrazyAuctions-1.7.0.jar..." && curl -fSL "https://github.com/Crazy-Crew/CrazyAuctions/releases/download/v1.7.0/CrazyAuctions-1.7.0.jar" -o "plugins/CrazyAuctions-1.7.0.jar"
echo " -> ClansLite-1.4.3.jar..." && curl -fSL "https://github.com/Loving11ish/ClansLite/releases/download/v1.4.3/ClansLite-1.4.3.jar" -o "plugins/ClansLite-1.4.3.jar"
echo " -> CMILib1.5.9.6.jar..." && curl -fSL "https://github.com/Zrips/CMILib/releases/download/1.5.9.6/CMILib1.5.9.6.jar" -o "plugins/CMILib1.5.9.6.jar"
echo " -> Jobs5.2.6.3.jar..." && curl -fSL "https://github.com/Zrips/Jobs/releases/download/v5.2.6.3/Jobs5.2.6.3.jar" -o "plugins/Jobs5.2.6.3.jar"
echo " -> AuthMe-6.0.1-Paper.jar..." && curl -fSL "https://github.com/AuthMe/AuthMeReloaded/releases/download/6.0.1/AuthMe-6.0.1-Paper.jar" -o "plugins/AuthMe-6.0.1-Paper.jar"
echo " -> FastAsyncWorldEdit-Paper-2.16.1.jar..." && curl -fSL "https://github.com/IntellectualSites/FastAsyncWorldEdit/releases/download/2.16.1/FastAsyncWorldEdit-Paper-2.16.1.jar" -o "plugins/FastAsyncWorldEdit-Paper-2.16.1.jar"
echo " -> ViaVersion-5.12.1.jar..." && curl -fSL "https://github.com/ViaVersion/ViaVersion/releases/download/5.12.1/ViaVersion-5.12.1.jar" -o "plugins/ViaVersion-5.12.1.jar"
echo " -> ViaBackwards-5.12.1.jar..." && curl -fSL "https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.1/ViaBackwards-5.12.1.jar" -o "plugins/ViaBackwards-5.12.1.jar"
# Также скачиваем LuckPerms, WorldGuard и PlayerPoints с Modrinth API:
for slug_out in 'luckperms:LuckPerms.jar' 'worldguard:WorldGuard.jar'; do
  slug=${slug_out%%:*}; out=${slug_out##*:}
  u=$(curl -fsSL "https://api.modrinth.com/v2/project/${slug}/version" | python3 -c "import sys,json; vs=json.load(sys.stdin); print(next(f['url'] for v in vs if any(l in [x.lower() for x in v.get('loaders',[])] for l in ('paper','bukkit','spigot')) for f in v['files'] if f['filename'].endswith('.jar')))" 2>/dev/null || true)
  [ -n "$u" ] && echo " -> $out..." && curl -fSL "$u" -o "plugins/$out"
done
echo '=== ГОТОВО! Все бесплатные плагины успешно установлены в папку ./plugins/! ==='
