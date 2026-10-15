package com.hungergames.platform.sponsor;

import com.hungergames.platform.sponsor.ShopItemParser.CustomItemReward;
import com.hungergames.platform.sponsor.ShopItemParser.EffectReward;
import com.hungergames.platform.sponsor.ShopItemParser.MaterialReward;
import com.hungergames.platform.sponsor.ShopItemParser.ShopItem;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopItemParserTest {

    @Test
    void parstMaterialReward() {
        ShopItem item = ShopItemParser.parse("bread_pack|BREAD:8|1|Essen Paket");
        assertEquals("bread_pack", item.id());
        assertEquals(1, item.cost());
        assertEquals("Essen Paket", item.displayName());
        MaterialReward reward = assertInstanceOf(MaterialReward.class, item.reward());
        assertEquals(Material.BREAD, reward.material());
        assertEquals(8, reward.amount());
    }

    @Test
    void parstEffectReward() {
        ShopItem item = ShopItemParser.parse("speed_15s|EFFECT:SPEED:15:0|3|Speed 15 Sekunden");
        EffectReward reward = assertInstanceOf(EffectReward.class, item.reward());
        assertEquals("SPEED", reward.effectName());
        assertEquals(15, reward.durationSeconds());
        assertEquals(0, reward.amplifier());
    }

    @Test
    void parstCustomItemReward() {
        ShopItem item = ShopItemParser.parse("ff|ITEM:FIENDFINDER:1|4|Fiendfinder");
        CustomItemReward reward = assertInstanceOf(CustomItemReward.class, item.reward());
        assertEquals("FIENDFINDER", reward.customId());
        assertEquals(1, reward.amount());
    }

    @Test
    void lehntUngueltigeEintraegeAb() {
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("zu|wenig|felder"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|KEIN_MATERIAL:1|1|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|BREAD:0|1|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|BREAD:1|0|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|BREAD:1|eins|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|ITEM:UNBEKANNT:1|1|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("x|EFFECT:SPEED:15|1|X"));
        assertThrows(IllegalArgumentException.class, () -> ShopItemParser.parse("|BREAD:1|1|X"));
    }

    @Test
    void validateListMeldetDuplikateUndFehler() {
        assertTrue(ShopItemParser.validateList(List.of(
                "a|BREAD:1|1|Brot", "b|ARROW:16|1|Pfeile")).isEmpty());
        assertTrue(ShopItemParser.validateList(List.of(
                "a|BREAD:1|1|Brot", "a|ARROW:16|1|Pfeile")).isPresent());
        assertTrue(ShopItemParser.validateList(List.of("kaputt")).isPresent());
    }
}
