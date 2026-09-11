package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import me.diono.chestmanager.core.fake.FakeWorld;
import me.diono.chestmanager.core.fake.RecordingLog;

class AreaScannerTest {

    private static final CategoryTable CATEGORIES = CategoryTable.load(List.of(
            new CategoryTable.Definition("ores", "Erze", "minecraft:iron_ore", List.of("minecraft:iron_ore")),
            new CategoryTable.Definition("blocks", "Blöcke", "minecraft:stone", List.of("minecraft:stone")),
            new CategoryTable.Definition("food", "Essen", "minecraft:bread", List.of("minecraft:bread"))))
            .table();

    private final RecordingLog log = new RecordingLog();

    private AreaScanner scannerFor(FakeWorld world) {
        return new AreaScanner(world, CATEGORIES, log);
    }

    /**
     * Stellt eine markierte Truhe her. Das Schild sitzt auf dem Block über der
     * Truhe, so wie im Spiel; ein Schild kann nicht im Truhenblock selbst stehen.
     */
    private static FakeWorld marked(FakeWorld world, BlockPos pos, String header) {
        return world.chest(pos).sign(pos.offset(0, 1, 0), header);
    }

    private static SorterSettings settings(int radius, int linkDistance, int maxChests, int perTick) {
        return settings(radius, linkDistance, maxChests, perTick, 0);
    }

    private static SorterSettings settings(int radius, int linkDistance, int maxChests,
                                           int perTick, int height) {
        SorterSettings defaults = SorterSettings.defaults();
        return new SorterSettings(radius, maxChests, height, linkDistance,
                perTick, defaults.reach(), defaults.navTimeoutMillis(), defaults.maxNavAttempts(),
                defaults.retryBackoffMillis(), defaults.blockDurationMillis(), defaults.carryLimit(),
                defaults.sortIntervalMillis(), defaults.rescanIntervalMillis(), defaults.opsPerTick(),
                defaults.maxChunkTickets(), false);
    }

    private AreaScanner.Session runToCompletion(AreaScanner.Session session) {
        int guard = 0;
        while (!session.step()) {
            if (++guard > 100_000) {
                throw new IllegalStateException("Scan endet nicht");
            }
        }
        return session;
    }

    @Test
    void findetAnkerUndKategorieTruhenImVerbund() {
        FakeWorld world = marked(marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[ORES]"),
                new BlockPos(2, 64, 0), "[BLOCKS]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024));

        assertTrue(area.hasAnchor());
        assertEquals(new BlockPos(0, 64, 0), area.anchor());
        assertEquals(3, area.size());
        assertEquals(List.of(new BlockPos(1, 64, 0)),
                area.targets("ores").stream().map(ChestRef::pos).toList());
        assertEquals(List.of(new BlockPos(2, 64, 0)),
                area.targets("blocks").stream().map(ChestRef::pos).toList());
    }

    @Test
    void getrennteTruheninselBleibtAusserhalb() {
        FakeWorld world = marked(marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[ORES]"),
                new BlockPos(4, 64, 0), "[BLOCKS]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(10, 0, 64, 4096));

        assertEquals(2, area.size());
        assertEquals(0, area.targets("blocks").size());
    }

    @Test
    void linkDistanceUeberbruecktEineLuecke() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(2, 64, 0), "[BLOCKS]");

        ChestArea ohne = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(10, 0, 64, 4096));
        assertEquals(1, ohne.size());

        ChestArea mit = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(10, 1, 64, 4096));
        assertEquals(2, mit.size());
        assertEquals(1, mit.targets("blocks").size());
    }

    @Test
    void schildAmNachbarblockWirdErkannt() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[ORES]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024));

        assertEquals(2, area.size());
        assertEquals(1, area.targets("ores").size());
    }

    @Test
    void truheOhneSchildGehoertZumBereichAberOhneRolle() {
        FakeWorld world = marked(new FakeWorld()
                .chest(new BlockPos(1, 64, 0)), new BlockPos(0, 64, 0), "[INPUT]");
        marked(world, new BlockPos(2, 64, 0), "[ORES]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024));

        assertEquals(2, area.size(), "unmarkierte Truhe zählt nicht als markierte Truhe");
        assertEquals(List.of(new BlockPos(1, 64, 0)), area.unmarked());
        assertEquals(1, area.targets("ores").size());
    }

    @Test
    void ignorierteTruheIstImBereichAberWederQuelleNochZiel() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[IGNORE]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024));

        assertEquals(2, area.size());
        assertEquals(1, area.sources().size(), "die ignorierte Truhe ist keine Quelle");
        assertEquals(RoleKind.IGNORED, area.at(new BlockPos(1, 64, 0)).orElseThrow().kind());
    }

    @Test
    void auffangtruheWirdGefunden() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[UNSORTABLE]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024));

        assertEquals(new BlockPos(1, 64, 0), area.fallback().orElseThrow().pos());
    }

    @Test
    void unbekannteKategorieGiltAlsNichtMarkiertUndWirdGemeldet() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[NETHER]");

        AreaScanner.Session session = runToCompletion(
                scannerFor(world).start(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024)));

        assertEquals(1, session.area().size());
        assertEquals(List.of(new BlockPos(1, 64, 0)), session.area().unmarked());
        assertTrue(session.warnings().stream().anyMatch(w -> w.contains("NETHER")),
                "Warnung nennt den unbekannten Namen: " + session.warnings());
    }

    @Test
    void fehlenderAnkerWirdGemeldet() {
        FakeWorld world = marked(new FakeWorld(), new BlockPos(0, 64, 0), "[ORES]");

        AreaScanner.Session session = runToCompletion(
                scannerFor(world).start(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024)));

        assertFalse(session.area().hasAnchor());
        assertTrue(session.warnings().stream().anyMatch(w -> w.contains("[INPUT]")),
                "Warnung nennt das erwartete Schild: " + session.warnings());
    }

    @Test
    void mehrereAnkerWerdenGemeldetUndDerErsteGewinnt() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[INPUT]");

        AreaScanner.Session session = runToCompletion(
                scannerFor(world).start(new BlockPos(0, 64, 0), settings(4, 0, 64, 1024)));

        assertTrue(session.warnings().stream().anyMatch(w -> w.contains("Ankertruhen")),
                "Warnung nennt die Mehrfachbelegung: " + session.warnings());
    }

    @Test
    void hoechstzahlTruhenBrichtDieErmittlungAbUndMeldet() {
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < 5; i++) {
            marked(world, new BlockPos(i, 64, 0), i == 0 ? "[INPUT]" : "[ORES]");
        }

        AreaScanner.Session session = runToCompletion(
                scannerFor(world).start(new BlockPos(0, 64, 0), settings(4, 0, 3, 1024)));

        assertEquals(3, session.area().size());
        assertTrue(session.warnings().stream().anyMatch(w -> w.contains("Höchstzahl")),
                "Warnung nennt die Grenze: " + session.warnings());
    }

    @Test
    void hoehenunterschiedBegrenztDenSuchraum() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(0, 40, 0), "[ORES]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 4096, 2));

        assertEquals(1, area.size(), "die Truhe ausserhalb des Höhenfensters wird nicht erfasst");
    }

    @Test
    void scanBleibtProAufrufImBlockbudget() {
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < 3; i++) {
            marked(world, new BlockPos(i, 64, 0), i == 0 ? "[INPUT]" : "[ORES]");
        }

        AreaScanner.Session session = scannerFor(world).start(new BlockPos(0, 64, 0), settings(2, 0, 64, 5));
        world.resetProbes();

        int rounds = 0;
        while (!session.step()) {
            int probes = world.probes();
            assertTrue(probes <= 5, "Aufruf " + rounds + " hat " + probes + " Blöcke abgefragt");
            world.resetProbes();
            rounds++;
            assertTrue(rounds < 100, "Scan endet nicht");
        }
        assertTrue(rounds >= 4, "25 Blöcke bei 5 pro Aufruf brauchen mindestens vier Aufrufe, waren " + rounds);
    }

    @Test
    void fortschrittSteigtBisHundert() {
        FakeWorld world = marked(new FakeWorld(), new BlockPos(0, 64, 0), "[INPUT]");

        AreaScanner.Session session = scannerFor(world).start(new BlockPos(0, 64, 0), settings(2, 0, 64, 10));

        assertEquals(0, session.progressPercent());
        session.step();
        assertTrue(session.progressPercent() > 0, "nach dem ersten Aufruf muss Fortschritt sichtbar sein");
        runToCompletion(session);
        assertEquals(100, session.progressPercent());
    }

    @Test
    void abgebauteTruheFaelltBeimNaechstenScanAusDemBereich() {
        FakeWorld world = marked(marked(new FakeWorld(),
                new BlockPos(0, 64, 0), "[INPUT]"),
                new BlockPos(1, 64, 0), "[ORES]");

        ChestArea first = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 4096));
        assertEquals(2, first.size());

        FakeWorld reduced = marked(new FakeWorld(), new BlockPos(0, 64, 0), "[INPUT]");
        ChestArea second = scannerFor(reduced).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 4096));

        assertEquals(1, second.size());
        assertEquals(Optional.empty(), second.at(new BlockPos(1, 64, 0)));
    }

    @Test
    void nichtGeladeneChunksWerdenUebersprungenUndGemeldet() {
        FakeWorld world = marked(new FakeWorld(), new BlockPos(0, 64, 0), "[INPUT]")
                .unloaded(new BlockPos(1, 64, 0), new BlockPos(2, 64, 0));

        AreaScanner.Session session = runToCompletion(
                scannerFor(world).start(new BlockPos(0, 64, 0), settings(2, 0, 64, 4096)));

        assertTrue(session.warnings().stream().anyMatch(w -> w.contains("nicht geladenen Chunks")),
                "Warnung nennt übersprungene Blöcke: " + session.warnings());
    }

    @Test
    void nieGescannterBereichLiefertLeereFlaeche() {
        FakeWorld world = new FakeWorld();

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(2, 0, 64, 4096));

        assertTrue(area.isEmpty());
        assertFalse(area.hasAnchor());
    }

    @Test
    void countByCategoryZaehltZieltruhen() {
        FakeWorld world = new FakeWorld();
        marked(world, new BlockPos(0, 64, 0), "[INPUT]");
        marked(world, new BlockPos(1, 64, 0), "[ORES]");
        marked(world, new BlockPos(2, 64, 0), "[ORES]");
        marked(world, new BlockPos(3, 64, 0), "[FOOD]");

        ChestArea area = scannerFor(world).scanNow(new BlockPos(0, 64, 0), settings(4, 0, 64, 4096));

        assertEquals(2, area.countByCategory().get("ores"));
        assertEquals(1, area.countByCategory().get("food"));
    }
}
