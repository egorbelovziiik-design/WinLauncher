package net.kinggrief.core;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only diagnostics: never rename, remove or replace the administrator's files. */
final class CoreInstallDiagnostics {
    private static final long MAX_DESCRIPTOR_BYTES = 64 * 1024;
    private static final String PLUGIN_NAME = "KingGriefCore";

    private CoreInstallDiagnostics() { }

    static void report(KingGriefCore plugin, File runtimeJar) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File installedJar = installedArchive(pluginsDir, runtimeJar);
        try {
            plugin.getLogger().info("[Install] Active " + PLUGIN_NAME + " v" + plugin.getDescription().getVersion()
                    + "; JAR=" + installedJar.getName() + "; SHA-256=" + sha256(installedJar));
            if (!sameFile(installedJar, runtimeJar)) {
                // Paper may remap plugins to a cache. The first checksum is the
                // administrator's original archive, not the remapped copy.
                plugin.getLogger().info("[Install] Paper runtime copy=" + runtimeJar.getPath()
                        + "; SHA-256=" + sha256(runtimeJar));
            }
        } catch (Exception error) {
            plugin.getLogger().warning("[Install] Could not read the loaded JAR/checksum: " + error.getMessage());
        }
        List<String> duplicates = findOtherCoreJars(pluginsDir, installedJar);
        if (!duplicates.isEmpty()) {
            plugin.getLogger().severe("[Install] Other KingGriefCore JARs in plugins/: " + String.join(", ", duplicates));
            plugin.getLogger().severe("[Install] Stop the server, back up its files and leave exactly one KingGriefCore JAR in plugins/. Do not delete its data folder.");
        }
    }

    private static File installedArchive(File pluginsDir, File runtimeJar) {
        // The Paper remapper normally preserves the basename in .paper-remapped/.
        // Only attribute it to that file when its descriptor actually names Core.
        File candidate = new File(pluginsDir, runtimeJar.getName());
        if (candidate.isFile() && (sameFile(candidate, runtimeJar) || isCoreArchive(candidate))) return candidate;
        return runtimeJar;
    }

    static List<String> findOtherCoreJars(File pluginsDir, File installedJar) {
        List<String> result = new ArrayList<String>();
        File[] files = pluginsDir.listFiles();
        if (files == null) return result;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                    || samePath(file, installedJar)) continue;
            if (isCoreArchive(file)) result.add(file.getName());
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static boolean isCoreArchive(File file) {
        try (ZipFile jar = new ZipFile(file)) {
            for (String descriptorName : new String[]{"plugin.yml", "paper-plugin.yml"}) {
                ZipEntry descriptor = jar.getEntry(descriptorName);
                // Do not parse arbitrarily large or unknown-size descriptors from
                // unrelated archives on the server.
                if (descriptor == null || descriptor.isDirectory() || descriptor.getSize() < 0
                        || descriptor.getSize() > MAX_DESCRIPTOR_BYTES) continue;
                try (InputStream input = jar.getInputStream(descriptor);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    String name = YamlConfiguration.loadConfiguration(reader).getString("name", "");
                    if (PLUGIN_NAME.equalsIgnoreCase(name.trim())) return true;
                }
            }
        } catch (Exception ignored) {
            // An unrelated, corrupt or unreadable archive is not evidence of a
            // duplicate Core. Paper reports invalid plugins separately.
        }
        return false;
    }

    private static boolean samePath(File first, File second) {
        return first.toPath().toAbsolutePath().normalize().equals(second.toPath().toAbsolutePath().normalize());
    }

    private static boolean sameFile(File first, File second) {
        try { return Files.isSameFile(first.toPath(), second.toPath()); }
        catch (Exception ignored) { return samePath(first, second); }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }
}
