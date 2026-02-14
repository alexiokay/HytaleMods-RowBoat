package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class VehicleReconnectSystem extends RefChangeSystem<EntityStore, VehicleDataComponent> {

    private static final VehicleLogger logger = VehicleLogger.console();
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
        int entityIndex = ref.getIndex();
        String definitionId = component.getDefinitionId();
        logger.info("[RECONNECT] onComponentAdded - entityIndex=" + entityIndex + ", definitionId=" + definitionId);

        if (processedEntities.contains(entityIndex)) {
            logger.info("[RECONNECT] SKIPPING - entity already processed");
            return;
        }
        processedEntities.add(entityIndex);

        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) {
            return;
        }

        VehicleRegistry registry = plugin.getRegistry();
        if (registry == null) {
            return;
        }

        // If this fires during our own spawn, skip — it's our entity being created.
        if (registry.isSpawning()) {
            logger.info("[RECONNECT] SKIPPING - spawnVehicle in progress");
            return;
        }

        // This is an ECS-persisted entity from a previous session that Hytale restored.
        // We use JSON persistence as the source of truth, so these stale entities must be
        // removed to prevent duplicates with wrong positions.
        logger.info("[RECONNECT] Removing stale ECS-persisted entity: " + definitionId + " at index=" + entityIndex);
        try {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            logger.info("[RECONNECT] Queued removal of stale entity index=" + entityIndex);
        } catch (Exception e) {
            logger.warning("[RECONNECT] Failed to remove stale entity: " + e.getMessage());
        }
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull VehicleDataComponent component,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                @Nonnull VehicleDataComponent oldComponent,
                                @Nonnull VehicleDataComponent newComponent,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No-op: we only care about stale entities appearing on world load
    }

    public static void clearCache() {
        logger.info("[RECONNECT] Clearing processedEntities cache - size was " + processedEntities.size());
        processedEntities.clear();
    }
}
