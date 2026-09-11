package me.diono.chestmanager.core;

/**
 * Alle abstimmbaren Werte des Sorters. Der Kern liest sie ausschliesslich von
 * hier, damit Tests Szenarien ohne Serverkonfiguration aufbauen können.
 */
public record SorterSettings(
        int searchRadius,
        int maxChests,
        int maxHeightDifference,
        int linkDistance,
        int blocksPerTick,
        double reach,
        long navTimeoutMillis,
        int maxNavAttempts,
        long retryBackoffMillis,
        long blockDurationMillis,
        int carryLimit,
        long sortIntervalMillis,
        long rescanIntervalMillis,
        int opsPerTick,
        int maxChunkTickets,
        boolean debug) {

    public static SorterSettings defaults() {
        return new SorterSettings(
                48,
                512,
                64,
                0,
                512,
                3.5,
                4_000L,
                2,
                1_000L,
                120_000L,
                27,
                30_000L,
                300_000L,
                8,
                256,
                false);
    }

    public SorterSettings {
        if (searchRadius < 1) throw new IllegalArgumentException("searchRadius muss positiv sein");
        if (maxChests < 1) throw new IllegalArgumentException("maxChests muss positiv sein");
        if (linkDistance < 0) throw new IllegalArgumentException("linkDistance darf nicht negativ sein");
        if (blocksPerTick < 1) throw new IllegalArgumentException("blocksPerTick muss positiv sein");
        if (reach <= 0) throw new IllegalArgumentException("reach muss positiv sein");
        if (carryLimit < 1) throw new IllegalArgumentException("carryLimit muss positiv sein");
        if (opsPerTick < 1) throw new IllegalArgumentException("opsPerTick muss positiv sein");
        // Die Tragemenge darf eine Doppeltruhe nicht überschreiten, sonst ist eine
        // Rückgabe nicht mehr garantiert.
        if (carryLimit > 54) throw new IllegalArgumentException("carryLimit darf 54 nicht überschreiten");
    }

    public SorterSettings withLinkDistance(int value) {
        return new SorterSettings(searchRadius, maxChests, maxHeightDifference, value, blocksPerTick,
                reach, navTimeoutMillis, maxNavAttempts, retryBackoffMillis, blockDurationMillis,
                carryLimit, sortIntervalMillis, rescanIntervalMillis, opsPerTick, maxChunkTickets, debug);
    }

    public SorterSettings withSearchRadius(int value) {
        return new SorterSettings(value, maxChests, maxHeightDifference, linkDistance, blocksPerTick,
                reach, navTimeoutMillis, maxNavAttempts, retryBackoffMillis, blockDurationMillis,
                carryLimit, sortIntervalMillis, rescanIntervalMillis, opsPerTick, maxChunkTickets, debug);
    }

    public SorterSettings withCarryLimit(int value) {
        return new SorterSettings(searchRadius, maxChests, maxHeightDifference, linkDistance, blocksPerTick,
                reach, navTimeoutMillis, maxNavAttempts, retryBackoffMillis, blockDurationMillis,
                value, sortIntervalMillis, rescanIntervalMillis, opsPerTick, maxChunkTickets, debug);
    }
}
