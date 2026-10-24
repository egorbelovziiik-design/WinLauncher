package nexus.core.session;

import nexus.core.Nexus;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.List;

/** Session statistics: playtime, worlds visited, deaths, kills, chat messages. */
public final class SessionTracker {

    public static final class ServerVisit {
        public final String address;
        public final long joinedAt;
        public long leftAt;

        ServerVisit(String address, long joinedAt) {
            this.address = address;
            this.joinedAt = joinedAt;
        }

        public String address() {
            return address;
        }

        public long durationMs() {
            return (leftAt == 0L ? System.currentTimeMillis() : leftAt) - joinedAt;
        }
    }

    private final Nexus nexus;
    private final long startedAt = System.currentTimeMillis();
    private long worldJoinedAt;
    private String currentWorld = "";
    private long totalPlaytimeMs;
    private int deaths;
    private int kills;
    private int messagesSent;
    private int blocksBroken;
    private int blocksPlaced;
    private int configSaves;
    private int commandsExecuted;
    private final List<ServerVisit> history = new ArrayList<ServerVisit>(16);
    private final List<String> deathLog = new ArrayList<String>(32);

    public SessionTracker(Nexus nexus) {
        this.nexus = nexus;
    }

    public void onTick(nexus.core.event.TickEvent event) {
        if (worldJoinedAt > 0L) {
            totalPlaytimeMs += 50L;
        }
    }

    public void onWorldJoin(String address, boolean singleplayer) {
        currentWorld = address == null ? (singleplayer ? "Singleplayer" : "Unknown") : address;
        worldJoinedAt = System.currentTimeMillis();
        history.add(new ServerVisit(currentWorld, worldJoinedAt));
        while (history.size() > 20) {
            history.remove(0);
        }
    }

    public void onWorldLeave() {
        if (!history.isEmpty()) {
            history.get(history.size() - 1).leftAt = System.currentTimeMillis();
        }
        worldJoinedAt = 0L;
        currentWorld = "";
    }

    public void onDeath(String cause) {
        deaths++;
        deathLog.add(System.currentTimeMillis() + " | " + currentWorld + " | " + cause);
        while (deathLog.size() > 50) {
            deathLog.remove(0);
        }
    }

    public void onKill() {
        kills++;
    }

    public void onMessageSent() {
        messagesSent++;
    }

    public void onBlockBroken() {
        blocksBroken++;
    }

    public void onBlockPlaced() {
        blocksPlaced++;
    }

    public void onCommandExecuted() {
        commandsExecuted++;
    }

    public void onConfigSaved() {
        configSaves++;
    }

    // ---------------------------------------------------------------- getters

    public long sessionMs() {
        return System.currentTimeMillis() - startedAt;
    }

    public long worldMs() {
        return worldJoinedAt == 0L ? 0L : System.currentTimeMillis() - worldJoinedAt;
    }

    public long totalPlaytimeMs() {
        return totalPlaytimeMs + worldMs();
    }

    public String currentWorld() {
        return currentWorld;
    }

    public int deaths() {
        return deaths;
    }

    public int kills() {
        return kills;
    }

    public int messagesSent() {
        return messagesSent;
    }

    public int blocksBroken() {
        return blocksBroken;
    }

    public int blocksPlaced() {
        return blocksPlaced;
    }

    public int commandsExecuted() {
        return commandsExecuted;
    }

    public List<ServerVisit> history() {
        return history;
    }

    public List<String> deathLog() {
        return deathLog;
    }

    public String lastServer() {
        return history.isEmpty() ? "" : history.get(history.size() - 1).address();
    }

    public double kdRatio() {
        return deaths == 0 ? kills : kills / (double) deaths;
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("totalPlaytimeMs", totalPlaytimeMs);
        object.set("deaths", deaths);
        object.set("kills", kills);
        object.set("messagesSent", messagesSent);
        object.set("blocksBroken", blocksBroken);
        object.set("blocksPlaced", blocksPlaced);
        object.set("commandsExecuted", commandsExecuted);
        object.set("configSaves", configSaves);
        Json.JsonArray servers = Json.array();
        for (ServerVisit visit : history) {
            servers.add(visit.address());
        }
        object.set("serverHistory", servers);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        totalPlaytimeMs = (long) object.double_("totalPlaytimeMs", 0.0);
        deaths = object.int_("deaths", 0);
        kills = object.int_("kills", 0);
        messagesSent = object.int_("messagesSent", 0);
        blocksBroken = object.int_("blocksBroken", 0);
        blocksPlaced = object.int_("blocksPlaced", 0);
        commandsExecuted = object.int_("commandsExecuted", 0);
        configSaves = object.int_("configSaves", 0);
        Json.JsonArray servers = object.get("serverHistory").isArray()
                ? (Json.JsonArray) object.get("serverHistory") : Json.array();
        for (int i = 0; i < servers.size(); i++) {
            Json entry = servers.get(i);
            if (entry instanceof Json.JsonString) {
                history.add(new ServerVisit(((Json.JsonString) entry).value(), System.currentTimeMillis()));
            }
        }
    }
}
