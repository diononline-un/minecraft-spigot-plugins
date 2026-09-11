package me.diono.chestmanager.core.port;

import me.diono.chestmanager.core.BlockPos;

/**
 * Sichtbare Rückmeldung des Sorters.
 *
 * <p>Der Sorter zeigt seine Handlungen, damit ein Betreiber ohne Diagnosebefehl
 * erkennt, ob er arbeitet. Geräusche gehören ausdrücklich nicht dazu: Der Adapter
 * führt jede Bewegung stumm aus.
 */
public interface FeedbackPort {

    /** Handbewegung beim Entnehmen oder Ablegen. Ohne Geräusch. */
    void swing();

    /** Richtet den Sorter zur angegebenen Truhe aus. */
    void lookAt(BlockPos target);
}
