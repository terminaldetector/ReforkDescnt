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
        Vec3d delta=to.subtract(from);double length=delta.length();
        if(!Double.isFinite(length) || length>256)return false;
        int steps=Math.max(1,(int)Math.ceil(length*2));
        for(int i=0;i<=steps;i++)if(!world.isChunkLoaded(BlockPos.ofFloored(from.add(delta.multiply(i/(double)steps)))))return false;
        return true;
    }
    public static Vec3d loadedEnd(World world,Vec3d from,Vec3d to) {
        Vec3d delta=to.subtract(from);double length=delta.length();
        if(!Double.isFinite(length) || length>256)return from;
        int steps=Math.max(1,(int)Math.ceil(length*2));Vec3d last=from;
        for(int i=0;i<=steps;i++) {
            Vec3d at=from.add(delta.multiply(i/(double)steps));
            if(!world.isChunkLoaded(BlockPos.ofFloored(at)))break;
            last=at;
        }
        return last;
    }
}
