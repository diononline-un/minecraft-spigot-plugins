package me.diono.chestmanager.bukkit;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.port.NavigationPort;
import net.citizensnpcs.api.npc.NPC;

/**
 * Fortbewegung über den Citizens-Navigator. Der Kern behandelt Navigation als
 * Auftrag mit ungewissem Ausgang und prüft die Ankunft selbst über die Position.
 */
public final class CitizensNavigationPort implements NavigationPort {

    private final NPC npc;
    private final World world;

    public CitizensNavigationPort(NPC npc, World world) {
        this.npc = npc;
        this.world = world;
    }

    @Override
    public void navigateTo(BlockPos target) {
        npc.getNavigator().setTarget(toLocation(target));
    }

    @Override
    public void cancel() {
        npc.getNavigator().cancelNavigation();
    }

    @Override
    public boolean isNavigating() {
        return npc.getNavigator().isNavigating();
    }

    @Override
    public Optional<BlockPos> position() {
        Entity entity = npc.getEntity();
        if (entity == null) {
            return Optional.empty();
        }
        Location location = entity.getLocation();
        return Optional.of(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public boolean isAvailable() {
        return npc.isSpawned() && npc.getEntity() != null;
    }

    private Location toLocation(BlockPos pos) {
        return new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }
}
