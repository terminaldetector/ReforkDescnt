package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

/** MOVE/LOOK below existing combat/bombardment; never assigns targets or fires. */
public final class SquadInvestigateGoal extends Goal {
    private final DroneEntity drone;
    private final DroneNavigation navigation;
    private DroneSquadMailbox.Message task;
    private long nextRead;
    public SquadInvestigateGoal(DroneEntity drone){this.drone=drone;navigation=new DroneNavigation(drone);setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    private boolean eligible(){return drone.isAlive()&&!drone.isAiDisabled()&&drone.getTarget()==null
        &&drone.getHealth()/Math.max(1,drone.getMaxHealth())>=.22f&&!"support".equals(drone.getRole().ai);}
    private Vec3d point(){return new Vec3d(task.x(),task.y(),task.z());}
    @Override public boolean canStart(){
        long now=drone.getWorld().getTime();if(!eligible()||now<nextRead)return false;
        nextRead=now+DroneSquadMailbox.CADENCE;task=DroneSquadCoordinator.message(drone).orElse(null);
        return task!=null&&drone.getPos().squaredDistanceTo(point())>4;
    }
    @Override public boolean shouldContinue(){return eligible()&&task!=null&&DroneSquadCoordinator.valid(drone,task)&&drone.getPos().squaredDistanceTo(point())>4;}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){
        long now=drone.getWorld().getTime();
        if(now>=nextRead){nextRead=now+DroneSquadMailbox.CADENCE;var newer=DroneSquadCoordinator.message(drone);if(newer.isPresent()&&newer.get().observedAt()>task.observedAt())task=newer.get();}
        // The route destination is the copied observation, never a target UUID's current position.
        var destination=point();var delta=destination.subtract(drone.getPos());if(delta.lengthSquared()<1e-6)return;
        var role=drone.getRole();double speed=DescentMod.su(role.speed)/20.0;
        if("standoff".equals(role.ai))speed*=.9;else if("anchor".equals(role.ai))speed*=.75;
        var desired=drone.steerFlightVelocity(delta.normalize().multiply(speed));
        drone.setVelocity(navigation.steer(desired,destination,true));drone.velocityModified=true;
        drone.getLookControl().lookAt(destination.x,destination.y,destination.z);
    }
    @Override public void stop(){navigation.stop();task=null;super.stop();}
}
