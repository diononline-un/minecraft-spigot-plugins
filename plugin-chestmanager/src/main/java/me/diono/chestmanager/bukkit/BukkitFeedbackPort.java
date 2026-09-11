package me.diono.chestmanager.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.FeedbackPort;
import net.citizensnpcs.api.npc.NPC;

/**
 * Sichtbare Rückmeldung des Sorters.
 *
 * <p>Alle Bewegungen sind stumm. Die Entität wird zusätzlich dauerhaft still
 * geschaltet, damit weder Laufschritte noch Villager-Laute zu hören sind.
 */
public final class BukkitFeedbackPort implements FeedbackPort {

    private final NPC npc;
    private final World world;

    public BukkitFeedbackPort(NPC npc, World world) {
        this.npc = npc;
        this.world = world;
    }

    @Override
    public void swing() {
        Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity living) {
            // Reine Animation; diese Methode erzeugt keinen Klang.
            living.swingMainHand();
        }
    }

    @Override
    public void lookAt(BlockPos target) {
        npc.faceLocation(new Location(world, target.x() + 0.5, target.y() + 0.5, target.z() + 0.5));
    }
}
