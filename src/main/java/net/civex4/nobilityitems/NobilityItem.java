package net.civex4.nobilityitems;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A custom item
 * 
 * @author KingVictoria
 */
public class NobilityItem {
    private final String internalName;
    private final String displayName;
    private Material material;
    private final String model;
    private final int customModelData;
    private final List<String> lore;
    private final boolean hasLore;
    private NobilityBlock block;

    NobilityItem(String id, String displayName, Material material, List<String> lore, String model) {
        this.internalName = id;
        this.displayName = displayName;
        this.material = material;
        this.lore = lore;
        this.model = model;
        this.customModelData = ItemManager.getModelData(material, model);

        hasLore = lore != null;
    }

    void setBlock(NobilityBlock block) {
        this.block = block;
        Material blockMaterial = block.getBlockData().getMaterial();
        if (blockMaterial.isItem()) {
            this.material = blockMaterial;
        } else {
            this.material = Material.STRUCTURE_VOID;
        }
    }

    public boolean hasLore() {
        return hasLore;
    }

    public String getInternalName() {
        return internalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasCustomModel() {
        return model != null;
    }

    public String getModel() {
        return model;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public Material getMaterial() {
        return material;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean hasBlock() {
        return block != null;
    }

    public NobilityBlock getBlock() {
        return block;
    }

    /**
     * Creates an ItemStack of this NobilityItem
     * 
     * @param amount int amount of the ItemStack
     * 
     * @return ItemStack
     */
    public ItemStack getItemStack(int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(amount);
        ItemMeta meta = NobilityItems.getInstance().getServer().getItemFactory().getItemMeta(material);
        assert meta != null;
        meta.setDisplayName(displayName);
        if (customModelData > -1)
            meta.setCustomModelData(customModelData);
        if (lore != null)
            meta.setLore(lore);
        if (block != null && material == block.getBlockData().getMaterial() && meta instanceof BlockDataMeta)
            ((BlockDataMeta) meta).setBlockData(block.getBlockData());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Will return true if the ItemStack is one that fits the one described by this NobilityItem.
     *
     * @param item The item stack to test
     */
    public boolean equalsStack(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        assert meta != null;

        if (!meta.hasDisplayName()) {
            return false;
        }
        if (item.getType() != material || !meta.getDisplayName().equals(displayName)) {
            return false;
        }
        if (!Objects.equals(lore, meta.getLore())) {
            return false;
        }

        return customModelData < 0 || meta.getCustomModelData() == customModelData;

    }

    @Override
    public int hashCode() {
        return internalName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof NobilityItem)) return false;
        NobilityItem that = (NobilityItem) o;
        return this.internalName.equals(that.internalName);
    }
}
