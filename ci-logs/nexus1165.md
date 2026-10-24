### Gradle log: nexus1165  (Sat Oct 24 14:18:47 UTC 2026)
```
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Platform.java:68: error: package Hooks does not exist
        Hooks.HOOKS.install();
             ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Platform.java:73: error: package Hooks does not exist
        Hooks.HOOKS.uninstall();
             ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Platform.java:78: error: package Hooks does not exist
        return Hooks.HOOKS.installed() && Nexus.isInitialized();
                    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                           ^
  symbol:   variable x1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                   ^
  symbol:   variable y1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                           ^
  symbol:   variable z1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                   ^
  symbol:   variable x2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                           ^
  symbol:   variable y2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:65: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                                   ^
  symbol:   variable z2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:187: error: cannot find symbol
        return ready() && self().isInWater();
                                ^
  symbol:   method isInWater()
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:265: error: cannot find symbol
                    return instance.getEffectName().getString();
                                   ^
  symbol:   method getEffectName()
  location: variable instance of type StatusEffectInstance
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:372: error: cannot find symbol
                self().sendCommand(message.substring(1), "");
                      ^
  symbol:   method sendCommand(String,String)
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Player.java:382: error: cannot find symbol
            self().sendCommand(command.startsWith("/") ? command.substring(1) : command, "");
                  ^
  symbol:   method sendCommand(command.st[...]mmand,String)
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165World.java:250: error: cannot find symbol
            for (net.minecraft.text.Text line : sign.text) {
                                                    ^
  symbol:   variable text
  location: variable sign of type SignBlockEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:236: error: cannot find symbol
        return self() == null ? 0 : self().container.syncId;
                                          ^
  symbol:   variable container
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:241: error: cannot find symbol
        return self() == null ? 0 : self().container.slots.size() - MAIN_SIZE;
                                          ^
  symbol:   variable container
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:246: error: cannot find symbol
        if (self() == null || slot < 0 || slot >= self().container.slots.size()) {
                                                        ^
  symbol:   variable container
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:249: error: cannot find symbol
        return wrap(self().container.getSlot(slot).getStack());
                          ^
  symbol:   variable container
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Inventory.java:271: error: cannot find symbol
        client.handle().interactionManager.clickSlot(self().container.syncId, slot, button, type, self());
                                                           ^
  symbol:   variable container
  location: class ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:56: error: bad operand types for binary operator '!='
        return options().ao != 0;
                            ^
  first type:  AoMode
  second type: int
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:81: error: cannot find symbol
        return options().clouds;
                        ^
  symbol:   variable clouds
  location: class GameOptions
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:86: error: fpsCounter has private access in MinecraftClient
        return (int) client.handle().fpsCounter.getFps();
                                    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Options.java:86: error: int cannot be dereferenced
        return (int) client.handle().fpsCounter.getFps();
                                               ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Input.java:46: error: package net.minecraft.client.options does not exist
                ? net.minecraft.client.options.KeyBinding.isKeyPressed(window, keyCode)
                                              ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Input.java:57: error: package net.minecraft.client.options does not exist
        return net.minecraft.client.options.KeyBinding.isKeyPressed(
                                           ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Input.java:63: error: package net.minecraft.client.options does not exist
        net.minecraft.client.options.KeyBinding.setKeyState(
                                    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Input.java:69: error: package net.minecraft.client.options does not exist
        net.minecraft.client.options.KeyBinding.unpressAll();
                                    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Screens.java:106: error: cannot find symbol
            handle().openScreen(handle().player.playerScreenHandler.createScreen(
                                                                   ^
  symbol:   method createScreen(PlayerInventory,ClientPlayerEntity)
  location: variable playerScreenHandler of type PlayerScreenHandler
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Screens.java:138: error: cannot find symbol
        if (handle().currentScreen instanceof net.minecraft.client.gui.screen.ingame.EnchantingScreen) {
                                                                                    ^
  symbol:   class EnchantingScreen
  location: package net.minecraft.client.gui.screen.ingame
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Screens.java:139: error: cannot find symbol
            ((net.minecraft.client.gui.screen.ingame.EnchantingScreen) handle().currentScreen)
                                                    ^
  symbol:   class EnchantingScreen
  location: package net.minecraft.client.gui.screen.ingame
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Interaction.java:63: error: incompatible types: World cannot be converted to ClientWorld
        return handle().interactionManager.interactBlock(player, player.world,
                                                                       ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Interaction.java:105: error: cannot find symbol
        return handle().player == null ? -1 : handle().player.container.syncId;
                                                             ^
  symbol:   variable container
  location: variable player of type ClientPlayerEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Resources.java:60: error: cannot find symbol
            handle().worldRenderer.setBlockBreakingProgress(
                                  ^
  symbol:   method setBlockBreakingProgress(int,BlockPos,int)
  location: variable worldRenderer of type WorldRenderer
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Resources.java:94: error: cannot find symbol
            net.minecraft.client.texture.ScreenshotUtils.saveScreenshot(directory,
                                        ^
  symbol:   class ScreenshotUtils
  location: package net.minecraft.client.texture
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Connection.java:33: error: cannot find symbol
                && !listener.getConnection().isDisconnected();
                                            ^
  symbol:   method isDisconnected()
  location: class ClientConnection
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Window.java:46: error: cannot find symbol
        return window().calculateScaleFactor(0, handle().isFullScreen());
                                                        ^
  symbol:   method isFullScreen()
  location: class MinecraftClient
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Window.java:61: error: cannot find symbol
        return window().isFocused();
                       ^
  symbol:   method isFocused()
  location: class Window
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Window.java:66: error: cannot find symbol
        return handle().isFullScreen();
                       ^
  symbol:   method isFullScreen()
  location: class MinecraftClient
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Renderer.java:69: error: method fillGradient in class DrawableHelper cannot be applied to given types;
        net.minecraft.client.gui.DrawableHelper.fillGradient(pose(), (int) x, (int) y,
                                               ^
  required: MatrixStack,int,int,int,int,int,int
  found:    MatrixStack,int,int,int,int,int,int
  reason: fillGradient(MatrixStack,int,int,int,int,int,int) has protected access in DrawableHelper
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165ScreenHelper.java:14: error: package net.minecraft.client.options does not exist
            net.minecraft.client.options.KeyBinding.setKeyState(
                                        ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165ItemStack.java:113: error: cannot find symbol
        return stack.getItem().isIn(net.minecraft.tag.TagRegistry.item(
                                                     ^
  symbol:   class TagRegistry
  location: package net.minecraft.tag
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                           ^
  symbol:   variable x1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                   ^
  symbol:   variable y1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                           ^
  symbol:   variable z1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                   ^
  symbol:   variable x2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                           ^
  symbol:   variable y2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                                   ^
  symbol:   variable z2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:163: error: cannot find symbol
        return entity.isInWater();
                     ^
  symbol:   method isInWater()
  location: variable entity of type Entity
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
```

### tail
```
  reason: fillGradient(MatrixStack,int,int,int,int,int,int) has protected access in DrawableHelper
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165ScreenHelper.java:14: error: package net.minecraft.client.options does not exist
            net.minecraft.client.options.KeyBinding.setKeyState(
                                        ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165ItemStack.java:113: error: cannot find symbol
        return stack.getItem().isIn(net.minecraft.tag.TagRegistry.item(
                                                     ^
  symbol:   class TagRegistry
  location: package net.minecraft.tag
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                           ^
  symbol:   variable x1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                   ^
  symbol:   variable y1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                           ^
  symbol:   variable z1
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                   ^
  symbol:   variable x2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                           ^
  symbol:   variable y2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:62: error: cannot find symbol
        return new Box3(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
                                                                   ^
  symbol:   variable z2
  location: variable box of type Box
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1165/src/nexus/adapter/v1165/Nexus1165Entity.java:163: error: cannot find symbol
        return entity.isInWater();
                     ^
  symbol:   method isInWater()
  location: variable entity of type Entity
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
48 errors
> Task :compileJava FAILED
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1792851491280.json
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':compileJava'.
> Compilation failed; see the compiler error output for details.
* Try:
> Run with --info option to get more log output.
> Run with --scan to get full insights.
Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
BUILD FAILED in 40s
1 actionable task: 1 executed
```
