package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.block.*;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** The same four in-plane neighbours control the frame and the reflection aperture. */
public final class MirrorEdges {
    private MirrorEdges() {}
    public static final BooleanProperty[] FRAME = {
        BooleanProperty.of("frame_u_min"), BooleanProperty.of("frame_u_max"),
        BooleanProperty.of("frame_v_min"), BooleanProperty.of("frame_v_max")
    };
    public static Direction[] directions(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
            case Y -> new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
            case Z -> new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
        };
    }
    public static boolean reflective(BlockState state) {
        return state.getBlock() instanceof MirrorBlock || state.getBlock() instanceof ChargedMirrorBlock
                && !state.get(ChargedMirrorBlock.LINKED);
    }
    public static boolean joins(BlockState a, BlockState b) {
        return reflective(a) && reflective(b) && a.getBlock() == b.getBlock()
                && a.get(Properties.FACING) == b.get(Properties.FACING);
    }
    /** Reconcile legacy blockstates as chunks arrive, without loading neighbouring chunks. */
    public static void refresh(World world, BlockPos pos, BlockState state) {
        var updated=update(state,world,pos);
        if(updated!=state)world.setBlockState(pos,updated,Block.NOTIFY_ALL);
    }
    public static BlockState update(BlockState state, BlockView world, BlockPos pos) {
        var directions = directions(state.get(Properties.FACING));
        for (int i=0;i<4;i++) {
            var neighbour=pos.offset(directions[i]);
            boolean loaded=!(world instanceof World w) || w.isChunkLoaded(neighbour);
            state=state.with(FRAME[i], !loaded || !joins(state,world.getBlockState(neighbour)));
        }
        return state;
    }
}
