package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

/** Ground receiver: copied last-known coordinates only, never target assignment or remote fire. */
public final class ShapesInvestigateGoal extends Goal {
    private final ShapesInfantryEntity mob;
    private DroneSquadMailbox.Message task;
    private long nextRead,nextMove;
    public ShapesInvestigateGoal(ShapesInfantryEntity mob){this.mob=mob;setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    private boolean eligible(){return mob.isAlive()&&!mob.isAiDisabled()&&!mob.controlSuspended()
            &&mob.getTarget()==null&&mob.getHealth()/Math.max(1,mob.getMaxHealth())>=.22f;}
    private Vec3d point(){return new Vec3d(task.x(),task.y(),task.z());}
    private boolean awayFromMarker(){
        var difference=point().subtract(mob.getPos());
        if(LocalSurfaceNavigation.framed(mob))difference=ActorFrames.tangent(mob,difference);
        return difference.lengthSquared()>4;
    }
    @Override public boolean canStart(){
        long now=mob.getWorld().getTime();if(!eligible()||now<nextRead)return false;
        nextRead=now+DroneSquadMailbox.CADENCE;task=DroneSquadCoordinator.message(mob).orElse(null);
        return task!=null&&awayFromMarker();
    }
    @Override public boolean shouldContinue(){return eligible()&&task!=null&&DroneSquadCoordinator.valid(mob,task)&&awayFromMarker();}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){
        if (mob.controlSuspended() || task == null) { stop(); return; }
        long now=mob.getWorld().getTime();
        if(now>=nextRead){nextRead=now+DroneSquadMailbox.CADENCE;var newer=DroneSquadCoordinator.message(mob);if(newer.isPresent()&&newer.get().observedAt()>task.observedAt())task=newer.get();}
        var at=point();mob.getLookControl().lookAt(at.x,at.y,at.z);
        if(now<nextMove)return;
        // Use the received location without reading an enemy remotely.
        nextMove=now+DroneSquadMailbox.CADENCE;
        mob.navigateInfantry(at);
    }
    @Override public void stop(){mob.getNavigation().stop();task=null;super.stop();}
}
