package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.DroneAi;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Enemy drone — port of D6_BuildDrone / SpawnEnemyDrone with role loadouts.
 */
public class DroneEntity extends HostileEntity {
	private static final TrackedData<String> ROLE = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Float> SHIELD = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> ARMOR = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> FLIGHT_ROLL = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private AiRole role = AiRole.ASSAULT;
	private float shield;
	private float armor;
	private float modEnergy = 100f;
	private float attackCooldown;
	private DroneAi.Phase phase = DroneAi.Phase.ORBIT;
	private float phaseTimer;
	private float resistK, resistE, resistX;

	public DroneEntity(EntityType<? extends DroneEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		applyRole(AiRole.ASSAULT);
	}

	public static DefaultAttributeContainer.Builder createDroneAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 200)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.8);
	}

	public void applyRole(AiRole role) {
		this.role = role;
		this.shield = role.shield;
		this.armor = role.armor;
		this.resistK = role.resistKinetic;
		this.resistE = role.resistEnergy;
		this.resistX = role.resistExplosive;
		if (!getWorld().isClient) {
			setHealth(role.hullHp);
			getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(role.hullHp);
			dataTracker.set(ROLE, role.id);
			dataTracker.set(SHIELD, shield);
			dataTracker.set(ARMOR, armor);
		}
	}

	public AiRole getRole() { return role; }
	public float getDroneShield() { return shield; }
	public float getDroneArmor() { return armor; }
	public float getFlightRoll() { return dataTracker.get(FLIGHT_ROLL); }
	public void setFlightRoll(float roll) {
		if (!getWorld().isClient) dataTracker.set(FLIGHT_ROLL, roll);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, AiRole.ASSAULT.id);
		builder.add(SHIELD, 50f);
		builder.add(ARMOR, 0f);
		builder.add(FLIGHT_ROLL, 0f);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new DroneAi.CombatGoal(this));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16f));
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		this.setNoGravity(true);
		if (!getWorld().isClient) {
			modEnergy = Math.min(100f, modEnergy + 20f / 20f);
			if (attackCooldown > 0) attackCooldown -= 1f / 20f;
			dataTracker.set(SHIELD, shield);
			dataTracker.set(ARMOR, armor);
			// Passive shield regen for roles with ShieldGen (heavy_elite / support)
			if ((role == AiRole.HEAVY_ELITE || role == AiRole.SUPPORT) && age % 20 > 10) {
				shield = Math.min(role.shield, shield + 8f / 20f);
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		DamageClass cls = DamageClass.KINETIC;
		String name = source.getName();
		if (name.contains("magic") || name.contains("lightning")) cls = DamageClass.ENERGY;
		else if (name.contains("explosion")) cls = DamageClass.EXPLOSIVE;

		float resist = switch (cls) {
			case KINETIC -> resistK;
			case ENERGY, EXOTIC -> resistE;
			case EXPLOSIVE -> resistX;
		};
		resist = Math.min(0.9f, Math.max(0f, resist));
		amount *= (1f - resist);

		// Shield → Armor → Hull
		if (shield > 0) {
			float abs = Math.min(shield, amount);
			shield -= abs;
			amount -= abs;
		}
		if (amount > 0 && armor > 0) {
			float abs = Math.min(armor, amount);
			armor -= abs;
			amount -= abs;
		}
		if (amount <= 0) {
			this.scheduleVelocityUpdate();
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!getWorld().isClient && role == AiRole.HEAVY_ELITE && random.nextFloat() < 0.35f) {
			// Reactor death boom 35%: rad 340 → ~4.25 blocks, dmg 200
			if (getWorld() instanceof ServerWorld sw) {
				WeaponCore.splashDamage(this, sw, getPos(), 200f, (float) DescentMod.su(340), DamageClass.EXPLOSIVE);
				sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
			}
		}
	}

	public boolean tryFireAt(LivingEntity target) {
		if (attackCooldown > 0 || target == null) return false;
		if (modEnergy < role.weaponCost) return false;
		modEnergy -= role.weaponCost;
		attackCooldown = role.weaponCooldown;

		Vec3d from = getPos().add(0, getHeight() * 0.5, 0);
		Vec3d dir = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from).normalize();

		WeaponCore.FireConfig cfg = new WeaponCore.FireConfig();
		cfg.owner = this;
		cfg.pos = from;
		cfg.dir = dir;
		cfg.speed = role.weaponSpeed;
		cfg.directDamage = role.weaponDamage;
		cfg.splashDamage = role.weaponSplash;
		cfg.splashRadius = role.weaponSplashRadius;
		cfg.dmgClass = role.weaponClass;
		cfg.homing = role.weaponHoming;
		cfg.homeTarget = role.weaponHoming ? target : null;
		cfg.life = 4f;
		WeaponCore.fireProjectile(cfg);
		return true;
	}

	public DroneAi.Phase getPhase() { return phase; }
	public void setPhase(DroneAi.Phase p) { this.phase = p; this.phaseTimer = 0; }
	public float getPhaseTimer() { return phaseTimer; }
	public void addPhaseTimer(float dt) { phaseTimer += dt; }
	public float getModEnergy() { return modEnergy; }

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("role", role.id);
		nbt.putFloat("shield", shield);
		nbt.putFloat("armor", armor);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		applyRole(AiRole.fromId(nbt.getString("role")));
		shield = nbt.getFloat("shield");
		armor = nbt.getFloat("armor");
	}
}
