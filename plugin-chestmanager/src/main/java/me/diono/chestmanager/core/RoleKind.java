package me.diono.chestmanager.core;

/** Rolle einer Truhe im Sortierbetrieb. */
public enum RoleKind {

    /** Markiert den Bereich und dient zugleich als Sortierziel wie jede Kategorytruhe. */
    ANCHOR,

    /** Zieltruhe einer Kategorie. */
    CATEGORY,

    /** Auffangtruhe für Items, deren Typ in keiner Kategorie vorkommt. */
    FALLBACK,

    /** Truhe im Bereich, die der Sorter weder liest noch beschreibt. */
    IGNORED;

    /** Ob der Sorter Items aus dieser Truhe entnehmen darf. */
    public boolean isReadable() {
        return this != IGNORED;
    }

    /** Ob der Sorter Items in diese Truhe legen darf. */
    public boolean isWritable() {
        return this != IGNORED;
    }
}
