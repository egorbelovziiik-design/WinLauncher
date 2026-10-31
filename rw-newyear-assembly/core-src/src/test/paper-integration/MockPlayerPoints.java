package net.kinggrief.probe.mockpoints;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only controllable PlayerPoints API; never packaged into the production Core. */
public final class MockPlayerPoints extends JavaPlugin {
    private final FakeAPI api = new FakeAPI();
    public FakeAPI getAPI() { return api; }
    public void mode(String mode) { api.mode = mode; }
    public int balance(UUID uuid) { return api.balances.getOrDefault(uuid, 0); }

    public static final class FakeAPI {
        private final Map<UUID, Integer> balances = new HashMap<>();
        private String mode = "normal";
        public int look(UUID uuid) {
            if ("read-fail".equals(mode)) throw new IllegalStateException("mock read failure");
            return balances.getOrDefault(uuid, 0);
        }
        public boolean set(UUID uuid, int amount) {
            if ("reject".equals(mode)) return false;
            if ("alter-once".equals(mode)) {
                mode = "normal"; balances.put(uuid, amount + 1); return true;
            }
            if ("throw-once".equals(mode)) {
                mode = "normal"; balances.put(uuid, amount); throw new IllegalStateException("mock failure after write");
            }
            balances.put(uuid, amount); return true;
        }
    }
}
