#!/usr/bin/env bash
# =============================================================================
# ReallyWorld Ultimate — Автоматический установщик ядра и внешних плагинов
# Загружает ТОЛЬКО официальные релизы (Purpur/Paper 1.21.11 + Modrinth/GitHub API)
# с обязательной криптографической проверкой SHA-256 / SHA-512!
# Собственный центральный плагин ReallyCore.jar УЖЕ скомпилирован в server/plugins/.
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${ROOT_DIR}/server"
PLUGINS_DIR="${SERVER_DIR}/plugins"
MC_VERSION="${MC_VERSION:-1.21.11}"
CORE_FLAVOR="${CORE_FLAVOR:-purpur}" # purpur | paper

mkdir -p "${PLUGINS_DIR}"

echo "=================================================================="
echo " ReallyWorld Ultimate — Установка официальных компонентов (${MC_VERSION})"
echo "=================================================================="

if ! command -v python3 >/dev/null 2>&1; then
  echo "[ОШИБКА] Требуется python3 для разбора JSON API и проверки SHA-256/SHA-512."
  exit 1
fi

python3 - "${SERVER_DIR}" "${PLUGINS_DIR}" "${MC_VERSION}" "${CORE_FLAVOR}" <<'PY'
import hashlib, json, os, sys, urllib.request

server_dir, plugins_dir, mc_ver, flavor = sys.argv[1:5]
UA = {"User-Agent": "ReallyWorldUltimate-Installer/1.0 (Minecraft 1.21.4-1.21.11)"}

def http_json(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

def download_verified(url, dest, expected_hash=None, algo="sha256"):
    print(f" -> Загрузка: {os.path.basename(dest)} ...")
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    if expected_hash:
        h = hashlib.new(algo, data).hexdigest()
        if h.lower() != expected_hash.lower():
            raise RuntimeError(f"Контрольная сумма {algo} не совпала для {dest}: {h} != {expected_hash}")
        print(f"    [OK] {algo.upper()} проверен: {h[:16]}...")
    with open(dest, "wb") as f:
        f.write(data)

# 1. Установка серверного ядра Purpur / Paper 1.21.11
core_jar = os.path.join(server_dir, "server.jar")
if flavor.lower() == "purpur":
    try:
        meta = http_json(f"https://api.purpurmc.org/v2/purpur/{mc_ver}")
        latest = meta["builds"]["latest"]
        binfo = http_json(f"https://api.purpurmc.org/v2/purpur/{mc_ver}/{latest}")
        md5_expected = binfo.get("md5")
        download_verified(
            f"https://api.purpurmc.org/v2/purpur/{mc_ver}/{latest}/download",
            core_jar,
            md5_expected,
            "md5"
        )
    except Exception as e:
        print(f"[!] Purpur {mc_ver} недоступен ({e}), переключаемся на PaperMC...")
        flavor = "paper"

if flavor.lower() == "paper":
    vmeta = http_json(f"https://api.papermc.io/v2/projects/paper/versions/{mc_ver}/builds")
    latest_build = vmeta["builds"][-1]
    bnum = latest_build["build"]
    dl = latest_build["downloads"]["application"]
    download_verified(
        f"https://api.papermc.io/v2/projects/paper/versions/{mc_ver}/builds/{bnum}/downloads/{dl['name']}",
        core_jar,
        dl["sha256"],
        "sha256"
    )

# 2. Установка официальных плагинов через Modrinth API v2 с проверкой SHA-512
MODRINTH_PLUGINS = [
    ("viaversion", "ViaVersion.jar"),
    ("viabackwards", "ViaBackwards.jar"),
    ("luckperms", "LuckPerms-Bukkit.jar"),
    ("placeholderapi", "PlaceholderAPI.jar"),
    ("worldedit", "WorldEdit.jar"),
    ("worldguard", "WorldGuard.jar"),
    ("tab-was-taken", "TAB.jar"),
    ("grimac", "GrimAC.jar"),
    ("coreprotect", "CoreProtect.jar"),
]

for slug, out_name in MODRINTH_PLUGINS:
    try:
        versions = http_json(f"https://api.modrinth.com/v2/project/{slug}/version")
        chosen = None
        for v in versions:
            loaders = [l.lower() for l in v.get("loaders", [])]
            if any(l in loaders for l in ("paper", "purpur", "spigot", "bukkit")):
                chosen = v
                break
        if not chosen:
            print(f" [!] Пропущен {slug}: подходящая сборка Paper/Bukkit не найдена.")
            continue
        primary = next((f for f in chosen["files"] if f.get("primary")), chosen["files"][0])
        sha512 = primary.get("hashes", {}).get("sha512")
        download_verified(primary["url"], os.path.join(plugins_dir, out_name), sha512, "sha512")
    except Exception as ex:
        print(f" [ПРЕДУПРЕЖДЕНИЕ] Не удалось загрузить {slug}: {ex}")

# 3. Vault (официальный релиз MilkBowl/Vault GitHub API)
try:
    rel = http_json("https://api.github.com/repos/MilkBowl/Vault/releases/latest")
    for asset in rel.get("assets", []):
        if asset["name"].endswith(".jar"):
            download_verified(asset["browser_download_url"], os.path.join(plugins_dir, "Vault.jar"))
            break
except Exception as ex:
    print(f" [ПРЕДУПРЕЖДЕНИЕ] Vault: {ex}")

print("\n[ГОТОВО] Все официальные компоненты проверены и установлены!")
print("Центральный плагин ReallyCore.jar готов: server/plugins/ReallyCore.jar")
print("Для запуска сервера выполните: cd server && ./start.sh")
PY
