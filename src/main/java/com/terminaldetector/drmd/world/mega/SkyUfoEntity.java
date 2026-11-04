package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.structure.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** The existing registered UFO is one real BlockBody, including its hollow interior.
 * Propulsion applies forces/torques; passenger motion belongs to DynamicBodyAttachment. */
public class SkyUfoEntity extends BlockBodyEntity {
    public static final int SWARM_CAP=12;
    /** Retained source compatibility; physical integration now runs every world tick. */
    public static final int MOVE_INTERVAL=1;
    private static final BlockPos CORE=new BlockPos(0,1,0);
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static final Map<UUID,WeakReference<SkyUfoEntity>> ACTIVE=new ConcurrentHashMap<>();
    private final List<UUID> swarm=new ArrayList<>();
    private boolean initialized,crashing,detonated,autopilot=true;
    private int spawnCooldown,crashTicks;
    private double cruiseYaw,cruiseAltitude;
    private DestructionMode destructionMode=DestructionMode.CRASH;

    public SkyUfoEntity(EntityType<? extends SkyUfoEntity> type,World world){super(type,world);}
    public boolean isMaterialized(){return !cells().isEmpty();}
    public boolean isVirtualFlight(){return false;}
    public boolean isCrashing(){return crashing;}
    public void setAutopilot(boolean value){autopilot=value;}
    public boolean autopilotEnabled(){return autopilot;}
    public boolean hasControlConsole(){return getLocalBlock(UfoControlSystem.CONSOLE).isOf(Blocks.GOLD_BLOCK);}
    @Override public boolean canUseLocalBlock(BlockPos local){return local.equals(UfoControlSystem.CONSOLE)&&hasControlConsole();}
    @Override public boolean useLocalBlock(net.minecraft.server.network.ServerPlayerEntity player,BlockPos local){return canUseLocalBlock(local)&&UfoControlSystem.claim(player,this);}
    public void forceDestructionMode(DestructionMode mode){destructionMode=Objects.requireNonNull(mode);}
    public BlockPos getCorePos(){return BlockPos.ofFloored(localToWorld(Vec3d.ofCenter(CORE)));}
    public Box getInterior() {
        Vec3d first=localToWorld(new Vec3d(-7.5,0,-7.5));Box result=new Box(first,first);
        for(double x:new double[]{-7.5,7.5})for(double y:new double[]{0,3})for(double z:new double[]{-7.5,7.5})result=result.union(new Box(localToWorld(new Vec3d(x,y,z)),localToWorld(new Vec3d(x,y,z))));
        return result;
    }
    public boolean containsPos(Vec3d world) {
        if(!isMaterialized())return false;var p=worldToLocal(world);
        return p.x>=-7.5&&p.x<=7.5&&p.y>=0&&p.y<=3&&p.z>=-7.5&&p.z<=7.5;
    }
    @Override public void tick() {
        if(getWorld().isClient){super.tick();return;}
        var world=(ServerWorld)getWorld();
        if(!initialized) {
            var blueprint=SkyUfoHull.TEMPLATE.cells().entrySet().stream().filter(e->!e.getValue().isAir())
                .map(e->new Cell(new BlockPos(e.getKey().x(),e.getKey().y(),e.getKey().z()),e.getValue())).toList();
            // The legacy spawn position named the template origin; no world block is ever placed.
            var origin=getPos();initializeLocal(blueprint,origin);initialized=true;cruiseAltitude=massPosition().y();
            setPassengerGravity(crashing?ZERO:new Vec3(0,-16,0));setPhysicsGravity(crashing);
        } else if(cells().isEmpty()){discard();return;}
        ACTIVE.put(getUuid(),new WeakReference<>(this));
        if(!crashing&&getLocalBlock(CORE).isAir())destroyFromCore(world,null,"local reactor destroyed");
        if(!crashing&&com.terminaldetector.drmd.world.contraption.boarding.BoardingRestore.waiting(this)){super.tick();return;}
        if(!crashing&&!UfoControlSystem.drive(this)&&autopilot)propel();
        if(crashing&&!detonated&&++crashTicks>=100)detonate(world);
        super.tick();
        if(crashing)return;
        if(spawnCooldown>0)spawnCooldown--;
        else if(world.getPlayers().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&squaredDistanceTo(p)<128*128)) {
            pruneSwarm(world);
            if(swarm.size()<SWARM_CAP&&dropDrone(world))spawnCooldown=60;else spawnCooldown=100;
        }
    }
    private void propel() {
        cruiseYaw+=.15;double yaw=Math.toRadians(cruiseYaw);
        var desired=new Vec3(-Math.sin(yaw)*3,MathHelper.clamp((cruiseAltitude-massPosition().y())*.4,-2,2),Math.cos(yaw)*3);
        Vec3 dv=desired.minus(physics().linearVelocity()).scaled(.2);if(dv.length()>.2)dv=dv.scaled(.2/dv.length());
        applyPhysicsImpulse(physicsCentre(),new Vec3d(dv.x()*physicsMass(),dv.y()*physicsMass(),dv.z()*physicsMass()));
        var pitch=ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),12*Math.sin(age*.012));
        var roll=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),18*Math.sin(age*.009));
        var heading=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,1,0),-cruiseYaw);
        var target=ImmPtlQuaternions.hamiltonProduct(heading,ImmPtlQuaternions.hamiltonProduct(pitch,roll)).normalized();
        var error=ImmPtlQuaternions.hamiltonProduct(target,rotation().inverse()).normalized();
        if(error.w()<0)error=new Quat(-error.x(),-error.y(),-error.z(),-error.w());
        var axis=new Vec3(error.x(),error.y(),error.z());double sine=axis.length();
        var turn=sine<1e-10?ZERO:axis.scaled(2*Math.atan2(sine,error.w())/sine);
        var acceleration=turn.scaled(8).minus(physics().angularVelocity().scaled(5));
        var torque=physics().worldInertia().transform(acceleration);double cap=physicsMass()*16;
        if(torque.length()>cap)torque=torque.scaled(cap/torque.length());applyPhysicsTorque(new Vec3d(torque.x(),torque.y(),torque.z()));
    }
    private void pruneSwarm(ServerWorld world) {
        // Unloaded drones retain their UUID and occupy their bay slot: absence is not death.
        swarm.removeIf(id->{var e=world.getEntity(id);return e!=null&&(!e.isAlive()||e.isRemoved());});
    }
    private boolean dropDrone(ServerWorld world) {
        var at=localToWorld(new Vec3d(0,-5,0));
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {
            var state=access.readIfLoaded(BlockPos.ofFloored(at).add(x,y,z));if(state.isEmpty()||!state.get().isAir())return false;
        }
        var drone=ModEntities.DRONE.create(world);if(drone==null)return false;
        drone.applyRole(AiRole.ASSAULT);drone.refreshPositionAndAngles(at.x,at.y,at.z,getYaw(),0);
        var v=physics().velocityAtPoint(new Vec3(at.x-massPosition().x(),at.y-massPosition().y(),at.z-massPosition().z()));
        drone.setVelocity(v.x()*.05,v.y()*.05,v.z()*.05);
        if(!world.spawnEntity(drone))return false;swarm.add(drone.getUuid());return true;
    }
    @Override protected void onLocalMaterialChanged(List<BlockBodyEntity> fragments) {
        if(!hasControlConsole())UfoControlSystem.releaseBody(this);
        if(initialized&&!isRemoved()&&getLocalBlock(CORE).isAir())enterCrash(fragments);
    }
    private void enterCrash(List<BlockBodyEntity> fragments) {
        if(crashing)return;crashing=true;UfoControlSystem.releaseBody(this);setPassengerGravity(ZERO);setPhysicsGravity(true);
        for(var fragment:fragments)fragment.setPhysicsGravity(true);
        if(destructionMode==DestructionMode.SHOCKWAVE)crashTicks=100;
    }
    public void destroyFromCore(ServerWorld world,PlayerEntity culprit,String reason) {
        if(crashing||getWorld()!=world)return;
        if(!getLocalBlock(CORE).isAir()&&!setLocalBlock(CORE,Blocks.AIR.getDefaultState()))return;
        enterCrash(List.of());
    }
    private void detonate(ServerWorld world) {
        if(detonated)return;detonated=true;
        StructureShockwave.detonate(world,localToWorld(Vec3d.ofCenter(CORE)),this,null,4,4,5,0,0);
        // Remaining material stays as an inert, destructible physical wreck. No real-block handoff.
    }
    public static SkyUfoEntity findContaining(ServerWorld world,Vec3d pos) {
        for(var weak:ACTIVE.values()){var ufo=weak.get();if(ufo!=null&&ufo.getWorld()==world&&!ufo.isRemoved()&&!ufo.crashing&&ufo.containsPos(pos))return ufo;}return null;
    }
    public static SkyUfoEntity findNear(ServerWorld world,BlockPos pos,double range) {
        SkyUfoEntity best=null;double distance=range*range;
        for(var weak:ACTIVE.values()){var ufo=weak.get();if(ufo==null||ufo.getWorld()!=world||ufo.isRemoved()||ufo.crashing)continue;double d=ufo.getCorePos().getSquaredDistance(pos);if(d<distance){distance=d;best=ufo;}}return best;
    }
    /** Physical local cells are damaged by BlockBody's weapon/explosion adapter, not world block callbacks. */
    public static void notifyCoreBroken(ServerWorld world,BlockPos pos){}
    public static void notifyBombDetonation(ServerWorld world,BlockPos pos,float power){}
    public static boolean tryReactorDump(ServerWorld world,PlayerEntity user) {
        var ufo=findContaining(world,user.getPos());if(ufo==null)return false;
        var core=ufo.localToWorld(Vec3d.ofCenter(CORE));if(user.getEyePos().squaredDistanceTo(core)>25)return false;
        var hit=new com.terminaldetector.drmd.world.contraption.BlockBodyWorldAccess(ufo).raycastBlockBody(user.getEyePos(),core);
        if(hit.isEmpty()||!hit.get().localBlock().equals(CORE))return false;
        ufo.destroyFromCore(world,user,"reactor dump");return ufo.crashing;
    }
    /** Legacy debug mode can no longer convert this hull into a world block copy. */
    public void forceVirtualFlight(ServerWorld world,boolean virtual){}
    public void debugOutlineBox(ServerWorld world){var p=physicsCentre();world.spawnParticles(ParticleTypes.END_ROD,p.x,p.y,p.z,8,radius(),1,radius(),0);}
    @Override public void remove(RemovalReason reason) {
        UfoControlSystem.releaseBody(this);
        var ref=ACTIVE.get(getUuid());if(ref!=null&&ref.get()==this)ACTIVE.remove(getUuid());super.remove(reason);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n) {
        if(n.contains("shape",NbtElement.COMPOUND_TYPE)&&n.contains("body",NbtElement.COMPOUND_TYPE))super.readCustomDataFromNbt(n);
        if(n.getInt("physicalUfoSchema")>1)throw new IllegalArgumentException("unsupported physical UFO schema");
        initialized=n.getInt("physicalUfoSchema")==1;crashing=n.getBoolean("crashing");detonated=n.getBoolean("detonated");
        cruiseYaw=n.getDouble("cruise");cruiseAltitude=n.getDouble("cruiseAltitude");autopilot=!n.contains("autopilot")||n.getBoolean("autopilot");
        crashTicks=Math.max(0,n.getInt("crashTicks"));spawnCooldown=Math.max(0,n.getInt("bayCooldown"));
        if(!Double.isFinite(cruiseYaw)||!Double.isFinite(cruiseAltitude))throw new IllegalArgumentException("non-finite UFO controller");
        if(n.contains("destructionMode"))destructionMode=DestructionMode.valueOf(n.getString("destructionMode"));
        swarm.clear();var saved=n.getList("swarm",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<Math.min(saved.size(),SWARM_CAP);i++)if(saved.getCompound(i).containsUuid("id"))swarm.add(saved.getCompound(i).getUuid("id"));
    }
    @Override protected void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);n.putInt("physicalUfoSchema",initialized?1:0);n.putBoolean("crashing",crashing);n.putBoolean("detonated",detonated);
        n.putDouble("cruise",cruiseYaw);n.putDouble("cruiseAltitude",cruiseAltitude);n.putBoolean("autopilot",UfoControlSystem.savedAutopilot(this,autopilot));n.putInt("crashTicks",crashTicks);n.putInt("bayCooldown",spawnCooldown);
        n.putString("destructionMode",destructionMode.name());
        var list=new NbtList();for(var id:swarm){var item=new NbtCompound();item.putUuid("id",id);list.add(item);}n.put("swarm",list);
    }
}
