package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.protocol.CollisionType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
// Mount components for vehicle mounting (without NPC system)
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
// Interactable component for F key interaction
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.alexispace.hyvehicles.definition.SeatDefinition;

/**
 * Bridge between our internal BaseVehicle physics and Hytale's ECS.
 *
 * <p>This class handles:</p>
 * <ul>
 *   <li>Spawning Hytale entities with TransformComponent</li>
 *   <li>Syncing our physics simulation to the ECS entity</li>
 *   <li>Cleanup when vehicle is destroyed</li>
 * </ul>
 *
 * <p>Architecture:</p>
 * <pre>
 * BaseVehicle (physics simulation)
 *      ↓
 * VehicleEntityBridge (sync layer)
 *      ↓
 * Hytale ECS Entity (TransformComponent, ModelComponent)
 *      ↓
 * Client renders entity
 * </pre>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleEntityBridge {

    private final VehicleLogger logger;

    public VehicleEntityBridge(VehicleLogger logger) {
        this.logger = logger;
    }

    /**
     * Spawn a Hytale entity for this vehicle.
     *
     * @param vehicle The vehicle physics object
     * @param world The world to spawn in
     * @return The entity reference, or null if spawn failed
     */
    public Ref<EntityStore> spawnEntity(BaseVehicle vehicle, World world) {
        if (world == null) {
            logger.severe("Cannot spawn entity: world is null");
            return null;
        }
        return spawnEntityWithStore(vehicle, world.getEntityStore().getStore());
    }

    /**
     * Spawn a Hytale entity using a CommandBuffer for deferred entity creation.
     * This must be used during interactions to avoid "Store is currently processing" errors.
     *
     * @param vehicle The vehicle physics object
     * @param commandBuffer The command buffer for deferred operations
     * @return The entity reference, or null if spawn failed
     */
    public Ref<EntityStore> spawnEntityWithCommandBuffer(BaseVehicle vehicle, CommandBuffer commandBuffer) {
        try {
            Store<EntityStore> store = commandBuffer.getStore();

            // Create holder (entity blueprint)
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Add TransformComponent for position/rotation
            // Apply modelYOffset to compensate for model origin (center vs bottom)
            float[] pos = vehicle.getPositionArray();
            float yOffset = vehicle.getDefinition().modelYOffset;
            logger.info("=== SPAWN DEBUG === Vehicle: " + vehicle.getDefinition().id +
                        ", modelYOffset from JSON: " + yOffset +
                        ", original Y: " + pos[1] +
                        ", adjusted Y: " + (pos[1] + yOffset));
            TransformComponent transform = new TransformComponent(
                new Vector3d(pos[0], pos[1] + yOffset, pos[2]),
                new Vector3f(0, vehicle.getRotationYaw(), 0)
            );
            holder.addComponent(TransformComponent.getComponentType(), transform);

            // Add ModelComponent with scaled model and custom collision box
            // loadModel creates the model with our vehicle's collision dimensions
            VehicleDefinition def = vehicle.getDefinition();
            Model model = loadModel(def);
            logger.info("=== MODEL DEBUG === modelId: " + def.modelId +
                        ", collision box from model: " + model.getBoundingBox());
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));

            // Use the model's bounding box (which has our custom collision dimensions)
            // Also set DetailBoxes for complex collision shapes (walls, floor, etc.)
            BoundingBox boundingBox = new BoundingBox(model.getBoundingBox());
            if (model.getDetailBoxes() != null && !model.getDetailBoxes().isEmpty()) {
                boundingBox.setDetailBoxes(model.getDetailBoxes());
                logger.info("Set DetailBoxes on BoundingBox: " + model.getDetailBoxes().size() + " groups");
                for (var entry : model.getDetailBoxes().entrySet()) {
                    logger.info("  DetailBox group '" + entry.getKey() + "': " + entry.getValue().length + " boxes");
                }
            } else {
                logger.info("No DetailBoxes found in model");
            }
            holder.addComponent(BoundingBox.getComponentType(), boundingBox);

            // NOTE: Collision components removed - was causing world loading issues
            // addCollisionComponents(holder, def);

            // Add RespondToHit to make entity respond to damage
            try {
                holder.addComponent(RespondToHit.getComponentType(), RespondToHit.INSTANCE);
                logger.info("Added RespondToHit for damage response");
            } catch (Exception e) {
                logger.warning("Could not add RespondToHit: " + e.getMessage());
            }

            // Ensure EntityStatMap exists and set initial health on the Holder
            // This MUST be done on the Holder BEFORE adding entity (can't access store during processing)
            try {
                EntityStatMap statMap = holder.ensureAndGetComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();

                    // Set max health using modifier (default is 100, we want 20, so add -80)
                    // Using ADDITIVE modifier to reduce max from 100 to our desired value
                    float maxHealthReduction = DEFAULT_VEHICLE_HEALTH - 100.0f;  // -80 for 20 max health
                    StaticModifier maxHealthModifier = new StaticModifier(
                        ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE,
                        maxHealthReduction
                    );
                    statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);

                    // Set current health to max
                    statMap.setStatValue(healthIndex, DEFAULT_VEHICLE_HEALTH);
                    logger.info("Set health to " + DEFAULT_VEHICLE_HEALTH + "/" + DEFAULT_VEHICLE_HEALTH + " on Holder for vehicle");
                } else {
                    logger.warning("Could not get EntityStatMap from holder");
                }
            } catch (Exception e) {
                logger.warning("Could not initialize health on Holder: " + e.getMessage());
            }

            // Ensure UIComponentList exists for damage numbers and health bar display
            try {
                holder.ensureComponent(UIComponentList.getComponentType());
                logger.info("Ensured UIComponentList for damage indicators");
            } catch (Exception e) {
                logger.warning("Could not ensure UIComponentList: " + e.getMessage());
            }

            // Ensure UUIDComponent exists
            holder.ensureComponent(UUIDComponent.getComponentType());

            // Add VehicleDataComponent - persisted by Hytale with the entity
            // (def already defined above for collision box)
            if (VehicleDataComponent.getComponentType() != null) {
                holder.addComponent(VehicleDataComponent.getComponentType(),
                    new VehicleDataComponent(def.id, def.dropItemId, def.dropQuantity));
                logger.info("Added VehicleDataComponent for persistence");
            }

            // Add NetworkId for client visibility (REQUIRED for entity to be seen!)
            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            // Add Interactable component for F key interaction (mount/dismount)
            try {
                holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
                logger.info("Added Interactable component for F key support");
            } catch (Exception e) {
                logger.warning("Could not add Interactable component: " + e.getMessage());
            }

            // NOTE: MountedByComponent is added dynamically when mounting (not at spawn)
            // Adding it at spawn breaks entity persistence/serialization

            // Spawn entity using CommandBuffer for deferred execution (avoids Store processing error)
            Ref<EntityStore> entityRef = commandBuffer.addEntity(holder, AddReason.SPAWN);

            // Set up block collision checker using World from EntityStore
            try {
                EntityStore entityStore = store.getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    vehicle.setCollisionChecker(createCollisionChecker(world));
                    logger.info("Set up block collision for vehicle: " + vehicle.getDefinition().id);
                }
            } catch (Exception e) {
                logger.warning("Could not set up block collision: " + e.getMessage());
            }

            logger.info("Spawned Hytale entity for vehicle: " + vehicle.getDefinition().id);

            // Log available APIs on first spawn
            logAvailableMethods();

            return entityRef;

        } catch (Exception e) {
            logger.severe("Failed to spawn Hytale entity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Spawn a Hytale entity using the store directly.
     * WARNING: Do NOT use this during interactions - use spawnEntityWithCommandBuffer instead.
     *
     * @param vehicle The vehicle physics object
     * @param store The entity store
     * @return The entity reference, or null if spawn failed
     */
    public Ref<EntityStore> spawnEntityWithStore(BaseVehicle vehicle, Store<EntityStore> store) {
        try {
            // Create holder (entity blueprint)
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Add TransformComponent for position/rotation
            // Apply modelYOffset to compensate for model origin (center vs bottom)
            float[] pos = vehicle.getPositionArray();
            float yOffset = vehicle.getDefinition().modelYOffset;
            TransformComponent transform = new TransformComponent(
                new Vector3d(pos[0], pos[1] + yOffset, pos[2]),
                new Vector3f(0, vehicle.getRotationYaw(), 0)
            );
            holder.addComponent(TransformComponent.getComponentType(), transform);

            // Add ModelComponent with scaled model and custom collision box
            VehicleDefinition def = vehicle.getDefinition();
            Model model = loadModel(def);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));

            // Use the model's bounding box (which has our custom collision dimensions)
            // Also set DetailBoxes for complex collision shapes (walls, floor, etc.)
            BoundingBox boundingBox2 = new BoundingBox(model.getBoundingBox());
            if (model.getDetailBoxes() != null && !model.getDetailBoxes().isEmpty()) {
                boundingBox2.setDetailBoxes(model.getDetailBoxes());
                logger.info("Set DetailBoxes on BoundingBox: " + model.getDetailBoxes().size() + " groups");
            } else {
                logger.info("No DetailBoxes found in model (Store spawn)");
            }
            holder.addComponent(BoundingBox.getComponentType(), boundingBox2);

            // NOTE: Collision components removed - was causing world loading issues
            // addCollisionComponents(holder, def);

            // Add RespondToHit to make entity respond to damage
            try {
                holder.addComponent(RespondToHit.getComponentType(), RespondToHit.INSTANCE);
                logger.info("Added RespondToHit for damage response");
            } catch (Exception e) {
                logger.warning("Could not add RespondToHit: " + e.getMessage());
            }

            // Ensure EntityStatMap exists and set initial health on the Holder
            // This MUST be done on the Holder BEFORE adding entity (can't access store during processing)
            try {
                EntityStatMap statMap = holder.ensureAndGetComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();

                    // Set max health using modifier (default is 100, we want 20, so add -80)
                    // Using ADDITIVE modifier to reduce max from 100 to our desired value
                    float maxHealthReduction = DEFAULT_VEHICLE_HEALTH - 100.0f;  // -80 for 20 max health
                    StaticModifier maxHealthModifier = new StaticModifier(
                        ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE,
                        maxHealthReduction
                    );
                    statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);

                    // Set current health to max
                    statMap.setStatValue(healthIndex, DEFAULT_VEHICLE_HEALTH);
                    logger.info("Set health to " + DEFAULT_VEHICLE_HEALTH + "/" + DEFAULT_VEHICLE_HEALTH + " on Holder for vehicle");
                } else {
                    logger.warning("Could not get EntityStatMap from holder");
                }
            } catch (Exception e) {
                logger.warning("Could not initialize health on Holder: " + e.getMessage());
            }

            // Ensure UIComponentList exists for damage numbers and health bar display
            try {
                holder.ensureComponent(UIComponentList.getComponentType());
                logger.info("Ensured UIComponentList for damage indicators");
            } catch (Exception e) {
                logger.warning("Could not ensure UIComponentList: " + e.getMessage());
            }

            // Ensure UUIDComponent exists
            holder.ensureComponent(UUIDComponent.getComponentType());

            // Add VehicleDataComponent - persisted by Hytale with the entity
            // (def already defined above for collision box)
            if (VehicleDataComponent.getComponentType() != null) {
                holder.addComponent(VehicleDataComponent.getComponentType(),
                    new VehicleDataComponent(def.id, def.dropItemId, def.dropQuantity));
                logger.info("Added VehicleDataComponent for persistence");
            }

            // Add NetworkId for client visibility (REQUIRED for entity to be seen!)
            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            // Add Interactable component for F key interaction (mount/dismount)
            try {
                holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
                logger.info("Added Interactable component for F key support");
            } catch (Exception e) {
                logger.warning("Could not add Interactable component: " + e.getMessage());
            }

            // NOTE: MountedByComponent is added dynamically when mounting (not at spawn)
            // Adding it at spawn breaks entity persistence/serialization

            // Spawn entity
            Ref<EntityStore> entityRef = store.addEntity(
                holder,
                AddReason.SPAWN
            );

            // Set up block collision checker using World from EntityStore
            try {
                EntityStore entityStore = store.getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    vehicle.setCollisionChecker(createCollisionChecker(world));
                    logger.info("Set up block collision for vehicle: " + vehicle.getDefinition().id);
                }
            } catch (Exception e2) {
                logger.warning("Could not set up block collision: " + e2.getMessage());
            }

            logger.info("Spawned Hytale entity for vehicle: " + vehicle.getDefinition().id);

            // Log available APIs on first spawn
            logAvailableMethods();

            return entityRef;

        } catch (Exception e) {
            logger.severe("Failed to spawn Hytale entity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sync vehicle physics to the Hytale entity.
     * Called each tick to update the entity's position.
     *
     * @param vehicle The vehicle with updated physics
     * @param entityRef The Hytale entity reference
     * @param world The world
     */
    public void syncToEntity(BaseVehicle vehicle, Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            TransformComponent transform = store.getComponent(
                entityRef,
                TransformComponent.getComponentType()
            );

            if (transform != null) {
                float[] pos = vehicle.getPositionArray();
                float yOffset = vehicle.getDefinition().modelYOffset;
                transform.setPosition(new Vector3d(pos[0], pos[1] + yOffset, pos[2]));
                transform.setRotation(new Vector3f(0, vehicle.getRotationYaw(), 0));
            }
        } catch (Exception e) {
            logger.severe("Failed to sync vehicle to entity: " + e.getMessage());
        }
    }

    /**
     * Load a model for the vehicle from Hytale's asset system.
     * Falls back to DEBUG model if specified model not found.
     * Applies modelScale from the definition.
     * Uses the model's own bounding box and DetailBoxes for collision.
     *
     * @param definition The vehicle definition
     * @return The loaded Model (never null - uses DEBUG as fallback)
     */
    private Model loadModel(VehicleDefinition definition) {
        String modelId = definition.modelId;
        float scale = definition.modelScale > 0 ? definition.modelScale : 1.0f;

        // Try to load specified model
        if (modelId != null && !modelId.isEmpty()) {
            // Parse model ID components (may include namespace like "hyvehicles:...")
            String baseName = modelId.contains(":") ? modelId.substring(modelId.indexOf(":") + 1) : modelId;
            String modPrefix = modelId.contains(":") ? modelId.substring(0, modelId.indexOf(":")) : "hyvehicles";

            // Try different model ID formats that Hytale might use
            String[] modelIdVariants = {
                modelId,                                         // As specified
                baseName,                                        // Without mod prefix
                modPrefix + ":" + baseName,                      // With mod prefix
                "Models/Vehicles/" + baseName.replace("Models/Vehicles/", ""),  // Full path
                modPrefix + ":Models/Vehicles/" + baseName.replace("Models/Vehicles/", ""), // Full path with prefix
                "Vehicles/" + baseName.replace("Models/Vehicles/", "").replace("Vehicles/", ""), // Vehicles folder
                baseName.replace("_Model", ""),                  // Without _Model suffix
                "hytale:" + baseName                             // Try hytale namespace
            };

            for (String variant : modelIdVariants) {
                try {
                    ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(variant);
                    if (modelAsset != null) {
                        logger.info("Found model asset: " + variant + " for vehicle: " + definition.id);
                        // Use model's own bounding box and DetailBoxes - NO override
                        Model model = Model.createScaledModel(modelAsset, scale);
                        logger.info("  Model bounding box: " + model.getBoundingBox());
                        logger.info("  Model DetailBoxes: " + (model.getDetailBoxes() != null ? model.getDetailBoxes().size() + " groups" : "none"));
                        return model;
                    }
                } catch (Exception e) {
                    logger.info("Tried variant '" + variant + "': " + e.getMessage());
                }
            }

            logger.warning("Model not found: " + modelId + " - using DEBUG model");

            // Log available model assets for debugging
            logAvailableModelAssets();
        }

        // Fallback to DEBUG model (always exists)
        logger.info("Using DEBUG placeholder model for: " + definition.id);
        return Model.createScaledModel(ModelAsset.DEBUG, scale);
    }

    private boolean modelAssetsLogged = false;

    /**
     * Log available model assets for debugging (once per session).
     */
    private void logAvailableModelAssets() {
        if (modelAssetsLogged) return;
        modelAssetsLogged = true;

        logger.info("=== Searching for ModelAssets containing 'boat' or 'vehicle' ===");
        try {
            // Try direct lookups for common boat/vehicle patterns
            String[] searchIds = {
                "boat", "Boat", "wood_boat", "Wood_Boat", "WoodBoat",
                "vehicle", "Vehicle", "vehicle_boat", "Vehicle_Boat",
                "Vehicle_wood_boat", "vehicle_wood_boat",
                "Models/Vehicles/Boat", "Vehicles/Boat",
                "NPC/Boat", "Entity/Boat"
            };

            for (String searchId : searchIds) {
                try {
                    ModelAsset asset = ModelAsset.getAssetMap().getAsset(searchId);
                    if (asset != null) {
                        logger.info("  FOUND: '" + searchId + "' -> " + asset.getId());
                    }
                } catch (Exception ignored) {}
            }

            // Log info about the asset map
            logger.info("ModelAsset.getAssetMap() class: " + ModelAsset.getAssetMap().getClass().getName());

            // Try to get the map contents via reflection
            try {
                var assetMap = ModelAsset.getAssetMap();
                for (java.lang.reflect.Method m : assetMap.getClass().getMethods()) {
                    String name = m.getName().toLowerCase();
                    if (name.contains("key") || name.contains("value") || name.contains("entry") ||
                        name.contains("size") || name.contains("all")) {
                        logger.info("  AssetMap method: " + m.getName() + "() -> " + m.getReturnType().getSimpleName());
                    }
                }
            } catch (Exception e) {
                logger.info("Could not inspect AssetMap methods: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.warning("Could not search ModelAssets: " + e.getMessage());
        }
    }

    /**
     * Add collision components to make the vehicle solid.
     * HitboxCollision component is REQUIRED for player-entity collision!
     * DetailBoxes define the shape, but HitboxCollision enables the collision system.
     *
     * @param holder The entity holder to add components to
     * @param definition The vehicle definition for collision size
     */
    private void addCollisionComponents(Holder<EntityStore> holder, VehicleDefinition definition) {
        // Add PropComponent for basic physics interaction
        try {
            holder.addComponent(PropComponent.getComponentType(), new PropComponent());
            logger.info("Added PropComponent for physics behavior");
        } catch (Exception e) {
            logger.warning("Could not add PropComponent: " + e.getMessage());
        }

        // Add HitboxCollision component - REQUIRED for player collision with entity!
        // This enables the client-side collision system with the entity.
        // DetailBoxes define the shape, but HitboxCollision activates it.
        try {
            HitboxCollisionConfig hitboxConfig = findOrCreateHitboxConfig();
            if (hitboxConfig != null) {
                holder.addComponent(HitboxCollision.getComponentType(), new HitboxCollision(hitboxConfig));
                logger.info("Added HitboxCollision with config: " + hitboxConfig.getId() +
                           ", type: " + hitboxConfig.getCollisionType());
            } else {
                logger.warning("Could not find HitboxCollisionConfig - vehicle won't have player collision!");
            }
        } catch (Exception e) {
            logger.warning("Could not add HitboxCollision: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find or create a HitboxCollisionConfig for vehicle collision.
     * Tries to find an existing "Hard" collision config from the asset system.
     *
     * @return A HitboxCollisionConfig, or null if none available
     */
    private HitboxCollisionConfig findOrCreateHitboxConfig() {
        try {
            // Try to get an existing config from the asset map
            // First try our custom config, then common Hytale configs
            String[] configIds = { "HytaleVehicles_Hard", "Hard", "hard", "Default", "default", "Player", "player" };

            for (String id : configIds) {
                try {
                    HitboxCollisionConfig config = HitboxCollisionConfig.getAssetMap().getAsset(id);
                    if (config != null) {
                        logger.info("Found existing HitboxCollisionConfig: " + id);
                        return config;
                    }
                } catch (Exception e) {
                    // Config not found with this ID, try next
                }
            }

            // Try to get by index (0 is usually the default)
            try {
                HitboxCollisionConfig config = HitboxCollisionConfig.getAssetMap().getAsset(0);
                if (config != null) {
                    logger.info("Using HitboxCollisionConfig at index 0: " + config.getId());
                    return config;
                }
            } catch (Exception e) {
                logger.warning("No HitboxCollisionConfig at index 0: " + e.getMessage());
            }

            // Log all available configs for debugging
            logger.info("=== Available HitboxCollisionConfigs ===");
            try {
                int count = 0;
                for (int i = 0; i < 100 && count < 10; i++) {
                    try {
                        HitboxCollisionConfig cfg = HitboxCollisionConfig.getAssetMap().getAsset(i);
                        if (cfg != null) {
                            logger.info("  [" + i + "] " + cfg.getId() + " - " + cfg.getCollisionType());
                            count++;
                            // Return the first valid one we find
                            if (count == 1) {
                                return cfg;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logger.warning("Could not enumerate HitboxCollisionConfigs: " + e.getMessage());
            }

            logger.warning("No HitboxCollisionConfig found in asset system");
            return null;

        } catch (Exception e) {
            logger.warning("Error finding HitboxCollisionConfig: " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove the Hytale entity when vehicle is destroyed.
     *
     * @param entityRef The entity to remove
     * @param world The world
     */
    public void removeEntity(Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.removeEntity(entityRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            logger.info("Removed Hytale entity");
        } catch (Exception e) {
            logger.severe("Failed to remove entity: " + e.getMessage());
        }
    }

    /**
     * Handle vehicle destruction - spawn particles and drop items.
     *
     * @param world The world
     * @param position The position of the destroyed vehicle
     * @param definition The vehicle definition (for drop item info)
     * @param commandBuffer Optional CommandBuffer for deferred operations (required during store processing)
     */
    public void onVehicleDestroyed(World world, Vector3d position, VehicleDefinition definition, CommandBuffer<?> commandBuffer) {
        logger.info("=== onVehicleDestroyed CALLED ===");
        logger.info("  World: " + (world != null ? "valid" : "NULL"));
        logger.info("  Position: " + (position != null ? position.toString() : "NULL"));
        logger.info("  Definition: " + (definition != null ? definition.id : "NULL"));
        logger.info("  CommandBuffer: " + (commandBuffer != null ? "available" : "NULL"));

        if (world == null || position == null) {
            logger.warning("Cannot handle destruction - world or position is null");
            return;
        }

        // Spawn destruction particles
        logger.info("Attempting to spawn destruction particles...");
        spawnDestructionParticles(world, position);

        // Drop items if configured
        if (definition != null && definition.dropItemId != null && !definition.dropItemId.isEmpty()) {
            logger.info("Attempting to drop item: " + definition.dropItemId + " x" + definition.dropQuantity);
            dropItem(world, position, definition.dropItemId, definition.dropQuantity, commandBuffer);
        } else {
            logger.info("No item drop configured for this vehicle");
        }

        // Log available SpawnUtil methods (one-time debug)
        logAvailableMethods();
    }

    private boolean methodsLogged = false;

    private void logAvailableMethods() {
        if (methodsLogged) return;
        methodsLogged = true;

        try {
            logger.info("=== Available SpawnUtil methods ===");
            for (java.lang.reflect.Method m : SpawnUtil.class.getMethods()) {
                logger.info("  SpawnUtil." + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
            }

            logger.info("=== Available ParticleUtil methods ===");
            for (java.lang.reflect.Method m : ParticleUtil.class.getMethods()) {
                logger.info("  ParticleUtil." + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
            }

            logger.info("=== Available UIComponentList methods ===");
            for (java.lang.reflect.Method m : UIComponentList.class.getMethods()) {
                logger.info("  UIComponentList." + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
            }

            logger.info("=== UIComponentList fields ===");
            for (java.lang.reflect.Field f : UIComponentList.class.getDeclaredFields()) {
                logger.info("  UIComponentList." + f.getName() + " : " + f.getType().getSimpleName());
            }

            // Log classes in the entityui package
            logger.info("=== Classes in same package as UIComponentList ===");
            Package pkg = UIComponentList.class.getPackage();
            if (pkg != null) {
                logger.info("  Package: " + pkg.getName());
            }

            // Try to find entity name related components
            String[] potentialNameComponents = {
                "com.hypixel.hytale.server.core.modules.entity.component.EntityName",
                "com.hypixel.hytale.server.core.modules.entity.component.NameComponent",
                "com.hypixel.hytale.server.core.modules.entity.component.DisplayName",
                "com.hypixel.hytale.server.core.modules.entity.component.EntityIdentity",
                "com.hypixel.hytale.server.core.modules.entity.component.Nameplate",
                "com.hypixel.hytale.server.core.modules.entityui.EntityNameplate",
                "com.hypixel.hytale.server.core.modules.entityui.NameDisplay",
                "com.hypixel.hytale.server.core.modules.entityui.EntityLabel"
            };

            logger.info("=== Searching for name-related components ===");
            for (String className : potentialNameComponents) {
                try {
                    Class<?> cls = Class.forName(className);
                    logger.info("  FOUND: " + className);
                    // Log its methods
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        if (!m.getDeclaringClass().equals(Object.class)) {
                            logger.info("    " + m.getName() + "(" + getParamTypes(m) + ")");
                        }
                    }
                } catch (ClassNotFoundException cnf) {
                    // Class doesn't exist, that's fine
                }
            }

            // Log EntityStatMap methods more thoroughly
            logger.info("=== EntityStatMap methods (for name support) ===");
            for (java.lang.reflect.Method m : EntityStatMap.class.getMethods()) {
                if (!m.getDeclaringClass().equals(Object.class)) {
                    logger.info("  EntityStatMap." + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
                }
            }

            // Search for item drop related classes
            String[] potentialItemDropClasses = {
                "com.hypixel.hytale.server.core.item.ItemStack",
                "com.hypixel.hytale.server.core.item.Item",
                "com.hypixel.hytale.server.core.modules.item.ItemStack",
                "com.hypixel.hytale.server.core.modules.item.Item",
                "com.hypixel.hytale.server.core.modules.item.ItemDropUtil",
                "com.hypixel.hytale.server.core.modules.item.ItemSpawner",
                "com.hypixel.hytale.server.core.universe.world.ItemDropper",
                "com.hypixel.hytale.server.core.asset.type.item.config.Item",
                "com.hypixel.hytale.server.core.asset.type.item.config.ItemAsset"
            };

            logger.info("=== Searching for item drop classes ===");
            for (String className : potentialItemDropClasses) {
                try {
                    Class<?> cls = Class.forName(className);
                    logger.info("  FOUND: " + className);
                    // Log static methods that might help spawn items
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (name.contains("spawn") || name.contains("drop") || name.contains("create")) {
                            logger.info("    " + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
                        }
                    }
                } catch (ClassNotFoundException cnf) {
                    // Class doesn't exist
                }
            }

            // Log World methods for item drops
            logger.info("=== World methods (for item drops) ===");
            for (java.lang.reflect.Method m : World.class.getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("item") || name.contains("drop") || name.contains("spawn") || name.contains("entity")) {
                    logger.info("  World." + m.getName() + "(" + getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
                }
            }
        } catch (Exception e) {
            logger.warning("Could not log methods: " + e.getMessage());
        }
    }

    private String getParamTypes(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : m.getParameterTypes()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Spawn destruction particles at the given position.
     * Store implements ComponentAccessor, so we can use it directly.
     */
    @SuppressWarnings("unchecked")
    private void spawnDestructionParticles(World world, Vector3d position) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            // Store implements ComponentAccessor<EntityStore>, so we can cast it directly
            com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor =
                (com.hypixel.hytale.component.ComponentAccessor<EntityStore>) store;

            logger.info("Got ComponentAccessor for particles: " + accessor.getClass().getName());

            // Try different particle effect names that might work for destruction
            // Based on common naming conventions in game engines
            String[] particleNames = {
                "wood_break",
                "Wood_Break",
                "WoodBreak",
                "block_break",
                "Block_Break",
                "BlockBreak",
                "explosion_small",
                "Explosion_Small",
                "ExplosionSmall",
                "debris",
                "Debris",
                "dust",
                "Dust",
                "smoke",
                "Smoke"
            };

            boolean spawned = false;
            for (String particleName : particleNames) {
                try {
                    ParticleUtil.spawnParticleEffect(particleName, position, accessor);
                    logger.info("Spawned destruction particle: " + particleName + " at " + position);
                    spawned = true;
                    break;
                } catch (Exception e) {
                    // Log which particle failed and why
                    logger.info("Particle '" + particleName + "' failed: " + e.getMessage());
                }
            }

            if (!spawned) {
                logger.warning("Could not find a valid particle effect for destruction - tried all common names");
            }
        } catch (Exception e) {
            logger.warning("Could not spawn particles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Drop an item at the specified position when a vehicle is destroyed.
     * Uses ItemComponent.generateItemDrop() to create the item entity directly.
     *
     * @param world The world to spawn the item in
     * @param position The position to drop the item at
     * @param itemId The item ID to drop (e.g., "HytaleVehicles_SimpleBoat")
     * @param quantity The number of items to drop
     * @param commandBuffer Optional CommandBuffer for deferred entity creation (required during store processing)
     */
    @SuppressWarnings("unchecked")
    public void dropItem(World world, Vector3d position, String itemId, int quantity, CommandBuffer<?> commandBuffer) {
        if (world == null || itemId == null || itemId.isEmpty()) {
            logger.warning("dropItem: invalid parameters - world=" + (world != null) + ", itemId=" + itemId);
            return;
        }

        logger.info("=== DROPPING ITEM ===");
        logger.info("  Item ID: " + itemId);
        logger.info("  Quantity: " + quantity);
        logger.info("  Position: " + position);
        logger.info("  CommandBuffer: " + (commandBuffer != null ? "available" : "null"));

        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            // Store implements ComponentAccessor<EntityStore>
            com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor =
                (com.hypixel.hytale.component.ComponentAccessor<EntityStore>) store;

            // Create ItemStack with the item ID and quantity
            ItemStack itemStack = new ItemStack(itemId, quantity);
            logger.info("Created ItemStack - valid: " + itemStack.isValid() + ", empty: " + itemStack.isEmpty());

            if (!itemStack.isValid()) {
                logger.warning("Created ItemStack is not valid - item ID may not exist: " + itemId);
                // Try some alternative item IDs
                String[] alternativeIds = {
                    "hytale:" + itemId,
                    "core:" + itemId,
                    itemId.toLowerCase(),
                    itemId.toUpperCase()
                };
                for (String altId : alternativeIds) {
                    logger.info("Trying alternative ID: " + altId);
                    ItemStack altStack = new ItemStack(altId, quantity);
                    logger.info("  valid: " + altStack.isValid() + ", empty: " + altStack.isEmpty());
                    if (altStack.isValid()) {
                        logger.info("Found valid alternative item ID: " + altId);
                        itemStack = altStack;
                        break;
                    }
                }
            }

            // Use ItemComponent.generateItemDrop() directly to create the item entity
            // This doesn't require an owner entity reference
            // Parameters: accessor, itemStack, position, rotation, velocityX, velocityY, velocityZ
            Vector3f rotation = new Vector3f(0, 0, 0);
            float velocityY = 3.25f; // Pop up a bit like normal item drops

            Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(
                accessor,
                itemStack,
                position,
                rotation,
                0.0f,       // velocityX
                velocityY,  // velocityY - makes it pop up
                0.0f        // velocityZ
            );

            if (itemHolder != null) {
                // Spawn the item entity - use CommandBuffer if available (during store processing)
                // Otherwise use store.addEntity directly (outside of processing)
                Ref<EntityStore> droppedItem;
                if (commandBuffer != null) {
                    logger.info("Using CommandBuffer for deferred item spawn");
                    CommandBuffer<EntityStore> typedBuffer = (CommandBuffer<EntityStore>) commandBuffer;
                    droppedItem = typedBuffer.addEntity(itemHolder, AddReason.SPAWN);
                } else {
                    logger.info("Using store.addEntity directly for item spawn");
                    droppedItem = store.addEntity(itemHolder, AddReason.SPAWN);
                }

                if (droppedItem != null && droppedItem.isValid()) {
                    logger.info("Successfully dropped item entity: " + droppedItem);
                } else {
                    logger.warning("addEntity returned null or invalid ref");
                }
            } else {
                logger.warning("ItemComponent.generateItemDrop returned null - item may be invalid");
                logger.warning("ItemStack details - itemId: " + itemStack.getItemId() +
                    ", quantity: " + itemStack.getQuantity() +
                    ", valid: " + itemStack.isValid());
            }

        } catch (Exception e) {
            logger.severe("Failed to drop item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a collision checker that uses the World's block data.
     *
     * @param world The world to check blocks in
     * @return A CollisionChecker that checks for solid blocks
     */
    public CollisionChecker createCollisionChecker(World world) {
        return (x, y, z, width, height) -> {
            try {
                // Check corners of the bounding box
                float halfWidth = width / 2.0f;

                // Check multiple points in the bounding box
                int[][] checkPoints = {
                    {(int) Math.floor(x - halfWidth), (int) Math.floor(y), (int) Math.floor(z - halfWidth)},
                    {(int) Math.floor(x + halfWidth), (int) Math.floor(y), (int) Math.floor(z - halfWidth)},
                    {(int) Math.floor(x - halfWidth), (int) Math.floor(y), (int) Math.floor(z + halfWidth)},
                    {(int) Math.floor(x + halfWidth), (int) Math.floor(y), (int) Math.floor(z + halfWidth)},
                    {(int) Math.floor(x), (int) Math.floor(y + height), (int) Math.floor(z)}, // Top center
                };

                for (int[] point : checkPoints) {
                    if (isBlockSolid(world, point[0], point[1], point[2])) {
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                // If collision check fails, assume no collision
                return false;
            }
        };
    }

    /**
     * Default health value for vehicles.
     */
    private static final float DEFAULT_VEHICLE_HEALTH = 20.0f;

    /**
     * Initialize health for a vehicle entity using direct store access.
     * Used for Store-based spawn where we can access components immediately.
     *
     * @param entityRef The entity reference
     * @param store The entity store
     * @param definition The vehicle definition
     */
    private void initializeHealth(Ref<EntityStore> entityRef, Store<EntityStore> store, VehicleDefinition definition) {
        try {
            // Get the EntityStatMap component (ensured to exist from holder setup)
            EntityStatMap statMap = store.ensureAndGetComponent(entityRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                logger.warning("Could not get EntityStatMap for health initialization");
                return;
            }

            // Get health stat index from DefaultEntityStatTypes
            int healthIndex = DefaultEntityStatTypes.getHealth();

            // Set max health using modifier (default is 100, we want 20, so add -80)
            float maxHealthReduction = DEFAULT_VEHICLE_HEALTH - 100.0f;
            StaticModifier maxHealthModifier = new StaticModifier(
                ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,
                maxHealthReduction
            );
            statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);

            // Set current health
            statMap.setStatValue(healthIndex, DEFAULT_VEHICLE_HEALTH);
            logger.info("Initialized health to " + DEFAULT_VEHICLE_HEALTH + "/" + DEFAULT_VEHICLE_HEALTH + " for vehicle: " + definition.id);
        } catch (Exception e) {
            logger.warning("Could not initialize health: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize health for a vehicle entity using CommandBuffer for deferred execution.
     * Used during interactions when the store is processing.
     *
     * @param entityRef The entity reference
     * @param commandBuffer The command buffer for deferred operations
     * @param definition The vehicle definition
     */
    @SuppressWarnings("unchecked")
    private void initializeHealthDeferred(Ref<EntityStore> entityRef, CommandBuffer<?> commandBuffer, VehicleDefinition definition) {
        try {
            Store<EntityStore> store = (Store<EntityStore>) commandBuffer.getStore();

            // Get the EntityStatMap component (ensured to exist from holder setup)
            EntityStatMap statMap = store.ensureAndGetComponent(entityRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                logger.warning("Could not get EntityStatMap for deferred health initialization");
                return;
            }

            // Get health stat index from DefaultEntityStatTypes (returns int directly)
            int healthIndex = DefaultEntityStatTypes.getHealth();

            // Set max health using modifier (default is 100, we want 20, so add -80)
            float maxHealthReduction = DEFAULT_VEHICLE_HEALTH - 100.0f;
            StaticModifier maxHealthModifier = new StaticModifier(
                ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,
                maxHealthReduction
            );
            statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);

            // Set current health
            statMap.setStatValue(healthIndex, DEFAULT_VEHICLE_HEALTH);
            logger.info("Initialized health (deferred) to " + DEFAULT_VEHICLE_HEALTH + "/" + DEFAULT_VEHICLE_HEALTH + " for vehicle: " + definition.id);
        } catch (Exception e) {
            // If deferred init fails, we'll need to initialize later in the tick loop
            logger.warning("Could not initialize health (deferred): " + e.getMessage());
        }
    }

    /**
     * Check if a block at the given coordinates is solid.
     */
    private boolean isBlockSolid(World world, int x, int y, int z) {
        try {
            // Get the chunk for this position
            long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkKey);

            if (accessor == null) {
                // Chunk not loaded - assume no collision
                return false;
            }

            // Get block type at position
            BlockType blockType = accessor.getBlockType(x, y, z);
            if (blockType == null) {
                return false;
            }

            // Check if block material is solid
            BlockMaterial material = blockType.getMaterial();
            return material == BlockMaterial.Solid;
        } catch (Exception e) {
            return false;
        }
    }
}
