package com.alexispace.hyvehicles.interaction;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class SpawnNPCBoatInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double LAND_Y_OFFSET = -0.7;
    private static final double WATER_Y_OFFSET = -0.3;
    private String entityId;
    private Vector3d spawnOffset;
    public static final BuilderCodec<SpawnNPCBoatInteraction> CODEC = SpawnNPCBoatInteraction.createCodec();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BuilderCodec<SpawnNPCBoatInteraction> createCodec() {
        BuilderCodec.Builder builder = BuilderCodec.builder(SpawnNPCBoatInteraction.class, SpawnNPCBoatInteraction::new, SimpleInstantInteraction.CODEC);
        return ((BuilderCodec.Builder<SpawnNPCBoatInteraction>) builder.appendInherited(
            new KeyedCodec("EntityId", Codec.STRING, true),
            (i, v) -> ((SpawnNPCBoatInteraction) i).entityId = (String) v,
            i -> ((SpawnNPCBoatInteraction) i).entityId,
            (i, p) -> ((SpawnNPCBoatInteraction) i).entityId = ((SpawnNPCBoatInteraction) p).entityId
        ).add().appendInherited(
            new KeyedCodec("SpawnOffset", Vector3d.CODEC, false),
            (i, v) -> ((SpawnNPCBoatInteraction) i).spawnOffset = (Vector3d) v,
            i -> ((SpawnNPCBoatInteraction) i).spawnOffset,
            (i, p) -> ((SpawnNPCBoatInteraction) i).spawnOffset = ((SpawnNPCBoatInteraction) p).spawnOffset
        ).add()).build();
    }

    public String getEntityId() {
        return this.entityId;
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

    @Override
    @SuppressWarnings("removal")
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldown) {
        double spawnY;
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            LOGGER.at(Level.WARNING).log("No target block - right-click on ground to spawn NPC boat");
            return;
        }
        CommandBuffer commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            LOGGER.at(Level.SEVERE).log("No command buffer available");
            return;
        }
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
                        spawnY = surfaceY + WATER_Y_OFFSET;
                        LOGGER.at(Level.INFO).log("NPC Boat: Clicked water at Y=%d, surface=%d, spawnY=%.1f", targetBlock.y, surfaceY, spawnY);
                    } else if (fluidAboveTarget > 0) {
                        int surfaceY = this.findWaterSurface(accessor, targetBlock.x, targetBlock.y + 1, targetBlock.z);
                        spawnY = surfaceY + WATER_Y_OFFSET;
                        LOGGER.at(Level.INFO).log("NPC Boat: Clicked under water at Y=%d, surface=%d, spawnY=%.1f", targetBlock.y, surfaceY, spawnY);
                    } else {
                        spawnY = targetBlock.y + 1.0 + LAND_Y_OFFSET;
                        LOGGER.at(Level.INFO).log("NPC Boat: Land placement at Y=%d, spawnY=%.1f (offset=%.1f)", targetBlock.y, spawnY, LAND_Y_OFFSET);
                    }
                } else {
                    spawnY = targetBlock.y + 1.0 + LAND_Y_OFFSET;
                }
            } else {
                spawnY = targetBlock.y + 1.0 + LAND_Y_OFFSET;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not detect water: %s", e.getMessage());
            spawnY = targetBlock.y + 1.0 + LAND_Y_OFFSET;
        }
        double offsetX = this.spawnOffset != null ? this.spawnOffset.x : 0.0;
        double offsetZ = this.spawnOffset != null ? this.spawnOffset.z : 0.0;
        Vector3d spawnPos = new Vector3d(targetBlock.x + 0.5 + offsetX, spawnY, targetBlock.z + 0.5 + offsetZ);
        Vector3f rotation = new Vector3f(0.0f, 0.0f, 0.0f);
        if (context.getHeldItem() != null) {
            int currentQty = context.getHeldItem().getQuantity();
            if (currentQty > 1) {
                context.setHeldItem(context.getHeldItem().withQuantity(currentQty - 1));
            } else {
                context.setHeldItem(null);
            }
        }
        try {
            EntityStore entityStore = (EntityStore) commandBuffer.getStore().getExternalData();
            World world = entityStore.getWorld();
            if (world == null) {
                LOGGER.at(Level.SEVERE).log("World is null, cannot spawn NPC boat");
                return;
            }
            String npcEntityId = this.entityId;
            Vector3d finalSpawnPos = spawnPos;
            Vector3f finalRotation = rotation;
            LOGGER.at(Level.INFO).log("Scheduling NPC boat '%s' spawn at (%.1f, %.1f, %.1f)", npcEntityId, finalSpawnPos.x, finalSpawnPos.y, finalSpawnPos.z);
            world.execute(() -> {
                try {
                    NPCPlugin npcPlugin = NPCPlugin.get();
                    Pair result = npcPlugin.spawnNPC(world.getEntityStore().getStore(), npcEntityId, null, finalSpawnPos, finalRotation);
                    if (result != null) {
                        LOGGER.at(Level.INFO).log("NPC boat spawned successfully: %s", npcEntityId);
                    } else {
                        LOGGER.at(Level.SEVERE).log("Failed to spawn NPC boat: %s (null result)", npcEntityId);
                    }
                } catch (Exception e) {
                    LOGGER.at(Level.SEVERE).log("Error spawning NPC boat: %s", e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Error scheduling NPC boat spawn: %s", e.getMessage());
            e.printStackTrace();
        }
    }
}
