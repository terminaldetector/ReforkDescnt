package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.registry.WeaponDef;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Base DRMD weapon item — consumes shared energy and fires via WeaponCore.
 */
public class DescentWeaponItem extends Item {
	private final WeaponDef def;

	public DescentWeaponItem(WeaponDef def, Settings settings) {
		super(settings.maxCount(1));
		this.def = def;
	}

	public WeaponDef getDef() { return def; }

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.pass(stack);

		DescentPlayerData data = DescentPlayerData.get(user);
		long now = world.getTime();
		long last = 0L;
		var custom = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		if (custom != null) last = custom.copyNbt().getLong("lastFire");
		int cdTicks = Math.max(1, (int) (def.fireRate * 20));
		if (now - last < cdTicks) return TypedActionResult.fail(stack);

		if (!fire(world, user, data, stack)) {
			return TypedActionResult.fail(stack);
		}

		NbtWrite.lastFire(stack, now);
		user.getItemCooldownManager().set(this, cdTicks);
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.6f, 1.2f);
		return TypedActionResult.success(stack);
	}

	protected boolean fire(World world, PlayerEntity user, DescentPlayerData data, ItemStack stack) {
		return switch (def.behavior) {
			case "laser" -> fireLaser(user, data);
			case "quad_laser" -> fireQuadLaser(user, data);
			case "mega_laser" -> fireMegaLaser(user, data);
			case "rockets" -> fireRockets(user, data);
			case "plasma" -> firePlasma(user, data);
			case "flak" -> fireFlak(user, data);
			case "frag" -> fireFrag(user, data);
			case "beam" -> fireBeam(user, data);
			case "shockwave" -> fireShockwave(user, data);
			case "warp" -> fireWarp(user, data);
			case "telefrag" -> fireTelefrag(user, data);
			case "reactor" -> fireReactor(user, data);
			case "gravy" -> fireGravy(user, data);
			case "whiplash" -> fireWhiplash(user, data);
			case "darklance" -> fireDarklance(user, data);
			case "deploy" -> fireDeploy(user, data);
			case "bfg" -> fireBfg(user, data);
			case "rail" -> fireRail(user, data);
			case "homing" -> fireHoming(user, data);
			case "vulcan", "mg", "heavy", "basic" -> fireBasic(user, data);
			default -> fireBasic(user, data);
		};
	}

	protected boolean consumeEnergy(DescentPlayerData data, float cost) {
		if (cost <= 0) return true;
		return EnergySystem.tryConsume(data, "weapons", cost);
	}

	protected boolean fireBasic(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		// Single-barrel weapons (mg/heavy/basic): primary muzzle only
		// Vulcan: all construction muzzles
		if ("vulcan".equals(def.behavior)) {
			fireFromAllMuzzles(user, cfg -> {
				cfg.directDamage = def.damage;
				cfg.speed = def.speed;
				cfg.recoil = def.recoil;
				cfg.dmgClass = def.dmgClass;
			});
		} else {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.directDamage = def.damage;
			cfg.splashDamage = def.splashDamage;
			cfg.splashRadius = def.splashRadius;
			cfg.speed = def.speed;
			cfg.recoil = def.recoil;
			cfg.dmgClass = def.dmgClass;
			cfg.meshKind = def.dmgClass == DamageClass.EXPLOSIVE
					? com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET
					: (def.dmgClass == DamageClass.EXOTIC
					? com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB
					: com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT);
			cfg.visualScale = def.splashRadius > 200 ? 1.35f : 1f;
			cfg.worldBlast = def.dmgClass == DamageClass.EXPLOSIVE && def.splashRadius >= 180;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	protected boolean firePlasma(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		// Prefer construction muzzles 1+2 (SideLeft/SideRight); fallback dual offset
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		if (muzzles.size() >= 2) {
			for (int i = 0; i < 2; i++) {
				WeaponCore.FireConfig cfg = baseCfg(user);
				cfg.pos = muzzles.get(i);
				cfg.directDamage = def.damage;
				cfg.splashDamage = def.splashDamage;
				cfg.splashRadius = def.splashRadius;
				cfg.speed = def.speed;
				cfg.recoil = i == 0 ? def.recoil : 0;
				cfg.dmgClass = DamageClass.EXOTIC;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
				cfg.visualScale = 0.9f;
				WeaponCore.fireProjectile(cfg);
			}
		} else {
			for (float side : new float[]{-0.25f, 0.25f}) {
				WeaponCore.FireConfig cfg = baseCfg(user);
				cfg.pos = WeaponCore.muzzle(user, 0.8f, side, -0.1f);
				cfg.directDamage = def.damage;
				cfg.splashDamage = def.splashDamage;
				cfg.splashRadius = def.splashRadius;
				cfg.speed = def.speed;
				cfg.recoil = def.recoil * 0.5f;
				cfg.dmgClass = DamageClass.EXOTIC;
				cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
				cfg.visualScale = 0.9f;
				WeaponCore.fireProjectile(cfg);
			}
		}
		return true;
	}

	protected boolean fireFlak(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		for (int i = 0; i < 12; i++) {
			Vec3d dir = spread(aim, 7f, user.getRandom());
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.dir = dir;
			cfg.directDamage = def.damage;
			cfg.splashDamage = def.splashDamage;
			cfg.splashRadius = def.splashRadius;
			cfg.speed = def.speed;
			cfg.recoil = i == 0 ? def.recoil : 0;
			cfg.life = 1.2f;
			cfg.dmgClass = DamageClass.EXPLOSIVE;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
			cfg.visualScale = 0.5f;
			cfg.worldBlast = false;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	protected boolean fireFrag(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.directDamage = def.damage;
		cfg.splashDamage = def.splashDamage;
		cfg.splashRadius = def.splashRadius;
		cfg.speed = def.speed;
		cfg.recoil = def.recoil;
		cfg.dmgClass = DamageClass.EXPLOSIVE;
		cfg.onHit = ctx -> {
			if (user.getWorld().isClient) return;
			for (int i = 0; i < 8; i++) {
				WeaponCore.FireConfig frag = new WeaponCore.FireConfig();
				frag.owner = user;
				frag.pos = ctx.hitPos();
				frag.dir = spread(new Vec3d(0, 1, 0), 180f, user.getRandom());
				frag.speed = 1200;
				frag.directDamage = 20;
				frag.life = 1.5f;
				frag.dmgClass = DamageClass.KINETIC;
				frag.inherit = 0;
				frag.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
				frag.visualScale = 0.4f;
				WeaponCore.fireProjectile(frag);
			}
		};
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_DRILL;
		cfg.visualScale = 1.1f;
		cfg.worldBlast = true;
		cfg.drillCarve = true;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected boolean fireRockets(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		int sub = data.getRocketSubmode();
		int count = switch (sub) { case 1 -> 3; case 2 -> 6; default -> 1; };
		float speed = switch (sub) { case 1 -> 2600f; case 2 -> 2200f; case 3 -> 2000f; default -> 2800f; };
		boolean homing = sub == 2;
		boolean atomic = sub == 3;
		for (int i = 0; i < count; i++) {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.dir = count > 1 ? spread(WeaponCore.aimDir(user), 5f, user.getRandom()) : WeaponCore.aimDir(user);
			cfg.speed = speed;
			cfg.homing = homing;
			cfg.turnRate = 120f;
			cfg.directDamage = atomic ? 420 : Math.max(def.damage, 100f);
			cfg.splashDamage = atomic ? 420 : Math.max(def.splashDamage, 90f);
			cfg.splashRadius = atomic ? 780 : Math.max(def.splashRadius, 320f);
			cfg.recoil = i == 0 ? (atomic ? 220 : 120) : 0;
			cfg.dmgClass = DamageClass.EXPLOSIVE;
			cfg.life = 6f;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
			cfg.visualScale = atomic ? 1.8f : (count > 1 ? 0.85f : 1.2f);
			cfg.worldBlast = true;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	protected boolean fireLaser(PlayerEntity user, DescentPlayerData data) {
		// Charge-style: spend up to 22 energy for scaled damage 40-150 + growing splash
		float spend = Math.min(22f, data.getEnergy());
		if (spend < 4f) return false;
		EnergySystem.tryConsume(data, "weapons", spend);
		float charge = spend / 22f;
		float dmg = 40f + 110f * charge;
		float splash = Math.max(def.splashDamage, 40f) + 80f * charge;
		float splashR = Math.max(def.splashRadius, 120f) + 140f * charge;
		WeaponCore.hitscan(user, user.getEyePos(), WeaponCore.aimDir(user), 8000f, dmg, DamageClass.ENERGY, ctx -> {
			if (!(user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
			float r = (float) com.terminaldetector.drmd.DescentMod.su(splashR);
			WeaponCore.splashDamage(user, sw, ctx.hitPos(), splash, r, DamageClass.ENERGY);
			com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(
					sw, net.minecraft.util.math.BlockPos.ofFloored(ctx.hitPos()),
					1 + (int) (2 * charge), 1 + (int) (2 * charge));
		});
		WeaponCore.applyRecoil(user, WeaponCore.aimDir(user), 30f * charge);
		return true;
	}

	protected boolean fireQuadLaser(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		float splash = Math.max(def.splashDamage, 40f);
		float splashR = (float) com.terminaldetector.drmd.DescentMod.su(Math.max(def.splashRadius, 140f));
		java.util.function.Consumer<WeaponCore.HitContext> splashHit = ctx -> {
			if (!(user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
			WeaponCore.splashDamage(user, sw, ctx.hitPos(), splash, splashR, DamageClass.ENERGY);
		};
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		if (muzzles.size() >= 4) {
			for (int i = 0; i < 4; i++) {
				WeaponCore.hitscan(user, muzzles.get(i), WeaponCore.aimDir(user), 8000f, def.damage, DamageClass.ENERGY, splashHit);
			}
		} else {
			for (float[] off : new float[][]{{-0.2f,0.1f},{0.2f,0.1f},{-0.2f,-0.1f},{0.2f,-0.1f}}) {
				Vec3d start = WeaponCore.muzzle(user, 0.5f, off[0], off[1]);
				WeaponCore.hitscan(user, start, WeaponCore.aimDir(user), 8000f, def.damage, DamageClass.ENERGY, splashHit);
			}
		}
		WeaponCore.applyRecoil(user, WeaponCore.aimDir(user), def.recoil);
		return true;
	}

	/** Mega laser — mega direct hit + mega energy splash radius. */
	protected boolean fireMegaLaser(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		float dmg = Math.max(def.damage, 420f);
		float splash = Math.max(def.splashDamage, 280f);
		float splashR = (float) com.terminaldetector.drmd.DescentMod.su(Math.max(def.splashRadius, 520f));
		WeaponCore.hitscan(user, WeaponCore.muzzleFor(user, def.id, 1), WeaponCore.aimDir(user),
				14000f, dmg, DamageClass.ENERGY, ctx -> {
					if (!(user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
					com.terminaldetector.drmd.weapon.fx.WeaponFx.explode(
							user, sw, ctx.hitPos(), splash, splashR, DamageClass.ENERGY, true);
					com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(
							sw, net.minecraft.util.math.BlockPos.ofFloored(ctx.hitPos()), 10, 5);
				});
		WeaponCore.applyRecoil(user, WeaponCore.aimDir(user), def.recoil);
		return true;
	}

	protected boolean fireBeam(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		float splash = Math.max(def.splashDamage, 18f);
		float splashR = (float) com.terminaldetector.drmd.DescentMod.su(Math.max(def.splashRadius, 90f));
		WeaponCore.hitscan(user, user.getEyePos(), WeaponCore.aimDir(user), 4000f, def.damage, DamageClass.ENERGY, ctx -> {
			if (ctx.hitEntity() instanceof PlayerEntity ply) {
				DescentPlayerData td = DescentPlayerData.get(ply);
				td.setShield(Math.max(0, td.getShield() - 12f));
			}
			if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
				WeaponCore.splashDamage(user, sw, ctx.hitPos(), splash, splashR, DamageClass.ENERGY);
			}
		});
		return true;
	}

	protected boolean fireShockwave(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			WeaponCore.splashDamage(user, sw, user.getPos(), def.splashDamage, def.splashRadius > 20 ? (float)com.terminaldetector.drmd.DescentMod.su(def.splashRadius) : def.splashRadius, DamageClass.ENERGY);
		}
		return true;
	}

	protected boolean fireWarp(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d dest = user.getPos().add(WeaponCore.aimDir(user).multiply(com.terminaldetector.drmd.DescentMod.su(700)));
		user.requestTeleport(dest.x, dest.y, dest.z);
		if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			WeaponCore.splashDamage(user, sw, dest, def.damage, (float)com.terminaldetector.drmd.DescentMod.su(200), DamageClass.EXOTIC);
		}
		return true;
	}

	protected boolean fireTelefrag(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		LivingEntity target = findLookTarget(user, com.terminaldetector.drmd.DescentMod.su(4000));
		if (target == null) return false;
		user.requestTeleport(target.getX(), target.getY(), target.getZ());
		if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			WeaponCore.splashDamage(user, sw, target.getPos(), def.damage, (float)com.terminaldetector.drmd.DescentMod.su(140), DamageClass.EXOTIC);
		}
		return true;
	}

	protected boolean fireReactor(PlayerEntity user, DescentPlayerData data) {
		float spend = Math.max(30f, data.getEnergy());
		if (!EnergySystem.tryConsume(data, "weapons", spend)) return false;
		float frac = spend / data.getEnergyMax();
		float dmg = 150f + 450f * frac;
		float rad = 300f + 600f * frac;
		if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			// Inside / near Sky UFO core — dump reactor to kill the flying hull
			if (com.terminaldetector.drmd.world.mega.SkyUfoEntity.tryReactorDump(sw, user)) {
				return true;
			}
			float r = (float) com.terminaldetector.drmd.DescentMod.su(rad);
			com.terminaldetector.drmd.weapon.fx.WeaponFx.explode(
					user, sw, user.getPos(), dmg, r, DamageClass.EXPLOSIVE, true);
		}
		return true;
	}

	protected boolean fireGravy(PlayerEntity user, DescentPlayerData data) {
		if (!(user instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return false;
		// Toggle grab / fling — Havok-lite via GravyPhysics
		if (com.terminaldetector.drmd.physics.GravyPhysics.isHolding(sp)) {
			if (data.getGravyEnergy() < 10f) return false;
			data.setGravyEnergy(data.getGravyEnergy() - 10f);
			com.terminaldetector.drmd.physics.GravyPhysics.fling(sp, 2.8f);
			data.setGravyGrabbing(false);
			return true;
		}
		if (data.getGravyEnergy() < 15f) return false;
		LivingEntity look = findLookTarget(user, 12);
		if (look != null && look != user) {
			float mass = Math.max(0.4f, look.getWidth() * look.getHeight());
			if (com.terminaldetector.drmd.physics.GravyPhysics.tryGrab(sp, look, mass)) {
				data.setGravyEnergy(data.getGravyEnergy() - 15f);
				data.setGravyGrabbing(true);
				return true;
			}
		}
		// Fallback kinetic bolt when nothing to grab (prop-rail analogue)
		if (data.getGravyEnergy() < 20f) return false;
		data.setGravyEnergy(data.getGravyEnergy() - 20f);
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = 18000;
		cfg.directDamage = 80;
		cfg.pierceCount = 3;
		cfg.dmgClass = DamageClass.EXOTIC;
		cfg.recoil = 40;
		cfg.life = 2f;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
		cfg.visualScale = 0.7f;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected boolean fireWhiplash(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		LivingEntity target = findLookTarget(user, com.terminaldetector.drmd.DescentMod.su(1500));
		if (target == null) {
			// Zip forward
			Vec3d dest = user.getPos().add(WeaponCore.aimDir(user).multiply(com.terminaldetector.drmd.DescentMod.su(800)));
			data.setFlightVelocity(WeaponCore.aimDir(user).multiply(com.terminaldetector.drmd.DescentMod.su(3200)));
			user.requestTeleport(dest.x, dest.y, dest.z);
			return true;
		}
		Vec3d mid = target.getPos();
		user.requestTeleport(mid.x, mid.y, mid.z);
		if (user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			WeaponCore.splashDamage(user, sw, mid, def.damage, (float)com.terminaldetector.drmd.DescentMod.su(160), DamageClass.KINETIC);
		}
		return true;
	}

	protected boolean fireDarklance(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d start = user.getEyePos();
		Vec3d dir = WeaponCore.aimDir(user);
		int hits = 0;
		for (int i = 0; i < 16 && hits < 8; i++) {
			WeaponCore.hitscan(user, start, dir, 2000f, def.damage, DamageClass.EXOTIC, ctx -> {});
			start = start.add(dir.multiply(com.terminaldetector.drmd.DescentMod.su(500)));
			hits++;
		}
		return true;
	}

	protected boolean fireDeploy(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = def.speed > 0 ? def.speed : 900;
		cfg.directDamage = def.damage;
		cfg.splashDamage = def.splashDamage;
		cfg.splashRadius = def.splashRadius;
		cfg.gravity = 0.2f;
		cfg.life = 8f;
		cfg.dmgClass = def.dmgClass;
		cfg.recoil = 10;
		cfg.meshKind = def.dmgClass == DamageClass.EXOTIC
				? com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB
				: com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
		cfg.visualScale = 1.05f;
		cfg.worldBlast = def.dmgClass == DamageClass.EXPLOSIVE || def.dmgClass == DamageClass.EXOTIC;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected boolean fireBfg(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = def.speed;
		cfg.directDamage = def.damage;
		cfg.splashDamage = def.splashDamage;
		cfg.splashRadius = def.splashRadius;
		cfg.dmgClass = DamageClass.EXOTIC;
		cfg.recoil = def.recoil;
		cfg.scale = 2.5f;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
		cfg.visualScale = 2.4f;
		cfg.worldBlast = true;
		cfg.life = 8f;
		cfg.onHit = ctx -> {
			if (!(user.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
			int beams = 0;
			for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, user.getBoundingBox().expand(24),
					ent -> ent != user && ent.isAlive())) {
				if (beams++ >= 16) break;
				WeaponCore.directDamage(user, e, 30f, DamageClass.EXOTIC);
			}
		};
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected boolean fireRail(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = def.speed;
		cfg.directDamage = def.damage;
		cfg.pierceCount = 5;
		cfg.dmgClass = DamageClass.KINETIC;
		cfg.recoil = def.recoil;
		cfg.life = 2f;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
		cfg.visualScale = 0.85f;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected boolean fireHoming(PlayerEntity user, DescentPlayerData data) {
		if (def.energyCost > 0 && !consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = def.speed;
		cfg.directDamage = def.damage;
		cfg.splashDamage = def.splashDamage;
		cfg.splashRadius = def.splashRadius;
		cfg.homing = true;
		cfg.turnRate = 140f;
		cfg.homeTarget = findLookTarget(user, 48);
		cfg.dmgClass = DamageClass.EXPLOSIVE;
		cfg.recoil = def.recoil;
		cfg.life = 15f;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET;
		cfg.visualScale = def.splashRadius > 300 ? 1.6f : 1.15f;
		cfg.worldBlast = true;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	protected WeaponCore.FireConfig baseCfg(PlayerEntity user) {
		WeaponCore.FireConfig cfg = new WeaponCore.FireConfig();
		cfg.owner = user;
		cfg.pos = WeaponCore.muzzleFor(user, def.id, 1);
		cfg.dir = WeaponCore.aimDir(user);
		cfg.dmgClass = def.dmgClass;
		cfg.life = 5f;
		return cfg;
	}

	/** Fire one projectile per construction muzzle (multi-barrel layouts). */
	protected void fireFromAllMuzzles(PlayerEntity user, java.util.function.Consumer<WeaponCore.FireConfig> tune) {
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		int i = 0;
		for (Vec3d pos : muzzles) {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.pos = pos;
			tune.accept(cfg);
			if (i > 0) cfg.recoil = 0; // only first barrel applies ship recoil
			WeaponCore.fireProjectile(cfg);
			i++;
		}
	}

	protected static Vec3d spread(Vec3d dir, float degrees, net.minecraft.util.math.random.Random random) {
		double yaw = Math.toRadians((random.nextDouble() - 0.5) * 2 * degrees);
		double pitch = Math.toRadians((random.nextDouble() - 0.5) * 2 * degrees);
		Vec3d d = dir.normalize();
		Vec3d right = d.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		Vec3d up = right.crossProduct(d).normalize();
		return d.add(right.multiply(Math.tan(yaw))).add(up.multiply(Math.tan(pitch))).normalize();
	}

	protected static LivingEntity findLookTarget(PlayerEntity user, double range) {
		Vec3d start = user.getEyePos();
		Vec3d end = start.add(WeaponCore.aimDir(user).multiply(range));
		LivingEntity best = null;
		double bestDot = 0.92;
		for (LivingEntity e : user.getWorld().getEntitiesByClass(LivingEntity.class,
				user.getBoundingBox().expand(range), ent -> ent != user && ent.isAlive())) {
			Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(start).normalize();
			double dot = to.dotProduct(WeaponCore.aimDir(user));
			if (dot > bestDot && start.squaredDistanceTo(e.getPos()) < range * range) {
				bestDot = dot;
				best = e;
			}
		}
		return best;
	}

	/** Helper for writing lastFire into custom data component. */
	public static final class NbtWrite {
		public static void lastFire(ItemStack stack, long time) {
			net.minecraft.component.type.NbtComponent existing = stack.getOrDefault(
					net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
					net.minecraft.component.type.NbtComponent.DEFAULT);
			net.minecraft.nbt.NbtCompound nbt = existing.copyNbt();
			nbt.putLong("lastFire", time);
			stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
					net.minecraft.component.type.NbtComponent.of(nbt));
		}
	}
}
