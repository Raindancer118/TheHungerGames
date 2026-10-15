package com.hungergames.items;

import com.hungergames.HungerGamesPlugin;
import com.hungergames.player.WhitelistManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Zentrale Registry der Custom-Sponsor-Items (neben dem {@link Fiendfinder}).
 * Jedes Item wird per PDC erkannt, im Shop gebaut und per Rechtsklick benutzt.
 * Neue coole Items einfach hier als Enum-Konstante mit Verhalten ergänzen.
 */
public enum CustomItems {

    /** Rauchbombe: benebelt Gegner, macht dich kurz komplett unsichtbar (inkl. Rüstung). */
    SMOKE_BOMB(Material.GUNPOWDER, "Rauchbombe", NamedTextColor.DARK_GRAY,
            List.of("Vernebelt Gegner im Umkreis",
                    "(Blindheit + Langsamkeit) und macht",
                    "dich 3s unsichtbar — inkl. Rüstung.")),

    /** Medikit: Vollheilung + Regeneration + Absorptions-Extraherzen — nach Countdown. */
    MEDIKIT(Material.GLISTERING_MELON_SLICE, "Medikit", NamedTextColor.RED,
            List.of("Vollheilung, Regeneration und",
                    "Extraherzen — wirkt nach kurzem",
                    "Countdown. Schaden bricht ab.",
                    "Der Sponsoren-Rettungsanker.")),

    /** Blitzschlag: ruft ein ganzes Gewitter aus mehreren Blitzen auf das Ziel herab. */
    LIGHTNING_STRIKE(Material.LIGHTNING_ROD, "Blitzschlag", NamedTextColor.AQUA,
            List.of("Ruft ein Gewitter aufs anvisierte Ziel:",
                    "mehrere Einschläge, Flächenschaden,",
                    "Feuer und Knockup. Das Kapitol zürnt.")),

    /** Hermes' Stiefel: bis zu 4 Sekunden fliegen. */
    HERMES_BOOTS(Material.FEATHER, "Hermes' Stiefel", NamedTextColor.YELLOW,
            List.of("Rechtsklick: fliege bis zu 4 Sekunden.",
                    "Flügel des Götterboten.")),

    /** Krückauwasser: Wurftrank — Ziele bekommen kurz Übelkeit + leichte Blindheit. */
    KRUECKAUWASSER(Material.SPLASH_POTION, "Krückauwasser", NamedTextColor.DARK_GREEN,
            List.of("Wirf es auf Gegner:",
                    "starke Übelkeit + leichte Blindheit.",
                    "Trübes Wasser aus der Krückau.")),

    /** Aura of Protection: kurzzeitige Schutzaura, die Gegner in Reichweite automatisch abwehrt. */
    AURA_OF_PROTECTION(Material.SHIELD, "Aura der Bewahrung", NamedTextColor.GOLD,
            List.of("Aktiviert ein paar Sekunden lang eine",
                    "Schutzaura: Gegner in Reichweite werden",
                    "automatisch getroffen & zurückgestoßen.")),

    /** Enterhaken: zieht dich zum anvisierten Punkt (Mobilität/Flucht). */
    GRAPPLING_HOOK(Material.FISHING_ROD, "Enterhaken", NamedTextColor.AQUA,
            List.of("Rechtsklick: zieht dich zum Punkt,",
                    "den du anvisierst — über Wände und",
                    "Schluchten hinweg.")),

    /** Druckwelle: stößt alle Gegner im Umkreis schlagartig weg. */
    REPULSE(Material.WIND_CHARGE, "Druckwelle", NamedTextColor.WHITE,
            List.of("Schleudert alle Gegner im Umkreis",
                    "schlagartig weg und verlangsamt sie kurz.",
                    "Schafft Luft im Nahkampf.")),

    /** Festmahl: füllt Hunger komplett und gibt goldene Äpfel. */
    FEAST(Material.COOKED_BEEF, "Festmahl des Kapitols", NamedTextColor.GOLD,
            List.of("Füllt Hunger & Sättigung komplett,",
                    "kurze Regeneration und goldene Äpfel.",
                    "Ein Geschenk der Sponsoren.")),

    /** Kriegsausrüstung: gibt ein komplettes Rüstungsset. */
    WAR_KIT(Material.IRON_CHESTPLATE, "Kriegsausrüstung", NamedTextColor.GRAY,
            List.of("Rechtsklick: komplettes Rüstungsset",
                    "(konfigurierbares Material).",
                    "Sofortiger Schutz.")),

    /** Sprungfeder: katapultiert dich in Blickrichtung nach vorn/oben. */
    LEAP(Material.SLIME_BLOCK, "Sprungfeder", NamedTextColor.GREEN,
            List.of("Rechtsklick: katapultiert dich in",
                    "Blickrichtung — mit sanfter Landung.",
                    "Perfekt für den schnellen Rückzug.")),

    /**
     * Trottel-Schutz: rettet automatisch vor tödlichem UMWELT-Schaden (Lava,
     * Sturz, Feuer, Ertrinken, Monster …) — NICHT vor Spieler-Kills oder
     * Admin-Eliminierung. Verbraucht sich wie ein Totem beim Auslösen.
     */
    STUPIDNESS_PROTECTOR(Material.NAUTILUS_SHELL, "Trottel-Schutz", NamedTextColor.DARK_AQUA,
            List.of("Passiv: rettet dich einmal vor tödlichem",
                    "Umweltschaden (Lava, Sturz, Feuer, Monster).",
                    "NICHT gegen Spieler-Kills. Für Tollpatsche.")),

    /**
     * Exmatrikulator: teures Craft-Item. Aktiviert für ein paar Sekunden eine
     * Blitz-Aura, die im Umkreis alles im Sekundenbruchteil-Takt mit Blitzsalven
     * beschießt — eine halbe Minigun aus Blitzen.
     */
    EXMATRIKULATOR(Material.BREEZE_ROD, "Exmatrikulator", NamedTextColor.LIGHT_PURPLE,
            List.of("Rechtsklick: entfesselt einige Sekunden lang",
                    "eine Blitz-Aura, die ALLES im Umkreis mit",
                    "Salven aus Blitzen niederstreckt.",
                    "Teuer craftbar — der große Rauswurf."));

    private static final double SMOKE_RADIUS = 6.0;
    private static final int SMOKE_DURATION_TICKS = 20 * 6;
    private static final int SMOKE_INVIS_SECONDS = 3;
    private static final int LIGHTNING_RANGE = 80;
    private static final int HERMES_FLIGHT_SECONDS = 4;
    private static final double KRUECKAU_RADIUS = 4.0;
    private static final int KRUECKAU_NAUSEA_TICKS = 20 * 8;
    private static final int KRUECKAU_BLIND_TICKS = 20 * 4;

    /** Wird beim Plugin-Start gesetzt (für die Rauchbomben-Unsichtbarkeit inkl. Rüstung). */
    private static InvisibilityCloak cloak;

    /** Laufende Medikit-Countdowns (UUID → Task); Schaden bricht ab. */
    private static final Map<UUID, org.bukkit.scheduler.BukkitRunnable> pendingMedikits =
            new java.util.HashMap<>();

    /**
     * Spieler, die kürzlich von einem Exmatrikulator-Blitz getroffen wurden.
     * Sterben sie innerhalb des Fensters, folgt auf ihren Namen die beim
     * Treffer ausgewürfelte Exmatrikulations-Phrase (Template + Modul +
     * Killer) — so erzählen Broadcast und Vanilla-Nachricht dieselbe
     * Geschichte.
     */
    private record ExmatrikulationHit(long expiry, String phrase) {
    }

    private static final Map<UUID, ExmatrikulationHit> exmatrikulatorHits = new java.util.HashMap<>();
    private static final long EXMATRIKULATOR_KILL_WINDOW_MS = 4000;

    /** Zentrale Services für konfigurierbare Sounds/Effekte der Items (Plugin-Start). */
    private static com.hungergames.platform.sound.SoundService sounds;
    private static com.hungergames.platform.effect.EffectService effects;
    private static com.hungergames.game.config.ConfigurationService config;
    private static com.hungergames.platform.config.HgSettings settings;

    public static void setCloak(InvisibilityCloak invisibilityCloak) {
        cloak = invisibilityCloak;
    }

    public static void setServices(com.hungergames.platform.sound.SoundService soundService,
                                   com.hungergames.platform.effect.EffectService effectService,
                                   com.hungergames.game.config.ConfigurationService configService,
                                   com.hungergames.platform.config.HgSettings hgSettings) {
        sounds = soundService;
        effects = effectService;
        config = configService;
        settings = hgSettings;
    }

    private static void itemSoundAt(org.bukkit.Location loc,
            com.hungergames.game.config.SettingDefinition<String> def) {
        if (sounds != null && def != null) {
            sounds.playAt(loc, def);
        }
    }

    /** Liest einen Integer-Wert aus der Config; Fallback, falls (noch) nicht verdrahtet. */
    private static int cfg(com.hungergames.game.config.SettingDefinition<Integer> def, int fallback) {
        return (config != null && def != null) ? config.get(def) : fallback;
    }

    private static void itemEffectAt(org.bukkit.Location loc,
            com.hungergames.game.config.SettingDefinition<String> def) {
        if (effects != null && def != null) {
            effects.playAt(loc, def);
        }
    }

    /** Konfigurierbarer Fiendfinder-Aktivierungssound (aus dem legacy Fiendfinder gerufen). */
    public static void playFiendfinderSound(org.bukkit.Location loc) {
        if (sounds != null && settings != null) {
            itemSoundAt(loc, settings.soundItemFiendfinder);
        }
    }

    private final Material material;
    private final String displayName;
    private final NamedTextColor color;
    private final List<String> loreLines;
    /** Lazy — nur zur Laufzeit (mit geladenem Plugin) erzeugbar, nicht im Enum-Init. */
    private NamespacedKey key;

    CustomItems(Material material, String displayName, NamedTextColor color, List<String> loreLines) {
        this.material = material;
        this.displayName = displayName;
        this.color = color;
        this.loreLines = loreLines;
    }

    private NamespacedKey key() {
        if (key == null) {
            key = new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class),
                    name().toLowerCase(Locale.ROOT));
        }
        return key;
    }

    public String id() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    /** Baut das Item für den Shop / die Vergabe. */
    public ItemStack create() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        var lore = new java.util.ArrayList<Component>();
        for (String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Rechtsklick zum Benutzen", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Einmalig", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key(), PersistentDataType.BOOLEAN, true);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key(), PersistentDataType.BOOLEAN);
    }

    /**
     * Führt das Item-Verhalten aus.
     *
     * @return {@code true}, wenn das Item verbraucht werden soll
     */
    public boolean use(Player user) {
        return switch (this) {
            case SMOKE_BOMB -> useSmokeBomb(user);
            case MEDIKIT -> useMedikit(user);
            case LIGHTNING_STRIKE -> useLightning(user);
            case HERMES_BOOTS -> useHermesBoots(user);
            case KRUECKAUWASSER -> useKrueckauwasser(user);
            case AURA_OF_PROTECTION -> useAura(user);
            case GRAPPLING_HOOK -> useGrapplingHook(user);
            case REPULSE -> useRepulse(user);
            case FEAST -> useFeast(user);
            case WAR_KIT -> useWarKit(user);
            case LEAP -> useLeap(user);
            case STUPIDNESS_PROTECTOR -> {
                user.sendMessage(Component.text("Trottel-Schutz ist passiv — er rettet dich "
                        + "automatisch vor tödlichem Umweltschaden.", NamedTextColor.DARK_AQUA));
                yield false; // nicht bei Rechtsklick verbrauchen
            }
            case EXMATRIKULATOR -> useExmatrikulator(user);
        };
    }

    private boolean useSmokeBomb(Player user) {
        Location center = user.getLocation();
        WhitelistManager whitelist = WhitelistManager.getInstance();
        double radius = settings != null ? cfg(settings.smokeBombRadius, 6) : SMOKE_RADIUS;
        int enemyTicks = (settings != null ? cfg(settings.smokeBombEnemyDuration, 6) : 6) * 20;
        int invisSeconds = settings != null ? cfg(settings.smokeBombInvisSeconds, SMOKE_INVIS_SECONDS)
                : SMOKE_INVIS_SECONDS;
        int affected = 0;
        for (Player other : user.getWorld().getPlayers()) {
            if (other.equals(user) || other.getGameMode() == GameMode.SPECTATOR
                    || !whitelist.isWhitelisted(other.getName())
                    || other.getLocation().distance(center) > radius) {
                continue;
            }
            other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, enemyTicks, 0));
            other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, enemyTicks, 1));
            affected++;
        }
        Location cloud = center.clone().add(0, 1, 0);
        if (settings != null) {
            itemEffectAt(cloud, settings.effectItemSmokeBomb);
            itemSoundAt(center, settings.soundItemSmokeBomb);
        }

        // Eigene Voll-Unsichtbarkeit inkl. Rüstung zum Abtauchen (0 = aus).
        if (invisSeconds > 0) {
            if (cloak != null) {
                cloak.cloak(user, invisSeconds);
            } else {
                user.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        invisSeconds * 20, 0, false, false, true));
            }
        }
        user.sendMessage(Component.text("Rauchbombe gezündet — " + affected
                + " Gegner vernebelt" + (invisSeconds > 0 ? ", du bist " + invisSeconds
                + "s unsichtbar" : "") + ".", NamedTextColor.GRAY));
        return true;
    }

    /**
     * Medikit mit Anlege-Countdown ({@code items.medikit.countdown-seconds},
     * 0 = sofort): Die Heilung wirkt erst nach Ablauf; nimmt der Nutzer in der
     * Zeit Schaden, bricht die Behandlung ab (siehe
     * {@link #abortMedikitOnDamage}) und das Item bleibt erhalten. Verbraucht
     * wird es deshalb erst beim Wirken.
     */
    private boolean useMedikit(Player user) {
        int countdown = Math.max(0, cfg(settings != null ? settings.medikitCountdownSeconds : null, 3));
        if (countdown <= 0) {
            applyMedikit(user);
            return true;
        }
        UUID id = user.getUniqueId();
        if (pendingMedikits.containsKey(id)) {
            user.sendMessage(Component.text("Ein Medikit wird bereits angelegt …", NamedTextColor.RED));
            return false;
        }
        user.sendMessage(Component.text("Medikit angelegt — wirkt in " + countdown
                + "s. Schaden bricht die Behandlung ab!", NamedTextColor.RED));

        JavaPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = countdown;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline() || p.isDead()) {
                    pendingMedikits.remove(id);
                    cancel();
                    return;
                }
                if (remaining > 0) {
                    p.sendActionBar(Component.text("Medikit wirkt in " + remaining + "s …",
                            NamedTextColor.RED));
                    remaining--;
                    return;
                }
                pendingMedikits.remove(id);
                cancel();
                // Erst jetzt verbrauchen — der Abbruch soll das Item nicht kosten.
                if (consumeFromInventory(p, MEDIKIT)) {
                    applyMedikit(p);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        pendingMedikits.put(id, task);
        return false; // Verbrauch erfolgt erst nach dem Countdown
    }

    /** Wendet die eigentliche Medikit-Heilung an (nach Countdown bzw. sofort). */
    private static void applyMedikit(Player user) {
        AttributeInstance maxHealth = user.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        user.setHealth(Math.min(max, max));
        user.setFoodLevel(20);
        user.setSaturation(20f);
        user.setFireTicks(0);
        int regenS = settings != null ? cfg(settings.medikitRegenSeconds, 6) : 6;
        int regenLvl = settings != null ? cfg(settings.medikitRegenLevel, 2) : 2;
        int absS = settings != null ? cfg(settings.medikitAbsorptionSeconds, 60) : 60;
        int absLvl = settings != null ? cfg(settings.medikitAbsorptionLevel, 2) : 2;
        if (regenS > 0) {
            user.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenS * 20, regenLvl - 1));
        }
        if (absS > 0) {
            user.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absS * 20, absLvl - 1));
        }
        if (settings != null) {
            itemEffectAt(user.getLocation().add(0, 1.5, 0), settings.effectItemMedikit);
            itemSoundAt(user.getLocation(), settings.soundItemMedikit);
        }
        user.sendMessage(Component.text("Medikit eingesetzt — vollständig geheilt.", NamedTextColor.RED));
    }

    /**
     * Bricht einen laufenden Medikit-Countdown ab, weil der Spieler Schaden
     * genommen hat (aus dem {@link ItemListener} gerufen). Das Item wurde noch
     * nicht verbraucht und bleibt erhalten.
     */
    public static void abortMedikitOnDamage(Player player) {
        org.bukkit.scheduler.BukkitRunnable task = pendingMedikits.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendActionBar(Component.text("Medikit unterbrochen — du hast Schaden genommen!",
                    NamedTextColor.RED));
            player.sendMessage(Component.text("Medikit unterbrochen — du hast Schaden genommen!",
                    NamedTextColor.RED));
        }
    }

    private boolean useLightning(Player user) {
        int range = cfg(settings != null ? settings.lightningRange : null, LIGHTNING_RANGE);
        Block target = user.getTargetBlockExact(range);
        Location center = target != null
                ? target.getLocation().add(0.5, 1, 0.5)
                : user.getEyeLocation().add(user.getEyeLocation().getDirection().multiply(30));
        World world = center.getWorld();
        if (world == null) {
            return false;
        }

        int bolts = Math.max(1, cfg(settings != null ? settings.lightningBoltCount : null, 6));
        int spread = cfg(settings != null ? settings.lightningSpread : null, 3);
        int bonus = cfg(settings != null ? settings.lightningBonusDamage : null, 8);
        int dmgRadius = Math.max(1, cfg(settings != null ? settings.lightningDamageRadius : null, 4));
        int fireTicks = cfg(settings != null ? settings.lightningFireTicks : null, 80);
        int delay = Math.max(0, cfg(settings != null ? settings.lightningBoltDelay : null, 3));
        boolean knockup = settings == null || config == null || config.get(settings.lightningKnockup);

        JavaPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < bolts; i++) {
            double ox = spread > 0 ? (rng.nextDouble() * 2 - 1) * spread : 0;
            double oz = spread > 0 ? (rng.nextDouble() * 2 - 1) * spread : 0;
            Location strike = center.clone().add(ox, 0, oz);
            Runnable bolt = () -> strikeBolt(world, strike, user, bonus, dmgRadius, fireTicks, knockup);
            long at = (long) i * delay;
            if (at <= 0) {
                bolt.run();
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, bolt, at);
            }
        }
        if (settings != null) {
            itemEffectAt(center, settings.effectItemLightning);
        }
        user.sendMessage(Component.text("⚡ Blitzsturm herabgerufen — " + bolts + " Einschläge!",
                NamedTextColor.AQUA));
        return true;
    }

    /**
     * Ein einzelner Blitz: NUR optischer Einschlag ({@code strikeLightningEffect},
     * kein Vanilla-Schaden) plus konfigurierbarer Flächenschaden/Feuer/Knockup.
     * Der Auslöser ({@code caster}) wird immer verschont — so kann sich niemand
     * mit dem eigenen Blitz (Exmatrikulator/Blitzschlag) selbst töten.
     */
    private static void strikeBolt(World world, Location strike, Player caster, int bonus,
                                   int radius, int fireTicks, boolean knockup) {
        world.strikeLightningEffect(strike);
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(strike, radius, radius, radius)) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity living)
                    || living.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (bonus > 0) {
                living.damage(bonus, caster);
            }
            if (fireTicks > 0) {
                living.setFireTicks(fireTicks);
            }
            if (knockup) {
                living.setVelocity(living.getVelocity().add(new org.bukkit.util.Vector(0, 0.6, 0)));
            }
        }
    }

    private boolean useExmatrikulator(Player user) {
        int seconds = Math.max(1, cfg(settings != null ? settings.exmatrikulatorDuration : null, 5));
        int radius = Math.max(1, cfg(settings != null ? settings.exmatrikulatorRadius : null, 8));
        int interval = Math.max(1, cfg(settings != null ? settings.exmatrikulatorInterval : null, 4));
        int bonus = cfg(settings != null ? settings.exmatrikulatorDamage : null, 6);
        int maxTargets = Math.max(1, cfg(settings != null ? settings.exmatrikulatorMaxTargets : null, 5));
        int fireTicks = cfg(settings != null ? settings.exmatrikulatorFireTicks : null, 40);

        if (settings != null) {
            itemSoundAt(user.getLocation(), settings.soundItemExmatrikulator);
        }
        user.sendMessage(Component.text("⚡ EXMATRIKULATOR entfesselt (" + seconds + "s)!",
                NamedTextColor.LIGHT_PURPLE));

        JavaPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        UUID id = user.getUniqueId();
        int totalTicks = seconds * 20;
        new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline() || elapsed >= totalTicks) {
                    cancel();
                    return;
                }
                Location center = p.getLocation();
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0),
                        20, radius / 2.0, 1.0, radius / 2.0, 0.1);
                if (settings != null) {
                    itemEffectAt(center, settings.effectItemExmatrikulator);
                }
                List<org.bukkit.entity.LivingEntity> targets = enemiesAround(p, radius, true);
                int fired = 0;
                for (org.bukkit.entity.LivingEntity target : targets) {
                    if (fired++ >= maxTargets) {
                        break;
                    }
                    strikeBolt(target.getWorld(), target.getLocation(), p, bonus, 2, fireTicks, false);
                    // Getroffene Spieler markieren: Tod im Zeitfenster →
                    // Todesnachricht „… wurde exmatrikuliert …" (mit Modul & Killer)
                    if (target instanceof Player hit) {
                        markExmatrikuliert(hit.getUniqueId(), p.getName());
                    }
                }
                elapsed += interval;
            }
        }.runTaskTimer(plugin, 0L, interval);
        return true;
    }

    private boolean useAura(Player user) {
        int seconds = cfg(settings != null ? settings.auraDurationSeconds : null, 5);
        int radius = Math.max(1, cfg(settings != null ? settings.auraRadius : null, 4));
        int damage = cfg(settings != null ? settings.auraDamage : null, 6);
        int interval = Math.max(1, cfg(settings != null ? settings.auraInterval : null, 10));
        double knockback = (settings != null ? cfg(settings.auraKnockback, 6) : 6) / 10.0;

        if (settings != null) {
            itemSoundAt(user.getLocation(), settings.soundItemAura);
        }
        user.sendMessage(Component.text("Aura der Bewahrung aktiv (" + seconds + "s).",
                NamedTextColor.GOLD));

        boolean affectMobs = settings == null || config == null || config.get(settings.auraAffectMobs);
        JavaPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        UUID id = user.getUniqueId();
        int totalTicks = seconds * 20;
        new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline() || elapsed >= totalTicks) {
                    cancel();
                    return;
                }
                Location center = p.getLocation();
                p.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add(0, 1, 0),
                        12, radius / 2.0, 0.8, radius / 2.0, 0.5);
                if (settings != null) {
                    itemEffectAt(center, settings.effectItemAura);
                }
                for (org.bukkit.entity.LivingEntity victim : enemiesAround(p, radius, affectMobs)) {
                    if (damage > 0) {
                        victim.damage(damage, p);
                    }
                    if (knockback > 0) {
                        victim.setVelocity(victim.getLocation().toVector()
                                .subtract(center.toVector()).setY(0.4).normalize().multiply(knockback));
                    }
                }
                elapsed += interval;
            }
        }.runTaskTimer(plugin, 0L, interval);
        return true;
    }

    /**
     * Gegner im Umkreis: lebende, whitelistete Feind-Spieler (nie der Nutzer/
     * Zuschauer) und — falls {@code includeMobs} — feindliche Mobs. Simulations-
     * Mannequins sind ausgenommen (die haben ihre eigene Trefferlogik).
     */
    private static List<org.bukkit.entity.LivingEntity> enemiesAround(Player user, double radius,
                                                                      boolean includeMobs) {
        WhitelistManager whitelist = WhitelistManager.getInstance();
        List<org.bukkit.entity.LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity entity : user.getWorld()
                .getNearbyEntities(user.getLocation(), radius, radius, radius)) {
            if (entity.equals(user) || entity instanceof org.bukkit.entity.Mannequin) {
                continue;
            }
            if (entity instanceof Player other) {
                if (other.getGameMode() != GameMode.SPECTATOR
                        && whitelist.isWhitelisted(other.getName())) {
                    targets.add(other);
                }
            } else if (includeMobs && entity instanceof org.bukkit.entity.Monster monster) {
                targets.add(monster);
            }
        }
        return targets;
    }

    private boolean useGrapplingHook(Player user) {
        int range = Math.max(1, cfg(settings != null ? settings.grapplingRange : null, 40));
        double power = (settings != null ? cfg(settings.grapplingPower, 14) : 14) / 10.0;
        Block target = user.getTargetBlockExact(range);
        Location dest = target != null ? target.getLocation().add(0.5, 1, 0.5)
                : user.getEyeLocation().add(user.getEyeLocation().getDirection().multiply(range));
        org.bukkit.util.Vector pull = dest.toVector().subtract(user.getLocation().toVector());
        if (pull.lengthSquared() < 0.01) {
            return false;
        }
        // Etwas Aufwärtsimpuls, damit man nicht in die Wand klatscht.
        user.setVelocity(pull.normalize().multiply(power).setY(Math.max(0.4, pull.getY() > 0 ? 0.6 : 0.3)));
        user.setFallDistance(0);
        if (settings != null) {
            itemSoundAt(user.getLocation(), settings.soundItemGrappling);
        }
        user.sendMessage(Component.text("Enterhaken!", NamedTextColor.AQUA));
        return true;
    }

    private boolean useRepulse(Player user) {
        int radius = Math.max(1, cfg(settings != null ? settings.repulseRadius : null, 6));
        double strength = (settings != null ? cfg(settings.repulseStrength, 12) : 12) / 10.0;
        int slowTicks = (settings != null ? cfg(settings.repulseSlowSeconds, 2) : 2) * 20;
        boolean affectMobs = settings == null || config == null || config.get(settings.repulseAffectMobs);
        Location center = user.getLocation();
        int affected = 0;
        for (org.bukkit.entity.LivingEntity victim : enemiesAround(user, radius, affectMobs)) {
            victim.setVelocity(victim.getLocation().toVector().subtract(center.toVector())
                    .setY(0.5).normalize().multiply(strength));
            if (slowTicks > 0) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1));
            }
            affected++;
        }
        if (settings != null) {
            itemEffectAt(center.clone().add(0, 1, 0), settings.effectItemRepulse);
            itemSoundAt(center, settings.soundItemRepulse);
        }
        user.sendMessage(Component.text("Druckwelle — " + affected + " Gegner weggeschleudert.",
                NamedTextColor.WHITE));
        return true;
    }

    private boolean useFeast(Player user) {
        user.setFoodLevel(20);
        user.setSaturation(20f);
        user.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1));
        int apples = cfg(settings != null ? settings.feastGoldenApples : null, 2);
        if (apples > 0) {
            giveItem(user, new ItemStack(Material.GOLDEN_APPLE, apples));
        }
        if (settings != null) {
            itemEffectAt(user.getLocation().add(0, 1, 0), settings.effectItemFeast);
            itemSoundAt(user.getLocation(), settings.soundItemFeast);
        }
        user.sendMessage(Component.text("Festmahl genossen — satt und gestärkt.", NamedTextColor.GOLD));
        return true;
    }

    private boolean useWarKit(Player user) {
        String tier = settings != null && config != null
                ? config.get(settings.warKitMaterial).toUpperCase(Locale.ROOT) : "IRON";
        Material helmet = Material.matchMaterial(tier + "_HELMET");
        Material chest = Material.matchMaterial(tier + "_CHESTPLATE");
        Material legs = Material.matchMaterial(tier + "_LEGGINGS");
        Material boots = Material.matchMaterial(tier + "_BOOTS");
        if (helmet == null || chest == null || legs == null || boots == null) {
            helmet = Material.IRON_HELMET;
            chest = Material.IRON_CHESTPLATE;
            legs = Material.IRON_LEGGINGS;
            boots = Material.IRON_BOOTS;
        }
        var inv = user.getInventory();
        equipOrGive(user, inv.getHelmet(), helmet, inv::setHelmet);
        equipOrGive(user, inv.getChestplate(), chest, inv::setChestplate);
        equipOrGive(user, inv.getLeggings(), legs, inv::setLeggings);
        equipOrGive(user, inv.getBoots(), boots, inv::setBoots);
        if (settings != null) {
            itemSoundAt(user.getLocation(), settings.soundItemWarKit);
        }
        user.sendMessage(Component.text("Kriegsausrüstung angelegt.", NamedTextColor.GRAY));
        return true;
    }

    private static void equipOrGive(Player user, ItemStack current, Material piece,
                                    java.util.function.Consumer<ItemStack> setter) {
        ItemStack armor = new ItemStack(piece);
        if (current == null || current.getType().isAir()) {
            setter.accept(armor);
        } else {
            giveItem(user, armor);
        }
    }

    private boolean useLeap(Player user) {
        double power = (settings != null ? cfg(settings.leapPower, 15) : 15) / 10.0;
        org.bukkit.util.Vector dir = user.getEyeLocation().getDirection().normalize();
        user.setVelocity(dir.multiply(power).setY(Math.max(0.5, dir.getY() + 0.5)));
        user.setFallDistance(0);
        // Kurze Slow-Falling gegen Sturzschaden bei der Landung.
        user.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 4, 0, false, false, false));
        if (settings != null) {
            itemEffectAt(user.getLocation(), settings.effectItemLeap);
            itemSoundAt(user.getLocation(), settings.soundItemLeap);
        }
        user.sendMessage(Component.text("Sprungfeder!", NamedTextColor.GREEN));
        return true;
    }

    private static void giveItem(Player user, ItemStack stack) {
        user.getInventory().addItem(stack).values()
                .forEach(rest -> user.getWorld().dropItemNaturally(user.getLocation(), rest));
    }

    /**
     * Versucht, einen Trottel-Schutz im Inventar zu finden, ihn zu verbrauchen
     * und den Spieler wie ein Totem zu retten (Heilung + Feuerschutz gegen Lava).
     *
     * @return {@code true}, wenn gerettet wurde (Schaden sollte gecancelt werden)
     */
    public static boolean tryStupidnessProtector(Player player) {
        if (!consumeFromInventory(player, STUPIDNESS_PROTECTOR)) {
            return false;
        }
        double max = 20.0;
        var maxAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr != null) {
            max = maxAttr.getValue();
        }
        int healHalfHearts = cfg(settings != null ? settings.stupidnessHealHearts : null, 8);
        int regenS = cfg(settings != null ? settings.stupidnessRegenSeconds : null, 8);
        int fireS = cfg(settings != null ? settings.stupidnessFireResistSeconds : null, 10);

        player.setHealth(Math.min(max, Math.max(1, healHalfHearts)));
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        if (regenS > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenS * 20, 1));
        }
        if (fireS > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireS * 20, 0));
        }
        // Sanfte Landung, falls es ein Sturz war.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 0, false, false, false));

        // Hübscher Schutz-Stoß: schleudert Gegner (und Mobs) rundum weg.
        int shoveRadius = cfg(settings != null ? settings.stupidnessShoveRadius : null, 5);
        double shoveStrength = (settings != null ? cfg(settings.stupidnessShoveStrength, 12) : 12) / 10.0;
        if (shoveRadius > 0 && shoveStrength > 0) {
            Location center = player.getLocation();
            for (org.bukkit.entity.LivingEntity victim : enemiesAround(player, shoveRadius, true)) {
                victim.setVelocity(victim.getLocation().toVector().subtract(center.toVector())
                        .setY(0.55).normalize().multiply(shoveStrength));
            }
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0),
                    12, shoveRadius / 2.0, 0.5, shoveRadius / 2.0, 0);
        }

        if (settings != null) {
            itemEffectAt(player.getLocation().add(0, 1, 0), settings.effectItemStupidness);
            itemSoundAt(player.getLocation(), settings.soundItemStupidness);
        }
        player.sendMessage(Component.text("Dein Trottel-Schutz hat dich gerettet!",
                NamedTextColor.DARK_AQUA));
        return true;
    }

    private static boolean consumeFromInventory(Player player, CustomItems type) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (type.matches(contents[i])) {
                ItemStack stack = contents[i];
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private boolean useHermesBoots(Player user) {
        // Kein Effekt, wenn ohnehin schon Flug erlaubt ist (z. B. Kreativ) — dann
        // würde das spätere Zurücksetzen den Kreativflug abschalten.
        if (user.getAllowFlight()) {
            user.sendMessage(Component.text("Du kannst bereits fliegen.", NamedTextColor.GRAY));
            return false;
        }
        user.setAllowFlight(true);
        user.setFlying(true);
        if (settings != null) {
            itemEffectAt(user.getLocation(), settings.effectItemHermes);
            itemSoundAt(user.getLocation(), settings.soundItemHermes);
        }
        int flightSeconds = settings != null ? cfg(settings.hermesFlightSeconds, HERMES_FLIGHT_SECONDS)
                : HERMES_FLIGHT_SECONDS;
        user.sendMessage(Component.text("Hermes' Stiefel — " + flightSeconds
                + "s Flug!", NamedTextColor.YELLOW));

        JavaPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        UUID id = user.getUniqueId();
        int warnSeconds = cfg(settings != null ? settings.hermesWarningSeconds : null, 3);
        // Sekundengenauer Countdown: Warnsound in den letzten N Sekunden, Ende bei 0.
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = flightSeconds;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline()
                        || p.getGameMode() == GameMode.CREATIVE
                        || p.getGameMode() == GameMode.SPECTATOR) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                            20 * 4, 0, false, false, false));
                    p.sendMessage(Component.text("Hermes' Stiefel verpuffen.", NamedTextColor.GRAY));
                    cancel();
                    return;
                }
                if (warnSeconds > 0 && remaining <= warnSeconds && settings != null) {
                    itemSoundAt(p.getLocation(), settings.soundItemHermesWarning);
                    p.sendActionBar(Component.text("Hermes' Stiefel: noch " + remaining + "s",
                            NamedTextColor.YELLOW));
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    private boolean useKrueckauwasser(Player user) {
        Snowball bottle = user.launchProjectile(Snowball.class);
        bottle.setItem(new ItemStack(Material.SPLASH_POTION));
        bottle.getPersistentDataContainer().set(krueckauKey(), PersistentDataType.BOOLEAN, true);
        if (settings != null) {
            itemSoundAt(user.getLocation(), settings.soundItemKrueckauThrow);
        }
        user.sendMessage(Component.text("Krückauwasser geworfen.", NamedTextColor.DARK_GREEN));
        return true;
    }

    /** Wird beim Aufprall des geworfenen Krückauwassers gerufen (aus dem ItemListener). */
    public static void onKrueckauImpact(Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        if (settings != null) {
            itemEffectAt(impact, settings.effectItemKrueckauImpact);
            itemSoundAt(impact, settings.soundItemKrueckauImpact);
        }
        double radius = settings != null ? cfg(settings.krueckauRadius, 4) : KRUECKAU_RADIUS;
        int nauseaTicks = (settings != null ? cfg(settings.krueckauNauseaSeconds, 12) : 12) * 20;
        int blindTicks = (settings != null ? cfg(settings.krueckauBlindnessSeconds, 0) : 0) * 20;
        WhitelistManager whitelist = WhitelistManager.getInstance();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR
                    || !whitelist.isWhitelisted(player.getName())
                    || player.getLocation().distance(impact) > radius) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0));
            if (blindTicks > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
            }
        }
    }

    public static boolean isKrueckauProjectile(org.bukkit.entity.Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(krueckauKey(), PersistentDataType.BOOLEAN);
    }

    /**
     * Merkt einen Exmatrikulator-Treffer vor: würfelt Todesnachricht-Template
     * ({@code items.exmatrikulator.death-messages}) und Modul
     * ({@code items.exmatrikulator.modules}) aus und speichert die fertige
     * Phrase für das Zeitfenster.
     */
    private static void markExmatrikuliert(UUID victim, String killerName) {
        long now = System.currentTimeMillis();
        exmatrikulatorHits.values().removeIf(hit -> hit.expiry() < now);
        exmatrikulatorHits.put(victim,
                new ExmatrikulationHit(now + EXMATRIKULATOR_KILL_WINDOW_MS,
                        buildExmatrikulationPhrase(killerName)));
    }

    private static String buildExmatrikulationPhrase(String killerName) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        List<String> templates = (config != null && settings != null)
                ? config.get(settings.exmatrikulatorDeathMessages) : List.of();
        String template = templates.isEmpty()
                ? "wurde exmatrikuliert, als er im Drittversuch in %modul% gegen %killer% versagte."
                : templates.get(rng.nextInt(templates.size()));

        List<String> modules = (config != null && settings != null)
                ? config.get(settings.exmatrikulatorModules) : List.of();
        String module = modules.isEmpty() ? "einem Wahlpflichtmodul"
                : modules.get(rng.nextInt(modules.size()));

        return template.replace("%modul%", module).replace("%killer%", killerName);
    }

    /**
     * @return die beim Treffer ausgewürfelte Exmatrikulations-Phrase (folgt
     *         auf den Spielernamen), wenn der Spieler kürzlich (im
     *         Zeitfenster) von einem Exmatrikulator-Blitz getroffen wurde —
     *         sein Tod gilt dann als Exmatrikulation.
     */
    public static Optional<String> exmatrikulationPhrase(UUID victim) {
        ExmatrikulationHit hit = exmatrikulatorHits.get(victim);
        if (hit == null) {
            return Optional.empty();
        }
        if (hit.expiry() < System.currentTimeMillis()) {
            exmatrikulatorHits.remove(victim);
            return Optional.empty();
        }
        return Optional.of(hit.phrase());
    }

    /** Schaden aus einem Custom-Item (z. B. der Blitzschlag-Blitz) — nicht durch Trottel-Schutz abfangbar. */
    public static boolean isCustomDamageSource(org.bukkit.entity.Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(customDamageKey(), PersistentDataType.BOOLEAN);
    }

    private static NamespacedKey krueckauKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "krueckau_projectile");
    }

    private static NamespacedKey customDamageKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "custom_damage_source");
    }

    // ==================== Crafting ====================

    /**
     * Registriert (bzw. aktualisiert) die Crafting-Rezepte der craftbaren
     * Custom-Items. Das Exmatrikulator-Rezept kommt vollständig aus der Config
     * ({@code items.exmatrikulator.recipe}) und ist damit frei editierbar.
     */
    public static void registerRecipes(JavaPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "exmatrikulator");
        try {
            plugin.getServer().removeRecipe(key);
        } catch (RuntimeException ignored) {
            // Rezept existierte noch nicht
        }

        List<String> rows = (config != null && settings != null)
                ? config.get(settings.exmatrikulatorRecipe)
                : List.of("LIGHTNING_ROD DIAMOND_BLOCK LIGHTNING_ROD",
                        "NETHERITE_INGOT DIAMOND_BLOCK NETHERITE_INGOT",
                        "LIGHTNING_ROD DIAMOND_BLOCK LIGHTNING_ROD");

        // 3×3-Raster aus der Config lesen (fehlende Zeilen/Felder = leer).
        Material[][] grid = new Material[3][3];
        for (int r = 0; r < 3 && r < rows.size(); r++) {
            String[] tokens = rows.get(r).trim().split("\\s+");
            for (int c = 0; c < 3 && c < tokens.length; c++) {
                String token = tokens[c].trim();
                if (token.isEmpty() || token.equals("-") || token.equalsIgnoreCase("AIR")) {
                    continue;
                }
                Material material = Material.matchMaterial(token);
                if (material == null) {
                    plugin.getLogger().warning("Exmatrikulator-Rezept: unbekanntes Material \""
                            + token + "\" — Feld bleibt leer.");
                    continue;
                }
                grid[r][c] = material;
            }
        }

        // Bounding-Box der belegten Felder bestimmen — komplett leere Rand-
        // Zeilen/-Spalten dürfen nicht ins Shape, sonst lehnt Bukkit das
        // Rezept ab (relevant für Teil-Rezepte aus dem GUI-Editor).
        int rMin = 3, rMax = -1, cMin = 3, cMax = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (grid[r][c] != null) {
                    rMin = Math.min(rMin, r);
                    rMax = Math.max(rMax, r);
                    cMin = Math.min(cMin, c);
                    cMax = Math.max(cMax, c);
                }
            }
        }
        if (rMax < 0) {
            plugin.getLogger().warning("Exmatrikulator-Rezept ist leer — kein Rezept registriert.");
            return;
        }

        // Materialien → Zeichen; Shape nur über die belegte Bounding-Box bauen.
        Map<Material, Character> chars = new LinkedHashMap<>();
        char next = 'A';
        String[] shape = new String[rMax - rMin + 1];
        for (int r = rMin; r <= rMax; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = cMin; c <= cMax; c++) {
                Material material = grid[r][c];
                if (material == null) {
                    row.append(' ');
                } else {
                    char ch = chars.computeIfAbsent(material, m -> (char) 0);
                    if (ch == 0) {
                        ch = next++;
                        chars.put(material, ch);
                    }
                    row.append(ch);
                }
            }
            shape[r - rMin] = row.toString();
        }

        org.bukkit.inventory.ShapedRecipe recipe =
                new org.bukkit.inventory.ShapedRecipe(key, EXMATRIKULATOR.create());
        recipe.shape(shape);
        chars.forEach((material, ch) -> recipe.setIngredient(ch, material));
        try {
            plugin.getServer().addRecipe(recipe);
        } catch (IllegalStateException | IllegalArgumentException e) {
            plugin.getLogger().warning("Exmatrikulator-Rezept konnte nicht registriert werden: "
                    + e.getMessage());
        }
    }

    // ==================== Statische Fassade ====================

    public static Optional<CustomItems> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (CustomItems item : values()) {
            if (item.name().equalsIgnoreCase(id)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** Alle bekannten Custom-Item-IDs (für Shop-Validierung), inkl. Fiendfinder. */
    public static Set<String> ids() {
        Set<String> ids = java.util.stream.Stream.of(values())
                .map(Enum::name).collect(Collectors.toCollection(java.util.HashSet::new));
        ids.add("FIENDFINDER");
        return Set.copyOf(ids);
    }

    /** Baut ein Custom-Item nach ID (auch {@code FIENDFINDER}); {@code null} bei unbekannt. */
    public static ItemStack create(String id) {
        if ("FIENDFINDER".equalsIgnoreCase(id)) {
            return Fiendfinder.createItem();
        }
        return byId(id).map(CustomItems::create).orElse(null);
    }

    /** Material-Icon für die Shop-Anzeige; {@link Material#SPYGLASS} als Fallback. */
    public static Material iconFor(String id) {
        if ("FIENDFINDER".equalsIgnoreCase(id)) {
            return Material.SPYGLASS;
        }
        return byId(id).map(CustomItems::material).orElse(Material.SPYGLASS);
    }

    /**
     * Behandelt einen Rechtsklick mit einem Custom-Item (außer Fiendfinder, der
     * seinen eigenen Handler hat).
     *
     * @return {@code true}, wenn es ein Custom-Item war (Event sollte gecancelt werden)
     */
    public static boolean handleInteract(Player player, ItemStack item) {
        for (CustomItems custom : values()) {
            if (custom.matches(item)) {
                if (custom.use(player)) {
                    consume(player, item);
                }
                return true;
            }
        }
        return false;
    }

    private static void consume(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
