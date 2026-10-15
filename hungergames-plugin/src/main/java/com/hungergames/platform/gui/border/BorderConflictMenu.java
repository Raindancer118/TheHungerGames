package com.hungergames.platform.gui.border;

import com.hungergames.game.border.BorderConflict;
import com.hungergames.game.border.BorderMath;
import com.hungergames.game.border.BorderResolution;
import com.hungergames.game.border.BorderSettings;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.platform.Messages;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Auflösung eines Border-Konflikts auf einem ENTWURF: Die auslösende
 * Änderung ist noch nicht gespeichert. Jede Lösungsoption ist ein Item mit
 * den konkreten Auswirkungen in der Lore; erst der Bestätigungsklick
 * committet ursprüngliche Änderung und Lösung in EINER Transaktion.
 * „Verwerfen" speichert nichts.
 */
public final class BorderConflictMenu extends Menu {

    private final ConfigurationService config;
    private final HgSettings settings;
    private final BorderSettings draftSettings;
    private final Duration draftGameDuration;
    private final BorderConflict conflict;
    private final Scope scope;
    private final Consumer<ConfigurationService.Transaction> pendingChange;
    private final Runnable onDiscard;
    private final AnvilInput anvilInput;

    private BorderResolution pendingConfirmation;

    /**
     * @param draftSettings  die künftigen BorderSettings inkl. der gewünschten Änderung
     * @param pendingChange  staged die ursprüngliche Änderung in die Commit-Transaktion
     * @param onDiscard      wird bei „Verwerfen" gerufen (z. B. vorheriges Menü öffnen)
     */
    public BorderConflictMenu(Player viewer, ConfigurationService config, HgSettings settings,
                              BorderSettings draftSettings, Duration draftGameDuration,
                              BorderConflict conflict, Scope scope,
                              Consumer<ConfigurationService.Transaction> pendingChange,
                              Runnable onDiscard, AnvilInput anvilInput) {
        super(viewer, 4, Component.text("Border-Konflikt lösen"));
        this.config = config;
        this.settings = settings;
        this.draftSettings = draftSettings;
        this.draftGameDuration = draftGameDuration;
        this.conflict = conflict;
        this.scope = scope;
        this.pendingChange = pendingChange;
        this.onDiscard = onDiscard;
        this.anvilInput = anvilInput;
    }

    @Override
    protected void build() {
        set(4, Items.of(Material.BARRIER,
                Component.text("Konflikt in Phase " + (conflict.phaseIndex() + 1), NamedTextColor.RED),
                Component.text(describeConflict(), NamedTextColor.GRAY),
                Component.text(String.format(Locale.ROOT,
                                "Implizierte Geschwindigkeit: %.2f Blöcke/s (Limit: %.2f)",
                                conflict.impliedSpeed(), conflict.limit()),
                        NamedTextColor.DARK_GRAY),
                Component.text("Die Änderung ist NOCH NICHT gespeichert.", NamedTextColor.GOLD)));

        List<BorderResolution> options = BorderMath.resolutions(
                draftSettings, conflict, Optional.of(draftGameDuration));

        int slot = 10;
        for (BorderResolution option : options) {
            if (slot >= 26) {
                break;
            }
            boolean isPending = option.equals(pendingConfirmation);
            set(slot++, buildOptionItem(option, isPending), click -> {
                if (option instanceof BorderResolution.Discard) {
                    viewer.sendMessage(Messages.info(
                            "Änderung verworfen — es wurde nichts gespeichert."));
                    onDiscard.run();
                    return;
                }
                if (!option.equals(pendingConfirmation)) {
                    pendingConfirmation = option;
                    refresh(); // zweiter Klick bestätigt
                    return;
                }
                applyResolution(option);
            });
        }
    }

    private org.bukkit.inventory.ItemStack buildOptionItem(BorderResolution option, boolean pending) {
        record Display(Material material, String title, List<String> effects) {
        }
        Display display = switch (option) {
            case BorderResolution.AdjustDuration r -> new Display(Material.CLOCK,
                    "Dauer anpassen", List.of(
                    "Neue Dauer: " + r.newDuration().toSeconds() + "s",
                    String.format(Locale.ROOT, "Geschwindigkeit: %.2f Blöcke/s je Kante", r.resultingSpeed())));
            case BorderResolution.AdjustTarget r -> new Display(Material.TARGET,
                    "Zielgröße anpassen", List.of(
                    "Neue Zielgröße: " + (int) r.newTarget() + " Blöcke",
                    String.format(Locale.ROOT, "Geschwindigkeit: %.2f Blöcke/s je Kante", r.resultingSpeed())));
            case BorderResolution.ShiftStart r -> new Display(Material.REPEATER,
                    "Startzeitpunkt verschieben", List.of(
                    "Neuer Start: nach " + r.newStart().toMinutes() + "m Spielzeit"));
            case BorderResolution.AdjustGameTime r -> new Display(Material.SUNFLOWER,
                    "Spielzeit anpassen", List.of(
                    "Neue Spieldauer: " + r.newGameDuration().toMinutes() + "m",
                    "Border-Phase bleibt unverändert"));
            case BorderResolution.UseSpeedAsMax r -> new Display(Material.COMPARATOR,
                    "Geschwindigkeit als Maximum", List.of(
                    String.format(Locale.ROOT, "Effektiv: %.2f Blöcke/s je Kante", r.effectiveSpeed()),
                    "Resultierende Dauer: " + r.effectiveDuration().toSeconds() + "s"));
            case BorderResolution.Discard r -> new Display(Material.BARRIER,
                    "Änderung verwerfen", List.of("Es wird nichts gespeichert"));
        };

        var lore = new java.util.ArrayList<Component>();
        display.effects().forEach(effect ->
                lore.add(Component.text(effect, NamedTextColor.GRAY)));
        lore.add(Component.empty());
        lore.add(pending
                ? Component.text("NOCHMAL KLICKEN ZUM BESTÄTIGEN", NamedTextColor.GOLD)
                : Component.text("Klick: diese Lösung wählen (mit Bestätigung)", NamedTextColor.YELLOW));

        return Items.of(pending ? Material.LIME_CONCRETE : display.material(),
                Component.text(display.title(), pending ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                lore);
    }

    private void applyResolution(BorderResolution option) {
        BorderMath.ApplyResult applied = BorderMath.apply(draftSettings, conflict.phaseIndex(), option);

        var tx = config.begin(viewer.getName());
        // 1. Die ursprüngliche (bisher zurückgehaltene) Änderung
        pendingChange.accept(tx);
        // 2. Die Lösungs-Anpassungen (überschreiben ggf. denselben Key)
        if (!applied.settings().phases().equals(draftSettings.phases())) {
            tx.set(settings.borderPhases,
                    com.hungergames.platform.border.BorderPhaseSerializer.serialize(applied.settings()),
                    scope);
        }
        applied.newGameDuration().ifPresent(newDuration ->
                tx.set(settings.gameDuration, newDuration, scope));

        List<String> errors = tx.commit();
        if (errors.isEmpty()) {
            viewer.sendMessage(Messages.ok("Änderung und Konfliktlösung gemeinsam übernommen."));
        } else {
            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
        }
        viewer.closeInventory();
    }

    private String describeConflict() {
        return switch (conflict.type()) {
            case SPEED_EXCEEDS_MAX -> "Die Border wäre schneller als die Fairness-Grenze.";
            case TARGET_BELOW_MINIMUM -> "Die Zielgröße liegt unter der Mindestgröße.";
            case TARGET_NOT_SHRINKING -> "Die Zielgröße ist nicht kleiner als die Ausgangsgröße.";
            case EXCEEDS_GAME_TIME -> "Die Phase endet nach dem Spielende.";
            case PHASES_OUT_OF_ORDER -> "Die Phasen-Trigger sind nicht chronologisch.";
        };
    }
}
