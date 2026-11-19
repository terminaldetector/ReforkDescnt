package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.AirMineEntity;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.mob.CyberMobEntity;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared targeting for DRMD machines — hostile to the living environment, never to each other.
 * End faction: CyberMobs + keepers/boss + Endermen (Oblivion Seeker coop).
 */
public final class HostileEnvironment {
	private HostileEnvironment() {}

	/** True for DRMD combat machines / End-faction allies that should not murder each other. */
	public static boolean isAlly(LivingEntity e) {
		if (e == null || !e.isAlive()) return true;
		return e instanceof DroneEntity
				|| e instanceof CyberMobEntity
				|| e instanceof AirMineEntity
				|| e instanceof ReactorKeeperEntity
				|| e instanceof EndReactorBossEntity
				|| e instanceof EndermanEntity;
	}

	public static boolean isHostileTarget(LivingEntity self, LivingEntity other) {
		if (other == null || other == self || !other.isAlive()) return false;
		if (isAlly(other)) return false;
		if (other instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
		return true;
	}

	/**
	 * Install environment-wide hunt priorities on {@code mob}'s target selector.
	 * Call from {@code initGoals} so the protected selector is in scope:
	 * {@code HostileEnvironment.installTargets(this, this.targetSelector)}.
	 *
	 * <p>Players → villagers / golems → animals; revenge on anything that hits us.
	 */
	public static void installTargets(PathAwareEntity mob, GoalSelector targetSelector) {
		targetSelector.add(1, new RevengeGoal(mob));
		targetSelector.add(2, targetGoal(mob, PlayerEntity.class));
		targetSelector.add(3, targetGoal(mob, VillagerEntity.class));
		targetSelector.add(3, targetGoal(mob, IronGolemEntity.class));
		targetSelector.add(5, targetGoal(mob, AnimalEntity.class));
	}

    private static <T extends LivingEntity> ActiveTargetGoal<T> targetGoal(PathAwareEntity mob,Class<T> type) {
        return mob instanceof DroneEntity ? new VolumeTargetGoal<>(mob,type) : new SurfaceTargetGoal<>(mob,type);
    }

    /** Native targeting still owns range, predicate, cadence and visibility. Only its
     * four-block normal band follows an already admitted physical surface frame. */
    private static final class SurfaceTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {
        private final PathAwareEntity actor;
        private SurfaceTargetGoal(PathAwareEntity mob,Class<T> type){super(mob,type,true);actor=mob;}
        @Override protected net.minecraft.util.math.Box getSearchBox(double distance) {
            if(!com.terminaldetector.drmd.world.gravity.ActorFrames.active(actor))return super.getSearchBox(distance);
            var up=com.terminaldetector.drmd.world.gravity.ActorFrames.up(actor);
            if(up.y>.999999||!Double.isFinite(distance)||distance<0)return super.getSearchBox(distance);
            var x=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new net.minecraft.util.math.Vec3d(distance,0,0),up);
            var y=up.multiply(4);
            var z=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new net.minecraft.util.math.Vec3d(0,0,distance),up);
            // Absolute basis columns are the conservative AABB of the same local
            // (distance,4,distance) expansion, including non-cardinal orientations.
            // No extra query, larger follow range or omniscient target is introduced.
            return actor.getBoundingBox().expand(Math.abs(x.x)+Math.abs(y.x)+Math.abs(z.x),
                Math.abs(x.y)+Math.abs(y.y)+Math.abs(z.y),Math.abs(x.z)+Math.abs(y.z)+Math.abs(z.z));
        }
    }

    /** Free-flight actors search in XYZ; vanilla's four-block Y band belongs to ground mobs. */
    private static final class VolumeTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {
        private final PathAwareEntity flyer;
        private VolumeTargetGoal(PathAwareEntity mob,Class<T> type){super(mob,type,true);flyer=mob;setMaxTimeWithoutVisibility(200);}
        @Override protected net.minecraft.util.math.Box getSearchBox(double distance) {
            return flyer.getBoundingBox().expand(distance);
        }
    }

	/** Convenience for HostileEntity subclasses calling from initGoals. */
	public static void installTargets(HostileEntity mob, GoalSelector targetSelector) {
		installTargets((PathAwareEntity) mob, targetSelector);
	}
}
