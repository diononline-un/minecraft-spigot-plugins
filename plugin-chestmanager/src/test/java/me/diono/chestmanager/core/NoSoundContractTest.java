package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Sichert die Anforderung, dass der Sorter stumm arbeitet. Ein Klangaufruf irgendwo
 * im Plugin würde sie brechen; dieser Test schlägt dann fehl.
 */
class NoSoundContractTest {

    @Test
    void derQuelltextEnthaeltKeineKlangaufrufe() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root), "Quellverzeichnis nicht gefunden: " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("playSound")
                        || source.contains("playNote")
                        || source.contains("World.Sound")
                        || source.contains("SoundCategory")) {
                    offenders.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Der Sorter darf keine Geräusche erzeugen, gefunden in: " + offenders);
    }

    @Test
    void handbewegungWirdAusgeloestAberNurEinmalJeVorgang() {
        SorterFixture f = new SorterFixture().defineStandardChests();
        f.chests.put(SorterFixture.ANCHOR_POS, SorterFixture.IRON, 16);

        Sorter sorter = f.build();
        sorter.requestScan();
        for (int i = 0; i < 5000 && sorter.state() != SorterState.SEEK_SOURCE; i++) {
            f.tickArriving(1);
        }
        int before = f.feedback.swings();

        sorter.sortNow();
        f.tickArriving(400);

        assertEquals(before + 2, f.feedback.swings(),
                "je Vorgang eine Bewegung beim Entnehmen und eine beim Ablegen");
        assertEquals(0, f.chests.countOf(SorterFixture.ANCHOR_POS, SorterFixture.IRON));
    }
}
