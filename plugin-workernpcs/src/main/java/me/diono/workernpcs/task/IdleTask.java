package me.diono.workernpcs.task;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class IdleTask extends WorkerTask {

    @Override
    public int tickInterval() { return 100; }

    @Override
    public String status() { return "Idle"; }
}
