# Hytale Modding Techniques - Master Summary

A comprehensive summary of techniques, APIs, and patterns discovered from analyzing 17+ community mods.

---

## Table of Contents

1. [ECS Components](#ecs-components)
2. [Ticking Systems](#ticking-systems)
3. [Custom Interactions](#custom-interactions)
4. [UI System](#ui-system)
5. [Entity Spawning](#entity-spawning)
6. [NPC & Companion Systems](#npc--companion-systems)
7. [Block States](#block-states)
8. [Storage & Persistence](#storage--persistence)
9. [Event System](#event-system)
10. [Config System](#config-system)
11. [Networking](#networking)
12. [Built-in Interaction Types](#built-in-interaction-types)
13. [Key API Classes](#key-api-classes)
14. [Patterns for HytaleVehicles](#patterns-for-hytalevehicles)

---

## ECS Components

### Registering Custom Components

```java
// Simple component (no persistence)
ComponentType<EntityStore, MyComponent> type =
    getEntityStoreRegistry().registerComponent(MyComponent.class, MyComponent::new);

// Persistent component with codec (saved with entity)
ComponentType<EntityStore, MyComponent> type =
    getEntityStoreRegistry().registerComponent(
        MyComponent.class, "mymod:component_name", MyComponent.CODEC);

// Chunk-based component
ComponentType<ChunkStore, MyChunkComponent> type =
    getChunkStoreRegistry().registerComponent(
        MyChunkComponent.class, "mymod:chunk_component", MyChunkComponent.CODEC);
```

### Component with BuilderCodec

```java
public class ManaComponent implements Component<EntityStore> {
    public static final BuilderCodec<ManaComponent> CODEC = BuilderCodec.builder(
        ManaComponent.class, ManaComponent::new
    )
    .append(new KeyedCodec("CurrentMana", Codec.INTEGER),
        (c, v) -> c.currentMana = v, c -> c.currentMana).add()
    .append(new KeyedCodec("MaxMana", Codec.INTEGER),
        (c, v) -> c.maxMana = v, c -> c.maxMana).add()
    .build();

    private int currentMana = 100;
    private int maxMana = 100;

    @Override
    public Component<EntityStore> clone() {
        ManaComponent clone = new ManaComponent();
        clone.currentMana = this.currentMana;
        clone.maxMana = this.maxMana;
        return clone;
    }
}
```

### Notable Components Discovered

| Component | Source | Purpose |
|-----------|--------|---------|
| `ManaComponent` | Spellbook Plus | Player mana tracking |
| `SkillComponent` | MMO Skill Tree | XP and levels per skill |
| `QuestComponent` | MMO Skill Tree | Quest progress |
| `SpellHotbarComponent` | Spellbook Plus | Equipped spells |
| `ProjectileDataComponent` | Spellbook Plus | Spell projectile data |
| `LumenSystem` | LumenChannelers | Wire signal state |
| `ProcessingBenchState` | SmartFurnaces | Furnace I/O |

---

## Ticking Systems

### EntityTickingSystem Pattern

```java
public class MyTickingSystem extends EntityTickingSystem<ChunkStore> {
    private ComponentType<ChunkStore, MyComponent> componentType;
    private int tickCounter = 0;

    @Override
    public Query<ChunkStore> getQuery() {
        return this.componentType;  // Query for entities with this component
    }

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
        // Rate limiting - only process every 20 ticks
        if (++tickCounter % 20 != 0) return;

        MyComponent component = chunk.getComponent(index, componentType);
        // Process the component...
    }
}
```

### System Registration

```java
// Entity store systems (per-entity)
getEntityStoreRegistry().registerSystem(new SpellLifetimeSystem());
getEntityStoreRegistry().registerSystem(new ManaHudUpdateSystem());

// Chunk store systems (per-chunk/block)
getChunkStoreRegistry().registerSystem(new SmartBenchSystem());
getChunkStoreRegistry().registerSystem(new LumenBlockSystem());
```

### Notable Systems Discovered

| System | Source | Purpose |
|--------|--------|---------|
| `SmartBenchSystem` | SmartFurnaces | Auto-output from furnaces |
| `LumenBlockSystem` | LumenChannelers | Signal propagation |
| `SpellLifetimeSystem` | Spellbook Plus | Projectile expiration |
| `ManaHudUpdateSystem` | Spellbook Plus | HUD refresh |
| `BreakBlockEventSystem` | MMO Skill Tree | XP on block break |
| `CombatXpEventSystem` | MMO Skill Tree | XP on combat |

---

## Custom Interactions

### SimpleInstantInteraction

```java
public class MyInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<MyInteraction> CODEC = BuilderCodec.builder(
        MyInteraction.class, MyInteraction::new, SimpleInstantInteraction.CODEC
    )
    .with(Codecs.STRING.fieldOf("CustomField").required(),
        MyInteraction::getCustomField,
        (builder, value) -> builder.customField = value)
    .build();

    private String customField;

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldown) {
        CommandBuffer commandBuffer = context.getCommandBuffer();
        Ref entityRef = context.getEntity();
        ItemStack heldItem = context.getHeldItem();
        Vector3d targetPos = context.getTargetPosition();

        // Custom logic here...
        context.consumeItem(1);  // Remove item from inventory
    }
}
```

### Interaction Registration

```java
getCodecRegistry(Interaction.CODEC).register(
    "mymod_interaction_name",
    MyInteraction.class,
    MyInteraction.CODEC);
```

### Using in Item JSON

```json
{
  "Interactions": {
    "Secondary": {
      "Interactions": [{
        "Type": "mymod_interaction_name",
        "CustomField": "value"
      }]
    }
  }
}
```

---

## UI System

### InteractiveCustomUIPage with Typed Data

```java
public class MyPage extends InteractiveCustomUIPage<MyPageData> {
    public MyPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, MyPageData.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder ui,
                      UIEventBuilder events, Store<EntityStore> store) {
        // Load UI template
        ui.append("Pages/MyPage.ui");

        // Set values
        ui.set("#MyLabel.Text", "Hello World");
        ui.set("#MyButton.Visible", true);

        // Bind events
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#MyButton",
            new EventData().append("Action", "DoSomething").append("Id", "123"),
            false);

        // Bind value changes (for text fields)
        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            new EventData().append("Action", "Search").append("@Query", "#SearchInput.Value"),
            false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                MyPageData data) {
        switch (data.action) {
            case "DoSomething" -> handleAction(data.id);
            case "Search" -> handleSearch(data.query);
        }
    }

    // Data class with codec
    public static class MyPageData {
        public String action;
        public String id;
        public String query;

        public static final BuilderCodec<MyPageData> CODEC = BuilderCodec.builder(
            MyPageData.class, MyPageData::new
        )
        .append(new KeyedCodec("Action", Codec.STRING),
            (o, v) -> o.action = v, o -> o.action).add()
        .append(new KeyedCodec("Id", Codec.STRING),
            (o, v) -> o.id = v, o -> o.id).add()
        .append(new KeyedCodec("@Query", Codec.STRING),
            (o, v) -> o.query = v, o -> o.query).add()
        .build();
    }
}
```

### UI File Format (.ui)

```
// Variables and includes
$C = "../Common.ui";

Group #Container {
    Anchor: (Width: 500, Height: 400);
    Background: #1a1a2e;
    LayoutMode: Top;
    Padding: (Full: 20);

    Label #Title {
        Text: "My Page";
        Anchor: (Height: 30);
        Style: (FontSize: 18, TextColor: #ffffff, RenderBold: true);
    }

    TextField #SearchInput {
        Anchor: (Height: 40);
        Background: #0f1621;
        PlaceholderText: "Search...";
    }

    Group #List {
        FlexWeight: 1;
        LayoutMode: TopScrolling;
        ScrollbarStyle: $C.@DefaultScrollbarStyle;
    }

    Button #ActionButton {
        Anchor: (Height: 44);
        Style: (
            Default: (Background: #3a5a7c),
            Hovered: (Background: #4a7a9c),
            Pressed: (Background: #2a4a6c)
        );
        Label { Text: "Click Me"; }
    }

    ProgressBar #ManaBar {
        Anchor: (Height: 20);
        BarTexturePath: "ManaFill.png";
        Value: 0.75;
    }
}
```

### Dynamic UI Updates

```java
private void refreshList(Ref<EntityStore> ref, Store<EntityStore> store, List<Item> items) {
    UICommandBuilder ui = new UICommandBuilder();
    UIEventBuilder events = new UIEventBuilder();

    ui.clear("#List");

    for (int i = 0; i < items.size(); i++) {
        String selector = "#List[" + i + "]";
        ui.append("#List", "Pages/ListItem.ui");
        ui.set(selector + " #ItemName.Text", items.get(i).name);

        events.addEventBinding(CustomUIEventBindingType.Activating,
            selector + " #SelectButton",
            new EventData().append("Action", "Select").append("Index", String.valueOf(i)),
            false);
    }

    // Inline UI for empty state
    if (items.isEmpty()) {
        ui.appendInline("#List", "Label { Text: \"No items\"; Style: (TextColor: #888888); }");
    }

    sendUpdate(ui, events, false);
}
```

### Opening Pages

```java
Player player = store.getComponent(ref, Player.getComponentType());
player.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));
```

---

## Entity Spawning

### Using Holder Pattern

```java
Store<EntityStore> store = world.getEntityStore().getStore();
Holder<EntityStore> holder = store.getRegistry().newHolder();

// Add transform
holder.addComponent(TransformComponent.getComponentType(),
    new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, yaw, 0)));

// Add model
ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset("hytale:entity/boat");
Model model = Model.createUnitScaleModel(modelAsset);
holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

// CRITICAL: Add NetworkId for client visibility!
holder.addComponent(NetworkId.getComponentType(),
    new NetworkId(store.getExternalData().takeNextNetworkId()));

holder.ensureComponent(UUIDComponent.getComponentType());

// Spawn
Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
```

### Spawning Projectiles

```java
TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
Vector3d position = new Vector3d(x, y, z);
Vector3f rotation = new Vector3f(pitch, yaw, 0);

Holder holder = ProjectileComponent.assembleDefaultProjectile(
    timeResource, "ProjectileAssetName", position, rotation);

ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
projectile.initialize();
projectile.shoot(holder, ownerId, x, y, z, yaw, pitch);

// Add custom data
holder.putComponent(MyDataComponent.getComponentType(), customData);

commandBuffer.addEntity(holder, AddReason.SPAWN);
```

---

## NPC & Companion Systems

### SpawnNPC Interaction (JSON)

```json
{
  "Type": "SpawnNPC",
  "EntityId": "MyMod_SummonedCreature"
}
```

### Flock System for Companions

```json
{
  "FlockArray": ["AllowedNPCType"],
  "FlockSpawnTypes": { "Compute": "FlockArray" },
  "DisableDamageGroups": ["Self", "Player"],
  "DespawnTimer": 300,

  "Instructions": [
    {
      "Sensor": { "Type": "Player", "Range": 20 },
      "Actions": [{ "Type": "JoinFlock", "ForceJoin": true }]
    },
    {
      "Sensor": { "Type": "FlockLeader" },
      "BodyMotion": { "Type": "Seek", "StopDistance": 4 }
    },
    {
      "Sensor": { "Type": "InflictedDamage", "Target": "FlockLeader" },
      "Actions": [
        { "Type": "FlockTarget" },
        { "Type": "State", "State": "Combat" }
      ]
    }
  ]
}
```

---

## Block States

### ChangeState Interaction

```json
{
  "Type": "ChangeState",
  "Changes": {
    "default": "StateB",
    "StateB": "StateC",
    "StateC": "default"
  }
}
```

### State Definitions

```json
{
  "State": {
    "Id": "container",      // Built-in container behavior
    "Capacity": 54,
    "Definitions": {
      "OpenWindow": {
        "InteractionSoundEventId": "SFX_Chest_Open",
        "CustomModelAnimation": "Open.blockyanim"
      },
      "CloseWindow": {
        "InteractionSoundEventId": "SFX_Chest_Close",
        "CustomModelAnimation": "Close.blockyanim"
      },
      "CustomState": {
        "CustomModelTexture": [{ "Texture": "alternate.png", "Weight": 1 }],
        "InteractionHint": "translation.key"
      }
    }
  }
}
```

---

## Storage & Persistence

### World Resources

```java
// Register global resource
getEntityStoreRegistry().registerResource(MailboxResource.class, MailboxResource::new);

// Access in code
MailboxResource resource = store.getResource(MailboxResource.getResourceType());
```

### Item Metadata

```java
// Write metadata
BsonDocument metadata = new BsonDocument();
metadata.put("mymod:custom_data", new BsonString("value"));
ItemStack newStack = itemStack.withMetadata(metadata);

// Read metadata
String value = itemStack.getFromMetadataOrDefault("mymod:custom_data", "default");
```

### Player World Data (MapMarkers)

```java
String worldName = player.getWorld().getName();
PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(worldName);
MapMarker[] markers = perWorldData.getWorldMapMarkers();
perWorldData.setWorldMapMarkers(newMarkers);
```

### External Config Files

```java
Path configPath = Paths.get("mods", "mymod", "config.json");
// Load/save using custom JSON handling
```

---

## Event System

### Registering Event Listeners

```java
// Per-entity events
getEventRegistry().register(PlayerConnectEvent.class, event -> {
    PlayerRef playerRef = event.getPlayerRef();
    Holder holder = event.getHolder();
    // Initialize player data
});

// Global events
getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
    Player player = event.getPlayer();
    World world = player.getWorld();
    world.execute(() -> {
        // Safe to access world data
    });
});

// Server-wide events
HytaleServer.get().getEventBus().register(PlayerMouseButtonEvent.class, listener);
```

### Key Events

| Event | When |
|-------|------|
| `PlayerConnectEvent` | Player connecting, has Holder |
| `PlayerReadyEvent` | Player fully loaded |
| `PlayerDisconnectEvent` | Player leaving |
| `AddPlayerToWorldEvent` | Player added to world |
| `PlayerMouseButtonEvent` | Mouse input |

---

## Config System

### Using Hytale's Config

```java
private final Config<MyConfig> config;

public MyPlugin(JavaPluginInit init) {
    super(init);
    this.config = withConfig("my_config", MyConfig.CODEC);
}

protected void setup() {
    config.save();  // Creates default if missing
    MyConfig values = config.get();
}
```

---

## Networking

### Custom Packet Handlers

```java
ServerManager.get().registerSubPacketHandlers(MyPacketHandler::new);
```

### Notifications

```java
NotificationUtil.sendNotification(
    playerRef.getPacketHandler(),
    Message.raw("Notification text").color(Color.GREEN));
```

---

## Built-in Interaction Types

| Type | Description | Key Properties |
|------|-------------|----------------|
| `Explode` | Explosion damage | `BlockDamageRadius`, `EntityDamage`, `Knockback` |
| `SpawnNPC` | Spawn entity | `EntityId` |
| `ChangeState` | Cycle block state | `Changes` map |
| `Charging` | Hold to charge | `RunTime`, `Next` thresholds |
| `Serial` | Sequential actions | `Interactions` array |
| `RunOnBlockTypes` | Target blocks | `BlockSets`, `Range`, `MaxCount` |
| `DestroyBlock` | Remove block | - |
| `ModifyInventory` | Change items | `AdjustHeldItemQuantity` |
| `OpenCustomUI` | Open UI page | `Page` |
| `Selector` | Target entities | `AOECircle`, `Range` |

---

## Key API Classes

### Core

```java
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
```

### Entities

```java
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
```

### Interactions

```java
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.entity.InteractionContext;
```

### UI

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
```

### Math

```java
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Transform;
```

---

## Patterns for HytaleVehicles

### Vehicle Spawning
- Use `Holder` pattern with `NetworkId` for client visibility
- Consider `SpawnNPC` for AI-driven vehicles
- Use `ProjectileComponent.assembleDefaultProjectile()` pattern

### Vehicle Physics
- Create `VehiclePhysicsSystem` extending ticking system
- Use `TransformComponent` for position/rotation
- Consider using Hytale's movement controllers

### Vehicle UI
- `InteractiveCustomUIPage<VehicleDashboardData>` for dashboard
- `ProgressBar` for fuel/health
- Dynamic button bindings for controls

### Vehicle States
- Use `State.Definitions` for door open/close
- `ChangeState` interaction for mode switching
- Animations via `CustomModelAnimation`

### Vehicle Ownership
- Persistent `VehicleOwnerComponent` with codec
- `DisableDamageGroups` for owner immunity
- Item metadata for vehicle keys

### Vehicle Companions
- Flock system for following player
- `DespawnTimer` for temporary vehicles
- Combat AI for defensive vehicles

### Vehicle Storage
- `State.Id: "container"` with `Capacity`
- Container state for cargo access

---

## Mods Analyzed

| Mod | Author | Type | Key Techniques |
|-----|--------|------|----------------|
| Spellbook Plus | Hexora | Java | Components, Systems, UI, Projectiles |
| MMO Skill Tree | ZiggFreed | Java | Persistent Data, Events, API |
| Waypoints | Boffmedia | Java | Custom UI Pages, Teleport |
| Books and Papers | Conczin | Java | Custom UI DSL, Metadata |
| LumenChannelers | Bunnir | Java | Signal Systems, Chunk Components |
| SmartFurnaces | Linceros | Java | Ticking Systems, Reflection |
| HyPipes | Blake21 | Java | Networks, Threading |
| Computale | Mouton | Java | LuaJ Scripting, Sandboxing |
| Trin's Motorcycle | Trin | Asset | MovementConfig, Mountable |
| BetterWardrobes | iTzKenar | Asset | Container State, Animations |
| MimicTale | Unknown | Asset | NPC Creation, Loot Tables |
| Treqy's Explosives | Treqy | Asset | Explode Interaction |
| Aures Paintings | BlackAures | Asset | ChangeState, Variants |
| Brotherhood Spellbooks | DarkWolf_XP | Asset | SpawnNPC, Flock System |
| ConveyorBelt | PixelComet | Java | Block Movement |
| ImageFrames | Unknown | Java | Custom Textures |
