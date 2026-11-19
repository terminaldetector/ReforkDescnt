package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import com.terminaldetector.drmd.world.gravity.LocalEyeSamples;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep all eight native vision/overlay tests, centred and oriented on the actual local eye. */
@Mixin(InGameOverlayRenderer.class)
public abstract class LocalGravityOverlayMixin {
    @Redirect(method="getInWallBlockState",at=@At(value="INVOKE",target="Lnet/minecraft/util/math/BlockPos$Mutable;set(DDD)Lnet/minecraft/util/math/BlockPos$Mutable;"),require=1,allow=1)
    private static BlockPos.Mutable actualEyeSample(BlockPos.Mutable position,double x,double y,double z,PlayerEntity player){
        if(!FootGravitySystem.isActive(player))return position.set(x,y,z);
        var nativeEye=new Vec3d(player.getX(),player.getEyeY(),player.getZ());
        var sample=LocalEyeSamples.transform(new Vec3d(x,y,z),nativeEye,player.getEyePos(),FootGravitySystem.getUp(player));
        return position.set(sample.x,sample.y,sample.z);
    }
}
