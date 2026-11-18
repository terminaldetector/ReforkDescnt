package com.terminaldetector.drmd.world.contraption.mechanism;

import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.UUID;

/** First narrow Survival policy. It grants no PvP, offline, land-claim or new
 * faction authority, and never changes the existing hostile station policy. */
public final class PlayerMountedDefense {
    private PlayerMountedDefense(){}
    public static ServerPlayerEntity principal(ServerWorld world,UUID identity){
        if(identity==null)return null;var entity=world.getEntity(identity);
        return entity instanceof ServerPlayerEntity player&&player.isAlive()&&!player.isRemoved()&&player.getWorld()==world
            &&player.interactionManager.getGameMode()==GameMode.SURVIVAL?player:null;
    }
    public static boolean hostile(ServerWorld world,UUID principal,ProjectileEntity missile){
        var player=principal(world,principal);
        if(player==null||missile==null||!missile.isAlive()||missile.getWorld()!=world||!missile.isPointDefenseMissile()||missile.getOwnerUuid()==null)return false;
        var entity=world.getEntity(missile.getOwnerUuid());
        return entity instanceof LivingEntity shooter&&shooter.isAlive()&&!shooter.isRemoved()&&shooter.getWorld()==world
            &&HostileEnvironment.isAlly(shooter)&&HostileEnvironment.isHostileTarget(shooter,player)
            &&!shooter.isTeammate(player)&&shooter.canTarget(player);
    }
    public static boolean source(ServerWorld world,BlockBodyEntity body,BlockPos cell,UUID principal,UUID token){
        return world.getServer().isOnThread()&&body!=null&&cell!=null&&body.getWorld()==world&&body.isAlive()&&!body.isRemoved()
            &&!body.boardingMotionPaused()&&BodySpatialIndex.find(world,body.getId()).orElse(null)==body
            &&world.getEntity(body.getUuid())==body
            &&body.mountedWeapons().defenseClaims().matches(body,cell,principal,token)&&principal(world,principal)!=null
            &&world.getWorldBorder().contains(BlockPos.ofFloored(MountedWeaponEmitter.muzzle(body,cell)))
            &&world.shouldTickEntity(BlockPos.ofFloored(MountedWeaponEmitter.muzzle(body,cell)))
            &&BodyMountedWeapons.locallyControlled(body,cell);
    }
}
