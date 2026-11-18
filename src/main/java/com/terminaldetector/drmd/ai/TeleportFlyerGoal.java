package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.TeleportFlyerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Only the new role's finite charge/recovery; existing combat/route goals resume normally. */
public final class TeleportFlyerGoal extends Goal {
    private final TeleportFlyerEntity actor;
    public TeleportFlyerGoal(TeleportFlyerEntity actor){this.actor=actor;setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    @Override public boolean canStart(){return actor.ability().ownsMovement()?actor.serverEligible():actor.requestCharge();}
    @Override public boolean shouldContinue(){return actor.isAlive()&&!actor.isAiDisabled()&&actor.ability().ownsMovement();}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){
        var phase=actor.ability().phase();var enemy=actor.chargedEnemy();
        if((phase==TeleportAbilityState.Phase.WARN||phase==TeleportAbilityState.Phase.COMMIT_PENDING)
            &&(enemy==null||!visible(actor,enemy,false))){actor.cancelCharge();enemy=null;}
        actor.setVelocity(actor.steerFlightVelocity(Vec3d.ZERO));actor.velocityModified=true;
        if(enemy!=null)actor.getLookControl().lookAt(enemy.getEyePos().x,enemy.getEyePos().y,enemy.getEyePos().z);
    }
    public static boolean visible(TeleportFlyerEntity actor,LivingEntity target,boolean freshBodies){
        if(target==null||!target.isAlive()||target.isRemoved()||target.getWorld()!=actor.getWorld()
            ||!HostileEnvironment.isHostileTarget(actor,target)||actor.isTeammate(target)||!actor.canTarget(target))return false;
        if(actor.getWorld() instanceof net.minecraft.server.world.ServerWorld world&&world.getEntity(target.getUuid())!=target)return false;
        return FlightRouteSpace.clearSight(actor,actor.getEyePos(),target.getEyePos(),
            Math.min(128,actor.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE)),freshBodies)&&actor.canSee(target);
    }
    /** Existing bank-aware pole-safe flight basis; no world-Y-only or degenerate vertical warp cone. */
    public static List<Vec3d> candidates(Vec3d forward,float bank,double radius){
        if(!Double.isFinite(forward.lengthSquared())||forward.lengthSquared()<1e-8||!Float.isFinite(bank)||!Double.isFinite(radius)||radius<=0||radius>16)return List.of();
        forward=forward.normalize();var up=FlightAttitude.upOf(forward,bank);var right=forward.crossProduct(up).normalize();
        return List.of(right.multiply(radius),right.multiply(-radius),up.multiply(radius),up.multiply(-radius),forward.multiply(-radius));
    }
}
