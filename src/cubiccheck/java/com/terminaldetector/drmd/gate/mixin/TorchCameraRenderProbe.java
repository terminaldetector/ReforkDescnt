package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeTorchCameraCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Delegates every original call. Captures callsite-return even when a HEAD hook cancels. */
@Mixin(GameRenderer.class)
public abstract class TorchCameraRenderProbe {
    @Shadow private void tiltViewWhenHurt(MatrixStack m,float delta){throw new AssertionError();}
    @Shadow private void bobView(MatrixStack m,float delta){throw new AssertionError();}
    @Inject(method="render",at=@At("HEAD"))
    private void drmd$torchBefore(RenderTickCounter ticks,boolean tick,CallbackInfo ci){if(NativeTorchCameraCheck.ENABLED)NativeTorchCameraCheck.beforeFrame();}
    @Inject(method="render",at=@At("RETURN"))
    private void drmd$torchAfter(RenderTickCounter ticks,boolean tick,CallbackInfo ci){if(NativeTorchCameraCheck.ENABLED)NativeTorchCameraCheck.rendered(ticks.getTickDelta(false));}
    @Redirect(method="renderHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/GameRenderer;tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V"))
    private void drmd$torchHandTilt(GameRenderer receiver,MatrixStack matrices,float delta){
        Matrix4f before=NativeTorchCameraCheck.ENABLED?new Matrix4f(matrices.peek().getPositionMatrix()):null;
        tiltViewWhenHurt(matrices,delta);
        if(before!=null)NativeTorchCameraCheck.transform(true,false,before,new Matrix4f(matrices.peek().getPositionMatrix()));
    }
    @Redirect(method="renderHand",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/GameRenderer;bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V"))
    private void drmd$torchHandBob(GameRenderer receiver,MatrixStack matrices,float delta){
        Matrix4f before=NativeTorchCameraCheck.ENABLED?new Matrix4f(matrices.peek().getPositionMatrix()):null;
        bobView(matrices,delta);
        if(before!=null)NativeTorchCameraCheck.transform(true,true,before,new Matrix4f(matrices.peek().getPositionMatrix()));
    }
    @Redirect(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/GameRenderer;tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V"))
    private void drmd$torchWorldTilt(GameRenderer receiver,MatrixStack matrices,float delta){
        Matrix4f before=NativeTorchCameraCheck.ENABLED?new Matrix4f(matrices.peek().getPositionMatrix()):null;
        tiltViewWhenHurt(matrices,delta);
        if(before!=null)NativeTorchCameraCheck.transform(false,false,before,new Matrix4f(matrices.peek().getPositionMatrix()));
    }
}
