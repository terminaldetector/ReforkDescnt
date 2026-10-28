package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Server geometry/impulse integration; these are not player-boarding acceptance tests. */
public final class BodyContactGameTests implements FabricGameTest {
    private BlockBodyEntity floor(TestContext c) {
        var p=c.getAbsolutePos(new BlockPos(8,140,8));var w=c.getWorld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((p.getX()>>4)+x,(p.getZ()>>4)+z);
        for(var b:BlockPos.iterate(p.add(-3,-3,-3),p.add(7,5,5)))w.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        for(int x=0;x<4;x++)w.setBlockState(p.east(x),Blocks.IRON_BLOCK.getDefaultState(),2);
        return BlockBodyEntity.assemble(w,p,p.east(3));
    }
    private static Vec3 pure(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-query-pose")
    public void worldContactQueryTracksExistingBodyRotationAndDoesNotMutateIt(TestContext c) {
        var hull=floor(c);
        try {
            var rotation=new Quat(.2,.3,.4,.7).normalized();hull.physics().withRotation(rotation);
            var centre=pure(hull.localToWorld(new Vec3d(2.5,1.89,.5)));
            var actor=new OrientedBox(centre,new Vec3(.2,.9,.2),rotation);
            var before=new net.minecraft.nbt.NbtCompound();hull.writeNbt(before);
            var contacts=hull.collisionQuery().contacts(actor,0);
            c.assertTrue(contacts.size()==1,"one merged floor contact, not four block rigid bodies");
            c.assertTrue(Math.abs(contacts.getFirst().penetration()-.01)<1e-8,"actual OBB penetration");
            c.assertTrue(contacts.getFirst().normal().minus(rotation.rotate(new Vec3(0,1,0))).length()<1e-8,"world normal rotates with hull");
            var after=new net.minecraft.nbt.NbtCompound();hull.writeNbt(after);
            c.assertTrue(before.equals(after),"read-only query does not change pose, velocity or persistent state");
        } finally {hull.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-query-ccd")
    public void sweptContactUsesBothParticipantsMotion(TestContext c) {
        var hull=floor(c);
        try {
            var actor=new OrientedBox(pure(hull.localToWorld(new Vec3d(2,4,.5))),new Vec3(.2,.5,.2),Quat.IDENTITY);
            var hit=hull.collisionQuery().sweep(actor,new Vec3(0,-1,0),new Vec3(0,4,0)).orElseThrow();
            c.assertTrue(Math.abs(hit.fraction()-.5)<1e-8,"relative CCD prevents a rising platform tunnelling through an actor");
            c.assertTrue(hull.collisionQuery().sweep(actor,new Vec3(0,-1,0),new Vec3(0,-2,0)).isEmpty(),"departing floor does not weld the actor");
        } finally {hull.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-contact-reaction")
    public void passengerConstraintExertsReactionOnTheExistingHullPhysics(TestContext c) {
        var hull=floor(c);
        try {
            hull.physics().withPosition(pure(hull.getPos())).withLinearVelocity(new Vec3(2,0,0));
            var point=pure(hull.localToWorld(new Vec3d(2,1,.5)));
            var actor=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1,1,1))
                .withPosition(point.plus(new Vec3(0,.9,0))).withLinearVelocity(new Vec3(0,-2,0));
            Vec3 momentum=hull.physics().linearVelocity().scaled(hull.physicsMass()).plus(actor.linearVelocity());
            var constraint=new BodyContactConstraint(actor,hull.physics(),point,new Vec3(0,1,0),.65);
            for(int i=0;i<32;i++)constraint.solve();
            Vec3 after=hull.physics().linearVelocity().scaled(hull.physicsMass()).plus(actor.linearVelocity());
            c.assertTrue(after.minus(momentum).length()<1e-8,"hull receives equal and opposite contact momentum");
            c.assertTrue(hull.physics().linearVelocity().y()<0,"passenger weight pushes the hull");
            c.assertTrue(actor.linearVelocity().x()>0 && actor.linearVelocity().x()<2,"friction accelerates rather than snaps the passenger");
            c.assertTrue(Math.abs(constraint.relativeVelocity().y())<1e-7,"contact velocity uses both participants");
        } finally {hull.discard();}c.complete();
    }
}
