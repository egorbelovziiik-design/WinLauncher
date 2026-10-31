package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.model.TradeRecord;
import dev.funtime.auctionhelper.util.Csv;
import dev.funtime.auctionhelper.util.Json;
import dev.funtime.auctionhelper.util.Log;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Journal of every trade operation.
 *
 * <p>Totals are computed from {@link TradeRecord.Result#CONFIRMED} entries only - a pending
 * or unverified operation never appears as money spent or earned.</p>
 */
public final class TradeHistoryStore {
    private static final String CSV_HEADER =
            "operation_id,type,item_id,item_name,quantity,price,timestamp,result,automation_mode,note";

    private final Path file;
    private final List<TradeRecord> records = new ArrayList<TradeRecord>();
    private int cap = 5000;
    private boolean dirty = false;

    public TradeHistoryStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        records.clear();
        if (file == null) {
            return;
        }
        RecordFile stored = Json.readJson(file, RecordFile.class, null);
        if (stored != null && stored.records != null) {
            for (int i = 0; i < stored.records.size(); i++) {
                TradeRecord record = stored.records.get(i);
                if (record != null && record.getOperationId() != null) {
                    records.add(record);
                }
            }
        }
    }

    public synchronized void save() {
        if (!dirty || file == null) {
            return;
        }
        RecordFile wrapper = new RecordFile();
        wrapper.records = records;
        try {
            Json.writeJson(file, wrapper);
            dirty = false;
        } catch (IOException error) {
            Log.error("Could not save trade history", error);
        }
    }

    public synchronized void add(TradeRecord record) {
        if (record == null) {
            return;
        }
        records.add(record);
        if (records.size() > cap) {
            records.remove(0);
        }
        dirty = true;
    }

    public synchronized TradeRecord find(String operationId) {
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).getOperationId().equals(operationId)) {
                return records.get(i);
            }
        }
        return null;
    }

    public synchronized boolean updateResult(String operationId, TradeRecord.Result result, String note,
                                             long timestampIfFinal) {
        TradeRecord record = find(operationId);
        if (record == null) {
            return false;
        }
        record.setResult(result, note);
        if (result != null && result.isFinal() && timestampIfFinal > 0) {
            record.setTimestamp(timestampIfFinal);
        }
        dirty = true;
        return true;
    }

    public synchronized List<TradeRecord> all() {
        List<TradeRecord> copy = new ArrayList<TradeRecord>(records);
        Collections.reverse(copy);
        return copy;
    }

    public synchronized List<TradeRecord> byResult(TradeRecord.Result result) {
        List<TradeRecord> result2 = new ArrayList<TradeRecord>();
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).getResult() == result) {
                result2.add(records.get(i));
            }
        }
        return result2;
    }

    public synchronized int count() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
        dirty = true;
    }

    /** Confirmed-only statistics. Unconfirmed operations are never counted. */
    public synchronized Stats stats() {
        Stats stats = new Stats();
        for (int i = 0; i < records.size(); i++) {
            TradeRecord record = records.get(i);
            if (record.getResult() != TradeRecord.Result.CONFIRMED) {
                if (record.getResult() == TradeRecord.Result.PENDING) {
                    stats.pending++;
                } else if (record.getResult() == TradeRecord.Result.UNVERIFIED) {
                    stats.unverified++;
                }
                continue;
            }
            stats.confirmed++;
            if (record.getType() == TradeRecord.Type.BUY) {
                stats.spent += record.getPrice();
                stats.itemsBought += record.getQuantity();
            } else {
                stats.earned += record.getPrice();
                stats.itemsSold += record.getQuantity();
            }
        }
        stats.net = stats.earned - stats.spent;
        return stats;
    }

    public synchronized double spentSince(long sinceTimestamp) {
        double sum = 0d;
        for (int i = 0; i < records.size(); i++) {
            TradeRecord record = records.get(i);
            if (record.getType() == TradeRecord.Type.BUY
                    && record.getResult() == TradeRecord.Result.CONFIRMED
                    && record.getTimestamp() >= sinceTimestamp) {
                sum += record.getPrice();
            }
        }
        return sum;
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
            List<TradeRecord> copy = all();
            for (int i = 0; i < copy.size(); i++) {
                TradeRecord record = copy.get(i);
                List<String> fields = new ArrayList<String>();
                fields.add(record.getOperationId());
                fields.add(record.getType().name());
                fields.add(record.getItemId());
                fields.add(record.getItemName());
                fields.add(String.valueOf(record.getQuantity()));
                fields.add(String.valueOf(record.getPrice()));
                fields.add(String.valueOf(record.getTimestamp()));
                fields.add(record.getResult().name());
                fields.add(record.getAutomationMode());
                fields.add(record.getNote());
                writer.write(Csv.join(fields));
                writer.write('\n');
            }
            return true;
        } catch (IOException error) {
            Log.error("Trade history export failed", error);
            return false;
        } finally {
            Json.closeQuietly(writer);
        }
    }

    public void setCap(int cap) {
        this.cap = Math.max(100, cap);
    }

    /** Aggregated numbers over confirmed operations. */
    public static final class Stats {
        public int confirmed;
        public int pending;
        public int unverified;
        public int itemsBought;
        public int itemsSold;
        public double spent;
        public double earned;
        public double net;
    }

    public static final class RecordFile {
        public List<TradeRecord> records = new ArrayList<TradeRecord>();
    }
}
