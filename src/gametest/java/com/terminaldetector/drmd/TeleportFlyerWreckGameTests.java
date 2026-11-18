package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.model.FlightHullPose;
import com.terminaldetector.drmd.weapon.core.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual engine death after a real blink, plus an explicitly coarse-box contact negative. */
public final class TeleportFlyerWreckGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-natural-lethal-wreck",tickLimit=360)
    public void anActualLethalProjectileAtTheAdmittedEndpointCreatesOneMatchingTickedReloadableCorpse(TestContext c){
        var rig=new TeleportFlyerFixture(c,3072,true);
        rig.actor.refreshPositionAndAngles(rig.start.x,rig.start.y,rig.start.z,25,-20);rig.actor.setFlightRoll(37); // Authored initial pose only, before actual spawn/AI.
        var owner=c.createMockCreativeServerPlayerInWorld();owner.setPosition(rig.start.add(-8,0,0));rig.extra.add(owner);
        boolean[] fired={false},saved={false};NbtCompound[] disk={null};RagdollEntity[] restored={null};long[] deathBlinks={0},restoredWorldTick={Long.MIN_VALUE};int[] restoredAge={-1};UUID[] savedIdentity={null};
        rig.drive(r->{
            var proof=r.record();
            if(!fired[0]){
                if(r.actor.teleportCount()==0){c.assertTrue(r.ticks<120,"natural actual blink before lethal projectile fixture: "+r.state());return;}
                c.assertTrue(proof.arrived!=null&&r.actor.getPos().squaredDistanceTo(proof.arrived)<1e-8,"lethal fixture begins at the actual admitted endpoint, no follow pose rewrite");
                deathBlinks[0]=r.actor.teleportCount();var cfg=new WeaponCore.FireConfig();cfg.owner=owner;cfg.pos=r.actor.getBoundingBox().getCenter().add(-4,0,0);cfg.dir=new Vec3d(1,0,0);cfg.speedIsSourceUnits=false;cfg.speed=2;cfg.directDamage=1000;cfg.dmgClass=DamageClass.KINETIC;cfg.life=2;
                var round=WeaponCore.fireProjectile(cfg);c.assertTrue(round!=null,"real moving lethal projectile emitted, no direct damage/rig factory call");r.extra.add(round);fired[0]=true;return;
            }
            if(proof.corpse==null){c.assertTrue(r.ticks<160,"actual projectile death and production replacement: "+r.state());return;}
            c.assertTrue(proof.death!=null&&proof.oneOwner&&!r.actor.isAlive()&&r.actor.isInvisible()&&r.actor.teleportCount()==deathBlinks[0],"one physical owner, no visible live duplicate or postmortem teleport");
            if(!saved[0]){
                var corpse=proof.corpse;c.assertTrue(proof.corpseAgeAtCapture==0,"END observer captures actual corpse before its first ordinary physics step");
                c.assertTrue(corpse.flightHullKind()==FlightEnemyKind.TELEPORT_FLYER&&corpse.rigVersion()==1&&corpse.rig().segments().size()==1
                    &&corpse.rig().segments().getFirst().id().equals("teleport_cage"),"matching own family, not ORB/null/scout or rigVersion0");
                var savedTag=proof.corpseTag;c.assertTrue(savedTag!=null&&savedTag.getString("flightHull").equals("teleport_flyer"),"actual initial corpse save has its own stable id");
                var initial=(RagdollEntity)EntityType.getEntityFromNbt(savedTag,r.world).orElseThrow();r.extra.add(initial);
                var death=proof.death;var direction=death.cause()==null?death.velocity():death.centre().subtract(death.cause());if(direction.lengthSquared()<1e-8)direction=new Vec3d(0,1,0);
                double mass=initial.rig().segments().stream().mapToDouble(p->p.mass()).sum();var expected=death.velocity().multiply(20).add(direction.normalize().multiply(8/mass));var part=initial.simulation().parts().getFirst();var actual=part.body().linearVelocity();
                c.assertTrue(expected.squaredDistanceTo(actual.x(),actual.y(),actual.z())<1e-8,"per-tick live velocity converts once to m/s plus existing8/m death impulse: expected="+expected+" actual="+actual);
                c.assertTrue(death.centre().squaredDistanceTo(part.body().position().x(),part.body().position().y(),part.body().position().z())<1e-8,"one-part initial physical centre matches actual death centre");
                c.assertTrue(Math.abs(Math.sin(Math.toRadians(death.bank())))>.2,"actual death has a genuinely nontrivial bank, not an identity-only orientation fixture");
                var rotation=FlightHullPose.physicalRotation(death.yaw(),death.pitch(),death.bank());
                for(var basis:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1))){
                    var expectedAxis=rotation.rotate(basis);var actualAxis=part.body().rotation().rotate(basis);
                    c.assertTrue(expectedAxis.minus(actualAxis).length()<1e-6,"all three independent death orientation axes survive, including actual bank: axis="+basis);
                }
                c.assertTrue(death.feet().squaredDistanceTo(proof.arrived)<1e-8,"natural death happened at last actual blink endpoint");
                var field=RagdollEntity.class.getDeclaredField("POSE");field.setAccessible(true);
                @SuppressWarnings("unchecked") var key=(TrackedData<NbtCompound>)field.get(null);var tracked=corpse.getDataTracker().get(key);
                c.assertTrue(tracked.getString("flightHull").equals("teleport_flyer")&&tracked.getInt("rigVersion")==1&&tracked.getList("parts",NbtElement.COMPOUND_TYPE).size()==1,"actual tracked pose carries the matching family/part identity");
                var directory=Path.of(System.getProperty("drmd.test.u07ProofDir"));var path=directory.resolve("actual-corpse-"+corpse.getUuid()+".dat");c.assertTrue(!Files.exists(path),"fresh actual compressed corpse save");NbtIo.writeCompressed(savedTag,path);disk[0]=NbtIo.readCompressed(path,NbtSizeTracker.of(2*1024*1024));savedIdentity[0]=corpse.getUuid();
                saved[0]=true;corpse.discard();return;
            }
            if(restored[0]==null){
                if(r.world.getEntity(proof.corpse.getUuid())!=null){c.assertTrue(r.ticks<210,"original corpse UUID removal before actual restore");return;}
                var candidate=(RagdollEntity)EntityType.getEntityFromNbt(disk[0],r.world).orElseThrow();r.extra.add(candidate);
                c.assertTrue(candidate.getUuid().equals(savedIdentity[0])&&r.chunks.entityTicking(candidate.getBlockPos()),"saved same UUID and real entity-ticking destination before restore");
                c.assertTrue(r.world.spawnEntity(candidate)&&r.world.getEntity(savedIdentity[0])==candidate,"actual saved UUID is registered only after original retirement");
                restored[0]=candidate;restoredWorldTick[0]=r.world.getTime();restoredAge[0]=candidate.age;return;
            }
            c.assertTrue(restored[0].getUuid().equals(savedIdentity[0])&&r.world.getEntity(savedIdentity[0])==restored[0]&&r.chunks.entityTicking(restored[0].getBlockPos()),"the same restored corpse remains registered in an actual entity-ticking volume");
            long worldDelta=r.world.getTime()-restoredWorldTick[0];int ageDelta=restored[0].age-restoredAge[0];
            if(ageDelta<8||worldDelta<8){c.assertTrue(r.ticks<210,"restored physical corpse gets eight actual world and entity ticks: world="+worldDelta+" age="+ageDelta);return;}
            c.assertTrue(ageDelta>=8&&worldDelta>=8&&restored[0].flightHullKind()==FlightEnemyKind.TELEPORT_FLYER&&restored[0].rigVersion()==1&&restored[0].rig().segments().getFirst().id().equals("teleport_cage")
                &&restored[0].simulation().parts().stream().allMatch(p->Double.isFinite(p.body().position().length()+p.body().linearVelocity().length()))
                &&r.actor.teleportCount()==deathBlinks[0],"real ticked restore retains matching physics and never activates corpse teleport");
            System.out.println("U07_LETHAL_WRECK_PASS endpoint="+proof.arrived+" death="+proof.death+" corpse="+restored[0].getUuid()+" initialAge="+restoredAge[0]+" ageDelta="+ageDelta+" worldTickDelta="+worldDelta);r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-coarse-wreck-wall",tickLimit=200)
    public void theCoarseWreckBoxDoesNotPretendToBeMeshExactOrTunnelThroughARealWall(TestContext c){
        var rig=new TeleportFlyerFixture(c,3584,true);rig.actor.setAiDisabled(true);rig.actor.refreshPositionAndAngles(rig.base.getX(),rig.start.y,rig.start.z,45,0);rig.actor.setVelocity(.1,0,0);
        int wall=rig.base.getX()+1;for(int y=-4;y<=5;y++)for(int z=-4;z<=5;z++)rig.block(new BlockPos(wall,rig.base.getY()+y,rig.base.getZ()+z),Blocks.BEDROCK.getDefaultState());
        boolean[] killed={false},overlap={false};
        rig.drive(r->{
            if(!killed[0]){
                var rotation=FlightHullPose.physicalRotation(r.actor.getYaw(),r.actor.getPitch(),r.actor.getFlightRoll());var centre=r.actor.getBoundingBox().getCenter();
                for(var face:com.terminaldetector.drmd.entity.model.FlightEnemyMesh.faces(FlightEnemyKind.TELEPORT_FLYER))for(var v:face.vertices()){
                    var p=rotation.rotate(new Vec3(v.x(),v.y(),v.z()));c.assertTrue(centre.x+p.x()<wall,"visible prototype mesh is clear of wall before death");}
                c.assertTrue(r.actor.damage(r.world.getDamageSources().genericKill(),Float.MAX_VALUE),"explicit direct factory-layer death, separate from actual projectile test");
                TeleportFlyerTestProbe.captureAfterActualDamageReturn(r);killed[0]=true;return;
            }
            var proof=r.record();if(proof.corpse==null){c.assertTrue(r.ticks<40,"coarse corpse actual replacement observed");return;}
            var corpse=proof.corpse;c.assertTrue(proof.corpseAgeAtCapture==0,"actual coarse spawn captured before contact integration");
            c.assertTrue(proof.corpseRegisteredAtCapture&&proof.corpseCapturePhase.equals("AFTER_ACTUAL_DAMAGE_RETURN"),"age0 receipt is the actual registered factory spawn, synchronously read after real death returned");
            overlap[0]|=proof.initialBounds.stream().anyMatch(b->b.maxX>wall);
            c.assertTrue(r.world.getEntity(corpse.getUuid())==corpse&&r.chunks.entityTicking(corpse.getBlockPos()),"actual coarse corpse remains registered and entity-ticking");
            if(corpse.age-proof.corpseAgeAtCapture<8||r.world.getTime()-proof.corpseCaptureTick<8)return;
            c.assertTrue(overlap[0],"coarse rotated box is honestly wider than visible hull, not reported mesh-exact");
            c.assertTrue(corpse.simulation().parts().stream().allMatch(p->Double.isFinite(p.body().position().length()+p.body().linearVelocity().length())),"actual terrain response stays finite");
            double cornerMax=greatestTransformedCornerX(corpse);
            c.assertTrue(cornerMax<=wall+1e-6&&corpse.partBounds().stream().allMatch(b->b.maxX<=wall+1e-6
                &&!r.world.getBlockCollisions(corpse,b.contract(1e-6)).iterator().hasNext()),"the whole actual transformed coarse collider separates from real wall material after ordinary ticks: maxCorner="+cornerMax+" wall="+wall+" bounds="+corpse.partBounds());
            System.out.println("U07_COARSE_WALL_NEGATIVE observedOverlap="+overlap[0]+" finalMaxCorner="+cornerMax+" worldTickDelta="+(r.world.getTime()-proof.corpseCaptureTick)+" actualBounds="+corpse.partBounds());r.complete();
        });
    }
    private static double greatestTransformedCornerX(RagdollEntity corpse){
        double greatest=Double.NEGATIVE_INFINITY;
        for(var part:corpse.simulation().parts()){
            var half=part.segment().halfExtents();
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
                var point=part.body().position().plus(part.body().rotation().rotate(new Vec3(x*half.x(),y*half.y(),z*half.z())));greatest=Math.max(greatest,point.x());
            }
        }
        return greatest;
    }
}
