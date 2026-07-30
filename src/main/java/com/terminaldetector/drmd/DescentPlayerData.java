package com.terminaldetector.drmd;

import com.terminaldetector.drmd.energy.EnergyPreset;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player DRMD state — mirrors GMod NWVars / ply fields from d6_core, energy, shield.
 */
public class DescentPlayerData {
	private static final Map<UUID, DescentPlayerData> STORE = new ConcurrentHashMap<>();

	public static DescentPlayerData get(PlayerEntity player) {
		return STORE.computeIfAbsent(player.getUuid(), id -> new DescentPlayerData());
	}

	public static void remove(UUID id) {
		STORE.remove(id);
	}

	// --- Flight (d6_core.lua CFG) ---
	private boolean enabled;
	private boolean flightAssist = true;
	private boolean alwaysRun;
	private boolean thirdPerson;
	private float roll;
	private float rollVel;
	private Vec3d velocity = Vec3d.ZERO;
	private float thrustSpool;
	private float idleTimer;
	private float gravityFactor;
	private float dashCooldown;
	private float dashTimer;
	private boolean hookActive;
	private Vec3d hookPos = Vec3d.ZERO;
	private boolean radarEnabled = true;

	// Tunable physics (admin d6_set) — Source units, scaled at runtime
	private float gravity = 200f;
	private float accel = 4200f;
	private float drag = 2.1f;
	private float maxSpeed = 2200f;

	// --- Energy (d6_energy.lua) ---
	private float energy = 100f;
	private float energyMax = 100f;
	private EnergyPreset preset = EnergyPreset.BALANCED;
	private float allocWeapons = 0.34f;
	private float allocShields = 0.33f;
	private float allocEngines = 0.33f;

	// --- Shields (d6_shield.lua) ---
	private float shield = 100f;
	private float shieldMax = 100f;
	private float shieldRegenDelay;

	// --- Combat ---
	private int rocketSubmode; // 0..3
	private int activeWeaponSlot;
	private float gravyEnergy = 100f;
	private boolean gravyGrabbing;

	// Workshop live-tuning overrides
	private float wepInherit = 1f;
	private float wepRecoil = 1f;

	/** First-join tip already shown. */
	private boolean sessionWelcomed;

	public void ensureInit() {
		if (energyMax <= 0) energyMax = 100f;
		if (shieldMax <= 0) shieldMax = 100f;
		if (energy <= 0 && !enabled) energy = energyMax;
		if (shield <= 0 && !enabled) shield = shieldMax;
	}

	public void writeNbt(NbtCompound nbt) {
		NbtCompound d = new NbtCompound();
		d.putBoolean("enabled", enabled);
		d.putBoolean("flightAssist", flightAssist);
		d.putBoolean("alwaysRun", alwaysRun);
		d.putFloat("roll", roll);
		d.putFloat("energy", energy);
		d.putFloat("energyMax", energyMax);
		d.putString("preset", preset.id);
		d.putFloat("allocW", allocWeapons);
		d.putFloat("allocS", allocShields);
		d.putFloat("allocE", allocEngines);
		d.putFloat("shield", shield);
		d.putFloat("shieldMax", shieldMax);
		d.putInt("rocketSub", rocketSubmode);
		d.putFloat("gravity", gravity);
		d.putFloat("accel", accel);
		d.putFloat("drag", drag);
		d.putFloat("maxSpeed", maxSpeed);
		d.putFloat("wepInherit", wepInherit);
		d.putFloat("wepRecoil", wepRecoil);
		d.putBoolean("radar", radarEnabled);
		d.putBoolean("sessionWelcomed", sessionWelcomed);
		nbt.put("DrmdData", d);
	}

	public void readNbt(NbtCompound nbt) {
		if (!nbt.contains("DrmdData")) return;
		NbtCompound d = nbt.getCompound("DrmdData");
		enabled = d.getBoolean("enabled");
		flightAssist = d.contains("flightAssist") ? d.getBoolean("flightAssist") : true;
		alwaysRun = d.getBoolean("alwaysRun");
		roll = d.getFloat("roll");
		energy = d.getFloat("energy");
		energyMax = d.contains("energyMax") ? d.getFloat("energyMax") : 100f;
		preset = EnergyPreset.fromId(d.getString("preset"));
		allocWeapons = d.contains("allocW") ? d.getFloat("allocW") : 0.34f;
		allocShields = d.contains("allocS") ? d.getFloat("allocS") : 0.33f;
		allocEngines = d.contains("allocE") ? d.getFloat("allocE") : 0.33f;
		shield = d.getFloat("shield");
		shieldMax = d.contains("shieldMax") ? d.getFloat("shieldMax") : 100f;
		rocketSubmode = d.getInt("rocketSub");
		if (d.contains("gravity")) gravity = d.getFloat("gravity");
		if (d.contains("accel")) accel = d.getFloat("accel");
		if (d.contains("drag")) drag = d.getFloat("drag");
		if (d.contains("maxSpeed")) maxSpeed = d.getFloat("maxSpeed");
		if (d.contains("wepInherit")) wepInherit = d.getFloat("wepInherit");
		if (d.contains("wepRecoil")) wepRecoil = d.getFloat("wepRecoil");
		radarEnabled = !d.contains("radar") || d.getBoolean("radar");
		sessionWelcomed = d.getBoolean("sessionWelcomed");
	}

	// Getters / setters
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean v) { this.enabled = v; }
	public boolean isFlightAssist() { return flightAssist; }
	public void setFlightAssist(boolean v) { this.flightAssist = v; }
	public boolean isAlwaysRun() { return alwaysRun; }
	public void setAlwaysRun(boolean v) { this.alwaysRun = v; }
	public boolean isThirdPerson() { return thirdPerson; }
	public void setThirdPerson(boolean v) { this.thirdPerson = v; }
	public float getRoll() { return roll; }
	public void setRoll(float roll) { this.roll = roll; }
	public float getRollVel() { return rollVel; }
	public void setRollVel(float rollVel) { this.rollVel = rollVel; }
	public Vec3d getFlightVelocity() { return velocity; }
	public void setFlightVelocity(Vec3d velocity) { this.velocity = velocity; }
	public float getThrustSpool() { return thrustSpool; }
	public void setThrustSpool(float thrustSpool) { this.thrustSpool = thrustSpool; }
	public float getIdleTimer() { return idleTimer; }
	public void setIdleTimer(float idleTimer) { this.idleTimer = idleTimer; }
	public float getGravityFactor() { return gravityFactor; }
	public void setGravityFactor(float gravityFactor) { this.gravityFactor = gravityFactor; }
	public float getDashCooldown() { return dashCooldown; }
	public void setDashCooldown(float dashCooldown) { this.dashCooldown = dashCooldown; }
	public float getDashTimer() { return dashTimer; }
	public void setDashTimer(float dashTimer) { this.dashTimer = dashTimer; }
	public boolean isHookActive() { return hookActive; }
	public void setHookActive(boolean hookActive) { this.hookActive = hookActive; }
	public Vec3d getHookPos() { return hookPos; }
	public void setHookPos(Vec3d hookPos) { this.hookPos = hookPos; }
	public boolean isRadarEnabled() { return radarEnabled; }
	public void setRadarEnabled(boolean radarEnabled) { this.radarEnabled = radarEnabled; }

	public float getGravity() { return gravity; }
	public void setGravity(float gravity) { this.gravity = gravity; }
	public float getAccel() { return accel; }
	public void setAccel(float accel) { this.accel = accel; }
	public float getDrag() { return drag; }
	public void setDrag(float drag) { this.drag = drag; }
	public float getMaxSpeed() { return maxSpeed; }
	public void setMaxSpeed(float maxSpeed) { this.maxSpeed = maxSpeed; }

	public float getEnergy() { return energy; }
	public void setEnergy(float energy) { this.energy = energy; }
	public float getEnergyMax() { return energyMax; }
	public void setEnergyMax(float energyMax) { this.energyMax = energyMax; }
	public EnergyPreset getPreset() { return preset; }
	public void setPreset(EnergyPreset preset) { this.preset = preset; }
	public float getAllocWeapons() { return allocWeapons; }
	public float getAllocShields() { return allocShields; }
	public float getAllocEngines() { return allocEngines; }
	public void setAlloc(float w, float s, float e) {
		float sum = w + s + e;
		if (sum <= 0) return;
		this.allocWeapons = w / sum;
		this.allocShields = s / sum;
		this.allocEngines = e / sum;
	}

	public float getShield() { return shield; }
	public void setShield(float shield) { this.shield = shield; }
	public float getShieldMax() { return shieldMax; }
	public void setShieldMax(float shieldMax) { this.shieldMax = shieldMax; }
	public float getShieldRegenDelay() { return shieldRegenDelay; }
	public void setShieldRegenDelay(float shieldRegenDelay) { this.shieldRegenDelay = shieldRegenDelay; }

	public int getRocketSubmode() { return rocketSubmode; }
	public void setRocketSubmode(int rocketSubmode) { this.rocketSubmode = rocketSubmode & 3; }
	public int getActiveWeaponSlot() { return activeWeaponSlot; }
	public void setActiveWeaponSlot(int activeWeaponSlot) { this.activeWeaponSlot = activeWeaponSlot; }
	public float getGravyEnergy() { return gravyEnergy; }
	public void setGravyEnergy(float gravyEnergy) { this.gravyEnergy = gravyEnergy; }
	public boolean isGravyGrabbing() { return gravyGrabbing; }
	public void setGravyGrabbing(boolean gravyGrabbing) { this.gravyGrabbing = gravyGrabbing; }
	public float getWepInherit() { return wepInherit; }
	public void setWepInherit(float wepInherit) { this.wepInherit = wepInherit; }
	public float getWepRecoil() { return wepRecoil; }
	public void setWepRecoil(float wepRecoil) { this.wepRecoil = wepRecoil; }
	public boolean isSessionWelcomed() { return sessionWelcomed; }
	public void setSessionWelcomed(boolean sessionWelcomed) { this.sessionWelcomed = sessionWelcomed; }
}
