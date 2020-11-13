package net.civex4.nobilityitems.impl;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.civex4.nobilityitems.impl.block.ChunkDataListener;
import net.civex4.nobilityitems.impl.block.TagRegistryListener;
import org.bukkit.plugin.Plugin;

public class Protocol {
    public static ProtocolManager protocolManager;

    public static void init(Plugin plugin) {
        protocolManager = ProtocolLibrary.getProtocolManager();
        TagRegistryListener.register(plugin);
        ChunkDataListener.register(plugin);
    }
}
