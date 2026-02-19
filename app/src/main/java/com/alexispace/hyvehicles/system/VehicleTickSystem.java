package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.Vec3;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class VehicleTickSystem extends TickingSystem<EntityStore> {
    private static final VehicleLogger logger = VehicleLogger.console();
    private static final float DELTA_TIME = 0.05f;
    private int debugTickCounter = 0;

    public void tick(float deltaTime, int tickCount, @Nonnull Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;
        VehicleRegistry registry = plugin.getRegistry();
        if (registry == null) return;

        // CRITICAL: Refresh all vehicle entity Refs by iterating ECS chunks.
        // Hytale's ECS moves entities between archetype chunks when components are
        // added/removed (e.g. internal systems adding physics/network components after
        // spawn). This invalidates the Ref returned by store.addEntity(). By iterating
        // chunks each tick, we get fresh Refs that point to the correct archetype/index.
        this.refreshVehicleRefs(store, registry);

        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem != null) {
            mountSystem.tickMountedPlayers(store);
        }

        for (BaseVehicle vehicle : registry.getAllVehicles()) {
            Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
            if (vehicle.isDestroyed() || vehicleRef == null || !vehicleRef.isValid()) continue;
            this.processCustomBoatInput(vehicle, vehicleRef, store);
            try {
                this.updateVehicleSteering(vehicle, vehicleRef, store);
            } catch (Exception e) {
                // silently ignore steering errors
            }
        }

        registry.tickAll(DELTA_TIME);

        boolean savePosition = this.debugTickCounter % 20 == 0;
        for (BaseVehicle vehicle : registry.getAllVehicles()) {
            Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
            if (vehicle.isDestroyed() || vehicleRef == null || !vehicleRef.isValid()) continue;
            this.syncPositionToEntity(vehicle, vehicleRef, store);
            if (savePosition) {
                this.persistPosition(vehicle, vehicleRef, store);
            }
        }
    }

    private void refreshVehicleRefs(Store<EntityStore> store, VehicleRegistry registry) {
        try {
            com.hypixel.hytale.component.query.Query<EntityStore> vehicleQuery =
                Archetype.of(VehicleDataComponent.getComponentType());
            java.util.function.BiConsumer<ArchetypeChunk<EntityStore>, com.hypixel.hytale.component.CommandBuffer<EntityStore>> refresher =
                (chunk, commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        try {
                            NetworkId netId = chunk.getComponent(i, NetworkId.getComponentType());
                            if (netId == null || netId.getId() < 0) continue;

                            BaseVehicle vehicle = registry.getVehicleByNetworkId(netId.getId());
                            if (vehicle == null) continue;

                            Ref<EntityStore> freshRef = chunk.getReferenceTo(i);
                            if (freshRef != null && freshRef.isValid()) {
                                vehicle.setEntityRef(freshRef);
                            }
                        } catch (Exception e) {
                            // Skip entities we can't read
                        }
                    }
                };
            store.forEachChunk(vehicleQuery, refresher);
        } catch (Exception e) {
            if (debugTickCounter % 200 == 0) {
                logger.warning("[REF REFRESH] Failed to iterate chunks: " + e.getMessage());
            }
        }
    }

    private void persistPosition(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        try {
            VehicleDataComponent vehicleData = store.getComponent(vehicleRef, VehicleDataComponent.getComponentType());
            if (vehicleData != null) {
                Vec3 pos = vehicle.getPosition();
                vehicleData.setPosition(pos.x, pos.y, pos.z, vehicle.getRotationYaw());
            }
        } catch (Exception e) {
            // silently ignore
        }
    }

    private void updateVehicleSteering(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;
        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem == null) return;

        VehicleDefinition definition = vehicle.getDefinition();
        if (definition == null || definition.seats.isEmpty()) return;

        PlayerRef controllerPlayer = mountSystem.getVehicleController(vehicle.getNetworkId(), definition.seats);
        if (controllerPlayer == null) return;

        Ref controllerRef = controllerPlayer.getReference();
        if (controllerRef == null || !controllerRef.isValid()) return;

        TransformComponent controllerTransform = (TransformComponent) store.getComponent(controllerRef, TransformComponent.getComponentType());
        if (controllerTransform == null) return;

        Vector3f controllerRotation = controllerTransform.getRotation();
        float controllerYaw = controllerRotation.getYaw();
        vehicle.setRotationYaw(controllerYaw);
    }

    private void processCustomBoatInput(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;
        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem == null) return;
        VehicleDefinition definition = vehicle.getDefinition();
        if (definition == null || definition.seats.isEmpty()) return;

        boolean hasDriver = vehicle.hasDriver();
        if (this.debugTickCounter % 100 == 0 && hasDriver) {
            logger.info("[INPUT-DEBUG] Vehicle " + vehicle.getDefinition().id + " hasDriver=" + hasDriver + ", networkId=" + vehicle.getNetworkId());
        }
        if (!hasDriver) return;

        PlayerRef controllerPlayer = mountSystem.getVehicleController(vehicle.getNetworkId(), definition.seats);
        if (this.debugTickCounter % 100 == 0) {
            logger.info("[INPUT-DEBUG] getVehicleController returned: " + (controllerPlayer != null ? "PLAYER" : "NULL"));
        }
        if (controllerPlayer == null) return;

        Ref controllerRef = controllerPlayer.getReference();
        if (controllerRef == null || !controllerRef.isValid()) return;

        MovementStatesComponent movementComp;
        try {
            movementComp = (MovementStatesComponent) store.getComponent(controllerRef, MovementStatesComponent.getComponentType());
        } catch (ArrayIndexOutOfBoundsException e) {
            return;
        }
        if (movementComp == null) return;

        MovementStates states = movementComp.getMovementStates();
        if (states == null) return;

        // Log ACTUAL movement states every second
        if (this.debugTickCounter % 20 == 0) {
            logger.info("[STATES] walking=" + states.walking + ", running=" + states.running
                + ", sprinting=" + states.sprinting + ", crouching=" + states.crouching
                + ", jumping=" + states.jumping + ", swimming=" + states.swimming);
        }

        BaseVehicle.VehicleInput input = new BaseVehicle.VehicleInput();

        if (states.walking || states.running || states.swimming) {
            input.forward = 1.0f;
        }
        if (states.crouching && !states.walking && !states.running) {
            input.backward = 0.5f;
        }
        if (states.jumping) {
            input.brake = true;
            states.jumping = false;
            states.swimJumping = false;
            movementComp.setMovementStates(states);
        }
        if (states.sprinting) {
            input.forward = 1.5f;
        }

        if (this.debugTickCounter % 100 == 0 && input.forward > 0) {
            logger.info("[INPUT-DEBUG] WASD detected! forward=" + input.forward
                + " (walking=" + states.walking + ", running=" + states.running
                + ", sprinting=" + states.sprinting + ")");
        }

        vehicle.setDriverInput(input);
    }

    private void syncPositionToEntity(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        try {
            // Periodically verify ref still points to our entity (detect ECS compaction drift)
            if (vehicle.getNetworkId() >= 0 && this.debugTickCounter % 200 == 0) {
                try {
                    NetworkId refNetId = (NetworkId) store.getComponent(vehicleRef, NetworkId.getComponentType());
                    if (refNetId != null && refNetId.getId() != vehicle.getNetworkId()) {
                        logger.warning("[REF STALE] Vehicle " + vehicle.getDefinition().id
                            + " entityRef points to NetworkId=" + refNetId.getId()
                            + " but expected " + vehicle.getNetworkId()
                            + " - position sync writing to WRONG entity!");
                        return; // Don't write to wrong entity
                    }
                } catch (Exception e) {
                    // ref may be completely invalid - skip this tick
                }
            }
            TransformComponent transform = (TransformComponent) store.getComponent(vehicleRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vec3 vehiclePos = vehicle.getPosition();
                float vehicleYaw = vehicle.getRotationYaw();
                float modelYOffset = vehicle.getDefinition().modelYOffset;
                Vector3d oldPos = transform.getPosition();

                // Log position changes for 20 ticks after dismount
                int ticksSinceDismount = vehicle.getTicksSinceDismount();
                if (ticksSinceDismount >= 0 && ticksSinceDismount < 20) {
                    logger.info("[POST-DISMOUNT T+" + ticksSinceDismount + "] " + vehicle.getDefinition().id
                        + " | Physics pos: (" + String.format("%.2f", vehiclePos.x) + ", " + String.format("%.2f", vehiclePos.y) + ", " + String.format("%.2f", vehiclePos.z) + ")"
                        + " | Entity pos: (" + String.format("%.2f", oldPos.x) + ", " + String.format("%.2f", oldPos.y) + ", " + String.format("%.2f", oldPos.z) + ")"
                        + " | Vel: (" + String.format("%.3f", vehicle.getVelocity().x) + ", " + String.format("%.3f", vehicle.getVelocity().y) + ", " + String.format("%.3f", vehicle.getVelocity().z) + ")");
                }

                // Detect if Hytale modified TransformComponent between our syncs
                float expectedX = vehiclePos.x;
                float expectedY = vehiclePos.y + modelYOffset;
                float expectedZ = vehiclePos.z;
                float driftX = (float) Math.abs(oldPos.x - expectedX);
                float driftZ = (float) Math.abs(oldPos.z - expectedZ);
                float driftY = (float) Math.abs(oldPos.y - expectedY);
                if (driftX > 1.0f || driftZ > 1.0f || driftY > 2.0f) {
                    logger.warning("[POS JUMP] " + vehicle.getDefinition().id
                        + " | Entity moved externally! Expected: (" + String.format("%.2f", expectedX) + ", " + String.format("%.2f", expectedY) + ", " + String.format("%.2f", expectedZ) + ")"
                        + " | Actual: (" + String.format("%.2f", oldPos.x) + ", " + String.format("%.2f", oldPos.y) + ", " + String.format("%.2f", oldPos.z) + ")"
                        + " | Drift: (" + String.format("%.2f", driftX) + ", " + String.format("%.2f", driftY) + ", " + String.format("%.2f", driftZ) + ")"
                        + " | hasDriver=" + vehicle.hasDriver());
                }

                // Periodic position sync logging (reduced frequency)
                if (this.debugTickCounter % 200 == 0 && vehicle.hasDriver()) {
                    Vec3 vel = vehicle.getVelocity();
                    logger.info("[POS SYNC] " + vehicle.getDefinition().id
                        + " at (" + String.format("%.2f", vehiclePos.x) + ", " + String.format("%.2f", vehiclePos.y) + ", " + String.format("%.2f", vehiclePos.z) + ")"
                        + " | Vel: (" + String.format("%.2f", vel.x) + ", " + String.format("%.2f", vel.z) + ")");
                }
                transform.setPosition(new Vector3d((double) vehiclePos.x, (double) (vehiclePos.y + modelYOffset), (double) vehiclePos.z));
                transform.setRotation(new Vector3f(0.0f, vehicleYaw, 0.0f));
            }
        } catch (Exception e) {
            if (this.debugTickCounter % 60 == 0) {
                logger.warning("[POSITION SYNC] Failed to sync position: " + e.getMessage());
            }
        }
    }

    private void syncPositionFromEntity(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        try {
            TransformComponent transform = (TransformComponent) store.getComponent(vehicleRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d entityPos = transform.getPosition();
                Vector3f entityRot = transform.getRotation();
                float modelYOffset = vehicle.getDefinition().modelYOffset;
                vehicle.setPosition((float) entityPos.x, (float) entityPos.y - modelYOffset, (float) entityPos.z);
                vehicle.setRotationYaw(entityRot.getYaw());
            }
        } catch (Exception e) {
            // silently ignore
        }
    }
}
