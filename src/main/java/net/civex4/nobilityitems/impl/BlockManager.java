package net.civex4.nobilityitems.impl;

import net.civex4.nobilityitems.NobilityBlock;
import net.civex4.nobilityitems.NobilityItem;
import net.civex4.nobilityitems.NobilityItems;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class BlockManager {
    private static Map<String, NobilityBlock> blocks;
    private static List<NobilityBlock> blockList;
    private static File file;
    private static FileConfiguration blocksConfig;
    private static Map<Object, Boolean> isNmsNobilityBlockCache = new IdentityHashMap<>();

    public static void init(NobilityItems nobilityItems) {
        nobilityItems.saveResource("blocks.yml", false);
        file = new File(nobilityItems.getDataFolder(), "blocks.yml");
        blocksConfig = YamlConfiguration.loadConfiguration(file);

        blocks = new LinkedHashMap<>();
        blockList = null;
        for (String internalName : blocksConfig.getKeys(false)) {
            ConfigurationSection blockConfig = blocksConfig.getConfigurationSection(internalName);
            if (blockConfig != null) {
                NobilityBlock block = new NobilityBlock(internalName, blockConfig);
                blocks.put(internalName, block);
            }
        }
        isNmsNobilityBlockCache = new IdentityHashMap<>();
    }

    public static boolean makeBlock(String internalName, BlockData blockData, NobilityItem item) {
        if (blocks.containsKey(internalName)) {
            return false;
        }

        NobilityBlock block = new NobilityBlock(internalName, blockData, item);
        blocks.put(internalName, block);
        blockList = null;

        ConfigurationSection blockSection = blocksConfig.createSection(internalName);
        blockSection.set("blockData", block.getBlockData().getAsString(false));
        if (block.hasItem()) {
            blockSection.set("hasItem", true);
        }

        save();

        return true;
    }

    public static List<NobilityBlock> getBlocks() {
        if (blockList == null) {
            blockList = new ArrayList<>(blocks.values());
        }
        return blockList;
    }

    public static NobilityBlock getNullableBlock(String internalName) {
        return blocks.get(internalName);
    }

    public static NobilityBlock getBlock(String internalName) {
        NobilityBlock block = blocks.get(internalName);

        if (block == null) {
            throw new IllegalArgumentException(internalName + " is not a valid NobilityBlock!");
        }

        return block;
    }

    public static NobilityBlock getBlock(BlockData blockData) {
        for (NobilityBlock block : getBlocks()) {
            if (block.equalsBlock(blockData)) {
                return block;
            }
        }

        return null;
    }

    static boolean isNmsNobilityBlockCache(Object blockState, BooleanSupplier nmsFunction) {
        return isNmsNobilityBlockCache.computeIfAbsent(blockState, k -> nmsFunction.getAsBoolean());
    }

    static void save() {
        try {
            blocksConfig.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Unable to save config!");
            e.printStackTrace();
        }
    }
}
