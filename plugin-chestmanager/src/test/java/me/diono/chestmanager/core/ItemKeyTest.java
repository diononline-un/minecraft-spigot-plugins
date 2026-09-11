package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemKeyTest {

    @Test
    void ohneVarianteWirdDieEinfacheVarianteGesetzt() {
        assertEquals(ItemKey.PLAIN, ItemKey.of("minecraft:stone").variantKey());
    }

    @Test
    void leererVariantenSchluesselWirdAlsEinfacheVarianteBehandelt() {
        assertEquals(ItemKey.PLAIN, new ItemKey("minecraft:stone", "").variantKey());
        assertEquals(ItemKey.of("minecraft:stone"), new ItemKey("minecraft:stone", null));
    }

    @Test
    void gleicherTypUndGleicheVarianteSindGleich() {
        assertEquals(ItemKey.of("minecraft:iron_ore", "ench"),
                ItemKey.of("minecraft:iron_ore", "ench"));
    }

    @Test
    void gleicherTypMitAndererVarianteIstUngleich() {
        assertNotEquals(ItemKey.of("minecraft:diamond_pickaxe", "eff5"),
                ItemKey.of("minecraft:diamond_pickaxe", "plain"));
    }

    @Test
    void sameTypeIgnoriertDieVariante() {
        assertTrue(ItemKey.of("minecraft:diamond_pickaxe", "eff5")
                .sameType(ItemKey.of("minecraft:diamond_pickaxe", "fortune3")));
    }

    @Test
    void sameTypeIstFalschBeiAnderemTyp() {
        assertFalse(ItemKey.of("minecraft:iron_ore").sameType(ItemKey.of("minecraft:gold_ore")));
    }

    @Test
    void sameTypeVertraegtNull() {
        assertFalse(ItemKey.of("minecraft:iron_ore").sameType(null));
    }

    @Test
    void fehlenderTypIstEinFehler() {
        assertThrows(NullPointerException.class, () -> new ItemKey(null, null));
    }

    @Test
    void toStringZeigtDieVarianteNurWennVorhanden() {
        assertEquals("minecraft:stone", ItemKey.of("minecraft:stone").toString());
        assertEquals("minecraft:stone{silk}", ItemKey.of("minecraft:stone", "silk").toString());
    }
}
