package nexus.api;

import java.util.List;

/** Player inventory abstraction (36 main slots + 4 armor + offhand). */
public interface IInventory {

    int MAIN_SIZE = 36;
    int OFFHAND_SLOT = 40;
    int HELMET_SLOT = 39;
    int CHESTPLATE_SLOT = 38;
    int LEGGINGS_SLOT = 37;
    int BOOTS_SLOT = 36;

    IItemStack stack(int slot);

    /** Number of main inventory slots (always {@link #MAIN_SIZE}). */
    int mainSize();

    List<IItemStack> mainStacks();

    IItemStack heldItem();

    IItemStack offhandItem();

    IItemStack helmet();

    IItemStack chestplate();

    IItemStack leggings();

    IItemStack boots();

    int selectedSlot();

    void setSelectedSlot(int slot);

    /** Finds the first hotbar slot holding an item whose id contains {@code text}. */
    int findHotbar(String text);

    int findHotbar(Predicate<IItemStack> predicate);

    int find(String text);

    int find(Predicate<IItemStack> predicate);

    int count(String itemId);

    boolean has(String itemId);

    int firstEmptySlot();

    int freeSlots();

    boolean isFull();

    /** Total armor protection value (0..20). */
    int armorValue();

    /** Best weapon score currently available in the hotbar. */
    int bestWeaponSlot();

    /** Best armor piece slot for a given armor slot index, -1 when none. */
    int bestArmorSlotFor(int armorSlot);

    void swapWithHotbar(int inventorySlot, int hotbarSlot);

    void drop(int slot, boolean all);

    /** Sync id of the currently open container, -1 when none is open. */
    int syncId();

    /** Number of slots of the currently open container (0 when none). */
    int containerSlots();

    IItemStack containerStack(int slot);

    /**
     * Performs a container click through the vanilla window click packet.
     *
     * @param slot        container slot index (or -999 for outside)
     * @param button      0 = left, 1 = right, hotbar index for SWAP
     * @param clickType   see {@link ClickType}
     */
    void clickSlot(int slot, int button, ClickType clickType);

    /** Simple predicate to avoid depending on java.util.function in old Java targets. */
    interface Predicate<T> {
        boolean test(T value);
    }

    /** Vanilla window click action types (protocol stable since 1.16). */
    enum ClickType {
        PICKUP(0),
        QUICK_MOVE(1),
        SWAP(2),
        CLONE(3),
        THROW(4),
        QUICK_CRAFT(5),
        PICKUP_ALL(6);

        private final int id;

        ClickType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }
    }
}
