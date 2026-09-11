package me.diono.chestmanager.core.port;

import java.util.Optional;

import me.diono.chestmanager.core.BlockPos;

/**
 * Lesender Zugriff auf die Welt. Alle Aufrufe laufen auf dem Server-Thread; der
 * Kern kennt weder Chunk- noch Weltobjekte.
 */
public interface WorldPort {

    /** Ob an dieser Position ein Truhenblock steht, der als Sortiereinheit zählt. */
    boolean isChest(BlockPos pos);

    /**
     * Erste Zeile des Schildes an dieser Position, sofern dort ein Schild steht.
     * Ein leeres Ergebnis bedeutet „kein Schild"; ein Schild ohne Text liefert einen
     * leeren String.
     */
    Optional<String> signHeader(BlockPos pos);

    /** Ob an dieser Position ein Spieler stehen könnte (Luft, Teppich, Wasser …). */
    boolean isPassable(BlockPos pos);

    /** Ob der Block an dieser Position den Sorter schädigen würde (Lava, Feuer, Kaktus …). */
    boolean isDangerous(BlockPos pos);

    /** Ob der Chunk dieser Position geladen ist. */
    boolean isLoaded(BlockPos pos);

    int minY();

    int maxY();
}
