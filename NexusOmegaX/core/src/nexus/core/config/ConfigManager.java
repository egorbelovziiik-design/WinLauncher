package nexus.core.config;

import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.FileUtil;
import nexus.util.Log;
import nexus.util.json.Json;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Profile based configuration system.
 * <p>
 * Layout inside the game directory:
 * <pre>
 * NexusOmegaX/
 *   configs/
 *     profiles/&lt;name&gt;.json     full profile (modules, hud, theme, colors, fonts, keybinds)
 *     friends.json  waypoints.json  macros.json  themes.json  launcher.json  hud.json
 *     backups/&lt;name&gt;-timestamp.json
 * </pre>
 * Every write is atomic (temp file + rename), every load is validated, corrupt
 * files are quarantined to {@code *.corrupt} and replaced by defaults.
 */
public final class ConfigManager {

    public static final String DEFAULT_PROFILE = "default";
    private static final int MAX_BACKUPS = 12;

    private final Nexus nexus;
    private final File configsDir;
    private final File profilesDir;
    private final File backupsDir;

    private final BooleanSetting autoSave;
    private final BooleanSetting autoLoad;
    private final NumberSetting autoSaveInterval;
    private final BooleanSetting backupOnSave;
    private final BooleanSetting saveOnShutdown;

    private String activeProfile = DEFAULT_PROFILE;
    private boolean dirty;
    private long lastSave = 0L;

    public ConfigManager(Nexus nexus) {
        this.nexus = nexus;
        this.configsDir = FileUtil.directory(nexus.directory(), "configs");
        this.profilesDir = FileUtil.directory(configsDir, "profiles");
        this.backupsDir = FileUtil.directory(configsDir, "backups");

        this.autoSave = new BooleanSetting("Auto Save", "Save the active profile automatically", true);
        this.autoLoad = new BooleanSetting("Auto Load", "Load the active profile on startup", true);
        this.autoSaveInterval = new NumberSetting("Auto Save Interval", "Milliseconds between auto saves",
                60000.0, 10000.0, 600000.0, 10000.0, true, "ms");
        this.backupOnSave = new BooleanSetting("Backup On Save", "Create a timestamped backup before overwriting", true);
        this.saveOnShutdown = new BooleanSetting("Save On Shutdown", "Save everything when the game closes", true);
    }

    public File folder() {
        return configsDir;
    }

    public File profilesFolder() {
        return profilesDir;
    }

    public File backupsFolder() {
        return backupsDir;
    }

    public String activeProfile() {
        return activeProfile;
    }

    // ------------------------------------------------------------- global state

    public boolean autoSaveEnabled() {
        return autoSave.isEnabled();
    }

    public boolean autoLoadEnabled() {
        return autoLoad.isEnabled();
    }

    public long autoSaveIntervalMs() {
        return (long) autoSaveInterval.value();
    }

    public boolean saveOnShutdownEnabled() {
        return saveOnShutdown.isEnabled();
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    // ------------------------------------------------------------------ loading

    /** Loads the persistent state that is shared by all profiles. */
    public void loadStartupConfig() {
        Json.JsonObject settings = readObject(new File(configsDir, "config-settings.json"));
        autoSave.fromJson(settings.get("autosave"));
        autoLoad.fromJson(settings.get("autoload"));
        autoSaveInterval.fromJson(settings.get("autosaveInterval"));
        backupOnSave.fromJson(settings.get("backupOnSave"));
        saveOnShutdown.fromJson(settings.get("saveOnShutdown"));
        activeProfile = settings.string("activeProfile", DEFAULT_PROFILE);

        nexus.friends().fromJson(readObject(new File(configsDir, "friends.json")));
        nexus.waypoints().fromJson(readObject(new File(configsDir, "waypoints.json")));
        nexus.macros().fromJson(readObject(new File(configsDir, "macros.json")));
        nexus.themes().fromJson(readObject(new File(configsDir, "themes.json")));
        nexus.hud().fromJson(readObject(new File(configsDir, "hud.json")));
        nexus.notifications().fromJson(readObject(new File(configsDir, "notifications.json")));
        nexus.session().fromJson(readObject(new File(configsDir, "session.json")));
        nexus.configManager().loadLauncherSettings();

        if (autoLoadEnabled()) {
            loadProfile(activeProfile, true);
        }
        Log.info("Configuration loaded (profile: " + activeProfile + ")");
    }

    public void saveAll(boolean force) {
        if (!force && !dirty) {
            return;
        }
        Json.JsonObject settings = Json.object();
        settings.set("autosave", autoSave.toJson());
        settings.set("autoload", autoLoad.toJson());
        settings.set("autosaveInterval", autoSaveInterval.toJson());
        settings.set("backupOnSave", backupOnSave.toJson());
        settings.set("saveOnShutdown", saveOnShutdown.toJson());
        settings.set("activeProfile", activeProfile);
        writeObject(new File(configsDir, "config-settings.json"), settings);

        writeObject(new File(configsDir, "friends.json"), nexus.friends().toJson());
        writeObject(new File(configsDir, "waypoints.json"), nexus.waypoints().toJson());
        writeObject(new File(configsDir, "macros.json"), nexus.macros().toJson());
        writeObject(new File(configsDir, "themes.json"), nexus.themes().toJson());
        writeObject(new File(configsDir, "hud.json"), nexus.hud().toJson());
        writeObject(new File(configsDir, "notifications.json"), nexus.notifications().toJson());
        writeObject(new File(configsDir, "session.json"), nexus.session().toJson());

        saveProfile(activeProfile);
        dirty = false;
        lastSave = System.currentTimeMillis();
    }

    // ----------------------------------------------------------------- profiles

    public List<String> listProfiles() {
        List<String> names = new ArrayList<String>();
        for (File file : FileUtil.listFiles(profilesDir, ".json")) {
            names.add(FileUtil.nameWithoutExtension(file));
        }
        Collections.sort(names);
        if (!names.contains(DEFAULT_PROFILE)) {
            names.add(0, DEFAULT_PROFILE);
        }
        return names;
    }

    public boolean profileExists(String name) {
        return profileFile(name).exists();
    }

    public File profileFile(String name) {
        return new File(profilesDir, FileUtil.sanitize(name) + ".json");
    }

    public boolean saveProfile(String name) {
        String profileName = name == null || name.trim().isEmpty() ? activeProfile : FileUtil.sanitize(name.trim());
        File file = profileFile(profileName);
        if (file.exists() && backupOnSave.isEnabled()) {
            createBackup(profileName);
        }
        Json.JsonObject root = Json.object();
        root.set("schema", NexusInfo.SCHEMA);
        root.set("client", NexusInfo.VERSION);
        root.set("minecraft", nexus.platform().minecraftVersion());
        root.set("adapter", nexus.platform().id());
        root.set("created", System.currentTimeMillis());
        root.set("name", profileName);
        root.set("modules", nexus.modules().toJson());
        root.set("keybinds", nexus.keybinds().toJson());
        root.set("hud", nexus.hud().layoutToJson());
        root.set("theme", nexus.themes().activeTheme().name());
        root.set("colors", nexus.themes().colorsToJson());
        boolean ok = writeObject(file, root);
        if (ok) {
            activeProfile = profileName;
            Log.info("Profile saved: " + profileName);
            nexus.notifications().success("Config", "Saved '" + profileName + "'");
        } else {
            nexus.notifications().error("Config", "Failed to save '" + profileName + "'");
        }
        return ok;
    }

    public boolean loadProfile(String name, boolean apply) {
        String profileName = name == null || name.trim().isEmpty() ? DEFAULT_PROFILE : FileUtil.sanitize(name.trim());
        File file = profileFile(profileName);
        if (!file.exists()) {
            Log.warn("Profile not found: " + profileName);
            return false;
        }
        Json.JsonObject root = readObject(file);
        if (root.size() == 0) {
            Log.error("Profile is empty or corrupt: " + profileName, null);
            return false;
        }
        int schema = root.int_("schema", 1);
        if (schema != NexusInfo.SCHEMA) {
            migrate(root, schema);
        }
        try {
            nexus.keybinds().fromJson(root.get("keybinds"));
            nexus.modules().fromJson(root.get("modules"));
            nexus.keybinds().syncFromModules(nexus.modules().all());
            if (root.has("hud")) {
                nexus.hud().layoutFromJson(root.get("hud"));
            }
            String themeName = root.string("theme", null);
            if (themeName != null) {
                nexus.themes().setActive(themeName);
            }
            Json colors = root.get("colors");
            if (!colors.isNull()) {
                nexus.themes().colorsFromJson(colors);
            }
        } catch (Throwable throwable) {
            Log.error("Failed to apply profile " + profileName, throwable);
            nexus.notifications().error("Config", "Load failed, kept current settings");
            return false;
        }
        activeProfile = profileName;
        if (apply) {
            Log.info("Profile loaded: " + profileName);
            nexus.notifications().success("Config", "Loaded '" + profileName + "'");
        }
        return true;
    }

    public boolean deleteProfile(String name) {
        if (name == null || name.equalsIgnoreCase(DEFAULT_PROFILE)) {
            return false;
        }
        boolean deleted = FileUtil.delete(profileFile(name));
        if (deleted && name.equals(activeProfile)) {
            activeProfile = DEFAULT_PROFILE;
        }
        return deleted;
    }

    public boolean renameProfile(String oldName, String newName) {
        if (oldName == null || newName == null || newName.trim().isEmpty()) {
            return false;
        }
        File from = profileFile(oldName);
        File to = profileFile(newName.trim());
        if (!from.exists() || to.exists()) {
            return false;
        }
        boolean ok = from.renameTo(to);
        if (ok && oldName.equals(activeProfile)) {
            activeProfile = FileUtil.sanitize(newName.trim());
        }
        return ok;
    }

    public boolean resetProfile(String name) {
        String profileName = name == null ? activeProfile : name;
        boolean deleted = FileUtil.delete(profileFile(profileName));
        for (nexus.core.module.Module module : nexus.modules().all()) {
            for (nexus.core.setting.Setting<?> setting : module.settings()) {
                setting.reset();
            }
            if (module.isEnabled()) {
                module.setEnabled(false, false);
            }
        }
        return deleted;
    }

    // ------------------------------------------------------------------ backups

    public File createBackup(String name) {
        File source = profileFile(name);
        if (!source.exists()) {
            return null;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(backupsDir, FileUtil.sanitize(name) + "-" + stamp + ".json");
        if (FileUtil.copy(source, target)) {
            trimBackups(name);
            return target;
        }
        return null;
    }

    private void trimBackups(String name) {
        List<File> backups = new ArrayList<File>();
        for (File file : FileUtil.listFiles(backupsDir, ".json")) {
            if (file.getName().startsWith(FileUtil.sanitize(name) + "-")) {
                backups.add(file);
            }
        }
        if (backups.size() <= MAX_BACKUPS) {
            return;
        }
        Collections.sort(backups, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i < backups.size() - MAX_BACKUPS; i++) {
            FileUtil.delete(backups.get(i));
        }
    }

    public List<String> listBackups() {
        List<String> names = new ArrayList<String>();
        for (File file : FileUtil.listFiles(backupsDir, ".json")) {
            names.add(file.getName());
        }
        Collections.sort(names);
        return names;
    }

    // ------------------------------------------------------------ import/export

    public boolean exportProfile(String name, File target) {
        File source = profileFile(name);
        if (!source.exists()) {
            boolean saved = saveProfile(name);
            if (!saved) {
                return false;
            }
        }
        return FileUtil.copy(source, target);
    }

    public boolean importProfile(File source, String newName) {
        if (source == null || !source.exists()) {
            return false;
        }
        String content = FileUtil.read(source);
        if (content == null) {
            return false;
        }
        Json parsed = Json.parseLenient(content);
        if (!parsed.isObject()) {
            nexus.notifications().error("Config", "Invalid config file");
            return false;
        }
        String name = newName == null || newName.trim().isEmpty()
                ? FileUtil.nameWithoutExtension(source) : FileUtil.sanitize(newName.trim());
        File target = profileFile(name);
        if (!FileUtil.write(target, Json.pretty(parsed))) {
            return false;
        }
        return loadProfile(name, true);
    }

    // -------------------------------------------------------- launcher settings

    public Json.JsonObject launcherSettings() {
        return launcher;
    }

    private Json.JsonObject launcher = Json.object();

    public void loadLauncherSettings() {
        launcher = readObject(new File(configsDir, "launcher.json"));
        Json.JsonObject defaults = defaultLauncherSettings();
        for (java.util.Map.Entry<String, Json> entry : defaults.members().entrySet()) {
            if (!launcher.has(entry.getKey())) {
                launcher.set(entry.getKey(), entry.getValue());
            }
        }
    }

    public void saveLauncherSettings() {
        writeObject(new File(configsDir, "launcher.json"), launcher);
    }

    public static Json.JsonObject defaultLauncherSettings() {
        Json.JsonObject defaults = Json.object();
        defaults.set("minecraftVersion", "1.21.4");
        defaults.set("minecraftDirectory", "");
        defaults.set("javaPath", "");
        defaults.set("memoryMb", 4096);
        defaults.set("injected", false);
        defaults.set("autoUpdate", true);
        defaults.set("closeLauncherAfterStart", false);
        defaults.set("lastProfile", DEFAULT_PROFILE);
        return defaults;
    }

    // ---------------------------------------------------------------- migration

    /** Upgrades configurations written by older client versions. */
    private void migrate(Json.JsonObject root, int fromSchema) {
        Log.info("Migrating config schema " + fromSchema + " -> " + NexusInfo.SCHEMA);
        if (fromSchema < 2) {
            // v1 stored settings flat inside the module object
            Json.JsonObject modules = root.getObject("modules", Json.object());
            for (java.util.Map.Entry<String, Json> entry : modules.members().entrySet()) {
                if (entry.getValue().isObject()) {
                    Json.JsonObject module = (Json.JsonObject) entry.getValue();
                    Json.JsonObject settings = module.getObject("settings", Json.object());
                    for (java.util.Map.Entry<String, Json> member : new java.util.ArrayList<java.util.Map.Entry<String, Json>>(module.members().entrySet())) {
                        String key = member.getKey();
                        if (!key.equals("enabled") && !key.equals("favorite") && !key.equals("settings")) {
                            settings.set(key, member.getValue());
                        }
                    }
                }
            }
        }
        if (fromSchema < 3 && root.has("hudElements")) {
            root.set("hud", root.get("hudElements"));
        }
        root.set("schema", NexusInfo.SCHEMA);
    }

    // ------------------------------------------------------------------- helpers

    public Json.JsonObject readObject(File file) {
        String content = FileUtil.read(file);
        if (content == null || content.trim().isEmpty()) {
            return Json.object();
        }
        Json parsed = Json.parseLenient(content);
        if (parsed.isObject()) {
            return (Json.JsonObject) parsed;
        }
        Log.error("Corrupt config file: " + file.getName(), null);
        File quarantine = new File(file.getParentFile(), file.getName() + ".corrupt");
        FileUtil.copy(file, quarantine);
        FileUtil.delete(file);
        nexus.notifications().error("Config", "Corrupt file recovered: " + file.getName());
        return Json.object();
    }

    public boolean writeObject(File file, Json value) {
        return FileUtil.write(file, Json.pretty(value));
    }

    public String info() {
        return "profiles=" + listProfiles().size() + ", backups=" + listBackups().size()
                + ", active=" + activeProfile + ", lastSave=" + lastSave;
    }
}
