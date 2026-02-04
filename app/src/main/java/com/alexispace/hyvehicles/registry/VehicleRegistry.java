package com.alexispace.hyvehicles.registry;

import com.alexispace.hyvehicles.api.VehicleTypeCreator;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import com.hypixel.hytale.component.CommandBuffer;

/**
 * Registry for vehicle types and definitions.
 *
 * <p>Manages:</p>
 * <ul>
 *   <li>Vehicle type creators (like MTS's part creators)</li>
 *   <li>Vehicle definitions from JSON</li>
 *   <li>Spawned vehicle instances</li>
 * </ul>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleRegistry {

    private final VehicleLogger logger;

    /**
     * Registered vehicle type creators.
     * Key: type name (e.g., "BOAT"), Value: creator
     */
    private final Map<String, VehicleTypeCreator> creators = new ConcurrentHashMap<>();

    /**
     * Registered vehicle definitions.
     * Key: vehicle ID (e.g., "hyboats:wooden_boat"), Value: definition
     */
    private final Map<String, VehicleDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * Currently spawned vehicles.
     * Key: instance UUID, Value: vehicle entity
     */
    private final Map<UUID, BaseVehicle> spawnedVehicles = new ConcurrentHashMap<>();

    /**
     * Callback for when a vehicle is destroyed.
     * Used to trigger destruction effects (particles, item drops).
     * Takes the vehicle and an optional CommandBuffer for deferred operations.
     */
    private BiConsumer<BaseVehicle, CommandBuffer<?>> destructionListener;

    public VehicleRegistry(VehicleLogger logger) {
        this.logger = logger;
    }

    /**
     * Set the destruction listener callback.
     * Called when a vehicle entity is destroyed by the game (health reaches 0).
     *
     * @param listener The callback to invoke with the destroyed vehicle and optional CommandBuffer
     */
    public void setDestructionListener(BiConsumer<BaseVehicle, CommandBuffer<?>> listener) {
        this.destructionListener = listener;
    }

    // ==================== Creator Management ====================

    /**
     * Register a vehicle type creator.
     *
     * @param typeName The type name (e.g., "BOAT", "HOVERCRAFT")
     * @param creator The creator that handles this type
     */
    public void registerCreator(String typeName, VehicleTypeCreator creator) {
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("Type name cannot be null or empty");
        }
        if (creator == null) {
            throw new IllegalArgumentException("Creator cannot be null");
        }

        String upperType = typeName.toUpperCase();

        if (creators.containsKey(upperType)) {
            logger.warning("Overriding vehicle type creator: " + upperType);
        }

        creators.put(upperType, creator);
        logger.info("Registered vehicle type: " + upperType);
    }

    /**
     * Get a creator for a vehicle type.
     *
     * @param typeName The type name
     * @return The creator, or null if not found
     */
    public VehicleTypeCreator getCreator(String typeName) {
        return creators.get(typeName.toUpperCase());
    }

    /**
     * Check if a type has a registered creator.
     */
    public boolean hasCreator(String typeName) {
        return creators.containsKey(typeName.toUpperCase());
    }

    /**
     * Get all registered type names.
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(creators.keySet());
    }

    // ==================== Definition Management ====================

    /**
     * Register a vehicle definition.
     *
     * @param definition The vehicle definition
     * @throws IllegalArgumentException if ID is already registered
     * @throws IllegalArgumentException if type has no creator
     */
    public void registerDefinition(VehicleDefinition definition) {
        if (definition.id == null || definition.id.isEmpty()) {
            throw new IllegalArgumentException("Vehicle ID cannot be null or empty");
        }

        if (definitions.containsKey(definition.id)) {
            throw new IllegalArgumentException("Vehicle ID already registered: " + definition.id);
        }

        if (!hasCreator(definition.type)) {
            throw new IllegalArgumentException(
                "No creator registered for vehicle type: " + definition.type +
                " (vehicle: " + definition.id + ")");
        }

        // Validate with the creator
        VehicleTypeCreator creator = getCreator(definition.type);
        VehicleTypeCreator.ValidationResult result = creator.validate(definition);
        if (!result.isValid()) {
            throw new IllegalArgumentException(
                "Vehicle definition validation failed: " + definition.id +
                " - " + result.getErrorMessage());
        }

        definitions.put(definition.id, definition);
        logger.info("Registered vehicle: " + definition.id + " (type: " + definition.type + ")");
    }

    /**
     * Get a vehicle definition by ID.
     */
    public VehicleDefinition getDefinition(String vehicleId) {
        return definitions.get(vehicleId);
    }

    /**
     * Check if a vehicle ID is registered.
     */
    public boolean hasDefinition(String vehicleId) {
        return definitions.containsKey(vehicleId);
    }

    /**
     * Get all registered vehicle IDs.
     */
    public Set<String> getRegisteredVehicles() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    // ==================== Instance Management ====================

    /**
     * Track a spawned vehicle.
     */
    public void trackVehicle(BaseVehicle vehicle) {
        spawnedVehicles.put(vehicle.getInstanceId(), vehicle);
    }

    /**
     * Untrack a vehicle (when destroyed).
     */
    public void untrackVehicle(UUID instanceId) {
        spawnedVehicles.remove(instanceId);
    }

    /**
     * Get a spawned vehicle by instance ID.
     */
    public BaseVehicle getVehicle(UUID instanceId) {
        return spawnedVehicles.get(instanceId);
    }

    /**
     * Get all spawned vehicles.
     */
    public Collection<BaseVehicle> getAllVehicles() {
        return Collections.unmodifiableCollection(spawnedVehicles.values());
    }

    /**
     * Find a vehicle by its entity reference.
     * @param entityRef The entity reference to search for
     * @return The vehicle with matching entity ref, or null if not found
     */
    public BaseVehicle getVehicleByEntityRef(Ref<EntityStore> entityRef) {
        if (entityRef == null) return null;
        for (BaseVehicle vehicle : spawnedVehicles.values()) {
            Ref<EntityStore> vehicleEntityRef = vehicle.getEntityRef();
            if (vehicleEntityRef != null && vehicleEntityRef.equals(entityRef)) {
                return vehicle;
            }
        }
        return null;
    }

    /**
     * Clear all vehicle tracking without destroying them.
     * Used when player leaves world - ECS entities persist automatically.
     */
    public void clearAllTracking() {
        spawnedVehicles.clear();
        logger.info("Cleared all vehicle tracking");
    }

    // Grace period before death detection can trigger (in ticks)
    // This prevents vehicles from being destroyed immediately after spawn due to unloaded chunks
    private static final long DEATH_CHECK_GRACE_PERIOD = 60; // 3 seconds at 20 ticks/sec

    /**
     * Tick all spawned vehicles.
     * Also detects and cleans up vehicles whose Hytale entities were destroyed.
     */
    public void tickAll(float deltaTime) {
        // Remove destroyed vehicles and vehicles whose entities were destroyed by game systems
        spawnedVehicles.entrySet().removeIf(entry -> {
            BaseVehicle vehicle = entry.getValue();

            // Check if already marked as destroyed
            if (vehicle.isDestroyed()) {
                logger.info("Removing destroyed vehicle: " + vehicle.getInstanceId());
                return true;
            }

            // Check if the Hytale entity was destroyed (e.g., by damage/death system)
            Ref<EntityStore> entityRef = vehicle.getEntityRef();
            World world = vehicle.getWorld();

            if (entityRef != null && world != null) {
                // Log every 100 ticks to avoid spam
                if (vehicle.getTicksExisted() % 100 == 0) {
                    boolean refValid = entityRef.isValid();
                    boolean hasDeath = hasDeathComponent(entityRef, world);
                    logger.info("Vehicle " + vehicle.getInstanceId() + " - refValid: " + refValid + ", hasDeath: " + hasDeath);
                }

                // Skip death detection during grace period after spawn
                // This prevents vehicles from being destroyed due to chunks not being loaded yet
                if (vehicle.getTicksExisted() < DEATH_CHECK_GRACE_PERIOD) {
                    vehicle.tick(deltaTime);
                    return false;
                }

                // Check if entity has DeathComponent (entity killed but not yet removed)
                // OR if the Ref is no longer valid (entity fully removed)
                boolean entityDead = hasDeathComponent(entityRef, world) || !entityRef.isValid();

                if (entityDead) {
                    logger.info("=== VEHICLE ENTITY DEATH DETECTED ===");
                    logger.info("Vehicle: " + vehicle.getInstanceId());
                    logger.info("Definition: " + vehicle.getDefinition().id);
                    logger.info("Position: " + vehicle.getPosition());
                    logger.info("HasDeathComponent: " + hasDeathComponent(entityRef, world));
                    logger.info("RefValid: " + entityRef.isValid());
                    logger.info("HasDestructionListener: " + (destructionListener != null));

                    // Call destruction listener for effects (particles, item drops)
                    // Note: From tick loop, we don't have a CommandBuffer, so pass null
                    if (destructionListener != null) {
                        try {
                            logger.info("Calling destruction listener (from tick)...");
                            destructionListener.accept(vehicle, null);
                            logger.info("Destruction listener completed");
                        } catch (Exception e) {
                            logger.warning("Destruction listener error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        logger.warning("No destruction listener registered!");
                    }

                    vehicle.destroy();  // Mark our vehicle as destroyed too
                    return true;
                }
            } else {
                // Debug: entity ref or world is null
                if (vehicle.getTicksExisted() % 100 == 0) {
                    logger.warning("Vehicle " + vehicle.getInstanceId() +
                        " - entityRef: " + (entityRef != null ? "valid" : "NULL") +
                        ", world: " + (world != null ? "valid" : "NULL"));
                }
            }

            vehicle.tick(deltaTime);
            return false;
        });
    }

    /**
     * Check if an entity has a DeathComponent (meaning it died).
     * This is how Hytale marks entities as dead before removing them.
     *
     * @param entityRef The entity reference
     * @param world The world containing the entity
     * @return true if entity has DeathComponent (is dead)
     */
    private boolean hasDeathComponent(Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid() || world == null) {
            return false;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            return store.getArchetype(entityRef).contains(DeathComponent.getComponentType());
        } catch (Exception e) {
            // If we can't check, assume not dead
            return false;
        }
    }

    // ==================== Death Handling ====================

    /**
     * Called by VehicleDeathSystem when a vehicle entity dies.
     * This is the proper event-driven way to handle death (no tick loop needed).
     *
     * @param vehicle The vehicle that died
     * @param commandBuffer The CommandBuffer for deferred operations (item drops), or null if not available
     */
    public void onVehicleDeath(BaseVehicle vehicle, CommandBuffer<?> commandBuffer) {
        if (vehicle == null || vehicle.isDestroyed()) {
            return;
        }

        logger.info("=== onVehicleDeath called ===");
        logger.info("Vehicle: " + vehicle.getInstanceId());
        logger.info("Definition: " + vehicle.getDefinition().id);
        logger.info("CommandBuffer: " + (commandBuffer != null ? "available" : "null"));

        // Call destruction listener for effects (particles, item drops)
        if (destructionListener != null) {
            try {
                logger.info("Calling destruction listener...");
                destructionListener.accept(vehicle, commandBuffer);
                logger.info("Destruction listener completed");
            } catch (Exception e) {
                logger.warning("Destruction listener error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            logger.warning("No destruction listener registered!");
        }

        // Mark vehicle as destroyed and remove from tracking
        vehicle.destroy();
        untrackVehicle(vehicle.getInstanceId());

        logger.info("Vehicle removed from tracking");
    }

    // ==================== Stats ====================

    public int getTypeCount() {
        return creators.size();
    }

    public int getVehicleCount() {
        return definitions.size();
    }

    public int getSpawnedCount() {
        return spawnedVehicles.size();
    }
}
