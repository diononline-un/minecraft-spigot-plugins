package me.diono.chestmanager.core.fake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.diono.chestmanager.core.JournalEntry;
import me.diono.chestmanager.core.port.JournalPort;

/** Journal-Attrappe im Speicher, mit Zähler für Schreibvorgänge. */
public final class InMemoryJournal implements JournalPort {

    private final Map<String, JournalEntry> entries = new LinkedHashMap<>();
    private int writes;
    private int deletes;
    private boolean failOnWrite;

    /** Lässt jeden Schreibvorgang fehlschlagen, um Fehlerpfade zu prüfen. */
    public void failOnWrite(boolean value) {
        this.failOnWrite = value;
    }

    public int writes() {
        return writes;
    }

    public int deletes() {
        return deletes;
    }

    public boolean contains(String id) {
        return entries.containsKey(id);
    }

    public JournalEntry get(String id) {
        return entries.get(id);
    }

    public int size() {
        return entries.size();
    }

    @Override
    public void write(JournalEntry entry) {
        if (failOnWrite) {
            throw new IllegalStateException("Journal nicht beschreibbar");
        }
        writes++;
        entries.put(entry.id(), entry);
    }

    @Override
    public void delete(String entryId) {
        deletes++;
        entries.remove(entryId);
    }

    @Override
    public List<JournalEntry> readAll() {
        return new ArrayList<>(entries.values());
    }
}
