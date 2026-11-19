package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubeLifecycle;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Torch descriptors are server-owned; subscriptions observe FULL cubes without acquiring tickets. */
final class GravityTorchFields {
    private static final int MAX_WATCHED_CUBES = 1024;
    private static final class Cube {
        final Map<BlockPos, GravityFields.Field> sources = new LinkedHashMap<>();
        CubeLifecycle.Subscription subscription;
        boolean attempted;
    }
    private static final Map<ServerWorld, Map<D6ChunkPos, Cube>> WORLDS = new WeakHashMap<>();
    private static boolean registered;
    private GravityTorchFields() {}

    static void publish(ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Torch fields require the world thread");
        if (!registered) {
            registered = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server, closed) -> close(closed));
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                for (var closed : java.util.List.copyOf(WORLDS.keySet())) if (closed.getServer() == server) close(closed);
            });
        }
        var key = D6ChunkPos.ofBlock(pos.getX(), pos.getY(), pos.getZ());
        var cubes = WORLDS.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        var cube = cubes.get(key);
        if (cube == null && cubes.size() < MAX_WATCHED_CUBES) {cube = new Cube(); cubes.put(key, cube);}
        var field = descriptor(world, pos, state);
        if (cube != null) {
            cube.sources.put(pos.toImmutable(), field);
            if (!cube.attempted) {
                cube.attempted = true;
                var reference = new WeakReference<>(world); var owned = cube;
                cube.subscription = CubeLifecycle.watch(world, key, event -> {
                    var owner = reference.get();
                    if (owner == null) return;
                    for (var source : java.util.List.copyOf(owned.sources.values())) {
                        if (event.change() == CubeLifecycle.Change.AVAILABLE) current(owner, source);
                        else GravityFields.remove(source.id());
                    }
                    if (event.change() == CubeLifecycle.Change.CLOSED) {
                        var active = WORLDS.get(owner);
                        if (active != null) {active.remove(key, owned); if (active.isEmpty()) WORLDS.remove(owner);}
                    }
                }).orElse(null);
                if (cubes.get(key) != cube && cube.subscription != null) cube.subscription.close();
            }
        }
        // The existing persistent scheduled torch tick is the bounded fallback when the shared
        // subscription budget is occupied. Gameplay samples always validate the source as well.
        current(world, field);
    }

    static void remove(ServerWorld world, BlockPos pos) {
        GravityFields.remove(GravityFieldIds.torch(world.getRegistryKey(), pos));
        var cubes = WORLDS.get(world); if (cubes == null) return;
        var key = D6ChunkPos.ofBlock(pos.getX(), pos.getY(), pos.getZ());
        var cube = cubes.get(key); if (cube == null) return;
        cube.sources.remove(pos);
        if (cube.sources.isEmpty()) {
            cubes.remove(key, cube);
            if (cube.subscription != null) cube.subscription.close();
            if (cubes.isEmpty()) WORLDS.remove(world);
        }
    }

    static GravityFields.Field current(ServerWorld world, GravityFields.Field field) {
        if (!world.getRegistryKey().equals(field.worldKey())
            || !field.id().equals(GravityFieldIds.torch(field.worldKey(), field.origin()))) return field;
        var access = WorldAccess.of(world); var pos = field.origin();
        var cube = D6ChunkPos.ofBlock(pos.getX(), pos.getY(), pos.getZ());
        if (!access.isVolumeAvailable(cube)) {GravityFields.remove(field.id()); return null;}
        var actual = access.readIfLoaded(pos);
        if (actual.isEmpty() || !(actual.orElseThrow().getBlock() instanceof GravityTorchBlock)) {
            remove(world, pos); return null;
        }
        var fresh = descriptor(world, pos, actual.orElseThrow());
        GravityFields.put(fresh);
        return fresh;
    }

    private static void close(ServerWorld world) {
        var cubes = WORLDS.remove(world);
        if (cubes != null) for (var cube : java.util.List.copyOf(cubes.values())) {
            for (var source : cube.sources.values()) GravityFields.remove(source.id());
            if (cube.subscription != null) cube.subscription.close();
        }
        // Includes sources that used the native scheduled-tick fallback when watch budgets were full.
        for (var field : GravityFields.all()) if (world.getRegistryKey().equals(field.worldKey())
            && field.id().equals(GravityFieldIds.torch(field.worldKey(), field.origin()))) GravityFields.remove(field.id());
    }

    private static GravityFields.Field descriptor(ServerWorld world, BlockPos pos, BlockState state) {
        var down = Vec3d.of(state.get(GravityTorchBlock.FACING).getOpposite().getVector());
        return new GravityFields.Field(GravityFieldIds.torch(world.getRegistryKey(), pos), world.getRegistryKey(), pos,
            down, GravityTorchBlock.RADIUS, GravityTorchBlock.POWER, FieldShape.SPHERE, "Gravity Torch", true);
    }
}
