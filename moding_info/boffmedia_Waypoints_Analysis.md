# Waypoints - Deep Analysis

**Mod Name:** Waypoints
**Version:** 1.4.0
**Author:** Luisca (Boffmedia)
**Architecture:** Java Plugin with Asset Pack

## Overview

A waypoint management system with custom UI pages for creating, editing, and teleporting to waypoints. Stores waypoints in player's world data using Hytale's built-in map marker system.

---

## File Structure

```
Waypoints-1.4.0.jar
├── manifest.json
├── es/boffmedia/waypoints/
│   ├── Waypoints.java                 # Main plugin
│   ├── Constants.java
│   ├── Icon.java / Icons.java / IconNames.java
│   ├── commands/waypoints/
│   │   ├── WaypointCommand.java
│   │   ├── AddWaypointCommand.java
│   │   ├── ListWaypointsCommand.java
│   │   ├── RemoveWaypointCommand.java
│   │   ├── ResetWaypointsCommand.java
│   │   └── WaypointTeleportCommand.java
│   ├── pages/
│   │   ├── WaypointPage.java          # Main waypoint list
│   │   ├── AddWaypointPage.java       # Create waypoint
│   │   ├── EditWaypointPage.java      # Edit waypoint
│   │   └── IconPickerPage.java        # Choose icon
│   ├── config/
│   │   └── WaypointsConfig.java
│   └── util/
│       ├── PermissionsUtil.java
│       └── UIHelpers.java
└── Common/UI/Custom/
    ├── Pages/
    │   ├── WaypointPage.ui
    │   ├── AddWaypointPage.ui
    │   ├── EditWaypointPage.ui
    │   ├── IconPickerPage.ui
    │   ├── WaypointItem.ui
    │   └── Icons/*.ui
    └── Markers/
        ├── Campfire.png, Coordinate.png, Death.png
        ├── Home.png, Player.png, Portal.png
        └── ...
```

---

## Plugin Initialization

### Waypoints.java

```java
public class Waypoints extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Config<WaypointsConfig> config;

    public Waypoints(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from " + getName() + " version " +
            getManifest().getVersion().toString());

        // Load config using Hytale's config system
        this.config = withConfig("waypoints_config", WaypointsConfig.CODEC);
    }

    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + getName());
        config.save();

        // Register commands
        getCommandRegistry().registerCommand(new WaypointCommand(config));
        getCommandRegistry().registerCommand(new ResetWaypointsCommand());
        getCommandRegistry().registerCommand(new ListWaypointsCommand());
        getCommandRegistry().registerCommand(new WaypointTeleportCommand());
    }

    public Config<WaypointsConfig> getConfig() {
        return config;
    }
}
```

---

## Interactive UI Pages

### WaypointPage.java

```java
public class WaypointPage extends InteractiveCustomUIPage<WaypointPageData> {
    private final MapMarker[] waypoints;
    private final Config<WaypointsConfig> config;

    public WaypointPage(@Nonnull PlayerRef playerRef, MapMarker[] waypoints,
                        Config<WaypointsConfig> config) {
        super(playerRef, CustomPageLifetime.CanDismiss, WaypointPageData.CODEC);
        this.waypoints = waypoints;
        this.config = config;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        // Load main UI template
        uiCommandBuilder.append("Pages/WaypointPage.ui");
        uiCommandBuilder.clear("#WaypointsList");

        // Register search event
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            new EventData().append("Action", "Search").append("@Query", "#SearchInput.Value"),
            false);

        // Get player position for distance calculation
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(player.getReference(),
            TransformComponent.getComponentType());
        Position playerPosition = transform.getSentTransform().position;

        // Sort waypoints by distance
        List<WaypointWithDistance> waypointsWithDistance = new ArrayList<>();
        for (MapMarker waypoint : waypoints) {
            double distance = calculateDistance(playerPosition, waypoint.transform.position);
            waypointsWithDistance.add(new WaypointWithDistance(waypoint, distance));
        }
        waypointsWithDistance.sort(Comparator.comparingDouble(w -> w.distance));

        // Build list items
        int i = 0;
        for (WaypointWithDistance waypointData : waypointsWithDistance) {
            String selector = "#WaypointsList[" + i + "]";

            // Append item template
            uiCommandBuilder.append("#WaypointsList", "Pages/WaypointItem.ui");

            // Set item data
            uiCommandBuilder.set(selector + " #WaypointName.Text", waypointData.waypoint.name);
            uiCommandBuilder.set(selector + " #WaypointCoordinates.Text",
                String.format("X: %.0f  Y: %.0f  Z: %.0f  -  %.1f blocks away",
                    waypointData.waypoint.transform.position.x,
                    waypointData.waypoint.transform.position.y,
                    waypointData.waypoint.transform.position.z,
                    waypointData.distance));

            // Add icon
            String iconUiPath = IconNames.resolveIconUiPath(waypointData.waypoint.markerImage);
            uiCommandBuilder.append(selector + " #IconContainer", iconUiPath);

            // Set button visibility based on permissions
            boolean canTeleport = PermissionsUtil.canTeleport(player);
            uiCommandBuilder.set(selector + " #TeleportButton.Visible", canTeleport);

            // Register button events
            if (canTeleport) {
                uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    selector + " #TeleportButton",
                    new EventData().append("Action", "Teleport")
                                  .append("WaypointId", waypointData.waypoint.id),
                    false);
            }
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                selector + " #EditButton",
                new EventData().append("Action", "Edit")
                              .append("WaypointId", waypointData.waypoint.id),
                false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                selector + " #RemoveButton",
                new EventData().append("Action", "Remove")
                              .append("WaypointId", waypointData.waypoint.id),
                false);

            i++;
        }

        // Create button
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#CreateWaypointButton", new EventData().append("Action", "Create"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#CloseButton", new EventData().append("Action", "Close"), false);
    }
}
```

---

## Event Handling

### handleDataEvent

```java
@Override
public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                            @Nonnull WaypointPageData data) {
    Player player = store.getComponent(ref, Player.getComponentType());

    switch (data.action) {
        case "Teleport": {
            // Find waypoint by ID
            String worldName = player.getWorld().getName();
            PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(worldName);
            MapMarker[] markers = perWorldData.getWorldMapMarkers();

            MapMarker waypoint = findWaypointById(markers, data.waypointId);
            if (waypoint == null) {
                player.sendMessage(Message.raw("No waypoint was found with that ID."));
                break;
            }

            // Teleport using Teleport component
            Vector3d targetPos = new Vector3d(
                waypoint.transform.position.x,
                waypoint.transform.position.y,
                waypoint.transform.position.z);
            Vector3f rotation = new Vector3f(0f, 0f, 0f);
            Teleport teleport = new Teleport(targetPos, rotation);
            store.addComponent(ref, Teleport.getComponentType(), teleport);

            player.sendMessage(Message.raw("Teleported to '" + waypoint.name + "'!"));
            break;
        }

        case "Edit": {
            MapMarker waypointToEdit = findWaypointById(markers, data.waypointId);
            player.getPageManager().openCustomPage(ref, store,
                new EditWaypointPage(playerRef, waypointToEdit, config));
            break;
        }

        case "Remove": {
            // Filter out the waypoint to remove
            List<MapMarker> updatedMarkers = new ArrayList<>();
            for (MapMarker marker : markers) {
                if (!marker.id.equals(data.waypointId)) {
                    updatedMarkers.add(marker);
                }
            }
            perWorldData.setWorldMapMarkers(updatedMarkers.toArray(new MapMarker[0]));
            player.sendMessage(Message.raw("Waypoint removed successfully."));

            // Refresh the page
            player.getPageManager().openCustomPage(ref, store,
                new WaypointPage(playerRef, updatedMarkers.toArray(new MapMarker[0]), config));
            break;
        }

        case "Create": {
            player.getPageManager().openCustomPage(ref, store,
                new AddWaypointPage(playerRef, config));
            break;
        }

        case "Search": {
            String query = data.query.trim().toLowerCase();
            List<MapMarker> filtered = new ArrayList<>();
            for (MapMarker m : waypoints) {
                if (m.name.toLowerCase().contains(query)) {
                    filtered.add(m);
                }
            }
            refreshWaypoints(ref, store, filtered.toArray(new MapMarker[0]), query);
            break;
        }

        case "Close": {
            close();
            break;
        }
    }
}
```

---

## Page Data Codec

```java
public static class WaypointPageData {
    public String action;
    public String waypointId;
    public String query;

    public static final BuilderCodec<WaypointPageData> CODEC = BuilderCodec.builder(
        WaypointPageData.class, WaypointPageData::new
    )
    .append(new KeyedCodec("Action", Codec.STRING),
        (o, v) -> o.action = v, o -> o.action).add()
    .append(new KeyedCodec("WaypointId", Codec.STRING),
        (o, v) -> o.waypointId = v, o -> o.waypointId).add()
    .append(new KeyedCodec("@Query", Codec.STRING),
        (o, v) -> o.query = v, o -> o.query).add()
    .build();
}
```

---

## UI Files

### WaypointPage.ui

```
$C = "../Common.ui";

$C.@PageOverlay {
    $C.@DecoratedContainer {
        @CloseButton = true;
        Anchor: (Width: 650, Height: 450);

        #Title { $C.@Title { @Text = "WAYPOINTS"; } }

        #Content {
            LayoutMode: Top;
            Padding: (Full: 24);

            // Search input
            Group {
                LayoutMode: Left;
                Anchor: (Height: 44);

                $C.@TextField #SearchInput {
                    FlexWeight: 1;
                    Anchor: (Height: 40);
                    Background: #0f1621;
                    PlaceholderText: "Search waypoints...";
                }
            }

            // Waypoint list (scrollable)
            Group #WaypointsList {
                FlexWeight: 1;
                LayoutMode: TopScrolling;
                Background: #0f1621(0.7);
                Padding: (Full: 8);
                ScrollbarStyle: $C.@DefaultScrollbarStyle;
            }

            // Create button
            TextButton #CreateWaypointButton {
                Text: "CREATE WAYPOINT";
                Anchor: (Height: 44);
                Style: $C.@DefaultTextButtonStyle;
            }
        }
    }
}
```

### AddWaypointPage.ui

```
$C = "../Common.ui";

$C.@PageOverlay {
    $C.@DecoratedContainer {
        @CloseButton = true;
        Anchor: (Width: 500, Height: 400);

        #Title { $C.@Title { @Text = "ADD WAYPOINT"; } }

        #Content {
            LayoutMode: Top;
            Padding: (Full: 24);

            // Name input
            Group {
                LayoutMode: Left;
                Label { Text: "Name:"; Anchor: (Width: 80); }
                TextField #WaypointNameInput {
                    FlexWeight: 1;
                    Background: #0f1621;
                    PlaceholderText: "Enter waypoint name...";
                }
            }

            // Icon picker
            Group {
                LayoutMode: Left;
                Label { Text: "Icon:"; Anchor: (Width: 80); }
                Label #SelectedIconLabel { Text: "Coordinate"; }
                TextButton #ChooseIconButton { Text: "CHOOSE"; }
            }

            // Coordinate inputs
            Group {
                LayoutMode: Left;
                Label { Text: "X:"; }
                TextField #XInput { PlaceholderText: "0"; }
                Label { Text: "Y:"; }
                TextField #YInput { PlaceholderText: "0"; }
                Label { Text: "Z:"; }
                TextField #ZInput { PlaceholderText: "0"; }
            }

            // Error display
            Label #Error {
                Text: "";
                Visible: false;
                Style: (TextColor: #ff6b6b);
            }

            // Buttons
            Group {
                LayoutMode: Left;
                TextButton #AddButton { Text: "ADD"; }
                TextButton #CancelButton { Text: "CANCEL"; }
            }
        }
    }
}
```

---

## Teleportation

```java
// Using Hytale's built-in Teleport component
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;

Vector3d targetPos = new Vector3d(waypoint.transform.position.x,
                                   waypoint.transform.position.y,
                                   waypoint.transform.position.z);
Vector3f rotation = new Vector3f(0f, 0f, 0f);
Teleport teleport = new Teleport(targetPos, rotation);
store.addComponent(ref, Teleport.getComponentType(), teleport);
```

---

## World Map Markers (Storage)

```java
// Get player's world-specific data
String worldName = player.getWorld().getName();
PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(worldName);

// Get existing markers
MapMarker[] markers = perWorldData.getWorldMapMarkers();

// Add new marker
List<MapMarker> updatedMarkers = new ArrayList<>(Arrays.asList(markers));
updatedMarkers.add(newMarker);
perWorldData.setWorldMapMarkers(updatedMarkers.toArray(new MapMarker[0]));
```

---

## Dynamic UI Updates

### refreshWaypoints

```java
private void refreshWaypoints(Ref<EntityStore> ref, Store<EntityStore> store,
                              MapMarker[] markers, String query) {
    UICommandBuilder ui = new UICommandBuilder();
    UIEventBuilder events = new UIEventBuilder();

    // Clear existing list
    ui.clear("#WaypointsList");

    // Update search input
    if (query != null) {
        ui.set("#SearchInput.Value", query);
    }

    // Add filtered items
    for (int i = 0; i < markers.length; i++) {
        String selector = "#WaypointsList[" + i + "]";
        ui.append("#WaypointsList", "Pages/WaypointItem.ui");
        ui.set(selector + " #WaypointName.Text", markers[i].name);
        // ... set other properties
    }

    // Send partial update to client
    sendUpdate(ui, events, false);
}
```

---

## Key Imports

```java
// UI System
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

// World Map
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;

// Teleportation
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;

// Player
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.protocol.Position;
```

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|---------------------|
| InteractiveCustomUIPage | Vehicle dashboard/controls |
| UICommandBuilder.append() | Dynamic vehicle list |
| EventData with action strings | Vehicle UI actions |
| Teleport component | Vehicle teleport/recall |
| MapMarker storage | Vehicle parking/garage locations |
| Permission checking | Vehicle driving permissions |
| Dynamic UI updates | Real-time dashboard updates |
| Icon picker page | Vehicle type/color selection |

---

## Summary

Waypoints demonstrates:
- **InteractiveCustomUIPage** with typed data codec
- **Dynamic UI building** with append/clear
- **Event binding** with action strings
- **Built-in Teleport component** usage
- **MapMarker storage** in PlayerWorldData
- **Search/filter** with UI refresh
- **Page navigation** between UI pages
- **Permission checking** for features
- **Common.ui includes** for reusable styles
