package com.terminaldetector.drmd.world.cubic;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.d6.D6Cube;
import com.terminaldetector.drmd.world.DrmdServerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Cubic adapter boundary. Each dimension/space owns independent 16³ snapshots and files.
 * Only the adapter reads vanilla columns. It never generates/loads a chunk to satisfy a request.
 * This is not a replacement for vanilla's light engine, tickets or client chunk renderer yet.
 */
public final class CubicWorldSystem {
    private static final Map<ServerWorld, Space> SPACES = new ConcurrentHashMap<>();
    private static final int CAP = 512, PER_TICK = 2;
    private CubicWorldSystem() {}

    private static final class Space {
        final Map<Long, D6Cube<BlockState>> live = new LinkedHashMap<>(16, .75f, true);
        final Set<Long> dirty = new HashSet<>();
        final Object liveLock = new Object();
        final Map<Long, D6Cube<BlockState>> pending = new ConcurrentHashMap<>();
        final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "drmd-cubes-io"); thread.setDaemon(true); return thread;
        });
        final Path root;
        volatile boolean writing;
        Space(Path root) { this.root = root; }
        Path path(long key) {
            D6ChunkPos p = D6ChunkPos.fromLong(key);
            return root.resolve(p.x() + "_" + p.y() + "_" + p.z() + ".nbt");
        }
        void drain() {
            for (var entry : pending.entrySet()) {
                Path path = path(entry.getKey());
                try {
                    Files.createDirectories(root);
                    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                    NbtIo.writeCompressed(CubicBlockCodec.write(entry.getValue()), temp);
                    try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
                    pending.remove(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    DescentMod.LOGGER.error("Cube {} could not be saved", path, e);
                    break; // Backpressure; keep the snapshot and retry on the next scheduled flush.
                }
            }
        }
    }
    private static Space space(ServerWorld world) {
        return SPACES.computeIfAbsent(world, w -> new Space(w.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("drmd/cubes").resolve(w.getRegistryKey().getValue().getNamespace())
            .resolve(w.getRegistryKey().getValue().getPath())));
    }
    public static void changed(ServerWorld world, BlockPos pos) {
        Space s = SPACES.get(world);
        if (s == null) return;
        long key = D6ChunkPos.ofBlock(pos.getX(), pos.getY(), pos.getZ()).asLong();
        synchronized (s.liveLock) {
            if (s.live.containsKey(key)) s.dirty.add(key);
        }
    }
    public static void tick(MinecraftServer server) {
        if (!DrmdServerConfig.cubicSnapshots) return;
        for (ServerWorld world : server.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            Space s = space(world);
            // Failed/slow disk must not produce an unbounded write queue.
            if (s.pending.size() < CAP) {
                Map<Long, Integer> wanted = new HashMap<>();
                for (var player : world.getPlayers()) {
                    D6ChunkPos centre = D6ChunkPos.ofBlock(player.getBlockX(), player.getBlockY(), player.getBlockZ());
                    for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                        D6ChunkPos pos = centre.offset(dx, dy, dz);
                        if (pos.minBlockY() < world.getBottomY() || pos.minBlockY() >= world.getTopY()) continue;
                        long key = pos.asLong();
                        if (isMissingOrDirty(s, key))
                            wanted.merge(key, dx * dx + dy * dy + dz * dz, Math::min);
                    }
                }
                int done = 0;
                for (long key : wanted.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList()) {
                    D6ChunkPos p = D6ChunkPos.fromLong(key);
                    var chunk = world.getChunkManager().getChunk(p.x(), p.z(), ChunkStatus.FULL, false);
                    if (!(chunk instanceof WorldChunk wc)) continue;
                    D6Cube<BlockState> cube = snapshot(wc, p.y());
                    synchronized (s.liveLock) {
                        s.live.put(key, cube);
                        s.dirty.remove(key);
                        while (s.live.size() > CAP) {
                            long oldest = s.live.keySet().iterator().next();
                            s.live.remove(oldest);
                            s.dirty.remove(oldest);
                        }
                    }
                    s.pending.put(key, cube);
                    if (++done >= PER_TICK || s.pending.size() >= CAP) break;
                }
            }
            // At most one task per second; snapshots coalesce by key while the worker writes.
            if (server.getTicks() % 20 == 0 && !s.pending.isEmpty() && !s.writing) {
                s.writing = true;
                s.io.execute(() -> { try { s.drain(); } finally { s.writing = false; } });
            }
        }
    }
    private static boolean isMissingOrDirty(Space space, long key) {
        synchronized (space.liveLock) {
            return !space.live.containsKey(key) || space.dirty.contains(key);
        }
    }
    private static D6Cube<BlockState> snapshot(WorldChunk chunk, int sectionY) {
        var section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
        D6Cube<BlockState> cube = new D6Cube<>(Blocks.AIR.getDefaultState());
        if (!section.isEmpty()) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            cube.set(x, y, z, section.getBlockState(x, y, z));
        return cube;
    }
    /** Historical cube read on the IO worker; never mistaken for authoritative live terrain. */
    public static CompletableFuture<D6Cube<BlockState>> readSaved(ServerWorld world, D6ChunkPos pos) {
        Space s = SPACES.get(world);
        if (s == null) return CompletableFuture.completedFuture(null);
        D6Cube<BlockState> live;
        synchronized (s.liveLock) {
            live = s.live.get(pos.asLong());
        }
        if (live != null) return CompletableFuture.completedFuture(live.copy());
        try {
            return CompletableFuture.supplyAsync(() -> {
                D6Cube<BlockState> queued = s.pending.get(pos.asLong());
                if (queued != null) return queued.copy();
                try {
                    Path path = s.path(pos.asLong());
                    return Files.exists(path)
                            ? CubicBlockCodec.read(NbtIo.readCompressed(path, NbtSizeTracker.of(2 * 1024 * 1024)))
                            : null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, s.io);
        } catch (RejectedExecutionException closed) {
            return CompletableFuture.failedFuture(closed);
        }
    }
    public static int residentCount(ServerWorld world) {
        Space s = SPACES.get(world);
        if (s == null) return 0;
        synchronized (s.liveLock) { return s.live.size(); }
    }
    public static void close() {
        List<Space> spaces = List.copyOf(SPACES.values());
        for (Space s : spaces) {
            try {
                s.io.execute(s::drain);
                s.io.shutdown();
            } catch (RejectedExecutionException alreadyClosed) {
                // A repeated lifecycle callback has already queued the final drain.
            }
        }
        for (Space s : spaces) {
            try {
                if (!s.io.awaitTermination(30, TimeUnit.SECONDS)) DescentMod.LOGGER.error("Cubic snapshot IO still draining after shutdown timeout");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        SPACES.clear();
    }
}
