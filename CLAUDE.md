# HytaleVehicles - Claude Project Memory

This file helps Claude understand the project context without re-exploring each session.

## Quick Commands

```bash
# Build
./gradlew.bat :app:build

# Deploy to game
./gradlew.bat :app:deployToGame

# Clean build
./gradlew.bat clean :app:build

# Run with daemon (faster subsequent builds)
./gradlew.bat :app:build --daemon

# Regenerate API docs (after Hytale updates)
./gradlew.bat generateApiDocs
```

## API Reference

**Community Documentation (up-to-date):**
- [HytaleDocs](https://hytale-docs.com/docs/modding/plugins/overview) - Plugin overview & patterns
- [Britakee GitBook](https://britakee-studios.gitbook.io/hytale-modding-documentation) - Comprehensive guides
- [doc.hytaledev.fr](https://doc.hytaledev.fr/en/) - Plugin documentation
- [HytaleModding GitHub](https://github.com/HytaleModding) - Templates & tools

**Local Reference (auto-generated from JAR):**
- [HYTALE_API.md](HYTALE_API.md) - Class list from your installed version
- Run `./gradlew generateApiDocs` to refresh after Hytale updates

## Hytale Installation
- Path: `F:\games\hytale`
- Mods folder: `F:\games\hytale\UserData\Mods`

## In-Game Testing
```
/hv help                          # Show all commands
/hv list                          # List registered vehicles
/hv types                         # List vehicle types (BOAT, etc.)
/hv spawn hyvehicles:simple_boat  # Spawn a boat
/hv info hyvehicles:simple_boat   # Show vehicle details
```

---

## Hytale Server API Patterns

### Command System

**Base class:** `com.hypixel.hytale.server.core.command.system.basecommands.CommandBase`

**Argument Types (ArgTypes):**
- `ArgTypes.STRING` - String argument
- `ArgTypes.INTEGER` - Integer argument
- `ArgTypes.FLOAT` - Float argument
- `ArgTypes.BOOLEAN` - Boolean argument
- `ArgTypes.PLAYER_REF` - Player reference

**Defining Arguments:**
```java
// Required argument
RequiredArg<String> nameArg = withRequiredArg("name", "Description", ArgTypes.STRING);

// Optional with default
DefaultArg<String> modeArg = withDefaultArg("mode", "Description", ArgTypes.STRING, "default", "default");

// Flag argument
FlagArg verboseFlag = withFlagArg("verbose", "Enable verbose output");
```

**Getting Values:**
```java
String value = ctx.get(nameArg);
boolean provided = ctx.provided(optionalArg);
```

**Sending Messages:**
```java
ctx.sendMessage(Message.raw("Plain text"));
ctx.sendMessage(Message.raw("Colored").color("#FF5555"));  // Red
ctx.sendMessage(Message.raw("Success").color("#55FF55"));  // Green
ctx.sendMessage(Message.raw("Warning").color("#FFAA00"));  // Orange
ctx.sendMessage(Message.raw("Highlight").color("#FFFF55")); // Yellow
```

### Player Data via ECS

**IMPORTANT**: For commands that access player/world data, use `AbstractPlayerCommand`:

```java
// CORRECT: Thread-safe player access
public class MyCommand extends AbstractPlayerCommand {
    public MyCommand() {
        super("mycommand", "Description");
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef player, World world) {
        // player, store, ref, world are already available and thread-safe!
        Transform transform = player.getTransform();
        Vector3d position = transform.getPosition();  // position.x, position.y, position.z
        Vector3f rotation = transform.getRotation();
        float yaw = rotation.getYaw();
    }
}
```

**DO NOT** use `ctx.senderAsPlayerRef()` in `CommandBase.executeSync()` - it causes threading errors!

### Vector Classes

**Vector3d (position - doubles):**
- Public fields: `x`, `y`, `z`
- Methods: `getX()`, `getY()`, `getZ()`, `add()`, `subtract()`, `scale()`, etc.

**Vector3f (rotation - floats):**
- Public fields: `x`, `y`, `z`
- Rotation aliases: `getPitch()` (x), `getYaw()` (y), `getRoll()` (z)

### Plugin Lifecycle

```java
public class MyPlugin extends JavaPlugin {
    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        // Register commands, initialize systems
        getCommandRegistry().registerCommand(new MyCommand());
    }

    @Override
    public void start() {
        // Plugin fully loaded, game running
    }

    @Override
    public void shutdown() {
        // Cleanup
    }
}
```

### ECS Entity Spawning (VISIBLE ENTITIES)

**Core pattern: Holder → Store → Ref**

**IMPORTANT:** For entities to be VISIBLE to clients, you need:
1. `TransformComponent` - Position/rotation
2. `ModelComponent` - Visual model
3. `PersistentModel` - Model reference
4. `BoundingBox` - Collision bounds
5. `UUIDComponent` - Entity tracking
6. **`NetworkId`** - CLIENT SYNC (REQUIRED for visibility!)

```java
// 1. Get the store from World
Store<EntityStore> store = world.getEntityStore().getStore();

// 2. Create a holder (entity blueprint)
Holder<EntityStore> holder = store.getRegistry().newHolder();

// 3. Add TransformComponent for position
TransformComponent transform = new TransformComponent(
    new Vector3d(x, y, z),
    new Vector3f(0, yaw, 0)
);
holder.addComponent(TransformComponent.getComponentType(), transform);

// 4. Add model components
ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset("hytale:entity/boat");
// Fallback: ModelAsset.DEBUG always exists
Model model = Model.createUnitScaleModel(modelAsset);
holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

// 5. Add tracking components
holder.ensureComponent(UUIDComponent.getComponentType());

// 6. ADD NETWORKID - REQUIRED FOR CLIENT VISIBILITY!
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));

// 7. Spawn entity
Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);

// 8. Later: remove entity
store.removeEntity(entityRef, RemoveReason.REMOVE);
```

**Key ECS classes:**
- `Store<EntityStore>` - Entity container, add/remove entities
- `Holder<EntityStore>` - Entity blueprint, add components before spawning
- `Ref<EntityStore>` - Reference to a live entity
- `TransformComponent` - Position and rotation
- `ModelComponent` - Visual 3D model
- `PersistentModel` - Model reference for persistence
- `BoundingBox` - Collision bounds
- `NetworkId` - **CLIENT SYNC** (without this, entity only exists server-side!)
- `AddReason.SPAWN` / `RemoveReason.REMOVE` - Spawn/remove reasons

### Key Imports

```java
// Commands
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.arguments.system.*;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

// Messaging
import com.hypixel.hytale.server.core.Message;

// ECS / Player
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// Entity Components (for visible entities)
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;  // CRITICAL for client visibility!

// Models
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

// Math (Hytale's built-in vectors - use these, NOT JOML!)
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Transform;

// Plugin
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
```

### IMPORTANT: External Libraries Don't Work!

**DO NOT use JOML or other external math libraries!**
- Hytale's plugin classloader doesn't see bundled JAR dependencies
- Shadow JAR relocation fails on Java 25 (ASM doesn't support class version 69)
- **Solution:** Use Hytale's built-in `Vector3d`/`Vector3f` or create your own simple `Vec3` class

```java
// Our custom mutable Vec3 for physics (see util/Vec3.java)
public class Vec3 {
    public float x, y, z;
    public Vec3(float x, float y, float z) { ... }
    public void set(Vec3 other) { ... }
    public float[] toArray() { return new float[] { x, y, z }; }
}
```

---

## Project Structure

```
app/src/main/java/com/alexispace/hyvehicles/
├── HytaleVehiclesPlugin.java    # Main plugin entry
├── api/
│   ├── VehicleAPI.java          # Public API interface
│   ├── VehicleAPIImpl.java      # API implementation
│   ├── VehicleHandle.java       # Spawned vehicle reference
│   ├── VehicleHandleImpl.java
│   └── VehicleTypeCreator.java  # Extension point for custom types
├── command/
│   └── HvCommand.java           # /hv command handler
├── definition/
│   ├── VehicleDefinition.java   # JSON vehicle schema
│   └── SeatDefinition.java      # Seat position schema
├── entity/
│   ├── BaseVehicle.java         # Core vehicle entity (physics)
│   ├── WaterVehicle.java        # Boat with buoyancy
│   └── VehicleEntityBridge.java # Hytale ECS integration
├── interaction/
│   └── SpawnVehicleInteraction.java  # Right-click to spawn vehicle
├── loader/
│   └── VehicleLoader.java       # JSON loading
├── registry/
│   ├── VehicleRegistry.java     # Type & definition registry
│   └── BoatCreator.java         # Built-in BOAT type
└── util/
    ├── VehicleLogger.java       # Logging wrapper
    └── Vec3.java                # Simple mutable vector (replaces JOML)

app/src/main/resources/
├── manifest.json                # Plugin manifest (IncludesAssetPack: true)
├── vehicles/                    # Internal vehicle definitions
│   ├── simple_boat.json
│   └── rowboat.json
├── Server/
│   ├── Item/
│   │   ├── Items/
│   │   │   ├── simple_boat_spawn.json   # Spawn item for simple boat
│   │   │   └── rowboat_spawn.json       # Spawn item for rowboat
│   │   └── Category/
│   │       └── CreativeLibrary/
│   │           └── VehiclesCategory.json  # Vehicles tab in creative menu
│   └── Languages/
│       └── en-US/
│           └── ui.lang              # Translation strings
└── Common/
    └── Icons/
        └── ItemCategories/          # Category icons (32x32 PNG)
            └── README.txt           # Icon requirements
```

---

## Development Workflow

1. Make code changes
2. Build: `./gradlew.bat :app:build`
3. Deploy: `./gradlew.bat :app:deployToGame`
4. Restart Hytale (or reload if hot-reload supported)
5. Test in-game with `/hv` commands

## Common Issues

- **Build fails with API errors**: Check javap output for actual Hytale class signatures
- **Command not found**: Verify command is registered in `registerCommands()` method
- **Null player**: Always check `ctx.isPlayer()` before `senderAsPlayerRef()`
- **NoClassDefFoundError for JOML/external libs**: Hytale's classloader doesn't see bundled deps - use Hytale's vectors or custom Vec3
- **Entity spawns but invisible**: Missing `NetworkId` component! It's required to sync entity to clients
- **Shadow JAR relocation fails**: Java 25 class files (version 69) not supported by ASM - can't relocate packages
- **NPCMarkerComponent not found**: Deprecated/removed - use `NetworkId` instead for entity visibility

## Hytale API Gotchas (Lessons Learned)

1. **Package paths aren't documented** - search web/community for correct imports
2. **NetworkId is in `modules.entity.tracker`** not `entity` package
3. **UUIDComponent is in `server.core.entity`** not `modules.entity.component`
4. **Always use `AbstractPlayerCommand`** for commands that need world/player access
5. **Hytale is Early Access** - API changes frequently, community docs may be outdated

## Creative Inventory & Item System

### Adding Items to Creative Menu

Items are defined in JSON files at `Server/Item/Items/`. The `Categories` field controls which creative menu tab they appear in.

**Item Definition (`Server/Item/Items/my_item.json`):**
```json
{
  "Id": "mymod_my_item",
  "TranslationProperties": {
    "Name": "My Item",
    "Description": "Item description here."
  },
  "Icon": "Icons/ItemsGenerated/my_item_icon.png",
  "Quality": "Common",
  "MaxStack": 1,
  "Categories": ["Vehicles.Boats"],

  "Interactions": {
    "Secondary": {
      "Interactions": [{
        "Type": "mymod_custom_interaction",
        "CustomField": "value"
      }]
    }
  },

  "Recipe": {
    "TimeSeconds": 5.0,
    "Input": [
      {"ItemId": "Plank_Oak", "Quantity": 5}
    ],
    "BenchRequirement": [{
      "Id": "Workbench",
      "Type": "Crafting",
      "Categories": ["Workbench_Survival"]
    }]
  }
}
```

**Custom Item Category (`Server/Item/Category/CreativeLibrary/VehiclesCategory.json`):**
```json
{
  "Id": "Vehicles",
  "Name": "ui.itemcategory.vehicles",
  "Icon": "Icons/ItemCategories/Vehicles.png",
  "Order": 10,
  "Children": [
    {
      "Id": "Boats",
      "Name": "ui.itemcategory.vehicles_boats",
      "Icon": "Icons/ItemCategories/Vehicles_Boats.png"
    }
  ]
}
```

**Translation file (`Server/Languages/en-US/ui.lang`):**
```
itemcategory.vehicles = Vehicles
itemcategory.vehicles_boats = Boats
```

### Custom Interactions

**1. Create interaction class:**
```java
public class SpawnVehicleInteraction extends SimpleInstantInteraction {
    private final String vehicleId;

    public static final BuilderCodec<SpawnVehicleInteraction> CODEC = BuilderCodec.builder(
        SpawnVehicleInteraction.class,
        SpawnVehicleInteraction::new,
        SimpleInstantInteraction.CODEC
    )
    .with(
        Codecs.STRING.fieldOf("VehicleId").required(),
        SpawnVehicleInteraction::getVehicleId,
        (builder, vehicleId) -> builder.vehicleId = vehicleId
    )
    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldown) {
        // Custom behavior when item is used
        Vector3d targetPos = context.getTargetPosition();
        context.consumeItem(1);  // Remove item from inventory
    }
}
```

**2. Register in plugin setup:**
```java
@Override
public void setup() {
    getCodecRegistry(Interaction.CODEC).register(
        "hyvehicles_spawn_vehicle",
        SpawnVehicleInteraction.class,
        SpawnVehicleInteraction.CODEC
    );
}
```

**Key interaction imports:**
```java
import com.hypixel.hytale.codec.BuilderCodec;
import com.hypixel.hytale.codec.Codecs;
import com.hypixel.hytale.server.core.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.interaction.Interaction;
import com.hypixel.hytale.server.core.interaction.InteractionContext;
import com.hypixel.hytale.server.core.interaction.InteractionType;
import com.hypixel.hytale.server.core.interaction.SimpleInstantInteraction;
```

### Manifest.json Requirements

For asset packs (items, models, etc.), set in manifest.json:
```json
{
  "IncludesAssetPack": true
}
```

### Asset Pack Folder Structure

```
app/src/main/resources/
├── manifest.json
├── Server/
│   ├── Item/
│   │   ├── Items/
│   │   │   └── my_item.json
│   │   └── Category/
│   │       └── CreativeLibrary/
│   │           └── MyCategory.json   # Custom creative menu tab
│   └── Languages/
│       └── en-US/
│           └── ui.lang               # Translation strings
├── Common/
│   ├── Icons/
│   │   ├── ItemsGenerated/
│   │   │   └── my_item_icon.png
│   │   └── ItemCategories/           # Category tab icons (32x32 PNG)
│   │       └── MyCategory.png
│   └── Items/
│       └── my_item/
│           ├── model.blockymodel
│           └── model_texture.png
└── vehicles/
    └── simple_boat.json  (our internal vehicle definitions)
```

---

## Crafting Recipes

### Recipe JSON Structure

Add a `"Recipe"` field to any item JSON to make it craftable:

```json
"Recipe": {
  "TimeSeconds": 5.0,
  "Input": [
    {"ItemId": "Ingredient_Stick", "Quantity": 2},
    {"ResourceTypeId": "Wood_Planks", "Quantity": 5}
  ],
  "BenchRequirement": [{
    "Id": "Workbench",
    "Type": "Crafting",
    "Categories": ["Workbench_Survival"]
  }]
}
```

### ItemId vs ResourceTypeId

Recipe inputs support two modes:
- **`ItemId`** — requires a specific item (e.g., `"Ingredient_Stick"`)
- **`ResourceTypeId`** — accepts any item in a category (e.g., `"Wood_Planks"` accepts all plank types)

ResourceTypes are defined in `Server/Item/ResourceTypes/*.json`. Each item specifies which ResourceTypes it belongs to via a `"ResourceTypes"` array.

### Common Item IDs (Verified)

| In-game Name | Correct ItemId |
|---|---|
| Stick | `Ingredient_Stick` |
| Plant Fibre | `Ingredient_Fibre` (British spelling!) |
| Sand | `Soil_Sand` |
| Bronze Bar | `Ingredient_Bar_Bronze` |

### Common ResourceTypeIds

| Category | ResourceTypeId |
|---|---|
| Any wood planks | `Wood_Planks` |
| Any wood trunk | `Wood_Trunk` |
| All wood | `Wood_All` |
| Any metal bar | `Metal_Bars` |
| Any rock | `Rock` |
| Any fish | `Fish` |
| Any meat | `Meat` |

### Workbench Types

**Survival Workbench:**
```json
"BenchRequirement": [{
  "Id": "Workbench",
  "Type": "Crafting",
  "Categories": ["Workbench_Survival"]
}]
```

**Builder's Workbench:**
```json
"BenchRequirement": [{
  "Id": "Builders",
  "Type": "StructuralCrafting",
  "Categories": ["Window"]
}]
```

Builder's Workbench categories include: `Wall`, `Platform`, `Stairs`, `HalfSlab`, `Beam`, `Decorative`, `Ornate`, `Roof`, `Pillar`, `Door`, `Window`, `WoodPlanks`, and more (defined in `Bench_Builders.json`).

### Gotchas

- **No `Output` object!** The `Output` field expects an array `[...]`, not `{...}`. Passing `"Output": {"Quantity": 4}` causes a codec decode error. Default output is 1 item.
- **Item IDs use underscores and specific prefixes** — don't guess! Common patterns: `Ingredient_*`, `Soil_*`, `Wood_*_Planks`
- **`Ingredient_Fibre`** uses British spelling (not "Fiber")
- **ResourceTypeId naming** follows pattern: `Wood_Planks`, `Wood_Trunk`, `Rock_Stone`, etc.

---

## Two-Sided Rendering in Blockymodel Files

### The Problem

When creating thin 3D objects like glass panes, fences, or panels that need to look correct from both sides, using `"doubleSided": true` causes **UV mirroring issues** on the back face. This happens because the back face of a 3D box naturally has inverted UV coordinates (it faces the opposite direction).

**Symptoms of the problem:**
- Borders appear on wrong edges when viewed from the back
- Textures look mirrored/flipped on one side
- Left/right borders swap positions depending on viewing angle

### The Solution: Texture Atlas Approach

**Discovered from official Hytale file:** `Panel_256.blockymodel` in `image_frames_assets`

Hytale's official approach uses:
1. `"doubleSided": false` (NOT true!)
2. A **texture atlas** with pre-mirrored back texture
3. Different texture offsets for front and back faces

### Texture Atlas Format

Create a **64x32 pixel** texture atlas:
- **Left half (x: 0-31):** Normal texture for front face
- **Right half (x: 32-63):** Pre-mirrored (X-flipped) texture for back face

```
┌────────────────┬────────────────┐
│                │                │
│  FRONT FACE    │   BACK FACE    │
│  (normal)      │  (X-mirrored)  │
│                │                │
│   0-31 px      │   32-63 px     │
└────────────────┴────────────────┘
        64 pixels wide
```

**Example PowerShell to create the atlas:**
```powershell
$bmp = New-Object System.Drawing.Bitmap(64, 32)

# LEFT HALF (0-31): Normal texture for front face
if ($leftBorder) {
    for ($y = 0; $y -lt 32; $y++) {
        $bmp.SetPixel(0, $y, $borderColor)
        $bmp.SetPixel(1, $y, $borderColor)
    }
}
if ($rightBorder) {
    for ($y = 0; $y -lt 32; $y++) {
        $bmp.SetPixel(30, $y, $borderColor)
        $bmp.SetPixel(31, $y, $borderColor)
    }
}

# RIGHT HALF (32-63): X-Mirrored texture for back face
# Mirrored: leftBorder appears on RIGHT side of right half
if ($leftBorder) {
    for ($y = 0; $y -lt 32; $y++) {
        $bmp.SetPixel(62, $y, $borderColor)
        $bmp.SetPixel(63, $y, $borderColor)
    }
}
# Mirrored: rightBorder appears on LEFT side of right half
if ($rightBorder) {
    for ($y = 0; $y -lt 32; $y++) {
        $bmp.SetPixel(32, $y, $borderColor)
        $bmp.SetPixel(33, $y, $borderColor)
    }
}
```

### Blockymodel Configuration

```json
{
  "nodes": [
    {
      "id": "1",
      "name": "pane",
      "position": {"x": 0, "y": 0, "z": 0},
      "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
      "shape": {
        "type": "box",
        "offset": {"x": 0, "y": 16, "z": 0},
        "stretch": {"x": 1, "y": 1, "z": 1},
        "settings": {"isPiece": false, "size": {"x": 32, "y": 32, "z": 4}},
        "textureLayout": {
          "front": {"offset": {"x": 0, "y": 0}},
          "back": {"offset": {"x": 32, "y": 0}},
          "right": {"offset": {"x": 0, "y": 0}},
          "left": {"offset": {"x": 0, "y": 0}},
          "top": {"offset": {"x": 0, "y": 0}},
          "bottom": {"offset": {"x": 0, "y": 0}}
        },
        "unwrapMode": "custom",
        "visible": true,
        "doubleSided": false,
        "shadingMode": "flat"
      }
    }
  ],
  "format": "prop",
  "lod": "auto"
}
```

**Key settings:**
- `"doubleSided": false` - Renders front and back as separate faces with their own UVs
- `"front": {"offset": {"x": 0, "y": 0}}` - Front face uses left half of texture (x: 0-31)
- `"back": {"offset": {"x": 32, "y": 0}}` - Back face uses right half of texture (x: 32-63)
- `"unwrapMode": "custom"` - Required for manual UV control

### What NOT to Do

**DON'T use `"doubleSided": true` with mirror property:**
```json
// WRONG - causes broken textures, dark colors, or garbage pixels
"textureLayout": {
  "front": {"offset": {"x": 0, "y": 0}},
  "back": {"offset": {"x": 0, "y": 0}, "mirror": {"x": true, "y": false}}
}
```

**DON'T use two separate nodes for front/back:**
```json
// WRONG - causes z-fighting and thickness inconsistencies
"nodes": [
  {"name": "front_face", "shape": {..., "doubleSided": false}},
  {"name": "back_face", "shape": {..., "doubleSided": false}}
]
```

**DON'T mix shape types:**
```json
// WRONG - "quad" and "box" have different thicknesses
"nodes": [
  {"shape": {"type": "quad", ...}},  // 2D plane
  {"shape": {"type": "box", ...}}    // 3D box
]
```

### Reference Files

- **Official Hytale example:** `F:\games\hytale\UserData\Saves\dupoland\image_frames_assets\Common\Blocks\ImageFrames\PanelModels\Panel_256.blockymodel`
- **Working glass pane models:** `HytaleWindows\app\src\main\resources\Common\Items\GlassPane\*.blockymodel`

---

## Useful Resources

- [HytaleModding Spawning Guide](https://hytalemodding.dev/en/docs/guides/plugin/spawning-entities)
- [Title Holograms Example](https://hytalemodding.dev/en/docs/guides/plugin/text-hologram) - shows NetworkId usage
- [Hytale ECS Theory](https://hytalemodding.dev/en/docs/guides/ecs/hytale-ecs-theory)
- [Item Interaction Guide](https://hytalemodding.dev/en/docs/guides/plugin/item-interaction) - Custom item interactions
- [Hytale Item Schema](https://gist.github.com/Huijiro/fe069677c25d58edb5beaab917f760a4) - Item JSON fields

here are game logs: F:\games\hytale\UserData\Saves\test mods\logs

---

## Future Improvements / TODO

### Custom Vehicle Preview System (Low Priority - Wait for API)

**Current state:** Using `BlockType` in item JSON for hover preview. Works but has limitations:
- Preview position is static (doesn't account for water surface detection)
- Preview shows at block position, spawn may adjust Y for water (-0.3f offset)

**Future improvement:** Create tick-based ghost entity preview system:
1. Detect when player holds vehicle item
2. Server raycast + water detection each tick
3. Spawn/move semi-transparent ghost entity at correct spawn position
4. Remove ghost when player clicks or switches items

**Why wait:** Hytale is in Early Access. They may add:
- Better preview API for custom interactions
- More interaction types with built-in preview support
- Client-side preview hooks

**Note:** Hytale does NOT support client-side mods (client is C# compiled to machine code).
All preview logic must be server-side, synced to client via entity NetworkId.

**Resources:**
- [Hytale Modding Strategy](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status)
- [HTDevLib](https://www.curseforge.com/hytale/mods/htdevlib) - Has tick tracking utilities