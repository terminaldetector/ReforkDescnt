package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.geometry.HybridTerrain;
import com.terminaldetector.drmd.world.geometry.NaturalGeometry;
import com.terminaldetector.drmd.world.geometry.OrganicBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A coarse snapshot of the server's accepted client-side cube data. */
final class TerrainMapVolume {
    static final int CELL = 4;
    private static final int WIDTH = 17;
    private static final int HEIGHT = 13;
    private static final int COUNT = WIDTH * WIDTH * HEIGHT;
    // At most 16,384 block-state reads in a tick, even when every sampled cell is empty.
    private static final int CELLS_PER_TICK = 256;

    enum Material {
        UNKNOWN, FREE, NATURAL, ENGINEERED, LIQUID;

        boolean occupied() {
            return this != UNKNOWN && this != FREE;
        }
    }

    record Cell(int x, int y, int z, Material material) {}
    record Snapshot(Box region, List<Cell> surface, int unknownPercent, int freePercent) {}

    private final int minX;
    private final int minY;
    private final int minZ;
    private final Material[] cells = new Material[COUNT];
    private int scanned;

    TerrainMapVolume(World world, BlockPos center, int altitudeOffset) {
        minX = align(center.getX()) - (WIDTH / 2) * CELL;
        minY = Math.max(world.getBottomY(), Math.min(world.getTopY() - HEIGHT * CELL,
                align(center.getY() + altitudeOffset) - (HEIGHT / 2) * CELL));
        minZ = align(center.getZ()) - (WIDTH / 2) * CELL;
    }

    private static int align(int coordinate) {
        return Math.floorDiv(coordinate, CELL) * CELL;
    }

    boolean advance(World world) {
        Map<D6ChunkPos, Optional<WorldChunk>> available = new HashMap<>();
        BlockPos.Mutable block = new BlockPos.Mutable();
        int end = Math.min(COUNT, scanned + CELLS_PER_TICK);
        while (scanned < end) {
            int x = minX + scanned % WIDTH * CELL;
            int z = minZ + scanned / WIDTH % WIDTH * CELL;
            int y = minY + scanned / (WIDTH * WIDTH) * CELL;
            D6ChunkPos cube = D6ChunkPos.ofBlock(x, y, z);
            WorldChunk column = available.computeIfAbsent(cube, pos -> availableColumn(world, pos)).orElse(null);
            cells[scanned++] = column == null ? Material.UNKNOWN : sample(column, block, x, y, z);
        }
        return scanned == COUNT;
    }

    private static Optional<WorldChunk> availableColumn(World world, D6ChunkPos cube) {
        if (!CubicAvailability.available(world, cube)) return Optional.empty();
        return Optional.ofNullable(CubicAvailability.loadedColumn(world, cube.x(), cube.z()));
    }

    private static Material sample(WorldChunk column, BlockPos.Mutable block, int x, int y, int z) {
        boolean liquid = false;
        for (int dy = 0; dy < CELL; dy++) {
            for (int dz = 0; dz < CELL; dz++) {
                for (int dx = 0; dx < CELL; dx++) {
                    var state = column.getBlockState(block.set(x + dx, y + dy, z + dz));
                    if (state.isAir()) continue;
                    if (state.isReplaceable()) {
                        liquid |= !state.getFluidState().isEmpty();
                        continue;
                    }
                    Object shape = column.getBlockEntityRenderData(block);
                    boolean natural = shape instanceof HybridTerrain.Data data ? data.organic()
                            : state.getBlock() instanceof OrganicBlock
                            || NaturalGeometry.applies(state) && NaturalGeometry.usesImplicitTerrain(column);
                    return natural ? Material.NATURAL : Material.ENGINEERED;
                }
            }
        }
        return liquid ? Material.LIQUID : Material.FREE;
    }

    Snapshot finish() {
        List<Cell> surface = new ArrayList<>();
        int unknown = 0;
        int free = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    Material material = at(x, y, z);
                    if (material == Material.UNKNOWN) unknown++;
                    if (material == Material.FREE) free++;
                    if (material.occupied() && exposed(x, y, z)) {
                        surface.add(new Cell(minX + x * CELL, minY + y * CELL, minZ + z * CELL, material));
                    }
                }
            }
        }
        var region = new Box(minX, minY, minZ, minX + WIDTH * CELL, minY + HEIGHT * CELL, minZ + WIDTH * CELL);
        return new Snapshot(region, List.copyOf(surface), unknown * 100 / COUNT, free * 100 / COUNT);
    }

    private boolean exposed(int x, int y, int z) {
        return !at(x - 1, y, z).occupied() || !at(x + 1, y, z).occupied()
                || !at(x, y - 1, z).occupied() || !at(x, y + 1, z).occupied()
                || !at(x, y, z - 1).occupied() || !at(x, y, z + 1).occupied();
    }

    private Material at(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= WIDTH) return Material.UNKNOWN;
        return cells[(y * WIDTH + z) * WIDTH + x];
    }
}
