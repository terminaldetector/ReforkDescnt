package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Combat motion with GMod role styles (pressure / flank / standoff / support / anchor)
 * plus Reynolds swarm blend from {@link DroneSwarmAi}.
 */
public final class DroneAi {
	public enum Phase { ORBIT, RUN, BREAK }

	public static class CombatGoal extends Goal {
		/**
		 * Hull fraction below which every style drops what it was doing and runs.
		 *
		 * <p>From Descent's own AI: {@code do_ai_robot_hit} snaps a hit {@code AIM_STILL} robot
		 * straight to chasing, and separately, every per-type behaviour table routes a robot that is
		 * losing to {@code AIM_HIDE} — nothing in the canon keeps fighting once it is clearly losing.
		 * Our roles carry no static "this type hides" flag the way a Descent robot's behaviour byte
		 * does, so hull fraction stands in for it: it is the number this AI already tracks, and it
		 * means the same thing — this fight is going badly for you.
		 */
		private static final float FLEE_HEALTH_FRACTION = 0.22f;

		private final DroneEntity drone;

		public CombatGoal(DroneEntity drone) {
			this.drone = drone;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return drone.getTarget() != null && drone.getTarget().isAlive()
					&& HostileEnvironment.isHostileTarget(drone, drone.getTarget());
		}

		@Override
		public boolean shouldContinue() {
			LivingEntity t = drone.getTarget();
			return t != null && t.isAlive() && HostileEnvironment.isHostileTarget(drone, t);
		}

		@Override
		public void tick() {
			LivingEntity target = drone.getTarget();
			if (target == null) return;
			// Drop ally locks (e.g. revenge misfires).
			if (!HostileEnvironment.isHostileTarget(drone, target)) {
				drone.setTarget(null);
				return;
			}
			float dt = 1f / 20f;
			drone.addPhaseTimer(dt);

			Vec3d pos = drone.getPos();
			Vec3d tpos = target.getPos().add(0, target.getHeight() * 0.5, 0);
			Vec3d toTarget = tpos.subtract(pos);
			double dist = toTarget.length();
			String style = drone.getRole().ai;

			// Checked before style dispatch, including support: a support drone at critical hull has
			// nothing left to spend healing others with, and needs the same out everyone else gets.
			if (drone.getHealth() / Math.max(1f, drone.getMaxHealth()) < FLEE_HEALTH_FRACTION) {
				tickFlee(pos, tpos, dt);
				return;
			}

			// Support: peel off to heal the most damaged flock-mate instead of dogfighting.
			if ("support".equals(style)) {
				tickSupport(pos, tpos, toTarget, dist, target, dt);
				return;
			}

			double preferred = preferredRange(style);
			Phase phase = drone.getPhase();
			if (phase == Phase.ORBIT && drone.getPhaseTimer() > 3f) drone.setPhase(Phase.RUN);
			else if (phase == Phase.RUN && drone.getPhaseTimer() > 1.5f) drone.setPhase(Phase.BREAK);
			else if (phase == Phase.BREAK && drone.getPhaseTimer() > 1.2f) drone.setPhase(Phase.ORBIT);

			Vec3d desire = styleDesire(style, pos, tpos, toTarget, dist, preferred);
			Vec3d swarm = DroneSwarmAi.compute(drone, target);
			if (swarm.lengthSquared() > 1e-6) {
				desire = desire.add(swarm.multiply(1.15));
			}
			if (desire.lengthSquared() < 1e-6) desire = toTarget;
			desire = desire.normalize();

			double maxSpd = DescentMod.su(drone.getRole().speed);
			if ("standoff".equals(style)) maxSpd *= 0.9;
			if ("anchor".equals(style)) maxSpd *= 0.75;
			Vec3d vel = desire.multiply(maxSpd / 20.0);
			drone.setVelocity(vel);
			drone.velocityModified = true;
			alignAttitude(drone, vel, desire, dt);

			double fireRange = "standoff".equals(style) ? DescentMod.su(2800) : DescentMod.su(2000);
			if (dist < fireRange) {
				drone.tryFireAt(target);
			}
		}

		private double preferredRange(String style) {
			return switch (style) {
				case "flank" -> DescentMod.su(420);
				case "standoff" -> DescentMod.su(1100);
				case "anchor" -> DescentMod.su(280);
				default -> DescentMod.su(drone.getRole().speed > 500 ? 500 : 700);
			};
		}

		private Vec3d styleDesire(String style, Vec3d pos, Vec3d tpos, Vec3d toTarget,
								  double dist, double preferred) {
			Vec3d radial = toTarget.lengthSquared() > 1e-6 ? toTarget.normalize() : new Vec3d(0, 0, 1);
			return switch (style) {
				case "flank" -> {
					// Perpendicular approach — GMod flank: never dive the nose straight in.
					Vec3d upHint = Math.abs(radial.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
					Vec3d side = radial.crossProduct(upHint);
					if (side.lengthSquared() < 1e-4) side = new Vec3d(1, 0, 0);
					side = side.normalize();
					if (((drone.getId() >> 1) & 1) == 0) side = side.multiply(-1);
					double radialErr = MathHelper.clamp((dist - preferred) / preferred, -1, 1);
					yield side.multiply(2.6).add(radial.multiply(-radialErr * 1.4)).add(0, 0.25, 0);
				}
				case "standoff" -> {
					double radialErr = MathHelper.clamp((dist - preferred) / preferred, -1.2, 1.2);
					Vec3d upHint = Math.abs(radial.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
					Vec3d tangent = radial.crossProduct(upHint).normalize();
					// Hold the ring; back off hard if the target closes.
					Vec3d hold = tangent.multiply(1.4).add(radial.multiply(-radialErr * 2.2));
					if (dist < preferred * 0.55) hold = radial.multiply(-2.8).add(0, 0.8, 0);
					yield hold;
				}
				case "anchor" -> {
					// Slow pressure in — heavy elite / heavy.
					yield switch (drone.getPhase()) {
						case RUN, ORBIT -> radial.add(0, 0.15, 0);
						case BREAK -> pos.subtract(tpos).normalize().add(0, 0.4, 0);
					};
				}
				default -> switch (drone.getPhase()) {
					case ORBIT -> {
						Vec3d upHint = Math.abs(radial.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
						Vec3d tangent = radial.crossProduct(upHint);
						if (tangent.lengthSquared() < 1e-4) tangent = new Vec3d(1, 0, 0);
						tangent = tangent.normalize();
						double radialErr = MathHelper.clamp((dist - preferred) / preferred, -1, 1);
						yield tangent.multiply(2.2).add(radial.multiply(-radialErr));
					}
					case RUN -> radial.add(0, drone.getRandom().nextBoolean() ? 0.4 : -0.4, 0);
					case BREAK -> pos.subtract(tpos).normalize().add(0, 0.6, 0)
							.add(drone.getRotationVector().multiply(0.5));
				};
			};
		}

		private void tickSupport(Vec3d pos, Vec3d tpos, Vec3d toTarget, double dist,
								 LivingEntity target, float dt) {
			DroneEntity ally = null;
			float worst = 1f;
			for (DroneEntity other : drone.getWorld().getEntitiesByClass(DroneEntity.class,
					drone.getBoundingBox().expand(DescentMod.su(500)),
					d -> d != drone && d.isAlive())) {
				float ratio = other.getHealth() / Math.max(1f, other.getMaxHealth());
				if (ratio < worst) {
					worst = ratio;
					ally = other;
				}
			}
			Vec3d desire;
			if (ally != null && worst < 0.85f) {
				desire = ally.getPos().subtract(pos);
				if (desire.lengthSquared() > 1e-4) desire = desire.normalize();
				if (pos.distanceTo(ally.getPos()) < 6) {
					ally.heal(4f / 20f);
				}
			} else {
				// Keep clear of the threat, orbit the flock.
				desire = pos.subtract(tpos);
				if (desire.lengthSquared() < 1e-4) desire = new Vec3d(0, 1, 0);
				desire = desire.normalize().add(DroneSwarmAi.compute(drone, target));
				if (desire.lengthSquared() > 1e-6) desire = desire.normalize();
			}
			double maxSpd = DescentMod.su(drone.getRole().speed);
			drone.setVelocity(desire.multiply(maxSpd / 20.0));
			drone.velocityModified = true;
			alignAttitude(drone, drone.getVelocity(), desire, dt);
			// Support still snipes if something is already on them.
			if (dist < DescentMod.su(900) && drone.getRole().weaponDamage > 0) {
				drone.tryFireAt(target);
			}
		}

		/**
		 * Run, and run toward whatever breaks line of sight fastest — not just away from the threat.
		 *
		 * <p>A straight retreat away from the target is not what "hide" meant in the original: it is
		 * what makes the difference between a robot that reads as panicking in the open and one that
		 * reads as ducking behind something. No return fire either — one still shooting at you is not
		 * actually retreating.
		 */
		private void tickFlee(Vec3d pos, Vec3d tpos, float dt) {
			Vec3d away = pos.subtract(tpos);
			if (away.lengthSquared() < 1e-4) {
				away = new Vec3d(drone.getRandom().nextDouble() - 0.5, 0.2, drone.getRandom().nextDouble() - 0.5);
			}
			away = away.normalize();
			Vec3d desire = nearestCoverDirection(pos, away);
			// Fear beats the patrol speed a role cruises at — this is the one moment a drone is
			// trying to survive rather than hold a formation.
			double maxSpd = DescentMod.su(drone.getRole().speed) * 1.15;
			Vec3d vel = desire.multiply(maxSpd / 20.0);
			drone.setVelocity(vel);
			drone.velocityModified = true;
			alignAttitude(drone, vel, desire, dt);
		}

		/**
		 * Among a few directions biased away from the threat, the one whose ray hits solid ground
		 * soonest — the nearest thing to duck behind, rather than open sky in roughly the right
		 * direction. Falls back to running straight away when nothing is close enough to reach for.
		 */
		private Vec3d nearestCoverDirection(Vec3d pos, Vec3d away) {
			Vec3d upHint = Math.abs(away.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
			Vec3d side = away.crossProduct(upHint);
			side = side.lengthSquared() > 1e-6 ? side.normalize() : new Vec3d(1, 0, 0);

			Vec3d best = away;
			double bestDist = Double.MAX_VALUE;
			double range = DescentMod.su(2000);
			for (Vec3d dir : new Vec3d[]{
					away, away.add(side.multiply(0.6)).normalize(),
					away.subtract(side.multiply(0.6)).normalize(), away.add(0, 0.5, 0).normalize()}) {
				var hit = drone.getWorld().raycast(new net.minecraft.world.RaycastContext(
						pos, pos.add(dir.multiply(range)),
						net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
						net.minecraft.world.RaycastContext.FluidHandling.NONE, drone));
				if (hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) continue;
				double d = pos.distanceTo(hit.getPos());
				if (d < bestDist) {
					bestDist = d;
					best = dir;
				}
			}
			return best;
		}

		private static void alignAttitude(DroneEntity drone, Vec3d vel, Vec3d desire, float dt) {
			if (vel.lengthSquared() < 1e-6) return;
			FlightAttitude.steer(drone, vel, 9f, dt);
			float bank = FlightAttitude.bankTarget(vel, desire, 55f);
			drone.setFlightRoll(FlightAttitude.approachAngle(drone.getFlightRoll(), bank, 6f, dt));
		}
	}
}
