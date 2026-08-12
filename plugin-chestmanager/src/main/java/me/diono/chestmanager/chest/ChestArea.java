package me.diono.chestmanager.chest;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.diono.chestmanager.config.CategoryConfig;

public class ChestArea {

    private Location home;
    private int radius;
    private final CategoryConfig categoryConfig;

    private Location inputChest;
    private Location unsortableChest;
    private Location requestChest;
    private final Map<String, List<Location>> categoryChests = new LinkedHashMap<>();
    private long lastScanTime;

    public ChestArea(CategoryConfig categoryConfig) {
        this.categoryConfig = categoryConfig;
    }

    public void setHome(Location home) {
        this.home = home.clone();
    }

    public Location getHome() {
        return home;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getRadius() {
        return radius;
    }

    public long getLastScanTime() {
        return lastScanTime;
    }

    public void scan() {
        if (home == null || home.getWorld() == null) return;

        inputChest = null;
        unsortableChest = null;
        requestChest = null;
        categoryChests.clear();

        World world = home.getWorld();
        int minX = home.getBlockX() - radius;
        int maxX = home.getBlockX() + radius;
        int minZ = home.getBlockZ() - radius;
        int maxZ = home.getBlockZ() + radius;
        int startY = home.getBlockY();
        int endY = world.getMinHeight();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = startY; y >= endY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();

                    if (type != Material.CHEST && type != Material.TRAPPED_CHEST) continue;
                    if (isDoubleChestRightSide(block)) continue;

                    String role = readChestSign(block);
                    if (role == null) continue;

                    Location loc = block.getLocation().clone();
                    switch (role) {
                        case "input" -> inputChest = loc;
                        case "unsortable" -> unsortableChest = loc;
                        case "request" -> requestChest = loc;
                        default -> {
                            categoryChests.computeIfAbsent(role, k -> new ArrayList<>()).add(loc);
                        }
                    }
                }
            }
        }

        lastScanTime = System.currentTimeMillis();
    }

    @SuppressWarnings("deprecation")
    private String readChestSign(Block chestBlock) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

        for (BlockFace face : faces) {
            Block adj = chestBlock.getRelative(face);
            if (adj.getState() instanceof Sign sign) {
                String role = parseSpecialRole(sign);
                if (role != null) return role;
            }
        }

        for (BlockFace face : faces) {
            Block neighbor = chestBlock.getRelative(face);
            for (BlockFace signFace : faces) {
                Block signBlock = neighbor.getRelative(signFace);
                if (signBlock.getState() instanceof Sign sign) {
                    String role = parseCategoryRole(sign);
                    if (role != null) return role;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    private String parseSpecialRole(Sign sign) {
        String line = sign.getLine(0);
        if (line == null || line.isEmpty()) return null;

        String role = line.trim().replaceAll("\\[|\\]", "");
        if (role.equalsIgnoreCase("input")) return "input";
        if (role.equalsIgnoreCase("unsortable")
                || role.equalsIgnoreCase("unsortet")
                || role.equalsIgnoreCase("sonstige")) return "unsortable";
        if (role.equalsIgnoreCase("request")) return "request";
        return null;
    }

    @SuppressWarnings("deprecation")
    private String parseCategoryRole(Sign sign) {
        String line = sign.getLine(0);
        if (line == null || line.isEmpty()) return null;

        String role = line.trim().replaceAll("\\[|\\]", "");
        for (String catKey : categoryConfig.getCategories()) {
            if (role.equalsIgnoreCase(catKey)) return catKey;
        }
        return null;
    }

    private boolean isDoubleChestRightSide(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
            return chestData.getType() == org.bukkit.block.data.type.Chest.Type.RIGHT;
        }
        return false;
    }

    public Location getInputChest() {
        return inputChest;
    }

    public Location getUnsortableChest() {
        return unsortableChest;
    }

    public Location getRequestChest() {
        return requestChest;
    }

    public List<Location> getCategoryChests(String categoryKey) {
        return categoryChests.getOrDefault(categoryKey, Collections.emptyList());
    }

    public Map<String, List<Location>> getAllCategoryChests() {
        return Collections.unmodifiableMap(categoryChests);
    }

    public boolean hasRequiredChests() {
        return inputChest != null;
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Input: ").append(inputChest != null ? "found" : "missing").append("\n");
        sb.append("Request: ").append(requestChest != null ? "found" : "missing").append("\n");
        sb.append("Unsortable: ").append(unsortableChest != null ? "found" : "missing").append("\n");
        sb.append("Categories:\n");
        for (Map.Entry<String, List<Location>> entry : categoryChests.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" chest(s)\n");
        }
        return sb.toString().trim();
    }

    public Inventory getChestInventory(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        if (loc.getBlock().getState() instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        return null;
    }

    public Map<Material, Integer> getItemCountsByCategory(String categoryKey) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        List<Location> chests = categoryChests.get(categoryKey);
        if (chests == null) return counts;

        for (Location loc : chests) {
            Inventory inv = getChestInventory(loc);
            if (inv == null) continue;
            for (ItemStack stack : inv.getContents()) {
                if (stack == null || stack.getType() == Material.AIR) continue;
                Material mat = stack.getType();
                counts.merge(mat, stack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    public int getTotalItemsForCategory(String categoryKey) {
        Map<Material, Integer> counts = getItemCountsByCategory(categoryKey);
        int total = 0;
        for (int amount : counts.values()) {
            total += amount;
        }
        return total;
    }

    public boolean withdrawFromCategory(String categoryKey, Material material, int amount) {
        List<Location> chests = categoryChests.get(categoryKey);
        if (chests == null) return false;

        int remaining = amount;
        for (Location loc : chests) {
            Inventory inv = getChestInventory(loc);
            if (inv == null) continue;

            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() != material) continue;

                int take = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) {
                    inv.setItem(slot, null);
                }
                remaining -= take;
                if (remaining <= 0) return true;
            }
        }
        return remaining < amount;
    }
}
