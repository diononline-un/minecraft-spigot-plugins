package me.diono.chestmanager;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.diono.chestmanager.command.ChestManagerCommand;
import me.diono.chestmanager.config.CategoryConfig;
import me.diono.chestmanager.listener.RequestChestListener;
import me.diono.chestmanager.trait.ChestManagerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;

public class ChestManagerPlugin extends JavaPlugin {

    private static ChestManagerPlugin instance;
    private CategoryConfig categoryConfig;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        categoryConfig = new CategoryConfig(this);
        categoryConfig.load();

        getCommand("chestmanager").setExecutor(new ChestManagerCommand(this));
        getServer().getPluginManager().registerEvents(new RequestChestListener(this), this);

        Plugin citizens = getServer().getPluginManager().getPlugin("Citizens");
        if (citizens != null && citizens.isEnabled()) {
            registerTrait();
        } else {
            getLogger().warning("Citizens not found! ChestManager requires Citizens2.");
        }
    }

    @Override
    public void onDisable() {
        ChestManagerTrait.shutdownAll();
    }

    public static ChestManagerPlugin getInstance() {
        return instance;
    }

    public CategoryConfig getCategoryConfig() {
        return categoryConfig;
    }

    private void registerTrait() {
        CitizensAPI.getTraitFactory().registerTrait(
                TraitInfo.create(ChestManagerTrait.class).withName("chestmanager"));
        getLogger().info("Registered ChestManagerTrait with Citizens2");

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getTraitNullable(ChestManagerTrait.class) != null) {
                ChestManagerTrait trait = npc.getOrAddTrait(ChestManagerTrait.class);
                trait.onServerStart();
            }
        }
    }
}
