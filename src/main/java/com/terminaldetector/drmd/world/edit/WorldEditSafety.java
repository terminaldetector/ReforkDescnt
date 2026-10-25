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
        if(world.isOutOfHeightLimit(at) || !world.getWorldBorder().contains(at) || !world.isChunkLoaded(at))return false;
        return !(actor instanceof PlayerEntity player) || player.isAlive() && !player.isSpectator()
            && player.getAbilities().allowModifyWorld && world.canPlayerModifyAt(player,at);
    }
    public static boolean loadedSegment(World world,Vec3d from,Vec3d to) {
        double length=to.subtract(from).length();
        return Double.isFinite(length) && length<=256 && LoadedChunkRay.fraction(from.x,from.z,to.x,to.z,(x,z)->world.isChunkLoaded(new BlockPos(x*16,world.getBottomY(),z*16)))==1;
    }
    public static Vec3d loadedEnd(World world,Vec3d from,Vec3d to) {
        Vec3d delta=to.subtract(from);double length=delta.length();
        if(!Double.isFinite(length) || length>256)return from;
        double fraction=LoadedChunkRay.fraction(from.x,from.z,to.x,to.z,(x,z)->world.isChunkLoaded(new BlockPos(x*16,world.getBottomY(),z*16)));
        return from.add(delta.multiply(fraction));
    }
}
