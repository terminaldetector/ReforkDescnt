package com.terminaldetector.drmd.gate;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Reads the exact two native BDH leaf columns without submitting terrain or blocking either game thread. */
final class NativeBdhCubicMaterial {
    private static final String ROOT = "com.seibel.distanthorizons.";
    private static final int LEAF_WIDTH = 64;

    private final Method getWorld = type("core.api.internal.SharedApi").getMethod("getAbstractDhWorld");
    private final Method getLevels = type("core.world.IDhWorld").getMethod("getAllLoadedLevels");
    private final Class<?> level = type("core.level.IDhLevel");
    private final Method isCubic = level.getMethod("isCubic");
    private final Method getWrapper = level.getMethod("getLevelWrapper");
    private final Method getProvider = level.getMethod("getFullDataProvider");
    private final Method getDimension = type("core.wrapperInterfaces.world.ILevelWrapper").getMethod("getDimensionName");
    private final Method getSource = type("core.file.fullDatafile.V2.FullDataSourceProviderV2").getMethod("getAsync", long.class);
    private final Class<?> section = type("core.pos.DhSectionPos");
    private final byte detail = section.getField("SECTION_BLOCK_DETAIL_LEVEL").getByte(null);
    private final Method encode = section.getMethod("encodeContainingCubic", byte.class, int.class, int.class, int.class);
    private final Method getColumn = type("core.dataObjects.fullData.sources.FullDataSourceV2")
        .getMethod("getApiDataPointColumn", int.class, int.class);
    private final Class<?> point = type("api.objects.data.DhApiTerrainDataPoint");
    private final Field bottom = point.getField("bottomYBlockPos");
    private final Field top = point.getField("topYBlockPos");
    private final Field state = point.getField("blockStateWrapper");
    private final Method serialize = type("api.interfaces.block.IDhApiBlockStateWrapper").getMethod("getSerialString");

    NativeBdhCubicMaterial() throws ReflectiveOperationException {}

    CompletableFuture<List<String>> read(BlockPos lower, BlockPos upper) throws ReflectiveOperationException {
        Object world = getWorld.invoke(null);
        if (world != null) {
            for (Object candidate : (Iterable<?>) getLevels.invoke(world)) {
                if (!Boolean.TRUE.equals(isCubic.invoke(candidate))
                    || !"minecraft:overworld".equals(getDimension.invoke(getWrapper.invoke(candidate)))) continue;
                Object provider = getProvider.invoke(candidate);
                if (provider != null) return read(provider, lower).thenCombine(read(provider, upper), List::of);
            }
        }
        return CompletableFuture.completedFuture(List.of());
    }

    private CompletableFuture<String> read(Object provider, BlockPos pos) throws ReflectiveOperationException {
        long sectionPos = (long) encode.invoke(null, detail, pos.getX(), pos.getY(), pos.getZ());
        return ((CompletableFuture<?>) getSource.invoke(provider, sectionPos)).thenApply(source -> {
            if (source == null) return "unavailable";
            // The upstream data-source checkout must be closed even if the gate exits while a read is in flight.
            try (var ignored = (AutoCloseable) source) {
                var column = (List<?>) getColumn.invoke(source, Math.floorMod(pos.getX(), LEAF_WIDTH),
                    Math.floorMod(pos.getZ(), LEAF_WIDTH));
                int relativeY = Math.floorMod(pos.getY(), LEAF_WIDTH);
                for (Object entry : column) {
                    if (bottom.getInt(entry) <= relativeY && relativeY < top.getInt(entry)) {
                        String serialized = (String) serialize.invoke(state.get(entry));
                        return serialized.split("_STATE_", 2)[0];
                    }
                }
                return "empty";
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(ROOT + name);
    }
}
