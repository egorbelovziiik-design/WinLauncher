#!/usr/bin/env python3
"""Validate the generated pack against KingGriefCore without a running server.

Run after build_rp_v66.py: python3 test_resource_pack.py
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
PACK = HERE.parent / "kinggrief-mega-rp-v66.zip"
V65_PACK = HERE.parent / "kinggrief-mega-rp-v65.zip"
RANK_PACK = HERE.parent.parent / "kinggrief-ranks-pack/kinggrief-ranks-pack-v66.zip"
V64_PACK = HERE.parent / "kinggrief-mega-rp-v64.zip"
JAVA_DIR = HERE.parent / "core-src/src/main/java/net/kinggrief/core"
MAPPING = json.loads((HERE / "item-model-map-v66.json").read_text(encoding="utf-8"))
FALLBACKS = json.loads((HERE / "vanilla-item-fallbacks.json").read_text(encoding="utf-8"))


def sphere_names():
    source = ast.parse((HERE / "build_rp_v66.py").read_text(encoding="utf-8"))
    assignments = [n for n in source.body if isinstance(n, ast.Assign)
                   and any(isinstance(t, ast.Name) and t.id == "ITEMS_SPECS" for t in n.targets)]
    specs = ast.literal_eval(assignments[0].value)
    return {name for primary, aliases, category, *_ in specs if category == "sphere"
            for name in [primary, *aliases]}


SPHERE_NAMES = sphere_names()

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

    def test_spheres_are_real_small_cubes(self):
        spheres = SPHERE_NAMES
        self.assertGreaterEqual(len(spheres), 12)
        with zipfile.ZipFile(V64_PACK) as old:
            for item in spheres:
                with self.subTest(sphere=item):
                    path = f"assets/kinggrief/models/item/{item}.json"
                    model = self.load(path)
                    previous = json.loads(old.read(path))
                    self.assertEqual(model["parent"], "minecraft:block/block")
                    self.assertEqual(len(model["elements"]), 1)
                    cube = model["elements"][0]
                    self.assertEqual(cube["from"], [5, 5, 5])
                    self.assertEqual(cube["to"], [11, 11, 11])
                    self.assertEqual(set(cube["faces"]), {"up", "down", "north", "south", "east", "west"})
                    self.assertTrue(all(face["texture"] == "#layer0" for face in cube["faces"].values()))
                    for ctx in ("gui", "firstperson_righthand", "firstperson_lefthand",
                                "thirdperson_righthand", "thirdperson_lefthand", "ground", "fixed"):
                        self.assertLess(6 / 16 * model["display"][ctx]["scale"][0], previous["display"][ctx]["scale"][0], ctx)
            other = "assets/kinggrief/models/item/totem_santy.json"
            self.assertEqual(self.z.read(other), old.read(other))

    def test_new_glyphs_and_separate_ranks_pack(self):
        font = self.load("assets/minecraft/font/default.json")
        providers = {char: p for p in font["providers"] for line in p.get("chars", []) for char in line}
        for idx, rank in enumerate(("curator_discord", "curator_kp", "curator_media", "curator_dkp", "gladmin", "stadmin"), 43):
            for frame in range(8):
                provider = providers[chr(0xea00 + idx * 8 + frame)]
                self.assertEqual(provider["file"], f"minecraft:font/ranks/{rank}_{frame}.png")
                self.assertIn(f"assets/minecraft/textures/font/ranks/{rank}_{frame}.png", self.names)
        with zipfile.ZipFile(RANK_PACK) as ranks:
            self.assertIsNone(ranks.testzip())
            for path in ranks.namelist():
                if not path.endswith("/") and path != "pack.mcmeta":
                    self.assertIn(path, self.names)
                    self.assertEqual(ranks.read(path), self.z.read(path), path)
            self.assertFalse(any("/models/item/" in path for path in ranks.namelist()))

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
                        expected = (255, 255) if Path(name).stem in SPHERE_NAMES else (0, 255)
                        self.assertEqual(alpha.getextrema(), expected, f"invalid transparency in frame {frame}")


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
        self.assertEqual(hashlib.sha1(V65_PACK.read_bytes()).hexdigest(), "fc61175f0692ab85486898160dce223097aa4382")
        self.assertEqual((public / V65_PACK.name).read_bytes(), V65_PACK.read_bytes())
        self.assertEqual((public / RANK_PACK.name).read_bytes(), RANK_PACK.read_bytes())
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

        plugin_yml = (HERE.parent / "core-src/src/main/resources/plugin.yml").read_text(encoding="utf-8")
        match = re.search(r"^version:\s*['\"]?([0-9]+\.[0-9]+\.[0-9]+)", plugin_yml, re.MULTILINE)
        self.assertIsNotNone(match, "Core version missing from plugin.yml")
        core_version = match.group(1)
        core_jar = (public / "KingGriefCore.jar").read_bytes()
        self.assertEqual((public / f"KingGriefCore-v{core_version}.jar").read_bytes(), core_jar)
        for file in (HERE.parent / "KingGriefCore.jar", HERE.parent / "plugins/KingGriefCore.jar",
                     HERE.parent / "server/plugins/KingGriefCore.jar"):
            self.assertEqual(file.read_bytes(), core_jar)
        self.assertEqual((public / "KingHub.jar").read_bytes(), (HERE.parent / "KingHub.jar").read_bytes())
        update = HERE.parent / f"KingGriefCore-{core_version}-update.zip"
        self.assertEqual(update.read_bytes(), (public / update.name).read_bytes())
        with zipfile.ZipFile(update) as patch:
            self.assertIsNone(patch.testzip())
            self.assertEqual(set(patch.namelist()), {"KingGriefCore.jar", "ПРОЧИТАЙ_СНАЧАЛА.md"})
            self.assertEqual(patch.read("KingGriefCore.jar"), core_jar)
            instruction = patch.read("ПРОЧИТАЙ_СНАЧАЛА.md").decode("utf-8")
            self.assertEqual(instruction, (HERE.parent / f"ОБНОВЛЕНИЕ_{core_version}.md").read_text(encoding="utf-8"))
            self.assertIn(hashlib.sha256(core_jar).hexdigest(), instruction)
            self.assertIn("plugins/update/", instruction)
            self.assertNotIn("{{CORE_SHA256}}", instruction)
        # Older versioned downloads must remain byte-for-byte available.
        previous = HERE.parent / "KingGriefCore-6.7.0-update.zip"
        self.assertEqual(previous.read_bytes(), (public / previous.name).read_bytes())
        self.assertEqual(hashlib.sha256(previous.read_bytes()).hexdigest(),
                         "d3aa9c406d380edbe5476e9024b5c10c6048b0adcd74b88fd5b83caa6e308250")
        with zipfile.ZipFile(previous) as patch:
            self.assertEqual(patch.read("KingGriefCore.jar"), (public / "KingGriefCore-v6.7.0.jar").read_bytes())
        hotfix = HERE.parent / "KingGriefCore-6.6.4-hotfix.zip"
        self.assertEqual(hotfix.read_bytes(), (public / hotfix.name).read_bytes())
        with zipfile.ZipFile(hotfix) as patch:
            self.assertEqual(patch.read("KingGriefCore.jar"), (public / "KingGriefCore-v6.6.4.jar").read_bytes())
        for name, version, default in (("KingGriefCore.jar", core_version, core_default),
                                       ("KingHub.jar", "1.2.2", hub_default)):
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
            self.assertEqual(z.read("resourcepack/" + V65_PACK.name), V65_PACK.read_bytes())
            self.assertEqual(z.read("resourcepack/" + RANK_PACK.name), RANK_PACK.read_bytes())
            self.assertEqual(z.read("server.properties").decode("utf-8"), props)
            self.assertEqual(z.read("plugins/KingGriefCore/config.yml").decode("utf-8"), config)
            self.assertEqual(z.read("plugins/KingGriefCore.jar"), core_jar)
            self.assertNotIn("plugins/KingHub.jar", z.namelist())
            self.assertFalse(any(name.startswith("spawn_world/") for name in z.namelist()),
                             "archive must not overwrite an existing world during extraction")
            for rank in ("curator_discord", "curator_kp", "curator_media", "curator_dkp", "gladmin", "stadmin"):
                self.assertIn("plugins/LuckPerms/yaml-storage/groups/" + rank + ".yml", z.namelist())
                self.assertIn(rank + ":", z.read("plugins/TAB/groups.yml").decode("utf-8"))

    def test_prebuilt_spawn_distribution(self):
        import hashlib
        import io
        repo = HERE.parent.parent
        template = HERE.parent / "server/plugins/KingGriefCore/spawn-v66-template.zip"
        data = template.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        expected = (HERE.parent / "core-src/src/main/resources/spawn_v66_template.sha256").read_text(encoding="ascii").strip()
        self.assertEqual(digest, expected)
        self.assertEqual((repo / "kinggrief/public/kinggrief-spawn-v66.zip").read_bytes(), data)
        with zipfile.ZipFile(HERE.parent / "server/plugins/KingGriefCore.jar") as jar:
            self.assertEqual(jar.read("spawn_v66_template.sha256").decode("ascii").strip(), digest)
            self.assertIn("net/kinggrief/core/PreparedSpawnInstaller.class", jar.namelist())
        with zipfile.ZipFile(HERE.parent / "rw-newyear-full-assembly.zip") as assembly:
            self.assertEqual(assembly.read("plugins/KingGriefCore/spawn-v66-template.zip"), data)
            self.assertFalse(any(name.startswith("spawn_world/") for name in assembly.namelist()))
        with zipfile.ZipFile(io.BytesIO(data)) as world:
            self.assertIsNone(world.testzip())
            names = world.namelist()
            self.assertEqual(len(names), len(set(names)))
            self.assertTrue({"level.dat", ".spawn_v66_royal_ok", ".kinggrief_prebuilt_v66"}.issubset(names))
            self.assertTrue(any(name.startswith("region/") and name.endswith(".mca") for name in names))
            self.assertFalse(any(name.startswith(("entities/", "playerdata/", "stats/", "advancements/"))
                                 or name in {"uid.dat", "session.lock", "level.dat_old"} for name in names))

    def test_prebuilt_spawn_bad_template_does_not_replace_archives(self):
        import importlib.util
        import tempfile
        from unittest.mock import patch
        spec = importlib.util.spec_from_file_location("kinggrief_assembly_builder", HERE.parent / "build-assembly.py")
        builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(builder)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            server = root / "server"
            data = server / "plugins/KingGriefCore"
            data.mkdir(parents=True)
            template = data / "spawn-v66-template.zip"
            template.write_bytes(b"corrupted")
            output = root / "assembly.zip"
            public = root / "public.zip"
            output.write_bytes(b"old assembly")
            public.write_bytes(b"old public assembly")
            with patch.object(builder, "SERVER", server), patch.object(builder, "TEMPLATE", template), \
                 patch.object(builder, "ASSEMBLY", output), patch.object(builder, "PUBLIC", public):
                with self.assertRaisesRegex(RuntimeError, "SHA-256"):
                    builder.main()
                self.assertEqual(output.read_bytes(), b"old assembly")
                self.assertEqual(public.read_bytes(), b"old public assembly")
                (server / "spawn_world").mkdir()
                with self.assertRaisesRegex(RuntimeError, "Do not place spawn_world"):
                    builder.main()
                self.assertEqual(output.read_bytes(), b"old assembly")

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
