package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The boarding motor owns slow gravity/contact momentum; vanilla cleanup precedes travel cancellation. */
@Mixin(LivingEntity.class)
public abstract class BoardingVelocityCleanupMixin {
    @Redirect(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V",ordinal=0),require=1,allow=1)
    private void preserveOwnedMomentum(LivingEntity actor,double x,double y,double z) {
        var current=actor.getVelocity();
        // Match this exact native cleanup, preserving any external impulse already in the receiver.
        // A different setter/result (including another mod's adjustment) is forwarded unchanged.
        if(actor instanceof ServerPlayerEntity player&&BoardingSystem.drives(player)
                &&x==(Math.abs(current.x)<.003?0:current.x)
                &&y==(Math.abs(current.y)<.003?0:current.y)
                &&z==(Math.abs(current.z)<.003?0:current.z))actor.setVelocity(current);
        else actor.setVelocity(x,y,z);
    }
}
