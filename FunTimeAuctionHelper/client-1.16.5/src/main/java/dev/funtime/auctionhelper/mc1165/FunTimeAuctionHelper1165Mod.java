package dev.funtime.auctionhelper.mc1165;

import dev.funtime.auctionhelper.HelperCore;
import dev.funtime.auctionhelper.adapter.RegexAuctionAdapter;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.engine.AutomationState;
import dev.funtime.auctionhelper.engine.AuctionIntegration;
import dev.funtime.auctionhelper.model.ScreenSnapshot;
import dev.funtime.auctionhelper.model.SlotSnapshot;
import dev.funtime.auctionhelper.ui.HelperUi;
import dev.funtime.auctionhelper.util.KeyCodes;
import dev.funtime.auctionhelper.util.SelfTestReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point for Minecraft 1.16.5.
 *
 * <p>Key handling is done with raw GLFW key codes (physical keys), so the GUI opens with the
 * key labelled <b>Ё</b> / {@code `} / {@code ~} on both Russian and English layouts, and it is
 * ignored while any screen (chat included) is open.</p>
 */
@Environment(EnvType.CLIENT)
public final class FunTimeAuctionHelper1165Mod implements ClientModInitializer {
    private static HelperCore core;
    private static boolean keyDownOpen;
    private static boolean keyDownEmergency;
    private static SelfTest selfTest;

    @Override
    public void onInitializeClient() {
        Platform1165 platform = new Platform1165();
        // FunTime does not publish an authorized automation API that this project could verify,
        // therefore the default integration never trades. Automation must stay unavailable.
        AuctionIntegration integration = new AuctionIntegration.None(
                "FunTime: no documented automation API is available to this mod");
        core = new HelperCore(platform, integration);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (core != null) {
                core.onWorldJoined(platform.getServerName(), System.currentTimeMillis());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (core != null) {
                core.onDisconnected(System.currentTimeMillis());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (core != null) {
                core.onShutdown();
            }
        });

        if (Boolean.getBoolean("funtimeauctionhelper.selftest")) {
            selfTest = new SelfTest();
        }
    }

    private void onEndTick(Minecraft client) {
        if (core == null || client == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            core.onClientTick(now);
            pollKeys(client);
            if (selfTest != null) {
                selfTest.tick(client, core);
            }
        } catch (RuntimeException error) {
            core.handleError("client tick", error);
        }
    }

    private void pollKeys(Minecraft client) {
        long window = client.getWindow().getWindow();
        HelperConfig config = core.getConfig();
        boolean open = GLFW.glfwGetKey(window, config.ui.openKeyCode) == GLFW.GLFW_PRESS;
        boolean emergency = GLFW.glfwGetKey(window, config.ui.emergencyKeyCode) == GLFW.GLFW_PRESS;
        // Never react while chat or any other screen is open, and never while the GUI itself is open.
        boolean gameLikeState = client.screen == null;
        if (open && !keyDownOpen && gameLikeState) {
            core.getPlatform().openHelperScreen();
        }
        if (emergency && !keyDownEmergency && (client.screen == null || core.getPlatform().isHelperScreenOpen())) {
            core.emergencyStop("emergency hotkey");
        }
        keyDownOpen = open;
        keyDownEmergency = emergency;
    }

    /** In-game verification: runs only when {@code -Dfuntimeauctionhelper.selftest=true}. */
    private static final class SelfTest {
        private int step;
        private final SelfTestReport report =
                new SelfTestReport(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(), "1.16.5");

        void tick(Minecraft client, HelperCore core) {
            step++;
            try {
                switch (step) {
                    case 5:
                        check(!core.getAutomation().isMasterEnabled(), "automation starts OFF");
                        core.getPlatform().openHelperScreen();
                        check(client.screen instanceof HelperScreen1165, "GUI opened by the Ё key binding path");
                        break;
                    case 7:
                        HelperUi ui = core.getUi();
                        ui.keyPressed(core.getConfig().ui.openKeyCode);
                        check(!(client.screen instanceof HelperScreen1165), "GUI closed with the open key");
                        core.getPlatform().openHelperScreen();
                        check(client.screen instanceof HelperScreen1165, "GUI reopened");
                        break;
                    case 9:
                        core.getUi().keyPressed(KeyCodes.KEY_ESCAPE);
                        check(!(client.screen instanceof HelperScreen1165), "GUI closed with ESCAPE");
                        break;
                    case 11:
                        check(!core.getAutomation().setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION,
                                "selftest"), "authorized automation refused without a server API");
                        check(core.getAutomation().setMode(AutomationState.Mode.MANUAL_ASSISTANT, "selftest"),
                                "manual assistant mode selectable");
                        check(!core.getAutomation().setMaster(true, "selftest"),
                                "master switch refused while no API is available");
                        break;
                    case 13: {
                        int cancelled = core.emergencyStop("selftest");
                        check(core.getAutomation().getEmergencyStopCount() == 1,
                                "emergency stop counted (" + cancelled + " pending cancelled)");
                        check(!core.getAutomation().isMasterEnabled(), "emergency stop left automation off");
                        break;
                    }
                    case 15: {
                        HelperConfig.AdapterProfileConfig profile = core.getConfig().adapterProfiles.get(0);
                        RegexAuctionAdapter adapter = new RegexAuctionAdapter(profile);
                        List<String> lore = new ArrayList<String>(Arrays.asList("Цена: 1 250", "Кол-во: 64"));
                        SlotSnapshot slot = new SlotSnapshot(10, "minecraft:diamond", "Алмаз", 64, lore);
                        ScreenSnapshot snapshot = new ScreenSnapshot("Аукцион", "test", Arrays.asList(slot),
                                System.currentTimeMillis(), "selftest", true);
                        int parsed = adapter.parse(snapshot).getListings().size();
                        check(parsed == 1, "price parser read " + parsed + " offer(s) from a sample lore");
                        core.getPriceHistory().addAll(
                                java.util.Collections.singletonList(
                                        dev.funtime.auctionhelper.model.PriceObservation.of(
                                                adapter.parse(snapshot).getListings().get(0))));
                        check(core.getPriceHistory().size() > 0, "price history recorded locally");
                        core.saveAll();
                        break;
                    }
                    case 17:
                        core.getConfigManager().load();
                        check(!core.getConfig().automation.masterEnabled,
                                "automation stays OFF after reloading the config");
                        report.detail("mode", core.getAutomation().getMode().name());
                        report.detail("priceHistoryPoints", core.getPriceHistory().size());
                        report.detail("adapterProfiles", core.getConfig().adapterProfiles.size());
                        report.pass("all self test checks passed", client::stop);
                        break;
                    default:
                        break;
                }
            } catch (Throwable error) {
                report.fail(error, client::stop);
            }
        }

        private void check(boolean condition, String description) {
            if (!condition) {
                throw new IllegalStateException("check failed: " + description);
            }
            report.check(description);
        }
    }
}
