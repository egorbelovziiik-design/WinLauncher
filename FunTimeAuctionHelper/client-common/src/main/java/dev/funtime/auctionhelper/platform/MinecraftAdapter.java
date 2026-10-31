package dev.funtime.auctionhelper.platform;

import dev.funtime.auctionhelper.model.SlotSnapshot;
import dev.funtime.auctionhelper.model.ScreenSnapshot;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Everything the common core needs from the running game. Implemented once per
 * Minecraft version (1.16.5, 1.21.4, 1.21.11) so the core stays version independent.
 */
public interface MinecraftAdapter {
    String getMinecraftVersion();

    String getJavaVersion();

    String getLoaderVersion();

    Path getGameDir();

    boolean isInWorld();

    boolean isSingleplayer();

    /** Multiplayer server address, or "singleplayer". */
    String getServerName();

    String getPlayerName();

    /** Screen currently open in the client, or null when in game. */
    String getCurrentScreenTitle();

    String getCurrentScreenClass();

    boolean isHelperScreenOpen();

    void openHelperScreen();

    void closeScreen();

    /**
     * Reads the currently open container screen (title + visible slots).
     *
     * @return null when no container screen is open
     */
    ScreenSnapshot captureScreen();

    /** The player's own inventory contents (36 slots + armour + offhand). */
    List<SlotSnapshot> readInventory();

    void sendChatMessage(String message);

    void copyToClipboard(String text);

    default String getClipboard() {
        return "";
    }

    /** Optional: balance exposed by the server (scoreboard/API). Unknown by default. */
    default boolean isBalanceKnown() {
        return false;
    }

    default double getBalance() {
        return Double.NaN;
    }

    default List<String> describeEnvironment() {
        return Collections.emptyList();
    }
}
