package com.hungergames.platform.config;

import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Katalog validiert alle Defaults bereits bei der Registrierung —
 * dieser Test stellt sicher, dass das für den kompletten Katalog gilt und
 * jede GUI-Kategorie tatsächlich Einstellungen enthält.
 */
class HgSettingsTest {

    @Test
    void alleDefaultsSindGueltigUndKeysEindeutig() {
        // Der SettingDefinition-Konstruktor validiert jeden Default, die
        // Registry lehnt doppelte Keys ab — die Instanziierung selbst ist der Test.
        HgSettings settings = new HgSettings();

        Set<String> keys = new HashSet<>();
        for (SettingDefinition<?> def : settings.registry.all()) {
            assertTrue(keys.add(def.key()), "Doppelter Key: " + def.key());
        }
        assertTrue(keys.size() > 100, "Katalog unerwartet klein: " + keys.size());
    }

    @Test
    void jedeKategorieHatEinstellungen() {
        HgSettings settings = new HgSettings();
        for (SettingCategory category : SettingCategory.values()) {
            assertFalse(settings.registry.byCategory(category).isEmpty(),
                    "Kategorie ohne Einstellungen: " + category);
        }
    }

    @Test
    void neueKategorienExistieren() {
        assertEquals(18, SettingCategory.values().length);
        assertTrue(Set.of(SettingCategory.values()).containsAll(Set.of(
                SettingCategory.EVENTS, SettingCategory.SOUNDS, SettingCategory.EFFECTS,
                SettingCategory.SPONSORS, SettingCategory.DEATHMATCH, SettingCategory.GAMEMASTER,
                SettingCategory.ANNOUNCEMENTS, SettingCategory.API, SettingCategory.RESOURCEPACK)));
    }
}
