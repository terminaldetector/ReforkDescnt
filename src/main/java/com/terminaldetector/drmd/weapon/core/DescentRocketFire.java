package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.weapon.projectile.FuseType;
import com.terminaldetector.drmd.weapon.projectile.ProjectileFramework;
import com.terminaldetector.drmd.weapon.projectile.ProjectileKind;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Descent secondaries — the launched half of the bank.
 *
 * <p>The canon is five in Descent 1 and an older brother for each in Descent 2. Three of those pairs
 * are launched and live here; the fourth pair is laid rather than launched (Proximity Bomb and Smart
 * Mine) and lives in {@link DescentMineFire}.
 *
 * <p>Each super is a step relative to its ancestor rather than the same missile with bigger numbers,
 * because that is the relationship the originals had: Flash gives up the kill to take the fight away
 * from the target, Guided hands the steering to the pilot instead of to a lock, Mercury removes the
 * time to react rather than the room to hide, and Earthshaker keeps going off after it lands.
 */
public final class DescentRocketFire {

	/** How a warhead finds its way there. */
	public enum Guidance {
		/** Dumbfire — it goes where the nose was pointing. */
		NONE,
		/** Picks a target itself and chases it. */
		SEEK,
		/** Flies the pilot's aim for its whole life: it turns when you turn. */
		PILOT
	}

	/** What it does when it arrives. */
	public enum Warhead {
		/** Blast only. */
		PLAIN,
		/** Almost no damage; blinds what is near it and loses it the pilot. */
		FLASH,
		/** Throws seeking blobs out of the burst — the area is denied after the hit, not by it. */
		SEEKERS,
		/** Throws delayed charges out of the burst, so the blast arrives in waves. */
		SHAKER
	}

	public enum Kind {
		// ---- Descent 1 (four launched; Proximity Bomb is the fifth and is laid) ----------------
		/** 1 — the missile you always have. Dumbfire, honest blast. */
		CONCUSSION(1, 70f, 55f, 220f, 3000f, 1.0f, Guidance.NONE, Warhead.PLAIN, 6.5f, 18f, 8f),
		/** 2 — finds its own target and stays on it. */
		HOMING(1, 110f, 85f, 280f, 2400f, 1.15f, Guidance.SEEK, Warhead.PLAIN, 6.5f, 22f, 12f),
		/** 4 — modest on impact; the blobs it sheds are the weapon. */
		SMART(1, 85f, 70f, 260f, 2500f, 1.1f, Guidance.NONE, Warhead.SEEKERS, 6.5f, 36f, 20f),
		/** 5 — one very large hole. */
		MEGA(1, 420f, 360f, 720f, 1600f, 1.9f, Guidance.NONE, Warhead.PLAIN, 6.5f, 70f, 55f),

		// ---- Descent 2 supers ------------------------------------------------------------------
		/** Concussion's brother: trades the kill for taking the fight away from the target. */
		FLASH(1, 12f, 18f, 200f, 3200f, 0.9f, Guidance.NONE, Warhead.FLASH, 6.5f, 20f, 6f),
		/** Homing's brother: no lock to break, because the pilot is the lock. Slow, and long-lived
		 *  so there is time to fly it around a corner. */
		GUIDED(1, 100f, 80f, 270f, 1700f, 1.15f, Guidance.PILOT, Warhead.PLAIN, 9.5f, 26f, 10f),
		/** Smart's brother: no seekers, just far too fast to answer. */
		MERCURY(1, 200f, 110f, 300f, 5200f, 1.0f, Guidance.NONE, Warhead.PLAIN, 3.5f, 40f, 34f),
		/** Mega's brother: the first blast is not the last one. */
		EARTHSHAKER(1, 480f, 420f, 820f, 1500f, 2.1f, Guidance.NONE, Warhead.SHAKER, 6.5f, 85f, 75f),

		// ---- Weights that predate the port and answer to nothing in the canon -------------------
		DUAL(2, 95f, 80f, 300f, 2600f, 0.95f, Guidance.NONE, Warhead.PLAIN, 6.5f, 28f, 16f),
		HEAVY(1, 180f, 160f, 480f, 2000f, 1.45f, Guidance.NONE, Warhead.PLAIN, 6.5f, 45f, 28f);

		public final int count;
		public final float direct;
		public final float splash;
		public final float splashR;
		public final float speed;
		public final float scale;
		public final Guidance guidance;
		public final Warhead warhead;
		public final float life;
		public final float energy;
		public final float recoil;

		Kind(int count, float direct, float splash, float splashR, float speed, float scale,
			 Guidance guidance, Warhead warhead, float life, float energy, float recoil) {
			this.count = count;
			this.direct = direct;
			this.splash = splash;
			this.splashR = splashR;
			this.speed = speed;
			this.scale = scale;
			this.guidance = guidance;
			this.warhead = warhead;
			this.life = life;
			this.energy = energy;
			this.recoil = recoil;
		}

		/**
		 * Behaviour string to warhead.
		 *
		 * <p>The canonical names are the live ones; the generic {@code rocket_*} names that the
		 * secondaries carried before the port are kept so anything still holding one keeps firing
		 * what it used to fire — except {@code rocket_triple}, whose three-rocket volley was never a
		 * Descent weapon and is now the Smart Missile it was always named after.
		 */
		public static Kind fromBehavior(String behavior) {
			return switch (behavior) {
				// Named even though it is also the default: an unknown behaviour arriving here should
				// be a Concussion because nothing better is known, not because it was spelled right.
				case "concussion", "rocket_light" -> CONCUSSION;
				case "homing", "rocket_offense" -> HOMING;
				case "smart_missile", "rocket_triple" -> SMART;
				case "mega_missile", "rocket_mega" -> MEGA;
				case "flash" -> FLASH;
				case "guided" -> GUIDED;
				case "mercury" -> MERCURY;
				case "earthshaker" -> EARTHSHAKER;
				case "rocket_dual", "rockets" -> DUAL;
				case "rocket_heavy" -> HEAVY;
				default -> CONCUSSION;
			};
		}
	}

	private DescentRocketFire() {}

	public static boolean fire(PlayerEntity user, Kind kind) {
		if (user.getWorld().isClient) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		for (int i = 0; i < kind.count; i++) {
			Vec3d dir = kind.count > 1 ? spread(aim, 4.5f + i, user) : aim;
			var muzzles = WeaponCore.allMuzzles(user, "rockets");
			Vec3d pos = muzzles.isEmpty()
					? WeaponCore.muzzle(user, 0.9f, (i % 2 == 0 ? -1 : 1) * 0.2f, -0.1f)
					: muzzles.get(Math.min(i, muzzles.size() - 1));
			WeaponCore.FireConfig cfg = ProjectileFramework.config(ProjectileKind.ROCKET, user, pos, dir);
			cfg.speed = kind.speed;
			cfg.directDamage = kind.direct;
			cfg.splashDamage = kind.splash;
			cfg.splashRadius = kind.splashR;
			cfg.homing = kind.guidance == Guidance.SEEK;
			cfg.pilotGuided = kind.guidance == Guidance.PILOT;
			cfg.turnRate = switch (kind.guidance) {
				case SEEK -> 140f;
				// The pilot turns faster than a seeker head does, which is the point of flying it.
				case PILOT -> 200f;
				case NONE -> 0f;
			};
			cfg.life = kind.life;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
			cfg.visualScale = kind.scale;
			cfg.worldBlast = true;
			cfg.recoil = i == 0 ? kind.recoil : 0f;
			cfg.dmgClass = DamageClass.EXPLOSIVE;
			payload(cfg, user, kind.warhead);
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	/**
	 * Hang the warhead's own work off the impact, keeping whatever the framework already does there.
	 */
	private static void payload(WeaponCore.FireConfig cfg, PlayerEntity user, Warhead warhead) {
		if (warhead == Warhead.PLAIN) return;
		var framework = cfg.onHit;
		cfg.onHit = ctx -> {
			if (framework != null) framework.accept(ctx);
			// Re-resolve the shooter rather than trusting the one captured at launch: a missile can
			// outlive the pilot who fired it, and the projectile knows how to look them up again.
			LivingEntity owner = ctx.projectile() != null ? ctx.projectile().getOwnerLiving() : user;
			if (owner == null) return;
			// Step back off the surface, or the children are born inside the wall that was hit.
			Vec3d at = ctx.hitPos().add(ctx.hitNormal().multiply(0.4));
			switch (warhead) {
				case FLASH -> flashBurst(owner, at);
				case SEEKERS -> seekerSplit(owner, at);
				case SHAKER -> shakerSplit(owner, at);
				default -> { }
			}
		};
	}

	/**
	 * The Flash burst: everything near it stops being able to fight rather than stops existing.
	 *
	 * <p>Blinding a player and blinding a robot are different things, so both are done — the screen
	 * goes for the pilot, and a mob drops whatever it was chasing. That second half is the one that
	 * matters in a corridor, and it is why the missile is worth carrying without any damage on it.
	 */
	private static void flashBurst(LivingEntity user, Vec3d at) {
		if (!(user.getWorld() instanceof ServerWorld world)) return;
		// Reaches well past its own blast: the flash is the weapon, the charge only carries it there.
		double radius = 9.0;
		Box box = new Box(at, at).expand(radius);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (e == user) continue;
			if (e.getPos().distanceTo(at) > radius) continue;
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 140, 0, false, false, true));
			if (e instanceof MobEntity mob) mob.setTarget(null);
		}
		// The blast is small enough to read as a dud without this: the white-out is the tell.
		world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, at.x, at.y, at.z, 4, 0.6, 0.6, 0.6, 0.0);
		world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 1.6, 1.6, 1.6, 0.35);
	}

	/**
	 * The Smart Missile's actual weapon: the burst sheds blobs that go looking.
	 *
	 * <p>They turn far harder than the missile did, so cover stops being cover once the missile has
	 * gone off anywhere near you. The missile itself is deliberately unimpressive — spending the
	 * damage on the children is what makes it the Smart Missile rather than a bigger Concussion.
	 */
	private static void seekerSplit(LivingEntity user, Vec3d at) {
		for (int i = 0; i < 5; i++) {
			WeaponCore.FireConfig cfg = ProjectileFramework.config(
					ProjectileKind.PLASMA, user, at, scatter(user, i, 5));
			cfg.speed = 1100f;
			cfg.directDamage = 34f;
			cfg.splashDamage = 26f;
			cfg.splashRadius = 110f;
			cfg.homing = true;
			cfg.turnRate = 260f;
			cfg.life = 3.0f;
			// Inert for the first breath so the blobs clear the wall they were born against.
			cfg.fuse = FuseType.DELAYED_IMPACT;
			cfg.armSeconds = 0.1f;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
			cfg.visualScale = 0.7f;
			cfg.recoil = 0f;
			cfg.dmgClass = DamageClass.EXOTIC;
			cfg.onHit = null; // children do not split again
			WeaponCore.fireProjectile(cfg);
		}
	}

	/**
	 * The Earthshaker's second wave: charges thrown clear of the crater on a short timer.
	 *
	 * <p>A timed fuse coasts through geometry instead of striking it, which is what puts the follow-up
	 * blasts inside the room rather than against the first surface they touch. They are slow and the
	 * fuse is short, so the ring lands around the impact rather than somewhere down the corridor.
	 */
	private static void shakerSplit(LivingEntity user, Vec3d at) {
		for (int i = 0; i < 5; i++) {
			WeaponCore.FireConfig cfg = ProjectileFramework.config(
					ProjectileKind.ROCKET, user, at, scatter(user, i, 5));
			cfg.speed = 120f;
			cfg.directDamage = 0f;
			cfg.splashDamage = 140f;
			cfg.splashRadius = 380f;
			cfg.life = 1.2f;
			cfg.fuse = FuseType.TIMED;
			cfg.fuseSeconds = 0.35f;
			cfg.armSeconds = 0.05f;
			cfg.homing = false;
			cfg.worldBlast = true;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
			cfg.visualScale = 1.3f;
			cfg.recoil = 0f;
			cfg.dmgClass = DamageClass.EXPLOSIVE;
			cfg.onHit = null; // children do not split again
			WeaponCore.fireProjectile(cfg);
		}
	}

	/**
	 * Evenly-spaced outward directions around the burst, tilted off the horizontal so a split on a
	 * floor does not send every child skimming along it.
	 */
	private static Vec3d scatter(LivingEntity user, int index, int total) {
		double ring = Math.PI * 2 * index / total;
		double lift = 0.35 + user.getRandom().nextDouble() * 0.4;
		return new Vec3d(Math.cos(ring), lift, Math.sin(ring)).normalize();
	}

	private static Vec3d spread(Vec3d aim, float deg, LivingEntity user) {
		double yaw = Math.toRadians((user.getRandom().nextDouble() - 0.5) * 2 * deg);
		double pitch = Math.toRadians((user.getRandom().nextDouble() - 0.5) * 2 * deg);
		Vec3d f = aim.normalize();
		// Pole-safe: never cross world +Y (collapses past vertical).
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(f);
		Vec3d up = right.crossProduct(f).normalize();
		return f.add(right.multiply(Math.sin(yaw))).add(up.multiply(Math.sin(pitch))).normalize();
	}
}
