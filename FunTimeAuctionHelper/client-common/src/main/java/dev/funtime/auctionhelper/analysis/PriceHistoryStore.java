package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.model.PriceObservation;
import dev.funtime.auctionhelper.util.Csv;
import dev.funtime.auctionhelper.util.Json;
import dev.funtime.auctionhelper.util.Log;
import dev.funtime.auctionhelper.util.Numbers;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local price history: every offer the player actually saw, kept on disk only.
 * Nothing is uploaded anywhere.
 */
public final class PriceHistoryStore {
    private static final String CSV_HEADER = "timestamp,item_id,item_name,quantity,unit_price,total_price,server_profile";

    private final Path file;
    private final List<PriceObservation> observations = new ArrayList<PriceObservation>();
    private final Map<String, PriceObservation> lastSeen = new HashMap<String, PriceObservation>();
    private int cap = 20000;
    private boolean dirty = false;

    public PriceHistoryStore(Path file) {
        this.file = file;
    }

    public void setCap(int cap) {
        this.cap = Math.max(100, cap);
    }

    public synchronized void load() {
        observations.clear();
        if (file == null) {
            return;
        }
        lastSeen.clear();
        ObservationFile stored = Json.readJson(file, ObservationFile.class, null);
        List<PriceObservation> loaded = stored == null ? null : stored.observations;
        if (loaded != null) {
            for (int i = 0; i < loaded.size(); i++) {
                PriceObservation observation = loaded.get(i);
                if (observation != null) {
                    observations.add(observation);
                }
            }
        }
        rebuildLastSeen();
        trim();
    }

    public synchronized void save() {
        if (!dirty || file == null) {
            return;
        }
        ObservationFile wrapper = new ObservationFile();
        wrapper.observations = observations;
        try {
            Json.writeJson(file, wrapper);
            dirty = false;
        } catch (IOException error) {
            Log.error("Could not save price history", error);
        }
    }

    public synchronized void add(PriceObservation observation) {
        if (observation == null || !observation.isValid()) {
            return;
        }
        observations.add(observation);
        lastSeen.put(observation.getItemId(), observation);
        dirty = true;
        trim();
    }

    public synchronized void addAll(List<PriceObservation> newObservations) {
        if (newObservations == null) {
            return;
        }
        for (int i = 0; i < newObservations.size(); i++) {
            add(newObservations.get(i));
        }
    }

    public synchronized int size() {
        return observations.size();
    }

    public synchronized List<PriceObservation> all() {
        return new ArrayList<PriceObservation>(observations);
    }

    public synchronized PriceObservation lastSeenFor(String itemId) {
        return lastSeen.get(itemId);
    }

    /** Observations of one item inside the observation window (minutes). */
    public synchronized List<PriceObservation> windowFor(String itemId, int windowMinutes, long now) {
        List<PriceObservation> result = new ArrayList<PriceObservation>();
        long cutoff = windowMinutes <= 0 ? Long.MIN_VALUE : now - windowMinutes * 60_000L;
        for (int i = 0; i < observations.size(); i++) {
            PriceObservation observation = observations.get(i);
            if (!observation.getItemId().equals(itemId)) {
                continue;
            }
            if (observation.getTimestamp() >= cutoff) {
                result.add(observation);
            }
        }
        return result;
    }

    public synchronized List<String> knownItems() {
        List<String> items = new ArrayList<String>(lastSeen.keySet());
        Collections.sort(items);
        return items;
    }

    /** Aggregated series for charts: bucket index -> median unit price in that bucket. */
    public synchronized List<double[]> seriesFor(String itemId, int bucketCount, int windowMinutes, long now) {
        List<PriceObservation> window = windowFor(itemId, windowMinutes, now);
        List<double[]> series = new ArrayList<double[]>();
        if (window.isEmpty() || bucketCount <= 0) {
            return series;
        }
        long windowMillis = Math.max(1L, windowMinutes * 60_000L);
        long start = now - windowMillis;
        List<List<Double>> buckets = new ArrayList<List<Double>>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new ArrayList<Double>());
        }
        for (int i = 0; i < window.size(); i++) {
            PriceObservation observation = window.get(i);
            int index = (int) ((observation.getTimestamp() - start) * bucketCount / windowMillis);
            if (index < 0) {
                index = 0;
            }
            if (index >= bucketCount) {
                index = bucketCount - 1;
            }
            buckets.get(index).add(observation.getUnitPrice());
        }
        for (int i = 0; i < buckets.size(); i++) {
            List<Double> bucket = buckets.get(i);
            if (bucket.isEmpty()) {
                continue;
            }
            Collections.sort(bucket);
            series.add(new double[]{i, PriceAnalyzer.percentile(bucket, 0.5), bucket.size()});
        }
        return series;
    }

    public synchronized int cleanup(int retentionDays, long now) {
        long cutoff = now - Math.max(1, retentionDays) * 86_400_000L;
        int before = observations.size();
        for (int i = observations.size() - 1; i >= 0; i--) {
            if (observations.get(i).getTimestamp() < cutoff) {
                observations.remove(i);
            }
        }
        int removed = before - observations.size();
        if (removed > 0) {
            dirty = true;
            rebuildLastSeen();
            trim();
        }
        return removed;
    }

    public synchronized void clear() {
        observations.clear();
        lastSeen.clear();
        dirty = true;
    }

    private void trim() {
        if (observations.size() <= cap) {
            return;
        }
        int excess = observations.size() - cap;
        for (int i = 0; i < excess; i++) {
            observations.remove(0);
        }
        rebuildLastSeen();
    }

    private void rebuildLastSeen() {
        lastSeen.clear();
        for (int i = 0; i < observations.size(); i++) {
            PriceObservation observation = observations.get(i);
            PriceObservation previous = lastSeen.get(observation.getItemId());
            if (previous == null || previous.getTimestamp() <= observation.getTimestamp()) {
                lastSeen.put(observation.getItemId(), observation);
            }
        }
    }

    public boolean exportCsv(Path target) {
        Writer writer = null;
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
            writer.write(CSV_HEADER);
            writer.write('\n');
            List<PriceObservation> copy = all();
            for (int i = 0; i < copy.size(); i++) {
                PriceObservation observation = copy.get(i);
                List<String> fields = new ArrayList<String>();
                fields.add(String.valueOf(observation.getTimestamp()));
                fields.add(observation.getItemId());
                fields.add(observation.getItemName());
                fields.add(String.valueOf(observation.getQuantity()));
                fields.add(String.valueOf(observation.getUnitPrice()));
                fields.add(String.valueOf(observation.getTotalPrice()));
                fields.add(observation.getServerProfile());
                writer.write(Csv.join(fields));
                writer.write('\n');
            }
            return true;
        } catch (IOException error) {
            Log.error("CSV export failed", error);
            return false;
        } finally {
            Json.closeQuietly(writer);
        }
    }

    public int importCsv(Path source) {
        if (!Files.isRegularFile(source)) {
            return 0;
        }
        int imported = 0;
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.trim().isEmpty() || line.startsWith("timestamp")) {
                    continue;
                }
                List<String> fields = Csv.split(line);
                if (fields.size() < 6) {
                    continue;
                }
                double unit = parseDouble(fields.get(4));
                double total = parseDouble(fields.get(5));
                int quantity = (int) parseDouble(fields.get(3));
                long timestamp = (long) parseDouble(fields.get(0));
                if (Double.isNaN(unit) || quantity <= 0) {
                    continue;
                }
                add(new PriceObservation(fields.get(1), fields.get(2), unit, total, quantity, timestamp,
                        fields.size() > 6 ? fields.get(6) : "imported"));
                imported++;
            }
            save();
        } catch (IOException error) {
            Log.error("CSV import failed", error);
        }
        return imported;
    }

    private static double parseDouble(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException error) {
            return Numbers.parseAmount(raw);
        }
    }

    /** Gson wrapper so the file has a stable shape. */
    public static final class ObservationFile {
        public List<PriceObservation> observations = new ArrayList<PriceObservation>();
    }
}
