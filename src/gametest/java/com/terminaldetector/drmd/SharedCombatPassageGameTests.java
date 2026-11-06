package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.test.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Six real combat goals share one aperture; no supplied route, velocity or manual entity ticks. */
public final class SharedCombatPassageGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shared-combat-wall",tickLimit=700)
    public void sixOrbsShareAnOffsetWallOpening(TestContext c){check(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shared-combat-ceiling",tickLimit=700)
    public void sixOrbsShareAnOffsetCeilingOpening(TestContext c){check(c,true);}
    private static Vec3d point(BlockPos b,boolean vertical,double forward,double lateral,double z){return Vec3d.of(b).add(vertical?lateral:forward,vertical?forward:lateral,z);}
    private static void check(TestContext c,boolean vertical){
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(vertical?19000:18000,100,1000));var lease=new AiFixtureChunks(w,base,3);
        var wall=new ArrayList<BlockPos>();for(int a=-20;a<=20;a++)for(int z=-20;z<=20;z++){
            if(a>=4&&a<6&&z>=0&&z<2)continue;var p=base.add(vertical?a:6,vertical?6:a,z);wall.add(p);w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);
        }
        var drones=new ArrayList<FlightEnemyEntity>();var targets=new ArrayList<net.minecraft.entity.passive.CowEntity>();
        for(int i=0;i<6;i++){
            var d=FlightEnemyKind.ORB.create(w);d.setPosition(point(base,vertical,.77-(i%2)*1.25,.37+(i/2)*1.3,.69+(i%2)*1.4));d.setPersistent();d.setInvulnerable(true);d.setAiDisabled(true);drones.add(d);
            var t=EntityType.COW.create(w);t.setPosition(point(base,vertical,18.77,.37+(i/2)*2.5,.69+((i%2)-.5)*5));t.setAiDisabled(true);t.setNoGravity(true);t.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);t.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);targets.add(t);
        }
        var ids=new HashSet<UUID>();drones.forEach(d->ids.add(d.getUuid()));var lanes=new HashSet<UUID>();var hits=new HashSet<UUID>();int[] ticks={0},admission={0};int[][] merged=new int[6][6];int[] worstMerge={0};boolean[] spawned={false},started={false},finished={false};
        Runnable cleanup=()->{for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()!=null&&ids.contains(p.getOwnerLiving().getUuid()))e.discard();drones.forEach(d->d.discard());targets.forEach(t->t.discard());wall.forEach(p->w.setBlockState(p,Blocks.AIR.getDefaultState(),3));lease.close();DroneSwarmAi.clear();};
        c.runAtEveryTick(()->{
            if(finished[0])return;w.resetIdleTimeout();
            if(!started[0]){
                if(++admission[0]>=80){finished[0]=true;cleanup.run();c.assertTrue(false,"six combat actors admitted");return;}
                if(!spawned[0]){for(var d:drones)if(!lease.entityTicking(d.getBlockPos()))return;for(var t:targets)if(!lease.entityTicking(t.getBlockPos()))return;for(int i=0;i<6;i++){c.assertTrue(w.spawnEntity(drones.get(i))&&w.spawnEntity(targets.get(i)),"registered shared-route actors");drones.get(i).setTarget(targets.get(i));}spawned[0]=true;return;}
                if(drones.stream().anyMatch(d->d.age<5)||targets.stream().anyMatch(t->t.age<5))return;DroneSwarmAi.clear();drones.forEach(d->d.setAiDisabled(false));started[0]=true;return;
            }
            ticks[0]++;
            for(int i=0;i<6;i++){
                var d=drones.get(i);var from=d.getBoundingBox().getCenter();var currentTarget=d.getTarget();var to=currentTarget==null?from:currentTarget.getBoundingBox().getCenter();double df=vertical?to.y-from.y:to.x-from.x;
                if(Math.abs(df)>1e-8){double t=((vertical?base.getY():base.getX())+6.5-(vertical?from.y:from.x))/df;var at=from.lerp(to,t);double lateral=vertical?at.x-base.getX():at.y-base.getY();
                    if(t>=0&&t<=1&&lateral>=4&&lateral<=6&&at.z>=base.getZ()&&at.z<=base.getZ()+2&&w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,d)).getType()==HitResult.Type.MISS)lanes.add(d.getUuid());
                }
                for(int j=0;j<i;j++){merged[i][j]=d.getPos().squaredDistanceTo(drones.get(j).getPos())<.04?merged[i][j]+1:0;worstMerge[0]=Math.max(worstMerge[0],merged[i][j]);}
            }
            for(var t:targets)if(t.getHealth()<1000&&t.getAttacker()!=null&&ids.contains(t.getAttacker().getUuid()))hits.add(t.getAttacker().getUuid());
            // Training targets remain available to all six actors after damage has been observed.
            targets.forEach(t->{if(t.isAlive())t.setHealth(t.getMaxHealth());});
            boolean alive=drones.stream().allMatch(d->d.isAlive())&&targets.stream().allMatch(t->t.isAlive());boolean success=lanes.size()==6&&hits.size()==6&&worstMerge[0]<20;
            if(alive&&!success&&ticks[0]<580&&worstMerge[0]<20)return;
            finished[0]=true;boolean intact=wall.stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK));String detail="targetPositions="+targets.stream().map(t->t.getPos().subtract(Vec3d.of(base))).toList()+" targetHealth="+targets.stream().map(t->t.getHealth()).toList()+" targets="+drones.stream().map(d->d.getTarget()==null?"none":d.getTarget().getUuid().toString()).toList()+" vertical="+vertical+" tick="+ticks[0]+" lanes="+lanes.size()+" damagingActors="+hits.size()+" coincidentTicks="+worstMerge[0]+" positions="+drones.stream().map(d->d.getPos().subtract(Vec3d.of(base))).toList();cleanup.run();System.out.println("SHARED_COMBAT_RESULT "+detail);c.assertTrue(success&&alive&&intact,"shared aperture vertical="+vertical+" ticks="+ticks[0]+" lanes="+lanes.size()+" hits="+hits.size()+" coincident="+worstMerge[0]);c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flight-cover-memory",tickLimit=700)
    public void knownTargetSurvivesADetourButIsEventuallyForgotten(TestContext c){
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(22000,120,1000));var lease=new AiFixtureChunks(w,base,3);var drone=FlightEnemyKind.ORB.create(w);var target=EntityType.COW.create(w);
        drone.setPosition(Vec3d.ofBottomCenter(base));drone.setAiDisabled(true);drone.setPersistent();drone.setInvulnerable(true);target.setPosition(Vec3d.ofBottomCenter(base.add(18,0,0)));target.setAiDisabled(true);target.setNoGravity(true);target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(1000);
        var cover=new ArrayList<BlockPos>();int[] ticks={0},admission={0},acquisition={0};boolean[] spawned={false},started={false},closed={false},done={false};
        Runnable cleanup=()->{for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()==drone)e.discard();drone.discard();target.discard();cover.forEach(p->w.setBlockState(p,Blocks.AIR.getDefaultState(),3));lease.close();DroneSwarmAi.clear();};
        c.runAtEveryTick(()->{
            if(done[0])return;w.resetIdleTimeout();
            if(!spawned[0]){if(++admission[0]>=80){done[0]=true;cleanup.run();c.assertTrue(false,"memory fixture admitted");return;}if(!lease.entityTicking(drone.getBlockPos(),target.getBlockPos()))return;c.assertTrue(w.spawnEntity(drone)&&w.spawnEntity(target),"memory actors spawned");spawned[0]=true;return;}
            if(!started[0]){if(drone.age<5||target.age<5)return;drone.setAiDisabled(false);started[0]=true;}
            if(!closed[0]){
                if(++acquisition[0]>160){done[0]=true;cleanup.run();c.assertTrue(false,"natural target acquired before enclosure");return;}
                if(drone.getTarget()!=target)return;
                var middle=target.getBlockPos();for(int x=-2;x<=2;x++)for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||y==-2||y==3||Math.abs(z)==2){var p=middle.add(x,y,z);cover.add(p);w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);}closed[0]=true;return;
            }
            ticks[0]++;
            if(ticks[0]==100&&drone.getTarget()!=target){done[0]=true;cleanup.run();c.assertTrue(false,"known hidden target retained while recovering a route");return;}
            if(ticks[0]<300)return;done[0]=true;boolean forgotten=drone.getTarget()==null;boolean intact=cover.stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK));cleanup.run();c.assertTrue(forgotten&&intact,"hidden-target memory remains finite without breaking its enclosure");c.complete();
        });
    }

}
