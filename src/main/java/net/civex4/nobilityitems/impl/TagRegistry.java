package net.civex4.nobilityitems.impl;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TagRegistry {

    private static final Class<?> TAG_REGISTRY = MinecraftReflection.getMinecraftClass("ITagRegistry");
    private static final Class<?> TAGS = MinecraftReflection.getMinecraftClass("Tags");
    private static final Class<?> TAG = MinecraftReflection.getMinecraftClass("Tag");
    private static final MethodAccessor CREATE_REGISTRY = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG_REGISTRY).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(TAG_REGISTRY)
            .parameterExactArray(TAGS, TAGS, TAGS, TAGS)
            .requireModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor GET_BLOCK_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG_REGISTRY).getMethod(FuzzyMethodContract.newBuilder()
            .nameExact("getBlockTags")
            .build()));
    private static final MethodAccessor GET_ITEM_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG_REGISTRY).getMethod(FuzzyMethodContract.newBuilder()
            .nameExact("getItemTags")
            .build()));
    private static final MethodAccessor GET_FLUID_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG_REGISTRY).getMethod(FuzzyMethodContract.newBuilder()
            .nameExact("getFluidTags")
            .build()));
    private static final MethodAccessor GET_ENTITY_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG_REGISTRY).getMethod(FuzzyMethodContract.newBuilder()
            .nameExact("getEntityTags")
            .build()));
    private static final MethodAccessor CREATE_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAGS).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(TAGS)
            .parameterExactArray(Map.class)
            .requireModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor GET_TAGS = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAGS).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(Map.class)
            .parameterCount(0)
            .banModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor GET_TAG_ID = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAGS).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(MinecraftReflection.getMinecraftKeyClass())
            .parameterExactArray(TAG)
            .banModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor TAG_VALUES = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG).getMethod(FuzzyMethodContract.newBuilder()
            .returnDerivedOf(Collection.class)
            .parameterCount(0)
            .banModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor CREATE_TAG = Accessors.getMethodAccessor(FuzzyReflection.fromClass(TAG).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(TAG)
            .parameterExactArray(Set.class)
            .requireModifier(Modifier.STATIC)
            .build()));

    private static final Class<?> ITEM = MinecraftReflection.getMinecraftClass("Item");
    private static final Class<?> MAGIC_NUMBERS = MinecraftReflection.getCraftBukkitClass("util.CraftMagicNumbers");
    private static final MethodAccessor ITEM_TO_MATERIAL = Accessors.getMethodAccessor(FuzzyReflection.fromClass(MAGIC_NUMBERS).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(Material.class)
            .parameterExactArray(ITEM)
            .requireModifier(Modifier.STATIC)
            .build()));
    private static final MethodAccessor MATERIAL_TO_ITEM = Accessors.getMethodAccessor(FuzzyReflection.fromClass(MAGIC_NUMBERS).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(ITEM)
            .parameterExactArray(Material.class)
            .requireModifier(Modifier.STATIC)
            .build()));
    private static final EquivalentConverter<Material> BLOCK_CONVERTER = BukkitConverters.getBlockConverter();
    private static final EquivalentConverter<EntityType> ENTITY_TYPE_CONVERTER = BukkitConverters.getEntityTypeConverter();
    private static final EquivalentConverter<MinecraftKey> MINECRAFT_KEY_CONVERTER = MinecraftKey.getConverter();

    private final Multimap<String, Material> blockTags = HashMultimap.create();
    private final Multimap<String, Material> itemTags = HashMultimap.create();
    private Object fluidTypes; // don't want to modify this
    private final Multimap<String, EntityType> entityTags = HashMultimap.create();

    private TagRegistry() {
    }

    public Multimap<String, Material> getBlockTags() {
        return blockTags;
    }

    public Multimap<String, Material> getItemTags() {
        return itemTags;
    }

    public Multimap<String, EntityType> getEntityTags() {
        return entityTags;
    }

    public static TagRegistry fromNms(Object nmsRegistry) {
        TagRegistry registry = new TagRegistry();
        registry.blockTags.putAll(nmsToTags(GET_BLOCK_TAGS.invoke(nmsRegistry), BLOCK_CONVERTER::getSpecific));
        registry.itemTags.putAll(nmsToTags(GET_ITEM_TAGS.invoke(nmsRegistry), nmsItem -> (Material) ITEM_TO_MATERIAL.invoke(null, nmsItem)));
        registry.fluidTypes = GET_FLUID_TAGS.invoke(nmsRegistry);
        registry.entityTags.putAll(nmsToTags(GET_ENTITY_TAGS.invoke(nmsRegistry), ENTITY_TYPE_CONVERTER::getSpecific));
        return registry;
    }

    @SuppressWarnings("unchecked")
    private static <T> Multimap<String, T> nmsToTags(Object nmsTags, Function<Object, T> nmsToValue) {
        Map<Object, Object> map = (Map<Object, Object>) GET_TAGS.invoke(nmsTags);
        Multimap<String, T> multimap = HashMultimap.create();
        map.forEach((nmsKey, nmsTag) -> {
            String key = MinecraftKey.fromHandle(nmsKey).getFullKey();
            Collection<Object> nmsValues = (Collection<Object>) TAG_VALUES.invoke(nmsTag);
            for (Object nmsValue : nmsValues) {
                multimap.put(key, nmsToValue.apply(nmsValue));
            }
        });
        return multimap;
    }
    
    public Object toNms() {
        return CREATE_REGISTRY.invoke(null,
                tagsToNms(blockTags, BLOCK_CONVERTER::getGeneric),
                tagsToNms(itemTags, item -> MATERIAL_TO_ITEM.invoke(null, item)),
                fluidTypes,
                tagsToNms(entityTags, ENTITY_TYPE_CONVERTER::getGeneric));
    }
    
    private static <T> Object tagsToNms(Multimap<String, T> tags, Function<T, Object> valueToNms) {
        Map<Object, Object> nmsIdToTag = new HashMap<>();
        for (String key : tags.keySet()) {
            String[] keyParts = key.split(":", 2);
            MinecraftKey mcKey = keyParts.length == 2 ? new MinecraftKey(keyParts[0], keyParts[1]) : new MinecraftKey(keyParts[0]);
            Object nmsKey = MINECRAFT_KEY_CONVERTER.getGeneric(mcKey);
            Object nmsTag = CREATE_TAG.invoke(null, tags.get(key).stream().map(valueToNms).collect(Collectors.toSet()));
            nmsIdToTag.put(nmsKey, nmsTag);
        }
        return CREATE_TAGS.invoke(null, nmsIdToTag);
    }

    public static StructureModifier<TagRegistry> getTagRegistryModifier(PacketContainer packetContainer) {
        return packetContainer.getModifier().withType(TAG_REGISTRY, CONVERTER);
    }

    public static final EquivalentConverter<TagRegistry> CONVERTER = Converters.ignoreNull(new EquivalentConverter<TagRegistry>() {
        @Override
        public Object getGeneric(TagRegistry specific) {
            return specific.toNms();
        }

        @Override
        public TagRegistry getSpecific(Object generic) {
            return TagRegistry.fromNms(generic);
        }

        @Override
        public Class<TagRegistry> getSpecificType() {
            return TagRegistry.class;
        }
    });
}
