package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.energy.EnergyPreset;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player DRMD state — mirrors GMod NWVars / ply fields from d6_core, energy, shield.
 */
public class DescentPlayerData {
	private net.minecraft.inventory.SimpleInventory droneLoadout;
    public net.minecraft.inventory.SimpleInventory droneLoadout(){if(droneLoadout==null)droneLoadout=new net.minecraft.inventory.SimpleInventory(5);return droneLoadout;}
	private static final Map<UUID, DescentPlayerData> STORE = new ConcurrentHashMap<>();
    private static final Map<UUID, DescentPlayerData> CLIENT_STORE = new ConcurrentHashMap<>();

	public static DescentPlayerData get(PlayerEntity player) {
		return player.getWorld().isClient?client(player.getUuid()):
                STORE.computeIfAbsent(player.getUuid(), id -> new DescentPlayerData());
	}

    /** Packet mirror, never the authoritative server object even in an integrated game. */
    public static DescentPlayerData client(UUID id) { return CLIENT_STORE.computeIfAbsent(id,key->new DescentPlayerData()); }
    public static void clearClient() { CLIENT_STORE.clear(); }

    public static void remove(UUID id) {
		STORE.remove(id);
        CLIENT_STORE.remove(id);
	}

	/**
	 * Drop every player's state. Called when a server stops.
	 *
	 * <p>This map is keyed by UUID and lives as long as the game process, not as long as the world.
	 * Nothing used to clear it, so quitting to the title screen and creating a new world left the
	 * previous world's state in place — and because a brand-new world has no player file to read
	 * over it, the stale entry simply won. The visible symptom was 6DoF being off in every world
	 * after the first: {@code sessionWelcomed} was still set, so the first-join branch that switches
	 * it on never ran again. Energy, shields and the ship's attitude leaked the same way.
	 */
	public static void clear() {
		STORE.clear();
        CLIENT_STORE.clear();
	}

	// Session-only generation of the portal frame; old in-flight input cannot undo teleport rotation.
	private int portalEpoch;
	public int portalEpoch() { return portalEpoch; }
	public int nextPortalEpoch() { return ++portalEpoch; }
	public void resetPortalEpoch() { portalEpoch = 0; }

	// --- Flight (d6_core.lua CFG) ---
	private boolean enabled;
    /** Presence of a persisted mode distinguishes a returning walker from a new player. */
    private boolean savedFlightMode;
    public boolean hasSavedFlightMode(){return savedFlightMode;}
    /** Local feet and velocity belong to the solid support, orientation to the field if present. */
    public record BoardingAnchor(UUID support,UUID gravityOwner,Vec3 feet,Vec3 velocity,Quat frame) {}
    public record BoardingResume(UUID body,String dimension,Quat frame,BoardingAnchor anchor) {}
    private BoardingResume boardingResume;
    private boolean boardingResumePending;
    public BoardingResume pendingBoardingResume(){return boardingResumePending?boardingResume:null;}
    public void rememberBoarding(UUID body,String dimension,Quat frame){rememberBoarding(body,dimension,frame,null);}
    public void rememberBoarding(UUID body,String dimension,Quat frame,BoardingAnchor anchor){
        boardingResume=new BoardingResume(body,dimension,frame,anchor);boardingResumePending=false;
    }
    public void clearBoardingResume(){boardingResume=null;boardingResumePending=false;}
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

	/** Descent ship basis (synced from client while 6DoF). */
	private boolean shipAttitudeValid;
    private com.terminaldetector.drmd.client.portal.PortalTransform.Quat shipOrientation=
        com.terminaldetector.drmd.client.portal.PortalTransform.Quat.IDENTITY;

	// Tunable physics (admin d6_set) — Source units, scaled at runtime. accel/drag/maxSpeed are the
	// pilotless free-6DoF fallback only — while piloting a Pyro GX, its own fields apply instead
	// (see FlightSystem.tick's effAccel/effDrag/effMaxSpeed resolution).
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
	/** Pilotless free-6DoF fallback tier — while piloting a Pyro GX, the ship's own tier applies
	 *  instead (see {@code FlightSystem.tick}, {@code PyroShipEntity.getAfterburnerTier}). */
	private int afterburnerTier = com.terminaldetector.drmd.flight.AfterburnerTiers.DEFAULT;
	private float gravyEnergy = 100f;
	private boolean gravyGrabbing;

	// Workshop live-tuning overrides
	private float wepInherit = 1f;
	private float wepRecoil = 1f;

	/** First-join tip already shown. */
	private boolean sessionWelcomed;

	/** Last Overworld XZ — planet-map focus while viewing from End / orbit. */
	private int lastOverworldX;
	private int lastOverworldZ;

	/** UUID of the last {@code PyroShipEntity} this pilot boarded — lets a rejoin find and remount
	 *  the same hull instead of always minting a fresh one (see {@code FlightSystem.autoMountPyroShip}). */
	private UUID lastShipUuid;
	/**
	 * Where that hull was last seen, remembered alongside its id.
	 *
	 * <p>The id alone is not enough to find it. {@code ServerWorld.getEntity(UUID)} searches the
	 * loaded entity index, so a hull parked and walked away from is indistinguishable from one that
	 * never existed — and the pilot gets a fresh ship while the old one stays in the world. That is
	 * the litter that had to be burned off by hand. With a position the chunk can be loaded and the
	 * lookup retried.
	 *
	 * <p>Null when no hull is remembered, and kept in step with {@link #lastShipUuid} by
	 * {@link #setLastShip} rather than by two independent setters that could disagree.
	 */
	private BlockPos lastShipPos;

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
		d.putInt("abTier", afterburnerTier);
		d.putFloat("gravity", gravity);
		d.putFloat("accel", accel);
		d.putFloat("drag", drag);
		d.putFloat("maxSpeed", maxSpeed);
		d.putFloat("wepInherit", wepInherit);
		d.putFloat("wepRecoil", wepRecoil);
		d.putBoolean("radar", radarEnabled);
		d.putBoolean("sessionWelcomed", sessionWelcomed);
        if(boardingResume!=null&&!enabled) {
            var anchor=boardingResume.anchor();
            d.putInt("boardingResumeSchema",anchor==null?1:2);
            d.putUuid("boardingBody",boardingResume.body());d.putString("boardingDimension",boardingResume.dimension());
            var q=boardingResume.frame();d.putDouble("boardingQx",q.x());d.putDouble("boardingQy",q.y());d.putDouble("boardingQz",q.z());d.putDouble("boardingQw",q.w());
            if(anchor!=null){
                var a=new NbtCompound();a.putUuid("support",anchor.support());
                if(anchor.gravityOwner()!=null)a.putUuid("gravity",anchor.gravityOwner());
                writeBoardingVector(a,"feet",anchor.feet());writeBoardingVector(a,"velocity",anchor.velocity());
                var frame=anchor.frame();a.putDouble("qx",frame.x());a.putDouble("qy",frame.y());a.putDouble("qz",frame.z());a.putDouble("qw",frame.w());
                d.put("boardingAnchor",a);
            }
        }
		if (lastShipUuid != null) d.putUuid("lastShip", lastShipUuid);
		if (lastShipPos != null) d.putLong("lastShipPos", lastShipPos.asLong());
		nbt.put("DrmdData", d);
	}

    private static void writeBoardingVector(NbtCompound n,String key,Vec3 v){
        n.putDouble(key+"x",v.x());n.putDouble(key+"y",v.y());n.putDouble(key+"z",v.z());
    }

    private static Vec3 readBoardingVector(NbtCompound n,String key){
        if(!n.contains(key+"x",NbtElement.NUMBER_TYPE)||!n.contains(key+"y",NbtElement.NUMBER_TYPE)
            ||!n.contains(key+"z",NbtElement.NUMBER_TYPE))return null;
        var v=new Vec3(n.getDouble(key+"x"),n.getDouble(key+"y"),n.getDouble(key+"z"));
        return Double.isFinite(v.lengthSquared())?v:null;
    }

    private static Quat readBoardingFrame(NbtCompound n,String prefix){
        var q=new Quat(n.getDouble(prefix+"x"),n.getDouble(prefix+"y"),n.getDouble(prefix+"z"),n.getDouble(prefix+"w"));
        double norm=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
        return Double.isFinite(norm)&&norm>1e-8?q.normalized():null;
    }

    private static BoardingAnchor readBoardingAnchor(NbtCompound n,UUID body){
        if(!n.containsUuid("support")||(n.contains("gravity")&&!n.containsUuid("gravity")))return null;
        UUID support=n.getUuid("support"),gravity=n.containsUuid("gravity")?n.getUuid("gravity"):null;
        if(!body.equals(gravity==null?support:gravity))return null;
        var feet=readBoardingVector(n,"feet");var velocity=readBoardingVector(n,"velocity");var frame=readBoardingFrame(n,"q");
        return feet==null||velocity==null||frame==null?null:new BoardingAnchor(support,gravity,feet,velocity,frame);
    }

	public void readNbt(NbtCompound nbt) {
		savedFlightMode=false;
        clearBoardingResume();
		if (!nbt.contains("DrmdData")) return;
		NbtCompound d = nbt.getCompound("DrmdData");
		savedFlightMode=d.contains("enabled");
		enabled = d.getBoolean("enabled");
        int boardingSchema=d.getInt("boardingResumeSchema");
        if(!enabled&&(boardingSchema==1||boardingSchema==2)&&d.containsUuid("boardingBody")&&net.minecraft.util.Identifier.tryParse(d.getString("boardingDimension"))!=null) {
            UUID body=d.getUuid("boardingBody");var q=readBoardingFrame(d,"boardingQ");
            var anchor=boardingSchema==2?readBoardingAnchor(d.getCompound("boardingAnchor"),body):null;
            if(q!=null&&(boardingSchema==1||anchor!=null)){
                boardingResume=new BoardingResume(body,d.getString("boardingDimension"),q,anchor);boardingResumePending=true;
            }
        }
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
		afterburnerTier = d.contains("abTier")
				? com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(d.getInt("abTier"))
				: com.terminaldetector.drmd.flight.AfterburnerTiers.DEFAULT;
		if (d.contains("gravity")) gravity = d.getFloat("gravity");
		if (d.contains("accel")) accel = d.getFloat("accel");
		if (d.contains("drag")) drag = d.getFloat("drag");
		if (d.contains("maxSpeed")) maxSpeed = d.getFloat("maxSpeed");
		if (d.contains("wepInherit")) wepInherit = d.getFloat("wepInherit");
		if (d.contains("wepRecoil")) wepRecoil = d.getFloat("wepRecoil");
		radarEnabled = !d.contains("radar") || d.getBoolean("radar");
		sessionWelcomed = d.getBoolean("sessionWelcomed");
		lastShipUuid = d.containsUuid("lastShip") ? d.getUuid("lastShip") : null;
		lastShipPos = d.contains("lastShipPos") ? BlockPos.fromLong(d.getLong("lastShipPos")) : null;
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
	public int getAfterburnerTier() {
		return droneLoadout!=null && droneLoadout.getStack(4).getItem() instanceof com.terminaldetector.drmd.flight.AcceleratorModuleItem module?module.tier():com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(afterburnerTier);
	}
	public void setAfterburnerTier(int afterburnerTier) {
		this.afterburnerTier = com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(afterburnerTier);
	}
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

	public int getLastOverworldX() { return lastOverworldX; }
	public int getLastOverworldZ() { return lastOverworldZ; }
	public void setLastOverworldBlock(int x, int z) {
		this.lastOverworldX = x;
		this.lastOverworldZ = z;
	}

	public UUID getLastShipUuid() { return lastShipUuid; }
	/** Where the remembered hull was last seen, or null — see {@link #lastShipPos}. */
	public BlockPos getLastShipPos() { return lastShipPos; }

	/**
	 * Remember a hull by id and position together.
	 *
	 * <p>One setter rather than two, because the two are only useful in agreement: an id without a
	 * position cannot be found once its chunk unloads, and a position without an id names no
	 * particular hull.
	 */
	public void setLastShip(UUID uuid, BlockPos pos) {
		this.lastShipUuid = uuid;
		this.lastShipPos = pos;
	}

	public void clearLastShip() {
		this.lastShipUuid = null;
		this.lastShipPos = null;
	}

	public boolean hasShipAttitude() { return shipAttitudeValid; }

	public void clearShipAttitude() {
		this.shipAttitudeValid = false;
        this.shipOrientation=com.terminaldetector.drmd.client.portal.PortalTransform.Quat.IDENTITY;
	}

	public void setShipAttitude(float fx, float fy, float fz, float ux, float uy, float uz) {
        var frame=com.terminaldetector.drmd.physics.OrientationFrame.decode(new Vec3d(fx,fy,fz),new Vec3d(ux,uy,uz));
        if(frame.isEmpty())return; // invalid packet must not poison the last authoritative state
        shipOrientation=frame.orElseThrow();shipAttitudeValid=true;
        roll=com.terminaldetector.drmd.flight.ShipAttitude.bankDegrees(
            com.terminaldetector.drmd.physics.OrientationFrame.toWorld(shipOrientation,new Vec3d(0,0,1)),
            com.terminaldetector.drmd.physics.OrientationFrame.toWorld(shipOrientation,new Vec3d(0,1,0)));
    }
    public com.terminaldetector.drmd.client.portal.PortalTransform.Quat shipOrientation(){return shipOrientation;}

    public Vec3d shipForward(PlayerEntity player) {
        if(shipAttitudeValid)return com.terminaldetector.drmd.physics.OrientationFrame.toWorld(shipOrientation,new Vec3d(0,0,1));
        return player.getRotationVec(1f);
    }
    public Vec3d shipUp(PlayerEntity player) {
        if(shipAttitudeValid)return com.terminaldetector.drmd.physics.OrientationFrame.toWorld(shipOrientation,new Vec3d(0,1,0));
        return com.terminaldetector.drmd.flight.ShipAttitude.levelUpOf(player.getRotationVec(1f));
    }

	public Vec3d shipRight(PlayerEntity player) {
		return shipForward(player).crossProduct(shipUp(player)).normalize();
	}

	public void levelShipAttitude(PlayerEntity player) {
		com.terminaldetector.drmd.flight.ShipAttitude a = new com.terminaldetector.drmd.flight.ShipAttitude();
		if (shipAttitudeValid) a.setForwardUp(shipForward(player),shipUp(player));
		else a.fromLook(player.getRotationVec(1f));
		a.levelRoll();
		setShipAttitude((float) a.fx, (float) a.fy, (float) a.fz, (float) a.ux, (float) a.uy, (float) a.uz);
	}
}
