package me.diono.example;

import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("ExamplePlugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ExamplePlugin disabled!");
    }
}
