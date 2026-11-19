package com.terminaldetector.drmd.aerisfixture;
import net.minecraft.test.TestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.*;
/** TestServer auto-enables every discoverable pack; select the actual ordinary/explicit profile
 * BEFORE registry load instead of changing any dimension object after it exists. */
@Mixin(TestServer.class)
public abstract class AerisDataPackSelectionProbe {
    @ModifyArgs(method="create",at=@At(value="INVOKE",target="Lnet/minecraft/resource/DataPackSettings;<init>(Ljava/util/List;Ljava/util/List;)V"))
    private static void actualProfiles(Args args){
        if(!AerisProfileState.ENABLED)return;
        List<String> before=args.get(0),oldDisabled=args.get(1);var enabled=new ArrayList<>(before);var disabled=new ArrayList<>(oldDisabled);
        enabled.removeIf(v->v.equals(AerisProfileState.DEMO)||v.equals(AerisProfileState.ADVANCED));
        disabled.removeIf(v->v.equals(AerisProfileState.DEMO));
        if(!disabled.contains(AerisProfileState.ADVANCED))disabled.add(AerisProfileState.ADVANCED);
        if(AerisProfileState.MODE.equals("explicit"))enabled.add(AerisProfileState.DEMO);else disabled.add(AerisProfileState.DEMO);
        AerisProfileState.record(before,enabled,disabled);args.set(0,enabled);args.set(1,disabled);
    }
}
