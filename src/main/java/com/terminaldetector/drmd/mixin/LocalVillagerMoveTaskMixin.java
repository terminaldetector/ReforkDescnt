package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.MoveToTargetTask;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native Brain owns WALK_TARGET. Pending local routing is not an unreachable goal. */
@Mixin(MoveToTargetTask.class)
public abstract class LocalVillagerMoveTaskMixin {
    @Shadow private Path path;
    @Shadow private boolean hasReached(MobEntity actor,WalkTarget target){throw new AssertionError();}

    @Unique private boolean waitingForLocalVillager(MobEntity actor,WalkTarget target) {
        return actor.getClass()==VillagerEntity.class&&target!=null&&!hasReached(actor,target)
            &&LocalSurfaceNavigation.searchingFor(actor,target.getLookTarget().getBlockPos());
    }

    @Inject(method="hasFinishedPath",at=@At(value="FIELD",target="Lnet/minecraft/entity/ai/brain/task/MoveToTargetTask;path:Lnet/minecraft/entity/ai/pathing/Path;",
        opcode=Opcodes.PUTFIELD,ordinal=0,shift=At.Shift.AFTER),cancellable=true,require=1)
    private void waitBeforeNativeFallback(MobEntity actor,WalkTarget target,long time,CallbackInfoReturnable<Boolean> ci) {
        if(path!=null||!waitingForLocalVillager(actor,target))return;
        // On a running retarget, the navigator still owns the previous accepted path.
        path=actor.getNavigation().getCurrentPath();
        ci.setReturnValue(false);
    }

    @Inject(method="shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/MobEntity;)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/entity/ai/brain/Brain;forget(Lnet/minecraft/entity/ai/brain/MemoryModuleType;)V",ordinal=0),
        cancellable=true,require=1)
    private void keepTheCurrentWalkMemory(ServerWorld world,MobEntity actor,CallbackInfoReturnable<Boolean> ci) {
        if(actor.getClass()!=VillagerEntity.class)return;
        var target=actor.getBrain().getOptionalRegisteredMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if(waitingForLocalVillager(actor,target))ci.setReturnValue(false);
    }
}
