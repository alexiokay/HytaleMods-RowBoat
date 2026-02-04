package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * System that handles vehicle control based on mounted player's look direction.
 *
 * <p>When a player is mounted on a vehicle and looking in a direction,
 * this system updates the vehicle's rotation to match the player's yaw.</p>
 *
 * <p>This creates natural steering behavior where looking left/right turns the vehicle.</p>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleControlSystem extends RefChangeSystem<EntityStore, MountedByComponent> {

    private static final VehicleLogger logger = VehicleLogger.console();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Match entities that have VehicleDataComponent (our vehicles)
        return Query.any();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, MountedByComponent> componentType() {
        return MountedByComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> vehicleRef,
                                  @Nonnull MountedByComponent mountedBy,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Player mounted - log it
        logger.info("Player mounted on vehicle entity");
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> vehicleRef,
                                    @Nonnull MountedByComponent mountedBy,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Player dismounted - log it
        logger.info("Player dismounted from vehicle entity");
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> vehicleRef,
                                @Nonnull MountedByComponent oldMountedBy,
                                @Nonnull MountedByComponent newMountedBy,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // MountedBy changed - log it
        // NOTE: Steering is now handled in VehicleTickSystem for smooth, continuous updates
        logger.info("MountedByComponent changed on vehicle");
    }
}
