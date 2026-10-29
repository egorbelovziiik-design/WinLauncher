package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * One-time installation of the prepared spawn shipped in the complete assembly.
 * The template lives in the plugin data directory, never directly in an existing
 * world. A previous world is flushed and moved intact to a sibling backup before
 * the staged replacement is installed. The standalone Core still works without
 * a template and keeps the old, non-destructive startup behavior.
 */
final class PreparedSpawnInstaller {
    static final String TEMPLATE_FILE = "spawn-v66-template.zip";
    static final String DONE_FILE = ".spawn-v66-template-installed";
    private static final String PREBUILT_MARKER = ".kinggrief_prebuilt_v66";
    private static final String SPAWN_MARKER = ".spawn_v66_royal_ok";
    private static final int MAX_TEMPLATE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 512;

    private PreparedSpawnInstaller() {}

    enum Result { READY, NOT_READY, STOP_SERVER }

    static Result initialize(KingGriefCore plugin, SpawnEngine engine) {
        Swap swap;
        try {
            swap = installIfRequested(plugin);
        } catch (UnsafeStateException e) {
            plugin.getLogger().severe("[SpawnInstall] " + e.getMessage());
            // Do not create or modify a world if a failed move could not be reversed.
            return Result.STOP_SERVER;
        } catch (Exception e) {
            plugin.getLogger().severe("[SpawnInstall] Prepared spawn NOT installed; existing world preserved: " + e.getMessage());
            // A failed or damaged installation must not silently fall back to a
            // different world or touch an incomplete/linked existing folder.
            return Result.STOP_SERVER;
        }
        if (swap == null) return engine.initializeSpawnWorldAndBuild(false) ? Result.READY : Result.NOT_READY;

        boolean initialized = false;
        try {
            initialized = engine.initializeSpawnWorldAndBuild(false);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("[SpawnInstall] New spawn initialization failed: " + e.getMessage());
        }
        if (initialized) {
            plugin.getLogger().info("[SpawnInstall] Prepared v66 spawn installed and initialized; no rebuild command needed.");
            if (swap.backup != null) {
                plugin.getLogger().info("[SpawnInstall] Previous spawn saved intact at: " + swap.backup);
            }
            return Result.READY;
        }

        // The old directory is kept until successful load. On failure, quarantine
        // the replacement, restore the original and stop rather than serving an
        // uncertain world while operators diagnose the failed installation.
        rollbackAfterLoad(plugin, swap);
        return Result.STOP_SERVER;
    }

    private static Swap installIfRequested(KingGriefCore plugin) throws IOException {
        Path data = plugin.getDataFolder().toPath();
        Path template = data.resolve(TEMPLATE_FILE);
        if (!Files.exists(template, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(template, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Template is not a regular file: " + template);

        Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        Path target = container.resolve(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
        Path done = data.resolve(DONE_FILE);
        if (Files.isSymbolicLink(target)) throw new IOException("spawn_world is a symbolic link");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("spawn_world is not a directory");
        if (Files.exists(done, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(done, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Install marker is not a regular file");
            if (Files.size(done) > 32) throw new IOException("Invalid install marker size");
            String value = new String(Files.readAllBytes(done), StandardCharsets.UTF_8).trim();
            if (!"v66".equals(value)) throw new IOException("Unrecognized install marker; refusing to overwrite it");
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) return null;
            // The world was deliberately removed: install the template again.
            Files.delete(done);
        }

        byte[] bytes = readVerifiedTemplate(plugin, template);
        if (Files.isRegularFile(target.resolve(PREBUILT_MARKER), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(target.resolve(SPAWN_MARKER), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(target.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            writeDoneMarker(data, done);
            return null;
        }

        Path stage = Files.createTempDirectory(container, ".kinggrief-spawn-staged-");
        Path backup = null;
        boolean oldMoved = false;
        boolean newMoved = false;
        try {
            extractVerifiedTemplate(bytes, stage);
            World loaded = Bukkit.getWorld(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
            if (loaded != null) {
                if (!loaded.getWorldFolder().getCanonicalFile().toPath().equals(target))
                    throw new IOException("Loaded spawn world is not in the expected directory");
                if (Bukkit.getWorlds().get(0).equals(loaded))
                    throw new IOException("spawn_world is the main server world and cannot safely be replaced automatically");
                if (!loaded.getPlayers().isEmpty())
                    throw new IOException("Players are still in spawn_world");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) validateExistingWorld(target);
            else if (loaded != null) throw new IOException("Loaded spawn world folder is missing");

            // Saving during unload flushes Paper's asynchronous chunk writes.
            if (loaded != null && !Bukkit.unloadWorld(loaded, true))
                throw new IOException("Could not unload and save the existing spawn world");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                validateExistingWorld(target);
                // Preserve the existing Bukkit world UUID so player locations and
                // third-party warps referring to this world continue to resolve.
                Path worldId = target.resolve("uid.dat");
                if (Files.exists(worldId, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(worldId, LinkOption.NOFOLLOW_LINKS) || Files.size(worldId) != 16)
                        throw new IOException("Existing spawn has an invalid uid.dat");
                    Files.copy(worldId, stage.resolve("uid.dat"));
                }
                backup = uniqueSibling(container, "spawn_world_backup_");
                Files.move(target, backup);
                oldMoved = true;
            }
            Files.move(stage, target);
            newMoved = true;
            writeDoneMarker(data, done);
            return new Swap(target, done, backup);
        } catch (Exception failure) {
            try {
                if (newMoved && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(target, stage);
                    newMoved = false;
                }
                if (oldMoved) {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Target appeared during rollback");
                    Files.move(backup, target);
                }
            } catch (Exception rollbackFailure) {
                throw new UnsafeStateException("Could not restore spawn after installation failure. "
                        + "Original backup: " + backup + "; error: " + rollbackFailure.getMessage());
            }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException(failure);
        } finally {
            if (!newMoved) deleteStagingDirectory(stage);
        }
    }

    private static byte[] readVerifiedTemplate(KingGriefCore plugin, Path template) throws IOException {
        long length = Files.size(template);
        if (length <= 0 || length > MAX_TEMPLATE_BYTES) throw new IOException("Invalid template size");
        byte[] bytes = Files.readAllBytes(template);
        if (bytes.length != length) throw new IOException("Template changed while being read");
        String expected;
        try (InputStream stream = plugin.getResource("spawn_v66_template.sha256")) {
            if (stream == null) throw new IOException("Missing template checksum in Core");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int b;
            while ((b = stream.read()) != -1) {
                if (out.size() >= 128) throw new IOException("Invalid template checksum resource");
                out.write(b);
            }
            expected = new String(out.toByteArray(), StandardCharsets.US_ASCII).trim();
        }
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(sha256(bytes)))
            throw new IOException("Template checksum mismatch");
        return bytes;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(64);
            for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static void extractVerifiedTemplate(byte[] bytes, Path stage) throws IOException {
        Set<String> names = new HashSet<String>();
        long total = 0;
        int totalEntries = 0;
        boolean hasRegion = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (++totalEntries > MAX_ENTRIES) throw new IOException("Too many template entries");
                if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0)
                    throw new IOException("Unsafe template path: " + name);
                for (String part : name.split("/", -1)) {
                    if (part.isEmpty() || ".".equals(part) || "..".equals(part))
                        throw new IOException("Unsafe template path: " + name);
                }
                if (entry.isDirectory() || !names.add(name)) throw new IOException("Unexpected or duplicate template entry: " + name);
                if (!allowedTemplatePath(name)) throw new IOException("Unexpected template file: " + name);
                Path file = stage.resolve(name).normalize();
                if (!file.startsWith(stage)) throw new IOException("Template path leaves staging directory");
                Files.createDirectories(file.getParent());
                try (java.io.OutputStream out = Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_EXTRACTED_BYTES) throw new IOException("Template exceeds extracted size limit");
                        out.write(buffer, 0, n);
                    }
                }
                if (name.startsWith("region/") && name.endsWith(".mca")) hasRegion = true;
                zip.closeEntry(); // validates entry CRC
            }
        }
        if (!hasRegion || !names.contains("level.dat") || !names.contains(SPAWN_MARKER) || !names.contains(PREBUILT_MARKER))
            throw new IOException("Template is missing required world files");
    }

    private static boolean allowedTemplatePath(String name) {
        if (name.equals("level.dat") || name.equals("paper-world.yml")
                || name.equals(SPAWN_MARKER) || name.equals(PREBUILT_MARKER)) return true;
        return name.matches("data/[a-zA-Z0-9_-]+\\.dat")
                || name.matches("(region|poi)/r\\.-?[0-9]+\\.-?[0-9]+\\.mca");
    }

    private static void validateExistingWorld(Path target) throws IOException {
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(target.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Existing spawn is not a complete, regular world directory");
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !Files.isReadable(dir)) throw new IOException("Unsafe directory in spawn: " + dir);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || !Files.isReadable(file))
                    throw new IOException("Unsafe file in spawn: " + file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeDoneMarker(Path data, Path done) throws IOException {
        Path temp = Files.createTempFile(data, ".spawn-v66-install-", ".tmp");
        try {
            Files.write(temp, "v66\n".getBytes(StandardCharsets.US_ASCII));
            Files.move(temp, done);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean rollbackAfterLoad(KingGriefCore plugin, Swap swap) {
        try {
            World world = Bukkit.getWorld(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
            if (world != null && (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false)))
                throw new IOException("Could not unload the failed replacement");
            if (Files.exists(swap.target, LinkOption.NOFOLLOW_LINKS)) {
                Path failed = uniqueSibling(swap.target.getParent(), "spawn_world_failed_");
                Files.move(swap.target, failed);
                plugin.getLogger().warning("[SpawnInstall] Failed replacement saved at: " + failed);
            }
            if (swap.backup != null) Files.move(swap.backup, swap.target);
            Files.deleteIfExists(swap.done);
            plugin.getLogger().severe("[SpawnInstall] Prepared spawn failed to initialize; the previous world was restored.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[SpawnInstall] Automatic rollback failed. Previous world backup: "
                    + swap.backup + "; error: " + e.getMessage());
            return false;
        }
    }

    private static Path uniqueSibling(Path parent, String prefix) throws IOException {
        for (int i = 0; i < 10; i++) {
            Path path = parent.resolve(prefix + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return path;
        }
        throw new IOException("Could not select a unique backup path");
    }

    private static void deleteStagingDirectory(Path stage) {
        if (stage == null || !Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(stage, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Never delete anything other than our own staging directory.
        }
    }

    private static final class Swap {
        final Path target;
        final Path done;
        final Path backup;
        Swap(Path target, Path done, Path backup) {
            this.target = target;
            this.done = done;
            this.backup = backup;
        }
    }

    private static final class UnsafeStateException extends IOException {
        UnsafeStateException(String message) { super(message); }
    }
}
