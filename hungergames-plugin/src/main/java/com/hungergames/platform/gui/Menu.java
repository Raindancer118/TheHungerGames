package com.hungergames.platform.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Basisklasse aller Inventar-Menüs. Identifikation über den
 * {@link InventoryHolder} (keine Titel-Vergleiche), Klick-Handler pro Slot.
 *
 * <p>Unterklassen implementieren {@link #build()} und registrieren dort ihre
 * Items via {@link #set(int, ItemStack, Consumer)}. {@link #refresh()} baut
 * das Menü neu auf (z. B. nach einer Zustandsänderung).</p>
 */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    protected final Player viewer;

    protected Menu(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** Registriert die Items des Menüs (wird bei open/refresh gerufen). */
    protected abstract void build();

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            handlers.put(slot, onClick);
        } else {
            handlers.remove(slot);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void open() {
        refresh();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        inventory.clear();
        handlers.clear();
        build();
    }

    /** Wird vom {@link MenuListener} aufgerufen; Events sind bereits gecancelt. */
    final void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
