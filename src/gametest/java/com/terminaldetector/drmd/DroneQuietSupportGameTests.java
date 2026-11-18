package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Ordinary existing SUPPORT goal, no hostile target, manual ticks, follow poses or direct healing. */
public final class DroneQuietSupportGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;final ServerWorld w;final BlockPos base;final Vec3d start;
        final AiFixtureChunks chunks;final DroneEntity support,patient;final float initialHealth;
        final List<BlockPos> material=new ArrayList<>();BlockBodyEntity hull;
        boolean spawned,enabled,closed,retiredPatient;int admission,ticks;
        Rig(TestContext c,int offset,Vec3d delta){
            this.c=c;w=c.getWorld();base=c.getAbsolutePos(new BlockPos(7000+offset,180,3000));start=Vec3d.ofBottomCenter(base).add(0,.05,0);chunks=new AiFixtureChunks(w,base,2);
            for(var p:BlockPos.iterate(base.add(-9,-4,-9),base.add(9,9,9)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
            support=ModEntities.DRONE.create(w);patient=ModEntities.DRONE.create(w);support.applyRole(AiRole.SUPPORT);patient.applyRole(AiRole.MG);
            support.setPosition(start);patient.setPosition(start.add(delta));support.setPersistent();patient.setPersistent();support.setAiDisabled(true);patient.setAiDisabled(true);
            patient.setHealth(patient.getMaxHealth()*.25f);initialHealth=patient.getHealth();
        }
        void block(BlockPos p){material.add(p.toImmutable());w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),2);}
        boolean intact(){return material.stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK));}
        String state(){return "ticks="+ticks+" pos="+support.getPos().subtract(start)+" velocity="+support.getVelocity()+" target="+support.getTarget()+" patient="+patient.getHealth()+"/"+patient.getMaxHealth()+" original="+initialHealth;}
        void drive(IntConsumer step){c.runAtEveryTick(()->{if(closed)return;try{w.resetIdleTimeout();
            if(!spawned){c.assertTrue(++admission<80,"real quiet-support entity-ticking admission");if(!chunks.entityTicking(support.getBlockPos(),patient.getBlockPos()))return;c.assertTrue(w.spawnEntity(support)&&w.spawnEntity(patient),"two actual registered drone roles");spawned=true;return;}
            if(!enabled){c.assertTrue(++admission<80,"ordinary actor ages before enabling actual goal");if(support.age<3||patient.age<3||w.getEntity(support.getUuid())!=support||w.getEntity(patient.getUuid())!=patient)return;c.assertTrue(support.getTarget()==null&&patient.getTarget()==null,"quiet fixture supplies no synthetic hostile lock");support.setAiDisabled(false);enabled=true;}
            c.assertTrue(support.isAlive()&&support.getRole()==AiRole.SUPPORT&&support.getTarget()==null,"real support role remains autonomous and never invents an enemy: "+state());
            c.assertTrue(Double.isFinite(support.getPos().lengthSquared())&&Double.isFinite(support.getVelocity().lengthSquared()),"finite real navigation: "+state());
            if(!retiredPatient)c.assertTrue(patient.isAlive()&&w.getEntity(patient.getUuid())==patient,"same actual repair patient remains registered");
            c.assertTrue(w.getEntitiesByClass(ProjectileEntity.class,new Box(start,start).expand(24),p->p.getOwnerLiving()==support).isEmpty(),"targetless rescue does not emit combat projectiles");step.accept(++ticks);
        }catch(RuntimeException|Error error){close();throw error;}});}
        void done(){close();c.complete();}
        @Override public void close(){if(closed)return;closed=true;support.discard();patient.discard();if(hull!=null)hull.discard();for(var p:material)w.setBlockState(p,Blocks.AIR.getDefaultState(),2);chunks.close();}
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-quiet-support-aperture",tickLimit=400)
    public void withoutAnEnemySupportPassesACeilingApertureHealsAndRetiresARemovedPatient(TestContext c){
        var r=new Rig(c,0,new Vec3d(0,5,0));for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(x!=2||z!=0)r.block(r.base.add(x,2,z));
        boolean[] entered={false},crossed={false};int[] removedAt={-1};float[] removedHealth={0};
        r.drive(t->{var b=r.support.getBoundingBox();double e=1e-5;
            if(b.maxY>r.base.getY()+2+e&&b.minY<r.base.getY()+3-e&&b.minX>=r.base.getX()+2-e&&b.maxX<=r.base.getX()+3+e&&b.minZ>=r.base.getZ()-e&&b.maxZ<=r.base.getZ()+1+e)entered[0]=true;
            crossed[0]|=r.support.getY()>r.base.getY()+3.15;
            if(removedAt[0]<0&&entered[0]&&crossed[0]&&r.patient.getHealth()>r.initialHealth){c.assertTrue(r.intact(),"real3D route used the actual aperture without destroying opaque ceiling");removedHealth[0]=r.patient.getHealth();removedAt[0]=r.support.age;r.retiredPatient=true;r.patient.discard();}
            if(removedAt[0]>=0&&r.support.age-removedAt[0]>=10){c.assertTrue(r.w.getEntity(r.patient.getUuid())==null&&r.patient.getHealth()==removedHealth[0],"five-tick UUID cache cannot continue healing an actually removed entity");System.out.println("QUIET_SUPPORT_APERTURE_PASS "+r.state());r.done();return;}
            c.assertTrue(t<280,"natural targetless support crosses intact physical opening, heals and retires task: entered="+entered[0]+" crossed="+crossed[0]+" "+r.state());
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-quiet-support-opaque",tickLimit=200)
    public void anIntactOpaqueEnclosurePreventsRemoteHealingWhileTheRealGoalAttemptsToApproach(TestContext c){
        var r=new Rig(c,2048,new Vec3d(4,0,0));var centre=r.base.east(4);
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(Math.abs(x)==1||Math.abs(y)==1||Math.abs(z)==1)r.block(centre.add(x,y,z));
        r.drive(t->{c.assertTrue(r.patient.getHealth()==r.initialHealth,"actual loaded opaque blocks deny healing even inside the authored6m radius: "+r.state());if(t==80){c.assertTrue(r.intact()&&r.support.getPos().squaredDistanceTo(r.start)>.01,"normal goal was actually active, not a dormant negative control: "+r.state());r.done();}});
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-quiet-support-oriented-hull",tickLimit=260)
    public void aRealRotatedCompoundHullBlocksHealingUntilItsActualCollisionMaterialIsRetired(TestContext c){
        var r=new Rig(c,4096,new Vec3d(5.75,0,0));var cells=new ArrayList<BlockBodyEntity.Cell>();
        // Three-cell interior contains the whole patient AABB under this nontrivial rotation.
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(y)==2||Math.abs(z)==2)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        var q=new Quat(.13,.27,.18,.91).normalized();var centre=r.patient.getBoundingBox().getCenter();var offset=q.rotate(new Vec3(.5,.5,.5));r.hull=BlockBodyEntity.createLocal(r.w,cells,centre.add(-offset.x(),-offset.y(),-offset.z()),q,Vec3d.ZERO,Vec3d.ZERO);r.hull.setPhysicsGravity(false);
        int[] receipts={0};
        r.drive(t->{if(t<=60){boolean blocked=r.hull.physicsRaycast(r.support.getEyePos(),r.patient.getBoundingBox().getCenter()).isPresent();
                if(receipts[0]<2&&(t==1||!blocked||r.patient.getHealth()!=r.initialHealth)){receipts[0]++;System.out.println("QUIET_SUPPORT_HULL_GEOMETRY tick="+t+" time="+r.w.getTime()+" blocked="+blocked+" body="+r.hull.getUuid()+" registered="+(r.w.getEntity(r.hull.getUuid())==r.hull)+" bodyAge="+r.hull.age+" simulated="+r.hull.hasSimulatedPhysics()+" paused="+r.hull.boardingMotionPaused()+" com="+r.hull.massPosition()+" rotation="+r.hull.rotation()+" bodyV="+r.hull.physics().linearVelocity()+" bodyW="+r.hull.physics().angularVelocity()+" eye="+r.support.getEyePos()+" eyeLocal="+r.hull.worldToLocal(r.support.getEyePos())+" patientCentre="+r.patient.getBoundingBox().getCenter()+" patientLocal="+r.hull.worldToLocal(r.patient.getBoundingBox().getCenter())+" "+r.state());}
                c.assertTrue(r.patient.getHealth()==r.initialHealth,"real oriented hull material denies the repair ray before retirement: "+r.state());c.assertTrue(blocked,"actual compound material, not only its broad AABB, encloses the patient");}
            if(t==60){c.assertTrue(r.hull.hasSimulatedPhysics()&&r.hull.cells().size()==98,"one actual normally simulated compound body, no per-block bodies");r.hull.discard();}
            if(t>=65&&r.patient.getHealth()>r.initialHealth){c.assertTrue(r.hull.isRemoved()&&!BodySpatialIndex.query(r.w,new Box(r.start,r.patient.getPos()).expand(8)).contains(r.hull),"normal body lifecycle actually removes blocking indexed material before healing resumes");r.done();return;}
            c.assertTrue(t<160,"ordinary support resumes repair after the real hull is retired: "+r.state());
        });
    }
}
