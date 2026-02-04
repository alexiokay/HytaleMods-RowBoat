# Pixelcomet ConveyorBelt Mod Analysis

**Mod:** `pixelcomet-mod-conveyorBelt.jar`
**Authors:** Zane444, Arkise (Pixelcomet Games)
**Version:** 2.0.0
**Architecture:** Hybrid Java + JavaScript (GraalVM)

A comprehensive analysis of the ConveyorBelt mod, documenting Hytale modding techniques, APIs, and patterns.

---

## Table of Contents

1. [Plugin Architecture Options](#plugin-architecture-options)
2. [ECS System & Tick Updates](#ecs-system--tick-updates)
3. [Entity Management](#entity-management)
4. [Block & Chunk Access](#block--chunk-access)
5. [Container & Inventory System](#container--inventory-system)
6. [Item Entity Manipulation](#item-entity-manipulation)
7. [Physics Manipulation](#physics-manipulation)
8. [Block States & Connected Blocks](#block-states--connected-blocks)
9. [Processing Benches](#processing-benches)
10. [JavaScript Modding with GraalVM](#javascript-modding-with-graalvm)
11. [Useful Patterns & Techniques](#useful-patterns--techniques)
12. [Common Imports Reference](#common-imports-reference)

---

## Plugin Architecture Options

### Option 1: Pure Java Plugin
Traditional approach using `JavaPlugin` base class.

```java
public class MyPlugin extends JavaPlugin {
    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        // Register commands, codecs, interactions
    }

    @Override
    public void start() {
        // Plugin fully loaded
    }
}
```

### Option 2: Hybrid Java + JavaScript (GraalVM)
Use Java for bootstrap/registration, JavaScript for game logic. More flexible for rapid iteration.

```java
// Java: Register tick system, expose bindings
Context jsContext = Context.newBuilder("js")
    .allowAllAccess(true)
    .allowHostAccess(HostAccess.ALL)
    .build();

jsContext.getBindings("js").putMember("Logger", getLogger());
jsContext.getBindings("js").putMember("Adapter", this);
jsContext.eval(Source.newBuilder("js", new InputStreamReader(mainJs), "main.js").build());
```

```javascript
// JavaScript: All game logic
var Universe = Java.type('com.hypixel.hytale.server.core.universe.Universe');
function onServerTick(world) {
    // Called every frame
}
```

---

## ECS System & Tick Updates

### Registering a Custom System

To run code every tick, register an `ISystem` with the EntityStore registry:

```java
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// In plugin start():
EntityStore.REGISTRY.registerSystem(new ISystem<EntityStore>() {
    @Override
    public void tick(Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        // Your per-tick logic here
    }
});
```

### Chunk Load Listener

```java
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

ChunkStore.REGISTRY.registerSystem(new ISystem<ChunkStore>() {
    // Called when chunks load
});
```

### Block Place Listener

```java
// Register listener for block placement events
// Use to detect when special blocks are placed
```

---

## Entity Management

### Finding Entities Near a Position

Use the spatial resource system to efficiently find nearby entities:

```java
// Get the spatial resource from the store
SpatialResource spatialRes = store.getResource(
    EntityModule.get().getNetworkSendableSpatialResourceType()
);

// Get thread-local list for results
List<Ref<EntityStore>> results = SpatialResource.getThreadLocalReferenceList();
results.clear();

// Collect entities within radius
Vector3d searchCenter = playerTransform.getPosition();
double radius = 24.0;
spatialRes.getSpatialStructure().collect(searchCenter, radius, results);

// Process results
for (Ref<EntityStore> entityRef : results) {
    if (!entityRef.isValid()) continue;

    // Check entity type
    ItemComponent itemComp = store.getComponent(entityRef, ItemComponent.getComponentType());
    if (itemComp != null) {
        // It's an item entity
    }
}
```

### Getting All Players

```java
Universe universe = Universe.get();
Collection<PlayerRef> players = universe.getPlayers();

for (PlayerRef pRef : players) {
    Ref<EntityStore> actualRef = pRef.getReference();
    if (actualRef == null || !actualRef.isValid()) continue;

    Store<EntityStore> store = actualRef.getStore();
    TransformComponent transform = store.getComponent(actualRef, TransformComponent.getComponentType());
}
```

### Spawning Item Entities

```java
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;

// Create item drop from ItemStack
Vector3d spawnPos = new Vector3d(x, y, z);
Vector3f velocity = Vector3f.ZERO;  // No initial velocity
float pickupDelay = 0;
float mergeDelay = 0;
float despawnTime = 0;

Holder<EntityStore> holder = ItemComponent.generateItemDrop(
    store,
    itemStack,
    spawnPos,
    velocity,
    pickupDelay,
    mergeDelay,
    despawnTime
);

// Spawn the entity
Ref<EntityStore> itemRef = store.addEntity(holder, AddReason.SPAWN);
```

### Removing Entities

```java
import com.hypixel.hytale.component.RemoveReason;

if (entityRef.isValid()) {
    store.removeEntity(entityRef, RemoveReason.REMOVE);
}
```

---

## Block & Chunk Access

### Getting a Chunk

```java
import com.hypixel.hytale.math.util.ChunkUtil;

// Get chunk index from world coordinates
long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);

// Get chunk (may be null if not loaded)
WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
```

### Reading Block Data

```java
// Convert world coords to local chunk coords
int localX = worldX & 31;  // 0-31
int localZ = worldZ & 31;  // 0-31

// Get block ID
int blockId = chunk.getBlock(localX, worldY, localZ);

// Get block asset
BlockType.AssetMap assetMap = BlockType.getAssetMap();
BlockTypeAsset asset = assetMap.getAsset(blockId);
String assetId = asset.getId();  // e.g., "ConveyorBelt"

// Get block rotation (0-3 for NESW)
int rotation = chunk.getRotationIndex(localX, worldY, localZ);

// Get block state
BlockState blockState = chunk.getState(localX, worldY, localZ);

// Get block type (includes state info)
BlockType blockType = chunk.getBlockType(localX, worldY, localZ);
```

### Block Rotation to Direction

```java
// Convert rotation index to direction vector
int dx = 0, dz = 0;
switch (rotation) {
    case 0: dz = 1; break;   // North → faces South
    case 1: dx = 1; break;   // East
    case 2: dz = -1; break;  // South → faces North
    case 3: dx = -1; break;  // West
}
```

### Setting Block Data

```java
// Change block state/animation
String stateKey = blockType.getBlockKeyForState("grab");  // State name
int stateKeyId = BlockType.getAssetMap().getIndex(stateKey);
BlockTypeAsset stateAsset = BlockType.getAssetMap().getAsset(stateKey);

chunk.setBlock(localX, worldY, localZ, stateKeyId, stateAsset, rotation, filler, flags);
```

### Filler Blocks (Multi-block Structures)

Large blocks use "filler" blocks to occupy multiple spaces:

```java
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

// Check if this position is a filler
int filler = chunk.getFiller(localX, worldY, localZ);
if (filler != 0) {
    // Get offset to the main block
    int offsetX = FillerBlockUtil.unpackX(filler);
    int offsetY = FillerBlockUtil.unpackY(filler);
    int offsetZ = FillerBlockUtil.unpackZ(filler);

    // Calculate main block position
    int mainX = worldX - offsetX;
    int mainY = worldY - offsetY;
    int mainZ = worldZ - offsetZ;
}
```

---

## Container & Inventory System

### Accessing Block Containers

Chests, furnaces, benches, and other blocks with inventory:

```java
// Get block state
BlockState blockState = chunk.getState(localX, worldY, localZ);

// Check if it has a container
if (blockState != null && blockState instanceof ContainerBlockState) {
    ItemContainer container = ((ContainerBlockState) blockState).getItemContainer();
}

// JavaScript version:
if (blockState && typeof blockState.getItemContainer === 'function') {
    let container = blockState.getItemContainer();
}
```

### Reading Container Contents

```java
int capacity = container.getCapacity();

for (int slot = 0; slot < capacity; slot++) {
    ItemStack stack = container.getItemStack(slot);
    if (stack != null && !stack.isEmpty()) {
        String itemId = stack.getItemId();
        int quantity = stack.getQuantity();
        int maxStack = stack.getItem().getMaxStack();
    }
}
```

### Detecting Output Slots

Output slots (like furnace output) can't accept items:

```java
// Check if slot is an output slot
ItemStack testStack = existingStack.withQuantity(1);
boolean canAdd = container.canAddItemStackToSlot(slot, testStack, false, true);
if (!canAdd) {
    // This is an output slot!
}
```

### Inserting Items

```java
ItemStack stackToInsert = ...;

for (int slot = 0; slot < container.getCapacity(); slot++) {
    ItemContainerTransaction transaction = container.addItemStackToSlot(slot, stackToInsert);

    if (transaction.succeeded()) {
        ItemStack remainder = transaction.getRemainder();
        if (remainder == null || remainder.isEmpty()) {
            // Fully inserted
            break;
        } else {
            // Partial insert, continue with remainder
            stackToInsert = remainder;
        }
    }
}
```

### Extracting Items

```java
int slotToExtract = 0;
int quantityToExtract = 1;

ItemContainerTransaction transaction = container.removeItemStackFromSlot(slotToExtract, quantityToExtract);

if (transaction.succeeded()) {
    ItemStack extractedItem = transaction.getOutput();
    // Do something with extracted item
}
```

---

## Item Entity Manipulation

### Getting Item Component

```java
ItemComponent itemComp = store.getComponent(entityRef, ItemComponent.getComponentType());
if (itemComp != null) {
    ItemStack stack = itemComp.getItemStack();
    String itemId = stack.getItem().getId();
}
```

### Modifying Item Stack

```java
ItemStack newStack = oldStack.withQuantity(newQuantity);
itemComp.setItemStack(newStack);
```

### Moving Items (Position Updates)

```java
TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
transform.setPosition(new Vector3d(newX, newY, newZ));
```

### Stopping Item Velocity

```java
Velocity vel = store.getComponent(entityRef, Velocity.getComponentType());
if (vel != null) {
    vel.setZero();
}
```

### Adding Velocity

```java
import com.hypixel.hytale.protocol.ChangeVelocityType;

Velocity vel = store.getComponent(entityRef, Velocity.getComponentType());
vel.addInstruction(
    new Vector3d(dx * 15, 0, dz * 15),  // Velocity vector
    null,                                 // Optional modifier
    ChangeVelocityType.fromValue(0)      // Type
);
```

---

## Physics Manipulation

### Using Reflection to Access Private Fields

Sometimes you need to modify private physics properties:

```java
import java.lang.reflect.Field;

// Get private field
Field mergeDelayField = ItemComponent.class.getDeclaredField("mergeDelay");
mergeDelayField.setAccessible(true);

// Set value
mergeDelayField.setFloat(itemComponent, 10.0f);  // Delay item merging

// For physics collision mask
Field collisionMaskField = PhysicsValues.class.getDeclaredField("collisionMask");
collisionMaskField.setAccessible(true);
collisionMaskField.setInt(physicsValues, 0);  // Disable collision

// For mass
Field massField = PhysicsValues.class.getDeclaredField("mass");
massField.setAccessible(true);
massField.setDouble(physicsValues, 0.0);  // Weightless
```

### Common Physics Fields

| Field | Class | Purpose |
|-------|-------|---------|
| `mergeDelay` | ItemComponent | Prevent items from merging |
| `pickupDelay` | ItemComponent | Prevent player pickup |
| `collisionMask` | PhysicsValues | Enable/disable collision |
| `mass` | PhysicsValues | Item weight for physics |

---

## Block States & Connected Blocks

### Defining Block States

Block states allow different visuals/behaviors based on conditions:

```json
{
  "BlockType": {
    "State": {
      "Definitions": {
        "Corner_Left": {
          "CustomModel": "path/to/corner_model.blockymodel",
          "CustomModelAnimation": "path/to/corner_anim.blockyanim"
        },
        "SlopeUp": {
          "CustomModel": "path/to/slope_model.blockymodel",
          "HitboxType": "CustomHitbox"
        }
      }
    }
  }
}
```

### Connected Block Templates

Auto-morph blocks based on neighbors (like rails, fences):

```json
{
  "ConnectedBlockRuleSet": {
    "Type": "CustomTemplate",
    "TemplateShapeAssetId": "MyConnectedBlockTemplate",
    "TemplateShapeBlockPatterns": {
      "Straight": "MyBlock",
      "Corner_Left": "*MyBlock_State_Definitions_Corner_Left",
      "Corner_Right": "*MyBlock_State_Definitions_Corner_Right",
      "TShape": "*MyBlock_State_Definitions_T",
      "Cross": "*MyBlock_State_Definitions_Cross"
    }
  }
}
```

### Support/Supporting Rules

Define how blocks connect:

```json
{
  "Support": {
    "Down": [{"FaceType": "Full"}],
    "North": [{"FaceType": "ConveyorBelt"}],
    "South": [{"FaceType": "ConveyorBelt"}]
  },
  "Supporting": {
    "North": [{"FaceType": "ConveyorBelt"}],
    "South": [{"FaceType": "ConveyorBelt"}]
  }
}
```

---

## Processing Benches

### Creating a Bench Block

Benches are blocks with inventory that can process items:

```json
{
  "BlockType": {
    "Bench": {
      "Type": "Processing",
      "Id": "MyBench",
      "Input": [
        {"FilterValidIngredients": false}
      ],
      "OutputSlotsCount": 1,
      "LocalOpenSoundEventId": "SFX_Metal_Land",
      "LocalCloseSoundEventId": "SFX_Metal_Build"
    },
    "State": {
      "Id": "processingBench"
    },
    "Interactions": {
      "Use": "Open_MyBench"
    }
  }
}
```

### Using Bench as Filter Slot

A bench with 1 input slot can act as a filter:

```java
// Get the bench's container
ItemContainer armContainer = blockState.getItemContainer();
ItemStack filterStack = armContainer.getItemStack(0);

if (filterStack != null && !filterStack.isEmpty()) {
    String filterType = filterStack.getItemId();
    String itemType = itemToProcess.getItemId();

    if (!itemType.equals(filterType)) {
        // Item doesn't match filter, skip
        return;
    }
}
```

---

## JavaScript Modding with GraalVM

### Accessing Java Classes

```javascript
var Universe = Java.type('com.hypixel.hytale.server.core.universe.Universe');
var Vector3d = Java.type('com.hypixel.hytale.math.vector.Vector3d');
var CopyOnWriteArrayList = Java.type('java.util.concurrent.CopyOnWriteArrayList');
```

### Creating Java Objects

```javascript
var position = new Vector3d(x, y, z);
var list = new CopyOnWriteArrayList();
```

### Converting Arrays

```javascript
// JavaScript array to Java int[]
var jsArray = [1, 2, 3, 4];
var javaArray = Java.to(jsArray, "int[]");
```

### Checking Method Existence

```javascript
if (blockState && typeof blockState.getItemContainer === 'function') {
    var container = blockState.getItemContainer();
}
```

### World-Scoped Data

```javascript
// Track data per-world
var activeItems = {};  // worldId -> CopyOnWriteArrayList

function onServerTick(world) {
    var worldId = world.getName();

    if (!activeItems[worldId]) {
        activeItems[worldId] = new CopyOnWriteArrayList();
    }

    // Process items for this world
}
```

---

## Useful Patterns & Techniques

### 1. Periodic Updates (Not Every Tick)

```javascript
var updateCounter = {};

function onServerTick(world) {
    var worldId = world.getName();
    updateCounter[worldId] = (updateCounter[worldId] || 0) + 1;

    // Run every 30 ticks (~1 second at 30 TPS)
    if (updateCounter[worldId] >= 30) {
        updateCounter[worldId] = 0;
        doExpensiveOperation(world);
    }
}
```

### 2. Smooth Position Interpolation

```javascript
var SPEED = 0.05;  // Progress per tick

state.progress += SPEED;

var curX = state.startPos.x + (state.endPos.x - state.startPos.x) * state.progress;
var curY = state.startPos.y + (state.endPos.y - state.startPos.y) * state.progress;
var curZ = state.startPos.z + (state.endPos.z - state.startPos.z) * state.progress;

// Optional: Add arc for jumping motion
if (Math.abs(state.endPos.y - state.startPos.y) > 0.6) {
    curY += Math.sin(state.progress * Math.PI) * 0.65;
}

transform.setPosition(new Vector3d(curX, curY, curZ));
```

### 3. Circular Motion (Robot Arm)

```javascript
var progress = (Date.now() - startTime) / duration;  // 0 to 1
var angle = Math.PI * progress + (Math.PI / 2) + (rotation * (Math.PI / 2));

var x = centerX + Math.cos(angle) * radius;
var z = centerZ + Math.sin(angle) * radius;
```

### 4. Safe Entity Reference Handling

```javascript
// Always check validity before using
if (entityRef && entityRef.isValid()) {
    var transform = store.getComponent(entityRef, TransformComponent.getComponentType());
    if (transform) {
        // Safe to use
    }
}
```

### 5. Chunk-Based Block Scanning

```javascript
// Scan chunk for specific block types
function scanChunkForBlocks(chunk, targetBlockIds) {
    var results = [];
    var minX = chunk.getX() << 5;
    var minZ = chunk.getZ() << 5;

    for (var lx = 0; lx < 32; lx++) {
        for (var lz = 0; lz < 32; lz++) {
            for (var y = 0; y < 256; y++) {
                var blockId = chunk.getBlock(lx, y, lz);
                if (targetBlockIds.includes(blockId)) {
                    results.push({x: minX + lx, y: y, z: minZ + lz});
                }
            }
        }
    }
    return results;
}
```

### 6. Deferred Entity Removal

```javascript
// Batch removals to avoid concurrent modification
var removeQueue = [];

function processItems(store) {
    for (var item of activeItems) {
        if (shouldRemove(item)) {
            removeQueue.push(item.ref);
        }
    }
}

function flushRemoveQueue(store, world) {
    world.execute(function() {
        for (var ref of removeQueue) {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    });
    removeQueue = [];
}
```

---

## Common Imports Reference

### Core ECS
```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.spatial.SpatialResource;
```

### World & Universe
```java
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
```

### Entity Components
```java
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
```

### Blocks
```java
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
```

### Math
```java
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
```

### Items
```java
import com.hypixel.hytale.server.core.item.ItemStack;
import com.hypixel.hytale.server.core.item.container.ItemContainer;
import com.hypixel.hytale.server.core.item.container.ItemContainerTransaction;
```

---

## Resources

- [HytaleDocs](https://hytale-docs.com/docs/modding/plugins/overview) - Plugin overview
- [Britakee GitBook](https://britakee-studios.gitbook.io/hytale-modding-documentation) - Comprehensive guides
- [doc.hytaledev.fr](https://doc.hytaledev.fr/en/) - Plugin documentation
- [HytaleModding GitHub](https://github.com/HytaleModding) - Templates & tools
- [HytaleModding Entity Spawning](https://hytalemodding.dev/en/docs/guides/plugin/spawning-entities)
- [Hytale ECS Theory](https://hytalemodding.dev/en/docs/guides/ecs/hytale-ecs-theory)

---

---

## Animation System (Blocky Format)

Hytale uses `.blockymodel` for 3D models and `.blockyanim` for animations. These are JSON-based formats with node hierarchies.

### Model Format (`.blockymodel`)

Models use a **node tree** with named bones/parts:

```json
{
  "nodes": [
    {
      "id": "1",
      "name": "root",
      "position": {"x": 0, "y": 10.89, "z": -0.89},
      "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
      "shape": {
        "type": "box",
        "offset": {"x": 0, "y": -6.39, "z": 0.89},
        "size": {"x": 16, "y": 9, "z": 16},
        "textureLayout": {
          "back": {"offset": {"x": 112, "y": 16}, "mirror": {"x": false, "y": false}},
          "front": {"offset": {"x": 144, "y": 16}},
          "top": {"offset": {"x": 128, "y": 16}, "mirror": {"x": true, "y": true}}
        }
      },
      "children": [
        {
          "id": "2",
          "name": "arm_joint1",
          "position": {"x": 0, "y": 6.39, "z": -0.89},
          "children": [...]
        }
      ]
    }
  ]
}
```

**Key node properties:**
- `name` - Used to reference in animations (e.g., "arm_joint1", "hand", "clamp_upper")
- `position` - Local offset from parent
- `orientation` - Quaternion rotation (x, y, z, w)
- `shape` - Visual geometry (box, etc.)
- `children` - Child nodes (hierarchical rigging)

### Animation Format (`.blockyanim`)

Animations define keyframe data for each named node:

```json
{
  "formatVersion": 1,
  "duration": 120,
  "holdLastKeyframe": false,
  "nodeAnimations": {
    "root": {
      "position": [],
      "orientation": [
        {"time": 0, "delta": {"x": 0, "y": 0, "z": 0, "w": 1}, "interpolationType": "linear"},
        {"time": 60, "delta": {"x": 0, "y": 1, "z": 0, "w": 0}, "interpolationType": "linear"},
        {"time": 120, "delta": {"x": 0, "y": 0, "z": 0, "w": 1}, "interpolationType": "linear"}
      ],
      "shapeStretch": [],
      "shapeVisible": [],
      "shapeUvOffset": []
    },
    "arm_joint1": {
      "orientation": [
        {"time": 0, "delta": {"x": 0.01463, "y": 0, "z": 0, "w": 0.99989}},
        {"time": 15, "delta": {"x": 0.34499, "y": 0, "z": 0, "w": 0.93861}},
        {"time": 30, "delta": {"x": -0.10897, "y": 0, "z": 0, "w": 0.99404}}
      ]
    },
    "clamp_upper": {
      "orientation": [
        {"time": 0, "delta": {"x": 0, "y": 0, "z": 0, "w": 1}},
        {"time": 13, "delta": {"x": -0.38268, "y": 0, "z": 0, "w": 0.92388}},
        {"time": 18, "delta": {"x": 0.38268, "y": 0, "z": 0, "w": 0.92388}}
      ]
    }
  }
}
```

**Animation properties:**
- `duration` - Total animation length in ticks (30 ticks = 1 second)
- `holdLastKeyframe` - Whether to hold or loop
- `nodeAnimations` - Keyed by node name from model
- `position` - Position keyframes (delta from bind pose)
- `orientation` - Rotation keyframes (quaternion delta)
- `shapeVisible` - Toggle visibility at keyframes
- `shapeUvOffset` - Animate texture coordinates
- `interpolationType` - "linear" or potentially "step"

### Conveyor Arm Animation Breakdown

The robotic arm has this bone hierarchy:
```
root (base rotation - 180° turn)
├── arm_joint1 (lower arm pivot)
│   └── arm_upper (upper arm rotation)
│       └── hand (wrist rotation)
│           ├── clamp_upper (grabber finger 1)
│           └── clamp_lower (grabber finger 2)
```

Animation sequence (120 ticks = 4 seconds):
1. **Ticks 0-30**: Arm extends forward, clamps open
2. **Ticks 30-60**: Root rotates 180°, arm retracts
3. **Ticks 60-90**: Hold position, clamps close (grabbing)
4. **Ticks 90-120**: Root rotates back, arm extends to drop

### Conveyor Belt Animation

Uses many small "belt_N" nodes that all move together:

```json
{
  "duration": 8,
  "holdLastKeyframe": false,
  "nodeAnimations": {
    "belt_1": {
      "position": [
        {"time": 0, "delta": {"x": 0, "y": 0, "z": 0}},
        {"time": 8, "delta": {"x": 0, "y": 0, "z": -4}}
      ]
    }
  }
}
```

All belt segments move -4 units in Z over 8 ticks, creating continuous motion illusion.

### Triggering Animations via Block States

Animations are linked to block states in the item JSON:

```json
{
  "BlockType": {
    "State": {
      "Definitions": {
        "grab": {
          "CustomModelAnimation": "Items/ConveyorArm/grab_loop.blockyanim"
        }
      }
    },
    "CustomModelAnimation": "Items/ConveyorArm/idle.blockyanim",
    "Looping": true
  }
}
```

**Switching animation from code:**
```javascript
// Get the block type and find the state key
let blockType = chunk.getBlockType(x & 31, y, z & 31);
let animKey = blockType.getBlockKeyForState("grab");  // State name → asset key

// Get numeric ID and asset
let animKeyId = BlockType.getAssetMap().getIndex(animKey);
let animAsset = BlockType.getAssetMap().getAsset(animKey);

// Update the block (this changes its visual state/animation)
chunk.setBlock(x & 31, y, z & 31, animKeyId, animAsset, rotation, filler, flags);
```

**Important:** Animations are NOT code-driven frame-by-frame. You define animations in `.blockyanim` files, then switch between them by changing block states. The client handles interpolation automatically.

---

## Why JavaScript? (GraalVM Architecture)

### The Problem with Pure Java

1. **No Hot Reload**: Java mods require recompiling and restarting the game
2. **Verbose Boilerplate**: Java needs lots of setup code for simple operations
3. **Iteration Speed**: Testing changes requires full rebuild cycle

### The GraalVM Solution

The mod uses a **hybrid architecture**:

```
┌─────────────────────────────────────────────────────┐
│                    main.js                          │
│  (Game Logic - easily editable, hot-reloadable*)   │
│  - Item movement                                    │
│  - State machines                                   │
│  - Container interaction                            │
│  - All business logic                               │
└─────────────────────────────────────────────────────┘
                        ↓ calls
┌─────────────────────────────────────────────────────┐
│              hytalejsadapter.java                   │
│  (Bootstrap Layer - compiled once)                  │
│  - Register ECS tick system                         │
│  - Register event listeners                         │
│  - Expose helper methods (scanChunkForBelts)        │
│  - Bridge JS → Java API calls                       │
└─────────────────────────────────────────────────────┘
                        ↓ uses
┌─────────────────────────────────────────────────────┐
│              Hytale Server API                      │
│  (Native game code)                                 │
└─────────────────────────────────────────────────────┘
```

### What Java Does (Minimal)

```java
public class hytalejsadapter extends JavaPlugin {
    // 1. Register tick system that calls JS every frame
    EntityStore.REGISTRY.registerSystem(new ISystem<EntityStore>() {
        @Override
        public void tick(Store<EntityStore> store) {
            // Call JavaScript onServerTick(world)
            jsContext.eval("onServerTick(world)");
        }
    });

    // 2. Register chunk load listener
    ChunkStore.REGISTRY.registerSystem(...);

    // 3. Register block place listener
    // Uses PlaceBlockEvent

    // 4. Provide helper methods callable from JS
    public List<Integer> scanChunkForBelts(BlockChunk chunk, int[] targetIds) {
        // Efficient Java loop scanning all 32x320x32 blocks
        // Returns list of [x,y,z, x,y,z, ...] positions
    }
}
```

### What JavaScript Does (Everything Else)

```javascript
// Access ANY Java class dynamically
var Universe = Java.type('com.hypixel.hytale.server.core.universe.Universe');
var ItemComponent = Java.type('com.hypixel.hytale.server.core.modules.entity.item.ItemComponent');

// Simple state management
var activeItems = {};  // No class definitions needed
var conveyorArms = {};

// Easy iteration
function onServerTick(world) {
    for (let arm of conveyorArms[worldId]) {
        arm.progress += 0.05;
        // ...
    }
}

// Reflection is trivial
var mergeDelayField = ItemComponent.class.getDeclaredField("mergeDelay");
mergeDelayField.setAccessible(true);
```

### Benefits of JavaScript

| Aspect | Java | JavaScript |
|--------|------|------------|
| Edit-test cycle | Rebuild + restart | Edit file + reload* |
| State management | Classes, constructors | Plain objects |
| Reflection | Verbose try/catch | One-liners |
| Dynamic typing | No | Yes (flexible) |
| Callback functions | Anonymous classes | Arrow functions |
| JSON handling | Jackson/Gson | Native |

*Hot reload may require mod support or game restart depending on implementation

### The Adapter Pattern

The Java adapter exposes itself to JavaScript:

```java
// Java side
jsContext.getBindings("js").putMember("Adapter", this);
jsContext.getBindings("js").putMember("Logger", getLogger());
```

```javascript
// JavaScript side - call Java helper methods
let scanResults = Adapter.scanChunkForBelts(chunk, javaIds);
Logger.log("Found " + scanResults.size() + " belts");
```

This gives JavaScript access to optimized Java operations while keeping logic in JS.

---

## State Machine Pattern (No API - Just Variables)

The conveyor arm doesn't use a formal state machine API. It's just **JavaScript variables tracking state**:

```javascript
// Arm state tracking (per arm object)
arm.isMovingItem = undefined;        // undefined = idle, 0 = grabbing
arm.isMovingItemResetTime = 0;       // Timeout for state reset
arm.movingItemRef = null;            // Entity being moved
arm.movingItemTime = 0;              // When to drop item
arm.movingItemTimeStart = 0;         // Animation start time
arm.canDropItem = false;             // Ready to release
arm.animation = '';                  // Current animation name
arm.pulledItemDelay = 0;             // Cooldown after pulling from container

// State transitions via simple conditionals
function onServerTick_conveyorArms(world) {
    for (let arm of conveyorArms[worldId]) {

        // State: IDLE → GRABBING
        if (arm.isMovingItem === undefined) {
            let itemRef = findItemInFront(arm);
            if (itemRef) {
                arm.isMovingItem = 0;
                arm.movingItemRef = itemRef;
                arm.movingItemTime = Date.now() + 2000;  // 2 sec animation
                arm.movingItemTimeStart = Date.now();
            }
        }

        // State: GRABBING (animate)
        if (arm.movingItemTime && arm.movingItemTime > Date.now()) {
            let progress = (Date.now() - arm.movingItemTimeStart) / 2000;
            // Move item along arc...
            if (progress > 0.5) {
                arm.canDropItem = true;
            }
        }

        // State: DROPPING
        if (arm.canDropItem) {
            arm.canDropItem = false;
            // Insert item into container or drop on belt
            arm.movingItemRef = undefined;
        }

        // State: TIMEOUT RESET
        if (arm.isMovingItemResetTime < Date.now()) {
            arm.isMovingItem = undefined;
            arm.animation = '';
        }

        // Update block animation based on state
        let newAnim = arm.isMovingItem !== undefined ? 'grab' : '';
        if (arm.animation !== newAnim) {
            arm.animation = newAnim;
            setBlockState(arm.pos, newAnim);  // Changes visual
        }
    }
}
```

### Why This Works

- **Simple**: No framework overhead, just if/else
- **Debuggable**: `print(JSON.stringify(arm))` shows entire state
- **Flexible**: Easy to add new states or modify transitions
- **Time-based**: Uses `Date.now()` for timeouts, not tick counting

---

## Connected Block Templates (Advanced)

The conveyor belt auto-morphs based on neighbors using a **custom template system**:

### Template Definition

```json
{
  "MaterialName": "ConveyorBelt",
  "ConnectsToOtherMaterials": true,
  "DefaultShape": "Straight",
  "Shapes": {
    "Straight": {
      "FaceTags": {
        "North": ["ConveyorBeltConnection"],
        "South": ["ConveyorBeltConnection"]
      },
      "PatternsToMatchAnyOf": [...]
    },
    "Corner_Left": {
      "FaceTags": {
        "West": ["ConveyorBeltConnection"],
        "South": ["ConveyorBeltConnection"]
      },
      "PatternsToMatchAnyOf": [
        {
          "Type": "Custom",
          "RequireFaceTagsMatchingRoll": true,
          "TransformRulesToOrientation": true,
          "RulesToMatch": [
            {
              "Position": {"X": -1, "Y": 0, "Z": 0},
              "IncludeOrExclude": "Include",
              "FaceTags": {"East": ["ConveyorBeltConnection"]}
            },
            {
              "Position": {"X": 0, "Y": 0, "Z": 1},
              "IncludeOrExclude": "Include",
              "FaceTags": {"North": ["ConveyorBeltConnection"]}
            }
          ]
        }
      ]
    },
    "SlopeUp": {
      "FaceTags": {"North": ["ConveyorBeltConnection"]},
      "PatternsToMatchAnyOf": [
        {
          "Type": "Custom",
          "AllowedPatternTransformations": {"MirrorZ": false},
          "RulesToMatch": [
            {
              "Position": {"X": 0, "Y": 1, "Z": 1},
              "IncludeOrExclude": "Include",
              "FaceTags": {"North": ["ConveyorBeltConnection"]}
            }
          ]
        }
      ]
    }
  }
}
```

### How It Works

1. **FaceTags**: Define connection points (which faces can connect)
2. **PatternsToMatchAnyOf**: Rules for when to use this shape
3. **RulesToMatch**: Check neighbor blocks at relative positions
4. **Include/Exclude**: Must have / must not have these neighbors
5. **TransformRulesToOrientation**: Auto-rotate rules with block rotation

### Block Item Registration

```json
{
  "BlockType": {
    "ConnectedBlockRuleSet": {
      "Type": "CustomTemplate",
      "TemplateShapeAssetId": "ConveyorBeltConnectedBlockTemplate",
      "TemplateShapeBlockPatterns": {
        "Straight": "ConveyorBelt",
        "Corner_Left": "*ConveyorBelt_State_Definitions_Corner_Left",
        "Corner_Right": "*ConveyorBelt_State_Definitions_Corner_Right",
        "SlopeUp": "*ConveyorBelt_State_Definitions_SlopeUp",
        "SlopeDown": "*ConveyorBelt_State_Definitions_SlopeDown",
        "TShape": "*ConveyorBelt_State_Definitions_T",
        "Cross": "*ConveyorBelt_State_Definitions_Cross"
      }
    }
  }
}
```

The `*` prefix references a block state definition, not a separate block type.

---

## More Cool Techniques

### 1. Efficient Chunk Scanning (Java Helper)

The adapter provides an optimized Java method for scanning chunks:

```java
public List<Integer> scanChunkForBelts(BlockChunk chunk, int[] targetIds) {
    List<Integer> results = new ArrayList<>();
    Set<Integer> idSet = new HashSet<>();
    for (int id : targetIds) idSet.add(id);

    // Scan all 32x320x32 blocks
    for (int y = 0; y < 320; y++) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int blockId = chunk.getBlock(x, y, z);
                if (blockId != 0 && idSet.contains(blockId)) {
                    results.add(x);
                    results.add(y);
                    results.add(z);
                }
            }
        }
    }
    return results;  // [x,y,z, x,y,z, ...]
}
```

Called from JavaScript:
```javascript
let javaIds = Java.to([id1, id2, id3], "int[]");
let results = Adapter.scanChunkForBelts(chunk, javaIds);

for (let i = 0; i < results.size(); i += 3) {
    let x = results.get(i);
    let y = results.get(i + 1);
    let z = results.get(i + 2);
    // Found belt at (x, y, z)
}
```

### 2. Processing Bench as Filter Slot

The conveyor arm is defined as a "Processing Bench" just to get an inventory slot:

```json
{
  "BlockType": {
    "Bench": {
      "Type": "Processing",
      "Input": [{"FilterValidIngredients": false}],  // Accept ANY item
      "OutputSlotsCount": 1,
      "Id": "ConveyorArm"
    }
  }
}
```

The code then uses this slot as a **filter**:

```javascript
let armContainer = blockState.getItemContainer();
let filterStack = armContainer.getItemStack(0);

if (filterStack && !filterStack.isEmpty()) {
    // Only grab items matching filter
    if (itemStack.getItemId() !== filterStack.getItemId()) {
        canGrabItem = false;
    }
}
```

### 3. World-Execute for Thread Safety

Some operations need to run on the world thread:

```javascript
world.execute(function() {
    // This runs on the world's execution thread
    store.removeEntity(ref, RemoveReason.REMOVE);
});
```

### 4. CopyOnWriteArrayList for Concurrent Modification

```javascript
var CopyOnWriteArrayList = Java.type('java.util.concurrent.CopyOnWriteArrayList');
var activeItems = new CopyOnWriteArrayList();

// Safe to iterate and modify simultaneously
for (var i = 0; i < activeItems.size(); i++) {
    if (shouldRemove(activeItems.get(i))) {
        activeItems.remove(i);
        i--;  // Adjust index
    }
}
```

### 5. Date.now() for Time-Based Logic

```javascript
arm.movingItemTime = Date.now() + 2000;  // 2 seconds from now

// Later:
if (Date.now() > arm.movingItemTime) {
    // Time elapsed, perform action
}

// Calculate progress (0 to 1):
let progress = (Date.now() - arm.movingItemTimeStart) / 2000;
```

---

## Summary: Key Takeaways

1. **Animations are declarative** - Define in `.blockyanim`, switch via block states
2. **JavaScript for logic, Java for bootstrap** - Best of both worlds
3. **No formal state machine** - Just variables and if/else
4. **Connected blocks are powerful** - Auto-morph based on neighbor rules
5. **Processing bench = inventory slot** - Clever abuse for filter functionality
6. **Reflection is essential** - Many useful fields are private
7. **World.execute() for thread safety** - Some ops need world thread
8. **Time-based, not tick-based** - Use `Date.now()` for animations

---

*Last updated: February 2026*
*Based on analysis of Hytale Early Access modding patterns*
