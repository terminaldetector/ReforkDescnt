package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Shared by reference batteries and player-built mechanisms. This emits, not schedules. */
public final class MountedWeaponEmitter {
    private MountedWeaponEmitter(){}
    public static Direction facing(BlockBodyEntity body,BlockPos slot){var state=body.getLocalBlock(slot);return state.contains(Properties.FACING)?state.get(Properties.FACING):Direction.SOUTH;}
    private static Vec3d localDirection(BlockBodyEntity body,BlockPos slot){return Vec3d.of(facing(body,slot).getVector());}
    public static Vec3d muzzle(BlockBodyEntity body,BlockPos slot){return body.localToWorld(Vec3d.ofCenter(slot).add(localDirection(body,slot).multiply(.65)));}
    public static ProjectileEntity emit(BlockBodyEntity body,Entity owner,BlockPos slot){
        if(!(body.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||body.isRemoved()||owner==null||!owner.isAlive()||owner.getWorld()!=world
            ||BodySpatialIndex.find(world,body.getId()).orElse(null)!=body)return null;
        var state=body.getLocalBlock(slot);boolean rocket=state.isOf(ModWorldBlocks.SHIP_ROCKET);
        if(!rocket&&!state.isOf(ModWorldBlocks.SHIP_LASER))return null;
        var local=localDirection(body,slot);var start=body.localToWorld(Vec3d.ofCenter(slot).add(local.multiply(.501)));var muzzle=muzzle(body,slot);
        if(!body.collisionRegionAvailable(new Box(start,muzzle).expand(.01)))return null;
        if(new BlockBodyWorldAccess(body).raycastBlockBody(start,muzzle).isPresent())return null;
        if(world.raycast(new RaycastContext(start,muzzle,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,owner)).getType()!=HitResult.Type.MISS)return null;
        for(var other:BodySpatialIndex.query(world,new Box(start,muzzle).expand(.01)))if(other!=body&&other.physicsRaycast(start,muzzle).isPresent())return null;
        var axis=body.rotation().rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(local.x,local.y,local.z));var forward=new Vec3d(axis.x(),axis.y(),axis.z());
        var cfg=new WeaponCore.FireConfig();cfg.owner=owner;cfg.pos=muzzle;cfg.dir=forward;cfg.inheritedVelocity=body.surfaceVelocityAt(muzzle).multiply(.05);
        cfg.speedIsSourceUnits=false;cfg.speed=rocket?1.4f:2.5f;cfg.life=rocket?5:4;cfg.meshKind=rocket?1:0;cfg.visualScale=rocket?1.2f:.75f;
        cfg.dmgClass=rocket?DamageClass.EXPLOSIVE:DamageClass.ENERGY;cfg.directDamage=rocket?16:8;cfg.splashDamage=rocket?20:0;cfg.splashRadius=rocket?2:0;
        cfg.colorR=rocket?255:70;cfg.colorG=rocket?165:235;cfg.colorB=rocket?70:255;
        var shot=WeaponCore.fireProjectile(cfg);if(shot!=null)body.applyPhysicsImpulse(muzzle,forward.multiply(rocket?-160:-24));return shot;
    }
}
