package me.diono.chestmanager.core;

/** Zustände des Sortierautomaten. */
public enum SorterState {

    /** Wartet auf den nächsten Sortierlauf. */
    IDLE,

    /** Ermittelt den Bereich, budgetiert über mehrere Ticks. */
    SCANNING,

    /** Sucht eine Quelltruhe mit fehlplatzierten Items. */
    SEEK_SOURCE,

    /** Sucht das Ziel für den gewählten Stapel, bevor etwas entnommen wird. */
    SEEK_TARGET,

    /** Läuft zur Quelltruhe. */
    APPROACH_SOURCE,

    /** Entnimmt den Stapel und sichert ihn im Journal. */
    PICKUP,

    /** Läuft zur Zieltruhe. */
    APPROACH_TARGET,

    /** Legt den Stapel ab und schliesst den Vorgang ab. */
    DELIVER,

    /** Führt offene Vorgänge aus dem Journal zu Ende oder zurück. */
    RECOVER,

    /** Vom Betreiber angehalten; gibt keine neuen Fahrten auf. */
    STOPPED
}
