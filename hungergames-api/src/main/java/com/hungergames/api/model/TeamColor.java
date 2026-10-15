package com.hungergames.api.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

/**
 * The colors a team can claim. Each color may be claimed by at most one team
 * per round.
 *
 * <p>Every value maps consistently onto the three places a team color is
 * displayed: leather armor ({@link #dyeColor()}), name tags / scoreboard
 * ({@link #textColor()}) and chat.</p>
 */
public enum TeamColor {

    WHITE(DyeColor.WHITE, NamedTextColor.WHITE),
    ORANGE(DyeColor.ORANGE, NamedTextColor.GOLD),
    MAGENTA(DyeColor.MAGENTA, NamedTextColor.LIGHT_PURPLE),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, NamedTextColor.AQUA),
    YELLOW(DyeColor.YELLOW, NamedTextColor.YELLOW),
    LIME(DyeColor.LIME, NamedTextColor.GREEN),
    PINK(DyeColor.PINK, NamedTextColor.LIGHT_PURPLE),
    GRAY(DyeColor.GRAY, NamedTextColor.DARK_GRAY),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, NamedTextColor.GRAY),
    CYAN(DyeColor.CYAN, NamedTextColor.DARK_AQUA),
    PURPLE(DyeColor.PURPLE, NamedTextColor.DARK_PURPLE),
    BLUE(DyeColor.BLUE, NamedTextColor.BLUE),
    BROWN(DyeColor.BROWN, NamedTextColor.DARK_RED),
    GREEN(DyeColor.GREEN, NamedTextColor.DARK_GREEN),
    RED(DyeColor.RED, NamedTextColor.RED),
    BLACK(DyeColor.BLACK, NamedTextColor.BLACK);

    private final DyeColor dyeColor;
    private final NamedTextColor textColor;

    TeamColor(DyeColor dyeColor, NamedTextColor textColor) {
        this.dyeColor = dyeColor;
        this.textColor = textColor;
    }

    /** @return the dye color used for leather armor. */
    public DyeColor dyeColor() {
        return dyeColor;
    }

    /** @return the RGB color applied to leather armor meta. */
    public Color armorColor() {
        return dyeColor.getColor();
    }

    /** @return the Adventure text color for name tags, scoreboard and chat. */
    public TextColor textColor() {
        return textColor;
    }

    /** @return the matching {@link NamedTextColor} (needed for scoreboard teams). */
    public NamedTextColor namedTextColor() {
        return textColor;
    }
}
