### Gradle log: nexus1214  (Sat Oct 24 14:54:51 UTC 2026)
```
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
        client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                              ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Connection.java:27: error: cannot find symbol
        return listener() != null && !listener().getConnection().isDisconnected();
                                                                ^
  symbol:   method isDisconnected()
  location: class Connection
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:41: error: incompatible types: possible lossy conversion from double to int
        return handle().getWindow().getGuiScale();
                                               ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:56: error: cannot find symbol
        return handle().getWindow().isFocused();
                                   ^
  symbol:   method isFocused()
  location: class Window
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:253: error: cannot find symbol
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, vanilla,
                                                       ^
  symbol:   method renderLineBox(PoseStack,VertexConsumer,AABB,float,float,float,float)
  location: class LevelRenderer
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:272: error: cannot find symbol
        buffer.vertex(entry.pose(), (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z))
              ^
  symbol:   method vertex(Matrix4f,float,float,float)
  location: variable buffer of type VertexConsumer
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:275: error: cannot find symbol
        buffer.vertex(entry.pose(), (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z))
              ^
  symbol:   method vertex(Matrix4f,float,float,float)
  location: variable buffer of type VertexConsumer
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
    @Override
    ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:43: error: cannot find symbol
                (int) (Minecraft.getInstance().mouseHandler.xpos() * width
                       ^
  symbol:   variable Minecraft
  location: class Nexus1214Hooks
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:44: error: cannot find symbol
                        / Minecraft.getInstance().getWindow().getScreenWidth()),
                          ^
  symbol:   variable Minecraft
  location: class Nexus1214Hooks
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:45: error: cannot find symbol
                (int) (Minecraft.getInstance().mouseHandler.ypos() * height
                       ^
  symbol:   variable Minecraft
  location: class Nexus1214Hooks
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:46: error: cannot find symbol
                        / Minecraft.getInstance().getWindow().getScreenHeight())));
                          ^
  symbol:   variable Minecraft
  location: class Nexus1214Hooks
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:69: error: cannot find symbol
        boolean inGui = Minecraft.getInstance().screen instanceof NexusScreenBridge;
                        ^
  symbol:   variable Minecraft
  location: class Nexus1214Hooks
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:68: error: cannot find symbol
        return stack.getItem().isEdible();
                              ^
  symbol:   method isEdible()
  location: class Item
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:73: error: cannot find symbol
        return stack.getItem().getFoodProperties(stack, null) == null ? 0
                              ^
  symbol:   method getFoodProperties(ItemStack,<null>)
  location: class Item
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:74: error: cannot find symbol
                : stack.getItem().getFoodProperties(stack, null).nutrition();
                                 ^
  symbol:   method getFoodProperties(ItemStack,<null>)
  location: class Item
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
                stack, net.minecraft.world.entity.MobType.UNDEFINED);
                                                 ^
  symbol:   class MobType
  location: package net.minecraft.world.entity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:118: error: cannot find symbol
        return stack.getItem() instanceof net.minecraft.world.item.TotemItem;
                                                                  ^
  symbol:   class TotemItem
  location: package net.minecraft.world.item
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/mixin/MouseHandlerMixin.java:23: error: cannot find symbol
        Nexus1214Input.noteScroll(vertical);
        ^
  symbol:   variable Nexus1214Input
  location: class MouseHandlerMixin
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
            return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                     ^
  symbol:   variable animationPosition
  location: variable living of type LivingEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
            return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                                               ^
  symbol:   variable animationSpeed
  location: variable living of type LivingEntity
Note: /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Block.java uses or overrides a deprecated API.
--
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
          client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                                ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:41: error: incompatible types: possible lossy conversion from double to int
          return handle().getWindow().getGuiScale();
                                                 ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:43: error: cannot find symbol
                  (int) (Minecraft.getInstance().mouseHandler.xpos() * width
                         ^
    symbol:   variable Minecraft
    location: class Nexus1214Hooks
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:44: error: cannot find symbol
                          / Minecraft.getInstance().getWindow().getScreenWidth()),
                            ^
    symbol:   variable Minecraft
    location: class Nexus1214Hooks
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:45: error: cannot find symbol
                  (int) (Minecraft.getInstance().mouseHandler.ypos() * height
                         ^
    symbol:   variable Minecraft
    location: class Nexus1214Hooks
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:46: error: cannot find symbol
                          / Minecraft.getInstance().getWindow().getScreenHeight())));
                            ^
    symbol:   variable Minecraft
    location: class Nexus1214Hooks
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Hooks.java:69: error: cannot find symbol
          boolean inGui = Minecraft.getInstance().screen instanceof NexusScreenBridge;
                          ^
    symbol:   variable Minecraft
    location: class Nexus1214Hooks
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:108: error: cannot find symbol
                  stack, net.minecraft.world.entity.MobType.UNDEFINED);
                                                   ^
    symbol:   class MobType
    location: package net.minecraft.world.entity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:118: error: cannot find symbol
          return stack.getItem() instanceof net.minecraft.world.item.TotemItem;
                                                                    ^
    symbol:   class TotemItem
    location: package net.minecraft.world.item
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/mixin/MouseHandlerMixin.java:23: error: cannot find symbol
          Nexus1214Input.noteScroll(vertical);
          ^
    symbol:   variable Nexus1214Input
    location: class MouseHandlerMixin
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                       ^
    symbol:   variable animationPosition
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                                                 ^
    symbol:   variable animationSpeed
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
      @Override
      ^
  Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Connection.java:27: error: cannot find symbol
          return listener() != null && !listener().getConnection().isDisconnected();
                                                                  ^
    symbol:   method isDisconnected()
    location: class Connection
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:56: error: cannot find symbol
          return handle().getWindow().isFocused();
                                     ^
    symbol:   method isFocused()
    location: class Window
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:253: error: cannot find symbol
              net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, vanilla,
                                                         ^
    symbol:   method renderLineBox(PoseStack,VertexConsumer,AABB,float,float,float,float)
    location: class LevelRenderer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:272: error: cannot find symbol
          buffer.vertex(entry.pose(), (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z))
                ^
    symbol:   method vertex(Matrix4f,float,float,float)
    location: variable buffer of type VertexConsumer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:275: error: cannot find symbol
          buffer.vertex(entry.pose(), (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z))
                ^
    symbol:   method vertex(Matrix4f,float,float,float)
    location: variable buffer of type VertexConsumer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:68: error: cannot find symbol
          return stack.getItem().isEdible();
                                ^
    symbol:   method isEdible()
    location: class Item
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:73: error: cannot find symbol
          return stack.getItem().getFoodProperties(stack, null) == null ? 0
                                ^
    symbol:   method getFoodProperties(ItemStack,<null>)
    location: class Item
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:74: error: cannot find symbol
                  : stack.getItem().getFoodProperties(stack, null).nutrition();
                                   ^
    symbol:   method getFoodProperties(ItemStack,<null>)
    location: class Item
  21 errors
```

### tail
```
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                       ^
    symbol:   variable animationPosition
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Entity.java:189: error: cannot find symbol
              return new float[]{living.animationPosition, living.animationSpeed, living.attackAnim,
                                                                 ^
    symbol:   variable animationSpeed
    location: variable living of type LivingEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/NexusScreenBridge.java:93: error: method does not override or implement a method from a supertype
      @Override
      ^
  Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Connection.java:27: error: cannot find symbol
          return listener() != null && !listener().getConnection().isDisconnected();
                                                                  ^
    symbol:   method isDisconnected()
    location: class Connection
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:56: error: cannot find symbol
          return handle().getWindow().isFocused();
                                     ^
    symbol:   method isFocused()
    location: class Window
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:253: error: cannot find symbol
              net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, vanilla,
                                                         ^
    symbol:   method renderLineBox(PoseStack,VertexConsumer,AABB,float,float,float,float)
    location: class LevelRenderer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:272: error: cannot find symbol
          buffer.vertex(entry.pose(), (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z))
                ^
    symbol:   method vertex(Matrix4f,float,float,float)
    location: variable buffer of type VertexConsumer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Renderer.java:275: error: cannot find symbol
          buffer.vertex(entry.pose(), (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z))
                ^
    symbol:   method vertex(Matrix4f,float,float,float)
    location: variable buffer of type VertexConsumer
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:68: error: cannot find symbol
          return stack.getItem().isEdible();
                                ^
    symbol:   method isEdible()
    location: class Item
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:73: error: cannot find symbol
          return stack.getItem().getFoodProperties(stack, null) == null ? 0
                                ^
    symbol:   method getFoodProperties(ItemStack,<null>)
    location: class Item
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214ItemStack.java:74: error: cannot find symbol
                  : stack.getItem().getFoodProperties(stack, null).nutrition();
                                   ^
    symbol:   method getFoodProperties(ItemStack,<null>)
    location: class Item
  21 errors
* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
BUILD FAILED in 34s
1 actionable task: 1 executed
```
