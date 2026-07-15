package me.diono.workernpcs.task;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class FollowTask extends WorkerTask {

    private final UUID targetId;

    public FollowTask(UUID targetId) {
        this.targetId = targetId;
    }

    @Override
    public void tick(AiWorkerTrait trait) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            trait.setTask(new IdleTask());
            return;
        }

        trait.getNPC().getNavigator().setTarget(target.getLocation());
        trait.getNPC().getNavigator().getLocalParameters()
            .speedModifier(1.2f)
            .range(2f);
    }

    @Override
    public void stop(AiWorkerTrait trait) {
        trait.getNPC().getNavigator().cancelNavigation();
    }

    @Override
    public int tickInterval() { return 20; }

    @Override
    public String status() { return "Following player"; }
}
