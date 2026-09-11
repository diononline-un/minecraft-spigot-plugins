package me.diono.chestmanager.core;

/**
 * Ein Transportvorgang im Journal. Der Eintrag entsteht, bevor der Sorter die
 * Quelltruhe anfasst, und verschwindet erst, wenn die Items nachweislich abgelegt
 * sind.
 *
 * <p>{@code payload} ist der serialisierte Inhalt, den der Sorter bei sich führt.
 * Er ist für den Kern undurchsichtig und macht die Wiederherstellung nach einem
 * Absturz überhaupt erst möglich. {@code targetBefore} hält fest, wie viele Items
 * dieses Schlüssels vor der Ablage in der Zieltruhe lagen; daran erkennt die
 * Wiederherstellung, ob eine Ablage bereits stattgefunden hat.
 *
 * @param id           eindeutige Kennung des Vorgangs
 * @param key          Item-Schlüssel der transportierten Items
 * @param amount       Menge
 * @param source       Quelltruhe
 * @param target       geplante Zieltruhe
 * @param phase        Zustand des Vorgangs
 * @param timestamp    Zeitpunkt der letzten Änderung
 * @param payload      serialisierter Trägerinhalt, erst ab {@code CARRIED} gesetzt
 * @param targetBefore Itemmenge dieses Schlüssels in der Zieltruhe vor der Ablage
 */
public record JournalEntry(String id, ItemKey key, int amount, BlockPos source,
                           BlockPos target, JournalPhase phase, long timestamp,
                           String payload, int targetBefore) {

    /** {@code PENDING} liegt vor der Entnahme, {@code CARRIED} danach. */
    public enum JournalPhase {
        PENDING,
        CARRIED
    }

    public static JournalEntry pending(String id, ItemKey key, int amount, BlockPos source,
                                       BlockPos target, long now, int targetBefore) {
        return new JournalEntry(id, key, amount, source, target, JournalPhase.PENDING, now, null, targetBefore);
    }

    public JournalEntry withPhase(JournalPhase newPhase, long now) {
        return new JournalEntry(id, key, amount, source, target, newPhase, now, payload, targetBefore);
    }

    public JournalEntry withCarried(String carriedPayload, int carriedAmount, long now) {
        return new JournalEntry(id, key, carriedAmount, source, target, JournalPhase.CARRIED, now,
                carriedPayload, targetBefore);
    }

    public boolean hasPayload() {
        return payload != null && !payload.isEmpty();
    }
}
