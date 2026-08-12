package me.diono.chestmanager.task;

import me.diono.chestmanager.trait.ChestManagerTrait;

public class IdleTask extends ChestTask {

    @Override
    public int tickInterval() {
        return 100;
    }

    @Override
    public String status() {
        return "Idle";
    }
}
