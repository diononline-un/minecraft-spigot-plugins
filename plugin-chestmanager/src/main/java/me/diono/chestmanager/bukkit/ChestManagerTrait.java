package me.diono.chestmanager.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import me.diono.chestmanager.core.CategoryTable;
import me.diono.chestmanager.core.Sorter;
import me.diono.chestmanager.core.SorterSettings;
import me.diono.chestmanager.core.port.LogPort;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

/**
 * Verbindet einen Citizens-NPC mit einem {@link Sorter}.
 *
 * <p>Der Trait speichert nur den Heimatpunkt und den Pausenzustand. Getragene Items
 * liegen bewusst nicht hier: Citizens sichert Traits periodisch, nicht atomar mit
 * einer Entnahme. Dafür gibt es das Journal.
 */
@TraitName("chestmanager")
public class ChestManagerTrait extends Trait {

    @Persist("home")
    private Location home;

    @Persist("paused")
    private boolean paused;

    private Sorter sorter;
    private BukkitCarryPort carry;
    private YamlJournal journal;
    private boolean registrationAttempted;
    private long lastTicketRefresh;

    public ChestManagerTrait() {
        super("chestmanager");
    }

    /** Zeitpunkt der letzten Neuberechnung der Chunk-Tickets. */
    public long lastTicketRefresh() {
        return lastTicketRefresh;
    }

    public void markTicketRefresh(long now) {
        this.lastTicketRefresh = now;
    }

    // --- Citizens-Lifecycle ----------------------------------------------

    @Override
    public void onAttach() {
        ChestManagerPlugin plugin = ChestManagerPlugin.getInstance();
        if (plugin != null) {
            plugin.registerTrait(this);
        }
    }

    @Override
    public void onSpawn() {
        if (npc != null && npc.getEntity() instanceof LivingEntity living) {
            living.setSilent(true);
        }
        // Die Welt ist beim Spawn verfügbar; jetzt können die Ports entstehen.
        if (sorter == null) {
            registrationAttempted = false;
            start();
        }
    }

    @Override
    public void onDespawn() {
        // Ein Despawn ist kein Anhalten durch den Betreiber. Der Sorter soll nach dem
        // nächsten Spawn weiterarbeiten, deshalb wird nur die Navigation beendet.
        if (sorter != null) {
            sorter.cancelNavigation();
        }
    }

    @Override
    public void onRemove() {
        if (sorter != null) {
            sorter.stop();
            // Getragene Items dürfen den NPC nicht verlassen.
            int rounds = 0;
            while (!carryIsEmpty() && rounds++ < 40) {
                sorter.tick(4);
            }
        }
        ChestManagerPlugin plugin = ChestManagerPlugin.getInstance();
        if (plugin != null) {
            plugin.unregisterTrait(this);
        }
    }

    // --- Aufbau -----------------------------------------------------------

    /**
     * Baut Sorter und Ports auf. Braucht eine geladene Welt und einen gespawnten NPC;
     * vorher kehrt die Methode ohne Wirkung zurück.
     */
    public void start() {
        ChestManagerPlugin plugin = ChestManagerPlugin.getInstance();
        if (plugin == null || npc == null || home == null || home.getWorld() == null) {
            return;
        }
        World world = home.getWorld();
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }

        CategoryTable categories = plugin.categories();
        SorterSettings settings = plugin.settings();
        LogPort log = plugin.logPort();

        BukkitWorldPort worldPort = new BukkitWorldPort(world);
        BukkitChestPort chestPort = new BukkitChestPort(world);
        this.carry = new BukkitCarryPort(world, chestPort);
        this.journal = new YamlJournal(plugin.journalFile(npc.getUniqueId()), plugin.getLogger());

        this.sorter = new Sorter(npc.getName(), toBlockPos(home), settings, categories,
                new Sorter.Ports(worldPort, chestPort, carry,
                        new CitizensNavigationPort(npc, world), journal,
                        plugin.clock(), log, new BukkitFeedbackPort(npc, world)));
        plugin.attachSorter(this, sorter);
        registrationAttempted = true;

        if (paused) {
            sorter.stop();
        } else {
            sorter.requestScan();
        }
    }

    /** Baut den Sorter neu auf, etwa nach einem Konfigurationswechsel. */
    public void restart() {
        if (sorter != null) {
            sorter.stop();
            releaseChunks();
        }
        sorter = null;
        start();
    }

    public boolean isReady() {
        return sorter != null;
    }

    private boolean carryIsEmpty() {
        return carry == null || carry.isCarryEmpty();
    }

    // --- Zugriff ----------------------------------------------------------

    public Sorter sorter() {
        return sorter;
    }

    public BukkitCarryPort carry() {
        return carry;
    }

    public YamlJournal journal() {
        return journal;
    }

    public Location home() {
        return home;
    }

    public void setHome(Location location) {
        this.home = location.clone();
        if (sorter != null) {
            sorter.setHome(toBlockPos(location));
            sorter.requestScan();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean value) {
        this.paused = value;
        if (sorter == null) {
            return;
        }
        if (value) {
            sorter.stop();
        } else {
            sorter.resume();
            sorter.requestScan();
        }
    }

    public void releaseChunks() {
        ChestManagerPlugin plugin = ChestManagerPlugin.getInstance();
        if (plugin != null) {
            plugin.releaseChunks(this);
        }
    }

    private static me.diono.chestmanager.core.BlockPos toBlockPos(Location location) {
        return new me.diono.chestmanager.core.BlockPos(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
