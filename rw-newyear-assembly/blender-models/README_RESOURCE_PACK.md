# KINGGRIEF resource pack v6.4

`build_rp_v56.py` builds `../kinggrief-mega-rp-v64.zip` without changing the
legacy v63 download or the separate ranks-only pack. Dependencies: Python 3,
Pillow and NumPy (`python -m pip install pillow numpy`). Build and verify:

```sh
python build_rp_v56.py
python test_resource_pack.py
```

The ZIP is reproducible with the same Python/Pillow/NumPy versions. If the ZIP
changes, update its SHA-1 in `server/server.properties`, the fresh-install
`server/plugins/KingGriefCore/config.yml`, the installation instructions, and
the copies bundled with the site and full assembly. Existing servers must merge
only the pack URL and SHA-1 into their own configs; an old core plugin also
normalizes the previous URL to v63, so **use the new versioned URL**, not the
old filename with a different query. KingHub has its own config on hub servers.

`item-model-map.json` is the mapping from ItemFactory materials and numeric
CustomModelData to resource-pack model IDs. The test independently reads the
Java source (including the dynamic armor, mace and scroll paths), checks every
mapping and verifies both legacy overrides and 1.21.4+ item definitions.
`vanilla-item-fallbacks.json` is based on Minecraft 1.21.3/1.21.4 assets from
[misode/mcmeta](https://github.com/misode/mcmeta) and preserves vanilla bow,
trident, elytra and armor-trim fallbacks. Resource packs cannot assign a custom
model to an item for which the plugin sets neither `item_model` nor
`CustomModelData`; those items keep their normal vanilla appearance.

The old public `/kinggrief-mega-rp.zip?v=63` must not be replaced: deployed
older servers still send the SHA-1 of those exact bytes. The new download is
`/kinggrief-mega-rp-v64.zip`.
