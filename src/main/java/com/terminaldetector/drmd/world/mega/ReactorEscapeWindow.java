package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Bounds for the existing UFO crash clock and power4 shockwave, not another reactor engine. */
public final class ReactorEscapeWindow {
    public static final int DEFAULT_TICKS=100,MAX_TICKS=1200;
    private ReactorEscapeWindow() {}
    public static boolean validDelay(int ticks){return ticks>=0&&ticks<=MAX_TICKS;}
    public static int remaining(int delay,int elapsed){
        if(!validDelay(delay)||elapsed<0)throw new IllegalArgumentException("Invalid reactor escape clock");
        return Math.max(0,delay-elapsed);
    }
    /** Power4 has an8-block damage radius. Include a2-block shape/neighbour margin.
     * At most27 existing16-cubes are inspected; never generate/join an unknown volume. */
    public static boolean blastVolumeAvailable(ServerWorld world,Vec3d centre){
        if(!world.getServer().isOnThread()||!Double.isFinite(centre.lengthSquared()))return false;
        var access=WorldAccess.of(world);
        for(int x=MathHelper.floor(centre.x-10)>>4;x<=MathHelper.floor(centre.x+10)>>4;x++)
        for(int y=MathHelper.floor(centre.y-10)>>4;y<=MathHelper.floor(centre.y+10)>>4;y++)
        for(int z=MathHelper.floor(centre.z-10)>>4;z<=MathHelper.floor(centre.z+10)>>4;z++){
            var cube=new D6ChunkPos(x,y,z);
            if(CubicAvailability.withinHeight(world,cube)&&!access.isVolumeAvailable(cube))return false;
        }
        return true;
    }
}
