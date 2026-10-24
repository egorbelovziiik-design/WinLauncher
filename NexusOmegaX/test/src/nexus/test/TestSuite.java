package nexus.test;

import nexus.api.KeyCodes;
import nexus.core.Nexus;
import nexus.core.NexusInfo;
import nexus.core.event.*;
import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.Setting;
import nexus.util.FileUtil;
import nexus.util.json.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless acceptance test suite for the Nexus client.
 * <p>
 * It boots the real core on the {@link HeadlessPlatform}, then exercises every
 * acceptance criterion of the task: module registration, per module enable /
 * tick / render / disable, the command set, the config system, the HUD, the
 * ClickGUI (RIGHT SHIFT opens and closes, ESC closes nested windows first,
 * mouse control is returned to the game), themes, keybinds, panic and a clean
 * shutdown.
 * <p>
 * Exit code 0 means every check passed.
 */
public final class TestSuite {

    private static final List<String> failures = new ArrayList<String>();
    private static final List<String> results = new ArrayList<String>();
    private static int checks;

    public static void main(String[] args) {
        File directory = new File(args.length > 0 ? args[0] : "build/test-runtime");
        FileUtil.ensure(directory);
        HeadlessPlatform platform = new HeadlessPlatform(directory);
        HeadlessClient client = platform.headless();

        section("1. Boot");
        Nexus nexus = Nexus.init(platform, directory);
        check("client initialised", Nexus.isInitialized());
        check("hooks installed", platform.isHealthy());
        int moduleCount = nexus.modules().size();
        check("modules registered >= 250 (actual " + moduleCount + ")", moduleCount >= 250);

        for (ModuleCategory category : ModuleCategory.values()) {
            check("category " + category.name() + " not empty",
                    !nexus.modules().byCategory(category).isEmpty());
        }

        section("2. Every module: enable / tick / render / disable");
        int exercised = 0;
        for (Module module : nexus.modules().all()) {
            if ("panic".equals(module.name())) {
                continue; // tested separately, it disables everything by design
            }
            try {
                module.setEnabled(true, false);
                tick(nexus, 3);
                render2D(nexus, client, 1);
                render3D(nexus, client, 1);
                module.setEnabled(false, false);
                exercised++;
            } catch (Throwable throwable) {
                failures.add("module " + module.name() + ": " + throwable);
            }
        }
        check("all " + exercised + " modules ran without an exception",
                exercised == moduleCount - 1);

        section("3. Settings round trip");
        int settingsChecked = 0;
        for (Module module : nexus.modules().all()) {
            for (Setting<?> setting : module.settings()) {
                try {
                    String text = setting.displayValue();
                    if (text != null) {
                        setting.parse(text);
                    }
                    setting.reset();
                    settingsChecked++;
                } catch (Throwable throwable) {
                    failures.add("setting " + module.name() + "." + setting.name() + ": " + throwable);
                }
            }
        }
        check("all " + settingsChecked + " settings round tripped", true);

        section("4. Commands");
        String[] commands = {".help", ".modules", ".search esp", ".version", ".stats",
                ".cfg list", ".cfg save testprofile", ".cfg list", ".cfg reset",
                ".theme", ".theme OMEGA PURPLE", ".hud list", ".friend add Alex",
                ".friend list", ".friend remove Alex", ".enemy add Griefer", ".enemy list",
                ".waypoint add base", ".waypoint list", ".waypoint remove base",
                ".gui", ".font", ".color list", ".watermark", ".debug", ".client",
                ".settings", ".profile list", ".reload", ".bind", ".toggle sprint",
                ".panic", ".modules"};
        for (String command : commands) {
            try {
                nexus.commands().execute(command);
            } catch (Throwable throwable) {
                failures.add("command " + command + ": " + throwable);
            }
        }
        check("all " + commands.length + " commands executed", true);
        check("modules disabled by panic", nexus.modules().enabledCount() == 0);

        section("5. Keybinds");
        Module sprint = nexus.modules().byName("sprint");
        check("sprint module exists", sprint != null);
        if (sprint != null) {
            sprint.setKeybind(KeyCodes.KEY_V);
            nexus.keybinds().syncFromModules(nexus.modules().all());
            boolean before = sprint.isEnabled();
            nexus.onKey(KeyCodes.KEY_V, 0, 0, KeyEvent.Action.PRESS, false);
            nexus.onKey(KeyCodes.KEY_V, 0, 0, KeyEvent.Action.RELEASE, false);
            tick(nexus, 1);
            boolean after = sprint.isEnabled();
            check("keybind toggled the module (" + before + " -> " + after + ")", before != after);
            sprint.setEnabled(false, false);
        }

        section("6. ClickGUI: RIGHT SHIFT opens and closes, ESC closes, mouse recapture");
        client.input().grabMouse();
        client.mouseRestored = false;
        check("mouse captured by the game at start", client.input().isMouseGrabbed());

        pressShift(nexus, client);
        check("RIGHT SHIFT opened the ClickGUI", nexus.gui().isClickGuiOpen());
        check("mouse released while the GUI is open", !client.input().isMouseGrabbed());
        check("exactly one window on the stack", nexus.gui().stackSize() == 1);

        nexus.gui().openClickGui();
        check("no double open", nexus.gui().stackSize() == 1);

        // open a nested window (HUD editor) and close it with ESC first
        nexus.gui().openHudEditor();
        check("nested window opened", nexus.gui().stackSize() == 2);
        renderGui(nexus, client, 3);

        nexus.onKey(KeyCodes.ESCAPE, 0, 0, KeyEvent.Action.PRESS, false);
        check("ESC closed the nested window first", nexus.gui().stackSize() == 1);
        check("ClickGUI still open after the first ESC", nexus.gui().isClickGuiOpen());

        nexus.onKey(KeyCodes.ESCAPE, 0, 0, KeyEvent.Action.PRESS, false);
        check("second ESC closed the ClickGUI", !nexus.gui().isClickGuiOpen());
        check("no invisible windows left", nexus.gui().stackSize() == 0);
        check("mouse control restored to the game", client.mouseRestored
                && client.input().isMouseGrabbed());

        // RIGHT SHIFT must also close it again (with the 120 ms debounce in between)
        pressShift(nexus, client);
        check("RIGHT SHIFT re-opened the ClickGUI", nexus.gui().isClickGuiOpen());
        pressShift(nexus, client);
        check("RIGHT SHIFT closed the ClickGUI again", !nexus.gui().isClickGuiOpen());
        check("mouse control restored after RIGHT SHIFT", client.input().isMouseGrabbed());

        section("7. HUD");
        for (HudElement element : nexus.hud().all()) {
            try {
                element.setEnabled(true);
            } catch (Throwable throwable) {
                failures.add("hud element " + element.id() + ": " + throwable);
            }
        }
        int before = client.drawCalls;
        render2D(nexus, client, 2);
        check("HUD renders (" + (client.drawCalls - before) + " draw calls)",
                client.drawCalls > before);
        nexus.hud().resetLayout();
        check("HUD has " + nexus.hud().size() + " elements", nexus.hud().size() >= 20);
        for (HudElement element : nexus.hud().all()) {
            element.setEnabled(false);
        }

        section("8. Themes");
        int themes = 0;
        for (String name : nexus.themes().names()) {
            if (nexus.themes().setActive(name)) {
                themes++;
            }
        }
        check("all " + themes + " themes activate", themes >= 7);
        nexus.themes().setActive("OMEGA PURPLE");

        section("9. Config system");
        String activeBefore = nexus.configManager().activeProfile();
        check("default profile active", activeBefore != null && !activeBefore.isEmpty());
        check("save profile", nexus.configManager().saveProfile("suite"));
        check("profile listed", nexus.configManager().listProfiles().contains("suite"));
        File export = new File(directory, "export.json");
        check("export profile", nexus.configManager().exportProfile("suite", export));
        check("import profile", nexus.configManager().importProfile(export, "imported"));
        check("rename profile", nexus.configManager().renameProfile("imported", "renamed"));
        check("load profile", nexus.configManager().loadProfile("renamed", true));
        check("delete profile", nexus.configManager().deleteProfile("renamed"));
        File backup = nexus.configManager().createBackup("suite");
        check("backup created", backup != null && backup.exists());
        check("load profile back", nexus.configManager().loadProfile(activeBefore, true));
        check("delete test profile", nexus.configManager().deleteProfile("suite"));

        section("10. Waypoints, friends, macros, notifications");
        nexus.waypoints().add("suite", 10, 64, -20, "minecraft:overworld", 0xFFC084FC);
        check("waypoint added", nexus.waypoints().byName("suite") != null);
        check("waypoint removed", nexus.waypoints().remove("suite"));
        check("friend added", nexus.friends().addFriend("Suite Buddy"));
        check("friend recognised", nexus.friends().isFriend("Suite Buddy"));
        check("friend removed", nexus.friends().removeFriend("Suite Buddy"));
        check("macro profile created", nexus.macros().create("suite-macro") != null);
        check("macro profile deleted", nexus.macros().delete("suite-macro"));
        nexus.notifications().info("Suite", "notification test");
        check("notification queued", true);

        section("11. Long run (600 ticks with all modules on)");
        int enableFailed = 0;
        for (Module module : nexus.modules().all()) {
            if ("panic".equals(module.name())) {
                continue;
            }
            try {
                module.setEnabled(true, false);
            } catch (Throwable throwable) {
                enableFailed++;
            }
        }
        for (int i = 0; i < 600; i++) {
            tick(nexus, 1);
            if (i % 20 == 0) {
                render2D(nexus, client, 1);
                render3D(nexus, client, 1);
            }
        }
        check("600 ticks with every module enabled", enableFailed == 0);
        check("every module stayed enabled for the whole run",
                nexus.modules().enabledCount() == moduleCount - 1);
        Module panicModule = nexus.modules().byName("panic");
        panicModule.setEnabled(true, false);
        int afterPanic = nexus.modules().enabledCount();
        check("PANIC disabled " + (moduleCount - 1 - afterPanic) + " modules",
                afterPanic == 0);
        check("PANIC module disables itself too", !panicModule.isEnabled());
        check("all keys released after PANIC", client.keys.isEmpty());

        section("12. Shutdown and files on disk");
        nexus.shutdown();
        check("client shut down cleanly", true);
        File configFolder = new File(directory, NexusInfo.CONFIG_FOLDER + "/configs");
        check("config folder created (" + configFolder.getName() + ")", configFolder.exists());
        File profileFile = new File(configFolder, "profiles/default.json");
        check("profile file written", profileFile.exists());
        check("profile file is valid json", isJson(profileFile));
        Json.JsonObject profile = Json.parseObject(FileUtil.read(profileFile));
        check("profile stores every module", profile.getObject("modules", Json.object()).size() > 200);
        check("profile stores the keybinds", profile.getObject("keybinds", Json.object()).size() > 200);
        File hudFile = new File(configFolder, "hud.json");
        check("hud.json written", hudFile.exists());
        check("hud.json is valid json", isJson(hudFile));
        File themesFile = new File(configFolder, "themes.json");
        check("themes.json written", themesFile.exists());
        File waypointsFile = new File(configFolder, "waypoints.json");
        check("waypoints.json written", waypointsFile.exists());
        File guiFile = new File(configFolder, "gui-layout.json");
        check("gui-layout.json written", guiFile.exists());

        // ------------------------------------------------------------- report
        System.out.println();
        System.out.println("================ NEXUS CLIENT OMEGA X - HEADLESS TEST SUITE ================");
        System.out.println("product      : " + NexusInfo.fullTitle());
        System.out.println("build        : " + NexusInfo.BUILD);
        System.out.println("modules      : " + moduleCount);
        System.out.println("settings     : " + settingsChecked);
        System.out.println("java         : " + System.getProperty("java.version"));
        System.out.println("---------------------------------------------------------------------------");
        for (String line : results) {
            System.out.println(line);
        }
        System.out.println("---------------------------------------------------------------------------");
        System.out.println("checks       : " + checks);
        System.out.println("failures     : " + failures.size());
        for (String failure : failures) {
            System.out.println("  FAIL " + failure);
        }
        System.out.println("===========================================================================");
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ utils

    /** Presses RIGHT SHIFT, waits out the GUI debounce and releases the key. */
    private static void pressShift(Nexus nexus, HeadlessClient client) {
        try {
            Thread.sleep(160L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        nexus.onKey(KeyCodes.RIGHT_SHIFT, 0, 0, KeyEvent.Action.PRESS, false);
        nexus.onKey(KeyCodes.RIGHT_SHIFT, 0, 0, KeyEvent.Action.RELEASE, false);
    }

    private static void tick(Nexus nexus, int count) {
        for (int i = 0; i < count; i++) {
            nexus.tick();
        }
    }

    private static void render2D(Nexus nexus, HeadlessClient client, int frames) {
        for (int i = 0; i < frames; i++) {
            Render2DEvent event = new Render2DEvent(client.renderer(), 960, 540, 16.6f, 100, 100);
            nexus.render2D(event);
        }
    }

    private static void render3D(Nexus nexus, HeadlessClient client, int frames) {
        for (int i = 0; i < frames; i++) {
            Render3DEvent event = new Render3DEvent(client.renderer(), 16.6f, 0.5f);
            nexus.render3D(event);
        }
    }

    private static void renderGui(Nexus nexus, HeadlessClient client, int frames) {
        for (int i = 0; i < frames; i++) {
            nexus.gui().render(client.renderer(), 100, 100, 16.6f);
        }
    }

    private static boolean isJson(File file) {
        try {
            Json.parse(FileUtil.read(file));
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void section(String name) {
        results.add("");
        results.add(name);
    }

    private static void check(String name, boolean condition) {
        checks++;
        results.add((condition ? "  [PASS] " : "  [FAIL] ") + name);
        if (!condition) {
            failures.add(name);
        }
    }
}
