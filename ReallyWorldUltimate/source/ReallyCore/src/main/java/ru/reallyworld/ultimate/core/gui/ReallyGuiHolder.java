package ru.reallyworld.ultimate.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ReallyGuiHolder implements InventoryHolder {
    private final String menuType;
    private final Map<Integer, Consumer<InventoryClickEvent>> slotActions = new HashMap<>();
    private Runnable closeCallback;
    private Inventory inventory;

    public ReallyGuiHolder(String menuType) {
        this.menuType = menuType;
    }

    public String menuType() {
        return menuType;
    }

    public void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void onClick(int slot, Consumer<InventoryClickEvent> handler) {
        slotActions.put(slot, handler);
    }

    public Consumer<InventoryClickEvent> handlerFor(int slot) {
        return slotActions.get(slot);
    }

    public void onClose(Runnable closeCallback) {
        this.closeCallback = closeCallback;
    }

    public Runnable closeCallback() {
        return closeCallback;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
