package me.diono.workernpcs.trait;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.diono.workernpcs.WorkerNPCPlugin;
import me.diono.workernpcs.ai.DeepSeekClient;
import me.diono.workernpcs.task.*;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

@TraitName("worker")
public class AiWorkerTrait extends Trait {

    private final WorkerNPCPlugin plugin = WorkerNPCPlugin.getInstance();

    @Persist("home") Location home;
    @Persist("personality") String personality;
    @Persist("known_locations") String knownLocationsJson = "{}";

    private final List<ItemStack> items = new ArrayList<>(27);
    private final Map<String, Location> knownLocations = new LinkedHashMap<>();
    private WorkerTask currentTask;
    private Player followTarget;
    private DeepSeekClient aiClient;
    private int tickCounter;

    public AiWorkerTrait() {
        super("worker");
    }

    public void onServerStart() {
        aiClient = new DeepSeekClient(plugin.getApiKey(), plugin.getModel());
        if (personality == null || personality.isEmpty()) {
            personality = plugin.getDefaultPersonality();
        }
        deserializeKnownLocations();
        if (currentTask == null) {
            currentTask = new IdleTask();
        }
    }

    @Override
    public void onAttach() {
        aiClient = new DeepSeekClient(plugin.getApiKey(), plugin.getModel());
        if (personality == null || personality.isEmpty()) {
            personality = plugin.getDefaultPersonality();
        }
        deserializeKnownLocations();
        if (currentTask == null) {
            currentTask = new IdleTask();
        }
    }

    @Override
    public void onSpawn() {
        if (currentTask == null) {
            currentTask = new IdleTask();
        }
    }

    @Override
    public void onRemove() {
        if (currentTask != null) {
            currentTask.stop(this);
            currentTask = null;
        }
        shutdown();
    }

    @Override
    public void onDespawn() {
        if (currentTask != null) {
            currentTask.stop(this);
        }
    }

    @Override
    public void run() {
        tickCounter++;
        if (currentTask == null) return;
        if (tickCounter % currentTask.tickInterval() == 0) {
            currentTask.tick(this);
        }
    }

    // ── Chat ──────────────────────────────────────────────────

    public CompletableFuture<String> chat(Player player, String message) {
        if (aiClient == null) {
            return CompletableFuture.completedFuture("I'm not ready yet...");
        }

        String context = buildContext();
        return aiClient.chat(personality, context, player.getName(), message)
            .thenCompose(result -> {
                if (result.getTask() != null) {
                    setTaskFromAi(result.getTask(), result.getTaskParams(), player);
                }
                return CompletableFuture.completedFuture(result.getMessage());
            });
    }

    private String buildContext() {
        StringBuilder ctx = new StringBuilder();
        ctx.append("NPC name: ").append(npc.getName()).append("\n");
        ctx.append("Current task: ").append(currentTask != null ? currentTask.getClass().getSimpleName() : "idle").append("\n");
        if (home != null) ctx.append("Home: ").append(formatLoc(home)).append("\n");
        ctx.append("Inventory: ").append(items.isEmpty() ? "empty" : items.size() + " items").append("\n");
        if (followTarget != null) ctx.append("Following: ").append(followTarget.getName()).append("\n");
        ctx.append("Location: ").append(formatLoc(npc.getStoredLocation())).append("\n");
        if (!knownLocations.isEmpty()) {
            ctx.append("Known locations:\n");
            for (Map.Entry<String, Location> e : knownLocations.entrySet()) {
                ctx.append("  ").append(e.getKey()).append(": ").append(formatLoc(e.getValue())).append("\n");
            }
        }
        return ctx.toString();
    }

    // ── Task System ───────────────────────────────────────────

    public void setTask(WorkerTask task) {
        if (currentTask != null) {
            currentTask.stop(this);
        }
        currentTask = task;
        task.start(this);
    }

    public void setFollowTarget(Player player) {
        this.followTarget = player;
        if (player != null) {
            setTask(new FollowTask(player.getUniqueId()));
        }
    }

    public Location getHome() { return home; }
    public void setHome(Location loc) {
        this.home = loc;
    }

    // ── Memory ────────────────────────────────────────────────

    public void remember(String key, Location loc) {
        knownLocations.put(key.toLowerCase(), loc.clone());
    }

    public void forget(String key) {
        knownLocations.remove(key.toLowerCase());
    }

    public Location recall(String key) {
        return knownLocations.get(key.toLowerCase());
    }

    public Map<String, Location> getKnownLocations() {
        return Collections.unmodifiableMap(knownLocations);
    }

    private void deserializeKnownLocations() {
        knownLocations.clear();
        if (knownLocationsJson == null || knownLocationsJson.isEmpty()) return;
        String[] entries = knownLocationsJson.replace("{", "").replace("}", "").split(";");
        for (int i = 0; i < entries.length - 1; i += 2) {
            try {
                String key = entries[i].trim().replace("\"", "");
                String[] parts = entries[i + 1].trim().replace("\"", "").split(",");
                if (parts.length >= 4 && Bukkit.getWorld(parts[0]) != null) {
                    Location loc = new Location(
                        Bukkit.getWorld(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                    );
                    knownLocations.put(key, loc);
                }
            } catch (Exception ignored) {}
        }
    }

    public String serializeKnownLocations() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Location> entry : knownLocations.entrySet()) {
            sb.append(entry.getKey()).append(";");
            Location loc = entry.getValue();
            sb.append(loc.getWorld().getName()).append(",");
            sb.append(String.format("%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ()));
            sb.append(";");
        }
        return sb.toString();
    }

    public Player getFollowTarget() {
        return followTarget;
    }

    public WorkerTask getCurrentTask() {
        return currentTask;
    }

    private void setTaskFromAi(String taskName, Map<String, Object> params, Player player) {
        switch (taskName.toLowerCase()) {
            case "follow":
                if (player != null) setFollowTarget(player);
                break;
            case "farm":
                setTask(new FarmTask(this));
                break;
            case "breed":
                setTask(new BreedTask(this));
                break;
            case "gohome":
                if (home != null) setTask(new GoHomeTask(home));
                break;
            case "build":
                if (params != null && params.containsKey("structure")) {
                    setTask(new BuildTask(params, this));
                }
                break;
            case "idle":
                setTask(new IdleTask());
                break;
            default:
                npc.getNavigator().cancelNavigation();
        }
    }

    // ── Inventory ─────────────────────────────────────────────

    public List<ItemStack> getItems() { return items; }

    public boolean addItem(ItemStack stack) {
        if (items.size() < 27) {
            items.add(stack.clone());
            return true;
        }
        return false;
    }

    public void removeItem(ItemStack stack) {
        items.removeIf(i -> i.isSimilar(stack));
    }

    public boolean hasItem(Material mat) {
        return items.stream().anyMatch(i -> i.getType() == mat);
    }

    public ItemStack takeItem(Material mat) {
        Iterator<ItemStack> it = items.iterator();
        while (it.hasNext()) {
            ItemStack i = it.next();
            if (i.getType() == mat) {
                it.remove();
                return i;
            }
        }
        return null;
    }

    public Inventory createInventory() {
        Inventory inv = Bukkit.createInventory(null, 27, npc.getName() + "'s Inventory");
        for (int i = 0; i < items.size() && i < 27; i++) {
            inv.setItem(i, items.get(i).clone());
        }
        return inv;
    }

    public void saveInventoryFrom(Inventory inv) {
        items.clear();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(stack.clone());
            }
        }
    }

    // ── Events ────────────────────────────────────────────────

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (event.getNPC() != npc) return;
        Player player = event.getClicker();
        if (!player.isSneaking()) return;

        // Open inventory on shift+right-click
        Inventory inv = createInventory();
        player.openInventory(inv);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (followTarget == null || !event.getEntity().getUniqueId().equals(followTarget.getUniqueId())) {
            return;
        }

        Player player = event.getEntity();
        Location deathLoc = player.getLocation();

        // Collect dropped items
        CollectTask collect = new CollectTask(deathLoc, home);
        setTask(collect);
    }

    // ── Cleanup ───────────────────────────────────────────────

    public void shutdown() {
        if (currentTask != null) {
            currentTask.stop(this);
            currentTask = null;
        }
    }

    public static void shutdownAll() {
        // Called from onDisable
    }

    // ── Helpers ───────────────────────────────────────────────

    private static String formatLoc(Location loc) {
        if (loc == null) return "none";
        return String.format("%.0f %.0f %.0f in %s",
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}
