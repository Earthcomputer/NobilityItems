package net.civex4.nobilityitems.impl;

import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.civex4.nobilityitems.NobilityBlock;
import net.civex4.nobilityitems.NobilityItems;
import net.civex4.nobilitypatch.CallbackKey;
import net.civex4.nobilitypatch.NobilityPatch;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Patches {
    private static final Class<?> blockData = MinecraftReflection.getMinecraftClass("BlockBase$BlockData");
    private static final Class<?> iBlockData = MinecraftReflection.getIBlockDataClass();
    private static final Class<?> blockPosition = MinecraftReflection.getBlockPositionClass();
    private static final MethodAccessor blockData_asIBlockData = Accessors.getMethodAccessor(FuzzyReflection.fromClass(blockData, true).getMethod(FuzzyMethodContract.newBuilder()
            .requireModifier(Modifier.ABSTRACT)
            .returnTypeExact(iBlockData)
            .parameterCount(0)
            .build()));
    private static final Class<?> craftBlockData = MinecraftReflection.getCraftBukkitClass("block.data.CraftBlockData");
    private static final MethodAccessor craftBlockData_fromData = Accessors.getMethodAccessor(FuzzyReflection.fromClass(craftBlockData).getMethodByParameters("fromData", iBlockData));
    private static final Class<?> chunkProviderServer = MinecraftReflection.getMinecraftClass("ChunkProviderServer");
    private static final MethodAccessor getChunkProviderServer = Accessors.getMethodAccessor(FuzzyReflection.fromClass(MinecraftReflection.getWorldServerClass()).getMethod(FuzzyMethodContract.newBuilder()
            .returnTypeExact(chunkProviderServer)
            .parameterCount(0)
            .build()));
    private static final MethodAccessor flagDirty = Accessors.getMethodAccessor(FuzzyReflection.fromClass(chunkProviderServer).getMethod(FuzzyMethodContract.newBuilder()
            .nameExact("flagDirty")
            .parameterExactArray(blockPosition)
            .build()));

    private static final Class<?> fluid = MinecraftReflection.getMinecraftClass("Fluid");
    private static final Method getFluidMethod = FuzzyReflection.fromClass(blockData).getMethodByParameters("getFluid", fluid, new Class[0]);
    private static final MethodAccessor getFluid = Accessors.getMethodAccessor(getFluidMethod);


    private static final EquivalentConverter<World> WORLD_CONVERTER = BukkitConverters.getWorldConverter();
    private static final EquivalentConverter<BlockPosition> BLOCKPOS_CONVERTER = BlockPosition.getConverter();
    private static final EquivalentConverter<WrappedBlockData> BLOCK_DATA_CONVERTER = BukkitConverters.getWrappedBlockDataConverter();

    public static void apply() {
        addNeighborPlacementCallback();
        addWaterloggedCallback();
    }

    private static void addNeighborPlacementCallback() {
        CallbackKey<OverrideNeighborPlacementDelegate> neighborPlacementCallback = NobilityPatch.registerCallback(OverrideNeighborPlacementDelegate.class, (state, _this, world, pos) -> {
            BlockData newData = (BlockData) craftBlockData_fromData.invoke(null, state);
            BlockData oldData = (BlockData) craftBlockData_fromData.invoke(null, blockData_asIBlockData.invoke(_this));
            World bukkitWorld = WORLD_CONVERTER.getSpecific(world);
            BlockPosition wrappedPos = BLOCKPOS_CONVERTER.getSpecific(pos);
            BlockData result = UnobtainableBlocks.overrideNeighborPlacement(bukkitWorld, new Location(null, wrappedPos.getX(), wrappedPos.getY(), wrappedPos.getZ()), oldData, newData);
            if (result == null) {
                return state;
            } else {
                if (!result.matches(newData)) {
                    Object chunkProviderServer = getChunkProviderServer.invoke(world);
                    flagDirty.invoke(chunkProviderServer, pos);
                }
                return BLOCK_DATA_CONVERTER.getGeneric(WrappedBlockData.createData(result));
            }
        });

        Class<?> enumDirection = MinecraftReflection.getMinecraftClass("EnumDirection");
        Class<?> generatorAccess = MinecraftReflection.getMinecraftClass("GeneratorAccess");
        Method method = FuzzyReflection.fromClass(blockData).getMethodByParameters("updateState", iBlockData, new Class[] {enumDirection, iBlockData, generatorAccess, blockPosition, blockPosition});
        NobilityPatch.transformMethod(method, visitor -> new MethodVisitor(Opcodes.ASM9, visitor) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.ARETURN) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mv.visitVarInsn(Opcodes.ALOAD, 3); // world
                    mv.visitVarInsn(Opcodes.ALOAD, 4); // pos
                    NobilityPatch.invokeCallback(mv, neighborPlacementCallback);
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(iBlockData));
                    mv.visitInsn(opcode);
                }
            }
        });
    }

    private static final Object EMPTY_FLUID = getFluid.invoke(BLOCK_DATA_CONVERTER.getGeneric(WrappedBlockData.createData(Material.AIR)));
    private static final Object WATER_FLUID = getFluid.invoke(BLOCK_DATA_CONVERTER.getGeneric(WrappedBlockData.createData(Material.WATER)));

    private static void addWaterloggedCallback() {
        CallbackKey<WaterloggedDelegate> waterloggedCallback = NobilityPatch.registerCallback(WaterloggedDelegate.class, _this -> {
            BlockData blockData = (BlockData) craftBlockData_fromData.invoke(null, blockData_asIBlockData.invoke(_this));
            NobilityBlock nobilityBlock = NobilityItems.getBlock(blockData);
            if (nobilityBlock == null) {
                return null;
            }
            return nobilityBlock.isWaterlogged() ? WATER_FLUID : EMPTY_FLUID;
        });

        NobilityPatch.transformMethod(getFluidMethod, visitor -> new MethodVisitor(Opcodes.ASM9, visitor) {
            private boolean visitedLabel = false;
            @Override
            public void visitLabel(Label label) {
                mv.visitLabel(label);
                if (!visitedLabel) {
                    visitedLabel = true;
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    NobilityPatch.invokeCallback(mv, waterloggedCallback);
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(fluid));
                    mv.visitInsn(Opcodes.DUP);
                    Label endIf = new Label();
                    mv.visitJumpInsn(Opcodes.IFNULL, endIf);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitLabel(endIf);
                    mv.visitInsn(Opcodes.POP);
                }
            }
        });
    }

    @FunctionalInterface
    public interface OverrideNeighborPlacementDelegate {
        @SuppressWarnings("unused")
        Object /*IBlockData*/ overrideNeighborPlacementState(Object /*IBlockData*/ state, Object /*BlockBase$BlockData*/ _this, Object /*GeneratorAccess*/ world, Object /*BlockPosition*/ pos);
    }

    @FunctionalInterface
    public interface WaterloggedDelegate {
        @SuppressWarnings("unused")
        Object /*Fluid*/ getFluid(Object /*BlockBase$BlockData*/ _this);
    }
}
