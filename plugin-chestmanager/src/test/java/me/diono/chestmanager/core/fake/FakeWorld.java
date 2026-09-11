package me.diono.chestmanager.core.fake;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.WorldPort;

/** Welt-Attrappe: Truhen, Schilder, Hindernisse und Gefahren werden explizit gesetzt. */
public final class FakeWorld implements WorldPort {

    private final Set<BlockPos> chests = new HashSet<>();
    private final Map<BlockPos, String> signs = new HashMap<>();
    private final Set<BlockPos> solid = new HashSet<>();
    private final Set<BlockPos> dangerous = new HashSet<>();
    private final Set<BlockPos> unloaded = new HashSet<>();
    private int minY = -64;
    private int maxY = 320;
    private int probes;

    public FakeWorld chest(BlockPos pos) {
        chests.add(pos);
        return this;
    }

    public FakeWorld sign(BlockPos pos, String header) {
        signs.put(pos, header);
        return this;
    }

    public FakeWorld solid(BlockPos... positions) {
        for (BlockPos pos : positions) {
            solid.add(pos);
        }
        return this;
    }

    public FakeWorld dangerous(BlockPos... positions) {
        for (BlockPos pos : positions) {
            dangerous.add(pos);
        }
        return this;
    }

    public FakeWorld unloaded(BlockPos... positions) {
        for (BlockPos pos : positions) {
            unloaded.add(pos);
        }
        return this;
    }

    public FakeWorld bounds(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        return this;
    }

    public Set<BlockPos> chests() {
        return Set.copyOf(chests);
    }

    /** Zählt, wie oft der Scanner einen Block abgefragt hat. */
    public int probes() {
        return probes;
    }

    public void resetProbes() {
        probes = 0;
    }

    @Override
    public boolean isChest(BlockPos pos) {
        return chests.contains(pos);
    }

    @Override
    public Optional<String> signHeader(BlockPos pos) {
        return Optional.ofNullable(signs.get(pos));
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return !solid.contains(pos) && !dangerous.contains(pos);
    }

    @Override
    public boolean isDangerous(BlockPos pos) {
        return dangerous.contains(pos);
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        probes++;
        return !unloaded.contains(pos);
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}
