package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Natural scout/wingman behavior, before adding the missing squad-task layer.
 * No target assignment, mailbox injection, manual entity tick or following teleport. */
public final class DroneSquadTaskGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;
        final AiFixtureChunks chunks;
        final DroneEntity scout,wingman;
        final CowEntity victim;
        final Vec3d start;
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final boolean withScout;
        boolean spawned,closed;
        int admission,ticks;
        boolean scoutAcquired,scoutSaw,preAcquisitionClimb,ownAcquisition,ownShot;
        double maxClimb;
        Rig(TestContext c,boolean withScout,int offset){
            this.c=c;this.withScout=withScout;var world=c.getWorld();
            var base=c.getAbsolutePos(new BlockPos(86000+offset,170,4000));start=Vec3d.ofBottomCenter(base);
            chunks=new AiFixtureChunks(world,base,2);
            wingman=ModEntities.ORB.create(world);scout=withScout?ModEntities.ORB.create(world):null;
            victim=EntityType.COW.create(world);
            try{
                c.assertTrue(wingman!=null&&victim!=null&&(!withScout||scout!=null),"real registered actors are available");
                wingman.setPosition(start);wingman.setPersistent();wingman.setVelocity(Vec3d.ZERO);
                if(scout!=null){scout.setPosition(start.add(-2,10,0));scout.setPersistent();scout.setVelocity(Vec3d.ZERO);}
                victim.setPosition(start.add(24,12,0));victim.setPersistent();victim.setNoGravity(true);victim.setAiDisabled(true);
                // A real living victim survives the scout's normal fire long enough for
                // the communication assertion. Its pose is never rewritten after setup.
                victim.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4096);victim.setHealth(4096);
                victim.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
                for(int y=-3;y<=7;y++)for(int z=-4;z<=4;z++){
                    var pos=base.add(8,y,z);blocks.put(pos,world.getBlockState(pos));world.setBlockState(pos,Blocks.BEDROCK.getDefaultState(),2);
                }
                c.assertTrue(wingman.getRole()==com.terminaldetector.drmd.ai.AiRole.ASSAULT,"authored ORB role remains intact");
                if(scout!=null)c.assertTrue(HostileEnvironment.isAlly(scout)
                    &&!HostileEnvironment.isHostileTarget(wingman,scout)&&!HostileEnvironment.isHostileTarget(scout,wingman),"fixture allies satisfy the actual current allegiance predicates");
                c.assertTrue(HostileEnvironment.isHostileTarget(wingman,victim),"victim is a legitimate current enemy");
            }catch(Throwable failure){close();throw failure;}
        }
        boolean admitted(){
            if(spawned)return true;var world=c.getWorld();
            if(!chunks.entityTicking(wingman.getBlockPos(),victim.getBlockPos(),scout==null?wingman.getBlockPos():scout.getBlockPos())){
                if(++admission>=80)c.assertTrue(false,"squad fixture obtains real entity-ticking columns: "+chunks.readiness(wingman.getBlockPos(),victim.getBlockPos()));
                return false;
            }
            c.assertTrue(world.spawnEntity(victim)&&world.spawnEntity(wingman)&&(scout==null||world.spawnEntity(scout)),"actual scout/wingman/victim spawn");
            c.assertTrue(wingman.getTarget()==null&&!wingman.canSee(victim),"wingman initially has no target and real bedrock blocks its own LOS");
            if(scout!=null)c.assertTrue(scout.canSee(victim),"scout alone has actual unobstructed LOS over the wall");
            spawned=true;return false;
        }
        void sample(){
            ticks++;maxClimb=Math.max(maxClimb,wingman.getY()-start.y);
            if(scout!=null){scoutAcquired|=scout.getTarget()==victim;scoutSaw|=scout.getTarget()==victim&&scout.canSee(victim);}
            // Communication must move the wingman while its own target is still unknown.
            // A victim drifting into visibility or a vanilla lock cannot fake this proof.
            if(wingman.getTarget()==null&&!wingman.canSee(victim)&&wingman.getY()-start.y>1)preAcquisitionClimb=true;
            ownAcquisition|=wingman.getTarget()==victim&&wingman.canSee(victim);
            for(var entity:c.getWorld().iterateEntities())if(entity instanceof ProjectileEntity shot&&shot.getOwnerLiving()==wingman)ownShot=true;
        }
        String state(){return "ticks="+ticks+" scoutAcquired="+scoutAcquired+" scoutSaw="+scoutSaw+" preAcquisitionClimb="+preAcquisitionClimb
            +" ownAcquisition="+ownAcquisition+" ownShot="+ownShot+" climb="+maxClimb+" wingman="+wingman.getPos()+" wingmanAge="+wingman.age
            +" wingmanTarget="+(wingman.getTarget()==null?"none":wingman.getTarget().getUuid())+" victim="+victim.getPos()+" victimHealth="+victim.getHealth();}
        public void close(){
            if(closed)return;closed=true;
            var owned=new HashSet<UUID>();if(wingman!=null)owned.add(wingman.getUuid());if(scout!=null)owned.add(scout.getUuid());
            for(var entity:java.util.stream.StreamSupport.stream(c.getWorld().iterateEntities().spliterator(),false).toList())
                if(entity instanceof ProjectileEntity shot&&shot.getOwnerLiving()!=null&&owned.contains(shot.getOwnerLiving().getUuid()))entity.discard();
            if(wingman!=null)wingman.discard();if(scout!=null)scout.discard();if(victim!=null)victim.discard();
            for(var entry:blocks.entrySet())c.getWorld().setBlockState(entry.getKey(),entry.getValue(),2);
            chunks.close();
        }
    }
    private void check(TestContext c,boolean withScout,int offset){
        var rig=new Rig(c,withScout,offset);
        // Sole scheduler driver, registered at entry before task-map iteration.
        c.runAtEveryTick(()->{
            if(rig.closed)return;
            try{
                c.getWorld().resetIdleTimeout();if(!rig.admitted())return;rig.sample();
                if(withScout){
                    if(rig.ticks==60)c.assertTrue(rig.scoutAcquired&&rig.scoutSaw,"fixture scout naturally acquires and sees a real hostile without a target injection: "+rig.state());
                    if(rig.preAcquisitionClimb&&rig.ownAcquisition&&rig.ownShot){
                        System.out.println("SQUAD_NATURAL_PASS "+rig.state());rig.close();c.complete();return;
                    }
                    if(rig.ticks>=120){String detail=rig.state();rig.close();c.assertTrue(false,"missing scout-to-wingman investigation: real own-LOS scout must enable occluded wingman XYZ investigation then normal own acquisition/fire: "+detail);}
                }else if(rig.ticks>=80){
                    boolean valid=rig.wingman.getTarget()==null&&!rig.ownShot&&!rig.preAcquisitionClimb&&rig.wingman.getPos().squaredDistanceTo(rig.start)<.25;
                    String detail=rig.state();rig.close();c.assertTrue(valid,"no scout means no invented target, mailbox pursuit or physical shot: "+detail);c.complete();
                }
            }catch(Throwable failure){rig.close();throw failure;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-natural-scout",tickLimit=240)
    public void naturalScoutEnablesOccludedWingmanInvestigationAndOwnCombat(TestContext c){check(c,true,0);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="squad-no-scout",tickLimit=200)
    public void noScoutCannotInventAnUnseenEnemyOrInvestigation(TestContext c){check(c,false,512);}
}
