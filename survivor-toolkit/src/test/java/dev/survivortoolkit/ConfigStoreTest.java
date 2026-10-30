package dev.survivortoolkit;

import dev.survivortoolkit.config.ConfigStore;
import dev.survivortoolkit.config.ToolkitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path dir;
    @Test void savesLoadsAndBoundsData() {
        Path file = dir.resolve("toolkit.json");
        ConfigStore first = new ConfigStore(file);
        first.get().wardenEnabled = true;
        first.get().wardenRadius = 30;
        first.get().freecamSpeed = Double.POSITIVE_INFINITY;
        first.get().wardenAllowedServers.add("  Example.ORG  ");
        assertTrue(first.save());
        ToolkitConfig loaded = new ConfigStore(file).load();
        assertTrue(loaded.wardenEnabled);
        assertEquals(30, loaded.wardenRadius);
        assertEquals(0.25, loaded.freecamSpeed);
        assertTrue(loaded.wardenAllowedServers.contains("example.org"));
    }
    @Test void migrationDoesNotGrantPermission() throws Exception {
        Path file = dir.resolve("old.json");
        Files.writeString(file, "{\"alertRadius\": 17, \"wardenEnabled\": true}");
        ToolkitConfig loaded = new ConfigStore(file).load();
        assertEquals(17, loaded.wardenRadius);
        assertTrue(loaded.wardenAllowedServers.isEmpty());
        assertTrue(loaded.auctionAllowedServers.isEmpty());
    }
    @Test void brokenOrFutureConfigIsNotOverwritten() throws Exception {
        for (String original : new String[]{"{not json", "{\"schemaVersion\":999}"}) {
            Path file = dir.resolve("bad.json");
            Files.writeString(file, original);
            ConfigStore store = new ConfigStore(file);
            store.load();
            assertFalse(store.writable());
            assertFalse(store.save());
            assertEquals(original, Files.readString(file));
        }
    }
    @Test void malformedFieldsAndNullCollectionsFailSafe() throws Exception {
        Path file = dir.resolve("nulls.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"wardenRadius\":-10,\"wardenAllowedServers\":null,\"priceHistory\":null,\"priceReference\":null}");
        ToolkitConfig c = new ConfigStore(file).load();
        assertEquals(24, c.wardenRadius);
        assertEquals("0", c.priceReference);
        assertTrue(c.wardenAllowedServers.isEmpty());
        assertTrue(c.priceHistory.isEmpty());
    }
}
