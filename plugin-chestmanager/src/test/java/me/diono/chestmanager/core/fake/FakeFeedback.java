package me.diono.chestmanager.core.fake;

import java.util.ArrayList;
import java.util.List;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.FeedbackPort;

/** Rückmeldungs-Attrappe, die Animationen zählt. */
public final class FakeFeedback implements FeedbackPort {

    private int swings;
    private final List<BlockPos> lookedAt = new ArrayList<>();

    public int swings() {
        return swings;
    }

    public List<BlockPos> lookedAt() {
        return List.copyOf(lookedAt);
    }

    @Override
    public void swing() {
        swings++;
    }

    @Override
    public void lookAt(BlockPos target) {
        lookedAt.add(target);
    }
}
