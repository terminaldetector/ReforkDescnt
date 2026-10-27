package com.terminaldetector.drmd.world.compat;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional BCC 0.1.1 interoperability, using its public sky-source API. A removed sky
 * occluder can leave the persisted column height above the loaded source height.
 * Only FULL cubes are authoritative. No cube is loaded, generated or treated as air
 * by this repair. Work and retained watches are bounded even under mass destruction.
 */
public final class NativeCubicSkylight {
    private static final int MAX_COLUMNS = 4096, COLUMNS_PER_TICK = 32, MAX_CUBES_DOWN = 64;
    private static final Map<ServerWorld, Watches> WORLDS = new WeakHashMap<>();
    private static Api api;
    private static boolean initialized;

    private NativeCubicSkylight() {}

    public static void removed(ServerWorld world, BlockPos pos) {
        if (api() == null || !world.getDimension().hasSkyLight()) return;
        var watches = WORLDS.computeIfAbsent(world, ignored -> new Watches());
        long key = ((long) pos.getX() << 32) | (pos.getZ() & 0xffffffffL);
        watch(watches, key);
        if (watches.urgentKeys.add(key)) {
            if (watches.urgent.size() == MAX_COLUMNS) watches.urgentKeys.remove(watches.urgent.removeFirst());
            watches.urgent.addLast(key);
        }
    }

    private static void watch(Watches watches, long key) {
        if (watches.keys.add(key)) {
            if (watches.queue.size() == MAX_COLUMNS) watches.keys.remove(watches.queue.removeFirst());
            watches.queue.addLast(key);
        }
    }

    /** Native cube arrivals and column reopen can make a formerly unknown floor available. */
    public static void loaded(WorldChunk chunk) {
        if (api() == null || !(chunk.getWorld() instanceof ServerWorld world)
                || !world.getDimension().hasSkyLight()) return;
        var watches = WORLDS.computeIfAbsent(world, ignored -> new Watches());
        long key = chunk.getPos().toLong();
        if (watches.scanKeys.add(key)) {
            if (watches.scans.size() == 128) watches.scanKeys.remove(watches.scans.removeFirst());
            watches.scans.addLast(key);
        }
    }

    public static void tick(MinecraftServer server) {
        if (api() == null) return;
        for (ServerWorld world : server.getWorlds()) {
            var watches = WORLDS.get(world);
            if (watches == null) continue;
            // At most one reopened column is scanned per tick. Native cube loading can
            // call this repeatedly for one X/Z column; the queue deduplicates those calls.
            if (!watches.scans.isEmpty()) {
                long key = watches.scans.removeFirst();watches.scanKeys.remove(key);
                var pos = new ChunkPos(key);
                var full = com.terminaldetector.drmd.world.access.CubicAvailability.loadedColumn(world, pos.x, pos.z);
                if (full != null) {
                    scan(world, full, watches);
                }
            }
            if (api == null) return;
            var relight = new HashMap<WorldChunk, int[]>();
            int urgentCount = Math.min(COLUMNS_PER_TICK, watches.urgent.size());
            for (int n = 0; n < urgentCount; n++) {
                long key = watches.urgent.removeFirst();watches.urgentKeys.remove(key);
                if (repair(world, (int) (key >> 32), (int) key, relight)) watch(watches, key);
                if (api == null) return;
            }
            int count = Math.min(COLUMNS_PER_TICK - urgentCount, watches.queue.size());
            for (int n = 0; n < count; n++) {
                long key = watches.queue.removeFirst();
                if (repair(world, (int) (key >> 32), (int) key, relight)) watches.queue.addLast(key);
                else watches.keys.remove(key);
                if (api == null) return;
            }
            // One light task per affected column chunk, regardless of destroyed block count.
            for (var entry : relight.entrySet()) {
                var chunk = entry.getKey();
                var pos = chunk.getPos();
                var light = world.getChunkManager().getLightingProvider();
                light.propagateLight(pos);
                int from = entry.getValue()[0], to = entry.getValue()[1];
                light.enqueue(pos.x, pos.z).thenRun(() -> server.execute(() -> {
                    if (api == null || com.terminaldetector.drmd.world.access.CubicAvailability.loadedColumn(world, pos.x, pos.z) != chunk) return;
                    try {
                        Object cubic = api.cubic.invoke(world);
                        if (cubic == null) return;
                        Object state = api.column.invoke(chunk.getChunkSkyLight());
                        for (int index = from; index <= to; index++) if (isFull(state, index)) {
                            api.lightChanged.invoke(cubic, ChunkSectionPos.from(pos.x, world.sectionIndexToCoord(index), pos.z));
                        }
                    } catch (ReflectiveOperationException failure) {
                        DescentMod.LOGGER.error("[drmd cubic light] could not announce repaired native cube lighting", failure);
                    }
                }));
            }
        }
    }

    public static void close() { WORLDS.clear(); }

    private static void scan(ServerWorld world, WorldChunk chunk, Watches watches) {
        var sky = chunk.getChunkSkyLight();
        if (!api.ext.isInstance(sky)) return;
        try {
            Object state = api.column.invoke(sky);
            if (state == null) return;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int cached = (Integer) api.occlusion.invoke(state, x, z);
                if (cached <= world.getBottomY() || cached > world.getTopY()
                        || !isFull(state, world.getSectionIndex(cached - 1))) continue;
                int blockX = chunk.getPos().getStartX() + x, blockZ = chunk.getPos().getStartZ() + z;
                if (chunk.getBlockState(new BlockPos(blockX, cached - 1, blockZ)).isAir()) {
                    watch(watches, ((long) blockX << 32) | (blockZ & 0xffffffffL));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            DescentMod.LOGGER.error("[drmd cubic light] optional BCC sky-source scan failed; repair disabled", failure);
            api = null;WORLDS.clear();
        }
    }

    /** Return true while an unknown lower cube still bounds this watched column. */
    private static boolean repair(ServerWorld world, int x, int z, Map<WorldChunk, int[]> relight) {
        var full = com.terminaldetector.drmd.world.access.CubicAvailability.loadedColumn(world, x >> 4, z >> 4);
        if (full == null) return false;
        var sky = full.getChunkSkyLight();
        if (!api.ext.isInstance(sky)) return false; // Ordinary dimensions remain native vanilla.
        try {
            Object state = api.column.invoke(sky);
            if (state == null) return false;
            int localX = x & 15, localZ = z & 15;
            int cached = (Integer) api.occlusion.invoke(state, localX, localZ);
            if (cached <= world.getBottomY() || cached > world.getTopY()) return false;
            int top = cached - 1, index = world.getSectionIndex(top);
            // Unknown top: keep watching until the backend loads it, never read placeholder air.
            if (!isFull(state, index)) return true;
            var topPos = new BlockPos(x, top, z);
            if (!full.getBlockState(topPos).isAir()) return false;
            api.changed.invoke(sky, full, index);
            int loaded = (Integer) api.loaded.invoke(sky, localX, localZ);
            int bottomIndex = index;
            while (bottomIndex > 0 && index - bottomIndex < MAX_CUBES_DOWN - 1
                    && isFull(state, bottomIndex - 1)) bottomIndex--;
            int lowestCube = world.sectionIndexToCoord(bottomIndex);
            int target = NativeOcclusionBounds.target(cached, loaded, lowestCube);
            if (target < cached) {
                api.setOcclusion.invoke(state, localX, localZ, target);
                api.changed.invoke(sky, full, index);
                var light = world.getChunkManager().getLightingProvider();
                light.checkBlock(topPos);
                light.checkBlock(topPos.down());
                int min = bottomIndex, max = index;
                relight.compute(full, (ignored, old) -> old == null ? new int[]{min, max}
                        : new int[]{Math.min(old[0], min), Math.max(old[1], max)});
                DescentMod.LOGGER.debug("[drmd cubic light] repaired {},{} source {} -> {}, loaded {} floor {}",
                        x, z, cached, target, loaded, lowestCube * 16);
            }
            // A later native cube arrival can lower the conservative floor. Bounded watches
            // revisit these columns; they do not retain chunks or insert load tickets.
            return target > loaded;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            DescentMod.LOGGER.error("[drmd cubic light] optional BCC sky-source API failed; repair disabled", failure);
            api = null;
            WORLDS.clear();
            return false;
        }
    }

    private static boolean isFull(Object state, int index) throws ReflectiveOperationException {
        return "FULL".equals(String.valueOf(api.status.invoke(state, index)));
    }

    private static Api api() {
        if (!initialized) {
            initialized = true;
            if (FabricLoader.getInstance().isModLoaded("bettercubicchunks")) {
                try {
                    api = new Api();
                    DescentMod.LOGGER.info("[drmd cubic light] bounded native sky-source repair enabled");
                } catch (ReflectiveOperationException failure) {
                    DescentMod.LOGGER.error("[drmd cubic light] unsupported optional BCC sky-source API; repair disabled", failure);
                }
            }
        }
        return api;
    }

    private static final class Watches {
        final ArrayDeque<Long> queue = new ArrayDeque<>();
        final HashSet<Long> keys = new HashSet<>();
        final ArrayDeque<Long> scans = new ArrayDeque<>();
        final HashSet<Long> scanKeys = new HashSet<>();
        final ArrayDeque<Long> urgent = new ArrayDeque<>();
        final HashSet<Long> urgentKeys = new HashSet<>();
    }

    private static final class Api {
        final Class<?> ext = Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
        final Class<?> state = Class.forName("dev.fallingcloud.bettercubicchunks.chunk.CubicColumnState");
        final Class<?> server = Class.forName("dev.fallingcloud.bettercubicchunks.server.ICubicServerLevel");
        final Class<?> level = Class.forName("dev.fallingcloud.bettercubicchunks.server.CubicServerLevel");
        final Method column = ext.getMethod("bcc$columnState"),
                loaded = ext.getMethod("bcc$loadedSourceY", int.class, int.class),
                changed = ext.getMethod("bcc$sectionChanged", Chunk.class, int.class),
                occlusion = state.getMethod("occlusionHeight", int.class, int.class),
                setOcclusion = state.getMethod("setOcclusion", int.class, int.class, int.class),
                status = state.getMethod("status", int.class),
                cubic = server.getMethod("bcc$cubic"),
                lightChanged = level.getMethod("onSectionLightChanged", ChunkSectionPos.class);
        Api() throws ReflectiveOperationException {}
    }
}
