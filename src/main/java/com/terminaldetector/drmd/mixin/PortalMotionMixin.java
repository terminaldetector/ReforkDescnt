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
    @Unique private long drmd$travelTick=Long.MIN_VALUE;
    @Unique private RegistryKey<World> drmd$travelWorld;
    @Unique private Vec3d drmd$travelStart=Vec3d.ZERO,drmd$travelEnd=Vec3d.ZERO;
    @Inject(method="move",at=@At("HEAD"))
    private void before(MovementType type,Vec3d movement,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(entity.getWorld() instanceof ServerWorld world && (entity instanceof net.minecraft.server.network.ServerPlayerEntity||PortalGunState.hasActivePairs(world))) {
            if(entity instanceof net.minecraft.server.network.ServerPlayerEntity){
                long tick=world.getServer().getTicks();
                if(drmd$travelTick!=tick||!world.getRegistryKey().equals(drmd$travelWorld)||!drmd$travelEnd.equals(entity.getPos()))drmd$travelStart=entity.getPos();
                drmd$travelTick=tick;drmd$travelWorld=world.getRegistryKey();drmd$travelEnd=entity.getPos();
            }
            drmd$portalTick=world.getServer().getTicks();drmd$portalWorld=world.getRegistryKey();
            drmd$portalStart=entity.getPos();drmd$portalBefore=entity.getVelocity();drmd$portalAfter=drmd$portalBefore;
        }
    }
    @Inject(method="move",at=@At("RETURN"))
    private void after(MovementType type,Vec3d movement,CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if(entity.getWorld() instanceof ServerWorld world && drmd$portalTick==world.getServer().getTicks()){
            drmd$portalAfter=entity.getVelocity();
            if(entity instanceof net.minecraft.server.network.ServerPlayerEntity&&drmd$travelTick==world.getServer().getTicks())drmd$travelEnd=entity.getPos();
        }
    }
    @Override public Vec3d drmd$portalMovementStart(long tick){
        Entity entity=(Entity)(Object)this;
        return entity instanceof net.minecraft.server.network.ServerPlayerEntity&&drmd$travelTick!=Long.MIN_VALUE&&tick>=drmd$travelTick&&tick-drmd$travelTick<=1
            &&entity.getWorld().getRegistryKey().equals(drmd$travelWorld)&&entity.getPos().equals(drmd$travelEnd)?drmd$travelStart:null;
    }
    @Override public Vec3d drmd$portalVelocity(long tick) {
        Entity entity=(Entity)(Object)this;
        if(drmd$portalTick!=Long.MIN_VALUE && tick>=drmd$portalTick && tick-drmd$portalTick<=1
            && entity.getWorld().getRegistryKey().equals(drmd$portalWorld)
            && (drmd$portalStart.squaredDistanceTo(entity.prevX,entity.prevY,entity.prevZ)<1e-8
                ||entity instanceof net.minecraft.server.network.ServerPlayerEntity&&drmd$portalMovementStart(tick)!=null))
            return entity.getVelocity().add(drmd$portalBefore.subtract(drmd$portalAfter));
        return entity.getVelocity();
    }
}
