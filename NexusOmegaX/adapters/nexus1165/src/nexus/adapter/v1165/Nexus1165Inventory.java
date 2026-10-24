package nexus.adapter.v1165;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import nexus.api.IInventory;
import nexus.api.IItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@link IInventory} implementation for Minecraft 1.16.5. */
public final class Nexus1165Inventory implements IInventory {

    private final Nexus1165Client client;

    Nexus1165Inventory(Nexus1165Client client) {
        this.client = client;
    }

    private ClientPlayerEntity self() {
        return client.localPlayer();
    }

    private IItemStack wrap(ItemStack stack) {
        return new Nexus1165ItemStack(stack);
    }

    @Override
    public IItemStack stack(int slot) {
        if (self() == null) {
            return wrap(ItemStack.EMPTY);
        }
        if (slot < MAIN_SIZE) {
            return wrap(self().inventory.getStack(slot));
        }
        if (slot == OFFHAND_SLOT) {
            return wrap(self().inventory.offHand.get(0));
        }
        int armor = slot - BOOTS_SLOT;
        if (armor >= 0 && armor < 4) {
            return wrap(self().inventory.armor.get(armor));
        }
        return wrap(ItemStack.EMPTY);
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
        return self() == null ? 0 : self().inventory.selectedSlot;
    }

    @Override
    public void setSelectedSlot(int slot) {
        if (self() != null && slot >= 0 && slot < 9) {
            self().inventory.selectedSlot = slot;
        }
    }

    @Override
    public int findHotbar(String text) {
        for (int i = 0; i < 9; i++) {
            if (!stack(i).isEmpty() && stack(i).id().contains(text)) {
                return i;
            }
        }
        return -1;
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
        for (int i = 0; i < MAIN_SIZE; i++) {
            if (!stack(i).isEmpty() && stack(i).id().contains(text)) {
                return i;
            }
        }
        return -1;
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
        return self() == null ? 0 : self().getArmor();
    }

    @Override
    public int bestWeaponSlot() {
        int best = -1;
        float damage = -1.0f;
        for (int i = 0; i < 9; i++) {
            IItemStack stack = stack(i);
            if (!stack.isEmpty() && stack.isWeapon() && stack.attackDamage() > damage) {
                damage = stack.attackDamage();
                best = i;
            }
        }
        return best;
    }

    @Override
    public int bestArmorSlotFor(int armorSlot) {
        for (int i = 0; i < MAIN_SIZE; i++) {
            if (!stack(i).isEmpty() && stack(i).isArmor()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void swapWithHotbar(int inventorySlot, int hotbarSlot) {
        if (self() == null) {
            return;
        }
        ItemStack from = self().inventory.getStack(inventorySlot);
        ItemStack to = self().inventory.getStack(hotbarSlot);
        self().inventory.setStack(inventorySlot, to);
        self().inventory.setStack(hotbarSlot, from);
    }

    @Override
    public void drop(int slot, boolean all) {
        if (self() == null) {
            return;
        }
        self().inventory.removeStack(slot, all ? self().inventory.getStack(slot).getCount() : 1);
    }

    @Override
    public int syncId() {
        return self() == null ? 0 : self().container.syncId;
    }

    @Override
    public int containerSlots() {
        return self() == null ? 0 : self().container.slots.size() - MAIN_SIZE;
    }

    @Override
    public IItemStack containerStack(int slot) {
        if (self() == null || slot < 0 || slot >= self().container.slots.size()) {
            return wrap(ItemStack.EMPTY);
        }
        return wrap(self().container.getSlot(slot).getStack());
    }

    @Override
    public void clickSlot(int slot, int button, nexus.api.IInventory.ClickType clickType) {
        if (self() == null || client.handle().interactionManager == null) {
            return;
        }
        SlotActionType type = SlotActionType.PICKUP;
        if (clickType == nexus.api.IInventory.ClickType.QUICK_MOVE) {
            type = SlotActionType.QUICK_MOVE;
        } else if (clickType == nexus.api.IInventory.ClickType.SWAP) {
            type = SlotActionType.SWAP;
        } else if (clickType == nexus.api.IInventory.ClickType.THROW) {
            type = SlotActionType.THROW;
        } else if (clickType == nexus.api.IInventory.ClickType.CLONE) {
            type = SlotActionType.CLONE;
        } else if (clickType == nexus.api.IInventory.ClickType.PICKUP_ALL) {
            type = SlotActionType.PICKUP_ALL;
        } else if (clickType == nexus.api.IInventory.ClickType.QUICK_CRAFT) {
            type = SlotActionType.QUICK_CRAFT;
        }
        client.handle().interactionManager.clickSlot(self().container.syncId, slot, button, type, self());
    }
}
