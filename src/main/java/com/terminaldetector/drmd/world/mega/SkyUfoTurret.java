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
    private SkyUfoTurret(UUID child,UUID joint){this.child=child;this.joint=joint;}
    public UUID bodyId(){return child;}public UUID jointId(){return joint;}
    private static boolean live(SkyUfoEntity root){return root.getWorld() instanceof ServerWorld&&!root.isRemoved()&&BodySpatialIndex.find(root.getWorld(),root.getId()).orElse(null)==root;}
    private Optional<BodyJointLink> link(SkyUfoEntity root) {
        if(!live(root))return Optional.empty();return BodyJointRegistry.get((ServerWorld)root.getWorld()).find(joint)
            .filter(l->l.kind()==BodyJointConstraint.Kind.MOTOR&&l.a().body().equals(root.getUuid())&&l.a().cell().equals(ROOF)&&l.b().body().equals(child)&&l.b().cell().equals(BASE));
    }
    public Optional<BlockBodyEntity> body(SkyUfoEntity root) {
        if(!live(root))return Optional.empty();var b=cached.get();
        if(b!=null&&!b.isRemoved()&&b.getWorld()==root.getWorld()&&b.getUuid().equals(child))return Optional.of(b);
        for(var candidate:BodySpatialIndex.loaded(root.getWorld()))if(candidate.getUuid().equals(child)){cached=new WeakReference<>(candidate);return Optional.of(candidate);}
        return Optional.empty();
    }
    public boolean connected(SkyUfoEntity root){return link(root).isPresent()&&body(root).filter(b->!b.getLocalBlock(BASE).isAir()).isPresent()&&!root.getLocalBlock(ROOF).isAir();}
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
                BodyJointConstraint.Kind.MOTOR,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,0,40,50000,50000);
            BodyJointRegistry.get(world).add(world,root,body,link);
            var result=new SkyUfoTurret(body.getUuid(),joint);result.cached=new WeakReference<>(body);return result;
        }catch(RuntimeException failure){BodyJointRegistry.get(world).remove(joint);if(body!=null)body.discard();throw failure;}
    }
    private static boolean overlaps(OrientedBox a,OrientedBox b){return a.contact(b,0).filter(c->c.penetration()>1e-6).isPresent();}
    public void tick(SkyUfoEntity root){if(laserCooldown>0)laserCooldown--;if(rocketCooldown>0)rocketCooldown--;if(link(root).isEmpty())detach(root);}
    public void aim(SkyUfoEntity root,float input) {
        if(!Float.isFinite(input)||Math.abs(input)>1)return;var l=link(root).orElse(null);if(l==null)return;
        boolean powered=root.hasPoweredReactor();BodyJointRegistry.get((ServerWorld)root.getWorld()).configure(joint,powered?input*.6:0,powered?40:0,l.breakForce(),l.breakTorque());
    }
    public void fire(SkyUfoEntity root,ServerPlayerEntity pilot) {
        if(!root.hasPoweredReactor()||!UfoControlSystem.controls(pilot,root)||!connected(root))return;
        var b=body(root).orElseThrow();var world=(ServerWorld)root.getWorld();
        if(b.boardingMotionPaused()||!BodyJointRegistry.get(world).active(joint,world.getTime()))return;
        if(laserCooldown==0&&SkyUfoWeapons.emit(b,pilot,List.of(LASER),ModWorldBlocks.SHIP_LASER,false)>0){laserCooldown=8;BodyJointSystem.reconcileImpulse(world,b);}
        if(rocketCooldown==0&&SkyUfoWeapons.emit(b,pilot,List.of(ROCKET),ModWorldBlocks.SHIP_ROCKET,true)>0){rocketCooldown=24;BodyJointSystem.reconcileImpulse(world,b);}
    }
    public void detach(SkyUfoEntity root){body(root).ifPresent(b->{b.setPhysicsGravity(true);b.setPassengerGravity(new Vec3(0,0,0));});}
    public NbtCompound write(){var n=new NbtCompound();n.putInt("schema",1);n.putUuid("body",child);n.putUuid("joint",joint);n.putInt("laserCooldown",laserCooldown);n.putInt("rocketCooldown",rocketCooldown);return n;}
    public static SkyUfoTurret read(NbtCompound n){if(n.getInt("schema")!=1||!n.containsUuid("body")||!n.containsUuid("joint"))throw new IllegalArgumentException("invalid UFO turret reference");var t=new SkyUfoTurret(n.getUuid("body"),n.getUuid("joint"));t.laserCooldown=MathHelper.clamp(n.getInt("laserCooldown"),0,8);t.rocketCooldown=MathHelper.clamp(n.getInt("rocketCooldown"),0,24);return t;}
}
