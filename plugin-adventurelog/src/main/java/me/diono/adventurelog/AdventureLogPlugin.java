package me.diono.adventurelog;

import java.util.*;

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

    private static final long COOLDOWN_MS = 5000;

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

    private final Map<UUID, Map<String, BundleEntry>> bundles = new HashMap<>();
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
        String mobName = formatName(event.getEntityType().name());
        String color = isHostile(event.getEntityType()) ? "&c" : "&e";
        handleBundled(killer, key,
            "killed a " + color + mobName,
            "killed %dx " + color + mobName);
    }

    // ── Ore ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!ORES.contains(type)) return;

        String key = "ore:" + type.name();
        String name = formatName(type.name());
        handleBundled(event.getPlayer(), key,
            "found &b" + name,
            "found %dx &b" + name);
    }

    // ── Harvest ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (HARVEST_BLOCKS.contains(type)) {
            String key = "harvest:" + type.name();
            String name = formatName(type.name());
            handleBundled(player, key,
                "harvested &a" + name,
                "harvested %dx &a" + name);
            return;
        }

        Integer maxAge = AGEABLE_CROPS.get(type);
        if (maxAge != null && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < maxAge) return;
            String key = "harvest:" + type.name();
            String name = formatName(type.name());
            handleBundled(player, key,
                "harvested &a" + name,
                "harvested %dx &a" + name);
        }
    }

    // ── Breed ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        LivingEntity breeder = event.getBreeder();
        if (!(breeder instanceof Player player)) return;

        String key = "breed:" + event.getEntity().getType().name();
        String mobName = formatName(event.getEntity().getType().name());
        handleBundled(player, key,
            "bred a &d" + mobName,
            "bred %dx &d" + mobName);
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

        String name = advancementToName(key);
        send(event.getPlayer(), "discovered &d" + name);
    }

    // ── Bundling Engine ─────────────────────────────────────────

    private static class BundleEntry {
        int count;
        long firstSeen;
        long lastSeen;

        BundleEntry(int count, long ts) {
            this.count = count;
            this.firstSeen = ts;
            this.lastSeen = ts;
        }
    }

    private void handleBundled(Player player, String key, String singleMsg, String multiMsg) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Map<String, BundleEntry> map = bundles.computeIfAbsent(uuid, k -> new HashMap<>());

        flushExpired(player, map, key, now);

        BundleEntry entry = map.get(key);
        if (entry == null) {
            map.put(key, new BundleEntry(1, now));
            send(player, singleMsg);
            return;
        }

        long elapsed = now - entry.firstSeen;
        if (elapsed > COOLDOWN_MS) {
            if (entry.count > 1) {
                send(player, multiMsg.replace("%d", String.valueOf(entry.count)));
            }
            entry.count = 1;
            entry.firstSeen = now;
            entry.lastSeen = now;
            send(player, singleMsg);
            return;
        }

        entry.count++;
        entry.lastSeen = now;
    }

    private void flushExpired(Player player, Map<String, BundleEntry> map, String currentKey, long now) {
        Iterator<Map.Entry<String, BundleEntry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BundleEntry> e = it.next();
            if (e.getKey().equals(currentKey)) continue;
            BundleEntry be = e.getValue();
            if (now - be.lastSeen > COOLDOWN_MS) {
                it.remove();
            }
        }
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
