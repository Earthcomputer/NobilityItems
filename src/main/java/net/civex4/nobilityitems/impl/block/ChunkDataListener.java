package net.civex4.nobilityitems.impl.block;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.civex4.nobilityitems.NobilityBlock;
import net.civex4.nobilityitems.NobilityItems;
import net.civex4.nobilityitems.impl.Protocol;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

public class ChunkDataListener {
    private static final EquivalentConverter<WrappedBlockData> STATE_CONVERTER = BukkitConverters.getWrappedBlockDataConverter();
    private static final MethodAccessor STATE_TO_ID;
    static {
        Class<?> stateClass = MinecraftReflection.getIBlockDataClass();
        Class<?> blockClass = MinecraftReflection.getBlockClass();
        FuzzyReflection fuzzyBlock = FuzzyReflection.fromClass(blockClass);
        STATE_TO_ID = Accessors.getMethodAccessor(fuzzyBlock.getMethod(FuzzyMethodContract.newBuilder().requireModifier(Modifier.STATIC).returnTypeExact(int.class).parameterExactArray(stateClass).build()));
    }

    private static final int AIR = getStateId(Material.AIR);
    private static final int VOID_AIR = getStateId(Material.SNOW);
    private static final IntSet AIR_BLOCKS = new IntArraySet();
    static {
        for (Material material : Registry.MATERIAL) {
            if (material.isAir()) {
                AIR_BLOCKS.add(getStateId(material));
            }
        }
    }

    private static final IntSet waterloggedSlabs = new IntOpenHashSet();
    private static final ReadWriteLock waterloggedSlabsLock = new ReentrantReadWriteLock();

    public static void recomputeCache() {
        waterloggedSlabsLock.writeLock().lock();
        try {
            waterloggedSlabs.clear();
            for (NobilityBlock block : NobilityItems.getBlocks()) {
                if (block.getBlockData() instanceof Waterlogged && ((Waterlogged) block.getBlockData()).isWaterlogged() && !block.isWaterlogged()) {
                    waterloggedSlabs.add(getStateId(block.getBlockData()));
                }
            }
        } finally {
            waterloggedSlabsLock.writeLock().unlock();
        }
    }

    private static int getStateId(Material material) {
        return (Integer) STATE_TO_ID.invoke(null, STATE_CONVERTER.getGeneric(WrappedBlockData.createData(material)));
    }

    private static int getStateId(BlockData data) {
        return (Integer) STATE_TO_ID.invoke(null, STATE_CONVERTER.getGeneric(WrappedBlockData.createData(data)));
    }

    public static void register(Plugin plugin) {
        Protocol.protocolManager.addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.MAP_CHUNK).optionAsync()) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                int availableSections = packet.getIntegers().read(2);
                byte[] oldData = packet.getByteArrays().read(0);
                assert oldData != null;

                ByteBuffer buffer = ByteBuffer.wrap(oldData);
                ChunkData chunkData = ChunkData.read(availableSections, buffer);

                waterloggedSlabsLock.readLock().lock();
                try {
                    for (ChunkData.Section section : chunkData.sections) {
                        if (section != null) {
                            section.forEach((pos, block) -> {
                                if (waterloggedSlabs.contains(block)) {
                                    int downPos = down(pos);
                                    if (AIR_BLOCKS.contains(section.getStateId(downPos))) {
                                        section.setStateId(downPos, VOID_AIR);
                                    }
                                }
                            });
                        }
                    }
                } finally {
                    waterloggedSlabsLock.readLock().unlock();
                }

                byte[] newData = new byte[chunkData.getDataSize()];
                buffer = ByteBuffer.wrap(newData);
                availableSections = chunkData.write(buffer);

                packet.getByteArrays().write(0, newData);
                packet.getIntegers().write(2, availableSections);
            }
        });
    }

    private static class ChunkData {
        private final Section[] sections = new Section[16];

        private static ChunkData read(int availableSections, ByteBuffer buffer) {
            ChunkData dest = new ChunkData();
            for (int sectionY = 0; sectionY < dest.sections.length; sectionY++) {
                if ((availableSections & (1 << sectionY)) != 0) {
                    dest.sections[sectionY] = Section.read(dest, sectionY, buffer);
                }
            }
            return dest;
        }

        private int write(ByteBuffer buffer) {
            int availableSections = 0;
            for (int sectionY = 0; sectionY < sections.length; sectionY++) {
                if (sections[sectionY] != null) {
                    availableSections |= 1 << sectionY;
                    sections[sectionY].write(buffer);
                }
            }
            return availableSections;
        }

        private int getDataSize() {
            int dataSize = 0;
            for (Section section : sections) {
                if (section != null) {
                    dataSize += section.getDataSize();
                }
            }
            return dataSize;
        }

        private static class Section {
            private final ChunkData parent;
            private final int sectionY;
            private int nonEmptyBlockCount;
            private int bitsPerBlock;
            private int blocksPerWord;
            private int mask;
            private IntList palette;
            private Int2ShortMap reversePalette;
            private long[] data;

            private Section(ChunkData parent, int sectionY) {
                this.parent = parent;
                this.sectionY = sectionY;
            }

            private static Section createEmpty(ChunkData parent, int sectionY) {
                Section dest = new Section(parent, sectionY);
                dest.nonEmptyBlockCount = 0;
                dest.bitsPerBlock = 4;
                dest.blocksPerWord = 16;
                dest.mask = 15;
                dest.palette = new IntArrayList();
                dest.palette.add(0);
                dest.reversePalette = new Int2ShortOpenHashMap();
                dest.reversePalette.put(0, (short) 0);
                dest.data = new long[256];
                return dest;
            }

            private static Section read(ChunkData parent, int sectionY, ByteBuffer buffer) {
                Section dest = new Section(parent, sectionY);
                dest.nonEmptyBlockCount = buffer.getShort();
                dest.bitsPerBlock = buffer.get();
                if (dest.bitsPerBlock < 4) {
                    dest.bitsPerBlock = 4;
                }
                if (dest.bitsPerBlock <= 8) {
                    dest.palette = new IntArrayList();
                    dest.reversePalette = new Int2ShortOpenHashMap();
                    int paletteSize = readVarInt(buffer);
                    for (int i = 0; i < paletteSize; i++) {
                        int stateId = readVarInt(buffer);
                        dest.palette.add(stateId);
                        dest.reversePalette.put(stateId, (short) i);
                    }
                }
                dest.blocksPerWord = 64 / dest.bitsPerBlock;
                dest.mask = (1 << dest.bitsPerBlock) - 1;
                int length = readVarInt(buffer);
                dest.data = new long[(4096 + dest.blocksPerWord - 1) / dest.blocksPerWord];
                LongBuffer longBuffer = buffer.asLongBuffer();
                longBuffer.get(dest.data, 0, length);
                buffer.position(buffer.position() + longBuffer.position() * 8);

                return dest;
            }

            private void write(ByteBuffer buffer) {
                buffer.putShort((short) nonEmptyBlockCount);
                buffer.put((byte) bitsPerBlock);
                if (palette != null) {
                    writeVarInt(buffer, palette.size());
                    palette.forEach((IntConsumer) val -> writeVarInt(buffer, val));
                }
                writeVarInt(buffer, data.length);
                LongBuffer longBuffer = buffer.asLongBuffer();
                longBuffer.put(data);
                buffer.position(buffer.position() + longBuffer.position() * 8);
            }

            private int getDataSize() {
                int paletteSize;
                if (palette != null) {
                    paletteSize = getVarIntSize(palette.size());
                    for(int i = 0; i < palette.size(); ++i) {
                        paletteSize += getVarIntSize(palette.getInt(i));
                    }
                } else {
                    paletteSize = 0;
                }
                return 2 + 1 + paletteSize + getVarIntSize(data.length) + data.length * 8;
            }

            private void forEach(BlockAcceptor acceptor) {
                for (int pos = 0; pos < 4096; pos++) {
                    acceptor.accept(pos, getStateId(pos));
                }
            }

            private int getStateId(int pos) {
                // check if we need to get the block from a different chunk section
                if (pos < 0 || pos >= 4096) {
                    int destSectionY = sectionY + (pos >> 12);
                    if (destSectionY < 0 || destSectionY >= parent.sections.length || parent.sections[destSectionY] == null) {
                        return AIR;
                    }
                    return parent.sections[destSectionY].getStateId(pos & 4095);
                }

                int block = (int) (data[pos / blocksPerWord] >>> (bitsPerBlock * (pos % blocksPerWord))) & mask;
                if (palette != null) {
                    block = palette.getInt(block);
                }
                return block;
            }

            private void setStateId(int pos, int stateId) {
                // check if we need to set block in a different chunk section
                if (pos < 0 || pos >= 4096) {
                    int destSectionY = sectionY + (pos >> 12);
                    if (destSectionY >= 0 && destSectionY < parent.sections.length) {
                        Section destSection = parent.sections[destSectionY];
                        if (destSection == null) {
                            destSection = parent.sections[destSectionY] = createEmpty(parent, destSectionY);
                        }
                        destSection.setStateId(pos & 4095, stateId);
                    }
                    return;
                }

                boolean isAirBlock = AIR_BLOCKS.contains(stateId);
                boolean wasAirBlock = AIR_BLOCKS.contains(getStateId(pos));
                if (isAirBlock && !wasAirBlock) {
                    nonEmptyBlockCount--;
                } else if (!isAirBlock && wasAirBlock) {
                    nonEmptyBlockCount++;
                }

                // check the palette, add to it and realign the data if needed
                if (reversePalette != null) {
                    if (!reversePalette.containsKey(stateId)) {
                        int newId = palette.size();
                        if ((newId & -newId) == newId) { // is newId a power of 2
                            // resize palette
                            int newBitsPerBlock = bitsPerBlock + 1;
                            int newBlocksPerWord = 64 / newBitsPerBlock;
                            int newMask = (1 << newBitsPerBlock) - 1;
                            int newWordCount = (4096 + newBitsPerBlock - 1) / newBitsPerBlock;
                            long[] newData = new long[newWordCount];
                            for (int translatePos = 0; translatePos < 4096; translatePos++) {
                                int state = (int) (data[translatePos / blocksPerWord] >>> (bitsPerBlock & (translatePos % blocksPerWord))) & mask;
                                if (newId > 8) {
                                    state = palette.getInt((short) state);
                                }
                                newData[translatePos / newBlocksPerWord] |= (state & newMask) << (newBitsPerBlock * (translatePos % newBlocksPerWord));
                            }
                            bitsPerBlock = newBitsPerBlock;
                            blocksPerWord = newBlocksPerWord;
                            mask = newMask;
                            data = newData;
                            if (newId > 8) {
                                palette = null;
                                reversePalette = null;
                            }
                        }
                        if (palette != null) {
                            palette.add(stateId);
                            reversePalette.put(stateId, (short) newId);
                            stateId = newId;
                        }
                    } else {
                        stateId = reversePalette.get(stateId);
                    }
                }

                // actually set the block
                data[pos / blocksPerWord] |= (stateId & mask) << (bitsPerBlock * (pos % blocksPerWord));
            }
        }
    }

    private static int readVarInt(ByteBuffer buffer) {
        int val = 0;
        int bytesRead = 0;

        byte b;
        do {
            b = buffer.get();
            val |= (b & 127) << (bytesRead++ * 7);
            if (bytesRead > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while((b & 128) == 128);

        return val;
    }

    private static void writeVarInt(ByteBuffer buffer, int val) {
        while ((val & -128) != 0) {
            buffer.put((byte) ((val & 127) | 128));
            val >>>= 7;
        }

        buffer.put((byte) val);
    }

    private static int getVarIntSize(int val) {
        for (int bytesWritten = 1; bytesWritten < 5; bytesWritten++) {
            if ((val & -1 << bytesWritten * 7) == 0) {
                return bytesWritten;
            }
        }

        return 5;
    }

    private static int down(int pos) {
        return pos - 256;
    }

    @FunctionalInterface
    private interface BlockAcceptor {
        void accept(int pos, int block);
    }
}
