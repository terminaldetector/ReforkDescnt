package com.terminaldetector.drmd.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Queue;
import java.util.function.BooleanSupplier;

/** A completed save future may still have live generation references. Its retry requeues
 * itself immediately; draining forever starves the executor which must release those refs. */
@Mixin(ServerChunkLoadingManager.class)
public class VanillaChunkUnloadBudgetMixin {
    @Shadow @Final private Queue<Runnable> unloadTaskQueue;
    @Unique private int drmd$unloadAllowance;
    @Inject(method="unloadChunks",at=@At("HEAD"))
    private void begin(BooleanSupplier keepTicking,CallbackInfo ci){drmd$unloadAllowance=Math.max(2000,unloadTaskQueue.size());}
    @Redirect(method="unloadChunks",at=@At(value="INVOKE",target="Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object next(Queue<Runnable> queue){return drmd$unloadAllowance-->0?queue.poll():null;}
}
