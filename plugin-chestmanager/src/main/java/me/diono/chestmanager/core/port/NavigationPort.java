package me.diono.chestmanager.core.port;

import me.diono.chestmanager.core.BlockPos;

/**
 * Fortbewegung des Sorters. Der Kern behandelt Navigation als Auftrag mit
 * ungewissem Ausgang: {@link #isNavigating()} ist ein Fortschrittssignal, kein
 * Erfolgsnachweis. Ob der Sorter wirklich angekommen ist, prüft er über
 * {@link #position()}.
 */
public interface NavigationPort {

    void navigateTo(BlockPos target);

    void cancel();

    boolean isNavigating();

    /** Aktuelle Position der Entität, leer wenn die Entität nicht verfügbar ist. */
    java.util.Optional<BlockPos> position();

    /** Ob die Entität existiert und angesprochen werden kann. */
    boolean isAvailable();
}
