package me.diono.workernpcs.task;

import org.bukkit.Location;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class GoHomeTask extends WorkerTask {

    private final Location home;
    private boolean started;

    public GoHomeTask(Location home) {
        this.home = home;
    }

    @Override
    public void start(AiWorkerTrait trait) {
        if (home != null && trait.getNPC().isSpawned()) {
            trait.getNPC().getNavigator().setTarget(home);
            trait.getNPC().getNavigator().getLocalParameters()
                .speedModifier(1.0f)
                .range(1.5f);
            started = true;
        }
    }

    @Override
    public void tick(AiWorkerTrait trait) {
        if (!started || home == null) return;
        if (!trait.getNPC().getNavigator().isNavigating()) {
            trait.setTask(new IdleTask());
        }
    }

    @Override
    public int tickInterval() { return 20; }

    @Override
    public String status() { return "Returning home"; }
}
