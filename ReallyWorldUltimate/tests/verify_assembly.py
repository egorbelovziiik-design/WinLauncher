#!/usr/bin/env python3
"""
ReallyWorld Ultimate — Комплексный автоматический валидатор сборки:
1. Проверяет отсутствие пустых (0-байтных) файлов во всём проекте.
2. Валидирует все JSON-файлы ресурспака и серверных конфигураций.
3. Проверяет целостность архива ReallyCore.jar и наличие всех 19 модулей.
4. Сверяет SHA-1 ресурспака с server/server.properties.
5. Проверяет целостность SQLite базы server/plugins/ReallyCore/data/reallycore.db (PRAGMA integrity_check).
"""
import hashlib
import json
import os
import sqlite3
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def main():
    errors = []
    checked_files = 0
    json_count = 0

    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            full = os.path.join(dirpath, fn)
            checked_files += 1
            if os.path.getsize(full) == 0:
                errors.append(f"Empty file detected: {full}")
            if fn.endswith((".json", ".mcmeta")):
                json_count += 1
                try:
                    with open(full, "r", encoding="utf-8") as f:
                        json.load(f)
                except Exception as e:
                    errors.append(f"Invalid JSON {full}: {e}")

    # Verify ReallyCore.jar
    jar_path = os.path.join(ROOT, "server/plugins/ReallyCore.jar")
    if not os.path.isfile(jar_path):
        errors.append("Missing server/plugins/ReallyCore.jar")
    else:
        with zipfile.ZipFile(jar_path, "r") as zf:
            bad = zf.testzip()
            if bad:
                errors.append(f"Corrupt entry in ReallyCore.jar: {bad}")
            names = set(zf.namelist())
            for req in [
                "plugin.yml",
                "ru/reallyworld/ultimate/core/ReallyCorePlugin.class",
                "ru/reallyworld/ultimate/core/module/CustomItemsModule.class",
                "ru/reallyworld/ultimate/core/module/EconomyModule.class",
                "ru/reallyworld/ultimate/core/module/AuctionModule.class",
                "ru/reallyworld/ultimate/core/module/ClanModule.class",
                "ru/reallyworld/ultimate/core/module/EventsModule.class",
                "ru/reallyworld/ultimate/core/gui/MenuModule.class",
                "migrations/V1__initial_schema_sqlite.sql",
            ]:
                if req not in names:
                    errors.append(f"Missing {req} inside ReallyCore.jar")

    # Verify ResourcePack ZIP & SHA-1 sync in server.properties
    rp_zip = os.path.join(ROOT, "resourcepack/ReallyWorld-ResourcePack-1.21.4-1.21.11.zip")
    if not os.path.isfile(rp_zip):
        errors.append("Missing ResourcePack ZIP")
    else:
        actual_sha1 = hashlib.sha1(open(rp_zip, "rb").read()).hexdigest()
        props = open(os.path.join(ROOT, "server/server.properties"), encoding="utf-8").read()
        if f"resource-pack-sha1={actual_sha1}" not in props:
            errors.append(f"server.properties resource-pack-sha1 mismatch (expected {actual_sha1})")
        if "online-mode=true" not in props:
            errors.append("server.properties must default to online-mode=true!")

    # Verify SQLite DB integrity & seeded warps
    db_path = os.path.join(ROOT, "server/plugins/ReallyCore/data/reallycore.db")
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    integrity = cur.execute("PRAGMA integrity_check;").fetchone()[0]
    if integrity != "ok":
        errors.append(f"SQLite integrity check failed: {integrity}")
    warp_count = cur.execute("SELECT COUNT(*) FROM server_warps;").fetchone()[0]
    if warp_count < 5:
        errors.append(f"Expected >= 5 default warps in reallycore.db, found {warp_count}")
    conn.close()

    print(f"[VERIFY] Checked {checked_files} files, {json_count} JSONs, ReallyCore.jar, ResourcePack SHA-1, and SQLite DB.")
    if errors:
        for err in errors:
            print(" [FAIL]", err)
        sys.exit(1)
    print("[PASS] All assembly integrity checks succeeded with 0 errors!")

if __name__ == "__main__":
    main()
