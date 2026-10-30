package dev.axsesclient.common;

import dev.axsesclient.common.animation.AnimationManager;
import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.config.ConfigManager;
import dev.axsesclient.common.events.ClientEvents;
import dev.axsesclient.common.events.EventManager;
import dev.axsesclient.common.gui.AxsesMainMenuController;
import dev.axsesclient.common.gui.ClickGuiController;
import dev.axsesclient.common.gui.HudEditorController;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.keybinds.KeybindManager;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.modules.impl.ClientRuntimeState;
import dev.axsesclient.common.modules.impl.CombatTrainingModulesRegistry;
import dev.axsesclient.common.modules.impl.GuiFeaturesRegistry;
import dev.axsesclient.common.modules.impl.MovementPlayerModulesRegistry;
import dev.axsesclient.common.modules.impl.VisualModulesRegistry;
import dev.axsesclient.common.modules.impl.WorldUtilityModulesRegistry;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.perf.PerformanceProfiler;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.SettingManager;
import dev.axsesclient.common.theme.ThemeManager;
import java.nio.file.Path;

public final class AxsesCore {
    public static final String PRODUCT_NAME = "AXSESCLIENT";
    public static final String CODENAME = "GENESIS X";
    public static final String VERSION = "1.0.0";

    private static volatile AxsesCore instance;

    private final Path clientRootDir;
    private final MinecraftAdapter minecraftAdapter;
    private final InputAdapter inputAdapter;

    private final PerformanceProfiler performanceProfiler;
    private final EventManager eventManager;
    private final SettingManager settingManager;
    private final NotificationManager notificationManager;
    private final AnimationManager animationManager;
    private final LocalizationManager localizationManager;
    private final FontManager fontManager;
    private final ThemeManager themeManager;
    private final RenderManager renderManager;
    private final HudManager hudManager;
    private final ModuleManager moduleManager;
    private final KeybindManager keybindManager;
    private final CompatibilityEngine compatibilityEngine;
    private final ConfigManager configManager;
    private final ClientRuntimeState runtimeState;

    private final ClickGuiController clickGuiController;
    private final HudEditorController hudEditorController;
    private final AxsesMainMenuController mainMenuController;

    public AxsesCore(Path gameDir, MinecraftAdapter minecraftAdapter, InputAdapter inputAdapter) {
        this.performanceProfiler = new PerformanceProfiler();
        this.performanceProfiler.markStartupBegin();

        this.clientRootDir = gameDir.resolve("axsesclient");
        this.minecraftAdapter = minecraftAdapter;
        this.inputAdapter = inputAdapter;

        this.eventManager = new EventManager();
        this.settingManager = new SettingManager();
        this.notificationManager = new NotificationManager();
        this.animationManager = new AnimationManager();
        this.localizationManager = new LocalizationManager();
        this.fontManager = new FontManager();
        this.themeManager = new ThemeManager(this.clientRootDir);
        this.renderManager = new RenderManager(this.themeManager, this.fontManager);
        this.hudManager = new HudManager();
        this.moduleManager = new ModuleManager(this.settingManager, this.notificationManager);
        this.keybindManager = new KeybindManager(this.moduleManager);
        this.compatibilityEngine = new CompatibilityEngine();
        this.runtimeState = new ClientRuntimeState(this.clientRootDir);

        // Register all 90 modules & GUI features across 5 modular domain registries
        VisualModulesRegistry.registerAll(
                moduleManager, hudManager, runtimeState, performanceProfiler,
                inputAdapter, themeManager, fontManager, renderManager
        );
        MovementPlayerModulesRegistry.registerAll(moduleManager, hudManager, runtimeState, inputAdapter);
        CombatTrainingModulesRegistry.registerAll(moduleManager, hudManager, runtimeState, themeManager, fontManager, notificationManager);
        WorldUtilityModulesRegistry.registerAll(moduleManager, hudManager, runtimeState, notificationManager);
        this.configManager = new ConfigManager(
                this.clientRootDir,
                moduleManager,
                hudManager,
                themeManager,
                keybindManager,
                animationManager,
                fontManager,
                localizationManager,
                notificationManager,
                compatibilityEngine
        );
        GuiFeaturesRegistry.registerAll(
                moduleManager, hudManager, themeManager, configManager,
                keybindManager, notificationManager, animationManager, fontManager,
                renderManager, compatibilityEngine, inputAdapter
        );

        this.clickGuiController = new ClickGuiController(this);
        this.hudEditorController = new HudEditorController(this);
        this.mainMenuController = new AxsesMainMenuController(this);

        // Wire event listeners
        this.eventManager.subscribe(ClientEvents.MouseClickEvent.class, e -> runtimeState.recordMouseClick(e.getButton()));
        this.eventManager.subscribe(ClientEvents.AttackEvent.class, e -> runtimeState.recordAttack(e.getCooldownProgress(), e.isHitEntity(), e.isCritical()));
        this.eventManager.subscribe(ClientEvents.ScreenshotEvent.class, e -> runtimeState.recordScreenshot(e.getFileName()));

        // Load persisted configuration & generate startup compatibility report
        this.configManager.load("default", minecraftAdapter);
        this.compatibilityEngine.evaluate(minecraftAdapter, inputAdapter, moduleManager, keybindManager);
        this.compatibilityEngine.writeReportToDisk(this.clientRootDir);

        this.performanceProfiler.markStartupComplete();
        instance = this;
    }

    public static AxsesCore getInstance() {
        return instance;
    }

    public void onClientTick() {
        runtimeState.updateTick(minecraftAdapter);
        moduleManager.onTick(minecraftAdapter);
    }

    public void onHudRender(RenderAdapter renderer) {
        performanceProfiler.recordFrame();
        long start = System.nanoTime();
        float dt = animationManager.beginFrame();
        if (!minecraftAdapter.isHudEditorOpen()) {
            hudManager.renderInGameHud(renderer, minecraftAdapter, themeManager, fontManager);
        }
        moduleManager.onRender(renderer, minecraftAdapter, dt);
        if (!minecraftAdapter.isClickGuiOpen()) {
            renderManager.renderNotifications(
                    renderer,
                    notificationManager,
                    dt,
                    animationManager.getAnimationSpeed(),
                    animationManager.isReducedMotion()
            );
        }
        performanceProfiler.recordHudRenderNanos(System.nanoTime() - start);
    }

    public boolean onInGameKeyPress(int keyCode) {
        return keybindManager.handleInGameKeyPress(keyCode, minecraftAdapter);
    }

    public void onInGameMouseClick(int button) {
        eventManager.post(new ClientEvents.MouseClickEvent(button, System.currentTimeMillis()));
    }

    public void onAttack(float cooldownProgress, boolean hitEntity, boolean critical) {
        eventManager.post(new ClientEvents.AttackEvent(cooldownProgress, hitEntity, critical));
    }

    public void onScreenshotSaved(String fileName) {
        eventManager.post(new ClientEvents.ScreenshotEvent(fileName, System.currentTimeMillis()));
    }

    public void shutdown() {
        configManager.saveActive();
        runtimeState.saveWaypointsAndNotes();
    }

    public Path getClientRootDir() { return clientRootDir; }
    public MinecraftAdapter getMinecraftAdapter() { return minecraftAdapter; }
    public InputAdapter getInputAdapter() { return inputAdapter; }
    public PerformanceProfiler getPerformanceProfiler() { return performanceProfiler; }
    public EventManager getEventManager() { return eventManager; }
    public SettingManager getSettingManager() { return settingManager; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public AnimationManager getAnimationManager() { return animationManager; }
    public LocalizationManager getLocalizationManager() { return localizationManager; }
    public FontManager getFontManager() { return fontManager; }
    public ThemeManager getThemeManager() { return themeManager; }
    public RenderManager getRenderManager() { return renderManager; }
    public HudManager getHudManager() { return hudManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public KeybindManager getKeybindManager() { return keybindManager; }
    public CompatibilityEngine getCompatibilityEngine() { return compatibilityEngine; }
    public ConfigManager getConfigManager() { return configManager; }
    public ClientRuntimeState getRuntimeState() { return runtimeState; }
    public ClickGuiController getClickGuiController() { return clickGuiController; }
    public HudEditorController getHudEditorController() { return hudEditorController; }
    public AxsesMainMenuController getMainMenuController() { return mainMenuController; }
}
