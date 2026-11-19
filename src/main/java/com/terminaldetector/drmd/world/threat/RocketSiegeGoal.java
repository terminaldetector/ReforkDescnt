package com.terminaldetector.drmd.world.threat;

import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import net.minecraft.entity.ai.goal.Goal;
import java.util.EnumSet;

/** Exclusive order on the existing ground infantry navigator; no parallel combat engine. */
public final class RocketSiegeGoal extends Goal {
    private final ShapesInfantryEntity mob;
    public RocketSiegeGoal(ShapesInfantryEntity mob){this.mob=mob;setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
    @Override public boolean canStart(){return RocketSiegeRuntime.ordered(mob);}
    @Override public boolean shouldContinue(){return canStart();}
    @Override public boolean shouldRunEveryTick(){return true;}
    @Override public void tick(){RocketSiegeRuntime.tickActor(mob);}
    @Override public void stop(){mob.getNavigation().stop();}
}
