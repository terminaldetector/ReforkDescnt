package com.terminaldetector.drmd.gate;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import com.terminaldetector.drmd.world.geometry.HybridMesh;

/** Evidence from the real chunk rebuild path, not a second foreground terrain renderer. */
public final class CubicTerrainCheck {
    public static final AtomicInteger blocks=new AtomicInteger(),quads=new AtomicInteger(),warped=new AtomicInteger();
    private static final ThreadLocal<Boolean> sample=ThreadLocal.withInitial(()->false);
    public static void begin(BlockState state,BlockPos pos) {
        boolean fixture=state.isOf(Blocks.GRASS_BLOCK) && pos.getX()>=14 && pos.getX()<=18
            && pos.getZ()>=6 && pos.getZ()<=10 && pos.getY()>=2156 && pos.getY()<=2161;
        sample.set(fixture);if(fixture)blocks.incrementAndGet();
    }
    public static void end(){sample.set(false);}
    public static void face(HybridMesh.Face face) {
        if(!sample.get())return;
        quads.incrementAndGet();
        for(var v:face.vertices()) {
            var p=v.position();
            if(offGrid(p.x()) || offGrid(p.y()) || offGrid(p.z()))warped.incrementAndGet();
        }
    }
    private static boolean offGrid(double x){return Math.abs(x*4-Math.rint(x*4))>.01;}
}
