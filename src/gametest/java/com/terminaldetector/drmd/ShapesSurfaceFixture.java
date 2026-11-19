package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.DroneSquadCoordinator;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import java.util.function.Consumer;

/** One finite loaded surface, real native goals and one pre-spawn checked frame per actor. */
final class ShapesSurfaceFixture implements AutoCloseable {
    private static final Map<ShapesInfantryEntity,ShapesSurfaceFixture> ACTIVE=new IdentityHashMap<>();
    private record Birth(ProjectileEntity projectile,Vec3d position,Vec3d velocity,Vec3d actualShooterPosition,long worldTick,int actorAge) {}
    static {
        // Read-only native event observations, armed only for these finite fixture objects.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,w)->{
            if(!(entity instanceof ProjectileEntity shot)||!(shot.getOwnerLiving() instanceof ShapesInfantryEntity owner))return;
            var f=ACTIVE.get(owner);if(f==null||f.closed||f.world!=w)return;
            f.emitted++;
            if(f.births.size()>=128){f.evidenceOverflow=true;return;}
            f.births.put(shot.getUuid(),new Birth(shot,shot.getPos(),shot.getVelocity(),owner.getPos(),w.getTime(),owner.age));
            if(f.emitted<=8)System.out.println("SHAPES_SURFACE_REAL_ENTITY_LOAD owner="+owner.getUuid()+" shot="+shot.getUuid()+" actorAge="+owner.age+" worldTick="+w.getTime()+" shotPosition="+shot.getPos()+" shotVelocity="+shot.getVelocity()+" actualShooterPosition="+owner.getPos());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(source.getAttacker() instanceof ShapesInfantryEntity owner){
                var f=ACTIVE.get(owner);
                if(f!=null&&!f.closed&&entity==f.victim&&entity.getWorld()==f.world&&amount>0){
                    f.hitAttempts++;
                    // WeaponCore uses mobAttack(owner), so DamageSource has no projectile UUID.
                    // Keep the two genuine facts separate: this exact owner's native load and
                    // this exact victim's real damage attempt. Never invent a hit-round id.
                    for(var birth:f.births.values()){
                        var p=birth.projectile();long elapsed=f.world.getTime()-birth.worldTick();
                        if(elapsed>=0&&elapsed<=80&&p.age>0&&!p.isRemoved()&&p.getOwnerLiving()==owner
                            &&f.world.getEntity(p.getUuid())==p&&birth.velocity().lengthSquared()>.01
                            &&!f.victim.getBoundingBox().expand(.1).contains(birth.position())){
                            f.nativeImpactAfterBirth=true;break;
                        }
                    }
                    if(f.hitAttempts<=8)System.out.println("SHAPES_SURFACE_NATIVE_HIT_ATTEMPT owner="+owner.getUuid()+" victim="+entity.getUuid()+" actorAge="+owner.age+" worldTick="+f.world.getTime()+" damage="+amount+" appliedRoundIdentity=NOT_IN_DAMAGE_SOURCE registeredNativeBirth="+f.nativeImpactAfterBirth);
                }
            }
            return true; // the observer never changes damage permission or the hurt-window branch
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,amount,blocked)->{
            if(source.getAttacker() instanceof ShapesInfantryEntity owner){var f=ACTIVE.get(owner);if(f!=null&&!f.closed&&entity==f.victim&&amount>0&&!blocked)f.appliedDamage++;}
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,w)->ACTIVE.entrySet().removeIf(e->e.getValue().world==w));
    }
    final TestContext c;final ServerWorld world;final BlockPos centre,min,max;final Vec3d up,plane;
    final AiFixtureChunks chunks;final ShapesInfantryEntity receiver,scout;final CowEntity victim;
    final UUID field;final boolean barrier;final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
    final Object navigation,control;final List<Entity> extra=new ArrayList<>();
    int calls,born=-1,lastAge,emitted,hitAttempts,appliedDamage;boolean spawned,closed,enabled,mailSeen,preOwnMovement,localPath,ownTarget,movingShot,passage;
    boolean twoSampleMovement,nativeImpactAfterBirth,evidenceOverflow;final Map<UUID,Birth> births=new LinkedHashMap<>();
    double largestStep,largestGap,initialDistance;Vec3d start,last;final Map<UUID,Vec3d> shots=new HashMap<>();
    ShapesSurfaceFixture(TestContext c,Vec3d up,boolean barrier){
        this.c=c;this.up=up;this.barrier=barrier;world=c.getWorld();centre=c.getAbsolutePos(new BlockPos(6,100,6));min=centre.add(-8,-8,-8);max=centre.add(8,8,8);
        plane=Vec3d.ofCenter(centre).subtract(up.multiply(7.5));chunks=new AiFixtureChunks(world,centre,1);
        receiver=ModEntities.SHAPES_INFANTRY.create(world);scout=ModEntities.SHAPES_INFANTRY.create(world);victim=EntityType.COW.create(world);
        c.assertTrue(receiver!=null&&scout!=null&&victim!=null,"actual registered Shapes and real victim exist");
        field=up.y>.999999?null:UUID.randomUUID();navigation=receiver.getNavigation();control=receiver.getMoveControl();
        c.assertTrue(ACTIVE.size()<4,"finite four-rig native-event observer admission");ACTIVE.put(receiver,this);
        receiver.setPosition(point(-4,.02,-4));scout.setPosition(point(4,.02,4));victim.setPosition(point(0,.02,4));
        receiver.setPersistent();scout.setPersistent();victim.setPersistent();receiver.setAiDisabled(true);scout.setAiDisabled(true);victim.setAiDisabled(true);
        victim.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4096);victim.setHealth(4096);
    }
    Vec3d point(double x,double y,double z){return plane.add(ActorFrameMath.toWorld(new Vec3d(x,y,z),up));}
    boolean admit(){
        world.resetIdleTimeout();
        if(!spawned){
            if(!chunks.entityTicking(receiver.getBlockPos(),scout.getBlockPos(),victim.getBlockPos()))return false;
            var access=WorldAccess.of(world);
            for(int x=(min.getX()-1)>>4;x<=(max.getX()+1)>>4;x++)for(int y=(min.getY()-1)>>4;y<=(max.getY()+1)>>4;y++)for(int z=(min.getZ()-1)>>4;z<=(max.getZ()+1)>>4;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
            for(var at:BlockPos.iterate(min,max)){
                c.assertTrue(world.getBlockEntity(at)==null,"loaded disposable surface cannot overwrite functional state");originals.put(at.toImmutable(),world.getBlockState(at));
                boolean shell=at.getX()==min.getX()||at.getX()==max.getX()||at.getY()==min.getY()||at.getY()==max.getY()||at.getZ()==min.getZ()||at.getZ()==max.getZ();
                var local=ActorFrameMath.toLocal(Vec3d.ofCenter(at).subtract(plane),up);
                boolean plate=barrier&&local.y>0&&local.y<5&&local.x>=-7.01&&local.x<=1.01&&Math.abs(local.z)<.1;
                world.setBlockState(at,(shell||plate?Blocks.OBSIDIAN:Blocks.AIR).getDefaultState(),2);
            }
            for(var actor:List.of(receiver,scout,victim)){
                c.assertTrue(!actor.hasNoGravity(),"native flags remain owned by each actual actor");
                if(field!=null)c.assertTrue(ActorFrames.trySetUp(actor,up),"one disclosed pre-spawn checked physical frame, not a pose follower");
                c.assertTrue(world.spawnEntity(actor),"native world registration");
            }
            if(field!=null)GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,up.negate(),32,1,FieldShape.SPHERE,"Shapes native surface route"));
            spawned=true;return false;
        }
        if(receiver.age<2||scout.age<2||victim.age<2)return false;
        if(field!=null&&(!EntityGravitySystem.suppressesVanillaGravity(receiver)||ActorFrames.up(receiver).distanceTo(up)>1e-6||ActorFrames.up(scout).distanceTo(up)>1e-6||ActorFrames.up(victim).distanceTo(up)>1e-6))return false;
        support(receiver);support(scout);support(victim);return true;
    }
    double support(LivingEntity actor){
        c.assertTrue(world.isSpaceEmpty(actor),"actual tall body stays clear of real material");
        var from=actor.getPos().add(up.multiply(.04));var to=actor.getPos().subtract(up.multiply(1.6));
        c.assertTrue(com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(world,from,to),"support ray reads only FULL fixture terrain");
        var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        c.assertTrue(hit.getType()==HitResult.Type.BLOCK&&Vec3d.of(hit.getSide().getVector()).dotProduct(up)>=.95,"real support normal follows the actual local UP");
        double gap=actor.getPos().subtract(hit.getPos()).dotProduct(up);c.assertTrue(gap>=-1e-6&&gap<=.08,"the existing physical support skin is .08m: "+gap);return gap;
    }
    void enable(){
        c.assertTrue(!receiver.canSee(victim)&&scout.canSee(victim),"actual five-block plate occludes receiver while the real allied scout has its own eye LOS");
        receiver.setAiDisabled(false);scout.setAiDisabled(false);enabled=true;born=lastAge=receiver.age;start=last=receiver.getPos();initialDistance=start.distanceTo(victim.getPos());
    }
    void sample(){
        if(receiver.age==lastAge)return;
        largestStep=Math.max(largestStep,receiver.getPos().distanceTo(last)/Math.max(1,receiver.age-lastAge));last=receiver.getPos();lastAge=receiver.age;largestGap=Math.max(largestGap,support(receiver));
        c.assertTrue(!receiver.isAiDisabled()&&!scout.isAiDisabled()&&!receiver.hasNoGravity()&&receiver.getNavigation()==navigation&&receiver.getMoveControl()==control&&largestStep<=1,"same native controllers/flags and finite actual motion, no pose or velocity following");
        var local=ActorFrameMath.toLocal(receiver.getPos().subtract(plane),up);
        if(Math.abs(local.z)<=.8){c.assertTrue(local.x>=1.8-1e-5,"the actual body crosses the plate slab only through its right passage");passage=true;}
        var message=DroneSquadCoordinator.message(receiver);
        if(message.isPresent()&&message.get().scout().equals(scout.getUuid())&&message.get().target().equals(victim.getUuid())){
            mailSeen=true;var m=message.get();double normal=Math.abs(new Vec3d(m.x(),m.y(),m.z()).subtract(receiver.getPos()).dotProduct(up));
            c.assertTrue(normal>1,"actual copied radio point is the victim EYE, not a secretly read remote foot point");
            if(receiver.getTarget()==null&&!receiver.canSee(victim)&&receiver.getPos().distanceTo(start)>.5)preOwnMovement=true;
        }
        localPath|=receiver.getNavigation().getCurrentPath() instanceof LocalSurfacePath;
        ownTarget|=receiver.getTarget()==victim&&receiver.canSee(victim);
        var box=new Box(Vec3d.of(min),Vec3d.of(max.add(1,1,1))).expand(4);
        for(var p:world.getEntitiesByClass(ProjectileEntity.class,box,p->p.getOwnerLiving()==receiver)){
            var before=shots.putIfAbsent(p.getUuid(),p.getPos());twoSampleMovement|=p.age>0&&before!=null&&p.getPos().distanceTo(before)>.01&&p.getVelocity().lengthSquared()>.01;
        }
        c.assertTrue(!evidenceOverflow,"native event evidence stays within128 actual receiver-owned projectile births");
        movingShot=twoSampleMovement||nativeImpactAfterBirth;
        var stats=DroneSquadCoordinator.stats(world);c.assertTrue(stats.mailboxes()<=256&&stats.maxAllies()<=32&&stats.maxCandidates()<=96,"existing bounded squad coordinator remains the sole knowledge source");
    }
    boolean completeRoute(){return mailSeen&&preOwnMovement&&(field==null||localPath)&&passage&&ownTarget&&movingShot&&initialDistance-receiver.distanceTo(victim)>=1;}
    String state(){return "age="+(receiver.age-born)+" up="+up+" pos="+receiver.getPos()+" local="+ActorFrameMath.toLocal(receiver.getPos().subtract(plane),up)+" mail="+mailSeen+" preOwnMovement="+preOwnMovement+" localPath="+localPath+" passage="+passage+" ownTarget="+ownTarget+" movingShot="+movingShot+" legacyTwoSampleMovement="+twoSampleMovement+" nativeBirths="+emitted+" actualNativeHitAttempts="+hitAttempts+" nativeAppliedDamage="+appliedDamage+" nativeImpactAfterBirth="+nativeImpactAfterBirth+" energy="+receiver.getWeaponEnergy()+" cooldown="+receiver.getAttackCooldownTicks()+" maxStep="+largestStep+" maxGap="+largestGap;}
    void drive(Consumer<ShapesSurfaceFixture> action){c.runAtEveryTick(()->{if(closed)return;try{c.assertTrue(++calls<430,"bounded Shapes surface fixture admission/runtime: "+state());if(admit())action.accept(this);}catch(Throwable e){close();throw e;}});}
    void complete(){close();c.complete();}
    @Override public void close(){
        if(closed)return;closed=true;ACTIVE.remove(receiver);if(field!=null)GravityFields.remove(field);
        var box=new Box(Vec3d.of(min),Vec3d.of(max.add(1,1,1))).expand(8);
        for(var p:world.getEntitiesByClass(ProjectileEntity.class,box,p->p.getOwnerLiving()==receiver||p.getOwnerLiving()==scout))p.discard();
        receiver.discard();scout.discard();victim.discard();for(var e:extra)e.discard();for(var e:originals.entrySet())world.setBlockState(e.getKey(),e.getValue(),2);chunks.close();
    }
}
