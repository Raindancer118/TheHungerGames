package com.hungergames.items;

import com.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for custom item interactions.
 */
public class ItemListener implements Listener {

    /**
     * Registers this listener.
     */
    public static void register() {
        HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        plugin.getServer().getPluginManager().registerEvents(new ItemListener(), plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check for right-click (air or block)
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only handle main hand
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        // Weitere Custom-Items (Rauchbombe, Medikit, Blitzschlag …): Rechtsklick genügt
        if (CustomItems.handleInteract(player, item)) {
            event.setCancelled(true);
            return;
        }

        // Check for Fiendfinder
        if (Fiendfinder.isFiendfinder(item)) {
            // Must be sneaking (shift-click)
            if (!player.isSneaking()) {
                return;
            }

            // Cancel the normal spyglass zoom behavior
            event.setCancelled(true);

            // Activate the Fiendfinder
            boolean consumed = Fiendfinder.activate(player);

            if (consumed) {
                // Remove one item from stack
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        }
    }

    /** Medikit-Countdown: jeder echte Schaden bricht die laufende Behandlung ab. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onMedikitInterrupt(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            CustomItems.abortMedikitOnDamage(player);
        }
    }

    /**
     * Exmatrikulator-Kill: Vanilla-Todesnachricht ersetzen. Für Tribute
     * überschreibt der EliminationListener (HIGH) die Nachricht ohnehin mit
     * dem Kanonenschuss-Broadcast — der übernimmt die Formulierung ebenfalls.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        CustomItems.exmatrikulationPhrase(event.getEntity().getUniqueId()).ifPresent(phrase ->
                event.deathMessage(net.kyori.adventure.text.Component.text(
                        event.getEntity().getName() + " " + phrase,
                        net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)));
    }

    /** Aufprall des geworfenen Krückauwassers: Übelkeit/Blindheit im Umkreis. */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (CustomItems.isKrueckauProjectile(event.getEntity())) {
            CustomItems.onKrueckauImpact(event.getEntity().getLocation());
            event.getEntity().remove();
        }
    }

    /**
     * Trottel-Schutz: rettet vor tödlichem UMWELT-Schaden (Lava, Sturz, Feuer,
     * Monster …), NICHT vor Spieler-Kills. Admin-Eliminierung läuft nicht über
     * Damage-Events und ist damit ohnehin nicht betroffen.
     */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST,
            ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0) {
            return; // nicht tödlich
        }
        // Schaden durch einen anderen Spieler oder ein Custom-Item (z. B. der
        // Blitzschlag aus dem Shop) NICHT abfangen — der Trottel-Schutz rettet nur
        // vor echtem Umweltpech, nicht vor offensiven Waffen.
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            org.bukkit.entity.Entity damager = byEntity.getDamager();
            if (damager instanceof Player || CustomItems.isCustomDamageSource(damager)) {
                return;
            }
            if (damager instanceof org.bukkit.entity.Projectile proj) {
                if (proj.getShooter() instanceof Player || CustomItems.isCustomDamageSource(proj)) {
                    return;
                }
            }
        }
        if (CustomItems.tryStupidnessProtector(player)) {
            event.setCancelled(true);
        }
    }
}
