package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** First standing-ground-mob adapters. Unknown renderers/poses keep their existing geometry. */
public final class ActorFrames {
    private static final java.util.Map<LivingEntity,Boolean> TRACKED=
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private ActorFrames() {}

    public static boolean supported(Entity entity) {
        var type=entity.getType();
        return entity instanceof com.terminaldetector.drmd.entity.mob.TripodEntity
            || entity instanceof com.terminaldetector.drmd.entity.mob.SpiderTurretEntity
            || type==EntityType.COW || type==EntityType.PIG || type==EntityType.SHEEP
            || type==EntityType.ZOMBIE || type==EntityType.HUSK || type==EntityType.SKELETON
            || type==EntityType.STRAY || type==EntityType.CREEPER;
    }

    public static Vec3d up(Entity entity) {
        return supported(entity) && entity instanceof ActorFrameAccess frame ? frame.drmd$actorUp() : Vec3d.ZERO;
    }

    public static boolean active(Entity entity) {return up(entity).lengthSquared()>.5;}

    /** Weak physical-pose index also receives NBT restores; it holds no chunk/entity ticket. */
    public static void frameChanged(LivingEntity entity) {
        if(entity.getWorld().isClient)return;
        if(active(entity))TRACKED.put(entity,Boolean.TRUE);else TRACKED.remove(entity);
    }
    public static java.util.List<LivingEntity> tracked(ServerWorld world) {
        synchronized(TRACKED) {
            TRACKED.keySet().removeIf(entity->entity.isRemoved());
            return TRACKED.keySet().stream().filter(entity->entity.getWorld()==world && entity.isAlive()
                && world.getEntity(entity.getUuid())==entity).toList();
        }
    }

    public static Vec3d tangent(Entity entity,Vec3d direction) {
        Vec3d up=active(entity)?up(entity):ActorFrameMath.WORLD_UP;
        return direction.subtract(up.multiply(direction.dotProduct(up)));
    }

    /** Direct surface pursuit adapter; world-Y vanilla navigation remains for ordinary floors. */
    public static void driveSurface(net.minecraft.entity.mob.MobEntity actor,Vec3d direction,double speed) {
        actor.getNavigation().stop();actor.setForwardSpeed(0);actor.setSidewaysSpeed(0);actor.setJumping(false);
        Vec3d up=up(actor),normal=up.multiply(actor.getVelocity().dotProduct(up));
        Vec3d tangent=tangent(actor,direction);
        actor.setVelocity(normal.add(tangent.lengthSquared()>1e-8?tangent.normalize().multiply(speed):Vec3d.ZERO));
        actor.velocityModified=true;
    }

    public static Box body(LivingEntity entity, Vec3d feet, Vec3d up) {
        return GravityMount.standingBox(feet,up,entity.getWidth()/2.0,
            entity.getDimensions(entity.getPose()).height());
    }

    /** No teleports, chunk tickets or speculative block reads when changing physical pose. */
    public static boolean trySetUp(LivingEntity entity, Vec3d requested) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()
                || !supported(entity) || entity.getPose()!=EntityPose.STANDING || entity.hasVehicle())return false;
        Vec3d decoded=ActorFrameMath.decode(requested.x,requested.y,requested.z);
        if(decoded==Vec3d.ZERO)return false;
        // Match the float tracker before testing the authoritative collision volume.
        Vec3d up=ActorFrameMath.decode((float)decoded.x,(float)decoded.y,(float)decoded.z);
        if(up(entity).distanceTo(up)<1e-12)return true;
        Box box=body(entity,entity.getPos(),up);
        if(!loaded(world,box) || !world.isSpaceEmpty(entity,box.contract(1e-6)))return false;
        ((ActorFrameAccess)entity).drmd$setActorUp(up);
        return true;
    }

    /** A blocked release is retried, rather than replacing a safe wall body by an intersecting one. */
    public static boolean tryRelease(LivingEntity entity) {
        if(!active(entity))return true;
        if(!(entity.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread())return false;
        Box upright=body(entity,entity.getPos(),ActorFrameMath.WORLD_UP);
        if(!loaded(world,upright) || !world.isSpaceEmpty(entity,upright.contract(1e-6)))return false;
        ((ActorFrameAccess)entity).drmd$setActorUp(Vec3d.ZERO);
        return true;
    }

    private static boolean loaded(ServerWorld world,Box box) {
        if(!world.getWorldBorder().contains(box) || box.minY<world.getBottomY()
                || box.maxY>world.getTopY())return false;
        // Collision/organic shapes can read neighbouring blocks. Bound those reads first.
        Box query=box.expand(1);
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
