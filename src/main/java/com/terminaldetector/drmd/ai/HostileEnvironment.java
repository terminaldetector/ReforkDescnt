package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.AirMineEntity;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.mob.CyberMobEntity;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import com.terminaldetector.drmd.world.mega.MegaWormEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared targeting for DRMD machines — hostile to the living environment, never to each other.
 * Port of GMod GetEnemy breadth: anything alive that is not a Descent unit is fair game.
 */
public final class HostileEnvironment {
	private HostileEnvironment() {}

	/** True for DRMD combat machines / keepers that should not murder each other. */
	public static boolean isAlly(LivingEntity e) {
		if (e == null || !e.isAlive()) return true;
		return e instanceof DroneEntity
				|| e instanceof CyberMobEntity
				|| e instanceof AirMineEntity
				|| e instanceof MegaWormEntity
				|| e instanceof ReactorKeeperEntity
				|| e instanceof EndReactorBossEntity;
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
		targetSelector.add(2, new ActiveTargetGoal<>(mob, PlayerEntity.class, true));
		targetSelector.add(3, new ActiveTargetGoal<>(mob, VillagerEntity.class, true));
		targetSelector.add(3, new ActiveTargetGoal<>(mob, IronGolemEntity.class, true));
		targetSelector.add(5, new ActiveTargetGoal<>(mob, AnimalEntity.class, true));
	}

	/** Convenience for HostileEntity subclasses calling from initGoals. */
	public static void installTargets(HostileEntity mob, GoalSelector targetSelector) {
		installTargets((PathAwareEntity) mob, targetSelector);
	}
}
