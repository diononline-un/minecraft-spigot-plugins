package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SignRoleParserTest {

    private static final CategoryTable CATEGORIES = CategoryTable.load(List.of(
            new CategoryTable.Definition("ores", "Erze", "minecraft:iron_ore", List.of("minecraft:iron_ore")),
            new CategoryTable.Definition("blocks", "Blöcke", "minecraft:stone", List.of("minecraft:stone")),
            new CategoryTable.Definition("misc", "Sonstiges", "minecraft:chest", List.of("minecraft:stick"))))
            .table();

    private final SignRoleParser parser = new SignRoleParser(CATEGORIES);

    private RoleKind kindOf(String header) {
        Optional<SignRoleParser.Interpretation> parsed = parser.parse(header);
        assertTrue(parsed.isPresent(), "keine Rolle erkannt für: " + header);
        return assertInstanceOf(SignRoleParser.Interpretation.Assignment.class, parsed.get()).kind();
    }

    @Test
    void erkenntDieAnkerrolle() {
        assertEquals(RoleKind.ANCHOR, kindOf("[INPUT]"));
    }

    @Test
    void erkenntDieAuffangrolleInAllenSchreibweisen() {
        assertEquals(RoleKind.FALLBACK, kindOf("[UNSORTABLE]"));
        assertEquals(RoleKind.FALLBACK, kindOf("[unsortet]"));
        assertEquals(RoleKind.FALLBACK, kindOf("[Sonstige]"));
    }

    @Test
    void erkenntDieIgnorierrolle() {
        assertEquals(RoleKind.IGNORED, kindOf("[IGNORE]"));
    }

    @Test
    void erkenntKlammernFreiUndInBeliebigerSchreibweise() {
        assertEquals(RoleKind.CATEGORY, kindOf("ORES"));
        assertEquals(RoleKind.CATEGORY, kindOf("ores"));
        assertEquals(RoleKind.CATEGORY, kindOf("  [ OrEs ]  "));
        assertEquals(RoleKind.CATEGORY, kindOf("[ORES"));
        assertEquals(RoleKind.CATEGORY, kindOf("ORES]"));
    }

    @Test
    void liefertDenKategorieschluesselInKleinschreibung() {
        Optional<SignRoleParser.Interpretation> parsed = parser.parse("[BLOCKS]");

        SignRoleParser.Interpretation.Assignment assignment = assertInstanceOf(
                SignRoleParser.Interpretation.Assignment.class, parsed.orElseThrow());
        assertEquals(RoleKind.CATEGORY, assignment.kind());
        assertEquals("blocks", assignment.categoryKey());
    }

    @Test
    void unbekannteKategorieWirdAlsSolcheGemeldet() {
        Optional<SignRoleParser.Interpretation> parsed = parser.parse("[NETHER]");

        SignRoleParser.Interpretation.UnknownCategory unknown = assertInstanceOf(
                SignRoleParser.Interpretation.UnknownCategory.class, parsed.orElseThrow());
        assertEquals("[NETHER]", unknown.header());
    }

    @Test
    void miscGiltAlsKategorieWennDefiniert() {
        assertEquals(RoleKind.CATEGORY, kindOf("[MISC]"));
    }

    @Test
    void leereUndFehlendeSchilderErgebenKeineRolle() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("[]").isEmpty());
        assertTrue(parser.parse("[ ]").isEmpty());
    }

    @Test
    void freierTextOhneKategorieGiltAlsUnbekannt() {
        Optional<SignRoleParser.Interpretation> parsed = parser.parse("Willkommen");

        assertInstanceOf(SignRoleParser.Interpretation.UnknownCategory.class, parsed.orElseThrow());
    }

    @Test
    void ankerrolleSchlaegtKategorieVor() {
        // "input" ist kein Kategoriename, muss also als Anker gelesen werden.
        assertEquals(RoleKind.ANCHOR, kindOf("input"));
    }

    @Test
    void liestDenTextAusEinerJsonKomponente() {
        // Schilder, die per Befehl gesetzt wurden, tragen ihren Text als JSON.
        assertEquals(RoleKind.ANCHOR, kindOf("{\"text\":\"[INPUT]\"}"));
        assertEquals(RoleKind.CATEGORY, kindOf("{\"text\": \"[ORES]\"}"));
        assertEquals(RoleKind.FALLBACK, kindOf("{\"text\":\"[UNSORTABLE]\"}"));
    }

    @Test
    void unbekannteKategorieInJsonFormWirdGemeldet() {
        Optional<SignRoleParser.Interpretation> parsed = parser.parse("{\"text\":\"[NETHER]\"}");

        assertInstanceOf(SignRoleParser.Interpretation.UnknownCategory.class, parsed.orElseThrow());
    }

    @Test
    void leereJsonKomponenteErgibtKeineRolle() {
        assertTrue(parser.parse("{\"text\":\"\"}").isEmpty());
    }

    @Test
    void gewoehnlicherJsonTextOhneRolleGiltAlsUnbekannt() {
        Optional<SignRoleParser.Interpretation> parsed =
                parser.parse("{\"text\":\"Willkommen\"}");

        assertInstanceOf(SignRoleParser.Interpretation.UnknownCategory.class, parsed.orElseThrow());
    }
}
