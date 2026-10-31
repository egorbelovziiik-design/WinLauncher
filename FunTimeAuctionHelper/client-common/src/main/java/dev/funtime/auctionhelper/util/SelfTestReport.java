package dev.funtime.auctionhelper.util;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the machine readable report of the in-game self test.
 *
 * <p>The self test only ever reports steps that really ran inside a real Minecraft client:
 * if a check fails the report says FAIL and contains the error.</p>
 */
public final class SelfTestReport {
    private final Path outFile;
    private final String minecraftVersion;
    private final List<String> checks = new ArrayList<String>();
    private final JsonObject details = new JsonObject();

    public SelfTestReport(Path gameDir, String minecraftVersion) {
        this.outFile = gameDir.resolve("funtimeauctionhelper-selftest-" + minecraftVersion + ".json");
        this.minecraftVersion = minecraftVersion;
        details.addProperty("minecraftVersion", minecraftVersion);
        details.addProperty("javaRuntime", System.getProperty("java.version"));
    }

    public void check(String description) {
        checks.add(description);
    }

    public void detail(String key, Object value) {
        if (value == null) {
            details.addProperty(key, "-");
            return;
        }
        details.addProperty(key, String.valueOf(value));
    }

    public Path getOutFile() {
        return outFile;
    }

    public int getCheckCount() {
        return checks.size();
    }

    public void pass(String note, Runnable stopAction) {
        write("PASS", note, null, stopAction);
    }

    public void fail(Throwable error, Runnable stopAction) {
        write("FAIL", null, error, stopAction);
    }

    private void write(String status, String note, Throwable error, Runnable stopAction) {
        JsonObject report = new JsonObject();
        report.addProperty("status", status);
        report.addProperty("mod", "FunTimeAuctionHelper");
        report.addProperty("modVersion", "1.0.0");
        report.add("details", details);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (int i = 0; i < checks.size(); i++) {
            array.add(checks.get(i));
        }
        report.add("checks", array);
        report.addProperty("checksPassed", checks.size());
        if (note != null) {
            report.addProperty("note", note);
        }
        if (error != null) {
            report.addProperty("error", error.getClass().getName() + ": " + error.getMessage());
        }
        try {
            Json.writeString(outFile, Json.GSON.toJson(report));
            Log.info("Self test report written to " + outFile + " (status " + status + ")");
        } catch (IOException ioError) {
            Log.error("Could not write self test report", ioError);
        } finally {
            if (stopAction != null) {
                stopAction.run();
            }
        }
    }
}
