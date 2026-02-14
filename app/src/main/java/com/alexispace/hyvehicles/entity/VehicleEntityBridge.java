package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.DetailBox;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class VehicleEntityBridge {
    private final VehicleLogger logger;
    private boolean modelAssetsLogged = false;
    private boolean methodsLogged = false;
    private static final float DEFAULT_VEHICLE_HEALTH = 20.0f;

    public VehicleEntityBridge(VehicleLogger logger) {
        this.logger = logger;
    }

    public Ref<EntityStore> spawnEntity(BaseVehicle vehicle, World world) {
        if (world == null) {
            this.logger.severe("Cannot spawn entity: world is null");
            return null;
        }
        return this.spawnEntityWithStore(vehicle, world.getEntityStore().getStore());
    }

    public Ref<EntityStore> spawnEntityWithCommandBuffer(BaseVehicle vehicle, CommandBuffer commandBuffer) {
        try {
            Store store = commandBuffer.getStore();
            Holder holder = store.getRegistry().newHolder();
            float[] pos = vehicle.getPositionArray();
            float yOffset = vehicle.getDefinition().modelYOffset;
            this.logger.info("=== SPAWN DEBUG === Vehicle: " + vehicle.getDefinition().id + ", modelYOffset from JSON: " + yOffset + ", original Y: " + pos[1] + ", adjusted Y: " + (pos[1] + yOffset));
            TransformComponent transform = new TransformComponent(new Vector3d((double)pos[0], (double)(pos[1] + yOffset), (double)pos[2]), new Vector3f(0.0f, vehicle.getRotationYaw(), 0.0f));
            holder.addComponent(TransformComponent.getComponentType(), transform);
            VehicleDefinition def = vehicle.getDefinition();
            Model model = this.loadModel(def);
            this.logger.info("=== MODEL DEBUG === modelId: " + def.modelId + ", collision box from model: " + String.valueOf(model.getBoundingBox()));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.addComponent(ActiveAnimationComponent.getComponentType(), new ActiveAnimationComponent());
            this.logger.info("Added ActiveAnimationComponent for vehicle animations");
            BoundingBox boundingBox = new BoundingBox(model.getBoundingBox());
            if (model.getDetailBoxes() != null && !model.getDetailBoxes().isEmpty()) {
                boundingBox.setDetailBoxes(model.getDetailBoxes());
                this.logger.info("Set DetailBoxes on BoundingBox: " + model.getDetailBoxes().size() + " groups");
                for (Map.Entry entry : model.getDetailBoxes().entrySet()) {
                    this.logger.info("  DetailBox group '" + (String)entry.getKey() + "': " + ((DetailBox[])entry.getValue()).length + " boxes");
                }
            } else {
                this.logger.info("No DetailBoxes found in model");
            }
            holder.addComponent(BoundingBox.getComponentType(), boundingBox);
            try {
                holder.addComponent(RespondToHit.getComponentType(), RespondToHit.INSTANCE);
                this.logger.info("Added RespondToHit for damage response");
            }
            catch (Exception e) {
                this.logger.warning("Could not add RespondToHit: " + e.getMessage());
            }
            try {
                EntityStatMap statMap = (EntityStatMap)holder.ensureAndGetComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();
                    float maxHealthReduction = -80.0f;
                    StaticModifier maxHealthModifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, maxHealthReduction);
                    statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);
                    statMap.setStatValue(healthIndex, 20.0f);
                    this.logger.info("Set health to 20.0/20.0 on Holder for vehicle");
                } else {
                    this.logger.warning("Could not get EntityStatMap from holder");
                }
            }
            catch (Exception e) {
                this.logger.warning("Could not initialize health on Holder: " + e.getMessage());
            }
            try {
                holder.ensureComponent(UIComponentList.getComponentType());
                this.logger.info("Ensured UIComponentList for damage indicators");
            }
            catch (Exception e) {
                this.logger.warning("Could not ensure UIComponentList: " + e.getMessage());
            }
            holder.ensureComponent(UUIDComponent.getComponentType());
            if (VehicleDataComponent.getComponentType() != null) {
                VehicleDataComponent vehicleDataComp = new VehicleDataComponent(def.id, def.dropItemId, def.dropQuantity);
                vehicleDataComp.setPosition(pos[0], pos[1], pos[2], vehicle.getRotationYaw());
                holder.addComponent(VehicleDataComponent.getComponentType(), vehicleDataComp);
                this.logger.info("Added VehicleDataComponent with initial position for persistence");
            }
            int assignedNetworkId = ((EntityStore)store.getExternalData()).takeNextNetworkId();
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(assignedNetworkId));
            vehicle.setNetworkId(assignedNetworkId);
            this.logger.info("Assigned NetworkId=" + assignedNetworkId + " to vehicle at spawn time");
            try {
                holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
                this.logger.info("Added Interactable component for F key support");
            }
            catch (Exception e) {
                this.logger.warning("Could not add Interactable component: " + e.getMessage());
            }
            Ref entityRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
            try {
                EntityStore entityStore = (EntityStore)store.getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    vehicle.setCollisionChecker(this.createCollisionChecker(world));
                    this.logger.info("Set up block collision for vehicle: " + vehicle.getDefinition().id);
                }
            }
            catch (Exception e) {
                this.logger.warning("Could not set up block collision: " + e.getMessage());
            }
            this.logger.info("Spawned Hytale entity for vehicle: " + vehicle.getDefinition().id);
            this.logAvailableMethods();
            return entityRef;
        }
        catch (Exception e) {
            this.logger.severe("Failed to spawn Hytale entity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Ref<EntityStore> spawnEntityWithStore(BaseVehicle vehicle, Store<EntityStore> store) {
        try {
            Holder holder = store.getRegistry().newHolder();
            float[] pos = vehicle.getPositionArray();
            float yOffset = vehicle.getDefinition().modelYOffset;
            TransformComponent transform = new TransformComponent(new Vector3d((double)pos[0], (double)(pos[1] + yOffset), (double)pos[2]), new Vector3f(0.0f, vehicle.getRotationYaw(), 0.0f));
            holder.addComponent(TransformComponent.getComponentType(), transform);
            VehicleDefinition def = vehicle.getDefinition();
            Model model = this.loadModel(def);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.addComponent(ActiveAnimationComponent.getComponentType(), new ActiveAnimationComponent());
            this.logger.info("Added ActiveAnimationComponent for vehicle animations");
            BoundingBox boundingBox2 = new BoundingBox(model.getBoundingBox());
            if (model.getDetailBoxes() != null && !model.getDetailBoxes().isEmpty()) {
                boundingBox2.setDetailBoxes(model.getDetailBoxes());
                this.logger.info("Set DetailBoxes on BoundingBox: " + model.getDetailBoxes().size() + " groups");
            } else {
                this.logger.info("No DetailBoxes found in model (Store spawn)");
            }
            holder.addComponent(BoundingBox.getComponentType(), boundingBox2);
            try {
                holder.addComponent(RespondToHit.getComponentType(), RespondToHit.INSTANCE);
                this.logger.info("Added RespondToHit for damage response");
            }
            catch (Exception e) {
                this.logger.warning("Could not add RespondToHit: " + e.getMessage());
            }
            try {
                EntityStatMap statMap = (EntityStatMap)holder.ensureAndGetComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();
                    float maxHealthReduction = -80.0f;
                    StaticModifier maxHealthModifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, maxHealthReduction);
                    statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);
                    statMap.setStatValue(healthIndex, 20.0f);
                    this.logger.info("Set health to 20.0/20.0 on Holder for vehicle");
                } else {
                    this.logger.warning("Could not get EntityStatMap from holder");
                }
            }
            catch (Exception e) {
                this.logger.warning("Could not initialize health on Holder: " + e.getMessage());
            }
            try {
                holder.ensureComponent(UIComponentList.getComponentType());
                this.logger.info("Ensured UIComponentList for damage indicators");
            }
            catch (Exception e) {
                this.logger.warning("Could not ensure UIComponentList: " + e.getMessage());
            }
            holder.ensureComponent(UUIDComponent.getComponentType());
            if (VehicleDataComponent.getComponentType() != null) {
                VehicleDataComponent vehicleDataComp2 = new VehicleDataComponent(def.id, def.dropItemId, def.dropQuantity);
                vehicleDataComp2.setPosition(pos[0], pos[1], pos[2], vehicle.getRotationYaw());
                holder.addComponent(VehicleDataComponent.getComponentType(), vehicleDataComp2);
                this.logger.info("Added VehicleDataComponent with initial position for persistence");
            }
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)store.getExternalData()).takeNextNetworkId()));
            try {
                holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
                this.logger.info("Added Interactable component for F key support");
            }
            catch (Exception e) {
                this.logger.warning("Could not add Interactable component: " + e.getMessage());
            }
            Ref entityRef = store.addEntity(holder, AddReason.SPAWN);
            try {
                EntityStore entityStore = (EntityStore)store.getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    vehicle.setCollisionChecker(this.createCollisionChecker(world));
                    this.logger.info("Set up block collision for vehicle: " + vehicle.getDefinition().id);
                }
            }
            catch (Exception e2) {
                this.logger.warning("Could not set up block collision: " + e2.getMessage());
            }
            this.logger.info("Spawned Hytale entity for vehicle: " + vehicle.getDefinition().id);
            this.logAvailableMethods();
            return entityRef;
        }
        catch (Exception e) {
            this.logger.severe("Failed to spawn Hytale entity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void syncToEntity(BaseVehicle vehicle, Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        try {
            Store store = world.getEntityStore().getStore();
            TransformComponent transform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform != null) {
                float[] pos = vehicle.getPositionArray();
                float yOffset = vehicle.getDefinition().modelYOffset;
                transform.setPosition(new Vector3d((double)pos[0], (double)(pos[1] + yOffset), (double)pos[2]));
                transform.setRotation(new Vector3f(0.0f, vehicle.getRotationYaw(), 0.0f));
            }
        }
        catch (Exception e) {
            this.logger.severe("Failed to sync vehicle to entity: " + e.getMessage());
        }
    }

    private Model loadModel(VehicleDefinition definition) {
        float scale;
        String modelId = definition.modelId;
        float f = scale = definition.modelScale > 0.0f ? definition.modelScale : 1.0f;
        if (modelId != null && !modelId.isEmpty()) {
            String[] modelIdVariants;
            String baseName = modelId.contains(":") ? modelId.substring(modelId.indexOf(":") + 1) : modelId;
            String modPrefix = modelId.contains(":") ? modelId.substring(0, modelId.indexOf(":")) : "hyvehicles";
            for (String variant : modelIdVariants = new String[]{modelId, baseName, modPrefix + ":" + baseName, "Models/Vehicles/" + baseName.replace("Models/Vehicles/", ""), modPrefix + ":Models/Vehicles/" + baseName.replace("Models/Vehicles/", ""), "Vehicles/" + baseName.replace("Models/Vehicles/", "").replace("Vehicles/", ""), baseName.replace("_Model", ""), "hytale:" + baseName}) {
                try {
                    ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(variant);
                    if (modelAsset == null) continue;
                    this.logger.info("Found model asset: " + variant + " for vehicle: " + definition.id);
                    Model model = Model.createScaledModel(modelAsset, scale);
                    this.logger.info("  Model bounding box: " + String.valueOf(model.getBoundingBox()));
                    this.logger.info("  Model DetailBoxes: " + (model.getDetailBoxes() != null ? model.getDetailBoxes().size() + " groups" : "none"));
                    return model;
                }
                catch (Exception e) {
                    this.logger.info("Tried variant '" + variant + "': " + e.getMessage());
                }
            }
            this.logger.warning("Model not found: " + modelId + " - using DEBUG model");
            this.logAvailableModelAssets();
        }
        this.logger.info("Using DEBUG placeholder model for: " + definition.id);
        return Model.createScaledModel(ModelAsset.DEBUG, scale);
    }

    private void logAvailableModelAssets() {
        if (this.modelAssetsLogged) {
            return;
        }
        this.modelAssetsLogged = true;
        this.logger.info("=== Searching for ModelAssets containing 'boat' or 'vehicle' ===");
        try {
            String[] searchIds;
            for (String searchId : searchIds = new String[]{"boat", "Boat", "wood_boat", "Wood_Boat", "WoodBoat", "vehicle", "Vehicle", "vehicle_boat", "Vehicle_Boat", "Vehicle_wood_boat", "vehicle_wood_boat", "Models/Vehicles/Boat", "Vehicles/Boat", "NPC/Boat", "Entity/Boat"}) {
                try {
                    ModelAsset asset = ModelAsset.getAssetMap().getAsset(searchId);
                    if (asset == null) continue;
                    this.logger.info("  FOUND: '" + searchId + "' -> " + asset.getId());
                }
                catch (Exception asset) {
                }
            }
            this.logger.info("ModelAsset.getAssetMap() class: " + ModelAsset.getAssetMap().getClass().getName());
            try {
                DefaultAssetMap assetMap = ModelAsset.getAssetMap();
                for (Method m : assetMap.getClass().getMethods()) {
                    String name = m.getName().toLowerCase();
                    if (!name.contains("key") && !name.contains("value") && !name.contains("entry") && !name.contains("size") && !name.contains("all")) continue;
                    this.logger.info("  AssetMap method: " + m.getName() + "() -> " + m.getReturnType().getSimpleName());
                }
            }
            catch (Exception e) {
                this.logger.info("Could not inspect AssetMap methods: " + e.getMessage());
            }
        }
        catch (Exception e) {
            this.logger.warning("Could not search ModelAssets: " + e.getMessage());
        }
    }

    public void removeEntity(Ref<EntityStore> entityRef, World world) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        try {
            Store store = world.getEntityStore().getStore();
            store.removeEntity(entityRef, RemoveReason.REMOVE);
            this.logger.info("Removed Hytale entity");
        }
        catch (Exception e) {
            this.logger.severe("Failed to remove entity: " + e.getMessage());
        }
    }

    public void onVehicleDestroyed(World world, Vector3d position, VehicleDefinition definition, CommandBuffer<?> commandBuffer) {
        this.logger.info("=== onVehicleDestroyed CALLED ===");
        this.logger.info("  World: " + (world != null ? "valid" : "NULL"));
        this.logger.info("  Position: " + (position != null ? position.toString() : "NULL"));
        this.logger.info("  Definition: " + (definition != null ? definition.id : "NULL"));
        this.logger.info("  CommandBuffer: " + (commandBuffer != null ? "available" : "NULL"));
        if (world == null || position == null) {
            this.logger.warning("Cannot handle destruction - world or position is null");
            return;
        }
        this.logger.info("Attempting to spawn destruction particles...");
        this.spawnDestructionParticles(world, position);
        if (definition != null && definition.dropItemId != null && !definition.dropItemId.isEmpty()) {
            this.logger.info("Attempting to drop item: " + definition.dropItemId + " x" + definition.dropQuantity);
            this.dropItem(world, position, definition.dropItemId, definition.dropQuantity, commandBuffer);
        } else {
            this.logger.info("No item drop configured for this vehicle");
        }
        this.logAvailableMethods();
    }

    private void logAvailableMethods() {
        if (this.methodsLogged) {
            return;
        }
        this.methodsLogged = true;
        try {
            this.logger.info("=== Available SpawnUtil methods ===");
            for (Method method : SpawnUtil.class.getMethods()) {
                this.logger.info("  SpawnUtil." + method.getName() + "(" + this.getParamTypes(method) + ") -> " + method.getReturnType().getSimpleName());
            }
            this.logger.info("=== Available ParticleUtil methods ===");
            for (Method method : ParticleUtil.class.getMethods()) {
                this.logger.info("  ParticleUtil." + method.getName() + "(" + this.getParamTypes(method) + ") -> " + method.getReturnType().getSimpleName());
            }
            this.logger.info("=== Available UIComponentList methods ===");
            for (Method method : UIComponentList.class.getMethods()) {
                this.logger.info("  UIComponentList." + method.getName() + "(" + this.getParamTypes(method) + ") -> " + method.getReturnType().getSimpleName());
            }
            this.logger.info("=== UIComponentList fields ===");
            for (Field field : UIComponentList.class.getDeclaredFields()) {
                this.logger.info("  UIComponentList." + field.getName() + " : " + field.getType().getSimpleName());
            }
            this.logger.info("=== Classes in same package as UIComponentList ===");
            Package pkg = UIComponentList.class.getPackage();
            if (pkg != null) {
                this.logger.info("  Package: " + pkg.getName());
            }
            String[] potentialNameComponents = new String[]{"com.hypixel.hytale.server.core.modules.entity.component.EntityName", "com.hypixel.hytale.server.core.modules.entity.component.NameComponent", "com.hypixel.hytale.server.core.modules.entity.component.DisplayName", "com.hypixel.hytale.server.core.modules.entity.component.EntityIdentity", "com.hypixel.hytale.server.core.modules.entity.component.Nameplate", "com.hypixel.hytale.server.core.modules.entityui.EntityNameplate", "com.hypixel.hytale.server.core.modules.entityui.NameDisplay", "com.hypixel.hytale.server.core.modules.entityui.EntityLabel"};
            this.logger.info("=== Searching for name-related components ===");
            for (String className : potentialNameComponents) {
                try {
                    Class<?> cls = Class.forName(className);
                    this.logger.info("  FOUND: " + className);
                    for (Method m : cls.getMethods()) {
                        if (m.getDeclaringClass().equals(Object.class)) continue;
                        this.logger.info("    " + m.getName() + "(" + this.getParamTypes(m) + ")");
                    }
                }
                catch (ClassNotFoundException cls) {
                }
            }
            this.logger.info("=== EntityStatMap methods (for name support) ===");
            for (Method m : EntityStatMap.class.getMethods()) {
                if (m.getDeclaringClass().equals(Object.class)) continue;
                this.logger.info("  EntityStatMap." + m.getName() + "(" + this.getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
            }
            String[] potentialItemDropClasses = new String[]{"com.hypixel.hytale.server.core.item.ItemStack", "com.hypixel.hytale.server.core.item.Item", "com.hypixel.hytale.server.core.modules.item.ItemStack", "com.hypixel.hytale.server.core.modules.item.Item", "com.hypixel.hytale.server.core.modules.item.ItemDropUtil", "com.hypixel.hytale.server.core.modules.item.ItemSpawner", "com.hypixel.hytale.server.core.universe.world.ItemDropper", "com.hypixel.hytale.server.core.asset.type.item.config.Item", "com.hypixel.hytale.server.core.asset.type.item.config.ItemAsset"};
            this.logger.info("=== Searching for item drop classes ===");
            for (String className : potentialItemDropClasses) {
                try {
                    Class<?> cls = Class.forName(className);
                    this.logger.info("  FOUND: " + className);
                    for (Method m : cls.getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (!name.contains("spawn") && !name.contains("drop") && !name.contains("create")) continue;
                        this.logger.info("    " + m.getName() + "(" + this.getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
                    }
                }
                catch (ClassNotFoundException cls) {
                }
            }
            this.logger.info("=== World methods (for item drops) ===");
            for (Method m : World.class.getMethods()) {
                String name = m.getName().toLowerCase();
                if (!name.contains("item") && !name.contains("drop") && !name.contains("spawn") && !name.contains("entity")) continue;
                this.logger.info("  World." + m.getName() + "(" + this.getParamTypes(m) + ") -> " + m.getReturnType().getSimpleName());
            }
        }
        catch (Exception e) {
            this.logger.warning("Could not log methods: " + e.getMessage());
        }
    }

    private String getParamTypes(Method m) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : m.getParameterTypes()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getSimpleName());
        }
        return sb.toString();
    }

    private void spawnDestructionParticles(World world, Vector3d position) {
        try {
            Store store;
            ComponentAccessor accessor = store = world.getEntityStore().getStore();
            this.logger.info("Got ComponentAccessor for particles: " + accessor.getClass().getName());
            String[] particleNames = new String[]{"wood_break", "Wood_Break", "WoodBreak", "block_break", "Block_Break", "BlockBreak", "explosion_small", "Explosion_Small", "ExplosionSmall", "debris", "Debris", "dust", "Dust", "smoke", "Smoke"};
            boolean spawned = false;
            for (String particleName : particleNames) {
                try {
                    ParticleUtil.spawnParticleEffect(particleName, position, accessor);
                    this.logger.info("Spawned destruction particle: " + particleName + " at " + String.valueOf(position));
                    spawned = true;
                    break;
                }
                catch (Exception e) {
                    this.logger.info("Particle '" + particleName + "' failed: " + e.getMessage());
                }
            }
            if (!spawned) {
                this.logger.warning("Could not find a valid particle effect for destruction - tried all common names");
            }
        }
        catch (Exception e) {
            this.logger.warning("Could not spawn particles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void dropItem(World world, Vector3d position, String itemId, int quantity, CommandBuffer<?> commandBuffer) {
        if (world == null || itemId == null || itemId.isEmpty()) {
            this.logger.warning("dropItem: invalid parameters - world=" + (world != null) + ", itemId=" + itemId);
            return;
        }
        this.logger.info("=== DROPPING ITEM ===");
        this.logger.info("  Item ID: " + itemId);
        this.logger.info("  Quantity: " + quantity);
        this.logger.info("  Position: " + String.valueOf(position));
        this.logger.info("  CommandBuffer: " + (commandBuffer != null ? "available" : "null"));
        try {
            float velocityY;
            Vector3f rotation;
            Holder itemHolder;
            Store store;
            ComponentAccessor accessor = store = world.getEntityStore().getStore();
            ItemStack itemStack = new ItemStack(itemId, quantity);
            this.logger.info("Created ItemStack - valid: " + itemStack.isValid() + ", empty: " + itemStack.isEmpty());
            if (!itemStack.isValid()) {
                String[] alternativeIds;
                this.logger.warning("Created ItemStack is not valid - item ID may not exist: " + itemId);
                for (String altId : alternativeIds = new String[]{"hytale:" + itemId, "core:" + itemId, itemId.toLowerCase(), itemId.toUpperCase()}) {
                    this.logger.info("Trying alternative ID: " + altId);
                    ItemStack altStack = new ItemStack(altId, quantity);
                    this.logger.info("  valid: " + altStack.isValid() + ", empty: " + altStack.isEmpty());
                    if (!altStack.isValid()) continue;
                    this.logger.info("Found valid alternative item ID: " + altId);
                    itemStack = altStack;
                    break;
                }
            }
            if ((itemHolder = ItemComponent.generateItemDrop(accessor, itemStack, position, rotation = new Vector3f(0.0f, 0.0f, 0.0f), 0.0f, velocityY = 3.25f, 0.0f)) != null) {
                Ref droppedItem;
                if (commandBuffer != null) {
                    this.logger.info("Using CommandBuffer for deferred item spawn");
                    CommandBuffer<?> typedBuffer = commandBuffer;
                    droppedItem = typedBuffer.addEntity(itemHolder, AddReason.SPAWN);
                } else {
                    this.logger.info("Using store.addEntity directly for item spawn");
                    droppedItem = store.addEntity(itemHolder, AddReason.SPAWN);
                }
                if (droppedItem != null && droppedItem.isValid()) {
                    this.logger.info("Successfully dropped item entity: " + String.valueOf(droppedItem));
                } else {
                    this.logger.warning("addEntity returned null or invalid ref");
                }
            } else {
                this.logger.warning("ItemComponent.generateItemDrop returned null - item may be invalid");
                this.logger.warning("ItemStack details - itemId: " + itemStack.getItemId() + ", quantity: " + itemStack.getQuantity() + ", valid: " + itemStack.isValid());
            }
        }
        catch (Exception e) {
            this.logger.severe("Failed to drop item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CollisionChecker createCollisionChecker(World world) {
        return (x, y, z, width, height) -> {
            try {
                int[][] checkPoints;
                float halfWidth = width / 2.0f;
                for (int[] point : checkPoints = new int[][]{{(int)Math.floor(x - halfWidth), (int)Math.floor(y), (int)Math.floor(z - halfWidth)}, {(int)Math.floor(x + halfWidth), (int)Math.floor(y), (int)Math.floor(z - halfWidth)}, {(int)Math.floor(x - halfWidth), (int)Math.floor(y), (int)Math.floor(z + halfWidth)}, {(int)Math.floor(x + halfWidth), (int)Math.floor(y), (int)Math.floor(z + halfWidth)}, {(int)Math.floor(x), (int)Math.floor(y + height), (int)Math.floor(z)}}) {
                    if (!this.isBlockSolid(world, point[0], point[1], point[2])) continue;
                    return true;
                }
                return false;
            }
            catch (Exception e) {
                return false;
            }
        };
    }

    private void initializeHealth(Ref<EntityStore> entityRef, Store<EntityStore> store, VehicleDefinition definition) {
        try {
            EntityStatMap statMap = (EntityStatMap)store.ensureAndGetComponent(entityRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                this.logger.warning("Could not get EntityStatMap for health initialization");
                return;
            }
            int healthIndex = DefaultEntityStatTypes.getHealth();
            float maxHealthReduction = -80.0f;
            StaticModifier maxHealthModifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, maxHealthReduction);
            statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);
            statMap.setStatValue(healthIndex, 20.0f);
            this.logger.info("Initialized health to 20.0/20.0 for vehicle: " + definition.id);
        }
        catch (Exception e) {
            this.logger.warning("Could not initialize health: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeHealthDeferred(Ref<EntityStore> entityRef, CommandBuffer<?> commandBuffer, VehicleDefinition definition) {
        try {
            Store store = commandBuffer.getStore();
            EntityStatMap statMap = (EntityStatMap)store.ensureAndGetComponent(entityRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                this.logger.warning("Could not get EntityStatMap for deferred health initialization");
                return;
            }
            int healthIndex = DefaultEntityStatTypes.getHealth();
            float maxHealthReduction = -80.0f;
            StaticModifier maxHealthModifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, maxHealthReduction);
            statMap.putModifier(healthIndex, "hyvehicles_max_health", maxHealthModifier);
            statMap.setStatValue(healthIndex, 20.0f);
            this.logger.info("Initialized health (deferred) to 20.0/20.0 for vehicle: " + definition.id);
        }
        catch (Exception e) {
            this.logger.warning("Could not initialize health (deferred): " + e.getMessage());
        }
    }

    private boolean isBlockSolid(World world, int x, int y, int z) {
        try {
            long chunkKey = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk accessor = world.getChunkIfLoaded(chunkKey);
            if (accessor == null) {
                return false;
            }
            BlockType blockType = accessor.getBlockType(x, y, z);
            if (blockType == null) {
                return false;
            }
            BlockMaterial material = blockType.getMaterial();
            return material == BlockMaterial.Solid;
        }
        catch (Exception e) {
            return false;
        }
    }
}
