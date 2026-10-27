package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.portal.gun.*;
import net.minecraft.entity.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Small per-entity memo, no global entity/world cache and no additional movement integration. */
@Mixin(Entity.class)
public class PortalMotionMixin implements PortalMotionAccess {
    @Unique private long drmd$portalTick=Long.MIN_VALUE;
    @Unique private RegistryKey<World> drmd$portalWorld;
    @Unique private Vec3d drmd$portalStart=Vec3d.ZERO,drmd$portalBefore=Vec3d.ZERO,drmd$portalAfter=Vec3d.ZERO;
    @Inject(method="move",at=@At("HEAD"))
    private void before(MovementType type,Vec3d movement,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(entity.getWorld() instanceof ServerWorld world && PortalGunState.hasActivePairs(world)) {
            drmd$portalTick=world.getServer().getTicks();drmd$portalWorld=world.getRegistryKey();
            drmd$portalStart=entity.getPos();drmd$portalBefore=entity.getVelocity();drmd$portalAfter=drmd$portalBefore;
        }
    }
    @Inject(method="move",at=@At("RETURN"))
    private void after(MovementType type,Vec3d movement,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(entity.getWorld() instanceof ServerWorld world && drmd$portalTick==world.getServer().getTicks())drmd$portalAfter=entity.getVelocity();
    }
    @Override public Vec3d drmd$portalVelocity(long tick) {
        Entity entity=(Entity)(Object)this;
        if(drmd$portalTick!=Long.MIN_VALUE && tick>=drmd$portalTick && tick-drmd$portalTick<=1
            && entity.getWorld().getRegistryKey().equals(drmd$portalWorld)
            && drmd$portalStart.squaredDistanceTo(entity.prevX,entity.prevY,entity.prevZ)<1e-8)
            return entity.getVelocity().add(drmd$portalBefore.subtract(drmd$portalAfter));
        return entity.getVelocity();
    }
}
