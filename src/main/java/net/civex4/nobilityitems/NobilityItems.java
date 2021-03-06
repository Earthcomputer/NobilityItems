package net.civex4.nobilityitems;

import java.io.File;
import java.util.List;

import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class NobilityItems extends JavaPlugin {
    private static NobilityItems instance;

    @Override
    public void onEnable() {
        instance = this;

        PluginCommand nobilityCommand = getCommand("nobilityitems");
        assert nobilityCommand != null;
        nobilityCommand.setExecutor(new CommandListener());
        nobilityCommand.setTabCompleter(new CommandTabCompleter());

        ItemManager.init(new File(getDataFolder(), "items"), new File(getDataFolder(), "tags.yml"));
    }

    protected static NobilityItems getInstance() {
        return instance;
    }

    /**
     * Gets a NobilityItem from its DisplayName (what the
     * ItemStack itself is called). If multiple items have
     * the same display name this will just get the first
     * one that matches.
     * 
     * @param displayName The display name
     * @return NobilityItem, null if no DisplayName matches
     */
    public static NobilityItem getItemByDisplayName(String displayName) {
        return ItemManager.getItemByDisplayName(displayName);
    }

    /**
     * Gets a NobilityItem from its internal name (the one used
     * in the items.yml config section header). Throws an
     * IllegalArgumentException if the internal name does not
     * align with any NobilityItem!
     * 
     * @param internalName String
     * @return NobilityItem
     */
    public static NobilityItem getItemByName(String internalName) {
        return ItemManager.getItem(internalName);
    }

    /**
     * Attempts to get a NobilityItem from an ItemStack.
     * 
     * @param item ItemStack
     * @return NobilityItem, null if no valid NobilityItems for this ItemStack
     */
    public static NobilityItem getItem(ItemStack item) {
        return ItemManager.getItem(item);
    }

    /**
     * Returns a List of all loaded NobilityItems
     * 
     * @return List<NobilityItem>
     */
    public static List<NobilityItem> getItems() {
        return ItemManager.getItems();
    }

}
