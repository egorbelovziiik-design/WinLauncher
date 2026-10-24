package nexus.api;

import nexus.api.screen.NexusScreen;

/**
 * Screen stack access. Guarantees the ClickGUI lifecycle rules required by the
 * spec: never open twice, ESC closes nested windows first, RIGHT SHIFT closes
 * everything, and mouse capture is always restored.
 */
public interface IScreenHost {

    void open(NexusScreen screen);

    /** Closes a specific screen if it is on the stack. */
    void close(NexusScreen screen);

    /** Closes the topmost Nexus screen (used by ESC on nested windows). */
    void closeTop();

    /** Closes every Nexus screen on the stack (RIGHT SHIFT behaviour). */
    void closeAll();

    NexusScreen current();

    boolean isOpen(NexusScreen screen);

    int stackSize();

    /** Vanilla screen currently displayed, or null (in game). */
    String currentVanillaScreen();

    /** Forces Minecraft to take mouse control back after the GUI closes. */
    void restoreMouseToGame();

    /** Opens the currently active container (inventory) screen, if any. */
    void openInventoryScreen();

    /** Writes four lines into an open sign edit screen. */
    void fillSignText(String[] lines);

    /** Clicks one of the three enchantment buttons (0 = top). */
    void clickEnchantment(int index);

    /** Opens the vanilla "singleplayer" world list (used by the Nexus menu). */
    void openSingleplayerMenu();

    /** Opens the vanilla multiplayer server list. */
    void openMultiplayerMenu();

    /** Opens the vanilla options screen. */
    void openVanillaOptions();

    /** True when the vanilla title screen is the active screen. */
    boolean isTitleScreen();

    /** True when a Nexus screen is actually visible (used to detect desync). */
    boolean isGuiVisible();
}
