package com.terminaldetector.drmd.stock.mixin;

import com.terminaldetector.drmd.stock.SurfaceStockFixtureState;
import net.minecraft.test.TestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.ArrayList;
import java.util.List;

/** Real profile configuration before registry load; never substitutes dimension values. */
@Mixin(TestServer.class)
public abstract class StockDataPackSelectionProbe {
    @ModifyArgs(method="create",at=@At(value="INVOKE",target="Lnet/minecraft/resource/DataPackSettings;<init>(Ljava/util/List;Ljava/util/List;)V"))
    private static void ordinaryProfile(Args args){
        if(!SurfaceStockFixtureState.ENABLED)return;
        List<String> before=args.get(0),disabledBefore=args.get(1);
        var enabled=new ArrayList<>(before);enabled.removeIf(SurfaceStockFixtureState.LEGACY::equals);
        var disabled=new ArrayList<>(disabledBefore);if(!disabled.contains(SurfaceStockFixtureState.LEGACY))disabled.add(SurfaceStockFixtureState.LEGACY);
        SurfaceStockFixtureState.record(before,disabledBefore,enabled,disabled);args.set(0,enabled);args.set(1,disabled);
    }
}
