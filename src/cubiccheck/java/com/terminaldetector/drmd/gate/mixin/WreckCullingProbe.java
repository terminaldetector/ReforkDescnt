package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.gate.NativeDroneWreckCheck;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
@Mixin(EntityRenderer.class)
public class WreckCullingProbe {
    private static final Set<UUID> drmd$observed=new HashSet<>();
    @Inject(method="shouldRender",at=@At("RETURN"))
    private void drmd$culling(Entity e,Frustum f,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        if(!NativeDroneWreckCheck.READ||!(e instanceof RagdollEntity r)||ci.getReturnValue()||!e.shouldRender(x,y,z)||!f.isVisible(e.getVisibilityBoundingBox().expand(.5))||!drmd$observed.add(e.getUuid()))return;
        com.terminaldetector.drmd.DescentMod.LOGGER.info("[wreck cull rejection] uuid={} kind={} vanillaRange=true vanillaFrustum=true finalVisible=false camera=({},{},{}) box={}",e.getUuid(),r.flightHullKind(),x,y,z,e.getVisibilityBoundingBox());
    }
}
