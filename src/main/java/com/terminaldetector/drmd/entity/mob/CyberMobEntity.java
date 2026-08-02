package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Shared chassis for the hybrid-cyberpunk enemy roster.
 *
 * <p>Gives every machine the DRMD defence stack — shield soaks first, then armour, then hull, each
 * scaled by a per-class resistance to the incoming damage class — plus a metered energy pool that
 * gates weapon fire, and a synced roll used by renderers.
 *
 * <p>Subclasses supply their loadout in the constructor via {@link #configure} and drive their own
 * goals; everything below the trigger is common.
 */
public abstract class CyberMobEntity extends HostileEntity {
	private static final TrackedData<Float> SHIELD =
			DataTracker.registerData(CyberMobEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> ARMOR =
			DataTracker.registerData(CyberMobEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> ROLL =
			DataTracker.registerData(CyberMobEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> ALERT =
			DataTracker.registerData(CyberMobEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	protected float shield;
	protected float shieldMax;
	protected float armor;
	protected float armorMax;
	protected float shieldRegenPerSec;
	protected float resistKinetic;
	protected float resistEnergy;
	protected float resistExplosive;
	protected float energy = 100f;
	protected float energyMax = 100f;
	protected float energyRegenPerSec = 18f;

	protected CyberMobEntity(EntityType<? extends CyberMobEntity> type, World world) {
		super(type, world);
	}

	/** Loadout hook — call from the subclass constructor and from NBT reload. */
	protected void configure(float shieldMax, float armorMax, float shieldRegenPerSec,
							 float resistKinetic, float resistEnergy, float resistExplosive,
							 float energyMax, float energyRegenPerSec) {
		this.shieldMax = shieldMax;
		this.armorMax = armorMax;
		this.shield = shieldMax;
		this.armor = armorMax;
		this.shieldRegenPerSec = shieldRegenPerSec;
		this.resistKinetic = resistKinetic;
		this.resistEnergy = resistEnergy;
		this.resistExplosive = resistExplosive;
		this.energyMax = energyMax;
		this.energy = energyMax;
		this.energyRegenPerSec = energyRegenPerSec;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SHIELD, 0f);
		builder.add(ARMOR, 0f);
		builder.add(ROLL, 0f);
		builder.add(ALERT, false);
	}

	// Named away from LivingEntity#getShield / #getArmor: the vanilla armour accessor is an int
	// attribute value, and DRMD plating is a separate float pool in front of the hull.
	public float getShieldCharge() { return dataTracker.get(SHIELD); }
	public float getArmorPlating() { return dataTracker.get(ARMOR); }
	public float getShieldChargeMax() { return shieldMax; }
	public float getArmorPlatingMax() { return armorMax; }
	public float getFlightRoll() { return dataTracker.get(ROLL); }
	public boolean isAlert() { return dataTracker.get(ALERT); }

	public void setFlightRoll(float roll) {
		if (!getWorld().isClient) dataTracker.set(ROLL, roll);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		float dt = 1f / 20f;
		if (energy < energyMax) energy = Math.min(energyMax, energy + energyRegenPerSec * dt);
		if (shieldRegenPerSec > 0 && shield < shieldMax) {
			shield = Math.min(shieldMax, shield + shieldRegenPerSec * dt);
		}
		dataTracker.set(SHIELD, shield);
		dataTracker.set(ARMOR, armor);
		dataTracker.set(ALERT, getTarget() != null && getTarget().isAlive());
	}

	/** Shield → armour → hull, after the class resistance. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		String name = source.getName();
		float resist;
		if (name.contains("magic") || name.contains("lightning")) resist = resistEnergy;
		else if (name.contains("explosion") || name.contains("fireworks")) resist = resistExplosive;
		else resist = resistKinetic;
		amount *= 1f - Math.min(0.9f, Math.max(0f, resist));

		if (shield > 0) {
			float absorbed = Math.min(shield, amount);
			shield -= absorbed;
			amount -= absorbed;
		}
		if (amount > 0 && armor > 0) {
			float absorbed = Math.min(armor, amount);
			armor -= absorbed;
			amount -= absorbed;
		}
		if (amount <= 0) {
			scheduleVelocityUpdate();
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!getWorld().isClient) {
			com.terminaldetector.drmd.pickup.EnemyDrops.onDroneDeath(this, damageSource, 0.45f, 0.30f);
		}
	}

	/** Spend from the pool; false when the capacitor has not recharged yet. */
	protected boolean drawEnergy(float cost) {
		if (energy < cost) return false;
		energy -= cost;
		return true;
	}

	protected Vec3d muzzle(double forwardOffset, double upOffset) {
		Vec3d look = getRotationVec(1f);
		return getPos().add(0, getHeight() * upOffset, 0).add(look.multiply(forwardOffset));
	}

	protected static Vec3d aimAt(Vec3d from, LivingEntity target) {
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		Vec3d dir = to.subtract(from);
		return dir.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : dir.normalize();
	}

	protected void fire(WeaponCore.FireConfig cfg) {
		WeaponCore.fireProjectile(cfg);
	}

	protected WeaponCore.FireConfig shot(Vec3d from, Vec3d dir, float speed, float damage,
										 DamageClass cls, float life) {
		WeaponCore.FireConfig cfg = new WeaponCore.FireConfig();
		cfg.owner = this;
		cfg.pos = from;
		cfg.dir = dir;
		cfg.speed = speed;
		cfg.directDamage = damage;
		cfg.dmgClass = cls;
		cfg.life = life;
		return cfg;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("drmdShield", shield);
		nbt.putFloat("drmdArmor", armor);
		nbt.putFloat("drmdEnergy", energy);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("drmdShield")) shield = nbt.getFloat("drmdShield");
		if (nbt.contains("drmdArmor")) armor = nbt.getFloat("drmdArmor");
		if (nbt.contains("drmdEnergy")) energy = nbt.getFloat("drmdEnergy");
	}
}
