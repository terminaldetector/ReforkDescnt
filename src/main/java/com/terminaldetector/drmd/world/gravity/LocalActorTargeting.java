package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.physics.OrientationFrame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Native targeting boundaries share the physical actor frame; this class owns no goal or motion tick. */
public final class LocalActorTargeting {
    private record Knockback(LivingEntity target,com.terminaldetector.drmd.client.portal.PortalTransform.Quat frame) {}
    private static final ThreadLocal<Knockback> KNOCKBACK=new ThreadLocal<>();
    private LocalActorTargeting() {}
    public static boolean framed(Entity actor){return !actor.getWorld().isClient&&ActorFrames.active(actor);}
    public static Vec3d eye(Entity target){return target instanceof LivingEntity?target.getEyePos():target.getBoundingBox().getCenter();}
    public static boolean canSee(LivingEntity actor,Entity target) {
        if(actor.getWorld()!=target.getWorld())return false;
        var from=actor.getEyePos();var to=eye(target);
        if(from.squaredDistanceTo(to)>128*128||!com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(actor.getWorld(),from,to))return false;
        return actor.getWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor)).getType()==HitResult.Type.MISS;
    }
    public static Vec3d lookDelta(LivingEntity actor,double x,double y,double z){return ActorFrameMath.toLocal(new Vec3d(x,y,z).subtract(actor.getEyePos()),ActorFrames.up(actor));}
    public static boolean inAttackRange(net.minecraft.entity.mob.MobEntity actor,LivingEntity target,double range) {
        if(actor.getWorld()!=target.getWorld())return false;
        var up=ActorFrames.up(actor);var centre=actor.getPos().add(up.multiply(actor.getHeight()/2));
        var a=new com.terminaldetector.drmd.physics.collision.OrientedBox(
            new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(centre.x,centre.y,centre.z),
            new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(actor.getWidth()/2+range,actor.getHeight()/2,actor.getWidth()/2+range),OrientationFrame.fromUp(up));
        var box=((LocalActorTargetAccess)target).drmd$targetHitbox();
        var b=com.terminaldetector.drmd.physics.collision.OrientedBox.axisAligned(new com.terminaldetector.drmd.physics.collision.BodyBounds(
            new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(box.minX,box.minY,box.minZ),
            new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(box.maxX,box.maxY,box.maxZ)));
        if(ActorFrames.active(target)) {
            var p=target.getPos().add(ActorFrames.up(target).multiply(target.getHeight()/2));
            b=new com.terminaldetector.drmd.physics.collision.OrientedBox(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(p.x,p.y,p.z),
                new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(target.getWidth()/2,target.getHeight()/2,target.getWidth()/2),OrientationFrame.fromUp(ActorFrames.up(target)));
        }
        return a.contact(b,0).filter(hit->hit.penetration()>0).isPresent();
    }
    /** The damage-stage impulse precedes MobEntity.tryAttack's extra impulse. Direct framed
     * attackers need its source displacement projected into the same native tangent frame. */
    public static void damageKnockback(LivingEntity target,double strength,double x,double z,net.minecraft.entity.damage.DamageSource source) {
        if(source.getSource() instanceof LivingEntity attacker&&framed(attacker)&&attacker.getWorld()==target.getWorld()&&source.getPosition()!=null) {
            var direction=ActorFrameMath.toLocal(source.getPosition().subtract(target.getPos()),ActorFrames.up(attacker));
            knockback(target,strength,direction.x,direction.z,attacker);
        } else target.takeKnockback(strength,x,z);
    }
    public static void knockback(LivingEntity target,double strength,double x,double z,LivingEntity attacker) {
        if(!framed(attacker)||target.getWorld()!=attacker.getWorld()){target.takeKnockback(strength,x,z);return;}
        var before=KNOCKBACK.get();KNOCKBACK.set(new Knockback(target,OrientationFrame.fromUp(ActorFrames.up(attacker))));
        try{target.takeKnockback(strength,x,z);}finally{if(before==null)KNOCKBACK.remove();else KNOCKBACK.set(before);}
    }
    public static Vec3d nativeKnockbackVelocity(LivingEntity target){var owner=KNOCKBACK.get();var v=target.getVelocity();return owner!=null&&owner.target==target?OrientationFrame.toLocal(owner.frame,v):v;}
    public static void nativeKnockbackResult(LivingEntity target,double x,double y,double z){var owner=KNOCKBACK.get();var v=new Vec3d(x,y,z);target.setVelocity(owner!=null&&owner.target==target?OrientationFrame.toWorld(owner.frame,v):v);}
}
