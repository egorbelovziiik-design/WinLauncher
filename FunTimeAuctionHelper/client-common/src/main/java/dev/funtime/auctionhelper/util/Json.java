package dev.funtime.auctionhelper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small JSON helper: one shared Gson instance plus crash safe (atomic) file IO.
 *
 * <p>Every write goes to a temporary file first and is then moved over the target,
 * so a crash or a full disk can never leave a half written config behind.</p>
 */
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static final Gson COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static void writeJson(Path target, Object value) throws IOException {
        writeString(target, GSON.toJson(value));
    }

    /**
     * Atomically writes {@code content} to {@code target}. Creates parent directories.
     */
    public static void writeString(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();
        } finally {
            closeQuietly(writer);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String readString(Path source, String fallback) {
        if (!Files.isRegularFile(source)) {
            return fallback;
        }
        try {
            byte[] bytes = Files.readAllBytes(source);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException error) {
            Log.warn("Could not read " + source + ": " + error.getMessage());
            return fallback;
        }
    }

    public static <T> T readJson(Path source, Class<T> type, T fallback) {
        if (!Files.isRegularFile(source)) {
            return fallback;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(source), StandardCharsets.UTF_8);
            T parsed = GSON.fromJson(reader, type);
            return parsed == null ? fallback : parsed;
        } catch (IOException | JsonParseException error) {
            Log.warn("Could not parse " + source + ": " + error.getMessage());
            return fallback;
        } finally {
            closeQuietly(reader);
        }
    }

    /** Copies {@code source} to {@code backupDir} keeping at most {@code keep} newest backups. */
    public static void backup(Path source, Path backupDir, int keep) {
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            Files.createDirectories(backupDir);
            String name = source.getFileName().toString();
            String stamp = String.valueOf(System.currentTimeMillis());
            int dot = name.lastIndexOf('.');
            String backupName = dot > 0
                    ? name.substring(0, dot) + "-" + stamp + name.substring(dot)
                    : name + "-" + stamp;
            Files.copy(source, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);
            pruneBackups(backupDir, keep);
        } catch (IOException error) {
            Log.warn("Backup failed for " + source + ": " + error.getMessage());
        }
    }

    private static void pruneBackups(Path backupDir, int keep) {
        try {
            java.util.List<Path> files = new java.util.ArrayList<Path>();
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir);
            try {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        files.add(path);
                    }
                }
            } finally {
                stream.close();
            }
            java.util.Collections.sort(files, new java.util.Comparator<Path>() {
                @Override
                public int compare(Path a, Path b) {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException ignored) {
                        return 0;
                    }
                }
            });
            for (int i = Math.max(0, keep); i < files.size(); i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (IOException error) {
            Log.warn("Backup pruning failed: " + error.getMessage());
        }
    }

    public static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // ignore
        }
    }
}
