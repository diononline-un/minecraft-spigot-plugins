package me.diono.chestmanager.core;

/**
 * Blockkoordinate innerhalb der Welt eines Sorters. Ein Sorter ist an genau eine
 * Welt gebunden, deshalb trägt die Position keinen Weltbezug.
 */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /**
     * Quadratischer Abstand, saturierend bei {@link Long#MAX_VALUE}. Ein negativer
     * Rückgabewert würde Entfernungsvergleiche umdrehen und die falsche Truhe als
     * die nächste ausweisen; deshalb wird hier saturiert statt überlaufen.
     */
    public long distanceSquared(BlockPos other) {
        long dx = Math.abs((long) x - other.x);
        long dy = Math.abs((long) y - other.y);
        long dz = Math.abs((long) z - other.z);
        long sum = square(dx);
        sum = saturatingAdd(sum, square(dy));
        return saturatingAdd(sum, square(dz));
    }

    /** Grösste Zahl, deren Quadrat noch in einen {@code long} passt. */
    private static final long MAX_SAFE_ROOT = 3_037_000_499L;

    private static long square(long value) {
        if (value > MAX_SAFE_ROOT) {
            return Long.MAX_VALUE;
        }
        return value * value;
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    public double distance(BlockPos other) {
        return Math.sqrt((double) distanceSquared(other));
    }

    /** Chebyshev-Abstand: die grösste Achsendifferenz. */
    public int chebyshevDistance(BlockPos other) {
        return Math.max(Math.abs(x - other.x),
                Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
    }

    /** Mittelpunkt des Blocks, für Reichweitenmessungen. */
    public double distanceToCenter(BlockPos other) {
        double dx = (x + 0.5) - (other.x + 0.5);
        double dy = (y + 0.5) - (other.y + 0.5);
        double dz = (z + 0.5) - (other.z + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return x + "/" + y + "/" + z;
    }
}
