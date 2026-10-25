package com.terminaldetector.drmd.world.edit;

import java.util.function.BiPredicate;

/** Conservative chunk-grid traversal; sampling can miss a tiny unloaded wedge at a corner. */
public final class LoadedChunkRay {
    private static final double CELL=16,MAX_LENGTH=256,MAX_COORD=30_000_000,EPS=1e-12;
    private LoadedChunkRay() {}
    public static double fraction(double x0,double z0,double x1,double z1,BiPredicate<Integer,Integer> loaded) {
        if(!Double.isFinite(x0) || !Double.isFinite(z0) || !Double.isFinite(x1) || !Double.isFinite(z1)
            || Math.abs(x0)>MAX_COORD || Math.abs(z0)>MAX_COORD || Math.abs(x1)>MAX_COORD || Math.abs(z1)>MAX_COORD)return 0;
        double dx=x1-x0,dz=z1-z0,length=Math.hypot(dx,dz);
        if(length>MAX_LENGTH)return 0;
        int x=(int)Math.floor(x0/CELL),z=(int)Math.floor(z0/CELL);
        if(!loaded.test(x,z))return 0;
        int sx=dx==0?0:dx>0?1:-1,sz=dz==0?0:dz>0?1:-1;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*CELL:x*CELL)-x0)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*CELL:z*CELL)-z0)/dz;
        double stepX=sx==0?Double.POSITIVE_INFINITY:CELL/Math.abs(dx),stepZ=sz==0?Double.POSITIVE_INFINITY:CELL/Math.abs(dz);
        for(int crossing=0;crossing<64;crossing++) {
            double t=Math.min(tx,tz);
            if(t>1)return 1;
            boolean crossX=tx<=tz+EPS,crossZ=tz<=tx+EPS;
            // At a corner include both side neighbours, as well as the diagonal chunk.
            if(crossX && !loaded.test(x+sx,z) || crossZ && !loaded.test(x,z+sz)
                || crossX && crossZ && !loaded.test(x+sx,z+sz))return Math.max(0,t-1e-6/Math.max(1,length));
            if(crossX){x+=sx;tx+=stepX;}
            if(crossZ){z+=sz;tz+=stepZ;}
        }
        return 0;
    }
}
