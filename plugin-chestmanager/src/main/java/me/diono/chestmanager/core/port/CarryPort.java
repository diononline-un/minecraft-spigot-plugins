package me.diono.chestmanager.core.port;

import java.util.Optional;

import me.diono.chestmanager.core.ItemKey;
import me.diono.chestmanager.core.BlockPos;

/**
 * Der Träger des Sorters: die Items, die er zwischen Quelltruhe und Zieltruhe
 * bei sich führt. Der Träger gehört dem Adapter, damit der Kern nur mit dem
 * serialisierten Inhalt arbeitet und keinen Bukkit-Typ kennen muss.
 *
 * <p>Der Ablauf ist bewusst dreischrittig: erst {@link #preview}, damit der Kern
 * den Inhalt ins Journal schreiben kann, dann {@link #extract}, dann
 * {@link #deposit}. Stirbt der Server zwischen den Schritten, kann der Kern den
 * serialisierten Inhalt aus dem Journal zurück in eine Truhe legen.
 */
public interface CarryPort {

    /**
     * Serialisiert die Items, die {@link #extract} entnehmen würde, ohne das
     * Inventar zu verändern. Leer, wenn dort nichts Passendes liegt.
     */
    Optional<String> preview(BlockPos pos, ItemKey key, int amount);

    /**
     * Entnimmt bis zu {@code amount} Items mit diesem Schlüssel in den Träger und
     * gibt die tatsächlich entnommene Menge zurück.
     */
    int extract(BlockPos pos, ItemKey key, int amount);

    /**
     * Legt den gesamten Trägerinhalt in die Truhe und gibt die Menge zurück, die
     * keinen Platz gefunden hat. Ein Rest verbleibt im Träger.
     */
    int deposit(BlockPos pos);

    /** Aktueller Trägerinhalt als serialisierter Blob. Leer, wenn der Träger leer ist. */
    String carriedPayload();

    /** Lädt einen zuvor gesicherten Blob in den Träger. Ersetzt den bisherigen Inhalt. */
    void loadCarried(String payload);

    /**
     * Notausgang: legt den Trägerinhalt an der aktuellen Position des Sorters ab und
     * gibt die abgelegte Menge zurück. Wird nur benutzt, wenn weder Quell- noch
     * Auffangtruhe die Items aufnehmen können.
     */
    int dropCarried();

    boolean isCarryEmpty();
}
