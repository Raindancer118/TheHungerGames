package com.hungergames.platform.border;

import com.hungergames.game.border.BorderConflict;
import com.hungergames.game.border.BorderMath;
import com.hungergames.game.border.BorderSettings;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.border.BorderConflictMenu;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Fängt Border-relevante Konfigurationsänderungen ab, BEVOR sie gespeichert
 * werden: Aus dem Änderungswunsch wird ein vollständiger Entwurf der
 * künftigen {@link BorderSettings} gebaut und via {@link BorderMath#validate}
 * geprüft. Bei Konflikten öffnet sich sofort das {@link BorderConflictMenu} —
 * gespeichert wird erst, wenn der Admin dort eine Lösung bestätigt.
 */
public final class BorderDraftGuard implements ConfigCategoryMenu.ChangeInterceptor {

    private final ConfigurationService config;
    private final HgSettings settings;
    private final AnvilInput anvilInput;

    public BorderDraftGuard(ConfigurationService config, HgSettings settings, AnvilInput anvilInput) {
        this.config = config;
        this.settings = settings;
        this.anvilInput = anvilInput;
    }

    @Override
    public <T> boolean intercept(Player viewer, SettingDefinition<T> def, T value,
                                 Scope scope, Runnable reopen) {
        if (!isBorderRelevant(def)) {
            return false;
        }

        Optional<Draft> draft = buildDraft(def, value);
        if (draft.isEmpty()) {
            return false; // Entwurf nicht baubar (z. B. Parse-Fehler) → normale Validierung
        }

        List<BorderConflict> conflicts = BorderMath.validate(
                draft.get().settings(), Optional.of(draft.get().gameDuration()));
        if (conflicts.isEmpty()) {
            return false; // konsistent → normaler Commit
        }

        // Konflikt: Änderung NICHT speichern, sondern die Auflösung öffnen.
        // Die ursprüngliche Änderung wird als Pending-Change mitgegeben und
        // erst zusammen mit der bestätigten Lösung committet.
        new BorderConflictMenu(viewer, config, settings,
                draft.get().settings(), draft.get().gameDuration(),
                conflicts.get(0), scope,
                tx -> tx.set(def, value, scope),
                reopen, anvilInput).open();
        return true;
    }

    private boolean isBorderRelevant(SettingDefinition<?> def) {
        String key = def.key();
        return key.equals(settings.borderPhases.key())
                || key.equals(settings.borderInitialSize.key())
                || key.equals(settings.borderMinimumSize.key())
                || key.equals(settings.borderMaxEdgeSpeed.key())
                || key.equals(settings.gameDuration.key());
    }

    private record Draft(BorderSettings settings, Duration gameDuration) {
    }

    /** Baut die künftigen BorderSettings mit dem geänderten Wert überlagert. */
    private Optional<Draft> buildDraft(SettingDefinition<?> def, Object value) {
        try {
            Duration gameDuration = def.key().equals(settings.gameDuration.key())
                    ? (Duration) value : config.get(settings.gameDuration);
            double initial = def.key().equals(settings.borderInitialSize.key())
                    ? ((Number) value).doubleValue() : config.get(settings.borderInitialSize);
            double minimum = def.key().equals(settings.borderMinimumSize.key())
                    ? ((Number) value).doubleValue() : config.get(settings.borderMinimumSize);
            double maxSpeed = def.key().equals(settings.borderMaxEdgeSpeed.key())
                    ? ((Number) value).doubleValue() : config.get(settings.borderMaxEdgeSpeed);
            @SuppressWarnings("unchecked")
            List<String> phaseLines = def.key().equals(settings.borderPhases.key())
                    ? (List<String>) value : config.get(settings.borderPhases);

            return Optional.of(new Draft(
                    new BorderSettings(initial, minimum, maxSpeed,
                            BorderPhaseParser.parseAll(phaseLines, gameDuration)),
                    gameDuration));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
