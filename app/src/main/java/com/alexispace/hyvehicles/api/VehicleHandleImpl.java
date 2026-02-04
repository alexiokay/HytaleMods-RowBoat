package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Implementation of VehicleHandle.
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleHandleImpl implements VehicleHandle {

    private final BaseVehicle vehicle;
    private final VehicleRegistry registry;
    private final Ref<EntityStore> entityRef;
    private final World world;
    private final VehicleEntityBridge entityBridge;

    /**
     * Legacy constructor for backwards compatibility.
     */
    public VehicleHandleImpl(BaseVehicle vehicle, VehicleRegistry registry) {
        this(vehicle, registry, null, null, null);
    }

    /**
     * Full constructor with Hytale entity support.
     */
    public VehicleHandleImpl(BaseVehicle vehicle, VehicleRegistry registry,
                             Ref<EntityStore> entityRef, World world,
                             VehicleEntityBridge entityBridge) {
        this.vehicle = vehicle;
        this.registry = registry;
        this.entityRef = entityRef;
        this.world = world;
        this.entityBridge = entityBridge;
    }
    
    @Override
    public String getVehicleId() {
        return vehicle.getDefinition().id;
    }
    
    @Override
    public String getInstanceId() {
        return vehicle.getInstanceId().toString();
    }
    
    @Override
    public boolean isValid() {
        return !vehicle.isDestroyed() && registry.getVehicle(vehicle.getInstanceId()) != null;
    }
    
    @Override
    public Vec3 getPosition() {
        if (!isValid()) return null;
        return vehicle.getPosition();
    }
    
    @Override
    public float getYaw() {
        return vehicle.getRotationYaw();
    }
    
    @Override
    public BaseVehicle getVehicle() {
        if (!isValid()) return null;
        return vehicle;
    }
    
    @Override
    public boolean destroy() {
        if (!isValid()) return false;

        // Remove Hytale entity first
        if (entityRef != null && world != null && entityBridge != null) {
            entityBridge.removeEntity(entityRef, world);
        }

        vehicle.destroy();
        registry.untrackVehicle(vehicle.getInstanceId());
        return true;
    }

    /**
     * Check if this vehicle has a visible Hytale entity.
     *
     * @return true if entity was spawned successfully
     */
    public boolean hasEntity() {
        return entityRef != null && entityRef.isValid();
    }

    /**
     * Get the Hytale entity reference.
     *
     * @return The entity ref, or null if no entity
     */
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }
    
    @Override
    public int getPassengerCount() {
        return vehicle.getPassengerCount();
    }
    
    @Override
    public boolean hasDriver() {
        return vehicle.hasDriver();
    }
}
