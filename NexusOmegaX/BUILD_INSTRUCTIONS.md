# BUILD INSTRUCTIONS — NEXUS CLIENT OMEGA X

Everything below was executed in this repository's build environment. Read the
*Verification* section for what was compiled here and what must be compiled on
your machine (the three mod jars need Minecraft + Fabric, which cannot be
downloaded in the sandbox this was authored in).

---

## 0. Requirements

| Tool | Version used | Needed for |
|---|---|---|
| JDK | 21 (Temurin) — **and** JDK 8 for the 1.16.5 jar | compiling the core |
| Gradle | 8.x (wrapper not bundled: run `gradle`, not `./gradlew`) | Fabric Loom builds |
| Zig | 0.17 (`pip install ziglang`) | cross compiling `NexusInjector.exe` |
| Windows | 10/11 (64-bit) | running the exe |

The core has **zero runtime dependencies**: no Gson, no Guava, no LWJGL. It only
needs `nexus.api` + a JDK.

---

## 1. Build the three mod jars — Windows, one click

```
build.bat /install
```

That is all. The script

1. checks Java (JDK 21 needed for 1.21.x),
2. uses your `gradle` — or downloads the official Gradle 8.10.2 into
   `%USERPROFILE%\.gradle-dist` if it is missing,
3. builds all three adapters (`gradle build` with Fabric Loom),
4. copies `NexusOmegaX-1.16.5.jar`, `-1.21.4.jar`, `-1.21.11.jar` into `dist\`,
5. with `/install` — also straight into `%APPDATA%\.minecraft\mods`.

Useful variants:

```
build.bat               build all three versions, keep the jars in dist\
build.bat 1.21.4        build a single version
build.bat /install      build and install into .minecraft\mods
install-mods.bat        only copy already built jars into .minecraft\mods
```

On Linux / macOS the same thing is `./build-all.sh` (add `--install` to copy the
jars into `~/.minecraft/mods`).

### Mixin safety check

`gradle build` runs `verifyMixins` first: it compares every `@Inject` /
`@ModifyVariable` target with the real mapped Minecraft jar and fails if a method was
renamed or its parameters changed.

### If a build fails

Every version knob lives in one file per adapter — no code editing:

```
adapters/nexus1214/gradle.properties     loomVersion, minecraftVersion, loaderVersion
adapters/nexus12111/gradle.properties    loomVersion, minecraftVersion, loaderVersion
adapters/nexus1165/gradle.properties     loomVersion, minecraftVersion, loaderVersion, yarnMappings
```

Override anything from the command line, e.g.
`gradle build -p adapters/nexus1214 -PloomVersion=1.10-SNAPSHOT`.
Typical fixes:

| Gradle says | Fix |
|---|---|
| `Could not find net.fabricmc:fabric-loom:1.11-SNAPSHOT` | raise/lower `loomVersion` (try 1.10 or 1.12) |
| `Could not resolve com.mojang:minecraft` | no internet / proxy; allow `piston-data.mojang.com` and `libraries.minecraft.net` |
| `Could not resolve net.fabricmc:yarn:1.16.5+build.10` | change `yarnMappings` (see <https://maven.fabricmc.net/net/fabricmc/yarn/>) |
| `Unsupported class file major version` on 1.21.x | run Gradle with JDK 21 (`set JAVA_HOME=...`) |
| Gradle 7 + Loom 1.6 | set `loomVersion=0.12-SNAPSHOT` for the 1.16.5 adapter |

Send me the last 30 lines of the Gradle output and I will fix it in the sources.

## 2. Build the three mod jars (manual)

Fabric Loom resolves Minecraft and the mappings, so this step **needs internet
access to `maven.fabricmc.net`, `maven.minecraftforge.net` and `repo1.maven.org`**.

```bash
# from the repository root
./build-all.sh                 # builds all three adapters
# or, one version at a time:
cd adapters/nexus1214  && gradle build && cd -
cd adapters/nexus12111 && gradle build && cd -
cd adapters/nexus1165  && gradle build && cd -
```

Output lands in `dist/`:

```
dist/NexusOmegaX-1.16.5.jar
dist/NexusOmegaX-1.21.4.jar
dist/NexusOmegaX-1.21.11.jar
```

Each adapter's `build.gradle` compiles `api/src`, `core/src`, `modules/src`
**plus** its own adapter sources into a single remapped Fabric jar:

```gradle
sourceSets {
    main {
        java      { srcDirs = ['src', '../../api/src', '../../core/src', '../../modules/src'] }
        resources { srcDirs = ['src/main/resources'] }
    }
}
```

Notes per version:

* **1.21.4 / 1.21.11** – `fabric-loom` 1.9+/1.11+, `JavaVersion.VERSION_21`,
  mojmap names (`net.minecraft.client.Minecraft`, `GuiGraphics`, `PoseStack` …).
* **1.16.5** – `fabric-loom` 0.12+, `JavaVersion.VERSION_1_8`, yarn names
  (`MinecraftClient`, `MatrixStack`, `GameOptions` …).
  Loom needs a **Java 8 toolchain** for this module; if your Gradle runs on JDK
  21, point Loom at a JDK 8 install:

  ```gradle
  // adapters/nexus1165/build.gradle
  java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }
  ```

### If a version cannot be built

Do **not** fake the jar. The launcher shows `FAIL client not installed` in
`VERIFY INSTALL` and the client simply does not load for that version — every
other version keeps working.

---

## 3. Build the Windows launcher

```bash
pip install ziglang          # or https://ziglang.org/download/
cd launcher
./build.sh                   # -> launcher/build/NexusInjector.exe
cp build/NexusInjector.exe ../dist/
```

What `build.sh` runs:

```bash
zig cc -target x86_64-windows-gnu -O2 -std=c11 \
  -o build/NexusInjector.exe src/nexus_injector.c \
  -Wl,--subsystem,windows -lgdi32 -luser32 -lcomctl32 -lcomdlg32 \
  -lshell32 -lole32 -lurlmon -lshlwapi -ladvapi32 -luxtheme
```

A cross compiled **PE32+ Windows GUI** executable, 249 KB, no installer, no
runtime, no external files. It links the Universal CRT that ships with Windows
10 and 11.

(`build-all.sh` does both steps and re-creates `NexusOmegaX-Source.zip`.)

---

## 4. Compile and run the headless test suite

The suite boots the whole client against a fake platform
(`nexus.test.HeadlessPlatform`), so it needs neither Minecraft nor a display.

```bash
javac -d build/classes $(find api/src core/src modules/src test/src -name '*.java')
java  -cp build/classes nexus.test.TestSuite build/test-runtime
```

Result: `checks: 71, failures: 0` (exit code 0).
See `TEST_REPORT.md` for what each of the 71 checks asserts.

---

## 5. Source archive

```bash
./build-all.sh --zip-only      # or:  zip -r NexusOmegaX-Source.zip . -x 'dist/*' 'build/*'
```

The archive contains `api/`, `core/`, `modules/`, `adapters/`, `launcher/`,
`test/`, the three markdown documents and the build scripts — no binaries.

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Could not resolve com.mojang:minecraft` | no internet / proxy | open the firewall for `maven.minecraftforge.net` |
| `Unsupported class file major version` on 1.16.5 | Loom compiled with JDK 21 | set the Java 8 toolchain (see above) |
| `INSTALL CLIENT` says jar not found | jars not built | run `./build-all.sh`, or put the jar next to the exe / in `jars/` |
| `INSTALL FABRIC` fails to download | no internet | download the installer from <https://fabricmc.net/use/installer/> by hand |
| ClickGUI does not open | Fabric profile not selected | choose the `Nexus Client Omega X` profile in the official launcher |
| Modules show as *unsupported* | version mismatch | each module declares the versions it supports; unsupported modules refuse to enable |
