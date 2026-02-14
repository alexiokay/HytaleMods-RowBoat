package com.alexispace.hyvehicles.registry;

import com.alexispace.hyvehicles.api.VehicleTypeCreator;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class VehicleRegistry {
    private final VehicleLogger logger;
    private final Map<String, VehicleTypeCreator> creators = new ConcurrentHashMap<>();
    private final Map<String, VehicleDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, BaseVehicle> spawnedVehicles = new ConcurrentHashMap<>();
    private volatile boolean spawning = false;
    private BiConsumer<BaseVehicle, CommandBuffer<?>> destructionListener;
    private static final long DEATH_CHECK_GRACE_PERIOD = 60L;

    public VehicleRegistry(VehicleLogger logger) {
        this.logger = logger;
    }

    public void setDestructionListener(BiConsumer<BaseVehicle, CommandBuffer<?>> listener) {
        this.destructionListener = listener;
    }

    public void registerCreator(String typeName, VehicleTypeCreator creator) {
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("Type name cannot be null or empty");
        }
        if (creator == null) {
            throw new IllegalArgumentException("Creator cannot be null");
        }
        String upperType = typeName.toUpperCase();
        if (this.creators.containsKey(upperType)) {
            this.logger.warning("Overriding vehicle type creator: " + upperType);
        }
        this.creators.put(upperType, creator);
        this.logger.info("Registered vehicle type: " + upperType);
    }

    public VehicleTypeCreator getCreator(String typeName) {
        return this.creators.get(typeName.toUpperCase());
    }

    public boolean hasCreator(String typeName) {
        return this.creators.containsKey(typeName.toUpperCase());
    }

    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(this.creators.keySet());
    }

    public void registerDefinition(VehicleDefinition definition) {
        if (definition.id == null || definition.id.isEmpty()) {
            throw new IllegalArgumentException("Vehicle ID cannot be null or empty");
        }
        if (this.definitions.containsKey(definition.id)) {
            throw new IllegalArgumentException("Vehicle ID already registered: " + definition.id);
        }
        if (!this.hasCreator(definition.type)) {
            throw new IllegalArgumentException("No creator registered for vehicle type: " + definition.type + " (vehicle: " + definition.id + ")");
        }
        VehicleTypeCreator creator = this.getCreator(definition.type);
        VehicleTypeCreator.ValidationResult result = creator.validate(definition);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Vehicle definition validation failed: " + definition.id + " - " + result.getErrorMessage());
        }
        this.definitions.put(definition.id, definition);
        this.logger.info("Registered vehicle: " + definition.id + " (type: " + definition.type + ")");
    }

    public VehicleDefinition getDefinition(String vehicleId) {
        return this.definitions.get(vehicleId);
    }

    public boolean hasDefinition(String vehicleId) {
        return this.definitions.containsKey(vehicleId);
    }

    public Set<String> getRegisteredVehicles() {
        return Collections.unmodifiableSet(this.definitions.keySet());
    }

    public void trackVehicle(BaseVehicle vehicle) {
        Ref<EntityStore> ref = vehicle.getEntityRef();
        this.logger.info("[REGISTRY] Tracking vehicle: " + vehicle.getDefinition().id + ", instanceId=" + vehicle.getInstanceId() + ", entityRefIndex=" + (ref != null ? Integer.valueOf(ref.getIndex()) : "null") + ", totalTracked=" + (this.spawnedVehicles.size() + 1));
        this.spawnedVehicles.put(vehicle.getInstanceId(), vehicle);
    }

    public void untrackVehicle(UUID instanceId) {
        BaseVehicle removed = this.spawnedVehicles.remove(instanceId);
        if (removed != null) {
            this.logger.info("[REGISTRY] Untracked vehicle: " + removed.getDefinition().id + ", instanceId=" + instanceId + ", totalTracked=" + this.spawnedVehicles.size());
        }
    }

    public BaseVehicle getVehicle(UUID instanceId) {
        return this.spawnedVehicles.get(instanceId);
    }

    public Collection<BaseVehicle> getAllVehicles() {
        return Collections.unmodifiableCollection(this.spawnedVehicles.values());
    }

    public BaseVehicle getVehicleByNetworkId(int networkId) {
        if (networkId < 0) {
            return null;
        }
        for (BaseVehicle vehicle : this.spawnedVehicles.values()) {
            if (vehicle.getNetworkId() == networkId) {
                return vehicle;
            }
        }
        return null;
    }

    public BaseVehicle getVehicleByEntityRef(Ref<EntityStore> entityRef) {
        if (entityRef == null) {
            this.logger.warning("[REGISTRY DEBUG] getVehicleByEntityRef: entityRef is NULL");
            return null;
        }
        this.logger.info("[REGISTRY DEBUG] Searching for vehicle with entityRef=" + entityRef);
        this.logger.info("[REGISTRY DEBUG] Total vehicles in registry: " + this.spawnedVehicles.size());
        int matchCount = 0;
        for (BaseVehicle vehicle : this.spawnedVehicles.values()) {
            Ref<EntityStore> vehicleEntityRef = vehicle.getEntityRef();
            boolean matches = vehicleEntityRef != null && vehicleEntityRef.equals(entityRef);
            this.logger.info("[REGISTRY DEBUG] Vehicle " + vehicle.getInstanceId() + ": entityRef=" + vehicleEntityRef + ", matches=" + matches);
            if (!matches) continue;
            ++matchCount;
            this.logger.info("[REGISTRY DEBUG] MATCH FOUND! Returning vehicle " + vehicle.getInstanceId());
            return vehicle;
        }
        this.logger.warning("[REGISTRY DEBUG] No matching vehicle found after checking " + this.spawnedVehicles.size() + " vehicles");
        return null;
    }

    public void clearAllTracking() {
        int count = this.spawnedVehicles.size();
        this.logger.info("[REGISTRY] Clearing all vehicle tracking - removing " + count + " vehicles");
        for (BaseVehicle vehicle : this.spawnedVehicles.values()) {
            Ref<EntityStore> ref = vehicle.getEntityRef();
            this.logger.info("[REGISTRY]   Clearing: " + vehicle.getDefinition().id + ", entityRefIndex=" + (ref != null ? Integer.valueOf(ref.getIndex()) : "null") + ", refValid=" + (ref != null ? Boolean.valueOf(ref.isValid()) : "null"));
        }
        this.spawnedVehicles.clear();
        this.logger.info("[REGISTRY] Cleared all vehicle tracking - registry now has " + this.spawnedVehicles.size() + " vehicles");
    }

    public void tickAll(float deltaTime) {
        this.spawnedVehicles.entrySet().removeIf(entry -> {
            BaseVehicle vehicle = entry.getValue();
            if (vehicle.isDestroyed()) {
                this.logger.info("Removing destroyed vehicle: " + vehicle.getInstanceId());
                return true;
            }
            Ref<EntityStore> entityRef = vehicle.getEntityRef();
            World world = vehicle.getWorld();
            if (entityRef != null && world != null) {
                if (vehicle.getTicksExisted() < DEATH_CHECK_GRACE_PERIOD) {
                    vehicle.tick(deltaTime);
                    return false;
                }
                // Only treat DeathComponent as actual death — NOT stale refs.
                // Stale refs happen from ECS compaction (batch spawning, archetype changes)
                // and do NOT mean the vehicle is destroyed. BaseVehicle.position stays correct
                // regardless of ref validity, so JSON persistence still works.
                if (entityRef.isValid() && this.hasDeathComponent(entityRef, world)) {
                    this.logger.info("Vehicle death: " + vehicle.getDefinition().id + " at " + vehicle.getPosition());
                    if (this.destructionListener != null) {
                        try {
                            this.destructionListener.accept(vehicle, null);
                        } catch (Exception e) {
                            this.logger.warning("Destruction listener error: " + e.getMessage());
                        }
                    }
                    vehicle.destroy();
                    return true;
                }
            }
            vehicle.tick(deltaTime);
            return false;
        });
    }

    private boolean hasDeathComponent(Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid() || world == null) {
            return false;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            return store.getArchetype(entityRef).contains(DeathComponent.getComponentType());
        } catch (Exception e) {
            return false;
        }
    }

    public void onVehicleDeath(BaseVehicle vehicle, CommandBuffer<?> commandBuffer) {
        if (vehicle == null || vehicle.isDestroyed()) {
            return;
        }
        this.logger.info("=== onVehicleDeath called ===");
        this.logger.info("Vehicle: " + vehicle.getInstanceId());
        this.logger.info("Definition: " + vehicle.getDefinition().id);
        this.logger.info("CommandBuffer: " + (commandBuffer != null ? "available" : "null"));
        if (this.destructionListener != null) {
            try {
                this.logger.info("Calling destruction listener...");
                this.destructionListener.accept(vehicle, commandBuffer);
                this.logger.info("Destruction listener completed");
            } catch (Exception e) {
                this.logger.warning("Destruction listener error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            this.logger.warning("No destruction listener registered!");
        }
        vehicle.destroy();
        this.untrackVehicle(vehicle.getInstanceId());
        this.logger.info("Vehicle removed from tracking");
    }

    public int getTypeCount() {
        return this.creators.size();
    }

    public int getVehicleCount() {
        return this.definitions.size();
    }

    public int getSpawnedCount() {
        return this.spawnedVehicles.size();
    }

    public void setSpawning(boolean spawning) {
        this.spawning = spawning;
    }

    public boolean isSpawning() {
        return this.spawning;
    }
}
