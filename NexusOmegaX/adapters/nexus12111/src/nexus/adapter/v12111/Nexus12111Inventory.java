package nexus.adapter.v12111;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import nexus.api.IInventory;
import nexus.api.IItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@link IInventory} implementation for Minecraft 1.21.11. */
public final class Nexus12111Inventory implements IInventory {

    private final Nexus12111Client client;
    private final List<ItemStack> slots = new ArrayList<ItemStack>(36);

    Nexus12111Inventory(Nexus12111Client client) {
        this.client = client;
        for (int i = 0; i < 36; i++) {
            slots.add(ItemStack.EMPTY);
        }
    }

    /** Refreshes the cached slot list (called once per tick by the hooks). */
    public void refresh() {
        LocalPlayer player = client.localPlayer();
        if (player == null) {
            return;
        }
        for (int i = 0; i < 36; i++) {
            slots.set(i, i < player.getInventory().getContainerSize()
                    ? player.getInventory().getItem(i) : ItemStack.EMPTY);
        }
    }

    private net.minecraft.world.entity.player.Inventory vanilla() {
        return client.localPlayer().getInventory();
    }

    @Override
    public IItemStack stack(int slot) {
        if (client.localPlayer() == null) {
            return new Nexus12111ItemStack(ItemStack.EMPTY);
        }
        return new Nexus12111ItemStack(vanilla().getItem(slot));
    }

    @Override
    public int mainSize() {
        return MAIN_SIZE;
    }

    @Override
    public List<IItemStack> mainStacks() {
        List<IItemStack> result = new ArrayList<IItemStack>(MAIN_SIZE);
        for (int i = 0; i < MAIN_SIZE; i++) {
            result.add(stack(i));
        }
        return result;
    }

    @Override
    public IItemStack heldItem() {
        return stack(selectedSlot());
    }

    @Override
    public IItemStack offhandItem() {
        return stack(OFFHAND_SLOT);
    }

    @Override
    public IItemStack helmet() {
        return stack(HELMET_SLOT);
    }

    @Override
    public IItemStack chestplate() {
        return stack(CHESTPLATE_SLOT);
    }

    @Override
    public IItemStack leggings() {
        return stack(LEGGINGS_SLOT);
    }

    @Override
    public IItemStack boots() {
        return stack(BOOTS_SLOT);
    }

    @Override
    public int selectedSlot() {
        return client.localPlayer() == null ? 0 : vanilla().getSelectedSlot();
    }

    @Override
    public void setSelectedSlot(int slot) {
        if (client.localPlayer() != null && slot >= 0 && slot < 9) {
            vanilla().setSelectedSlot(slot);
        }
    }

    @Override
    public int findHotbar(String text) {
        return findHotbar(new Predicate<IItemStack>() {
            @Override
            public boolean test(IItemStack value) {
                return value.id().contains(text);
            }
        });
    }

    @Override
    public int findHotbar(Predicate<IItemStack> predicate) {
        for (int i = 0; i < 9; i++) {
            if (!stack(i).isEmpty() && predicate.test(stack(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int find(String text) {
        return find(new Predicate<IItemStack>() {
            @Override
            public boolean test(IItemStack value) {
                return value.id().contains(text);
            }
        });
    }

    @Override
    public int find(Predicate<IItemStack> predicate) {
        for (int i = 0; i < MAIN_SIZE; i++) {
            if (!stack(i).isEmpty() && predicate.test(stack(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int count(String itemId) {
        int total = 0;
        for (int i = 0; i < MAIN_SIZE; i++) {
            IItemStack stack = stack(i);
            if (!stack.isEmpty() && stack.id().contains(itemId)) {
                total += stack.count();
            }
        }
        return total;
    }

    @Override
    public boolean has(String itemId) {
        return count(itemId) > 0;
    }

    @Override
    public int firstEmptySlot() {
        for (int i = 0; i < MAIN_SIZE; i++) {
            if (stack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int freeSlots() {
        int free = 0;
        for (int i = 0; i < MAIN_SIZE; i++) {
            if (stack(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    @Override
    public boolean isFull() {
        return freeSlots() == 0;
    }

    @Override
    public int armorValue() {
        return client.localPlayer() == null ? 0 : client.localPlayer().getArmorValue();
    }

    @Override
    public int bestWeaponSlot() {
        int best = -1;
        float damage = -1.0f;
        for (int i = 0; i < 9; i++) {
            IItemStack stack = stack(i);
            if (stack.isEmpty() || !stack.isWeapon()) {
                continue;
            }
            if (stack.attackDamage() > damage) {
                damage = stack.attackDamage();
                best = i;
            }
        }
        return best;
    }

    @Override
    public int bestArmorSlotFor(int armorSlot) {
        for (int i = 0; i < MAIN_SIZE; i++) {
            IItemStack stack = stack(i);
            if (!stack.isEmpty() && stack.isArmor()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void swapWithHotbar(int inventorySlot, int hotbarSlot) {
        if (client.localPlayer() == null) {
            return;
        }
        ItemStack from = vanilla().getItem(inventorySlot);
        ItemStack to = vanilla().getItem(hotbarSlot);
        vanilla().setItem(inventorySlot, to);
        vanilla().setItem(hotbarSlot, from);
    }

    @Override
    public void drop(int slot, boolean all) {
        if (client.localPlayer() == null) {
            return;
        }
        client.localPlayer().drop(vanilla().removeItem(slot, all ? vanilla().getItem(slot).getCount() : 1), false);
    }

    @Override
    public int syncId() {
        AbstractContainerMenu menu = client.localPlayer() == null ? null : client.localPlayer().containerMenu;
        return menu == null ? 0 : menu.containerId;
    }

    @Override
    public int containerSlots() {
        AbstractContainerMenu menu = client.localPlayer() == null ? null : client.localPlayer().containerMenu;
        if (menu == null) {
            return 0;
        }
        return menu.slots.size() - MAIN_SIZE;
    }

    @Override
    public IItemStack containerStack(int slot) {
        AbstractContainerMenu menu = client.localPlayer() == null ? null : client.localPlayer().containerMenu;
        if (menu == null || slot < 0 || slot >= menu.slots.size()) {
            return new Nexus12111ItemStack(ItemStack.EMPTY);
        }
        return new Nexus12111ItemStack(menu.getSlot(slot).getItem());
    }

    @Override
    public void clickSlot(int slot, int button, nexus.api.IInventory.ClickType clickType) {
        if (client.localPlayer() == null || client.minecraft().gameMode == null) {
            return;
        }
        AbstractContainerMenu menu = client.localPlayer().containerMenu;
        if (menu == null) {
            return;
        }
        net.minecraft.world.inventory.ClickType vanillaType = net.minecraft.world.inventory.ClickType.PICKUP;
        if (clickType == nexus.api.IInventory.ClickType.QUICK_MOVE) {
            vanillaType = net.minecraft.world.inventory.ClickType.QUICK_MOVE;
        } else if (clickType == nexus.api.IInventory.ClickType.SWAP) {
            vanillaType = net.minecraft.world.inventory.ClickType.SWAP;
        } else if (clickType == nexus.api.IInventory.ClickType.THROW) {
            vanillaType = net.minecraft.world.inventory.ClickType.THROW;
        } else if (clickType == nexus.api.IInventory.ClickType.CLONE) {
            vanillaType = net.minecraft.world.inventory.ClickType.CLONE;
        } else if (clickType == nexus.api.IInventory.ClickType.PICKUP_ALL) {
            vanillaType = net.minecraft.world.inventory.ClickType.PICKUP_ALL;
        } else if (clickType == nexus.api.IInventory.ClickType.QUICK_CRAFT) {
            vanillaType = net.minecraft.world.inventory.ClickType.QUICK_CRAFT;
        }
        client.minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, vanillaType,
                client.localPlayer());
    }
}
