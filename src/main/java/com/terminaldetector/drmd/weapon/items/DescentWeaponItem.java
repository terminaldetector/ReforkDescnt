package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.DescentLaserFire;
import com.terminaldetector.drmd.weapon.core.DescentMineFire;
import com.terminaldetector.drmd.weapon.core.DescentRocketFire;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.registry.WeaponDef;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

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

		// Mega Beam — hold to sustain (FP thick white-core cyan column).
		if ("mega_laser".equals(def.behavior)) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}

		if (world.isClient) return TypedActionResult.pass(stack);

		DescentPlayerData data = DescentPlayerData.get(user);

		// Descent laser powerup: sneak + glowstone dust in offhand raises LASER LVL (max 4).
		if ("laser".equals(def.behavior) && user.isSneaking()) {
			ItemStack off = user.getOffHandStack();
			if (off.isOf(net.minecraft.item.Items.GLOWSTONE_DUST)) {
				int lvl = DescentLaserFire.levelOf(stack);
				if (lvl >= 4) {
					user.sendMessage(Text.literal("LASER LVL: 4 (max)"), true);
					return TypedActionResult.fail(stack);
				}
				if (!user.getAbilities().creativeMode) off.decrement(1);
				DescentLaserFire.setLevel(stack, lvl + 1);
				user.sendMessage(Text.literal("LASER LVL: " + (lvl + 1)), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.7f, 1.4f);
				return TypedActionResult.success(stack);
			}
		}

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

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return "mega_laser".equals(def.behavior) ? 72000 : 0;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return "mega_laser".equals(def.behavior) ? UseAction.BOW : UseAction.NONE;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!"mega_laser".equals(def.behavior)) return;
		if (!(user instanceof PlayerEntity player)) return;
		if (world.isClient) {
			com.terminaldetector.drmd.client.DescentClientState.megaBeamActive = true;
			return;
		}
		int used = 72000 - remainingUseTicks;
		if (used % 2 != 0) return;
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!com.terminaldetector.drmd.weapon.core.MegaBeamFire.tick(player, data)) {
			player.stopUsingItem();
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if ("mega_laser".equals(def.behavior) && world.isClient) {
			com.terminaldetector.drmd.client.DescentClientState.megaBeamActive = false;
		}
	}

	/** Which of the five helix bank positions the next shot uses. */
	private int helixStep;

	protected boolean fire(World world, PlayerEntity user, DescentPlayerData data, ItemStack stack) {
		return switch (def.behavior) {
			// Lasers (5)
			case "laser" -> fireLaser(user, data, stack);
			case "laser_pulse" -> fireLaserPulse(user, data);
			case "quad_laser" -> fireQuadLaser(user, data);
			case "mega_laser" -> fireMegaLaser(user, data);
			case "laser_prism" -> fireLaserPrism(user, data);
			// Blasters
			case "spread", "flak" -> fireSpread(user, data);
			case "plasma" -> firePlasma(user, data);
			case "vulcan", "gatling", "mg" -> fireBasic(user, data);
			// Descent 2 super primaries — each is the D1 weapon taken one step further,
			// which is the relationship the originals had and the reason they are not reskins.
			case "gauss" -> fireGauss(user, data);
			case "helix" -> fireHelix(user, data);
			case "phoenix" -> firePhoenix(user, data);
			case "omega" -> fireOmega(user, data);
			// Rockets (6)
			case "rocket_light", "rocket_offense", "rocket_dual", "rocket_triple",
					"rocket_heavy", "rocket_mega", "rockets", "homing" -> fireDescentRocket(user, data);
			// Air mines (4)
			case "mine_prox", "mine_plasma", "mine_energy", "mine_smart",
					"deploy", "plasmamine", "gravmine", "energytrap", "darkfield" -> fireAirMine(user, data);
			// Unique
			case "bfg" -> fireBfg(user, data);
			case "beam_lance" -> fireBeamLance(user, data);
			case "warp" -> fireWarp(user, data);
			// Retired / leftover
			case "beam" -> fireBeam(user, data);
			case "shockwave" -> fireShockwave(user, data);
			case "telefrag" -> fireTelefrag(user, data);
			case "reactor" -> fireReactor(user, data);
			case "gravy" -> fireGravy(user, data);
			case "whiplash" -> fireWhiplash(user, data);
			case "darklance" -> fireDarklance(user, data);
			case "frag" -> fireFrag(user, data);
			case "rail" -> fireRail(user, data);
			case "heavy", "basic" -> fireBasic(user, data);
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
		if ("vulcan".equals(def.behavior) || "gatling".equals(def.behavior)) {
			fireFromAllMuzzles(user, cfg -> {
				cfg.directDamage = def.damage;
				cfg.speed = def.speed;
				cfg.recoil = def.recoil;
				cfg.dmgClass = def.dmgClass;
				cfg.colorR = "vulcan".equals(def.behavior) ? 0x66 : 0xCC;
				cfg.colorG = "vulcan".equals(def.behavior) ? 0xFF : 0xCC;
				cfg.colorB = "vulcan".equals(def.behavior) ? 0x66 : 0xAA;
				cfg.visualScale = "gatling".equals(def.behavior) ? 0.7f : 0.85f;
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

	/**
	 * Descent primary laser — dual travel-time bolts from wing modules,
	 * converging on the reticle. Level 1–4 on the stack (powerup / {@code /d6 laserlevel}).
	 */
	protected boolean fireLaser(PlayerEntity user, DescentPlayerData data, ItemStack stack) {
		int level = DescentLaserFire.levelOf(stack);
		float cost = DescentLaserFire.primaryEnergy(level);
		if (!consumeEnergy(data, cost)) return false;
		return DescentLaserFire.firePrimary(user, def.id, stack);
	}

	protected boolean fireLaserPulse(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost > 0 ? def.energyCost : 2.5f)) return false;
		return DescentLaserFire.firePulse(user, def.id);
	}

	/** Quad laser — four module banks, Descent bolt projectiles with convergence. */
	protected boolean fireQuadLaser(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		float splash = Math.max(def.splashDamage, 40f);
		float splashR = Math.max(def.splashRadius, 140f);
		return DescentLaserFire.fireQuad(user, def.id, def.damage, splash, splashR);
	}

	/** Legacy bolt volley — Mega Beam is hold-to-sustain via {@link #usageTick}. */
	protected boolean fireMegaLaser(PlayerEntity user, DescentPlayerData data) {
		return com.terminaldetector.drmd.weapon.core.MegaBeamFire.tick(user, data);
	}

	protected boolean fireLaserPrism(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost > 0 ? def.energyCost : 6f)) return false;
		return DescentLaserFire.firePrism(user, def.id);
	}

	/** Descent Spreadfire — kinetic pellet cone from modules. */
	/**
	 * Gauss — the Vulcan taken to a rail gun.
	 *
	 * <p>Hitscan rather than a fast bullet, which is the whole difference in Descent 2: the Vulcan
	 * leads a moving target and the Gauss does not. Punches through what it hits instead of melting
	 * it, so it is a weapon against ships rather than against walls.
	 */
	protected boolean fireGauss(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		Vec3d origin = muzzles.isEmpty() ? WeaponCore.muzzle(user, 0.7f, 0f, -0.1f) : muzzles.get(0);
		if (user instanceof net.minecraft.entity.LivingEntity living) {
			WeaponCore.hitscan(living, origin, aim, 9000f, def.damage, DamageClass.KINETIC,
					null, true, false);
		}
		WeaponCore.applyRecoil(user, aim, def.recoil);
		return true;
	}

	/**
	 * Helix — the Spreadfire taken from a cross to a rotating fan.
	 *
	 * <p>Five bolts on a line through the crosshair, and the line turns a step every shot. Held down
	 * it sweeps a volume rather than a plane, which is what made it the room-clearing weapon: the
	 * spread is deterministic, so it is aimed rather than sprayed.
	 */
	protected boolean fireHelix(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		Vec3d origin = muzzles.isEmpty() ? WeaponCore.muzzle(user, 0.7f, 0f, -0.1f) : muzzles.get(0);

		// The bank steps 36° a shot: five positions before it repeats, so a burst covers the circle.
		helixStep = (helixStep + 1) % 5;
		double bank = Math.toRadians(helixStep * 36.0);
		Vec3d right = aim.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1.0e-4) right = aim.crossProduct(new Vec3d(1, 0, 0));
		right = right.normalize();
		Vec3d up = right.crossProduct(aim).normalize();
		Vec3d axis = right.multiply(Math.cos(bank)).add(up.multiply(Math.sin(bank)));

		for (int i = -2; i <= 2; i++) {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.pos = origin;
			cfg.dir = aim.add(axis.multiply(i * 0.11)).normalize();
			cfg.directDamage = def.damage;
			cfg.splashDamage = def.splashDamage;
			cfg.splashRadius = def.splashRadius;
			cfg.speed = def.speed;
			cfg.recoil = i == -2 ? def.recoil : 0;
			cfg.life = 1.4f;
			cfg.dmgClass = DamageClass.KINETIC;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
			cfg.visualScale = 0.8f;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	/**
	 * Phoenix — the Plasma taken to a bolt that comes back at you.
	 *
	 * <p>Fires low so the bolts skip off the floor of a corridor. The bounce itself is the shot
	 * pattern rather than a physics property: two bolts on slightly divergent lines with a long life,
	 * which in a corridor reads as the ricochet the original had. A real reflecting projectile wants
	 * a bounce flag on the projectile entity, and that is worth doing when the projectile framework
	 * grows one — noted rather than pretended.
	 */
	protected boolean firePhoenix(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		for (int i = 0; i < 2; i++) {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.pos = muzzles.size() > i ? muzzles.get(i) : WeaponCore.muzzle(user, 0.7f, 0f, -0.1f);
			cfg.dir = aim.add(new Vec3d(0, -0.05 - i * 0.02, 0)).normalize();
			cfg.directDamage = def.damage;
			cfg.splashDamage = def.splashDamage;
			cfg.splashRadius = def.splashRadius;
			cfg.speed = def.speed;
			cfg.recoil = i == 0 ? def.recoil : 0;
			cfg.life = 3.5f;
			cfg.dmgClass = DamageClass.EXOTIC;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ORB;
			cfg.visualScale = 1.05f;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	/**
	 * Omega — the Fusion taken from a charge to a chain.
	 *
	 * <p>No projectile at all: it reaches the nearest thing in front of the ship and jumps from there
	 * to whatever is near it. That is why it drinks energy — in Descent 2 the Omega runs off the
	 * ship's own supply rather than ammunition, and firing it flat is what empties you.
	 */
	protected boolean fireOmega(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		if (!(user instanceof net.minecraft.entity.LivingEntity living)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		Vec3d origin = muzzles.isEmpty() ? WeaponCore.muzzle(user, 0.7f, 0f, -0.1f) : muzzles.get(0);

		WeaponCore.hitscan(living, origin, aim, 1800f, def.damage, DamageClass.EXOTIC,
				hit -> chainFrom(user, hit.hitPos(), def.damage), true, false);
		return true;
	}

	/**
	 * The jump: everything within reach of where the arc landed takes a share, twice.
	 *
	 * <p>Each link is weaker than the last, so a crowd is worth more than a single target without the
	 * chain being free damage forever.
	 */
	private void chainFrom(PlayerEntity user, Vec3d from, float damage) {
		if (!(user.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
		double reach = 7.0;
		float linkDamage = damage * 0.6f;
		Vec3d at = from;
		for (int link = 0; link < 2 && linkDamage > 1f; link++) {
			net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
					at.x - reach, at.y - reach, at.z - reach, at.x + reach, at.y + reach, at.z + reach);
			var targets = world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, box,
					e -> e != user && e.isAlive());
			if (targets.isEmpty()) return;
			net.minecraft.entity.LivingEntity next = targets.get(0);
			double best = Double.MAX_VALUE;
			for (var candidate : targets) {
				double d = candidate.getPos().squaredDistanceTo(at);
				if (d < best) {
					best = d;
					next = candidate;
				}
			}
			WeaponCore.directDamage(user, next, linkDamage, DamageClass.EXOTIC);
			com.terminaldetector.drmd.weapon.fx.WeaponFx.beamExotic(world, at, next.getPos().add(0, 0.9, 0));
			at = next.getPos().add(0, 0.9, 0);
			linkDamage *= 0.6f;
		}
	}

	protected boolean fireSpread(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		Vec3d aim = WeaponCore.aimDir(user);
		var muzzles = WeaponCore.allMuzzles(user, def.id);
		Vec3d origin = muzzles.isEmpty() ? WeaponCore.muzzle(user, 0.7f, 0f, -0.1f) : muzzles.get(0);
		for (int i = 0; i < 8; i++) {
			WeaponCore.FireConfig cfg = baseCfg(user);
			cfg.pos = origin;
			cfg.dir = spread(aim, 9f, user.getRandom());
			cfg.directDamage = def.damage;
			cfg.splashDamage = def.splashDamage;
			cfg.splashRadius = def.splashRadius;
			cfg.speed = def.speed;
			cfg.recoil = i == 0 ? def.recoil : 0;
			cfg.life = 1.1f;
			cfg.dmgClass = DamageClass.KINETIC;
			cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
			cfg.visualScale = 0.55f;
			cfg.colorR = 0xFF; cfg.colorG = 0xEE; cfg.colorB = 0x66;
			WeaponCore.fireProjectile(cfg);
		}
		return true;
	}

	protected boolean fireDescentRocket(PlayerEntity user, DescentPlayerData data) {
		var kind = DescentRocketFire.Kind.fromBehavior(def.behavior);
		float cost = def.energyCost > 0 ? def.energyCost : kind.energy;
		if (!consumeEnergy(data, cost)) return false;
		return DescentRocketFire.fire(user, kind);
	}

	protected boolean fireAirMine(PlayerEntity user, DescentPlayerData data) {
		var kind = DescentMineFire.Kind.fromBehavior(def.behavior);
		float cost = def.energyCost > 0 ? def.energyCost : kind.energy;
		if (!consumeEnergy(data, cost)) return false;
		return DescentMineFire.fire(user, kind);
	}

	/** Unique large beam projectile — fat energy lance, not hitscan. */
	protected boolean fireBeamLance(PlayerEntity user, DescentPlayerData data) {
		if (!consumeEnergy(data, def.energyCost)) return false;
		WeaponCore.FireConfig cfg = baseCfg(user);
		cfg.speed = def.speed > 0 ? def.speed : 2800f;
		cfg.directDamage = Math.max(def.damage, 160f);
		cfg.splashDamage = Math.max(def.splashDamage, 80f);
		cfg.splashRadius = Math.max(def.splashRadius, 200f);
		cfg.life = 2.8f;
		cfg.pierceCount = 3;
		cfg.dmgClass = DamageClass.ENERGY;
		cfg.meshKind = com.terminaldetector.drmd.entity.ProjectileEntity.MESH_BOLT;
		cfg.visualScale = 2.6f;
		cfg.igniteOnHit = true;
		cfg.recoil = def.recoil;
		cfg.colorR = 0x44; cfg.colorG = 0xFF; cfg.colorB = 0xAA;
		WeaponCore.fireProjectile(cfg);
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
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(d);
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

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		if ("laser".equals(def.behavior)) {
			int lvl = DescentLaserFire.levelOf(stack);
			tooltip.add(Text.literal("LASER LVL: " + lvl + "  ·  dual module bolts"));
			tooltip.add(Text.literal("Energy/shot: " + DescentLaserFire.primaryEnergy(lvl)));
		} else if ("quad_laser".equals(def.behavior) || "mega_laser".equals(def.behavior)) {
			tooltip.add(Text.literal("Descent bolts from weapon modules (converge)"));
		}
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
