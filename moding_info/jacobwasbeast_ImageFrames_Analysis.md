# Jacobwasbeast ImageFrames Mod Analysis

**Mod:** `ImageFrames-1.0.3.jar`
**Author:** Jacobwasbeast
**Version:** 1.0.3
**Architecture:** Pure Java

A mod that allows players to place image frames and display custom images from URLs on blocks in-game.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Key Components](#key-components)
4. [Custom UI System](#custom-ui-system)
5. [Event System](#event-system)
6. [Runtime Assets & Dynamic Block Creation](#runtime-assets--dynamic-block-creation)
7. [Image Caching System](#image-caching-system)
8. [Async Initialization Pattern](#async-initialization-pattern)
9. [Useful APIs Discovered](#useful-apis-discovered)
10. [Code Patterns & Techniques](#code-patterns--techniques)

---

## Overview

The ImageFrames mod demonstrates several advanced Hytale modding techniques:

- **Custom UI pages** for player configuration
- **Runtime asset generation** (creating blocks/textures dynamically)
- **Image downloading and caching** from web URLs
- **Event-driven systems** using EntityEventSystem
- **Async initialization** to avoid blocking server startup
- **Owner locking** with UUID-based permissions

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ImageFramesPlugin                            │
│  - Main entry point                                             │
│  - Registers systems, UI pages, event handlers                  │
└─────────────────────────────────────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌───────────────┐    ┌──────────────────┐    ┌────────────────┐
│ ImageFrame-   │    │  ImageFrame-     │    │  ImageFrame-   │
│ RuntimeManager│    │  Store           │    │  Config        │
│               │    │                  │    │                │
│ - Creates     │    │ - Persists frame │    │ - Settings     │
│   block tiles │    │   groups to disk │    │ - Owner lock   │
│ - Manages     │    │ - Position →     │    │                │
│   textures    │    │   group mapping  │    │                │
│ - Broadcasts  │    │                  │    │                │
│   assets      │    │                  │    │                │
└───────────────┘    └──────────────────┘    └────────────────┘
        │
        ▼
┌───────────────────┐
│ ImageFrameImage-  │
│ Cache             │
│                   │
│ - Downloads URLs  │
│ - Caches to disk  │
│ - Memory cache    │
└───────────────────┘
```

---

## Key Components

### Main Plugin Class

```java
public class ImageFramesPlugin extends JavaPlugin {
    private ImageFramesConfig config;
    private ImageFrameStore store;
    private ImageFrameRuntimeManager runtimeManager;

    @Override
    protected void setup() {
        // Register custom UI page codec
        getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
            .register("ImageFrames_Config",
                      ImageFrameConfigSupplier.class,
                      ImageFrameConfigSupplier.CODEC);

        // Initialize components
        this.config = new ImageFramesConfig();
        this.store = new ImageFrameStore();
        this.runtimeManager = new ImageFrameRuntimeManager(this, store);
    }

    @Override
    protected void start() {
        // Register ECS systems
        ComponentRegistryProxy registry = EntityModule.get().getEntityStoreRegistry();
        registry.registerSystem(new ImageFrameInteractionSystem(this));
        registry.registerSystem(new ImageFrameBreakSystem(this));

        // Register global event handler for player ready
        HytaleServer.get().getEventBus()
            .registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Start async initialization
        new Thread(this::initAsync).start();
    }
}
```

### Frame Store (Persistence)

The store manages frame groups - each group represents a multi-block image display:

```java
public class ImageFrameStore {
    public static class FrameGroup {
        public String groupId;          // Unique ID
        public String worldName;        // Which world
        public String imageUrl;         // Source image URL
        public String ownerUuid;        // Who placed it
        public List<Vector3i> blocks;   // Block positions
        public int width, height;       // Grid dimensions
        // ...
    }

    // Lookup frame by position
    public FrameGroup getGroupByPos(String worldName, Vector3i pos);

    // Persist to disk
    public void syncSave();
    public void syncLoad();
}
```

---

## Custom UI System

### Registering a Custom UI Page

Hytale has a built-in UI system. Mods can register custom page "suppliers":

```java
// In setup():
getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
    .register("ImageFrames_Config",              // Page type ID
              ImageFrameConfigSupplier.class,    // Supplier class
              ImageFrameConfigSupplier.CODEC);   // Codec for serialization
```

### Custom UI Page Implementation

```java
public class ImageFrameConfigPage extends CustomUIPage {
    private final ImageFramesPlugin plugin;
    private final PlayerRef player;
    private final Vector3i blockPos;

    public ImageFrameConfigPage(ImageFramesPlugin plugin, PlayerRef player,
                                Vector3i blockPos, InteractionContext context) {
        this.plugin = plugin;
        this.player = player;
        this.blockPos = blockPos;
    }

    // Called when page data changes (user input)
    @Override
    public void onPageDataUpdate(PageDataStore data) {
        String imageUrl = data.getString("imageUrl");
        int width = data.getInt("width");
        int height = data.getInt("height");

        // Apply configuration...
    }
}
```

### Opening a Custom UI Page

```java
// Get the player's page manager
PageManager pageManager = player.getPageManager();

// Open the custom page
pageManager.openCustomPage(
    playerRef,           // Entity reference
    store,               // ECS store
    new ImageFrameConfigPage(plugin, playerRef, blockPos, context)
);
```

---

## Event System

### Entity Event System

For handling block interactions, use `EntityEventSystem`:

```java
public class ImageFrameInteractionSystem
    extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    public ImageFrameInteractionSystem(ImageFramesPlugin plugin) {
        super(UseBlockEvent.Pre.class);  // Subscribe to this event type
        this.plugin = plugin;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                       UseBlockEvent.Pre event) {

        // Check interaction type
        if (event.getInteractionType() != InteractionType.Secondary &&
            event.getInteractionType() != InteractionType.Use) {
            return;
        }

        // Get block type
        BlockType blockType = event.getBlockType();
        String blockId = blockType.getId();

        // Check if it's our block
        if (!isFrameBlock(blockId)) return;

        // Get player components
        Ref<EntityStore> playerRef = chunk.getReferenceTo(entityIndex);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pRef = store.getComponent(playerRef, PlayerRef.getComponentType());

        // Get world
        World world = store.getExternalData().getWorld();

        // Open config UI
        player.getPageManager().openCustomPage(playerRef, store,
            new ImageFrameConfigPage(plugin, pRef, event.getTargetBlock(), event.getContext()));

        // Cancel default interaction
        event.setCancelled(true);
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Only run for entities with Player and PlayerRef components
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
    }
}
```

### Global Event Bus

For events not tied to specific entities:

```java
// Register a global event handler
HytaleServer.get().getEventBus()
    .registerGlobal(PlayerReadyEvent.class, event -> {
        // Called when any player becomes ready
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Refresh frames for this player's world
        runtimeManager.refreshFramesForWorld(world);
    });
```

### Common Event Types

| Event Class | When Fired |
|------------|------------|
| `UseBlockEvent.Pre` | Before block interaction |
| `UseBlockEvent.Post` | After block interaction |
| `PlaceBlockEvent` | When block is placed |
| `BreakBlockEvent` | When block is broken |
| `PlayerReadyEvent` | Player fully loaded |
| `PlayerJoinEvent` | Player joins server |

---

## Runtime Assets & Dynamic Block Creation

### Runtime Asset Pack Registration

The mod creates textures and blocks dynamically at runtime:

```java
public class ImageFrameRuntimeManager {
    private final Path runtimeAssetsPath;      // Dynamic assets location
    private final Path runtimeCommonBlocksPath; // Tile textures
    private final Path runtimeBlockTypesPath;   // Block type JSONs

    public void init() {
        // Create directories
        Files.createDirectories(runtimeCommonBlocksPath);
        Files.createDirectories(runtimeBlockTypesPath);

        // Register as asset pack
        registerRuntimeAssetsPack();

        // Load existing assets from disk
        loadExistingRuntimeAssets();

        // Rebuild from store if needed
        rebuildAssetsFromStore();
    }

    private void registerRuntimeAssetsPack() {
        // Register path as an asset pack that clients will receive
        AssetManager.registerRuntimePack("image_frames_assets", runtimeAssetsPath);
    }
}
```

### Creating Dynamic Textures

```java
// Download image from URL
BufferedImage sourceImage = imageCache.loadOrDownload(url, () -> {
    // Fallback: download from URL
    return ImageIO.read(new URL(url));
});

// Split into tiles for multi-block display
for (int ty = 0; ty < tilesY; ty++) {
    for (int tx = 0; tx < tilesX; tx++) {
        BufferedImage tile = sourceImage.getSubimage(
            tx * tileWidth, ty * tileHeight,
            tileWidth, tileHeight
        );

        // Save tile as texture
        Path tilePath = runtimeCommonBlocksPath.resolve("tile_" + groupId + "_" + tx + "_" + ty + ".png");
        ImageIO.write(tile, "png", tilePath.toFile());
    }
}
```

### Creating Dynamic Block Types

```java
// Create block type JSON for each tile
String blockJson = """
{
  "Material": "Solid",
  "DrawType": "Cube",
  "Textures": [{
    "All": "Blocks/ImageFrames/tiles/tile_%s_%d_%d.png",
    "Weight": 1
  }],
  "Flags": {"IsUsable": true},
  "Interactions": {"Use": "ImageFrames_Use"}
}
""".formatted(groupId, x, y);

Path blockPath = runtimeBlockTypesPath.resolve("tile_" + groupId + "_" + x + "_" + y + ".json");
Files.writeString(blockPath, blockJson);
```

### Broadcasting Assets to Clients

```java
public void broadcastRuntimeAssets() {
    // Query for asset updates
    AssetUpdateQuery query = AssetUpdateQuery.forPath("Blocks/ImageFrames/tiles/*");

    // Send to all connected players
    for (PlayerRef player : Universe.get().getPlayers()) {
        player.sendAssetUpdate(query);
    }
}
```

---

## Image Caching System

### Cache Architecture

```java
public class ImageFrameImageCache {
    private final Path cacheDir;              // Disk cache directory
    private final Path indexPath;             // Cache index JSON
    private final Map<String, CacheEntry> index;      // URL → file mapping
    private final Map<String, BufferedImage> memoryCache;  // In-memory cache

    public static class CacheEntry {
        public String fileName;
        public long timestamp;
    }
}
```

### Load or Download Pattern

```java
public synchronized BufferedImage loadOrDownload(String url,
    Supplier<BufferedImage> downloadFn) throws IOException {

    // 1. Check memory cache
    BufferedImage cached = memoryCache.get(url);
    if (cached != null) return cached;

    // 2. Check disk cache
    CacheEntry entry = index.get(url);
    if (entry != null && entry.fileName != null) {
        Path filePath = cacheDir.resolve(entry.fileName);
        if (Files.exists(filePath)) {
            BufferedImage img = ImageIO.read(filePath.toFile());
            if (img != null) {
                memoryCache.put(url, img);
                return img;
            }
        }
    }

    // 3. Download from web
    BufferedImage downloaded = downloadFn.get();
    if (downloaded == null) {
        throw new IOException("Failed to download image");
    }

    // 4. Store in cache
    store(url, downloaded);
    return downloaded;
}
```

### URL to Filename Conversion

```java
private String fileNameForUrl(String url) {
    // Hash the URL to create a safe filename
    String hash = DigestUtils.sha256Hex(url);
    return hash + ".png";
}
```

---

## Async Initialization Pattern

### Problem
Loading configs, downloading images, and creating assets can be slow. Blocking server startup is bad.

### Solution
Initialize in a background thread, then schedule final setup on the main thread:

```java
@Override
protected void start() {
    // Start async initialization
    if (initialized.compareAndSet(false, true)) {
        new Thread(() -> {
            try {
                // Slow operations in background
                config.syncLoad();
                store.syncLoad();
                runtimeManager.init();

                // Schedule final setup on main thread
                HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
                    runtimeManager.broadcastRuntimeAssets();
                    runtimeManager.refreshFramesForWorld(
                        Universe.get().getDefaultWorld()
                    );
                });
            } catch (Exception e) {
                getLogger().at(Level.SEVERE)
                    .withCause(e)
                    .log("Failed to initialize ImageFrames");
            }
        }).start();
    }
}
```

### Scheduled Executor Service

Hytale provides a thread pool for background tasks:

```java
// Run once
HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
    // Background task
});

// Run periodically
HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
    () -> validateGroupsIntegrity(),
    2,  // Initial delay
    2,  // Period
    TimeUnit.SECONDS
);
```

---

## Useful APIs Discovered

### Block Type Access

```java
// Get block type at position
BlockType blockType = world.getBlockType(Vector3i pos);
String blockId = blockType.getId();

// Check if block exists
if (blockType != null) {
    // Do something
}
```

### Chunk Access

```java
// Get chunk containing block
long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
WorldChunk chunk = world.getChunk(chunkIndex);

// Check if chunk is loaded
if (chunk != null) {
    // Safe to access
}
```

### Player References

```java
// From event or ECS query
PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
Player player = store.getComponent(entityRef, Player.getComponentType());

// Send message
playerRef.sendMessage(Message.raw("Hello!"));

// Get UUID
UUID uuid = playerRef.getUuid();

// Get world
World world = player.getWorld();
```

### World Execution

```java
// Run code on world thread (thread-safe)
world.execute(() -> {
    // Modify world state here
    world.setBlock(pos, blockType);
});
```

### Universe Access

```java
Universe universe = Universe.get();

// Get world by name
World world = universe.getWorld("world_name");

// Get default world
World defaultWorld = universe.getDefaultWorld();

// Get all players
Collection<PlayerRef> players = universe.getPlayers();
```

---

## Code Patterns & Techniques

### 1. Singleton Plugin Instance

```java
public class ImageFramesPlugin extends JavaPlugin {
    private static ImageFramesPlugin instance;

    public ImageFramesPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static ImageFramesPlugin getInstance() {
        return instance;
    }
}
```

### 2. AtomicBoolean for One-Time Init

```java
private final AtomicBoolean initialized = new AtomicBoolean(false);

public void maybeInit() {
    if (initialized.compareAndSet(false, true)) {
        // Only runs once, even if called from multiple threads
        doInit();
    }
}
```

### 3. Owner Lock Pattern

```java
// Check if player owns the frame
if (config.isOwnerLockEnabled() &&
    group.ownerUuid != null &&
    !group.ownerUuid.equals(playerRef.getUuid().toString())) {

    playerRef.sendMessage(Message.raw("This frame is locked by another player."));
    return;
}
```

### 4. Integrity Checks with Scheduled Tasks

```java
public void startIntegrityChecks(long intervalSeconds) {
    if (intervalSeconds <= 0) return;
    if (!integrityCheckStarted.compareAndSet(false, true)) return;

    HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
        () -> validateGroupsIntegrity(),
        intervalSeconds,
        intervalSeconds,
        TimeUnit.SECONDS
    );
}

private void validateGroupsIntegrity() {
    // Check each frame group
    for (FrameGroup group : store.getGroupsSnapshot().values()) {
        World world = Universe.get().getWorld(group.worldName);
        if (world == null) continue;

        // Execute validation on world thread
        world.execute(() -> validateGroupInWorld(world, group));
    }
}
```

### 5. Graceful Block Validation

```java
private void validateGroupInWorld(World world, FrameGroup group) {
    Vector3i invalidBlock = null;

    for (Vector3i blockPos : group.blocks) {
        // Check if chunk is loaded
        long chunkIdx = ChunkUtil.indexChunkFromBlock(blockPos.getX(), blockPos.getZ());
        if (world.getChunk(chunkIdx) == null) return;  // Can't validate yet

        // Check block type
        BlockType blockType = world.getBlockType(blockPos);
        String blockId = (blockType != null) ? blockType.getId() : null;

        if (!isFrameBlockId(blockId)) {
            invalidBlock = blockPos;
            break;
        }
    }

    // If a block was broken, clean up the entire group
    if (invalidBlock != null) {
        dropGroupItems(world, group, invalidBlock);
        removeGroupAndAssets(world, group);
    }
}
```

---

## Summary: Key Takeaways

1. **Custom UI Pages** - Register via `OpenCustomUIInteraction.PAGE_CODEC`, implement `CustomUIPage`
2. **EntityEventSystem** - Subscribe to ECS events like `UseBlockEvent.Pre`
3. **Global EventBus** - `HytaleServer.get().getEventBus()` for non-entity events
4. **Runtime Assets** - Create textures/blocks dynamically, broadcast to clients
5. **Image Caching** - Multi-layer cache (memory → disk → web)
6. **Async Init** - Background thread + `SCHEDULED_EXECUTOR` for main thread callback
7. **Owner Permissions** - Store UUID with data, check on interaction
8. **Integrity Checks** - Scheduled validation to handle broken/modified blocks
9. **World.execute()** - Thread-safe world modifications

---

## Differences from ConveyorBelt Mod

| Aspect | ConveyorBelt | ImageFrames |
|--------|--------------|-------------|
| **Language** | JavaScript (GraalVM) | Pure Java |
| **Tick System** | Custom ISystem tick | Event-driven |
| **UI** | None | Custom UI pages |
| **Assets** | Static (bundled) | Dynamic (runtime) |
| **Networking** | None | Asset broadcasting |
| **State** | In-memory per-world | Persisted to disk |

---

*Last updated: February 2026*
*Based on analysis of ImageFrames v1.0.3*
