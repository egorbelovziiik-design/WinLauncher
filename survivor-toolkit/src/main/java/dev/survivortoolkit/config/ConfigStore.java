package dev.survivortoolkit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.survivortoolkit.auction.PriceCalculator;
import dev.survivortoolkit.policy.ServerPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Versioned JSON config with bounded inputs and atomic replacement. */
public final class ConfigStore {
    private static final long MAX_FILE_SIZE = 256 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path path;
    private boolean writable = true;
    private String warning = "";
    private ToolkitConfig value = new ToolkitConfig();

    public ConfigStore(Path path) { this.path = path; }
    public ToolkitConfig get() { return value; }
    public String warning() { return warning; }
    public boolean writable() { return writable; }

    public ToolkitConfig load() {
        warning = "";
        writable = true;
        if (!Files.exists(path)) return value = new ToolkitConfig();
        try {
            if (Files.size(path) > MAX_FILE_SIZE) throw new IOException("config too large");
            JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            int schema = object.has("schemaVersion") ? object.get("schemaVersion").getAsInt() : 0;
            if (schema < 0 || schema > ToolkitConfig.CURRENT_SCHEMA) {
                writable = false;
                warning = "future";
                return value = new ToolkitConfig();
            }
            migrate(object, schema);
            ToolkitConfig loaded = GSON.fromJson(object, ToolkitConfig.class);
            value = validate(loaded == null ? new ToolkitConfig() : loaded);
        } catch (RuntimeException | IOException e) {
            // Do not destroy the original invalid file on close or during startup.
            writable = false;
            warning = "invalid";
            value = new ToolkitConfig();
        }
        return value;
    }

    private static void migrate(JsonObject json, int version) {
        if (version == 0) {
            if (json.has("alertRadius") && !json.has("wardenRadius")) json.add("wardenRadius", json.get("alertRadius"));
            json.remove("alertRadius");
            json.addProperty("schemaVersion", 1);
        }
    }

    public static ToolkitConfig validate(ToolkitConfig c) {
        ToolkitConfig d = new ToolkitConfig();
        c.schemaVersion = ToolkitConfig.CURRENT_SCHEMA;
        if (c.wardenRadius < 4 || c.wardenRadius > 48) c.wardenRadius = d.wardenRadius;
        if (c.warningSeconds < 1 || c.warningSeconds > 15) c.warningSeconds = d.warningSeconds;
        if (c.warningColor < 0 || c.warningColor > 0xFFFFFF) c.warningColor = d.warningColor;
        if (!"pling".equals(c.warningSound) && !"bell".equals(c.warningSound) && !"none".equals(c.warningSound)) c.warningSound = d.warningSound;
        if (!valid(c.warningVolume, 0, 1)) c.warningVolume = d.warningVolume;
        if (!valid(c.freecamSpeed, 0.05, 0.8)) c.freecamSpeed = d.freecamSpeed;
        if (!valid(c.freecamSensitivity, 0.25, 2)) c.freecamSensitivity = d.freecamSensitivity;
        if (!valid(c.uiScale, 0.8, 1.25)) c.uiScale = d.uiScale;
        try { PriceCalculator.price(c.priceReference); } catch (RuntimeException e) { c.priceReference = d.priceReference; }
        c.wardenAllowedServers = safeAddresses(c.wardenAllowedServers);
        c.auctionAllowedServers = safeAddresses(c.auctionAllowedServers);
        List<ToolkitConfig.PriceRecord> clean = new ArrayList<>();
        if (c.priceHistory != null) for (ToolkitConfig.PriceRecord r : c.priceHistory) {
            if (clean.size() >= 50) break;
            if (r == null || r.item == null || r.item.length() > 64 || r.count < 1 || r.count > 2304 || r.savedAt < 0) continue;
            try { PriceCalculator.price(r.total); PriceCalculator.price(r.unit); clean.add(r); } catch (RuntimeException ignored) {}
        }
        c.priceHistory = clean;
        return c;
    }

    private static boolean valid(double n, double min, double max) { return Double.isFinite(n) && n >= min && n <= max; }
    private static Set<String> safeAddresses(Set<String> addresses) {
        Set<String> result = new LinkedHashSet<>();
        if (addresses != null) for (String s : addresses) {
            if (result.size() >= 200) break;
            String normalized = ServerPolicy.normalize(s);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    public boolean save() {
        if (!writable) return false;
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temp, GSON.toJson(validate(value)) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException e) {
            warning = "write";
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return false;
        }
    }
}
