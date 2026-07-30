package com.terminaldetector.drmd.world.bombardment;

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
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Falling aerial bomb — long smoke trail, accelerates in thin air, crater on impact.
 */
public class AerialBombEntity extends Entity {
	private OrdnanceType type = OrdnanceType.TNT_BOMB;
	private UUID ownerId;
	private BlockPos laserTarget;
	private int ageTicks;

	public AerialBombEntity(EntityType<? extends AerialBombEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	public void configure(OrdnanceType ordnance, LivingEntity owner, BlockPos laserTarget) {
		this.type = ordnance;
		if (owner != null) this.ownerId = owner.getUuid();
		this.laserTarget = laserTarget;
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
			getWorld().addParticle(new DustParticleEffect(new Vector3f(r, g, b), 1.4f),
					getX(), getY(), getZ(), 0, 0.05, 0);
			getWorld().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 0, 0.02, 0);
			if (ageTicks % 2 == 0) {
				SmokeSystem.emit(getPos(), SmokeSystem.Source.BOMB_TRAIL, 0.6f, 0.45f, 50);
			}
			return;
		}

		AtmosphereBand band = AtmosphereBand.at(getY());
		Vec3d vel = getVelocity();

		// Gravity + thin-air acceleration (almost no drag)
		double grav = 0.04 + (1.0 - band.airDrag) * 0.03;
		vel = vel.add(0, -grav, 0);
		vel = vel.multiply(0.99 + (1.0 - band.airDrag) * 0.008);

		// Laser guidance — gentle steer toward mark
		if (type == OrdnanceType.LASER_GUIDED && laserTarget != null) {
			Vec3d target = Vec3d.ofCenter(laserTarget);
			Vec3d to = target.subtract(getPos());
			if (to.lengthSquared() > 1e-6) {
				vel = vel.add(to.normalize().multiply(0.03));
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

		BlockHitResult hit = getWorld().raycast(new net.minecraft.world.RaycastContext(
				from, lookAhead,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
		setPosition(next);

		boolean grounded = hit.getType() == HitResult.Type.BLOCK
				|| verticalCollision || horizontalCollision
				|| (!getWorld().getBlockState(getBlockPos().down()).isAir() && vel.y < 0 && (getY() - Math.floor(getY())) < 0.35);

		if (grounded || ageTicks > 20 * 90) {
			detonate();
		}
	}

	private void detonate() {
		if (!(getWorld() instanceof ServerWorld sw)) {
			discard();
			return;
		}
		float power = AtmosphereRules.scaleBlast(getY(), 4.0f * type.blastPower);
		sw.createExplosion(this, getX(), getY(), getZ(), power, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), power);
		BlockPos at = getBlockPos();
		com.terminaldetector.drmd.world.mega.SkyUfoEntity.notifyBombDetonation(sw, at, power);

		if (type.incendiary || type == OrdnanceType.TNT_BOMB) {
			FireSystem.igniteBlast(sw, at, type.incendiary ? 18 : 6, type.incendiary ? 6 : 3);
		}
		if (type.cluster) {
			for (int i = 0; i < 5; i++) {
				double ox = (sw.getRandom().nextDouble() - 0.5) * 8;
				double oz = (sw.getRandom().nextDouble() - 0.5) * 8;
				sw.createExplosion(this, getX() + ox, getY(), getZ() + oz,
						AtmosphereRules.scaleBlast(getY(), 2.2f), true, World.ExplosionSourceType.TNT);
				SmokeSystem.emitExplosion(getPos().add(ox, 0, oz), 2.2f);
				FireSystem.igniteBlast(sw, at.add((int) ox, 0, (int) oz), 3, 2);
			}
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
	}
}
