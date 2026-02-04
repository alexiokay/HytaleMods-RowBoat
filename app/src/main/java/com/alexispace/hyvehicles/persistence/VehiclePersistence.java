package com.alexispace.hyvehicles.persistence;

import com.alexispace.hyvehicles.util.VehicleLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSON-based persistence for spawned vehicles.
 * Saves vehicle data to a JSON file when the server shuts down,
 * and loads it back when the server starts.
 *
 * @since 1.0
 * @author alexispace
 */
public class VehiclePersistence {

    private final VehicleLogger logger;
    private final Path saveFile;
    private final Gson gson;

    public VehiclePersistence(VehicleLogger logger, Path saveDirectory) {
        this.logger = logger;
        this.saveFile = saveDirectory.resolve("vehicles.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Ensure directory exists
        try {
            Files.createDirectories(saveDirectory);
        } catch (IOException e) {
            logger.warning("Could not create save directory: " + e.getMessage());
        }
    }

    /**
     * Save vehicle data to JSON file.
     *
     * @param vehicles List of vehicle data to save
     */
    public void saveVehicles(List<VehicleSaveData> vehicles) {
        try (Writer writer = new FileWriter(saveFile.toFile())) {
            gson.toJson(vehicles, writer);
            logger.info("Saved " + vehicles.size() + " vehicles to " + saveFile);
        } catch (IOException e) {
            logger.severe("Failed to save vehicles: " + e.getMessage());
        }
    }

    /**
     * Load vehicle data from JSON file.
     *
     * @return List of saved vehicle data, or empty list if no save exists
     */
    public List<VehicleSaveData> loadVehicles() {
        if (!Files.exists(saveFile)) {
            logger.info("No vehicle save file found at " + saveFile);
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(saveFile.toFile())) {
            Type listType = new TypeToken<List<VehicleSaveData>>() {}.getType();
            List<VehicleSaveData> vehicles = gson.fromJson(reader, listType);
            if (vehicles == null) {
                return new ArrayList<>();
            }
            logger.info("Loaded " + vehicles.size() + " vehicles from " + saveFile);
            return vehicles;
        } catch (Exception e) {
            logger.severe("Failed to load vehicles: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Delete the save file (e.g., after successful load).
     */
    public void clearSaveFile() {
        try {
            Files.deleteIfExists(saveFile);
        } catch (IOException e) {
            logger.warning("Could not delete save file: " + e.getMessage());
        }
    }

    /**
     * Data class for serializing vehicle state.
     */
    public static class VehicleSaveData {
        public String definitionId;
        public float x, y, z;
        public float yaw;

        public VehicleSaveData() {}

        public VehicleSaveData(String definitionId, float x, float y, float z, float yaw) {
            this.definitionId = definitionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }
}
