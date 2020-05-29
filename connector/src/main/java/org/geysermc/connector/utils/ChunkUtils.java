/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.utils;

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.nukkitx.math.vector.Vector2i;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.CompoundTagBuilder;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.protocol.bedrock.packet.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.ItemFrameEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.world.block.BlockStateValues;
import org.geysermc.connector.network.translators.world.block.entity.*;
import org.geysermc.connector.network.translators.world.block.BlockTranslator;
import org.geysermc.connector.network.translators.world.chunk.ChunkPosition;
import org.geysermc.connector.network.translators.world.chunk.ChunkSection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.geysermc.connector.network.translators.world.block.BlockTranslator.AIR;
import static org.geysermc.connector.network.translators.world.block.BlockTranslator.BEDROCK_WATER_ID;

public class ChunkUtils {

    /**
     * Temporarily stores positions of BlockState values that are needed for certain block entities actively
     */
    public static final Map<Position, BlockState> CACHED_BLOCK_ENTITIES = new HashMap<>();

    private static final com.nukkitx.nbt.tag.CompoundTag EMPTY_TAG = CompoundTagBuilder.builder().buildRootTag();
    public static final byte[] EMPTY_LEVEL_CHUNK_DATA;

    static {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(new byte[258]); // Biomes + Border Size + Extra Data Size

            try (NBTOutputStream stream = NbtUtils.createNetworkWriter(outputStream)) {
                stream.write(EMPTY_TAG);
            }

            EMPTY_LEVEL_CHUNK_DATA = outputStream.toByteArray();
        }catch (IOException e) {
            throw new AssertionError("Unable to generate empty level chunk data");
        }
    }

    public static ChunkData translateToBedrock(Column column, GeyserSession session) {
        ChunkData chunkData = new ChunkData();
        Chunk[] chunks = column.getChunks();
        chunkData.sections = new ChunkSection[chunks.length];

        CompoundTag[] blockEntities = column.getTileEntities();
        // Temporarily stores positions of BlockState values per chunk load
        Map<Position, BlockState> blockEntityPositions = new HashMap<>();

        // Temporarily stores compound tags of Bedrock-only block entities
        ObjectArrayList<com.nukkitx.nbt.tag.CompoundTag> bedrockOnlyBlockEntities = new ObjectArrayList<>();

        for (int chunkY = 0; chunkY < chunks.length; chunkY++) {
            chunkData.sections[chunkY] = new ChunkSection();
            Chunk chunk = chunks[chunkY];

            if (chunk == null || chunk.isEmpty())
                continue;

            ChunkSection section = chunkData.sections[chunkY];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState blockState = chunk.get(x, y, z);
                        int id = BlockTranslator.getBedrockBlockId(blockState);

                        // Check to see if the name is in BlockTranslator.getBlockEntityString, and therefore must be handled differently
                        if (BlockTranslator.getBlockEntityString(blockState) != null) {
                            Position pos = new ChunkPosition(column.getX(), column.getZ()).getBlock(x, (chunkY << 4) + y, z);
                            blockEntityPositions.put(pos, blockState);
                        }

                        section.getBlockStorageArray()[0].setFullBlock(ChunkSection.blockPosition(x, y, z), id);

                        // Check if block is piston or flower - only block entities in Bedrock
                        if (BlockStateValues.getFlowerPotValues().containsKey(blockState.getId()) ||
                                BlockStateValues.getPistonValues().containsKey(blockState.getId())) {
                            Position pos = new ChunkPosition(column.getX(), column.getZ()).getBlock(x, (chunkY << 4) + y, z);
                            bedrockOnlyBlockEntities.add(BedrockOnlyBlockEntity.getTag(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()), blockState));
                        }

                        if (BlockTranslator.isWaterlogged(blockState)) {
                            section.getBlockStorageArray()[1].setFullBlock(ChunkSection.blockPosition(x, y, z), BEDROCK_WATER_ID);
                        }
                    }
                }
            }

        }

        com.nukkitx.nbt.tag.CompoundTag[] bedrockBlockEntities = new com.nukkitx.nbt.tag.CompoundTag[blockEntities.length + bedrockOnlyBlockEntities.size()];
        int i = 0;
        while (i < blockEntities.length) {
            CompoundTag tag = blockEntities[i];
            String tagName;
            if (!tag.contains("id")) {
                GeyserConnector.getInstance().getLogger().debug("Got tag with no id: " + tag.getValue());
                tagName = "Empty";
            } else {
                tagName = (String) tag.get("id").getValue();
            }

            String id = BlockEntityUtils.getBedrockBlockEntityId(tagName);
            BlockEntityTranslator blockEntityTranslator = BlockEntityUtils.getBlockEntityTranslator(id);
            Position pos = new Position((int) tag.get("x").getValue(), (int) tag.get("y").getValue(), (int) tag.get("z").getValue());
            BlockState blockState = blockEntityPositions.get(pos);
            bedrockBlockEntities[i] = blockEntityTranslator.getBlockEntityTag(tagName, tag, blockState);

            //Check for custom skulls
            if (tag.contains("Owner") && SkullBlockEntityTranslator.allowCustomSkulls) {
                CompoundTag Owner = tag.get("Owner");
                if (Owner.contains("Properties")) {
                    SkullBlockEntityTranslator.spawnPlayer(session, tag, blockState);
                }
            }
            i++;
        }
        for (com.nukkitx.nbt.tag.CompoundTag tag : bedrockOnlyBlockEntities) {
            bedrockBlockEntities[i] = tag;
            i++;
        }

        chunkData.blockEntities = bedrockBlockEntities;
        return chunkData;
    }

    public static void updateChunkPosition(GeyserSession session, Vector3i position) {
        Vector2i chunkPos = session.getLastChunkPosition();
        Vector2i newChunkPos = Vector2i.from(position.getX() >> 4, position.getZ() >> 4);

        if (chunkPos == null || !chunkPos.equals(newChunkPos)) {
            NetworkChunkPublisherUpdatePacket chunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
            chunkPublisherUpdatePacket.setPosition(position);
            chunkPublisherUpdatePacket.setRadius(session.getRenderDistance() << 4);
            session.sendUpstreamPacket(chunkPublisherUpdatePacket);

            session.setLastChunkPosition(newChunkPos);
        }
    }

    public static void updateBlock(GeyserSession session, BlockState blockState, Position position) {
        Vector3i pos = Vector3i.from(position.getX(), position.getY(), position.getZ());
        updateBlock(session, blockState, pos);
    }

    public static void updateBlock(GeyserSession session, BlockState blockState, Vector3i position) {
        // Checks for item frames so they aren't tripped up and removed
        if (ItemFrameEntity.positionContainsItemFrame(session, position) && blockState.equals(AIR)) {
            ((ItemFrameEntity) session.getEntityCache().getEntityByJavaId(ItemFrameEntity.getItemFrameEntityId(session, position))).updateBlock(session);
            return;
        } else if (ItemFrameEntity.positionContainsItemFrame(session, position)) {
            Entity entity = session.getEntityCache().getEntityByJavaId(ItemFrameEntity.getItemFrameEntityId(session, position));
            if (entity != null) {
                session.getEntityCache().removeEntity(entity, false);
            } else {
                ItemFrameEntity.removePosition(session, position);
            }
        }

        if (SkullBlockEntityTranslator.containsCustomSkull(new Position(position.getX(), position.getY(), position.getZ()), session) && blockState.equals(AIR)) {
            Position skullPosition = new Position(position.getX(), position.getY(), position.getZ());
            RemoveEntityPacket removeEntityPacket = new RemoveEntityPacket();
            removeEntityPacket.setUniqueEntityId(session.getSkullCache().get(skullPosition).getGeyserId());
            session.sendUpstreamPacket(removeEntityPacket);
            session.getSkullCache().remove(skullPosition);
        }

        int blockId = BlockTranslator.getBedrockBlockId(blockState);

        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(position);
        updateBlockPacket.setRuntimeId(blockId);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.sendUpstreamPacket(updateBlockPacket);

        UpdateBlockPacket waterPacket = new UpdateBlockPacket();
        waterPacket.setDataLayer(1);
        waterPacket.setBlockPosition(position);
        if (BlockTranslator.isWaterlogged(blockState)) {
            waterPacket.setRuntimeId(BEDROCK_WATER_ID);
        } else {
            waterPacket.setRuntimeId(0);
        }
        session.sendUpstreamPacket(waterPacket);

        // Since Java stores bed colors/skull information as part of the namespaced ID and Bedrock stores it as a tag
        // This is the only place I could find that interacts with the Java block state and block updates
        // Iterates through all block entity translators and determines if the block state needs to be saved
        for (RequiresBlockState requiresBlockState : BlockEntityTranslator.REQUIRES_BLOCK_STATE_LIST) {
            if (requiresBlockState.isBlock(blockState)) {
                // Flower pots are block entities only in Bedrock and are not updated anywhere else like note blocks
                if (requiresBlockState instanceof BedrockOnlyBlockEntity) {
                    ((BedrockOnlyBlockEntity) requiresBlockState).updateBlock(session, blockState, position);
                    break;
                }
                CACHED_BLOCK_ENTITIES.put(new Position(position.getX(), position.getY(), position.getZ()), blockState);
                break; //No block will be a part of two classes
            }
        }
        session.getChunkCache().updateBlock(new Position(position.getX(), position.getY(), position.getZ()), blockState);
    }

    public static void sendEmptyChunks(GeyserSession session, Vector3i position, int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                LevelChunkPacket data = new LevelChunkPacket();
                data.setChunkX(chunkX + x);
                data.setChunkZ(chunkZ + z);
                data.setSubChunksLength(0);
                data.setData(EMPTY_LEVEL_CHUNK_DATA);
                data.setCachingEnabled(false);
                session.sendUpstreamPacket(data);

                if (forceUpdate) {
                    Vector3i pos = Vector3i.from(chunkX + x << 4, 80, chunkZ + z << 4);
                    UpdateBlockPacket blockPacket = new UpdateBlockPacket();
                    blockPacket.setBlockPosition(pos);
                    blockPacket.setDataLayer(0);
                    blockPacket.setRuntimeId(1);
                    session.sendUpstreamPacket(blockPacket);
                }
            }
        }
    }

    public static final class ChunkData {
        public ChunkSection[] sections;

        @Getter
        private com.nukkitx.nbt.tag.CompoundTag[] blockEntities = new com.nukkitx.nbt.tag.CompoundTag[0];
        @Getter
        private Object2IntMap<com.nukkitx.nbt.tag.CompoundTag> loadBlockEntitiesLater = new Object2IntOpenHashMap<>();
    }
}
