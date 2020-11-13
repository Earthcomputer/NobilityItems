package net.civex4.nobilityitems.impl.block;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.civex4.nobilityitems.impl.Protocol;
import net.civex4.nobilityitems.impl.TagRegistry;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public class TagRegistryListener {
    public static void register(Plugin plugin) {
        Protocol.protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.TAGS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                TagRegistry.getTagRegistryModifier(event.getPacket()).modify(0, tagRegistry -> {
                    assert tagRegistry != null;
                    tagRegistry.getBlockTags().put("minecraft:impermeable", Material.VOID_AIR);
                    tagRegistry.getBlockTags().put("minecraft:impermeable", Material.COBBLESTONE_WALL);
                    return tagRegistry;
                });
            }
        });
    }
}
