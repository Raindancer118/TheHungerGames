package com.hungergames.platform.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Item-Bau-Helfer für Menü-Icons (Adventure-Components, keine Legacy-Codes). */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack of(Material material, Component name, Component... lore) {
        return of(material, name, List.of(lore));
    }

    /** Standard-Icon mit Titel in Farbe und grauen Lore-Zeilen. */
    public static ItemStack simple(Material material, String name, String... loreLines) {
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        return of(material, Component.text(name, NamedTextColor.YELLOW), lore);
    }

    public static ItemStack back() {
        return of(Material.ARROW, Component.text("Zurück", NamedTextColor.WHITE));
    }

    public static ItemStack close() {
        return of(Material.BARRIER, Component.text("Schließen", NamedTextColor.RED));
    }
}
