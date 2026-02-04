package com.alexispace.hyvehicles.command;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.api.VehicleAPI;
import com.alexispace.hyvehicles.api.VehicleHandle;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.alexispace.hyvehicles.system.VehicleMountSystem;

import java.util.Collection;

/**
 * Main command for HytaleVehicles (/hv).
 * Uses Hytale's native subcommand system.
 */
public class HvCommand extends CommandBase {

    public HvCommand() {
        super("hv", "HytaleVehicles commands");

        // Register subcommands
        addSubCommand(new HelpSubCommand());
        addSubCommand(new ListSubCommand());
        addSubCommand(new TypesSubCommand());
        addSubCommand(new SpawnSubCommand());
        addSubCommand(new InfoSubCommand());
        addSubCommand(new StatusSubCommand());
        addSubCommand(new RemoveSubCommand());
        addSubCommand(new MountSubCommand());
        addSubCommand(new DismountSubCommand());
        addSubCommand(new ReloadSubCommand());
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        // Default: show help when just /hv is typed
        showHelp(ctx);
    }

    private static void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== HytaleVehicles Commands ===").color("#FFAA00"));
        ctx.sendMessage(Message.raw("/hv help").color("#FFFF55").insert(Message.raw(" - Show this help")));
        ctx.sendMessage(Message.raw("/hv list").color("#FFFF55").insert(Message.raw(" - List all vehicles")));
        ctx.sendMessage(Message.raw("/hv types").color("#FFFF55").insert(Message.raw(" - List vehicle types")));
        ctx.sendMessage(Message.raw("/hv spawn <id>").color("#FFFF55").insert(Message.raw(" - Spawn a vehicle")));
        ctx.sendMessage(Message.raw("/hv info <id>").color("#FFFF55").insert(Message.raw(" - Show vehicle details")));
        ctx.sendMessage(Message.raw("/hv status").color("#FFFF55").insert(Message.raw(" - Show spawned vehicles count")));
        ctx.sendMessage(Message.raw("/hv mount").color("#FFFF55").insert(Message.raw(" - Mount nearest vehicle")));
        ctx.sendMessage(Message.raw("/hv dismount").color("#FFFF55").insert(Message.raw(" - Dismount from vehicle")));
        ctx.sendMessage(Message.raw("/hv reload").color("#FFFF55").insert(Message.raw(" - Reload saved vehicles")));
    }

    // ==================== Subcommands ====================

    public static class HelpSubCommand extends CommandBase {
        public HelpSubCommand() {
            super("help", "Show help");
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            showHelp(ctx);
        }
    }

    public static class ListSubCommand extends CommandBase {
        public ListSubCommand() {
            super("list", "List all registered vehicles");
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            VehicleAPI api = HytaleVehiclesPlugin.getAPI();
            Collection<String> vehicles = api.getRegisteredVehicles();

            if (vehicles.isEmpty()) {
                ctx.sendMessage(Message.raw("No vehicles registered."));
                return;
            }

            ctx.sendMessage(Message.raw("=== Registered Vehicles (" + vehicles.size() + ") ===").color("#FFAA00"));
            for (String id : vehicles) {
                VehicleDefinition def = api.getVehicleDefinition(id);
                String type = def != null ? def.type : "?";
                ctx.sendMessage(Message.raw("- " + id + " (" + type + ")"));
            }
        }
    }

    public static class TypesSubCommand extends CommandBase {
        public TypesSubCommand() {
            super("types", "List all vehicle types");
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            VehicleAPI api = HytaleVehiclesPlugin.getAPI();
            Collection<String> types = api.getRegisteredTypes();

            if (types.isEmpty()) {
                ctx.sendMessage(Message.raw("No vehicle types registered."));
                return;
            }

            ctx.sendMessage(Message.raw("=== Vehicle Types (" + types.size() + ") ===").color("#FFAA00"));
            for (String type : types) {
                ctx.sendMessage(Message.raw("- " + type));
            }
        }
    }

    /**
     * Spawn subcommand - extends AbstractPlayerCommand for thread-safe world access.
     */
    public static class SpawnSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> vehicleIdArg;

        public SpawnSubCommand() {
            super("spawn", "Spawn a vehicle");
            vehicleIdArg = withRequiredArg("vehicleId", "The vehicle ID to spawn", ArgTypes.STRING);
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef player, World world) {
            String vehicleId = ctx.get(vehicleIdArg);
            VehicleAPI api = HytaleVehiclesPlugin.getAPI();

            VehicleDefinition def = api.getVehicleDefinition(vehicleId);
            if (def == null) {
                ctx.sendMessage(Message.raw("Vehicle not found: " + vehicleId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Use /hv list to see available vehicles."));
                return;
            }

            try {
                Transform transform = player.getTransform();
                Vector3d position = transform.getPosition();
                Vector3f rotation = transform.getRotation();
                float yaw = rotation.getYaw();

                float rad = (float) Math.toRadians(yaw);
                com.alexispace.hyvehicles.util.Vec3 spawnPos = new com.alexispace.hyvehicles.util.Vec3(
                    (float) position.x + (float) Math.sin(rad) * 3,
                    (float) position.y,
                    (float) position.z + (float) Math.cos(rad) * 3
                );

                VehicleHandle handle = api.spawnVehicle(vehicleId, spawnPos, yaw, world);

                if (handle != null) {
                    ctx.sendMessage(Message.raw("Spawned " + def.name).color("#55FF55"));
                    ctx.sendMessage(Message.raw("Instance ID: " + handle.getInstanceId()));
                } else {
                    ctx.sendMessage(Message.raw("Failed to spawn vehicle.").color("#FF5555"));
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
            }
        }
    }

    public static class InfoSubCommand extends CommandBase {
        private final RequiredArg<String> vehicleIdArg;

        public InfoSubCommand() {
            super("info", "Show vehicle details");
            vehicleIdArg = withRequiredArg("vehicleId", "The vehicle ID", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            String vehicleId = ctx.get(vehicleIdArg);
            VehicleAPI api = HytaleVehiclesPlugin.getAPI();

            VehicleDefinition def = api.getVehicleDefinition(vehicleId);
            if (def == null) {
                ctx.sendMessage(Message.raw("Vehicle not found: " + vehicleId).color("#FF5555"));
                return;
            }

            ctx.sendMessage(Message.raw("=== " + def.name + " ===").color("#FFAA00"));
            ctx.sendMessage(Message.raw("ID: " + def.id));
            ctx.sendMessage(Message.raw("Type: " + def.type));
            ctx.sendMessage(Message.raw("Description: " + (def.description != null ? def.description : "N/A")));
            ctx.sendMessage(Message.raw("Max Speed: " + def.maxSpeed + " blocks/sec"));
            ctx.sendMessage(Message.raw("Seats: " + def.seats.size()));

            if ("BOAT".equals(def.type)) {
                ctx.sendMessage(Message.raw("Buoyancy: " + def.buoyancy));
                ctx.sendMessage(Message.raw("Water Drag: " + def.waterDrag));
            }
        }
    }

    /**
     * Status subcommand - shows spawned vehicle count.
     */
    public static class StatusSubCommand extends CommandBase {
        public StatusSubCommand() {
            super("status", "Show spawned vehicles status");
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            var registry = HytaleVehiclesPlugin.getInstance().getRegistry();
            int spawned = registry.getSpawnedCount();
            int definitions = registry.getVehicleCount();

            ctx.sendMessage(Message.raw("=== Vehicle Status ===").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Registered definitions: " + definitions));
            ctx.sendMessage(Message.raw("Currently spawned: " + spawned));
            ctx.sendMessage(Message.raw("Persistence: JSON file").color("#55FF55"));
        }
    }

    /**
     * Remove subcommand - removes nearby vehicles.
     */
    public static class RemoveSubCommand extends AbstractPlayerCommand {
        public RemoveSubCommand() {
            super("remove", "Remove nearby vehicles (within 5 blocks)");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                Transform transform = player.getTransform();
                Vector3d playerPos = transform.getPosition();

                var registry = HytaleVehiclesPlugin.getInstance().getRegistry();
                var allVehicles = registry.getAllVehicles();

                int removedCount = 0;
                float maxDistance = 5.0f;

                // Find and remove vehicles within range
                var toRemove = new java.util.ArrayList<com.alexispace.hyvehicles.entity.BaseVehicle>();

                for (var vehicle : allVehicles) {
                    float[] pos = vehicle.getPositionArray();
                    double dx = pos[0] - playerPos.x;
                    double dy = pos[1] - playerPos.y;
                    double dz = pos[2] - playerPos.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance <= maxDistance) {
                        toRemove.add(vehicle);
                    }
                }

                for (var vehicle : toRemove) {
                    vehicle.destroy();
                    registry.untrackVehicle(vehicle.getInstanceId());
                    removedCount++;
                }

                if (removedCount > 0) {
                    ctx.sendMessage(Message.raw("Removed " + removedCount + " vehicle(s).").color("#55FF55"));
                } else {
                    ctx.sendMessage(Message.raw("No vehicles within 5 blocks.").color("#FFFF55"));
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
            }
        }
    }

    /**
     * Mount subcommand - mounts player to nearest vehicle.
     */
    public static class MountSubCommand extends AbstractPlayerCommand {
        public MountSubCommand() {
            super("mount", "Mount nearest vehicle");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                Transform transform = player.getTransform();
                Vector3d playerPos = transform.getPosition();

                var registry = HytaleVehiclesPlugin.getInstance().getRegistry();
                var allVehicles = registry.getAllVehicles();

                // Find nearest vehicle within 5 blocks
                com.alexispace.hyvehicles.entity.BaseVehicle nearestVehicle = null;
                double nearestDistance = 5.0;
                Ref<EntityStore> nearestVehicleRef = null;

                for (var vehicle : allVehicles) {
                    float[] pos = vehicle.getPositionArray();
                    double dx = pos[0] - playerPos.x;
                    double dy = pos[1] - playerPos.y;
                    double dz = pos[2] - playerPos.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestVehicle = vehicle;
                        nearestVehicleRef = vehicle.getEntityRef();
                    }
                }

                if (nearestVehicle == null || nearestVehicleRef == null) {
                    ctx.sendMessage(Message.raw("No vehicle within 5 blocks.").color("#FFFF55"));
                    return;
                }

                // Get seat offset from vehicle definition
                // Account for model scale AND modelYOffset
                // Entity position has modelYOffset applied, so we need to compensate
                var def = nearestVehicle.getDefinition();
                float scale = def.modelScale > 0 ? def.modelScale : 1.0f;
                float modelYOffset = def.modelYOffset; // Usually negative (e.g., -0.7)
                float seatX = 0f, seatY = 0.5f, seatZ = 0f;
                if (def.seats != null && !def.seats.isEmpty()) {
                    var driverSeat = def.seats.get(0);
                    // Scale seat position and compensate for modelYOffset
                    seatX = driverSeat.x * scale;
                    seatY = (driverSeat.y * scale) - modelYOffset; // Subtract negative = add
                    seatZ = driverSeat.z * scale;
                }
                ctx.sendMessage(Message.raw("Seat offset: " + seatX + ", " + seatY + ", " + seatZ + " (scale=" + scale + ", yOffset=" + modelYOffset + ")").color("#AAAAAA"));

                // Use VehicleMountSystem to mount
                VehicleMountSystem mountSystem = new VehicleMountSystem(
                    new com.alexispace.hyvehicles.util.VehicleLogger() {
                        @Override public void info(String msg) { }
                        @Override public void warning(String msg) { }
                        @Override public void severe(String msg) { }
                    }
                );

                boolean success = mountSystem.mountPlayer(player, nearestVehicleRef, store, seatX, seatY, seatZ, 0);

                if (success) {
                    ctx.sendMessage(Message.raw("Mounted to " + def.name).color("#55FF55"));
                } else {
                    ctx.sendMessage(Message.raw("Failed to mount vehicle.").color("#FF5555"));
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
                e.printStackTrace();
            }
        }
    }

    /**
     * Dismount subcommand - dismounts player from current vehicle.
     */
    public static class DismountSubCommand extends AbstractPlayerCommand {
        public DismountSubCommand() {
            super("dismount", "Dismount from vehicle");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                VehicleMountSystem mountSystem = new VehicleMountSystem(
                    new com.alexispace.hyvehicles.util.VehicleLogger() {
                        @Override public void info(String msg) { }
                        @Override public void warning(String msg) { }
                        @Override public void severe(String msg) { }
                    }
                );

                if (!mountSystem.isPlayerMounted(player, store)) {
                    ctx.sendMessage(Message.raw("You are not mounted.").color("#FFFF55"));
                    return;
                }

                boolean success = mountSystem.dismountPlayer(player, store);

                if (success) {
                    ctx.sendMessage(Message.raw("Dismounted from vehicle.").color("#55FF55"));
                } else {
                    ctx.sendMessage(Message.raw("Failed to dismount.").color("#FF5555"));
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
                e.printStackTrace();
            }
        }
    }

    /**
     * Reload subcommand - loads vehicles from JSON backup.
     */
    public static class ReloadSubCommand extends AbstractPlayerCommand {
        public ReloadSubCommand() {
            super("reload", "Load vehicles from JSON backup");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store,
                               Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                var plugin = HytaleVehiclesPlugin.getInstance();
                int count = plugin.loadAndSpawnVehicles(world);

                if (count > 0) {
                    ctx.sendMessage(Message.raw("Loaded " + count + " vehicles from backup.").color("#55FF55"));
                } else {
                    ctx.sendMessage(Message.raw("No saved vehicles to load.").color("#FFFF55"));
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
            }
        }
    }
}
