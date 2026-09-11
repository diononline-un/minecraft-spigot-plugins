package me.diono.chestmanager.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Protokoll der Transportvorgänge, damit der Betreiber nachvollziehen kann, wohin
 * ein Item gebracht wurde. Der Umfang ist begrenzt; ältere Einträge fallen heraus.
 */
public final class TransportLog {

    /** Wie ein Vorgang ausgegangen ist. */
    public enum Outcome {
        /** In der Zieltruhe abgelegt. */
        DELIVERED,
        /** In die Quelltruhe zurückgelegt. */
        RETURNED,
        /** In die Auffangtruhe gelegt, weil die Quelltruhe nicht mehr verfügbar war. */
        FALLBACK,
        /** An der Position des Sorters abgelegt; der Betreiber muss eingreifen. */
        DROPPED,
        /** Ablagezustand unklar, Items wurden zurückgeführt. */
        UNSAFE
    }

    public record Record(ItemKey key, int amount, BlockPos source, BlockPos target,
                         long timestamp, Outcome outcome) {}

    private final int capacity;
    private final Deque<Record> records = new ArrayDeque<>();

    public TransportLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public void record(ItemKey key, int amount, BlockPos source, BlockPos target,
                       long now, Outcome outcome) {
        records.addFirst(new Record(key, amount, source, target, now, outcome));
        while (records.size() > capacity) {
            records.removeLast();
        }
    }

    /** Die jüngsten Vorgänge, optional auf einen Item-Typ eingegrenzt. */
    public List<Record> recent(int limit, String typeIdFilter) {
        List<Record> result = new ArrayList<>();
        for (Record record : records) {
            if (typeIdFilter != null && !record.key().typeId().equals(typeIdFilter)) {
                continue;
            }
            result.add(record);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    public List<Record> recent(int limit) {
        return recent(limit, null);
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }
}
