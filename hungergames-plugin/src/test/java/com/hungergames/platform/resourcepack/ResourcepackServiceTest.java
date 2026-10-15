package com.hungergames.platform.resourcepack;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Zustellung des Resourcepacks: nur bei gültiger Konfiguration, sonst No-Op. */
class ResourcepackServiceTest {

    private HgSettings settings;
    private ConfigurationService config;
    private ResourcepackService service;
    private Player player;
    private Map<String, String> stored;

    @BeforeEach
    void setUp() {
        settings = new HgSettings();
        stored = new HashMap<>();
        config = new ConfigurationService(settings.registry, new ConfigurationService.Backend() {
            @Override
            public Map<String, String> load() {
                return stored;
            }

            @Override
            public void save(Map<String, String> serializedValues) {
                stored.clear();
                stored.putAll(serializedValues);
            }
        }, entry -> {
        }, System::currentTimeMillis);

        service = new ResourcepackService(config, settings, Logger.getAnonymousLogger());
        player = mock(Player.class);
    }

    @Test
    @DisplayName("Mit gültiger Default-Konfiguration wird das Pack gesendet")
    void sendetPackMitGueltigerKonfiguration() {
        service.send(player);

        verify(player).addResourcePack(any(), eq(config.get(settings.resourcepackUrl)),
                any(byte[].class), eq(config.get(settings.resourcepackPrompt)), eq(true));
    }

    @Test
    @DisplayName("resourcepack.enabled=false verschickt nichts")
    void keinVersandWennDeaktiviert() {
        config.begin("Test")
                .set(settings.resourcepackEnabled, false, ConfigurationService.Scope.PERSISTENT)
                .commit();

        service.send(player);

        verify(player, never()).addResourcePack(any(), any(), any(byte[].class), any(String.class), anyBoolean());
    }

    @Test
    @DisplayName("Leere URL verschickt nichts")
    void keinVersandOhneUrl() {
        config.begin("Test")
                .set(settings.resourcepackUrl, "", ConfigurationService.Scope.PERSISTENT)
                .commit();

        service.send(player);

        verify(player, never()).addResourcePack(any(), any(), any(byte[].class), any(String.class), anyBoolean());
    }

    @Test
    @DisplayName("decodeSha1 akzeptiert nur einen 40-stelligen Hex-Hash")
    void decodeSha1PrueftFormat() {
        byte[] decoded = ResourcepackService.decodeSha1(config.get(settings.resourcepackSha1));
        org.junit.jupiter.api.Assertions.assertNotNull(decoded);
        org.junit.jupiter.api.Assertions.assertEquals(20, decoded.length);

        org.junit.jupiter.api.Assertions.assertNull(ResourcepackService.decodeSha1("keinvalidersha1hash"));
        org.junit.jupiter.api.Assertions.assertNull(ResourcepackService.decodeSha1(""));
        org.junit.jupiter.api.Assertions.assertNull(ResourcepackService.decodeSha1(null));
    }
}
