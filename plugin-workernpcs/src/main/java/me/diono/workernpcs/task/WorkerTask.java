package me.diono.workernpcs.task;

import me.diono.workernpcs.trait.AiWorkerTrait;

public abstract class WorkerTask {

    public void start(AiWorkerTrait trait) {}
    public void tick(AiWorkerTrait trait) {}
    public void stop(AiWorkerTrait trait) {}

    public int tickInterval() { return 10; }
    public String status() { return "Working..."; }
    public boolean isComplete() { return false; }
}
