package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.model.*;
import com.terminaldetector.drmd.physics.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual deaths must retain their authored mesh identity/scale and a reloadable compound rig. */
public final class DroneWreckKindGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="orb-wreck-kind")
    public void orbKeepsItsOwnPhysicalHullAndSavedIdentity(TestContext c){check(c,FlightEnemyKind.ORB,1,1);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="rocket-wreck-kind")
    public void rocketKeepsItsBodyAndFourFins(TestContext c){check(c,FlightEnemyKind.ROCKET,1,5);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="jet-wreck-kind")
    public void jetKeepsItsCoreAndTwoHullPanels(TestContext c){check(c,FlightEnemyKind.JET,1,3);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="jet-wreck-scale")
    public void scaledJetDoesNotBecomeATinyScoutAfterDeathOrReload(TestContext c){check(c,FlightEnemyKind.JET,2,3);}
    private static void check(TestContext c,FlightEnemyKind kind,float scale,int parts){
        var w=c.getWorld();var profile=PhysicsProfileState.get(w);var old=profile.profile();profile.set(PhysicsProfile.EXPANDED);
        var d=kind.create(w);var start=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(7,9,7)));var area=new Box(start,start).expand(10);var before=new HashSet<UUID>();for(var e:w.iterateEntities())if(area.contains(e.getPos()))before.add(e.getUuid());
        try{
            d.getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(scale);d.calculateDimensions();d.refreshPositionAndAngles(start.x,start.y,start.z,25,-35);d.setFlightRoll(60);d.setAiDisabled(true);
            var centre=d.getBoundingBox().getCenter();var rotation=FlightHullPose.physicalRotation(d.getYaw(),d.getPitch(),d.getFlightRoll());
            c.assertTrue(w.spawnEntity(d)&&d.damage(w.getDamageSources().genericKill(),Float.MAX_VALUE),"actual scaled authored enemy death");
            var found=w.getEntitiesByClass(RagdollEntity.class,area,e->!before.contains(e.getUuid()));c.assertTrue(found.size()==1,"one physical replacement");var wreck=found.getFirst();var tag=new NbtCompound();c.assertTrue(wreck.saveSelfNbt(tag),"real serializable wreck");
            c.assertTrue(tag.getString("flightHull").equals(kind.id)&&tag.getFloat("hullScale")==scale,"wreck retains actual authored identity and scale: "+kind);
            c.assertTrue(wreck.rig().segments().size()==parts,"bounded compound rig for "+kind);
            for(var face:FlightEnemyMesh.faces(kind))for(var vertex:face.vertices()){
                var p=rotation.rotate(new Vec3(vertex.x()*scale,vertex.y()*scale,vertex.z()*scale));var world=centre.add(p.x(),p.y(),p.z());
                c.assertTrue(wreck.partBounds().stream().anyMatch(box->box.expand(1e-5).contains(world)),"every original visible hull vertex belongs to physical replacement: "+kind);
            }
            var copy=(RagdollEntity)EntityType.getEntityFromNbt(tag,w).orElseThrow();var saved=new NbtCompound();copy.writeNbt(saved);
            c.assertTrue(saved.getString("flightHull").equals(kind.id)&&saved.getFloat("hullScale")==scale&&copy.rig().segments().size()==parts,"NBT factory restores authored rig before component state");
            for(int i=0;i<parts;i++){
                var a=wreck.simulation().parts().get(i);var b=copy.simulation().parts().get(i);
                c.assertTrue(a.segment().equals(b.segment())&&a.body().position().minus(b.body().position()).length()<1e-8&&a.body().linearVelocity().minus(b.body().linearVelocity()).length()<1e-8&&a.body().angularMomentum().minus(b.body().angularMomentum()).length()<1e-8,"scaled shape/mass and actual saved part state round trip");
            }
            c.complete();
        }finally{d.discard();for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(area.contains(e.getPos())&&!before.contains(e.getUuid()))e.discard();profile.set(old);}
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-declined-wreck-visible",tickLimit=100)
    public void declinedPhysicalWreckKeepsTheRealNativeDeathVisibleAcrossReloadAndProfileChange(TestContext c){deathChoice(c,false,false,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-accepted-wreck-reload",tickLimit=100)
    public void acceptedPhysicalWreckOwnsVisibilityOnceAcrossRealDeadEntityReload(TestContext c){deathChoice(c,true,false,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-authored-invisible-death",tickLimit=100)
    public void declinedPhysicalWreckDoesNotClearAuthoredInvisibility(TestContext c){deathChoice(c,false,true,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-legacy-visible-death",tickLimit=100)
    public void legacyDeadSavePreservesNativeVisibleDeathWithoutInventingAReplacement(TestContext c){deathChoice(c,false,false,true);}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-legacy-hidden-death",tickLimit=100)
    public void legacyDeadSavePreservesItsOriginalAuthoredInvisibleFlag(TestContext c){deathChoice(c,false,true,true);}

    /** Real lethal damage, real allocator policy and ordinary registered entity ticks. */
    private static void deathChoice(TestContext c,boolean replacement,boolean authoredInvisible,boolean legacy){
        var f=new RagdollLifecycleFixture(c,3,0,0);var profile=PhysicsProfileState.get(f.world);var old=profile.profile();
        profile.set(replacement?PhysicsProfile.EXPANDED:PhysicsProfile.STANDARD);f.cleanup.add(()->profile.set(old));
        var drone=ModEntities.DRONE.create(f.world);c.assertTrue(drone!=null,"actual original drone factory");
        drone.setPosition(Vec3d.ofBottomCenter(f.floor.add(0,3,0)));drone.setAiDisabled(true);drone.setInvisible(authoredInvisible);f.own(drone);
        var before=f.world.getEntitiesByClass(RagdollEntity.class,f.area,e->true).stream().map(net.minecraft.entity.Entity::getUuid).collect(java.util.stream.Collectors.toSet());
        int[] phase={0},born={0};long[] time={0};NbtCompound[] saved={null};DroneEntity[] restored={null};
        f.drive(80,()->{
            if(phase[0]==0){if(!f.chunks.entityTicking(drone.getBlockPos()))return;c.assertTrue(f.world.spawnEntity(drone),"actual original native actor registered");phase[0]=1;return;}
            if(phase[0]==1){
                if(drone.age<2)return;
                if(authoredInvisible)drone.setInvisible(true); // disclosed authored flag at the actual death boundary
                c.assertTrue(drone.damage(f.world.getDamageSources().genericKill(),Float.MAX_VALUE)&&!drone.isAlive(),"actual native lethal-damage death event");
                var wrecks=f.world.getEntitiesByClass(RagdollEntity.class,f.area,e->!before.contains(e.getUuid()));wrecks.forEach(f::own);
                c.assertTrue(wrecks.size()==(replacement?1:0),"real allocator outcome is explicit, not inferred from death: "+wrecks.size());
                c.assertTrue(drone.isInvisible()==(replacement||authoredInvisible),"only a successful replacement may hide a previously visible native death");
                saved[0]=new NbtCompound();c.assertTrue(drone.saveSelfNbt(saved[0]),"real dead drone serializes its native entity identity");
                c.assertTrue(saved[0].getBoolean("wreckReplaced")==replacement,"saved replacement ownership matches the actual allocation outcome");
                if(legacy)saved[0].remove("wreckReplaced");
                drone.discard();phase[0]=2;return;
            }
            if(phase[0]==2){
                if(f.world.getEntity(drone.getUuid())!=null)return;
                // A later Expanded selection cannot turn a previously declined/legacy death into
                // a second allocation. The actual replacement, when present, stays independent.
                profile.set(PhysicsProfile.EXPANDED);
                restored[0]=(DroneEntity)EntityType.getEntityFromNbt(saved[0],f.world).orElseThrow();f.own(restored[0]);
                c.assertTrue(restored[0].getUuid().equals(drone.getUuid())&&!restored[0].isAlive(),"real factory reload preserves the dead entity identity");
                c.assertTrue(f.world.spawnEntity(restored[0]),"reopened dead native entity registered after original removal");
                born[0]=restored[0].age;time[0]=f.world.getTime();phase[0]=3;return;
            }
            var copy=restored[0];if(copy.age-born[0]<4)return;f.assertTicks(copy,born[0],time[0],4);
            c.assertTrue(copy.isInvisible()==(replacement||authoredInvisible),"ordinary native death ticks preserve the correct visible or authored hidden fallback");
            var wrecks=f.world.getEntitiesByClass(RagdollEntity.class,f.area,e->!before.contains(e.getUuid()));
            c.assertTrue(wrecks.size()==(replacement?1:0),"reload/profile changes cannot retry a completed death choice or duplicate the physical body");
            System.out.println("DRONE_DEATH_CHOICE physical="+replacement+" authoredInvisible="+authoredInvisible+" legacy="+legacy+" nativeInvisible="+copy.isInvisible()+" actualWrecks="+wrecks.size()+" ordinaryTicks="+(copy.age-born[0]));f.complete();
        });
    }

}
