### Gradle log: nexus1214  (Sat Oct 24 14:57:16 UTC 2026)
```
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
        client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                              ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
    @Override
    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
        return 1.0f + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(stack, null);
                                                                            ^
  symbol:   method getDamageBonus(ItemStack,<null>)
  location: class EnchantmentHelper
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
            return new float[]{living.walkAnimation.position(), living.animationSpeed, living.attackAnim,
                                                                      ^
  symbol:   variable animationSpeed
  location: variable living of type LivingEntity
Note: /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Block.java uses or overrides a deprecated API.
--
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
          client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                                ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.walkAnimation.position(), living.animationSpeed, living.attackAnim,
                                                                        ^
    symbol:   variable animationSpeed
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
      @Override
      ^
  Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
          return 1.0f + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(stack, null);
                                                                              ^
    symbol:   method getDamageBonus(ItemStack,<null>)
    location: class EnchantmentHelper
  4 errors
```

### tail
```
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Using of the official minecraft mappings is at your own risk!
Please make sure to read and understand the following license:
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# (c) 2020 Microsoft Corporation. These mappings are provided "as-is" and you bear the risk of using them. You may copy and use the mappings for development purposes, but you may not redistribute the mappings complete and unmodified. Microsoft makes no warranties, express or implied, with respect to the mappings provided here.  Use and modification of this document or the source code (in any form) of Minecraft: Java Edition is governed by the Minecraft End User License Agreement available at https://account.mojang.com/documents/minecraft_eula.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Not publishing sources jar as it was not created by the java plugin. Use java.withSourcesJar() to fix.
> Task :compileJava
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
        client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                              ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
    @Override
    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
        return 1.0f + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(stack, null);
                                                                            ^
  symbol:   method getDamageBonus(ItemStack,<null>)
  location: class EnchantmentHelper
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
            return new float[]{living.walkAnimation.position(), living.animationSpeed, living.attackAnim,
                                                                      ^
  symbol:   variable animationSpeed
  location: variable living of type LivingEntity
Note: /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Block.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
4 errors
> Task :compileJava FAILED
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1792853794021.json
[Incubating] Problems report is available at: file:///home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/build/reports/problems/problems-report.html
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':compileJava' (registered by plugin class 'org.gradle.api.plugins.JavaBasePlugin').
> Compilation failed; see the compiler output below.
  Note: /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Block.java uses or overrides a deprecated API.
  Note: Recompile with -Xlint:deprecation for details.
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
          client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                                ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.walkAnimation.position(), living.animationSpeed, living.attackAnim,
                                                                        ^
    symbol:   variable animationSpeed
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
      @Override
      ^
  Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
          return 1.0f + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(stack, null);
                                                                              ^
    symbol:   method getDamageBonus(ItemStack,<null>)
    location: class EnchantmentHelper
  4 errors
* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
BUILD FAILED in 47s
1 actionable task: 1 executed
```
