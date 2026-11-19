package com.terminaldetector.drmd.pickup;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coalesce native loot from the same two-block cell of a single blast before it becomes entities. */
public final class BlastBlockDrops {
    private record Drop(BlockPos position, ItemStack stack) {}

    private static final ThreadLocal<BlastBlockDrops> ACTIVE = new ThreadLocal<>();
    private final ServerWorld world;
    private final Map<BlockPos, List<Drop>> cells = new LinkedHashMap<>();
    private BlockPos cutting;

    private BlastBlockDrops(ServerWorld world) {
        this.world = world;
    }

    public static void breakBlocks(ServerWorld world, List<BlockPos> positions, Entity breaker) {
        if (positions.size() < 2 || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)) {
            for (var pos : positions) world.breakBlock(pos, true, breaker);
            return;
        }
        var batch = new BlastBlockDrops(world);
        var previous = ACTIVE.get();
        ACTIVE.set(batch);
        try {
            for (var pos : positions) {
                batch.cutting = pos;
                world.breakBlock(pos, true, breaker);
            }
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
            batch.flush();
        }
    }

    public static boolean capture(World world, BlockPos pos, ItemStack stack) {
        var batch = ACTIVE.get();
        if (batch == null || batch.world != world || !pos.equals(batch.cutting)
            || stack.isEmpty() || stack.getMaxCount() <= 1 || stack.getCount() > stack.getMaxCount()
            || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)) return false;
        batch.collect(pos, stack);
        return true;
    }

    private void collect(BlockPos pos, ItemStack stack) {
        var cell = new BlockPos(pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);
        var drops = cells.computeIfAbsent(cell, key -> new ArrayList<>());
        int count = stack.getCount();
        for (var drop : drops) {
            var existing = drop.stack();
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) continue;
            int move = Math.min(count, existing.getMaxCount() - existing.getCount());
            existing.increment(move);
            count -= move;
            if (count == 0) return;
        }
        drops.add(new Drop(pos.toImmutable(), stack.copyWithCount(count)));
    }

    private void flush() {
        for (var drops : cells.values()) {
            for (var drop : drops) Block.dropStack(world, drop.position(), drop.stack());
        }
    }
}
