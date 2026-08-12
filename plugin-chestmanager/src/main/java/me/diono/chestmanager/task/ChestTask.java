package me.diono.chestmanager.task;

import me.diono.chestmanager.trait.ChestManagerTrait;

public abstract class ChestTask {

    public void start(ChestManagerTrait trait) {}
    public void tick(ChestManagerTrait trait) {}
    public void stop(ChestManagerTrait trait) {}

    public int tickInterval() {
        return 10;
    }

    public String status() {
        return "Working...";
    }
}
