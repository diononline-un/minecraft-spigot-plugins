package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.diono.chestmanager.core.SorterFixture;

class SorterTest {

    private SorterFixture f;

    @BeforeEach
    void setUp() {
        f = new SorterFixture().defineStandardChests();
    }

    /**
     * Baut den Sorter und arbeitet so lange, bis der Bereich steht und keine offenen
     * Transportvorgänge mehr im Journal liegen. Vor dem ersten Transport kehrt die
     * Methode zurück.
     */
    private Sorter started() {
        Sorter sorter = f.build();
        sorter.requestScan();
        for (int guard = 0; guard < 5000; guard++) {
            if (sorter.journal().isEmpty()) {
                SorterState current = sorter.state();
                if (current == SorterState.SEEK_SOURCE) {
                    return sorter;
                }
                if (current == SorterState.IDLE && sorter.area().hasAnchor()) {
                    return sorter;
                }
            }
            f.tickArriving(1);
        }
        throw new IllegalStateException("Sorter erreicht keinen Bereitschaftszustand: "
                + sorter.state() + ", Journal " + sorter.journal().size());
    }

    // --- Ende zu Ende -----------------------------------------------------

    @Test
    void bringtFehlplatziertesItemInDiePassendeKategorie() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 32);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(400);

        assertEquals(0, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON));
        assertEquals(32, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ORES_POS_2, SorterFixture.IRON));
        assertTrue(f.chests.isCarryEmpty(), "der Träger ist am Ende leer");
    }

    @Test
    void laesstKorrektLiegendeItemsUnangetastet() {
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.IRON, 20);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(200);

        assertEquals(20, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON));
        assertEquals(0, f.chests.depositCalls());
    }

    @Test
    void korrigiertFalschLiegendeItemsInKategorietruhen() {
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.BREAD, 8);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(400);

        assertEquals(0, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.BREAD));
        assertEquals(8, f.chests.countOf(SorterFixture.FOOD_POS, SorterFixture.BREAD));
    }

    @Test
    void bringtItemsOhneKategorieInDieAuffangtruhe() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.STICK, 12);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(400);

        assertEquals(12, f.chests.countOf(SorterFixture.FALLBACK_POS, SorterFixture.STICK));
    }

    @Test
    void sortiertMehrereQuelltruhenInEinemLauf() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 5);
        f.chests.put(SorterFixture.FOOD_POS, SorterFixture.IRON, 7);
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.BREAD, 9);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(600);

        assertEquals(12, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ORES_POS_2, SorterFixture.IRON));
        assertEquals(9, f.chests.countOf(SorterFixture.FOOD_POS, SorterFixture.BREAD));
    }

    // --- Zustandsautomat --------------------------------------------------

    @Test
    void durchlaeuftDieZustaendeInDerVorgesehenenReihenfolge() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 4);
        Sorter sorter = f.build();

        List<SorterState> seen = new java.util.ArrayList<>();
        sorter.requestScan();
        for (int i = 0; i < 300; i++) {
            SorterState before = sorter.state();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != before) {
                seen.add(before);
            }
            sorter.sortNow();
            f.tickArriving(1);
            if (sorter.state() == SorterState.IDLE && seen.contains(SorterState.DELIVER)) {
                break;
            }
        }

        assertTrue(seen.contains(SorterState.SCANNING), "Scan fehlt: " + seen);
        assertTrue(seen.contains(SorterState.SEEK_SOURCE), "Quellsuche fehlt: " + seen);
        assertTrue(seen.contains(SorterState.SEEK_TARGET), "Zielsuche fehlt: " + seen);
        assertTrue(seen.contains(SorterState.APPROACH_SOURCE), "Anlauf Quelle fehlt: " + seen);
        assertTrue(seen.contains(SorterState.PICKUP), "Entnahme fehlt: " + seen);
        assertTrue(seen.contains(SorterState.APPROACH_TARGET), "Anlauf Ziel fehlt: " + seen);
        assertTrue(seen.contains(SorterState.DELIVER), "Ablage fehlt: " + seen);

        // Die Zielsuche liegt vor der Entnahme, damit nichts entnommen wird, wofür
        // es kein Ziel gibt.
        assertTrue(seen.indexOf(SorterState.SEEK_TARGET) < seen.indexOf(SorterState.PICKUP),
                "Zielsuche muss vor der Entnahme laufen: " + seen);
    }

    @Test
    void greiftNichtZuWennDieNavigationEndetDerSorterAberWeitWegSteht() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        Sorter sorter = started();
        sorter.sortNow();

        // Navigation endet ergebnislos, die Position bleibt weit entfernt.
        f.navigation.teleport(new BlockPos(40, 64, 40));
        f.tickWithoutArrival(200);

        assertEquals(16, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "ohne Reichweite darf nichts entnommen werden");
        assertTrue(f.chests.isCarryEmpty());
    }

    @Test
    void sperrtUnereichbareTruheNachDenVersuchenUndMeldet() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        Sorter sorter = started();
        sorter.sortNow();

        f.navigation.teleport(new BlockPos(40, 64, 40));
        f.tickWithoutArrival(300);

        assertFalse(sorter.blockedChests().active(f.clock.nowMillis()).isEmpty(),
                "nach den Fehlversuchen muss eine Sperre bestehen");
        assertTrue(f.log.containsText("nicht erreichbar"), "der Betreiber wird informiert");
    }

    @Test
    void gesperrteTruheWirdUebersprungenUndDerRestWeitersortiert() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.BREAD, 8);
        Sorter sorter = started();

        sorter.blockedChests().block(SorterFixture.ANCHOR_POS, "Test", f.clock.nowMillis(), 60_000, 1);
        sorter.sortNow();
        f.tickArriving(400);

        assertEquals(8, f.chests.countOf(SorterFixture.FOOD_POS, SorterFixture.BREAD),
                "die übrigen Truhen werden weiter bearbeitet");
        assertEquals(16, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON));
    }

    @Test
    void leererBereichErzeugtKeineNavigationsauftraege() {
        Sorter sorter = started();
        int before = f.navigation.navigateCalls();

        sorter.sortNow();
        f.tickArriving(100);

        assertEquals(before, f.navigation.navigateCalls(), "nichts zu tun heisst nicht laufen");
        assertEquals(SorterState.IDLE, sorter.state());
    }

    @Test
    void anhaltenMittenInDerFahrtGibtGetrageneItemsZurueck() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        Sorter sorter = started();
        sorter.sortNow();

        // Bis zur Entnahme laufen lassen.
        int guard = 0;
        while (sorter.state() != SorterState.APPROACH_TARGET && guard++ < 400) {
            f.tickArriving(1);
        }
        assertEquals(SorterState.APPROACH_TARGET, sorter.state(), "Vorbedingung: Items sind entnommen");
        assertFalse(f.chests.isCarryEmpty(), "Vorbedingung: der Träger ist gefüllt");

        sorter.stop();
        f.tickArriving(200);

        assertTrue(f.chests.isCarryEmpty(), "der Träger ist nach dem Anhalten leer");
        assertEquals(16, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "die Items liegen wieder in der Quelltruhe");
        assertEquals(SorterState.STOPPED, sorter.state());
    }

    @Test
    void anhaltenImRuhezustandVerhindertNeueFahrten() {
        Sorter sorter = started();
        sorter.stop();
        f.tickArriving(50);

        assertEquals(SorterState.STOPPED, sorter.state());
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 8);
        sorter.sortNow();
        f.tickArriving(200);

        assertEquals(8, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "ein angehaltener Sorter sortiert nicht");
    }

    // --- Träger und Journal ----------------------------------------------

    @Test
    void tragemengeIstBegrenzt() {
        f.withSettings(settings -> settings.withCarryLimit(5));
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 64);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(800);

        List<TransportLog.Record> records = sorter.transportLog().recent(200, "minecraft:iron_ore");
        assertFalse(records.isEmpty(), "es muss Transportvorgänge geben");
        assertTrue(records.stream().allMatch(r -> r.amount() <= 5),
                "keine Fahrt darf mehr als die Tragemenge bewegen");
        assertEquals(64, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ORES_POS_2, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "kein Item geht verloren");
    }

    @Test
    void journalIstAmEndeEinesVorgangsLeer() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(400);

        assertTrue(sorter.journal().isEmpty(), "erledigte Vorgänge bleiben nicht im Journal");
        assertEquals(0, f.journalPort.size());
    }

    @Test
    void transportprotokollHaeltVorgaengeFestUndLaesstSichFiltern() {
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.BREAD, 8);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(600);

        List<TransportLog.Record> all = sorter.transportLog().recent(50);
        assertFalse(all.isEmpty(), "es muss Vorgänge im Protokoll geben");
        assertTrue(all.stream().allMatch(r -> r.outcome() == TransportLog.Outcome.DELIVERED));

        List<TransportLog.Record> onlyIron = sorter.transportLog().recent(50, "minecraft:iron_ore");
        assertFalse(onlyIron.isEmpty());
        assertTrue(onlyIron.stream().allMatch(r -> r.key().equals(SorterFixture.IRON)));

        TransportLog.Record first = onlyIron.get(0);
        assertNotNull(first.source());
        assertNotNull(first.target());
        assertEquals(16, first.amount());
    }

    @Test
    void entnimmtNichtsWennKeineZieltruheExistiert() {
        // Für "minecraft:diamond_pickaxe" gibt es eine Kategorie, aber keine Truhe.
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.PICKAXE, 4);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(300);

        assertEquals(4, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.PICKAXE),
                "ohne Zieltruhe wird nichts entnommen");
        assertTrue(f.chests.isCarryEmpty());
        assertTrue(f.log.containsText("keine Zieltruhe"), "der Betreiber wird informiert");
    }

    @Test
    void volleKategorieLaesstItemsLiegenUndMeldet() {
        f.chests.remove(SorterFixture.ORES_POS).define(SorterFixture.ORES_POS, 1);
        f.chests.remove(SorterFixture.ORES_POS_2).define(SorterFixture.ORES_POS_2, 1);
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.IRON, 64);
        f.chests.put(SorterFixture.ORES_POS_2, SorterFixture.IRON, 64);
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.GOLD, 10);
        Sorter sorter = started();

        sorter.sortNow();
        f.tickArriving(300);

        assertEquals(10, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.GOLD),
                "keine Ausweichablage in eine andere Kategorie");
        assertTrue(f.log.containsText("voll"), "der Betreiber wird auf die volle Kategorie hingewiesen");
    }

    // --- Wiederherstellung ------------------------------------------------

    @Test
    void stelleNachAbsturzGetrageneItemsInDieQuelltruheZurueck() {
        // Ein Vorgang, der beim Absturz mitten im Transport war.
        f.chests.carry(SorterFixture.IRON, 16);
        f.journalPort.write(JournalEntry.pending("alt", SorterFixture.IRON, 16,
                        SorterFixture.ANCHOR_POS, SorterFixture.ORES_POS, 500, 0)
                .withCarried(f.chests.carriedPayload(), 16, 600));

        Sorter sorter = started();

        assertEquals(16, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "die Items sind zurück in der Quelltruhe");
        assertTrue(f.chests.isCarryEmpty());
        assertEquals(0, f.journalPort.size(), "der Vorgang ist abgeschlossen");
        assertNotNull(sorter);
    }

    @Test
    void verwirftAusstehendeEntnahmenOhneInventarzugriff() {
        // PENDING heisst: Die Entnahme hat nie stattgefunden, die Truhe ist unberührt.
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);
        f.journalPort.write(JournalEntry.pending("halb", SorterFixture.IRON, 16,
                SorterFixture.ANCHOR_POS, SorterFixture.ORES_POS, 500, 0));

        Sorter sorter = started();
        f.tickArriving(300);

        assertEquals(0, f.journalPort.size(), "der ausstehende Eintrag wird verworfen");
        assertEquals(16, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON)
                + f.chests.countOf(SorterFixture.ORES_POS_2, SorterFixture.IRON),
                "kein Item geht verloren oder wird verdoppelt");
        assertNotNull(sorter);
    }

    @Test
    void schliesstEinenVorgangAbDerBereitsAbgelegtWurde() {
        // targetBefore war 0, in der Zieltruhe liegen jetzt die 16 Items. Der Träger
        // ist nach dem Absturz leer: Die Items sind bereits angekommen.
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.IRON, 16);
        f.journalPort.write(JournalEntry.pending("fast", SorterFixture.IRON, 16,
                        SorterFixture.ANCHOR_POS, SorterFixture.ORES_POS, 500, 0)
                .withCarried("minecraft:iron_ore\u0001-\u000116", 16, 600));

        Sorter sorter = started();

        assertEquals(16, f.chests.countOf(SorterFixture.ORES_POS, SorterFixture.IRON),
                "es wird nichts doppelt abgelegt");
        assertEquals(0, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON));
        assertTrue(f.chests.isCarryEmpty());
        assertEquals(0, f.journalPort.size());
        assertNotNull(sorter);
    }

    @Test
    void fuehrtItemsInDieAuffangtruheWennDieQuelltruheFehlt() {
        f.chests.carry(SorterFixture.IRON, 8);
        f.journalPort.write(JournalEntry.pending("verwaist", SorterFixture.IRON, 8,
                        new BlockPos(30, 64, 30), SorterFixture.ORES_POS, 500, 0)
                .withCarried(f.chests.carriedPayload(), 8, 600));

        Sorter sorter = started();

        assertEquals(8, f.chests.countOf(SorterFixture.FALLBACK_POS, SorterFixture.IRON));
        assertTrue(f.chests.isCarryEmpty());
        assertTrue(sorter.transportLog().recent(10).stream()
                .anyMatch(r -> r.outcome() == TransportLog.Outcome.FALLBACK));
    }
}
