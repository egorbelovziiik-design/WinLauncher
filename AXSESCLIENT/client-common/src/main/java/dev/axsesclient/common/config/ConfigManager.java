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
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
    private String pendingProfile = null;
    private byte[] pendingPayload = null;
    // Newer profiles must not be overwritten by this older client, including at shutdown.
    private final Set<String> unsupportedProfiles = new HashSet<String>();

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
        if (!flushScheduledSave()) return false;
        String safe = sanitizeProfileName(profileName);
        if (Files.exists(resolveSafeConfigPath(safe))) return false;
        return save(safe);
    }

    private byte[] snapshot(String profile) {
        return GSON.toJson(serializeState(profile)).getBytes(StandardCharsets.UTF_8);
    }

    // Called under this manager's lock. The worker only writes an immutable snapshot;
    // it never reads game state or changes the active profile from a background thread.
    private boolean writeSnapshot(String safe, byte[] payload, boolean preserveExisting) {
        if (unsupportedProfiles.contains(safe)) {
            notificationManager.push("Config Save Error", "Profile requires a newer client: " + safe, NotificationManager.Severity.ERROR);
            return false;
        }
        Path target = resolveSafeConfigPath(safe);
        Path tmp = null;
        try {
            Files.createDirectories(configsDir);
            if (preserveExisting && Files.exists(target)) {
                if (!Files.isRegularFile(target) || !isSupportedConfig(target)) {
                    unsupportedProfiles.add(safe);
                    notificationManager.push("Config Save Error", "Existing profile is invalid or requires a newer client; left untouched: " + safe, NotificationManager.Severity.ERROR);
                    return false;
                }
                Files.createDirectories(backupsDir);
                Path backup = backupsDir.resolve(safe + ".lastgood.bak");
                Path backupTmp = Files.createTempFile(backupsDir, safe + "-", ".tmp");
                try {
                    Files.copy(target, backupTmp, StandardCopyOption.REPLACE_EXISTING);
                    moveReplace(backupTmp, backup);
                } finally {
                    Files.deleteIfExists(backupTmp);
                }
            }
            tmp = Files.createTempFile(configsDir, safe + "-", ".tmp");
            Files.write(tmp, payload);
            moveReplace(tmp, target);
            return true;
        } catch (IOException e) {
            notificationManager.push("Config Save Error", e.getMessage(), NotificationManager.Severity.ERROR);
            return false;
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isSupportedConfig(Path path) {
        try {
            JsonObject root = readConfig(path);
            validateSchema(root);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static JsonObject readConfig(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0L || size > MAX_CONFIG_FILE_BYTES) {
            throw new IOException("Config file empty or exceeds 512 KB safety limit");
        }
        JsonElement parsed = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IllegalStateException("Root element is not a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    private static void validateSchema(JsonObject root) {
        if (root.has("schemaVersion")) {
            int ver = root.get("schemaVersion").getAsInt();
            if (ver > CURRENT_SCHEMA_VERSION) throw new UnsupportedSchemaException(ver);
            if (ver < 1) throw new IllegalArgumentException("Unsupported config schemaVersion: " + ver);
        }
    }

    private static final class UnsupportedSchemaException extends IllegalArgumentException {
        UnsupportedSchemaException(int version) { super("Profile requires newer schema: " + version); }
    }

    public synchronized boolean save(String profileName) {
        if (!flushScheduledSave()) return false;
        String safe = sanitizeProfileName(profileName);
        boolean ok = writeSnapshot(safe, snapshot(safe), true);
        if (ok) activeProfile = safe;
        return ok;
    }

    public synchronized boolean saveActive() { return save(activeProfile); }

    /** Debounce disk I/O while capturing mutable client state on the calling thread. */
    public synchronized void saveActiveAsync() {
        if (unsupportedProfiles.contains(activeProfile)) return;
        if (pendingSaveTask != null) pendingSaveTask.cancel(false);
        pendingProfile = activeProfile;
        pendingPayload = snapshot(activeProfile);
        pendingSaveTask = asyncWriter.schedule(new Runnable() {
            @Override public void run() {
                synchronized (ConfigManager.this) { flushScheduledSave(); }
            }
        }, 220L, TimeUnit.MILLISECONDS);
    }

    private boolean flushScheduledSave() {
        if (pendingSaveTask != null) pendingSaveTask.cancel(false);
        pendingSaveTask = null;
        if (pendingPayload == null) return true;
        // Keep the snapshot on failure so it can be retried rather than silently lost.
        if (!writeSnapshot(pendingProfile, pendingPayload, true)) return false;
        pendingPayload = null;
        pendingProfile = null;
        return true;
    }

    public synchronized void flushPendingSave() {
        if (flushScheduledSave()) save(activeProfile);
    }

    public synchronized boolean load(String profileName, MinecraftAdapter mc) {
        if (!flushScheduledSave()) return false;
        String safe = sanitizeProfileName(profileName);
        Path source = resolveSafeConfigPath(safe);
        if (!Files.exists(source)) return save(safe);
        JsonObject previous = serializeState(activeProfile);
        try {
            JsonObject root = readConfig(source);
            validateSchema(root);
            applyState(root, mc);
            unsupportedProfiles.remove(safe);
            activeProfile = safe;
            return true;
        } catch (UnsupportedSchemaException ex) {
            unsupportedProfiles.add(safe);
            compatibilityEngine.recordConfigWarning(ex.getMessage());
            notificationManager.push("Config requires a newer client", safe, NotificationManager.Severity.WARNING);
            return false;
        } catch (Exception ex) {
            // Applying a malformed profile can fail after changing some settings.
            try { applyState(previous, mc); } catch (Exception ignored) { }
            handleCorruptedConfig(safe, source, ex, mc);
            return false;
        }
    }

    private void handleCorruptedConfig(String safeName, Path brokenFile, Exception cause, MinecraftAdapter mc) {
        JsonObject previous = serializeState(activeProfile);
        try {
            Files.createDirectories(backupsDir);
            Path quarantine = Files.createTempFile(backupsDir, "corrupt-" + safeName + "-", ".json");
            Files.copy(brokenFile, quarantine, StandardCopyOption.REPLACE_EXISTING);
            lastCorruptBackupName = quarantine.getFileName().toString();
        } catch (IOException e) {
            unsupportedProfiles.add(safeName);
            compatibilityEngine.recordConfigWarning("Cannot back up damaged profile: " + safeName);
            notificationManager.push("Config Recovery Error", "Could not back up " + safeName + "; original left untouched.", NotificationManager.Severity.ERROR);
            return;
        }

        compatibilityEngine.recordConfigWarning("Corrupted config '" + safeName + "' backed up (" + cause.getClass().getSimpleName() + ")");
        notificationManager.push("Config Recovery", "Damaged profile backed up: " + safeName, NotificationManager.Severity.WARNING, 5500L);
        Path rollingBackup = backupsDir.resolve(safeName + ".lastgood.bak");
        if (Files.isRegularFile(rollingBackup)) {
            try {
                JsonObject backup = readConfig(rollingBackup);
                validateSchema(backup);
                applyState(backup, mc);
                // Do not replace the last-good backup with the corrupt active file.
                if (writeSnapshot(safeName, snapshot(safeName), false)) {
                    activeProfile = safeName;
                    notificationManager.push("Backup Restored", "Recovered '" + safeName + "' from last valid backup.", NotificationManager.Severity.SUCCESS);
                    return;
                }
            } catch (Exception ignored) { }
        }
        // Keep the original intact if no usable backup exists. Never silently reset it.
        try { applyState(previous, mc); } catch (Exception ignored) { }
        unsupportedProfiles.add(safeName);
        notificationManager.push("Config Recovery", "No usable backup; original profile left untouched.", NotificationManager.Severity.WARNING);
    }

    public synchronized boolean rename(String oldName, String newName) {
        if (!flushScheduledSave()) return false;
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
            if (unsupportedProfiles.remove(safeOld)) unsupportedProfiles.add(safeNew);
            if (safeOld.equals(activeProfile)) {
                activeProfile = safeNew;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean delete(String profileName, MinecraftAdapter mc) {
        if (!flushScheduledSave()) return false;
        String safe = sanitizeProfileName(profileName);
        if ("default".equals(safe)) {
            if (unsupportedProfiles.contains(safe)) return false;
            // Reset default instead of leaving 0 profiles
            moduleManager.resetAll(mc);
            hudManager.resetAll();
            return save("default");
        }
        Path target = resolveSafeConfigPath(safe);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) unsupportedProfiles.remove(safe);
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
        if (!flushScheduledSave()) return false;
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            return false;
        }
        JsonObject previous = serializeState(activeProfile);
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
            validateSchema(el.getAsJsonObject());
            String safeTarget = sanitizeProfileName(targetProfileName);
            if (unsupportedProfiles.contains(safeTarget)) return false;
            applyState(el.getAsJsonObject(), mc);
            if (save(safeTarget)) return true;
            applyState(previous, mc);
            return false;
        } catch (Exception e) {
            try { applyState(previous, mc); } catch (Exception ignored) { }
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
        validateSchema(root);
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
