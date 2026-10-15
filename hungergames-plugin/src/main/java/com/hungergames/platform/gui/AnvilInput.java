package com.hungergames.platform.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Freitext-/Zahleneingabe über ein Anvil-Inventar: Der Spieler benennt das
 * Item um und klickt auf den Ergebnis-Slot. Abbruch durch Schließen.
 */
public final class AnvilInput implements Listener {

    private record Session(AnvilView view, Consumer<String> onSubmit, Runnable onCancel) {
    }

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public AnvilInput(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Öffnet die Eingabe.
     *
     * @param initial  vorbelegter Text
     * @param onSubmit erhält die Eingabe (auf dem Main-Thread)
     * @param onCancel wird bei Abbruch gerufen (z. B. vorheriges Menü öffnen)
     */
    public void open(Player player, Component title, String initial,
                     Consumer<String> onSubmit, Runnable onCancel) {
        // Einen Tick verzögert: open() wird aus Klick-Handlern noch offener
        // Menüs gerufen; ein Inventar darf nicht mitten im Klick-Event
        // geöffnet werden.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            AnvilView view = MenuType.ANVIL.create(player, title);

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text(initial));
            paper.setItemMeta(meta);
            view.getTopInventory().setItem(0, paper);
            view.setRepairCost(0);

            sessions.put(player.getUniqueId(), new Session(view, onSubmit, onCancel));
            player.openInventory(view);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getView() != session.view()) {
            return;
        }
        event.setCancelled(true);

        if (event.getRawSlot() == 2) { // Ergebnis-Slot = Bestätigung
            String text = session.view().getRenameText();
            sessions.remove(player.getUniqueId());
            // Papier entfernen, bevor der Amboss schließt — sonst gibt Vanilla
            // es dem Spieler zurück (Papier mit dem Eingabetext im Inventar).
            session.view().getTopInventory().clear();
            player.closeInventory();
            String input = text == null ? "" : text.trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> session.onSubmit().accept(input));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getView() != session.view()) {
            // Nicht unser Amboss — z. B. das Close-Event des Menüs, aus dem
            // die Eingabe gerade geöffnet wird. Session unangetastet lassen,
            // sonst wird der Amboss sofort wieder geschlossen und Vanilla
            // gibt das Papier ins Spielerinventar zurück.
            return;
        }
        sessions.remove(player.getUniqueId());
        // Abbruch durch Schließen: Papier entfernen, bevor Vanilla die
        // Amboss-Slots ins Spielerinventar zurückgibt.
        event.getInventory().clear();
        if (session.onCancel() != null) {
            plugin.getServer().getScheduler().runTask(plugin, session.onCancel());
        }
    }
}
