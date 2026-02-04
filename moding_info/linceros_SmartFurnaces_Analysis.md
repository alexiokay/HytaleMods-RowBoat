# SmartFurnaces - Deep Analysis

**Mod Name:** SmartFurnaces
**Version:** 1.2.1
**Author:** Linceros
**Architecture:** Java Plugin (12KB - minimal)

## Overview

SmartFurnaces automatically transfers items from processing bench outputs to adjacent chests. This tiny but powerful mod demonstrates the **EntityTickingSystem** pattern - one of the most important APIs for creating automated systems in Hytale.

---

## Why This Mod Is Important

This 12KB mod demonstrates:
1. **EntityTickingSystem** - Periodic processing of game entities
2. **Reflection** for internal API access
3. **Configuration system** with JSON persistence
4. **Chunk-level component queries**

---

## Complete Source Code (Decompiled)

### SmartBenchesPlugin.java

```java
public class SmartBenchesPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static SmartBenchesPlugin instance;
    private static Config<SmartBenchesConfig> configRef;

    // Load config on construction
    private final Config<SmartBenchesConfig> config =
        this.withConfig("SmartBenches", SmartBenchesConfig.CODEC);

    public SmartBenchesPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static SmartBenchesPlugin getInstance() { return instance; }
    public static SmartBenchesConfig getConfig() {
        return configRef != null ? configRef.get() : new SmartBenchesConfig();
    }

    protected void setup() {
        LOGGER.at(Level.INFO).log("[SmartBenches] Setting up...");
        this.config.save();
        configRef = this.config;

        // Register command
        this.getCommandRegistry().registerCommand(new SmartBenchesCommand());

        // Get built-in ProcessingBenchState component type
        ComponentType processingBenchType = BlockStateModule.get()
            .getComponentType(ProcessingBenchState.class);

        if (processingBenchType != null) {
            // Register our ticking system for processing benches
            this.getChunkStoreRegistry().registerSystem(
                new SmartBenchSystem(processingBenchType)
            );
            LOGGER.at(Level.INFO).log("[SmartBenches] System registered successfully!");
        } else {
            LOGGER.at(Level.WARNING).log(
                "[SmartBenches] Could not find ProcessingBenchState component type!"
            );
        }
    }

    public void saveConfig() {
        if (configRef != null) configRef.save();
    }

    protected void start() {
        LOGGER.at(Level.INFO).log("[SmartBenches] Plugin started!");
    }

    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[SmartBenches] Plugin disabled.");
    }
}
```

### SmartBenchSystem.java (The Core!)

```java
public class SmartBenchSystem extends EntityTickingSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ComponentType<ChunkStore, ProcessingBenchState> processingBenchComponentType;

    // Items to never put in furnace input (processed outputs)
    private static final Set<String> INPUT_EXCLUDED_ITEMS = new HashSet<String>();

    // Reflection fields to access internal containers
    private static Field outputContainerField;
    private static Field inputContainerField;
    private static Field fuelContainerField;

    // Static initializer - set up reflection
    static {
        INPUT_EXCLUDED_ITEMS.add("Ingredient_Bar_Gold");
        try {
            outputContainerField = ProcessingBenchState.class
                .getDeclaredField("outputContainer");
            outputContainerField.setAccessible(true);

            inputContainerField = ProcessingBenchState.class
                .getDeclaredField("inputContainer");
            inputContainerField.setAccessible(true);

            fuelContainerField = ProcessingBenchState.class
                .getDeclaredField("fuelContainer");
            fuelContainerField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e)
                .log("Failed to initialize reflection fields");
        }
    }

    public SmartBenchSystem(ComponentType<ChunkStore, ProcessingBenchState> componentType) {
        this.processingBenchComponentType = componentType;
    }

    // No dependencies on other systems
    public Set<Dependency<ChunkStore>> getDependencies() {
        return Set.of();
    }

    // Query: Process all entities with ProcessingBenchState component
    public Query<ChunkStore> getQuery() {
        return this.processingBenchComponentType;
    }

    // Don't run in parallel (modifying shared state)
    public boolean isParallel(int index, int count) {
        return false;
    }

    // Called every game tick for each matching entity
    public void tick(float dt,
                     int index,
                     ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> commandBuffer) {

        SmartBenchesConfig config = SmartBenchesPlugin.getConfig();
        if (config == null || !config.isEnabled()) return;

        // Rate limiting: only process every N ticks
        long currentTick = System.currentTimeMillis() / 50L;  // ~20 ticks/second
        if ((currentTick + index) % config.getTickInterval() != 0L) return;

        // Get the furnace component
        ProcessingBenchState furnace = archetypeChunk.getComponent(
            index, this.processingBenchComponentType
        );
        if (furnace == null || outputContainerField == null) return;

        try {
            // Access internal containers via reflection
            ItemContainer outputContainer = (ItemContainer)outputContainerField.get(furnace);
            ItemContainer inputContainer = (ItemContainer)inputContainerField.get(furnace);
            ItemContainer fuelContainer = (ItemContainer)fuelContainerField.get(furnace);

            if (outputContainer == null || inputContainer == null) return;

            boolean canOutput = config.isEnableOutput() && !outputContainer.isEmpty();
            boolean canInput = config.isEnableInput();
            if (!canOutput && !canInput) return;

            WorldChunk chunk = furnace.getChunk();
            if (chunk == null) return;

            Vector3i furnacePos = furnace.getBlockPosition();
            int radius = config.getSearchRadius();

            // Search adjacent blocks for chests
            for (int x = -radius; x <= radius; ++x) {
                for (int y = -radius; y <= radius; ++y) {
                    for (int z = -radius; z <= radius; ++z) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        int targetX = furnacePos.x + x;
                        int targetY = furnacePos.y + y;
                        int targetZ = furnacePos.z + z;

                        BlockState nearbyState = chunk.getState(targetX, targetY, targetZ);
                        if (!(nearbyState instanceof ItemContainerState)) continue;

                        ItemContainerState chest = (ItemContainerState)nearbyState;
                        ItemContainer chestContainer = chest.getItemContainer();
                        if (chestContainer == null) continue;

                        // Output: furnace -> chest
                        if (canOutput && !outputContainer.isEmpty()) {
                            this.transferAll(outputContainer, chestContainer);
                        }

                        // Input: chest -> furnace
                        if (canInput && !chestContainer.isEmpty()) {
                            this.transferToFurnace(chestContainer, inputContainer, fuelContainer);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail - don't spam logs
        }
    }

    // Transfer all items from source to target
    private void transferAll(ItemContainer source, ItemContainer target) {
        short capacity = source.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            if (source.getItemStack(slot) != null) {
                source.moveItemStackFromSlot(slot, target);
            }
        }
    }

    // Auto-feed furnace from chest
    private void transferToFurnace(ItemContainer chest,
                                   ItemContainer furnaceInput,
                                   ItemContainer furnaceFuel) {
        short capacity = chest.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = chest.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            // Try fuel slot first
            if (furnaceFuel != null) {
                MoveTransaction fuelTrans = chest.moveItemStackFromSlot(slot, furnaceFuel);
                if (fuelTrans.succeeded()) {
                    ItemStack remaining = chest.getItemStack(slot);
                    if (remaining == null || remaining.isEmpty()) continue;
                }
            }

            // Then input slot (exclude processed items)
            ItemStack remainingItem = chest.getItemStack(slot);
            if (remainingItem == null || remainingItem.isEmpty()) continue;
            if (INPUT_EXCLUDED_ITEMS.contains(remainingItem.getItemId())) continue;

            chest.moveItemStackFromSlot(slot, furnaceInput);
        }
    }
}
```

---

## EntityTickingSystem Deep Dive

### Class Hierarchy

```
ISystem (interface)
└── EntityTickingSystem<S extends StoreType> (abstract)
    └── SmartBenchSystem (our implementation)
```

### Required Methods

| Method | Purpose |
|--------|---------|
| `getQuery()` | Defines which entities to process |
| `getDependencies()` | Declare dependencies on other systems |
| `isParallel(index, count)` | Can ticks run concurrently? |
| `tick(dt, index, chunk, store, buffer)` | Called for each matching entity |

### Tick Method Parameters

```java
public void tick(
    float dt,                              // Delta time since last tick
    int index,                             // Entity index within archetype chunk
    ArchetypeChunk<ChunkStore> chunk,      // Chunk containing entity data
    Store<ChunkStore> store,               // Full store reference
    CommandBuffer<ChunkStore> buffer       // For deferred commands
) {
    // Get component for this entity
    MyComponent comp = chunk.getComponent(index, myComponentType);
}
```

### Query System

The query defines which entities your system processes:

```java
// Process all entities with a specific component
public Query<ChunkStore> getQuery() {
    return this.myComponentType;  // ComponentType implements Query
}

// Or use complex queries:
public Query<ChunkStore> getQuery() {
    return Query.all(componentTypeA, componentTypeB);  // Require both
}
```

---

## Configuration System

### Using Config API

```java
// In plugin class
private final Config<SmartBenchesConfig> config =
    this.withConfig("SmartBenches", SmartBenchesConfig.CODEC);

// Config class with CODEC
public class SmartBenchesConfig {
    public static final BuilderCodec<SmartBenchesConfig> CODEC = ...;

    private boolean enabled = true;
    private boolean enableOutput = true;
    private boolean enableInput = true;
    private int tickInterval = 20;
    private int searchRadius = 1;

    // Getters...
}
```

Creates `SmartBenches.json` in plugin config folder.

---

## Reflection Pattern

When the official API doesn't expose needed fields:

```java
// Set up reflection in static block
private static Field outputContainerField;

static {
    try {
        outputContainerField = ProcessingBenchState.class
            .getDeclaredField("outputContainer");
        outputContainerField.setAccessible(true);  // Bypass private
    } catch (Exception e) {
        LOGGER.at(Level.SEVERE).withCause(e).log("Reflection failed");
    }
}

// Use in code
ItemContainer output = (ItemContainer)outputContainerField.get(furnaceState);
```

**Warning:** Reflection can break with API updates!

---

## Rate Limiting Pattern

Don't process every tick - use modulo:

```java
long currentTick = System.currentTimeMillis() / 50L;  // ~20 TPS
if ((currentTick + index) % tickInterval != 0L) {
    return;  // Skip this tick
}
```

The `+ index` ensures entities don't all process on same tick (load balancing).

---

## Item Transfer Patterns

### Move Item Between Containers

```java
// Move from slot to target container
MoveTransaction tx = sourceContainer.moveItemStackFromSlot(slot, targetContainer);
if (tx.succeeded()) {
    // Item was moved (possibly partially)
}
```

### Get All Slots

```java
short capacity = container.getCapacity();
for (short slot = 0; slot < capacity; slot++) {
    ItemStack item = container.getItemStack(slot);
    if (item != null && !item.isEmpty()) {
        // Process item
    }
}
```

---

## Getting Built-in Component Types

```java
// Access Hytale's built-in component types
ComponentType processingBenchType = BlockStateModule.get()
    .getComponentType(ProcessingBenchState.class);

// Other block states
ComponentType chestType = BlockStateModule.get()
    .getComponentType(ItemContainerState.class);
```

---

## Application to HytaleVehicles

This pattern is **perfect** for vehicle systems:

```java
public class VehiclePhysicsSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, VehicleComponent> vehicleType;

    public Query<EntityStore> getQuery() {
        return this.vehicleType;
    }

    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        VehicleComponent vehicle = chunk.getComponent(index, vehicleType);

        // Apply physics
        vehicle.velocity.y -= 9.8f * dt;  // Gravity
        vehicle.position.add(vehicle.velocity.scale(dt));

        // Update transform component
        TransformComponent transform = chunk.getComponent(index, transformType);
        transform.setPosition(vehicle.position);
    }
}
```

### Benefits for Vehicles

| Benefit | Description |
|---------|-------------|
| Automatic entity iteration | No manual vehicle list management |
| Parallel processing | Multiple vehicles can update concurrently |
| Rate limiting | Control update frequency |
| ECS integration | Works with transform, velocity components |

---

## File Structure

```
SmartFurnaces-1.2.1.jar (12KB)
├── manifest.json
└── com/linceros/smartbenches/
    ├── SmartBenchesPlugin.class
    ├── SmartBenchSystem.class
    ├── SmartBenchesConfig.class
    └── SmartBenchesCommand.class
```

---

## Summary

SmartFurnaces demonstrates:
- **EntityTickingSystem** for periodic entity processing
- **Query-based** entity selection
- **Reflection** for internal API access
- **Config system** with JSON persistence
- **Rate limiting** for performance
- **Container operations** (move, transfer)

This is a template for any automated game system!
