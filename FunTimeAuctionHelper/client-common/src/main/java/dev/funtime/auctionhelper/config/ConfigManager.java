package dev.funtime.auctionhelper.config;

import dev.funtime.auctionhelper.util.Json;
import dev.funtime.auctionhelper.util.Log;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Create / save / load / rename / delete / import / export / backup / restore of named
 * configuration profiles.
 *
 * <p>Loading always runs {@link HelperConfig#applySafetyReset()}, so a config file that was
 * saved while automation was on can never re-arm trading on start up.</p>
 */
public final class ConfigManager {
    private final Path configDir;
    private final Path profilesDir;
    private final Path backupDir;
    private HelperConfig config;
    private String activeProfile = "default";
    private String lastError = null;

    public ConfigManager(Path configDir) {
        this.configDir = configDir;
        this.profilesDir = configDir.resolve("profiles");
        this.backupDir = configDir.resolve("backups");
        this.config = HelperConfig.createDefault();
    }

    public Path getConfigDir() {
        return configDir;
    }

    public Path getProfilesDir() {
        return profilesDir;
    }

    public HelperConfig getConfig() {
        return config;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public String getLastError() {
        return lastError;
    }

    private Path mainFile() {
        return configDir.resolve("funtime-auction-helper.json");
    }

    private Path profileFile(String name) {
        return profilesDir.resolve(sanitize(name) + ".json");
    }

    /** Loads the main config (falls back to defaults and keeps a backup of the broken file). */
    public HelperConfig load() {
        HelperConfig loaded = Json.readJson(mainFile(), HelperConfig.class, null);
        if (loaded == null) {
            config = HelperConfig.createDefault();
            save();
            return config;
        }
        if (loaded.automation == null) {
            loaded.automation = new HelperConfig.Automation();
        }
        loaded.applySafetyReset();
        config = loaded;
        return config;
    }

    public void save() {
        try {
            if (Files.isRegularFile(mainFile())) {
                Json.backup(mainFile(), backupDir, 10);
            }
            Json.writeJson(mainFile(), config);
            lastError = null;
        } catch (IOException error) {
            lastError = "Save failed: " + error.getMessage();
            Log.error("Could not save config", error);
        }
    }

    /** Saves the current configuration under {@code name} (Create / Save as). */
    public boolean saveAs(String name) {
        if (name == null || name.trim().isEmpty()) {
            lastError = "Profile name must not be empty";
            return false;
        }
        try {
            config.profileName = name.trim();
            Json.writeJson(profileFile(name), config);
            save();
            activeProfile = name.trim();
            lastError = null;
            return true;
        } catch (IOException error) {
            lastError = "Save as failed: " + error.getMessage();
            return false;
        }
    }

    public boolean loadProfile(String name) {
        HelperConfig loaded = Json.readJson(profileFile(name), HelperConfig.class, null);
        if (loaded == null) {
            lastError = "Profile not found: " + name;
            return false;
        }
        loaded.applySafetyReset();
        config = loaded;
        activeProfile = name;
        save();
        lastError = null;
        return true;
    }

    public boolean renameProfile(String from, String to) {
        if (to == null || to.trim().isEmpty()) {
            lastError = "New name must not be empty";
            return false;
        }
        try {
            Path source = profileFile(from);
            if (!Files.isRegularFile(source)) {
                lastError = "Profile not found: " + from;
                return false;
            }
            Files.move(source, profileFile(to));
            if (activeProfile.equals(from)) {
                activeProfile = to.trim();
                config.profileName = to.trim();
                save();
            }
            return true;
        } catch (IOException error) {
            lastError = "Rename failed: " + error.getMessage();
            return false;
        }
    }

    public boolean deleteProfile(String name) {
        try {
            boolean deleted = Files.deleteIfExists(profileFile(name));
            if (deleted && activeProfile.equals(name)) {
                activeProfile = "default";
            }
            return deleted;
        } catch (IOException error) {
            lastError = "Delete failed: " + error.getMessage();
            return false;
        }
    }

    public List<String> listProfiles() {
        List<String> names = new ArrayList<String>();
        if (!Files.isDirectory(profilesDir)) {
            return names;
        }
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(profilesDir, "*.json");
            for (Path path : stream) {
                String file = path.getFileName().toString();
                names.add(file.substring(0, file.length() - ".json".length()));
            }
        } catch (IOException error) {
            Log.warn("Could not list profiles: " + error.getMessage());
        } finally {
            Json.closeQuietly(stream);
        }
        Collections.sort(names);
        return names;
    }

    /** Exports the current configuration to an arbitrary path chosen by the player. */
    public boolean exportTo(Path target) {
        try {
            Json.writeJson(target, config);
            lastError = null;
            return true;
        } catch (IOException error) {
            lastError = "Export failed: " + error.getMessage();
            return false;
        }
    }

    /** Imports a configuration file. Automation switches are force-disabled afterwards. */
    public boolean importFrom(Path source) {
        HelperConfig imported = Json.readJson(source, HelperConfig.class, null);
        if (imported == null) {
            lastError = "Import failed: file is not a valid configuration";
            return false;
        }
        if (imported.automation == null) {
            imported.automation = new HelperConfig.Automation();
        }
        imported.applySafetyReset();
        config = imported;
        save();
        lastError = null;
        return true;
    }

    public boolean backupNow() {
        if (!Files.isRegularFile(mainFile())) {
            save();
        }
        Json.backup(mainFile(), backupDir, 20);
        return true;
    }

    public List<Path> listBackups() {
        List<Path> backups = new ArrayList<Path>();
        if (!Files.isDirectory(backupDir)) {
            return backups;
        }
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(backupDir, "*.json");
            for (Path path : stream) {
                backups.add(path);
            }
        } catch (IOException error) {
            Log.warn("Could not list backups: " + error.getMessage());
        } finally {
            Json.closeQuietly(stream);
        }
        Collections.sort(backups, new java.util.Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return a.getFileName().toString().compareTo(b.getFileName().toString());
            }
        });
        return backups;
    }

    public boolean restore(Path backupFile) {
        HelperConfig restored = Json.readJson(backupFile, HelperConfig.class, null);
        if (restored == null) {
            lastError = "Restore failed: backup is not readable";
            return false;
        }
        if (restored.automation == null) {
            restored.automation = new HelperConfig.Automation();
        }
        restored.applySafetyReset();
        config = restored;
        save();
        return true;
    }

    private static String sanitize(String name) {
        String value = name == null ? "default" : name.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length() && builder.length() < 48; i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') {
                builder.append(c);
            }
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? "default" : result;
    }
}
