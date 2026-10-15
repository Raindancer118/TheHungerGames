package com.hungergames.platform.gui.admin;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.border.BorderConflict;
import com.hungergames.game.border.BorderMath;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.config.ConfigMainMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Zentrale Admin- und Spielmacher-Schaltzentrale ({@code /hg admin}).
 *
 * <p>Buttons erscheinen abhängig von den Rechten des Betrachters: Admins
 * sehen alles, reine Spielmacher sehen Admin-Funktionen als gesperrt
 * markiert. Feature-Bereiche späterer Ausbaustufen registrieren ihre Buttons
 * hier, sobald ihr Service in {@link AdminServices} verfügbar ist.</p>
 */
public final class AdminMainMenu extends Menu {

    private final AdminServices services;

    public AdminMainMenu(Player viewer, AdminServices services) {
        super(viewer, 6, Component.text("HungerGames Admin"));
        this.services = services;
    }

    @Override
    protected void build() {
        boolean admin = Perms.isAdmin(viewer);

        set(4, statusItem());

        // === Reihe 1: Spielbetrieb ===
        section(10, Material.CLOCK, "Spielsteuerung",
                List.of("Status, Init, Startsequenz, Start,", "Runde beenden/zurücksetzen"),
                true, click -> new GameControlMenu(viewer, services).open());

        section(11, Material.COMPARATOR, "Konfiguration",
                List.of("Alle Einstellungen nach Kategorie"),
                admin, click -> new ConfigMainMenu(viewer, services.config,
                        services.anvilInput, services.borderGuard).open());

        section(12, Material.PLAYER_HEAD, "Spieler & Tribute",
                List.of("Whitelist, Wiederbeleben,", "Tribut-Übersicht"),
                admin, click -> new TributesMenu(viewer, services, this::open).open());

        section(13, Material.WHITE_BANNER, "Teams",
                List.of("Teams anlegen, löschen,", "zufällig verteilen"),
                admin, click -> new TeamAdminMenu(viewer, services, this::open).open());

        if (services.gamemaster != null) {
            boolean gmAccess = admin || services.gamemaster.isGamemaster(viewer);
            section(14, Material.COMPASS, "Spielmacher",
                    List.of("Spielmacher-Modus, Teleport,", "Namensliste verwalten"),
                    gmAccess, click -> new GamemasterMenu(viewer, services, this::open).open());
        }

        section(15, Material.BARRIER, "Worldborder",
                borderLore(), admin,
                click -> new com.hungergames.platform.gui.config.ConfigCategoryMenu(viewer,
                        services.config,
                        com.hungergames.game.config.SettingDefinition.SettingCategory.BORDER,
                        "Worldborder", services.anvilInput, this::open, services.borderGuard).open());

        section(16, Material.ENDER_EYE, "Spectator-Teleport",
                List.of("Zu lebenden Tributen teleportieren"),
                true, click -> services.spectator.openTeleportMenu(viewer));

        // === Reihe 2: Events & Systeme ===
        if (services.lootTables != null) {
            boolean lootDirty = services.lootTables.isDirty();
            section(28, Material.CHEST, "Loot",
                    List.of("LootTables ansehen, testen, bearbeiten",
                            lootDirty ? "⚠ Ungespeicherte Änderungen" : "Alles gespeichert"),
                    true, click -> new com.hungergames.platform.gui.loot.LootMainMenu(
                            viewer, services, this::open).open());
        }
        if (services.sponsorBeacons != null) {
            section(29, Material.NETHER_STAR, "Sponsor-System",
                    List.of("Beacons: " + services.sponsorBeacons.statusLine(),
                            "Tokens, Beacons, Shop verwalten"),
                    true, click -> new SponsorAdminMenu(viewer, services, this::open).open());
        }
        if (services.supplyDrops != null) {
            section(30, Material.CHEST_MINECART, "Supply Drops",
                    List.of("Status: " + services.supplyDrops.statusLine(),
                            "Zeitplan, manuell ausliefern"),
                    true, click -> new SupplyDropMenu(viewer, services, this::open).open());
        }
        if (services.deathmatch != null) {
            section(31, Material.NETHERITE_SWORD, "Deathmatch",
                    List.of("Status: " + services.deathmatch.statusLine(),
                            "Manuell starten, abbrechen,", "konfigurieren"),
                    true, click -> new DeathmatchMenu(viewer, services, this::open).open());
        }

        section(32, Material.NOTE_BLOCK, "Sounds, Ansagen & Effekte",
                List.of("Sounds & Partikel-Effekte pro Event,", "Ansage-Texte konfigurieren"),
                true, click -> new SoundsAnnouncementsMenu(viewer, services, this::open).open());

        section(33, Material.CRAFTING_TABLE, "Crafting-Rezepte",
                List.of("Exmatrikulator-Rezept im", "3×3-Raster bearbeiten"),
                admin, click -> new RecipeEditorMenu(viewer, services, this::open).open());

        section(34, Material.REDSTONE, "System & Debug",
                List.of("Reloads, Speedup, Tests,", "Fliptable (Server-Reset)"),
                admin, click -> new SystemDebugMenu(viewer, services, this::open).open());

        set(49, Items.close(), click -> viewer.closeInventory());
    }

    // ==================== Bausteine ====================

    /**
     * Registriert einen Bereichs-Button. Ohne Berechtigung erscheint er
     * ausgegraut mit Hinweis statt zu verschwinden — Spielmacher sollen
     * sehen, was es gibt.
     */
    void section(int slot, Material icon, String label, List<String> lore,
                 boolean allowed, Consumer<InventoryClickEvent> onClick) {
        if (allowed) {
            set(slot, Items.simple(icon, label, lore.toArray(String[]::new)), onClick);
        } else {
            set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(label, NamedTextColor.DARK_GRAY),
                    Component.text("Erfordert " + Perms.ADMIN, NamedTextColor.RED)));
        }
    }

    private org.bukkit.inventory.ItemStack statusItem() {
        GamePhase phase = services.session.phase();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Phase: ", NamedTextColor.GRAY)
                .append(Component.text(phase.name(), phaseColor(phase))));
        lore.add(Component.text("Tribute: ", NamedTextColor.GRAY)
                .append(Component.text(services.session.participants().aliveCount() + " lebend / "
                        + services.session.participants().all().size() + " registriert",
                        NamedTextColor.YELLOW)));
        if (phase == GamePhase.RUNNING) {
            Duration elapsed = services.virtualTime.elapsed();
            lore.add(Component.text("Spielzeit: ", NamedTextColor.GRAY)
                    .append(Component.text(elapsed.toMinutes() + "m " + elapsed.toSecondsPart() + "s",
                            NamedTextColor.YELLOW)));
        }
        services.session.winner().ifPresent(winner -> lore.add(
                Component.text("Sieger ermittelt — /init für die nächste Runde",
                        NamedTextColor.GOLD)));
        return Items.of(Material.NETHER_STAR,
                Component.text("HungerGames — Status", NamedTextColor.GOLD), lore);
    }

    private List<String> borderLore() {
        var borderSettings = services.border.currentSettings();
        Duration gameDuration = services.config.get(services.settings.gameDuration);
        List<BorderConflict> conflicts =
                BorderMath.validate(borderSettings, Optional.of(gameDuration));
        List<String> lore = new ArrayList<>();
        lore.add((int) borderSettings.initialSize() + " → min "
                + (int) borderSettings.minimumSize() + ", " + borderSettings.phases().size() + " Phasen");
        lore.add(conflicts.isEmpty() ? "Keine Konflikte"
                : "⚠ " + conflicts.size() + " Konflikt(e) — /hg border");
        lore.add("Klick: Border-Konfiguration");
        return lore;
    }

    static NamedTextColor phaseColor(GamePhase phase) {
        return switch (phase) {
            case NOT_INITIALIZED -> NamedTextColor.DARK_GRAY;
            case PREFLIGHT, LOBBY, STARTUP -> NamedTextColor.YELLOW;
            case READY -> NamedTextColor.AQUA;
            case RUNNING -> NamedTextColor.GREEN;
            case FINISHED -> NamedTextColor.GOLD;
        };
    }
}
