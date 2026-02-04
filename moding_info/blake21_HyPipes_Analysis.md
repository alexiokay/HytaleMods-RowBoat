# HyPipes Mod Analysis

**Mod Name:** HyPipes
**Version:** 1.2.1
**Author:** blake21
**Architecture:** Pure Java Plugin

## Overview

HyPipes is a logistics mod that implements pipe networks for automated item transfer between containers. It features input/output pipe modes, item filtering, and multiple routing strategies.

## Key Features

- **Pipe Networks** - Automated item transfer between connected containers
- **Filter GUI** - Configure which items can flow through pipes
- **Priority System** - Control item routing with configurable priorities
- **Distribution Strategies** - Round-robin, nearest-first, and random distribution

---

## Architecture

### Plugin Structure

```
HyPipes-1.2.1.jar
├── manifest.json
├── com/blake21/hypipes/
│   ├── PipePlugin.java          # Main plugin entry point
│   ├── PipeNetworkManager.java  # Core network logic
│   ├── PipeNode.java            # Individual pipe definition
│   ├── FilterConfigGui.java     # Filter UI
│   └── ...
└── Server/
    └── Item/
        └── Items/
            ├── pipe_input.json
            ├── pipe_output.json
            └── pipe_filter.json
```

### Thread Safety Pattern

The mod uses `ConcurrentHashMap` for thread-safe pipe registry:

```java
public class PipeNetworkManager {
    // Thread-safe collections for multi-threaded ECS access
    private final ConcurrentHashMap<Long, PipeNode> pipeRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PipeNetwork> activeNetworks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ItemContainer> inventoryCache = new ConcurrentHashMap<>();

    // Scheduled executor for periodic transfers
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void start() {
        // Run item transfer every 500ms (2 times per second)
        scheduler.scheduleAtFixedRate(this::processTransfers, 0, 500, TimeUnit.MILLISECONDS);
    }
}
```

---

## Pipe System Design

### PipeNode Structure

```java
public class PipeNode {
    private final Vector3i position;
    private final PipeMode mode;           // INPUT or OUTPUT
    private final RoutingStrategy strategy; // ROUND_ROBIN, NEAREST, RANDOM
    private final int priority;            // Higher = processed first
    private final ItemFilter filter;       // Optional item filtering

    public enum PipeMode {
        INPUT,   // Pulls items FROM containers
        OUTPUT   // Pushes items TO containers
    }

    public enum RoutingStrategy {
        ROUND_ROBIN,  // Distribute evenly
        NEAREST,      // Closest output first
        RANDOM        // Random selection
    }
}
```

### Network Discovery

Pipes auto-connect to adjacent containers using block scanning:

```java
private void discoverNetwork(World world, Vector3i startPos) {
    Set<Vector3i> visited = new HashSet<>();
    Queue<Vector3i> toVisit = new LinkedList<>();
    toVisit.add(startPos);

    while (!toVisit.isEmpty()) {
        Vector3i pos = toVisit.poll();
        if (visited.contains(pos)) continue;
        visited.add(pos);

        // Check all 6 directions
        for (Direction dir : Direction.values()) {
            Vector3i neighborPos = pos.add(dir.getOffset());

            // Check if neighbor is a pipe or container
            BlockType block = world.getBlockType(neighborPos);
            if (isPipe(block)) {
                toVisit.add(neighborPos);
            } else if (hasContainer(world, neighborPos)) {
                // Register as source/destination based on pipe mode
                registerContainerConnection(neighborPos);
            }
        }
    }
}
```

---

## Item Transfer Logic

### Transfer Process

```java
private void processTransfers() {
    for (PipeNetwork network : activeNetworks.values()) {
        // Sort inputs by priority (descending)
        List<PipeNode> inputs = network.getInputs().stream()
            .sorted(Comparator.comparingInt(PipeNode::getPriority).reversed())
            .collect(Collectors.toList());

        for (PipeNode input : inputs) {
            ItemContainer source = getContainer(input.getPosition());
            if (source == null || source.isEmpty()) continue;

            // Find valid output based on strategy
            PipeNode output = selectOutput(network, input);
            if (output == null) continue;

            ItemContainer dest = getContainer(output.getPosition());
            if (dest == null) continue;

            // Transfer items
            transferItems(source, dest, input.getFilter());
        }
    }
}

private void transferItems(ItemContainer source, ItemContainer dest, ItemFilter filter) {
    for (int slot = 0; slot < source.getCapacity(); slot++) {
        ItemStack stack = source.getItemStack(slot);
        if (stack == null || stack.isEmpty()) continue;

        // Apply filter
        if (filter != null && !filter.accepts(stack)) continue;

        // Extract one item
        ItemContainerTransaction extract = source.removeItemStackFromSlot(slot, 1);
        if (!extract.succeeded()) continue;

        // Try to insert into destination
        ItemStack toInsert = extract.getOutput();
        for (int destSlot = 0; destSlot < dest.getCapacity(); destSlot++) {
            ItemContainerTransaction insert = dest.addItemStackToSlot(destSlot, toInsert);
            if (insert.succeeded()) {
                ItemStack remainder = insert.getRemainder();
                if (remainder == null || remainder.isEmpty()) {
                    return; // Successfully transferred
                }
                toInsert = remainder;
            }
        }

        // If failed to insert, put item back
        source.addItemStackToSlot(slot, toInsert);
    }
}
```

---

## Filter Configuration GUI

### Custom UI Page Pattern

```java
public class FilterConfigGui extends CustomUIPage {
    private final PipeNode pipe;
    private final List<ItemStack> allowedItems = new ArrayList<>();

    public static final BuilderCodec<FilterConfigGui> CODEC = BuilderCodec.builder(
        FilterConfigGui.class,
        FilterConfigGui::new
    )
    .with(
        Codecs.VECTOR3I.fieldOf("PipePosition").required(),
        FilterConfigGui::getPipePosition,
        (builder, pos) -> builder.pipePosition = pos
    )
    .build();

    @Override
    protected void onOpen(PlayerRef player) {
        // Send current filter state to client
        refreshFilterDisplay();
    }

    @Override
    protected void onItemSlotClicked(int slot, ItemStack clickedWith) {
        if (clickedWith != null && !clickedWith.isEmpty()) {
            // Add item type to filter
            addToFilter(clickedWith.getItem());
        } else {
            // Remove from filter
            removeFromFilter(slot);
        }
    }
}
```

---

## Key Hytale APIs Used

### Container System

```java
// Getting container from block state
BlockState blockState = chunk.getState(localX, y, localZ);
if (blockState instanceof ItemContainerHolder) {
    ItemContainer container = ((ItemContainerHolder) blockState).getItemContainer();
}

// Transaction-based operations
ItemContainerTransaction tx = container.removeItemStackFromSlot(slot, count);
if (tx.succeeded()) {
    ItemStack removed = tx.getOutput();
}

tx = container.addItemStackToSlot(slot, stack);
if (tx.succeeded()) {
    ItemStack remainder = tx.getRemainder();
}
```

### Block Position Utilities

```java
// Chunk-relative coordinates
int localX = worldX & 31;  // Equivalent to worldX % 32
int localZ = worldZ & 31;

// Chunk index from world position
long chunkIdx = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
WorldChunk chunk = world.getChunkIfInMemory(chunkIdx);
```

---

## Techniques Learned

### 1. Scheduled Periodic Tasks
Use `ScheduledExecutorService` for regular updates instead of relying on tick events:
```java
scheduler.scheduleAtFixedRate(this::processTransfers, 0, 500, TimeUnit.MILLISECONDS);
```

### 2. Priority-Based Processing
Sort by priority before processing to ensure important transfers happen first.

### 3. Transaction Pattern
Always use transactions for container operations - they're atomic and safe.

### 4. Network Caching
Cache pipe networks and invalidate on block changes instead of recalculating every tick.

### 5. Thread-Safe Collections
Use `ConcurrentHashMap` when data is accessed from multiple threads (ECS systems, scheduled tasks).

---

## Relevance to HytaleVehicles

While pipes are different from vehicles, some patterns are useful:

| HyPipes Pattern | HytaleVehicles Application |
|-----------------|---------------------------|
| Scheduled tasks | Regular vehicle physics updates |
| Network caching | Caching nearby vehicles for collision |
| Priority system | Seat selection priority |
| Thread-safe collections | Managing active vehicle instances |

---

## File Structure Reference

```
Server/
├── Item/
│   └── Items/
│       ├── pipe_input.json       # Input pipe item
│       ├── pipe_output.json      # Output pipe item
│       └── pipe_filter.json      # Filter configuration item
└── Block/
    └── Blocks/
        ├── pipe_straight.json    # Straight pipe block
        ├── pipe_corner.json      # Corner pipe block
        └── pipe_junction.json    # T-junction pipe block
```

---

## Summary

HyPipes demonstrates:
- Pure Java plugin architecture
- Thread-safe concurrent data structures
- Scheduled background task execution
- Transaction-based inventory manipulation
- Network/graph algorithms for connected blocks
- Custom UI for configuration

The mod is well-architected for performance with caching and scheduled updates rather than per-tick processing.
