package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual lethal damage and registered replacement physics, before the first solver step. */
public final class DroneWreckPoseGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="orb-wreck-pose")
    public void destroyedOrbKeepsItsHeading(TestContext c){check(c,FlightEnemyKind.ORB,90,0,0);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="rocket-wreck-pose")
    public void verticallyFlyingRocketKeepsItsNoseAtDeath(TestContext c){check(c,FlightEnemyKind.ROCKET,0,-90,0);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="jet-wreck-pose")
    public void bankedJetDoesNotResetItsPhysicalWreckToWorldAxes(TestContext c){check(c,FlightEnemyKind.JET,35,40,75);}
    private static void check(TestContext c,FlightEnemyKind kind,float yaw,float pitch,float bank){
        var w=c.getWorld();var profile=PhysicsProfileState.get(w);var old=profile.profile();profile.set(PhysicsProfile.EXPANDED);
        var d=kind.create(w);var origin=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(7,9,7)));var area=new Box(origin,origin).expand(6);
        var before=new HashSet<UUID>();for(var e:w.iterateEntities())if(area.contains(e.getPos()))before.add(e.getUuid());
        try{
            d.refreshPositionAndAngles(origin.x,origin.y,origin.z,yaw,pitch);d.setFlightRoll(bank);d.setAiDisabled(true);d.setVelocity(.1,.2,-.1);
            var centre=d.getBoundingBox().getCenter();var forward=Vec3d.fromPolar(pitch,yaw).normalize();var up=FlightAttitude.upOf(forward,bank).normalize();var right=up.crossProduct(forward).normalize();
            c.assertTrue(w.spawnEntity(d),"real authored flight enemy spawned");
            c.assertTrue(d.damage(w.getDamageSources().genericKill(),Float.MAX_VALUE)&&!d.isAlive(),"actual lethal damage");
            var wrecks=w.getEntitiesByClass(RagdollEntity.class,area,e->!before.contains(e.getUuid()));c.assertTrue(wrecks.size()==1,"one physical replacement owns the destroyed hull");var wreck=wrecks.getFirst();
            for(var part:wreck.simulation().parts()){
                var local=part.segment().restPosition();var expected=centre.add(right.multiply(local.x())).add(up.multiply(local.y())).add(forward.multiply(local.z()));var actual=part.body().position();
                c.assertTrue(expected.squaredDistanceTo(actual.x(),actual.y(),actual.z())<1e-8,"death preserves world part pose: "+kind+"/"+part.segment().id()+" expected="+expected+" actual="+actual);
                var nose=part.body().rotation().rotate(new Vec3(0,0,1));var top=part.body().rotation().rotate(new Vec3(0,1,0));
                c.assertTrue(forward.dotProduct(new Vec3d(nose.x(),nose.y(),nose.z()))>1-1e-6&&up.dotProduct(new Vec3d(top.x(),top.y(),top.z()))>1-1e-6,"physical replacement keeps pitched/banked body axes: "+kind);
            }
            c.complete();
        }finally{
            d.discard();for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(area.contains(e.getPos())&&!before.contains(e.getUuid()))e.discard();profile.set(old);
        }
    }
}
