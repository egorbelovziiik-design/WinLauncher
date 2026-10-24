package nexus.core;

import nexus.api.IClient;
import nexus.api.KeyCodes;
import nexus.api.NexusPlatform;
import nexus.core.automation.MacroManager;
import nexus.core.config.ConfigManager;
import nexus.core.diagnostics.Diagnostics;
import nexus.core.event.*;
import nexus.core.event.EventBus;
import nexus.core.hud.HudManager;
import nexus.core.keybind.KeybindManager;
import nexus.core.module.Module;
import nexus.core.module.ModuleManager;
import nexus.core.command.CommandManager;
import nexus.core.module.ModuleRegistry;
import nexus.core.notification.NotificationManager;
import nexus.core.render.RenderManager;
import nexus.core.session.SessionTracker;
import nexus.core.social.FriendManager;
import nexus.core.theme.ThemeManager;
import nexus.core.performance.PerformanceMonitor;
import nexus.core.world.WaypointManager;
import nexus.gui.GuiManager;
import nexus.util.FileUtil;
import nexus.util.Log;
import nexus.util.TimeUtil;

import java.io.File;

/**
 * Root of the client. Owns every manager and exposes the single event bus.
 * <p>
 * The adapter (per Minecraft version) creates the instance once during mod
 * initialization and drives it through {@link #tick()}, {@link #render2D(Render2DEvent)},
 * {@link #render3D(Render3DEvent)} and the input hooks.
 */
public final class Nexus {

    private static volatile Nexus instance;

    private final NexusPlatform platform;
    private final File directory;
    private final EventBus eventBus = new EventBus();

    private final ModuleManager modules = new ModuleManager();
    private final KeybindManager keybinds = new KeybindManager();
    private final ConfigManager configs;
    private final CommandManager commands;
    private final ThemeManager themes;
    private final HudManager hud;
    private final NotificationManager notifications;
    private final RenderManager render;
    private final PerformanceMonitor performance;
    private final FriendManager friends;
    private final WaypointManager waypoints;
    private final MacroManager macros;
    private final Diagnostics diagnostics;
    private final SessionTracker session;
    private final GuiManager gui;

    private int tickCount;
    private boolean initialized;
    private boolean shuttingDown;
    private final TimeUtil.Timer autosaveTimer = new TimeUtil.Timer();

    private Nexus(NexusPlatform platform, File directory) {
        this.platform = platform;
        this.directory = directory;
        this.configs = new ConfigManager(this);
        this.commands = new CommandManager(this);
        this.themes = new ThemeManager(this);
        this.hud = new HudManager(this);
        this.notifications = new NotificationManager(this);
        this.render = new RenderManager(this);
        this.performance = new PerformanceMonitor(this);
        this.friends = new FriendManager(this);
        this.waypoints = new WaypointManager(this);
        this.macros = new MacroManager(this);
        this.diagnostics = new Diagnostics(this);
        this.session = new SessionTracker(this);
        this.gui = new GuiManager(this);
    }

    public static synchronized Nexus init(NexusPlatform platform, File gameDirectory) {
        if (instance != null) {
            Log.warn("Nexus.init called twice - ignoring");
            return instance;
        }
        Log.info("Booting " + NexusInfo.fullTitle() + " on " + platform.id() + " / MC " + platform.minecraftVersion());
        File directory = FileUtil.directory(gameDirectory, NexusInfo.CONFIG_FOLDER);
        instance = new Nexus(platform, directory);
        instance.startup();
        return instance;
    }

    public static Nexus instance() {
        Nexus current = instance;
        if (current == null) {
            throw new IllegalStateException("Nexus has not been initialized");
        }
        return current;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static EventBus eventBus() {
        return isInitialized() ? instance().eventBus : null;
    }

    // ------------------------------------------------------------------ startup

    private void startup() {
        Log.info("Registering modules...");
        ModuleRegistry.registerAll(this);
        Log.info("Registered " + modules.size() + " modules");

        commands.registerDefaults();
        hud.registerDefaults();
        themes.registerDefaults();
        gui.registerClientBinds();

        platform.installHooks();

        configs.loadStartupConfig();
        themes.applyActiveTheme();

        eventBus.subscribe(TickEvent.class, new Listener<TickEvent>() {
            @Override
            public void on(TickEvent event) {
                session.onTick(event);
            }
        });

        initialized = true;
        Log.info(NexusInfo.PRODUCT + " ready - " + modules.size() + " modules, " + hud.size() + " HUD elements");
        notifications.success("Nexus Omega X", "Loaded " + modules.size() + " modules");
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        Log.info("Shutting down Nexus...");
        gui.closeAll(true);
        modules.disableAll();
        configs.saveAll(true);
        platform.uninstallHooks();
        eventBus.clear();
        Log.info("Nexus stopped cleanly");
    }

    // ------------------------------------------------------------------- frame

    /**
     * Called once per client tick by the adapter.
     */
    public void tick() {
        if (!initialized) {
            return;
        }
        tickCount++;
        performance.onTick();
        eventBus.post(new TickEvent(TickEvent.Phase.PRE, tickCount));
        notifications.onTick();
        macros.onTick();
        gui.onTick();
        eventBus.post(new TickEvent(TickEvent.Phase.POST, tickCount));

        if (configs.autoSaveEnabled() && autosaveTimer.hasPassed(configs.autoSaveIntervalMs(), true)) {
            configs.saveAll(false);
        }
    }

    public void render2D(Render2DEvent event) {
        if (!initialized) {
            return;
        }
        performance.onFrame();
        render.beginFrame(event.renderer, event.deltaMs);
        hud.render(event);
        // the notification stack is drawn by its HUD element when that element is enabled
        nexus.core.hud.HudElement notificationsElement = hud.byId("notifications");
        if (notificationsElement == null || !notificationsElement.isEnabled() || !hud.isVisible()) {
            notifications.render(event);
        }
        eventBus.post(event);
        render.endFrame();
    }

    public void render3D(Render3DEvent event) {
        if (!initialized) {
            return;
        }
        render.beginWorld(event.renderer, event.deltaMs);
        eventBus.post(event);
        render.endWorld();
    }

    /** Returns true when the key was consumed by Nexus. */
    public boolean onKey(int key, int scanCode, int modifiers, KeyEvent.Action action, boolean inGui) {
        if (!initialized) {
            return false;
        }
        KeyEvent event = new KeyEvent(key, scanCode, modifiers, action, inGui);
        // 1) the GUI manager owns RIGHT SHIFT and ESC while interfaces are open
        if (gui.onKey(event)) {
            return true;
        }
        // 2) module / client keybinds
        boolean consumed = keybinds.onKey(event);
        eventBus.post(event);
        return consumed || event.isCancelled();
    }

    public boolean onMouse(int button, MouseEvent.Action action, double scroll, double mouseX, double mouseY) {
        if (!initialized) {
            return false;
        }
        MouseEvent event = new MouseEvent(button, action, scroll, mouseX, mouseY);
        eventBus.post(event);
        return event.isCancelled();
    }

    public void onChatSend(String message) {
        if (!initialized) {
            return;
        }
        ChatSendEvent event = new ChatSendEvent(message);
        eventBus.post(event);
        if (!event.isCancelled() && event.message != null && event.message.startsWith(NexusInfo.COMMAND_PREFIX)) {
            commands.execute(event.message.substring(NexusInfo.COMMAND_PREFIX.length()));
        }
    }

    public boolean onChatReceive(String message, String sender) {
        if (!initialized) {
            return false;
        }
        ChatReceiveEvent event = new ChatReceiveEvent(message, sender);
        eventBus.post(event);
        return event.isCancelled();
    }

    public void onWorldJoin(String address, boolean singleplayer) {
        eventBus.post(new WorldJoinEvent(address, singleplayer));
        session.onWorldJoin(address, singleplayer);
        notifications.info("Connected", singleplayer ? "Singleplayer world" : address);
    }

    public void onWorldLeave(String address) {
        eventBus.post(new WorldLeaveEvent(address));
        session.onWorldLeave();
    }

    public void onDeath(String cause, int score) {
        eventBus.post(new DeathEvent(cause, score));
        session.onDeath(cause);
    }

    // ------------------------------------------------------------------- panic

    /**
     * PANIC: disable every module, hide the HUD, close all interfaces and
     * restore the vanilla visual settings.
     */
    public int panic() {
        gui.closeAll(true);
        keybinds.releaseAll();
        int disabled = modules.disableAll();
        hud.setVisible(false);
        render.resetVisualOverrides();
        macros.stopAll();
        notifications.warn("PANIC", disabled + " modules disabled");
        Log.warn("PANIC executed - " + disabled + " modules disabled");
        return disabled;
    }

    // ---------------------------------------------------------------- accessors

    public NexusPlatform platform() {
        return platform;
    }

    public IClient client() {
        return platform.client();
    }

    public File directory() {
        return directory;
    }

    public EventBus bus() {
        return eventBus;
    }

    public ModuleManager modules() {
        return modules;
    }

    public CommandManager commands() {
        return commands;
    }

    public ConfigManager configManager() {
        return configs;
    }

    public ThemeManager themes() {
        return themes;
    }

    public HudManager hud() {
        return hud;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public RenderManager render() {
        return render;
    }

    public PerformanceMonitor performance() {
        return performance;
    }

    public FriendManager friends() {
        return friends;
    }

    public WaypointManager waypoints() {
        return waypoints;
    }

    public MacroManager macros() {
        return macros;
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    public SessionTracker session() {
        return session;
    }

    public KeybindManager keybinds() {
        return keybinds;
    }

    public GuiManager gui() {
        return gui;
    }

    public int tickCount() {
        return tickCount;
    }

    public boolean isInitializedInstance() {
        return initialized;
    }

    /** Convenience: opens the ClickGUI the same way the RIGHT SHIFT bind does. */
    public void openClickGui() {
        gui.openClickGui();
    }

    /** GUI key constant (RIGHT SHIFT) re-exported for adapters and tests. */
    public static int guiKey() {
        return KeyCodes.GUI_KEY;
    }
}
