package me.diono.chestmanager.core.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.NavigationPort;

/**
 * Navigations-Attrappe ohne automatische Fortbewegung. Tests setzen die Position
 * selbst, damit sie „NPC steht weit weg, Navigation meldet Ende" gezielt nachstellen
 * können.
 */
public final class FakeNavigation implements NavigationPort {

    private BlockPos position;
    private BlockPos target;
    private boolean navigating;
    private boolean available = true;
    private int navigateCalls;
    private int cancelCalls;
    private final List<BlockPos> targets = new ArrayList<>();

    public FakeNavigation at(BlockPos start) {
        this.position = start;
        return this;
    }

    public void teleport(BlockPos pos) {
        this.position = pos;
    }

    /** Beendet die Navigation so, als hätte der Server den Pfad verworfen. */
    public void finishNavigation() {
        this.navigating = false;
    }

    public void setAvailable(boolean value) {
        this.available = value;
    }

    public int navigateCalls() {
        return navigateCalls;
    }

    public int cancelCalls() {
        return cancelCalls;
    }

    public List<BlockPos> requestedTargets() {
        return List.copyOf(targets);
    }

    public BlockPos currentTarget() {
        return target;
    }

    /** Bewegt den Sorter sofort zum letzten Ziel und beendet die Navigation. */
    public void arrive() {
        if (target != null) {
            position = target;
        }
        navigating = false;
    }

    @Override
    public void navigateTo(BlockPos newTarget) {
        navigateCalls++;
        this.target = newTarget;
        this.navigating = true;
        targets.add(newTarget);
    }

    @Override
    public void cancel() {
        cancelCalls++;
        this.navigating = false;
    }

    @Override
    public Optional<BlockPos> position() {
        return Optional.ofNullable(position);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isNavigating() {
        return navigating;
    }
}
