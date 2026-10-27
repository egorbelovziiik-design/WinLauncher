package net.kinggrief.connect;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServerNode {

    public final String id;
    public String displayName;
    public String host;
    public int port;
    public String proxyName;
    public Material icon;
    public int slot;
    public boolean maintenance;
    public List<String> lore;

    // Live runtime telemetry (updated via direct Minecraft SLP ping, Proxy PlayerCount, or Cloud Sync)
    public volatile boolean onlineState = false;
    public volatile boolean techWorksRemote = false;
    public volatile int onlinePlayers = 0;
    public volatile int maxPlayers = 100;
    public volatile int pingMs = -1;
    public volatile double tps = 20.0;
    public volatile long lastUpdatedMs = 0L;
    public volatile List<String> playerNames = Collections.emptyList();

    public ServerNode(
            String id,
            String displayName,
            String host,
            int port,
            String proxyName,
            Material icon,
            int slot,
            boolean maintenance,
            List<String> lore
    ) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.host = host == null ? "" : host.trim();
        this.port = port > 0 ? port : 25565;
        this.proxyName = (proxyName == null || proxyName.trim().isEmpty()) ? this.id : proxyName.trim();
        this.icon = icon == null ? Material.GRASS_BLOCK : icon;
        this.slot = slot;
        this.maintenance = maintenance;
        this.lore = lore == null ? new ArrayList<String>() : new ArrayList<String>(lore);
    }

    public boolean isEffectivelyMaintenance() {
        return maintenance || techWorksRemote;
    }

    public boolean hasDirectAddress() {
        return host != null && !host.isEmpty() && port > 0 && port <= 65535;
    }
}
