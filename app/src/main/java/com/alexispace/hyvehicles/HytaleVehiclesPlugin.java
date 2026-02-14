package com.alexispace.hyvehicles;

import com.alexispace.hyvehicles.api.VehicleAPI;
import com.alexispace.hyvehicles.api.VehicleAPIImpl;
import com.alexispace.hyvehicles.api.VehicleHandle;
import com.alexispace.hyvehicles.api.VehicleTypeCreator;
import com.alexispace.hyvehicles.command.HvCommand;
import com.alexispace.hyvehicles.config.VehicleDeployableConfig;
import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.alexispace.hyvehicles.interaction.SpawnNPCBoatInteraction;
import com.alexispace.hyvehicles.interaction.SpawnVehicleInteraction;
import com.alexispace.hyvehicles.loader.VehicleLoader;
import com.alexispace.hyvehicles.persistence.VehiclePersistence;
import com.alexispace.hyvehicles.registry.BoatCreator;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.system.VehicleControlSystem;
import com.alexispace.hyvehicles.system.VehicleDeathSystem;
import com.alexispace.hyvehicles.system.VehicleMountSystem;
import com.alexispace.hyvehicles.system.VehicleReconnectSystem;
import com.alexispace.hyvehicles.system.VehicleTickSystem;
import com.alexispace.hyvehicles.util.Vec3;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class HytaleVehiclesPlugin
extends JavaPlugin {
    private static HytaleVehiclesPlugin instance;
    private VehicleRegistry vehicleRegistry;
    private VehicleAPIImpl vehicleAPI;
    private VehicleLoader vehicleLoader;
    private VehicleLogger logger;
    private VehiclePersistence vehiclePersistence;
    private VehicleMountSystem mountSystem;
    private final AtomicBoolean vehiclesRespawned = new AtomicBoolean(false);
    private volatile World cachedWorld;
    private ScheduledExecutorService scheduler;
    private volatile boolean vehiclesSavedForShutdown = false;

    public HytaleVehiclesPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    private VehicleLogger createLogger() {
        return new VehicleLogger() {
            @Override
            public void info(String message) {
                HytaleVehiclesPlugin.this.getLogger().at(Level.INFO).log(message);
            }

            @Override
            public void warning(String message) {
                HytaleVehiclesPlugin.this.getLogger().at(Level.WARNING).log(message);
            }

            @Override
            public void severe(String message) {
                HytaleVehiclesPlugin.this.getLogger().at(Level.SEVERE).log(message);
            }
        };
    }

    public void setup() {
        this.getLogger().at(Level.INFO).log("HytaleVehicles is setting up...");
        this.logger = this.createLogger();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.vehicleRegistry = new VehicleRegistry(this.logger);
        this.vehicleAPI = new VehicleAPIImpl(this.vehicleRegistry, this.logger);
        this.vehicleLoader = new VehicleLoader(this.logger);
        this.vehicleLoader.setResourceClassLoader(this.getClass().getClassLoader());
        this.registerVehicleDataComponent();
        this.registerBuiltInTypes();
        this.registerInteractions();
        this.registerCommands();
        this.loadBuiltInVehicles();
        this.getEntityStoreRegistry().registerSystem(new VehicleDeathSystem());
        this.logger.info("Registered VehicleDeathSystem for real-time death detection");
        this.getEntityStoreRegistry().registerSystem(new VehicleReconnectSystem());
        this.logger.info("Registered VehicleReconnectSystem for ECS persistence");
        this.getEntityStoreRegistry().registerSystem(new VehicleTickSystem());
        this.logger.info("Registered VehicleTickSystem for physics and input processing");
        this.getEntityStoreRegistry().registerSystem(new VehicleControlSystem());
        this.logger.info("Registered VehicleControlSystem for steering");
        Path saveDir = this.getDataDirectory().resolve("saves");
        this.vehiclePersistence = new VehiclePersistence(this.logger, saveDir);
        this.logger.info("Vehicle save directory: " + String.valueOf(saveDir.toAbsolutePath()));
        this.mountSystem = new VehicleMountSystem(this.logger);
        this.logger.info("Initialized VehicleMountSystem");
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);
        this.logger.info("Registered AddPlayerToWorldEvent for auto-respawn");
        this.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, this::onPlayerLeavingWorld);
        this.logger.info("Registered DrainPlayerFromWorldEvent for auto-save");
        this.registerFKeyPacketListener();
        this.logger.info("Registered packet listener for F key mounting");
        this.getLogger().at(Level.INFO).log("HytaleVehicles setup complete");
    }

    private void registerFKeyPacketListener() {
        PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            int packetId = packet.getId();
            if (packetId == 290) {
                this.logger.info("SyncInteractionChains packet received - checking for F key interaction");
                this.handleFKeyFromSyncPacket(playerRef, packet);
            }
        });
    }

    private void handleFKeyFromSyncPacket(PlayerRef playerRef, Packet packet) {
        if (playerRef == null) {
            return;
        }
        World world = this.cachedWorld;
        if (world == null) {
            return;
        }
        SyncInteractionChains interactionChains = (SyncInteractionChains) packet;
        SyncInteractionChain[] updates = interactionChains.updates;
        if (updates == null || updates.length == 0) {
            return;
        }
        int targetEntityId = -1;
        boolean isUseInteraction = false;
        this.logger.info("=== F KEY PACKET PARSING ===");
        this.logger.info("Number of interaction chains: " + updates.length);
        for (int i = 0; i < updates.length; ++i) {
            SyncInteractionChain chain = updates[i];
            this.logger.info("Chain[" + i + "] interactionType=" + String.valueOf(chain.interactionType));
            if (chain.interactionType != InteractionType.Use) continue;
            isUseInteraction = true;
            this.logger.info("  -> This is a USE interaction (F key)");
            try {
                this.logger.info("  Chain fields:");
                for (Field field : chain.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(chain);
                    this.logger.info("    " + field.getName() + " = " + String.valueOf(value) + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                    if (!field.getName().equals("data") || value == null) continue;
                    this.logger.info("    -> Data object fields:");
                    for (Field dataField : value.getClass().getDeclaredFields()) {
                        dataField.setAccessible(true);
                        Object dataValue = dataField.get(value);
                        this.logger.info("       " + dataField.getName() + " = " + String.valueOf(dataValue) + " (type: " + (dataValue != null ? dataValue.getClass().getSimpleName() : "null") + ")");
                        if (!dataField.getName().equals("entityId")) continue;
                        Object idValue = dataField.get(value);
                        if (idValue instanceof Integer) {
                            targetEntityId = (Integer) idValue;
                            this.logger.info("       *** FOUND entityId (Integer): " + targetEntityId);
                            continue;
                        }
                        if (!(idValue instanceof Number)) continue;
                        targetEntityId = ((Number) idValue).intValue();
                        this.logger.info("       *** FOUND entityId (Number): " + targetEntityId);
                    }
                }
                continue;
            }
            catch (Exception e) {
                this.logger.warning("Error reading entityId from packet: " + e.getMessage());
                e.printStackTrace();
            }
        }
        this.logger.info("=== END PACKET PARSING, targetEntityId=" + targetEntityId + " ===");
        if (!isUseInteraction) {
            return;
        }
        if (targetEntityId <= 0) {
            this.logger.info("F key pressed but no valid entityId in packet");
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    if (this.mountSystem.isPlayerMounted(playerRef, store)) {
                        this.logger.info("Player is mounted, dismounting because F pressed without target");
                        this.mountSystem.dismountPlayer(playerRef, store);
                        this.logger.info("Player dismounted successfully");
                    } else {
                        this.logger.info("Player not mounted, ignoring F press without target");
                    }
                }
                catch (Exception e) {
                    this.logger.severe("Error checking mount status for dismount: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            return;
        }
        int finalTargetId = targetEntityId;
        world.execute(() -> {
            try {
                Ref<EntityStore> targetEntityRef;
                Store<EntityStore> store;
                block11: {
                    store = world.getEntityStore().getStore();
                    Ref<EntityStore> playerEntityRef = playerRef.getReference();
                    if (this.mountSystem.isPlayerMounted(playerRef, store)) {
                        // Use stable NetworkId from MountInfo, not from Ref (which can become stale after ECS compaction)
                        int currentVehicleNetworkId = this.mountSystem.getMountedVehicleNetworkId(playerRef);
                        Ref<EntityStore> currentVehicleRef = this.mountSystem.getMountedVehicle(playerRef, store);
                        if (currentVehicleNetworkId >= 0) {
                            this.logger.info("Player already mounted to vehicle with NetworkId=" + currentVehicleNetworkId + ", clicked on NetworkId=" + finalTargetId);
                            if (currentVehicleNetworkId != finalTargetId) {
                                this.logger.info("Clicked on different vehicle - dismounting from current and mounting new");
                                this.mountSystem.dismountPlayer(playerRef, store);
                                break block11;
                            } else {
                                String definitionId;
                                VehicleDefinition definition;
                                VehicleDataComponent vehicleData = store.getComponent(currentVehicleRef, VehicleDataComponent.getComponentType());
                                if (vehicleData != null && (definition = this.vehicleRegistry.getDefinition(definitionId = vehicleData.getDefinitionId())) != null && definition.seats.size() > 1) {
                                    boolean seatsPreScaled;
                                    float scale = definition.modelScale > 0.0f ? definition.modelScale : 1.0f;
                                    boolean cycled = this.mountSystem.cycleSeat(playerRef, store, vehicleData, definition.seats, scale, definition.modelYOffset, seatsPreScaled = definition.blockyModelPath != null && !definition.blockyModelPath.isEmpty());
                                    if (cycled) {
                                        this.logger.info("Player cycled to next seat");
                                        return;
                                    }
                                    this.logger.info("All other seats occupied - dismounting");
                                }
                                this.mountSystem.dismountPlayer(playerRef, store);
                                this.logger.info("Player dismounted via F key");
                                return;
                            }
                        }
                        this.mountSystem.dismountPlayer(playerRef, store);
                        this.logger.info("Player dismounted via F key (invalid vehicle ref)");
                        return;
                    }
                }
                if ((targetEntityRef = this.findEntityByNetworkId(store, finalTargetId)) == null || !targetEntityRef.isValid()) {
                    this.logger.info("Could not find entity with networkId: " + finalTargetId);
                    return;
                }
                VehicleDataComponent vehicleData = store.getComponent(targetEntityRef, VehicleDataComponent.getComponentType());
                if (vehicleData == null) {
                    this.logger.info("F key target (networkId=" + finalTargetId + ") is not a vehicle");
                    return;
                }
                this.logger.info("=== MOUNTING TO VEHICLE ===");
                this.logger.info("  targetEntityRef=" + String.valueOf(targetEntityRef));
                this.logger.info("  vehicleData.definitionId=" + vehicleData.getDefinitionId());
                NetworkId targetNetworkIdComp = store.getComponent(targetEntityRef, NetworkId.getComponentType());
                this.logger.info("  targetEntityRef's NetworkId=" + String.valueOf(targetNetworkIdComp != null ? Integer.valueOf(targetNetworkIdComp.getId()) : "null"));
                this.logger.info("  Expected NetworkId from packet=" + finalTargetId);
                BaseVehicle vehicleWrapper = this.createVehicleWrapperOnDemand(targetEntityRef, vehicleData, store, world);
                if (vehicleWrapper == null) {
                    this.logger.warning("Failed to create vehicle wrapper");
                    return;
                }
                this.logger.info("  vehicleWrapper.instanceId=" + String.valueOf(vehicleWrapper.getInstanceId()));
                this.logger.info("  vehicleWrapper.entityRef=" + String.valueOf(vehicleWrapper.getEntityRef()));
                this.mountToVehicle(playerRef, targetEntityRef, vehicleData, store);
                this.logger.info("=== END MOUNTING ===");
                return;
            }
            catch (Exception e) {
                this.logger.warning("Error handling F key: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private Ref<EntityStore> findEntityByNetworkId(Store<EntityStore> store, int targetNetworkId) {
        try {
            this.logger.info("Searching for vehicle with networkId=" + targetNetworkId + " (registry has " + this.vehicleRegistry.getSpawnedCount() + " vehicles)");
            // Primary: use cached NetworkId on BaseVehicle (stable, no Ref dependency)
            BaseVehicle match = this.vehicleRegistry.getVehicleByNetworkId(targetNetworkId);
            if (match != null) {
                Ref<EntityStore> vehicleRef = match.getEntityRef();
                if (vehicleRef != null && vehicleRef.isValid()) {
                    this.logger.info("Found vehicle by cached networkId: " + match.getDefinition().id);
                    return vehicleRef;
                }
                this.logger.warning("Vehicle found by networkId but Ref is invalid - ref may be stale");
            }
            // Fallback: scan store components (handles Ref drift)
            ComponentType networkIdType = NetworkId.getComponentType();
            if (networkIdType == null) {
                return null;
            }
            for (BaseVehicle vehicle : this.vehicleRegistry.getAllVehicles()) {
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef == null || !vehicleRef.isValid()) continue;
                try {
                    NetworkId networkId = (NetworkId) store.getComponent(vehicleRef, networkIdType);
                    if (networkId != null && networkId.getId() == targetNetworkId) {
                        this.logger.info("Found vehicle by store scan: " + vehicle.getDefinition().id);
                        return vehicleRef;
                    }
                } catch (Exception e) {
                    // Ref may be stale, skip
                }
            }
            this.logger.info("No vehicle found with networkId=" + targetNetworkId);
            return null;
        }
        catch (Exception e) {
            this.logger.warning("Error finding entity by NetworkId: " + e.getMessage());
            return null;
        }
    }

    private void mountToVehicle(PlayerRef playerRef, Ref<EntityStore> vehicleRef, VehicleDataComponent vehicleData, Store<EntityStore> store) {
        String definitionId = vehicleData.getDefinitionId();
        VehicleDefinition definition = this.vehicleRegistry.getDefinition(definitionId);
        this.logger.info("=== MOUNT DEBUG ===");
        this.logger.info("Definition ID: " + definitionId);
        this.logger.info("Definition found: " + (definition != null));
        float seatX = 0.0f;
        float seatY = 0.5f;
        float seatZ = 0.0f;
        int seatIndex = 0;
        if (definition != null) {
            float scale = definition.modelScale > 0.0f ? definition.modelScale : 1.0f;
            float modelYOffset = definition.modelYOffset;
            this.logger.info("Definition seats count: " + definition.seats.size());
            this.logger.info("Scale: " + scale + ", modelYOffset: " + modelYOffset);
            if (!definition.seats.isEmpty()) {
                boolean seatsFromModel;
                int totalSeats = definition.seats.size();
                int driverSeat = -1;
                for (int i = 0; i < totalSeats; ++i) {
                    if (vehicleData.isSeatOccupied(i)) continue;
                    SeatDefinition seat = definition.seats.get(i);
                    if (!seat.canControl) continue;
                    driverSeat = i;
                    break;
                }
                seatIndex = driverSeat >= 0 ? driverSeat : vehicleData.findAvailableSeat(totalSeats);
                this.logger.info("Finding available seat. Total: " + totalSeats + ", Driver seat: " + driverSeat + ", Final: " + seatIndex);
                if (seatIndex < 0) {
                    this.logger.info("All seats occupied on this vehicle");
                    return;
                }
                SeatDefinition seat = definition.seats.get(seatIndex);
                this.logger.info("Seat " + seatIndex + " raw position: x=" + seat.x + ", y=" + seat.y + ", z=" + seat.z);
                seatsFromModel = definition.blockyModelPath != null && !definition.blockyModelPath.isEmpty();
                if (seatsFromModel) {
                    seatX = seat.x;
                    seatY = seat.y - modelYOffset;
                    seatZ = seat.z;
                    this.logger.info("Using pre-scaled seats from blockymodel");
                } else {
                    seatX = seat.x * scale;
                    seatY = seat.y * scale - modelYOffset;
                    seatZ = seat.z * scale;
                }
                this.logger.info("Seat " + seatIndex + " FINAL offset: x=" + seatX + ", y=" + seatY + ", z=" + seatZ);
                vehicleData.occupySeat(seatIndex);
                this.logger.info("Occupied seats after mount: " + vehicleData.getOccupiedSeatCount());
            } else {
                this.logger.info("No seats defined, using default Y offset");
                seatY = 0.5f * scale - modelYOffset;
            }
        } else {
            this.logger.warning("Definition is NULL for " + definitionId);
        }
        this.logger.info("Calling mountPlayer with offset: (" + seatX + ", " + seatY + ", " + seatZ + ")");
        boolean success = this.mountSystem.mountPlayer(playerRef, vehicleRef, store, seatX, seatY, seatZ, seatIndex);
        if (success) {
            this.logger.info("SUCCESS: Player mounted vehicle at seat " + seatIndex);
        } else {
            this.logger.warning("FAILED: Mount failed, vacating seat " + seatIndex);
            vehicleData.vacateSeat(seatIndex);
        }
        this.logger.info("=== END MOUNT DEBUG ===");
    }

    private void onPlayerAddedToWorld(AddPlayerToWorldEvent event) {
        this.logger.info("=== PLAYER ADDED TO WORLD ===");
        World world = event.getWorld();
        if (world == null) {
            return;
        }
        this.logger.info("Vehicles in registry BEFORE loading: " + this.vehicleRegistry.getSpawnedCount());
        this.cachedWorld = world;
        if (this.vehiclesRespawned.compareAndSet(false, true)) {
            this.scheduler.schedule(() -> world.execute(() -> {
                try {
                    this.logger.info("=== DELAYED VEHICLE SPAWN (after 2s) ===");
                    this.logger.info("Vehicles in registry BEFORE JSON spawn: " + this.vehicleRegistry.getSpawnedCount());
                    List<VehiclePersistence.VehicleSaveData> savedVehicles = this.vehiclePersistence.loadVehicles();
                    if (!savedVehicles.isEmpty()) {
                        this.logger.info("Auto-loading " + savedVehicles.size() + " vehicles from JSON (after 2s delay)...");
                        int count = this.respawnVehiclesInternal(world, savedVehicles);
                        this.logger.info("Auto-loaded " + count + " vehicles from JSON persistence");
                        this.logger.info("Vehicles in registry AFTER JSON spawn: " + this.vehicleRegistry.getSpawnedCount());
                    } else {
                        this.logger.info("No saved vehicles to load");
                    }
                    this.logger.info("=== END DELAYED VEHICLE SPAWN ===");
                }
                catch (Exception e) {
                    this.logger.severe("Failed to auto-load vehicles: " + e.getMessage());
                    e.printStackTrace();
                }
            }), 2L, TimeUnit.SECONDS);
        } else {
            this.logger.info("Player joined world - vehicles already loaded");
        }
        this.logger.info("=== END PLAYER ADDED TO WORLD ===");
    }

    private BaseVehicle createVehicleWrapperOnDemand(Ref<EntityStore> entityRef, VehicleDataComponent vehicleData, Store<EntityStore> store, World world) {
        String definitionId = vehicleData.getDefinitionId();
        if (definitionId == null || definitionId.isEmpty()) {
            this.logger.warning("Vehicle entity has no definition ID");
            return null;
        }
        // Use NetworkId (stable) for existing wrapper lookup, not Ref (unstable after ECS compaction)
        int targetNetworkId = -1;
        try {
            NetworkId netId = (NetworkId) store.getComponent(entityRef, NetworkId.getComponentType());
            if (netId != null) {
                targetNetworkId = netId.getId();
            }
        } catch (Exception e) {
            this.logger.warning("Could not read NetworkId for wrapper lookup: " + e.getMessage());
        }
        if (targetNetworkId >= 0) {
            BaseVehicle existing = this.vehicleRegistry.getVehicleByNetworkId(targetNetworkId);
            if (existing != null) {
                // Update the vehicle's entity ref in case it changed due to ECS compaction
                existing.setEntityRef(entityRef);
                this.logger.info("Vehicle already has wrapper: " + definitionId + " (networkId=" + targetNetworkId + ")");
                return existing;
            }
        }
        VehicleDefinition definition = this.vehicleRegistry.getDefinition(definitionId);
        if (definition == null) {
            this.logger.warning("Unknown vehicle definition: " + definitionId);
            return null;
        }
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            this.logger.warning("Vehicle entity has no TransformComponent");
            return null;
        }
        Vector3d position = transform.getPosition();
        float yaw = transform.getRotation().getYaw();
        float yOffset = definition.modelYOffset;
        Vec3 vehiclePos = new Vec3((float) position.x, (float) position.y - yOffset, (float) position.z);
        VehicleTypeCreator creator = this.vehicleRegistry.getCreator(definition.type);
        if (creator == null) {
            this.logger.warning("No creator for vehicle type: " + definition.type);
            return null;
        }
        BaseVehicle vehicle = creator.createVehicle(definition, vehiclePos, yaw);
        vehicle.setWorld(world);
        vehicle.setEntityRef(entityRef);
        if (targetNetworkId >= 0) {
            vehicle.setNetworkId(targetNetworkId);
        }
        VehicleEntityBridge bridge = this.getEntityBridge();
        if (bridge != null) {
            vehicle.setCollisionChecker(bridge.createCollisionChecker(world));
        }
        this.vehicleRegistry.trackVehicle(vehicle);
        this.logger.info("Created on-demand wrapper for vehicle: " + definitionId + " at " + String.valueOf(vehiclePos) + " (networkId=" + targetNetworkId + ")");
        return vehicle;
    }

    private void onPlayerLeavingWorld(DrainPlayerFromWorldEvent event) {
        this.logger.info("=== PLAYER LEAVING WORLD ===");
        this.logger.info("Vehicles in registry: " + this.vehicleRegistry.getSpawnedCount());
        Store<EntityStore> store = null;
        try {
            for (BaseVehicle vehicle : this.vehicleRegistry.getAllVehicles()) {
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef == null || !vehicleRef.isValid()) continue;
                if (store == null) {
                    store = vehicleRef.getStore();
                }
            }
        }
        catch (Exception e) {
            this.logger.warning("Error during player leaving cleanup: " + e.getMessage());
        }
        if (this.mountSystem != null && store != null) {
            this.mountSystem.clearAllMounts(store);
        } else if (this.mountSystem != null) {
            this.logger.warning("No store available - using deprecated clearAllMounts()");
            this.mountSystem.clearAllMounts();
        }
        this.saveVehicles();
        this.vehiclesSavedForShutdown = true;
        int vehicleCount = this.vehicleRegistry.getSpawnedCount();
        for (BaseVehicle vehicle : this.vehicleRegistry.getAllVehicles()) {
            try {
                vehicle.destroy();
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef == null || !vehicleRef.isValid()) continue;
                if (store == null) {
                    store = vehicleRef.getStore();
                }
                if (store == null) continue;
                store.removeEntity(vehicleRef, RemoveReason.REMOVE);
                this.logger.info("Removed ECS entity for vehicle: " + vehicle.getDefinition().id);
            }
            catch (Exception e) {
                this.logger.warning("Failed to remove vehicle during world drain: " + e.getMessage());
            }
        }
        this.vehicleRegistry.clearAllTracking();
        this.logger.info("Saved and removed " + vehicleCount + " vehicles (will reload from JSON)");
        this.vehiclesRespawned.set(false);
        try {
            VehicleReconnectSystem.clearCache();
        }
        catch (Exception exception) {
        }
        this.cachedWorld = null;
        this.logger.info("Cleared vehicle wrappers - vehicles will be saved to JSON on shutdown");
        this.logger.info("=== END PLAYER LEAVING WORLD ===");
    }

    private int respawnVehiclesInternal(World world, List<VehiclePersistence.VehicleSaveData> savedVehicles) {
        // Dedup: remove duplicate vehicles at same position before spawning
        List<VehiclePersistence.VehicleSaveData> dedupedVehicles = new ArrayList<>();
        int duplicatesSkipped = 0;
        for (VehiclePersistence.VehicleSaveData data : savedVehicles) {
            boolean isDuplicate = false;
            for (VehiclePersistence.VehicleSaveData existing : dedupedVehicles) {
                if (existing.definitionId.equals(data.definitionId)) {
                    float dx = Math.abs(existing.x - data.x);
                    float dy = Math.abs(existing.y - data.y);
                    float dz = Math.abs(existing.z - data.z);
                    if (dx < 1.0f && dy < 2.0f && dz < 1.0f) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (isDuplicate) {
                this.logger.info("[DEDUP] Skipping duplicate vehicle on load: " + data.definitionId
                    + " at (" + String.format("%.2f", data.x) + ", " + String.format("%.2f", data.y) + ", " + String.format("%.2f", data.z) + ")");
                ++duplicatesSkipped;
                continue;
            }
            dedupedVehicles.add(data);
        }
        if (duplicatesSkipped > 0) {
            this.logger.info("[DEDUP] Removed " + duplicatesSkipped + " duplicate vehicles from JSON data (had " + savedVehicles.size() + ", now " + dedupedVehicles.size() + ")");
        }
        int successCount = 0;
        List<VehiclePersistence.VehicleSaveData> failedVehicles = new ArrayList<>();
        for (VehiclePersistence.VehicleSaveData data : dedupedVehicles) {
            try {
                float adjustedY = data.y;
                if (adjustedY < -20.0f) {
                    this.logger.warning("Vehicle " + data.definitionId + " saved at invalid Y=" + adjustedY + ", adjusting to 65");
                    adjustedY = 65.0f;
                } else if (adjustedY > 320.0f) {
                    this.logger.warning("Vehicle " + data.definitionId + " saved at invalid Y=" + adjustedY + ", adjusting to 100");
                    adjustedY = 100.0f;
                }
                Vec3 pos = new Vec3(data.x, adjustedY, data.z);
                this.logger.info("Spawning " + data.definitionId + " at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
                VehicleHandle handle = this.vehicleAPI.spawnVehicle(data.definitionId, pos, data.yaw, world);
                if (handle != null) {
                    ++successCount;
                    this.logger.info("Successfully spawned " + data.definitionId);
                } else {
                    this.logger.warning("Failed to spawn " + data.definitionId + " - will retry");
                    failedVehicles.add(new VehiclePersistence.VehicleSaveData(data.definitionId, data.x, adjustedY, data.z, data.yaw));
                }
            }
            catch (Exception e) {
                this.logger.warning("Failed to respawn " + data.definitionId + ": " + e.getMessage());
                failedVehicles.add(data);
            }
        }
        this.vehiclePersistence.clearSaveFile();
        if (!failedVehicles.isEmpty()) {
            this.logger.info(failedVehicles.size() + " vehicles failed to spawn - scheduling retry in 3s");
            this.scheduler.schedule(() -> world.execute(() -> {
                try {
                    this.logger.info("=== RETRY SPAWN for " + failedVehicles.size() + " vehicles ===");
                    int retrySuccess = 0;
                    for (VehiclePersistence.VehicleSaveData data : failedVehicles) {
                        try {
                            Vec3 pos = new Vec3(data.x, data.y, data.z);
                            VehicleHandle handle = this.vehicleAPI.spawnVehicle(data.definitionId, pos, data.yaw, world);
                            if (handle != null) {
                                ++retrySuccess;
                                this.logger.info("Retry succeeded: " + data.definitionId);
                            } else {
                                this.logger.warning("Retry failed: " + data.definitionId + " - vehicle will be lost");
                            }
                        } catch (Exception e) {
                            this.logger.warning("Retry error for " + data.definitionId + ": " + e.getMessage());
                        }
                    }
                    this.logger.info("=== RETRY COMPLETE: " + retrySuccess + "/" + failedVehicles.size() + " recovered ===");
                } catch (Exception e) {
                    this.logger.severe("Retry spawn failed: " + e.getMessage());
                }
            }), 3L, TimeUnit.SECONDS);
        }
        return successCount;
    }

    private void registerVehicleDataComponent() {
        try {
            ComponentType type = this.getEntityStoreRegistry().registerComponent(VehicleDataComponent.class, "hyvehicles:vehicle_data", VehicleDataComponent.CODEC);
            VehicleDataComponent.setComponentType(type);
            this.logger.info("Registered VehicleDataComponent with Hytale ECS");
        }
        catch (Exception e) {
            this.logger.severe("Failed to register VehicleDataComponent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerBuiltInTypes() {
        this.vehicleRegistry.registerCreator("BOAT", BoatCreator.INSTANCE);
        this.logger.info("Registered " + this.vehicleRegistry.getTypeCount() + " built-in vehicle types");
    }

    private void registerInteractions() {
        DeployableConfig.CODEC.register("Vehicle", VehicleDeployableConfig.class, VehicleDeployableConfig.CODEC);
        this.getLogger().at(Level.INFO).log("Registered deployable config type: Vehicle");
        Interaction.CODEC.register("hyvehicles_spawn_vehicle", SpawnVehicleInteraction.class, SpawnVehicleInteraction.CODEC);
        this.getLogger().at(Level.INFO).log("Registered interaction type: hyvehicles_spawn_vehicle");
        Interaction.CODEC.register("hyvehicles_spawn_npc_boat", SpawnNPCBoatInteraction.class, SpawnNPCBoatInteraction.CODEC);
        this.getLogger().at(Level.INFO).log("Registered interaction type: hyvehicles_spawn_npc_boat");
    }

    private void registerCommands() {
        this.getCommandRegistry().registerCommand(new HvCommand());
        this.logger.info("Registered /hv command");
        this.logger.info("Commands: /hv spawn, /hv list, /hv types, /hv info, /hv help");
    }

    private void loadBuiltInVehicles() {
        String[] vehicleFiles;
        for (String resourcePath : vehicleFiles = new String[]{"vehicles/simple_boat.json", "vehicles/rowboat.json", "vehicles/spruce_rowboat.json"}) {
            try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    this.logger.warning("Vehicle resource not found: " + resourcePath);
                    continue;
                }
                VehicleDefinition def = this.vehicleLoader.loadFromStream(stream, resourcePath);
                if (def == null) continue;
                this.vehicleAPI.registerVehicle(def);
                this.logger.info("Loaded vehicle from JSON: " + def.id + " with " + def.seats.size() + " seats");
                for (int i = 0; i < def.seats.size(); ++i) {
                    SeatDefinition seat = def.seats.get(i);
                    this.logger.info("  Seat " + i + ": z=" + seat.z + ", y=" + seat.y + ", driver=" + seat.isDriver);
                }
            }
            catch (Exception e) {
                this.logger.severe("Failed to load vehicle from " + resourcePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void start() {
        this.logger.info("HytaleVehicles has started!");
        this.logger.info("API Version: 1");
        this.logger.info("Registered " + this.vehicleRegistry.getTypeCount() + " vehicle types");
        this.logger.info("Registered " + this.vehicleRegistry.getVehicleCount() + " vehicle definitions");
        this.logger.info("Using JSON persistence for vehicles");
    }

    public void shutdown() {
        this.logger.info("HytaleVehicles is shutting down...");
        if (this.mountSystem != null) {
            this.mountSystem.clearAllMounts();
        }
        if (!this.vehiclesSavedForShutdown) {
            this.saveVehicles();
        } else {
            this.logger.info("Vehicles already saved by onPlayerLeavingWorld - skipping duplicate save");
        }
        int vehicleCount = this.vehicleRegistry.getSpawnedCount();
        for (BaseVehicle vehicle : this.vehicleRegistry.getAllVehicles()) {
            try {
                Store<EntityStore> store;
                vehicle.destroy();
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef == null || !vehicleRef.isValid() || (store = vehicleRef.getStore()) == null) continue;
                store.removeEntity(vehicleRef, RemoveReason.REMOVE);
                this.logger.info("Removed ECS entity for vehicle: " + vehicle.getDefinition().id);
            }
            catch (Exception e) {
                this.logger.warning("Failed to remove vehicle during shutdown: " + e.getMessage());
            }
        }
        this.logger.info("Cleaned up " + vehicleCount + " vehicles (wrappers + ECS entities)");
        if (this.scheduler != null) {
            this.scheduler.shutdown();
            try {
                if (!this.scheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                    this.scheduler.shutdownNow();
                }
            }
            catch (InterruptedException interruptedException) {
                this.scheduler.shutdownNow();
            }
        }
    }

    private void saveVehicles() {
        Collection<BaseVehicle> vehicles = this.vehicleRegistry.getAllVehicles();
        ArrayList<VehiclePersistence.VehicleSaveData> saveData = new ArrayList<VehiclePersistence.VehicleSaveData>();
        int duplicatesSkipped = 0;

        // Get store for reading TransformComponent
        Store<EntityStore> store = null;
        if (this.cachedWorld != null) {
            store = this.cachedWorld.getEntityStore().getStore();
        }

        for (BaseVehicle vehicle : vehicles) {
            float x, y, z, yaw;

            // CRITICAL: Read ACTUAL position from TransformComponent (works for both NPC and custom entities!)
            Ref<EntityStore> entityRef = vehicle.getEntityRef();
            if (store != null && entityRef != null && entityRef.isValid()) {
                try {
                    TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d pos = transform.getPosition();
                        Vector3f rot = transform.getRotation();
                        x = (float) pos.x;
                        y = (float) pos.y;
                        z = (float) pos.z;
                        yaw = rot.getYaw();
                        this.logger.info("[SAVE] Reading position from TransformComponent (actual NPC position)");
                    } else {
                        // Fallback to BaseVehicle.position
                        float[] pos = vehicle.getPositionArray();
                        x = pos[0];
                        y = pos[1];
                        z = pos[2];
                        yaw = vehicle.getRotationYaw();
                    }
                } catch (Exception e) {
                    // Fallback to BaseVehicle.position
                    float[] pos = vehicle.getPositionArray();
                    x = pos[0];
                    y = pos[1];
                    z = pos[2];
                    yaw = vehicle.getRotationYaw();
                }
            } else {
                // Fallback to BaseVehicle.position
                float[] pos = vehicle.getPositionArray();
                x = pos[0];
                y = pos[1];
                z = pos[2];
                yaw = vehicle.getRotationYaw();
            }
            // Dedup: skip if another vehicle with same definition already saved at this position
            boolean isDuplicate = false;
            for (VehiclePersistence.VehicleSaveData existing : saveData) {
                if (existing.definitionId.equals(vehicle.getDefinition().id)) {
                    float dx = Math.abs(existing.x - x);
                    float dy = Math.abs(existing.y - y);
                    float dz = Math.abs(existing.z - z);
                    if (dx < 1.0f && dy < 2.0f && dz < 1.0f) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (isDuplicate) {
                this.logger.info("[SAVE] DEDUP: Skipping duplicate vehicle " + vehicle.getDefinition().id
                    + " at (" + String.format("%.2f", x) + ", " + String.format("%.2f", y) + ", " + String.format("%.2f", z) + ")");
                ++duplicatesSkipped;
                continue;
            }
            this.logger.info("[SAVE] Vehicle " + vehicle.getDefinition().id
                + " saving position: (" + String.format("%.2f", x) + ", " + String.format("%.2f", y) + ", " + String.format("%.2f", z) + ")"
                + " yaw=" + String.format("%.2f", yaw));
            saveData.add(new VehiclePersistence.VehicleSaveData(vehicle.getDefinition().id, x, y, z, yaw));
        }
        if (duplicatesSkipped > 0) {
            this.logger.info("[SAVE] Removed " + duplicatesSkipped + " duplicate vehicles during save");
        }
        this.vehiclePersistence.saveVehicles(saveData);
    }

    public int loadAndSpawnVehicles(World world) {
        List<VehiclePersistence.VehicleSaveData> savedVehicles = this.vehiclePersistence.loadVehicles();
        if (savedVehicles.isEmpty()) {
            this.logger.info("No saved vehicles to load");
            return 0;
        }
        this.logger.info("Loading " + savedVehicles.size() + " vehicles from JSON...");
        int successCount = 0;
        for (VehiclePersistence.VehicleSaveData data : savedVehicles) {
            try {
                Vec3 pos = new Vec3(data.x, data.y, data.z);
                VehicleHandle handle = this.vehicleAPI.spawnVehicle(data.definitionId, pos, data.yaw, world);
                if (handle == null) continue;
                this.logger.info("Loaded: " + data.definitionId);
                ++successCount;
            }
            catch (Exception e) {
                this.logger.warning("Failed to load " + data.definitionId + ": " + e.getMessage());
            }
        }
        this.vehiclePersistence.clearSaveFile();
        this.logger.info("Loaded " + successCount + " vehicles from JSON backup");
        return successCount;
    }

    public static HytaleVehiclesPlugin getInstance() {
        return instance;
    }

    public static VehicleAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("HytaleVehicles is not loaded");
        }
        return HytaleVehiclesPlugin.instance.vehicleAPI;
    }

    public VehicleRegistry getRegistry() {
        return this.vehicleRegistry;
    }

    public VehicleEntityBridge getEntityBridge() {
        if (this.vehicleAPI instanceof VehicleAPIImpl) {
            return this.vehicleAPI.getEntityBridge();
        }
        return null;
    }

    public VehicleLoader getLoader() {
        return this.vehicleLoader;
    }

    public VehicleMountSystem getMountSystem() {
        return this.mountSystem;
    }
}
