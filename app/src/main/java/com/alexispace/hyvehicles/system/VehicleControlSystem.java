package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * ECS system that reacts to MountedByComponent changes on vehicle entities.
 * Handles automatic dismount cleanup when a vehicle entity is destroyed/removed.
 */
public class VehicleControlSystem extends RefChangeSystem<EntityStore, MountedByComponent> {

    private static final VehicleLogger logger = VehicleLogger.console();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
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
        int count = mountedBy.getPassengers() != null ? mountedBy.getPassengers().size() : 0;
        logger.info("MountedByComponent added to vehicle (passengers: " + count + ")");
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> vehicleRef,
                                    @Nonnull MountedByComponent mountedBy,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        logger.info("MountedByComponent removed from vehicle - cleaning up passengers");
        try {
            List<Ref<EntityStore>> passengers = mountedBy.getPassengers();
            if (passengers == null || passengers.isEmpty()) {
                return;
            }
            HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
            if (plugin == null) {
                return;
            }
            VehicleMountSystem mountSystem = plugin.getMountSystem();
            VehicleRegistry registry = plugin.getRegistry();

            for (Ref<EntityStore> passengerRef : passengers) {
                if (passengerRef == null || !passengerRef.isValid()) {
                    continue;
                }
                try {
                    UUIDComponent uuidComp = store.getComponent(passengerRef, UUIDComponent.getComponentType());
                    if (uuidComp == null) {
                        continue;
                    }
                    PlayerRef playerRef = mountSystem != null ? mountSystem.getPlayerRefByUuid(uuidComp.getUuid()) : null;
                    if (playerRef == null) {
                        continue;
                    }
                    // Reset movement config
                    try {
                        MovementManager movementManager = store.getComponent(passengerRef, MovementManager.getComponentType());
                        if (movementManager != null) {
                            movementManager.resetDefaultsAndUpdate(passengerRef, store);
                            logger.info("Reset MovementConfig for dismounted passenger");
                        }
                    } catch (Exception e) {
                        logger.warning("Could not reset MovementConfig: " + e.getMessage());
                    }
                    // Send dismount packet
                    try {
                        DismountNPC dismountPacket = new DismountNPC();
                        playerRef.getPacketHandler().write(dismountPacket);
                    } catch (Exception e) {
                        logger.warning("Could not send DismountNPC packet: " + e.getMessage());
                    }
                    // Clean up BaseVehicle tracking (use NetworkId, not Ref.equals)
                    if (registry != null) {
                        try {
                            com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId netId =
                                store.getComponent(vehicleRef, com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType());
                            if (netId != null && netId.getId() >= 0) {
                                BaseVehicle vehicle = registry.getVehicleByNetworkId(netId.getId());
                                if (vehicle != null) {
                                    vehicle.dismount(playerRef.getUuid());
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning("Could not look up vehicle by NetworkId: " + ex.getMessage());
                        }
                    }
                    logger.info("Auto-dismounted passenger from destroyed vehicle");
                } catch (Exception e) {
                    logger.warning("Error cleaning up passenger: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Error in MountedByComponent removal cleanup: " + e.getMessage());
        }
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> vehicleRef,
                                @Nonnull MountedByComponent oldMountedBy,
                                @Nonnull MountedByComponent newMountedBy,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        int oldCount = oldMountedBy.getPassengers() != null ? oldMountedBy.getPassengers().size() : 0;
        int newCount = newMountedBy.getPassengers() != null ? newMountedBy.getPassengers().size() : 0;
        logger.info("MountedByComponent changed on vehicle (passengers: " + oldCount + " -> " + newCount + ")");
    }
}
