package com.alexispace.hyvehicles.interaction;

import com.alexispace.hyvehicles.HytaleVehiclesPlugin;
import com.alexispace.hyvehicles.api.VehicleAPI;
import com.alexispace.hyvehicles.api.VehicleHandle;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class SpawnVehicleInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private String vehicleId;
    public static final BuilderCodec<SpawnVehicleInteraction> CODEC = SpawnVehicleInteraction.createCodec();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BuilderCodec<SpawnVehicleInteraction> createCodec() {
        BuilderCodec.Builder builder = BuilderCodec.builder(SpawnVehicleInteraction.class, SpawnVehicleInteraction::new, SimpleInstantInteraction.CODEC);
        return ((BuilderCodec.Builder<SpawnVehicleInteraction>) builder.appendInherited(
            new KeyedCodec("VehicleId", Codec.STRING, true),
            (i, v) -> ((SpawnVehicleInteraction) i).vehicleId = (String) v,
            i -> ((SpawnVehicleInteraction) i).vehicleId,
            (i, p) -> ((SpawnVehicleInteraction) i).vehicleId = ((SpawnVehicleInteraction) p).vehicleId
        ).add()).build();
    }

    public String getVehicleId() {
        return this.vehicleId;
    }

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

    @SuppressWarnings("removal")
    private void clearReplaceableBlocksInFootprint(World world, float centerX, float centerZ, int y, float width, float length, float yawDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        float halfWidth = width / 2.0f;
        float halfLength = length / 2.0f;
        float maxExtent = (float) Math.ceil(Math.max(halfWidth, halfLength) + 0.5f);
        int minX = (int) Math.floor(centerX - maxExtent);
        int maxX = (int) Math.ceil(centerX + maxExtent);
        int minZ = (int) Math.floor(centerZ - maxExtent);
        int maxZ = (int) Math.ceil(centerZ + maxExtent);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                double localX = (bx + 0.5 - centerX) * cosYaw + (bz + 0.5 - centerZ) * sinYaw;
                double localZ = -(bx + 0.5 - centerX) * sinYaw + (bz + 0.5 - centerZ) * cosYaw;
                if (Math.abs(localX) <= halfWidth && Math.abs(localZ) <= halfLength) {
                    try {
                        long chunkKey = ChunkUtil.indexChunkFromBlock(bx, bz);
                        WorldChunk accessor = world.getChunkIfLoaded(chunkKey);
                        if (accessor != null) {
                            BlockType blockType = accessor.getBlockType(bx, y, bz);
                            if (blockType != null && blockType.getMaterial() == BlockMaterial.Empty) {
                                accessor.breakBlock(bx, y, bz);
                                LOGGER.at(Level.FINE).log("Cleared replaceable block at %d, %d, %d", bx, y, bz);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("removal")
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldown) {
        VehicleAPI api;
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            LOGGER.at(Level.WARNING).log("No target block - right-click on ground to spawn vehicle");
            return;
        }
        CommandBuffer commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            LOGGER.at(Level.SEVERE).log("No command buffer available");
            return;
        }
        float spawnY = targetBlock.y + 1.0f;
        try {
            EntityStore entityStore = (EntityStore) commandBuffer.getStore().getExternalData();
            World world = entityStore.getWorld();
            if (world != null) {
                long chunkKey = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
                WorldChunk accessor = world.getChunkIfLoaded(chunkKey);
                if (accessor != null) {
                    int fluidAtTarget = accessor.getFluidId(targetBlock.x, targetBlock.y, targetBlock.z);
                    int fluidAboveTarget = accessor.getFluidId(targetBlock.x, targetBlock.y + 1, targetBlock.z);
                    if (fluidAtTarget > 0) {
                        int surfaceY = this.findWaterSurface(accessor, targetBlock.x, targetBlock.y, targetBlock.z);
                        spawnY = surfaceY - 0.3f;
                    } else if (fluidAboveTarget > 0) {
                        int surfaceY = this.findWaterSurface(accessor, targetBlock.x, targetBlock.y + 1, targetBlock.z);
                        spawnY = surfaceY - 0.3f;
                    } else {
                        BlockType blockType = accessor.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
                        spawnY = blockType != null && blockType.getMaterial() == BlockMaterial.Empty ? targetBlock.y : targetBlock.y + 1.0f;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not detect water: %s", e.getMessage());
            spawnY = targetBlock.y + 1.0f;
        }
        Vec3 spawnPos = new Vec3(targetBlock.x + 0.5f, spawnY, targetBlock.z + 0.5f);
        float vehicleYaw = 0.0f;
        try {
            InteractionSyncData clientState = context.getClientState();
            if (clientState != null && clientState.blockRotation != null && clientState.blockRotation.rotationYaw != null) {
                vehicleYaw = (float) clientState.blockRotation.rotationYaw.getValue() * 90.0f;
                LOGGER.at(Level.INFO).log("Using block rotation from client: %f degrees", Float.valueOf(vehicleYaw));
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not get block rotation: %s", e.getMessage());
        }
        try {
            api = HytaleVehiclesPlugin.getAPI();
            VehicleDefinition def = api.getVehicleDefinition(this.vehicleId);
            if (def != null) {
                EntityStore entityStore = (EntityStore) commandBuffer.getStore().getExternalData();
                World world = entityStore.getWorld();
                if (world != null) {
                    float scaledWidth = def.collisionWidth * def.modelScale;
                    float scaledLength = def.collisionLength * def.modelScale;
                    int clearY = (int) Math.floor(spawnY);
                    this.clearReplaceableBlocksInFootprint(world, spawnPos.x, spawnPos.z, clearY, scaledWidth, scaledLength, vehicleYaw);
                    LOGGER.at(Level.INFO).log("Cleared footprint: %.1fx%.1f (scale=%.1f) at Y=%d, yaw=%.0f", Float.valueOf(scaledWidth), Float.valueOf(scaledLength), Float.valueOf(def.modelScale), clearY, Float.valueOf(vehicleYaw));
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not clear vehicle footprint: %s", e.getMessage());
        }
        try {
            LOGGER.at(Level.INFO).log("Spawning vehicle: %s at (%f, %f, %f) yaw=%f", this.vehicleId, Float.valueOf(spawnPos.x), Float.valueOf(spawnPos.y), Float.valueOf(spawnPos.z), Float.valueOf(vehicleYaw));
            api = HytaleVehiclesPlugin.getAPI();
            VehicleHandle handle = api.spawnVehicleWithCommandBuffer(this.vehicleId, spawnPos, vehicleYaw, commandBuffer);
            if (handle != null) {
                LOGGER.at(Level.INFO).log("Vehicle spawned successfully: %s", this.vehicleId);
                if (context.getHeldItem() != null) {
                    int currentQty = context.getHeldItem().getQuantity();
                    if (currentQty > 1) {
                        context.setHeldItem(context.getHeldItem().withQuantity(currentQty - 1));
                    } else {
                        context.setHeldItem(null);
                    }
                }
            } else {
                LOGGER.at(Level.SEVERE).log("Failed to spawn vehicle: %s", this.vehicleId);
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Error spawning vehicle: %s", e.getMessage());
            e.printStackTrace();
        }
    }
}
