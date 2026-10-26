package com.terminaldetector.drmd.world.geometry;

import net.minecraft.util.shape.ArrayVoxelShape;
import net.minecraft.util.shape.BitSetVoxelSet;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import java.util.List;

/** Conservative raster on a fixed grid: never create a Cartesian product of warped coordinates. */
public final class BoundedHybridShape extends ArrayVoxelShape {
    public static final int CELLS=32;
    public static final double PITCH=1.0/16,MIN=-.5,MAX=1.5;
    private BoundedHybridShape(BitSetVoxelSet cells) {super(cells,coordinates(),coordinates(),coordinates());}
    private static double[] coordinates() {
        var points=new double[CELLS+1];
        for(int i=0;i<=CELLS;i++)points[i]=MIN+i*PITCH;
        return points;
    }
    public static VoxelShape from(List<HybridMesh.Bounds> boxes) {
        if(boxes.size()>64)throw new IllegalArgumentException("Hybrid block has more than 64 occupied quarter cells");
        if(boxes.isEmpty())return VoxelShapes.empty();
        var cells=new BitSetVoxelSet(CELLS,CELLS,CELLS);
        for(var b:boxes) {
            int x0=lower(b.minX()),y0=lower(b.minY()),z0=lower(b.minZ());
            int x1=upper(b.maxX()),y1=upper(b.maxY()),z1=upper(b.maxZ());
            for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++)cells.set(x,y,z);
        }
        return new BoundedHybridShape(cells);
    }
    private static int lower(double value) {check(value);return (int)Math.floor((value-MIN)/PITCH);}
    private static int upper(double value) {check(value);return (int)Math.ceil((value-MIN)/PITCH);}
    private static void check(double value) {
        if(!Double.isFinite(value)||value<MIN||value>MAX)throw new IllegalArgumentException("Hybrid bounds exceed fixed collider domain");
    }
}
