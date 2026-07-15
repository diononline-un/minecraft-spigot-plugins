package me.diono.workernpcs.task;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import me.diono.workernpcs.trait.AiWorkerTrait;
import me.diono.workernpcs.util.SpawnUtil;

public class BuildTask extends WorkerTask {

    private enum Phase { GATHERING, BUILDING, DONE }

    private final Map<String, Object> params;
    private Phase phase = Phase.GATHERING;
    private final Map<Material, Integer> needed = new HashMap<>();
    private final List<BuildBlock> buildPlan = new ArrayList<>();
    private int buildIndex;
    private int searchStep;

    public BuildTask(Map<String, Object> params, AiWorkerTrait trait) {
        this.params = params;
        parseBuildParams();
    }

    @SuppressWarnings("unchecked")
    private void parseBuildParams() {
        Object sizeObj = params.get("size");
        Object matObj = params.get("material");

        String size = sizeObj != null ? sizeObj.toString() : "3x3x3";
        String[] dims = size.split("x");
        int w = dims.length > 0 ? Integer.parseInt(dims[0].trim()) : 3;
        int h = dims.length > 1 ? Integer.parseInt(dims[1].trim()) : 3;
        int d = dims.length > 2 ? Integer.parseInt(dims[2].trim()) : 3;

        Material material = parseMaterial(matObj != null ? matObj.toString() : "OAK_PLANKS");

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1) {
                        Material m = (y == 0) ? Material.OAK_LOG : material;
                        buildPlan.add(new BuildBlock(x, y, z, m));
                    }
                }
            }
        }

        for (BuildBlock bb : buildPlan) {
            needed.merge(bb.material, 1, Integer::sum);
        }
    }

    private Material parseMaterial(String s) {
        try {
            return Material.valueOf(s.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            try {
                return Material.valueOf(s.toUpperCase(Locale.ENGLISH) + "_PLANKS");
            } catch (IllegalArgumentException e2) {
                return Material.OAK_PLANKS;
            }
        }
    }

    @Override
    public void tick(AiWorkerTrait trait) {
        if (trait.getNPC().getEntity() == null) return;

        switch (phase) {
            case GATHERING -> gatherResources(trait);
            case BUILDING -> placeBlocks(trait);
            case DONE -> trait.setTask(new IdleTask());
        }
    }

    private void gatherResources(AiWorkerTrait trait) {
        boolean allDone = true;
        for (Map.Entry<Material, Integer> entry : needed.entrySet()) {
            int have = countInInventory(trait, entry.getKey());
            if (have < entry.getValue()) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            phase = Phase.BUILDING;
            return;
        }

        if (trait.getNPC().getNavigator().isNavigating()) return;

        Location npcLoc = trait.getNPC().getEntity().getLocation();

        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    Block block = npcLoc.getWorld().getBlockAt(
                        npcLoc.getBlockX() + x, npcLoc.getBlockY() + y, npcLoc.getBlockZ() + z);
                    Material type = block.getType();

                    if (needed.containsKey(type) && countInInventory(trait, type) < needed.get(type)) {
                        if (SpawnUtil.isWithinSpawnRadius(block.getLocation(),
                                trait.getHome() != null ? trait.getHome() : npcLoc)) {
                            continue;
                        }

                        Collection<ItemStack> drops = block.getDrops(
                            new ItemStack(detectTool(type)));
                        for (ItemStack drop : drops) trait.addItem(drop);
                        block.setType(Material.AIR);

                        trait.remember("ore:" + type.name().toLowerCase(),
                            block.getLocation().clone());
                        return;
                    }
                }
            }
        }

        searchStep++;
        double angle = (searchStep * 0.8);
        int dist = 15 + (searchStep % 4) * 10;
        Location target = npcLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        if (!SpawnUtil.isWithinSpawnRadius(target,
                trait.getHome() != null ? trait.getHome() : npcLoc)) {
            target.setY(npcLoc.getWorld().getHighestBlockYAt(target));
            trait.getNPC().getNavigator().setTarget(target);
            trait.getNPC().getNavigator().getLocalParameters()
                .speedModifier(1.0f).range(1f);
        }
    }

    private void placeBlocks(AiWorkerTrait trait) {
        if (buildIndex >= buildPlan.size()) {
            phase = Phase.DONE;

            Location buildSite = trait.getNPC().getStoredLocation().clone();
            trait.remember("build_site", buildSite);
            return;
        }

        if (!trait.getNPC().getNavigator().isNavigating()) {
            BuildBlock bb = buildPlan.get(buildIndex);
            Location target = trait.getNPC().getStoredLocation().clone()
                .add(bb.relX, bb.relY, bb.relZ);

            ItemStack mat = trait.takeItem(bb.material);
            if (mat == null) {
                phase = Phase.GATHERING;
                return;
            }

            trait.getNPC().getNavigator().setTarget(target.add(0.5, 0, 0.5));
            trait.getNPC().getNavigator().getLocalParameters()
                .speedModifier(1.0f).range(1.5f);

            target.getBlock().setType(bb.material);
            buildIndex++;
        }
    }

    private Material detectTool(Material ore) {
        String name = ore.name();
        if (name.contains("DIAMOND") || name.contains("EMERALD")
                || name.contains("ANCIENT") || name.contains("OBSIDIAN")) {
            return Material.IRON_PICKAXE;
        }
        if (name.contains("IRON") || name.contains("GOLD") || name.contains("COPPER")
                || name.contains("REDSTONE") || name.contains("LAPIS")) {
            return Material.STONE_PICKAXE;
        }
        return Material.WOODEN_PICKAXE;
    }

    private int countInInventory(AiWorkerTrait trait, Material mat) {
        return (int) trait.getItems().stream()
            .filter(i -> i.getType() == mat)
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    @Override
    public void stop(AiWorkerTrait trait) {
        trait.getNPC().getNavigator().cancelNavigation();
    }

    @Override
    public int tickInterval() { return 10; }

    @Override
    public String status() {
        return phase == Phase.GATHERING ? "Gathering resources..." : "Building...";
    }

    private record BuildBlock(int relX, int relY, int relZ, Material material) {}
}
