package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.util.*;

/** Authored rigid nose mounts in the existing hull; no independent entity/turret tick. */
public final class SkyUfoWeapons {
    public static final List<BlockPos> LASERS=List.of(new BlockPos(-3,0,10),new BlockPos(3,0,10));
    public static final List<BlockPos> ROCKETS=List.of(new BlockPos(-6,0,9),new BlockPos(6,0,9));
    private SkyUfoWeapons(){}
    public static Vec3d muzzle(BlockBodyEntity body,BlockPos slot){return com.terminaldetector.drmd.world.contraption.mechanism.MountedWeaponEmitter.muzzle(body,slot);}
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
        int count=0;
        for(var slot:slots){
            if(!body.getLocalBlock(slot).isOf(block)||com.terminaldetector.drmd.world.contraption.mechanism.MountedWeaponEmitter.facing(body,slot)!=Direction.SOUTH
                ||com.terminaldetector.drmd.world.contraption.mechanism.BodyMountedWeapons.locallyControlled(body,slot))continue;
            if(com.terminaldetector.drmd.world.contraption.mechanism.MountedWeaponEmitter.emit(body,pilot,slot)!=null)count++;
        }
        return count;
    }
}
