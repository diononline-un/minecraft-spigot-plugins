package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Truhen, die der Sorter nicht erreicht. Eine Sperre läuft nach einer
 * konfigurierbaren Dauer ab, damit sich die Lage ändern kann, ohne dass der
 * Betreiber eingreifen muss.
 */
public final class BlockedChests {

    /**
     * Ein Sperreintrag.
     *
     * @param reason          Klartextgrund, etwa „nicht angelaufen"
     * @param blockedAt       Zeitpunkt der Sperrung
     * @param until           Zeitpunkt, ab dem wieder versucht wird
     * @param attempts        erfolglose Anläufe, die zur Sperre geführt haben
     */
    public record Entry(BlockPos pos, String reason, long blockedAt, long until, int attempts) {

        public long remainingMillis(long now) {
            return Math.max(0, until - now);
        }
    }

    private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();

    public void block(BlockPos pos, String reason, long now, long durationMillis, int attempts) {
        entries.put(pos, new Entry(pos, reason, now, now + Math.max(0, durationMillis), attempts));
    }

    /** Ob die Truhe gerade gesperrt ist. Abgelaufene Sperren zählen nicht mehr. */
    public boolean isBlocked(BlockPos pos, long now) {
        Entry entry = entries.get(pos);
        return entry != null && entry.until() > now;
    }

    public void unblock(BlockPos pos) {
        entries.remove(pos);
    }

    /** Aktive Sperren, abgelaufene werden dabei entfernt. */
    public List<Entry> active(long now) {
        Iterator<Map.Entry<BlockPos, Entry>> iterator = entries.entrySet().iterator();
        List<Entry> result = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.until() <= now) {
                iterator.remove();
            } else {
                result.add(entry);
            }
        }
        return result;
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
