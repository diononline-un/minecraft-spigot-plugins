package me.diono.workernpcs.task;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class CollectTask extends WorkerTask {

    private enum Phase { NAVIGATE_TO, COLLECT, RETURN_HOME }

    private final Location deathLoc;
    private final Location home;
    private Phase phase = Phase.NAVIGATE_TO;
    private boolean navStarted;

    public CollectTask(Location deathLoc, Location home) {
        this.deathLoc = deathLoc;
        this.home = home;
    }

    @Override
    public void start(AiWorkerTrait trait) {
        if (trait.getNPC().isSpawned() && deathLoc != null) {
            trait.getNPC().getNavigator().setTarget(deathLoc);
            trait.getNPC().getNavigator().getLocalParameters()
                .speedModifier(1.3f)
                .range(2f);
            navStarted = true;
        }
    }

    @Override
    public void tick(AiWorkerTrait trait) {
        if (!navStarted) return;

        switch (phase) {
            case NAVIGATE_TO:
                if (!trait.getNPC().getNavigator().isNavigating()) {
                    phase = Phase.COLLECT;
                }
                break;

            case COLLECT:
                if (trait.getNPC().getEntity() == null) return;
                collectNearbyItems(trait);
                phase = Phase.RETURN_HOME;
                trait.getNPC().getNavigator().setTarget(home != null ? home : trait.getNPC().getStoredLocation());
                break;

            case RETURN_HOME:
                if (!trait.getNPC().getNavigator().isNavigating()) {
                    trait.setTask(new IdleTask());
                }
                break;
        }
    }

    private void collectNearbyItems(AiWorkerTrait trait) {
        if (trait.getNPC().getEntity() == null) return;
        Location loc = trait.getNPC().getEntity().getLocation();

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
            if (entity instanceof Item item && item.getPickupDelay() <= 0) {
                ItemStack stack = item.getItemStack();
                boolean added = trait.addItem(stack);
                if (added) {
                    item.remove();
                }
            }
        }
    }

    @Override
    public void stop(AiWorkerTrait trait) {
        trait.getNPC().getNavigator().cancelNavigation();
    }

    @Override
    public int tickInterval() { return 10; }

    @Override
    public String status() { return "Collecting dropped items"; }
}
