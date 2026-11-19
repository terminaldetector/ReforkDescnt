package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.portal.MirrorTargetGuard;
import com.terminaldetector.drmd.gate.NativeMirrorCheck;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Only the explicit mirror fallback fixture refuses one supplier before resource allocation. */
@Mixin(value=MirrorTargetGuard.class,remap=false)
public class MirrorAllocationFailureProbe {
    @Redirect(method="acquire",at=@At(value="INVOKE",target="Ljava/util/function/Supplier;get()Ljava/lang/Object;"),remap=false)
    private Object allocate(Supplier<?> supplier){return NativeMirrorCheck.allocateTarget(supplier);}
}
