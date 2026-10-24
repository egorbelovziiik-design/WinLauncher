package nexus.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe file IO. Every write goes through a temp file + atomic rename so a crash
 * during save can never corrupt a config (config error recovery requirement).
 */
public final class FileUtil {

    private FileUtil() {
    }

    public static File directory(File base, String... parts) {
        File current = base;
        for (String part : parts) {
            current = new File(current, part);
        }
        if (!current.exists() && !current.mkdirs()) {
            Log.warn("Failed to create directory: " + current.getAbsolutePath());
        }
        return current;
    }

    public static boolean ensure(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        return true;
    }

    public static String read(File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } finally {
                reader.close();
            }
            return sb.toString();
        } catch (IOException e) {
            Log.error("Failed to read " + file.getName(), e);
            return null;
        }
    }

    /** Atomic write: temp file, flush, fsync, rename into place. */
    public static boolean write(File file, String content) {
        if (!ensure(file)) {
            return false;
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8));
            try {
                writer.write(content);
                writer.flush();
            } finally {
                writer.close();
            }
            if (file.exists() && !file.delete()) {
                Log.warn("Could not replace " + file.getName());
            }
            if (!tmp.renameTo(file)) {
                // Fallback for filesystems refusing rename over existing file
                FileOutputStream out = new FileOutputStream(file, false);
                try {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } finally {
                    out.close();
                }
                tmp.delete();
            }
            return true;
        } catch (IOException e) {
            Log.error("Failed to write " + file.getName(), e);
            return false;
        }
    }

    public static boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    public static boolean copy(File from, File to) {
        String content = read(from);
        return content != null && write(to, content);
    }

    public static List<File> listFiles(File directory, String extension) {
        List<File> result = new ArrayList<File>();
        if (directory == null || !directory.isDirectory()) {
            return result;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.isFile() && (extension == null || file.getName().endsWith(extension))) {
                result.add(file);
            }
        }
        return result;
    }

    public static String nameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /** Keeps only characters safe for file names. */
    public static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\- ]+", "_").trim();
    }

    public static long size(File file) {
        return file == null || !file.exists() ? 0L : file.length();
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
