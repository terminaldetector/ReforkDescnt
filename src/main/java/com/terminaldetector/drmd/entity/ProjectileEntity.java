package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Physical projectile — port of prop_physics projectiles from D6_Wep.FireProjectile.
 */
public class ProjectileEntity extends Entity {
	private UUID ownerUuid;
	private LivingEntity owner;
	private DamageClass dmgClass = DamageClass.KINETIC;
	private float directDamage;
	private float splashDamage;
	private float splashRadius;
	private int lifeTicks = 100;
	private float gravityStrength;
	private float drag;
	private boolean homing;
	private float turnRate = 90f;
	private Entity homeTarget;
	private int pierceCount;
	private int colorR = 180, colorG = 220, colorB = 255;
	private Consumer<WeaponCore.HitContext> onHit;
	private final Set<Integer> pierced = new HashSet<>();

	public ProjectileEntity(EntityType<? extends ProjectileEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		this.ownerUuid = owner.getUuid();
	}

	public LivingEntity getOwnerLiving() {
		if (owner != null && owner.isAlive()) return owner;
		if (ownerUuid != null && getWorld() instanceof ServerWorld sw) {
			Entity e = sw.getEntity(ownerUuid);
			if (e instanceof LivingEntity le) {
				owner = le;
				return le;
			}
		}
		return null;
	}

	public void setDamageClass(DamageClass c) { this.dmgClass = c; colorR = c.r; colorG = c.g; colorB = c.b; }
	public void setDirectDamage(float v) { this.directDamage = v; }
	public void setSplashDamage(float v) { this.splashDamage = v; }
	public void setSplashRadius(float v) { this.splashRadius = v; }
	public void setLifeTicks(int v) { this.lifeTicks = v; }
	public void setGravityStrength(float v) { this.gravityStrength = v; }
	public void setDrag(float v) { this.drag = v; }
	public void setHoming(boolean h, float turn, Entity target) { this.homing = h; this.turnRate = turn; this.homeTarget = target; }
	public void setPierceCount(int v) { this.pierceCount = v; }
	public void setColor(int r, int g, int b) { colorR = r; colorG = g; colorB = b; }
	public void setOnHit(Consumer<WeaponCore.HitContext> cb) { this.onHit = cb; }
	public int getColorR() { return colorR; }
	public int getColorG() { return colorG; }
	public int getColorB() { return colorB; }

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			getWorld().addParticle(new DustParticleEffect(new Vector3f(colorR / 255f, colorG / 255f, colorB / 255f), 1.0f),
					getX(), getY(), getZ(), 0, 0, 0);
			return;
		}

		lifeTicks--;
		if (lifeTicks <= 0) {
			discard();
			return;
		}

		Vec3d vel = getVelocity();

		if (homing) {
			Entity target = homeTarget;
			if (target == null || !target.isAlive()) {
				target = findTarget();
				homeTarget = target;
			}
			if (target != null) {
				Vec3d desired = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos()).normalize();
				Vec3d cur = vel.lengthSquared() > 1e-6 ? vel.normalize() : desired;
				double maxRad = Math.toRadians(turnRate / 20.0);
				Vec3d blended = cur.add(desired.subtract(cur).multiply(Math.min(1.0, maxRad * 3))).normalize();
				vel = blended.multiply(vel.length());
			}
		}

		if (gravityStrength != 0) vel = vel.add(0, -gravityStrength * 0.05, 0);
		if (drag > 0 && vel.lengthSquared() > 1e-6) {
			vel = vel.multiply(Math.max(0, 1.0 - drag * 0.02));
		}
		setVelocity(vel);

		Vec3d next = getPos().add(vel);
		HitResult hit = getWorld().raycast(new net.minecraft.world.RaycastContext(
				getPos(), next,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, this));

		EntityHitResult eHit = raycastEntity(getPos(), next);
		if (eHit != null && (hit.getType() == HitResult.Type.MISS || eHit.getPos().squaredDistanceTo(getPos()) < hit.getPos().squaredDistanceTo(getPos()))) {
			onEntityHit(eHit);
			return;
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			onBlockHit((BlockHitResult) hit);
			return;
		}
		setPosition(next);
	}

	private Entity findTarget() {
		LivingEntity own = getOwnerLiving();
		double best = 64 * 64;
		Entity found = null;
		for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(48),
				ent -> ent instanceof LivingEntity && ent.isAlive() && ent != own)) {
			double d = e.squaredDistanceTo(this);
			if (d < best) { best = d; found = e; }
		}
		return found;
	}

	private EntityHitResult raycastEntity(Vec3d start, Vec3d end) {
		LivingEntity own = getOwnerLiving();
		EntityHitResult best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().stretch(end.subtract(start)).expand(0.5),
				ent -> ent instanceof LivingEntity && ent.isAlive() && ent != own && !pierced.contains(ent.getId()))) {
			var opt = e.getBoundingBox().expand(0.3).raycast(start, end);
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

	private void onEntityHit(EntityHitResult hit) {
		LivingEntity own = getOwnerLiving();
		if (own != null) {
			WeaponCore.directDamage(own, hit.getEntity(), directDamage, dmgClass);
			if (splashRadius > 0 && getWorld() instanceof ServerWorld sw) {
				WeaponCore.splashDamage(own, sw, hit.getPos(), splashDamage, splashRadius, dmgClass);
			}
		}
		if (onHit != null) onHit.accept(new WeaponCore.HitContext(this, hit.getEntity(), hit.getPos(), getVelocity().negate().normalize(), false));
		pierced.add(hit.getEntity().getId());
		if (pierceCount > 0) {
			pierceCount--;
			setPosition(hit.getPos().add(getVelocity().normalize().multiply(0.5)));
		} else {
			discard();
		}
	}

	private void onBlockHit(BlockHitResult hit) {
		LivingEntity own = getOwnerLiving();
		if (own != null && splashRadius > 0 && getWorld() instanceof ServerWorld sw) {
			WeaponCore.splashDamage(own, sw, hit.getPos(), splashDamage > 0 ? splashDamage : directDamage * 0.5f, splashRadius, dmgClass);
		}
		if (onHit != null) onHit.accept(new WeaponCore.HitContext(this, null, hit.getPos(), Vec3d.of(hit.getSide().getVector()), true));
		discard();
	}

	@Override
	protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		lifeTicks = nbt.getInt("life");
		directDamage = nbt.getFloat("dmg");
		splashDamage = nbt.getFloat("splash");
		splashRadius = nbt.getFloat("radius");
		dmgClass = DamageClass.fromId(nbt.getString("class"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("life", lifeTicks);
		nbt.putFloat("dmg", directDamage);
		nbt.putFloat("splash", splashDamage);
		nbt.putFloat("radius", splashRadius);
		nbt.putString("class", dmgClass.id);
	}

	@Override
	public void onSpawnPacket(EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
	}
}
