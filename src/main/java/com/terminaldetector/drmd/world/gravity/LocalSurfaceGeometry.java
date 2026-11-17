package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Loaded-only static support queries for the first locally planar walking chart. */
final class LocalSurfaceGeometry {
    static final double SKIN=.08,NORMAL_DOT=.95;
    private static final double EPS=1e-6,TANGENT_MARGIN=.12;
    private LocalSurfaceGeometry() {}

    static boolean supported(LivingEntity actor,Vec3d feet,Vec3d up) {
        if(!(actor.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return false;
        var box=ActorFrames.body(actor,feet,up);
        if(!known(world,box.union(box.offset(up.multiply(-1.6))).expand(1)))return false;
        return !world.getBlockCollisions(actor,box.contract(EPS)).iterator().hasNext()&&raySupport(actor,feet,up);
    }

    static boolean clear(LivingEntity actor,Vec3d from,Vec3d to,Vec3d up,boolean margin) {
        if(!(actor.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return false;
        double length=from.distanceTo(to);
        if(!Double.isFinite(length)||length>1.75)return false;
        var swept=ActorFrames.body(actor,from,up).union(ActorFrames.body(actor,to,up));
        if(margin)swept=swept.expand(TANGENT_MARGIN*Math.max(0,1-Math.abs(up.x)),
            TANGENT_MARGIN*Math.max(0,1-Math.abs(up.y)),TANGENT_MARGIN*Math.max(0,1-Math.abs(up.z)));
        if(!known(world,swept.union(swept.offset(up.multiply(-1.6))).expand(1)))return false;
        if(world.getBlockCollisions(actor,swept.contract(EPS)).iterator().hasNext())return false;
        // Moving hulls need their own support chart. Never treat an unhandled physical hull as AIR.
        if(!BodySpatialIndex.query(world,swept).isEmpty())return false;
        int samples=Math.max(1,(int)Math.ceil(length/.25));
        for(int i=0;i<=samples;i++)if(!raySupport(actor,from.lerp(to,(double)i/samples),up))return false;
        return true;
    }

    private static boolean raySupport(LivingEntity actor,Vec3d feet,Vec3d up) {
        var hit=actor.getWorld().raycast(new RaycastContext(feet.add(up.multiply(.04)),feet.subtract(up.multiply(1.6)),
            RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        if(hit.getType()!=HitResult.Type.BLOCK||Vec3d.of(hit.getSide().getVector()).dotProduct(up)<NORMAL_DOT)return false;
        double gap=feet.subtract(hit.getPos()).dotProduct(up);
        return gap>=-EPS&&gap<=SKIN;
    }

    private static boolean known(ServerWorld world,Box query) {
        if(!Double.isFinite(query.minX)||!Double.isFinite(query.maxX)||!Double.isFinite(query.minY)
            ||!Double.isFinite(query.maxY)||!Double.isFinite(query.minZ)||!Double.isFinite(query.maxZ)
            ||query.minY<world.getBottomY()||query.maxY>world.getTopY()||!world.getWorldBorder().contains(query))return false;
        int x0=MathHelper.floor(query.minX)>>4,x1=MathHelper.floor(query.maxX)>>4;
        int y0=MathHelper.floor(query.minY)>>4,y1=MathHelper.floor(query.maxY)>>4;
        int z0=MathHelper.floor(query.minZ)>>4,z1=MathHelper.floor(query.maxZ)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>64)return false;
        var access=WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
}
