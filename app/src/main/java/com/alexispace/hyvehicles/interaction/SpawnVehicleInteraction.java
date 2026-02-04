package com.alexispace.hyvehicles.interaction;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.api.VehicleAPI;
import com.alexispace.hyvehicles.api.VehicleHandle;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.BlockMaterial;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Custom interaction that spawns a vehicle when the item is used (right-click).
 */
public class SpawnVehicleInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private String vehicleId;

    public static final BuilderCodec<SpawnVehicleInteraction> CODEC = createCodec();

    private static BuilderCodec<SpawnVehicleInteraction> createCodec() {
        BuilderCodec.Builder<SpawnVehicleInteraction> builder = BuilderCodec.builder(
            SpawnVehicleInteraction.class,
            SpawnVehicleInteraction::new,
            SimpleInstantInteraction.CODEC
        );

        return builder.appendInherited(
            new KeyedCodec<>("VehicleId", Codec.STRING, true),
            (SpawnVehicleInteraction i, String v) -> i.vehicleId = v,
            (SpawnVehicleInteraction i) -> i.vehicleId,
            (SpawnVehicleInteraction i, SpawnVehicleInteraction p) -> i.vehicleId = p.vehicleId
        ).add().build();
    }

    public SpawnVehicleInteraction() {
    }

    public String getVehicleId() {
        return vehicleId;
    }

    /**
     * Find the water surface Y level starting from a water block.
     */
    @SuppressWarnings("removal")
    private int findWaterSurface(BlockAccessor accessor, int x, int y, int z) {
        int surfaceY = y;
        for (int checkY = y; checkY < y + 10; checkY++) {
            int fluidAbove = accessor.getFluidId(x, checkY, z);
            if (fluidAbove <= 0) {
                surfaceY = checkY;
                break;
            }
            surfaceY = checkY + 1;
        }
        return surfaceY;
    }

    /**
     * Clear all replaceable blocks (grass, flowers, etc.) within the vehicle's footprint.
     * Accounts for vehicle rotation.
     */
    @SuppressWarnings("removal")
    private void clearReplaceableBlocksInFootprint(World world, float centerX, float centerZ, int y,
                                                    float width, float length, float yawDegrees) {
        // Convert yaw to radians
        double yawRad = Math.toRadians(yawDegrees);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);

        // Half dimensions
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        // Calculate the bounding box that encompasses all rotations
        float maxExtent = (float) Math.ceil(Math.max(halfWidth, halfLength) + 0.5f);

        int minX = (int) Math.floor(centerX - maxExtent);
        int maxX = (int) Math.ceil(centerX + maxExtent);
        int minZ = (int) Math.floor(centerZ - maxExtent);
        int maxZ = (int) Math.ceil(centerZ + maxExtent);

        // Check each block in the bounding area
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                // Transform block position to vehicle-local coordinates
                double localX = (bx + 0.5 - centerX) * cosYaw + (bz + 0.5 - centerZ) * sinYaw;
                double localZ = -(bx + 0.5 - centerX) * sinYaw + (bz + 0.5 - centerZ) * cosYaw;

                // Check if this block is within the vehicle's rotated footprint
                if (Math.abs(localX) <= halfWidth && Math.abs(localZ) <= halfLength) {
                    try {
                        long chunkKey = ChunkUtil.indexChunkFromBlock(bx, bz);
                        BlockAccessor accessor = world.getChunkIfLoaded(chunkKey);
                        if (accessor != null) {
                            BlockType blockType = accessor.getBlockType(bx, y, bz);
                            if (blockType != null && blockType.getMaterial() == BlockMaterial.Empty) {
                                accessor.breakBlock(bx, y, bz);
                                LOGGER.at(Level.FINE).log("Cleared replaceable block at %d, %d, %d", bx, y, bz);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors for individual blocks
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("removal")
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldown) {

        // Get target block position (where player right-clicked)
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            LOGGER.at(Level.WARNING).log("No target block - right-click on ground to spawn vehicle");
            return;
        }

        // Get the command buffer (needed for spawning)
        var commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            LOGGER.at(Level.SEVERE).log("No command buffer available");
            return;
        }

        // Calculate spawn position - detect water and spawn on surface
        float spawnY = targetBlock.y + 1.0f;

        try {
            EntityStore entityStore = commandBuffer.getStore().getExternalData();
            World world = entityStore.getWorld();
            if (world != null) {
                long chunkKey = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
                BlockAccessor accessor = world.getChunkIfLoaded(chunkKey);
                if (accessor != null) {
                    int fluidAtTarget = accessor.getFluidId(targetBlock.x, targetBlock.y, targetBlock.z);
                    int fluidAboveTarget = accessor.getFluidId(targetBlock.x, targetBlock.y + 1, targetBlock.z);

                    if (fluidAtTarget > 0) {
                        // Clicked on water - find surface
                        int surfaceY = findWaterSurface(accessor, targetBlock.x, targetBlock.y, targetBlock.z);
                        spawnY = surfaceY - 0.3f;
                    } else if (fluidAboveTarget > 0) {
                        // Clicked on block under water - find surface
                        int surfaceY = findWaterSurface(accessor, targetBlock.x, targetBlock.y + 1, targetBlock.z);
                        spawnY = surfaceY - 0.3f;
                    } else {
                        // Check if target block is replaceable (grass, flowers, etc.)
                        BlockType blockType = accessor.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
                        if (blockType != null && blockType.getMaterial() == BlockMaterial.Empty) {
                            // Replaceable block - spawn at same level (footprint clearing handles block removal)
                            spawnY = targetBlock.y;
                        } else {
                            // Solid block - spawn on top
                            spawnY = targetBlock.y + 1.0f;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not detect water: %s", e.getMessage());
            spawnY = targetBlock.y + 1.0f;
        }

        Vec3 spawnPos = new Vec3(
            targetBlock.x + 0.5f,
            spawnY,
            targetBlock.z + 0.5f
        );

        // Get rotation from the client state (if BlockType with VariantRotation is used)
        float vehicleYaw = 0f;
        try {
            InteractionSyncData clientState = context.getClientState();
            if (clientState != null && clientState.blockRotation != null && clientState.blockRotation.rotationYaw != null) {
                vehicleYaw = clientState.blockRotation.rotationYaw.getValue() * 90f;
                LOGGER.at(Level.INFO).log("Using block rotation from client: %f degrees", vehicleYaw);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not get block rotation: %s", e.getMessage());
        }

        // Clear replaceable blocks in the vehicle's footprint
        try {
            VehicleAPI api = HytaleVehiclesPlugin.getAPI();
            VehicleDefinition def = api.getVehicleDefinition(vehicleId);
            if (def != null) {
                EntityStore entityStore = commandBuffer.getStore().getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    // Apply modelScale to collision dimensions (visual model is scaled)
                    float scaledWidth = def.collisionWidth * def.modelScale;
                    float scaledLength = def.collisionLength * def.modelScale;
                    // Clear blocks at spawn Y level (where vehicle bottom sits)
                    int clearY = (int) Math.floor(spawnY);
                    clearReplaceableBlocksInFootprint(world, spawnPos.x, spawnPos.z, clearY,
                        scaledWidth, scaledLength, vehicleYaw);
                    LOGGER.at(Level.INFO).log("Cleared footprint: %.1fx%.1f (scale=%.1f) at Y=%d, yaw=%.0f",
                        scaledWidth, scaledLength, def.modelScale, clearY, vehicleYaw);
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not clear vehicle footprint: %s", e.getMessage());
        }

        try {
            LOGGER.at(Level.INFO).log("Spawning vehicle: %s at (%f, %f, %f) yaw=%f",
                vehicleId, spawnPos.x, spawnPos.y, spawnPos.z, vehicleYaw);

            VehicleAPI api = HytaleVehiclesPlugin.getAPI();
            VehicleHandle handle = api.spawnVehicleWithCommandBuffer(
                vehicleId,
                spawnPos,
                vehicleYaw,
                commandBuffer
            );

            if (handle != null) {
                LOGGER.at(Level.INFO).log("Vehicle spawned successfully: %s", vehicleId);

                // Consume one item
                if (context.getHeldItem() != null) {
                    int currentQty = context.getHeldItem().getQuantity();
                    if (currentQty > 1) {
                        context.setHeldItem(context.getHeldItem().withQuantity(currentQty - 1));
                    } else {
                        context.setHeldItem(null);
                    }
                }
            } else {
                LOGGER.at(Level.SEVERE).log("Failed to spawn vehicle: %s", vehicleId);
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Error spawning vehicle: %s", e.getMessage());
            e.printStackTrace();
        }
    }
}
