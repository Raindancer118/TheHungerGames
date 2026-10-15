package com.hungergames.items;

import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import com.hungergames.HungerGamesPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom item: Fiendfinder Spyglass
 * A single-use spyglass that reveals the closest enemy with a glowing effect.
 */
public class Fiendfinder {

    private static final String ITEM_ID = "fiendfinder";
    private static final NamespacedKey ITEM_KEY;

    static {
        ITEM_KEY = new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), ITEM_ID);
    }

    /**
     * Creates a new Fiendfinder item.
     * 
     * @return The Fiendfinder ItemStack
     */
    public static ItemStack createItem() {
        ItemStack spyglass = new ItemStack(Material.SPYGLASS, 1);
        ItemMeta meta = spyglass.getItemMeta();

        if (meta != null) {
            // Set display name
            meta.displayName(Component.text("Fiendfinder")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            // Set lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("Reveals your closest enemy")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("with a glowing effect.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text("§6§lSHIFT + RIGHT-CLICK §7to activate")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("§c§lSINGLE USE")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            // Add custom data to identify this item
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BOOLEAN, true);

            // Make it glow (enchantment glint)
            meta.setEnchantmentGlintOverride(true);

            spyglass.setItemMeta(meta);
        }

        return spyglass;
    }

    /**
     * Checks if an ItemStack is a Fiendfinder.
     */
    public static boolean isFiendfinder(ItemStack item) {
        if (item == null || item.getType() != Material.SPYGLASS)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;

        return meta.getPersistentDataContainer().has(ITEM_KEY, PersistentDataType.BOOLEAN);
    }

    /**
     * Activates the Fiendfinder for a player.
     * Finds the closest enemy and applies glowing effect.
     * 
     * @param user The player using the Fiendfinder
     * @return true if successfully activated (item should be consumed)
     */
    public static boolean activate(Player user) {
        WhitelistManager whitelist = WhitelistManager.getInstance();
        ConfigManager config = ConfigManager.getInstance();

        // Find closest enemy
        Player closestEnemy = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : user.getWorld().getPlayers()) {
            // Skip self
            if (player.equals(user))
                continue;

            // Skip spectators
            if (player.getGameMode() == GameMode.SPECTATOR)
                continue;

            // Only target whitelisted players (participants)
            if (!whitelist.isWhitelisted(player.getName()))
                continue;

            double distance = user.getLocation().distance(player.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEnemy = player;
            }
        }

        if (closestEnemy == null) {
            user.sendMessage("§cNo enemies found nearby!");
            user.playSound(user.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false; // Don't consume the item
        }

        // Apply glowing effect
        int glowDuration = config.getFiendfinderGlowDuration();
        closestEnemy.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                glowDuration * 20, // Convert seconds to ticks
                0,
                false, false, true));

        // Notify user
        int distance = (int) Math.round(closestDistance);
        user.sendMessage("§6§lFIENDFINDER ACTIVATED!");
        user.sendMessage("§7Revealed: §e" + closestEnemy.getName() + " §7(~" + distance + " blocks away)");
        CustomItems.playFiendfinderSound(user.getLocation());

        // Notify the revealed player
        closestEnemy.sendMessage("§c§l⚠ You have been revealed by a Fiendfinder!");
        closestEnemy.playSound(closestEnemy.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.0f);

        Sender.debug(user.getName() + " used Fiendfinder on " + closestEnemy.getName() +
                " (distance: " + distance + " blocks, glow: " + glowDuration + "s)");

        return true; // Consume the item
    }
}
