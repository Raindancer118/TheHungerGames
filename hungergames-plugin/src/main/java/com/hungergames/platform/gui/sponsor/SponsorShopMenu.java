package com.hungergames.platform.gui.sponsor;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.items.Fiendfinder;
import com.hungergames.platform.Messages;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.sound.SoundService;
import com.hungergames.platform.sponsor.ShopItemParser;
import com.hungergames.platform.sponsor.ShopItemParser.CustomItemReward;
import com.hungergames.platform.sponsor.ShopItemParser.EffectReward;
import com.hungergames.platform.sponsor.ShopItemParser.MaterialReward;
import com.hungergames.platform.sponsor.ShopItemParser.PotionReward;
import com.hungergames.platform.sponsor.ShopItemParser.ShopItem;
import com.hungergames.platform.sponsor.SponsorTokenService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sponsor-Shop an Beacons: kauft Items, Effekte oder Custom-Items gegen
 * Sponsor Tokens. Im Vorschau-Modus (Admin-GUI) sind Käufe gesperrt.
 */
public final class SponsorShopMenu extends Menu {

    private final ConfigurationService config;
    private final HgSettings settings;
    private final SponsorTokenService tokens;
    private final AnnouncementService announcements;
    private final SoundService sounds;
    private final RoundLogService roundLog;
    private final boolean previewOnly;

    public SponsorShopMenu(Player viewer, ConfigurationService config, HgSettings settings,
                           SponsorTokenService tokens, AnnouncementService announcements,
                           SoundService sounds, RoundLogService roundLog, boolean previewOnly) {
        super(viewer, 6, Component.text(previewOnly ? "Sponsor-Shop (Vorschau)" : "Sponsor-Shop"));
        this.config = config;
        this.settings = settings;
        this.tokens = tokens;
        this.announcements = announcements;
        this.sounds = sounds;
        this.roundLog = roundLog;
        this.previewOnly = previewOnly;
    }

    @Override
    protected void build() {
        int balance = tokens.countTokens(viewer);
        set(4, Items.of(Material.NETHER_STAR,
                Component.text("Deine Sponsor Tokens: " + balance, NamedTextColor.GOLD),
                Component.text("Verdient durch Überleben — einlösbar hier.", NamedTextColor.GRAY)));

        int slot = 9;
        for (String line : config.get(settings.sponsorShopItems)) {
            if (slot >= 45) {
                break;
            }
            ShopItem item;
            try {
                item = ShopItemParser.parse(line);
            } catch (IllegalArgumentException e) {
                continue; // Validator meldet das beim Setzen; hier still überspringen
            }
            if (!item.enabled()) {
                continue; // deaktivierte Angebote erscheinen nicht im Shop
            }
            addShopItem(slot++, item, balance);
        }

        set(49, Items.close(), click -> viewer.closeInventory());
    }

    private void addShopItem(int slot, ShopItem item, int balance) {
        boolean affordable = balance >= item.cost();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(describeReward(item.reward()), NamedTextColor.GRAY));
        if (!item.enchantments().isEmpty()) {
            lore.add(Component.text("Verzaubert: " + String.join(", ", item.enchantments()),
                    NamedTextColor.LIGHT_PURPLE));
        }
        lore.add(Component.text("Preis: " + item.cost() + " Token",
                affordable ? NamedTextColor.GOLD : NamedTextColor.RED));
        if (previewOnly) {
            lore.add(Component.text("Vorschau — Kauf nur am Beacon", NamedTextColor.DARK_GRAY));
        } else if (!affordable) {
            lore.add(Component.text("Nicht genug Tokens", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Klick zum Kaufen", NamedTextColor.AQUA));
        }

        set(slot, Items.of(iconFor(item.reward()),
                        Component.text(item.displayName(), NamedTextColor.YELLOW), lore),
                click -> purchase(item));
    }

    private void purchase(ShopItem item) {
        if (previewOnly) {
            viewer.sendMessage(Messages.info("Vorschau-Modus — Käufe nur am Sponsor-Beacon."));
            return;
        }
        if (!tokens.removeTokens(viewer, item.cost())) {
            announcements.sendTo(viewer, settings.textSponsorNotEnough,
                    Map.of("cost", String.valueOf(item.cost())), Style.CHAT);
            sounds.playTo(viewer, settings.soundSponsorPurchaseFailed);
            return;
        }
        if (!grantReward(item)) {
            // Reward nicht vergebbar (z. B. unbekannter Effekt) → Tokens zurück
            tokens.giveTokens(viewer, item.cost());
            viewer.sendMessage(Messages.error("Belohnung \"" + item.displayName()
                    + "\" konnte nicht vergeben werden — Tokens zurückerstattet."));
            return;
        }
        announcements.sendTo(viewer, settings.textSponsorPurchase,
                Map.of("item", item.displayName(), "cost", String.valueOf(item.cost())),
                Style.CHAT, Style.ACTIONBAR);
        sounds.playTo(viewer, settings.soundSponsorPurchaseSuccess);
        roundLog.log("SPONSOR", viewer.getName() + " kauft \"" + item.displayName()
                + "\" für " + item.cost() + " Token");
        refresh();
    }

    private boolean grantReward(ShopItem item) {
        ShopItemParser.Reward reward = item.reward();
        // Effekt-Reward wirkt direkt auf den Spieler (kein Item).
        if (reward instanceof EffectReward effect) {
            PotionEffectType type = resolveEffect(effect.effectName());
            if (type == null) {
                return false;
            }
            viewer.addPotionEffect(new PotionEffect(type,
                    effect.durationSeconds() * 20, effect.amplifier()));
            return true;
        }
        ItemStack stack = buildStack(reward);
        if (stack == null) {
            return false;
        }
        applyEnchantments(stack, item.enchantments());
        give(stack);
        return true;
    }

    /** Baut das auszugebende Item (Material, Custom-Item oder Trank). */
    private static ItemStack buildStack(ShopItemParser.Reward reward) {
        return switch (reward) {
            case MaterialReward material -> new ItemStack(material.material(), material.amount());
            case CustomItemReward custom -> {
                ItemStack template = com.hungergames.items.CustomItems.create(custom.customId());
                if (template != null) {
                    template.setAmount(Math.max(1, custom.amount()));
                }
                yield template;
            }
            case PotionReward potion -> buildPotion(potion);
            case EffectReward ignored -> null; // separat behandelt
        };
    }

    private static ItemStack buildPotion(PotionReward potion) {
        org.bukkit.potion.PotionType type = resolvePotionType(potion.potionType());
        if (type == null) {
            return null;
        }
        ItemStack stack = new ItemStack(potion.variant().material(), Math.max(1, potion.amount()));
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            meta.setBasePotionType(type);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Hängt beliebige Verzauberungen ({@code KEY:LEVEL}) an — unsafe, also ohne Limits. */
    private static void applyEnchantments(ItemStack stack, java.util.List<String> enchantments) {
        for (String spec : enchantments) {
            String[] parts = spec.split(":", 2);
            org.bukkit.enchantments.Enchantment enchant = resolveEnchant(parts[0]);
            if (enchant == null) {
                continue;
            }
            int level = 1;
            if (parts.length == 2) {
                try {
                    level = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // Default 1
                }
            }
            stack.addUnsafeEnchantment(enchant, level);
        }
    }

    private static org.bukkit.enchantments.Enchantment resolveEnchant(String name) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
    }

    private static org.bukkit.potion.PotionType resolvePotionType(String name) {
        try {
            return org.bukkit.potion.PotionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void give(ItemStack stack) {
        var leftover = viewer.getInventory().addItem(stack);
        leftover.values().forEach(rest ->
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), rest));
    }

    private static PotionEffectType resolveEffect(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        return Registry.EFFECT.get(key);
    }

    private static String describeReward(ShopItemParser.Reward reward) {
        return switch (reward) {
            case MaterialReward material -> material.amount() + "× " + material.material().name();
            case EffectReward effect -> "Effekt: " + effect.effectName() + " "
                    + effect.durationSeconds() + "s (Stärke " + (effect.amplifier() + 1) + ")";
            case CustomItemReward custom -> custom.amount() + "× "
                    + com.hungergames.items.CustomItems.byId(custom.customId())
                            .map(com.hungergames.items.CustomItems::displayName)
                            .orElse(custom.customId());
            case PotionReward potion -> potion.amount() + "× Trank ("
                    + potion.variant().name().toLowerCase(Locale.ROOT) + ", " + potion.potionType() + ")";
        };
    }

    private static Material iconFor(ShopItemParser.Reward reward) {
        return switch (reward) {
            case MaterialReward material -> material.material();
            case EffectReward effect -> Material.POTION;
            case CustomItemReward custom -> com.hungergames.items.CustomItems.iconFor(custom.customId());
            case PotionReward potion -> potion.variant().material();
        };
    }
}
