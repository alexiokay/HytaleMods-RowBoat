package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.math.vector.Vector3d;

import java.util.Collection;

/**
 * Implementation of the VehicleAPI.
 * 
 * @since 1.0
 * @author alexispace
 */
public class VehicleAPIImpl implements VehicleAPI {

    private final VehicleRegistry registry;
    private final VehicleLogger logger;
    private final VehicleEntityBridge entityBridge;

    public VehicleAPIImpl(VehicleRegistry registry, VehicleLogger logger) {
        this.registry = registry;
        this.logger = logger;
        this.entityBridge = new VehicleEntityBridge(logger);

        // Set up destruction listener to handle particles and item drops
        registry.setDestructionListener((vehicle, commandBuffer) -> {
            World world = vehicle.getWorld();
            if (world != null) {
                Vec3 pos = vehicle.getPosition();
                Vector3d position = new Vector3d(pos.x, pos.y, pos.z);
                entityBridge.onVehicleDestroyed(world, position, vehicle.getDefinition(), commandBuffer);
            }
        });
    }
    
    @Override
    public void registerVehicle(VehicleDefinition definition) {
        registry.registerDefinition(definition);
    }
    
    @Override
    public VehicleHandle spawnVehicle(String vehicleId, Vec3 position, float yaw, World world) {
        if (world == null) {
            logger.warning("spawnVehicle called with null world");
            return null;
        }

        VehicleDefinition definition = registry.getDefinition(vehicleId);
        if (definition == null) {
            throw new IllegalArgumentException("Vehicle not registered: " + vehicleId);
        }

        VehicleTypeCreator creator = registry.getCreator(definition.type);
        if (creator == null) {
            throw new IllegalStateException("No creator for type: " + definition.type);
        }

        // Create the vehicle physics simulation
        BaseVehicle vehicle = creator.createVehicle(definition, position, yaw);
        vehicle.setWorld(world);  // Store world reference for destruction effects

        // Track it in our registry
        registry.trackVehicle(vehicle);

        // Spawn Hytale entity using the world's store directly (only safe outside of tick processing)
        Ref<EntityStore> entityRef = entityBridge.spawnEntity(vehicle, world);
        if (entityRef != null) {
            vehicle.setEntityRef(entityRef);  // Link vehicle to Hytale entity for cleanup detection
            logger.info("Spawned vehicle: " + vehicleId + " at " + position);
        } else {
            logger.warning("Created vehicle but failed to spawn entity: " + vehicleId);
        }

        return new VehicleHandleImpl(vehicle, registry, entityRef, world, entityBridge);
    }

    @Override
    public VehicleHandle spawnVehicleWithCommandBuffer(String vehicleId, Vec3 position, float yaw, CommandBuffer commandBuffer) {
        VehicleDefinition definition = registry.getDefinition(vehicleId);
        if (definition == null) {
            throw new IllegalArgumentException("Vehicle not registered: " + vehicleId);
        }

        VehicleTypeCreator creator = registry.getCreator(definition.type);
        if (creator == null) {
            throw new IllegalStateException("No creator for type: " + definition.type);
        }

        // Create the vehicle physics simulation
        BaseVehicle vehicle = creator.createVehicle(definition, position, yaw);

        // Try to get World from CommandBuffer's store
        World world = null;
        try {
            @SuppressWarnings("unchecked")
            com.hypixel.hytale.component.Store<EntityStore> store =
                (com.hypixel.hytale.component.Store<EntityStore>) commandBuffer.getStore();
            EntityStore entityStore = store.getExternalData();
            world = entityStore.getWorld();
        } catch (Exception e) {
            logger.warning("Could not get World from CommandBuffer: " + e.getMessage());
        }
        vehicle.setWorld(world);  // Store world reference for destruction effects

        // Track it in our registry
        registry.trackVehicle(vehicle);

        // Spawn Hytale entity using CommandBuffer for deferred execution
        Ref<EntityStore> entityRef = entityBridge.spawnEntityWithCommandBuffer(vehicle, commandBuffer);
        if (entityRef != null) {
            vehicle.setEntityRef(entityRef);  // Link vehicle to Hytale entity for cleanup detection
            logger.info("Spawned vehicle (deferred): " + vehicleId + " at " + position);
        } else {
            logger.warning("Created vehicle but failed to spawn entity: " + vehicleId);
        }

        return new VehicleHandleImpl(vehicle, registry, entityRef, world, entityBridge);
    }
    
    @Override
    public Collection<String> getRegisteredVehicles() {
        return registry.getRegisteredVehicles();
    }
    
    @Override
    public Collection<String> getRegisteredTypes() {
        return registry.getRegisteredTypes();
    }
    
    @Override
    public boolean isTypeRegistered(String typeName) {
        return registry.hasCreator(typeName);
    }
    
    @Override
    public void registerVehicleCreator(String typeName, VehicleTypeCreator creator) {
        registry.registerCreator(typeName, creator);
    }
    
    @Override
    public VehicleDefinition getVehicleDefinition(String vehicleId) {
        return registry.getDefinition(vehicleId);
    }

    /**
     * Get the entity bridge for direct entity operations.
     * Used by VehicleDeathSystem for item drops after world reload.
     */
    public VehicleEntityBridge getEntityBridge() {
        return entityBridge;
    }
}
