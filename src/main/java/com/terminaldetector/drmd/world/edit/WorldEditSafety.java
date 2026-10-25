package com.terminaldetector.drmd.world.edit;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)
            if(!world.isChunkLoaded(new BlockPos(x*16,world.getBottomY(),z*16)))return false;
        return true;
    }
    private static boolean rayColumnLoaded(World world,int x,int z) {
        // A ray may query a boundary block's shape, whose neighbours lie off the central ray.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(!world.isChunkLoaded(new BlockPos((x+dx)*16,world.getBottomY(),(z+dz)*16)))return false;
        return true;
    }
    public static boolean loadedSegment(World world,Vec3d from,Vec3d to) {
        double length=to.subtract(from).length();
        return Double.isFinite(length) && length<=256 && LoadedChunkRay.fraction(from.x,from.z,to.x,to.z,(x,z)->rayColumnLoaded(world,x,z))==1;
    }
    public static Vec3d loadedEnd(World world,Vec3d from,Vec3d to) {
        Vec3d delta=to.subtract(from);double length=delta.length();
        if(!Double.isFinite(length) || length>256)return from;
        double fraction=LoadedChunkRay.fraction(from.x,from.z,to.x,to.z,(x,z)->rayColumnLoaded(world,x,z));
        return from.add(delta.multiply(fraction));
    }
}
