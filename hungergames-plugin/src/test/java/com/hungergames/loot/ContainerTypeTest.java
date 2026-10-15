package com.hungergames.loot;

import com.hungergames.loot.LootConfig.ContainerType;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sichert die Material-Zuordnung der Loot-Container ab — insbesondere die
 * Regale, die seit Minecraft 26.2 als eigene Blockart existieren.
 */
class ContainerTypeTest {

    @Test
    @DisplayName("Holzregale aller Varianten zählen als SHELF")
    void shelvesAreRecognized() {
        for (Material material : new Material[]{Material.OAK_SHELF, Material.SPRUCE_SHELF,
                Material.BAMBOO_SHELF, Material.WARPED_SHELF, Material.PALE_OAK_SHELF}) {
            assertEquals(ContainerType.SHELF, ContainerType.fromMaterial(material),
                    material + " sollte als Regal erkannt werden");
        }
    }

    @Test
    @DisplayName("Chiseled Bookshelf zählt als SHELF, normales Bücherregal nicht")
    void bookshelves() {
        assertEquals(ContainerType.SHELF, ContainerType.fromMaterial(Material.CHISELED_BOOKSHELF));
        assertEquals(ContainerType.SHELF, ContainerType.fromMaterial(Material.BOOKSHELF),
                "BOOKSHELF matcht den Namen — hat aber kein Inventar und wird beim Scan "
                        + "gar nicht erst erreicht");
    }

    @Test
    @DisplayName("Truhen, Kupfertruhen, Fallentruhen und Fässer bleiben getrennt")
    void chestsAndBarrels() {
        assertEquals(ContainerType.CHEST, ContainerType.fromMaterial(Material.CHEST));
        assertEquals(ContainerType.TRAPPED_CHEST, ContainerType.fromMaterial(Material.TRAPPED_CHEST));
        assertEquals(ContainerType.COPPER_CHEST, ContainerType.fromMaterial(Material.COPPER_CHEST));
        assertEquals(ContainerType.COPPER_CHEST,
                ContainerType.fromMaterial(Material.WAXED_OXIDIZED_COPPER_CHEST));
        assertEquals(ContainerType.BARREL, ContainerType.fromMaterial(Material.BARREL));
    }

    @Test
    @DisplayName("Blöcke ohne Loot-Tabelle liefern null")
    void unknownMaterials() {
        assertNull(ContainerType.fromMaterial(Material.STONE));
        assertNull(ContainerType.fromMaterial(Material.FURNACE));
        assertNull(ContainerType.fromMaterial(Material.DECORATED_POT));
    }
}
