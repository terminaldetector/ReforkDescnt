package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

/** Ground cover/peek movement. Hidden targets contribute no current coordinates to a route or a shot. */
public final class ShapesCombatGoal extends Goal {
    private final ShapesInfantryEntity mob;
    private Vec3d remembered;
    private long observedAt,nextMove;
    public ShapesCombatGoal(ShapesInfantryEntity mob){this.mob=mob;setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    public static boolean clearShot(ShapesInfantryEntity mob,Vec3d muzzle,LivingEntity target){
        double range=Math.min(128,mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
        return FlightRouteSpace.clearSight(mob,mob.getEyePos(),muzzle,range)
            &&FlightRouteSpace.clearSight(mob,muzzle,target.getBoundingBox().getCenter(),range);
    }
    @Override public boolean canStart(){return !mob.isAiDisabled()&&mob.validEnemy(mob.getTarget());}
    @Override public boolean shouldContinue(){return canStart();}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){
        var target=mob.getTarget();if(!mob.validEnemy(target))return;
        long now=mob.getWorld().getTime();
        boolean visible=FlightRouteSpace.clearSight(mob,mob.getEyePos(),target.getEyePos(),
            Math.min(128,mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE)))&&mob.canSee(target);
        if(visible){remembered=target.getPos();observedAt=now;
            mob.getLookControl().lookAt(target.getEyePos().x,target.getEyePos().y,target.getEyePos().z);
        }
        if(remembered==null||now<observedAt||now-observedAt>=DroneSquadMailbox.TTL){mob.getNavigation().stop();return;}
        // This22percent flee threshold already belongs to the shared drone tactics; no new role statistics.
        boolean needsCover=mob.getHealth()/Math.max(1,mob.getMaxHealth())<.22f||mob.getWeaponEnergy()<mob.infantryRole().weaponCost;
        if(now>=nextMove){
            nextMove=now+DroneSquadMailbox.CADENCE;
            Vec3d destination=needsCover?cover(remembered):remembered;
            if(destination!=null&&(needsCover||!visible||mob.getPos().squaredDistanceTo(remembered)>32*32))move(destination);
            else mob.getNavigation().stop();
        }
        if(visible&&!needsCover)mob.tryFireAt(target);
    }
    private void move(Vec3d point){
        if(!WorldEditSafety.loadedSegment(mob.getWorld(),mob.getPos(),point))return;
        if(ActorFrames.active(mob))ActorFrames.driveSurface(mob,point.subtract(mob.getPos()),mob.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        else mob.getNavigation().startMovingTo(point.x,point.y,point.z,1);
    }
    /** Four physical ground candidates, no flying cover probes or random teleport. */
    private Vec3d cover(Vec3d enemy){
        var away=ActorFrames.tangent(mob,mob.getPos().subtract(enemy));
        if(away.lengthSquared()<1e-8)return null;away=away.normalize();
        var up=ActorFrames.active(mob)?ActorFrames.up(mob):new Vec3d(0,1,0);
        var right=up.crossProduct(away).normalize();
        Vec3d best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var delta:new Vec3d[]{away.multiply(4).add(right.multiply(4)),away.multiply(4).subtract(right.multiply(4)),right.multiply(4),right.multiply(-4)}){
            var point=mob.getPos().add(delta);
            if(!WorldEditSafety.loadedSegment(mob.getWorld(),mob.getPos(),point)||!ActorFrames.canOccupy(mob,point,up))continue;
            var eye=point.add(mob.getEyePos().subtract(mob.getPos()));var toward=enemy.subtract(eye);double distance=toward.length();
            if(distance<1e-6||distance>32||!Double.isFinite(FlightRouteSpace.coverDistance(mob,eye,toward,distance)))continue;
            if(delta.lengthSquared()<nearest){nearest=delta.lengthSquared();best=point;}
        }
        return best;
    }
    @Override public void stop(){mob.getNavigation().stop();remembered=null;super.stop();}
}
