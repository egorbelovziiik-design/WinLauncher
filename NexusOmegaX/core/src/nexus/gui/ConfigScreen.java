package nexus.gui;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.config.ConfigManager;
import nexus.util.ColorUtil;
import nexus.util.FileUtil;

import java.io.File;
import java.util.List;

/** Config manager interface: profiles, backups, import / export. */
public final class ConfigScreen extends NexusScreen {

    private final Nexus nexus;
    private String newName = "";
    private String status = "Ready";
    private int selectedIndex;
    private double scroll;

    public ConfigScreen(Nexus nexus) {
        this.nexus = nexus;
    }

    @Override
    public String title() {
        return "Nexus Config Manager";
    }

    private ConfigManager configs() {
        return nexus.configManager();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.ESCAPE) {
            return false;
        }
        if (keyCode == KeyCodes.BACKSPACE && !newName.isEmpty()) {
            newName = newName.substring(0, newName.length() - 1);
        }
        return true;
    }

    @Override
    public boolean charTyped(char character) {
        if (character >= 32 && character != 127 && newName.length() < 32) {
            newName += character;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0.0, scroll - amount * 12.0);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double width = Math.min(420.0, width() - 40.0);
        double x = (width() - width) / 2.0;
        double height = Math.min(360.0, height() - 40.0);
        double y = (height() - height) / 2.0;

        List<String> profiles = configs().listProfiles();
        double listY = y + 74.0;
        double listHeight = height - 150.0;
        if (mouseX >= x + 16.0 && mouseX <= x + width - 16.0 && mouseY >= listY && mouseY <= listY + listHeight) {
            int index = (int) Math.floor((mouseY - listY + scroll) / 22.0);
            if (index >= 0 && index < profiles.size()) {
                selectedIndex = index;
                newName = profiles.get(index);
                return true;
            }
        }
        // action buttons
        String[] actions = new String[]{"Save", "Load", "Delete", "Rename", "Backup", "Export", "Import", "Open Folder"};
        for (int i = 0; i < actions.length; i++) {
            double bx = x + 16.0 + (i % 4) * ((width - 32.0) / 4.0);
            double by = y + height - 62.0 + (i / 4) * 26.0;
            if (mouseX >= bx && mouseX <= bx + (width - 32.0) / 4.0 - 6.0 && mouseY >= by && mouseY <= by + 22.0) {
                runAction(actions[i]);
                return true;
            }
        }
        return true;
    }

    private void runAction(String action) {
        List<String> profiles = configs().listProfiles();
        String name = selectedIndex < profiles.size() ? profiles.get(selectedIndex) : configs().activeProfile();
        if (action.equals("Save")) {
            String target = newName.isEmpty() ? name : newName;
            status = configs().saveProfile(target) ? "Saved '" + target + "'" : "Save failed";
        } else if (action.equals("Load")) {
            status = configs().loadProfile(name, true) ? "Loaded '" + name + "'" : "Load failed";
        } else if (action.equals("Delete")) {
            status = configs().deleteProfile(name) ? "Deleted '" + name + "'" : "Cannot delete '" + name + "'";
        } else if (action.equals("Rename")) {
            if (newName.isEmpty()) {
                status = "Type a new name first";
            } else {
                status = configs().renameProfile(name, newName) ? "Renamed to '" + newName + "'" : "Rename failed";
            }
        } else if (action.equals("Backup")) {
            File backup = configs().createBackup(name);
            status = backup != null ? "Backup: " + backup.getName() : "Backup failed";
        } else if (action.equals("Export")) {
            File target = new File(configs().folder(), name + "-export.json");
            status = configs().exportProfile(name, target) ? "Exported " + target.getName() : "Export failed";
        } else if (action.equals("Import")) {
            File source = new File(configs().folder(), "import.json");
            status = configs().importProfile(source, newName.isEmpty() ? null : newName)
                    ? "Imported " + source.getName() : "Import failed (place import.json in the config folder)";
        } else if (action.equals("Open Folder")) {
            status = configs().folder().getAbsolutePath();
        }
        nexus.notifications().info("Config Manager", status);
    }

    @Override
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        long now = System.currentTimeMillis();
        GuiTheme.backdrop(renderer, width(), height(), 1.0);

        double width = Math.min(420.0, width() - 40.0);
        double height = Math.min(360.0, height() - 40.0);
        double x = (width() - width) / 2.0;
        double y = (height() - height) / 2.0;

        GuiTheme.panel(renderer, x, y, width, height);
        GuiTheme.titleBar(renderer, x, y, width, "Config Manager", 26.0);
        renderer.drawText("Active: " + configs().activeProfile() + "   |   " + configs().info(),
                x + 16.0, y + 34.0, nexus.themes().textDim(), false, 1.0);
        GuiTheme.searchBox(renderer, x + 16.0, y + 48.0, width - 32.0, 18.0, newName, true);

        double listY = y + 74.0;
        double listHeight = height - 150.0;
        renderer.pushScissor(x + 16.0, listY, width - 32.0, listHeight);
        List<String> profiles = configs().listProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            double rowY = listY + i * 22.0 - scroll;
            boolean active = profiles.get(i).equals(configs().activeProfile());
            if (i == selectedIndex) {
                renderer.fillRoundRect(x + 16.0, rowY, width - 32.0, 20.0, 4.0,
                        ColorUtil.withAlpha(nexus.themes().accent(now), 120));
            }
            renderer.drawText((active ? "> " : "  ") + profiles.get(i), x + 24.0, rowY + 6.0,
                    nexus.themes().text(), false, 1.0);
        }
        renderer.popScissor();

        String[] actions = new String[]{"Save", "Load", "Delete", "Rename", "Backup", "Export", "Import", "Open Folder"};
        for (int i = 0; i < actions.length; i++) {
            double bx = x + 16.0 + (i % 4) * ((width - 32.0) / 4.0);
            double by = y + height - 62.0 + (i / 4) * 26.0;
            boolean hovered = mouseX >= bx && mouseX <= bx + (width - 32.0) / 4.0 - 6.0 && mouseY >= by && mouseY <= by + 22.0;
            GuiTheme.button(renderer, bx, by, (width - 32.0) / 4.0 - 6.0, 22.0, actions[i], hovered, false);
        }
        renderer.drawText(status, x + 16.0, y + height - 14.0,
                ColorUtil.withAlpha(nexus.themes().textDim(), 220), false, 1.0);
    }

    @Override
    public void onClosed() {
        FileUtil.ensure(configs().folder());
    }
}
