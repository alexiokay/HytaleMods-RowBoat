# LumenChannelers - Deep Analysis

**Mod Name:** LumenChannelers
**Version:** 0.0.9
**Author:** Bunnir
**Architecture:** Java Plugin (412KB)

## Overview

LumenChannelers implements a redstone-like circuit system for Hytale. It demonstrates **custom ECS components**, **multiple system registrations**, and **complex signal propagation** through wire networks.

---

## Key Concepts

| Concept | Description |
|---------|-------------|
| Lumen Channels | Wire-like blocks that transmit colored signals |
| Logic Gates | AND, OR, NOT, XOR components |
| Rotatable | Blocks with directional inputs/outputs |
| Colliders | Detection triggers |
| Display Blocks | Visual feedback (lights, etc.) |

---

## Plugin Architecture

### Main Plugin (LumenChannelers.java)

```java
public class LumenChannelers extends JavaPlugin {
    static LumenChannelers instance;

    // Custom component types
    private static ComponentType lumenWireComponentType;
    private static ComponentType lumenLogicComponentType;
    private static ComponentType lumenRotatableComponentType;
    private static ComponentType lumenDisplayBlockComponentType;
    private static ComponentType lumenSystemComponentType;
    private static ComponentType lumenColliderComponentType;

    public LumenChannelers(JavaPluginInit init) {
        super(init);
    }

    protected void setup() {
        instance = this;

        // === Register Custom Interactions ===
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenChangeStateInteraction",
            LumenChangeStateInteraction.class,
            LumenChangeStateInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenPlaceBlockInteraction",
            LumenPlaceBlockInteraction.class,
            LumenPlaceBlockInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenPlaceBlockConditionInteraction",
            LumenPlaceBlockConditionInteraction.class,
            LumenPlaceBlockConditionInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenInputInteraction",
            LumenInputInteraction.class,
            LumenInputInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenChiselInteraction",
            LumenChiselInteraction.class,
            LumenChiselInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
            "LumenChiselInteraction_Off",
            LumenChiselInteraction_Off.class,
            LumenChiselInteraction_Off.CODEC
        );

        // === Register Custom Components ===
        // Chunk-level components (blocks)
        lumenSystemComponentType = this.getChunkStoreRegistry().registerComponent(
            LumenSystem.class, "LumenSystem", LumenSystem.CODEC
        );
        lumenLogicComponentType = this.getChunkStoreRegistry().registerComponent(
            LumenLogic.class, "LumenLogic", LumenLogic.CODEC
        );
        lumenRotatableComponentType = this.getChunkStoreRegistry().registerComponent(
            LumenRotatable.class, "LumenRotatable", LumenRotatable.CODEC
        );
        lumenWireComponentType = this.getChunkStoreRegistry().registerComponent(
            LumenWire.class, "LumenChannel", LumenWire.CODEC
        );
        lumenColliderComponentType = this.getChunkStoreRegistry().registerComponent(
            LumenCollider.class, "LumenCollider", LumenCollider.CODEC
        );

        // Entity-level components (dynamic objects)
        lumenDisplayBlockComponentType = this.getEntityStoreRegistry().registerComponent(
            LumenDisplayBlock.class, "LumenDisplayBlock", LumenDisplayBlock.CODEC
        );

        // === Register Systems ===
        // Chunk systems (for blocks)
        this.getChunkStoreRegistry().registerSystem(new LumenInitializer());
        this.getChunkStoreRegistry().registerSystem(new LumenBlockSystem());

        // Entity systems (for entities)
        this.getEntityStoreRegistry().registerSystem(new LumenEntityInitializer());
        this.getEntityStoreRegistry().registerSystem(new LumenEntitySystem());
        this.getEntityStoreRegistry().registerSystem(new LumenAbuseSystem());
    }

    // Getters for component types
    public ComponentType getLumenWireComponentType() { return lumenWireComponentType; }
    public ComponentType getLumenSystemComponentType() { return lumenSystemComponentType; }
    public ComponentType getLumenLogicComponentType() { return lumenLogicComponentType; }
    public ComponentType getLumenRotatableComponentType() { return lumenRotatableComponentType; }
    public ComponentType getLumenDisplayBlockComponentType() { return lumenDisplayBlockComponentType; }
    public ComponentType getLumenColliderComponentType() { return lumenColliderComponentType; }

    public static LumenChannelers get() { return instance; }
}
```

---

## Component Architecture

### ChunkStore vs EntityStore

| Store Type | Use Case | Example Components |
|------------|----------|-------------------|
| ChunkStore | Block-level data | LumenWire, LumenLogic, LumenRotatable |
| EntityStore | Entity-level data | LumenDisplayBlock |

### LumenSystem Component

The main component that handles signal routing:

```java
public class LumenSystem implements Component<ChunkStore> {
    public static final BuilderCodec CODEC = BuilderCodec.builder(
        LumenSystem.class, LumenSystem::new
    ).documentation("Allows this to be registered by Lumen.").build();

    public static ComponentType<ChunkStore, LumenSystem> getComponentType() {
        return LumenChannelers.get().getLumenSystemComponentType();
    }

    public Component<ChunkStore> clone() {
        return new LumenSystem();
    }

    // Check if signal can enter from this direction
    public int GetValidEntrance(Vector3i thisBlock, World world,
                                LumenRotatable rotatable, Vector3iDir entry) {
        LumenLogic logic = Utils.GetBlockComponent(thisBlock, world,
            LumenLogic.getComponentType());
        if (logic != null) {
            return logic.GetValidEntrance(rotatable, entry);
        }
        return -1;
    }

    // Process signal state change
    public List<TriggerWireData> RegisterStateChange(
        LumenRotatable rotatable,
        HashMap<Vector3iDir, LumenColors.COLOR> previousColors,
        HashMap<Vector3i, ArrayList<LumenColors.COLOR>[]> components,
        Vector3i block,
        World world,
        int inputIndex,
        LumenColors.COLOR color
    ) {
        LumenLogic logic = Utils.GetBlockComponent(block, world,
            LumenLogic.getComponentType());
        if (logic != null) {
            return logic.RegisterStateChange(rotatable, previousColors,
                components, block, world, inputIndex, color);
        }
        return null;
    }

    // Show wire signal visually
    public void DisplayWireParticles(Vector3i blockPos, World world,
                                     ArrayList<LumenColors.COLOR>[] v,
                                     LumenRotatable rotatable) {
        LumenLogic logic = Utils.GetBlockComponent(blockPos, world,
            LumenLogic.getComponentType());
        if (logic != null) {
            logic.DisplayWireParticles(blockPos, world, v, rotatable);
        }
    }
}
```

---

## Custom Component Registration

### Pattern for Custom Components

```java
// 1. Define the component class
public class MyComponent implements Component<ChunkStore> {
    public static final BuilderCodec<MyComponent> CODEC =
        BuilderCodec.builder(MyComponent.class, MyComponent::new).build();

    private int myValue;

    // Component interface requirement
    public Component<ChunkStore> clone() {
        MyComponent copy = new MyComponent();
        copy.myValue = this.myValue;
        return copy;
    }
}

// 2. Register in plugin setup()
myComponentType = this.getChunkStoreRegistry().registerComponent(
    MyComponent.class,
    "MyComponent",       // Asset ID for JSON reference
    MyComponent.CODEC
);

// 3. Access from other code
MyComponent comp = Utils.GetBlockComponent(position, world, myComponentType);
```

---

## System Registration

### Multiple Systems Pattern

LumenChannelers registers 5 different systems:

```java
// Chunk-level systems (process block data)
this.getChunkStoreRegistry().registerSystem(new LumenInitializer());
this.getChunkStoreRegistry().registerSystem(new LumenBlockSystem());

// Entity-level systems (process entity data)
this.getEntityStoreRegistry().registerSystem(new LumenEntityInitializer());
this.getEntityStoreRegistry().registerSystem(new LumenEntitySystem());
this.getEntityStoreRegistry().registerSystem(new LumenAbuseSystem());
```

### System Types

| System | Purpose |
|--------|---------|
| LumenInitializer | Initialize lumen components on chunk load |
| LumenBlockSystem | Process block state changes |
| LumenEntityInitializer | Initialize entity lumen components |
| LumenEntitySystem | Process entity lumen state |
| LumenAbuseSystem | Prevent exploit behaviors |

---

## Signal Colors

```java
public class LumenColors {
    public enum COLOR {
        OFF,
        RED,
        GREEN,
        BLUE,
        YELLOW,
        CYAN,
        MAGENTA,
        WHITE
    }
}
```

Wires can carry different colored signals, allowing for multiple independent circuits on the same wire network.

---

## Directional Data

### Vector3iDir

Combines position with direction:

```java
public class Vector3iDir {
    public Vector3i position;
    public int direction;  // 0=North, 1=East, 2=South, 3=West, 4=Up, 5=Down
}
```

Used for tracking signal entry/exit points on blocks.

---

## Wire Signal Propagation

### Algorithm Overview

1. **Input event** triggers on a block (lever, button, etc.)
2. **RegisterStateChange** propagates signal to connected wires
3. Each wire checks **GetValidEntrance** for connected blocks
4. Connected logic gates process inputs and produce outputs
5. **DisplayWireParticles** shows visual feedback
6. Continue until no more changes

### Data Structures

```java
// Track previous signal states
HashMap<Vector3iDir, LumenColors.COLOR> previousColors;

// Track component states at each position
HashMap<Vector3i, ArrayList<LumenColors.COLOR>[]> components;

// List of wires that need updating
List<TriggerWireData> triggeredWires;
```

---

## Custom Interactions

| Interaction | Purpose |
|-------------|---------|
| LumenChangeStateInteraction | Toggle signal on/off |
| LumenPlaceBlockInteraction | Place lumen block |
| LumenPlaceBlockConditionInteraction | Conditional placement |
| LumenInputInteraction | Input signal trigger |
| LumenChiselInteraction | Wire connection tool |
| LumenChiselInteraction_Off | Disconnect wires |

---

## File Structure

```
LumenChannelers-0.0.9.jar (412KB)
├── manifest.json
├── com/bunnir/plugin/
│   ├── LumenChannelers.java
│   ├── component/
│   │   ├── LumenWire.java
│   │   ├── LumenLogic.java
│   │   ├── LumenSystem.java
│   │   ├── LumenRotatable.java
│   │   ├── LumenDisplayBlock.java
│   │   └── LumenCollider.java
│   ├── interactions/
│   │   ├── LumenChangeStateInteraction.java
│   │   ├── LumenChiselInteraction.java
│   │   ├── LumenInputInteraction.java
│   │   └── LumenPlaceBlockInteraction.java
│   ├── jsonassets/
│   │   ├── LumenLogic.java
│   │   ├── LumenLogicInputs.java
│   │   ├── LumenLogicOutputs.java
│   │   └── LumenLogicType.java
│   ├── lumen/
│   │   ├── CircuitData.java
│   │   ├── TriggerWireData.java
│   │   ├── Vector3iDir.java
│   │   └── LumenColors.java
│   ├── system/
│   │   ├── LumenInitializer.java
│   │   ├── LumenBlockSystem.java
│   │   ├── LumenEntityInitializer.java
│   │   ├── LumenEntitySystem.java
│   │   └── LumenAbuseSystem.java
│   └── utils/
│       └── Utils.java
└── Server/
    └── [Block and item definitions]
```

---

## Key Patterns for HytaleVehicles

### 1. Multiple Component Types

```java
// Vehicle might need multiple components
vehiclePhysicsType = registry.registerComponent(VehiclePhysics.class, ...);
vehicleSeatType = registry.registerComponent(VehicleSeat.class, ...);
vehicleHealthType = registry.registerComponent(VehicleHealth.class, ...);
```

### 2. Multiple Systems

```java
// Separate systems for different concerns
registry.registerSystem(new VehiclePhysicsSystem());
registry.registerSystem(new VehicleSeatSystem());
registry.registerSystem(new VehicleDamageSystem());
```

### 3. Component Communication

```java
// Get related components
VehiclePhysics physics = chunk.getComponent(index, physicsType);
VehicleSeat seat = chunk.getComponent(index, seatType);

// Update based on seat input
if (seat.hasDriver()) {
    physics.applyThrottle(seat.getDriverInput());
}
```

---

## Summary

LumenChannelers demonstrates:
- **Custom ECS components** for both blocks and entities
- **Multiple system registration** for different update phases
- **Component type management** with getters
- **Signal propagation** through connected networks
- **Directional data** for input/output tracking
- **Particle effects** for visual feedback

This is a complex mod showing advanced Hytale ECS usage patterns.
