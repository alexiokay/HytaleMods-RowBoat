package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles vehicle mounting using Hytale's built-in NPCMountComponent.
 *
 * Features:
 * - NPCMountComponent on VEHICLE for proper mount behavior
 * - Input interception for custom vehicle controls:
 *   - SPACE (jump) → brake/slowdown instead of jumping
 *   - SHIFT (sprint) → speed boost
 *
 * @author alexispace
 * @since 1.0
 */
public class VehicleMountSystem {

    private final VehicleLogger logger;

    /**
     * Info about a mounted player.
     */
    private static class MountInfo {
        final PlayerRef player;
        final Ref<EntityStore> vehicleRef;
        final int seatIndex;

        MountInfo(PlayerRef player, Ref<EntityStore> vehicleRef, int seatIndex) {
            this.player = player;
            this.vehicleRef = vehicleRef;
            this.seatIndex = seatIndex;
        }
    }

    // Track mounted players (player UUID -> mount info)
    private final Map<UUID, MountInfo> mountedPlayers = new ConcurrentHashMap<>();

    // Track previous crouching state for edge detection (player UUID -> was crouching)
    private final Map<UUID, Boolean> previousCrouchingState = new ConcurrentHashMap<>();

    // Brake/boost multipliers
    private static final float BRAKE_MULTIPLIER = 0.5f;  // 50% speed when braking
    private static final float BOOST_MULTIPLIER = 1.5f;  // 150% speed when boosting

    public VehicleMountSystem(VehicleLogger logger) {
        this.logger = logger;
    }

    /**
     * Mount a player onto a vehicle entity using NPCMountComponent.
     */
    public boolean mountPlayer(PlayerRef player, Ref<EntityStore> vehicleRef,
                               Store<EntityStore> store,
                               float seatOffsetX, float seatOffsetY, float seatOffsetZ,
                               int seatIndex) {
        if (player == null || vehicleRef == null || !vehicleRef.isValid()) {
            logger.warning("Cannot mount: player or vehicle is null/invalid");
            return false;
        }

        try {
            UUID playerUUID = player.getUuid();

            // If already mounted, dismount first
            if (mountedPlayers.containsKey(playerUUID)) {
                logger.info("Player is already mounted - dismounting first");
                dismountPlayer(player, store);
            }

            // Verify vehicle has VehicleDataComponent
            VehicleDataComponent vehicleData = store.getComponent(
                vehicleRef, VehicleDataComponent.getComponentType());
            if (vehicleData == null) {
                logger.warning("Cannot mount: entity is not a vehicle");
                return false;
            }

            // Get vehicle NetworkId for packet
            NetworkId networkId = store.getComponent(vehicleRef, NetworkId.getComponentType());
            if (networkId == null) {
                logger.warning("Vehicle has no NetworkId - cannot send mount packet");
                return false;
            }

            // NOTE: We do NOT add NPCMountComponent to the vehicle!
            // NPCMountComponent causes world corruption on reload because Hytale expects
            // NPCEntity component which our vehicles don't have.
            // The MountNPC packet alone is sufficient for visual mounting.

            // Remove any existing NPCMountComponent (cleanup from old versions)
            var existingMount = store.getComponent(
                vehicleRef, NPCMountComponent.getComponentType());
            if (existingMount != null) {
                store.removeComponent(vehicleRef, NPCMountComponent.getComponentType());
                logger.info("Removed stale NPCMountComponent from vehicle");
            }

            // Send MountNPC packet to client to visually mount the player
            int vehicleNetworkId = networkId.getId();
            MountNPC mountPacket = new MountNPC(seatOffsetX, seatOffsetY, seatOffsetZ, vehicleNetworkId);
            logger.info("MountNPC packet: offset=(" + seatOffsetX + ", " + seatOffsetY + ", " + seatOffsetZ + "), networkId=" + vehicleNetworkId);
            player.getPacketHandler().write(mountPacket);

            // === APPLY MOVEMENTCONFIG TO DISABLE JUMPING ===
            applyMovementConfig(player, store, "Montar");

            // === SET MOUNTING FLAG ===
            // Set mounting=true so we can detect when Hytale clears it on dismount
            try {
                Ref<EntityStore> playerEntityRef = player.getReference();
                if (playerEntityRef != null && playerEntityRef.isValid()) {
                    MovementStatesComponent movementComp = store.getComponent(
                        playerEntityRef, MovementStatesComponent.getComponentType());
                    if (movementComp != null) {
                        MovementStates states = movementComp.getMovementStates();
                        if (states != null) {
                            states.mounting = true;
                            movementComp.setMovementStates(states);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to set mounting flag: " + e.getMessage());
            }

            // Track this mount with full info
            mountedPlayers.put(playerUUID, new MountInfo(player, vehicleRef, seatIndex));

            // Also inform the BaseVehicle about the mount (for driver input processing)
            try {
                var plugin = com.alexispace.hyvehicles.HytaleVehiclesPlugin.getInstance();
                if (plugin != null) {
                    var registry = plugin.getRegistry();
                    if (registry != null) {
                        var vehicle = registry.getVehicleByEntityRef(vehicleRef);
                        if (vehicle != null) {
                            vehicle.mount(playerUUID, seatIndex);
                            logger.info("Called BaseVehicle.mount() for player " + playerUUID);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to call BaseVehicle.mount(): " + e.getMessage());
            }

            logger.info("Mounted player to vehicle seat " + seatIndex + " at offset: (" +
                seatOffsetX + ", " + seatOffsetY + ", " + seatOffsetZ + ")");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to mount player: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Dismount a player from their vehicle.
     */
    public boolean dismountPlayer(PlayerRef player, Store<EntityStore> store) {
        if (player == null) {
            return false;
        }

        try {
            UUID playerUUID = player.getUuid();
            MountInfo mountInfo = mountedPlayers.remove(playerUUID);

            // === RESET MOVEMENT SETTINGS TO RE-ENABLE JUMPING ===
            // This is how Hytale's MountPlugin.resetOriginalPlayerMovementSettings works
            resetMovementConfig(player, store);

            // Send DismountNPC packet to client
            DismountNPC dismountPacket = new DismountNPC();
            player.getPacketHandler().write(dismountPacket);

            // Remove NPCMountComponent from vehicle if we have the reference
            Ref<EntityStore> vehicleRef = mountInfo != null ? mountInfo.vehicleRef : null;
            if (vehicleRef != null && vehicleRef.isValid()) {
                var mountComp = store.getComponent(
                    vehicleRef, NPCMountComponent.getComponentType());
                if (mountComp != null) {
                    store.removeComponent(vehicleRef, NPCMountComponent.getComponentType());
                }

                // Vacate the seat on the vehicle
                VehicleDataComponent vehicleData = store.getComponent(
                    vehicleRef, VehicleDataComponent.getComponentType());
                if (vehicleData != null && mountInfo != null) {
                    vehicleData.vacateSeat(mountInfo.seatIndex);
                    logger.info("Vacated seat " + mountInfo.seatIndex);
                }

                // Also inform the BaseVehicle about the dismount
                try {
                    var plugin = com.alexispace.hyvehicles.HytaleVehiclesPlugin.getInstance();
                    if (plugin != null) {
                        var registry = plugin.getRegistry();
                        if (registry != null) {
                            var vehicle = registry.getVehicleByEntityRef(vehicleRef);
                            if (vehicle != null) {
                                vehicle.dismount(playerUUID);
                                logger.info("Called BaseVehicle.dismount() for player " + playerUUID);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to call BaseVehicle.dismount(): " + e.getMessage());
                }
            }

            logger.info("Dismounted player from vehicle");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to dismount player: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Called each tick to:
     * 1. Check for invalid mounts
     * 2. Intercept player input for custom vehicle controls
     */
    public void tickMountedPlayers(Store<EntityStore> store) {
        if (mountedPlayers.isEmpty()) return;

        var iterator = mountedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            MountInfo mountInfo = entry.getValue();
            PlayerRef player = mountInfo.player;
            Ref<EntityStore> vehicleRef = mountInfo.vehicleRef;

            // Check if player is still valid
            if (player == null || !player.isValid()) {
                iterator.remove();
                logger.info("Auto-removed mount tracking - player no longer valid");
                continue;
            }

            // Check if vehicle is still valid
            if (vehicleRef == null || !vehicleRef.isValid()) {
                iterator.remove();
                logger.info("Auto-removed mount tracking - vehicle no longer valid");
                continue;
            }

            // Check if vehicle still has VehicleDataComponent (is still a vehicle)
            // Wrapped in try-catch because entity may become invalid between isValid() check and getComponent()
            VehicleDataComponent vehicleData;
            try {
                vehicleData = store.getComponent(
                    vehicleRef, VehicleDataComponent.getComponentType());
            } catch (ArrayIndexOutOfBoundsException e) {
                // Entity access failed due to store index issue - skip this tick, don't remove tracking
                // This happens when the store is being modified concurrently
                continue;
            } catch (Exception e) {
                // Other unexpected errors - skip this tick but don't remove tracking
                continue;
            }
            if (vehicleData == null) {
                iterator.remove();
                logger.info("Auto-removed mount tracking - vehicle data component removed");
                continue;
            }

            // CHECK: Has player actually dismounted?
            // When player presses F to dismount, Hytale clears the 'mounting' flag
            try {
                Ref<EntityStore> playerEntityRef = player.getReference();
                if (playerEntityRef != null && playerEntityRef.isValid()) {
                    MovementStatesComponent movementComp = store.getComponent(
                        playerEntityRef, MovementStatesComponent.getComponentType());

                    if (movementComp != null) {
                        MovementStates states = movementComp.getMovementStates();

                        // If mounting flag is false, player has dismounted
                        if (states != null && !states.mounting) {
                            UUID playerUUID = entry.getKey();
                            logger.info("[DISMOUNT] Player dismounted - cleaning up mount tracking");

                            // Clean up
                            resetMovementConfig(player, store);
                            vehicleData.vacateSeat(mountInfo.seatIndex);
                            previousCrouchingState.remove(playerUUID); // Clean up crouch state tracking

                            try {
                                var plugin = com.alexispace.hyvehicles.HytaleVehiclesPlugin.getInstance();
                                if (plugin != null) {
                                    var registry = plugin.getRegistry();
                                    if (registry != null) {
                                        var vehicle = registry.getVehicleByEntityRef(vehicleRef);
                                        if (vehicle != null) {
                                            vehicle.dismount(playerUUID);
                                        }
                                    }
                                }
                            } catch (Exception ve) {
                                logger.warning("Failed to clear vehicle driver: " + ve.getMessage());
                            }

                            iterator.remove();
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to check dismount state: " + e.getMessage());
            }

            // === INPUT INTERCEPTION ===
            processPlayerInput(player, vehicleRef, store);
        }
    }

    /**
     * Process player input and apply custom vehicle controls.
     * - SPACE (jumping) → brake/slowdown
     * - SHIFT (sprinting) → speed boost
     * - Also ensures 'mounting' flag stays set to disable client-side jumping
     */
    private void processPlayerInput(PlayerRef player, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }

            // Get player's movement states
            MovementStatesComponent movementComp = store.getComponent(
                playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                return;
            }

            MovementStates states = movementComp.getMovementStates();
            if (states == null) {
                return;
            }

            // Get vehicle data for speed control
            VehicleDataComponent vehicleData = store.getComponent(
                vehicleRef, VehicleDataComponent.getComponentType());

            boolean statesModified = false;

            // === SPACE KEY (jumping) → BRAKE ===
            if (states.jumping || states.swimJumping) {
                // Cancel any jump state
                states.jumping = false;
                states.swimJumping = false;
                statesModified = true;

                // Apply brake to vehicle
                if (vehicleData != null) {
                    applyBrake(vehicleRef, vehicleData, store);
                }
            }

            // === SHIFT KEY (sprinting) → BOOST ===
            if (states.sprinting) {
                // Apply boost to vehicle
                if (vehicleData != null) {
                    applyBoost(vehicleRef, vehicleData, store);
                }
            }

            // === CTRL KEY (crouching) → SEAT CYCLING ===
            UUID playerUUID = player.getUuid();
            Boolean wasCrouching = previousCrouchingState.get(playerUUID);
            boolean isCrouching = states.crouching;

            // Detect edge: crouching just started (false → true)
            if (isCrouching && (wasCrouching == null || !wasCrouching)) {
                // Player just pressed CTRL - cycle to next seat
                if (vehicleData != null) {
                    logger.info("[SEAT CYCLE] Player pressed CTRL - attempting to cycle seat");

                    // Get vehicle definition to access seat info
                    try {
                        var plugin = com.alexispace.hyvehicles.HytaleVehiclesPlugin.getInstance();
                        if (plugin != null) {
                            var registry = plugin.getRegistry();
                            if (registry != null) {
                                var vehicle = registry.getVehicleByEntityRef(vehicleRef);
                                if (vehicle != null) {
                                    var definition = vehicle.getDefinition();
                                    boolean seatsPreScaled = definition.blockyModelPath != null && !definition.blockyModelPath.isEmpty();
                                    boolean success = cycleSeat(
                                        player, store, vehicleData, definition.seats,
                                        definition.modelScale, definition.modelYOffset,
                                        seatsPreScaled
                                    );
                                    if (success) {
                                        logger.info("[SEAT CYCLE] Successfully cycled seat");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to cycle seat: " + e.getMessage());
                    }
                }
            }

            // Update previous crouching state
            previousCrouchingState.put(playerUUID, isCrouching);

            // Only call setMovementStates if we modified something
            if (statesModified) {
                movementComp.setMovementStates(states);
            }

        } catch (Exception e) {
            // Silently ignore input processing errors
        }
    }

    /**
     * Apply brake/slowdown to vehicle.
     */
    private void applyBrake(Ref<EntityStore> vehicleRef, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        // For now, we'll set a "braking" flag on the vehicle data
        // The physics system can check this flag and reduce speed
        vehicleData.setBraking(true);

        // Log only occasionally to avoid spam
        // logger.info("Vehicle braking");
    }

    /**
     * Apply speed boost to vehicle.
     */
    private void applyBoost(Ref<EntityStore> vehicleRef, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        // Set a "boosting" flag on the vehicle data
        // The physics system can check this flag and increase speed
        vehicleData.setBoosting(true);

        // Log only occasionally to avoid spam
        // logger.info("Vehicle boosting");
    }

    /**
     * Check if a player is currently mounted.
     */
    public boolean isPlayerMounted(PlayerRef player, Store<EntityStore> store) {
        if (player == null) return false;
        return mountedPlayers.containsKey(player.getUuid());
    }

    /**
     * Get the vehicle a player is mounted on.
     */
    public Ref<EntityStore> getMountedVehicle(PlayerRef player, Store<EntityStore> store) {
        if (player == null) return null;
        MountInfo info = mountedPlayers.get(player.getUuid());
        return info != null ? info.vehicleRef : null;
    }

    /**
     * Get the current seat index for a mounted player.
     * @return Seat index (0-based), or -1 if not mounted
     */
    public int getCurrentSeatIndex(PlayerRef player) {
        if (player == null) return -1;
        MountInfo info = mountedPlayers.get(player.getUuid());
        return info != null ? info.seatIndex : -1;
    }

    // Debug counter to avoid log spam
    private int controllerDebugCounter = 0;

    /**
     * Find the player who can control a specific vehicle.
     * Returns the PlayerRef of a player in a seat with canControl=true, or null if none.
     *
     * @param vehicleRef The vehicle entity reference
     * @param seats List of seat definitions with canControl flags
     * @return PlayerRef of the controller, or null if no controller mounted
     */
    public PlayerRef getVehicleController(Ref<EntityStore> vehicleRef,
                                           java.util.List<com.alexispace.hyvehicles.definition.SeatDefinition> seats) {
        if (vehicleRef == null || seats == null || seats.isEmpty()) {
            return null;
        }

        // Debug: log all mounted players on this vehicle (every 100 ticks)
        controllerDebugCounter++;
        boolean shouldLog = (controllerDebugCounter % 100 == 0);

        if (shouldLog) {
            logger.info("[CONTROLLER DEBUG] Looking for controller on vehicleRef=" + vehicleRef +
                       ", mountedPlayers.size()=" + mountedPlayers.size());
        }

        int playersOnThisVehicle = 0;
        for (MountInfo info : mountedPlayers.values()) {
            if (shouldLog) {
                logger.info("[CONTROLLER DEBUG] Checking player, stored vehicleRef=" + info.vehicleRef +
                           ", equals=" + (info.vehicleRef != null ? info.vehicleRef.equals(vehicleRef) : "null"));
            }
            // Check if this player is on the target vehicle
            if (info.vehicleRef != null && info.vehicleRef.equals(vehicleRef)) {
                playersOnThisVehicle++;
                int seatIndex = info.seatIndex;

                if (shouldLog) {
                    String playerId = info.player != null ? info.player.getUuid().toString().substring(0, 8) : "null";
                    boolean canControl = (seatIndex >= 0 && seatIndex < seats.size()) ?
                                          seats.get(seatIndex).canControl : false;
                    logger.info("[CONTROLLER DEBUG] Player '" + playerId +
                               "' in seat " + seatIndex +
                               ", canControl=" + canControl +
                               " (seat has canControl=" + (seatIndex < seats.size() ? seats.get(seatIndex).canControl : "N/A") + ")");
                }

                // Check if their seat has canControl=true
                if (seatIndex >= 0 && seatIndex < seats.size()) {
                    var seat = seats.get(seatIndex);
                    if (seat.canControl) {
                        return info.player;
                    }
                }
            }
        }

        if (shouldLog && playersOnThisVehicle > 0) {
            logger.info("[CONTROLLER DEBUG] " + playersOnThisVehicle + " players mounted but none have canControl=true");
        }

        return null;
    }

    /**
     * Cycle to the next available seat on the same vehicle.
     * @param seatsPreScaled If true, seats are already in final coordinates (from blockymodel)
     * @return true if successfully cycled to a new seat, false if no other seats available
     */
    public boolean cycleSeat(PlayerRef player, Store<EntityStore> store,
                             VehicleDataComponent vehicleData,
                             java.util.List<com.alexispace.hyvehicles.definition.SeatDefinition> seats,
                             float modelScale, float modelYOffset, boolean seatsPreScaled) {
        if (player == null) return false;

        UUID playerUUID = player.getUuid();
        MountInfo currentMount = mountedPlayers.get(playerUUID);
        if (currentMount == null) {
            logger.info("Cannot cycle seat - player not mounted");
            return false;
        }

        Ref<EntityStore> vehicleRef = currentMount.vehicleRef;
        int currentSeatIndex = currentMount.seatIndex;
        int totalSeats = seats.size();

        if (totalSeats <= 1) {
            logger.info("Cannot cycle seat - vehicle has only one seat");
            return false;
        }

        // Find next available seat (wrap around)
        int nextSeatIndex = -1;
        for (int i = 1; i < totalSeats; i++) {
            int candidateIndex = (currentSeatIndex + i) % totalSeats;
            if (!vehicleData.isSeatOccupied(candidateIndex)) {
                nextSeatIndex = candidateIndex;
                break;
            }
        }

        if (nextSeatIndex < 0) {
            logger.info("Cannot cycle seat - no other seats available");
            return false;
        }

        // Get the new seat definition
        var newSeat = seats.get(nextSeatIndex);
        float seatX, seatY, seatZ;
        if (seatsPreScaled) {
            // Seats from blockymodel are already in final coordinates
            seatX = newSeat.x;
            seatY = newSeat.y - modelYOffset;
            seatZ = newSeat.z;
        } else {
            float scale = modelScale > 0 ? modelScale : 1.0f;
            seatX = newSeat.x * scale;
            seatY = (newSeat.y * scale) - modelYOffset;
            seatZ = newSeat.z * scale;
        }

        try {
            // Update seat occupancy
            vehicleData.vacateSeat(currentSeatIndex);
            vehicleData.occupySeat(nextSeatIndex);

            // Update mount tracking
            mountedPlayers.put(playerUUID, new MountInfo(player, vehicleRef, nextSeatIndex));

            // Also update BaseVehicle's driver tracking (for input processing)
            try {
                var plugin = com.alexispace.hyvehicles.HytaleVehiclesPlugin.getInstance();
                if (plugin != null) {
                    var registry = plugin.getRegistry();
                    if (registry != null) {
                        var vehicle = registry.getVehicleByEntityRef(vehicleRef);
                        if (vehicle != null) {
                            // Dismount from old seat, mount to new seat
                            vehicle.dismount(playerUUID);
                            vehicle.mount(playerUUID, nextSeatIndex);
                            logger.info("Updated BaseVehicle seat: " + currentSeatIndex + " -> " + nextSeatIndex);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to update BaseVehicle during seat cycle: " + e.getMessage());
            }

            // Send new MountNPC packet to update client seat position
            NetworkId networkId = store.getComponent(vehicleRef, NetworkId.getComponentType());
            if (networkId != null) {
                MountNPC mountPacket = new MountNPC(seatX, seatY, seatZ, networkId.getId());
                player.getPacketHandler().write(mountPacket);
            }

            // Re-apply movement config to ensure jump stays disabled
            applyMovementConfig(player, store, "Montar");

            logger.info("Cycled player from seat " + currentSeatIndex + " to seat " + nextSeatIndex +
                       " at offset: (" + seatX + ", " + seatY + ", " + seatZ + ")");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to cycle seat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a player is currently crouching.
     */
    public boolean isPlayerCrouching(PlayerRef player, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return false;
            }

            MovementStatesComponent movementComp = store.getComponent(
                playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                return false;
            }

            MovementStates states = movementComp.getMovementStates();
            return states != null && states.crouching;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clear all mount tracking and reset all players' MovementConfigs.
     * CRITICAL: Call this when players leave to restore their movement (jumping, etc.)
     */
    public void clearAllMounts(Store<EntityStore> store) {
        // Reset MovementConfig for all mounted players before clearing
        for (Map.Entry<UUID, MountInfo> entry : mountedPlayers.entrySet()) {
            try {
                PlayerRef player = entry.getValue().player;
                if (player != null) {
                    resetMovementConfig(player, store);
                    logger.info("Reset MovementConfig for player " + entry.getKey() + " before clearing mounts");
                }
            } catch (Exception e) {
                logger.warning("Failed to reset MovementConfig for player " + entry.getKey() + ": " + e.getMessage());
            }
        }
        mountedPlayers.clear();
    }

    /**
     * Clear all mount tracking WITHOUT resetting MovementConfigs.
     * Only use this if you're certain players don't need their movement reset.
     * @deprecated Use clearAllMounts(Store) instead to properly reset player movement
     */
    @Deprecated
    public void clearAllMounts() {
        logger.warning("clearAllMounts() called without Store - player MovementConfigs will NOT be reset!");
        mountedPlayers.clear();
    }

    private boolean methodsLogged = false;

    /**
     * Apply MovementConfig to the player via MovementManager.
     * This is exactly how Hytale's ActionMount applies MovementConfig.
     *
     * @param player The player to apply config to
     * @param store The entity store
     * @param movementConfigId The MovementConfig asset ID (e.g., "Montar")
     */
    private void applyMovementConfig(PlayerRef player, Store<EntityStore> store, String movementConfigId) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                logger.warning("Cannot apply MovementConfig - player entity ref invalid");
                return;
            }

            // Get MovementConfig asset by ID
            MovementConfig movementConfig = (MovementConfig) MovementConfig.getAssetMap().getAsset(movementConfigId);
            if (movementConfig == null) {
                logger.warning("MovementConfig '" + movementConfigId + "' not found in asset map");
                return;
            }

            // Get player's MovementManager component
            MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
            if (movementManager == null) {
                logger.warning("Player has no MovementManager component");
                return;
            }

            // Get player's PhysicsValues component
            PhysicsValues physicsValues = store.getComponent(playerEntityRef, PhysicsValues.getComponentType());
            if (physicsValues == null) {
                logger.warning("Player has no PhysicsValues component");
                return;
            }

            // Get player's Player component for GameMode
            Player playerComponent = store.getComponent(playerEntityRef, Player.getComponentType());
            if (playerComponent == null) {
                logger.warning("Player has no Player component");
                return;
            }

            // Apply MovementConfig exactly like Hytale's ActionMount does:
            // 1. setDefaultSettings with the config packet, physics values, and game mode
            // 2. applyDefaultSettings to activate them
            // 3. update to send to client
            movementManager.setDefaultSettings(movementConfig.toPacket(), physicsValues, playerComponent.getGameMode());
            movementManager.applyDefaultSettings();
            movementManager.update(player.getPacketHandler());

            logger.info("Applied MovementConfig '" + movementConfigId + "' to player (JumpForce should be 0)");

        } catch (Exception e) {
            logger.warning("Failed to apply MovementConfig: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reset the player's movement settings to defaults.
     * This is how Hytale's MountPlugin.resetOriginalPlayerMovementSettings works.
     */
    private void resetMovementConfig(PlayerRef player, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                logger.warning("Cannot reset MovementConfig - player entity ref invalid");
                return;
            }

            // Get player's MovementManager component
            MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
            if (movementManager == null) {
                logger.warning("Player has no MovementManager component for reset");
                return;
            }

            // Reset to defaults and update client - exactly like Hytale does
            movementManager.resetDefaultsAndUpdate(playerEntityRef, store);

            logger.info("Reset player's MovementConfig to defaults");

        } catch (Exception e) {
            logger.warning("Failed to reset MovementConfig: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set the 'mounting' flag on a player's MovementStatesComponent.
     * When mounting=true, Hytale's movement system should disable jumping.
     * @deprecated Use applyMovementConfig instead for proper client-side jump disable
     */
    @SuppressWarnings("unused")
    private void setPlayerMountingFlag(PlayerRef player, Store<EntityStore> store, boolean mounting) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                logger.warning("Cannot set mounting flag - player entity ref invalid");
                return;
            }

            MovementStatesComponent movementComp = store.getComponent(
                playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                logger.warning("Player has no MovementStatesComponent");
                return;
            }

            MovementStates states = movementComp.getMovementStates();
            if (states == null) {
                logger.warning("MovementStatesComponent has no MovementStates");
                return;
            }

            // Set the mounting flag
            states.mounting = mounting;
            movementComp.setMovementStates(states);

            logger.info("Set player mounting flag to: " + mounting);

        } catch (Exception e) {
            logger.warning("Failed to set mounting flag: " + e.getMessage());
        }
    }

    /**
     * Log all methods on NPCMountComponent for debugging.
     */
    private void logNPCMountComponentMethods(NPCMountComponent mountComp) {
        if (methodsLogged) return;
        methodsLogged = true;

        logger.info("=== NPCMountComponent methods ===");
        for (var method : mountComp.getClass().getMethods()) {
            String name = method.getName();
            // Log all set methods and anything with movement/config in name
            if (name.startsWith("set") || name.toLowerCase().contains("movement") ||
                name.toLowerCase().contains("config") || name.toLowerCase().contains("mount")) {
                StringBuilder params = new StringBuilder();
                for (var param : method.getParameterTypes()) {
                    if (params.length() > 0) params.append(", ");
                    params.append(param.getSimpleName());
                }
                logger.info("  " + name + "(" + params + ") -> " + method.getReturnType().getSimpleName());
            }
        }

        logger.info("=== NPCMountComponent fields ===");
        for (var field : mountComp.getClass().getDeclaredFields()) {
            logger.info("  " + field.getName() + " : " + field.getType().getSimpleName());
        }
    }
}
