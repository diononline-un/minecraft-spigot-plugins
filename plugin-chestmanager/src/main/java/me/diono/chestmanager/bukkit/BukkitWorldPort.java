package me.diono.chestmanager.bukkit;

import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.WorldPort;

/** Weltzugriff über die Bukkit-API. Alle Aufrufe laufen auf dem Server-Thread. */
public final class BukkitWorldPort implements WorldPort {

    /** Blöcke, die den Sorter schädigen würden, wenn er darauf läuft. */
    private static final Set<Material> DANGEROUS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.WITHER_ROSE);

    private final World world;

    public BukkitWorldPort(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    @Override
    public boolean isChest(BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return false;
        }
        // Eine Doppeltruhe zählt einmal. Sie wird über ihre linke Hälfte geführt;
        // die rechte Hälfte liefert dasselbe Inventar und wird übersprungen.
        BlockData data = block.getBlockData();
        if (data instanceof Chest chest) {
            return chest.getType() != Chest.Type.RIGHT;
        }
        return true;
    }

    @Override
    public Optional<String> signHeader(BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (!Tag.ALL_SIGNS.isTagged(block.getType())) {
            return Optional.empty();
        }
        if (!(block.getState() instanceof Sign sign)) {
            return Optional.empty();
        }
        SignSide front = sign.getSide(Side.FRONT);
        String line = front.getLine(0);
        return Optional.of(line == null ? "" : line);
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z()).isPassable();
    }

    @Override
    public boolean isDangerous(BlockPos pos) {
        return DANGEROUS.contains(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType());
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4);
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight() - 1;
    }
}
