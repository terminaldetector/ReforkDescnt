package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.ai.DroneSquadCoordinator;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Consumer;

/** Test-owned actual floor/entities. One pre-registered GameTest driver; no direct production/entity ticks. */
final class ShapesFixture implements AutoCloseable {
    final TestContext c;final ServerWorld world;final BlockPos base;final Vec3d start;
    final AiFixtureChunks chunks;final ShapesInfantryEntity infantry,scout;final CowEntity victim;
    final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
    final List<Entity> extra=new ArrayList<>();final Set<UUID> shots=new HashSet<>();
    final Map<UUID,Vec3d> firstShotPos=new HashMap<>();
    int admission,ticks;boolean spawned,closed,movingShot,attributedDamage,ownTarget,preOwnInvestigation;
    Vec3d firstMailPos;
    ShapesFixture(TestContext c,int offset,boolean squad,boolean observer){
        this.c=c;world=c.getWorld();base=c.getAbsolutePos(new BlockPos(116000+offset,130,14000));start=Vec3d.ofBottomCenter(base);
        // Centre the same25-ticket lease on the complete floor/actor footprint [-8,+42], not its left edge.
        chunks=new AiFixtureChunks(world,base.add(16,0,0),2);infantry=ModEntities.SHAPES_INFANTRY.create(world);
        scout=observer?ModEntities.SHAPES_INFANTRY.create(world):null;victim=EntityType.COW.create(world);
        try{
            c.assertTrue(infantry!=null&&victim!=null&&(!observer||scout!=null),"real registered Shapes fixture actors exist");
            for(int x=-8;x<=42;x++)for(int z=-10;z<=10;z++)block(base.add(x,-1,z),Blocks.BEDROCK);
            infantry.setPosition(start);infantry.setPersistent();
            // Keep a real long firing lane after the scout enclosure:3.75m/tick LASER rounds must survive
            // normal integration long enough for the unchanged two-position movement observer.
            victim.setPosition(start.add(observer?22:squad?10:36,0,0));victim.setPersistent();victim.setAiDisabled(true);
            // A durable real victim preserves the observation window; this is not a new balance statistic.
            victim.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4096);victim.setHealth(4096);
            victim.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
            if(squad){for(int y=0;y<=4;y++)for(int z=-2;z<=2;z++)block(base.add(4,y,z),Blocks.BEDROCK);}
            if(scout!=null){scout.setPosition(start.add(0,0,-7));scout.setPersistent();}
            if(Boolean.getBoolean("drmd.test.shapesTrace"))ShapesCombatTrace.attach(this);
        }catch(Throwable failure){close();throw failure;}
    }
    void block(BlockPos at,Block block){originals.putIfAbsent(at,world.getBlockState(at));world.setBlockState(at,block.getDefaultState(),2);}
    boolean admit(){
        if(spawned)return true;
        if(!chunks.entityTicking(infantry.getBlockPos(),victim.getBlockPos(),scout==null?infantry.getBlockPos():scout.getBlockPos())){
            c.assertTrue(++admission<80,"Shapes fixture obtains real entity-ticking terrain: "+chunks.readiness(infantry.getBlockPos(),victim.getBlockPos()));return false;
        }
        c.assertTrue(world.spawnEntity(victim)&&world.spawnEntity(infantry)&&(scout==null||world.spawnEntity(scout)),"actual registered actors are spawned normally");
        spawned=true;return false;
    }
    void sample(){
        ticks++;ownTarget|=infantry.getTarget()==victim&&infantry.canSee(victim);
        var mail=DroneSquadCoordinator.message(infantry);
        if(mail.isPresent()&&scout!=null&&mail.get().scout().equals(scout.getUuid())&&mail.get().target().equals(victim.getUuid())){
            if(firstMailPos==null)firstMailPos=infantry.getPos();
            if(infantry.getTarget()==null&&!infantry.canSee(victim)&&infantry.getPos().squaredDistanceTo(firstMailPos)>.25)preOwnInvestigation=true;
        }
        attributedDamage|=victim.getHealth()<4096&&victim.getAttacker()==infantry;
        for(var entity:world.iterateEntities())if(entity instanceof ProjectileEntity shot&&shot.getOwnerLiving()==infantry){
            shots.add(shot.getUuid());var old=firstShotPos.putIfAbsent(shot.getUuid(),shot.getPos());
            if(old!=null&&shot.getPos().squaredDistanceTo(old)>.01&&shot.getVelocity().lengthSquared()>0)movingShot=true;
        }
        if(Boolean.getBoolean("drmd.test.shapesTrace"))ShapesCombatTrace.sample(this);
    }
    String state(){return "ticks="+ticks+" pos="+infantry.getPos()+" age="+infantry.age+" target="+(infantry.getTarget()==null?"none":infantry.getTarget().getUuid())
        +" shots="+shots.size()+" moving="+movingShot+" hurt="+attributedDamage+" energy="+infantry.getWeaponEnergy()+" own="+ownTarget+" investigation="+preOwnInvestigation;}
    void drive(Consumer<ShapesFixture> action){c.runAtEveryTick(()->{
        if(closed)return;try{world.resetIdleTimeout();if(!admit())return;sample();action.accept(this);}catch(Throwable failure){close();throw failure;}
    });}
    void complete(){close();c.complete();}
    public void close(){
        if(closed)return;closed=true;
        if(Boolean.getBoolean("drmd.test.shapesTrace"))ShapesCombatTrace.close(this);
        var owners=new HashSet<UUID>();if(infantry!=null)owners.add(infantry.getUuid());if(scout!=null)owners.add(scout.getUuid());
        for(var e:java.util.stream.StreamSupport.stream(world.iterateEntities().spliterator(),false).toList())
            if(e instanceof ProjectileEntity shot&&shot.getOwnerLiving()!=null&&owners.contains(shot.getOwnerLiving().getUuid()))shot.discard();
        if(infantry!=null)infantry.discard();if(scout!=null)scout.discard();if(victim!=null)victim.discard();for(var e:extra)e.discard();
        for(var entry:originals.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);chunks.close();
    }
}
