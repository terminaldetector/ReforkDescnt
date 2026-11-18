package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.weapon.core.InterceptAim;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Vector3f;
import java.util.*;
import java.lang.ref.WeakReference;

/** U07 prototype v0: a distinct physical flyer; teleport is a finite, vulnerable server transaction. */
public final class TeleportFlyerEntity extends FlightEnemyEntity {
    public static final float TELEPORT_COST=20;
    public static final double CANDIDATE_RADIUS=8,MAX_COMMIT_DISPLACEMENT=16;
    private static final String SAVE="u07Teleport";
    private static final TrackedData<Integer> STAGE=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TICKS=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    // Int coordinates avoid BlockPos packet's12-bit Y and float world-coordinate precision loss.
    private static final TrackedData<Integer> MARK_X=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MARK_Y=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MARK_Z=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> MARK_FRACTION=DataTracker.registerData(TeleportFlyerEntity.class,TrackedDataHandlerRegistry.VECTOR3F);
    private final TeleportAbilityState ability=new TeleportAbilityState();
    private final UUID lifetimeNonce=UUID.randomUUID();
    private UUID observedTarget;
    private WeakReference<ServerWorld> chargeWorld;
    private Vec3d lockedDestination;
    private NbtElement opaqueAbility;
    private long relocations,pendingRequestTick=Long.MIN_VALUE;
    public TeleportFlyerEntity(EntityType<? extends TeleportFlyerEntity> type,World world){super(type,world,FlightEnemyKind.TELEPORT_FLYER);}
    @Override protected void initDataTracker(DataTracker.Builder builder){
        super.initDataTracker(builder);builder.add(STAGE,0);builder.add(TICKS,0);builder.add(MARK_X,0);builder.add(MARK_Y,0);builder.add(MARK_Z,0);builder.add(MARK_FRACTION,new Vector3f());
    }
    @Override protected void initGoals(){
        // Reuse actual goals, but never install the base village-bombardment path for this type.
        goalSelector.add(0,new TeleportFlyerGoal(this));goalSelector.add(1,new DroneAi.CombatGoal(this));
        goalSelector.add(2,new SquadInvestigateGoal(this));goalSelector.add(8,new LookAtEntityGoal(this,LivingEntity.class,24));
        HostileEnvironment.installTargets(this,targetSelector);
    }
    @Override public boolean continuousFlightSteering(){return true;}
    @Override public Vec3d steerFlightVelocity(Vec3d desired){return JetFlightMotor.steer(getVelocity(),desired);}
    public TeleportAbilityState ability(){return ability;}
    public UUID lifetimeNonce(){return lifetimeNonce;}
    public Vec3d lockedDestination(){return lockedDestination;}
    public long teleportCount(){return relocations;}
    @Override protected void onPortalFrameMoved(){
        TeleportAbilityRelay.cancelPending(this);cancelCharge();pendingRequestTick=Long.MIN_VALUE;
    }
    @Override public java.util.List<DataTracker.SerializedEntry<?>> portalTrackedPose(){
        var pose=new java.util.ArrayList<DataTracker.SerializedEntry<?>>(super.portalTrackedPose());
        pose.add(DataTracker.SerializedEntry.of(STAGE,dataTracker.get(STAGE)));pose.add(DataTracker.SerializedEntry.of(TICKS,dataTracker.get(TICKS)));return java.util.List.copyOf(pose);
    }
    public boolean serverEligible(){
        return getWorld() instanceof ServerWorld world&&world.getServer().isOnThread()&&isAlive()&&!isRemoved()&&!isAiDisabled()
            &&!hasVehicle()&&!hasPassengers()&&world.getEntity(getUuid())==this&&world.shouldTickEntity(getBlockPos());
    }
    public LivingEntity chargedEnemy(){
        var target=getTarget();
        return target!=null&&observedTarget!=null&&observedTarget.equals(target.getUuid())&&target.isAlive()&&!target.isRemoved()
            &&target.getWorld()==getWorld()&&getWorld() instanceof ServerWorld world&&world.getEntity(target.getUuid())==target
            &&HostileEnvironment.isHostileTarget(this,target)&&!isTeammate(target)&&canTarget(target)?target:null;
    }
    public boolean requestCharge(){
        var target=getTarget();if(!TeleportAbilityRelay.registered()||!serverEligible()||!ability.ready()||getModEnergy()<TELEPORT_COST||target==null
            ||!TeleportFlyerGoal.visible(this,target,false))return false;
        long token=ability.requestStart();if(token<0)return false;observedTarget=target.getUuid();pendingRequestTick=getWorld().getTime();chargeWorld=new WeakReference<>((ServerWorld)getWorld());
        if(!TeleportAbilityRelay.enqueue(this,TeleportAbilityRelay.Operation.START,token)){cancelCharge();return false;}
        syncAbility();return true;
    }
    public boolean beginWarning(long token,Vec3d destination){
        if(!ability.matches(TeleportAbilityState.Phase.START_PENDING,token)||!ability.warn(token))return false;
        lockedDestination=destination;syncAbility();emitCue(destination);
        if(getWorld() instanceof ServerWorld world)world.playSound(null,getX(),getY(),getZ(),net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE,net.minecraft.sound.SoundCategory.HOSTILE,.5f,1.3f);
        return true;
    }
    public void cancelCharge(){ability.interrupt();lockedDestination=null;observedTarget=null;chargeWorld=null;syncAbility();}
    public boolean commitTeleport(TeleportAbilityRelay.CommitPermit permit){
        if(permit==null||!permit.consume(this,lockedDestination))return false;
        long token=permit.epoch();
        if(!serverEligible()||lockedDestination==null||!ability.matches(TeleportAbilityState.Phase.COMMIT_PENDING,token)
            ||getPos().squaredDistanceTo(lockedDestination)>MAX_COMMIT_DISPLACEMENT*MAX_COMMIT_DISPLACEMENT)return false;
        Vec3d destination=lockedDestination,old=getPos(),velocity=getVelocity();float yaw=getYaw(),pitch=getPitch(),roll=getFlightRoll();
        boolean accepted=withModEnergy(TELEPORT_COST,()->{
            try{
                requestTeleport(destination.x,destination.y,destination.z);
                setVelocity(velocity);setYaw(yaw);setPitch(pitch);setFlightRoll(roll);velocityModified=true;
                if(getWorld() instanceof ServerWorld world&&world.getEntity(getUuid())==this&&isAlive()&&!isRemoved()
                    &&ability.matches(TeleportAbilityState.Phase.COMMIT_PENDING,token)&&getPos().squaredDistanceTo(destination)<1e-10)return true;
            }catch(RuntimeException refused){/* Restore the one known pre-transaction pose, refund below. */}
            if(!isRemoved()){requestTeleport(old.x,old.y,old.z);setVelocity(velocity);setYaw(yaw);setPitch(pitch);setFlightRoll(roll);velocityModified=true;}
            return false;
        });
        if(!accepted)return false;
        relocations++;ability.committed(token);lockedDestination=null;observedTarget=null;chargeWorld=null;syncAbility();emitCue(old);emitCue(destination);
        if(getWorld() instanceof ServerWorld world)world.playSound(null,getX(),getY(),getZ(),net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,net.minecraft.sound.SoundCategory.HOSTILE,.7f,1);
        return true;
    }
    @Override public void tick(){
        if(!getWorld().isClient){
            boolean due=ability.tick(getWorld().getTime());if(due)pendingRequestTick=getWorld().getTime();
            boolean pending=ability.phase()==TeleportAbilityState.Phase.START_PENDING||ability.phase()==TeleportAbilityState.Phase.WARN||ability.phase()==TeleportAbilityState.Phase.COMMIT_PENDING;
            if(ability.ownsMovement()&&(!serverEligible()||pending&&(chargeWorld==null||chargeWorld.get()!=getWorld()||pendingRequestTick<getWorld().getTime()&&ability.phase()!=TeleportAbilityState.Phase.WARN)))cancelCharge();
            if(due&&ability.phase()==TeleportAbilityState.Phase.COMMIT_PENDING
                &&!TeleportAbilityRelay.enqueue(this,TeleportAbilityRelay.Operation.COMMIT,ability.epoch()))cancelCharge();
        }
        super.tick();
        if(!getWorld().isClient){syncAbility();if(ability.phase()==TeleportAbilityState.Phase.WARN&&age%5==0&&lockedDestination!=null)emitCue(lockedDestination);}
    }
    @Override public boolean tryFireAt(LivingEntity target){
        return serverEligible()&&ability.mayFire()&&TeleportFlyerGoal.visible(this,target,false)&&super.tryFireAt(target);
    }
    @Override protected Vec3d projectileAim(LivingEntity target,Vec3d from,Vec3d direct,WeaponCore.FireConfig shot){
        if(getRole()!=AiRole.INTERCEPTOR||target==null||shot.owner!=this||shot.homing||shot.pilotGuided||shot.gravity!=0||shot.drag!=0
            ||shot.inherit!=1||shot.inheritedVelocity!=null||!Float.isFinite(shot.speed)||!Float.isFinite(shot.life))return direct;
        // Match WeaponCore's real launch-speed float conversion and normal life cap, preserving its full owner-velocity inheritance.
        float speed=shot.speedIsSourceUnits?(float)(DescentMod.su(shot.speed)/DescentMod.TICKS_PER_SECOND):shot.speed;
        int life=(int)(shot.life*DescentMod.TICKS_PER_SECOND);
        var offset=target.getBoundingBox().getCenter().subtract(from);var relative=target.getVelocity().subtract(getVelocity());
        return InterceptAim.solve(new Vec3(offset.x,offset.y,offset.z),new Vec3(relative.x,relative.y,relative.z),speed,life)
            .map(s->new Vec3d(s.direction().x(),s.direction().y(),s.direction().z())).orElse(direct);
    }
    @Override public boolean damage(DamageSource source,float amount){
        float before=getHealth()+getDroneShield()+getDroneArmor();boolean result=super.damage(source,amount);
        if(!getWorld().isClient&&getHealth()+getDroneShield()+getDroneArmor()<before)cancelCharge();
        return result;
    }
    @Override public void onDeath(DamageSource source){if(!getWorld().isClient)cancelCharge();super.onDeath(source);}
    private void emitCue(Vec3d at){
        if(getWorld() instanceof ServerWorld world)world.spawnParticles(ParticleTypes.REVERSE_PORTAL,at.x,at.y+getHeight()*.5,at.z,6,.4,.4,.4,0);
    }
    private void syncAbility(){
        if(getWorld().isClient)return;
        dataTracker.set(STAGE,ability.phase().ordinal());dataTracker.set(TICKS,ability.remaining());
        if(lockedDestination!=null){var at=BlockPos.ofFloored(lockedDestination);dataTracker.set(MARK_X,at.getX());dataTracker.set(MARK_Y,at.getY());dataTracker.set(MARK_Z,at.getZ());
            dataTracker.set(MARK_FRACTION,new Vector3f((float)(lockedDestination.x-at.getX()),(float)(lockedDestination.y-at.getY()),(float)(lockedDestination.z-at.getZ())));}
    }
    public TeleportAbilityState.Phase displayedPhase(){int value=dataTracker.get(STAGE);return value>=0&&value<TeleportAbilityState.Phase.values().length?TeleportAbilityState.Phase.values()[value]:TeleportAbilityState.Phase.REFUSED;}
    public int displayedTicks(){return dataTracker.get(TICKS);}
    public Vec3d displayedDestination(){var f=dataTracker.get(MARK_FRACTION);return new Vec3d(dataTracker.get(MARK_X).doubleValue()+f.x,dataTracker.get(MARK_Y).doubleValue()+f.y,dataTracker.get(MARK_Z).doubleValue()+f.z);}
    @Override public void writeCustomDataToNbt(NbtCompound nbt){
        super.writeCustomDataToNbt(nbt);
        if(opaqueAbility!=null){nbt.put(SAVE,opaqueAbility.copy());return;}
        var data=ability.saved();var tag=new NbtCompound();tag.putInt("schema",1);tag.putString("phase",data.phase().name());tag.putInt("remaining",data.remaining());tag.putInt("cooldown",data.cooldown());nbt.put(SAVE,tag);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt){
        super.readCustomDataFromNbt(nbt);opaqueAbility=null;lockedDestination=null;observedTarget=null;chargeWorld=null;
        var raw=nbt.get(SAVE);
        if(raw==null){ability.restore(new TeleportAbilityState.Saved(TeleportAbilityState.Phase.IDLE,0,0));syncAbility();return;}
        boolean accepted=false;
        if(raw instanceof NbtCompound tag&&tag.getKeys().equals(Set.of("schema","phase","remaining","cooldown"))
            &&tag.contains("schema",NbtElement.INT_TYPE)&&tag.getInt("schema")==1&&tag.contains("phase",NbtElement.STRING_TYPE)
            &&tag.contains("remaining",NbtElement.INT_TYPE)&&tag.contains("cooldown",NbtElement.INT_TYPE)){
            try{accepted=ability.restore(new TeleportAbilityState.Saved(TeleportAbilityState.Phase.valueOf(tag.getString("phase")),tag.getInt("remaining"),tag.getInt("cooldown")));}
            catch(IllegalArgumentException malformed){accepted=false;}
        }
        if(!accepted){opaqueAbility=raw.copy();ability.refuse();}syncAbility();
    }
}
