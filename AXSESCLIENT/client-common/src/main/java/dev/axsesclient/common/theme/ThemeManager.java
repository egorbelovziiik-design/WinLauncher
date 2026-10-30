package dev.axsesclient.common.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThemeManager {
    private static final long MAX_THEME_FILE_BYTES = 64L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path themesDirectory;
    private final Map<String, AxsesTheme> themes = new LinkedHashMap<String, AxsesTheme>();
    private String activeThemeId = "axses-dark";
    private String previewThemeId = null;
    private ColorBlindMode colorBlindMode = ColorBlindMode.NONE;

    public ThemeManager(Path clientRootDir) {
        this.themesDirectory = clientRootDir.resolve("themes");
        registerBuiltInThemes();
        loadCustomThemesFromDisk();
    }

    private void registerBuiltInThemes() {
        registerBuiltIn(new AxsesTheme("axses-dark", "AXSES DARK", true,
                0xFF070911, 0xFF10131D, 0xFF191D2B, 0xFF9060FF, 0xFF536FFF, 0xFF00D9FF,
                0xFFF7F8FF, 0xFF929BB1, 0xFF39DFA2, 0xFFFFC16A, 0xFFFF5B79, 0.94f, 1.0f, 10, 0.75f));
        registerBuiltIn(new AxsesTheme("axses-purple", "AXSES PURPLE", true,
                0xFF0B0716, 0xFF150E28, 0xFF21163D, 0xFFB36BFF, 0xFF7E42FF, 0xFFE580FF,
                0xFFFAF7FF, 0xFFA596C7, 0xFF39DFA2, 0xFFFFC16A, 0xFFFF5B79, 0.94f, 1.0f, 12, 0.80f));
        registerBuiltIn(new AxsesTheme("axses-blue", "AXSES BLUE", true,
                0xFF060B16, 0xFF0D1629, 0xFF16233E, 0xFF3B82F6, 0xFF2563EB, 0xFF38BDF8,
                0xFFF4F8FF, 0xFF8EA4C8, 0xFF34D399, 0xFFFBBF24, 0xFFFB7185, 0.94f, 1.0f, 10, 0.70f));
        registerBuiltIn(new AxsesTheme("axses-cyber", "AXSES CYBER", true,
                0xFF050A0E, 0xFF0B141C, 0xFF12212E, 0xFF00E5FF, 0xFF00FFA3, 0xFFFFE600,
                0xFFF0FCFF, 0xFF7FA6B8, 0xFF00FFA3, 0xFFFFE600, 0xFFFF3366, 0.92f, 1.15f, 6, 0.85f));
        registerBuiltIn(new AxsesTheme("axses-midnight", "AXSES MIDNIGHT", true,
                0xFF04050A, 0xFF0A0D18, 0xFF12172A, 0xFF6366F1, 0xFF4338CA, 0xFF818CF8,
                0xFFEEF2FF, 0xFF7E88AC, 0xFF39DFA2, 0xFFFFC16A, 0xFFFF5B79, 0.96f, 0.95f, 10, 0.85f));
        registerBuiltIn(new AxsesTheme("axses-minimal", "AXSES MINIMAL", true,
                0xFF0C0D10, 0xFF14161B, 0xFF1E2028, 0xFFE2E8F0, 0xFF94A3B8, 0xFF38BDF8,
                0xFFF8FAFC, 0xFF8B95A5, 0xFF4ADE80, 0xFFFACC15, 0xFFF87171, 0.95f, 1.0f, 8, 0.45f));
        registerBuiltIn(new AxsesTheme("axses-graphite", "AXSES GRAPHITE", true,
                0xFF101216, 0xFF181B21, 0xFF232831, 0xFF7C93FF, 0xFF5A6FD6, 0xFF5EEAD4,
                0xFFF3F4F6, 0xFF9CA3AF, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0.96f, 1.0f, 8, 0.60f));
        registerBuiltIn(new AxsesTheme("axses-neon", "AXSES NEON", true,
                0xFF080612, 0xFF120D24, 0xFF1C1436, 0xFFFF3CAC, 0xFF784BA0, 0xFF00F5D4,
                0xFFFFF7FD, 0xFFB39CC8, 0xFF00F5D4, 0xFFFEE440, 0xFFFF4D6D, 0.92f, 1.2f, 12, 0.90f));
    }

    private void registerBuiltIn(AxsesTheme theme) {
        themes.put(theme.getId(), theme);
    }

    public synchronized void loadCustomThemesFromDisk() {
        try {
            Files.createDirectories(themesDirectory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(themesDirectory, "*.json")) {
                for (Path file : stream) {
                    try {
                        importTheme(file, false);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized List<AxsesTheme> getAllThemes() {
        return Collections.unmodifiableList(new ArrayList<AxsesTheme>(themes.values()));
    }

    public synchronized AxsesTheme getTheme(String id) {
        return themes.get(id);
    }

    public synchronized AxsesTheme getActiveTheme() {
        String target = previewThemeId != null && themes.containsKey(previewThemeId) ? previewThemeId : activeThemeId;
        AxsesTheme t = themes.get(target);
        return t != null ? t : themes.get("axses-dark");
    }

    public synchronized String getActiveThemeId() {
        return activeThemeId;
    }

    public synchronized boolean setActiveTheme(String id) {
        if (id != null && themes.containsKey(id)) {
            this.activeThemeId = id;
            this.previewThemeId = null;
            return true;
        }
        return false;
    }

    public synchronized void setPreviewThemeId(String id) {
        this.previewThemeId = (id != null && themes.containsKey(id)) ? id : null;
    }

    public synchronized String getPreviewThemeId() {
        return previewThemeId;
    }

    public synchronized void cycleNextTheme() {
        List<AxsesTheme> list = getAllThemes();
        int idx = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(activeThemeId)) {
                idx = i;
                break;
            }
        }
        setActiveTheme(list.get((idx + 1) % list.size()).getId());
    }

    public synchronized AxsesTheme createCustomTheme(String name, String baseThemeId) {
        AxsesTheme base = themes.containsKey(baseThemeId) ? themes.get(baseThemeId) : getActiveTheme();
        String id = AxsesTheme.sanitizeId("custom-" + name);
        int suffix = 1;
        while (themes.containsKey(id) && themes.get(id).isBuiltIn()) {
            id = AxsesTheme.sanitizeId("custom-" + name + "-" + (suffix++));
        }
        AxsesTheme created = base.copy(id, name, false);
        themes.put(created.getId(), created);
        saveTheme(created);
        return created;
    }

    public synchronized boolean saveTheme(AxsesTheme theme) {
        if (theme == null) {
            return false;
        }
        themes.put(theme.getId(), theme);
        if (theme.isBuiltIn()) {
            return true;
        }
        try {
            Files.createDirectories(themesDirectory);
            Path target = themesDirectory.resolve(theme.getId() + ".axtheme.json").normalize();
            if (!target.startsWith(themesDirectory.normalize())) {
                throw new SecurityException("Blocked path traversal in theme save");
            }
            Path tmp = themesDirectory.resolve(theme.getId() + ".tmp");
            byte[] bytes = GSON.toJson(theme.toJson()).getBytes(StandardCharsets.UTF_8);
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized Path exportTheme(String themeId, Path targetFile) throws IOException {
        AxsesTheme theme = themes.get(themeId);
        if (theme == null) {
            throw new IllegalArgumentException("Unknown theme: " + themeId);
        }
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        byte[] bytes = GSON.toJson(theme.toJson()).getBytes(StandardCharsets.UTF_8);
        Files.write(targetFile, bytes);
        return targetFile;
    }

    public synchronized AxsesTheme importTheme(Path sourceFile, boolean persistCopy) throws IOException {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Theme file does not exist");
        }
        long size = Files.size(sourceFile);
        if (size <= 0L || size > MAX_THEME_FILE_BYTES) {
            throw new IllegalArgumentException("Theme file size exceeds safety limit (64 KB)");
        }
        String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
        return importThemeFromJsonString(content, persistCopy);
    }

    public synchronized AxsesTheme importThemeFromJsonString(String jsonContent, boolean persistCopy) {
        if (jsonContent == null || jsonContent.length() > MAX_THEME_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid or oversized theme JSON");
        }
        JsonElement root = new JsonParser().parse(jsonContent);
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("Theme JSON root must be an object");
        }
        AxsesTheme imported = AxsesTheme.fromJsonValidated(root.getAsJsonObject(), false);
        // Never allow imported theme to overwrite a built-in ID
        if (themes.containsKey(imported.getId()) && themes.get(imported.getId()).isBuiltIn()) {
            imported = imported.copy("imported-" + imported.getId(), imported.getName() + " (Imported)", false);
        }
        themes.put(imported.getId(), imported);
        if (persistCopy) {
            saveTheme(imported);
        }
        return imported;
    }

    public synchronized void resetThemes() {
        themes.clear();
        registerBuiltInThemes();
        activeThemeId = "axses-dark";
        previewThemeId = null;
        colorBlindMode = ColorBlindMode.NONE;
    }

    public ColorBlindMode getColorBlindMode() {
        return colorBlindMode;
    }

    public void setColorBlindMode(ColorBlindMode mode) {
        this.colorBlindMode = mode == null ? ColorBlindMode.NONE : mode;
    }

    public int filterColor(int argb) {
        return colorBlindMode.transform(argb);
    }
}
