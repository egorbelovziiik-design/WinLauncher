package dev.funtime.auctionhelper.adapter;

import dev.funtime.auctionhelper.util.Json;
import dev.funtime.auctionhelper.util.Log;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the last N adapter events in memory and appends them to a diagnostics file when a GUI
 * cannot be understood, so the player can report a broken layout instead of guessing.
 */
public final class AdapterDiagnostics {
    private static final int CAP = 200;

    private final Path file;
    private final List<String> lines = new ArrayList<String>();
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    public AdapterDiagnostics(Path directory) {
        this.file = directory == null ? null : directory.resolve("adapter-diagnostics.log");
    }

    public void record(String event) {
        String line = format.format(new Date()) + " | " + event;
        lines.add(line);
        while (lines.size() > CAP) {
            lines.remove(0);
        }
        Log.info("[adapter] " + event);
    }

    public List<String> recent() {
        return new ArrayList<String>(lines);
    }

    /** Appends a full snapshot description, used when a GUI changes and parsing fails. */
    public void recordUnknownScreen(String title, String screenClass, int nonEmptySlots,
                                    List<String> sampleLines) {
        StringBuilder builder = new StringBuilder();
        builder.append("UNKNOWN SCREEN title=\"").append(title).append("\" class=").append(screenClass)
                .append(" nonEmptySlots=").append(nonEmptySlots);
        if (sampleLines != null) {
            for (int i = 0; i < sampleLines.size() && i < 6; i++) {
                builder.append(" | ").append(sampleLines.get(i));
            }
        }
        record(builder.toString());
        flush();
    }

    public void flush() {
        if (file == null) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                builder.append(lines.get(i)).append('\n');
            }
            Json.writeString(file, builder.toString());
        } catch (IOException error) {
            Log.warn("Could not write adapter diagnostics: " + error.getMessage());
        }
    }
}
