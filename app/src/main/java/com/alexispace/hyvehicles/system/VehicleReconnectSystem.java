package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.api.VehicleTypeCreator;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.Vec3;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * System that reconnects persisted vehicle entities to BaseVehicle wrappers.
 *
 * <p>When Hytale loads entities with VehicleDataComponent from the world save,
 * this system detects them and creates the necessary BaseVehicle wrappers
 * so they can be controlled and tracked by our plugin.</p>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleReconnectSystem extends RefChangeSystem<EntityStore, VehicleDataComponent> {

    private static final VehicleLogger logger = VehicleLogger.console();

    // Track which entities we've already processed
    private static final Set<Integer> processedEntities = new HashSet<>();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, VehicleDataComponent> componentType() {
        return VehicleDataComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull VehicleDataComponent component,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        reconnectVehicle(ref, component, store);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull VehicleDataComponent component,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component removed - vehicle entity was despawned
        // VehicleDeathSystem handles cleanup, so nothing needed here
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                @Nonnull VehicleDataComponent oldComponent,
                                @Nonnull VehicleDataComponent newComponent,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component replaced - not expected for vehicles, but handle gracefully
        // Just reconnect with the new data
        reconnectVehicle(ref, newComponent, store);
    }

    /**
     * Reconnect a vehicle entity to a BaseVehicle wrapper.
     */
    private void reconnectVehicle(Ref<EntityStore> ref, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        // Skip if already processed
        int entityIndex = ref.getIndex();
        if (processedEntities.contains(entityIndex)) {
            return;
        }
        processedEntities.add(entityIndex);

        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;

        VehicleRegistry registry = plugin.getRegistry();
        if (registry == null) return;

        // Check if already tracked
        for (BaseVehicle existing : registry.getAllVehicles()) {
            Ref<EntityStore> existingRef = existing.getEntityRef();
            if (existingRef != null && existingRef.getIndex() == entityIndex) {
                return;
            }
        }

        String definitionId = vehicleData.getDefinitionId();
        if (definitionId == null || definitionId.isEmpty()) {
            return;
        }

        VehicleDefinition definition = registry.getDefinition(definitionId);
        if (definition == null) {
            logger.warning("Unknown vehicle definition: " + definitionId);
            return;
        }

        // Get position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d position = transform.getPosition();
        float yaw = transform.getRotation().getYaw();
        float yOffset = definition.modelYOffset;

        Vec3 vehiclePos = new Vec3(
            (float) position.x,
            (float) position.y - yOffset,
            (float) position.z
        );

        // Create wrapper
        VehicleTypeCreator creator = registry.getCreator(definition.type);
        if (creator == null) return;

        World world = null;
        try {
            world = store.getExternalData().getWorld();
        } catch (Exception ignored) {}

        BaseVehicle vehicle = creator.createVehicle(definition, vehiclePos, yaw);
        vehicle.setWorld(world);
        vehicle.setEntityRef(ref);

        if (world != null) {
            var bridge = plugin.getEntityBridge();
            if (bridge != null) {
                vehicle.setCollisionChecker(bridge.createCollisionChecker(world));
            }
        }

        registry.trackVehicle(vehicle);
        logger.info("Reconnected vehicle from ECS: " + definitionId + " at " + vehiclePos +
            " (entityRef valid=" + (ref != null && ref.isValid()) + ", refIndex=" + entityIndex + ")");
    }

    public static void clearCache() {
        processedEntities.clear();
    }
}
