package com.terminaldetector.drmd.world.edit;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;

/** Bounds before block reads: handheld/machine excavation must not force chunk generation. */
public final class WorldEditSafety {
    private WorldEditSafety() {}
    public static boolean mayChange(ServerWorld world,BlockPos at,LivingEntity actor) {
        if(world.isOutOfHeightLimit(at) || !world.getWorldBorder().contains(at) || !neighbourhoodLoaded(world,at))return false;
        return !(actor instanceof PlayerEntity player) || player.isAlive() && !player.isSpectator()
            && player.getAbilities().allowModifyWorld && world.canPlayerModifyAt(player,at);
    }
    /** Collision/organic shape queries may inspect diagonal adjacent blocks. */
    public static boolean neighbourhoodLoaded(World world,BlockPos at) {
        int x0=(at.getX()-1)>>4,x1=(at.getX()+1)>>4,z0=(at.getZ()-1)>>4,z1=(at.getZ()+1)>>4;
        int y0=(at.getY()-1)>>4,y1=(at.getY()+1)>>4;
        var access=WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            var cube=new D6ChunkPos(x,y,z);
            // Outside the dimension is known empty space, rather than an unloaded native cube.
            if(CubicAvailability.withinHeight(world,cube) && !access.isVolumeAvailable(cube))return false;
        }
        return true;
    }
    private static boolean rayCubeLoaded(World world,int x,int y,int z) {
        // A ray may query a boundary block's shape, whose neighbours lie off the central ray.
        var access=WorldAccess.of(world);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            var cube=new D6ChunkPos(x+dx,y+dy,z+dz);
            if(CubicAvailability.withinHeight(world,cube) && !access.isVolumeAvailable(cube))return false;
        }
        return true;
    }
    public static boolean loadedSegment(World world,Vec3d from,Vec3d to) {
        double length=to.subtract(from).length();
        return Double.isFinite(length) && length<=256 && LoadedCubeRay.fraction(from.x,from.y,from.z,to.x,to.y,to.z,(x,y,z)->rayCubeLoaded(world,x,y,z))==1;
    }
    public static Vec3d loadedEnd(World world,Vec3d from,Vec3d to) {
        Vec3d delta=to.subtract(from);double length=delta.length();
        if(!Double.isFinite(length) || length>256)return from;
        double fraction=LoadedCubeRay.fraction(from.x,from.y,from.z,to.x,to.y,to.z,(x,y,z)->rayCubeLoaded(world,x,y,z));
        return from.add(delta.multiply(fraction));
    }
}
