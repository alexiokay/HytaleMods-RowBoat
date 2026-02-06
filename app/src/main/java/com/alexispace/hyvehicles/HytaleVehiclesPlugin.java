package com.alexispace.hyvehicles;

import com.alexispace.hyvehicles.api.VehicleAPI;
import com.alexispace.hyvehicles.api.VehicleAPIImpl;
import com.alexispace.hyvehicles.command.HvCommand;
import com.alexispace.hyvehicles.config.VehicleDeployableConfig;
import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.VehicleDataComponent;
import com.alexispace.hyvehicles.entity.VehicleEntityBridge;
import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.alexispace.hyvehicles.interaction.SpawnVehicleInteraction;
import com.alexispace.hyvehicles.interaction.SpawnNPCBoatInteraction;
import com.alexispace.hyvehicles.loader.VehicleLoader;
import com.alexispace.hyvehicles.registry.BoatCreator;
import com.alexispace.hyvehicles.registry.VehicleRegistry;
import com.alexispace.hyvehicles.persistence.VehiclePersistence;
import com.alexispace.hyvehicles.system.VehicleDeathSystem;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.alexispace.hyvehicles.system.VehicleMountSystem;
// Packet listener imports for F key interaction
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;

import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HytaleVehicles - Core vehicle system for Hytale.
 * 
 * <p>This plugin provides the foundation for vehicle mods in Hytale:</p>
 * <ul>
 *   <li>JSON-driven vehicle definitions</li>
 *   <li>Extensible vehicle type system</li>
 *   <li>Integration with Hytale's mount system</li>
 *   <li>Physics for water and ground vehicles</li>
 * </ul>
 * 
 * <h2>For Content Pack Developers</h2>
 * <pre>{@code
 * // In your pack's plugin:
 * VehicleAPI api = HytaleVehiclesPlugin.getAPI();
 * 
 * // Register vehicles from JSON
 * api.registerVehicle(myBoatDefinition);
 * 
 * // Or register custom types
 * api.registerVehicleCreator("HOVERCRAFT", new MyHovercraftCreator());
 * }</pre>
 * 
 * <h2>Architecture (Inspired by MTS)</h2>
 * <pre>
 * HytaleVehicles
 * ├── API Layer (VehicleAPI, VehicleTypeCreator)
 * ├── Entity Layer (BaseVehicle, WaterVehicle, GroundVehicle)
 * ├── Definition Layer (VehicleDefinition - JSON)
 * └── Registry Layer (VehicleRegistry)
 * </pre>
 * 
 * @since 1.0
 * @author alexispace
 * @see VehicleAPI
 */
public class HytaleVehiclesPlugin extends JavaPlugin {
    
    /**
     * Singleton instance for static access.
     */
    private static HytaleVehiclesPlugin instance;
    
    /**
     * The vehicle registry.
     */
    private VehicleRegistry vehicleRegistry;
    
    /**
     * The public API.
     */
    private VehicleAPIImpl vehicleAPI;
    
    /**
     * Vehicle JSON loader.
     */
    private VehicleLoader vehicleLoader;
    
    /**
     * Our logger wrapper.
     */
    private VehicleLogger logger;

    /**
     * JSON persistence for vehicles.
     */
    private VehiclePersistence vehiclePersistence;

    /**
     * Vehicle mount system for F key mounting.
     */
    private VehicleMountSystem mountSystem;

    /**
     * Thread-safe flag to prevent multiple respawns.
     */
    private final java.util.concurrent.atomic.AtomicBoolean vehiclesRespawned = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Cached world reference for F key mounting.
     */
    private volatile com.hypixel.hytale.server.core.universe.world.World cachedWorld;

    /**
     * Scheduler for delayed tasks (e.g., delayed vehicle spawn after world join).
     */
    private ScheduledExecutorService scheduler;

    public HytaleVehiclesPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }
    
    /**
     * Create a VehicleLogger that wraps Hytale's logger.
     */
    private VehicleLogger createLogger() {
        return new VehicleLogger() {
            @Override
            public void info(String message) {
                getLogger().at(Level.INFO).log(message);
            }
            
            @Override
            public void warning(String message) {
                getLogger().at(Level.WARNING).log(message);
            }
            
            @Override
            public void severe(String message) {
                getLogger().at(Level.SEVERE).log(message);
            }
        };
    }
    
    @Override
    public void setup() {
        getLogger().at(Level.INFO).log("HytaleVehicles is setting up...");

        // Create logger wrapper
        this.logger = createLogger();

        // Initialize scheduler for delayed tasks
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initialize core systems
        this.vehicleRegistry = new VehicleRegistry(logger);
        this.vehicleAPI = new VehicleAPIImpl(vehicleRegistry, logger);
        this.vehicleLoader = new VehicleLoader(logger);
        this.vehicleLoader.setResourceClassLoader(getClass().getClassLoader());

        // Register VehicleDataComponent with Hytale's ECS
        // This component is persisted ON the entity by Hytale, so it survives world reloads
        registerVehicleDataComponent();

        // Register built-in vehicle types (like MTS's addItemPartCreator calls)
        registerBuiltInTypes();

        // Register custom interactions for spawn items
        registerInteractions();

        // Register commands
        registerCommands();

        // Load built-in example vehicle
        loadBuiltInVehicles();

        // Register death system to detect when vehicles are destroyed
        getEntityStoreRegistry().registerSystem(new VehicleDeathSystem());
        logger.info("Registered VehicleDeathSystem for real-time death detection");

        // Register reconnect system to restore BaseVehicle wrappers for ECS-persisted entities
        getEntityStoreRegistry().registerSystem(new com.alexispace.hyvehicles.system.VehicleReconnectSystem());
        logger.info("Registered VehicleReconnectSystem for ECS persistence");

        // Register tick system for vehicle physics and input processing
        getEntityStoreRegistry().registerSystem(new com.alexispace.hyvehicles.system.VehicleTickSystem());
        logger.info("Registered VehicleTickSystem for physics and input processing");

        // Register control system for steering based on player look direction
        getEntityStoreRegistry().registerSystem(new com.alexispace.hyvehicles.system.VehicleControlSystem());
        logger.info("Registered VehicleControlSystem for steering");

        // Initialize JSON persistence
        java.nio.file.Path saveDir = getDataDirectory().resolve("saves");
        this.vehiclePersistence = new VehiclePersistence(logger, saveDir);
        logger.info("Vehicle save directory: " + saveDir.toAbsolutePath());

        // Initialize mount system for F key mounting
        this.mountSystem = new VehicleMountSystem(logger);
        logger.info("Initialized VehicleMountSystem");

        // Register event to respawn vehicles when player joins world
        // Uses World.execute() to schedule spawn on world thread (fixes threading issue)
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);
        logger.info("Registered AddPlayerToWorldEvent for auto-respawn");

        // Register event to save vehicles when player leaves world
        getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, this::onPlayerLeavingWorld);
        logger.info("Registered DrainPlayerFromWorldEvent for auto-save");

        // Register packet listener for F key mounting (SyncInteractionChains packet ID 290)
        registerFKeyPacketListener();
        logger.info("Registered packet listener for F key mounting");

        getLogger().at(Level.INFO).log("HytaleVehicles setup complete");
    }

    /**
     * Register packet listener for F key (Use interaction) to mount/dismount vehicles.
     * Uses MouseInteraction packet (ID 111) which contains worldInteraction with target entity.
     */
    private void registerFKeyPacketListener() {
        // PlayerPacketWatcher gives us (PlayerRef, Packet) directly
        PacketAdapters.registerInbound((PlayerPacketWatcher) (PlayerRef playerRef, Packet packet) -> {
            int packetId = packet.getId();

            // SyncInteractionChains (ID 290) is sent when player presses F to interact
            // MouseInteraction (ID 111) is for mouse clicks - NOT for F key!
            if (packetId == 290) {
                logger.info("SyncInteractionChains packet received - checking for F key interaction");
                handleFKeyFromSyncPacket(playerRef, packet);
            }
        });
    }

    /**
     * Handle F key from SyncInteractionChains packet.
     * This packet is sent when player presses F to interact with an entity.
     * The target entity is identified by an integer entityId (network ID), not a Ref.
     */
    @SuppressWarnings("unchecked")
    private void handleFKeyFromSyncPacket(PlayerRef playerRef, Packet packet) {
        if (playerRef == null) return;

        var world = this.cachedWorld;
        if (world == null) return;

        // Cast to proper packet type
        SyncInteractionChains interactionChains = (SyncInteractionChains) packet;
        SyncInteractionChain[] updates = interactionChains.updates;

        if (updates == null || updates.length == 0) {
            return;
        }

        // Find F key (Use) interaction and extract target entity ID
        int targetEntityId = -1;
        boolean isUseInteraction = false;

        logger.info("=== F KEY PACKET PARSING ===");
        logger.info("Number of interaction chains: " + updates.length);

        for (int i = 0; i < updates.length; i++) {
            SyncInteractionChain chain = updates[i];
            logger.info("Chain[" + i + "] interactionType=" + chain.interactionType);

            // Check if this is F key (Use interaction)
            if (chain.interactionType == InteractionType.Use) {
                isUseInteraction = true;
                logger.info("  -> This is a USE interaction (F key)");

                // Extract entityId from InteractionChainData using reflection
                try {
                    logger.info("  Chain fields:");
                    for (var field : chain.getClass().getDeclaredFields()) {
                        field.setAccessible(true);
                        Object value = field.get(chain);
                        logger.info("    " + field.getName() + " = " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");

                        // Check InteractionChainData for entityId
                        if (field.getName().equals("data") && value != null) {
                            logger.info("    -> Data object fields:");
                            for (var dataField : value.getClass().getDeclaredFields()) {
                                dataField.setAccessible(true);
                                Object dataValue = dataField.get(value);
                                logger.info("       " + dataField.getName() + " = " + dataValue + " (type: " + (dataValue != null ? dataValue.getClass().getSimpleName() : "null") + ")");

                                // entityId is an integer - the network ID of the target entity
                                if (dataField.getName().equals("entityId")) {
                                    Object idValue = dataField.get(value);
                                    if (idValue instanceof Integer) {
                                        targetEntityId = (Integer) idValue;
                                        logger.info("       *** FOUND entityId (Integer): " + targetEntityId);
                                    } else if (idValue instanceof Number) {
                                        targetEntityId = ((Number) idValue).intValue();
                                        logger.info("       *** FOUND entityId (Number): " + targetEntityId);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error reading entityId from packet: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        logger.info("=== END PACKET PARSING, targetEntityId=" + targetEntityId + " ===");

        if (!isUseInteraction) {
            // Not F key, ignore
            return;
        }

        if (targetEntityId <= 0) {
            logger.info("F key pressed but no valid entityId in packet");
            return;
        }

        final int finalTargetId = targetEntityId;

        // Schedule on world thread
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> playerEntityRef = playerRef.getReference();

                // Check if player is already mounted
                if (mountSystem.isPlayerMounted(playerRef, store)) {
                    // Get current vehicle
                    Ref<EntityStore> currentVehicleRef = mountSystem.getMountedVehicle(playerRef, store);

                    // Check if clicking on the SAME vehicle or a DIFFERENT one
                    if (currentVehicleRef != null && currentVehicleRef.isValid()) {
                        var networkIdType = com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType();
                        var currentNetworkId = store.getComponent(currentVehicleRef, networkIdType);
                        int currentVehicleNetworkId = currentNetworkId != null ? currentNetworkId.getId() : -1;

                        logger.info("Player already mounted to vehicle with NetworkId=" + currentVehicleNetworkId +
                                   ", clicked on NetworkId=" + finalTargetId);

                        // If clicking on a DIFFERENT vehicle, dismount and continue to mount the new one
                        if (currentVehicleNetworkId != finalTargetId) {
                            logger.info("Clicked on different vehicle - dismounting from current and mounting new");
                            mountSystem.dismountPlayer(playerRef, store);
                            // Don't return - continue to mount the new vehicle below
                        } else {
                            // Clicking on the SAME vehicle - try to cycle seats
                            VehicleDataComponent vehicleData = store.getComponent(currentVehicleRef, VehicleDataComponent.getComponentType());
                            if (vehicleData != null) {
                                String definitionId = vehicleData.getDefinitionId();
                                VehicleDefinition definition = vehicleRegistry.getDefinition(definitionId);
                                if (definition != null && definition.seats.size() > 1) {
                                    float scale = definition.modelScale > 0 ? definition.modelScale : 1.0f;
                                    boolean seatsPreScaled = definition.blockyModelPath != null && !definition.blockyModelPath.isEmpty();
                                    boolean cycled = mountSystem.cycleSeat(playerRef, store, vehicleData,
                                        definition.seats, scale, definition.modelYOffset, seatsPreScaled);
                                    if (cycled) {
                                        logger.info("Player cycled to next seat");
                                        return;
                                    }
                                    // Could not cycle - all other seats occupied, dismount
                                    logger.info("All other seats occupied - dismounting");
                                }
                                // Single seat vehicle or no seats to cycle to - dismount
                            }
                            // Dismount
                            mountSystem.dismountPlayer(playerRef, store);
                            logger.info("Player dismounted via F key");
                            return;
                        }
                    } else {
                        // Invalid vehicle ref, just dismount
                        mountSystem.dismountPlayer(playerRef, store);
                        logger.info("Player dismounted via F key (invalid vehicle ref)");
                        return;
                    }
                }

                // Look up entity by NetworkId - find the entity with matching network ID
                Ref<EntityStore> targetEntityRef = findEntityByNetworkId(store, finalTargetId);

                if (targetEntityRef == null || !targetEntityRef.isValid()) {
                    logger.info("Could not find entity with networkId: " + finalTargetId);
                    return;
                }

                // Check if target is a vehicle
                VehicleDataComponent vehicleData = store.getComponent(targetEntityRef, VehicleDataComponent.getComponentType());
                if (vehicleData == null) {
                    logger.info("F key target (networkId=" + finalTargetId + ") is not a vehicle");
                    return;
                }

                logger.info("=== MOUNTING TO VEHICLE ===");
                logger.info("  targetEntityRef=" + targetEntityRef);
                logger.info("  vehicleData.definitionId=" + vehicleData.getDefinitionId());

                // Get NetworkId of the target ref to double-check
                var targetNetworkIdComp = store.getComponent(targetEntityRef,
                    com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType());
                logger.info("  targetEntityRef's NetworkId=" + (targetNetworkIdComp != null ? targetNetworkIdComp.getId() : "null"));
                logger.info("  Expected NetworkId from packet=" + finalTargetId);

                // Ensure we have a BaseVehicle wrapper for this entity (creates on-demand if needed)
                var vehicleWrapper = createVehicleWrapperOnDemand(targetEntityRef, vehicleData, store, world);
                if (vehicleWrapper == null) {
                    logger.warning("Failed to create vehicle wrapper");
                    return;
                }

                logger.info("  vehicleWrapper.instanceId=" + vehicleWrapper.getInstanceId());
                logger.info("  vehicleWrapper.entityRef=" + vehicleWrapper.getEntityRef());

                mountToVehicle(playerRef, targetEntityRef, vehicleData, store);
                logger.info("=== END MOUNTING ===");

            } catch (Exception e) {
                logger.warning("Error handling F key: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Find an entity by its NetworkId value.
     * NetworkId is the integer ID sent to clients for entity sync.
     *
     * We try multiple approaches:
     * 1. Check our registry first
     * 2. Try to find a Ref by iterating possible indices
     * 3. Use reflection to explore available lookup methods
     */
    private Ref<EntityStore> findEntityByNetworkId(Store<EntityStore> store, int targetNetworkId) {
        try {
            logger.info("=== SEARCHING FOR ENTITY WITH networkId=" + targetNetworkId + " ===");

            var networkIdType = com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType();
            if (networkIdType == null) {
                logger.warning("NetworkId component type not available");
                return null;
            }

            // First check our registry (for vehicles we've already wrapped)
            logger.info("Checking registry (has " + vehicleRegistry.getSpawnedCount() + " vehicles)...");
            int vehicleIndex = 0;
            for (var vehicle : vehicleRegistry.getAllVehicles()) {
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                logger.info("  Vehicle[" + vehicleIndex + "]: def=" + vehicle.getDefinition().id +
                           ", ref=" + vehicleRef + ", refValid=" + (vehicleRef != null ? vehicleRef.isValid() : "null"));

                if (vehicleRef == null || !vehicleRef.isValid()) {
                    vehicleIndex++;
                    continue;
                }

                var networkId = store.getComponent(vehicleRef, networkIdType);
                int vehicleNetworkId = networkId != null ? networkId.getId() : -1;
                logger.info("    NetworkId=" + vehicleNetworkId + ", looking for=" + targetNetworkId +
                           ", match=" + (vehicleNetworkId == targetNetworkId));

                if (networkId != null && networkId.getId() == targetNetworkId) {
                    logger.info("  >>> FOUND MATCH at index " + vehicleIndex + " <<<");
                    return vehicleRef;
                }
                vehicleIndex++;
            }

            // Cannot iterate all entities or lookup by networkId directly
            // Vehicle entities persisted by ECS won't be found until we have a wrapper
            // TODO: Use a TickingSystem to periodically scan for VehicleDataComponent entities
            logger.info("=== NO ENTITY FOUND WITH networkId=" + targetNetworkId + " ===");
            logger.info("Note: ECS-persisted vehicles need to be re-placed to create wrappers");
            return null;

        } catch (Exception e) {
            logger.warning("Error finding entity by NetworkId: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Mount player to a specific vehicle entity.
     * Helper method used by both packet-based and fallback mounting.
     * Automatically finds the next available seat.
     */
    private void mountToVehicle(PlayerRef playerRef, Ref<EntityStore> vehicleRef,
                                 VehicleDataComponent vehicleData, Store<EntityStore> store) {
        String definitionId = vehicleData.getDefinitionId();
        VehicleDefinition definition = vehicleRegistry.getDefinition(definitionId);

        logger.info("=== MOUNT DEBUG ===");
        logger.info("Definition ID: " + definitionId);
        logger.info("Definition found: " + (definition != null));

        float seatX = 0f, seatY = 0.5f, seatZ = 0f;
        int seatIndex = 0;

        if (definition != null) {
            float scale = definition.modelScale > 0 ? definition.modelScale : 1.0f;
            float modelYOffset = definition.modelYOffset;

            logger.info("Definition seats count: " + definition.seats.size());
            logger.info("Scale: " + scale + ", modelYOffset: " + modelYOffset);

            if (!definition.seats.isEmpty()) {
                // Find next available seat - prioritize driver seats
                int totalSeats = definition.seats.size();

                // First, try to find a driver seat (canControl=true)
                int driverSeat = -1;
                for (int i = 0; i < totalSeats; i++) {
                    if (!vehicleData.isSeatOccupied(i)) {
                        var seat = definition.seats.get(i);
                        if (seat.canControl) {
                            driverSeat = i;
                            break;
                        }
                    }
                }

                // If driver seat found, use it; otherwise use first available
                seatIndex = driverSeat >= 0 ? driverSeat : vehicleData.findAvailableSeat(totalSeats);
                logger.info("Finding available seat. Total: " + totalSeats + ", Driver seat: " + driverSeat + ", Final: " + seatIndex);

                if (seatIndex < 0) {
                    logger.info("All seats occupied on this vehicle");
                    return; // No seats available
                }

                var seat = definition.seats.get(seatIndex);
                logger.info("Seat " + seatIndex + " raw position: x=" + seat.x + ", y=" + seat.y + ", z=" + seat.z);

                // If seats were extracted from blockymodel, they're already in final coordinates
                // Don't multiply by modelScale again
                boolean seatsFromModel = definition.blockyModelPath != null && !definition.blockyModelPath.isEmpty();
                if (seatsFromModel) {
                    seatX = seat.x;
                    seatY = seat.y - modelYOffset;
                    seatZ = seat.z;
                    logger.info("Using pre-scaled seats from blockymodel");
                } else {
                    seatX = seat.x * scale;
                    seatY = (seat.y * scale) - modelYOffset;
                    seatZ = seat.z * scale;
                }

                logger.info("Seat " + seatIndex + " FINAL offset: x=" + seatX + ", y=" + seatY + ", z=" + seatZ);

                // Mark seat as occupied
                vehicleData.occupySeat(seatIndex);
                logger.info("Occupied seats after mount: " + vehicleData.getOccupiedSeatCount());
            } else {
                logger.info("No seats defined, using default Y offset");
                seatY = (0.5f * scale) - modelYOffset;
            }
        } else {
            logger.warning("Definition is NULL for " + definitionId);
        }

        logger.info("Calling mountPlayer with offset: (" + seatX + ", " + seatY + ", " + seatZ + ")");
        boolean success = mountSystem.mountPlayer(playerRef, vehicleRef, store, seatX, seatY, seatZ, seatIndex);
        if (success) {
            logger.info("SUCCESS: Player mounted vehicle at seat " + seatIndex);
        } else {
            logger.warning("FAILED: Mount failed, vacating seat " + seatIndex);
            // Mount failed, vacate the seat
            vehicleData.vacateSeat(seatIndex);
        }
        logger.info("=== END MOUNT DEBUG ===");
    }

    /**
     * Handle player joining world.
     * Load vehicles from JSON save file (if they haven't been loaded yet).
     */
    private void onPlayerAddedToWorld(AddPlayerToWorldEvent event) {
        var world = event.getWorld();
        if (world == null) return;

        // Cache the world reference for F key mounting
        this.cachedWorld = world;

        // Load vehicles from JSON (only once per world, thread-safe)
        if (vehiclesRespawned.compareAndSet(false, true)) {
            // CRITICAL: Delay vehicle spawn by 2 seconds to let client fully join world
            // EntityRefs become invalid if spawned before GameInstance.StartJoiningWorld completes
            scheduler.schedule(() -> {
                world.execute(() -> {
                    try {
                        var savedVehicles = vehiclePersistence.loadVehicles();
                        if (!savedVehicles.isEmpty()) {
                            logger.info("Auto-loading " + savedVehicles.size() + " vehicles from JSON (after 2s delay)...");
                            int count = respawnVehiclesInternal(world, savedVehicles);
                            logger.info("Auto-loaded " + count + " vehicles from JSON persistence");
                        } else {
                            logger.info("No saved vehicles to load");
                        }
                    } catch (Exception e) {
                        logger.severe("Failed to auto-load vehicles: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }, 2, TimeUnit.SECONDS); // 2 seconds delay
        } else {
            logger.info("Player joined world - vehicles already loaded");
        }
    }

    /**
     * Create a BaseVehicle wrapper for an existing ECS entity on-demand.
     * Called when player interacts with a vehicle entity that doesn't have a wrapper yet.
     *
     * @param entityRef The entity reference
     * @param vehicleData The VehicleDataComponent
     * @param store The entity store
     * @param world The world
     * @return The created BaseVehicle, or null if creation failed
     */
    private com.alexispace.hyvehicles.entity.BaseVehicle createVehicleWrapperOnDemand(
            Ref<EntityStore> entityRef,
            VehicleDataComponent vehicleData,
            Store<EntityStore> store,
            com.hypixel.hytale.server.core.universe.world.World world) {

        String definitionId = vehicleData.getDefinitionId();
        if (definitionId == null || definitionId.isEmpty()) {
            logger.warning("Vehicle entity has no definition ID");
            return null;
        }

        // Check if we already have a wrapper for this entity
        for (var existing : vehicleRegistry.getAllVehicles()) {
            Ref<EntityStore> existingRef = existing.getEntityRef();
            if (existingRef != null && existingRef.equals(entityRef)) {
                logger.info("Vehicle already has wrapper: " + definitionId);
                return existing;
            }
        }

        // Get definition
        VehicleDefinition definition = vehicleRegistry.getDefinition(definitionId);
        if (definition == null) {
            logger.warning("Unknown vehicle definition: " + definitionId);
            return null;
        }

        // Get position from TransformComponent
        var transform = store.getComponent(entityRef,
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        if (transform == null) {
            logger.warning("Vehicle entity has no TransformComponent");
            return null;
        }

        var position = transform.getPosition();
        float yaw = transform.getRotation().getYaw();
        float yOffset = definition.modelYOffset;

        var vehiclePos = new com.alexispace.hyvehicles.util.Vec3(
            (float) position.x,
            (float) position.y - yOffset,
            (float) position.z
        );

        // Create BaseVehicle wrapper
        var creator = vehicleRegistry.getCreator(definition.type);
        if (creator == null) {
            logger.warning("No creator for vehicle type: " + definition.type);
            return null;
        }

        var vehicle = creator.createVehicle(definition, vehiclePos, yaw);
        vehicle.setWorld(world);
        vehicle.setEntityRef(entityRef);

        // Set up collision checker
        var bridge = getEntityBridge();
        if (bridge != null) {
            vehicle.setCollisionChecker(bridge.createCollisionChecker(world));
        }

        // Track it
        vehicleRegistry.trackVehicle(vehicle);

        logger.info("Created on-demand wrapper for vehicle: " + definitionId +
                   " at " + vehiclePos + " (entityRef=" + entityRef + ")");

        return vehicle;
    }

    /**
     * Handle player leaving world - cleanup and reset state.
     * Vehicles are saved to JSON in shutdown() method.
     */
    private void onPlayerLeavingWorld(DrainPlayerFromWorldEvent event) {
        Store<EntityStore> store = null;

        // CRITICAL: Remove NPCMountComponent from all vehicles before saving
        // This prevents world corruption on reload
        try {
            for (var vehicle : vehicleRegistry.getAllVehicles()) {
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef != null && vehicleRef.isValid()) {
                    if (store == null) {
                        store = vehicleRef.getStore(); // Get store once for reuse
                    }
                    if (store != null) {
                        var mountComp = store.getComponent(vehicleRef,
                            com.hypixel.hytale.builtin.mounts.NPCMountComponent.getComponentType());
                        if (mountComp != null) {
                            store.removeComponent(vehicleRef,
                                com.hypixel.hytale.builtin.mounts.NPCMountComponent.getComponentType());
                            logger.info("Removed NPCMountComponent from vehicle before save");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error cleaning up mount components: " + e.getMessage());
        }

        // Clear mount tracking and reset player MovementConfigs
        if (mountSystem != null && store != null) {
            mountSystem.clearAllMounts(store);
        } else if (mountSystem != null) {
            logger.warning("No store available - using deprecated clearAllMounts()");
            mountSystem.clearAllMounts();
        }

        // Save vehicles to JSON (before world unloads)
        saveVehicles();

        // Remove ECS entities so they don't persist in world save
        // We'll respawn from JSON on next join
        int vehicleCount = vehicleRegistry.getSpawnedCount();
        for (var vehicle : vehicleRegistry.getAllVehicles()) {
            try {
                // First dismount all passengers
                vehicle.destroy();

                // Then remove the ECS entity from the world
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef != null && vehicleRef.isValid()) {
                    if (store == null) {
                        store = vehicleRef.getStore();
                    }
                    if (store != null) {
                        store.removeEntity(vehicleRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                        logger.info("Removed ECS entity for vehicle: " + vehicle.getDefinition().id);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to remove vehicle during world drain: " + e.getMessage());
            }
        }
        vehicleRegistry.clearAllTracking();

        logger.info("Saved and removed " + vehicleCount + " vehicles (will reload from JSON)");
        vehiclesRespawned.set(false); // Reset so we load vehicles on next join

        // Clear VehicleReconnectSystem cache (if still registered)
        try {
            com.alexispace.hyvehicles.system.VehicleReconnectSystem.clearCache();
        } catch (Exception ignored) {
            // VehicleReconnectSystem may not exist anymore
        }

        // Clear cached world reference
        this.cachedWorld = null;

        logger.info("Cleared vehicle wrappers - vehicles will be saved to JSON on shutdown");
    }

    /**
     * Internal method to spawn vehicles - called on world thread.
     */
    private int respawnVehiclesInternal(com.hypixel.hytale.server.core.universe.world.World world,
                                         java.util.List<VehiclePersistence.VehicleSaveData> savedVehicles) {
        int successCount = 0;
        for (var data : savedVehicles) {
            try {
                // Use exact saved position - don't adjust unless clearly invalid
                float adjustedY = data.y;

                // Only adjust if clearly invalid (underground or too high)
                if (adjustedY < -20) {
                    logger.warning("Vehicle " + data.definitionId + " saved at invalid Y=" + adjustedY + ", adjusting to 65");
                    adjustedY = 65;
                } else if (adjustedY > 320) {
                    logger.warning("Vehicle " + data.definitionId + " saved at invalid Y=" + adjustedY + ", adjusting to 100");
                    adjustedY = 100;
                }

                var pos = new com.alexispace.hyvehicles.util.Vec3(data.x, adjustedY, data.z);
                logger.info("Spawning " + data.definitionId + " at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");

                var handle = vehicleAPI.spawnVehicle(data.definitionId, pos, data.yaw, world);
                if (handle != null) {
                    successCount++;
                    logger.info("Successfully spawned " + data.definitionId);

                    // CRITICAL: Verify the vehicle has a valid wrapper with entityRef
                    // Without this, water vehicles sink and die (no buoyancy physics)
                    com.alexispace.hyvehicles.entity.BaseVehicle vehicle = handle.getVehicle();
                    if (vehicle != null && vehicle.getEntityRef() != null) {
                        logger.info("Vehicle has valid wrapper and entityRef");
                    } else {
                        logger.warning("WARNING: Vehicle spawned but wrapper/ref is invalid!");
                    }
                } else {
                    logger.warning("Failed to spawn " + data.definitionId + " - handle is null");
                }
            } catch (Exception e) {
                logger.warning("Failed to respawn " + data.definitionId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Clear save file after respawn
        vehiclePersistence.clearSaveFile();
        return successCount;
    }

    /**
     * Register VehicleDataComponent with Hytale's ECS.
     * This component is persisted ON the entity by Hytale.
     */
    private void registerVehicleDataComponent() {
        try {
            ComponentType<EntityStore, VehicleDataComponent> type = getEntityStoreRegistry().registerComponent(
                VehicleDataComponent.class,
                "hyvehicles:vehicle_data",
                VehicleDataComponent.CODEC
            );
            VehicleDataComponent.setComponentType(type);
            logger.info("Registered VehicleDataComponent with Hytale ECS");
        } catch (Exception e) {
            logger.severe("Failed to register VehicleDataComponent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register built-in vehicle type creators.
     * Similar to MTS's InterfaceManager static initialization.
     */
    private void registerBuiltInTypes() {
        // Phase 1: Water vehicles
        vehicleRegistry.registerCreator("BOAT", BoatCreator.INSTANCE);
        
        // Phase 2: Ground vehicles (uncomment when implemented)
        // vehicleRegistry.registerCreator("CART", CartCreator.INSTANCE);
        // vehicleRegistry.registerCreator("CAR", CarCreator.INSTANCE);
        
        logger.info("Registered " + vehicleRegistry.getTypeCount() + " built-in vehicle types");
    }

    /**
     * Register custom interactions for vehicle spawn items.
     */
    @SuppressWarnings("unchecked")
    private void registerInteractions() {
        // Register DIRECTLY to Interaction.CODEC like DeployablesPlugin does
        // This ensures the codec is available when item JSONs are parsed

        // Register our VehicleDeployableConfig type for ghost preview support
        DeployableConfig.CODEC.register(
            "Vehicle",
            (Class<DeployableConfig>) (Class<?>) VehicleDeployableConfig.class,
            (com.hypixel.hytale.codec.Codec<DeployableConfig>) (com.hypixel.hytale.codec.Codec<?>) VehicleDeployableConfig.CODEC
        );
        getLogger().at(Level.INFO).log("Registered deployable config type: Vehicle");

        // Basic spawn interaction (no preview)
        Interaction.CODEC.register(
            "hyvehicles_spawn_vehicle",
            SpawnVehicleInteraction.class,
            SpawnVehicleInteraction.CODEC
        );
        getLogger().at(Level.INFO).log("Registered interaction type: hyvehicles_spawn_vehicle");

        // NPC boat spawn interaction with water surface detection
        Interaction.CODEC.register(
            "hyvehicles_spawn_npc_boat",
            SpawnNPCBoatInteraction.class,
            SpawnNPCBoatInteraction.CODEC
        );
        getLogger().at(Level.INFO).log("Registered interaction type: hyvehicles_spawn_npc_boat");
    }

    /**
     * Register plugin commands with Hytale's command system.
     */
    private void registerCommands() {
        // Register the /hv command using Hytale's command API
        getCommandRegistry().registerCommand(new HvCommand());
        
        logger.info("Registered /hv command");
        logger.info("Commands: /hv spawn, /hv list, /hv types, /hv info, /hv help");
    }
    
    /**
     * Load built-in example vehicles from resources.
     * Loads vehicle definitions from JSON files in resources/vehicles/
     */
    private void loadBuiltInVehicles() {
        // Load vehicles from JSON files in resources
        String[] vehicleFiles = {"vehicles/simple_boat.json", "vehicles/rowboat.json"};

        for (String resourcePath : vehicleFiles) {
            try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    logger.warning("Vehicle resource not found: " + resourcePath);
                    continue;
                }

                VehicleDefinition def = vehicleLoader.loadFromStream(stream, resourcePath);
                if (def != null) {
                    vehicleAPI.registerVehicle(def);
                    logger.info("Loaded vehicle from JSON: " + def.id + " with " + def.seats.size() + " seats");
                    // Log seat positions for debugging
                    for (int i = 0; i < def.seats.size(); i++) {
                        var seat = def.seats.get(i);
                        logger.info("  Seat " + i + ": z=" + seat.z + ", y=" + seat.y + ", driver=" + seat.isDriver);
                    }
                }
            } catch (Exception e) {
                logger.severe("Failed to load vehicle from " + resourcePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void start() {
        logger.info("HytaleVehicles has started!");
        logger.info("API Version: " + VehicleAPI.API_VERSION);
        logger.info("Registered " + vehicleRegistry.getTypeCount() + " vehicle types");
        logger.info("Registered " + vehicleRegistry.getVehicleCount() + " vehicle definitions");
        logger.info("Using JSON persistence for vehicles");
    }
    
    @Override
    public void shutdown() {
        logger.info("HytaleVehicles is shutting down...");

        // CRITICAL: Remove NPCMountComponent from all vehicles to prevent world corruption
        // This component causes NPE on chunk load if the vehicle doesn't have full NPC setup
        try {
            for (var vehicle : vehicleRegistry.getAllVehicles()) {
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef != null && vehicleRef.isValid()) {
                    Store<EntityStore> store = vehicleRef.getStore();
                    if (store != null) {
                        var mountComp = store.getComponent(vehicleRef,
                            com.hypixel.hytale.builtin.mounts.NPCMountComponent.getComponentType());
                        if (mountComp != null) {
                            store.removeComponent(vehicleRef,
                                com.hypixel.hytale.builtin.mounts.NPCMountComponent.getComponentType());
                            logger.info("Removed NPCMountComponent from vehicle on shutdown");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error cleaning up mount components: " + e.getMessage());
        }

        // Clear mount tracking
        if (mountSystem != null) {
            mountSystem.clearAllMounts();
        }

        // Save all spawned vehicles to JSON
        saveVehicles();

        // Remove ECS entities and destroy vehicle wrappers
        int vehicleCount = vehicleRegistry.getSpawnedCount();
        for (var vehicle : vehicleRegistry.getAllVehicles()) {
            try {
                // First dismount all passengers
                vehicle.destroy();

                // Then remove the ECS entity from the world
                Ref<EntityStore> vehicleRef = vehicle.getEntityRef();
                if (vehicleRef != null && vehicleRef.isValid()) {
                    Store<EntityStore> store = vehicleRef.getStore();
                    if (store != null) {
                        store.removeEntity(vehicleRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                        logger.info("Removed ECS entity for vehicle: " + vehicle.getDefinition().id);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to remove vehicle during shutdown: " + e.getMessage());
            }
        }

        logger.info("Cleaned up " + vehicleCount + " vehicles (wrappers + ECS entities)");

        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    /**
     * Save all spawned vehicles to JSON.
     */
    private void saveVehicles() {
        var vehicles = vehicleRegistry.getAllVehicles();
        var saveData = new java.util.ArrayList<VehiclePersistence.VehicleSaveData>();

        for (var vehicle : vehicles) {
            float[] pos = vehicle.getPositionArray();
            saveData.add(new VehiclePersistence.VehicleSaveData(
                vehicle.getDefinition().id,
                pos[0], pos[1], pos[2],
                vehicle.getRotationYaw()
            ));
        }

        vehiclePersistence.saveVehicles(saveData);
    }

    /**
     * Load and spawn vehicles from JSON save file.
     * Must be called from the world thread (e.g., from /hv reload command).
     *
     * @param world The world to spawn vehicles in
     * @return Number of vehicles spawned
     */
    public int loadAndSpawnVehicles(com.hypixel.hytale.server.core.universe.world.World world) {
        var savedVehicles = vehiclePersistence.loadVehicles();
        if (savedVehicles.isEmpty()) {
            logger.info("No saved vehicles to load");
            return 0;
        }

        logger.info("Loading " + savedVehicles.size() + " vehicles from JSON...");

        int successCount = 0;
        for (var data : savedVehicles) {
            try {
                var pos = new com.alexispace.hyvehicles.util.Vec3(data.x, data.y, data.z);
                var handle = vehicleAPI.spawnVehicle(data.definitionId, pos, data.yaw, world);
                if (handle != null) {
                    logger.info("Loaded: " + data.definitionId);
                    successCount++;
                }
            } catch (Exception e) {
                logger.warning("Failed to load " + data.definitionId + ": " + e.getMessage());
            }
        }

        // Clear save file after loading
        vehiclePersistence.clearSaveFile();

        logger.info("Loaded " + successCount + " vehicles from JSON backup");
        return successCount;
    }

    // ==================== Static Access ====================
    
    /**
     * Get the plugin instance.
     */
    public static HytaleVehiclesPlugin getInstance() {
        return instance;
    }
    
    /**
     * Get the vehicle API for content packs.
     * 
     * @return The VehicleAPI
     */
    public static VehicleAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("HytaleVehicles is not loaded");
        }
        return instance.vehicleAPI;
    }
    
    /**
     * Get the vehicle registry (internal use).
     */
    public VehicleRegistry getRegistry() {
        return vehicleRegistry;
    }

    /**
     * Get the entity bridge for direct entity operations.
     * Used by VehicleDeathSystem for item drops after world reload.
     */
    public VehicleEntityBridge getEntityBridge() {
        if (vehicleAPI instanceof VehicleAPIImpl) {
            return ((VehicleAPIImpl) vehicleAPI).getEntityBridge();
        }
        return null;
    }

    /**
     * Get the vehicle loader.
     */
    public VehicleLoader getLoader() {
        return vehicleLoader;
    }

    /**
     * Get the vehicle mount system.
     * Used by VehicleTickSystem for NPC boat input processing.
     */
    public VehicleMountSystem getMountSystem() {
        return mountSystem;
    }

}
