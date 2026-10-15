package com.hungergames.items;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kurzzeitige Voll-Unsichtbarkeit inkl. Rüstung (für die Rauchbombe).
 *
 * <p>Der Unsichtbarkeits-Effekt allein versteckt getragene Rüstung nicht — daher
 * wird sie kurzzeitig aus den Slots genommen und nach Ablauf zurückgegeben. Das
 * ist tod-/disconnect-sicher: bei Tod droppt die zwischengelagerte Rüstung ganz
 * normal, bei Quit wird sofort zurückgegeben — es geht nichts verloren.</p>
 */
public final class InvisibilityCloak implements Listener {

    private final Plugin plugin;
    private final Map<UUID, ItemStack[]> storedArmor = new ConcurrentHashMap<>();

    public InvisibilityCloak(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Macht den Spieler für {@code seconds} Sekunden vollständig unsichtbar. */
    public void cloak(Player player, int seconds) {
        int ticks = Math.max(1, seconds) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));

        // Rüstung ausblenden: aus den Slots nehmen und nach Ablauf zurückgeben.
        // Nur einmal einlagern, falls die Bombe mehrfach kurz hintereinander kommt.
        if (!storedArmor.containsKey(player.getUniqueId())) {
            PlayerInventory inv = player.getInventory();
            ItemStack[] armor = inv.getArmorContents();
            boolean hasArmor = false;
            for (ItemStack piece : armor) {
                if (piece != null && !piece.getType().isAir()) {
                    hasArmor = true;
                    break;
                }
            }
            if (hasArmor) {
                storedArmor.put(player.getUniqueId(), armor);
                inv.setArmorContents(new ItemStack[4]);
            }
        }

        UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(id), ticks);
    }

    private void restore(UUID id) {
        ItemStack[] armor = storedArmor.remove(id);
        if (armor == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        // Nur leere Slots wieder befüllen — falls der Spieler zwischenzeitlich
        // neue Rüstung angezogen hat, überschreiben wir sie nicht.
        ItemStack[] current = inv.getArmorContents();
        for (int i = 0; i < 4 && i < armor.length; i++) {
            if (armor[i] == null || armor[i].getType().isAir()) {
                continue;
            }
            if (current[i] == null || current[i].getType().isAir()) {
                current[i] = armor[i];
            } else {
                // Slot belegt: Rüstung ins Inventar (oder zu Boden).
                inv.addItem(armor[i]).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
        inv.setArmorContents(current);
    }

    /** Tod: eingelagerte Rüstung ganz normal droppen lassen (kein Verlust). */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ItemStack[] armor = storedArmor.remove(event.getEntity().getUniqueId());
        if (armor != null && event.getDrops() != null && event.getKeepInventory() == false) {
            for (ItemStack piece : armor) {
                if (piece != null && !piece.getType().isAir()) {
                    event.getDrops().add(piece);
                }
            }
        }
    }

    /** Disconnect: sofort zurückgeben, damit beim nächsten Login alles da ist. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ItemStack[] armor = storedArmor.remove(player.getUniqueId());
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
    }
}
