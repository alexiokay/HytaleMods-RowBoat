package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.Vec3;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;

public class VehicleAPIImpl implements VehicleAPI {

    private final VehicleRegistry registry;
    private final VehicleLogger logger;
    private final VehicleEntityBridge entityBridge;

    public VehicleAPIImpl(VehicleRegistry registry, VehicleLogger logger) {
        this.registry = registry;
        this.logger = logger;
        this.entityBridge = new VehicleEntityBridge(logger);
        registry.setDestructionListener((vehicle, commandBuffer) -> {
            World world = vehicle.getWorld();
            if (world != null) {
                Vec3 pos = vehicle.getPosition();
                Vector3d position = new Vector3d((double) pos.x, (double) pos.y, (double) pos.z);
                this.entityBridge.onVehicleDestroyed(world, position, vehicle.getDefinition(), commandBuffer);
            }
        });
    }

    @Override
    public void registerVehicle(VehicleDefinition definition) {
        this.registry.registerDefinition(definition);
    }

    @Override
    public VehicleHandle spawnVehicle(String vehicleId, Vec3 position, float yaw, World world) {
        Ref<EntityStore> entityRef;
        if (world == null) {
            this.logger.warning("spawnVehicle called with null world");
            return null;
        }
        VehicleDefinition definition = this.registry.getDefinition(vehicleId);
        if (definition == null) {
            throw new IllegalArgumentException("Vehicle not registered: " + vehicleId);
        }
        VehicleTypeCreator creator = this.registry.getCreator(definition.type);
        if (creator == null) {
            throw new IllegalStateException("No creator for type: " + definition.type);
        }
        BaseVehicle vehicle = creator.createVehicle(definition, position, yaw);
        vehicle.setWorld(world);
        this.registry.trackVehicle(vehicle);
        this.registry.setSpawning(true);
        try {
            entityRef = this.entityBridge.spawnEntity(vehicle, world);
        } finally {
            this.registry.setSpawning(false);
        }
        if (entityRef != null) {
            vehicle.setEntityRef(entityRef);
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                NetworkId netId = (NetworkId) store.getComponent(entityRef, NetworkId.getComponentType());
                if (netId != null) {
                    vehicle.setNetworkId(netId.getId());
                    this.logger.info("Spawned vehicle: " + vehicleId + " at " + position + " | networkId=" + netId.getId() + ", refIndex=" + entityRef.getIndex());
                }
            } catch (Exception e) {
                this.logger.warning("Could not read NetworkId for " + vehicleId + ": " + e.getMessage());
            }
        } else {
            this.logger.warning("Failed to spawn entity for vehicle: " + vehicleId + " - untracking ghost vehicle");
            this.registry.untrackVehicle(vehicle.getInstanceId());
            return null;
        }
        return new VehicleHandleImpl(vehicle, this.registry, entityRef, world, this.entityBridge);
    }

    @Override
    public VehicleHandle spawnVehicleWithCommandBuffer(String vehicleId, Vec3 position, float yaw, CommandBuffer commandBuffer) {
        Ref<EntityStore> entityRef;
        VehicleDefinition definition = this.registry.getDefinition(vehicleId);
        if (definition == null) {
            throw new IllegalArgumentException("Vehicle not registered: " + vehicleId);
        }
        VehicleTypeCreator creator = this.registry.getCreator(definition.type);
        if (creator == null) {
            throw new IllegalStateException("No creator for type: " + definition.type);
        }
        BaseVehicle vehicle = creator.createVehicle(definition, position, yaw);
        World world = null;
        try {
            Store store = commandBuffer.getStore();
            EntityStore entityStore = (EntityStore) store.getExternalData();
            world = entityStore.getWorld();
        } catch (Exception e) {
            this.logger.warning("Could not get World from CommandBuffer: " + e.getMessage());
        }
        vehicle.setWorld(world);
        this.registry.trackVehicle(vehicle);
        this.registry.setSpawning(true);
        try {
            entityRef = this.entityBridge.spawnEntityWithCommandBuffer(vehicle, commandBuffer);
        } finally {
            this.registry.setSpawning(false);
        }
        if (entityRef != null) {
            vehicle.setEntityRef(entityRef);
            // NetworkId is now set directly in spawnEntityWithCommandBuffer() at spawn time
            this.logger.info("Spawned vehicle (deferred): " + vehicleId + " at " + position + " | networkId=" + vehicle.getNetworkId());
        } else {
            this.logger.warning("Created vehicle but failed to spawn entity: " + vehicleId);
        }
        return new VehicleHandleImpl(vehicle, this.registry, entityRef, world, this.entityBridge);
    }

    @Override
    public Collection<String> getRegisteredVehicles() {
        return this.registry.getRegisteredVehicles();
    }

    @Override
    public Collection<String> getRegisteredTypes() {
        return this.registry.getRegisteredTypes();
    }

    @Override
    public boolean isTypeRegistered(String typeName) {
        return this.registry.hasCreator(typeName);
    }

    @Override
    public void registerVehicleCreator(String typeName, VehicleTypeCreator creator) {
        this.registry.registerCreator(typeName, creator);
    }

    @Override
    public VehicleDefinition getVehicleDefinition(String vehicleId) {
        return this.registry.getDefinition(vehicleId);
    }

    public VehicleEntityBridge getEntityBridge() {
        return this.entityBridge;
    }
}
