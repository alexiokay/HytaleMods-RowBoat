package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * System that listens for entity death events using Hytale's built-in death system.
 * When a vehicle entity dies, this triggers destruction effects (particles, item drops).
 *
 * <p>This system identifies vehicles using VehicleDataComponent, which is persisted
 * by Hytale's ECS on the entity itself - works after world reloads.</p>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleDeathSystem extends DeathSystems.OnDeathSystem {

    private static final VehicleLogger logger = VehicleLogger.console();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Listen for ALL entity deaths - we'll filter for vehicles via VehicleDataComponent
        return Query.any();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onComponentAdded(@Nonnull Ref ref, @Nonnull DeathComponent component,
                                  @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
        try {
            HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
            if (plugin == null) {
                return;
            }

            // Cast to typed refs
            Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
            Store<EntityStore> entityStore = (Store<EntityStore>) store;
            CommandBuffer<EntityStore> buffer = (CommandBuffer<EntityStore>) commandBuffer;

            // Check if this entity has VehicleDataComponent (persisted by Hytale)
            if (VehicleDataComponent.getComponentType() == null) {
                return; // Component not registered
            }

            VehicleDataComponent vehicleData = entityStore.getComponent(
                entityRef, VehicleDataComponent.getComponentType());

            if (vehicleData == null) {
                // Not a vehicle entity
                return;
            }

            // === VEHICLE DEATH DETECTED ===
            logger.info("=== VEHICLE DEATH EVENT ===");
            logger.info("Definition ID: " + vehicleData.getDefinitionId());
            logger.info("Drop Item: " + vehicleData.getDropItemId() + " x" + vehicleData.getDropQuantity());

            // Get position from TransformComponent
            TransformComponent transform = entityStore.getComponent(
                entityRef, TransformComponent.getComponentType());

            Vector3d position;
            if (transform != null) {
                position = transform.getPosition();
                logger.info("Position: " + position);
            } else {
                logger.warning("No TransformComponent - cannot drop item");
                return;
            }

            // Get damage info
            Damage deathInfo = component.getDeathInfo();
            if (deathInfo != null) {
                logger.info("Death damage: " + deathInfo.getAmount());
            }

            // Drop the item
            String dropItemId = vehicleData.getDropItemId();
            int dropQuantity = vehicleData.getDropQuantity();

            if (dropItemId != null && !dropItemId.isEmpty() && dropQuantity > 0) {
                VehicleEntityBridge entityBridge = plugin.getEntityBridge();
                if (entityBridge != null) {
                    EntityStore externalData = entityStore.getExternalData();
                    if (externalData != null) {
                        entityBridge.dropItem(
                            externalData.getWorld(),
                            position,
                            dropItemId,
                            dropQuantity,
                            buffer
                        );
                        logger.info("Dropped item: " + dropItemId);
                    }
                }
            }

            // Clean up in-memory tracking if this vehicle was tracked
            VehicleRegistry registry = plugin.getRegistry();
            if (registry != null) {
                for (BaseVehicle vehicle : registry.getAllVehicles()) {
                    Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                    if (vehicleRef != null && vehicleRef.equals(entityRef)) {
                        vehicle.destroy();
                        logger.info("Cleaned up tracked vehicle: " + vehicle.getInstanceId());
                        break;
                    }
                }
            }

            // Remove the ENTIRE entity from ECS so it doesn't persist after death
            // Just removing VehicleDataComponent leaves the visual model which reappears as ghost
            buffer.removeEntity(entityRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            logger.info("Removed vehicle entity from ECS to prevent ghost persistence");

        } catch (Exception e) {
            logger.warning("Error in VehicleDeathSystem: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
