package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;

import javax.annotation.Nonnull;

/**
 * System that runs every tick to:
 * 1. Update vehicle physics (BaseVehicle.tick())
 * 2. Process NPC boat mounted player input (VehicleMountSystem)
 * 3. Process custom/simple boat driver input (MovementStates → VehicleInput)
 *
 * @author alexispace
 * @since 1.0
 */
public class VehicleTickSystem extends TickingSystem<EntityStore> {

    private static final VehicleLogger logger = VehicleLogger.console();
    private static final float DELTA_TIME = 0.05f; // 20 ticks per second

    @Override
    public void tick(float deltaTime, int tickCount, @Nonnull Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;

        VehicleRegistry registry = plugin.getRegistry();
        if (registry == null) return;

        // === 1. Tick NPC boat mounted players (input interception) ===
        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem != null) {
            mountSystem.tickMountedPlayers(store);
        }

        // === 2. Process input for custom/simple boats ===
        // For each vehicle, check if it has MountedByComponent and process driver input
        for (BaseVehicle vehicle : registry.getAllVehicles()) {
            if (vehicle.isDestroyed()) {
                continue;
            }

            Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
            if (vehicleRef == null || !vehicleRef.isValid()) {
                continue;
            }

            processCustomBoatInput(vehicle, vehicleRef, store);

            // === Update steering (rotation) based on controller's look direction ===
            updateVehicleSteering(vehicle, vehicleRef, store);
        }

        // === 3. Tick all vehicle physics ===
        // IMPORTANT: Physics MUST update BEFORE animation check
        // Animation depends on current velocity, which is updated by physics
        registry.tickAll(DELTA_TIME);

        // === 4. Update animations AFTER physics ===
        // Now that velocity is updated, we can correctly check if animation should play
        for (BaseVehicle vehicle : registry.getAllVehicles()) {
            if (vehicle.isDestroyed()) {
                continue;
            }

            Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
            if (vehicleRef == null || !vehicleRef.isValid()) {
                continue;
            }

            // === Update rowing animation based on current velocity ===
            updateVehicleAnimation(vehicle, vehicleRef, store);
        }
    }

    // Debug counter to limit log spam
    private int debugTickCounter = 0;

    /**
     * Update vehicle rotation based on the controller's look direction.
     * Only the player in a seat with canControl=true can steer.
     * This runs every tick for smooth steering.
     */
    private void updateVehicleSteering(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;

        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem == null) return;

        // Get vehicle definition
        VehicleDefinition definition = vehicle.getDefinition();
        if (definition == null || definition.seats.isEmpty()) {
            return;
        }

        // Find the player who can control this vehicle (has canControl=true seat)
        PlayerRef controllerPlayer = mountSystem.getVehicleController(vehicleRef, definition.seats);

        // Debug logging every 60 ticks (~3 seconds)
        debugTickCounter++;
        if (debugTickCounter % 60 == 0) {
            if (controllerPlayer == null) {
                logger.info("[STEERING DEBUG] No controller found for vehicle " + definition.id +
                           ". Seats with canControl: " + countCanControlSeats(definition));
            } else {
                logger.info("[STEERING DEBUG] Controller found: " + controllerPlayer.getUuid().toString().substring(0, 8) +
                           " for vehicle " + definition.id);
            }
        }

        if (controllerPlayer == null) {
            return; // No one with control authority mounted
        }

        // Get the controller's entity ref
        Ref<EntityStore> controllerRef = controllerPlayer.getReference();
        if (controllerRef == null || !controllerRef.isValid()) {
            return;
        }

        // Get controller's transform to get their look direction
        TransformComponent controllerTransform = store.getComponent(
            controllerRef, TransformComponent.getComponentType());
        if (controllerTransform == null) {
            return;
        }

        Vector3f controllerRotation = controllerTransform.getRotation();
        float controllerYaw = controllerRotation.getYaw();

        // Update vehicle's rotation to match controller's look direction
        vehicle.setRotationYaw(controllerYaw);
    }

    private String countCanControlSeats(VehicleDefinition def) {
        int canControl = 0;
        for (int i = 0; i < def.seats.size(); i++) {
            if (def.seats.get(i).canControl) {
                canControl++;
            }
        }
        return canControl + "/" + def.seats.size() + " seats have canControl=true";
    }

    /**
     * Process player input for custom/simple boats (non-NPC vehicles).
     * Uses VehicleMountSystem to find the controller and read their MovementStates.
     * Only processes input from passengers in seats with canControl=true.
     */
    private void processCustomBoatInput(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
        if (plugin == null) return;

        VehicleMountSystem mountSystem = plugin.getMountSystem();
        if (mountSystem == null) return;

        // Get vehicle definition
        VehicleDefinition definition = vehicle.getDefinition();
        if (definition == null || definition.seats.isEmpty()) {
            return;
        }

        // CRITICAL FIX: Verify vehicle actually has a driver FIRST
        // This prevents processing input from nearby players who aren't mounted
        if (!vehicle.hasDriver()) {
            return; // No driver = no input processing
        }

        // Find the player who can control this vehicle (using our seat tracking)
        var controllerPlayer = mountSystem.getVehicleController(vehicleRef, definition.seats);
        if (controllerPlayer == null) {
            return; // No one with control authority mounted
        }

        // Get the controller's entity ref
        Ref<EntityStore> controllerRef = controllerPlayer.getReference();
        if (controllerRef == null || !controllerRef.isValid()) {
            return;
        }

        // Get controller's MovementStatesComponent
        MovementStatesComponent movementComp = store.getComponent(
            controllerRef, MovementStatesComponent.getComponentType());
        if (movementComp == null) {
            return;
        }

        MovementStates states = movementComp.getMovementStates();
        if (states == null) {
            return;
        }

        // Convert MovementStates to VehicleInput
        BaseVehicle.VehicleInput input = new BaseVehicle.VehicleInput();

        // Log movement states every 20 ticks (1 second)
        if (debugTickCounter % 20 == 0) {
            logger.info("[INPUT DEBUG] walking=" + states.walking +
                       ", running=" + states.running +
                       ", swimming=" + states.swimming +
                       ", sprinting=" + states.sprinting +
                       ", crouching=" + states.crouching +
                       ", jumping=" + states.jumping);
        }

        // WASD keys - check walking/running/swimming for W key (forward movement)
        // Swimming state works with "Montar" MovementConfig when in water!
        if (states.walking || states.running || states.swimming) {
            input.forward = 1.0f;
        }

        // Crouching without movement = S key (back up slowly)
        if (states.crouching && !states.walking && !states.running) {
            input.backward = 0.5f;
        }

        // Note: A/D turning is handled via player look direction in VehicleControlSystem
        // The vehicle turns based on where the player is looking

        // === SPACE (jump) → BRAKE ===
        if (states.jumping) {
            input.brake = true;
            // Cancel the jump to prevent unwanted behavior
            states.jumping = false;
            states.swimJumping = false;
            movementComp.setMovementStates(states);
        }

        // === SHIFT (sprint) → BOOST (more speed) ===
        if (states.sprinting) {
            // Boost: increase forward input
            input.forward = 1.5f; // Boost multiplier
        }

        // Log applied input
        if (debugTickCounter % 20 == 0) {
            logger.info("[INPUT DEBUG] Applied input: forward=" + input.forward +
                       ", backward=" + input.backward +
                       ", brake=" + input.brake);
        }

        // Apply input to vehicle
        vehicle.setDriverInput(input);
    }

    /**
     * Update vehicle rowing animation based on current velocity.
     * Animation plays when moving > 0.1 blocks/tick (2 blocks/second).
     */
    private void updateVehicleAnimation(BaseVehicle vehicle, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        float speed = vehicle.getHorizontalSpeed();
        boolean hasDriver = vehicle.hasDriver();
        boolean shouldAnimate = vehicle.shouldAnimate();
        boolean isPlaying = vehicle.isAnimationPlaying();

        // Log animation state changes only (not every tick)
        // Add vehicle instance ID to track which boat is which
        String vehicleId = vehicle.getInstanceId().toString().substring(0, 8);

        // Log when animation state CHANGES or every 60 ticks for active boats
        boolean logThis = (shouldAnimate != isPlaying) ||
                         (debugTickCounter % 60 == 0 && (hasDriver || speed > 0.01f));

        if (logThis) {
            logger.info("[ANIM] ID:" + vehicleId + " " + vehicle.getDefinition().id +
                       " speed=" + String.format("%.3f", speed) +
                       " driver=" + hasDriver +
                       " pass=" + vehicle.getPassengerCount() +
                       " should=" + shouldAnimate +
                       " playing=" + isPlaying);
        }

        if (shouldAnimate && !isPlaying) {
            // Start rowing animation
            try {
                logger.info("Starting animation for " + vehicle.getDefinition().id +
                           " (speed=" + String.format("%.2f", speed) +
                           ", hasDriver=" + vehicle.hasDriver() + ")");

                String[] possibleNames = {"rowing", "wiosloo", "row", "Rowing", "Row"};
                boolean started = false;
                for (String animName : possibleNames) {
                    try {
                        AnimationUtils.playAnimation(
                            vehicleRef,
                            AnimationSlot.Action,
                            animName,
                            store
                        );
                        logger.info("✓ Started animation: " + animName);
                        started = true;
                        break;
                    } catch (Exception ignored) {
                    }
                }
                if (!started) {
                    logger.warning("Could not start any animation");
                }
                vehicle.setAnimationPlaying(true);
            } catch (Exception e) {
                logger.warning("Failed to start animation: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (!shouldAnimate && isPlaying) {
            // Stop rowing animation
            try {
                logger.info("Stopping animation for " + vehicle.getDefinition().id +
                           " (speed=" + String.format("%.2f", speed) +
                           ", hasDriver=" + vehicle.hasDriver() + ")");
                AnimationUtils.stopAnimation(
                    vehicleRef,
                    AnimationSlot.Action,
                    store
                );
                vehicle.setAnimationPlaying(false);
                logger.info("✓ Stopped animation");
            } catch (Exception e) {
                logger.warning("Failed to stop animation: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
