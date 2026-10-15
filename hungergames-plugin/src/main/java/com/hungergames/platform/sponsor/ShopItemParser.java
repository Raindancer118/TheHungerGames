package com.hungergames.platform.sponsor;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Parser für Sponsor-Shop-Einträge im Format
 * {@code id|reward|kosten|Anzeigename}.
 *
 * <p>Reward-Formen:</p>
 * <ul>
 *   <li>{@code MATERIAL:ANZAHL} — z. B. {@code BREAD:8}</li>
 *   <li>{@code EFFECT:TYP:SEKUNDEN:STÄRKE} — z. B. {@code EFFECT:SPEED:15:0}</li>
 *   <li>{@code ITEM:FIENDFINDER:ANZAHL} — Custom-Items des Plugins</li>
 * </ul>
 *
 * <p>Effekt-Typen werden hier nur strukturell geprüft; die Auflösung gegen die
 * Effekt-Registry passiert zur Laufzeit (Registry ist ohne Server nicht
 * verfügbar).</p>
 */
public final class ShopItemParser {

    /** Bekannte Custom-Item-IDs des Plugins (Registry + Fiendfinder). */
    public static final Set<String> CUSTOM_ITEM_IDS = com.hungergames.items.CustomItems.ids();

    /** Belohnung eines Shop-Eintrags. */
    public sealed interface Reward permits MaterialReward, EffectReward, CustomItemReward, PotionReward {
    }

    public record MaterialReward(Material material, int amount) implements Reward {
    }

    public record EffectReward(String effectName, int durationSeconds, int amplifier) implements Reward {
    }

    public record CustomItemReward(String customId, int amount) implements Reward {
    }

    /** Trink-/Wurf-/Verweiltrank mit einem Basis-Potion-Typ (z. B. STRONG_HEALING). */
    public record PotionReward(PotionVariant variant, String potionType, int amount) implements Reward {
    }

    /** Trank-Variante → zugehöriges Material. */
    public enum PotionVariant {
        NORMAL(Material.POTION),
        SPLASH(Material.SPLASH_POTION),
        LINGERING(Material.LINGERING_POTION);

        private final Material material;

        PotionVariant(Material material) {
            this.material = material;
        }

        public Material material() {
            return material;
        }
    }

    /**
     * Ein geparster Shop-Eintrag. {@code enabled=false} = im Shop deaktiviert
     * (Zeile mit {@code #}). {@code enchantments} sind optionale {@code KEY:LEVEL}
     * -Angaben, die dem herausgegebenen Item angehängt werden.
     */
    public record ShopItem(String id, Reward reward, int cost, String displayName, boolean enabled,
                           List<String> enchantments) {

        public ShopItem withEnchantments(List<String> newEnchantments) {
            return new ShopItem(id, reward, cost, displayName, enabled, List.copyOf(newEnchantments));
        }
    }

    private ShopItemParser() {
    }

    /** @throws IllegalArgumentException bei ungültiger Syntax */
    public static ShopItem parse(String line) {
        // Führendes '#' = deaktivierter Eintrag (bleibt erhalten, erscheint aber
        // nicht im Shop) — so kann man Angebote ein-/ausschalten ohne sie zu löschen.
        String body = line.trim();
        boolean enabled = true;
        if (body.startsWith("#")) {
            enabled = false;
            body = body.substring(1).trim();
        }
        String[] parts = body.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "erwartet 'id|reward|kosten|Anzeigename' (4 Felder, " + parts.length + " gefunden)");
        }
        String id = parts[0].trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ID darf nicht leer sein");
        }
        // Reward-Feld: Basis + optionale Verzauberungen, getrennt durch '+'
        // z. B. DIAMOND_SWORD:1+SHARPNESS:5+UNBREAKING:3
        String[] rewardTokens = parts[1].trim().split("\\+");
        Reward reward = parseReward(rewardTokens[0].trim());
        List<String> enchantments = new ArrayList<>();
        for (int i = 1; i < rewardTokens.length; i++) {
            enchantments.add(parseEnchant(rewardTokens[i].trim()));
        }
        int cost;
        try {
            cost = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Kosten \"" + parts[2].trim() + "\" sind keine Zahl");
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("Kosten müssen > 0 sein");
        }
        String displayName = parts[3].trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Anzeigename darf nicht leer sein");
        }
        return new ShopItem(id, reward, cost, displayName, enabled, enchantments);
    }

    /** Validiert das Format {@code KEY:LEVEL} einer Verzauberung (Existenz prüft die Laufzeit). */
    private static String parseEnchant(String raw) {
        String[] parts = raw.split(":", -1);
        if (parts.length != 2 || parts[0].trim().isEmpty()) {
            throw new IllegalArgumentException("Verzauberung braucht 'KEY:LEVEL': " + raw);
        }
        parsePositiveInt(parts[1], "Verzauberungs-Level");
        return parts[0].trim().toLowerCase(Locale.ROOT) + ":" + parts[1].trim();
    }

    /** Serialisiert einen Eintrag zurück in seine Config-Zeile (inkl. Enchants/#). */
    public static String serialize(ShopItem item) {
        StringBuilder reward = new StringBuilder(rewardToString(item.reward()));
        for (String enchant : item.enchantments()) {
            reward.append('+').append(enchant);
        }
        String line = item.id() + "|" + reward + "|" + item.cost() + "|" + item.displayName();
        return item.enabled() ? line : "#" + line;
    }

    private static String rewardToString(Reward reward) {
        return switch (reward) {
            case MaterialReward m -> m.material().name() + ":" + m.amount();
            case CustomItemReward c -> "ITEM:" + c.customId() + ":" + c.amount();
            case EffectReward e -> "EFFECT:" + e.effectName() + ":" + e.durationSeconds()
                    + ":" + e.amplifier();
            case PotionReward p -> "POTION:" + p.variant().name() + ":" + p.potionType()
                    + ":" + p.amount();
        };
    }

    private static Reward parseReward(String raw) {
        String[] parts = raw.split(":", -1);
        String head = parts[0].trim().toUpperCase(Locale.ROOT);

        if (head.equals("EFFECT")) {
            if (parts.length != 4) {
                throw new IllegalArgumentException(
                        "EFFECT braucht 'EFFECT:TYP:SEKUNDEN:STÄRKE': " + raw);
            }
            String effect = parts[1].trim().toUpperCase(Locale.ROOT);
            if (effect.isEmpty()) {
                throw new IllegalArgumentException("Effekt-Typ darf nicht leer sein");
            }
            int seconds = parsePositiveInt(parts[2], "Effektdauer");
            int amplifier = parseNonNegativeInt(parts[3], "Effektstärke");
            return new EffectReward(effect, seconds, amplifier);
        }

        if (head.equals("ITEM")) {
            if (parts.length != 3) {
                throw new IllegalArgumentException("ITEM braucht 'ITEM:ID:ANZAHL': " + raw);
            }
            String customId = parts[1].trim().toUpperCase(Locale.ROOT);
            if (!CUSTOM_ITEM_IDS.contains(customId)) {
                throw new IllegalArgumentException("Unbekanntes Custom-Item: " + customId);
            }
            return new CustomItemReward(customId, parsePositiveInt(parts[2], "Anzahl"));
        }

        if (head.equals("POTION")) {
            if (parts.length != 4) {
                throw new IllegalArgumentException(
                        "POTION braucht 'POTION:VARIANTE:TYP:ANZAHL': " + raw);
            }
            PotionVariant variant;
            try {
                variant = PotionVariant.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Trank-Variante muss NORMAL, SPLASH oder LINGERING sein: " + parts[1]);
            }
            String type = parts[2].trim().toUpperCase(Locale.ROOT);
            if (type.isEmpty()) {
                throw new IllegalArgumentException("Trank-Typ darf nicht leer sein");
            }
            return new PotionReward(variant, type, parsePositiveInt(parts[3], "Anzahl"));
        }

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Material-Reward braucht 'MATERIAL:ANZAHL': " + raw);
        }
        Material material = Material.matchMaterial(head);
        if (material == null) {
            throw new IllegalArgumentException("Unbekanntes Material: " + head);
        }
        return new MaterialReward(material, parsePositiveInt(parts[1], "Anzahl"));
    }

    /** Parst alle Einträge; ungültige führen zur Exception (alles oder nichts). */
    public static List<ShopItem> parseAll(List<String> lines) {
        List<ShopItem> items = new ArrayList<>();
        for (String line : lines) {
            items.add(parse(line));
        }
        return items;
    }

    /** Validator für {@code sponsors.shop.items}; leeres Optional = gültig. */
    public static Optional<String> validateList(List<String> lines) {
        Set<String> seenIds = new HashSet<>();
        for (String line : lines) {
            try {
                ShopItem item = parse(line);
                if (!seenIds.add(item.id())) {
                    return Optional.of("Doppelte Shop-ID: " + item.id());
                }
            } catch (IllegalArgumentException e) {
                return Optional.of("\"" + line + "\": " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static int parsePositiveInt(String raw, String label) {
        int value = parseNonNegativeInt(raw, label);
        if (value <= 0) {
            throw new IllegalArgumentException(label + " muss > 0 sein");
        }
        return value;
    }

    private static int parseNonNegativeInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException(label + " darf nicht negativ sein");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " \"" + raw.trim() + "\" ist keine Zahl");
        }
    }
}
