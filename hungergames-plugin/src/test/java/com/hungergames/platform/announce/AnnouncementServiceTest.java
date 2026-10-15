package com.hungergames.platform.announce;

import com.hungergames.platform.sound.SoundService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementServiceTest {

    @Test
    void ersetztPlatzhalter() {
        assertEquals("Kill: Katniss durch Cato (3 übrig)",
                AnnouncementService.format("Kill: {victim} durch {killer} ({alive} übrig)",
                        Map.of("victim", "Katniss", "killer", "Cato", "alive", "3")));
    }

    @Test
    void unbekanntePlatzhalterBleibenStehen() {
        assertEquals("Hallo {unbekannt}",
                AnnouncementService.format("Hallo {unbekannt}", Map.of("x", "y")));
    }

    @Test
    void soundKeyValidierung() {
        assertTrue(SoundService.validateSoundKey("ENTITY_WITHER_SPAWN").isEmpty());
        assertTrue(SoundService.validateSoundKey("custom.halt").isEmpty());
        assertTrue(SoundService.validateSoundKey("meinpack:event.start").isEmpty());
        // Leer = bewusst kein Sound (Stille) — erlaubt.
        assertTrue(SoundService.validateSoundKey("").isEmpty());
        assertTrue(SoundService.validateSoundKey("Kein gültiger Key!").isPresent());
    }

    @Test
    void mehrereSoundsMitFeintuning() {
        // Liste mehrerer Sounds, teils mit @lautstärke~tonhöhe.
        assertTrue(SoundService.validateSoundKey(
                "ENTITY_WITHER_HURT; ENTITY_GENERIC_EXPLODE@1.5~0.7").isEmpty());
        assertTrue(SoundService.validateSoundKey("custom.halt, ITEM_TOTEM_USE").isEmpty());
        // Ein ungültiger Eintrag in der Liste macht den ganzen Wert ungültig.
        assertTrue(SoundService.validateSoundKey("ITEM_TOTEM_USE; Kein Key!").isPresent());
    }
}
