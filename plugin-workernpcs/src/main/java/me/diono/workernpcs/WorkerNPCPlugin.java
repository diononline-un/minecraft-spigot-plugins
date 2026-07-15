package me.diono.workernpcs;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.diono.workernpcs.command.WorkerCommand;
import me.diono.workernpcs.trait.AiWorkerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;

public class WorkerNPCPlugin extends JavaPlugin {

    private static WorkerNPCPlugin instance;
    private String apiKey;
    private String model;
    private String defaultPersonality;
    private int maxNpcs;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reload();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getCommand("worker").setExecutor(new WorkerCommand(this));

        Plugin citizens = getServer().getPluginManager().getPlugin("Citizens");
        if (citizens != null && citizens.isEnabled()) {
            registerTrait();
        } else {
            getLogger().warning("Citizens not found! Waiting for CitizensEnableEvent...");
        }
    }

    @Override
    public void onDisable() {
        AiWorkerTrait.shutdownAll();
    }

    public static WorkerNPCPlugin getInstance() {
        return instance;
    }

    public void reload() {
        reloadConfig();
        apiKey = getConfig().getString("deepseek-api-key", "");
        model = getConfig().getString("deepseek-model", "deepseek-reasoner");
        maxNpcs = getConfig().getInt("max-npcs", 3);
        defaultPersonality = getConfig().getString("default-personality",
            "You are a helpful Minecraft worker. You take orders from the player. Be concise and direct.");
    }

    private void registerTrait() {
        CitizensAPI.getTraitFactory().registerTrait(
            TraitInfo.create(AiWorkerTrait.class).withName("worker"));
        getLogger().info("Registered AiWorkerTrait with Citizens2");

        // Re-attach trait to existing worker NPCs
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getTraitNullable(AiWorkerTrait.class) != null) {
                AiWorkerTrait trait = npc.getOrAddTrait(AiWorkerTrait.class);
                trait.onServerStart();
            }
        }
    }

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public int getMaxNpcs() { return maxNpcs; }
    public String getDefaultPersonality() { return defaultPersonality; }

    public boolean canCreateMore() {
        int count = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(AiWorkerTrait.class)) count++;
        }
        return count < maxNpcs;
    }

    public NPC createWorker(String name) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.getOrAddTrait(AiWorkerTrait.class);
        return npc;
    }
}
