#!/usr/bin/env python3
"""Validate the generated pack against KingGriefCore without a running server.

Run after build_rp_v56.py: python3 test_resource_pack.py
Pillow is required only for the pixel-visibility test.
"""
from __future__ import annotations

import ast
import json
import re
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACK = HERE.parent / "kinggrief-mega-rp-v65.zip"
V64_PACK = HERE.parent / "kinggrief-mega-rp-v64.zip"
JAVA_DIR = HERE.parent / "core-src/src/main/java/net/kinggrief/core"
MAPPING = json.loads((HERE / "item-model-map.json").read_text(encoding="utf-8"))
FALLBACKS = json.loads((HERE / "vanilla-item-fallbacks.json").read_text(encoding="utf-8"))


def split_java_args(text: str, start: int) -> list[str]:
    """Split an invocation without getting confused by nested calls/strings."""
    depth, square, braces, pos, begin = 1, 0, 0, start, start
    quote = None
    args = []
    while pos < len(text):
        c = text[pos]
        if quote:
            if c == "\\":
                pos += 2
                continue
            if c == quote:
                quote = None
            pos += 1
            continue
        if c in ('"', "'"):
            quote = c
        elif text[pos:pos + 2] == "//":
            end = text.find("\n", pos)
            pos = len(text) if end < 0 else end
            continue
        elif text[pos:pos + 2] == "/*":
            end = text.find("*/", pos + 2)
            if end < 0:
                raise ValueError("Unterminated comment")
            pos = end + 2
            continue
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                args.append(text[begin:pos].strip())
                return args
        elif c == "[":
            square += 1
        elif c == "]":
            square -= 1
        elif c == "{":
            braces += 1
        elif c == "}":
            braces -= 1
        elif c == "," and depth == 1 and square == 0 and braces == 0:
            args.append(text[begin:pos].strip())
            begin = pos + 1
        pos += 1
    raise ValueError("Unterminated invocation")


def calls(text: str, name: str):
    for m in re.finditer(r"(?<![\w])" + re.escape(name) + r"\s*\(", text):
        yield split_java_args(text, m.end())


def string_id(value: str):
    m = re.fullmatch(r'"([a-z0-9_]+)"', value.strip())
    return m.group(1) if m else None


def decimal(value: str):
    return int(value) if re.fullmatch(r"\d+", value.strip()) else None


def declared_models() -> set[tuple[str, int, str]]:
    text = (JAVA_DIR / "ItemFactory.java").read_text(encoding="utf-8")
    found = set()
    for args in calls(text, "make"):
        if len(args) != 8:
            continue
        model, cmd = string_id(args[6]), decimal(args[7])
        if model is None or cmd is None or cmd == 0:
            continue
        mat = re.fullmatch(r"Material\.([A-Z_]+)", args[0])
        if mat:
            found.add((mat.group(1), cmd, model))
        elif args[0] == "maceMat":
            assert 'ColorUtil.mat("MACE", "NETHERITE_AXE")' in text
            found.update({("MACE", cmd, model), ("NETHERITE_AXE", cmd, model)})
        else:
            raise AssertionError(f"Add dynamic material to model-map extraction: {args[0]}")

    for args in calls(text, "enchantedPick"):
        if len(args) != 9:
            continue
        mat = re.fullmatch(r"Material\.([A-Z_]+)", args[0])
        model, cmd = string_id(args[4]), decimal(args[5])
        if mat and model and cmd is not None:
            found.add((mat.group(1), cmd, model))

    for method, material, model_arg, cmd_arg, argc in (
        ("enchantedCustomSword", "NETHERITE_SWORD", 2, 3, 8),
        ("makeBuffedElytra", "ELYTRA", 1, 2, 6),
        ("makeCustomBow", "BOW", 1, 2, 6),
    ):
        for args in calls(text, method):
            if len(args) == argc:
                model, cmd = string_id(args[model_arg]), decimal(args[cmd_arg])
                if model and cmd is not None:
                    found.add((material, cmd, model))

    # customArmorPiece chooses a base CMD by set, then adds the slot index.
    armor = text[text.index("public static ItemStack customArmorPiece("):]
    armor = armor[:armor.index("String unbRoman =")]
    sets = {}
    pattern = r'(?:if|else if) \("([a-z]+)"\.equals\(setKey\)\) \{(.*?)(?=\n        \} else)'
    for name, body in re.findall(pattern, armor, re.S):
        match = re.search(r"baseCmd\s*=\s*(\d+)", body)
        if match:
            sets[name] = int(match.group(1))
    match = re.search(r"\} else \{(.*?)baseCmd\s*=\s*(\d+)", armor, re.S)
    assert match is not None
    sets["phantom"] = int(match.group(2))
    assert set(sets) == {"krush", "dracula", "frost", "zeus", "phantom", "owner"}, sets
    assert '"armor_" + setKey + "_" + slot' in text and "baseCmd + slotIdx" in text
    for set_name, base in sets.items():
        for index, slot in enumerate(("helmet", "chestplate", "leggings", "boots")):
            found.add((f"NETHERITE_{slot.upper()}", base + index, f"armor_{set_name}_{slot}"))

    # enchantScroll uses 1400 + the index in SCROLL_IDS.
    ids = re.search(r"SCROLL_IDS\s*=\s*new String\[\]\s*\{([^}]+)\}", text)
    assert ids and '"scroll_" + id, "scroll_" + id, 1400 + idx' in text
    for index, scroll in enumerate(re.findall(r'"([a-z_]+)"', ids.group(1))):
        found.add(("PAPER", 1400 + index, f"scroll_{scroll}"))

    # Other classes currently create menu icons without model IDs. Flag future
    # explicit models here so they cannot silently go missing from the pack.
    for path in JAVA_DIR.glob("*.java"):
        if path.name == "ItemFactory.java":
            continue
        for args in calls(path.read_text(encoding="utf-8"), "ItemFactory.make"):
            if len(args) == 8 and (string_id(args[6]) or (decimal(args[7]) or 0) > 0):
                raise AssertionError(f"Add explicit model from {path.name} to the manifest test")
    return found


def select_dispatch(model: dict, value: int) -> dict:
    selected = model["fallback"]
    for item in model["entries"]:
        if value >= item["threshold"]:
            selected = item["model"]
    return selected


def referenced_kinggrief_models(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ("model", "parent") and isinstance(child, str) and child.startswith("kinggrief:item/"):
                yield child.split("/", 1)[1]
            else:
                yield from referenced_kinggrief_models(child)
    elif isinstance(value, list):
        for child in value:
            yield from referenced_kinggrief_models(child)


class ResourcePackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.z = zipfile.ZipFile(PACK)
        cls.names = set(cls.z.namelist())

    @classmethod
    def tearDownClass(cls):
        cls.z.close()

    def load(self, name):
        return json.loads(self.z.read(name))

    def test_zip_and_metadata(self):
        self.assertIsNone(self.z.testzip())
        self.assertEqual(len(self.names), len(self.z.namelist()), "duplicate ZIP paths")
        self.assertIn("pack.mcmeta", self.names)
        self.assertIn("pack.png", self.names)
        pack = self.load("pack.mcmeta")
        self.assertEqual(pack["pack"]["pack_format"], 6)
        self.assertEqual(pack["pack"]["supported_formats"], [6, 75])
        self.assertEqual((pack["pack"]["min_format"], pack["pack"]["max_format"]), (6, 75))
        self.assertEqual(pack["overlays"]["entries"][0]["formats"], [18, 42])
        for name in self.names:
            if name.endswith((".json", ".mcmeta")):
                with self.subTest(path=name):
                    self.load(name)

    def test_mapping_matches_core_itemfactory(self):
        mapped = {(material, int(cmd), item) for material, entries in MAPPING.items() for cmd, item in entries}
        actual = declared_models()
        self.assertEqual(actual - mapped, set(), "A plugin item is missing from the pack manifest")
        self.assertEqual(mapped - actual, set(), "A manifest entry no longer corresponds to the plugin")
        self.assertGreater(len(mapped), 100)

    def test_all_item_definitions_models_textures_and_animations_exist(self):
        expected = {item for rows in MAPPING.values() for _, item in rows}
        for item in expected:
            with self.subTest(item=item):
                for path in (
                    f"assets/kinggrief/items/{item}.json",
                    f"assets/kinggrief/models/item/{item}.json",
                    f"assets/kinggrief/textures/item/{item}.png",
                    f"assets/kinggrief/textures/item/{item}.png.mcmeta",
                ):
                    self.assertIn(path, self.names)
                model = self.load(f"assets/kinggrief/models/item/{item}.json")
                self.assertEqual(model["textures"]["layer0"], f"kinggrief:item/{item}")

        # Follow every KINGGRIEF model/texture reference, including aliases and
        # bow pull stages, rather than only checking the main item list.
        for path in self.names:
            if not path.endswith(".json") or not (path.startswith("assets/kinggrief/") or path.startswith("assets/minecraft/items/")):
                continue
            data = self.load(path)
            for model_id in referenced_kinggrief_models(data):
                self.assertIn(f"assets/kinggrief/models/item/{model_id}.json", self.names, path)
            if path.startswith("assets/kinggrief/models/item/"):
                for texture in data.get("textures", {}).values():
                    if texture.startswith("kinggrief:item/"):
                        self.assertIn("assets/kinggrief/textures/item/" + texture.split("/", 1)[1] + ".png", self.names, path)

    def test_legacy_and_modern_dispatch(self):
        for material, rows in MAPPING.items():
            mat = material.lower()
            old = self.load(f"assets/minecraft/models/item/{mat}.json")
            new = self.load(f"assets/minecraft/items/{mat}.json")["model"]
            self.assertEqual(new["type"], "minecraft:range_dispatch")
            self.assertEqual(new["property"], "minecraft:custom_model_data")
            fallback = FALLBACKS["modern"].get(mat, {"type": "minecraft:model", "model": f"minecraft:item/{mat}"})
            self.assertEqual(new["fallback"], fallback)
            values = [x["threshold"] for x in new["entries"]]
            self.assertEqual(values, sorted(set(values)))
            occupied = {cmd for cmd, _ in rows}
            for cmd, item in rows:
                with self.subTest(material=mat, item=item):
                    old_match = [x for x in old["overrides"] if x["predicate"] == {"custom_model_data": cmd}]
                    self.assertEqual(len(old_match), 1)
                    self.assertEqual(old_match[0]["model"], f"kinggrief:item/{item}")
                    direct = self.load(f"assets/kinggrief/items/{item}.json")["model"]
                    self.assertEqual(select_dispatch(new, cmd), direct)
                    if cmd + 1 not in occupied:
                        self.assertEqual(select_dispatch(new, cmd + 1), fallback)
        self.assertEqual(self.load("assets/minecraft/models/item/tnt.json")["parent"], "minecraft:block/tnt")
        self.assertTrue(any("broken" in x["predicate"] for x in self.load("assets/minecraft/models/item/elytra.json")["overrides"]))
        for slot in ("helmet", "chestplate", "leggings", "boots"):
            p = f"legacy_trim/assets/minecraft/models/item/netherite_{slot}.json"
            self.assertTrue(any("trim_type" in x["predicate"] for x in self.load(p)["overrides"]))

    def test_bow_drawing_states(self):
        legacy = self.load("assets/minecraft/models/item/bow.json")["overrides"]
        for cmd, item in MAPPING["BOW"]:
            direct = self.load(f"assets/kinggrief/items/{item}.json")["model"]
            self.assertEqual(direct["property"], "minecraft:using_item")
            self.assertEqual(direct["on_true"]["property"], "minecraft:use_duration")
            for stage, pull in enumerate((0, 0.65, 0.9)):
                predicate = {"custom_model_data": cmd, "pulling": 1}
                if stage:
                    predicate["pull"] = pull
                self.assertTrue(any(x["predicate"] == predicate and x["model"] == f"kinggrief:item/{item}_pulling_{stage}" for x in legacy))
                self.assertIn(f"assets/kinggrief/textures/item/{item}_pulling_{stage}.png", self.names)

    def test_spheres_are_smaller_without_changing_other_items(self):
        source = ast.parse((HERE / "build_rp_v56.py").read_text(encoding="utf-8"))
        assignments = [node for node in source.body if isinstance(node, ast.Assign)
                       and any(isinstance(target, ast.Name) and target.id == "ITEMS_SPECS" for target in node.targets)]
        specs = ast.literal_eval(assignments[0].value)
        spheres = [name for primary, aliases, category, *_ in specs if category == "sphere" for name in [primary, *aliases]]
        self.assertGreaterEqual(len(spheres), 12)
        with zipfile.ZipFile(V64_PACK) as old:
            for item in spheres:
                with self.subTest(sphere=item):
                    path = f"assets/kinggrief/models/item/{item}.json"
                    new_model = self.load(path)
                    old_model = json.loads(old.read(path))
                    for context in ("gui", "firstperson_righthand", "firstperson_lefthand",
                                    "thirdperson_righthand", "thirdperson_lefthand", "ground", "fixed"):
                        self.assertLess(new_model["display"][context]["scale"][0],
                                        old_model["display"][context]["scale"][0])
                    texture = f"assets/kinggrief/textures/item/{item}.png"
                    self.assertEqual(self.z.read(texture), old.read(texture), "sphere artwork changed")
            # The dedicated sphere branch must not shrink the new Santa totem.
            other = "assets/kinggrief/models/item/totem_santy.json"
            self.assertEqual(self.z.read(other), old.read(other))

    def test_font_provider_files_exist(self):
        font = self.load("assets/minecraft/font/default.json")
        for provider in font["providers"]:
            if provider.get("type") == "bitmap" and provider["file"].startswith("minecraft:"):
                texture = "assets/minecraft/textures/" + provider["file"].split(":", 1)[1]
                self.assertIn(texture, self.names)

    def test_item_textures_have_visible_pixels(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Install Pillow for the pixel-visibility test")
        import io
        for name in self.names:
            if name.startswith("assets/kinggrief/textures/item/") and name.endswith(".png"):
                with self.subTest(texture=name):
                    im = Image.open(io.BytesIO(self.z.read(name))).convert("RGBA")
                    self.assertEqual(im.width, 32)
                    self.assertEqual(im.height, 32 * 12)
                    for frame in range(12):
                        alpha = im.crop((0, frame * 32, 32, (frame + 1) * 32)).getchannel("A")
                        self.assertIsNotNone(alpha.getbbox(), f"empty animation frame {frame}")
                        self.assertEqual(alpha.getextrema(), (0, 255), f"invalid transparency in frame {frame}")


class DistributionTests(unittest.TestCase):
    def test_hashes_and_fresh_install_settings(self):
        import hashlib
        repo = HERE.parent.parent
        public = repo / "kinggrief/public"
        pack_bytes = PACK.read_bytes()
        checksum = hashlib.sha1(pack_bytes).hexdigest()
        self.assertEqual((public / PACK.name).read_bytes(), pack_bytes)
        self.assertEqual((HERE.parent / "server/resourcepack" / PACK.name).read_bytes(), pack_bytes)
        # Never change the bytes that existing deployments still request with
        # the old pinned checksum.
        self.assertEqual(hashlib.sha1((public / "kinggrief-mega-rp.zip").read_bytes()).hexdigest(),
                         "3637015af079e6fea621c10fe6dfa51e35cc0113")
        self.assertEqual(hashlib.sha1((public / V64_PACK.name).read_bytes()).hexdigest(),
                         "21b2343076c751bf392ac3bd47528c59988fb92c")
        self.assertEqual((public / V64_PACK.name).read_bytes(), V64_PACK.read_bytes())
        props = (HERE.parent / "server/server.properties").read_text(encoding="utf-8")
        config = (HERE.parent / "server/plugins/KingGriefCore/config.yml").read_text(encoding="utf-8")
        core_default = (HERE.parent / "core-src/src/main/resources/config.yml").read_text(encoding="utf-8")
        hub_default = (HERE.parent / "kinghub-src/src/main/resources/config.yml").read_text(encoding="utf-8")
        shared = (HERE.parent / "common-src/src/main/java/net/kinggrief/common/ResourcePackSettings.java").read_text(encoding="utf-8")
        self.assertIn(f"resource-pack-sha1={checksum}", props)
        for text in (config, core_default, hub_default):
            self.assertIn(f'resourcepack-sha1: "{checksum}"', text)
            self.assertIn(PACK.name, text)
        self.assertIn(PACK.name, props)
        self.assertIn(checksum, shared)
        self.assertIn(PACK.name, shared)

        core_jar = (public / "KingGriefCore.jar").read_bytes()
        for file in (HERE.parent / "KingGriefCore.jar", HERE.parent / "plugins/KingGriefCore.jar",
                     HERE.parent / "server/plugins/KingGriefCore.jar"):
            self.assertEqual(file.read_bytes(), core_jar)
        self.assertEqual((public / "KingHub.jar").read_bytes(), (HERE.parent / "KingHub.jar").read_bytes())
        for name, version, default in (("KingGriefCore.jar", "6.4.1", core_default),
                                       ("KingHub.jar", "1.2.1", hub_default)):
            with zipfile.ZipFile(public / name) as jar:
                self.assertIsNone(jar.testzip())
                self.assertIn(f"version: {version}", jar.read("plugin.yml").decode("utf-8"))
                self.assertEqual(jar.read("config.yml").decode("utf-8"), default)
                self.assertIn("net/kinggrief/common/ResourcePackSettings.class", jar.namelist())
                self.assertFalse(any(path.startswith("org/bukkit/") for path in jar.namelist()), "test fixtures leaked into JAR")

        assembly = HERE.parent / "rw-newyear-full-assembly.zip"
        self.assertEqual(assembly.read_bytes(), (public / assembly.name).read_bytes())
        with zipfile.ZipFile(assembly) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(z.read("resourcepack/" + PACK.name), pack_bytes)
            self.assertEqual(z.read("resourcepack/" + V64_PACK.name), V64_PACK.read_bytes())
            self.assertEqual(z.read("server.properties").decode("utf-8"), props)
            self.assertEqual(z.read("plugins/KingGriefCore/config.yml").decode("utf-8"), config)
            self.assertEqual(z.read("plugins/KingGriefCore.jar"), core_jar)

    def test_source_does_not_force_the_old_item_or_pack_models(self):
        color = (JAVA_DIR / "ColorUtil.java").read_text(encoding="utf-8")
        method = color[color.index("public static void applyCustomItemModel("):]
        method = method[:method.index("public static void applyArmorTrimSafe(")]
        self.assertIn("setCustomModelData", method)
        self.assertNotIn("setItemModel", method)
        gameplay = (JAVA_DIR / "GameplayListener.java").read_text(encoding="utf-8")
        self.assertIn("ResourcePackSettings.resolve", gameplay)
        self.assertNotIn("effectiveUrl.contains", gameplay)
        for hook in ("repairInventory(p.getInventory())", "repairInventory(e.getInventory())", "repairItem(item)"):
            self.assertIn(hook, gameplay)


if __name__ == "__main__":
    unittest.main(verbosity=2)
