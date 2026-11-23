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
    private Vec3d remembered,coverPoint,coverUp;
    private LivingEntity coverTarget;
    private long observedAt,nextMove;
    public ShapesCombatGoal(ShapesInfantryEntity mob){this.mob=mob;setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    public static boolean clearShot(ShapesInfantryEntity mob,Vec3d muzzle,LivingEntity target){
        double range=Math.min(128,mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
        return FlightRouteSpace.clearSight(mob,mob.getEyePos(),muzzle,range)
            &&FlightRouteSpace.clearSight(mob,muzzle,target.getBoundingBox().getCenter(),range);
    }
    @Override public boolean canStart(){return !mob.isAiDisabled()&&!mob.controlSuspended()&&mob.validEnemy(mob.getTarget());}
    @Override public boolean shouldContinue(){return canStart();}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){
        var target=mob.getTarget();if(mob.controlSuspended()||!mob.validEnemy(target))return;
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
            Vec3d destination;
            if(needsCover)destination=retainedCover(remembered,target);
            else {clearCover();destination=remembered;}
            if(destination!=null&&(needsCover||!visible||mob.getPos().squaredDistanceTo(remembered)>32*32))mob.navigateInfantry(destination);
            else mob.getNavigation().stop();
        }
        if(visible&&!needsCover)mob.tryFireAt(target);
    }
    /** Keep a valid physical destination while the native navigator works around its cover.
     * Rebuilding an actor-relative point every five ticks changes the quantized goal and
     * cancels the bounded local search before the actor can finish the same detour. */
    private Vec3d retainedCover(Vec3d enemy,LivingEntity target){
        var up=ActorFrames.active(mob)?ActorFrames.up(mob):new Vec3d(0,1,0);
        if(coverTarget!=target||coverUp==null||coverUp.squaredDistanceTo(up)>1e-12
            ||!usableCover(coverPoint,enemy,up)){
            clearCover();coverPoint=cover(enemy);coverUp=up;coverTarget=target;
        }
        return coverPoint;
    }
    private boolean usableCover(Vec3d point,Vec3d enemy,Vec3d up){
        if(point==null||!WorldEditSafety.loadedSegment(mob.getWorld(),mob.getPos(),point)
            ||!ActorFrames.canOccupy(mob,point,up))return false;
        var eye=point.add(mob.getEyePos().subtract(mob.getPos()));var toward=enemy.subtract(eye);double distance=toward.length();
        return distance>=1e-6&&distance<=32&&Double.isFinite(FlightRouteSpace.coverDistance(mob,eye,toward,distance));
    }
    private void clearCover(){coverPoint=null;coverUp=null;coverTarget=null;}
    /** Four physical ground candidates, no flying cover probes or random teleport. */
    private Vec3d cover(Vec3d enemy){
        var away=ActorFrames.tangent(mob,mob.getPos().subtract(enemy));
        if(away.lengthSquared()<1e-8)return null;away=away.normalize();
        var up=ActorFrames.active(mob)?ActorFrames.up(mob):new Vec3d(0,1,0);
        var right=up.crossProduct(away).normalize();
        Vec3d best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var delta:new Vec3d[]{away.multiply(4).add(right.multiply(4)),away.multiply(4).subtract(right.multiply(4)),right.multiply(4),right.multiply(-4)}){
            var point=mob.getPos().add(delta);
            if(!usableCover(point,enemy,up))continue;
            if(delta.lengthSquared()<nearest){nearest=delta.lengthSquared();best=point;}
        }
        return best;
    }
    @Override public void stop(){mob.getNavigation().stop();remembered=null;clearCover();super.stop();}
}
