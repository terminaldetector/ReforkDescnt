package com.terminaldetector.drmd.mixin;
import com.terminaldetector.drmd.world.gravity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(AbstractSkeletonEntity.class)
public abstract class LocalActorRangedMixin {
    @Shadow protected abstract PersistentProjectileEntity createArrowProjectile(ItemStack stack,float pull,ItemStack bow);
    @Redirect(method="shootAt",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/AbstractSkeletonEntity;createArrowProjectile(Lnet/minecraft/item/ItemStack;FLnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/projectile/PersistentProjectileEntity;"),require=1,allow=1)
    private PersistentProjectileEntity physicalMuzzle(AbstractSkeletonEntity actor,ItemStack stack,float pull,ItemStack bow){var arrow=createArrowProjectile(stack,pull,bow);if(LocalActorTargeting.framed(actor))arrow.setPosition(actor.getEyePos().subtract(ActorFrames.up(actor).multiply(.1)));return arrow;}
    @Redirect(method="shootAt",at=@At(value="INVOKE",target="Lnet/minecraft/entity/projectile/PersistentProjectileEntity;setVelocity(DDDFF)V"),require=1,allow=1)
    private void physicalAim(PersistentProjectileEntity arrow,double x,double y,double z,float speed,float spread,LivingEntity target,float pull){
        var actor=(AbstractSkeletonEntity)(Object)this;
        if(LocalActorTargeting.framed(actor)||LocalActorTargeting.framed(target)){
            var aim=LocalActorTargeting.framed(target)?target.getPos().add(ActorFrames.up(target).multiply(target.getHeight()/3.0)):new Vec3d(target.getX(),target.getBodyY(1.0/3),target.getZ());
            var d=aim.subtract(arrow.getPos());x=d.x;y=d.y+Math.sqrt(d.x*d.x+d.z*d.z)*.2;z=d.z;
        }
        arrow.setVelocity(x,y,z,speed,spread);
    }
}
