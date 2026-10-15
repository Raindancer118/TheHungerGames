package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.sponsor.ShopItemParser;
import com.hungergames.platform.sponsor.ShopItemParser.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI zum nachträglichen Verzaubern eines Shop-Eintrags — jede Verzauberung ist
 * anklickbar (Links = Stufe +1, Rechts = Stufe −1/entfernen). Beliebige
 * Verzauberungen und Stufen sind erlaubt (unsafe). Speichert direkt in die
 * Shop-Zeile.
 */
public final class ShopEnchantMenu extends Menu {

    private static final int PER_PAGE = 45;
    private static final int MAX_LEVEL = 10;

    private final AdminServices services;
    private final int index;
    private final Runnable onBack;
    private int page;
    private List<Enchantment> cache;

    public ShopEnchantMenu(Player viewer, AdminServices services, int index, Runnable onBack) {
        super(viewer, 6, Component.text("Verzauberungen"));
        this.services = services;
        this.index = index;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        ShopItem item = current();
        if (item == null) {
            set(22, Items.of(Material.BARRIER, Component.text("Eintrag nicht mehr vorhanden",
                    NamedTextColor.RED)));
            set(53, Items.back(), click -> onBack.run());
            return;
        }
        Map<String, Integer> active = activeMap(item);

        set(4, Items.of(Material.ENCHANTED_BOOK,
                Component.text(item.displayName(), NamedTextColor.GOLD),
                Component.text(item.enchantments().isEmpty() ? "Keine Verzauberungen"
                        : "Aktiv: " + String.join(", ", item.enchantments()), NamedTextColor.LIGHT_PURPLE),
                Component.text("Links: Stufe +1 · Rechts: Stufe −1", NamedTextColor.AQUA)));

        List<Enchantment> all = allEnchantments();
        int totalPages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.floorMod(page, totalPages);
        int from = page * PER_PAGE;
        int to = Math.min(all.size(), from + PER_PAGE);
        int slot = 0;
        for (int i = from; i < to; i++) {
            addEnchant(slot++, all.get(i), active);
        }

        if (totalPages > 1) {
            set(45, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("◀ Seite " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE)),
                    click -> {
                        page--;
                        refresh();
                    });
            set(46, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("Seite " + (page + 1) + "/" + totalPages + " ▶", NamedTextColor.WHITE)),
                    click -> {
                        page++;
                        refresh();
                    });
        }
        set(53, Items.back(), click -> onBack.run());
    }

    private void addEnchant(int slot, Enchantment enchant, Map<String, Integer> active) {
        String key = enchant.getKey().getKey();
        int level = active.getOrDefault(key, 0);
        boolean on = level > 0;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(on ? "Stufe " + level : "nicht aktiv",
                on ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text("Links: +1  ·  Rechts: −1", NamedTextColor.AQUA));

        ItemStack icon = Items.of(Material.ENCHANTED_BOOK,
                Component.text(pretty(key), on ? NamedTextColor.GREEN : NamedTextColor.WHITE), lore);
        if (on) {
            ItemMeta meta = icon.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
        }
        set(slot, icon, click -> {
            int newLevel = level + (click.isRightClick() ? -1 : 1);
            newLevel = Math.max(0, Math.min(MAX_LEVEL, newLevel));
            setLevel(key, newLevel);
            refresh();
        });
    }

    private void setLevel(String key, int level) {
        ShopItem item = current();
        if (item == null) {
            return;
        }
        Map<String, Integer> map = activeMap(item);
        if (level <= 0) {
            map.remove(key);
        } else {
            map.put(key, level);
        }
        List<String> enchants = new ArrayList<>();
        map.forEach((k, v) -> enchants.add(k + ":" + v));
        String line = ShopItemParser.serialize(item.withEnchantments(enchants));

        List<String> lines = lines();
        if (index < lines.size()) {
            lines.set(index, line);
            List<String> errors = services.config.begin(viewer.getName())
                    .set(services.settings.sponsorShopItems, lines, Scope.PERSISTENT).commit();
            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
        }
    }

    private static Map<String, Integer> activeMap(ShopItem item) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String spec : item.enchantments()) {
            String[] parts = spec.split(":", 2);
            int level = 1;
            if (parts.length == 2) {
                try {
                    level = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // Default 1
                }
            }
            map.put(parts[0].trim().toLowerCase(Locale.ROOT), level);
        }
        return map;
    }

    private ShopItem current() {
        List<String> lines = lines();
        if (index >= lines.size()) {
            return null;
        }
        try {
            return ShopItemParser.parse(lines.get(index));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> lines() {
        return new ArrayList<>(services.config.get(services.settings.sponsorShopItems));
    }

    private List<Enchantment> allEnchantments() {
        if (cache == null) {
            List<Enchantment> list = new ArrayList<>();
            for (Enchantment enchant : Registry.ENCHANTMENT) {
                list.add(enchant);
            }
            list.sort(java.util.Comparator.comparing(e -> e.getKey().getKey()));
            cache = list;
        }
        return cache;
    }

    private static String pretty(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
