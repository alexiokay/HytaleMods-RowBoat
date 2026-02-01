package com.alexispace.hyvehicles;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;

/**
 * HytaleVehicles - Adds rideable vehicles to Hytale
 *
 * Features planned:
 * - Rideable animals (enhanced)
 * - Simple boats
 * - Motorcycles
 * - Cars
 * - Animal stables/homes
 */
public class HytaleVehiclesPlugin extends JavaPlugin {

    private static HytaleVehiclesPlugin instance;

    public HytaleVehiclesPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public void setup() {
        getLogger().at(Level.INFO).log("HytaleVehicles is setting up...");

        // TODO: Register vehicle types
        // TODO: Register commands
        // TODO: Load configuration
    }

    @Override
    public void start() {
        getLogger().at(Level.INFO).log("HytaleVehicles has started!");
        getLogger().at(Level.INFO).log("Version: " + getManifest().getVersion());
    }

    @Override
    public void shutdown() {
        getLogger().at(Level.INFO).log("HytaleVehicles is shutting down...");

        // TODO: Save data
        // TODO: Cleanup
    }

    public static HytaleVehiclesPlugin getInstance() {
        return instance;
    }
}
