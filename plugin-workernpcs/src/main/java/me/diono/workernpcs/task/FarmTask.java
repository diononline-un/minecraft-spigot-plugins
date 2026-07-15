package me.diono.workernpcs.task;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class FarmTask extends WorkerTask {

    private static final Map<Material, Material> CROP_SEED = Map.ofEntries(
        Map.entry(Material.WHEAT, Material.WHEAT_SEEDS),
        Map.entry(Material.CARROTS, Material.CARROT),
        Map.entry(Material.POTATOES, Material.POTATO),
        Map.entry(Material.BEETROOTS, Material.BEETROOT_SEEDS),
        Map.entry(Material.NETHER_WART, Material.NETHER_WART),
        Map.entry(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES),
        Map.entry(Material.COCOA, Material.COCOA_BEANS)
    );

    private static final Map<Material, Integer> MAX_AGE = Map.ofEntries(
        Map.entry(Material.WHEAT, 7),
        Map.entry(Material.CARROTS, 7),
        Map.entry(Material.POTATOES, 7),
        Map.entry(Material.BEETROOTS, 3),
        Map.entry(Material.NETHER_WART, 3),
        Map.entry(Material.SWEET_BERRY_BUSH, 3),
        Map.entry(Material.COCOA, 2)
    );

    private static final int SCAN_RADIUS = 12;
    private static final int SCAN_Y = 3;

    private Block currentTarget;
    private int scanCooldown;
    private final Queue<Block> ripeCrops = new ArrayDeque<>();
    private final Set<String> scannedCropTypes = new HashSet<>();

    public FarmTask(AiWorkerTrait trait) {}

    @Override
    public void tick(AiWorkerTrait trait) {
        if (trait.getNPC().getEntity() == null) return;

        if (currentTarget != null && trait.getNPC().getNavigator().isNavigating()) return;

        if (currentTarget != null && !trait.getNPC().getNavigator().isNavigating()) {
            harvestBlock(trait, currentTarget);
            currentTarget = null;
        }

        if (scanCooldown <= 0) {
            scanArea(trait);
            scanCooldown = 60;
        } else {
            scanCooldown--;
        }

        if (currentTarget == null && !ripeCrops.isEmpty()) {
            currentTarget = ripeCrops.poll();
            trait.getNPC().getNavigator().setTarget(
                currentTarget.getLocation().add(0.5, 0, 0.5));
            trait.getNPC().getNavigator().getLocalParameters()
                .speedModifier(1.0f).range(1.5f);
        }
    }

    private void scanArea(AiWorkerTrait trait) {
        ripeCrops.clear();
        scannedCropTypes.clear();

        List<Location> centers = new ArrayList<>();
        for (Map.Entry<String, Location> entry : trait.getKnownLocations().entrySet()) {
            if (entry.getKey().startsWith("farm:")) {
                centers.add(entry.getValue());
            }
        }

        if (centers.isEmpty()) {
            centers.add(trait.getNPC().getStoredLocation());
        }

        for (Location center : centers) {
            if (center.getWorld() == null) continue;
            for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
                for (int y = -SCAN_Y; y <= SCAN_Y; y++) {
                    for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                        Block block = center.clone().add(x, y, z).getBlock();
                        Material type = block.getType();
                        Integer maxAge = MAX_AGE.get(type);
                        if (maxAge == null) continue;
                        if (block.getBlockData() instanceof Ageable ageable
                                && ageable.getAge() >= maxAge) {
                            ripeCrops.add(block);
                            scannedCropTypes.add(type.name().toLowerCase());
                        }
                    }
                }
            }
            for (String crop : scannedCropTypes) {
                trait.remember("farm:" + crop, center.clone());
            }
        }
    }

    private void harvestBlock(AiWorkerTrait trait, Block block) {
        Material type = block.getType();
        Collection<ItemStack> drops = block.getDrops(new ItemStack(Material.AIR));
        for (ItemStack drop : drops) trait.addItem(drop);
        block.setType(Material.AIR);

        Material seed = CROP_SEED.get(type);
        if (seed != null && trait.hasItem(seed)) {
            trait.takeItem(seed);
            block.setType(type);
            if (block.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(0);
                block.setBlockData(ageable);
            }
        }
    }

    @Override
    public void stop(AiWorkerTrait trait) {
        trait.getNPC().getNavigator().cancelNavigation();
        currentTarget = null;
        ripeCrops.clear();
    }

    @Override
    public int tickInterval() { return 10; }

    @Override
    public String status() { return "Farming crops"; }
}
