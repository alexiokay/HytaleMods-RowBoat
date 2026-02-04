# HytaleVehicles

A modular vehicle system for Hytale, inspired by Minecraft Transport Simulator (MTS).

## Quick Start

### Building
```bash
./gradlew.bat build
```
Output: `app/build/libs/HytaleVehicles-1.0.0.jar`

### Testing the Plugin

#### Option 1: IntelliJ IDEA (Recommended)
1. Open this project in IntelliJ IDEA
2. Let Gradle sync complete
3. Run the "HytaleServer" configuration (appears after import)
4. On first run, type `auth login device` in the console
5. Follow the link to authenticate with your Hytale account
6. Type `auth persistence Encrypted` to save login
7. Launch Hytale client and connect to `localhost`

#### Option 2: Manual Server
1. Download Hytale Server from https://hytale.com/server
2. Place `hytale-server.jar` in `app/run/`
3. Run: `./gradlew.bat :app:runServer`
4. Connect with Hytale client to `localhost`

#### Option 3: Copy to Existing Server
```bash
# Deploy JAR to run/mods
./gradlew.bat :app:deployPlugin

# Or manually copy
copy app\build\libs\HytaleVehicles-1.0.0.jar <your-server>\mods\
```

## In-Game Commands

Once connected, grant yourself operator:
```
/op self
```

Then use HytaleVehicles commands:
```
/hv spawn hyvehicles:simple_boat  - Spawn a boat
/hv list                           - List all vehicles
/hv types                          - List vehicle types
/hv info <vehicleId>               - Show vehicle info
/hv help                           - Show help
```

## For Content Pack Developers

Create a new Hytale plugin that depends on HytaleVehicles:

```java
// In your plugin:
VehicleAPI api = HytaleVehiclesPlugin.getAPI();

// Register vehicles from JSON
api.registerVehicle(myBoatDefinition);

// Or register custom types
api.registerVehicleCreator("HOVERCRAFT", new HovercraftCreator());
```

See [Project Overview](docs/PROJECT_OVERVIEW.md) for architecture details.

## Project Structure

```
HytaleVehicles/
├── app/src/main/java/com/alexispace/hyvehicles/
│   ├── api/           # Public API (VehicleAPI, VehicleTypeCreator)
│   ├── command/       # Command handlers (/hv)
│   ├── definition/    # JSON schema (VehicleDefinition)
│   ├── entity/        # Vehicle physics (BaseVehicle, WaterVehicle)
│   ├── loader/        # JSON loading
│   ├── registry/      # Type and instance management
│   └── util/          # Utilities (VehicleLogger)
└── app/src/main/resources/
    ├── manifest.json  # Plugin manifest
    └── vehicles/      # Example vehicle JSON files
```

## Requirements

- Java 25 (auto-downloaded by Gradle)
- Hytale account (for testing)
