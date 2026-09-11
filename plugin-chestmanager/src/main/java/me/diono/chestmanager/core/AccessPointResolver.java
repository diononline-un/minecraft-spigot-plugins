package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.List;

import me.diono.chestmanager.core.port.WorldPort;

/**
 * Sucht die Position, von der aus der Sorter an eine Truhe herankommt.
 *
 * <p>Der Truhenblock selbst taugt nicht als Navigationsziel: Truhen stehen oft in
 * Wänden und Regalen. Geprüft werden die sechs Nachbarluken; eine Position gilt als
 * Zugang, wenn dort der Sorter stehen kann und der Block darüber frei ist. Bevorzugt
 * wird die Position, die dem Sorter am nächsten liegt, damit er nicht um die Truhe
 * herumläuft.
 */
public final class AccessPointResolver {

    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}
    };

    private final WorldPort world;

    public AccessPointResolver(WorldPort world) {
        this.world = world;
    }

    /**
     * Begehbare Position neben der Truhe, die dem Sorter am nächsten liegt.
     *
     * @param from Position, von der aus der Sorter anläuft
     * @return leer, wenn keine der Nachbarluken begehbar ist
     */
    public java.util.Optional<BlockPos> resolve(BlockPos chest, BlockPos from) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int[] offset : OFFSETS) {
            BlockPos candidate = chest.offset(offset[0], offset[1], offset[2]);
            if (isStandable(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (from == null) {
            return java.util.Optional.of(candidates.get(0));
        }
        candidates.sort(java.util.Comparator
                .comparingLong((BlockPos pos) -> pos.distanceSquared(from))
                .thenComparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y)
                .thenComparingInt(BlockPos::z));
        return java.util.Optional.of(candidates.get(0));
    }

    /**
     * Ob der Sorter auf dieser Position stehen kann: Der Block muss begehbar und
     * ungefährlich sein, der Block darüber ebenfalls, und der Boden darunter darf
     * nicht ins Leere führen.
     */
    private boolean isStandable(BlockPos pos) {
        if (!world.isLoaded(pos)) {
            return false;
        }
        if (world.isDangerous(pos)) {
            return false;
        }
        if (!world.isPassable(pos)) {
            return false;
        }
        BlockPos above = pos.offset(0, 1, 0);
        if (world.isDangerous(above) || !world.isPassable(above)) {
            return false;
        }
        return world.isLoaded(pos.offset(0, -1, 0));
    }
}
