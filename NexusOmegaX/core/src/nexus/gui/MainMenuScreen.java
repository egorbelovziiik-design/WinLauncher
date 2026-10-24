package nexus.gui;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.profile.ClientProfile;
import nexus.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The Nexus main menu.
 * <p>
 * It opens on top of the vanilla title screen and offers everything a player
 * needs before joining a world: the nickname used by the client, one click
 * access to singleplayer / multiplayer / vanilla options, the Nexus settings
 * and the ClickGUI. Closing it (BACK or ESC) simply reveals the untouched
 * vanilla menu underneath, so nothing can get "stuck" and the vanilla buttons
 * keep working.
 */
public final class MainMenuScreen extends NexusScreen {

    private static final double PANEL_WIDTH = 300.0;
    private static final double ROW_HEIGHT = 24.0;
    private static final double ROW_GAP = 6.0;
    private static final double FIELD_HEIGHT = 24.0;
    private static final double TOGGLE_HEIGHT = 20.0;
    private static final double PADDING = 16.0;

    /** One entry of the button column. */
    private static final class Row {
        private final String label;
        private final String hint;
        private final Runnable action;

        private Row(String label, String hint, Runnable action) {
            this.label = label;
            this.hint = hint;
            this.action = action;
        }
    }

    /** Geometry of every clickable element, computed once per frame/click. */
    private static final class Layout {
        private double panelX;
        private double panelY;
        private double panelHeight;
        private double contentX;
        private double contentWidth;
        private double fieldY;
        private double toggleY;
        private double rowsY;
    }

    private final Nexus nexus;
    private final List<Row> rows = new ArrayList<Row>();
    private final Layout layout = new Layout();

    private String draft;
    private boolean editingNickname;
    private String status = "";

    public MainMenuScreen(Nexus nexus) {
        this.nexus = nexus;
        this.draft = nexus.profile().nickname();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        rows.add(new Row("Одиночная игра", "Singleplayer", new Runnable() {
            @Override
            public void run() {
                leaveForVanilla();
                final nexus.api.IScreenHost host = nexus.client().screens();
                nexus.gui().runNextTick(new Runnable() {
                    @Override
                    public void run() {
                        host.openSingleplayerMenu();
                    }
                });
            }
        }));
        rows.add(new Row("Сетевая игра", "Multiplayer", new Runnable() {
            @Override
            public void run() {
                leaveForVanilla();
                final nexus.api.IScreenHost host = nexus.client().screens();
                nexus.gui().runNextTick(new Runnable() {
                    @Override
                    public void run() {
                        host.openMultiplayerMenu();
                    }
                });
            }
        }));
        rows.add(new Row("Настройки Minecraft", "Vanilla options", new Runnable() {
            @Override
            public void run() {
                leaveForVanilla();
                final nexus.api.IScreenHost host = nexus.client().screens();
                nexus.gui().runNextTick(new Runnable() {
                    @Override
                    public void run() {
                        host.openVanillaOptions();
                    }
                });
            }
        }));
        rows.add(new Row("Nexus настройки", "Themes, HUD, keybinds", new Runnable() {
            @Override
            public void run() {
                commitNickname();
                nexus.gui().runNextTick(() -> nexus.gui().openSettings());
            }
        }));
        rows.add(new Row("ClickGUI", "RIGHT SHIFT in game", new Runnable() {
            @Override
            public void run() {
                commitNickname();
                nexus.gui().runNextTick(() -> nexus.gui().openClickGui());
            }
        }));
        rows.add(new Row("Вернуться в меню Minecraft", "Back", new Runnable() {
            @Override
            public void run() {
                commitNickname();
                nexus.gui().runNextTick(() -> nexus.gui().closeAll(true));
            }
        }));
    }

    /**
     * Leaves the Nexus menu before a vanilla screen takes over, so the vanilla
     * menu gets the real title screen as its parent and ESC walks back into it.
     */
    private void leaveForVanilla() {
        editingNickname = false;
        commitNickname();
        nexus.gui().closeAll(false);
    }

    @Override
    public String title() {
        return "Nexus Menu";
    }

    @Override
    public boolean pausesGame() {
        return true;
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        this.draft = nexus.profile().nickname();
    }

    // ----------------------------------------------------------------- layout

    private Layout layout() {
        double panelHeight = 92.0 + FIELD_HEIGHT + TOGGLE_HEIGHT + 8.0
                + rows.size() * (ROW_HEIGHT + ROW_GAP) + 30.0;
        layout.panelHeight = panelHeight;
        layout.panelX = (width() - PANEL_WIDTH) / 2.0;
        layout.panelY = Math.max(12.0, (height() - panelHeight) / 2.0);
        layout.contentX = layout.panelX + PADDING;
        layout.contentWidth = PANEL_WIDTH - PADDING * 2.0;
        layout.fieldY = layout.panelY + 58.0;
        layout.toggleY = layout.fieldY + FIELD_HEIGHT + 8.0;
        layout.rowsY = layout.toggleY + TOGGLE_HEIGHT + 8.0;
        return layout;
    }

    private static boolean inside(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean blink() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L;
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        int width = width();
        int height = height();
        Layout layout = layout();

        renderer.fillGradientRect(0, 0, width, height, 0xF00A0612, 0xF0200E38, true);
        renderer.fillGradientRect(0, (int) (height * 0.55), width, (int) (height * 0.45),
                0x301E8FFF, 0x00FFFFFF, true);

        renderer.shadow(layout.panelX, layout.panelY, PANEL_WIDTH, layout.panelHeight, 12.0, 16.0, 0x66000000);
        renderer.fillGradientRound(layout.panelX, layout.panelY, PANEL_WIDTH, layout.panelHeight, 12.0,
                0xF0140B24, 0xF01C0F30, true);
        renderer.outlineRoundRect(layout.panelX, layout.panelY, PANEL_WIDTH, layout.panelHeight, 12.0,
                1.0, 0x606C4BFF);

        renderer.drawCenteredText("NEXUS", width / 2.0, layout.panelY + 16.0, 0xFFFFFFFF, true, 2.0);
        renderer.drawCenteredText("CLIENT OMEGA X", width / 2.0, layout.panelY + 40.0, 0xFF9B7BFF, false, 1.0);

        // ---- nickname field -------------------------------------------------
        double fieldX = layout.contentX;
        double fieldY = layout.fieldY;
        double fieldW = layout.contentWidth;
        boolean fieldHovered = inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT);
        renderer.fillRoundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, 6.0,
                editingNickname ? 0xFF2A1A46 : (fieldHovered ? 0xFF241537 : 0xFF1A0F2A));
        renderer.outlineRoundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, 6.0, 1.0,
                editingNickname ? 0xFF6C4BFF : 0x40FFFFFF);

        String shown = draft == null ? "" : draft;
        if (editingNickname) {
            renderer.drawText(shown + (blink() ? "_" : ""), fieldX + 8.0, fieldY + 8.0, 0xFFFFFFFF, false);
        } else if (shown.isEmpty()) {
            renderer.drawText("Ник (нажми, чтобы изменить)", fieldX + 8.0, fieldY + 8.0, 0xFF8A7BA8, false);
        } else {
            renderer.drawText(shown, fieldX + 8.0, fieldY + 8.0, 0xFFFFFFFF, false);
            renderer.drawText("ник", fieldX + fieldW - 26.0, fieldY + 8.0, 0xFF8A7BA8, false);
        }

        // ---- custom menu toggle --------------------------------------------
        ClientProfile profile = nexus.profile();
        boolean toggleHovered = inside(mouseX, mouseY, fieldX, layout.toggleY, fieldW, TOGGLE_HEIGHT);
        renderer.fillRoundRect(fieldX, layout.toggleY, fieldW, TOGGLE_HEIGHT, 5.0,
                toggleHovered ? 0xFF241537 : 0xFF170E26);
        double boxX = fieldX + 7.0;
        double boxY = layout.toggleY + 5.0;
        renderer.fillRoundRect(boxX, boxY, 10.0, 10.0, 2.0,
                profile.customMainMenu() ? 0xFF6C4BFF : 0xFF2A1E3E);
        renderer.drawText("Своё меню при запуске", boxX + 16.0, layout.toggleY + 5.0,
                profile.customMainMenu() ? 0xFFEDE7FF : 0xFF8A7BA8, false);
        String state = profile.customMainMenu() ? "ВКЛ" : "ВЫКЛ";
        renderer.drawText(state, fieldX + fieldW - renderer.textWidth(state, 1.0) - 8.0,
                layout.toggleY + 5.0, 0xFF9B7BFF, false);

        // ---- buttons --------------------------------------------------------
        double rowY = layout.rowsY;
        for (Row row : rows) {
            boolean hovered = inside(mouseX, mouseY, fieldX, rowY, fieldW, ROW_HEIGHT);
            renderer.fillGradientRound(fieldX, rowY, fieldW, ROW_HEIGHT, 6.0,
                    hovered ? 0xFF3A2270 : 0xFF221338, hovered ? 0xFF4A2C8C : 0xFF2A1745, false);
            renderer.outlineRoundRect(fieldX, rowY, fieldW, ROW_HEIGHT, 6.0, 1.0,
                    hovered ? 0x806C4BFF : 0x25FFFFFF);
            renderer.drawText(row.label, fieldX + 10.0, rowY + 8.0, 0xFFFFFFFF, false, 1.0);
            if (row.hint != null) {
                double hintWidth = renderer.textWidth(row.hint, 1.0);
                renderer.drawText(row.hint, fieldX + fieldW - hintWidth - 10.0, rowY + 8.0,
                        0xFF9B7BFF, false, 1.0);
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        if (!status.isEmpty()) {
            renderer.drawCenteredText(status, width / 2.0, rowY + 2.0, 0xFF74E0A0, false, 1.0);
        } else {
            String nick = profile.nickname();
            renderer.drawCenteredText(NexusInfo.NAME + " " + NexusInfo.VERSION + "  |  "
                            + (nick.isEmpty() ? "ник не задан" : nick)
                            + "  |  F6 - меню, RIGHT SHIFT - ClickGUI",
                    width / 2.0, rowY + 2.0, 0xFF7E6FA0, false, 1.0);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        Layout layout = layout();
        double fieldX = layout.contentX;
        double fieldW = layout.contentWidth;

        if (inside(mouseX, mouseY, fieldX, layout.fieldY, fieldW, FIELD_HEIGHT)) {
            editingNickname = true;
            status = "";
            return true;
        }

        if (inside(mouseX, mouseY, fieldX, layout.toggleY, fieldW, TOGGLE_HEIGHT)) {
            nexus.profile().setCustomMainMenu(!nexus.profile().customMainMenu());
            status = nexus.profile().customMainMenu()
                    ? "Своё меню включено" : "Своё меню выключено - будет ванильное";
            return true;
        }

        double rowY = layout.rowsY;
        for (Row row : rows) {
            if (inside(mouseX, mouseY, fieldX, rowY, fieldW, ROW_HEIGHT)) {
                editingNickname = false;
                commitNickname();
                // never swap screens from inside a click: the click is still
                // being processed by vanilla input at this point
                final Row target = row;
                nexus.gui().runNextTick(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            target.action.run();
                        } catch (Throwable throwable) {
                            Log.error("Main menu action failed: " + target.label, throwable);
                        }
                    }
                });
                return true;
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        editingNickname = false;
        commitNickname();
        return true;
    }

    @Override
    public boolean charTyped(char character) {
        if (!editingNickname) {
            return false;
        }
        if (draft.length() >= 16) {
            return true;
        }
        String filtered = ClientProfile.sanitize(String.valueOf(character));
        if (!filtered.isEmpty()) {
            draft = draft + filtered;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editingNickname) {
            return false;
        }
        if (keyCode == KeyCodes.BACKSPACE) {
            if (!draft.isEmpty()) {
                draft = draft.substring(0, draft.length() - 1);
            }
            return true;
        }
        if (keyCode == KeyCodes.DELETE) {
            draft = "";
            return true;
        }
        if (keyCode == KeyCodes.ENTER) {
            editingNickname = false;
            commitNickname();
            return true;
        }
        if (keyCode == KeyCodes.ESCAPE) {
            editingNickname = false;
            draft = nexus.profile().nickname();
            return true;
        }
        return true;
    }

    private void commitNickname() {
        String current = nexus.profile().nickname();
        String cleaned = ClientProfile.sanitize(draft);
        if (!cleaned.equals(current)) {
            nexus.profile().setNickname(cleaned);
            status = cleaned.isEmpty() ? "Ник сброшен" : "Ник сохранён: " + cleaned;
        }
        draft = cleaned;
    }
}
