package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual operator motors/scheduler/normal shots; no manual physics/AI ticks. */
public final class HeavyOperatorGameTests implements FabricGameTest {
    private record Birth(UUID id,BlockPos gun,Vec3d at,Vec3d velocity,int mesh){}
    private static final Map<UUID,Rig> WATCH=new HashMap<>();
    private static boolean observing;
    private static void observers(){if(observing)return;observing=true;ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
        if(!(entity instanceof ProjectileEntity shot))return;var rig=WATCH.get(shot.getOwnerUuid());
        if(rig==null||rig.closed||rig.c.getWorld()!=world)return;
        if(rig.births.size()>=128){rig.dropped++;return;}
        var p=rig.a.barrel().worldToLocal(shot.getPos());rig.births.add(new Birth(shot.getUuid(),new BlockPos(MathHelper.floor(p.x),MathHelper.floor(p.y),2),shot.getPos(),shot.getVelocity(),shot.getMeshKind()));
    });}
    private static final class Rig implements AutoCloseable {
        final TestContext c;final AiFixtureChunks lease;final CowEntity target;final List<Birth> births=new ArrayList<>();
        HeavyOperatorSystem.Assembly a;final Quat relative;final Vec3d initial;int dropped;boolean closed,powered,relativeMotion;
        Rig(TestContext c,AiFixtureChunks lease,Vec3d at){
            this.c=c;this.lease=lease;var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.9);
            a=HeavyOperatorSystem.assemble(c.getWorld(),at,q,new Vec3d(.35,0,0),new Vec3d(0,0,.025));
            initial=a.base().getPos();relative=ImmPtlQuaternions.hamiltonProduct(a.base().rotation().inverse(),a.barrel().rotation());WATCH.put(a.barrel().getUuid(),this);
            target=EntityType.COW.create(c.getWorld());c.assertTrue(target!=null,"registered ordinary target exists");
            var delta=q.rotate(new Vec3(1.3,.3,26));var point=MountedWeaponEmitter.muzzle(a.barrel(),HeavyOperatorSystem.LASER).add(delta.x(),delta.y(),delta.z());
            target.setPosition(point.subtract(0,target.getHeight()*.5,0));target.setNoGravity(true);target.setAiDisabled(true);
            target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1024);target.setHealth(1024);
            c.assertTrue(c.getWorld().spawnEntity(target),"actual damageable living target admitted");
        }
        void observe(){
            c.assertTrue(dropped==0,"bounded passive birth receipts did not overflow");
            var stats=HeavyOperatorSystem.stats(c.getWorld());c.assertTrue(stats.visited()<=32&&stats.assemblies()<=8&&stats.candidates()<=32&&stats.rays()<=32,"actual operator work remains bounded");
            powered|=a.base().getLocalBlock(HeavyOperatorSystem.YAW_DRIVE).get(Properties.POWER)>0||a.yaw().getLocalBlock(HeavyOperatorSystem.PITCH_DRIVE).get(Properties.POWER)>0;
            var current=ImmPtlQuaternions.hamiltonProduct(a.base().rotation().inverse(),a.barrel().rotation());
            relativeMotion|=Math.abs(ImmPtlQuaternions.dot(relative,current))<1-1e-6;
        }
        long shots(BlockPos gun){return births.stream().filter(b->b.gun.equals(gun)).count();}
        boolean allGuns(){return shots(HeavyOperatorSystem.LASER)>0&&shots(HeavyOperatorSystem.LEFT)>0&&shots(HeavyOperatorSystem.RIGHT)>0;}
        void done(){close();c.complete();}
        @Override public void close(){
            if(closed)return;closed=true;WATCH.remove(a.barrel().getUuid());target.discard();
            for(var shot:c.getWorld().getEntitiesByClass(ProjectileEntity.class,a.base().getBoundingBox().expand(160),p->p.getOwnerUuid()!=null&&(p.getOwnerUuid().equals(a.barrel().getUuid())||p.getOwnerUuid().equals(target.getUuid()))))shot.discard();
            for(var b:a.bodies()){BodyJointRegistry.get(c.getWorld()).removeBody(b.getUuid());b.discard();}lease.close();
        }
    }
    private static void admitted(TestContext c,Consumer<Rig> start,Consumer<Rig> tick){
        observers();var at=c.getAbsolutePos(new BlockPos(8,220,8));var lease=new AiFixtureChunks(c.getWorld(),at.add(8,0,12),3);Rig[] rig={null};int[] wait={0};long[] began={0};
        c.runAtEveryTick(()->{
            try{
                c.getWorld().resetIdleTimeout(); // The disposable no-player fixture represents an occupied runtime.
                if(rig[0]==null){if(!lease.entityTicking(at.add(-8,0,-8),at.add(32,0,48))){if(++wait[0]<80)return;lease.close();c.assertTrue(false,"operator fixture requires its complete actual entity-ticking footprint");return;}
                    rig[0]=new Rig(c,lease,Vec3d.of(at));began[0]=c.getTick();start.accept(rig[0]);}
                var r=rig[0];if(r.closed)return;r.observe();c.assertTrue(c.getTick()-began[0]<240,"actual motors/fire/material/restore must complete in bounded normal ticks");tick.accept(r);
            }catch(Throwable failure){if(rig[0]!=null)rig[0].close();else lease.close();throw failure;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="heavy-operator-fire",tickLimit=340)
    public void separateTwoAxisMechanismFiresLaserAndBothShoulderRocketsWhileActuallyMovingAndRolled(TestContext c){admitted(c,r->{},r->{
        if(!r.allGuns()||!r.powered||!r.relativeMotion)return;
        c.assertTrue(r.a.base().getPos().distanceTo(r.initial)>.05,"real free base moves; no static copy or actor-follow teleport");
        c.assertTrue(r.births.stream().allMatch(b->b.velocity.lengthSquared()>1e-6)&&r.births.stream().anyMatch(b->b.mesh==ProjectileEntity.MESH_BOLT)&&r.births.stream().anyMatch(b->b.mesh==ProjectileEntity.MESH_ROCKET),"actual normal emitter produced both genuine projectile families");
        c.assertTrue(BodyJointRegistry.get(c.getWorld()).forBody(r.a.yaw().getUuid()).size()==2,"the same two physical hinges, not a renderer-only turret");r.done();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="heavy-operator-damage",tickLimit=340)
    public void realShoulderImpactDisablesOnlyItsGunAndOrdinaryMaterialReplacementResumesWithoutBodyReset(TestContext c){
        int[] phase={0};long[] until={0},left={0},right={0},laser={0};UUID[] incoming={null};
        admitted(c,r->{},r->{
            if(phase[0]==0){if(!r.allGuns())return;var muzzle=MountedWeaponEmitter.muzzle(r.a.barrel(),HeavyOperatorSystem.LEFT);var direction=MountedWeaponEmitter.direction(r.a.barrel(),HeavyOperatorSystem.LEFT);
                var cfg=new WeaponCore.FireConfig();cfg.owner=r.target;cfg.pos=muzzle.add(direction.multiply(3));cfg.dir=direction.multiply(-1);cfg.speedIsSourceUnits=false;cfg.speed=3;cfg.life=2;cfg.directDamage=512;cfg.dmgClass=DamageClass.KINETIC;
                var shot=WeaponCore.fireProjectile(cfg);c.assertTrue(shot!=null,"actual incoming round emitted through ordinary WeaponCore");incoming[0]=shot.getUuid();phase[0]=1;return;}
            if(phase[0]==1){if(!r.a.barrel().getLocalBlock(HeavyOperatorSystem.LEFT).isAir())return;
                c.assertTrue(c.getWorld().getEntity(incoming[0])==null||c.getWorld().getEntity(incoming[0]).isRemoved(),"real incoming projectile terminated on the destroyed shoulder material");
                left[0]=r.shots(HeavyOperatorSystem.LEFT);right[0]=r.shots(HeavyOperatorSystem.RIGHT);laser[0]=r.shots(HeavyOperatorSystem.LASER);until[0]=c.getTick()+32;phase[0]=2;return;}
            if(phase[0]==2){if(c.getTick()<until[0])return;
                c.assertTrue(r.shots(HeavyOperatorSystem.LEFT)==left[0]&&r.shots(HeavyOperatorSystem.RIGHT)>right[0]&&r.shots(HeavyOperatorSystem.LASER)>laser[0],"destroyed actual shoulder stops while independently remaining material weapons continue: left="+left[0]+"->"+r.shots(HeavyOperatorSystem.LEFT)+" right="+right[0]+"->"+r.shots(HeavyOperatorSystem.RIGHT)+" laser="+laser[0]+"->"+r.shots(HeavyOperatorSystem.LASER)+" targetHP="+r.target.getHealth()+" mask="+HeavyOperatorSystem.fireMask(r.a.barrel())+" joints="+BodyJointRegistry.get(c.getWorld()).forBody(r.a.yaw().getUuid())+" barrelW="+r.a.barrel().physics().angularVelocity());
                var gun=r.a.barrel();var point=MountedWeaponEmitter.muzzle(gun,HeavyOperatorSystem.RIGHT);var velocity=gun.surfaceVelocityAt(point);var timers=gun.mountedWeapons().write(gun);var id=gun.getUuid();
                c.assertTrue(new BlockBodyWorldAccess(gun).placeLocalBlock(HeavyOperatorSystem.LEFT,ModWorldBlocks.SHIP_ROCKET.getDefaultState().with(Properties.FACING,Direction.SOUTH)),"ordinary supported replacement block fits the actual mechanism");
                var after=MountedWeaponEmitter.muzzle(gun,HeavyOperatorSystem.RIGHT);
                c.assertTrue(gun.getUuid().equals(id)&&point.distanceTo(after)<1e-7&&velocity.distanceTo(gun.surfaceVelocityAt(after))<1e-7&&timers.equals(gun.mountedWeapons().write(gun)),"replacement preserves existing material point velocity, position, identity and the other banks' real timers");phase[0]=3;return;}
            if(r.shots(HeavyOperatorSystem.LEFT)>left[0])r.done();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="heavy-operator-save",tickLimit=340)
    public void savedThreeBodyIdentityRestoresNormalFireAndFutureOrMissingCoreNeverGrantsAutomaticAuthority(TestContext c){
        int[] phase={0};long[] until={0},before={0};
        admitted(c,r->{},r->{
            if(phase[0]==0){if(!r.allGuns())return;var tags=new ArrayList<NbtCompound>();var ids=r.a.bodies().stream().map(Entity::getUuid).toList();
                for(var b:r.a.bodies()){var tag=new NbtCompound();c.assertTrue(b.saveSelfNbt(tag),"actual save includes each registered part type id");tags.add(tag);}for(var b:r.a.bodies())b.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);var restored=new ArrayList<BlockBodyEntity>();
                for(var tag:tags){var e=EntityType.loadEntityWithPassengers(tag,c.getWorld(),x->x);c.assertTrue(e instanceof BlockBodyEntity&&c.getWorld().spawnEntity(e),"registered saved part restores once through the ordinary entity codec");restored.add((BlockBodyEntity)e);}
                r.a=new HeavyOperatorSystem.Assembly(restored.get(0),restored.get(1),restored.get(2));c.assertTrue(ids.equals(r.a.bodies().stream().map(Entity::getUuid).toList()),"same three saved UUIDs and ordinary material controller tags");before[0]=r.births.size();until[0]=c.getTick()+8;phase[0]=1;return;}
            if(phase[0]==1){if(c.getTick()<until[0]||r.births.size()<=before[0])return;
                for(var b:r.a.bodies())c.assertTrue(c.getWorld().getEntity(b.getUuid())==b&&b.age>=8,"restored parts receive at least eight ordinary ticks, no manual stepping");
                r.a.base().getCommandTags().remove("drmd.operator.v1");r.a.base().addCommandTag("drmd.operator.v37");before[0]=r.births.size();until[0]=c.getTick()+32;phase[0]=2;return;}
            if(phase[0]==2){if(c.getTick()<until[0])return;c.assertTrue(r.births.size()==before[0]&&r.a.base().getCommandTags().contains("drmd.operator.v37")&&HeavyOperatorSystem.fireMask(r.a.barrel()).orElse(-1)==0,"unknown version remains intact and cannot fall back to automatic ordinary fire");
                r.a.base().getCommandTags().remove("drmd.operator.v37");r.a.base().addCommandTag("drmd.operator.v1");c.assertTrue(r.a.base().setLocalBlock(HeavyOperatorSystem.CORE,Blocks.AIR.getDefaultState()),"actual core material removed");until[0]=c.getTick()+32;phase[0]=3;return;}
            if(c.getTick()<until[0])return;c.assertTrue(r.births.size()==before[0]&&HeavyOperatorSystem.fireMask(r.a.barrel()).orElse(-1)==0,"lost core retires autonomous authority without replacing physical bodies");r.done();
        });
    }
}
