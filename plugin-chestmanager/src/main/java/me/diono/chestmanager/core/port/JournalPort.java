package me.diono.chestmanager.core.port;

import java.util.List;

import me.diono.chestmanager.core.JournalEntry;

/**
 * Dauerhafte Ablage des Transportzustands. Ein Schreibvorgang muss abgeschlossen
 * sein, bevor der Kern die Quelltruhe verändert — davon hängt die Item-Sicherheit ab.
 */
public interface JournalPort {

    /** Schreibt oder ersetzt den Eintrag und erzwingt die Ablage auf dem Datenträger. */
    void write(JournalEntry entry);

    void delete(String entryId);

    List<JournalEntry> readAll();
}
