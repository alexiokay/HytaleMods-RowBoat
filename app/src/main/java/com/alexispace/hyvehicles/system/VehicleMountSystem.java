package com.alexispace.hyvehicles.system;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleMountSystem {
    private final VehicleLogger logger;
    private final Map<UUID, MountInfo> mountedPlayers = new ConcurrentHashMap<UUID, MountInfo>();
    private static final float BRAKE_MULTIPLIER = 0.5f;
    private static final float BOOST_MULTIPLIER = 1.5f;
    private int controllerDebugCounter = 0;
    private int positionSyncTickCounter = 0;

    public VehicleMountSystem(VehicleLogger logger) {
        this.logger = logger;
    }

    public boolean mountPlayer(PlayerRef player, Ref<EntityStore> vehicleRef, Store<EntityStore> store, float seatOffsetX, float seatOffsetY, float seatOffsetZ, int seatIndex) {
        if (player == null || vehicleRef == null || !vehicleRef.isValid()) {
            this.logger.warning("Cannot mount: player or vehicle is null/invalid");
            return false;
        }
        try {
            VehicleDataComponent vehicleData;
            UUID playerUUID = player.getUuid();
            if (this.mountedPlayers.containsKey(playerUUID)) {
                this.logger.info("Player is already mounted - dismounting first");
                this.dismountPlayer(player, store);
            }
            if ((vehicleData = store.getComponent(vehicleRef, VehicleDataComponent.getComponentType())) == null) {
                this.logger.warning("Cannot mount: entity is not a vehicle");
                return false;
            }
            NetworkId networkId = store.getComponent(vehicleRef, NetworkId.getComponentType());
            if (networkId == null) {
                this.logger.warning("Vehicle has no NetworkId - cannot send mount packet");
                return false;
            }
            int vehicleNetworkId = networkId.getId();
            MountNPC mountPacket = new MountNPC(seatOffsetX, seatOffsetY, seatOffsetZ, vehicleNetworkId);
            this.logger.info("MountNPC packet: offset=(" + seatOffsetX + ", " + seatOffsetY + ", " + seatOffsetZ + "), networkId=" + vehicleNetworkId);
            player.getPacketHandler().write(mountPacket);
            this.applyMovementConfig(player, store, "Montar");
            this.mountedPlayers.put(playerUUID, new MountInfo(player, vehicleRef, seatIndex, vehicleNetworkId));
            try {
                BaseVehicle vehicle;
                VehicleRegistry registry;
                HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
                if (plugin != null && (registry = plugin.getRegistry()) != null && (vehicle = registry.getVehicleByNetworkId(vehicleNetworkId)) != null) {
                    vehicle.mount(playerUUID, seatIndex);
                    this.logger.info("Called BaseVehicle.mount() for player " + String.valueOf(playerUUID) + " (networkId=" + vehicleNetworkId + ")");
                }
            }
            catch (Exception e) {
                this.logger.warning("Failed to call BaseVehicle.mount(): " + e.getMessage());
            }
            this.logger.info("Mounted player to vehicle seat " + seatIndex + " at offset: (" + seatOffsetX + ", " + seatOffsetY + ", " + seatOffsetZ + ")");
            return true;
        }
        catch (Exception e) {
            this.logger.severe("Failed to mount player: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean dismountPlayer(PlayerRef player, Store<EntityStore> store) {
        if (player == null) {
            return false;
        }
        try {
            UUID playerUUID = player.getUuid();
            MountInfo mountInfo = this.mountedPlayers.remove(playerUUID);
            BaseVehicle vehicle = null;  // Declare here so it's in scope for force-sync later

            if (mountInfo != null) {
                // Use NetworkId (stable) to find the BaseVehicle, not Ref (unstable after ECS compaction)
                try {
                    HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
                    if (plugin != null) {
                        VehicleRegistry registry = plugin.getRegistry();
                        if (registry != null) {
                            vehicle = registry.getVehicleByNetworkId(mountInfo.vehicleNetworkId);
                            if (vehicle != null) {
                                // Log vehicle position BEFORE dismount
                                com.alexispace.hyvehicles.util.Vec3 posBefore = vehicle.getPosition();
                                this.logger.info("[DISMOUNT-DEBUG] Vehicle position BEFORE dismount: ("
                                    + String.format("%.2f", posBefore.x) + ", "
                                    + String.format("%.2f", posBefore.y) + ", "
                                    + String.format("%.2f", posBefore.z) + ")");

                                vehicle.dismount(playerUUID);
                                this.logger.info("Called BaseVehicle.dismount() via networkId=" + mountInfo.vehicleNetworkId);
                            }
                        }
                    }
                } catch (Exception e) {
                    this.logger.warning("Failed to call BaseVehicle.dismount(): " + e.getMessage());
                }

                // Force position sync to TransformComponent before sending DismountNPC
                Ref<EntityStore> mountedVehicleRef = mountInfo.vehicleRef;
                if (mountedVehicleRef != null && mountedVehicleRef.isValid() && vehicle != null) {
                    try {
                        com.alexispace.hyvehicles.util.Vec3 currentPos = vehicle.getPosition();
                        float currentYaw = vehicle.getRotationYaw();
                        float modelYOffset = vehicle.getDefinition().modelYOffset;

                        // Update TransformComponent with current physics position
                        com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                            store.getComponent(mountedVehicleRef, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                        if (transform != null) {
                            com.hypixel.hytale.math.vector.Vector3d oldEntityPos = transform.getPosition();
                            this.logger.info("[DISMOUNT-DEBUG] Entity TransformComponent position BEFORE sync: ("
                                + String.format("%.2f", oldEntityPos.x) + ", "
                                + String.format("%.2f", oldEntityPos.y) + ", "
                                + String.format("%.2f", oldEntityPos.z) + ")");

                            transform.setPosition(new com.hypixel.hytale.math.vector.Vector3d(
                                (double) currentPos.x,
                                (double) (currentPos.y + modelYOffset),
                                (double) currentPos.z
                            ));
                            transform.setRotation(new com.hypixel.hytale.math.vector.Vector3f(0.0f, currentYaw, 0.0f));

                            this.logger.info("[DISMOUNT-DEBUG] Synced TransformComponent to physics position: ("
                                + String.format("%.2f", currentPos.x) + ", "
                                + String.format("%.2f", currentPos.y + modelYOffset) + ", "
                                + String.format("%.2f", currentPos.z) + ")");
                        }

                        // Vacate seat on VehicleDataComponent
                        VehicleDataComponent vehicleData = store.getComponent(mountedVehicleRef, VehicleDataComponent.getComponentType());
                        if (vehicleData != null) {
                            vehicleData.vacateSeat(mountInfo.seatIndex);
                        }
                    } catch (Exception e) {
                        this.logger.warning("[DISMOUNT-DEBUG] Failed to sync position before dismount: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    this.logger.warning("[DISMOUNT-DEBUG] Cannot sync position - vehicleRef invalid or vehicle is null");
                }
            }
            this.resetMovementConfig(player, store);

            // Use DismountNPC - required for client input to work
            // This WILL cause visual teleport on client (boat appears at spawn on dismount)
            // But boat WILL move correctly on server and save properly
            DismountNPC dismountPacket = new DismountNPC();
            player.getPacketHandler().write(dismountPacket);
            this.logger.info("[DISMOUNT] Sent DismountNPC - boat may teleport visually on client but will save correctly");

            return true;
        }
        catch (Exception e) {
            this.logger.severe("Failed to dismount player: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void tickMountedPlayers(Store<EntityStore> store) {
        if (this.mountedPlayers.isEmpty()) {
            return;
        }
        this.positionSyncTickCounter++;
        boolean shouldLog = this.positionSyncTickCounter % 100 == 0; // Log every 100 ticks (5 seconds)
        Iterator<Map.Entry<UUID, MountInfo>> iterator = this.mountedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            VehicleDataComponent vehicleData;
            Map.Entry<UUID, MountInfo> entry = iterator.next();
            MountInfo mountInfo = entry.getValue();
            PlayerRef player = mountInfo.player;
            Ref<EntityStore> vehicleRef = mountInfo.vehicleRef;
            if (player == null || !player.isValid()) {
                iterator.remove();
                this.logger.info("Auto-removed mount tracking - player no longer valid");
                continue;
            }
            // Don't remove on stale Ref - use NetworkId instead and update the Ref!
            if (vehicleRef == null || !vehicleRef.isValid()) {
                // Try to get fresh Ref using NetworkId
                HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
                if (plugin != null) {
                    VehicleRegistry registry = plugin.getRegistry();
                    if (registry != null) {
                        BaseVehicle vehicle = registry.getVehicleByNetworkId(mountInfo.vehicleNetworkId);
                        if (vehicle != null && vehicle.getEntityRef() != null && vehicle.getEntityRef().isValid()) {
                            // Update stale Ref with fresh one
                            mountInfo.vehicleRef = vehicle.getEntityRef();
                            vehicleRef = mountInfo.vehicleRef;
                            if (shouldLog) {
                                this.logger.info("[MOUNT FIX] Updated stale vehicleRef for networkId=" + mountInfo.vehicleNetworkId);
                            }
                        } else {
                            // Vehicle really is gone
                            iterator.remove();
                            this.logger.info("Auto-removed mount tracking - vehicle truly gone (networkId=" + mountInfo.vehicleNetworkId + ")");
                            continue;
                        }
                    }
                }
            }

            // CRITICAL: Force-sync vehicle position to client EVERY TICK while mounted
            // Hytale's mount system doesn't auto-sync position changes, causing desync
            try {
                HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
                if (plugin != null) {
                    VehicleRegistry registry = plugin.getRegistry();
                    if (registry != null) {
                        BaseVehicle vehicle = registry.getVehicleByNetworkId(mountInfo.vehicleNetworkId);
                        if (vehicle != null && vehicle.getWorld() != null) {
                            com.alexispace.hyvehicles.util.Vec3 currentPos = vehicle.getPosition();
                            float currentYaw = vehicle.getRotationYaw();
                            float modelYOffset = vehicle.getDefinition().modelYOffset;

                            // Build and send EntityUpdates packet
                            com.hypixel.hytale.protocol.Position protocolPos = new com.hypixel.hytale.protocol.Position(
                                (double) currentPos.x,
                                (double) (currentPos.y + modelYOffset),
                                (double) currentPos.z
                            );
                            com.hypixel.hytale.protocol.Direction orientation = new com.hypixel.hytale.protocol.Direction(currentYaw, 0f, 0f);
                            com.hypixel.hytale.protocol.ModelTransform modelTransform = new com.hypixel.hytale.protocol.ModelTransform(protocolPos, orientation, orientation);

                            com.hypixel.hytale.protocol.ComponentUpdate transformUpdate = new com.hypixel.hytale.protocol.ComponentUpdate();
                            transformUpdate.type = com.hypixel.hytale.protocol.ComponentUpdateType.Transform;
                            transformUpdate.transform = modelTransform;

                            com.hypixel.hytale.protocol.EntityUpdate entityUpdate = new com.hypixel.hytale.protocol.EntityUpdate(
                                mountInfo.vehicleNetworkId,
                                new com.hypixel.hytale.protocol.ComponentUpdateType[] {},
                                new com.hypixel.hytale.protocol.ComponentUpdate[] { transformUpdate }
                            );

                            com.hypixel.hytale.protocol.packets.entities.EntityUpdates syncPacket = new com.hypixel.hytale.protocol.packets.entities.EntityUpdates(
                                new int[] {},
                                new com.hypixel.hytale.protocol.EntityUpdate[] { entityUpdate }
                            );

                            // Send only to mounted player (not all players - too expensive)
                            player.getPacketHandler().writeNoCache(syncPacket);

                            if (shouldLog) {
                                this.logger.info("[PER-TICK SYNC] Sent position update to mounted player - vehicle pos: ("
                                    + String.format("%.2f", currentPos.x) + ", "
                                    + String.format("%.2f", currentPos.y + modelYOffset) + ", "
                                    + String.format("%.2f", currentPos.z) + ")");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (shouldLog) {
                    this.logger.warning("[PER-TICK SYNC] Exception during position sync: " + e.getMessage());
                }
            }

            try {
                vehicleData = store.getComponent(vehicleRef, VehicleDataComponent.getComponentType());
            }
            catch (ArrayIndexOutOfBoundsException e) {
                continue;
            }
            catch (Exception e) {
                continue;
            }
            if (vehicleData == null) {
                iterator.remove();
                this.logger.info("Auto-removed mount tracking - vehicle data component removed");
                continue;
            }
            this.processPlayerInput(player, vehicleRef, store);
        }
    }

    private void processPlayerInput(PlayerRef player, Ref<EntityStore> vehicleRef, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }
            MovementStatesComponent movementComp = store.getComponent(playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                return;
            }
            MovementStates states = movementComp.getMovementStates();
            if (states == null) {
                return;
            }
            VehicleDataComponent vehicleData = store.getComponent(vehicleRef, VehicleDataComponent.getComponentType());
            boolean statesModified = false;
            if (states.jumping || states.swimJumping) {
                states.jumping = false;
                states.swimJumping = false;
                statesModified = true;
                if (vehicleData != null) {
                    this.applyBrake(vehicleRef, vehicleData, store);
                }
            }
            if (states.sprinting && vehicleData != null) {
                this.applyBoost(vehicleRef, vehicleData, store);
            }
            if (statesModified) {
                movementComp.setMovementStates(states);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void applyBrake(Ref<EntityStore> vehicleRef, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        vehicleData.setBraking(true);
    }

    private void applyBoost(Ref<EntityStore> vehicleRef, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        vehicleData.setBoosting(true);
    }

    public boolean isPlayerMounted(PlayerRef player, Store<EntityStore> store) {
        if (player == null) {
            return false;
        }
        return this.mountedPlayers.containsKey(player.getUuid());
    }

    public Ref<EntityStore> getMountedVehicle(PlayerRef player, Store<EntityStore> store) {
        if (player == null) {
            return null;
        }
        MountInfo info = this.mountedPlayers.get(player.getUuid());
        return info != null ? info.vehicleRef : null;
    }

    public int getMountedVehicleNetworkId(PlayerRef player) {
        if (player == null) {
            return -1;
        }
        MountInfo info = this.mountedPlayers.get(player.getUuid());
        return info != null ? info.vehicleNetworkId : -1;
    }

    public int getCurrentSeatIndex(PlayerRef player) {
        if (player == null) {
            return -1;
        }
        MountInfo info = this.mountedPlayers.get(player.getUuid());
        return info != null ? info.seatIndex : -1;
    }

    public PlayerRef getVehicleController(int vehicleNetworkId, List<SeatDefinition> seats) {
        boolean shouldLog;
        if (vehicleNetworkId < 0 || seats == null || seats.isEmpty()) {
            return null;
        }
        ++this.controllerDebugCounter;
        boolean bl = shouldLog = this.controllerDebugCounter % 100 == 0;
        if (shouldLog) {
            this.logger.info("[CONTROLLER DEBUG] Looking for controller on networkId=" + vehicleNetworkId + ", mountedPlayers.size()=" + this.mountedPlayers.size());
        }
        int playersOnThisVehicle = 0;
        for (MountInfo info : this.mountedPlayers.values()) {
            if (shouldLog) {
                this.logger.info("[CONTROLLER DEBUG] Checking player, stored networkId=" + info.vehicleNetworkId + ", match=" + (info.vehicleNetworkId == vehicleNetworkId));
            }
            if (info.vehicleNetworkId != vehicleNetworkId) continue;
            ++playersOnThisVehicle;
            int seatIndex = info.seatIndex;
            if (shouldLog) {
                String playerId = info.player != null ? info.player.getUuid().toString().substring(0, 8) : "null";
                boolean canControl = seatIndex >= 0 && seatIndex < seats.size() ? seats.get(seatIndex).canControl : false;
                this.logger.info("[CONTROLLER DEBUG] Player '" + playerId + "' in seat " + seatIndex + ", canControl=" + canControl);
            }
            if (seatIndex < 0 || seatIndex >= seats.size()) continue;
            SeatDefinition seat = seats.get(seatIndex);
            if (!seat.canControl) continue;
            return info.player;
        }
        if (shouldLog && playersOnThisVehicle > 0) {
            this.logger.info("[CONTROLLER DEBUG] " + playersOnThisVehicle + " players mounted but none have canControl=true");
        }
        return null;
    }

    public boolean cycleSeat(PlayerRef player, Store<EntityStore> store, VehicleDataComponent vehicleData, List<SeatDefinition> seats, float modelScale, float modelYOffset, boolean seatsPreScaled) {
        float seatZ;
        float seatY;
        float seatX;
        if (player == null) {
            return false;
        }
        UUID playerUUID = player.getUuid();
        MountInfo currentMount = this.mountedPlayers.get(playerUUID);
        if (currentMount == null) {
            this.logger.info("Cannot cycle seat - player not mounted");
            return false;
        }
        Ref<EntityStore> vehicleRef = currentMount.vehicleRef;
        int currentSeatIndex = currentMount.seatIndex;
        int totalSeats = seats.size();
        if (totalSeats <= 1) {
            this.logger.info("Cannot cycle seat - vehicle has only one seat");
            return false;
        }
        int nextSeatIndex = -1;
        for (int i = 1; i < totalSeats; ++i) {
            int candidateIndex = (currentSeatIndex + i) % totalSeats;
            if (vehicleData.isSeatOccupied(candidateIndex)) continue;
            nextSeatIndex = candidateIndex;
            break;
        }
        if (nextSeatIndex < 0) {
            this.logger.info("Cannot cycle seat - no other seats available");
            return false;
        }
        SeatDefinition newSeat = seats.get(nextSeatIndex);
        if (seatsPreScaled) {
            seatX = newSeat.x;
            seatY = newSeat.y - modelYOffset;
            seatZ = newSeat.z;
        } else {
            float scale = modelScale > 0.0f ? modelScale : 1.0f;
            seatX = newSeat.x * scale;
            seatY = newSeat.y * scale - modelYOffset;
            seatZ = newSeat.z * scale;
        }
        try {
            vehicleData.vacateSeat(currentSeatIndex);
            vehicleData.occupySeat(nextSeatIndex);
            this.mountedPlayers.put(playerUUID, new MountInfo(player, vehicleRef, nextSeatIndex, currentMount.vehicleNetworkId));
            try {
                BaseVehicle vehicle;
                VehicleRegistry registry;
                HytaleVehiclesPlugin plugin = HytaleVehiclesPlugin.getInstance();
                if (plugin != null && (registry = plugin.getRegistry()) != null && (vehicle = registry.getVehicleByNetworkId(currentMount.vehicleNetworkId)) != null) {
                    vehicle.dismount(playerUUID);
                    vehicle.mount(playerUUID, nextSeatIndex);
                    this.logger.info("Updated BaseVehicle seat: " + currentSeatIndex + " -> " + nextSeatIndex);
                }
            }
            catch (Exception e) {
                this.logger.warning("Failed to update BaseVehicle during seat cycle: " + e.getMessage());
            }
            NetworkId networkId = store.getComponent(vehicleRef, NetworkId.getComponentType());
            if (networkId != null) {
                MountNPC mountPacket = new MountNPC(seatX, seatY, seatZ, networkId.getId());
                player.getPacketHandler().write(mountPacket);
            }
            this.applyMovementConfig(player, store, "Montar");
            this.logger.info("Cycled player from seat " + currentSeatIndex + " to seat " + nextSeatIndex + " at offset: (" + seatX + ", " + seatY + ", " + seatZ + ")");
            return true;
        }
        catch (Exception e) {
            this.logger.severe("Failed to cycle seat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isPlayerCrouching(PlayerRef player, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return false;
            }
            MovementStatesComponent movementComp = store.getComponent(playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                return false;
            }
            MovementStates states = movementComp.getMovementStates();
            return states != null && states.crouching;
        }
        catch (Exception e) {
            return false;
        }
    }

    public void clearAllMounts(Store<EntityStore> store) {
        for (Map.Entry<UUID, MountInfo> entry : this.mountedPlayers.entrySet()) {
            try {
                PlayerRef player = entry.getValue().player;
                if (player == null) continue;
                this.resetMovementConfig(player, store);
                this.logger.info("Reset MovementConfig for player " + String.valueOf(entry.getKey()) + " before clearing mounts");
            }
            catch (Exception e) {
                this.logger.warning("Failed to reset MovementConfig for player " + String.valueOf(entry.getKey()) + ": " + e.getMessage());
            }
        }
        this.mountedPlayers.clear();
    }

    @Deprecated
    public void clearAllMounts() {
        this.logger.warning("clearAllMounts() called without Store - player MovementConfigs will NOT be reset!");
        this.mountedPlayers.clear();
    }

    private void applyMovementConfig(PlayerRef player, Store<EntityStore> store, String movementConfigId) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                this.logger.warning("Cannot apply MovementConfig - player entity ref invalid");
                return;
            }
            MovementConfig movementConfig = (MovementConfig) MovementConfig.getAssetMap().getAsset(movementConfigId);
            if (movementConfig == null) {
                this.logger.warning("MovementConfig '" + movementConfigId + "' not found in asset map");
                return;
            }
            MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
            if (movementManager == null) {
                this.logger.warning("Player has no MovementManager component");
                return;
            }
            PhysicsValues physicsValues = store.getComponent(playerEntityRef, PhysicsValues.getComponentType());
            if (physicsValues == null) {
                this.logger.warning("Player has no PhysicsValues component");
                return;
            }
            Player playerComponent = store.getComponent(playerEntityRef, Player.getComponentType());
            if (playerComponent == null) {
                this.logger.warning("Player has no Player component");
                return;
            }
            movementManager.setDefaultSettings(movementConfig.toPacket(), physicsValues, playerComponent.getGameMode());
            movementManager.applyDefaultSettings();
            movementManager.update(player.getPacketHandler());
            this.logger.info("Applied MovementConfig '" + movementConfigId + "' to player (JumpForce should be 0)");
        }
        catch (Exception e) {
            this.logger.warning("Failed to apply MovementConfig: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetMovementConfig(PlayerRef player, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                this.logger.warning("Cannot reset MovementConfig - player entity ref invalid");
                return;
            }
            MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
            if (movementManager == null) {
                this.logger.warning("Player has no MovementManager component for reset");
                return;
            }
            movementManager.resetDefaultsAndUpdate(playerEntityRef, store);
            this.logger.info("Reset player's MovementConfig to defaults");
        }
        catch (Exception e) {
            this.logger.warning("Failed to reset MovementConfig: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setPlayerMountingFlag(PlayerRef player, Store<EntityStore> store, boolean mounting) {
        try {
            Ref<EntityStore> playerEntityRef = player.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                this.logger.warning("Cannot set mounting flag - player entity ref invalid");
                return;
            }
            MovementStatesComponent movementComp = store.getComponent(playerEntityRef, MovementStatesComponent.getComponentType());
            if (movementComp == null) {
                this.logger.warning("Player has no MovementStatesComponent");
                return;
            }
            MovementStates states = movementComp.getMovementStates();
            if (states == null) {
                this.logger.warning("MovementStatesComponent has no MovementStates");
                return;
            }
            states.mounting = mounting;
            movementComp.setMovementStates(states);
            this.logger.info("Set player mounting flag to: " + mounting);
        }
        catch (Exception e) {
            this.logger.warning("Failed to set mounting flag: " + e.getMessage());
        }
    }

    private static class MountInfo {
        final PlayerRef player;
        Ref<EntityStore> vehicleRef;  // Non-final - can be updated when Ref becomes stale due to ECS compaction
        final int seatIndex;
        final int vehicleNetworkId;

        MountInfo(PlayerRef player, Ref<EntityStore> vehicleRef, int seatIndex, int vehicleNetworkId) {
            this.player = player;
            this.vehicleRef = vehicleRef;
            this.seatIndex = seatIndex;
            this.vehicleNetworkId = vehicleNetworkId;
        }
    }
}
