package dev.survivortoolkit.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Data only. Key bindings are stored by Minecraft itself in options.txt. */
public final class ToolkitConfig {
    public static final int CURRENT_SCHEMA = 1;
    public int schemaVersion = CURRENT_SCHEMA;
    public boolean wardenEnabled = false;
    public int wardenRadius = 24;
    public boolean warningSoundEnabled = true;
    public String warningSound = "pling";
    public double warningVolume = 0.7;
    public int warningSeconds = 4;
    public int warningColor = 0xF3A94F;
    public boolean auctionEnabled = true;
    public String priceReference = "0";
    public boolean buildAssistEnabled = true;
    public boolean showSelectedBlock = true;
    public double freecamSpeed = 0.25;
    public double freecamSensitivity = 1.0;
    public double uiScale = 1.0;
    public Set<String> wardenAllowedServers = new LinkedHashSet<>();
    public Set<String> auctionAllowedServers = new LinkedHashSet<>();
    public List<PriceRecord> priceHistory = new ArrayList<>();

    public static final class PriceRecord {
        public String item = "";
        public String total = "0";
        public int count = 1;
        public String unit = "0";
        public long savedAt;

        public PriceRecord() {}
        public PriceRecord(String item, String total, int count, String unit, long savedAt) {
            this.item = item;
            this.total = total;
            this.count = count;
            this.unit = unit;
            this.savedAt = savedAt;
        }
    }
}
