package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.shield.ShieldSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * Weapon firing framework — port of D6_Wep.FireProjectile / DirectDamage / SplashDamage.
 */
public final class WeaponCore {
	private WeaponCore() {}

	public static class FireConfig {
		public LivingEntity owner;
		public Vec3d pos;
		public Vec3d dir;
		public float speed = 40f; // already in MC units preferred; if from Source use DescentMod.su
		public boolean speedIsSourceUnits = true;
		public String model = "default";
		public float scale = 1f;
		public DamageClass dmgClass = DamageClass.KINETIC;
		public float mass = 1f;
		public float gravity = 0f;
		public float drag = 0f;
		public float inherit = 1f;
		public float recoil = 0f;
		public float life = 5f;
		public float directDamage = 0f;
		public float splashDamage = 0f;
		public float splashRadius = 0f;
		public boolean homing = false;
		public float turnRate = 90f;
		public Entity homeTarget;
		public int pierceCount = 0;
		public int colorR = 180, colorG = 220, colorB = 255;
		public Consumer<HitContext> onHit;
		/** Visual mesh: 0 bolt, 1 rocket, 2 orb, 3 drill. */
		public int meshKind = 0;
		public float visualScale = 1f;
		/** True → createExplosion crater (heavy ordinance). */
		public boolean worldBlast = false;
		/** True → 3×3 soft carve on block hit (drill charge). */
		public boolean drillCarve = false;
	}

	public record HitContext(ProjectileEntity projectile, Entity hitEntity, Vec3d hitPos, Vec3d hitNormal, boolean isBlock) {}

	public static ProjectileEntity fireProjectile(FireConfig cfg) {
		if (cfg.owner == null || cfg.owner.getWorld().isClient) return null;
		ServerWorld world = (ServerWorld) cfg.owner.getWorld();

		float spd = cfg.speedIsSourceUnits ? (float) DescentMod.su(cfg.speed) : cfg.speed;
		Vec3d dir = cfg.dir.normalize();
		Vec3d shipVel = Vec3d.ZERO;
		float inheritFactor = cfg.inherit;
		float recoilFactor = cfg.recoil;

		if (cfg.owner instanceof PlayerEntity player) {
			DescentPlayerData data = DescentPlayerData.get(player);
			shipVel = data.getFlightVelocity();
			inheritFactor *= data.getWepInherit();
			recoilFactor *= data.getWepRecoil();
		} else {
			shipVel = cfg.owner.getVelocity();
		}

		Vec3d vel = dir.multiply(spd).add(shipVel.multiply(inheritFactor));

		ProjectileEntity proj = ModEntities.PROJECTILE.create(world);
		if (proj == null) return null;
		proj.setOwner(cfg.owner);
		proj.setPosition(cfg.pos);
		proj.setVelocity(vel);
		proj.setDamageClass(cfg.dmgClass);
		proj.setDirectDamage(cfg.directDamage);
		proj.setSplashDamage(cfg.splashDamage);
		// Splash radii from GMod are Source units when > ~20
		float splashR = cfg.splashRadius;
		if (splashR > 20f) splashR = (float) DescentMod.su(splashR);
		proj.setSplashRadius(splashR);
		proj.setLifeTicks((int) (cfg.life * 20));
		proj.setGravityStrength(cfg.gravity);
		proj.setDrag(cfg.drag);
		proj.setHoming(cfg.homing, cfg.turnRate, cfg.homeTarget);
		proj.setPierceCount(cfg.pierceCount);
		proj.setColorRgb(cfg.colorR, cfg.colorG, cfg.colorB);
		proj.setOnHit(cfg.onHit);
		proj.setVisualScale(cfg.visualScale);
		proj.setMeshKind(cfg.meshKind);
		proj.setWorldBlast(cfg.worldBlast);
		proj.setDrillCarve(cfg.drillCarve);
		world.spawnEntity(proj);

		applyRecoil(cfg.owner, dir, recoilFactor);
		return proj;
	}

	public static void applyRecoil(LivingEntity owner, Vec3d dir, float recoilSource) {
		if (recoilSource <= 0) return;
		Vec3d push = dir.normalize().multiply(-DescentMod.su(recoilSource) * 0.15);
		if (owner instanceof PlayerEntity player) {
			DescentPlayerData data = DescentPlayerData.get(player);
			if (data.isEnabled()) {
				data.setFlightVelocity(data.getFlightVelocity().add(push));
				return;
			}
		}
		owner.addVelocity(push.x, push.y, push.z);
		owner.velocityModified = true;
	}

	public static void directDamage(LivingEntity attacker, Entity target, float amount, DamageClass dmgClass) {
		if (amount <= 0 || target == null || !target.isAlive()) return;
		if (target instanceof PlayerEntity ply) {
			DescentPlayerData data = DescentPlayerData.get(ply);
			amount = ShieldSystem.absorb(data, amount, dmgClass);
			if (amount <= 0) return;
		}
		DamageSource src = attacker.getDamageSources().mobAttack(attacker);
		if (attacker instanceof PlayerEntity p) {
			src = attacker.getDamageSources().playerAttack(p);
		}
		target.damage(src, amount);
	}

	public static void splashDamage(LivingEntity attacker, ServerWorld world, Vec3d pos, float amount, float radius, DamageClass dmgClass) {
		if (amount <= 0 || radius <= 0) return;
		Box box = new Box(pos, pos).expand(radius);
		List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != attacker);
		for (LivingEntity e : list) {
			double dist = e.getPos().distanceTo(pos);
			if (dist > radius) continue;
			float falloff = (float) (1.0 - dist / radius);
			directDamage(attacker, e, amount * falloff, dmgClass);
			Vec3d knock = e.getPos().subtract(pos).normalize().multiply(0.4 * falloff);
			e.addVelocity(knock.x, knock.y + 0.1, knock.z);
			e.velocityModified = true;
		}
	}

	public static void hitscan(LivingEntity owner, Vec3d start, Vec3d dir, float rangeSource, float damage, DamageClass dmgClass, Consumer<HitContext> onHit) {
		hitscan(owner, start, dir, rangeSource, damage, dmgClass, onHit, true, dmgClass == DamageClass.ENERGY || dmgClass == DamageClass.EXOTIC);
	}

	public static void hitscan(LivingEntity owner, Vec3d start, Vec3d dir, float rangeSource, float damage,
							   DamageClass dmgClass, Consumer<HitContext> onHit, boolean drawBeam, boolean meltBlocks) {
		if (owner.getWorld().isClient) return;
		ServerWorld world = (ServerWorld) owner.getWorld();
		double range = DescentMod.su(rangeSource);
		Vec3d end = start.add(dir.normalize().multiply(range));
		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
		EntityHitResult entityHit = raycastEntities(owner, start, blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos());

		Vec3d impact = end;
		if (entityHit != null) {
			impact = entityHit.getPos();
			directDamage(owner, entityHit.getEntity(), damage, dmgClass);
			if (onHit != null) onHit.accept(new HitContext(null, entityHit.getEntity(), entityHit.getPos(), dir.negate(), false));
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			impact = blockHit.getPos();
			if (meltBlocks) {
				int intensity = damage >= 100f ? 3 : (damage >= 50f ? 2 : 1);
				com.terminaldetector.drmd.weapon.fx.WeaponFx.melt(world, blockHit.getBlockPos(), intensity, owner);
			}
			if (onHit != null) onHit.accept(new HitContext(null, null, blockHit.getPos(), Vec3d.of(blockHit.getSide().getVector()), true));
		}
		if (drawBeam) {
			if (dmgClass == DamageClass.EXOTIC) {
				com.terminaldetector.drmd.weapon.fx.WeaponFx.beamExotic(world, start, impact);
			} else if (dmgClass == DamageClass.ENERGY) {
				com.terminaldetector.drmd.weapon.fx.WeaponFx.beamEnergy(world, start, impact);
			} else {
				com.terminaldetector.drmd.weapon.fx.WeaponFx.beam(world, start, impact,
						new org.joml.Vector3f(dmgClass.r / 255f, dmgClass.g / 255f, dmgClass.b / 255f), 0.95f);
			}
		}
	}

	private static EntityHitResult raycastEntities(LivingEntity owner, Vec3d start, Vec3d end) {
		Box box = owner.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
		EntityHitResult best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : owner.getWorld().getOtherEntities(owner, box, ent -> ent instanceof LivingEntity && ent.isAlive())) {
			Box eb = e.getBoundingBox().expand(0.3);
			var opt = eb.raycast(start, end);
			if (opt.isPresent()) {
				double d = start.squaredDistanceTo(opt.get());
				if (d < bestDist) {
					bestDist = d;
					best = new EntityHitResult(e, opt.get());
				}
			}
		}
		return best;
	}

	public static Vec3d muzzle(PlayerEntity player, float forward, float right, float up) {
		DescentPlayerData data = DescentPlayerData.get(player);
		Vec3d look = data.shipForward(player);
		Vec3d rr = data.shipRight(player);
		Vec3d uu = data.shipUp(player);
		return player.getEyePos()
				.add(look.multiply(forward))
				.add(rr.multiply(right))
				.add(uu.multiply(up));
	}

	/**
	 * Muzzle from construction clusters — port of D6_Wep.MuzzleFor / D6_SCKBridge.GetMuzzle.
	 * Offsets are Source inches converted to blocks via /16.
	 */
	public static Vec3d muzzleFor(PlayerEntity player, String weaponId, int idx) {
		var off = com.terminaldetector.drmd.workshop.ConstructionRegistry.getMuzzle(weaponId, idx);
		return muzzle(player, off.mcFwd(), off.mcRgt(), off.mcUp());
	}

	/** All construction muzzle world positions for multi-barrel fire. */
	public static java.util.List<Vec3d> allMuzzles(PlayerEntity player, String weaponId) {
		java.util.Map<Integer, com.terminaldetector.drmd.workshop.WeaponClusters.MuzzleOffset> map =
				com.terminaldetector.drmd.workshop.ConstructionRegistry.getMuzzles(weaponId);
		java.util.ArrayList<Vec3d> out = new java.util.ArrayList<>();
		if (map.isEmpty()) {
			out.add(muzzle(player, 0.9f, 0.15f, -0.1f));
			return out;
		}
		java.util.TreeMap<Integer, com.terminaldetector.drmd.workshop.WeaponClusters.MuzzleOffset> sorted =
				new java.util.TreeMap<>(map);
		for (var e : sorted.entrySet()) {
			var o = e.getValue();
			out.add(muzzle(player, o.mcFwd(), o.mcRgt(), o.mcUp()));
		}
		return out;
	}

	public static Vec3d aimDir(PlayerEntity player) {
		return player.getRotationVec(1f);
	}
}
