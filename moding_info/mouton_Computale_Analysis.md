# Computale Mod Analysis

**Mod Name:** Computale
**Version:** 0.2.0
**Author:** Mouton
**Architecture:** Pure Java Plugin with LuaJ
**Inspiration:** ComputerCraft (Minecraft)

## Overview

Computale brings programmable computers to Hytale, inspired by the famous ComputerCraft mod. Players can write and execute Lua scripts, interact with the world through APIs, and build automated systems.

## Key Features

- **Lua Programming** - Full Lua 5.2 scripting via LuaJ library
- **Virtual Filesystem** - Persistent file storage per computer
- **Interactive REPL** - Command-line interface for code execution
- **Peripheral API** - Interact with adjacent blocks/containers
- **Custom UI** - Terminal display rendered in-game

---

## Architecture

### Plugin Structure

```
Computale-0.2.0.jar
├── manifest.json
├── org/luaj/vm2/              # Bundled LuaJ library
│   ├── Globals.java
│   ├── LuaValue.java
│   └── ...
├── com/mouton/computale/
│   ├── ComputalePlugin.java       # Main plugin entry
│   ├── ComputerBlock.java         # Computer block definition
│   ├── ComputerLuaREPLLogic.java  # Lua interpreter wrapper
│   ├── ComputerFileSystem.java    # Virtual filesystem
│   ├── OpenComputerInteraction.java # Item interaction
│   └── api/
│       ├── OSApi.java             # OS functions (sleep, time)
│       ├── FSApi.java             # Filesystem functions
│       ├── PeripheralApi.java     # World interaction
│       └── RedstoneApi.java       # Redstone signals
└── Server/
    └── Item/Items/
        └── computer.json
```

---

## LuaJ Integration

### Embedding Lua in Java

```java
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

public class ComputerLuaREPLLogic {
    private final Globals luaGlobals;
    private final ComputerFileSystem fileSystem;
    private final StringBuilder outputBuffer = new StringBuilder();

    public ComputerLuaREPLLogic(UUID computerId) {
        // Create sandboxed Lua environment
        this.luaGlobals = JsePlatform.standardGlobals();

        // Initialize virtual filesystem
        this.fileSystem = new ComputerFileSystem(computerId);

        // Register custom APIs
        registerAPIs();

        // Redirect print() to capture output
        luaGlobals.set("print", new PrintFunction(this::appendOutput));
    }

    private void registerAPIs() {
        // OS API - sleep, time, etc.
        luaGlobals.set("os", new OSApi(this));

        // Filesystem API - read/write files
        luaGlobals.set("fs", new FSApi(fileSystem));

        // Peripheral API - interact with world
        luaGlobals.set("peripheral", new PeripheralApi(this));

        // Remove dangerous functions (sandboxing)
        luaGlobals.set("io", LuaValue.NIL);
        luaGlobals.set("os.execute", LuaValue.NIL);
        luaGlobals.set("loadfile", LuaValue.NIL);
    }

    public String executeCode(String luaCode) {
        outputBuffer.setLength(0);
        try {
            LuaValue chunk = luaGlobals.load(luaCode);
            LuaValue result = chunk.call();
            return outputBuffer.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

---

## Virtual Filesystem

### Per-Computer Storage

```java
public class ComputerFileSystem {
    private final Path rootPath;
    private final UUID computerId;

    public ComputerFileSystem(UUID computerId) {
        this.computerId = computerId;
        // Each computer gets isolated storage
        this.rootPath = Paths.get("computers", computerId.toString());
        ensureDirectoryExists(rootPath);
    }

    public String readFile(String path) throws IOException {
        Path filePath = resolveSafePath(path);
        return Files.readString(filePath);
    }

    public void writeFile(String path, String content) throws IOException {
        Path filePath = resolveSafePath(path);
        Files.writeString(filePath, content);
    }

    public List<String> listDirectory(String path) throws IOException {
        Path dirPath = resolveSafePath(path);
        return Files.list(dirPath)
            .map(p -> p.getFileName().toString())
            .collect(Collectors.toList());
    }

    // Prevent path traversal attacks
    private Path resolveSafePath(String userPath) throws SecurityException {
        Path resolved = rootPath.resolve(userPath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new SecurityException("Path traversal detected: " + userPath);
        }
        return resolved;
    }
}
```

### Lua FS API

```java
public class FSApi extends LuaTable {
    private final ComputerFileSystem fs;

    public FSApi(ComputerFileSystem fs) {
        this.fs = fs;

        // fs.open(path, mode)
        set("open", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String path = args.checkjstring(1);
                String mode = args.optjstring(2, "r");
                return openFile(path, mode);
            }
        });

        // fs.list(path)
        set("list", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String path = arg.checkjstring();
                try {
                    List<String> files = fs.listDirectory(path);
                    LuaTable result = new LuaTable();
                    for (int i = 0; i < files.size(); i++) {
                        result.set(i + 1, files.get(i));
                    }
                    return result;
                } catch (IOException e) {
                    return LuaValue.NIL;
                }
            }
        });

        // fs.exists(path)
        set("exists", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(fs.exists(arg.checkjstring()));
            }
        });
    }
}
```

---

## Custom Interaction for Opening Computer

### OpenComputerInteraction

```java
public class OpenComputerInteraction extends SimpleInstantInteraction {
    private String computerId;

    public static final BuilderCodec<OpenComputerInteraction> CODEC = BuilderCodec.builder(
        OpenComputerInteraction.class,
        OpenComputerInteraction::new,
        SimpleInstantInteraction.CODEC
    )
    .with(
        Codecs.STRING.fieldOf("ComputerId").optional(),
        OpenComputerInteraction::getComputerId,
        (builder, id) -> builder.computerId = id
    )
    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldown) {
        PlayerRef player = context.getPlayer();
        if (player == null) return;

        // Get computer at target position
        Vector3i targetPos = context.getTargetBlockPosition();
        ComputerBlock computer = ComputalePlugin.getComputer(targetPos);

        if (computer != null) {
            // Open terminal UI for this player
            ComputalePlugin.openTerminalUI(player, computer);
        }
    }
}
```

### Registration

```java
@Override
public void setup() {
    // Register custom interaction type
    getCodecRegistry(Interaction.CODEC).register(
        "computale_open_computer",
        OpenComputerInteraction.class,
        OpenComputerInteraction.CODEC
    );

    // Register commands
    getCommandRegistry().registerCommand(new LuaExecCommand());
}
```

---

## Terminal UI System

### Custom UI Page for Terminal

```java
public class ComputerTerminalPage extends CustomUIPage {
    private final ComputerLuaREPLLogic repl;
    private final List<String> history = new ArrayList<>();
    private String currentInput = "";

    @Override
    protected void onKeyPressed(String key) {
        if (key.equals("Enter")) {
            // Execute current input
            String output = repl.executeCode(currentInput);
            history.add("> " + currentInput);
            if (!output.isEmpty()) {
                history.add(output);
            }
            currentInput = "";
            refreshDisplay();
        } else if (key.equals("Backspace")) {
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
            }
        } else if (key.length() == 1) {
            currentInput += key;
        }
        updateInputLine();
    }

    private void refreshDisplay() {
        // Send terminal content to client
        // Last N lines + current input
        List<String> visibleLines = history.subList(
            Math.max(0, history.size() - 20),
            history.size()
        );
        // ... send to client UI
    }
}
```

---

## Peripheral API (World Interaction)

### Interacting with Adjacent Blocks

```java
public class PeripheralApi extends LuaTable {
    private final ComputerBlock computer;
    private final World world;

    public PeripheralApi(ComputerBlock computer) {
        this.computer = computer;
        this.world = computer.getWorld();

        // peripheral.getType(side)
        set("getType", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String side = arg.checkjstring();
                Vector3i pos = getAdjacentPos(side);
                BlockType block = world.getBlockType(pos);

                if (isInventory(block)) return LuaValue.valueOf("inventory");
                if (isComputer(block)) return LuaValue.valueOf("computer");
                return LuaValue.NIL;
            }
        });

        // peripheral.call(side, method, ...)
        set("call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String side = args.checkjstring(1);
                String method = args.checkjstring(2);
                Vector3i pos = getAdjacentPos(side);

                return callPeripheralMethod(pos, method, args.subargs(3));
            }
        });
    }

    private Vector3i getAdjacentPos(String side) {
        Vector3i pos = computer.getPosition();
        switch (side.toLowerCase()) {
            case "top":    return pos.add(0, 1, 0);
            case "bottom": return pos.add(0, -1, 0);
            case "front":  return pos.add(computer.getFacing());
            case "back":   return pos.subtract(computer.getFacing());
            case "left":   return pos.add(computer.getFacing().rotateY90());
            case "right":  return pos.subtract(computer.getFacing().rotateY90());
            default:       return pos;
        }
    }

    private Varargs callPeripheralMethod(Vector3i pos, String method, Varargs args) {
        BlockState state = world.getBlockState(pos);

        if (state instanceof ItemContainerHolder) {
            ItemContainer container = ((ItemContainerHolder) state).getItemContainer();
            return handleInventoryMethod(container, method, args);
        }

        return LuaValue.NIL;
    }

    private Varargs handleInventoryMethod(ItemContainer inv, String method, Varargs args) {
        switch (method) {
            case "size":
                return LuaValue.valueOf(inv.getCapacity());

            case "getItemDetail":
                int slot = args.checkint(1) - 1; // Lua is 1-indexed
                ItemStack stack = inv.getItemStack(slot);
                if (stack == null || stack.isEmpty()) return LuaValue.NIL;

                LuaTable details = new LuaTable();
                details.set("name", stack.getItem().getId());
                details.set("count", stack.getQuantity());
                return details;

            case "pushItems":
                // Transfer items to adjacent inventory
                // ...
                break;
        }
        return LuaValue.NIL;
    }
}
```

---

## Example Lua Scripts

### Hello World
```lua
print("Hello, Hytale!")
```

### Reading Adjacent Inventory
```lua
local inv = peripheral.wrap("front")
if inv then
    local size = inv.size()
    print("Inventory has " .. size .. " slots")

    for i = 1, size do
        local item = inv.getItemDetail(i)
        if item then
            print("Slot " .. i .. ": " .. item.name .. " x" .. item.count)
        end
    end
end
```

### File Operations
```lua
-- Write to file
local file = fs.open("mydata.txt", "w")
file.write("Hello from Lua!")
file.close()

-- Read from file
local file = fs.open("mydata.txt", "r")
local content = file.readAll()
file.close()
print(content)
```

### Simple Timer
```lua
while true do
    print("Tick: " .. os.time())
    os.sleep(1)
end
```

---

## Key Hytale APIs Used

### Custom UI Pages
```java
// Registering custom UI page
getCodecRegistry(CustomUIPage.CODEC).register(
    "computale_terminal",
    ComputerTerminalPage.class,
    ComputerTerminalPage.CODEC
);
```

### Block State Storage
```java
// Storing computer data in block state
public class ComputerBlockState extends BlockState {
    private UUID computerId;
    private ComputerLuaREPLLogic repl;

    // Persisted across world saves
    @Override
    public void serialize(DataOutput out) throws IOException {
        out.writeUTF(computerId.toString());
    }

    @Override
    public void deserialize(DataInput in) throws IOException {
        computerId = UUID.fromString(in.readUTF());
        repl = new ComputerLuaREPLLogic(computerId);
    }
}
```

---

## Techniques Learned

### 1. Embedding Script Languages
LuaJ provides full Lua interpreter in pure Java - no native code needed.

### 2. Sandboxing
Remove dangerous functions (`io`, `os.execute`, `loadfile`) to prevent exploits.

### 3. Path Traversal Prevention
Always validate user-provided paths don't escape the sandbox directory.

### 4. Custom UI Pages
Hytale supports full custom UI with keyboard input handling.

### 5. Peripheral Pattern
Abstract world interaction behind a simple API (ComputerCraft's design).

---

## Relevance to HytaleVehicles

| Computale Pattern | HytaleVehicles Application |
|-------------------|---------------------------|
| Custom UI pages | Vehicle control panel / dashboard |
| Block state storage | Persisting vehicle data |
| Peripheral API | Vehicle interacting with nearby blocks |
| Custom interactions | Mounting/dismounting vehicles |

---

## Dependencies

The mod bundles LuaJ library (`org.luaj.vm2.*`) inside the JAR. This works because:
- LuaJ is pure Java (no native code)
- The classes are at standard package paths
- Hytale's classloader can see bundled classes in the plugin JAR

**Note:** This approach may not work for all libraries, especially those requiring native code or reflection-heavy frameworks.

---

## Summary

Computale demonstrates:
- Embedding a scripting language (LuaJ) in a Hytale plugin
- Creating sandboxed execution environments
- Virtual filesystem with security
- Custom UI pages with keyboard input
- Peripheral/world interaction APIs
- Block state persistence

The mod shows how to bring complex functionality like programmable computers to Hytale while maintaining security through sandboxing.
