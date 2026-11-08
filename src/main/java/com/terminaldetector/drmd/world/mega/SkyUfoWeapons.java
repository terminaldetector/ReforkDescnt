package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Authored rigid nose mounts in the existing hull; no independent entity/turret tick. */
public final class SkyUfoWeapons {
    public static final List<BlockPos> LASERS=List.of(new BlockPos(-3,0,10),new BlockPos(3,0,10));
    public static final List<BlockPos> ROCKETS=List.of(new BlockPos(-6,0,9),new BlockPos(6,0,9));
    private SkyUfoWeapons(){}
    public static Vec3d muzzle(BlockBodyEntity body,BlockPos slot){return body.localToWorld(Vec3d.ofCenter(slot).add(0,0,.65));}
    public static int fire(SkyUfoEntity body,ServerPlayerEntity pilot,boolean rockets) {
        if(!UfoControlSystem.controls(pilot,body)||!body.hasPoweredReactor())return 0;
        var slots=rockets?ROCKETS:LASERS;Block block=rockets?ModWorldBlocks.SHIP_ROCKET:ModWorldBlocks.SHIP_LASER;
        return emit(body,pilot,slots,block,rockets);
    }
    static int fireAutonomous(SkyUfoEntity body,ServerPlayerEntity target,boolean rockets){
        if(!SkyUfoCombat.allowed(body)||target==null)return 0;
        var block=rockets?ModWorldBlocks.SHIP_ROCKET:ModWorldBlocks.SHIP_LASER;
        var slots=(rockets?ROCKETS:LASERS).stream().filter(p->body.getLocalBlock(p).isOf(block)&&SkyUfoCombat.clearShot(body,p,target,rockets?1.4:2.5,rockets?100:80)).toList();
        return emit(body,body,slots,block,rockets);
    }
    static int emit(BlockBodyEntity body,Entity pilot,List<BlockPos> slots,Block block,boolean rockets) {
        var dir=body.rotation().rotate(new Vec3(0,0,1));var forward=new Vec3d(dir.x(),dir.y(),dir.z());int count=0;
        var access=new BlockBodyWorldAccess(body);
        for(var slot:slots) {
            if(!body.getLocalBlock(slot).isOf(block))continue;
            var start=body.localToWorld(Vec3d.ofCenter(slot).add(0,0,.501));var muzzle=muzzle(body,slot);
            // Added local armor and ordinary world walls obstruct the real muzzle corridor.
            if(access.raycastBlockBody(start,muzzle).isPresent())continue;
            if(body.getWorld().raycast(new RaycastContext(start,muzzle,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,pilot)).getType()!=HitResult.Type.MISS)continue;
            boolean blocked=false;for(var other:BodySpatialIndex.query(body.getWorld(),new Box(start,muzzle).expand(.01)))if(other!=body&&other.physicsRaycast(start,muzzle).isPresent()){blocked=true;break;}if(blocked)continue;
            var cfg=new WeaponCore.FireConfig();cfg.owner=pilot;cfg.pos=muzzle;cfg.dir=forward;
            cfg.inheritedVelocity=body.surfaceVelocityAt(muzzle).multiply(.05);
            cfg.speedIsSourceUnits=false;cfg.speed=rockets?1.4f:2.5f;cfg.life=rockets?5:4;
            cfg.meshKind=rockets?1:0;cfg.visualScale=rockets?1.2f:.75f;
            cfg.dmgClass=rockets?DamageClass.EXPLOSIVE:DamageClass.ENERGY;
            cfg.directDamage=rockets?16:8;cfg.splashDamage=rockets?20:0;cfg.splashRadius=rockets?2:0;
            cfg.colorR=rockets?255:70;cfg.colorG=rockets?165:235;cfg.colorB=rockets?70:255;
            // Recoil belongs to the physical launcher, never to the pilot's hands/suit.
            if(WeaponCore.fireProjectile(cfg)!=null){body.applyPhysicsImpulse(muzzle,forward.multiply(rockets?-160:-24));count++;}
        }
        return count;
    }
}
