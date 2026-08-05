package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.MissileSteering;
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
	public static final int MESH_MINE = 4;

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
	private boolean pilotGuided;
	private float turnRate = 90f;
	private Entity homeTarget;
	private int pierceCount;
	private boolean worldBlast;
	private boolean drillCarve;
	private Consumer<WeaponCore.HitContext> onHit;
	private final Set<Integer> pierced = new HashSet<>();

	private com.terminaldetector.drmd.weapon.projectile.FuseType fuse =
			com.terminaldetector.drmd.weapon.projectile.FuseType.IMPACT;
	private int fuseTicks;
	private int armTicks;
	private float proximityRadius;
	private int ageTicks;
	private boolean igniteOnHit;

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

	/**
	 * Fly the owner's aim rather than a target — the Guided Missile.
	 *
	 * <p>Steering to a direction and not to a point is the faithful reading: in Descent the pilot's
	 * stick becomes the missile's stick, so it goes where you are facing, not where you are looking
	 * at. That is what lets it be walked around a corner into a room you cannot see into.
	 */
	public void setPilotGuided(boolean v) { this.pilotGuided = v; }
	public void setPierceCount(int v) { this.pierceCount = v; }
	public void setOnHit(Consumer<WeaponCore.HitContext> cb) { this.onHit = cb; }
	public void setWorldBlast(boolean v) { this.worldBlast = v; }
	public void setDrillCarve(boolean v) { this.drillCarve = v; }

	/**
	 * Arm the fuse.
	 *
	 * @param fuse       trigger condition
	 * @param fuseSecs   burn time for {@link com.terminaldetector.drmd.weapon.projectile.FuseType#TIMED}
	 * @param armSecs    dead time after launch during which no fuse can trigger
	 * @param proxRadius trigger radius in blocks for
	 *                   {@link com.terminaldetector.drmd.weapon.projectile.FuseType#PROXIMITY}
	 */
	public void setFuse(com.terminaldetector.drmd.weapon.projectile.FuseType fuse,
						float fuseSecs, float armSecs, float proxRadius) {
		this.fuse = fuse == null ? com.terminaldetector.drmd.weapon.projectile.FuseType.IMPACT : fuse;
		this.fuseTicks = Math.max(0, (int) (fuseSecs * 20));
		this.armTicks = Math.max(0, (int) (armSecs * 20));
		this.proximityRadius = Math.max(0f, proxRadius);
	}

	/** Energy weapons that burn: block hits start a fire focus, entity hits catch light. */
	public void setIgniteOnHit(boolean v) { this.igniteOnHit = v; }

	public com.terminaldetector.drmd.weapon.projectile.FuseType getFuse() { return fuse; }

	/** False while the arming delay is still running — contact and proximity are inert. */
	public boolean isArmed() { return ageTicks >= armTicks; }

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
			drawTracer();
			return;
		}

		lifeTicks--;
		if (lifeTicks <= 0) {
			discard();
			return;
		}
		ageTicks++;

		// Fuses that do not need contact get their chance before the movement trace.
		if (isArmed()) {
			if (fuse == com.terminaldetector.drmd.weapon.projectile.FuseType.TIMED
					&& ageTicks >= armTicks + fuseTicks) {
				detonateInPlace();
				return;
			}
			if (fuse == com.terminaldetector.drmd.weapon.projectile.FuseType.PROXIMITY
					&& proximityRadius > 0 && nearestTriggerWithin(proximityRadius) != null) {
				detonateInPlace();
				return;
			}
		}

		Vec3d vel = getVelocity();

		if (pilotGuided) {
			// No lock to acquire or lose: while the pilot is there to fly it, it turns when they turn.
			// Once they are not — dead, gone, on foot elsewhere — it coasts on its last heading.
			LivingEntity own = getOwnerLiving();
			if (own instanceof PlayerEntity ply) {
				vel = MissileSteering.steer(vel, WeaponCore.aimDir(ply), turnRate);
			}
		} else if (homing) {
			Entity target = homeTarget;
			if (target == null || !target.isAlive()) {
				target = findTarget();
				homeTarget = target;
			}
			if (target != null) {
				vel = MissileSteering.steer(vel,
						target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos()), turnRate);
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

		// A mine or an unarmed shell coasts through everything instead of striking it.
		boolean contactLive = isArmed()
				&& fuse != com.terminaldetector.drmd.weapon.projectile.FuseType.PROXIMITY
				&& fuse != com.terminaldetector.drmd.weapon.projectile.FuseType.TIMED;
		if (contactLive) {
			EntityHitResult eHit = raycastEntity(getPos(), next);
			if (eHit != null && (hit.getType() == HitResult.Type.MISS
					|| eHit.getPos().squaredDistanceTo(getPos()) < hit.getPos().squaredDistanceTo(getPos()))) {
				onEntityHit(eHit);
				return;
			}
			if (hit.getType() == HitResult.Type.BLOCK) {
				onBlockHit((BlockHitResult) hit);
				return;
			}
		} else if (hit.getType() == HitResult.Type.BLOCK
				&& fuse == com.terminaldetector.drmd.weapon.projectile.FuseType.PROXIMITY) {
			// Mines settle where they land and wait for a customer.
			setPosition(hit.getPos().subtract(getVelocity().normalize().multiply(0.15)));
			setVelocity(Vec3d.ZERO);
			return;
		}
		setPosition(next);
	}

	/**
	 * The visible shot: a bead of light every half block along the ground the round covered this tick.
	 *
	 * <p>This used to drop one particle per tick at the entity's position, which is why the guns
	 * looked like they were firing nothing. A Descent laser moves 6200 source units a second — some
	 * seventy blocks in a tick — so one bead per tick is one dot of light every seventy blocks, and
	 * the round is past the far wall before a second one is drawn. Nothing joins them up, so there is
	 * no bolt on screen at any point in its flight.
	 *
	 * <p>Filling the segment instead gives a continuous streak at any speed, which is what the
	 * original's bolts read as. Spacing is fixed rather than a fixed bead count so a slow rocket does
	 * not get a dense clot of particles around it, and the count is capped so a fast round costs a
	 * bounded amount.
	 */
	private void drawTracer() {
		Vec3d vel = getVelocity();
		float scale = getVisualScale();
		boolean bolt = getMeshKind() == MESH_BOLT;
		Vector3f rgb = new Vector3f(getColorR() / 255f, getColorG() / 255f, getColorB() / 255f);
		float size = (bolt ? 1.5f : 1.15f) * Math.max(0.6f, scale);

		double travel = vel.length();
		if (travel < 1e-3) {
			getWorld().addParticle(new DustParticleEffect(rgb, size), getX(), getY(), getZ(), 0, 0, 0);
			return;
		}

		// Where it was a tick ago: the client has already moved it, so walk back down the velocity.
		Vec3d head = getPos();
		Vec3d back = vel.normalize();
		int beads = (int) Math.min(48, Math.max(2, travel / 0.5));
		for (int i = 0; i < beads; i++) {
			double along = travel * i / (double) beads;
			Vec3d p = head.subtract(back.multiply(along));
			// The head stays brightest; the tail thins out so the streak has a direction.
			float f = size * (1f - 0.55f * i / (float) beads);
			getWorld().addParticle(new DustParticleEffect(rgb, f), p.x, p.y, p.z, 0, 0, 0);
		}
	}

	/** Closest thing the fuse would consider worth going off for, or null. */
	private Entity nearestTriggerWithin(double radius) {
		LivingEntity own = getOwnerLiving();
		Entity best = null;
		double bestDist = radius * radius;
		for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(radius),
				ent -> ent instanceof LivingEntity && ent.isAlive() && ent != own)) {
			double d = e.squaredDistanceTo(this);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	/** Go off where it sits — timed air-burst and proximity trigger both land here. */
	private void detonateInPlace() {
		LivingEntity own = getOwnerLiving();
		if (own != null) {
			detonate(own, getPos());
		}
		if (onHit != null) {
			onHit.accept(new WeaponCore.HitContext(this, null, getPos(), new Vec3d(0, 1, 0), false));
		}
		discard();
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
		if (igniteOnHit) {
			hit.getEntity().setOnFireFor(4);
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
			if (igniteOnHit) {
				// Seed the shared fire sim on the struck face; it handles spread and smoke.
				BlockPos face = hit.getBlockPos().offset(hit.getSide());
				com.terminaldetector.drmd.world.fire.FireSystem.ignite(sw, face, 3);
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
