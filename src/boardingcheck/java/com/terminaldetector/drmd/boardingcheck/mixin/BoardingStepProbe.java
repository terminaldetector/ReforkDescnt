package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.gate.NativeBoardingCheck;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=DynamicBodyAttachment.class,remap=false)
public class BoardingStepProbe {
 private static final String STEP="step(Lcom/terminaldetector/drmd/world/contraption/boarding/DynamicBodyAttachment$State;Lcom/terminaldetector/drmd/world/contraption/boarding/DynamicBodyAttachment$Input;Ljava/util/List;DDDLcom/terminaldetector/drmd/world/contraption/boarding/DynamicBodyAttachment$Motion;)Lcom/terminaldetector/drmd/world/contraption/boarding/DynamicBodyAttachment$State;";
 @Inject(method=STEP,at=@At("HEAD"),remap=false)
 private static void before(DynamicBodyAttachment.State state,DynamicBodyAttachment.Input input,List<BodyMotion> bodies,double width,double height,double seconds,DynamicBodyAttachment.Motion motion,CallbackInfoReturnable<DynamicBodyAttachment.State> ci) {
  if(Boolean.getBoolean("drmd.gate.boarding"))NativeBoardingCheck.beforeJumpStep(state,input,seconds);
 }
 @Inject(method=STEP,at=@At("RETURN"),remap=false)
 private static void after(DynamicBodyAttachment.State state,DynamicBodyAttachment.Input input,List<BodyMotion> bodies,double width,double height,double seconds,DynamicBodyAttachment.Motion motion,CallbackInfoReturnable<DynamicBodyAttachment.State> ci) {
  if(Boolean.getBoolean("drmd.gate.boarding"))NativeBoardingCheck.afterJumpStep(ci.getReturnValue());
 }
}
