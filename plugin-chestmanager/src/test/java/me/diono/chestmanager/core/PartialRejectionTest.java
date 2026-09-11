package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Eine volle Kategorie darf nur die betroffenen Stapel liegen lassen. Alles andere
 * in derselben Truhe muss trotzdem an seinen Platz kommen.
 */
class PartialRejectionTest {

    private SorterFixture f;

    @BeforeEach
    void setUp() {
        f = new SorterFixture().defineStandardChests();
    }

    private Sorter started() {
        Sorter sorter = f.build();
        sorter.requestScan();
        for (int guard = 0; guard < 5000; guard++) {
            if (sorter.journal().isEmpty()
                    && (sorter.state() == SorterState.SEEK_SOURCE
                        || (sorter.state() == SorterState.IDLE && sorter.area().hasAnchor()))) {
                return sorter;
            }
            f.tickArriving(1);
        }
        throw new IllegalStateException("Sorter nicht bereit: " + sorter.state());
    }

    @Test
    void volleKategorieLaesstNurDieBetroffenenStapelLiegen() {
        // Die Erztruhen sind randvoll, die Esstruhe ist leer.
        f.chests.remove(SorterFixture.ORES_POS).define(SorterFixture.ORES_POS, 1);
        f.chests.remove(SorterFixture.ORES_POS_2).define(SorterFixture.ORES_POS_2, 1);
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.IRON, 64);
        f.chests.put(SorterFixture.ORES_POS_2, SorterFixture.IRON, 64);

        // In der Ankertruhe liegen Erz (passt nirgends) und Brot (passt).
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 10);
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.BREAD, 12);

        Sorter sorter = started();
        sorter.sortNow();
        f.tickArriving(600);

        assertEquals(12, f.chests.countOf(SorterFixture.FOOD_POS, SorterFixture.BREAD),
                "das Brot muss trotz voller Erzkategorie ankommen");
        assertEquals(10, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON),
                "das Erz bleibt liegen, weil seine Kategorie voll ist");
        assertTrue(f.chests.isCarryEmpty(), "der Träger ist am Ende leer");
    }

    @Test
    void fehlendeZieltruheBlockiertAndereKategorienNicht() {
        // Für "tools" gibt es keine Truhe, die Spitzhacke ist also nicht unterzubringen.
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.PICKAXE, 4);
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.BREAD, 9);

        Sorter sorter = started();
        sorter.sortNow();
        f.tickArriving(600);

        assertEquals(9, f.chests.countOf(SorterFixture.FOOD_POS, SorterFixture.BREAD),
                "das Brot wird trotz fehlender Zieltruhe für Werkzeug sortiert");
        assertEquals(4, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.PICKAXE));
    }

    @Test
    void derLaufEndetUndBeginntNichtVonVorn() {
        f.chests.remove(SorterFixture.ORES_POS).define(SorterFixture.ORES_POS, 1);
        f.chests.remove(SorterFixture.ORES_POS_2).define(SorterFixture.ORES_POS_2, 1);
        f.chests.put(SorterFixture.ORES_POS, SorterFixture.IRON, 64);
        f.chests.put(SorterFixture.ORES_POS_2, SorterFixture.IRON, 64);
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 5);

        Sorter sorter = started();
        sorter.sortNow();
        f.tickArriving(600);

        // Der nicht unterzubringende Stapel darf nicht dauerhaft neuen Anlauf erzeugen.
        assertEquals(SorterState.IDLE, sorter.state(),
                "der Sorter muss in den Ruhezustand zurückkehren, statt zu kreisen");
        assertEquals(5, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON));
    }
}
