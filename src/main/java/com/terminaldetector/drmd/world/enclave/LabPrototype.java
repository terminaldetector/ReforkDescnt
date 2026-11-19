package com.terminaldetector.drmd.world.enclave;

import com.terminaldetector.drmd.world.access.ServerBlockEdits;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** A compact, manually authored room and stepped service shaft in the live world. */
final class LabPrototype {
    private LabPrototype() {}

    static int build(ServerPlayerEntity author, BlockPos origin) {
        var world = author.getServerWorld();
        var access = WorldAccess.of(world);
        var blueprint = blueprint(origin);
        var edits = new ArrayList<ServerBlockEdits.Edit>();
        for (var entry : blueprint.entrySet()) {
            var at = entry.getKey();
            if (!WorldEditSafety.mayChange(world, at, author))
                throw new IllegalArgumentException("Lab cell is protected or unavailable: " + at.toShortString());
            var state = access.readIfLoaded(at).orElseThrow(() -> new IllegalArgumentException("Lab cell is unavailable"));
            if (state.equals(entry.getValue())) continue;
            if (!excavatable(state))
                throw new IllegalArgumentException("Lab would replace existing material at " + at.toShortString());
            edits.add(new ServerBlockEdits.Edit(at, new ServerBlockEdits.Cell(state, null),
                new ServerBlockEdits.Cell(entry.getValue(), null)));
        }
        if (edits.isEmpty()) return 0;
        var bounds = new Box(Vec3d.of(origin.add(-9, -1, -2)), Vec3d.of(origin.add(3, 10, 3)));
        if (world.getOtherEntities(null, bounds, Entity::isAlive).stream().anyMatch(entity -> {
            var box = entity.getBoundingBox();
            return edits.stream().anyMatch(edit -> box.intersects(new Box(edit.position())));
        })) throw new IllegalArgumentException("Move all occupants away from the work area");
        var result = access.modifyServerAuthoritative(edits);
        if (!result.committed())
            throw new IllegalArgumentException("Lab transaction " + result.status() + ": " + result.detail());
        return result.changed();
    }

    private static boolean excavatable(BlockState state) {
        return state.isAir() || state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE)
            || state.isOf(Blocks.TUFF) || state.isOf(Blocks.ANDESITE)
            || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.GRANITE);
    }

    private static Map<BlockPos, BlockState> blueprint(BlockPos origin) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 3; y++) {
                    boolean entry = x == -2 && z == 0 && y >= 0 && y < 3;
                    boolean shell = y == -1 || y == 3 || Math.abs(x) == 2 || Math.abs(z) == 2;
                    var material = shell && !entry
                            ? (y == 1 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_DEEPSLATE)
                            : Blocks.AIR;
                    cells.put(origin.add(x, y, z), material.getDefaultState());
                }
            }
        }
        cells.put(origin.add(0, 3, 0), Blocks.SEA_LANTERN.getDefaultState());
        cells.put(origin.east(), Blocks.IRON_BLOCK.getDefaultState());
        for (int step = 1; step <= 7; step++) {
            int x = -2 - step;
            cells.put(origin.add(x, step - 1, 0), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int clearance = 0; clearance <= 2; clearance++)
                cells.put(origin.add(x, step + clearance, 0), Blocks.AIR.getDefaultState());
            for (int side : new int[]{-1, 1}) {
                cells.put(origin.add(x, step, side), Blocks.POLISHED_DEEPSLATE.getDefaultState());
                cells.put(origin.add(x, step + 1, side), Blocks.SMOOTH_STONE.getDefaultState());
            }
        }
        return cells;
    }
}
