package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.diono.chestmanager.core.port.Clock;
import me.diono.chestmanager.core.port.JournalPort;

/**
 * Führt den Transportzustand auf dem Datenträger. Die Reihenfolge ist der Kern der
 * Item-Sicherheit: Der Eintrag liegt auf der Platte, bevor die Quelltruhe angefasst
 * wird, und verschwindet erst, wenn die Ablage nachweislich stattgefunden hat.
 */
public final class CarryJournal {

    private final JournalPort port;
    private final Clock clock;
    private final Map<String, JournalEntry> open = new LinkedHashMap<>();

    public CarryJournal(JournalPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
        for (JournalEntry entry : port.readAll()) {
            open.put(entry.id(), entry);
        }
    }

    /**
     * Schreibt einen {@code PENDING}-Eintrag und erzwingt die Ablage, bevor der
     * Aufrufer die Quelltruhe verändert.
     */
    public JournalEntry reserve(ItemKey key, int amount, BlockPos source, BlockPos target,
                               int targetBefore) {
        String id = UUID.randomUUID().toString();
        JournalEntry entry = JournalEntry.pending(id, key, amount, source, target,
                clock.nowMillis(), targetBefore);
        port.write(entry);
        open.put(id, entry);
        return entry;
    }

    /** Hält nach der Entnahme den Trägerinhalt fest. */
    public JournalEntry markCarried(JournalEntry entry, String payload, int amount) {
        JournalEntry updated = entry.withCarried(payload, amount, clock.nowMillis());
        port.write(updated);
        open.put(updated.id(), updated);
        return updated;
    }

    /** Entfernt den Eintrag, nachdem die Items nachweislich in der Zieltruhe liegen. */
    public void complete(JournalEntry entry) {
        port.delete(entry.id());
        open.remove(entry.id());
    }

    /**
     * Verwirft einen Eintrag, dessen Entnahme nie stattgefunden hat. Es wird kein
     * Inventar angefasst; die Quelltruhe ist unverändert.
     */
    public void discard(JournalEntry entry) {
        port.delete(entry.id());
        open.remove(entry.id());
    }

    /** Alle noch offenen Vorgänge, in der Reihenfolge ihrer Entstehung. */
    public List<JournalEntry> openEntries() {
        return List.copyOf(open.values());
    }

    /** Vorgänge, bei denen die Entnahme noch aussteht. */
    public List<JournalEntry> pending() {
        List<JournalEntry> result = new ArrayList<>();
        for (JournalEntry entry : open.values()) {
            if (entry.phase() == JournalEntry.JournalPhase.PENDING) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Vorgänge, deren Items der Sorter bei sich führt. */
    public List<JournalEntry> carried() {
        List<JournalEntry> result = new ArrayList<>();
        for (JournalEntry entry : open.values()) {
            if (entry.phase() == JournalEntry.JournalPhase.CARRIED) {
                result.add(entry);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return open.isEmpty();
    }

    public int size() {
        return open.size();
    }
}
