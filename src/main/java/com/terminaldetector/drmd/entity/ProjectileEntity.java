package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Physical projectile — port of prop_physics projectiles from D6_Wep.FireProjectile.
 * Client mesh/color synced via DataTracker for 3D projectile silhouettes.
 */
public class ProjectileEntity extends Entity {
	public static final int MESH_BOLT = 0;
	public static final int MESH_ROCKET = 1;
	public static final int MESH_ORB = 2;
	public static final int MESH_DRILL = 3;

	private static final TrackedData<Integer> COLOR = DataTracker.registerData(ProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> SCALE = DataTracker.registerData(ProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> MESH = DataTracker.registerData(ProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);

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
	private boolean worldBlast;
	private boolean drillCarve;
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

	public void setDamageClass(DamageClass c) {
		this.dmgClass = c;
		setColorRgb(c.r, c.g, c.b);
	}

	public void setDirectDamage(float v) { this.directDamage = v; }
	public void setSplashDamage(float v) { this.splashDamage = v; }
	public void setSplashRadius(float v) { this.splashRadius = v; }
	public void setLifeTicks(int v) { this.lifeTicks = v; }
	public void setGravityStrength(float v) { this.gravityStrength = v; }
	public void setDrag(float v) { this.drag = v; }
	public void setHoming(boolean h, float turn, Entity target) { this.homing = h; this.turnRate = turn; this.homeTarget = target; }
	public void setPierceCount(int v) { this.pierceCount = v; }
	public void setOnHit(Consumer<WeaponCore.HitContext> cb) { this.onHit = cb; }
	public void setWorldBlast(boolean v) { this.worldBlast = v; }
	public void setDrillCarve(boolean v) { this.drillCarve = v; }

	public void setColorRgb(int r, int g, int b) {
		dataTracker.set(COLOR, ((r & 255) << 16) | ((g & 255) << 8) | (b & 255));
	}

	public void setVisualScale(float s) { dataTracker.set(SCALE, s); }
	public void setMeshKind(int kind) { dataTracker.set(MESH, kind); }

	public int getColorRgb() { return dataTracker.get(COLOR); }
	public float getVisualScale() { return dataTracker.get(SCALE); }
	public int getMeshKind() { return dataTracker.get(MESH); }
	public int getColorR() { return (getColorRgb() >> 16) & 255; }
	public int getColorG() { return (getColorRgb() >> 8) & 255; }
	public int getColorB() { return getColorRgb() & 255; }
	public DamageClass getDamageClass() { return dmgClass; }

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			getWorld().addParticle(new DustParticleEffect(
							new Vector3f(getColorR() / 255f, getColorG() / 255f, getColorB() / 255f), 1.0f),
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

		if (gravityStrength != 0) {
			LivingEntity own = getOwnerLiving();
			Vec3d gdir = own instanceof PlayerEntity
					? LocalOrientation.gravityDir(own.getUuid())
					: new Vec3d(0, -1, 0);
			vel = vel.add(gdir.multiply(gravityStrength * 0.05));
		}
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
			detonate(own, hit.getPos());
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
		if (own != null && getWorld() instanceof ServerWorld sw) {
			detonate(own, hit.getPos());
			if (drillCarve) {
				WeaponFx.drillCarve(sw, hit.getBlockPos(), own);
			} else if (dmgClass == DamageClass.ENERGY || dmgClass == DamageClass.EXOTIC) {
				WeaponFx.melt(sw, hit.getBlockPos(), splashRadius > 1.5f ? 2 : 1, own);
			}
		}
		if (onHit != null) onHit.accept(new WeaponCore.HitContext(this, null, hit.getPos(), Vec3d.of(hit.getSide().getVector()), true));
		discard();
	}

	private void detonate(LivingEntity own, Vec3d pos) {
		if (!(getWorld() instanceof ServerWorld sw)) return;
		if (splashRadius <= 0) return;
		float splash = splashDamage > 0 ? splashDamage : directDamage * 0.5f;
		if (worldBlast || dmgClass == DamageClass.EXPLOSIVE) {
			boolean crater = worldBlast || splashRadius >= 3.5f;
			WeaponFx.explode(own, sw, pos, splash, splashRadius, dmgClass, crater);
		} else {
			WeaponCore.splashDamage(own, sw, pos, splash, splashRadius, dmgClass);
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(COLOR, 0xB4DCFF);
		builder.add(SCALE, 1f);
		builder.add(MESH, MESH_BOLT);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		lifeTicks = nbt.getInt("life");
		directDamage = nbt.getFloat("dmg");
		splashDamage = nbt.getFloat("splash");
		splashRadius = nbt.getFloat("radius");
		dmgClass = DamageClass.fromId(nbt.getString("class"));
		worldBlast = nbt.getBoolean("blast");
		drillCarve = nbt.getBoolean("drill");
		setVisualScale(nbt.contains("scale") ? nbt.getFloat("scale") : 1f);
		setMeshKind(nbt.getInt("mesh"));
		setColorRgb(dmgClass.r, dmgClass.g, dmgClass.b);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("life", lifeTicks);
		nbt.putFloat("dmg", directDamage);
		nbt.putFloat("splash", splashDamage);
		nbt.putFloat("radius", splashRadius);
		nbt.putString("class", dmgClass.id);
		nbt.putBoolean("blast", worldBlast);
		nbt.putBoolean("drill", drillCarve);
		nbt.putFloat("scale", getVisualScale());
		nbt.putInt("mesh", getMeshKind());
	}

	@Override
	public void onSpawnPacket(EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
	}
}
