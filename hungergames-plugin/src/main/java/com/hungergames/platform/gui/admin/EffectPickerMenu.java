package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.Messages;
import com.hungergames.platform.effect.EffectService;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GUI-Auswahl visueller Effekte für ein Event — das optische Gegenstück zum
 * {@link SoundPickerMenu}. Kuratierte Partikel-Favoriten zuerst, dann jeder
 * weitere Partikel-Typ. Mehrere gleichzeitig aktivierbar (Ebenen).
 *
 * <ul>
 *   <li>Linksklick: an/aus</li>
 *   <li>Rechtsklick: an deiner Position probeansehen</li>
 *   <li>Shift-Klick (aktiv): Feineinstellungen (Anzahl, Streuung, Farbe …)</li>
 * </ul>
 */
public final class EffectPickerMenu extends Menu {

    private record EffectOption(String name, String label, Material icon) {
    }

    private static final int PER_PAGE = 36;

    /** Kuratierte, gut aussehende Partikel mit schönen Icons. */
    private static final List<EffectOption> PALETTE = List.of(
            new EffectOption("FLAME", "Flamme", Material.BLAZE_POWDER),
            new EffectOption("SOUL_FIRE_FLAME", "Seelenflamme", Material.SOUL_TORCH),
            new EffectOption("SMALL_FLAME", "Kleine Flamme", Material.TORCH),
            new EffectOption("LAVA", "Lava-Funken", Material.LAVA_BUCKET),
            new EffectOption("LARGE_SMOKE", "Rauch groß", Material.CAMPFIRE),
            new EffectOption("CAMPFIRE_COSY_SMOKE", "Lagerfeuer-Rauch", Material.CAMPFIRE),
            new EffectOption("EXPLOSION", "Explosion", Material.TNT),
            new EffectOption("EXPLOSION_EMITTER", "Große Explosion", Material.TNT_MINECART),
            new EffectOption("FIREWORK", "Feuerwerk", Material.FIREWORK_ROCKET),
            new EffectOption("HEART", "Herzen", Material.POPPY),
            new EffectOption("TOTEM_OF_UNDYING", "Totem-Glitzer", Material.TOTEM_OF_UNDYING),
            new EffectOption("END_ROD", "End-Rod", Material.END_ROD),
            new EffectOption("PORTAL", "Portal", Material.OBSIDIAN),
            new EffectOption("REVERSE_PORTAL", "Portal aufwärts", Material.CRYING_OBSIDIAN),
            new EffectOption("ENCHANT", "Verzauberung", Material.ENCHANTING_TABLE),
            new EffectOption("CRIT", "Krit-Funken", Material.IRON_SWORD),
            new EffectOption("ENCHANTED_HIT", "Magie-Treffer", Material.DIAMOND_SWORD),
            new EffectOption("DAMAGE_INDICATOR", "Schaden-Herzen", Material.REDSTONE),
            new EffectOption("DRAGON_BREATH", "Drachenatem", Material.DRAGON_BREATH),
            new EffectOption("DUST", "Farbstaub (färbbar)", Material.REDSTONE),
            new EffectOption("DUST_PLUME", "Staubwolke", Material.WHITE_DYE),
            new EffectOption("GLOW", "Leuchten", Material.GLOW_INK_SAC),
            new EffectOption("ELECTRIC_SPARK", "Elektrofunken", Material.LIGHTNING_ROD),
            new EffectOption("SONIC_BOOM", "Sonic Boom", Material.SCULK_CATALYST),
            new EffectOption("SCULK_SOUL", "Sculk-Seele", Material.SCULK),
            new EffectOption("SCULK_CHARGE_POP", "Sculk-Pop", Material.SCULK_VEIN),
            new EffectOption("SHRIEK", "Shriek", Material.SCULK_SHRIEKER),
            new EffectOption("SOUL", "Seele", Material.SOUL_SAND),
            new EffectOption("ASH", "Asche", Material.GRAY_DYE),
            new EffectOption("CRIMSON_SPORE", "Karmesin-Sporen", Material.CRIMSON_FUNGUS),
            new EffectOption("WARPED_SPORE", "Wirr-Sporen", Material.WARPED_FUNGUS),
            new EffectOption("SNOWFLAKE", "Schneeflocke", Material.SNOWBALL),
            new EffectOption("CHERRY_LEAVES", "Kirschblätter", Material.CHERRY_LEAVES),
            new EffectOption("SPORE_BLOSSOM_AIR", "Blütenstaub", Material.SPORE_BLOSSOM),
            new EffectOption("HAPPY_VILLAGER", "Grünes Funkeln", Material.EMERALD),
            new EffectOption("ANGRY_VILLAGER", "Wut-Wolke", Material.REDSTONE),
            new EffectOption("WITCH", "Hexen-Magie", Material.POTION),
            new EffectOption("NOTE", "Note", Material.NOTE_BLOCK),
            new EffectOption("WAX_ON", "Wachs-Glanz", Material.HONEYCOMB),
            new EffectOption("FLASH", "Blitz-Flash", Material.GLOWSTONE_DUST),
            new EffectOption("GUST", "Windstoß", Material.WIND_CHARGE),
            new EffectOption("CLOUD", "Wolke", Material.WHITE_WOOL),
            new EffectOption("POOF", "Puff", Material.WHITE_DYE),
            new EffectOption("FIREFLY", "Glühwürmchen", Material.GLOW_BERRIES),
            new EffectOption("RAID_OMEN", "Raid-Omen", Material.OMINOUS_BOTTLE),
            new EffectOption("OMINOUS_SPAWNING", "Böses Omen", Material.TRIAL_KEY),
            new EffectOption("DRIPPING_HONEY", "Honig-Tropfen", Material.HONEY_BOTTLE),
            new EffectOption("SNEEZE", "Nieser", Material.PANDA_SPAWN_EGG),
            new EffectOption("SQUID_INK", "Tinte", Material.INK_SAC),
            new EffectOption("NAUTILUS", "Nautilus", Material.NAUTILUS_SHELL));

    private final AdminServices services;
    private final SettingDefinition<String> effectDef;
    private final boolean admin;
    private final Runnable onBack;
    private int page;
    private List<EffectOption> cachedPalette;

    public EffectPickerMenu(Player viewer, AdminServices services,
                            SettingDefinition<String> effectDef, boolean admin, Runnable onBack) {
        super(viewer, 6, Component.text("Effekt: " + effectDef.key().replace("effects.", "")));
        this.services = services;
        this.effectDef = effectDef;
        this.admin = admin;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<String> tokens = currentTokens();

        set(4, Items.of(Material.BLAZE_ROD,
                Component.text(effectDef.key().replace("effects.", ""), NamedTextColor.GOLD),
                Component.text(effectDef.description(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Aktiv (" + tokens.size() + "): "
                        + (tokens.isEmpty() ? "—" : String.join(" + ", tokens)), NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("Links: an/aus · Rechts: probeansehen", NamedTextColor.AQUA),
                Component.text("Shift-Klick (aktiv): Feineinstellungen", NamedTextColor.AQUA)));

        List<EffectOption> palette = fullPalette();
        int totalPages = Math.max(1, (palette.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.floorMod(page, totalPages);
        int from = page * PER_PAGE;
        int to = Math.min(palette.size(), from + PER_PAGE);
        int slot = 9;
        for (int i = from; i < to; i++) {
            addOption(slot++, palette.get(i), tokens);
        }

        if (totalPages > 1) {
            set(45, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("◀ Seite " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE)),
                    click -> {
                        page--;
                        refresh();
                    });
            set(52, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("Seite " + (page + 1) + "/" + totalPages + " ▶", NamedTextColor.WHITE)),
                    click -> {
                        page++;
                        refresh();
                    });
        }

        set(47, Items.of(Material.FIREWORK_ROCKET,
                Component.text("▶ Kombination ansehen", NamedTextColor.GREEN),
                Component.text("Zeigt alle aktiven Effekte an dir", NamedTextColor.GRAY)),
                click -> {
                    if (tokens.isEmpty()) {
                        viewer.sendMessage(Messages.info("Kein Effekt aktiv."));
                    } else {
                        services.effects.test(viewer, String.join(";", tokens));
                    }
                });

        if (admin) {
            set(49, Items.of(Material.ANVIL,
                    Component.text("Eigener Partikel / Feintuning", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Freitext: PARTIKEL@anzahl~streuung", NamedTextColor.GRAY),
                    Component.text("*tempo >delay ^wdh #RRGGBB", NamedTextColor.GRAY),
                    Component.text("Aktuell: " + String.join("; ", tokens), NamedTextColor.DARK_GRAY)),
                    click -> openAnvil());

            set(51, Items.of(Material.LAVA_BUCKET,
                    Component.text("Alle entfernen", NamedTextColor.RED)),
                    click -> {
                        save(List.of());
                        refresh();
                    });
        }

        set(53, Items.back(), click -> onBack.run());
    }

    private void addOption(int slot, EffectOption option, List<String> tokens) {
        String token = findToken(option.name(), tokens);
        boolean active = token != null;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(option.name(), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(active ? Component.text("✔ aktiv", NamedTextColor.GREEN)
                : Component.text("inaktiv", NamedTextColor.GRAY));
        if (active) {
            lore.add(Component.text(token, NamedTextColor.YELLOW));
        }
        lore.add(Component.text("Rechtsklick: probeansehen", NamedTextColor.AQUA));
        if (admin) {
            lore.add(Component.text("Linksklick: " + (active ? "deaktivieren" : "aktivieren"),
                    NamedTextColor.AQUA));
            if (active) {
                lore.add(Component.text("Shift-Klick: Feineinstellungen", NamedTextColor.AQUA));
            }
        }

        ItemStack item = Items.of(option.icon(),
                Component.text(option.label(), active ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                lore);
        if (active) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }

        set(slot, item, click -> {
            if (click.isShiftClick() && active && admin) {
                new EffectLayerMenu(viewer, services, effectDef, option.name(), this::open).open();
            } else if (click.isRightClick()) {
                String preview = token != null ? token : option.name();
                services.effects.test(viewer, preview);
            } else if (click.isLeftClick() && admin) {
                toggle(option.name());
                refresh();
            }
        });
    }

    private List<EffectOption> fullPalette() {
        if (cachedPalette != null) {
            return cachedPalette;
        }
        List<EffectOption> combined = new ArrayList<>(PALETTE);
        Set<String> known = new HashSet<>();
        for (EffectOption option : PALETTE) {
            known.add(option.name());
        }
        for (String name : EffectService.allParticleNames()) {
            if (known.add(name)) {
                combined.add(new EffectOption(name, prettyLabel(name), Material.FIREWORK_STAR));
            }
        }
        cachedPalette = combined;
        return combined;
    }

    private static String prettyLabel(String name) {
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        String label = sb.toString().trim();
        return label.length() > 28 ? label.substring(0, 28) : label;
    }

    private void toggle(String name) {
        List<String> tokens = new ArrayList<>(currentTokens());
        boolean removed = tokens.removeIf(t -> bareName(t).equals(name));
        if (!removed) {
            tokens.add(name);
        }
        save(tokens);
    }

    private void save(List<String> tokens) {
        List<String> errors = services.config.begin(viewer.getName())
                .setFromInput(effectDef, String.join("; ", tokens), Scope.PERSISTENT).commit();
        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
    }

    private void openAnvil() {
        services.anvilInput.open(viewer, Component.text(effectDef.key()),
                services.config.get(effectDef),
                input -> {
                    List<String> errors = services.config.begin(viewer.getName())
                            .setFromInput(effectDef, input, Scope.PERSISTENT).commit();
                    if (errors.isEmpty()) {
                        viewer.sendMessage(Messages.ok(effectDef.key() + " = " + input.trim()));
                    } else {
                        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
                    }
                    open();
                }, this::open);
    }

    private List<String> currentTokens() {
        List<String> tokens = new ArrayList<>();
        for (String token : services.config.get(effectDef).split("[;,]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static String findToken(String name, List<String> tokens) {
        for (String token : tokens) {
            if (bareName(token).equals(name)) {
                return token;
            }
        }
        return null;
    }

    private static String bareName(String token) {
        return token.trim().replaceAll("[@~*>^][0-9.]*|#[0-9A-Fa-f]{6}", "").trim();
    }
}
