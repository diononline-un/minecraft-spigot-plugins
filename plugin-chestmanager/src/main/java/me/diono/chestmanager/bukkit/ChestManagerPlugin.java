package me.diono.chestmanager.bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import me.diono.chestmanager.bukkit.command.ChestManagerCommand;
import me.diono.chestmanager.core.CategoryTable;
import me.diono.chestmanager.core.Sorter;
import me.diono.chestmanager.core.SorterSettings;
import me.diono.chestmanager.core.port.Clock;
import me.diono.chestmanager.core.port.LogPort;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;

/**
 * Einstiegspunkt des Plugins. Hält Kategorien und Einstellungen, treibt alle Sorter
 * über einen einzigen Scheduler und verwaltet die Chunk-Tickets der Bereiche.
 */
public class ChestManagerPlugin extends JavaPlugin {

    private static final String EXAMPLE_RESOURCE = "config.yml.example";

    private static ChestManagerPlugin instance;

    private CategoryTable categories = CategoryTable.empty();
    private SorterSettings settings = SorterSettings.defaults();
    private LogPort logPort;
    private final Clock clock = System::currentTimeMillis;

    private final List<ChestManagerTrait> traits = new ArrayList<>();
    private final Map<UUID, Set<Long>> chunkTickets = new HashMap<>();

    private BukkitTask schedulerTask;
    private int tickPeriodTicks = 5;
    private int opsPerTick = 8;

    public static ChestManagerPlugin getInstance() {
        return instance;
    }

    // --- Lebenszyklus -----------------------------------------------------

    @Override
    public void onEnable() {
        instance = this;
        logPort = new PluginLog(getLogger());

        ensureConfigFile();
        reloadConfiguration();

        ChestManagerCommand command = new ChestManagerCommand(this);
        if (getCommand("chestmanager") != null) {
            getCommand("chestmanager").setExecutor(command);
            getCommand("chestmanager").setTabCompleter(command);
        }

        Plugin citizens = getServer().getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) {
            getLogger().severe("Citizens fehlt. ChestManager wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CitizensAPI.getTraitFactory().registerTrait(
                TraitInfo.create(ChestManagerTrait.class).withName("chestmanager"));

        // Bereits geladene NPCs übernehmen.
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(ChestManagerTrait.class)) {
                ChestManagerTrait trait = npc.getTraitNullable(ChestManagerTrait.class);
                if (trait != null) {
                    registerTrait(trait);
                }
            }
        }

        startScheduler();
        getLogger().info("ChestManager aktiv mit " + categories.categoryKeys().size()
                + " Kategorien und " + categories.knownItemCount() + " zugeordneten Item-Typen.");
    }

    @Override
    public void onDisable() {
        stopScheduler();
        for (ChestManagerTrait trait : List.copyOf(traits)) {
            Sorter sorter = trait.sorter();
            if (sorter != null) {
                sorter.stop();
                // Getragene Items zurückgeben, solange die Welt noch verfügbar ist.
                int rounds = 0;
                while (!trait.carry().isCarryEmpty() && rounds++ < 40) {
                    sorter.tick(4);
                }
            }
            releaseChunks(trait);
        }
        traits.clear();
        chunkTickets.clear();
        instance = null;
    }

    // --- Konfiguration ----------------------------------------------------

    private void ensureConfigFile() {
        File folder = getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            getLogger().warning("Datenordner " + folder + " liess sich nicht anlegen");
            return;
        }
        File target = new File(folder, "config.yml");
        saveResource(EXAMPLE_RESOURCE, true);
        if (target.isFile()) {
            return;
        }
        File example = new File(folder, EXAMPLE_RESOURCE);
        try {
            Files.copy(example.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("config.yml aus der Vorlage angelegt.");
        } catch (IOException failure) {
            getLogger().log(Level.SEVERE, "config.yml liess sich nicht anlegen", failure);
        }
    }

    /** Liest die Konfiguration neu und meldet jeden fehlerhaften Eintrag. */
    public List<String> reloadConfiguration() {
        reloadConfig();
        PluginConfig.Loaded loaded = PluginConfig.load(this);
        this.categories = loaded.categories();
        this.settings = loaded.settings();
        this.tickPeriodTicks = PluginConfig.tickPeriodTicks(this);
        this.opsPerTick = settings.opsPerTick();

        for (String problem : loaded.problems()) {
            getLogger().warning("Konfiguration: " + problem);
        }
        for (ChestManagerTrait trait : traits) {
            if (trait.sorter() != null) {
                trait.sorter().applyCategories(categories);
            }
        }
        return loaded.problems();
    }

    public CategoryTable categories() {
        return categories;
    }

    public SorterSettings settings() {
        return settings;
    }

    public LogPort logPort() {
        return logPort;
    }

    public Clock clock() {
        return clock;
    }

    public File journalFile(UUID npcId) {
        return new File(new File(getDataFolder(), "journal"), npcId + ".yml");
    }

    // --- Sorter-Verwaltung ------------------------------------------------

    /** Nimmt einen Trait in die Verwaltung auf. Mehrfachaufrufe sind unschädlich. */
    public void registerTrait(ChestManagerTrait trait) {
        if (!traits.contains(trait)) {
            traits.add(trait);
        }
    }

    public void unregisterTrait(ChestManagerTrait trait) {
        releaseChunks(trait);
        traits.remove(trait);
    }

    /** Meldet einen fertig aufgebauten Sorter an; prüft auf Bereichskonflikte. */
    public void attachSorter(ChestManagerTrait trait, Sorter sorter) {
        registerTrait(trait);
        refreshChunkTickets(trait);

        for (ChestManagerTrait other : traits) {
            if (other == trait || other.sorter() == null) {
                continue;
            }
            Sorter otherSorter = other.sorter();
            if (sorter.area().hasAnchor() && otherSorter.area().overlaps(sorter.area())) {
                getLogger().warning("Sorter " + sorter.name() + " und " + otherSorter.name()
                        + " beanspruchen dieselben Truhen: "
                        + otherSorter.area().sharedWith(sorter.area())
                        + ". Bitte die Bereiche trennen.");
            }
        }
    }

    public List<ChestManagerTrait> traits() {
        return List.copyOf(traits);
    }

    public ChestManagerTrait findTrait(String name) {
        for (ChestManagerTrait trait : traits) {
            NPC npc = trait.getNPC();
            if (npc != null && name.equalsIgnoreCase(npc.getName())) {
                return trait;
            }
        }
        return null;
    }

    // --- Chunks -----------------------------------------------------------

    /**
     * Hält die Chunks geladen, die der Sorter braucht.
     *
     * <p>Solange kein Bereich ermittelt ist, sind das die Chunks des Suchquaders.
     * Ohne sie fände der Sorter nach einem Serverneustart ohne Spieler in der Nähe
     * nie eine Truhe: Chunks sind dann entladen, der Scan sieht nichts, und es gibt
     * nichts, wofür ein Ticket gesetzt würde. Nach erfolgreicher Ermittlung schrumpft
     * die Menge auf die Chunks der tatsächlichen Bereichstruhen.
     */
    public void refreshChunkTickets(ChestManagerTrait trait) {
        Sorter sorter = trait.sorter();
        if (sorter == null || trait.home() == null || trait.home().getWorld() == null
                || trait.getNPC() == null) {
            return;
        }
        World world = trait.home().getWorld();
        Set<Long> wanted = new HashSet<>();

        if (sorter.area().hasAnchor()) {
            for (me.diono.chestmanager.core.BlockPos pos : sorter.area().positions()) {
                if (wanted.size() >= settings.maxChunkTickets()) {
                    break;
                }
                wanted.add(chunkKey(pos.x() >> 4, pos.z() >> 4));
            }
        } else {
            int radius = settings.searchRadius();
            int centreX = trait.home().getBlockX() >> 4;
            int centreZ = trait.home().getBlockZ() >> 4;
            int chunkRadius = (radius >> 4) + 1;
            for (int cx = centreX - chunkRadius; cx <= centreX + chunkRadius; cx++) {
                for (int cz = centreZ - chunkRadius; cz <= centreZ + chunkRadius; cz++) {
                    if (wanted.size() >= settings.maxChunkTickets()) {
                        break;
                    }
                    if (world.isChunkGenerated(cx, cz)) {
                        wanted.add(chunkKey(cx, cz));
                    }
                }
            }
        }

        Set<Long> previous = chunkTickets.getOrDefault(trait.getNPC().getUniqueId(), Set.of());
        if (previous.equals(wanted)) {
            return;
        }
        for (Long key : previous) {
            if (!wanted.contains(key)) {
                world.removePluginChunkTicket(unpackX(key), unpackZ(key), this);
            }
        }
        for (Long key : wanted) {
            if (!previous.contains(key)) {
                world.addPluginChunkTicket(unpackX(key), unpackZ(key), this);
            }
        }
        chunkTickets.put(trait.getNPC().getUniqueId(), wanted);
    }

    public void releaseChunks(ChestManagerTrait trait) {
        if (trait.getNPC() == null || trait.home() == null || trait.home().getWorld() == null) {
            return;
        }
        Set<Long> held = chunkTickets.remove(trait.getNPC().getUniqueId());
        if (held == null) {
            return;
        }
        World world = trait.home().getWorld();
        for (Long key : held) {
            world.removePluginChunkTicket(unpackX(key), unpackZ(key), this);
        }
    }

    public int heldTicketCount() {
        return chunkTickets.values().stream().mapToInt(Set::size).sum();
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }

    // --- Scheduler --------------------------------------------------------

    private void startScheduler() {
        stopScheduler();
        schedulerTask = getServer().getScheduler().runTaskTimer(this, this::tickAll,
                tickPeriodTicks, tickPeriodTicks);
    }

    private void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        for (ChestManagerTrait trait : List.copyOf(traits)) {
            Sorter sorter = trait.sorter();
            if (sorter == null) {
                // Der NPC ist möglicherweise erst jetzt gespawnt.
                trait.start();
                continue;
            }
            // Chunk-Tickets nur gelegentlich neu berechnen: Die Menge ändert sich
            // erst nach einer Bereichsermittlung.
            if (now - trait.lastTicketRefresh() > 5_000L) {
                trait.markTicketRefresh(now);
                refreshChunkTickets(trait);
            }
            try {
                sorter.tick(opsPerTick);
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Sorter " + sorter.name()
                        + " ist in einen Fehler gelaufen und wird angehalten", failure);
                sorter.stop();
            }
        }
    }

    /** Setzt den Takt neu, nachdem die Konfiguration geändert wurde. */
    public void restartScheduler() {
        startScheduler();
    }

    /** Log-Ausgabe an den Plugin-Logger. */
    private record PluginLog(java.util.logging.Logger logger) implements LogPort {

        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warn(String message) {
            logger.warning(message);
        }

        @Override
        public void debug(String message) {
            logger.fine(message);
        }
    }
}
