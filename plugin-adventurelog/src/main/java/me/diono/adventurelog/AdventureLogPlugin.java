package me.diono.adventurelog;

import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AdventureLogPlugin extends JavaPlugin implements Listener {

    private static final long COOLDOWN_MS = 180_000;

    private static final Set<Material> ORES = EnumSet.of(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS
    );

    private static final Map<Material, Integer> AGEABLE_CROPS = Map.ofEntries(
        Map.entry(Material.WHEAT, 7),
        Map.entry(Material.CARROTS, 7),
        Map.entry(Material.POTATOES, 7),
        Map.entry(Material.BEETROOTS, 3),
        Map.entry(Material.NETHER_WART, 3),
        Map.entry(Material.COCOA, 2),
        Map.entry(Material.SWEET_BERRY_BUSH, 3)
    );

    private static final Set<Material> HARVEST_BLOCKS = EnumSet.of(
        Material.MELON, Material.PUMPKIN,
        Material.SUGAR_CANE, Material.BAMBOO, Material.CACTUS
    );

    private static final Set<String> STRUCTURE_ADVANCEMENTS = Set.of(
        "adventure/adventuring_time",
        "adventure/voluntary_exile",
        "adventure/totem_of_undying",
        "adventure/hero_of_the_village",
        "story/follow_ender_eye",
        "story/enter_the_nether",
        "story/enter_the_end",
        "story/cure_zombie_villager",
        "nether/find_fortress",
        "nether/find_bastion",
        "nether/explore_nether",
        "nether/ride_strider",
        "end/enter_end_gateway",
        "end/find_end_city",
        "end/levitate",
        "end/elytra",
        "end/dragon_egg"
    );

    private final Map<UUID, String> lastKey = new HashMap<>();
    private final Map<UUID, Long> lastTime = new HashMap<>();
    private final Map<UUID, String> lastBiome = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AdventureLog enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AdventureLog disabled!");
    }

    // ── Kill ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        String key = "kill:" + event.getEntityType().name();
        if (!shouldAnnounce(killer, key)) return;

        String mobName = formatName(event.getEntityType().name());
        String color = isHostile(event.getEntityType()) ? "&c" : "&e";
        send(killer, "killed a " + color + mobName);
    }

    // ── Ore ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!ORES.contains(type)) return;

        String key = "ore:" + type.name();
        if (!shouldAnnounce(event.getPlayer(), key)) return;

        send(event.getPlayer(), "found &b" + formatName(type.name()));
    }

    // ── Harvest ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (HARVEST_BLOCKS.contains(type)) {
            String key = "harvest:" + type.name();
            if (!shouldAnnounce(player, key)) return;
            send(player, "harvested &a" + formatName(type.name()));
            return;
        }

        Integer maxAge = AGEABLE_CROPS.get(type);
        if (maxAge != null && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < maxAge) return;
            String key = "harvest:" + type.name();
            if (!shouldAnnounce(player, key)) return;
            send(player, "harvested &a" + formatName(type.name()));
        }
    }

    // ── Breed ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        LivingEntity breeder = event.getBreeder();
        if (!(breeder instanceof Player player)) return;

        String key = "breed:" + event.getEntity().getType().name();
        if (!shouldAnnounce(player, key)) return;

        send(player, "bred a &d" + formatName(event.getEntity().getType().name()));
    }

    // ── Biome ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onBiomeChange(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Biome biome = event.getTo().getBlock().getBiome();
        String name = formatName(biome.getKey().getKey());
        UUID uuid = player.getUniqueId();

        String prev = lastBiome.get(uuid);
        if (!name.equals(prev)) {
            lastBiome.put(uuid, name);
            send(player, "entered the &a" + name);
        }
    }

    // ── Structure Discovery ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().getKey();
        if (!STRUCTURE_ADVANCEMENTS.contains(key)) return;

        send(event.getPlayer(), "discovered &d" + advancementToName(key));
    }

    // ── Cooldown Engine ─────────────────────────────────────────

    private boolean shouldAnnounce(Player player, String key) {
        UUID uuid = player.getUniqueId();
        String current = lastKey.get(uuid);
        Long last = lastTime.get(uuid);
        long now = System.currentTimeMillis();

        if (current == null) {
            lastKey.put(uuid, key);
            lastTime.put(uuid, now);
            return true;
        }

        if (!current.equals(key)) {
            lastKey.put(uuid, key);
            lastTime.put(uuid, now);
            return true;
        }

        if (now - last > COOLDOWN_MS) {
            lastTime.put(uuid, now);
            return true;
        }

        return false;
    }

    // ── Messaging ───────────────────────────────────────────────

    private void send(Player player, String msg) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7[&6!&7] &f" + player.getName() + " &7" + msg));
    }

    // ── Formatting ──────────────────────────────────────────────

    private static String formatName(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ENGLISH).split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static boolean isHostile(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, WITCH, SLIME, MAGMA_CUBE,
                 GHAST, BLAZE, WITHER_SKELETON, WITHER, ENDER_DRAGON, EVOKER, VINDICATOR,
                 PILLAGER, RAVAGER, VEX, HOGLIN, ZOGLIN, PIGLIN_BRUTE, GUARDIAN,
                 ELDER_GUARDIAN, SILVERFISH, ENDERMITE, PHANTOM, SHULKER, HUSK, STRAY,
                 DROWNED, ILLUSIONER, WARDEN, BREEZE, BOGGED -> true;
            default -> false;
        };
    }

    private static String advancementToName(String key) {
        String path = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        return formatName(path);
    }
}
