package me.diono.workernpcs.task;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import me.diono.workernpcs.trait.AiWorkerTrait;

public class BreedTask extends WorkerTask {

    private static final Map<EntityType, Material> BREED_FOOD = Map.ofEntries(
        Map.entry(EntityType.COW, Material.WHEAT),
        Map.entry(EntityType.SHEEP, Material.WHEAT),
        Map.entry(EntityType.PIG, Material.CARROT),
        Map.entry(EntityType.CHICKEN, Material.WHEAT_SEEDS),
        Map.entry(EntityType.WOLF, Material.BONE),
        Map.entry(EntityType.CAT, Material.COD),
        Map.entry(EntityType.HORSE, Material.GOLDEN_APPLE),
        Map.entry(EntityType.DONKEY, Material.GOLDEN_APPLE),
        Map.entry(EntityType.RABBIT, Material.CARROT),
        Map.entry(EntityType.TURTLE, Material.SEAGRASS),
        Map.entry(EntityType.LLAMA, Material.HAY_BLOCK),
        Map.entry(EntityType.FOX, Material.SWEET_BERRIES),
        Map.entry(EntityType.PANDA, Material.BAMBOO),
        Map.entry(EntityType.BEE, Material.POPPY),
        Map.entry(EntityType.HOGLIN, Material.CRIMSON_FUNGUS),
        Map.entry(EntityType.STRIDER, Material.WARPED_FUNGUS),
        Map.entry(EntityType.AXOLOTL, Material.TROPICAL_FISH_BUCKET),
        Map.entry(EntityType.GOAT, Material.WHEAT),
        Map.entry(EntityType.FROG, Material.SLIME_BALL),
        Map.entry(EntityType.SNIFFER, Material.TORCHFLOWER_SEEDS),
        Map.entry(EntityType.ARMADILLO, Material.SPIDER_EYE),
        Map.entry(EntityType.CAMEL, Material.CACTUS),
        Map.entry(EntityType.MOOSHROOM, Material.WHEAT),
        Map.entry(EntityType.OCELOT, Material.COD)
    );

    private static final int SCAN_RADIUS = 12;
    private int scanCooldown;

    public BreedTask(AiWorkerTrait trait) {}

    @Override
    public void tick(AiWorkerTrait trait) {
        if (trait.getNPC().getEntity() == null) return;

        if (scanCooldown <= 0) {
            tryBreedAnimals(trait);
            scanCooldown = 40;
        } else {
            scanCooldown--;
        }
    }

    private void tryBreedAnimals(AiWorkerTrait trait) {
        List<Location> pens = new ArrayList<>();
        for (Map.Entry<String, Location> entry : trait.getKnownLocations().entrySet()) {
            if (entry.getKey().startsWith("pen:")) {
                pens.add(entry.getValue());
            }
        }

        if (pens.isEmpty()) {
            pens.add(trait.getNPC().getStoredLocation());
        }

        for (Location center : pens) {
            if (center.getWorld() == null) continue;

            Map<EntityType, List<Animals>> byType = new HashMap<>();
            for (Entity entity : center.getWorld().getNearbyEntities(
                    center, SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
                if (!(entity instanceof Animals animal)) continue;
                if (!animal.isAdult()) continue;
                if (animal.getBreedCause() != null) continue;
                Material food = BREED_FOOD.get(entity.getType());
                if (food == null) continue;

                byType.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add(animal);
            }

            for (Map.Entry<EntityType, List<Animals>> entry : byType.entrySet()) {
                EntityType type = entry.getKey();
                List<Animals> list = entry.getValue();
                if (list.size() < 2) continue;

                Material food = BREED_FOOD.get(type);
                if (food == null || !trait.hasItem(food)) continue;

                ItemStack foodStack = trait.takeItem(food);
                if (foodStack == null) continue;

                Animals a1 = list.get(0);
                Animals a2 = list.get(1);

                UUID npcUuid = trait.getNPC().getEntity().getUniqueId();
                a1.setBreedCause(npcUuid);
                a2.setBreedCause(npcUuid);

                trait.remember("pen:" + type.name().toLowerCase(), center.clone());

                trait.getNPC().getNavigator().setTarget(a1.getLocation());
                trait.getNPC().getNavigator().getLocalParameters().range(1.5f);
                return;
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
    public String status() { return "Tending animals"; }
}
