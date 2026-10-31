package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
    static final String TEMPLATE_FILE = "spawn-v71-template.zip";
    static final String DONE_FILE = ".spawn-v70-template-installed";
    private static final String FALLBACK_DONE_FILE = ".spawn-v722-template-installed";
    private static final String ACTIVE_FILE = ".spawn-v722-active-world";
    private static final String OWNER_FILE = ".kinggrief_spawn_owner";
    private static String activeOwnerToken;
    static final String PENDING_FILE = ".spawn-v72-rebuild-pending";
    private static final String JOURNAL_FILE = ".spawn-v72-swap-journal";
    private static final String EMBEDDED_TEMPLATE = "spawn_v71_template.zip";
    private static final java.util.concurrent.atomic.AtomicBoolean REQUEST_BUSY = new java.util.concurrent.atomic.AtomicBoolean();
    private static final String PREBUILT_MARKER = ".kinggrief_prebuilt_v70";
    private static final String SPAWN_MARKER = ".spawn_v70_village_ok";
    private static final int MAX_TEMPLATE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 512;

    private PreparedSpawnInstaller() {}

    enum Result { READY, NOT_READY, STOP_SERVER }

    /** Validate the prepared world off-thread, then ask for a full restart.
     * Never clear millions of blocks or generate hundreds of chunks in a tick. */
    static void requestRebuild(final KingGriefCore plugin, final CommandSender sender) {
        World world = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
        if (world != null && !Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).equals(world)) {
            sender.sendMessage(ColorUtil.c("&cspawn_world является основным миром; автоматически заменять его нельзя."));
            return;
        }
        if (!REQUEST_BUSY.compareAndSet(false, true)) {
            sender.sendMessage(ColorUtil.c("&eПроверка шаблона уже выполняется. Дождитесь результата."));
            return;
        }
        sender.sendMessage(ColorUtil.c("&eПроверяю готовый спавн без перестройки блоков. Дождитесь подтверждения."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                String message;
                Path stage = null;
                try {
                    Path data = plugin.getDataFolder().toPath();
                    Path pending = data.resolve(PENDING_FILE);
                    if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) {
                        validatePending(pending);
                        message = "&eПерестройка уже запланирована. Остановите и запустите сервер заново.";
                    } else {
                        byte[] bytes = readPreferredTemplate(plugin, data.resolve(TEMPLATE_FILE));
                        stage = Files.createTempDirectory(data, ".spawn-check-");
                        extractVerifiedTemplate(bytes, stage);
                        if (!plugin.isEnabled()) throw new IOException("Plugin is stopping");
                        writeAtomicMarker(data, pending, "v71\n");
                        message = "&aПерестройка запланирована. Полностью перезапустите сервер: прежний мир будет сохранён в spawn_world_backup_*.";
                    }
                } catch (Exception error) {
                    plugin.getLogger().warning("[SpawnInstall] Rebuild request refused: " + error.getMessage());
                    message = "&cПерестройка не запланирована. Проверьте шаблон и журнал сервера.";
                } finally {
                    deleteStagingDirectory(stage);
                    REQUEST_BUSY.set(false);
                }
                final String reply = message;
                plugin.getLogger().info("[SpawnInstall] " + org.bukkit.ChatColor.stripColor(ColorUtil.c(reply)));
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override public void run() {
                        if (!(sender instanceof Player) || ((Player)sender).isOnline()) sender.sendMessage(ColorUtil.c(reply));
                    }
                });
            }
        });
    }

    static void cancelRebuild(KingGriefCore plugin, CommandSender sender) {
        if (REQUEST_BUSY.get()) {
            sender.sendMessage(ColorUtil.c("&eПроверка ещё идёт; повторите отмену после её завершения."));
            return;
        }
        Path pending = plugin.getDataFolder().toPath().resolve(PENDING_FILE);
        try {
            if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) {
                sender.sendMessage(ColorUtil.c("&eПерестройка не запланирована."));
                return;
            }
            validatePending(pending);
            Files.delete(pending);
            sender.sendMessage(ColorUtil.c("&aЗапланированная перестройка отменена; мир не изменён."));
        } catch (IOException error) {
            plugin.getLogger().warning("[SpawnInstall] Could not cancel rebuild safely: " + error.getMessage());
            sender.sendMessage(ColorUtil.c("&cНе удалось безопасно отменить перестройку; проверьте журнал."));
        }
    }

    static Result initialize(KingGriefCore plugin, SpawnEngine engine) {
        try {
            loadActiveSelection(plugin);
            if (recoverInterruptedSwap(plugin)) {
                plugin.getLogger().severe("[SpawnInstall] Interrupted swap recovered. Server stopping; inspect the backup and restart once more.");
                return Result.STOP_SERVER;
            }
            selectIsolatedWorldIfNeeded(plugin);
        } catch (Exception error) {
            plugin.getLogger().severe("[SpawnInstall] Could not safely select/recover the spawn world: " + error.getMessage());
            return Result.STOP_SERVER;
        }
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
            try {
                if (swap.pending != null) Files.delete(swap.pending);
                Files.delete(swap.journal);
                plugin.getLogger().info("[SpawnInstall] Prepared v70 spawn installed and initialized; no rebuild command needed.");
                if (swap.backup != null) plugin.getLogger().info("[SpawnInstall] Previous spawn saved intact at: " + swap.backup);
                return Result.READY;
            } catch (IOException error) {
                plugin.getLogger().severe("[SpawnInstall] Could not complete swap metadata: " + error.getMessage());
            }
        }

        // The old directory is kept until successful load. On failure, quarantine
        // the replacement, restore the original and stop rather than serving an
        // uncertain world while operators diagnose the failed installation.
        rollbackAfterLoad(plugin, swap);
        return Result.STOP_SERVER;
    }

    /** Restore the selected isolated world before consulting the swap journal. */
    private static void loadActiveSelection(KingGriefCore plugin) throws IOException {
        VoidSpawnWorldGenerator.setSpawnWorldName(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
        activeOwnerToken = null;
        Path selection = plugin.getDataFolder().toPath().resolve(ACTIVE_FILE);
        if (!Files.exists(selection, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(selection, LinkOption.NOFOLLOW_LINKS) || Files.size(selection) > 160)
            throw new IOException("Unsafe active spawn selection");
        String[] parts = new String(Files.readAllBytes(selection), StandardCharsets.US_ASCII).split("\n", -1);
        if (parts.length != 4 || !"v1".equals(parts[0]) || !isFallbackName(parts[1])
                || !isOwnerToken(parts[2]) || !parts[3].isEmpty())
            throw new IOException("Invalid active spawn selection; existing worlds were not changed");
        VoidSpawnWorldGenerator.setSpawnWorldName(parts[1]);
        activeOwnerToken = parts[2];
    }

    private static boolean isFallbackName(String name) {
        return name != null && name.matches("kinggrief_spawn(?:_[2-9]|_[1-9][0-9]{1,3})?");
    }

    private static boolean isOwnerToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /** An old unknown/empty/linked world is never loaded, decorated or renamed. */
    private static void selectIsolatedWorldIfNeeded(KingGriefCore plugin) throws IOException {
        Path data = plugin.getDataFolder().toPath();
        // An operator queued an explicit rebuild: honor its original destination.
        if (Files.exists(data.resolve(PENDING_FILE), LinkOption.NOFOLLOW_LINKS)) return;
        Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        String previousName = VoidSpawnWorldGenerator.getSpawnWorldName();
        Path previous = container.resolve(previousName);
        if (activeOwnerToken != null) {
            if (!Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) return;
            verifyFallbackOwnership(previous);
            if (looksLikePreparedWorld(previous)) return;
        } else {
            boolean occupied = Files.exists(previous, LinkOption.NOFOLLOW_LINKS) || Bukkit.getWorld(previousName) != null;
            if (!occupied || looksLikePreparedWorld(previous)) return;
        }
        for (int i = 1; i <= 9999; i++) {
            String name = "kinggrief_spawn" + (i == 1 ? "" : "_" + i);
            Path target = container.resolve(name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Bukkit.getWorld(name) != null) continue;
            String token = UUID.randomUUID().toString();
            Path selection = data.resolve(ACTIVE_FILE);
            String value = "v1\n" + name + "\n" + token + "\n";
            if (activeOwnerToken == null) writeAtomicMarker(data, selection, value);
            else replaceActiveSelection(data, selection, "v1\n" + previousName + "\n" + activeOwnerToken + "\n", value);
            VoidSpawnWorldGenerator.setSpawnWorldName(name);
            activeOwnerToken = token;
            plugin.getLogger().warning("[SpawnInstall] Existing " + previousName + " was preserved unchanged. "
                    + "Installing the prepared spawn separately as " + name + "; use /kgspawn.");
            return;
        }
        throw new IOException("No free isolated spawn world name; no existing world was changed");
    }

    private static void replaceActiveSelection(Path data, Path marker, String expected, String replacement) throws IOException {
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 160
                || !expected.equals(new String(Files.readAllBytes(marker), StandardCharsets.US_ASCII)))
            throw new IOException("Active selection changed while choosing a safe spawn world");
        Path temp = Files.createTempFile(data, ".spawn-marker-", ".tmp");
        try {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temp,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(replacement.getBytes(StandardCharsets.US_ASCII));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    /** Cheap disk-header checks: a marker alone does not prove spawn chunks exist. */
    private static boolean looksLikePreparedWorld(Path world) {
        try {
            Path regionDir = world.resolve("region");
            Path region = regionDir.resolve("r.0.0.mca");
            if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(world.resolve(SPAWN_MARKER), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(regionDir, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(region, LinkOption.NOFOLLOW_LINKS)) return false;
            long size = Files.size(region);
            if (size < 8192) return false;
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(region, java.nio.file.StandardOpenOption.READ)) {
                for (int index : new int[]{0, 96}) {
                    java.nio.ByteBuffer entry = java.nio.ByteBuffer.allocate(4);
                    channel.position(index * 4L);
                    while (entry.hasRemaining()) if (channel.read(entry) <= 0) return false;
                    entry.flip();
                    int location = entry.getInt();
                    long offset = (location >>> 8) & 0xFFFFFFL;
                    int sectors = location & 0xFF;
                    if (offset < 2 || sectors == 0 || (offset + sectors) * 4096L > size) return false;
                }
            }
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private static Path doneMarker(Path data) {
        return data.resolve(activeOwnerToken == null ? DONE_FILE : FALLBACK_DONE_FILE);
    }

    private static void verifyFallbackOwnership(Path world) throws IOException {
        if (activeOwnerToken == null) return;
        Path owner = world.resolve(OWNER_FILE);
        if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS) || Files.size(owner) > 80
                || !activeOwnerToken.equals(new String(Files.readAllBytes(owner), StandardCharsets.US_ASCII).trim()))
            throw new IOException("Selected spawn path is not the previously installed world: " + world);
    }

    private static Swap installIfRequested(KingGriefCore plugin) throws IOException {
        Path data = plugin.getDataFolder().toPath();
        Path template = data.resolve(TEMPLATE_FILE);
        Path pending = data.resolve(PENDING_FILE);
        boolean requested = Files.exists(pending, LinkOption.NOFOLLOW_LINKS);
        if (requested) validatePending(pending);

        Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        Path target = container.resolve(VoidSpawnWorldGenerator.getSpawnWorldName());
        Path done = doneMarker(data);
        Path journal = data.resolve(JOURNAL_FILE);
        if (Files.isSymbolicLink(target)) throw new IOException("spawn_world is a symbolic link");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("spawn_world is not a directory");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) verifyFallbackOwnership(target);
        boolean hadDone = Files.exists(done, LinkOption.NOFOLLOW_LINKS);
        if (hadDone) {
            validateDone(done);
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && !requested) return null;
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(done);
                hadDone = false;
            }
        }
        // Neither a newly bundled JAR nor an external template is permission to
        // replace an existing world. Only an explicit queued request may do so.
        if (!requested && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!hadDone && Files.isRegularFile(target.resolve(PREBUILT_MARKER), LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(target.resolve(SPAWN_MARKER), LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(target.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) writeDoneMarker(data, done);
            return null;
        }
        byte[] bytes = readPreferredTemplate(plugin, template);

        Path stage = Files.createTempDirectory(container, ".kinggrief-spawn-staged-");
        Path backup = null;
        boolean oldMoved = false, newMoved = false, journalWritten = false;
        try {
            extractVerifiedTemplate(bytes, stage);
            if (activeOwnerToken != null) {
                Files.write(stage.resolve(OWNER_FILE), (activeOwnerToken + "\n").getBytes(StandardCharsets.US_ASCII),
                        java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            }
            World loaded = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
            if (loaded != null) {
                if (!loaded.getWorldFolder().getCanonicalFile().toPath().equals(target))
                    throw new IOException("Loaded spawn world is not in the expected directory");
                if (!Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).equals(loaded))
                    throw new IOException("spawn_world is the main server world and cannot safely be replaced automatically");
                if (!loaded.getPlayers().isEmpty()) throw new IOException("Players are still in spawn_world");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                validateExistingWorld(target);
                backup = uniqueSibling(container, "spawn_world_backup_");
            } else if (loaded != null) throw new IOException("Loaded spawn world folder is missing");

            // Journal is forced before the first destructive rename. If power is
            // lost, the next boot restores the original before it can serve players.
            writeSwapJournal(data, journal, stage, backup, hadDone, requested);
            journalWritten = true;
            if (loaded != null && !Bukkit.unloadWorld(loaded, true))
                throw new IOException("Could not unload and save the existing spawn world");
            if (backup != null) {
                validateExistingWorld(target);
                Path worldId = target.resolve("uid.dat");
                if (Files.exists(worldId, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(worldId, LinkOption.NOFOLLOW_LINKS) || Files.size(worldId) != 16)
                        throw new IOException("Existing spawn has an invalid uid.dat");
                    Files.copy(worldId, stage.resolve("uid.dat"));
                }
                Files.move(target, backup);
                oldMoved = true;
            }
            Files.move(stage, target);
            newMoved = true;
            if (!hadDone) writeDoneMarker(data, done);
            return new Swap(target, done, backup, journal, requested ? pending : null, hadDone);
        } catch (Exception failure) {
            try {
                if (newMoved && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(target, stage);
                    newMoved = false;
                }
                if (oldMoved) {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Target appeared during rollback");
                    Files.move(backup, target);
                }
                if (!hadDone && Files.exists(done, LinkOption.NOFOLLOW_LINKS)) {
                    validateDone(done); Files.delete(done);
                }
                // Do not silently retry a failed operator-requested replacement
                // on the next boot after the original has been restored.
                if (requested && Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) {
                    validatePending(pending); Files.delete(pending);
                }
                if (journalWritten) Files.delete(journal);
            } catch (Exception rollbackFailure) {
                throw new UnsafeStateException("Could not restore spawn after installation failure. Original backup: "
                        + backup + "; journal retained; error: " + rollbackFailure.getMessage());
            }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException(failure);
        } finally {
            if (!newMoved) deleteStagingDirectory(stage);
        }
    }

    private static byte[] readPreferredTemplate(KingGriefCore plugin, Path template) throws IOException {
        byte[] bytes;
        if (Files.exists(template, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(template, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Template is not a regular file");
            long length = Files.size(template);
            if (length <= 0 || length > MAX_TEMPLATE_BYTES) throw new IOException("Invalid template size");
            bytes = Files.readAllBytes(template);
            if (bytes.length != length) throw new IOException("Template changed while being read");
        } else {
            try (InputStream in = plugin.getResource(EMBEDDED_TEMPLATE)) {
                if (in == null) throw new IOException("Embedded spawn template is missing");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if ((long)out.size() + n > MAX_TEMPLATE_BYTES) throw new IOException("Embedded template is too large");
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
        }
        if (bytes.length == 0) throw new IOException("Empty spawn template");
        String expected;
        try (InputStream stream = plugin.getResource("spawn_v71_template.sha256")) {
            if (stream == null) throw new IOException("Missing template checksum in Core");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
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

    private static void validateDone(Path done) throws IOException {
        if (!Files.isRegularFile(done, LinkOption.NOFOLLOW_LINKS) || Files.size(done) > 32
                || !"v70".equals(new String(Files.readAllBytes(done), StandardCharsets.US_ASCII).trim()))
            throw new IOException("Invalid install marker");
    }

    private static void validatePending(Path pending) throws IOException {
        if (!Files.isRegularFile(pending, LinkOption.NOFOLLOW_LINKS) || Files.size(pending) > 32
                || !"v71".equals(new String(Files.readAllBytes(pending), StandardCharsets.US_ASCII).trim()))
            throw new IOException("Invalid rebuild request");
    }

    private static void writeAtomicMarker(Path data, Path marker, String text) throws IOException {
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Marker already exists: " + marker);
        Path temp = Files.createTempFile(data, ".spawn-marker-", ".tmp");
        try {
            byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temp,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void writeDoneMarker(Path data, Path done) throws IOException {
        writeAtomicMarker(data, done, "v70\n");
    }

    private static void writeSwapJournal(Path data, Path journal, Path stage, Path backup,
                                         boolean hadDone, boolean requested) throws IOException {
        // Bind recovery to the exact selected world and ownership token.
        String value = "v2\n" + stage.getFileName() + "\n" + (backup == null ? "-" : backup.getFileName())
                + "\n" + (hadDone ? "1" : "0") + "\n" + (requested ? "1" : "0")
                + "\n" + VoidSpawnWorldGenerator.getSpawnWorldName() + "\n"
                + (activeOwnerToken == null ? "-" : activeOwnerToken) + "\n";
        writeAtomicMarker(data, journal, value);
    }

    /** On an interrupted rename, restore the original without guessing at ambiguous files. */
    private static boolean recoverInterruptedSwap(KingGriefCore plugin) throws IOException {
        Path data = plugin.getDataFolder().toPath();
        Path journal = data.resolve(JOURNAL_FILE);
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS) || Files.size(journal) > 512)
            throw new IOException("Unsafe swap journal");
        String[] parts = new String(Files.readAllBytes(journal), StandardCharsets.US_ASCII).split("\n", -1);
        boolean legacy = parts.length == 6 && "v1".equals(parts[0]) && parts[5].isEmpty();
        boolean current = parts.length == 8 && "v2".equals(parts[0]) && parts[7].isEmpty();
        if ((!legacy && !current) || !parts[1].matches("\\.kinggrief-spawn-staged-[A-Za-z0-9_-]+")
                || !("-".equals(parts[2]) || parts[2].matches("spawn_world_backup_[0-9]+_[a-f0-9]{8}"))
                || !("0".equals(parts[3]) || "1".equals(parts[3]))
                || !("0".equals(parts[4]) || "1".equals(parts[4])))
            throw new IOException("Invalid swap journal; manual recovery required");
        String journalWorld = legacy ? VoidSpawnWorldGenerator.SPAWN_WORLD_NAME : parts[5];
        String journalToken = legacy ? "-" : parts[6];
        if (!VoidSpawnWorldGenerator.getSpawnWorldName().equals(journalWorld)
                || !(activeOwnerToken == null ? "-".equals(journalToken) : activeOwnerToken.equals(journalToken)))
            throw new IOException("Swap journal and active world selection differ; refusing to move any world");
        boolean hadDone = "1".equals(parts[3]), requested = "1".equals(parts[4]);
        Path container = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        Path target = container.resolve(VoidSpawnWorldGenerator.getSpawnWorldName());
        Path stage = container.resolve(parts[1]);
        Path backup = "-".equals(parts[2]) ? null : container.resolve(parts[2]);
        if (Files.isSymbolicLink(target) || Files.isSymbolicLink(stage) || (backup != null && Files.isSymbolicLink(backup)))
            throw new IOException("A recovery path is a symbolic link");
        boolean targetExists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        boolean stageExists = Files.exists(stage, LinkOption.NOFOLLOW_LINKS);
        if (targetExists) verifyFallbackOwnership(target);
        if (stageExists) verifyFallbackOwnership(stage);
        if (backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) verifyFallbackOwnership(backup);
        World loaded = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
        if (loaded != null) {
            if (!loaded.getWorldFolder().getCanonicalFile().toPath().equals(target)
                    || (!Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).equals(loaded))
                    || !loaded.getPlayers().isEmpty() || !Bukkit.unloadWorld(loaded, false))
                throw new IOException("Could not unload spawn_world for recovery");
        }
        if (backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            validateExistingWorld(backup);
            if (targetExists) {
                if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe replacement path");
                Path failed = uniqueSibling(container, "spawn_world_failed_");
                Files.move(target, failed);
                plugin.getLogger().warning("[SpawnInstall] Interrupted replacement quarantined at: " + failed);
            }
            Files.move(backup, target);
        } else if (backup != null) {
            // Backup absent + stage present means the original was never moved.
            // Any other state is ambiguous (including a crash during rollback).
            if (!targetExists || !stageExists) throw new IOException("Original backup missing; manual recovery required: " + backup);
            validateExistingWorld(target);
        } else if (targetExists) {
            if (stageExists || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Unexpected target during fresh install recovery");
            Path failed = uniqueSibling(container, "spawn_world_failed_");
            Files.move(target, failed);
            plugin.getLogger().warning("[SpawnInstall] Interrupted fresh world quarantined at: " + failed);
        }
        Path done = doneMarker(data);
        if (hadDone) {
            if (Files.exists(done, LinkOption.NOFOLLOW_LINKS)) validateDone(done);
            else writeDoneMarker(data, done);
        } else if (Files.exists(done, LinkOption.NOFOLLOW_LINKS)) { validateDone(done); Files.delete(done); }
        Path pending = data.resolve(PENDING_FILE);
        if (requested && Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) { validatePending(pending); Files.delete(pending); }
        Files.delete(journal);
        if (stageExists) deleteStagingDirectory(stage);
        plugin.getLogger().warning("[SpawnInstall] Original world restored after an interrupted swap.");
        return true;
    }

    private static boolean rollbackAfterLoad(KingGriefCore plugin, Swap swap) {
        try {
            if (Files.exists(swap.target, LinkOption.NOFOLLOW_LINKS)) verifyFallbackOwnership(swap.target);
            if (swap.backup != null && Files.exists(swap.backup, LinkOption.NOFOLLOW_LINKS)) verifyFallbackOwnership(swap.backup);
            World world = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
            if (world != null && (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false)))
                throw new IOException("Could not unload the failed replacement");
            if (Files.exists(swap.target, LinkOption.NOFOLLOW_LINKS)) {
                Path failed = uniqueSibling(swap.target.getParent(), "spawn_world_failed_");
                Files.move(swap.target, failed);
                plugin.getLogger().warning("[SpawnInstall] Failed replacement saved at: " + failed);
            }
            if (swap.backup != null) Files.move(swap.backup, swap.target);
            if (!swap.hadDone && Files.exists(swap.done, LinkOption.NOFOLLOW_LINKS)) {
                validateDone(swap.done); Files.delete(swap.done);
            }
            if (swap.pending != null && Files.exists(swap.pending, LinkOption.NOFOLLOW_LINKS)) {
                validatePending(swap.pending); Files.delete(swap.pending);
            }
            Files.delete(swap.journal);
            plugin.getLogger().severe("[SpawnInstall] Prepared spawn failed to initialize; the previous world was restored.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[SpawnInstall] Automatic rollback failed. Previous world backup: "
                    + swap.backup + "; journal retained; error: " + e.getMessage());
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
        final Path journal;
        final Path pending;
        final boolean hadDone;
        Swap(Path target, Path done, Path backup, Path journal, Path pending, boolean hadDone) {
            this.target = target;
            this.done = done;
            this.backup = backup;
            this.journal = journal;
            this.pending = pending;
            this.hadDone = hadDone;
        }
    }

    private static final class UnsafeStateException extends IOException {
        UnsafeStateException(String message) { super(message); }
    }
}
