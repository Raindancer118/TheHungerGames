package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GUI-Sound-Auswahl für ein Event: eine große, kuratierte Palette gängiger
 * Sounds als Toggles. Mehrere können gleichzeitig aktiv sein — sie werden als
 * Ebenen gleichzeitig abgespielt ({@code ;}-Liste im Config-Wert). Pro aktivem
 * Sound lassen sich Lautstärke und ein Echo einstellen.
 *
 * <ul>
 *   <li>Linksklick: an/aus</li>
 *   <li>Rechtsklick: probehören</li>
 *   <li>Shift+Links (aktiv): Lautstärke durchschalten</li>
 *   <li>Shift+Rechts (aktiv): Echo an/aus</li>
 * </ul>
 *
 * <p>Der Anvil-Button bleibt für freie Resource-Pack-Keys und Feintuning
 * ({@code @lautstärke~tonhöhe^echo}).</p>
 */
public final class SoundPickerMenu extends Menu {

    private record SoundOption(String key, String label, Material icon) {
    }

    private static final Pattern VOLUME_PAT = Pattern.compile("@([0-9.]+)");
    private static final int PER_PAGE = 36;

    /** Kuratierte Auswahl gut klingender Event-Sounds. */
    private static final List<SoundOption> PALETTE = List.of(
            new SoundOption("ENTITY_WITHER_SPAWN", "Wither-Spawn", Material.WITHER_SKELETON_SKULL),
            new SoundOption("ENTITY_WITHER_HURT", "Wither-Schrei", Material.WITHER_ROSE),
            new SoundOption("ENTITY_WITHER_DEATH", "Wither-Tod", Material.NETHER_STAR),
            new SoundOption("ENTITY_ENDER_DRAGON_GROWL", "Drachen-Brüllen", Material.DRAGON_HEAD),
            new SoundOption("ENTITY_ENDER_DRAGON_DEATH", "Drachen-Tod", Material.DRAGON_EGG),
            new SoundOption("ENTITY_ENDER_DRAGON_FLAP", "Drachen-Flügel", Material.ELYTRA),
            new SoundOption("ENTITY_WARDEN_ROAR", "Warden-Brüllen", Material.SCULK_SHRIEKER),
            new SoundOption("ENTITY_WARDEN_SONIC_BOOM", "Sonic Boom", Material.SCULK_CATALYST),
            new SoundOption("ENTITY_WARDEN_HEARTBEAT", "Warden-Herzschlag", Material.SCULK_SENSOR),
            new SoundOption("ENTITY_GHAST_SCREAM", "Ghast-Schrei", Material.GHAST_TEAR),
            new SoundOption("ENTITY_GHAST_SHOOT", "Ghast-Schuss", Material.FIRE_CHARGE),
            new SoundOption("ENTITY_BLAZE_SHOOT", "Blaze-Schuss", Material.BLAZE_POWDER),
            new SoundOption("ENTITY_ENDERMAN_SCREAM", "Enderman-Schrei", Material.ENDER_EYE),
            new SoundOption("ENTITY_GENERIC_EXPLODE", "Explosion", Material.TNT),
            new SoundOption("ENTITY_DRAGON_FIREBALL_EXPLODE", "Drachen-Explosion", Material.FIRE_CHARGE),
            new SoundOption("ENTITY_FIREWORK_ROCKET_LARGE_BLAST", "Feuerwerk groß", Material.FIREWORK_ROCKET),
            new SoundOption("ENTITY_FIREWORK_ROCKET_BLAST", "Feuerwerk", Material.FIREWORK_STAR),
            new SoundOption("ENTITY_LIGHTNING_BOLT_THUNDER", "Donner", Material.LIGHTNING_ROD),
            new SoundOption("ENTITY_LIGHTNING_BOLT_IMPACT", "Blitz-Einschlag", Material.END_ROD),
            new SoundOption("BLOCK_ANVIL_LAND", "Amboss-Aufprall", Material.ANVIL),
            new SoundOption("ITEM_TOTEM_USE", "Totem", Material.TOTEM_OF_UNDYING),
            new SoundOption("UI_TOAST_CHALLENGE_COMPLETE", "Fanfare", Material.EXPERIENCE_BOTTLE),
            new SoundOption("ENTITY_PLAYER_LEVELUP", "Level-Up", Material.GLOWSTONE_DUST),
            new SoundOption("ENTITY_EXPERIENCE_ORB_PICKUP", "XP-Ping", Material.EXPERIENCE_BOTTLE),
            new SoundOption("BLOCK_BELL_USE", "Glocke", Material.BELL),
            new SoundOption("BLOCK_BELL_RESONATE", "Glocken-Nachhall", Material.BELL),
            new SoundOption("EVENT_RAID_HORN", "Raid-Horn", Material.GOAT_HORN),
            new SoundOption("ITEM_GOAT_HORN_SOUND_0", "Ziegenhorn", Material.GOAT_HORN),
            new SoundOption("BLOCK_BEACON_ACTIVATE", "Beacon aktiv", Material.BEACON),
            new SoundOption("BLOCK_BEACON_POWER_SELECT", "Beacon-Wahl", Material.BEACON),
            new SoundOption("BLOCK_BEACON_DEACTIVATE", "Beacon aus", Material.GLASS),
            new SoundOption("BLOCK_CONDUIT_ACTIVATE", "Conduit", Material.CONDUIT),
            new SoundOption("ENTITY_ENDERMAN_TELEPORT", "Enderman-Teleport", Material.ENDER_PEARL),
            new SoundOption("BLOCK_END_PORTAL_SPAWN", "End-Portal", Material.END_PORTAL_FRAME),
            new SoundOption("BLOCK_PORTAL_TRIGGER", "Portal", Material.OBSIDIAN),
            new SoundOption("BLOCK_RESPAWN_ANCHOR_CHARGE", "Respawn-Anker", Material.RESPAWN_ANCHOR),
            new SoundOption("ENTITY_ZOMBIE_VILLAGER_CURE", "Heilung", Material.GOLDEN_APPLE),
            new SoundOption("ENTITY_EVOKER_CAST_SPELL", "Zauber", Material.TOTEM_OF_UNDYING),
            new SoundOption("ENTITY_ILLUSIONER_CAST_SPELL", "Illusion", Material.ENDER_EYE),
            new SoundOption("ENTITY_ELDER_GUARDIAN_CURSE", "Fluch", Material.PRISMARINE_SHARD),
            new SoundOption("BLOCK_NOTE_BLOCK_PLING", "Note: Pling", Material.NOTE_BLOCK),
            new SoundOption("BLOCK_NOTE_BLOCK_BELL", "Note: Bell", Material.NOTE_BLOCK),
            new SoundOption("BLOCK_NOTE_BLOCK_BASS", "Note: Bass", Material.NOTE_BLOCK),
            new SoundOption("BLOCK_NOTE_BLOCK_HARP", "Note: Harp", Material.NOTE_BLOCK),
            new SoundOption("BLOCK_NOTE_BLOCK_CHIME", "Note: Chime", Material.NOTE_BLOCK),
            new SoundOption("BLOCK_NOTE_BLOCK_XYLOPHONE", "Note: Xylophon", Material.NOTE_BLOCK),
            new SoundOption("ENTITY_VILLAGER_CELEBRATE", "Dorfbewohner-Jubel", Material.EMERALD),
            new SoundOption("UI_BUTTON_CLICK", "Klick", Material.STONE_BUTTON),
            new SoundOption("ENTITY_VILLAGER_NO", "Nein / Denied", Material.BARRIER),
            new SoundOption("ENTITY_ITEM_BREAK", "Item zerbricht", Material.STICK),
            new SoundOption("BLOCK_GLASS_BREAK", "Glasbruch", Material.GLASS),
            new SoundOption("ENTITY_GENERIC_BIG_FALL", "Aufprall", Material.IRON_BOOTS),
            new SoundOption("AMBIENT_CAVE", "Höhlen-Ambiente", Material.SCULK),
            new SoundOption("BLOCK_BEACON_AMBIENT", "Beacon-Summen", Material.BEACON),
            new SoundOption("MUSIC_DISC_PIGSTEP", "Disc: Pigstep", Material.MUSIC_DISC_PIGSTEP));

    private final AdminServices services;
    private final SettingDefinition<String> soundDef;
    private final boolean admin;
    private final Runnable onBack;
    private int page;
    private List<SoundOption> cachedPalette;

    public SoundPickerMenu(Player viewer, AdminServices services,
                           SettingDefinition<String> soundDef, boolean admin, Runnable onBack) {
        super(viewer, 6, Component.text("Sounds: " + soundDef.key().replace("sounds.", "")));
        this.services = services;
        this.soundDef = soundDef;
        this.admin = admin;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<String> tokens = currentTokens();

        set(4, Items.of(Material.JUKEBOX,
                Component.text(soundDef.key().replace("sounds.", ""), NamedTextColor.GOLD),
                Component.text(soundDef.description(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Aktiv (" + tokens.size() + "): "
                        + (tokens.isEmpty() ? "—" : String.join(" + ", tokens)), NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("Links: an/aus · Rechts: probehören", NamedTextColor.AQUA),
                Component.text("Shift-Klick (aktiv): Feineinstellungen", NamedTextColor.AQUA)));

        List<SoundOption> palette = fullPalette();
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

        set(47, Items.of(Material.NOTE_BLOCK,
                Component.text("▶ Kombination testen", NamedTextColor.GREEN),
                Component.text("Spielt alle aktiven Sounds gleichzeitig", NamedTextColor.GRAY)),
                click -> {
                    if (tokens.isEmpty()) {
                        viewer.sendMessage(Messages.info("Kein Sound aktiv."));
                    } else if (!services.sounds.test(viewer, String.join(";", tokens))) {
                        viewer.sendMessage(Messages.error("Keiner der Sounds ist auflösbar "
                                + "(Resource-Pack-Sounds brauchen den Client)."));
                    }
                });

        set(48, Items.of(Material.BARRIER,
                Component.text("⏹ Sounds stoppen", NamedTextColor.RED),
                Component.text("Bricht alle laufenden Preview-Sounds ab", NamedTextColor.GRAY)),
                click -> viewer.stopSound(net.kyori.adventure.sound.SoundStop.all()));

        if (admin) {
            set(49, Items.of(Material.ANVIL,
                    Component.text("Eigener Key / Feintuning", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Freitext: Resource-Pack-Keys und", NamedTextColor.GRAY),
                    Component.text("@lautstärke~tonhöhe^echo pro Sound", NamedTextColor.GRAY),
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

    private void addOption(int slot, SoundOption option, List<String> tokens) {
        String token = findToken(option.key(), tokens);
        boolean active = token != null;
        float volume = active ? volumeOf(token) : 1.0f;
        boolean echo = active && token.indexOf('^') >= 0;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(option.key(), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (active) {
            lore.add(Component.text("✔ aktiv", NamedTextColor.GREEN));
            lore.add(Component.text("Lautstärke: " + formatVol(volume), NamedTextColor.YELLOW));
            lore.add(Component.text("Echo: " + (echo ? "an" : "aus"),
                    echo ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("inaktiv", NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Rechtsklick: probehören", NamedTextColor.AQUA));
        if (admin) {
            lore.add(Component.text("Linksklick: " + (active ? "deaktivieren" : "aktivieren"),
                    NamedTextColor.AQUA));
            if (active) {
                lore.add(Component.text("Shift-Klick: Feineinstellungen (Echo, Delay, Dauer …)",
                        NamedTextColor.AQUA));
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
            if (!admin) {
                if (click.isRightClick()) {
                    previewSingle(option.key(), tokens);
                }
                return;
            }
            if (click.isShiftClick() && active) {
                new SoundLayerMenu(viewer, services, soundDef, option.key(), this::open).open();
            } else if (click.isRightClick()) {
                previewSingle(option.key(), tokens);
            } else if (click.isLeftClick()) {
                toggle(option.key());
                refresh();
            }
        });
    }

    /**
     * Vollständige Palette: erst die kuratierten Favoriten (schöne Icons/Namen),
     * dann JEDER weitere Sound aus der Registry — so ist wirklich jeder Sound
     * auswählbar (Rauchbomben-/Medikit-Sounds inklusive).
     */
    private List<SoundOption> fullPalette() {
        if (cachedPalette != null) {
            return cachedPalette;
        }
        List<SoundOption> combined = new ArrayList<>(PALETTE);
        java.util.Set<String> known = new java.util.HashSet<>();
        for (SoundOption option : PALETTE) {
            known.add(option.key());
        }
        for (String key : services.sounds.allSoundNames()) {
            if (known.add(key)) {
                combined.add(new SoundOption(key, prettyLabel(key), Material.NOTE_BLOCK));
            }
        }
        cachedPalette = combined;
        return combined;
    }

    /** "ENTITY_WITHER_SPAWN" → "Wither Spawn" (Präfixe gekürzt, Title-Case). */
    private static String prettyLabel(String key) {
        String s = key;
        for (String prefix : new String[]{"ENTITY_", "BLOCK_", "ITEM_", "UI_", "MUSIC_", "AMBIENT_", "EVENT_"}) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }
        String[] words = s.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        String label = sb.toString().trim();
        return label.length() > 28 ? label.substring(0, 28) : label;
    }

    private void previewSingle(String key, List<String> tokens) {
        String token = findToken(key, tokens);
        if (!services.sounds.test(viewer, token != null ? token : key)) {
            viewer.sendMessage(Messages.error("Sound \"" + key + "\" ist nicht auflösbar."));
        }
    }

    // ==================== Token-Manipulation ====================

    /** Fügt den Key hinzu oder entfernt alle Einträge mit diesem Basis-Key. */
    private void toggle(String key) {
        List<String> tokens = new ArrayList<>(currentTokens());
        boolean removed = tokens.removeIf(t -> bareKey(t).equals(key));
        if (!removed) {
            tokens.add(key);
        }
        save(tokens);
    }

    private void save(List<String> tokens) {
        String joined = String.join("; ", tokens);
        List<String> errors = services.config.begin(viewer.getName())
                .setFromInput(soundDef, joined, Scope.PERSISTENT).commit();
        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
    }

    private void openAnvil() {
        services.anvilInput.open(viewer, Component.text(soundDef.key()),
                services.config.get(soundDef),
                input -> {
                    List<String> errors = services.config.begin(viewer.getName())
                            .setFromInput(soundDef, input, Scope.PERSISTENT).commit();
                    if (errors.isEmpty()) {
                        viewer.sendMessage(Messages.ok(soundDef.key() + " = " + input.trim()));
                    } else {
                        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
                    }
                    open();
                }, this::open);
    }

    private List<String> currentTokens() {
        String raw = services.config.get(soundDef);
        List<String> tokens = new ArrayList<>();
        for (String token : raw.split("[;,]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static String findToken(String key, List<String> tokens) {
        for (String token : tokens) {
            if (bareKey(token).equals(key)) {
                return token;
            }
        }
        return null;
    }

    private static float volumeOf(String token) {
        Matcher m = VOLUME_PAT.matcher(token);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (NumberFormatException ignored) {
                // fällt auf Default
            }
        }
        return 1.0f;
    }

    private static String formatVol(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /** Basis-Sound-Key ohne {@code @volume}/{@code ~pitch}/{@code ^echo}. */
    private static String bareKey(String token) {
        return token.trim().replaceAll("[@~^][0-9.]*", "").trim();
    }
}
