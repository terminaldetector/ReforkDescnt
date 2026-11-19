package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativeBdhCubicCheck;
import com.terminaldetector.drmd.gate.NativeHudReadabilityCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class CubicFrameProbe {
    @Inject(method="render",at=@At("HEAD"))
    private void hudReadabilityBefore(RenderTickCounter counter,boolean tick,CallbackInfo ci){NativeHudReadabilityCheck.beforeFrame();}
    @Inject(method="render",at=@At("HEAD"))
    private void highAltitudeBefore(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(com.terminaldetector.drmd.gate.NativeHighAltitudeLodCheck.ENABLED)com.terminaldetector.drmd.gate.NativeHighAltitudeLodCheck.beforeFrame();}
    @Inject(method="render",at=@At("HEAD"))
    private void mobGeometryBefore(RenderTickCounter counter,boolean tick,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeMobGeometryCheck.beforeFrame();}
    @Inject(method="render",at=@At("HEAD"))
    private void assetsBefore(RenderTickCounter counter,boolean tick,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeAssetsPacingCheck.beforeFrame();com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.beforeFrame();com.terminaldetector.drmd.gate.NativeClusterInputCheck.beforeFrame();com.terminaldetector.drmd.gate.NativePs1AtmosphereCheck.beforeFrame();}
    @Inject(method="render",at=@At("HEAD"))
    private void before(RenderTickCounter counter,boolean tick,CallbackInfo ci){com.terminaldetector.drmd.gate.NativePresentationCheck.beforeFrame();com.terminaldetector.drmd.gate.NativeMountedWeaponCheck.beforeFrame();com.terminaldetector.drmd.gate.NativeMechanismCheck.beforeFrame();com.terminaldetector.drmd.gate.NativeTurretAiCheck.beforeFrame(counter.getTickDelta(false));com.terminaldetector.drmd.gate.NativeUfoAiCheck.beforeFrame(counter.getTickDelta(false));com.terminaldetector.drmd.gate.NativeTurretReloadCheck.beforeFrame(counter.getTickDelta(false));com.terminaldetector.drmd.gate.NativeIslandLoadCheck.beforeFrame(counter.getTickDelta(false));com.terminaldetector.drmd.gate.NativeSharedCombatCheck.beforeFrame(counter.getTickDelta(false));com.terminaldetector.drmd.gate.NativeTypedCombatRouteCheck.beforeFrame(counter.getTickDelta(false));}
    @Inject(method="render",at=@At("RETURN"))
    private void completed(RenderTickCounter counter,boolean tick,CallbackInfo ci) {
        if(com.terminaldetector.drmd.gate.NativeHighAltitudeLodCheck.ENABLED){com.terminaldetector.drmd.gate.NativeHighAltitudeLodCheck.rendered(counter.getTickDelta(false));return;}
        if(NativeBdhCubicCheck.ENABLED){NativeBdhCubicCheck.rendered();return;}
        if(NativeHudReadabilityCheck.ENABLED){NativeHudReadabilityCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeOrbitalMissionCheck.ENABLED){com.terminaldetector.drmd.gate.NativeOrbitalMissionCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.ENABLED){com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeVerticalFlightCheck.ENABLED){com.terminaldetector.drmd.gate.NativeVerticalFlightCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativePs1AtmosphereCheck.ENABLED){com.terminaldetector.drmd.gate.NativePs1AtmosphereCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeMobGeometryCheck.ENABLED){com.terminaldetector.drmd.gate.NativeMobGeometryCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeClusterInputCheck.ENABLED){com.terminaldetector.drmd.gate.NativeClusterInputCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.ENABLED){com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeAssetsPacingCheck.ENABLED){com.terminaldetector.drmd.gate.NativeAssetsPacingCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativePortalTransitCheck.ENABLED){com.terminaldetector.drmd.gate.NativePortalTransitCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativePresentationCheck.ENABLED){com.terminaldetector.drmd.gate.NativePresentationCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeMountedWeaponCheck.ENABLED){com.terminaldetector.drmd.gate.NativeMountedWeaponCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeMechanismCheck.ENABLED){com.terminaldetector.drmd.gate.NativeMechanismCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeTurretAiCheck.ENABLED){com.terminaldetector.drmd.gate.NativeTurretAiCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeUfoAiCheck.ENABLED){com.terminaldetector.drmd.gate.NativeUfoAiCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeIslandLoadCheck.ENABLED){com.terminaldetector.drmd.gate.NativeIslandLoadCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeJointReloadCheck.READ){com.terminaldetector.drmd.gate.NativeJointReloadCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeSharedCombatCheck.ENABLED){com.terminaldetector.drmd.gate.NativeSharedCombatCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeTypedCombatRouteCheck.ENABLED){com.terminaldetector.drmd.gate.NativeTypedCombatRouteCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeDroneWreckCheck.ENABLED){com.terminaldetector.drmd.gate.NativeDroneWreckCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.ENABLED){com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeDroneRouteCheck.ENABLED){com.terminaldetector.drmd.gate.NativeDroneRouteCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeDroneStateCheck.READ){com.terminaldetector.drmd.gate.NativeDroneStateCheck.rendered();return;}
        if(Boolean.getBoolean("drmd.gate.swarm")){com.terminaldetector.drmd.gate.NativeSwarmBaselineCheck.rendered();return;}
        if(com.terminaldetector.drmd.gate.NativeUfoReloadCheck.READ){com.terminaldetector.drmd.gate.NativeUfoReloadCheck.rendered();com.terminaldetector.drmd.gate.NativeTurretReloadCheck.rendered();return;}
        if(Boolean.getBoolean("drmd.gate.flightBody")){com.terminaldetector.drmd.gate.NativeFlightBodyCheck.rendered();return;}
        if(Boolean.getBoolean("drmd.gate.joints")){com.terminaldetector.drmd.gate.NativeJointCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.bodyIndex")){com.terminaldetector.drmd.gate.NativeBodyIndexCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.bodyEdit")){com.terminaldetector.drmd.gate.NativeBodyEditCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.boarding"))com.terminaldetector.drmd.gate.NativeBoardingCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.snapshot"))com.terminaldetector.drmd.gate.NativeSnapshotCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.gun"))com.terminaldetector.drmd.gate.NativePortalGunCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.actors"))com.terminaldetector.drmd.gate.NativeActorFrameCheck.rendered();
        else if(Boolean.getBoolean("drmd.gate.mirrors"))com.terminaldetector.drmd.gate.NativeMirrorCheck.rendered();
        else CubicClientGate.frameFinished();
    }
}
