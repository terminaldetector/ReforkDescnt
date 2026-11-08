package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** One physical yaw turret on the existing joint system. Saved references never fabricate a body. */
public final class SkyUfoTurret {
    public static final BlockPos ROOF=new BlockPos(0,3,0),BASE=BlockPos.ORIGIN,LASER=new BlockPos(0,1,0),ROCKET=new BlockPos(0,2,0);
    public static final Vec3 ROOT_POINT=new Vec3(.5,4,.5),CHILD_POINT=new Vec3(.5,0,.5);
    private static final Quat AXIS=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),90);
    private final UUID child,joint;
    private WeakReference<BlockBodyEntity> cached=new WeakReference<>(null);
    private int laserCooldown,rocketCooldown;
    private UUID autonomousTarget;private boolean rocketAim;private int burst;private long selectedSince;
    private SkyUfoTurret(UUID child,UUID joint){this.child=child;this.joint=joint;}
    public UUID bodyId(){return child;}public UUID jointId(){return joint;}
    private static boolean live(SkyUfoEntity root){return root.getWorld() instanceof ServerWorld&&!root.isRemoved()&&BodySpatialIndex.find(root.getWorld(),root.getId()).orElse(null)==root;}
    private boolean installed(SkyUfoEntity root){return live(root)&&root.turretModule().orElse(null)==this;}
    private Optional<BodyJointLink> link(SkyUfoEntity root) {
        if(!installed(root))return Optional.empty();return BodyJointRegistry.get((ServerWorld)root.getWorld()).find(joint)
            .filter(l->l.kind()==BodyJointConstraint.Kind.MOTOR&&l.a().body().equals(root.getUuid())&&l.a().cell().equals(ROOF)&&l.b().body().equals(child)&&l.b().cell().equals(BASE));
    }
    public Optional<BlockBodyEntity> body(SkyUfoEntity root) {
        if(!live(root))return Optional.empty();var b=cached.get();
        if(b!=null&&!b.isRemoved()&&b.getWorld()==root.getWorld()&&b.getUuid().equals(child))return Optional.of(b);
        for(var candidate:BodySpatialIndex.loaded(root.getWorld()))if(candidate!=root&&candidate.getUuid().equals(child)){cached=new WeakReference<>(candidate);return Optional.of(candidate);}
        return Optional.empty();
    }
    public boolean connected(SkyUfoEntity root){return link(root).isPresent()&&body(root).filter(b->!b.getLocalBlock(BASE).isAir()).isPresent()&&!root.getLocalBlock(ROOF).isAir();}
    boolean functional(SkyUfoEntity root){return connected(root)&&body(root).filter(b->b.getLocalBlock(LASER).isOf(ModWorldBlocks.SHIP_LASER)||b.getLocalBlock(ROCKET).isOf(ModWorldBlocks.SHIP_ROCKET)).isPresent();}
    public static SkyUfoTurret install(SkyUfoEntity root,ServerPlayerEntity player) {
        if(!live(root)||!UfoControlSystem.controls(player,root)||!root.hasPoweredReactor()||root.getLocalBlock(ROOF).isAir())return null;
        if(root.turretModule().filter(t->t.link(root).isPresent()).isPresent())return null;
        var cells=List.of(new BlockBodyEntity.Cell(new BlockPos(-1,0,0),Blocks.IRON_BLOCK.getDefaultState()),new BlockBodyEntity.Cell(BASE,Blocks.IRON_BLOCK.getDefaultState()),
            new BlockBodyEntity.Cell(new BlockPos(1,0,0),Blocks.IRON_BLOCK.getDefaultState()),new BlockBodyEntity.Cell(LASER,ModWorldBlocks.SHIP_LASER.getDefaultState()),new BlockBodyEntity.Cell(ROCKET,ModWorldBlocks.SHIP_ROCKET.getDefaultState()));
        var origin=root.localToWorld(new Vec3d(0,4,0));var world=(ServerWorld)root.getWorld();var q=root.rotation();
        for(var cell:cells) {
            var pos=cell.pos();var cube=OrientedBox.local(new BodyBounds(new Vec3(pos.getX(),pos.getY(),pos.getZ()),new Vec3(pos.getX()+1,pos.getY()+1,pos.getZ()+1)),new Vec3(origin.x,origin.y,origin.z),new Vec3(0,0,0),q);
            var bounds=cube.bounds();var area=new Box(bounds.min().x(),bounds.min().y(),bounds.min().z(),bounds.max().x(),bounds.max().y(),bounds.max().z());
            if(!root.collisionRegionAvailable(area))return null;
            for(var shape:world.getBlockCollisions(root,area.contract(1e-6)))for(var b:shape.getBoundingBoxes())if(overlaps(cube,OrientedBox.axisAligned(new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ)))))return null;
            for(var other:BodySpatialIndex.otherEntities(world,null,area,e->e.isAlive()&&!e.isSpectator())) {
                if(other instanceof BlockBodyEntity body){if(body.collisionQuery().contacts(cube,0).stream().anyMatch(c->c.penetration()>1e-6))return null;}
                else {var b=other.getBoundingBox();if(overlaps(cube,OrientedBox.axisAligned(new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ)))))return null;}
            }
        }
        BlockBodyEntity body=null;UUID joint=UUID.randomUUID();
        try {
            var w=root.physics().angularVelocity();body=BlockBodyEntity.createLocal(world,cells,origin,q,root.surfaceVelocityAt(origin),new Vec3d(w.x(),w.y(),w.z()));
            body.setPassengerGravity(new Vec3(0,-16,0));body.setPhysicsGravity(false);
            var link=new BodyJointLink(joint,new BodyJointLink.Endpoint(root.getUuid(),ROOF,ROOT_POINT,AXIS),new BodyJointLink.Endpoint(body.getUuid(),BASE,CHILD_POINT,AXIS),
                BodyJointConstraint.Kind.MOTOR,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,0,40,50000,50000,false);
            BodyJointRegistry.get(world).add(world,root,body,link);
            var result=new SkyUfoTurret(body.getUuid(),joint);result.cached=new WeakReference<>(body);return result;
        }catch(RuntimeException failure){BodyJointRegistry.get(world).remove(joint);if(body!=null)body.discard();throw failure;}
    }
    private static boolean overlaps(OrientedBox a,OrientedBox b){return a.contact(b,0).filter(c->c.penetration()>1e-6).isPresent();}
    public void tick(SkyUfoEntity root){if(!installed(root))return;if(laserCooldown>0)laserCooldown--;if(rocketCooldown>0)rocketCooldown--;if(link(root).isEmpty())detach(root);else body(root).ifPresent(b->{var gravity=root.hasPoweredReactor()?new Vec3(0,-16,0):new Vec3(0,0,0);if(!b.passengerGravity().equals(gravity))b.setPassengerGravity(gravity);});}
    public void aim(SkyUfoEntity root,float input) {
        if(!Float.isFinite(input)||Math.abs(input)>1)return;var l=link(root).orElse(null);if(l==null){retireMotor(root);return;}
        boolean powered=root.hasPoweredReactor();BodyJointRegistry.get((ServerWorld)root.getWorld()).configure(joint,powered?input*.6:0,powered?40:0,l.breakForce(),l.breakTorque());
    }
    public void stopAutonomous(SkyUfoEntity root){autonomousTarget=null;rocketAim=false;burst=0;aim(root,0);}
    Vec3 cooperativeAim(SkyUfoEntity root,ServerPlayerEntity target){
        if(target==null||!functional(root))return null;var b=body(root).orElseThrow();
        if(!b.getLocalBlock(LASER).isOf(ModWorldBlocks.SHIP_LASER))rocketAim=true;else if(!b.getLocalBlock(ROCKET).isOf(ModWorldBlocks.SHIP_ROCKET))rocketAim=false;
        return SkyUfoCombat.aim(b,rocketAim?ROCKET:LASER,target,rocketAim?1.4:2.5,rocketAim?100:80);
    }
    public void aimAutonomous(SkyUfoEntity root){
        if(UfoControlSystem.hasPilot(root))return;
        var target=root.autonomousTarget().orElse(null);
        if(!SkyUfoCombat.allowed(root)||!SkyUfoCombat.eligible(root,target)||!functional(root)){stopAutonomous(root);return;}
        long now=root.getWorld().getTime();if(!target.getUuid().equals(autonomousTarget)){autonomousTarget=target.getUuid();rocketAim=false;burst=0;selectedSince=now;}
        if(now-selectedSince>=160){rocketAim=!rocketAim;burst=0;selectedSince=now;}
        var b=body(root).orElseThrow();var desired=cooperativeAim(root,target);if(desired==null){aim(root,0);return;}
        var axis=root.rotation().rotate(link(root).orElseThrow().a().frame().rotate(new Vec3(1,0,0)));
        var muzzle=SkyUfoWeapons.muzzle(b,rocketAim?ROCKET:LASER);var offset=target.getBoundingBox().getCenter().subtract(muzzle);var velocity=SkyUfoCombat.targetVelocity(target).multiply(20).subtract(b.surfaceVelocityAt(muzzle));var r=new Vec3(offset.x,offset.y,offset.z);var v=new Vec3(velocity.x,velocity.y,velocity.z);
        double feed=com.terminaldetector.drmd.weapon.core.HingeAim.bearingRate(r,v.minus(root.physics().angularVelocity().cross(r)),axis);
        double speed=com.terminaldetector.drmd.weapon.core.HingeAim.velocity(b.rotation().rotate(new Vec3(0,0,1)),desired,axis,feed,.6);aim(root,(float)(speed/.6));
    }
    public int fireAutonomous(SkyUfoEntity root){
        if(!SkyUfoCombat.allowed(root)||!functional(root))return 0;var target=root.autonomousTarget().orElse(null);if(!SkyUfoCombat.eligible(root,target))return 0;
        var b=body(root).orElseThrow();var world=(ServerWorld)root.getWorld();if(!b.hasSimulatedPhysics()||b.boardingMotionPaused()||!BodyJointRegistry.get(world).active(joint,world.getTime()))return 0;
        int mask=0;
        if(laserCooldown==0&&SkyUfoCombat.clearShot(b,LASER,target,2.5,80)&&SkyUfoWeapons.emit(b,b,List.of(LASER),ModWorldBlocks.SHIP_LASER,false)>0){laserCooldown=8;mask|=1;emitted(root,false);BodyJointSystem.reconcileImpulse(world,b);}
        if(rocketCooldown==0&&SkyUfoCombat.clearShot(b,ROCKET,target,1.4,100)&&SkyUfoWeapons.emit(b,b,List.of(ROCKET),ModWorldBlocks.SHIP_ROCKET,true)>0){rocketCooldown=24;mask|=2;emitted(root,true);BodyJointSystem.reconcileImpulse(world,b);}
        return mask;
    }
    private void emitted(SkyUfoEntity root,boolean rocket){if(rocket==rocketAim&&++burst>=(rocket?1:3)){rocketAim=!rocketAim;burst=0;selectedSince=root.getWorld().getTime();}}
    public void fire(SkyUfoEntity root,ServerPlayerEntity pilot) {
        if(!root.hasPoweredReactor()||!UfoControlSystem.controls(pilot,root)||!connected(root))return;
        var b=body(root).orElseThrow();var world=(ServerWorld)root.getWorld();
        if(b.boardingMotionPaused()||!BodyJointRegistry.get(world).active(joint,world.getTime()))return;
        if(laserCooldown==0&&SkyUfoWeapons.emit(b,pilot,List.of(LASER),ModWorldBlocks.SHIP_LASER,false)>0){laserCooldown=8;BodyJointSystem.reconcileImpulse(world,b);}
        if(rocketCooldown==0&&SkyUfoWeapons.emit(b,pilot,List.of(ROCKET),ModWorldBlocks.SHIP_ROCKET,true)>0){rocketCooldown=24;BodyJointSystem.reconcileImpulse(world,b);}
    }
    private void retireMotor(SkyUfoEntity root){
        if(!installed(root))return;var registry=BodyJointRegistry.get((ServerWorld)root.getWorld());
        // A fracture can rebind the bearing to a roof fragment. Keep that real
        // mechanical joint, but its former reactor/controller supplies no power.
        registry.find(joint).filter(l->l.kind()==BodyJointConstraint.Kind.MOTOR&&((l.a().body().equals(child)&&l.a().cell().equals(BASE))||(l.b().body().equals(child)&&l.b().cell().equals(BASE))))
            .ifPresent(l->registry.configure(joint,0,0,l.breakForce(),l.breakTorque()));
    }
    public void detach(SkyUfoEntity root){if(installed(root)){retireMotor(root);body(root).ifPresent(b->{b.setPhysicsGravity(true);b.setPassengerGravity(new Vec3(0,0,0));});}}
    public NbtCompound write(){var n=new NbtCompound();n.putInt("schema",1);n.putUuid("body",child);n.putUuid("joint",joint);n.putInt("laserCooldown",laserCooldown);n.putInt("rocketCooldown",rocketCooldown);return n;}
    public static SkyUfoTurret read(NbtCompound n){if(n.getInt("schema")!=1||!n.containsUuid("body")||!n.containsUuid("joint"))throw new IllegalArgumentException("invalid UFO turret reference");var t=new SkyUfoTurret(n.getUuid("body"),n.getUuid("joint"));t.laserCooldown=MathHelper.clamp(n.getInt("laserCooldown"),0,8);t.rocketCooldown=MathHelper.clamp(n.getInt("rocketCooldown"),0,24);return t;}
}
