package com.terminaldetector.drmd.world.bombardment;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Falling / powered aerial munition — smoke trail, thin-air accel, crater on impact.
 * Clusters burst mid-air into physically scattered submunitions.
 */
public class AerialBombEntity extends Entity {
	private OrdnanceType type = OrdnanceType.TNT_BOMB;
	private UUID ownerId;
	private BlockPos laserTarget;
	private int ageTicks;
	/** Child bomblet — will not re-cluster. */
	private boolean submunition;
	private boolean dispensed;
	/** Multiplier on detonation blast for fragment bomblets. */
	private float blastMul = 1f;

	public AerialBombEntity(EntityType<? extends AerialBombEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	public void configure(OrdnanceType ordnance, LivingEntity owner, BlockPos laserTarget) {
		this.type = ordnance;
		if (owner != null) this.ownerId = owner.getUuid();
		this.laserTarget = laserTarget;
	}

	public void configureSubmunition(OrdnanceType ordnance, UUID owner, float blastMul, Vec3d velocity) {
		this.type = ordnance;
		this.ownerId = owner;
		this.submunition = true;
		this.blastMul = blastMul;
		this.laserTarget = null;
		setVelocity(velocity);
	}

	public OrdnanceType getOrdnance() {
		return type;
	}

	@Override
	protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		ageTicks++;
		if (getWorld().isClient) {
			float r = ((type.trailColor >> 16) & 0xFF) / 255f;
			float g = ((type.trailColor >> 8) & 0xFF) / 255f;
			float b = (type.trailColor & 0xFF) / 255f;
			float size = type.rocket ? 1.7f : 1.4f;
			getWorld().addParticle(new DustParticleEffect(new Vector3f(r, g, b), size),
					getX(), getY(), getZ(), 0, 0.05, 0);
			getWorld().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 0, 0.02, 0);
			if (type.rocket) {
				getWorld().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, -0.02, 0);
			}
			if (ageTicks % 2 == 0) {
				SmokeSystem.emit(getPos(), SmokeSystem.Source.BOMB_TRAIL, 0.6f, 0.45f, 50);
			}
			return;
		}

		AtmosphereBand band = AtmosphereBand.at(getY());
		Vec3d vel = getVelocity();

		if (type.rocket && !submunition) {
			// Powered rocket: thrust along nose (velocity bias + mild gravity).
			Vec3d nose = vel.lengthSquared() > 1e-4 ? vel.normalize() : new Vec3d(0, -1, 0);
			if (laserTarget != null) {
				Vec3d to = Vec3d.ofCenter(laserTarget).subtract(getPos());
				if (to.lengthSquared() > 1e-6) {
					nose = nose.add(to.normalize().multiply(0.45)).normalize();
				}
			}
			double thrust = 0.085 + (1.0 - band.airDrag) * 0.04;
			vel = vel.add(nose.multiply(thrust)).add(0, -0.018, 0);
			vel = vel.multiply(0.995);
		} else {
			double grav = 0.04 + (1.0 - band.airDrag) * 0.03;
			vel = vel.add(0, -grav, 0);
			vel = vel.multiply(0.99 + (1.0 - band.airDrag) * 0.008);
		}

		// Laser guidance — stronger for guided / rocket
		if ((type == OrdnanceType.LASER_GUIDED || type.rocket) && laserTarget != null) {
			Vec3d target = Vec3d.ofCenter(laserTarget);
			Vec3d to = target.subtract(getPos());
			if (to.lengthSquared() > 1e-6) {
				vel = vel.add(to.normalize().multiply(type.rocket ? 0.06 : 0.035));
			}
		}

		setVelocity(vel);
		Vec3d from = getPos();
		Vec3d next = from.add(vel);
		Vec3d lookAhead = vel.lengthSquared() > 1e-8
				? next.add(vel.normalize().multiply(0.5))
				: next.add(0, -0.5, 0);

		if (ageTicks % 2 == 0) {
			SmokeSystem.emit(from, SmokeSystem.Source.BOMB_TRAIL, 0.6f, 0.45f, 50);
		}

		// Cluster: open bay mid-air when ground is close — fragments get flight physics.
		if (type.cluster && !submunition && !dispensed && ageTicks > 14) {
			BlockHitResult groundProbe = getWorld().raycast(new net.minecraft.world.RaycastContext(
					from, from.add(0, -22, 0),
					net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
					net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
			if (groundProbe.getType() == HitResult.Type.BLOCK
					|| (vel.y < -0.8 && ageTicks > 35)) {
				dispenseCluster((ServerWorld) getWorld());
				return;
			}
		}

		BlockHitResult hit = getWorld().raycast(new net.minecraft.world.RaycastContext(
				from, lookAhead,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
		setPosition(next);

		boolean grounded = hit.getType() == HitResult.Type.BLOCK
				|| verticalCollision || horizontalCollision
				|| (!getWorld().getBlockState(getBlockPos().down()).isAir() && vel.y < 0 && (getY() - Math.floor(getY())) < 0.35);

		if (grounded || ageTicks > 20 * 90) {
			if (type.cluster && !submunition && !dispensed) {
				dispenseCluster((ServerWorld) getWorld());
			} else {
				detonate();
			}
		}
	}

	private void dispenseCluster(ServerWorld sw) {
		dispensed = true;
		Random rng = sw.getRandom();
		int count = type.heavyCluster ? 14 : 7;
		float outward = type.heavyCluster ? 1.25f : 0.62f;
		float fragBlast = type.heavyCluster ? 0.95f : 0.58f;
		Vec3d parentVel = getVelocity();
		Vec3d fallAxis = parentVel.lengthSquared() > 1e-4
				? parentVel.normalize()
				: new Vec3d(0, -1, 0);

		// Small opener charge so the canister does not vanish silently.
		float opener = AtmosphereRules.scaleBlast(getY(), 2.4f * type.blastPower);
		sw.createExplosion(this, getX(), getY(), getZ(), opener * 0.55f, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), opener);

		for (int i = 0; i < count; i++) {
			Vec3d dir;
			if (type.heavyCluster) {
				// Full sphere — fragments scream every which way.
				dir = randomUnit(rng);
			} else {
				// Cone ahead/under the canister (forward of fall + lateral).
				dir = fallAxis
						.add((rng.nextDouble() - 0.5) * 1.4,
								-0.35 - rng.nextDouble() * 0.55,
								(rng.nextDouble() - 0.5) * 1.4)
						.normalize();
			}
			double speed = outward * (0.75 + rng.nextDouble() * 0.55);
			Vec3d fragVel = parentVel.multiply(0.55).add(dir.multiply(speed));
			AerialBombEntity frag = ModEntities.AERIAL_BOMB.create(sw);
			if (frag == null) continue;
			frag.refreshPositionAndAngles(
					getX() + dir.x * 0.35,
					getY() + dir.y * 0.35,
					getZ() + dir.z * 0.35,
					(float) (rng.nextFloat() * 360f), 90);
			frag.configureSubmunition(OrdnanceType.TNT_BOMB, ownerId, fragBlast, fragVel);
			sw.spawnEntity(frag);
		}
		discard();
	}

	private static Vec3d randomUnit(Random rng) {
		double u = rng.nextDouble() * 2.0 - 1.0;
		double t = rng.nextDouble() * Math.PI * 2.0;
		double s = Math.sqrt(Math.max(0.0, 1.0 - u * u));
		return new Vec3d(s * Math.cos(t), u, s * Math.sin(t));
	}

	private void detonate() {
		if (!(getWorld() instanceof ServerWorld sw)) {
			discard();
			return;
		}
		float power = AtmosphereRules.scaleBlast(getY(), 5.2f * type.blastPower * blastMul);
		if (type.rocket) {
			power = AtmosphereRules.scaleBlast(getY(), 6.4f * type.blastPower * blastMul);
		}
		sw.createExplosion(this, getX(), getY(), getZ(), power, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), power);
		BlockPos at = getBlockPos();
		com.terminaldetector.drmd.world.mega.SkyUfoEntity.notifyBombDetonation(sw, at, power);

		if (type.incendiary || type == OrdnanceType.TNT_BOMB || type.rocket) {
			FireSystem.igniteBlast(sw, at,
					type.incendiary ? 20 : (type.rocket ? 12 : 8),
					type.incendiary ? 7 : (type.rocket ? 5 : 3));
		}
		// Deep pressure: extra tunnel shock puff
		if (AtmosphereBand.at(getY()).highPressure) {
			SmokeSystem.emit(getPos(), SmokeSystem.Source.TNT, 4f, 0.9f, 120);
		}
		discard();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		try {
			type = OrdnanceType.valueOf(nbt.getString("ordnance"));
		} catch (Exception e) {
			type = OrdnanceType.TNT_BOMB;
		}
		if (nbt.containsUuid("owner")) ownerId = nbt.getUuid("owner");
		if (nbt.contains("tx")) laserTarget = new BlockPos(nbt.getInt("tx"), nbt.getInt("ty"), nbt.getInt("tz"));
		submunition = nbt.getBoolean("sub");
		dispensed = nbt.getBoolean("dispensed");
		blastMul = nbt.contains("blastMul") ? nbt.getFloat("blastMul") : 1f;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("ordnance", type.name());
		if (ownerId != null) nbt.putUuid("owner", ownerId);
		if (laserTarget != null) {
			nbt.putInt("tx", laserTarget.getX());
			nbt.putInt("ty", laserTarget.getY());
			nbt.putInt("tz", laserTarget.getZ());
		}
		nbt.putBoolean("sub", submunition);
		nbt.putBoolean("dispensed", dispensed);
		nbt.putFloat("blastMul", blastMul);
	}
}
