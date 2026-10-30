package dev.axsesclient.common.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.axsesclient.common.keybinds.KeybindManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CompatibilityEngine {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> OFFICIAL_SUPPORTED_MC = Arrays.asList("1.16.5", "1.21.4", "1.21.11");

    public static final class CompatibilityReport {
        private final String minecraftVersion;
        private final boolean minecraftSupported;
        private final String javaVersion;
        private final boolean javaCompatible;
        private final String fabricLoaderVersion;
        private final String fabricApiVersion;
        private final String mappingsInfo;
        private final List<String> loadedMods;
        private final List<String> keybindConflicts;
        private final List<String> configWarnings;
        private final List<String> restrictedModules;

        public CompatibilityReport(
                String minecraftVersion,
                boolean minecraftSupported,
                String javaVersion,
                boolean javaCompatible,
                String fabricLoaderVersion,
                String fabricApiVersion,
                String mappingsInfo,
                List<String> loadedMods,
                List<String> keybindConflicts,
                List<String> configWarnings,
                List<String> restrictedModules
        ) {
            this.minecraftVersion = minecraftVersion;
            this.minecraftSupported = minecraftSupported;
            this.javaVersion = javaVersion;
            this.javaCompatible = javaCompatible;
            this.fabricLoaderVersion = fabricLoaderVersion;
            this.fabricApiVersion = fabricApiVersion;
            this.mappingsInfo = mappingsInfo;
            this.loadedMods = Collections.unmodifiableList(new ArrayList<String>(loadedMods));
            this.keybindConflicts = Collections.unmodifiableList(new ArrayList<String>(keybindConflicts));
            this.configWarnings = Collections.unmodifiableList(new ArrayList<String>(configWarnings));
            this.restrictedModules = Collections.unmodifiableList(new ArrayList<String>(restrictedModules));
        }

        public String getMinecraftVersion() { return minecraftVersion; }
        public boolean isMinecraftSupported() { return minecraftSupported; }
        public String getJavaVersion() { return javaVersion; }
        public boolean isJavaCompatible() { return javaCompatible; }
        public String getFabricLoaderVersion() { return fabricLoaderVersion; }
        public String getFabricApiVersion() { return fabricApiVersion; }
        public String getMappingsInfo() { return mappingsInfo; }
        public List<String> getLoadedMods() { return loadedMods; }
        public List<String> getKeybindConflicts() { return keybindConflicts; }
        public List<String> getConfigWarnings() { return configWarnings; }
        public List<String> getRestrictedModules() { return restrictedModules; }

        public boolean isHealthy() {
            return minecraftSupported && javaCompatible && configWarnings.isEmpty();
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("product", "AXSESCLIENT GENESIS X v1.0.0");
            o.addProperty("timestampMs", Long.valueOf(System.currentTimeMillis()));
            o.addProperty("healthy", Boolean.valueOf(isHealthy()));
            o.addProperty("minecraftVersion", minecraftVersion);
            o.addProperty("minecraftSupported", Boolean.valueOf(minecraftSupported));
            o.addProperty("javaVersion", javaVersion);
            o.addProperty("javaCompatible", Boolean.valueOf(javaCompatible));
            o.addProperty("fabricLoaderVersion", fabricLoaderVersion);
            o.addProperty("fabricApiVersion", fabricApiVersion);
            o.addProperty("mappings", mappingsInfo);
            o.add("loadedMods", toJsonArray(loadedMods));
            o.add("keybindConflicts", toJsonArray(keybindConflicts));
            o.add("configWarnings", toJsonArray(configWarnings));
            o.add("restrictedModules", toJsonArray(restrictedModules));
            return o;
        }

        private static JsonArray toJsonArray(List<String> items) {
            JsonArray arr = new JsonArray();
            for (String s : items) {
                arr.add(s);
            }
            return arr;
        }
    }

    private final List<String> configWarnings = new ArrayList<String>();
    private CompatibilityReport lastReport;

    public void recordConfigWarning(String warning) {
        if (warning != null && !warning.isEmpty()) {
            configWarnings.add(warning);
        }
    }

    public void clearConfigWarnings() {
        configWarnings.clear();
    }

    public CompatibilityReport evaluate(
            MinecraftAdapter mc,
            InputAdapter input,
            ModuleManager moduleManager,
            KeybindManager keybindManager
    ) {
        String mcVer = mc != null ? mc.getMinecraftVersion() : "unknown";
        boolean mcSupported = OFFICIAL_SUPPORTED_MC.contains(mcVer);

        String javaVer = mc != null ? mc.getJavaVersion() : System.getProperty("java.version", "unknown");
        int javaMajor = parseJavaMajor(javaVer);
        int requiredJava = "1.16.5".equals(mcVer) ? 8 : 21;
        boolean javaOk = javaMajor >= requiredJava;

        String loaderVer = mc != null ? mc.getFabricLoaderVersion() : "unknown";
        String fapiVer = mc != null ? mc.getFabricApiVersion() : "optional";
        List<String> mods = mc != null && mc.getLoadedMods() != null ? mc.getLoadedMods() : Collections.<String>emptyList();

        List<String> kbConflictText = new ArrayList<String>();
        if (keybindManager != null) {
            Map<Integer, List<String>> conflicts = keybindManager.detectConflicts(input);
            for (Map.Entry<Integer, List<String>> e : conflicts.entrySet()) {
                kbConflictText.add(KeyCodes.formatKey(e.getKey().intValue()) + " -> " + e.getValue());
            }
        }

        List<String> restricted = new ArrayList<String>();
        if (moduleManager != null) {
            for (Module m : moduleManager.getAll()) {
                String reason = m.getIncompatibilityReason(mc);
                if (reason != null) {
                    restricted.add(m.getName() + ": " + reason);
                }
            }
        }

        this.lastReport = new CompatibilityReport(
                mcVer,
                mcSupported,
                javaVer,
                javaOk,
                loaderVer,
                fapiVer,
                "Mojang Official (Remapped to Fabric Intermediary)",
                mods,
                kbConflictText,
                configWarnings,
                restricted
        );
        return this.lastReport;
    }

    public CompatibilityReport getLastReport() {
        return lastReport;
    }

    public void writeReportToDisk(Path clientRootDir) {
        if (lastReport == null || clientRootDir == null) {
            return;
        }
        try {
            Files.createDirectories(clientRootDir);
            Path target = clientRootDir.resolve("compatibility-report.json");
            Files.write(target, GSON.toJson(lastReport.toJson()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static int parseJavaMajor(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            return 8;
        }
        String v = versionString.trim();
        if (v.startsWith("1.")) {
            v = v.substring(2);
        }
        int dot = v.indexOf('.');
        int dash = v.indexOf('-');
        int end = v.length();
        if (dot > 0) end = Math.min(end, dot);
        if (dash > 0) end = Math.min(end, dash);
        try {
            return Integer.parseInt(v.substring(0, end));
        } catch (NumberFormatException e) {
            return 8;
        }
    }
}
