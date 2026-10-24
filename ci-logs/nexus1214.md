### Gradle log: nexus1214  (Sat Oct 24 14:37:31 UTC 2026)
```
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Resources.java:34: error: incompatible types: Optional<Reference<SoundEvent>> cannot be converted to SoundEvent
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
                                                            ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Resources.java:116: error: cannot find symbol
        net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(vanilla)
                                                              ^
  symbol:   method getEnchantments(ItemStack)
  location: class EnchantmentHelper
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214World.java:214: error: cannot find symbol
        Registry<Biome> registry = level().registryAccess().registryOrThrow(Registries.BIOME);
                                                           ^
  symbol:   method registryOrThrow(ResourceKey<Registry<Biome>>)
  location: interface RegistryAccess
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
        client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                              ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:36: error: incompatible types: double cannot be converted to Integer
        handle().options.fov().set(value);
                                   ^
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:81: error: cannot find symbol
        return handle().options.renderClouds().get() != net.minecraft.client.CloudStatus.OFF;
                               ^
  symbol:   method renderClouds()
  location: variable options of type Options
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:106: error: cannot find symbol
        handle().options.setSoundCategoryVolume(net.minecraft.sounds.SoundSource.MASTER, value);
                        ^
  symbol:   method setSoundCategoryVolume(SoundSource,float)
  location: variable options of type Options
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:36: error: cannot find symbol
        return handle().mouseHandler.getScrollXAmount() + handle().mouseHandler.getScrollYAmount();
                                    ^
  symbol:   method getScrollXAmount()
  location: variable mouseHandler of type MouseHandler
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:36: error: cannot find symbol
        return handle().mouseHandler.getScrollXAmount() + handle().mouseHandler.getScrollYAmount();
                                                                               ^
  symbol:   method getScrollYAmount()
  location: variable mouseHandler of type MouseHandler
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:45: error: cannot find symbol
        return net.minecraft.client.InputConstants.isKeyDown(handle().getWindow().getWindow(), keyCode);
                                   ^
  symbol:   class InputConstants
  location: package net.minecraft.client
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:55: error: cannot find symbol
        return handle().mouseHandler.isMouseDown();
                                    ^
  symbol:   method isMouseDown()
  location: variable mouseHandler of type MouseHandler
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:97: error: cannot find symbol
        handle().keyboardHandler.releaseAll();
                                ^
  symbol:   method releaseAll()
  location: variable keyboardHandler of type KeyboardHandler
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Chat.java:52: error: cannot find symbol
        for (GuiMessage message : handle().gui.getChat().getAllMessages()) {
                                                        ^
  symbol:   method getAllMessages()
  location: class ChatComponent
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:124: error: cannot find symbol
                    entity.setMessage(i, Component.literal(lines[i]));
                          ^
  symbol:   method setMessage(int,MutableComponent)
  location: variable entity of type SignBlockEntity
/home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:125: error: cannot find symbol
                    entity.setMessage(i + 4, Component.literal(lines[i]));
                          ^
  symbol:   method setMessage(int,MutableComponent)
  location: variable entity of type SignBlockEntity
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
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Resources.java:34: error: incompatible types: Optional<Reference<SoundEvent>> cannot be converted to SoundEvent
          SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
                                                              ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Inventory.java:288: error: incompatible types: nexus.api.IInventory.ClickType cannot be converted to net.minecraft.world.inventory.ClickType
          client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                                                                                                ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:36: error: incompatible types: double cannot be converted to Integer
          handle().options.fov().set(value);
                                     ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Window.java:41: error: incompatible types: possible lossy conversion from double to int
          return handle().getWindow().getGuiScale();
                                                 ^
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:45: error: cannot find symbol
          return net.minecraft.client.InputConstants.isKeyDown(handle().getWindow().getWindow(), keyCode);
                                     ^
    symbol:   class InputConstants
    location: package net.minecraft.client
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
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Resources.java:116: error: cannot find symbol
          net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(vanilla)
                                                                ^
    symbol:   method getEnchantments(ItemStack)
    location: class EnchantmentHelper
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214World.java:214: error: cannot find symbol
          Registry<Biome> registry = level().registryAccess().registryOrThrow(Registries.BIOME);
                                                             ^
    symbol:   method registryOrThrow(ResourceKey<Registry<Biome>>)
    location: interface RegistryAccess
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:81: error: cannot find symbol
          return handle().options.renderClouds().get() != net.minecraft.client.CloudStatus.OFF;
                                 ^
    symbol:   method renderClouds()
    location: variable options of type Options
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Options.java:106: error: cannot find symbol
          handle().options.setSoundCategoryVolume(net.minecraft.sounds.SoundSource.MASTER, value);
                          ^
    symbol:   method setSoundCategoryVolume(SoundSource,float)
    location: variable options of type Options
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:36: error: cannot find symbol
          return handle().mouseHandler.getScrollXAmount() + handle().mouseHandler.getScrollYAmount();
                                      ^
    symbol:   method getScrollXAmount()
    location: variable mouseHandler of type MouseHandler
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:36: error: cannot find symbol
          return handle().mouseHandler.getScrollXAmount() + handle().mouseHandler.getScrollYAmount();
                                                                                 ^
    symbol:   method getScrollYAmount()
    location: variable mouseHandler of type MouseHandler
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:55: error: cannot find symbol
          return handle().mouseHandler.isMouseDown();
                                      ^
    symbol:   method isMouseDown()
    location: variable mouseHandler of type MouseHandler
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Input.java:97: error: cannot find symbol
          handle().keyboardHandler.releaseAll();
                                  ^
    symbol:   method releaseAll()
    location: variable keyboardHandler of type KeyboardHandler
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Chat.java:52: error: cannot find symbol
          for (GuiMessage message : handle().gui.getChat().getAllMessages()) {
                                                          ^
    symbol:   method getAllMessages()
    location: class ChatComponent
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:124: error: cannot find symbol
                      entity.setMessage(i, Component.literal(lines[i]));
                            ^
    symbol:   method setMessage(int,MutableComponent)
    location: variable entity of type SignBlockEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:125: error: cannot find symbol
                      entity.setMessage(i + 4, Component.literal(lines[i]));
                            ^
    symbol:   method setMessage(int,MutableComponent)
    location: variable entity of type SignBlockEntity
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
  34 errors
```

### tail
```
          for (GuiMessage message : handle().gui.getChat().getAllMessages()) {
                                                          ^
    symbol:   method getAllMessages()
    location: class ChatComponent
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:124: error: cannot find symbol
                      entity.setMessage(i, Component.literal(lines[i]));
                            ^
    symbol:   method setMessage(int,MutableComponent)
    location: variable entity of type SignBlockEntity
  /home/runner/work/WinLauncher/WinLauncher/NexusOmegaX/adapters/nexus1214/src/nexus/adapter/v1214/Nexus1214Screens.java:125: error: cannot find symbol
                      entity.setMessage(i + 4, Component.literal(lines[i]));
                            ^
    symbol:   method setMessage(int,MutableComponent)
    location: variable entity of type SignBlockEntity
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
  34 errors
* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
BUILD FAILED in 37s
1 actionable task: 1 executed
```
