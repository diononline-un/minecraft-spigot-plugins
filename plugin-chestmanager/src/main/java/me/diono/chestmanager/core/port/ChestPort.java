package me.diono.chestmanager.core.port;

import java.util.Optional;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ChestSnapshot;

/**
 * Lesender Zugriff auf Truheninventare. Der Adapter stellt sicher, dass
 * Doppeltruhen als ein Inventar erscheinen. Der Kern ruft diese Methode erst auf,
 * nachdem er die Reichweite geprüft hat.
 */
public interface ChestPort {

    /** Inhalt der Truhe, leer wenn dort keine erreichbare Truhe steht. */
    Optional<ChestSnapshot> read(BlockPos pos);
}
