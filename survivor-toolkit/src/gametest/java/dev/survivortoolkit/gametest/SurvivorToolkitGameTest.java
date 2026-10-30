package dev.survivortoolkit.gametest;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import dev.survivortoolkit.client.WorldPolicy;
import dev.survivortoolkit.client.gui.ToolkitScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/** Isolated test mod: the commands below only operate on Fabric's disposable test world. */
@SuppressWarnings("UnstableApiUsage")
public final class SurvivorToolkitGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksDownload();
            context.waitFor(client -> client.player != null && client.player.isOnGround());
            require(context.computeOnClient(WorldPolicy::privateLocal), "private world was not recognized");
            testMenu(context);
            testWarden(context, world);
            testFreecamAndLan(context, world);
        }
        context.waitFor(client -> client.world == null);
        try (TestSingleplayerContext exitWorld = context.worldBuilder().create()) {
            exitWorld.getClientWorld().waitForChunksDownload();
            context.waitFor(client -> client.player != null && client.player.isOnGround());
            context.waitTicks(10);
            context.getInput().pressKey(SurvivorToolkitClient.FREECAM_KEY);
            context.waitFor(client -> SurvivorToolkitClient.FREECAM.active());
        }
        context.waitFor(client -> client.world == null);
        require(!context.computeOnClient(client -> SurvivorToolkitClient.FREECAM.active()), "camera remained active after leaving world");
        System.out.println("SURVIVOR_TOOLKIT_GAME_TEST_PASS");
    }

    private void testMenu(ClientGameTestContext context) {
        context.getInput().holdKey(SurvivorToolkitClient.MENU_KEY);
        context.waitForScreen(ToolkitScreen.class);
        context.waitTicks(5);
        require(context.computeOnClient(client -> client.currentScreen instanceof ToolkitScreen), "held menu key closed the menu");
        context.getInput().releaseKey(SurvivorToolkitClient.MENU_KEY);
        context.getInput().pressKey(SurvivorToolkitClient.MENU_KEY);
        context.waitForScreen(null);
        context.getInput().pressKey(SurvivorToolkitClient.MENU_KEY);
        context.waitForScreen(ToolkitScreen.class);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);

        // Exercise the calculator's actual widgets in the disposable private world.
        context.runOnClient(client -> SurvivorToolkitClient.config().auctionEnabled = true);
        context.getInput().pressKey(SurvivorToolkitClient.MENU_KEY);
        context.waitForScreen(ToolkitScreen.class);
        context.runOnClient(client -> {
            ToolkitScreen screen = (ToolkitScreen) client.currentScreen;
            ClickableWidget auction = (ClickableWidget) screen.children().get(2);
            Click tab = new Click(auction.getX() + 3, auction.getY() + 3, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
            require(screen.mouseClicked(tab, false), "auction tab did not respond");
            screen.mouseReleased(tab);
            for (int i = 0; i < 12 && screen.children().stream().noneMatch(x -> x instanceof TextFieldWidget); i++)
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
            TextFieldWidget field = (TextFieldWidget) screen.children().stream()
                    .filter(x -> x instanceof TextFieldWidget).findFirst().orElseThrow();
            Click focus = new Click(field.getX() + 4, field.getY() + 4, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(focus, false);
            screen.mouseReleased(focus);
            screen.charTyped(new CharInput('x', 0));
            require(field.getText().contains("x"), "calculator field could not be focused and typed into");
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.getInput().pressKey(options -> options.inventoryKey);
        context.waitForScreen(InventoryScreen.class);
        context.getInput().pressKey(SurvivorToolkitClient.MENU_KEY);
        require(context.computeOnClient(client -> client.currentScreen instanceof InventoryScreen), "menu key replaced the inventory screen");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);

        context.runOnClient(client -> {
            SurvivorToolkitClient.MENU_KEY.setBoundKey(InputUtil.fromKeyCode(new KeyInput(GLFW.GLFW_KEY_P, 0, 0)));
            KeyBinding.updateKeysByCode();
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        context.waitTicks(2);
        require(context.computeOnClient(client -> client.currentScreen == null), "old menu key still opened the screen after rebinding");
        context.getInput().pressKey(GLFW.GLFW_KEY_P);
        context.waitForScreen(ToolkitScreen.class);
        context.getInput().pressKey(GLFW.GLFW_KEY_P);
        context.waitForScreen(null);
        context.runOnClient(client -> {
            SurvivorToolkitClient.MENU_KEY.setBoundKey(SurvivorToolkitClient.MENU_KEY.getDefaultKey());
            KeyBinding.updateKeysByCode();
        });

        context.getInput().resizeWindow(640, 360);
        context.getInput().pressKey(SurvivorToolkitClient.MENU_KEY);
        context.waitForScreen(ToolkitScreen.class);
        context.getInput().resizeWindow(1000, 500);
        context.waitTicks(2);
        require(context.computeOnClient(client -> client.currentScreen instanceof ToolkitScreen), "resizing closed the menu");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.getInput().resizeWindow(854, 480);
    }

    private void testWarden(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            SurvivorToolkitClient.config().wardenEnabled = true;
            SurvivorToolkitClient.config().wardenRadius = 16;
            SurvivorToolkitClient.config().warningSoundEnabled = false;
        });
        world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
        world.getServer().runCommand("execute at @p run fill ~-2 ~ ~4 ~2 ~4 ~4 minecraft:stone");
        world.getServer().runCommand("execute at @p run summon minecraft:warden ~ ~ ~8 {NoAI:1b,Silent:1b,NoGravity:1b}");
        context.waitFor(client -> !client.world.getEntitiesByClass(WardenEntity.class,
                client.player.getBoundingBox().expand(16), entity -> true).isEmpty());
        context.waitTicks(20);
        require(!context.computeOnClient(client -> SurvivorToolkitClient.WARDEN.visible()), "Warden alert triggered through a wall");
        world.getServer().runCommand("execute at @p run fill ~-2 ~ ~4 ~2 ~4 ~4 minecraft:air");
        context.waitFor(client -> SurvivorToolkitClient.WARDEN.visible());
        world.getServer().runCommand("kill @e[type=minecraft:warden]");
        context.runOnClient(client -> SurvivorToolkitClient.config().wardenEnabled = false);
    }

    private void testFreecamAndLan(ClientGameTestContext context, TestSingleplayerContext world) {
        context.waitFor(client -> client.player.isOnGround());
        context.getInput().pressKey(SurvivorToolkitClient.FREECAM_KEY);
        context.waitFor(client -> SurvivorToolkitClient.FREECAM.active());
        Vec3d bodyBefore = context.computeOnClient(client -> client.player.getEntityPos());
        Vec3d cameraBefore = context.computeOnClient(client -> SurvivorToolkitClient.FREECAM.position());
        context.getInput().holdKey(options -> options.forwardKey);
        context.waitTicks(6);
        context.getInput().releaseKey(options -> options.forwardKey);
        context.waitTicks(2);
        require(context.computeOnClient(client -> SurvivorToolkitClient.FREECAM.active()), "camera unexpectedly stopped");
        require(context.computeOnClient(client -> client.player.getEntityPos().squaredDistanceTo(bodyBefore) < 0.01), "camera input moved the player");
        require(context.computeOnClient(client -> SurvivorToolkitClient.FREECAM.position().squaredDistanceTo(cameraBefore) > 0.04), "camera did not move");

        BlockPos target = context.computeOnClient(client -> client.player.getBlockPos().add(0, 1, 3));
        world.getServer().runCommand("setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:dirt");
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:cobblestone 5");
        context.waitFor(client -> client.world.getBlockState(target).isOf(Blocks.DIRT)
                && client.player.getMainHandStack().isOf(Items.COBBLESTONE));
        context.waitFor(client -> client.crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(target));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(50);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(context.computeOnClient(client -> client.world.getBlockState(target).isOf(Blocks.DIRT)), "attack broke a block during freecam");
        int count = context.computeOnClient(client -> client.player.getMainHandStack().getCount());
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(10);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        require(context.computeOnClient(client -> client.player.getMainHandStack().getCount() == count), "use consumed an item during freecam");

        world.getServer().runCommand("kill @p");
        context.waitFor(client -> client.player != null && !client.player.isAlive());
        context.waitFor(client -> !SurvivorToolkitClient.FREECAM.active());
        context.waitTicks(25); // vanilla death-screen button has a short delay
        // Standard client respawn, exclusively inside the test world.
        context.runOnClient(client -> { client.player.requestRespawn(); client.setScreen(null); });
        context.waitFor(client -> client.player != null && client.player.isAlive() && client.player.isOnGround(), 400);
        context.waitTicks(10);
        context.getInput().pressKey(SurvivorToolkitClient.FREECAM_KEY);
        context.waitFor(client -> SurvivorToolkitClient.FREECAM.active());
        world.getServer().runOnServer(server -> {
            if (!server.openToLan(null, false, 0)) throw new AssertionError("failed to open test world to LAN");
        });
        context.waitFor(client -> !SurvivorToolkitClient.FREECAM.active());
        require(!context.computeOnClient(WorldPolicy::privateLocal), "LAN world treated as private");
        require(!context.computeOnClient(client -> WorldPolicy.allowed(client, Set.of())), "LAN world bypassed permission gate");
        context.getInput().pressKey(SurvivorToolkitClient.FREECAM_KEY);
        context.waitTicks(5);
        require(!context.computeOnClient(client -> SurvivorToolkitClient.FREECAM.active()), "freecam enabled in a LAN world");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
