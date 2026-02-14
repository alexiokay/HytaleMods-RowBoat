package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class VehicleDeathSystem extends DeathSystems.OnDeathSystem {

    private static final VehicleLogger logger = VehicleLogger.console();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
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
            Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
            Store<EntityStore> entityStore = (Store<EntityStore>) store;
            CommandBuffer<EntityStore> buffer = (CommandBuffer<EntityStore>) commandBuffer;
            if (VehicleDataComponent.getComponentType() == null) {
                return;
            }
            VehicleDataComponent vehicleData = entityStore.getComponent(entityRef, VehicleDataComponent.getComponentType());
            if (vehicleData == null) {
                return;
            }
            logger.info("=== VEHICLE DEATH EVENT ===");
            logger.info("Definition ID: " + vehicleData.getDefinitionId());
            logger.info("Drop Item: " + vehicleData.getDropItemId() + " x" + vehicleData.getDropQuantity());
            TransformComponent transform = entityStore.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                logger.warning("No TransformComponent - cannot drop item");
                return;
            }
            Vector3d position = transform.getPosition();
            logger.info("Position: " + position);
            Damage deathInfo = component.getDeathInfo();
            if (deathInfo != null) {
                logger.info("Death damage: " + deathInfo.getAmount());
            }
            String dropItemId = vehicleData.getDropItemId();
            int dropQuantity = vehicleData.getDropQuantity();
            VehicleEntityBridge entityBridge;
            EntityStore externalData;
            if (dropItemId != null && !dropItemId.isEmpty() && dropQuantity > 0 && (entityBridge = plugin.getEntityBridge()) != null && (externalData = entityStore.getExternalData()) != null) {
                entityBridge.dropItem(externalData.getWorld(), position, dropItemId, dropQuantity, buffer);
                logger.info("Dropped item: " + dropItemId);
            }
            VehicleRegistry registry;
            if ((registry = plugin.getRegistry()) != null) {
                for (BaseVehicle vehicle : registry.getAllVehicles()) {
                    Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                    if (vehicleRef != null && vehicleRef.equals(entityRef)) {
                        vehicle.destroy();
                        logger.info("Cleaned up tracked vehicle: " + vehicle.getInstanceId());
                        break;
                    }
                }
            }
            buffer.removeEntity(entityRef, RemoveReason.REMOVE);
            logger.info("Removed vehicle entity from ECS to prevent ghost persistence");
        } catch (Exception e) {
            logger.warning("Error in VehicleDeathSystem: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
