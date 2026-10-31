package dev.axsesclient.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.axsesclient.common.animation.AnimationManager;
import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.keybinds.KeybindManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.theme.ColorBlindMode;
import dev.axsesclient.common.theme.ThemeManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ConfigManager {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final long MAX_CONFIG_FILE_BYTES = 512L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configsDir;
    private final Path backupsDir;
    private final ModuleManager moduleManager;
    private final HudManager hudManager;
    private final ThemeManager themeManager;
    private final KeybindManager keybindManager;
    private final AnimationManager animationManager;
    private final FontManager fontManager;
    private final LocalizationManager localizationManager;
    private final NotificationManager notificationManager;
    private final CompatibilityEngine compatibilityEngine;

    private String activeProfile = "default";
    private float guiScale = 1.0f;
    private float guiOffsetX = 0.0f;
    private float guiOffsetY = 0.0f;
    private String lastCorruptBackupName = null;
    private final ScheduledExecutorService asyncWriter = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Axses-Async-Config");
            t.setDaemon(true);
            return t;
        }
    });
    private ScheduledFuture<?> pendingSaveTask = null;

    public ConfigManager(
            Path clientRootDir,
            ModuleManager moduleManager,
            HudManager hudManager,
            ThemeManager themeManager,
            KeybindManager keybindManager,
            AnimationManager animationManager,
            FontManager fontManager,
            LocalizationManager localizationManager,
            NotificationManager notificationManager,
            CompatibilityEngine compatibilityEngine
    ) {
        this.configsDir = clientRootDir.resolve("configs");
        this.backupsDir = clientRootDir.resolve("backups");
        this.moduleManager = moduleManager;
        this.hudManager = hudManager;
        this.themeManager = themeManager;
        this.keybindManager = keybindManager;
        this.animationManager = animationManager;
        this.fontManager = fontManager;
        this.localizationManager = localizationManager;
        this.notificationManager = notificationManager;
        this.compatibilityEngine = compatibilityEngine;
        try {
            Files.createDirectories(this.configsDir);
            Files.createDirectories(this.backupsDir);
        } catch (IOException ignored) {
        }
    }

    public static String sanitizeProfileName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "default";
        }
        String clean = raw.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (clean.length() > 48) {
            clean = clean.substring(0, 48);
        }
        return clean.isEmpty() ? "default" : clean;
    }

    private Path resolveSafeConfigPath(String profileName) {
        String safe = sanitizeProfileName(profileName);
        Path target = configsDir.resolve(safe + ".json").normalize();
        if (!target.startsWith(configsDir.normalize())) {
            throw new SecurityException("Blocked path traversal in config path");
        }
        return target;
    }

    public synchronized boolean create(String profileName, MinecraftAdapter mc) {
        String safe = sanitizeProfileName(profileName);
        Path file = resolveSafeConfigPath(safe);
        if (Files.exists(file)) {
            return false;
        }
        this.activeProfile = safe;
        return save(safe);
    }

    public synchronized boolean save(String profileName) {
        String safe = sanitizeProfileName(profileName);
        Path target = resolveSafeConfigPath(safe);
        try {
            Files.createDirectories(configsDir);
            if (Files.exists(target)) {
                // Keep rolling last-known-good snapshot before overwriting
                Files.createDirectories(backupsDir);
                Path rollingBackup = backupsDir.resolve(safe + ".lastgood.bak");
                try {
                    Files.copy(target, rollingBackup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
            Path tmp = configsDir.resolve(safe + ".json.tmp");
            byte[] payload = GSON.toJson(serializeState(safe)).getBytes(StandardCharsets.UTF_8);
            Files.write(tmp, payload);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            this.activeProfile = safe;
            return true;
        } catch (IOException e) {
            notificationManager.push("Config Save Error", e.getMessage(), NotificationManager.Severity.ERROR);
            return false;
        }
    }

    public synchronized boolean saveActive() {
        return save(activeProfile);
    }

    /**
     * Non-blocking debounced background save so toggling modules or dragging sliders
     * never causes render-thread disk I/O lag spikes on Windows.
     */
    public synchronized void saveActiveAsync() {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
        }
        final String profileSnapshot = this.activeProfile;
        pendingSaveTask = asyncWriter.schedule(new Runnable() {
            @Override
            public void run() {
                save(profileSnapshot);
            }
        }, 220L, TimeUnit.MILLISECONDS);
    }

    public synchronized void flushPendingSave() {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
            pendingSaveTask = null;
        }
        save(activeProfile);
    }

    public synchronized boolean load(String profileName, MinecraftAdapter mc) {
        String safe = sanitizeProfileName(profileName);
        Path source = resolveSafeConfigPath(safe);
        if (!Files.exists(source)) {
            this.activeProfile = safe;
            return save(safe);
        }
        try {
            long size = Files.size(source);
            if (size <= 0L || size > MAX_CONFIG_FILE_BYTES) {
                throw new IOException("Config file empty or exceeds 512 KB safety limit");
            }
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(text);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException("Root element is not a JSON object");
            }
            applyState(parsed.getAsJsonObject(), mc);
            this.activeProfile = safe;
            return true;
        } catch (Exception ex) {
            handleCorruptedConfig(safe, source, ex, mc);
            return false;
        }
    }

    private void handleCorruptedConfig(String safeName, Path brokenFile, Exception cause, MinecraftAdapter mc) {
        try {
            Files.createDirectories(backupsDir);
            String corruptCopyName = "corrupt-" + safeName + "-" + System.currentTimeMillis() + ".json";
            Path quarantine = backupsDir.resolve(corruptCopyName);
            Files.copy(brokenFile, quarantine, StandardCopyOption.REPLACE_EXISTING);
            this.lastCorruptBackupName = corruptCopyName;
        } catch (IOException ignored) {
        }

        String msg = "Corrupted config '" + safeName + "' backed up safely. Click Restore Backup or continue with safe defaults.";
        compatibilityEngine.recordConfigWarning(msg + " (" + cause.getClass().getSimpleName() + ")");
        notificationManager.push("Config Recovery", msg, NotificationManager.Severity.WARNING, 5500L);

        // Attempt automatic restore from rolling last-known-good backup if valid, else clean defaults
        Path rollingBackup = backupsDir.resolve(safeName + ".lastgood.bak");
        if (Files.isRegularFile(rollingBackup)) {
            try {
                String bakText = new String(Files.readAllBytes(rollingBackup), StandardCharsets.UTF_8);
                JsonElement bakParsed = new JsonParser().parse(bakText);
                if (bakParsed != null && bakParsed.isJsonObject()) {
                    applyState(bakParsed.getAsJsonObject(), mc);
                    save(safeName);
                    notificationManager.push("Backup Restored", "Recovered '" + safeName + "' from last valid backup.", NotificationManager.Severity.SUCCESS);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        moduleManager.resetAll(mc);
        hudManager.resetAll();
        save(safeName);
    }

    public synchronized boolean rename(String oldName, String newName) {
        String safeOld = sanitizeProfileName(oldName);
        String safeNew = sanitizeProfileName(newName);
        if (safeOld.equals(safeNew)) {
            return false;
        }
        Path src = resolveSafeConfigPath(safeOld);
        Path dst = resolveSafeConfigPath(safeNew);
        if (!Files.exists(src) || Files.exists(dst)) {
            return false;
        }
        try {
            Files.move(src, dst);
            if (safeOld.equals(activeProfile)) {
                activeProfile = safeNew;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean delete(String profileName, MinecraftAdapter mc) {
        String safe = sanitizeProfileName(profileName);
        if ("default".equals(safe)) {
            // Reset default instead of leaving 0 profiles
            moduleManager.resetAll(mc);
            hudManager.resetAll();
            return save("default");
        }
        Path target = resolveSafeConfigPath(safe);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (safe.equals(activeProfile)) {
                load("default", mc);
            }
            return deleted;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized Path exportConfig(Path destinationFile) throws IOException {
        if (destinationFile.getParent() != null) {
            Files.createDirectories(destinationFile.getParent());
        }
        byte[] bytes = GSON.toJson(serializeState(activeProfile)).getBytes(StandardCharsets.UTF_8);
        Files.write(destinationFile, bytes);
        return destinationFile;
    }

    public synchronized boolean importConfig(Path sourceFile, String targetProfileName, MinecraftAdapter mc) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            return false;
        }
        try {
            long size = Files.size(sourceFile);
            if (size <= 0L || size > MAX_CONFIG_FILE_BYTES) {
                throw new IllegalArgumentException("Imported config exceeds 512 KB limit");
            }
            String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
            JsonElement el = new JsonParser().parse(content);
            if (el == null || !el.isJsonObject()) {
                throw new IllegalArgumentException("Imported config is not a JSON object");
            }
            applyState(el.getAsJsonObject(), mc);
            String safeTarget = sanitizeProfileName(targetProfileName);
            this.activeProfile = safeTarget;
            return save(safeTarget);
        } catch (Exception e) {
            notificationManager.push("Config Import Rejected", e.getMessage(), NotificationManager.Severity.ERROR);
            return false;
        }
    }

    public synchronized String createBackup() {
        try {
            Files.createDirectories(backupsDir);
            String stampName = sanitizeProfileName(activeProfile) + "-" + System.currentTimeMillis() + ".bak.json";
            Path backupFile = backupsDir.resolve(stampName);
            byte[] payload = GSON.toJson(serializeState(activeProfile)).getBytes(StandardCharsets.UTF_8);
            Files.write(backupFile, payload);
            notificationManager.push("Backup Created", stampName, NotificationManager.Severity.SUCCESS);
            return stampName;
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized boolean restoreBackup(String backupFileName, MinecraftAdapter mc) {
        if (backupFileName == null || backupFileName.contains("..") || backupFileName.contains("/") || backupFileName.contains("\\")) {
            return false;
        }
        Path file = backupsDir.resolve(backupFileName).normalize();
        if (!file.startsWith(backupsDir.normalize()) || !Files.isRegularFile(file)) {
            return false;
        }
        return importConfig(file, activeProfile, mc);
    }

    public synchronized List<String> listProfiles() {
        List<String> names = new ArrayList<String>();
        try {
            Files.createDirectories(configsDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.json")) {
                for (Path p : stream) {
                    String fn = p.getFileName().toString();
                    if (fn.endsWith(".json")) {
                        names.add(fn.substring(0, fn.length() - 5));
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (!names.contains("default")) {
            names.add("default");
        }
        Collections.sort(names);
        return names;
    }

    public synchronized List<String> listBackups() {
        List<String> names = new ArrayList<String>();
        try {
            Files.createDirectories(backupsDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.json")) {
                for (Path p : stream) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(names, Collections.reverseOrder());
        return names;
    }

    public JsonObject serializeState(String profileName) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(CURRENT_SCHEMA_VERSION));
        root.addProperty("profileName", profileName);
        root.addProperty("activeThemeId", themeManager.getActiveThemeId());
        root.addProperty("guiScale", Float.valueOf(guiScale));
        root.addProperty("guiX", Float.valueOf(guiOffsetX));
        root.addProperty("guiY", Float.valueOf(guiOffsetY));
        root.addProperty("openGuiKey", Integer.valueOf(keybindManager.getOpenGuiBind().getKeyCode()));
        root.addProperty("openHudEditorKey", Integer.valueOf(keybindManager.getOpenHudEditorBind().getKeyCode()));
        root.addProperty("reducedMotion", Boolean.valueOf(animationManager.isReducedMotion()));
        root.addProperty("animationSpeed", Float.valueOf(animationManager.getAnimationSpeed()));
        root.addProperty("animationIntensity", Float.valueOf(animationManager.getAnimationIntensity()));
        root.addProperty("colorBlindMode", themeManager.getColorBlindMode().name());
        root.addProperty("fontScale", fontManager.getFontScale().name());
        root.addProperty("language", localizationManager.getActiveLocale());

        JsonObject modsObj = new JsonObject();
        for (Module m : moduleManager.getAll()) {
            modsObj.add(m.getId(), m.toJson());
        }
        root.add("modules", modsObj);

        JsonObject hudObj = new JsonObject();
        for (HudElement el : hudManager.getAll()) {
            hudObj.add(el.getId(), el.toJson());
        }
        root.add("hudElements", hudObj);
        return root;
    }

    public void applyState(JsonObject root, MinecraftAdapter mc) {
        if (root.has("schemaVersion")) {
            int ver = root.get("schemaVersion").getAsInt();
            if (ver < 1 || ver > CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported config schemaVersion: " + ver);
            }
        }
        if (root.has("activeThemeId") && root.get("activeThemeId").isJsonPrimitive()) {
            themeManager.setActiveTheme(root.get("activeThemeId").getAsString());
        }
        if (root.has("guiScale") && root.get("guiScale").isJsonPrimitive()) {
            setGuiScale(root.get("guiScale").getAsFloat());
        }
        if (root.has("guiX") && root.get("guiX").isJsonPrimitive()) {
            this.guiOffsetX = clampFinite(root.get("guiX").getAsFloat(), -600f, 600f, 0f);
        }
        if (root.has("guiY") && root.get("guiY").isJsonPrimitive()) {
            this.guiOffsetY = clampFinite(root.get("guiY").getAsFloat(), -400f, 400f, 0f);
        }
        if (root.has("openGuiKey") && root.get("openGuiKey").isJsonPrimitive()) {
            keybindManager.getOpenGuiBind().assign(root.get("openGuiKey").getAsInt());
        }
        if (root.has("openHudEditorKey") && root.get("openHudEditorKey").isJsonPrimitive()) {
            keybindManager.getOpenHudEditorBind().assign(root.get("openHudEditorKey").getAsInt());
        }
        if (root.has("reducedMotion") && root.get("reducedMotion").isJsonPrimitive()) {
            animationManager.setReducedMotion(root.get("reducedMotion").getAsBoolean());
        }
        if (root.has("animationSpeed") && root.get("animationSpeed").isJsonPrimitive()) {
            animationManager.setAnimationSpeed(root.get("animationSpeed").getAsFloat());
        }
        if (root.has("animationIntensity") && root.get("animationIntensity").isJsonPrimitive()) {
            animationManager.setAnimationIntensity(root.get("animationIntensity").getAsFloat());
        }
        if (root.has("colorBlindMode") && root.get("colorBlindMode").isJsonPrimitive()) {
            try {
                themeManager.setColorBlindMode(ColorBlindMode.valueOf(root.get("colorBlindMode").getAsString().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        if (root.has("fontScale") && root.get("fontScale").isJsonPrimitive()) {
            try {
                fontManager.setFontScale(FontManager.FontScale.valueOf(root.get("fontScale").getAsString().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        if (root.has("language") && root.get("language").isJsonPrimitive()) {
            localizationManager.setActiveLocale(root.get("language").getAsString());
        }
        if (root.has("modules") && root.get("modules").isJsonObject()) {
            JsonObject modsObj = root.getAsJsonObject("modules");
            for (Module m : moduleManager.getAll()) {
                if (modsObj.has(m.getId()) && modsObj.get(m.getId()).isJsonObject()) {
                    m.fromJson(modsObj.getAsJsonObject(m.getId()), mc);
                }
            }
        }
        if (root.has("hudElements") && root.get("hudElements").isJsonObject()) {
            JsonObject hudObj = root.getAsJsonObject("hudElements");
            for (HudElement el : hudManager.getAll()) {
                if (hudObj.has(el.getId()) && hudObj.get(el.getId()).isJsonObject()) {
                    el.fromJson(hudObj.getAsJsonObject(el.getId()));
                }
            }
        }
    }

    private static float clampFinite(float v, float min, float max, float fallback) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return fallback;
        return Math.max(min, Math.min(max, v));
    }

    public String getActiveProfile() { return activeProfile; }
    public float getGuiScale() { return guiScale; }
    public void setGuiScale(float guiScale) { this.guiScale = clampFinite(guiScale, 0.70f, 1.65f, 1.0f); }
    public float getGuiOffsetX() { return guiOffsetX; }
    public void setGuiOffsetX(float x) { this.guiOffsetX = clampFinite(x, -600f, 600f, 0f); }
    public float getGuiOffsetY() { return guiOffsetY; }
    public void setGuiOffsetY(float y) { this.guiOffsetY = clampFinite(y, -400f, 400f, 0f); }
    public String getLastCorruptBackupName() { return lastCorruptBackupName; }
}
