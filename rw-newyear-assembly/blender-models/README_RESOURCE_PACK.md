# KINGGRIEF resource pack v65

`build_rp_v56.py` builds `../kinggrief-mega-rp-v65.zip`. It never overwrites the
old v63/v64 downloads or the separate ranks-only pack. Dependencies: Python 3,
Pillow and NumPy (`python -m pip install pillow numpy`). Build and verify:

```sh
python build_rp_v56.py
python test_resource_pack.py
```

The ZIP is reproducible with the same Python/Pillow/NumPy versions. If its bytes
change, update SHA-1 in `server/server.properties`, the fresh-install core and
hub configs, `common-src/.../ResourcePackSettings.java`, the installation
instructions, and the copies bundled with the site and full assembly. Never
replace an existing versioned public ZIP: installed servers pin its hash.

`item-model-map.json` maps ItemFactory materials and numeric CustomModelData to
resource-pack models. Tests independently read Java source (including dynamic
armor, mace and scrolls), follow resources, verify legacy overrides and 1.21.4+
item definitions, and compare sphere scales against v64. Sphere textures stay
unchanged while their GUI, hand and ground transforms are smaller. The vanilla
bow, trident, elytra and armor-trim fallbacks in `vanilla-item-fallbacks.json`
are based on [misode/mcmeta](https://github.com/misode/mcmeta).

The core now uses CustomModelData instead of forcing `kinggrief:<id>` as an
explicit item model. Without this pack, new items render using the vanilla base;
with v65 they use the custom models. Existing explicitly modelled items are
migrated only when the model namespace and key match their `kinggrief:custom_id`
PDC and they have CustomModelData; other metadata is preserved. Migration runs
on player join, container opening, and pickup. A packed shulker is migrated when
its contents are opened. Resource packs cannot assign unique custom models to
items for which the plugin sets neither a model identifier nor CustomModelData.

Core and Hub both include `common-src/.../ResourcePackSettings.java` at build
time. It upgrades only known official v63/v64 URL/hash pairs at runtime; custom
URLs and unrecognised hashes are kept. Existing owners should still update
`server.properties` separately and restart both servers. With a JDK, run the
isolated modern/old API migration and URL tests with:

```sh
../core-src/src/test/run-model-tests.sh
```

For deployment and player instructions see `../README_УСТАНОВКА.md`. The full
assembly is for the grief server; the separate hub uses its own KingHub JAR and
configuration. No graphical Minecraft client test is implied by the checks.
