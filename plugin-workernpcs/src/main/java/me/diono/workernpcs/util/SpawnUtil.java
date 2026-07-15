package me.diono.workernpcs.util;

import org.bukkit.Location;

public class SpawnUtil {

    private static final double SPAWN_RADIUS = 1000;

    public static boolean isWithinSpawnRadius(Location loc, Location spawnCenter) {
        if (loc == null || spawnCenter == null) return false;
        if (!loc.getWorld().equals(spawnCenter.getWorld())) return false;
        double dx = loc.getX() - spawnCenter.getX();
        double dz = loc.getZ() - spawnCenter.getZ();
        return (dx * dx + dz * dz) <= (SPAWN_RADIUS * SPAWN_RADIUS);
    }
}
