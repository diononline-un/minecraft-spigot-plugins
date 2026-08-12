package me.diono.chestmanager.trait;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.diono.chestmanager.ChestManagerPlugin;
import me.diono.chestmanager.chest.ChestArea;
import me.diono.chestmanager.config.CategoryConfig;
import me.diono.chestmanager.task.*;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

@TraitName("chestmanager")
public class ChestManagerTrait extends Trait {

    private final ChestManagerPlugin plugin = ChestManagerPlugin.getInstance();

    @Persist("home") Location home;
    @Persist("scan_radius") int scanRadius = 40;
    @Persist("sort_interval") int sortIntervalTicks = 600;

    private final ChestArea chestArea;
    private ChestTask currentTask;
    private int tickCounter;
    private final List<ItemStack> carryInventory = new ArrayList<>(27);

    public ChestManagerTrait() {
        super("chestmanager");
        this.chestArea = new ChestArea(plugin.getCategoryConfig());
        this.chestArea.setRadius(scanRadius);
    }

    public void onServerStart() {
        if (home != null) {
            chestArea.setHome(home);
            chestArea.setRadius(scanRadius);
        }
        if (currentTask == null) {
            currentTask = new IdleTask();
            currentTask.start(this);
        }
    }

    @Override
    public void onAttach() {
        if (home != null) {
            chestArea.setHome(home);
            chestArea.setRadius(scanRadius);
        }
        if (currentTask == null) {
            currentTask = new SortTask();
            currentTask.start(this);
        }
    }

    @Override
    public void onSpawn() {
        if (currentTask == null) {
            currentTask = new SortTask();
            currentTask.start(this);
        }
        if (npc.getEntity() instanceof LivingEntity entity) {
            entity.setSilent(true);
        }
    }

    @Override
    public void onRemove() {
        if (currentTask != null) {
            currentTask.stop(this);
            currentTask = null;
        }
    }

    @Override
    public void onDespawn() {
        if (currentTask != null) {
            currentTask.stop(this);
        }
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) return;
        tickCounter++;
        if (currentTask == null) return;
        if (tickCounter % currentTask.tickInterval() == 0) {
            chunkLoadCheck();
            currentTask.tick(this);
        }
    }

    private void chunkLoadCheck() {
        Location npcLoc = npc.getStoredLocation();
        if (npcLoc != null && !npcLoc.getChunk().isLoaded()) {
            npcLoc.getChunk().load();
        }
    }

    public void setTask(ChestTask task) {
        if (currentTask != null) {
            currentTask.stop(this);
        }
        currentTask = task;
        task.start(this);
    }

    public void setHome(Location loc) {
        this.home = loc.clone();
        this.chestArea.setHome(loc);
    }

    public Location getHome() {
        return home;
    }

    public void setScanRadius(int radius) {
        this.scanRadius = radius;
        this.chestArea.setRadius(radius);
    }

    public int getScanRadius() {
        return scanRadius;
    }

    public void setSortIntervalTicks(int ticks) {
        this.sortIntervalTicks = ticks;
    }

    public int getSortIntervalTicks() {
        return sortIntervalTicks;
    }

    public ChestArea getChestArea() {
        return chestArea;
    }

    public CategoryConfig getCategoryConfig() {
        return plugin.getCategoryConfig();
    }

    public ChestTask getCurrentTask() {
        return currentTask;
    }

    public List<ItemStack> getCarryInventory() {
        return carryInventory;
    }

    public void requestItems(Player player, Material material, int amount, String category) {
        Location requestChest = chestArea.getRequestChest();
        if (requestChest == null) {
            player.sendMessage("§cNo request chest found. Place a [REQUEST] sign on a chest.");
            return;
        }

        setTask(new FulfillRequestTask(player.getUniqueId(), material, amount,
                category, requestChest));
    }

    public void triggerSort() {
        if (!(currentTask instanceof SortTask)) {
            setTask(new SortTask());
        }
    }

    public static void shutdownAll() {
    }
}
