package com.terminaldetector.drmd.world.bombardment;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import com.terminaldetector.drmd.world.trap.LaserBeams;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Falling / powered aerial munition — smoke trail, thin-air accel, crater on impact.
 * Clusters burst mid-air into physically scattered submunitions.
 *
 * <p>Physics and tracers are attitude-safe for full 360° / 6DoF: gravity follows the
 * owner's local DOWN when known, fins stabilize along that axis (no Euler yaw/pitch),
 * and trails stream aft of velocity rather than world +Y.
 */
public class AerialBombEntity extends Entity {
	private static final TrackedData<Integer> TRAIL_COLOR =
			DataTracker.registerData(AerialBombEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> ROCKET_FX =
			DataTracker.registerData(AerialBombEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Cluster-laser hazard: sweep time after a laser bomblet lands, before its final pop. */
	private static final int LASER_HAZARD_TICKS = 100;
	private static final double LASER_SPIN_DEG_PER_TICK = 9.0;
	private static final int LASER_BEAM_LENGTH = 20;
	private static final float LASER_BEAM_DAMAGE = 3.5f;

	private OrdnanceType type = OrdnanceType.TNT_BOMB;
	private UUID ownerId;
	private BlockPos laserTarget;
	private int ageTicks;
	/** Child bomblet — will not re-cluster. */
	private boolean submunition;
	private boolean dispensed;
	/** Multiplier on detonation blast for fragment bomblets. */
	private float blastMul = 1f;
	/** Countdown while a landed laser bomblet is sweeping; 0 = no hazard active. */
	private int laserHazardTicks;

	public AerialBombEntity(EntityType<? extends AerialBombEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	public void configure(OrdnanceType ordnance, LivingEntity owner, BlockPos laserTarget) {
		this.type = ordnance;
		if (owner != null) this.ownerId = owner.getUuid();
		this.laserTarget = laserTarget;
		syncVisual();
	}

	public void configureSubmunition(OrdnanceType ordnance, UUID owner, float blastMul, Vec3d velocity) {
		this.type = ordnance;
		this.ownerId = owner;
		this.submunition = true;
		this.blastMul = blastMul;
		this.laserTarget = null;
		setVelocity(velocity);
		syncVisual();
	}

	public OrdnanceType getOrdnance() {
		return type;
	}

	/** Pushes the fields the client actually needs — {@code type} itself is never synced. */
	private void syncVisual() {
		dataTracker.set(TRAIL_COLOR, type.trailColor);
		dataTracker.set(ROCKET_FX, type.rocket);
	}

	/** Local DOWN for this round — owner's foot gravity when available, else world −Y. */
	private Vec3d gravityDir() {
		if (ownerId != null) {
			Vec3d g = LocalOrientation.gravityDir(ownerId);
			if (g.lengthSquared() > 1e-8) return g.normalize();
		}
		return new Vec3d(0, -1, 0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TRAIL_COLOR, OrdnanceType.TNT_BOMB.trailColor);
		builder.add(ROCKET_FX, false);
	}

	@Override
	public void tick() {
		super.tick();
		ageTicks++;
		Vec3d vel = getVelocity();
		Vec3d aft = vel.lengthSquared() > 1e-6 ? vel.normalize().multiply(-1) : gravityDir();

		if (getWorld().isClient) {
			int trailColor = dataTracker.get(TRAIL_COLOR);
			boolean rocketFx = dataTracker.get(ROCKET_FX);
			float r = ((trailColor >> 16) & 0xFF) / 255f;
			float g = ((trailColor >> 8) & 0xFF) / 255f;
			float b = (trailColor & 0xFF) / 255f;
			float size = rocketFx ? 1.7f : 1.4f;
			// Tracer bead at the round + dust streaming aft (works inverted / banked).
			getWorld().addParticle(new DustParticleEffect(new Vector3f(r, g, b), size),
					getX(), getY(), getZ(), 0, 0, 0);
			Vec3d trail = aft.multiply(0.12);
			getWorld().addParticle(new DustParticleEffect(new Vector3f(r * 0.7f, g * 0.7f, b * 0.7f), size * 0.75f),
					getX() + trail.x, getY() + trail.y, getZ() + trail.z, 0, 0, 0);
			getWorld().addParticle(ParticleTypes.LARGE_SMOKE,
					getX() + aft.x * 0.25, getY() + aft.y * 0.25, getZ() + aft.z * 0.25,
					aft.x * 0.02, aft.y * 0.02, aft.z * 0.02);
			if (rocketFx) {
				getWorld().addParticle(ParticleTypes.FLAME,
						getX() + aft.x * 0.15, getY() + aft.y * 0.15, getZ() + aft.z * 0.15,
						aft.x * 0.04, aft.y * 0.04, aft.z * 0.04);
			}
			if (ageTicks % 2 == 0) {
				SmokeSystem.emit(getPos(), SmokeSystem.Source.BOMB_TRAIL, 0.6f, 0.45f, 50,
						aft.multiply(0.03));
			}
			return;
		}

		if (laserHazardTicks > 0) {
			tickLaserHazard((ServerWorld) getWorld());
			return;
		}

		AtmosphereBand band = AtmosphereBand.at(getY());
		Vec3d gDir = gravityDir();

		if (type.rocket && !submunition) {
			// Powered rocket: thrust along nose (velocity), laser bias, mild local-g.
			Vec3d nose = vel.lengthSquared() > 1e-4 ? vel.normalize() : gDir;
			if (laserTarget != null) {
				Vec3d to = Vec3d.ofCenter(laserTarget).subtract(getPos());
				if (to.lengthSquared() > 1e-6) {
					nose = nose.add(to.normalize().multiply(0.45)).normalize();
				}
			}
			double thrust = 0.085 + (1.0 - band.airDrag) * 0.04;
			vel = vel.add(nose.multiply(thrust)).add(gDir.multiply(0.018));
			vel = vel.multiply(0.995);
		} else {
			double grav = 0.04 + (1.0 - band.airDrag) * 0.03;
			vel = vel.add(gDir.multiply(grav));
			vel = vel.multiply(0.99 + (1.0 - band.airDrag) * 0.008);
			// Fin stabilization: slerp toward local DOWN so free-fall reads in any attitude.
			if (vel.lengthSquared() > 1e-6) {
				Vec3d cur = vel.normalize();
				Vec3d desired = gDir;
				if (laserTarget != null && (type == OrdnanceType.LASER_GUIDED)) {
					Vec3d to = Vec3d.ofCenter(laserTarget).subtract(getPos());
					if (to.lengthSquared() > 1e-6) desired = to.normalize();
				}
				Vec3d blended = cur.add(desired.subtract(cur).multiply(0.08)).normalize();
				vel = blended.multiply(vel.length());
			}
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
				: next.add(gDir.multiply(0.5));

		if (ageTicks % 2 == 0) {
			Vec3d trailVel = vel.lengthSquared() > 1e-6
					? vel.normalize().multiply(-0.03)
					: gDir.multiply(-0.02);
			SmokeSystem.emit(from, SmokeSystem.Source.BOMB_TRAIL, 0.6f, 0.45f, 50, trailVel);
		}

		// Cluster: open bay when ground is close along fall / gravity axis (not world −Y).
		if (type.cluster && !submunition && !dispensed && ageTicks > 14) {
			Vec3d probeAxis = vel.lengthSquared() > 1e-4 ? vel.normalize() : gDir;
			BlockHitResult groundProbe = getWorld().raycast(new net.minecraft.world.RaycastContext(
					from, from.add(probeAxis.multiply(22)),
					net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
					net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
			double fallSpeed = vel.dotProduct(gDir);
			if (groundProbe.getType() == HitResult.Type.BLOCK
					|| (fallSpeed > 0.8 && ageTicks > 35)) {
				dispenseCluster((ServerWorld) getWorld());
				return;
			}
		}

		BlockHitResult hit = getWorld().raycast(new net.minecraft.world.RaycastContext(
				from, lookAhead,
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
		setPosition(next);

		// Ground contact: prefer hit along velocity; block under feet uses local DOWN, not world.
		BlockPos downProbe = BlockPos.ofFloored(getPos().add(gDir.multiply(0.35)));
		boolean grounded = hit.getType() == HitResult.Type.BLOCK
				|| verticalCollision || horizontalCollision
				|| (!getWorld().getBlockState(downProbe).isAir()
				&& vel.dotProduct(gDir) > 0.05);

		if (grounded || ageTicks > 20 * 90) {
			if (type.cluster && !submunition && !dispensed) {
				dispenseCluster((ServerWorld) getWorld());
			} else if (submunition && type.payload == OrdnanceType.SubmunitionPayload.LASER) {
				beginLaserHazard((ServerWorld) getWorld());
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
				: gravityDir();
		// Pole-safe lateral basis for the cone (no world-up cross).
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(fallAxis);
		Vec3d up = right.crossProduct(fallAxis).normalize();

		float opener = AtmosphereRules.scaleBlast(getY(), 2.4f * type.blastPower);
		sw.createExplosion(this, getX(), getY(), getZ(), opener * 0.55f, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), opener);

		for (int i = 0; i < count; i++) {
			Vec3d dir;
			if (type.heavyCluster) {
				dir = randomUnit(rng);
			} else {
				// Cone along fall axis with lateral scatter in ship-safe basis.
				dir = fallAxis
						.add(right.multiply((rng.nextDouble() - 0.5) * 1.4))
						.add(up.multiply((rng.nextDouble() - 0.5) * 1.4))
						.add(fallAxis.multiply(0.35 + rng.nextDouble() * 0.55))
						.normalize();
			}
			double speed = outward * (0.75 + rng.nextDouble() * 0.55);
			Vec3d fragVel = parentVel.multiply(0.55).add(dir.multiply(speed));
			AerialBombEntity frag = ModEntities.AERIAL_BOMB.create(sw);
			if (frag == null) continue;
			// No Euler pitch=90 — orientation is implied by velocity for tracers/mesh.
			frag.refreshPositionAndAngles(
					getX() + dir.x * 0.35,
					getY() + dir.y * 0.35,
					getZ() + dir.z * 0.35,
					0f, 0f);
			frag.setVelocity(fragVel);
			frag.configureSubmunition(this.type, ownerId, fragBlast, fragVel);
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
		switch (type.payload) {
			case VIRUS_NETHER -> detonateVirusNether(sw);
			case VIRUS_SCULK -> detonateVirusSculk(sw);
			case NONE, LASER -> detonateStandard(sw);
		}
		discard();
	}

	private void detonateStandard(ServerWorld sw) {
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
		if (AtmosphereBand.at(getY()).highPressure) {
			SmokeSystem.emit(getPos(), SmokeSystem.Source.TNT, 4f, 0.9f, 120);
		}
	}

	/** Smaller pop than a straight HE bomblet — the corruption spreading afterward is the payload. */
	private void detonateVirusNether(ServerWorld sw) {
		float power = AtmosphereRules.scaleBlast(getY(), 2.6f * type.blastPower * blastMul);
		sw.createExplosion(this, getX(), getY(), getZ(), power, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), power);
		BlockPos ground = groundBelow(sw, getBlockPos());
		corruptNether(sw, ground, 3 + (int) (power * 0.6f), sw.getRandom());
		FireSystem.igniteBlast(sw, ground, 10, 5);
		com.terminaldetector.drmd.world.mega.SkyUfoEntity.notifyBombDetonation(sw, ground, power);
	}

	private void detonateVirusSculk(ServerWorld sw) {
		float power = AtmosphereRules.scaleBlast(getY(), 2.2f * type.blastPower * blastMul);
		sw.createExplosion(this, getX(), getY(), getZ(), power, true, World.ExplosionSourceType.TNT);
		SmokeSystem.emitExplosion(getPos(), power);
		BlockPos ground = groundBelow(sw, getBlockPos());
		corruptSculk(sw, ground, 3 + (int) (power * 0.6f), sw.getRandom());
		darkenNearby(sw, ground, 5 + power);
		com.terminaldetector.drmd.world.mega.SkyUfoEntity.notifyBombDetonation(sw, ground, power);
	}

	/**
	 * First solid block at or below {@code at} — corruption and the Sculk catalyst want real ground,
	 * not whatever air pocket the round happened to burst in.
	 */
	private static BlockPos groundBelow(ServerWorld sw, BlockPos at) {
		BlockPos p = at;
		for (int i = 0; i < 4 && sw.getBlockState(p).isAir(); i++) {
			p = p.down();
		}
		return p;
	}

	/**
	 * Ragged, overlapping blobs rather than one filled sphere — a handful of small spheres with a
	 * porous edge (30% of candidate cells skipped) reads as organic corruption; one clean sphere reads
	 * as a crater with a different texture.
	 */
	private static void corruptNether(ServerWorld sw, BlockPos origin, int radius, Random rng) {
		int r = Math.max(1, radius);
		int blobs = 3 + rng.nextInt(3);
		for (int b = 0; b < blobs; b++) {
			BlockPos blobCenter = origin.add(
					rng.nextInt(r * 2 + 1) - r,
					rng.nextInt(3) - 1,
					rng.nextInt(r * 2 + 1) - r);
			int blobR = 2 + rng.nextInt(r);
			for (BlockPos p : BlockPos.iterate(blobCenter.add(-blobR, -blobR, -blobR), blobCenter.add(blobR, blobR, blobR))) {
				if (p.getSquaredDistance(blobCenter) > blobR * blobR) continue;
				if (rng.nextFloat() > 0.7f) continue;
				BlockState st = sw.getBlockState(p);
				if (st.isAir() || st.isOf(Blocks.BEDROCK) || !st.isSolidBlock(sw, p)) continue;
				float hardness = st.getHardness(sw, p);
				if (hardness < 0 || hardness > 12) continue;
				boolean exposedTop = sw.getBlockState(p.up()).isAir();
				BlockState stain = pickNetherStain(rng, exposedTop);
				sw.setBlockState(p, stain, Block.NOTIFY_ALL);
				// Fungus only where the stain is nylium — a known-good growth surface either way,
				// rather than trusting a placement-validity call we cannot compile-check here.
				boolean nylium = stain.isOf(Blocks.CRIMSON_NYLIUM) || stain.isOf(Blocks.WARPED_NYLIUM);
				if (exposedTop && nylium && rng.nextFloat() < 0.15f) {
					sw.setBlockState(p.up(), rng.nextBoolean()
							? Blocks.WARPED_FUNGUS.getDefaultState()
							: Blocks.CRIMSON_FUNGUS.getDefaultState(), Block.NOTIFY_ALL);
				}
			}
		}
	}

	private static BlockState pickNetherStain(Random rng, boolean exposedTop) {
		int roll = rng.nextInt(10);
		if (exposedTop && roll < 4) return Blocks.NETHER_WART_BLOCK.getDefaultState();
		if (roll < 5) return Blocks.CRIMSON_NYLIUM.getDefaultState();
		if (roll < 8) return Blocks.WARPED_NYLIUM.getDefaultState();
		return Blocks.NETHERRACK.getDefaultState();
	}

	/** Same ragged-blob shape as the Nether stain, plus one catalyst to seed real, ongoing vanilla spread. */
	private static void corruptSculk(ServerWorld sw, BlockPos origin, int radius, Random rng) {
		int r = Math.max(1, radius);
		int blobs = 3 + rng.nextInt(3);
		for (int b = 0; b < blobs; b++) {
			BlockPos blobCenter = origin.add(
					rng.nextInt(r * 2 + 1) - r,
					rng.nextInt(3) - 1,
					rng.nextInt(r * 2 + 1) - r);
			int blobR = 2 + rng.nextInt(r);
			for (BlockPos p : BlockPos.iterate(blobCenter.add(-blobR, -blobR, -blobR), blobCenter.add(blobR, blobR, blobR))) {
				if (p.getSquaredDistance(blobCenter) > blobR * blobR) continue;
				if (rng.nextFloat() > 0.7f) continue;
				BlockState st = sw.getBlockState(p);
				if (st.isAir() || st.isOf(Blocks.BEDROCK) || st.isOf(Blocks.SCULK_CATALYST) || !st.isSolidBlock(sw, p)) continue;
				float hardness = st.getHardness(sw, p);
				if (hardness < 0 || hardness > 12) continue;
				sw.setBlockState(p, Blocks.SCULK.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
		// Reuses vanilla's own catalyst so nearby mob deaths keep growing real Sculk long after this,
		// instead of a hand-rolled spread simulation duplicating what the game already does.
		BlockPos catalystAt = origin.up();
		if (sw.getBlockState(origin).isSolidBlock(sw, origin) && sw.getBlockState(catalystAt).isAir()) {
			sw.setBlockState(catalystAt, Blocks.SCULK_CATALYST.getDefaultState(), Block.NOTIFY_ALL);
		} else {
			sw.setBlockState(origin, Blocks.SCULK_CATALYST.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	/** Thematic nod to Sculk Shrieker / Warden rather than plain HE — the payload disorients, not just hurts. */
	private static void darkenNearby(ServerWorld sw, BlockPos center, double radius) {
		Box box = new Box(center).expand(radius);
		for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, true, true, true));
		}
	}

	/** Lands and arms instead of detonating — the Cyberpunk laser-grenade beat: stick, spin, then pop. */
	private void beginLaserHazard(ServerWorld sw) {
		laserHazardTicks = LASER_HAZARD_TICKS;
		setVelocity(Vec3d.ZERO);
		sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1f, 1.6f);
	}

	/**
	 * Two opposite beams rotating together in the plane perpendicular to local gravity — "horizontal"
	 * relative to whatever surface the bomblet is resting on, not to world Y, matching the rest of this
	 * class's attitude-safe physics. Reuses {@link LaserBeams#cast}, the same barrier-laser sweep the
	 * trap blocks use, for both the damage tick and the beam particles — one proven implementation
	 * instead of a second one duplicating it.
	 */
	private void tickLaserHazard(ServerWorld sw) {
		laserHazardTicks--;
		Vec3d spinAxis = gravityDir().multiply(-1);
		Vec3d origin = getPos().add(spinAxis.multiply(0.25));
		Vec3d ref = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(spinAxis);
		Vec3d ortho = ref.crossProduct(spinAxis).normalize();
		double angle = Math.toRadians((LASER_HAZARD_TICKS - laserHazardTicks) * LASER_SPIN_DEG_PER_TICK);
		Vec3d beamDir = ref.multiply(Math.cos(angle)).add(ortho.multiply(Math.sin(angle)));
		LaserBeams.cast(sw, origin, beamDir, LASER_BEAM_LENGTH, LASER_BEAM_DAMAGE);
		LaserBeams.cast(sw, origin, beamDir.multiply(-1), LASER_BEAM_LENGTH, LASER_BEAM_DAMAGE);
		if (laserHazardTicks <= 0) {
			detonate();
		}
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
		laserHazardTicks = nbt.getInt("laserHazard");
		syncVisual();
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
		nbt.putInt("laserHazard", laserHazardTicks);
	}
}
