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
}
