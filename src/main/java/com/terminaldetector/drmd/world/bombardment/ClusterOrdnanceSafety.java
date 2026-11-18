package com.terminaldetector.drmd.world.bombardment;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.*;

/** Existing vanilla explosion/hit mechanics with explicit programmed-round permissions, not another damage engine. */
final class ClusterOrdnanceSafety {
    private ClusterOrdnanceSafety(){}
    static boolean volumeLoaded(ServerWorld world,Vec3d at,double radius){
        if(!Double.isFinite(at.lengthSquared())||!Double.isFinite(radius)||radius<0||radius>32||!world.getWorldBorder().contains(new Box(at,at).expand(radius)))return false;
        int x0=MathHelper.floor(at.x-radius-1)>>4,x1=MathHelper.floor(at.x+radius+1)>>4,y0=MathHelper.floor(at.y-radius-1)>>4,y1=MathHelper.floor(at.y+radius+1)>>4,z0=MathHelper.floor(at.z-radius-1)>>4,z1=MathHelper.floor(at.z+radius+1)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>216)return false;var access=WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){var p=new D6ChunkPos(x,y,z);if(CubicAvailability.withinHeight(world,p)&&!access.isVolumeAvailable(p))return false;}return true;
    }
    static boolean friendly(LivingEntity owner,Entity target){return owner==target||owner.isTeammate(target)||owner instanceof PlayerEntity p&&target instanceof PlayerEntity other&&!p.shouldDamagePlayer(other);}
    static ExplosionBehavior behavior(ServerWorld world,LivingEntity owner){return new ExplosionBehavior(){
        @Override public boolean canDestroyBlock(Explosion explosion,BlockView view,BlockPos pos,BlockState state,float power){return WorldEditSafety.mayChange(world,pos,owner)&&super.canDestroyBlock(explosion,view,pos,state,power);}
        @Override public boolean shouldDamage(Explosion explosion,Entity target){return !friendly(owner,target)&&super.shouldDamage(explosion,target);}
        @Override public float getKnockbackModifier(Entity target){return friendly(owner,target)?0:super.getKnockbackModifier(target);}
    };}
}
