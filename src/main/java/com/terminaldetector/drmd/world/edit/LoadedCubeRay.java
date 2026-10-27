package com.terminaldetector.drmd.world.edit;

/** Supercover XYZ traversal: a ray touching an edge/corner checks every touched cube. */
public final class LoadedCubeRay {
    @FunctionalInterface public interface Available {boolean test(int x,int y,int z);}
    private static final double EPS=1e-12;
    private LoadedCubeRay() {}
    public static double fraction(double x0,double y0,double z0,double x1,double y1,double z1,Available available) {
        double[] start={x0,y0,z0},end={x1,y1,z1},delta={x1-x0,y1-y0,z1-z0};
        int[] cell=new int[3],sign=new int[3];double[] next=new double[3],step=new double[3];
        double length=Math.sqrt(delta[0]*delta[0]+delta[1]*delta[1]+delta[2]*delta[2]);
        if(!Double.isFinite(length) || length>256)return 0;
        for(int a=0;a<3;a++) {
            if(!Double.isFinite(start[a]) || !Double.isFinite(end[a]) || Math.abs(start[a])>30_000_000 || Math.abs(end[a])>30_000_000)return 0;
            cell[a]=(int)Math.floor(start[a]/16);sign[a]=delta[a]==0?0:delta[a]>0?1:-1;
            next[a]=sign[a]==0?Double.POSITIVE_INFINITY:((cell[a]+(sign[a]>0?1:0))*16.0-start[a])/delta[a];
            step[a]=sign[a]==0?Double.POSITIVE_INFINITY:16/Math.abs(delta[a]);
        }
        if(!available.test(cell[0],cell[1],cell[2]))return 0;
        for(int crossing=0;crossing<64;crossing++) {
            double t=Math.min(next[0],Math.min(next[1],next[2]));
            if(t>1)return 1;
            int axes=0;for(int a=0;a<3;a++)if(next[a]<=t+EPS)axes|=1<<a;
            for(int mask=axes;mask!=0;mask=(mask-1)&axes) {
                if(!available.test(cell[0]+((mask&1)!=0?sign[0]:0),cell[1]+((mask&2)!=0?sign[1]:0),cell[2]+((mask&4)!=0?sign[2]:0)))
                    return Math.max(0,t-1e-6/Math.max(1,length));
            }
            for(int a=0;a<3;a++)if((axes&(1<<a))!=0){cell[a]+=sign[a];next[a]+=step[a];}
        }
        return 0;
    }
}
