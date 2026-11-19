package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.ragdoll.RagdollDebrisGroups;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class RagdollLifecycleGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-articulated-rest",tickLimit=220)
    public void actualLimitedArticulatedStateSleepsInFiniteWindowsAndGroupImpactWakesWithoutExtraImpulse(TestContext c){
        var f=new RagdollLifecycleFixture(c,12,0,0);var group=UUID.randomUUID();var a=f.supportedJet(0,group);var b=f.supportedJet(12,group);
        int[] stage={0},age={0};long[] time={0},steps={0},skips={0};List<com.terminaldetector.drmd.d6.D6PhysicsBody.Snapshot>[] saved=new List[]{null};
        f.drive(200,()->{
            if(stage[0]==0){if(!f.chunks.entityTicking(a.getBlockPos(),b.getBlockPos()))return;c.assertTrue(f.world.spawnEntity(a)&&f.world.spawnEntity(b),"actual registered three-part limited JET rigs");stage[0]=1;return;}
            if(stage[0]==1){if(!a.isPhysicsSleeping()||!b.isPhysicsSleeping()||a.skippedPhysicsTicks()==0)return;age[0]=a.age;time[0]=f.world.getTime();steps[0]=a.simulatedPhysicsTicks();skips[0]=a.skippedPhysicsTicks();saved[0]=a.simulation().parts().stream().map(p->p.body().snapshot()).toList();stage[0]=2;return;}
            if(stage[0]==2){if(a.age-age[0]<4)return;f.assertTicks(a,age[0],time[0],4);
                c.assertTrue(a.simulatedPhysicsTicks()==steps[0]&&a.skippedPhysicsTicks()>=skips[0]+4,"actual articulated solver skipped four ticks");
                for(int i=0;i<saved[0].size();i++)c.assertTrue(a.simulation().parts().get(i).body().matches(saved[0].get(i)),"sleep preserves every p/q/v/L/force/torque and integration step exactly");
                var grouped=RagdollDebrisGroups.get(f.world).snapshot(group).orElseThrow();c.assertTrue(grouped.members().size()==2&&grouped.parts()==6&&grouped.mass()==28,"logical aggregation retains two UUIDs, six parts and all mass");
                var other=b.simulation().parts().stream().map(p->p.body().snapshot()).toList();a.applyPhysicsImpulse(a.getPos(),new Vec3d(1,0,0));
                c.assertTrue(!a.isPhysicsSleeping()&&!b.isPhysicsSleeping(),"impact wakes complete causal group immediately");
                for(int i=0;i<other.size();i++)c.assertTrue(b.simulation().parts().get(i).body().matches(other.get(i)),"group wake does not duplicate the impact onto other parts/entities");
                age[0]=a.age;time[0]=f.world.getTime();stage[0]=3;return;
            }
            if(a.age==age[0])return;f.assertTicks(a,age[0],time[0],1);c.assertTrue(a.simulatedPhysicsTicks()>steps[0],"impacted rig resumes actual solver");f.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-support-interest-wake",tickLimit=240)
    public void actualPlayerInterestAndRemovedSupportWakeAnArticulatedCorpse(TestContext c){
        var f=new RagdollLifecycleFixture(c,0,0,128);var body=f.supportedJet(0,UUID.randomUUID());
        int[] stage={0},age={0};long[] time={0},steps={0};double[] y={0};net.minecraft.server.network.ServerPlayerEntity[] player={null};
        f.drive(220,()->{
            if(stage[0]==0){if(!f.chunks.entityTicking(body.getBlockPos()))return;c.assertTrue(f.world.spawnEntity(body),"supported rig registered");stage[0]=1;return;}
            if(stage[0]==1){if(!body.isPhysicsSleeping())return;player[0]=c.createMockCreativeServerPlayerInWorld();f.own(player[0]);player[0].setPosition(body.getPos().add(4,0,0));player[0].setNoGravity(true);age[0]=body.age;time[0]=f.world.getTime();steps[0]=body.simulatedPhysicsTicks();stage[0]=2;return;}
            if(stage[0]==2){if(body.age==age[0])return;f.assertTicks(body,age[0],time[0],1);c.assertTrue(!body.isPhysicsSleeping()&&body.simulatedPhysicsTicks()>steps[0],"nearby actual player keeps full solver");player[0].discard();stage[0]=3;return;}
            if(stage[0]==3){if(!body.isPhysicsSleeping())return;y[0]=body.getY();age[0]=body.age;time[0]=f.world.getTime();steps[0]=body.simulatedPhysicsTicks();
                for(var p:BlockPos.iterate(f.floor.add(-2,0,-2),f.floor.add(2,0,2)))f.put(p,Blocks.AIR.getDefaultState());stage[0]=4;return;}
            if(body.age==age[0])return;f.assertTicks(body,age[0],time[0],1);c.assertTrue(!body.isPhysicsSleeping()&&body.simulatedPhysicsTicks()>steps[0]&&body.getY()<y[0],"removed real support resumes gravity/contact solver without hand ticks");f.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-128-causal-cleanup",tickLimit=1450)
    public void one128DeathWindowKeepsContentAndCausalOwnershipThenCleansAllPhysicalCorpses(TestContext c){
        var f=new RagdollLifecycleFixture(c,45,21,256);var drones=new ArrayList<DroneEntity>();var bodies=new ArrayList<RagdollEntity>();var loot=new TreeMap<String,Integer>();
        for(int i=0;i<128;i++){var d=(i%2==0?ModEntities.DRONE:ModEntities.JET).create(f.world);c.assertTrue(d!=null,"original species factory");d.setPosition(Vec3d.ofBottomCenter(f.floor.add(i%16*3,3,i/16*3)));d.setAiDisabled(true);d.setHealth(1);drones.add(d);f.own(d);}
        int[] stage={0};long[] died={0};UUID[] group={null};
        f.drive(1400,()->{
            if(stage[0]==0){if(!f.chunks.entityTicking(drones.stream().map(Entity::getBlockPos).toArray(BlockPos[]::new)))return;for(var d:drones)c.assertTrue(f.world.spawnEntity(d),"128 real admission, no cap increase or batching");stage[0]=1;return;}
            if(stage[0]==1){if(drones.stream().anyMatch(d->d.age<2))return;died[0]=f.world.getTime();var damage=f.world.getDamageSources().genericKill();
                for(var d:drones)c.assertTrue(d.damage(damage,Float.MAX_VALUE),"128 actual deaths share one ordinary world tick");
                bodies.addAll(f.world.getEntitiesByClass(RagdollEntity.class,f.area,Entity::isAlive));bodies.forEach(f::own);
                for(var item:f.world.getEntitiesByClass(ItemEntity.class,f.area,e->true)){loot.merge(net.minecraft.registry.Registries.ITEM.getId(item.getStack().getItem()).toString(),item.getStack().getCount(),Integer::sum);f.own(item);}
                c.assertTrue(bodies.size()==128&&bodies.stream().map(Entity::getUuid).distinct().count()==128,"exact128 physical identities");group[0]=bodies.getFirst().debrisGroupId();
                c.assertTrue(group[0]!=null&&bodies.stream().allMatch(b->group[0].equals(b.debrisGroupId())),"same actual damage event is one causal group");
                var aggregate=RagdollDebrisGroups.get(f.world).snapshot(group[0]).orElseThrow();
                c.assertTrue(aggregate.parts()==512&&aggregate.members().size()==128&&Math.abs(aggregate.mass()-bodies.stream().mapToDouble(RagdollEntity::physicsMass).sum())<1e-8,"aggregation keeps exact mixed64scout/64JET physical content");stage[0]=2;return;}
            for(var b:bodies)if(!b.isRemoved())c.assertTrue(b.simulatedPhysicsTicks()+b.skippedPhysicsTicks()==b.physicalLifeTicks(),"every real active/sleep tick accounted without TTL reset");
            if(bodies.stream().anyMatch(b->!b.isRemoved()||f.world.getEntity(b.getUuid())!=null))return;
            c.assertTrue(f.world.getTime()-died[0]>=1200,"actual ordinary lifetime elapsed");
            for(var b:bodies)c.assertTrue(b.simulatedPhysicsTicks()+b.skippedPhysicsTicks()==1200,"each corpse used its finite original1200tick lifetime");
            long skipped=bodies.stream().mapToLong(RagdollEntity::skippedPhysicsTicks).sum();
            if(skipped==0)System.out.println("RAGDOLL128_REST_FINAL "+bodies.stream().map(b->b.getUuid()+":"+(b.flightHullKind()==null?"scout":b.flightHullKind().id)+":"+b.restObservation()).toList());
            c.assertTrue(skipped>0,"natural128 cohort exercises some certified sleeping, not merely TTL");
            c.assertTrue(RagdollDebrisGroups.get(f.world).snapshot(group[0]).isEmpty()&&f.world.getEntitiesByClass(RagdollEntity.class,f.area,e->true).isEmpty(),"last physical removal clears logical ownership too");
            var remaining=new TreeMap<String,Integer>();for(var item:f.world.getEntitiesByClass(ItemEntity.class,f.area,e->true)){remaining.merge(net.minecraft.registry.Registries.ITEM.getId(item.getStack().getItem()).toString(),item.getStack().getCount(),Integer::sum);f.own(item);}
            c.assertTrue(loot.equals(remaining),"corpse cleanup preserves ordinary loot content, including legitimate Vanilla stack merging");
            System.out.println("RAGDOLL128_ACTUAL deaths=128 parts="+bodies.stream().mapToInt(b->b.simulation().parts().size()).sum()+" fullTicks="+bodies.stream().mapToLong(RagdollEntity::simulatedPhysicsTicks).sum()+" sleepTicks="+bodies.stream().mapToLong(RagdollEntity::skippedPhysicsTicks).sum()+" worldTicks="+(f.world.getTime()-died[0])+" drops="+loot.size());f.complete();
        });
    }
}
