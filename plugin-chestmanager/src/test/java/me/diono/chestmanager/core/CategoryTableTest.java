package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CategoryTableTest {

    private static CategoryTable.Definition def(String key, String... items) {
        return new CategoryTable.Definition(key, key, "minecraft:chest", List.of(items));
    }

    @Test
    void ordnetItemSeinerKategorieZu() {
        CategoryTable table = CategoryTable.load(List.of(
                def("ores", "minecraft:iron_ore"),
                def("food", "minecraft:bread"))).table();

        assertEquals(Optional.of("ores"), table.categoryOf(ItemKey.of("minecraft:iron_ore")));
        assertEquals(Optional.of("food"), table.categoryOf(ItemKey.of("minecraft:bread")));
    }

    @Test
    void unbekanntesItemHatKeineKategorie() {
        CategoryTable table = CategoryTable.load(List.of(def("ores", "minecraft:iron_ore"))).table();

        assertEquals(Optional.empty(), table.categoryOf(ItemKey.of("minecraft:dirt")));
    }

    @Test
    void varianteAendertDieKategorieNicht() {
        CategoryTable table = CategoryTable.load(List.of(def("tools", "minecraft:diamond_pickaxe"))).table();

        assertEquals(Optional.of("tools"),
                table.categoryOf(ItemKey.of("minecraft:diamond_pickaxe", "eff5")));
    }

    @Test
    void doppeltGenanntesItemNimmtDieErsteKategorieUndMeldetDenKonflikt() {
        CategoryTable.LoadResult result = CategoryTable.load(List.of(
                def("ores", "minecraft:quartz"),
                def("blocks", "minecraft:quartz")));

        assertEquals(Optional.of("ores"), result.table().categoryOf(ItemKey.of("minecraft:quartz")));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("ores"), result.warnings().get(0));
        assertTrue(result.warnings().get(0).contains("blocks"), result.warnings().get(0));
    }

    @Test
    void mehrfachDefinierteKategorieWirdGemeldetUndEinmalUebernommen() {
        CategoryTable.LoadResult result = CategoryTable.load(List.of(
                def("ores", "minecraft:iron_ore"),
                def("ores", "minecraft:gold_ore")));

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("mehrfach definiert")));
        assertEquals(Optional.of("ores"), result.table().categoryOf(ItemKey.of("minecraft:iron_ore")));
        assertEquals(Optional.empty(), result.table().categoryOf(ItemKey.of("minecraft:gold_ore")));
    }

    @Test
    void kenntNurDefinierteKategorien() {
        CategoryTable table = CategoryTable.load(List.of(def("ores"))).table();

        assertTrue(table.hasCategory("ores"));
        assertFalse(table.hasCategory("blocks"));
        assertFalse(table.hasCategory(null));
    }

    @Test
    void behaeltDieDefinitionsreihenfolge() {
        CategoryTable table = CategoryTable.load(List.of(
                def("ores"), def("wood"), def("misc"))).table();

        assertEquals(List.of("ores", "wood", "misc"), List.copyOf(table.categoryKeys()));
    }

    @Test
    void leereTabelleKenntNichts() {
        CategoryTable table = CategoryTable.empty();

        assertEquals(0, table.knownItemCount());
        assertEquals(Optional.empty(), table.categoryOf(ItemKey.of("minecraft:stone")));
    }

    @Test
    void anzeigenameUndSymbolFallenzurueck() {
        CategoryTable table = CategoryTable.load(List.of(
                new CategoryTable.Definition("ores", "Erze & Schätze", "minecraft:iron_ore",
                        List.of("minecraft:iron_ore")))).table();

        assertEquals("Erze & Schätze", table.display("ores"));
        assertEquals("minecraft:iron_ore", table.iconTypeId("ores"));
        assertEquals("unbekannt", table.display("unbekannt"));
        assertEquals("minecraft:chest", table.iconTypeId("unbekannt"));
    }
}
