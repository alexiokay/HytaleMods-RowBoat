package com.alexispace.hyvehicles.persistence;

import com.alexispace.hyvehicles.util.VehicleLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists the mapping of entity UUID -> drop item info.
 *
 * <p>This solves the world reload problem:</p>
 * <ul>
 *   <li>Hytale ECS persists the entity (position, model, health)</li>
 *   <li>We persist the UUID -> drop info mapping</li>
 *   <li>After reload, entity exists and we know what item to drop</li>
 * </ul>
 *
 * @since 1.0
 */
public class VehicleDropRegistry {

    private static final VehicleLogger logger = VehicleLogger.console();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String SAVE_FILE = "vehicle_drops.json";

    private final Path saveDirectory;
    private final Map<UUID, DropInfo> dropMap = new ConcurrentHashMap<>();

    public VehicleDropRegistry(Path saveDirectory) {
        this.saveDirectory = saveDirectory;
    }

    /**
     * Register drop info for a vehicle entity.
     * Called when a vehicle is spawned.
     *
     * @param entityUuid The entity's UUID (from UUIDComponent)
     * @param itemId The item ID to drop when destroyed
     * @param quantity The quantity to drop
     */
    public void register(UUID entityUuid, String itemId, int quantity) {
        if (entityUuid == null || itemId == null || itemId.isEmpty()) {
            return;
        }
        dropMap.put(entityUuid, new DropInfo(itemId, quantity));
        logger.info("Registered drop: " + entityUuid + " -> " + itemId + " x" + quantity);
    }

    /**
     * Get drop info for an entity UUID.
     *
     * @param entityUuid The entity's UUID
     * @return The drop info, or null if not registered
     */
    public DropInfo getDropInfo(UUID entityUuid) {
        return dropMap.get(entityUuid);
    }

    /**
     * Remove drop info for an entity (after it has dropped its item).
     *
     * @param entityUuid The entity's UUID
     */
    public void unregister(UUID entityUuid) {
        dropMap.remove(entityUuid);
        logger.info("Unregistered drop for: " + entityUuid);
    }

    /**
     * Save the drop registry to disk.
     * Called on plugin shutdown.
     */
    public void save() {
        try {
            Files.createDirectories(saveDirectory);
            Path saveFile = saveDirectory.resolve(SAVE_FILE);

            // Convert UUID keys to strings for JSON
            Map<String, DropInfo> stringKeyMap = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, DropInfo> entry : dropMap.entrySet()) {
                stringKeyMap.put(entry.getKey().toString(), entry.getValue());
            }

            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                gson.toJson(stringKeyMap, writer);
            }

            logger.info("Saved " + dropMap.size() + " vehicle drop entries to " + saveFile);

        } catch (IOException e) {
            logger.severe("Failed to save vehicle drops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load the drop registry from disk.
     * Called on plugin startup.
     */
    public void load() {
        Path saveFile = saveDirectory.resolve(SAVE_FILE);

        if (!Files.exists(saveFile)) {
            logger.info("No saved vehicle drops found at " + saveFile);
            return;
        }

        try (Reader reader = Files.newBufferedReader(saveFile)) {
            Type mapType = new TypeToken<Map<String, DropInfo>>() {}.getType();
            Map<String, DropInfo> stringKeyMap = gson.fromJson(reader, mapType);

            if (stringKeyMap != null) {
                dropMap.clear();
                for (Map.Entry<String, DropInfo> entry : stringKeyMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        dropMap.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID in save file: " + entry.getKey());
                    }
                }
                logger.info("Loaded " + dropMap.size() + " vehicle drop entries from " + saveFile);
            }

        } catch (Exception e) {
            logger.severe("Failed to load vehicle drops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the number of registered drops.
     */
    public int size() {
        return dropMap.size();
    }

    /**
     * Drop info data class.
     */
    public static class DropInfo {
        public String itemId;
        public int quantity;

        public DropInfo() {} // For Gson

        public DropInfo(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return itemId + " x" + quantity;
        }
    }
}
